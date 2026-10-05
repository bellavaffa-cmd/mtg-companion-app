package com.mtgcompanion.app.ui.scan

import com.mtgcompanion.app.tester.ScanOutcome
import com.mtgcompanion.app.tester.Tester
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CompletableDeferred
import com.mtgcompanion.app.data.SightPick
import com.mtgcompanion.app.data.smallPrintAgrees
import com.mtgcompanion.app.data.sharpness
import com.mtgcompanion.app.data.looksLikeAnotherCard
import com.mtgcompanion.app.data.choosePrinting
import com.mtgcompanion.app.data.cardBySight
import com.mtgcompanion.app.data.SetDecision
import com.mtgcompanion.app.data.decideInSet
import com.mtgcompanion.app.data.regularInSet
import com.mtgcompanion.app.data.parseSetCode
import kotlinx.coroutines.flow.update
import com.mtgcompanion.app.data.SettingsRepository
import com.mtgcompanion.app.data.ScanMode
import java.util.concurrent.Executors
import com.mtgcompanion.app.data.sensorBox
import com.mtgcompanion.app.data.relativeTo
import com.mtgcompanion.app.data.clampedTo
import com.mtgcompanion.app.data.ScanInFlight
import kotlinx.coroutines.channels.Channel
import android.util.Log
import android.os.SystemClock
import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.mtgcompanion.app.data.ScanBox
import com.mtgcompanion.app.data.ScanPile
import com.mtgcompanion.app.data.UNSORTED_COLLECTION_NAME
import com.mtgcompanion.app.data.UNSORTED_COLLECTION_ID
import com.mtgcompanion.app.data.grouped
import com.mtgcompanion.app.data.ScanGroup
import com.mtgcompanion.app.data.GUIDE_WIDTH
import com.mtgcompanion.app.data.SMALL_PRINT_SHARE
import com.mtgcompanion.app.data.GUIDE_GIVE_UP_FRAMES
import com.mtgcompanion.app.data.GUIDE_HEIGHT
import com.mtgcompanion.app.data.guideInImage
import com.mtgcompanion.app.data.GUIDE_SLACK
import com.mtgcompanion.app.data.confirmRead
import com.mtgcompanion.app.data.scanCacheKey
import com.mtgcompanion.app.data.STEADY_READS
import com.mtgcompanion.app.data.parseSetAndNumber
import com.mtgcompanion.app.data.Confirmation
import com.mtgcompanion.app.data.scannedTwiceOver
import com.mtgcompanion.app.data.copyNumber
import com.mtgcompanion.app.data.ScanRow
import com.mtgcompanion.app.data.CheckScan
import com.mtgcompanion.app.data.CheckScope
import com.mtgcompanion.app.data.CheckSessions
import com.mtgcompanion.app.data.PutAwayResult
import com.mtgcompanion.app.data.reconcile
import com.mtgcompanion.app.data.PutAwayStep
import com.mtgcompanion.app.data.Spot
import com.mtgcompanion.app.data.addedHere
import com.mtgcompanion.app.data.cardFactsOf
import com.mtgcompanion.app.data.placesOf
import com.mtgcompanion.app.data.pocketLabel
import com.mtgcompanion.app.data.suggestSpot
import com.mtgcompanion.app.data.undoPutAway
import com.mtgcompanion.app.data.putAway as putAwayInto
import com.mtgcompanion.app.data.PullProgress
import com.mtgcompanion.app.data.PullSource
import com.mtgcompanion.app.data.placeIdFromLabel
import com.mtgcompanion.app.data.pullList
import com.mtgcompanion.app.data.pullRowToTick
import com.mtgcompanion.app.data.pulledCopies
import com.mtgcompanion.app.data.putBackList
import com.mtgcompanion.app.data.putBackRowToTick
import com.mtgcompanion.app.data.sameCardName
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
import com.mtgcompanion.app.data.CardIndexRepository
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.scryfall.canBeFoil
import com.mtgcompanion.app.ui.common.AddToOps
import com.mtgcompanion.app.ui.common.AddToPick
import com.mtgcompanion.app.ui.common.SourceKind
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

/**
 * How long Accurate scanning keeps reading the small print, on fresh frames, once its first go
 * hasn't made it out — glare or blur on the bottom edge often clears a moment later. The card must
 * still be in view; a card whose small print reads straight away isn't held up at all.
 */
private const val SMALL_PRINT_PATIENCE_MS = 1000L

/** Frames in a row the title has to fail to read before the card is looked for by sight instead. */
private const val SIGHT_AFTER_BLANK = 2

/** At most one look by sight this often while the title won't read — each is a run of the model. */
private const val SIGHT_EVERY_MS = 400L

/**
 * Blank frames in a row, read guide-only, before one whole frame is read to check the guide is still
 * where the card is (see [ScanViewModel.onFrame]).
 */
private const val PROBE_EVERY = 10

/** How many frames the card is looked at before the crispest is kept. */
private const val SHARPEST_OF_FRAMES = 4

/** And how long that may take. A card in the hand is not still for long. */
private const val SHARPEST_OF_MS = 450L

/** Whole-frame checks in a row finding text only outside the guide before guide-only reading stops. */
private const val PROBES_TO_GIVE_UP = 3

/**
 * The framing guide cut out of a frame: what the reader reads instead of the whole picture, and —
 * if a card is confirmed from it — the picture its small print and art are read from. [guide] is
 * the drawn guide within [picture] once it's upright; [picture] itself has the guide's slack around it.
 */
private class GuideCut(val input: InputImage, val picture: Bitmap, val guide: ScanBox)

/**
 * A card the camera has confirmed, waiting for its lookup: what was read, and — when the set code
 * wasn't — a copy of the picture, so the small print and the art can still be read once the camera
 * has moved on to the next card.
 */
private class PendingScan(
    val candidate: String,
    val normalized: String,
    val forced: Boolean,
    val fromFrame: Pair<String, String>?,
    /** Known by sight, its title unread: nothing read to hold the card up against. */
    val seenBySight: Boolean,
    /** The set code alone, when the frame showed it but not the number beside it. */
    val frameSet: String?,
    val picture: Bitmap?,
    val rotation: Int,
    val guide: ScanBox?,
    val token: Long,
    val queuedAt: Long,
    /** Which ScanCapture attempt this scan is, so its record gets this scan's verdict. */
    val captureId: Int
)

/** The list scan-to-tick mode ticks: [deckId]'s pull list, or (when not [pull]) its put-back list. */
data class TickList(val deckId: String, val pull: Boolean)

/** One card put away this session (put-away mode): what happened to it, and how to take it back. */
data class PutAwayRow(
    val id: Long,
    val card: ScryfallCard,
    /** Where it goes: "Red › around “L”", "Page 3, slot 6". */
    val hint: String?,
    /** The section or pocket, short, for the list. */
    val where: String,
    val spot: Spot,
    val result: PutAwayResult,
    val label: String,
    val step: PutAwayStep?
)

data class ScanUiState(
    val status: String? = null,
    val scannedCards: List<ScanRow> = emptyList(),
    /** Bumped on every successful add — a one-shot event distinct from [status] (which is also
     * used for non-success messages like a failed lookup) so the UI can trigger a haptic/visual
     * flash only on real successes, via a LaunchedEffect keyed on this value. */
    val successToken: Int = 0,
    /** How careful the scanner is being — see ScanMode. */
    val scanMode: ScanMode = ScanMode.ACCURATE
)

class ScanViewModel(
    private val appContext: Context,
    private val cardRepository: CardRepository = CardRepository(),
    private val collectionRepository: CollectionRepository,
    private val deckRepository: DeckRepository,
    private val cardIndexRepository: CardIndexRepository,
    private val settingsRepository: SettingsRepository,
    /** Put-away mode: each card scanned is put away into this storage place at once (see StoragePlaces.kt). */
    putAwayPlaceId: String? = null,
    /** Scan-to-tick mode: each card scanned ticks its row on this deck's pull list or put-back list (PullList.kt). */
    tickList: TickList? = null,
    /** Check mode: each card scanned is matched against what's listed in this storage place (PlaceCheck.kt). */
    checkPlaceId: String? = null
) : ViewModel() {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** Debug builds keep what each scan saw, so a wrong answer can be explained. See ScanCapture. */
    private val capture = ScanCapture(appContext)

    /** Whether the last finished lookup was sure of its printing, for the capture record. */
    private var sightWasCertain: Boolean? = null

    // The small print gets a reader of its own, on a thread of its own. On one shared reader the
    // camera's frames and the small print queued behind each other — and the small print is the
    // slowest read in a scan, so the camera stalled on it and it waited on the camera.
    private val stripThread = Executors.newSingleThreadExecutor()
    private val stripReader = TextRecognition.getClient(
        TextRecognizerOptions.Builder().setExecutor(stripThread).build()
    )

    // Gates one frame's reading at a time; combined with ImageAnalysis's
    // STRATEGY_KEEP_ONLY_LATEST (which withholds the next frame until this one's
    // ImageProxy is closed), this throttles scanning to one frame per read. The lookup is no
    // longer part of that: a confirmed card goes to [lookups] and the camera moves straight on.
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

    // The card confirmed but not yet looked up, so it isn't looked up twice while it sits in view.
    private val inFlight = ScanInFlight()

    // Confirmed cards waiting for their lookup. One at a time, in the order they were scanned: the
    // list keeps scan order, and Scryfall is asked no faster than before — only the camera stops
    // waiting on it.
    private val lookups = Channel<PendingScan>(Channel.UNLIMITED)


    /** Switches between Fast and Accurate scanning; kept for next time. */
    fun setScanMode(mode: ScanMode) {
        viewModelScope.launch { settingsRepository.setScanMode(mode) }
    }

    // When the frame being read reached the reader, for the timings in the log.
    private var frameStartedAt = 0L

    // Reading just the guide (see onFrame). Frames in a row with no card name read that way; and
    // whole-frame checks in a row that found text only outside the guide — and whether that's
    // happened often enough that the guide is set aside and whole frames are read again.
    @Volatile private var guideBlankStreak = 0
    /** A read whose lookup came back as something else: from then on, nothing in view (see accept). */
    @Volatile private var rejectedRead: String? = null
    /** Asked by a lookup for the camera's next frame (see nextFrame); handed over by onFrame. */
    @Volatile private var frameWanted: CompletableDeferred<Pair<Bitmap, Int>?>? = null
    private var lastSightAt = 0L
    private var probeOutsideStreak = 0
    @Volatile private var guideOff = false

    // The preview's size in pixels, set by the screen: with it, the framing guide can be placed in
    // the camera's own picture, and text outside it (the next card along) left unread.
    private var previewWidth = 0
    private var previewHeight = 0
    // Frames in a row with text read, none of it inside the guide (see GUIDE_GIVE_UP_FRAMES).
    private var outsideGuideStreak = 0
    // The picture the frame being read came from, for a second look at the card's small print.
    private var currentFrame: (() -> Bitmap?)? = null

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

    private val _uiState = MutableStateFlow(ScanUiState(scannedCards = ScanPile.read()))
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    // The lookup queue's worker, and Fast or Accurate as last chosen. Both come after _uiState, which
    // they write to: the saved choice often arrives the moment it's asked for, and a coroutine that
    // starts running before the state exists crashes the scanner.
    init {
        // Knowing cards by sight needs its data on the phone: fetched the first time the scanner
        // opens (~26 MB), and until it's here the art is matched the old way, online. After that
        // it's checked every few days for new sets.
        cardIndexRepository.refresh()
        viewModelScope.launch { for (scan in lookups) lookUp(scan) }
        viewModelScope.launch {
            settingsRepository.scanMode.collect { mode -> _uiState.update { it.copy(scanMode = mode) } }
        }
    }

    val decks: StateFlow<List<Deck>> = deckRepository.decksFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val collections: StateFlow<List<Collection>> = collectionRepository.collectionsFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    /** Called for each analyzed camera frame; [onProcessed] must always run so the frame is released. */
    fun onFrame(image: InputImage, frame: (() -> Bitmap?)? = null, onProcessed: () -> Unit) {
        currentFrame = frame
        // A lookup wants a fresh look: copied now, while the frame is still open.
        frameWanted?.let { want ->
            frameWanted = null
            want.complete(frame?.let { runCatching { it() }.getOrNull() }?.let { it to image.rotationDegrees })
        }
        if (!busy.compareAndSet(false, true)) {
            onProcessed()
            return
        }
        frameStartedAt = SystemClock.elapsedRealtime()
        // Only the guide is read: text outside it is thrown away anyway, and reading a third to half
        // the pixels is that much quicker. Not for Scan now, which reads the whole frame when there's
        // nothing in the guide, and not once the guide has been set aside. And now and then, after a
        // run of blank frames, one whole frame is read to check the card isn't sitting outside the
        // guide on a phone that places it wrong — the same safety net as before, checked less often.
        val wholeFrame = frame == null || guideOff || forceScanNext.get() || guideBlankStreak >= PROBE_EVERY
        val probe = frame != null && !guideOff && guideBlankStreak >= PROBE_EVERY
        if (probe) guideBlankStreak = 0
        val cut = if (wholeFrame) null else guideCut(image, frame!!)
        recognizer.process(cut?.input ?: image)
            .addOnSuccessListener { visionText -> handleRecognizedText(visionText, image, onProcessed, cut, probe) }
            .addOnFailureListener {
                busy.set(false)
                onProcessed()
            }
    }

    private fun handleRecognizedText(
        visionText: Text,
        image: InputImage,
        onProcessed: () -> Unit,
        cut: GuideCut? = null,
        probe: Boolean = false
    ) {
        timing(if (cut != null) "read guide" else "read frame", frameStartedAt)
        val forced = forceScanNext.getAndSet(false)
        // Only text inside the framing guide is the card being scanned; the rest is whatever else is
        // on the table. Tapping "Scan now" with nothing in the guide reads the whole frame instead.
        // When only the guide was read, everything read is in it.
        val all = visionText.textBlocks.flatMap { it.lines }
        val inGuide = if (cut != null) all else linesInGuide(visionText, image, previewWidth, previewHeight)
        if (probe) {
            probeOutsideStreak = if (all.isNotEmpty() && inGuide.isEmpty()) probeOutsideStreak + 1 else 0
            if (probeOutsideStreak >= PROBES_TO_GIVE_UP) guideOff = true
        }
        // Safety net: if text keeps being read but never inside the guide — a phone whose reader
        // measures its picture differently — the guide is set aside rather than scanning nothing.
        outsideGuideStreak = if (all.isNotEmpty() && inGuide.isEmpty()) outsideGuideStreak + 1 else 0
        val ignoreGuide = forced || outsideGuideStreak >= GUIDE_GIVE_UP_FRAMES
        val lines = if (inGuide.isEmpty() && ignoreGuide) all else inGuide
        // A read already turned down is nothing in view: an empty table's grain can read the same
        // word frame after frame, and counted as a title it would hide the card leaving — and a
        // second copy of it coming back would never be added.
        val candidate = extractCardName(lines)?.takeUnless { read -> !forced && rejectedRead?.let { looksLikeSameCard(read, it) } == true }
        if (cut != null) guideBlankStreak = if (candidate == null) guideBlankStreak + 1 else 0
        if (candidate == null) {
            // A title that won't read — busy borderless art, glare, a foreign-language card, a torn
            // corner — needn't stop the card: once the card index is here, the whole card is looked
            // up by sight, and a clear match counts as the read (see lookBySight).
            val recognizer = cardIndexRepository.recognizer()
            val now = SystemClock.elapsedRealtime()
            if (cut != null && recognizer != null && !forced && guideBlankStreak >= SIGHT_AFTER_BLANK && now - lastSightAt >= SIGHT_EVERY_MS) {
                lastSightAt = now
                lookBySight(recognizer, image, onProcessed, cut)
                return
            }
            noTitle(onProcessed)
            return
        }
        blankFrameStreak = 0
        proceed(candidate, lines, image, onProcessed, cut, forced, seenBySight = false)
    }

    /**
     * No title in this frame. Only treated as "the card actually left" after a real streak of blank
     * frames — see blankFrameStreak's doc comment above.
     */
    private fun noTitle(onProcessed: () -> Unit) {
        if (++blankFrameStreak >= BLANK_FRAMES_TO_RESET) {
            steadyReads = 0
            lastCandidate = null
            lastLookedUp = null
            lastAddedCard = null
            inFlight.cardLeft()
        }
        busy.set(false)
        onProcessed()
    }

    /**
     * The card in the guide, its title unread, looked up by sight: found by its edges, run through
     * the model, and taken as the read only when it's clearly one card (see cardBySight). The camera
     * waits meanwhile — the same frame's picture is being looked at.
     */
    private fun lookBySight(recognizer: CardRecognizer, image: InputImage, onProcessed: () -> Unit, cut: GuideCut) {
        viewModelScope.launch {
            val started = SystemClock.elapsedRealtime()
            val sight = withContext(Dispatchers.Default) {
                runCatching {
                    uprightFrame(cut.picture, image.rotationDegrees)?.let { FlatCard.find(it, cut.guide) }
                        ?.let { cardBySight(recognizer.recognize(it).anywhere) }
                }.getOrNull()
            }
            if (sight == null) {
                noTitle(onProcessed)
                return@launch
            }
            timing("title unread; by sight: ${sight.name} ${sight.set} #${sight.number}", started)
            blankFrameStreak = 0
            proceed(sight.name, emptyList(), image, onProcessed, cut, forced = false, seenBySight = true)
        }
    }

    /**
     * A title read — or a card known by sight ([seenBySight]) — on its way to a lookup: once it's
     * read the same on enough frames running, and isn't the card just added still in view.
     */
    private fun proceed(
        candidate: String,
        lines: List<Text.Line>,
        image: InputImage,
        onProcessed: () -> Unit,
        cut: GuideCut?,
        forced: Boolean,
        seenBySight: Boolean
    ) {

        // Still the same physical card sitting in frame, even if this frame's OCR came out
        // slightly different from the exact string we last looked up — don't re-add it. A forced
        // (manual capture) scan skips this: the user tapping the button IS the "yes, really"
        // confirmation, and re-scanning the same card on purpose is how you bump its count.
        // The card still waiting on its lookup counts too: the camera no longer waits for it, so
        // it's still in view while its lookup is out.
        if (!forced) {
            val added = lastAddedCard?.let { looksLikeSameCard(candidate, it.name) } == true
            if (added || inFlight.isOnItsWay(candidate, ::looksLikeSameCard)) {
                lastCandidate = candidate.lowercase()
                busy.set(false)
                onProcessed()
                return
            }
        }
        val normalized = candidate.lowercase()
        // Require the same title on STEADY_READS frames in a row before spending a lookup — a card
        // halfway into the frame, or caught mid-motion, rarely reads the same three times running —
        // and don't re-look-up a title still in frame. A forced scan accepts whatever's in frame
        // right now instead of waiting.
        steadyReads = if (normalized == lastCandidate) steadyReads + 1 else 1
        val stable = forced || steadyReads >= _uiState.value.scanMode.steadyReads
        lastCandidate = normalized
        if (!stable || (!forced && normalized == lastLookedUp)) {
            busy.set(false)
            onProcessed()
            return
        }
        lastLookedUp = normalized

        // The set code + collector number name the exact printing. The camera's own pass sometimes
        // reads them; when it doesn't, a copy of the picture goes with the card, for a closer look
        // at the small print and for matching the art — both done in the lookup queue, off the
        // camera.
        val fromFrame = extractSetAndNumber(lines)
        val frameSet = if (fromFrame != null) null else parseSetCode(lines.map { it.text })
        val grabFrame: (() -> Bitmap?)? = if (cut != null) ({ cut.picture }) else currentFrame
        val rotation = image.rotationDegrees
        val guide = cut?.guide ?: guideInImage(
            if (rotation == 90 || rotation == 270) image.height else image.width,
            if (rotation == 90 || rotation == 270) image.width else image.height,
            previewWidth, previewHeight, GUIDE_WIDTH, GUIDE_HEIGHT
        )
        val token = inFlight.start(normalized)
        // Only now, once this really is going to be looked up. Beginning a record on every frame
        // that read a title logs a hundred abandoned attempts and, far worse, lets a verdict land on
        // a later frame's picture — which invents failures that never happened.
        val captureId = capture.begin(candidate, seenBySight)
        // The guide cut when there was one, otherwise the whole frame — a scan with the guide
        // set aside is exactly the kind that goes wrong, so it must not be the one with no picture.
        capture.frame(captureId, cut?.picture ?: runCatching { currentFrame?.invoke() }.getOrNull())

        viewModelScope.launch {
            // The copy is the only part that needs the frame itself; once it's taken the camera is
            // handed back, and the card waits its turn for a lookup while the next one is read.
            val grabbed = SystemClock.elapsedRealtime()
            // Only the close read of the small print and the art match look at the picture; Fast
            // scanning does neither, so it doesn't take the copy.
            val mode = _uiState.value.scanMode
            // A picture whenever the look is going to be used — including when the set and number
            // were read in the frame itself. Skipping it there meant the card index, the whole point
            // of the rebuild, never ran on the scans that felt most confident: the small print was
            // trusted outright, and nothing could catch it being misread.
            val needsPicture = mode.matchesArt || (fromFrame == null && mode.readsSmallPrint)
            val picture = try {
                if (!needsPicture) null else withContext(Dispatchers.Default) { grabFrame?.invoke() }
            } finally {
                busy.set(false)
                onProcessed()
            }
            if (picture != null) timing("copy picture", grabbed)
            lookups.send(
                PendingScan(candidate, normalized, forced, fromFrame, seenBySight, frameSet, picture, rotation, guide, token, SystemClock.elapsedRealtime(), captureId)
            )
        }
    }

    /**
     * One confirmed card, looked up: the small print read closer if the camera's pass missed it,
     * the card fetched (from this session's cache when it's been seen), checked against what was
     * read, and added. Runs in [lookups], one card at a time.
     */
    private suspend fun lookUp(scan: PendingScan) {
        timing("waited for lookup", scan.queuedAt)
        // Turned upright once and shared: the small print and the art both read it.
        // The camera's own frame. A deliberate, focused still was tried here instead and measured
        // worse: the flattened card came out at 874 against the frame's 1350 (the index's own
        // pictures are 2654), because a full-sensor photograph puts the same card in far more
        // pixels and then loses the detail on the way down to 224x224 — and quality mode's noise
        // reduction smooths exactly the fine print the model reads. It also cost 1.5 s a card.
        val picture = scan.picture?.let { withContext(Dispatchers.Default) { uprightFrame(it, scan.rotation) } }
        val guide = scan.guide
        picture?.let { capture.fact(scan.captureId, "sharpness", scoreOf(it).toInt()) }

        // The card itself, found by its edges and flattened: both the small print and the look
        // are then read off exactly the card, however it was held in the guide.
        var started = SystemClock.elapsedRealtime()
        val flat = picture?.let { withContext(Dispatchers.Default) { runCatching { FlatCard.find(it, guide) }.getOrNull() } }
        if (picture != null) timing("card edges ${if (flat != null) "found" else "not found"}", started)
        // How big the card actually landed, and what that leaves the set line's letters. Whether the
        // small print can be read at all is decided here and nowhere else — the strip is cut from
        // the flattened card, so its placement is exact and only the pixels underneath are in doubt.
        // Recorded rather than reasoned about, because it depends on the preview's shape per phone.
        flat?.quads?.firstOrNull()?.let { q ->
            val letterPx = q.height * SMALL_PRINT_SHARE
            capture.fact(scan.captureId, "cardPx", q.height.toInt())
            capture.fact(scan.captureId, "letterPx", String.format("%.1f", letterPx))
            Log.d("ScanTiming", "card ${q.width.toInt()}x${q.height.toInt()} px, set line about ${"%.1f".format(letterPx)} px tall")
        }

        started = SystemClock.elapsedRealtime()
        // Fast scanning skips the close read: the printing comes from the frame, or from the art.
        val readsSmallPrint = _uiState.value.scanMode.readsSmallPrint
        val strip = if (scan.fromFrame == null && readsSmallPrint) picture?.let { readSmallPrint(it, guide, flat, scan.captureId) } else null
        var printing = scan.fromFrame ?: strip?.let { parseSetAndNumber(it) }
        // When the number wouldn't read, the set code on its own still narrows the printings to
        // that set's few, for the look to choose between (see matchArt).
        var setCode = if (printing != null) null else strip?.let { parseSetCode(it) } ?: scan.frameSet
        // Still not read: a few more goes on fresh frames, for as long as the card is in view.
        if (printing == null && readsSmallPrint && scan.fromFrame == null && picture != null) {
            val until = SystemClock.elapsedRealtime() + SMALL_PRINT_PATIENCE_MS
            var tries = 0
            while (printing == null && SystemClock.elapsedRealtime() < until && inFlight.stillInView(scan.token)) {
                val (fresh, rotation) = nextFrame(until - SystemClock.elapsedRealtime()) ?: break
                val upright = withContext(Dispatchers.Default) { uprightFrame(fresh, rotation) } ?: break
                val guide = guideInImage(upright.width, upright.height, previewWidth, previewHeight, GUIDE_WIDTH, GUIDE_HEIGHT)
                val again = withContext(Dispatchers.Default) { runCatching { FlatCard.find(upright, guide) }.getOrNull() }
                    ?: break // the card has left the guide
                val lines = readSmallPrint(upright, guide, again, scan.captureId)
                printing = lines?.let { parseSetAndNumber(it) }
                if (setCode == null) setCode = lines?.let { parseSetCode(it) }
                tries++
            }
            if (printing != null) setCode = null
            Log.d("ScanTiming", "small print ${if (printing != null) "read on retry $tries" else "still unread after $tries more"}")
        }
        // What it read, as well as how long it took: a quicker read is no use if it reads less.
        if (scan.fromFrame == null && picture != null && readsSmallPrint) timing("small print ${printing?.let { "read ${it.first} #${it.second}" } ?: setCode?.let { "read set $it only" } ?: "not read"}", started)
        else if (scan.fromFrame != null) Log.d("ScanTiming", "small print read in the frame itself: ${scan.fromFrame.first} #${scan.fromFrame.second}")
        // Keyed on the title as well as the printing. On the printing alone, a misread collector
        // number hands back whichever card was scanned under that number earlier — a different card
        // entirely, with the title never consulted. That is exactly how a scan of Surveillance
        // Phantasm came back as Cryotheory Adept, "certain", in 33 ms.
        val cacheKey = scanCacheKey(scan.normalized, printing?.first, printing?.second)

        // Which printing it is, when the small print didn't say: by sight, from the card index on
        // the phone (see sightPrinting) — or, until that's downloaded or when the card's edges
        // weren't found, from what the card looked like compared with every printing online (see
        // matchArt). Fast scanning does neither and leaves the card as its usual printing.
        // With the card index, the look also checks a printing the small print named.
        val recognizer = if (_uiState.value.scanMode.matchesArt && flat != null) cardIndexRepository.recognizer() else null
        val matchesArt = printing == null && _uiState.value.scanMode.matchesArt
        started = SystemClock.elapsedRealtime()
        val look = if (!matchesArt || recognizer != null) null else picture?.let {
            withContext(Dispatchers.Default) { flat?.signatures() ?: cameraSignatures(it, 0, guide) }
        }?.ifEmpty { null }
        if (look != null) timing("art signature", started)

        var added: ScryfallCard? = null
        try {
            // Belt and braces: a cached card that doesn't answer to what was read is not the card,
            // whatever the key said. A forced scan skips this check in accept(), so the cache cannot
            // be the only thing standing between a misread and a confident wrong answer.
            val cached = nameCache[cacheKey]?.takeIf {
                confirmRead(scan.candidate, it.name, it.flavorName) == Confirmation.YES
            }
            started = SystemClock.elapsedRealtime()
            val named = cached ?: resolveCard(scan.candidate, printing)
            if (cached == null) timing("lookup", started)
            // The name is settled by now; by sight, which of its printings is in hand — or whether
            // the printing the small print named is borne out.
            val printed = printing != null && named.set.equals(printing.first, ignoreCase = true)
            val sight = if (recognizer != null && flat != null) sightPrinting(recognizer, flat, named, setCode, printed, scan.captureId) else null
            val card = sight?.card ?: named
            val exact = if (sight != null) sight.certain else printed
            sightWasCertain = exact
            // A card known by sight needs no reading to account for it: its look already did.
            accept(scan.candidate, card, scan.forced || scan.seenBySight, exact = exact)?.let { row ->
                nameCache[cacheKey] = named
                added = card
                if (recognizer == null) matchArt(row, card, look, setCode)
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(status = "Didn't recognize \"${scan.candidate}\" — keep scanning…")
        } finally {
            val card = added
            if (card == null) capture.finish(scan.captureId, "not added")
            else capture.finish(scan.captureId, "added", card.name, card.set, card.collectorNumber, sightWasCertain)
            // The tester app's scanner readout: what was read, what it became, and how long it took.
            if (Tester.on) Tester.scanDone(
                ScanOutcome(
                    captureId = scan.captureId,
                    titleRead = scan.candidate,
                    name = card?.name, set = card?.set, number = card?.collectorNumber,
                    certain = card != null && sightWasCertain == true,
                    how = when {
                        scan.fromFrame != null -> "printing read in the frame"
                        printing != null -> "printing read off the small print"
                        scan.seenBySight -> "known by sight"
                        setCode != null -> "set code only"
                        else -> "name only"
                    },
                    tookMs = SystemClock.elapsedRealtime() - scan.queuedAt
                )
            )
            // Remembered as the card in view only if it hasn't left since it was confirmed; if it
            // has, the next card in is a new one — even another copy of this card.
            if (inFlight.finished(scan.token, scan.normalized)) added?.let { lastAddedCard = it }
        }
    }

    /**
     * Which printing of [named] the flattened card is, by sight — the card index on the phone, no
     * fetching every printing to compare — and whether that's certain (see printingBySight). The set
     * code narrows it when it was read (see choosePrinting). Null when the look can't tell, and the
     * card stays as it was looked up. If the look plainly says it's another card altogether, that's
     * said, and the row is left as a best guess for a tap to fix.
     *
     * When [named] came from the small print ([printed]), the look checks it instead: a set code and
     * number misread as another real printing of the same card would otherwise go in as certain. It's
     * kept (null) when the look bears it out, and overruled by the look when it doesn't.
     */
    private suspend fun sightPrinting(recognizer: CardRecognizer, flat: FlatCard, named: ScryfallCard, setCode: String?, printed: Boolean, captureId: Int): SightResult? {
        val started = SystemClock.elapsedRealtime()
        val seen = withContext(Dispatchers.Default) {
            runCatching { recognizer.recognize(flat, named.name, setCode, if (printed) named.id else null) }.getOrNull()
        } ?: return null
        timing("by sight", started)
        capture.flat(captureId, flat.lookBitmap())
        capture.sight(captureId, named.name, setCode, printed, seen.anywhere, seen.named, seen.inSet, seen.printing)
        looksLikeAnotherCard(named.name, seen.named, seen.anywhere)?.let { other ->
            _uiState.value = _uiState.value.copy(status = "Read \"${named.name}\", but it looks like ${other.name} — tap the row to check.")
            return null
        }
        val overruled = printed && !smallPrintAgrees(seen.printing, seen.named)
        if (printed && !overruled) return null
        if (overruled) {
            Log.d("ScanTiming", "small print said ${named.set} #${named.collectorNumber}, but it doesn't look like it — going by sight")
            capture.note(captureId, "small print said ${named.set} #${named.collectorNumber}; overruled by the look")
        }
        // Once the small print is overruled, the look's best is the best there is, sure or not.
        val pick = choosePrinting(seen.named, if (overruled) emptyList() else seen.inSet)
            ?: seen.named.firstOrNull()?.takeIf { overruled }?.let { SightPick(it.entry, certain = false) }
            ?: return null
        if (pick.entry.id == named.id) return SightResult(named, pick.certain)
        val card = printingById[pick.entry.id]
            ?: runCatching { cardRepository.getCardsByIds(listOf(pick.entry.id)).firstOrNull() }.getOrNull()?.also { printingById[pick.entry.id] = it }
            ?: return null
        return SightResult(card, pick.certain)
    }

    private suspend fun scoreOf(bitmap: Bitmap): Float = withContext(Dispatchers.Default) {
        runCatching {
            val px = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(px, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            sharpness(px, bitmap.width, bitmap.height)
        }.getOrDefault(0f)
    }

    /** A printing decided by sight, and whether it's certain. */
    private class SightResult(val card: ScryfallCard, val certain: Boolean)

    /** Printings fetched by id this session, for a card seen by sight more than once. */
    private val printingById = mutableMapOf<String, ScryfallCard>()

    /**
     * The guide, with its slack, cut out of the camera's picture before it's turned upright — so
     * neither the reading nor the turning deals with the rest of the frame. The camera hands over a
     * sideways picture, so the upright guide is first found in the sensor's (see sensorBox). Null
     * when there's no guide to cut yet (the preview hasn't been measured) or the picture can't be had;
     * the whole frame is read instead.
     */
    private fun guideCut(image: InputImage, frame: () -> Bitmap?): GuideCut? {
        val rotation = image.rotationDegrees
        val sideways = rotation == 90 || rotation == 270
        val width = if (sideways) image.height else image.width
        val height = if (sideways) image.width else image.height
        val guide = guideInImage(width, height, previewWidth, previewHeight, GUIDE_WIDTH, GUIDE_HEIGHT) ?: return null
        val area = guide.grownBy(GUIDE_SLACK).clampedTo(width, height)
        if (area.right - area.left < 40 || area.bottom - area.top < 40) return null
        val started = SystemClock.elapsedRealtime()
        val picture = frame() ?: return null
        val s = sensorBox(area, rotation, picture.width, picture.height).clampedTo(picture.width, picture.height)
        val cut = runCatching { Bitmap.createBitmap(picture, s.left, s.top, s.right - s.left, s.bottom - s.top) }.getOrNull() ?: return null
        timing("cut guide", started)
        return GuideCut(InputImage.fromBitmap(cut, rotation), cut, guide.relativeTo(area))
    }

    /** How long a step of a scan took, logged under "ScanTiming" — for finding what's slow. */
    private fun timing(step: String, since: Long) {
        Log.d("ScanTiming", "$step: ${SystemClock.elapsedRealtime() - since} ms")
    }

    /**
     * The camera's next frame, copied as it comes in (see onFrame), with how far to turn it upright —
     * or null if none comes within [timeoutMs].
     */
    private suspend fun nextFrame(timeoutMs: Long): Pair<Bitmap, Int>? {
        if (timeoutMs <= 0) return null
        val want = CompletableDeferred<Pair<Bitmap, Int>?>()
        frameWanted = want
        return withTimeoutOrNull(timeoutMs) { want.await() }.also { if (frameWanted === want) frameWanted = null }
    }

    /**
     * A second look at the card's small print, blown up: the set code and collector number say
     * which printing is in your hand — the alternate art, the borderless one — where the name alone
     * only gets the usual printing. The lines it read, for parseSetAndNumber and parseSetCode; null
     * when nothing could be read.
     */
    private suspend fun readSmallPrint(upright: Bitmap, guide: ScanBox?, flat: FlatCard?, captureId: Int): List<String>? {
        // Off the flattened card first. Should its edges have been found wrong, the strip of the
        // guide is read as well, so finding them never reads less than before.
        val strip1 = flat?.let { card -> withContext(Dispatchers.Default) { card.smallPrintStrip() } }
        val fromCard = strip1?.let { readStrip(it) }
        capture.smallPrint(captureId, strip1, fromCard?.joinToString(" | "))
        if (fromCard != null && parseSetCode(fromCard) != null) return fromCard
        val strip = withContext(Dispatchers.Default) { smallPrintStrip(upright, 0, guide) } ?: return fromCard
        return (fromCard.orEmpty() + readStrip(strip).orEmpty()).ifEmpty { null }
    }

    /** The lines of text the small-print reader makes out in [strip]; null when it fails. */
    private suspend fun readStrip(strip: Bitmap): List<String>? {
        val text = runCatching {
            suspendCancellableCoroutine { cont ->
                stripReader.process(InputImage.fromBitmap(strip, 0))
                    .addOnSuccessListener { cont.resume(it) {} }
                    .addOnFailureListener { cont.resume(null) {} }
            }
        }.getOrNull() ?: return null
        return text.textBlocks.flatMap { it.lines }.map { it.text }
    }

    /**
     * Works out which printing was really in the frame from what the card looked like, and corrects
     * the row without being asked. This runs behind the scan rather than in front of it: fetching a
     * card's printings and their pictures takes a moment, and nobody should have to hold a card
     * still while it happens. The row is left alone if it's been deleted, or its printing already
     * picked by hand, since the scan.
     *
     * When the small print gave the set code but not the number, [setCode] narrows it first: the
     * name says which card, the set code which of its printings are in play, and the whole card's
     * look — framed, full art, borderless — which of that set's few it is. If that set turns up
     * nothing that looks like the card, the set code was misread, and every printing is compared
     * as if it had never been read.
     */
    private fun matchArt(rowId: Long, scanned: ScryfallCard, look: List<FloatArray>?, setCode: String? = null) {
        if (look == null) return
        viewModelScope.launch {
            if (setCode != null) {
                val started = SystemClock.elapsedRealtime()
                val inSet = runCatching { decideFromSet(scanned.name, setCode, look) }.getOrNull()
                timing("set $setCode ${if (inSet == null) "didn't match" else "matched"}", started)
                if (inSet != null) {
                    val (pick, only) = inSet
                    correctRow(rowId, scanned, pick, only, "matched the art in ${pick.setName ?: pick.set?.uppercase()}")
                    return@launch
                }
            }
            val printings = printingsByName[scanned.name]
                // Nothing came back — offline, most likely. Don't hold on to that as the answer.
                ?: runCatching { cardRepository.getPrintings(scanned.name) }.getOrDefault(emptyList())
                    .also { if (it.isNotEmpty()) printingsByName[scanned.name] = it }
            if (printings.size < 2) return@launch
            val found = runCatching { matchPrinting(appContext, look, printings) }.getOrNull() ?: return@launch
            correctRow(rowId, scanned, found.pick, found.only, "matched the art to ${found.pick.setName ?: found.pick.set?.uppercase()}")
        }
    }

    /**
     * Which of [name]'s printings in [setCode] the card looks like, and whether it's the only one
     * that does. The set's usual version, not sure, when its versions look too alike to tell; null
     * when none of them look like the card — or the set has none — so the set code was misread.
     */
    private suspend fun decideFromSet(name: String, setCode: String, look: List<FloatArray>): Pair<ScryfallCard, Boolean>? {
        // Every printing may already be here from an earlier copy; then the set's are among them.
        val inSet = printingsByName[name]?.filter { it.set.equals(setCode, ignoreCase = true) }
            ?: cardRepository.getPrintings(name, setCode)
        val regular = regularInSet(inSet) ?: return null
        return when (val decision = decideInSet(look, measurePrintings(appContext, inSet), regular)) {
            is SetDecision.Found -> decision.pick to decision.only
            is SetDecision.Unsure -> decision.regular to false
            SetDecision.Misread -> null
        }
    }

    /**
     * Row [rowId] switched to [pick] — unless it's been deleted, or its printing picked by hand, since
     * the scan. [only] says whether that's certain or a best guess.
     */
    private fun correctRow(rowId: Long, scanned: ScryfallCard, pick: ScryfallCard, only: Boolean, how: String) {
        val rows = _uiState.value.scannedCards
        if (rows.none { it.id == rowId && it.card.id == scanned.id && !it.exact }) return
        setScanned(
            rows.map { if (it.id == rowId) it.copy(card = pick, exact = only) else it },
            if (pick.id == scanned.id) null else "${scanned.name} — $how"
        )
    }

    /**
     * Adds [card] only if the read really named it. A fuzzy lookup answers half a title with a real
     * card, so a card that wasn't all in the frame would otherwise join the list as if it had been
     * scanned properly. Tapping "Scan now" ([forced]) says "yes, really" and skips the check.
     */
    private fun accept(candidate: String, card: ScryfallCard, forced: Boolean, exact: Boolean = false): Long? {
        val confirmation = if (forced) Confirmation.YES else confirmRead(candidate, card.name, card.flavorName)
        if (confirmation == Confirmation.YES) {
            return addScannedCard(card, exact)
        }
        // Nothing is added, and this reading isn't spent on another lookup; more of the card coming
        // into the frame reads differently, and that is looked up.
        steadyReads = 0
        rejectedRead = candidate
        _uiState.value = _uiState.value.copy(
            status = if (confirmation == Confirmation.PARTIAL) {
                "Only read \"$candidate\" — hold the whole card in the frame, its name in the gold strip."
            } else {
                "Read \"$candidate\", which looks like ${card.flavorName ?: card.name} — hold the card still and try again."
            }
        )
        return null
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

    // ---- Put-away mode ----

    private val _putAwayTarget = MutableStateFlow(putAwayPlaceId)
    /** The storage place cards are being put away into, or null when scanning into the pile. */
    val putAwayTarget: StateFlow<String?> = _putAwayTarget.asStateFlow()
    private val _session = MutableStateFlow<List<PutAwayRow>>(emptyList())
    /** The cards put away this session, newest first. */
    val session: StateFlow<List<PutAwayRow>> = _session.asStateFlow()

    fun setPutAwayTarget(placeId: String) {
        _putAwayTarget.value = placeId
        // Putting away is what the scanner does now, not ticking a list or checking a place.
        _tickList.value = null
        _check.value = null
    }

    // ---- Check mode ----

    private val _check = MutableStateFlow(checkPlaceId?.let { id ->
        CheckSessions.load(id) ?: CheckSessions.Session(id, null, emptyList()).also { CheckSessions.save(it) }
    })
    /**
     * The check going on (PlaceCheck.kt): the place, the section or null for all of it, and the cards
     * scanned so far — kept in CheckSessions too, so the results screen sees them. The web app's
     * ScanPage.tsx check mode.
     */
    val check: StateFlow<CheckSessions.Session?> = _check.asStateFlow()

    private fun setCheck(session: CheckSessions.Session) {
        CheckSessions.save(session)
        _check.value = session
    }

    /** Matches [card] against what's listed in the place, and says what it is: "belongs here", "should be in Blue"… */
    private fun checkCard(card: ScryfallCard, exact: Boolean, session: CheckSessions.Session): Long {
        val id = nextScanId++
        // The scanner can't see foil, so a scan matches plain or foil copies.
        val next = session.copy(scans = session.scans + CheckScan(card.id, card.name, card.displayImageUrl, null, exact))
        setCheck(next)
        val line = reconcile(collections.value, decks.value, CheckScope(next.placeId, next.section), next.scans).lines.lastOrNull()
        _uiState.update { it.copy(status = "${card.name} — ${line?.label ?: "scanned"}", successToken = it.successToken + 1) }
        scanSound.play(MediaActionSound.SHUTTER_CLICK)
        return id
    }

    /** Checks the whole place rather than one section, keeping what's been scanned. */
    fun checkWholePlace() {
        _check.value?.let { setCheck(it.copy(section = null)) }
    }

    /** Takes back the last card scanned in the check. */
    fun undoLastCheckScan() {
        val now = _check.value ?: return
        if (now.scans.isEmpty()) return
        setCheck(now.copy(scans = now.scans.dropLast(1)))
        _uiState.update { it.copy(status = "Last scan taken back") }
    }

    // ---- Scan-to-tick mode ----

    private val pullProgress = PullProgress(appContext)
    private val _tickList = MutableStateFlow(tickList)
    /** The list a scanned card ticks, or null when scanning does something else. */
    val tickList: StateFlow<TickList?> = _tickList.asStateFlow()
    private val _tickCount = MutableStateFlow<Pair<Int, Int>?>(null)
    /** Copies ticked of the list's, once one has been ticked here. */
    val tickCount: StateFlow<Pair<Int, Int>?> = _tickCount.asStateFlow()

    /** Ticks [card]'s row on the list: the first not ticked yet, as the list's own Scan to tick does. */
    private fun tickCard(card: ScryfallCard, list: TickList): Long {
        val id = nextScanId++
        val deck = decks.value.firstOrNull { it.id == list.deckId }
        if (deck == null) {
            _uiState.update { it.copy(status = "That deck isn't here any more") }
            return id
        }
        val status: String
        if (list.pull) {
            val rows = pullList(deck, collections.value, decks.value).groups.flatMap { it.rows }
            val ticked = pullProgress.ticked(PullProgress.ListKind.PULL, deck.id)
            val row = pullRowToTick(rows, ticked, card.name)
            status = when {
                row == null -> if (rows.any { sameCardName(it.name, card.name) }) "${card.name} — already ticked" else "${card.name} isn't on the list"
                row.source is PullSource.InDeck -> "${card.name} is only in another deck — tick it on the list to take it"
                else -> {
                    val now = pullProgress.tick(PullProgress.ListKind.PULL, deck.id, row.key)
                    _tickCount.value = pulledCopies(rows, now) to rows.filter { it.source != PullSource.Missing }.sumOf { it.qty }
                    "${card.name} — ticked (${row.where})"
                }
            }
        } else {
            val rows = putBackList(deck, collections.value, pullProgress.putBackMode(deck.id)).groups.flatMap { it.rows }
            val ticked = pullProgress.ticked(PullProgress.ListKind.PUT_BACK, deck.id)
            val row = putBackRowToTick(rows, ticked, card.name)
            status = if (row == null) {
                if (rows.any { sameCardName(it.name, card.name) }) "${card.name} — already ticked" else "${card.name} isn't on the list"
            } else {
                val now = pullProgress.tick(PullProgress.ListKind.PUT_BACK, deck.id, row.key)
                _tickCount.value = rows.filter { it.key in now }.sumOf { it.qty } to rows.sumOf { it.qty }
                "${card.name} — ticked"
            }
        }
        _uiState.update { it.copy(status = status, successToken = it.successToken + 1) }
        scanSound.play(MediaActionSound.SHUTTER_CLICK)
        return id
    }

    // ---- Box labels ----

    private val _labelPlace = MutableStateFlow<String?>(null)
    /** The place whose scanned label is showing its sheet; cards wait while it's up. */
    val labelPlace: StateFlow<String?> = _labelPlace.asStateFlow()
    private var labelClosedAt = 0L

    /**
     * A QR code the camera read: a box label (PlaceLabel.kt) shows its place's sheet, unless one is up
     * or was just closed (the label's still in view). A code that's only a place's id, as the first
     * labels held, counts only when it's one of the user's places. Answers whether it was a label.
     */
    fun onLabel(text: String): Boolean {
        val scan = placeIdFromLabel(text) ?: return false
        if (scan.bare && placesOf(collections.value).none { it.id == scan.id }) return false
        if (_labelPlace.value == null && System.currentTimeMillis() - labelClosedAt > 3_000) _labelPlace.value = scan.id
        return true
    }

    fun closeLabel() {
        _labelPlace.value = null
        labelClosedAt = System.currentTimeMillis()
    }

    /** A scanned card as a new binder entry, with no copies yet — as the scanner's Add to… makes it. */
    private fun newEntryOf(card: ScryfallCard) =
        CollectionEntry(card.id, card.name, card.displayImageUrl, backImageUrl = card.backImageUrl, tags = card.tags)

    /** Puts [card] away into [placeId] at once: given a place, moved here, or added here (putAway in StoragePlaces.kt). */
    private fun putAwayCard(card: ScryfallCard, placeId: String): Long {
        val id = nextScanId++
        viewModelScope.launch {
            var row: PutAwayRow? = null
            collectionRepository.changeStorage { collections ->
                val place = placesOf(collections).firstOrNull { it.id == placeId } ?: return@changeStorage collections
                val (spot, hint) = suggestSpot(place, cardFactsOf(card), collections)
                val outcome = putAwayInto(collections, card.id, card.name, spot, newEntryOf(card))
                val page = spot.page
                val slot = spot.slot
                val where = spot.section ?: if (page != null && slot != null) pocketLabel(page, slot) else place.name
                row = PutAwayRow(id, card, hint, where, spot, outcome.result, outcome.label, outcome.step)
                outcome.collections
            }
            val done = row ?: return@launch
            _session.update { listOf(done) + it }
            _uiState.update { it.copy(status = "${card.name} — ${done.label}", successToken = it.successToken + 1) }
        }
        scanSound.play(MediaActionSound.SHUTTER_CLICK)
        return id
    }

    /** A card that was already here is another copy after all: it's added, here. */
    fun anotherCopy(row: PutAwayRow) {
        viewModelScope.launch {
            var step: PutAwayStep? = null
            collectionRepository.changeStorage { collections ->
                val (next, added) = addedHere(collections, row.card.id, row.spot, newEntryOf(row.card))
                step = added
                next
            }
            val added = step ?: return@launch
            _session.update { listOf(row.copy(id = nextScanId++, result = PutAwayResult.NEW, label = "new to collection", step = added)) + it }
        }
    }

    /** Takes back the newest card of the session. */
    fun undoLastPutAway() {
        val last = _session.value.firstOrNull() ?: return
        _session.update { it.drop(1) }
        _uiState.update { it.copy(status = "${last.card.name} taken back") }
        val step = last.step ?: return
        viewModelScope.launch { collectionRepository.changeStorage { undoPutAway(it, step) } }
    }

    /** Every scan is its own row, newest first, so a card read twice shows twice. */
    private fun addScannedCard(card: ScryfallCard, exact: Boolean = false): Long {
        // While a box label's sheet is up, cards wait.
        if (_labelPlace.value != null) return nextScanId++
        _tickList.value?.let { return tickCard(card, it) }
        _putAwayTarget.value?.let { return putAwayCard(card, it) }
        _check.value?.let { return checkCard(card, exact, it) }
        val row = ScanRow(nextScanId++, card, System.currentTimeMillis(), exact)
        val rows = listOf(row) + _uiState.value.scannedCards
        val copy = copyNumber(rows, row)
        val status = if (copy > 1) {
            "${card.name} again — copy $copy" + if (scannedTwiceOver(rows, row)) ", scanned just now" else ""
        } else {
            "Added ${card.name}"
        }
        setScanned(rows, status)
        _uiState.value = _uiState.value.copy(successToken = _uiState.value.successToken + 1)
        scanSound.play(MediaActionSound.SHUTTER_CLICK)
        return row.id
    }

    private var nextScanId = ScanPile.nextId(_uiState.value.scannedCards)

    /**
     * Printings looked up this session, by card name. Scanning a pile of lands asks after the same
     * eight hundred Plains printings over and over otherwise, and Scryfall is owed better than that.
     */
    private val printingsByName = mutableMapOf<String, List<ScryfallCard>>()

    /** Every change to the pile is kept, so shutting the app down mid-session doesn't lose it. */
    private fun setScanned(rows: List<ScanRow>, status: String? = null) {
        _uiState.value = _uiState.value.copy(scannedCards = rows, status = status ?: _uiState.value.status)
        ScanPile.write(rows)
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
     * Identify a card by sight instead of text — for what the reader struggles with (glare, damage,
     * a foreign-language card, an unusual frame) as long as the card itself is in view. [bitmap] is
     * a full photo from the camera, upright.
     */
    fun matchByArt(bitmap: Bitmap) {
        val recognizer = cardIndexRepository.recognizer()
        if (recognizer == null) {
            val status = cardIndexRepository.status.value
            _uiState.value = _uiState.value.copy(
                status = if (status.downloading) "Card recognition is still downloading — try again in a moment." else "Card recognition isn't downloaded yet — see Settings."
            )
            cardIndexRepository.download()
            return
        }
        _uiState.value = _uiState.value.copy(status = "Looking at the card…")
        viewModelScope.launch {
            val seen = withContext(Dispatchers.Default) {
                runCatching {
                    // A full photo is far more than finding the card needs, and all of it would be held in memory.
                    val scale = minOf(1f, 1280f / bitmap.width)
                    val photo = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, 1280, (bitmap.height * scale).toInt(), true) else bitmap
                    val guide = guideInImage(photo.width, photo.height, previewWidth, previewHeight, GUIDE_WIDTH, GUIDE_HEIGHT)
                    FlatCard.find(photo, guide)?.let { recognizer.recognize(it) }
                }.getOrNull()
            }
            val sight = seen?.let { cardBySight(it.anywhere) }
            if (sight == null) {
                val guess = seen?.anywhere?.firstOrNull()?.entry?.name
                _uiState.value = _uiState.value.copy(
                    status = if (guess != null) "Not sure — it might be $guess. Hold it flat in the frame and try again, or type the name."
                    else "Couldn't make out a card — hold it flat in the frame and try again."
                )
                return@launch
            }
            val card = runCatching { cardRepository.getCardsByIds(listOf(sight.id)).firstOrNull() }.getOrNull()
            if (card == null) {
                _uiState.value = _uiState.value.copy(status = "Recognized ${sight.name}, but couldn't load it — check the connection.")
                return@launch
            }
            // The picture says which art; only when no other printing shares it is it certain.
            addScannedCard(card, exact = seen.anywhere.drop(1).none { it.entry.group == sight.group })
            lastAddedCard = card
        }
    }

    /**
     * Every printing of a scanned card, for picking the art actually in hand when the tiny set code
     * couldn't be read and the card came in as its usual printing.
     */
    suspend fun printingsOf(card: ScryfallCard): List<ScryfallCard> =
        runCatching { cardRepository.getPrintings(card.name) }.getOrDefault(emptyList())

    /** The printing on a row, swapped for the art the user picked. */
    fun setPrinting(rowId: Long, card: ScryfallCard) {
        // A printing that never comes in foil can't be a foil copy.
        setScanned(_uiState.value.scannedCards.map { if (it.id == rowId) it.copy(card = card, exact = true, foil = it.foil && card.canBeFoil) else it })
    }

    /** Marks one scanned copy foil or not — the camera can't tell, so the user says. */
    fun setFoil(rowId: Long, foil: Boolean) {
        setScanned(_uiState.value.scannedCards.map { if (it.id == rowId) it.copy(foil = foil && it.card.canBeFoil) else it })
    }

    /** One more copy of a card already scanned — its own row, as if it went past the camera again. */
    fun scanAgain(card: ScryfallCard) {
        addScannedCard(card)
    }

    override fun onCleared() {
        scanSound.release()
        recognizer.close()
        stripReader.close()
        stripThread.shutdown()
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

    /** Everything scanned, thrown away — leaving the scanner with cards still in the list. */
    fun clearScanned() {
        setScanned(emptyList(), status = "")
        lastAddedCard = null
        lastLookedUp = null
    }

    /** Takes one scan off the pile — a card read twice, or read wrongly. */
    fun removeScan(rowId: Long) {
        setScanned(_uiState.value.scannedCards.filterNot { it.id == rowId })
    }

    /** A pile's copies of a card as a binder entry: the ones marked foil as foil copies. */
    private fun collectionEntry(group: ScanGroup) =
        CollectionEntry(group.card.id, group.card.name, group.card.displayImageUrl, quantity = group.plain, foilQuantity = group.foils, backImageUrl = group.card.backImageUrl, tags = group.card.tags)

    private fun deckEntry(card: ScryfallCard, quantity: Int) =
        DeckCardEntry(card.id, card.name, card.displayImageUrl, quantity = quantity, canBeCommander = card.canBeCommander, typeLine = card.typeLine, partnerAbility = card.partnerAbility, backImageUrl = card.backImageUrl, tags = card.tags)

    /**
     * Every copy of [card] in the pile ([quantity]) where [pick] says — run by the add confirmation
     * ([ops]), which says so and can undo it. A card that's been put away leaves the list: what's
     * left is what still has to go somewhere, and scanning that card again starts a fresh count
     * rather than adding to a filed one. The scanner's cards are new copies in hand, so none come
     * out of the Unsorted pile.
     */
    suspend fun putAway(card: ScryfallCard, quantity: Int, pick: AddToPick, ops: AddToOps) {
        // Into a binder, the copies marked foil go in as foil (all of them, if the picker said foil).
        val foils = if (pick.foil) quantity else _uiState.value.scannedCards.count { it.card.id == card.id && it.foil }.coerceAtMost(quantity)
        if (pick.target.kind == SourceKind.BINDER && foils > 0) {
            // Made once (a new binder named in the picker), then both finishes go into it.
            val into = pick.copy(target = ops.resolve(pick), isNew = false)
            if (quantity - foils > 0) ops.addCard(card, into.copy(quantity = quantity - foils, foil = false), fromPile = false)
            ops.addCard(card, into.copy(quantity = foils, foil = true), fromPile = false)
        } else {
            ops.addCard(card, pick.copy(quantity = quantity), fromPile = false)
        }
        setScanned(_uiState.value.scannedCards.filterNot { it.card.id == card.id })
        if (lastAddedCard?.id == card.id) lastAddedCard = null
    }

    /** Everything scanned, copies added together — what a whole pile goes into a binder or deck as. */
    private fun pile(): List<ScanGroup> = grouped(_uiState.value.scannedCards)

    /** The whole pile where [pick] says; the list is emptied, ready for the next pile. */
    suspend fun putAllAway(pick: AddToPick, ops: AddToOps) {
        val pile = pile()
        if (pile.isEmpty()) return
        val target = ops.resolve(pick)
        when (target.kind) {
            SourceKind.BINDER -> {
                val entries = pile.map { collectionEntry(it) }
                // The Unsorted pile is made when the first cards go into it.
                if (target.id == UNSORTED_COLLECTION_ID) collectionRepository.addUnsorted(entries)
                else collectionRepository.addEntries(target.id, entries)
            }
            SourceKind.DECK -> {
                // Cards the user chose to leave out, as they aren't allowed in the deck (see
                // AddCheck), stay on the list to go somewhere else.
                val kept = pile.filterNot { ops.leaves(it.card.name) }
                // Into the sideboard — a Limited deck's pool, say — or the deck itself.
                if (pick.sideboard) deckRepository.addSideboardEntries(target.id, kept.map { deckEntry(it.card, it.quantity) })
                else deckRepository.addEntries(target.id, kept.map { deckEntry(it.card, it.quantity) })
                if (kept.size < pile.size) {
                    setScanned(_uiState.value.scannedCards.filter { ops.leaves(it.card.name) })
                    lastAddedCard = null
                    return
                }
            }
        }
        setScanned(emptyList())
        lastAddedCard = null
        lastLookedUp = null
    }

    /** Scans put away and then undone: back on the list, as they were. */
    fun restoreScans(rows: List<ScanRow>) {
        val current = _uiState.value.scannedCards
        val ids = current.map { it.id }.toSet()
        setScanned((current + rows.filter { it.id !in ids }).sortedByDescending { it.at })
    }

    class Factory(
        private val appContext: Context,
        private val collectionRepository: CollectionRepository,
        private val deckRepository: DeckRepository,
        private val cardIndexRepository: CardIndexRepository,
        private val settingsRepository: SettingsRepository,
        private val putAwayPlaceId: String? = null,
        private val tickList: TickList? = null,
        private val checkPlaceId: String? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ScanViewModel(
                appContext = appContext,
                cardRepository = CardRepository(),
                collectionRepository = collectionRepository,
                deckRepository = deckRepository,
                cardIndexRepository = cardIndexRepository,
                settingsRepository = settingsRepository,
                putAwayPlaceId = putAwayPlaceId,
                tickList = tickList,
                checkPlaceId = checkPlaceId
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

/**
 * Try to read the exact printing from the small print at the bottom of a card (see
 * [parseSetAndNumber]). Returns (setCode, collectorNumber), or null when either can't be read
 * confidently (the caller then falls back to name).
 */
internal fun extractSetAndNumber(textLines: List<Text.Line>): Pair<String, String>? =
    parseSetAndNumber(textLines.map { it.text })
