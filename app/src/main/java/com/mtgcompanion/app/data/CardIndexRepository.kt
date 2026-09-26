package com.mtgcompanion.app.data

import android.content.Context
import com.mtgcompanion.app.network.NetworkModule
import com.mtgcompanion.app.ui.scan.CardRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Where the card-recognition data stands, for the scan screen and Settings. */
data class CardIndexStatus(
    val ready: Boolean = false,
    val cardCount: Int = 0,
    val downloading: Boolean = false,
    val progress: Float = 0f,
    val message: String? = null
)

/**
 * The two files the scanner knows cards by sight with — the card index (a fingerprint of every
 * English paper printing, ~17 MB) and the image model that fingerprints the camera's card (~9 MB) —
 * downloaded once, the first time the scanner opens, and kept. See CardRecognizer.
 *
 * Both live on one release tag of their own, apart from the app's version tags, so the links don't
 * move with every app release. The index is rebuilt there twice a week when new printings come out
 * (the Android repo's card-index workflow), with card-index.json describing it: every few days the
 * scanner checks that, and fetches whichever file has changed (see refresh). "Update" in Settings
 * fetches both regardless.
 */
class CardIndexRepository(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val indexFile = File(appContext.filesDir, "card-index.bin")
    private val modelFile = File(appContext.filesDir, "card-model.onnx")

    private val _status = MutableStateFlow(CardIndexStatus())
    val status: StateFlow<CardIndexStatus> = _status.asStateFlow()

    @Volatile private var recognizer: CardRecognizer? = null

    init {
        // The art recognition this replaces kept 75 MB of its own; it's no use to anything now.
        for (old in listOf("card_art_index.mtgart", "mobilenetv2.onnx")) File(appContext.filesDir, old).delete()
        if (indexFile.exists() && modelFile.exists()) scope.launch { load() }
    }

    /**
     * Called when the scanner opens: fetches the data if it isn't here yet, and otherwise — at most
     * every few days — checks whether a newer index (new sets) or model has been published, and
     * fetches just what changed. The check is card-index.json, a few hundred bytes; the files on
     * the phone are compared with it by their digests, so nothing else needs remembering.
     */
    fun refresh() {
        if (!indexFile.exists() || !modelFile.exists()) {
            download()
            return
        }
        if (_status.value.downloading) return
        val prefs = appContext.getSharedPreferences("card_index", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(CHECKED, 0) < CHECK_EVERY_MS) return
        scope.launch {
            val meta = runCatching {
                NetworkModule.noCacheOkHttpClient.newCall(Request.Builder().url(META_URL).build()).execute().use { r ->
                    if (r.isSuccessful) r.body?.string()?.let { JSONObject(it) } else null
                }
            }.getOrNull() ?: return@launch
            prefs.edit().putLong(CHECKED, now).apply()
            val index = meta.optString("index")
            val model = meta.optString("model")
            val newIndex = index.isNotEmpty() && index != digest(indexFile)
            val newModel = model.isNotEmpty() && model != digest(modelFile)
            if (!newIndex && !newModel) return@launch
            _status.update { it.copy(downloading = true, progress = 0f, message = "Updating card recognition with new sets…") }
            try {
                val modelShare = if (newModel && newIndex) MODEL_SHARE else if (newModel) 1f else 0f
                if (newModel) download(MODEL_URL, modelFile, 0f, modelShare)
                if (newIndex) download(INDEX_URL, indexFile, modelShare, 1f - modelShare)
                load()
                // Published while this was downloading: what came down isn't what was described, so
                // look again next time the scanner opens.
                if ((newIndex && digest(indexFile) != index) || (newModel && digest(modelFile) != model)) prefs.edit().putLong(CHECKED, 0).apply()
                _status.update { it.copy(downloading = false, progress = 1f, message = null) }
            } catch (e: Exception) {
                prefs.edit().putLong(CHECKED, 0).apply()
                _status.update { it.copy(downloading = false, message = null) }
            }
        }
    }

    /** The first 16 hex digits of [file]'s SHA-256, as card-index.json gives them. */
    private fun digest(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n == -1) break
                md.update(buffer, 0, n)
            }
        }
        return md.digest().take(8).joinToString("") { "%02x".format(it) }
    }

    /** The recognizer, once the data is on the phone and loaded; null until then. */
    fun recognizer(): CardRecognizer? = recognizer

    private fun load() {
        val loaded = runCatching { CardRecognizer(modelFile, indexFile) }.getOrNull()
        // The first run builds the model's plan and costs ~14 s on a phone; done here, off the
        // scanning path, so no card ever waits for it.
        loaded?.warmUp()
        recognizer?.close()
        recognizer = loaded
        _status.update {
            it.copy(
                ready = loaded != null,
                cardCount = loaded?.index?.count ?: 0,
                message = if (loaded == null) "The card-recognition data couldn't be read — try Update." else null
            )
        }
    }

    /**
     * Fetches the data if it isn't here yet — or, with [force], again (Settings' "Update", for
     * newly released sets). No-op while already downloading.
     */
    fun download(force: Boolean = false) {
        if (_status.value.downloading) return
        if (!force && indexFile.exists() && modelFile.exists()) return
        scope.launch {
            _status.update { it.copy(downloading = true, progress = 0f, message = "Downloading card recognition (~26 MB)…") }
            try {
                download(MODEL_URL, modelFile, 0f, MODEL_SHARE)
                download(INDEX_URL, indexFile, MODEL_SHARE, 1f - MODEL_SHARE)
                load()
                _status.update { it.copy(downloading = false, progress = 1f) }
            } catch (e: Exception) {
                _status.update {
                    it.copy(
                        downloading = false,
                        message = if (isOffline(e)) "You're offline — card recognition will download when you're back online."
                        else "Card recognition didn't download: ${e.message ?: "unknown error"}"
                    )
                }
            }
        }
    }

    /**
     * Streams one file to disk, reporting progress into the [from] .. [from] + [span] slice of the
     * whole download. Written to a temporary file and renamed when complete, so an interrupted
     * download never leaves a truncated file looking like a whole one.
     */
    private fun download(url: String, dest: File, from: Float, span: Float) {
        val tmp = File(dest.parentFile, "${dest.name}.tmp")
        NetworkModule.noCacheOkHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for ${dest.name}")
            val body = response.body ?: throw IOException("Nothing came back for ${dest.name}")
            val total = body.contentLength().takeIf { it > 0 }
            var read = 0L
            var lastPercent = -1
            body.byteStream().use { input ->
                tmp.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n == -1) break
                        out.write(buffer, 0, n)
                        read += n
                        if (total != null) {
                            val percent = (read * 100 / total).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                _status.update { it.copy(progress = from + span * percent / 100f) }
                            }
                        }
                    }
                }
            }
        }
        if (dest.exists()) dest.delete()
        if (!tmp.renameTo(dest)) throw IOException("Couldn't keep the downloaded ${dest.name}")
    }

    private companion object {
        const val DATA_URL = "https://github.com/bellavaffa-cmd/mtg-companion-app/releases/download/card-index-v1"
        const val INDEX_URL = "$DATA_URL/card-index.bin"
        const val MODEL_URL = "$DATA_URL/card-model.onnx"
        const val META_URL = "$DATA_URL/card-index.json"

        /** How often, at most, the scanner checks for a newer index. */
        const val CHECK_EVERY_MS = 3L * 24 * 60 * 60 * 1000
        const val CHECKED = "checked_at"

        /** Roughly the model's share of the whole download, so one bar spans both files. */
        const val MODEL_SHARE = 0.34f
    }
}
