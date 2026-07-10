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

sealed interface ScanUiState {
    data object Ready : ScanUiState
    data object Processing : ScanUiState
    data class Found(val card: ScryfallCard) : ScanUiState
    data class NotFound(val recognizedText: String) : ScanUiState
    data class Error(val message: String) : ScanUiState
}

class ScanViewModel(private val repository: CardRepository = CardRepository()) : ViewModel() {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Ready)
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    /** [onProcessed] must always be called so the caller can release the camera frame. */
    fun onImageCaptured(image: InputImage, onProcessed: () -> Unit) {
        _uiState.value = ScanUiState.Processing
        recognizer.process(image)
            .addOnSuccessListener { visionText -> handleRecognizedText(visionText) }
            .addOnFailureListener { e ->
                _uiState.value = ScanUiState.Error(e.message ?: "Text recognition failed.")
            }
            .addOnCompleteListener { onProcessed() }
    }

    fun onCaptureError(message: String) {
        _uiState.value = ScanUiState.Error(message)
    }

    fun reset() {
        _uiState.value = ScanUiState.Ready
    }

    private fun handleRecognizedText(visionText: Text) {
        val candidate = extractCardName(visionText)
        if (candidate == null) {
            _uiState.value = ScanUiState.Error("Couldn't read a card name. Fill the frame with the card's title and try again.")
            return
        }
        viewModelScope.launch {
            _uiState.value = try {
                ScanUiState.Found(repository.getByFuzzyName(candidate))
            } catch (e: Exception) {
                ScanUiState.NotFound(candidate)
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
