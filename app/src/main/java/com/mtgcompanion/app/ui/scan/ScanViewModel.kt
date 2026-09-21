package com.mtgcompanion.app.ui.scan

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
import com.mtgcompanion.app.data.GUIDE_GIVE_UP_FRAMES
import com.mtgcompanion.app.data.GUIDE_HEIGHT
import com.mtgcompanion.app.data.guideInImage
import com.mtgcompanion.app.data.GUIDE_SLACK
import com.mtgcompanion.app.data.confirmRead
import com.mtgcompanion.app.data.STEADY_READS
import com.mtgcompanion.app.data.parseSetAndNumber
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

/**
 * Blank frames in a row, read guide-only, before one whole frame is read to check the guide is still
 * where the card is (see [ScanViewModel.onFrame]).
 */
private const val PROBE_EVERY = 10

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
    /** The set code alone, when the frame showed it but not the number beside it. */
    val frameSet: String?,
    val picture: Bitmap?,
    val rotation: Int,
    val guide: ScanBox?,
    val token: Long,
    val queuedAt: Long
)

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
    val successToken: Int = 0,
    /** How careful the scanner is being — see ScanMode. */
    val scanMode: ScanMode = ScanMode.ACCURATE
)

class ScanViewModel(
    private val appContext: Context,
    private val cardRepository: CardRepository = CardRepository(),
    private val collectionRepository: CollectionRepository,
    private val deckRepository: DeckRepository,
    private val artIndexRepository: ArtIndexRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

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
        val candidate = extractCardName(lines)
        if (cut != null) guideBlankStreak = if (candidate == null) guideBlankStreak + 1 else 0
        if (candidate == null) {
            // No title in view. Only treat this as "the card actually left" after a real streak
            // of blank frames — see blankFrameStreak's doc comment above.
            if (++blankFrameStreak >= BLANK_FRAMES_TO_RESET) {
                steadyReads = 0
                lastCandidate = null
                lastLookedUp = null
                lastAddedCard = null
                inFlight.cardLeft()
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

        viewModelScope.launch {
            // The copy is the only part that needs the frame itself; once it's taken the camera is
            // handed back, and the card waits its turn for a lookup while the next one is read.
            val grabbed = SystemClock.elapsedRealtime()
            // Only the close read of the small print and the art match look at the picture; Fast
            // scanning does neither, so it doesn't take the copy.
            val mode = _uiState.value.scanMode
            val needsPicture = fromFrame == null && (mode.readsSmallPrint || mode.matchesArt)
            val picture = try {
                if (!needsPicture) null else withContext(Dispatchers.Default) { grabFrame?.invoke() }
            } finally {
                busy.set(false)
                onProcessed()
            }
            if (picture != null) timing("copy picture", grabbed)
            lookups.send(
                PendingScan(candidate, normalized, forced, fromFrame, frameSet, picture, rotation, guide, token, SystemClock.elapsedRealtime())
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
        val picture = scan.picture?.let { withContext(Dispatchers.Default) { uprightFrame(it, scan.rotation) } }

        var started = SystemClock.elapsedRealtime()
        // Fast scanning skips the close read: the printing comes from the frame, or from the art.
        val readsSmallPrint = _uiState.value.scanMode.readsSmallPrint
        val strip = if (scan.fromFrame == null && readsSmallPrint) picture?.let { readSmallPrint(it, scan.guide) } else null
        val printing = scan.fromFrame ?: strip?.let { parseSetAndNumber(it) }
        // When the number wouldn't read, the set code on its own still narrows the printings to
        // that set's few, for the look to choose between (see matchArt).
        val setCode = if (printing != null) null else strip?.let { parseSetCode(it) } ?: scan.frameSet
        // What it read, as well as how long it took: a quicker read is no use if it reads less.
        if (scan.fromFrame == null && picture != null && readsSmallPrint) timing("small print ${printing?.let { "read ${it.first} #${it.second}" } ?: setCode?.let { "read set $it only" } ?: "not read"}", started)
        else if (scan.fromFrame != null) Log.d("ScanTiming", "small print read in the frame itself: ${scan.fromFrame.first} #${scan.fromFrame.second}")
        val cacheKey = printing?.let { "${it.first}:${it.second}" } ?: scan.normalized

        // What the card looked like. When the set code was read there's nothing left to work out;
        // otherwise this decides the printing (see matchArt) — except in Fast scanning, which
        // leaves the card as its usual printing rather than fetching every printing to compare.
        started = SystemClock.elapsedRealtime()
        val look = if (printing != null || !_uiState.value.scanMode.matchesArt) null else picture?.let { withContext(Dispatchers.Default) { cameraSignatures(it, 0, scan.guide) } }?.ifEmpty { null }
        if (look != null) timing("art signature", started)

        var added: ScryfallCard? = null
        try {
            val cached = nameCache[cacheKey]
            started = SystemClock.elapsedRealtime()
            val card = cached ?: resolveCard(scan.candidate, printing)
            if (cached == null) timing("lookup", started)
            accept(scan.candidate, card, scan.forced, exact = printing != null)?.let { row ->
                nameCache[cacheKey] = card
                added = card
                matchArt(row, card, look, setCode)
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(status = "Didn't recognize \"${scan.candidate}\" — keep scanning…")
        } finally {
            // Remembered as the card in view only if it hasn't left since it was confirmed; if it
            // has, the next card in is a new one — even another copy of this card.
            if (inFlight.finished(scan.token, scan.normalized)) added?.let { lastAddedCard = it }
        }
    }

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
     * A second look at the card's small print, blown up: the set code and collector number say
     * which printing is in your hand — the alternate art, the borderless one — where the name alone
     * only gets the usual printing. The lines it read, for parseSetAndNumber and parseSetCode; null
     * when nothing could be read.
     */
    private suspend fun readSmallPrint(upright: Bitmap, guide: ScanBox?): List<String>? {
        val strip = withContext(Dispatchers.Default) { smallPrintStrip(upright, 0, guide) } ?: return null
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

    /** Every scan is its own row, newest first, so a card read twice shows twice. */
    private fun addScannedCard(card: ScryfallCard, exact: Boolean = false): Long {
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

    /**
     * Every printing of a scanned card, for picking the art actually in hand when the tiny set code
     * couldn't be read and the card came in as its usual printing.
     */
    suspend fun printingsOf(card: ScryfallCard): List<ScryfallCard> =
        runCatching { cardRepository.getPrintings(card.name) }.getOrDefault(emptyList())

    /** The printing on a row, swapped for the art the user picked. */
    fun setPrinting(rowId: Long, card: ScryfallCard) {
        setScanned(_uiState.value.scannedCards.map { if (it.id == rowId) it.copy(card = card, exact = true) else it })
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

    private fun collectionEntry(card: ScryfallCard, quantity: Int) =
        CollectionEntry(card.id, card.name, card.displayImageUrl, quantity = quantity, foilQuantity = 0, backImageUrl = card.backImageUrl, tags = card.tags)

    private fun deckEntry(card: ScryfallCard, quantity: Int) =
        DeckCardEntry(card.id, card.name, card.displayImageUrl, quantity = quantity, canBeCommander = card.canBeCommander, typeLine = card.typeLine, partnerAbility = card.partnerAbility, backImageUrl = card.backImageUrl, tags = card.tags)

    fun addToCollection(card: ScryfallCard, quantity: Int, collectionId: String) {
        viewModelScope.launch {
            collectionRepository.addEntry(collectionId, collectionEntry(card, quantity))
            putAway(card, quantity, "binder")
        }
    }

    fun createCollectionAndAdd(card: ScryfallCard, quantity: Int, name: String) {
        viewModelScope.launch {
            val collection = collectionRepository.createCollection(name)
            collectionRepository.addEntry(collection.id, collectionEntry(card, quantity))
            putAway(card, quantity, "\"${collection.name}\"")
        }
    }

    /**
     * A card that's been put away leaves the list: what's left is what still has to go somewhere,
     * and scanning that card again starts a fresh count rather than adding to a filed one.
     */
    private fun putAway(card: ScryfallCard, quantity: Int, where: String) {
        setScanned(
            _uiState.value.scannedCards.filterNot { it.card.id == card.id },
            status = "Added $quantity × ${card.name} to $where"
        )
        if (lastAddedCard?.id == card.id) lastAddedCard = null
    }

    fun addToDeck(card: ScryfallCard, quantity: Int, deckId: String) {
        viewModelScope.launch {
            // Checked before adding, off the deck's currently-stored state — informational only,
            // the card is added either way (testing/sideboard scenarios are legitimate).
            val warning = deckRepository.decksFlow.first().find { it.id == deckId }
                ?.let { duplicateWarning(it, card, addingQuantity = quantity) }
            deckRepository.addEntry(deckId, deckEntry(card, quantity))
            putAway(card, quantity, "deck")
            warning?.let { _uiState.value = _uiState.value.copy(status = it) }
        }
    }

    fun createDeckAndAdd(card: ScryfallCard, quantity: Int, name: String) {
        viewModelScope.launch {
            val deck = deckRepository.createDeck(name)
            deckRepository.addEntry(deck.id, deckEntry(card, quantity))
            putAway(card, quantity, "\"${deck.name}\"")
        }
    }

    /** Everything scanned, copies added together — what a whole pile goes into a binder or deck as. */
    private fun pile(): List<ScanGroup> = grouped(_uiState.value.scannedCards)

    private fun pileAdded(where: String, cards: Int) {
        setScanned(emptyList(), status = "Added $cards ${if (cards == 1) "card" else "cards"} to $where")
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
        private val appContext: Context,
        private val collectionRepository: CollectionRepository,
        private val deckRepository: DeckRepository,
        private val artIndexRepository: ArtIndexRepository,
        private val settingsRepository: SettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ScanViewModel(
                appContext = appContext,
                cardRepository = CardRepository(),
                collectionRepository = collectionRepository,
                deckRepository = deckRepository,
                artIndexRepository = artIndexRepository,
                settingsRepository = settingsRepository
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
