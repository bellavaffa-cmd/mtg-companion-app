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

/** Progress/state of the downloadable art-recognition index, surfaced in Settings. */
data class ArtIndexStatus(
    val cardCount: Int = 0,
    val downloading: Boolean = false,
    val progress: Float = 0f,
    val message: String? = null
) {
    val hasData: Boolean get() = cardCount > 0
}

/** Hosted as a GitHub Release asset on its own stable tag (separate from the app's own version
 * tags) so this download link doesn't move every time the app itself gets a new release. */
private const val INDEX_DOWNLOAD_URL =
    "https://github.com/bellavaffa-cmd/mtg-companion-app/releases/download/art-index-v1/card_art_index.mtgart"

/**
 * Downloads (on request only — not automatic, it's ~61MB) and holds in memory the art-recognition
 * index, and runs the on-device embedding model against it. A fallback/confirming identification
 * signal for the scanner alongside OCR — see ScanViewModel — for the cases OCR struggles with
 * (glare, damaged text, an unusual frame) but the art is still readable.
 */
class ArtIndexRepository(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val file = File(appContext.filesDir, "card_art_index.mtgart")

    private val _status = MutableStateFlow(ArtIndexStatus())
    val status: StateFlow<ArtIndexStatus> = _status.asStateFlow()

    // Lazy: the ~14MB ONNX model only loads into memory once art-matching is actually used.
    private val embedder by lazy { ArtEmbedder(appContext) }
    @Volatile private var index: ArtIndex? = null

    init {
        if (file.exists()) scope.launch { loadIndex() }
    }

    private fun loadIndex() {
        val loaded = runCatching { ArtIndexReader.read(file) }.getOrNull()
        index = loaded
        if (loaded != null) {
            _status.update { it.copy(cardCount = loaded.size, message = "Ready — ${loaded.size} cards' art recognized.") }
        }
    }

    /** No-op while a download is already running. */
    fun downloadIndex() {
        if (_status.value.downloading) return
        scope.launch {
            _status.update { it.copy(downloading = true, progress = 0f, message = "Downloading art-recognition data…") }
            try {
                downloadToFile()
                loadIndex()
                _status.update { it.copy(downloading = false, progress = 1f) }
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

    private fun downloadToFile() {
        val tmp = File(appContext.filesDir, "card_art_index.mtgart.tmp")
        val request = Request.Builder().url(INDEX_DOWNLOAD_URL).build()
        // Non-caching client: a ~61MB one-time body has no business evicting the small JSON cache.
        NetworkModule.noCacheOkHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} downloading art-recognition data")
            val body = response.body ?: throw IOException("Empty response downloading art-recognition data")
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
                                _status.update { it.copy(progress = percent / 100f) }
                            }
                        }
                    }
                }
            }
        }
        if (file.exists()) file.delete()
        if (!tmp.renameTo(file)) throw IOException("Could not finalize the downloaded art-recognition data")
    }

    /**
     * Match a photographed card's art against the index. Null if the index hasn't been downloaded
     * yet. Always returns a match (never null just for low confidence) when the index is loaded —
     * callers gate on [ArtMatch.margin] themselves; see ScanViewModel's confidence threshold.
     */
    suspend fun match(bitmap: Bitmap): ArtMatch? = withContext(Dispatchers.Default) {
        val idx = index ?: return@withContext null
        idx.search(embedder.embed(bitmap))
    }
}
