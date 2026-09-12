package com.mtgcompanion.app.ui.scan

import android.graphics.Bitmap
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
import com.mtgcompanion.app.data.duplicateWarning
import com.mtgcompanion.app.data.artrecognition.ArtIndexRepository
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** A scanned card and how many copies were scanned (adjustable before adding to a deck/binder). */
data class ScannedCard(val card: ScryfallCard, val quantity: Int = 1)

/** Consecutive title-less frames required before concluding a card has actually left the frame
 * (as opposed to one blurry/glared frame while it's still sitting there). At the analyzer's
 * throttled rate (roughly one attempt per round trip, not real camera frame rate) this is a
 * fraction of a second — enough to absorb a flicker without meaningfully delaying recognition of
 * a genuinely new card. */
private const val BLANK_FRAMES_TO_RESET = 4

/** Minimum (score minus runner-up) an art match needs before it's trusted enough to auto-add —
 * calibrated against synthetic camera-like distortion (crop/rotation/lighting/JPEG noise) of clean
 * reference scans, not real photographs yet; a conservative starting point pending real-world use. */
private const val ART_MATCH_MIN_MARGIN = 4000

data class ScanUiState(
    val status: String? = null,
    val scannedCards: List<ScannedCard> = emptyList(),
    /** Bumped on every successful add — a one-shot event distinct from [status] (which is also
     * used for non-success messages like a failed lookup) so the UI can trigger a haptic/visual
     * flash only on real successes, via a LaunchedEffect keyed on this value. */
    val successToken: Int = 0
)

class ScanViewModel(
    private val cardRepository: CardRepository = CardRepository(),
    private val collectionRepository: CollectionRepository,
    private val deckRepository: DeckRepository,
    private val artIndexRepository: ArtIndexRepository
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
    //  - lastAddedCard: the card we most recently added from THIS card sitting in frame — cleared
    //                   once the card is confidently gone (see blankFrameStreak below). OCR isn't
    //                   pixel-stable frame to frame, so a lingering card can occasionally read as
    //                   a slightly different string (a stray misread character); comparing new
    //                   candidates against this via looksLikeSameCard catches that case so the
    //                   same physical card doesn't silently get re-added (bumping its count) just
    //                   because the exact OCR string flickered while it never actually left view.
    //  - blankFrameStreak: consecutive title-less frames. A single blank frame (glare, motion
    //                   blur, a hand momentarily crossing the lens) does NOT by itself mean the
    //                   card left — clearing the guards on just one blank frame was the original
    //                   bug: the very next frame reads the same still-in-view card fresh, passes
    //                   the stability check again, hits the nameCache, and silently re-adds it,
    //                   bumping its count with no card ever actually having been swapped. Only a
    //                   real run of blank frames (BLANK_FRAMES_TO_RESET) is treated as "card gone".
    private var lastCandidate: String? = null
    private var lastLookedUp: String? = null
    private var lastAddedCard: ScryfallCard? = null
    private var blankFrameStreak = 0
    private val nameCache = HashMap<String, ScryfallCard>()

    // Set by captureNow() (the manual "tap to scan" button): the next successfully OCR'd frame is
    // accepted immediately, skipping the two-frame stability wait and the same-card-still-in-frame
    // guard — an explicit user tap is itself the confirmation those guards otherwise stand in for,
    // and this doubles as the deliberate way to re-scan the same physical card to bump its count.
    private val forceScanNext = AtomicBoolean(false)

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
        val forced = forceScanNext.getAndSet(false)
        if (candidate == null) {
            // No title in view. Only treat this as "the card actually left" after a real streak
            // of blank frames — see blankFrameStreak's doc comment above.
            if (++blankFrameStreak >= BLANK_FRAMES_TO_RESET) {
                lastCandidate = null
                lastLookedUp = null
                lastAddedCard = null
            }
            busy.set(false)
            onProcessed()
            return
        }
        blankFrameStreak = 0

        // Still the same physical card sitting in frame, even if this frame's OCR came out
        // slightly different from the exact string we last looked up — don't re-add it. A forced
        // (manual capture) scan skips this: the user tapping the button IS the "yes, really"
        // confirmation, and re-scanning the same card on purpose is how you bump its count.
        if (!forced) {
            lastAddedCard?.let { last ->
                if (looksLikeSameCard(candidate, last.name)) {
                    lastCandidate = candidate.lowercase()
                    busy.set(false)
                    onProcessed()
                    return
                }
            }
        }

        val normalized = candidate.lowercase()
        // Require the same title on two consecutive frames before spending a lookup — this
        // rejects blurry mid-motion misreads — and don't re-look-up a title still in frame.
        // A forced scan accepts whatever's in frame right now instead of waiting.
        val stable = forced || normalized == lastCandidate
        lastCandidate = normalized
        if (!stable || (!forced && normalized == lastLookedUp)) {
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
            lastAddedCard = cached
            busy.set(false)
            onProcessed()
            return
        }

        viewModelScope.launch {
            try {
                val card = resolveCard(candidate, printing)
                nameCache[cacheKey] = card
                addScannedCard(card)
                lastAddedCard = card
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
        val next = if (existing != null) {
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
        _uiState.value = next.copy(successToken = next.successToken + 1)
        scanSound.play(MediaActionSound.SHUTTER_CLICK)
    }

    /** The manual "tap to scan" button: force the very next camera frame's OCR result straight
     * through, bypassing the stability wait and the same-card guard (see [forceScanNext]'s doc). */
    fun captureNow() {
        forceScanNext.set(true)
    }

    /**
     * Type-and-add fallback for when OCR keeps missing a card (glare, damaged/foil card, sleeve
     * glare) or grabbed the wrong one — a fuzzy name lookup straight to the scanned list, the same
     * resolution the camera path falls back to when it can't read an exact printing.
     */
    fun manualAdd(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            try {
                val card = cardRepository.getByFuzzyName(trimmed)
                addScannedCard(card)
                lastAddedCard = card
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(status = "No match for \"$trimmed\"")
            }
        }
    }

    /**
     * Identify a card by its art instead of text — the fallback for exactly what OCR struggles
     * with (glare, damage, an unusual frame) as long as the art itself is still legible. Requires
     * the art-recognition data to have been downloaded in Settings first.
     */
    fun matchByArt(bitmap: Bitmap) {
        _uiState.value = _uiState.value.copy(status = "Matching by art…")
        viewModelScope.launch {
            val match = try {
                artIndexRepository.match(bitmap)
            } catch (e: Exception) {
                null
            }
            when {
                match == null && !artIndexRepository.status.value.hasData ->
                    _uiState.value = _uiState.value.copy(status = "Download art-recognition data in Settings first.")
                match == null ->
                    _uiState.value = _uiState.value.copy(status = "Couldn't match this card by art.")
                match.margin < ART_MATCH_MIN_MARGIN ->
                    _uiState.value = _uiState.value.copy(status = "Not confident enough — try again, or type the name.")
                else -> {
                    val card = try {
                        cardRepository.getCardsByIds(listOf(match.card.scryfallId)).firstOrNull()
                    } catch (e: Exception) {
                        null
                    }
                    if (card != null) {
                        addScannedCard(card)
                        lastAddedCard = card
                    } else {
                        _uiState.value = _uiState.value.copy(status = "Matched \"${match.card.name}\" by art, but couldn't load its data.")
                    }
                }
            }
        }
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
        CollectionEntry(card.id, card.name, card.displayImageUrl, quantity = quantity, foilQuantity = 0, backImageUrl = card.backImageUrl, tags = card.tags)

    private fun deckEntry(card: ScryfallCard, quantity: Int) =
        DeckCardEntry(card.id, card.name, card.displayImageUrl, quantity = quantity, canBeCommander = card.canBeCommander, typeLine = card.typeLine, partnerAbility = card.partnerAbility, backImageUrl = card.backImageUrl, tags = card.tags)

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
            // Checked before adding, off the deck's currently-stored state — informational only,
            // the card is added either way (testing/sideboard scenarios are legitimate).
            val warning = deckRepository.decksFlow.first().find { it.id == deckId }
                ?.let { duplicateWarning(it, card, addingQuantity = quantity) }
            deckRepository.addEntry(deckId, deckEntry(card, quantity))
            _uiState.value = _uiState.value.copy(status = warning ?: "Added $quantity × ${card.name} to deck")
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
        private val deckRepository: DeckRepository,
        private val artIndexRepository: ArtIndexRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ScanViewModel(
                cardRepository = CardRepository(),
                collectionRepository = collectionRepository,
                deckRepository = deckRepository,
                artIndexRepository = artIndexRepository
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
