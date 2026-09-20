package com.mtgcompanion.app.ui.scan

import com.mtgcompanion.app.data.UNSORTED_COLLECTION_NAME
import com.mtgcompanion.app.data.UNSORTED_COLLECTION_ID
import com.mtgcompanion.app.data.grouped
import com.mtgcompanion.app.data.ScanGroup
import com.mtgcompanion.app.data.GUIDE_WIDTH
import com.mtgcompanion.app.data.GUIDE_GIVE_UP_FRAMES
import com.mtgcompanion.app.data.GUIDE_HEIGHT
import com.mtgcompanion.app.data.guideInImage
import com.mtgcompanion.app.data.GUIDE_SLACK
import com.mtgcompanion.app.data.confirmRead
import com.mtgcompanion.app.data.STEADY_READS
import com.mtgcompanion.app.data.Confirmation
import com.mtgcompanion.app.data.scannedTwiceOver
import com.mtgcompanion.app.data.copyNumber
import com.mtgcompanion.app.data.ScanRow
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

// The scanned list is one row per scan, newest first — see data/ScanLog.kt.

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
    val scannedCards: List<ScanRow> = emptyList(),
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
    // Reads of the same title in a row; a card is looked up at STEADY_READS of them.
    private var steadyReads = 0
    private val nameCache = HashMap<String, ScryfallCard>()

    // The preview's size in pixels, set by the screen: with it, the framing guide can be placed in
    // the camera's own picture, and text outside it (the next card along) left unread.
    private var previewWidth = 0
    private var previewHeight = 0
    // Frames in a row with text read, none of it inside the guide (see GUIDE_GIVE_UP_FRAMES).
    private var outsideGuideStreak = 0

    /** The screen says how big the camera preview is; the guide is a share of it (see ScanScreen). */
    fun previewSized(width: Int, height: Int) {
        previewWidth = width
        previewHeight = height
    }

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
            .addOnSuccessListener { visionText -> handleRecognizedText(visionText, image, onProcessed) }
            .addOnFailureListener {
                busy.set(false)
                onProcessed()
            }
    }

    private fun handleRecognizedText(visionText: Text, image: InputImage, onProcessed: () -> Unit) {
        val forced = forceScanNext.getAndSet(false)
        // Only text inside the framing guide is the card being scanned; the rest is whatever else is
        // on the table. Tapping "Scan now" with nothing in the guide reads the whole frame instead.
        val all = visionText.textBlocks.flatMap { it.lines }
        val inGuide = linesInGuide(visionText, image, previewWidth, previewHeight)
        // Safety net: if text keeps being read but never inside the guide — a phone whose reader
        // measures its picture differently — the guide is set aside rather than scanning nothing.
        outsideGuideStreak = if (all.isNotEmpty() && inGuide.isEmpty()) outsideGuideStreak + 1 else 0
        val ignoreGuide = forced || outsideGuideStreak >= GUIDE_GIVE_UP_FRAMES
        val lines = if (inGuide.isEmpty() && ignoreGuide) all else inGuide
        val candidate = extractCardName(lines)
        if (candidate == null) {
            // No title in view. Only treat this as "the card actually left" after a real streak
            // of blank frames — see blankFrameStreak's doc comment above.
            if (++blankFrameStreak >= BLANK_FRAMES_TO_RESET) {
                steadyReads = 0
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
        // Require the same title on STEADY_READS frames in a row before spending a lookup — a card
        // halfway into the frame, or caught mid-motion, rarely reads the same three times running —
        // and don't re-look-up a title still in frame. A forced scan accepts whatever's in frame
        // right now instead of waiting.
        steadyReads = if (normalized == lastCandidate) steadyReads + 1 else 1
        val stable = forced || steadyReads >= STEADY_READS
        lastCandidate = normalized
        if (!stable || (!forced && normalized == lastLookedUp)) {
            busy.set(false)
            onProcessed()
            return
        }
        lastLookedUp = normalized

        // Also read the set code + collector number so we can fetch the exact printing, not just
        // the default one. Cache by that printing when known so a re-scan skips the network.
        val printing = extractSetAndNumber(lines)
        val cacheKey = printing?.let { "${it.first}:${it.second}" } ?: normalized

        nameCache[cacheKey]?.let { cached ->
            if (accept(candidate, cached, forced)) lastAddedCard = cached
            busy.set(false)
            onProcessed()
            return
        }

        viewModelScope.launch {
            try {
                val card = resolveCard(candidate, printing)
                if (accept(candidate, card, forced)) {
                    nameCache[cacheKey] = card
                    lastAddedCard = card
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
     * Adds [card] only if the read really named it. A fuzzy lookup answers half a title with a real
     * card, so a card that wasn't all in the frame would otherwise join the list as if it had been
     * scanned properly. Tapping "Scan now" ([forced]) says "yes, really" and skips the check.
     */
    private fun accept(candidate: String, card: ScryfallCard, forced: Boolean): Boolean {
        val confirmation = if (forced) Confirmation.YES else confirmRead(candidate, card.name)
        if (confirmation == Confirmation.YES) {
            addScannedCard(card)
            return true
        }
        // Nothing is added, and this reading isn't spent on another lookup; more of the card coming
        // into the frame reads differently, and that is looked up.
        steadyReads = 0
        _uiState.value = _uiState.value.copy(
            status = if (confirmation == Confirmation.PARTIAL) {
                "Only read \"$candidate\" — hold the whole card in the frame, its name in the gold strip."
            } else {
                "Read \"$candidate\", which looks like ${card.name} — hold the card still and try again."
            }
        )
        return false
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

    /** Every scan is its own row, newest first, so a card read twice shows twice. */
    private fun addScannedCard(card: ScryfallCard) {
        val row = ScanRow(nextScanId++, card, System.currentTimeMillis())
        val rows = listOf(row) + _uiState.value.scannedCards
        val copy = copyNumber(rows, row)
        val status = if (copy > 1) {
            "${card.name} again — copy $copy" + if (scannedTwiceOver(rows, row)) ", scanned just now" else ""
        } else {
            "Added ${card.name}"
        }
        val next = _uiState.value.copy(status = status, scannedCards = rows)
        _uiState.value = next.copy(successToken = next.successToken + 1)
        scanSound.play(MediaActionSound.SHUTTER_CLICK)
    }

    private var nextScanId = 1L

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

    /** One more copy of a card already scanned — its own row, as if it went past the camera again. */
    fun scanAgain(card: ScryfallCard) {
        addScannedCard(card)
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
                val lines = visionText.textBlocks.flatMap { it.lines }
                val candidate = extractCardName(lines)
                if (candidate == null) {
                    _uiState.value = _uiState.value.copy(status = "OCR read no card title")
                    return@addOnSuccessListener
                }
                val printing = extractSetAndNumber(lines)
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

    /** Takes one scan off the pile — a card read twice, or read wrongly. */
    fun removeScan(rowId: Long) {
        _uiState.value = _uiState.value.copy(scannedCards = _uiState.value.scannedCards.filterNot { it.id == rowId })
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

    /** Everything scanned, copies added together — what a whole pile goes into a binder or deck as. */
    private fun pile(): List<ScanGroup> = grouped(_uiState.value.scannedCards)

    private fun pileAdded(where: String, cards: Int) {
        _uiState.value = _uiState.value.copy(
            status = "Added $cards ${if (cards == 1) "card" else "cards"} to $where",
            scannedCards = emptyList()
        )
        lastAddedCard = null
        lastLookedUp = null
    }

    /** The whole pile into a binder; the list is emptied, ready for the next pile. */
    fun addAllToCollection(collectionId: String) {
        val pile = pile()
        if (pile.isEmpty()) return
        viewModelScope.launch {
            val entries = pile.map { collectionEntry(it.card, it.quantity) }
            val name = collectionRepository.collectionsFlow.first().find { it.id == collectionId }?.name
                ?: UNSORTED_COLLECTION_NAME
            // The Unsorted pile is made when the first cards go into it.
            if (collectionId == UNSORTED_COLLECTION_ID) collectionRepository.addUnsorted(entries)
            else collectionRepository.addEntries(collectionId, entries)
            pileAdded("\"$name\"", pile.sumOf { it.quantity })
        }
    }

    /** The whole pile into a new binder named [name]. */
    fun createCollectionAndAddAll(name: String) {
        val pile = pile()
        if (pile.isEmpty()) return
        viewModelScope.launch {
            val collection = collectionRepository.createCollection(name)
            collectionRepository.addEntries(collection.id, pile.map { collectionEntry(it.card, it.quantity) })
            pileAdded("\"${collection.name}\"", pile.sumOf { it.quantity })
        }
    }

    /** The whole pile into a deck. */
    fun addAllToDeck(deckId: String) {
        val pile = pile()
        if (pile.isEmpty()) return
        viewModelScope.launch {
            val deck = deckRepository.decksFlow.first().find { it.id == deckId }
            pile.forEach { deckRepository.addEntry(deckId, deckEntry(it.card, it.quantity)) }
            pileAdded("\"${deck?.name ?: "deck"}\"", pile.sumOf { it.quantity })
        }
    }

    /** The whole pile into a new deck named [name]. */
    fun createDeckAndAddAll(name: String) {
        val pile = pile()
        if (pile.isEmpty()) return
        viewModelScope.launch {
            val deck = deckRepository.createDeck(name)
            pile.forEach { deckRepository.addEntry(deck.id, deckEntry(it.card, it.quantity)) }
            pileAdded("\"${deck.name}\"", pile.sumOf { it.quantity })
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
 * The lines of text inside the framing guide, with a little room to spare for a card held large.
 * Every line when the preview's size isn't known yet, or the guide can't be worked out.
 */
internal fun linesInGuide(visionText: Text, image: InputImage, previewWidth: Int, previewHeight: Int): List<Text.Line> {
    val lines = visionText.textBlocks.flatMap { it.lines }
    // The reader turns the picture upright, and the boxes it hands back are in that upright picture.
    val upright = image.rotationDegrees == 90 || image.rotationDegrees == 270
    val width = if (upright) image.height else image.width
    val height = if (upright) image.width else image.height
    val guide = guideInImage(width, height, previewWidth, previewHeight, GUIDE_WIDTH, GUIDE_HEIGHT)
        ?.grownBy(GUIDE_SLACK)
        ?: return lines
    return lines.filter { line ->
        val box = line.boundingBox ?: return@filter true
        guide.holdsCentreOf(box.left, box.top, box.right, box.bottom)
    }
}

/**
 * A card's title is printed as the top-most line of text on its frame, above the type line and
 * rules text. Scryfall's fuzzy search then tolerates the remaining OCR noise (mana symbols read
 * as stray characters, minor misreads, etc).
 */
internal fun extractCardName(lines: List<Text.Line>): String? {
    return lines
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
internal fun extractSetAndNumber(textLines: List<Text.Line>): Pair<String, String>? {
    val lines = textLines.map { it.text }
    val setCode = lines.firstNotNullOfOrNull { SET_LANG.find(it)?.groupValues?.get(1) } ?: return null
    val number = lines.firstNotNullOfOrNull { RARITY_NUMBER.find(it)?.groupValues?.get(1) }
        ?: lines.firstNotNullOfOrNull { SLASH_NUMBER.find(it)?.groupValues?.get(1) }
        ?: return null
    val trimmed = number.trimStart('0').ifEmpty { "0" }
    return setCode to trimmed
}
