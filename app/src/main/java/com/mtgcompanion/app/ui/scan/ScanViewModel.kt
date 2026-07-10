package com.mtgcompanion.app.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.CollectionRepository
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckRepository
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

data class ScanUiState(
    val status: String? = null,
    val scannedCards: List<ScryfallCard> = emptyList()
)

class ScanViewModel(
    private val cardRepository: CardRepository = CardRepository(),
    private val collectionRepository: CollectionRepository,
    private val deckRepository: DeckRepository
) : ViewModel() {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // Gates one frame's OCR+lookup at a time; combined with ImageAnalysis's
    // STRATEGY_KEEP_ONLY_LATEST (which withholds the next frame until this one's
    // ImageProxy is closed), this naturally throttles scanning to roughly one
    // attempt per round trip instead of hammering ML Kit/Scryfall at camera frame rate.
    private val busy = AtomicBoolean(false)

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    val decks: StateFlow<List<Deck>> = deckRepository.decksFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    /** Called for each analyzed camera frame; [onProcessed] must always run so the frame is released. */
    fun onFrame(image: InputImage, onProcessed: () -> Unit) {
        if (!busy.compareAndSet(false, true)) {
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
                val card = cardRepository.getByFuzzyName(candidate)
                val alreadyScanned = _uiState.value.scannedCards.any { it.id == card.id }
                _uiState.value = if (alreadyScanned) {
                    _uiState.value.copy(status = "${card.name} is already in the list")
                } else {
                    // Newest first so the just-scanned card is visible at the top of the list.
                    _uiState.value.copy(
                        status = "Added ${card.name}",
                        scannedCards = listOf(card) + _uiState.value.scannedCards
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(status = "Didn't recognize \"$candidate\" — keep scanning…")
            } finally {
                busy.set(false)
                onProcessed()
            }
        }
    }

    /**
     * Debug-only entry point: run one image through the real ML Kit + Scryfall pipeline,
     * bypassing the [busy] gate that the live camera analyzer holds. Surfaces the OCR result
     * in the status line so a headless emulator (no real camera) can still exercise recognition.
     */
    fun debugScan(image: InputImage) {
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val candidate = extractCardName(visionText)
                if (candidate == null) {
                    _uiState.value = _uiState.value.copy(status = "OCR read no card title")
                    return@addOnSuccessListener
                }
                _uiState.value = _uiState.value.copy(status = "OCR read \"$candidate\" — looking up…")
                viewModelScope.launch {
                    try {
                        val card = cardRepository.getByFuzzyName(candidate)
                        val alreadyScanned = _uiState.value.scannedCards.any { it.id == card.id }
                        _uiState.value = if (alreadyScanned) {
                            _uiState.value.copy(status = "${card.name} is already in the list")
                        } else {
                            _uiState.value.copy(
                                status = "Added ${card.name}",
                                scannedCards = listOf(card) + _uiState.value.scannedCards
                            )
                        }
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(status = "No match for \"$candidate\"")
                    }
                }
            }
            .addOnFailureListener { e ->
                _uiState.value = _uiState.value.copy(status = "OCR failed: ${e.message}")
            }
    }

    fun removeFromList(card: ScryfallCard) {
        _uiState.value = _uiState.value.copy(scannedCards = _uiState.value.scannedCards.filterNot { it.id == card.id })
    }

    fun addToCollection(card: ScryfallCard) {
        viewModelScope.launch {
            collectionRepository.addCard(card)
            _uiState.value = _uiState.value.copy(status = "Added ${card.name} to collection")
        }
    }

    fun addToDeck(card: ScryfallCard, deckId: String) {
        viewModelScope.launch {
            deckRepository.addCardToDeck(deckId, card)
            _uiState.value = _uiState.value.copy(status = "Added ${card.name} to deck")
        }
    }

    fun createDeckAndAdd(card: ScryfallCard, name: String) {
        viewModelScope.launch {
            val deck = deckRepository.createDeck(name)
            deckRepository.addCardToDeck(deck.id, card)
            _uiState.value = _uiState.value.copy(status = "Added ${card.name} to \"${deck.name}\"")
        }
    }

    class Factory(
        private val collectionRepository: CollectionRepository,
        private val deckRepository: DeckRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ScanViewModel(
                cardRepository = CardRepository(),
                collectionRepository = collectionRepository,
                deckRepository = deckRepository
            ) as T
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
