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
import java.io.File
import java.io.IOException

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
 * move with every app release; they're versioned together because the index is made BY that model.
 * A rebuilt index (new sets) goes up under the same tag, and "Update" in Settings fetches it.
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

    /** The recognizer, once the data is on the phone and loaded; null until then. */
    fun recognizer(): CardRecognizer? = recognizer

    private fun load() {
        val loaded = runCatching { CardRecognizer(modelFile, indexFile) }.getOrNull()
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

        /** Roughly the model's share of the whole download, so one bar spans both files. */
        const val MODEL_SHARE = 0.34f
    }
}
