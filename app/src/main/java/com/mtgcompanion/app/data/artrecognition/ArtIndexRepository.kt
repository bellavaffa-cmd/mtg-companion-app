package com.mtgcompanion.app.data.artrecognition

import android.content.Context
import android.graphics.Bitmap
import com.mtgcompanion.app.data.isOffline
import com.mtgcompanion.app.network.NetworkModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException

/** Progress/state of the downloadable art-recognition data, surfaced in Settings. */
data class ArtIndexStatus(
    val cardCount: Int = 0,
    val modelReady: Boolean = false,
    val downloading: Boolean = false,
    val progress: Float = 0f,
    val message: String? = null
) {
    /** Both halves — the reference index and the embedding model — have to be on disk to match. */
    val hasData: Boolean get() = cardCount > 0 && modelReady
}

/**
 * Both files live on one stable release tag, separate from the app's own version tags, so these
 * links don't move every time the app gets a release. They're versioned together deliberately: the
 * index holds embeddings produced BY this model, so a new model means a rebuilt index, and pairing
 * them on a single tag is what keeps the two from drifting apart.
 */
private const val DATA_TAG_URL =
    "https://github.com/bellavaffa-cmd/mtg-companion-app/releases/download/art-index-v1"
private const val INDEX_DOWNLOAD_URL = "$DATA_TAG_URL/card_art_index.mtgart"
private const val MODEL_DOWNLOAD_URL = "$DATA_TAG_URL/mobilenetv2.onnx"

/** Roughly the model's share of the ~75MB combined download, so one bar can span both files. */
private const val MODEL_PROGRESS_SHARE = 0.19f

/**
 * Downloads (on request only — it's ~75MB) and holds in memory the art-recognition data, and runs
 * the on-device embedding model against it. A fallback/confirming identification signal for the
 * scanner alongside OCR — see ScanViewModel — for the cases OCR struggles with (glare, damaged
 * text, an unusual frame) but the art is still readable.
 *
 * Two files: the ~61MB reference index, and the ~14MB MobileNetV2 model that embeds a photo into
 * the same vector space. The model used to be bundled in the APK, but it's dead weight for anyone
 * who never turns art recognition on — and useless without the index, which was already an opt-in
 * download — so as of 1.69.0 it comes down alongside it.
 */
class ArtIndexRepository(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val indexFile = File(appContext.filesDir, "card_art_index.mtgart")
    private val modelFile = File(appContext.filesDir, "mobilenetv2.onnx")

    private val _status = MutableStateFlow(ArtIndexStatus())
    val status: StateFlow<ArtIndexStatus> = _status.asStateFlow()

    @Volatile private var index: ArtIndex? = null
    @Volatile private var embedder: ArtEmbedder? = null

    init {
        _status.update { it.copy(modelReady = modelFile.exists()) }
        if (indexFile.exists()) scope.launch { loadIndex() }
    }

    private fun loadIndex() {
        val loaded = runCatching { ArtIndexReader.read(indexFile) }.getOrNull()
        index = loaded
        _status.update {
            val count = loaded?.size ?: 0
            it.copy(cardCount = count, message = readinessMessage(count, it.modelReady))
        }
    }

    private fun readinessMessage(cardCount: Int, modelReady: Boolean): String? = when {
        cardCount > 0 && modelReady -> "Ready — $cardCount cards' art recognized."
        // Upgrading from 1.68.0 or earlier: the index is already on disk from before, but the
        // recognition model that used to ship inside the APK now downloads alongside it.
        cardCount > 0 -> "Finish setup — the recognition model (~14 MB) still needs downloading."
        modelReady -> "The card index (~61 MB) still needs downloading."
        else -> null
    }

    /**
     * Fetches whatever isn't on disk yet. [force] re-fetches both regardless — that's the Settings
     * "update" action, which exists to pick up a rebuilt index. No-op while already downloading.
     */
    fun downloadData(force: Boolean = false) {
        if (_status.value.downloading) return
        scope.launch {
            _status.update {
                it.copy(downloading = true, progress = 0f, message = "Downloading art-recognition data…")
            }
            try {
                val fetchModel = force || !modelFile.exists()
                val fetchIndex = force || !indexFile.exists()
                // Split the bar by size when fetching both, but let whichever file is actually
                // being fetched span the whole width — someone upgrading from 1.68.0 only needs
                // the model, and a bar that stops dead at 19% would look like a stall.
                val modelSpan = when {
                    fetchModel && fetchIndex -> MODEL_PROGRESS_SHARE
                    fetchModel -> 1f
                    else -> 0f
                }

                if (fetchModel) {
                    download(MODEL_DOWNLOAD_URL, modelFile, 0f, modelSpan)
                    // A newly downloaded model invalidates any ORT session built from the old one.
                    embedder = null
                    _status.update { it.copy(modelReady = true) }
                }
                if (fetchIndex) {
                    download(INDEX_DOWNLOAD_URL, indexFile, modelSpan, 1f - modelSpan)
                }
                // Skipping this when only the model was fetched avoids re-parsing 61MB for nothing.
                if (fetchIndex || index == null) loadIndex()

                _status.update {
                    it.copy(
                        downloading = false,
                        progress = 1f,
                        message = readinessMessage(it.cardCount, it.modelReady)
                    )
                }
            } catch (e: Exception) {
                _status.update {
                    it.copy(
                        downloading = false,
                        message = if (isOffline(e)) {
                            "You're offline — connect to the internet to download art-recognition data."
                        } else {
                            "Download failed: ${e.message ?: "unknown error"}"
                        }
                    )
                }
            }
        }
    }

    /**
     * Streams one file to disk, reporting progress into the [progressFrom] ..
     * [progressFrom] + [progressSpan] slice of the combined two-file download. Writes to a
     * temporary file and renames on success, so an interrupted download can't leave a truncated
     * file looking like a complete one.
     */
    private fun download(url: String, dest: File, progressFrom: Float, progressSpan: Float) {
        val tmp = File(dest.parentFile, "${dest.name}.tmp")
        val request = Request.Builder().url(url).build()
        // Non-caching client: a one-time body this size has no business evicting the small JSON cache.
        NetworkModule.noCacheOkHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} downloading ${dest.name}")
            val body = response.body ?: throw IOException("Empty response downloading ${dest.name}")
            val total = body.contentLength().takeIf { it > 0 }
            var bytesRead = 0L
            var lastPercent = -1
            body.byteStream().use { input ->
                tmp.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        bytesRead += read
                        if (total != null) {
                            val percent = ((bytesRead * 100) / total).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                _status.update {
                                    it.copy(progress = progressFrom + progressSpan * (percent / 100f))
                                }
                            }
                        }
                    }
                }
            }
        }
        if (dest.exists()) dest.delete()
        if (!tmp.renameTo(dest)) throw IOException("Could not finalize the downloaded ${dest.name}")
    }

    /**
     * Built on first use and held afterwards — creating the ORT session reads the whole model.
     * Null when the model hasn't been downloaded yet.
     */
    private fun embedder(): ArtEmbedder? {
        embedder?.let { return it }
        if (!modelFile.exists()) return null
        return synchronized(this) {
            embedder ?: runCatching { ArtEmbedder(modelFile) }.getOrNull()?.also { embedder = it }
        }
    }

    /**
     * Match a photographed card's art against the index. Null if the data hasn't been downloaded
     * yet. Always returns a match (never null just for low confidence) when it has — callers gate
     * on [ArtMatch.margin] themselves; see ScanViewModel's confidence threshold.
     */
    suspend fun match(bitmap: Bitmap): ArtMatch? = withContext(Dispatchers.Default) {
        val idx = index ?: return@withContext null
        val emb = embedder() ?: return@withContext null
        idx.search(emb.embed(bitmap))
    }
}
