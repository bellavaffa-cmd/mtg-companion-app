package com.mtgcompanion.app.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

sealed interface ScanUiState {
    data class Scanning(val status: String? = null) : ScanUiState
    data class Found(val card: ScryfallCard) : ScanUiState
}

class ScanViewModel(private val repository: CardRepository = CardRepository()) : ViewModel() {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // Gates one frame's OCR+lookup at a time; combined with ImageAnalysis's
    // STRATEGY_KEEP_ONLY_LATEST (which withholds the next frame until this one's
    // ImageProxy is closed), this naturally throttles scanning to roughly one
    // attempt per round trip instead of hammering ML Kit/Scryfall at camera frame rate.
    private val busy = AtomicBoolean(false)

    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Scanning())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    /** Called for each analyzed camera frame; [onProcessed] must always run so the frame is released. */
    fun onFrame(image: InputImage, onProcessed: () -> Unit) {
        if (_uiState.value is ScanUiState.Found || !busy.compareAndSet(false, true)) {
            onProcessed()
            return
        }
        recognizer.process(image)
            .addOnSuccessListener { visionText -> handleRecognizedText(visionText, onProcessed) }
            .addOnFailureListener {
                busy.set(false)
                onProcessed()
            }
    }

    private fun handleRecognizedText(visionText: Text, onProcessed: () -> Unit) {
        val candidate = extractCardName(visionText)
        if (candidate == null) {
            busy.set(false)
            onProcessed()
            return
        }
        viewModelScope.launch {
            try {
                val card = repository.getByFuzzyName(candidate)
                _uiState.value = ScanUiState.Found(card)
                // Leave busy = true: the screen navigates away and unbinds the camera.
            } catch (e: Exception) {
                _uiState.value = ScanUiState.Scanning("Didn't recognize \"$candidate\" — keep scanning…")
                busy.set(false)
            } finally {
                onProcessed()
            }
        }
    }
}

/**
 * A card's title is printed as the top-most line of text on its frame, above the type line and
 * rules text. Scryfall's fuzzy search then tolerates the remaining OCR noise (mana symbols read
 * as stray characters, minor misreads, etc).
 */
internal fun extractCardName(visionText: Text): String? {
    return visionText.textBlocks
        .flatMap { it.lines }
        .filter { line -> line.text.count { c -> c.isLetter() } >= 3 }
        .minByOrNull { it.boundingBox?.top ?: Int.MAX_VALUE }
        ?.text
        ?.substringBefore("{")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}
