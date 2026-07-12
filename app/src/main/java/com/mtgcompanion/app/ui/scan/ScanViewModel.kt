package com.mtgcompanion.app.ui.scan

import android.media.MediaActionSound
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionEntry
import com.mtgcompanion.app.data.CollectionRepository
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckCardEntry
import com.mtgcompanion.app.data.DeckRepository
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** A scanned card and how many copies were scanned (adjustable before adding to a deck/binder). */
data class ScannedCard(val card: ScryfallCard, val quantity: Int = 1)

data class ScanUiState(
    val status: String? = null,
    val scannedCards: List<ScannedCard> = emptyList()
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

    // Scan-throughput guards so we don't fire a Scryfall lookup on every frame:
    //  - lastCandidate: the previous frame's OCR title, to require a stable two-frame read.
    //  - lastLookedUp:  the title we last sent to Scryfall, so a card lingering in frame
    //                   isn't looked up again and again.
    //  - nameCache:     titles already resolved this session, to skip the network entirely.
    private var lastCandidate: String? = null
    private var lastLookedUp: String? = null
    private val nameCache = HashMap<String, ScryfallCard>()

    // A camera-shutter click played on each successful scan.
    private val scanSound = MediaActionSound().apply { load(MediaActionSound.SHUTTER_CLICK) }

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    val decks: StateFlow<List<Deck>> = deckRepository.decksFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val collections: StateFlow<List<Collection>> = collectionRepository.collectionsFlow.stateIn(
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
            // No title in view (e.g. between cards) — reset so the next card reads as fresh.
            lastCandidate = null
            lastLookedUp = null
            busy.set(false)
            onProcessed()
            return
        }
        val normalized = candidate.lowercase()
        // Require the same title on two consecutive frames before spending a lookup — this
        // rejects blurry mid-motion misreads — and don't re-look-up a title still in frame.
        val stable = normalized == lastCandidate
        lastCandidate = normalized
        if (!stable || normalized == lastLookedUp) {
            busy.set(false)
            onProcessed()
            return
        }
        lastLookedUp = normalized

        // Also read the set code + collector number so we can fetch the exact printing, not just
        // the default one. Cache by that printing when known so a re-scan skips the network.
        val printing = extractSetAndNumber(visionText)
        val cacheKey = printing?.let { "${it.first}:${it.second}" } ?: normalized

        nameCache[cacheKey]?.let { cached ->
            addScannedCard(cached)
            busy.set(false)
            onProcessed()
            return
        }

        viewModelScope.launch {
            try {
                val card = resolveCard(candidate, printing)
                nameCache[cacheKey] = card
                addScannedCard(card)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(status = "Didn't recognize \"$candidate\" — keep scanning…")
            } finally {
                busy.set(false)
                onProcessed()
            }
        }
    }

    /**
     * Resolve OCR to a card. Prefer the exact printing read off the card (set + collector number),
     * accepting it only if its name matches the read title; otherwise fall back to a fuzzy name
     * lookup (which returns the default printing).
     */
    private suspend fun resolveCard(candidate: String, printing: Pair<String, String>?): ScryfallCard {
        if (printing != null) {
            val exact = try {
                cardRepository.getBySetAndNumber(printing.first, printing.second)
            } catch (e: Exception) {
                null
            }
            if (exact != null && looksLikeSameCard(candidate, exact.name)) return exact
        }
        return cardRepository.getByFuzzyName(candidate)
    }

    /** Guard against a mis-read set/number returning an unrelated card: names must roughly match. */
    private fun looksLikeSameCard(ocrTitle: String, cardName: String): Boolean {
        fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
        val a = norm(ocrTitle)
        val b = norm(cardName)
        if (a.isEmpty() || b.isEmpty()) return false
        return a.contains(b) || b.contains(a) || a.commonPrefixWith(b).length >= 4
    }

    private fun addScannedCard(card: ScryfallCard) {
        val existing = _uiState.value.scannedCards.find { it.card.id == card.id }
        _uiState.value = if (existing != null) {
            // Re-scanning a card bumps its copy count instead of duplicating the row.
            _uiState.value.copy(
                status = "${card.name} ×${existing.quantity + 1}",
                scannedCards = _uiState.value.scannedCards.map {
                    if (it.card.id == card.id) it.copy(quantity = it.quantity + 1) else it
                }
            )
        } else {
            // Newest first so the just-scanned card is visible at the top of the list.
            _uiState.value.copy(
                status = "Added ${card.name}",
                scannedCards = listOf(ScannedCard(card, 1)) + _uiState.value.scannedCards
            )
        }
        scanSound.play(MediaActionSound.SHUTTER_CLICK)
    }

    fun incrementScanned(card: ScryfallCard) {
        _uiState.value = _uiState.value.copy(
            scannedCards = _uiState.value.scannedCards.map {
                if (it.card.id == card.id) it.copy(quantity = it.quantity + 1) else it
            }
        )
    }

    /** Lower a scanned card's count; drops it from the list at zero. */
    fun decrementScanned(card: ScryfallCard) {
        _uiState.value = _uiState.value.copy(
            scannedCards = _uiState.value.scannedCards.mapNotNull {
                when {
                    it.card.id != card.id -> it
                    it.quantity > 1 -> it.copy(quantity = it.quantity - 1)
                    else -> null
                }
            }
        )
    }

    override fun onCleared() {
        scanSound.release()
        super.onCleared()
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
                val printing = extractSetAndNumber(visionText)
                _uiState.value = _uiState.value.copy(status = "OCR read \"$candidate\" — looking up…")
                viewModelScope.launch {
                    try {
                        addScannedCard(resolveCard(candidate, printing))
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
        _uiState.value = _uiState.value.copy(scannedCards = _uiState.value.scannedCards.filterNot { it.card.id == card.id })
    }

    private fun collectionEntry(card: ScryfallCard, quantity: Int) =
        CollectionEntry(card.id, card.name, card.displayImageUrl, quantity = quantity, foilQuantity = 0)

    private fun deckEntry(card: ScryfallCard, quantity: Int) =
        DeckCardEntry(card.id, card.name, card.displayImageUrl, quantity = quantity, canBeCommander = card.canBeCommander)

    fun addToCollection(card: ScryfallCard, quantity: Int, collectionId: String) {
        viewModelScope.launch {
            collectionRepository.addEntry(collectionId, collectionEntry(card, quantity))
            _uiState.value = _uiState.value.copy(status = "Added $quantity × ${card.name} to binder")
        }
    }

    fun createCollectionAndAdd(card: ScryfallCard, quantity: Int, name: String) {
        viewModelScope.launch {
            val collection = collectionRepository.createCollection(name)
            collectionRepository.addEntry(collection.id, collectionEntry(card, quantity))
            _uiState.value = _uiState.value.copy(status = "Added $quantity × ${card.name} to \"${collection.name}\"")
        }
    }

    fun addToDeck(card: ScryfallCard, quantity: Int, deckId: String) {
        viewModelScope.launch {
            deckRepository.addEntry(deckId, deckEntry(card, quantity))
            _uiState.value = _uiState.value.copy(status = "Added $quantity × ${card.name} to deck")
        }
    }

    fun createDeckAndAdd(card: ScryfallCard, quantity: Int, name: String) {
        viewModelScope.launch {
            val deck = deckRepository.createDeck(name)
            deckRepository.addEntry(deck.id, deckEntry(card, quantity))
            _uiState.value = _uiState.value.copy(status = "Added $quantity × ${card.name} to \"${deck.name}\"")
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

private val SET_LANG = Regex("\\b([A-Z0-9]{3,5})\\s*[•·・∙]\\s*[A-Z]{2}\\b")
private val RARITY_NUMBER = Regex("\\b[CURMSPLT]\\s+(\\d{1,4})\\b")
private val SLASH_NUMBER = Regex("\\b(\\d{1,4})\\s*/\\s*\\d{1,4}\\b")

/**
 * Try to read the exact printing from the small print at the bottom of a card: the set code sits
 * before a bullet and 2-letter language ("MSC • EN"), and the collector number follows the rarity
 * letter ("U 0211") or is written as "number/total". Returns (setCode, collectorNumber) with leading
 * zeros stripped, or null when either can't be read confidently (the caller then falls back to name).
 */
internal fun extractSetAndNumber(visionText: Text): Pair<String, String>? {
    val lines = visionText.textBlocks.flatMap { it.lines }.map { it.text }
    val setCode = lines.firstNotNullOfOrNull { SET_LANG.find(it)?.groupValues?.get(1) } ?: return null
    val number = lines.firstNotNullOfOrNull { RARITY_NUMBER.find(it)?.groupValues?.get(1) }
        ?: lines.firstNotNullOfOrNull { SLASH_NUMBER.find(it)?.groupValues?.get(1) }
        ?: return null
    val trimmed = number.trimStart('0').ifEmpty { "0" }
    return setCode to trimmed
}
