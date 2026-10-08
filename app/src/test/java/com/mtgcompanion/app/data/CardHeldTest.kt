package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.scryfall.ScryfallCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Frame sequences through the scanner's "same card still in view" logic: ScanViewModel.proceed and
 * noTitle, step for step (steady reads, the name hold, the blank-frame reset), with CardHeld deciding
 * the pile-of-one-name case. Tester build 40: "scanner is scanning the same card multiple times" — the
 * screenshot shows "Mountain again — copy 4, scanned just now", a Mountain gone in as a best guess
 * (Marvel Super Heroes #440) while its small print read as something else.
 */
class CardHeldTest {

    /** One camera frame: a title (or nothing), its small print, where the title sat. */
    private data class Frame(
        val title: String?,
        val read: Pair<String, String>? = null,
        val box: SeenBox? = null,
        val bySight: Boolean = false,
        val zoom: Boolean = false,
        val noEdges: Boolean = false
    )

    private data class Scan(val name: String, var printing: Pair<String, String>?)

    /**
     * The scanner, as ScanViewModel runs it. [takenAs] is what a lookup puts the card in as, given the
     * printing read when it was confirmed — the read printing, or a best guess that isn't it.
     */
    private class Scanner(private val takenAs: (Pair<String, String>?) -> Pair<String, String>? = { it }) {
        private fun same(a: String, b: String) = a.lowercase().filter { it.isLetter() } == b.lowercase().filter { it.isLetter() }
        val held = CardHeld(same = ::same)
        val scans = mutableListOf<Scan>()
        var updates = 0
        private var lastCandidate: String? = null
        private var lastLookedUp: String? = null
        private var lastAdded: String? = null
        private var steadyReads = 0
        private var blankStreak = 0

        fun run(frames: List<Frame>): Scanner {
            frames.forEach(::frame)
            return this
        }

        fun frame(f: Frame) {
            if (f.zoom) {
                held.zoomed()
                steadyReads = 0
                lastCandidate = null
            }
            val title = f.title
            if (title == null) {
                if (f.noEdges) held.noCard()
                held.blank()
                if (++blankStreak >= 4) {
                    steadyReads = 0
                    lastCandidate = null
                    lastLookedUp = null
                    lastAdded = null
                    held.reset()
                }
                return
            }
            blankStreak = 0
            when (held.frame(title, f.read, f.box, f.bySight)) {
                CardHeld.Verdict.NEW_CARD -> {
                    lastAdded = null
                    lastLookedUp = null
                }
                CardHeld.Verdict.UPDATE_PRINTING -> {
                    updates++
                    scans.last().printing = held.printing
                }
                CardHeld.Verdict.SAME -> {}
            }
            if (lastAdded?.let { same(title, it) } == true) {
                lastCandidate = title.lowercase()
                return
            }
            val normalized = title.lowercase()
            steadyReads = if (normalized == lastCandidate) steadyReads + 1 else 1
            lastCandidate = normalized
            if (steadyReads < 3 || normalized == lastLookedUp) return
            lastLookedUp = normalized
            held.confirmed()
            val printing = takenAs(f.read)
            scans += Scan(title, printing)
            lastAdded = title
            held.taken(title, listOf(f.read, printing), f.bySight)
        }
    }

    private fun title(left: Float = 0.10f, top: Float = 0.12f) = SeenBox(left, top, left + 0.5f, top + 0.04f, SeenBox.Kind.TITLE)
    private fun card(left: Float = 0.10f, top: Float = 0.10f) = SeenBox(left, top, left + 0.6f, top + 0.84f, SeenBox.Kind.CARD)
    private fun still(name: String, reads: List<Pair<String, String>?>, box: SeenBox = title()) = reads.map { Frame(name, it, box) }
    private fun blanks(n: Int) = List(n) { Frame(null) }

    private val fin306 = "FIN" to "306"
    private val fin307 = "FIN" to "307"
    private val fin308 = "FIN" to "308"
    private val fin309 = "FIN" to "309"
    private val spm440 = "SPM" to "440"
    private val m21 = "M21" to "277"

    @Test
    fun aStillCardWhoseSmallPrintFlickersIsCountedOnce() {
        // Ten seconds of one Mountain under the camera; the small print reads now one thing, now
        // another, now nothing — every misread run at least twice, as build 40 needed.
        val flicker = listOf(null, m21, m21, spm440, spm440, null, m21, m21, "M21" to "271", "M21" to "271", "M21" to "271")
        val frames = still("Mountain", List(10) { flicker }.flatten())
        val bestGuess = Scanner { spm440 }.run(frames)
        assertEquals(1, bestGuess.scans.size)
        val asRead = Scanner().run(frames)
        assertEquals(1, asRead.scans.size)
    }

    @Test
    fun theScreenshotCaseAStillCardTakenAsABestGuessIsPutRightNotCountedAgain() {
        // Taken as the name's best guess (SPM #440) before the small print read; then it reads M21 277
        // steadily. Build 40 took that for the next card of a pile: "Mountain again — copy 2".
        val frames = still("Mountain", listOf(null, null, null) + List(60) { m21 })
        val scanner = Scanner { spm440 }.run(frames)
        assertEquals(1, scanner.scans.size)
        assertEquals(1, scanner.updates)
        assertEquals(m21, scanner.scans.single().printing)
    }

    @Test
    fun aPrintingReadTwiceIsNotEnough() {
        val frames = still("Forest", listOf(fin306, fin306, fin306, null, fin307, fin307, null, fin306, fin307, fin307, fin306))
        val scanner = Scanner().run(frames)
        assertEquals(1, scanner.scans.size)
        assertEquals(0, scanner.updates)
    }

    @Test
    fun theFinForestPileCountsEveryCard() {
        // Full-art Forests laid one on top of another: a hand crosses the lens, and each lands a little off the last.
        val frames = still("Forest", List(6) { fin306 }, title(0.10f, 0.12f)) + blanks(2) +
            still("Forest", List(6) { fin307 }, title(0.16f, 0.15f)) + blanks(2) +
            still("Forest", List(6) { fin309 }, title(0.11f, 0.20f)) + blanks(3) +
            still("Forest", List(6) { fin308 }, title(0.04f, 0.13f))
        val scanner = Scanner().run(frames)
        assertEquals(listOf(fin306, fin307, fin309, fin308), scanner.scans.map { it.printing })
        assertEquals(0, scanner.updates)
    }

    @Test
    fun aNeatPileCountsByTheHandGoingOverIt() {
        // Laid squarely on top: the title sits where the last one did, but the hand blanked the reads.
        val frames = still("Forest", List(5) { fin306 }) + blanks(2) + still("Forest", List(5) { fin307 }) +
            blanks(2) + still("Forest", List(5) { fin309 })
        assertEquals(3, Scanner().run(frames).scans.size)
    }

    @Test
    fun aPileSlidInWithoutAGapCountsByTheCardMoving() {
        val frames = still("Forest", List(5) { fin306 }, title(0.10f, 0.12f)) + still("Forest", List(5) { fin307 }, title(0.10f, 0.22f))
        assertEquals(2, Scanner().run(frames).scans.size)
    }

    @Test
    fun theSamePrintingShownAgainAfterAGapIsCountedAgain() {
        val frames = still("Forest", List(5) { fin306 }) + blanks(5) + still("Forest", List(5) { fin306 })
        val scanner = Scanner().run(frames)
        assertEquals(2, scanner.scans.size)
        // And the list calls it what it is: "Forest again — copy 2".
        val forest = ScryfallCard(id = "fin-306", name = "Forest", set = "fin", collectorNumber = "306")
        val rows = scanner.scans.mapIndexed { i, _ -> ScanRow(i + 1L, forest, i * 10_000L) }.reversed()
        assertEquals(2, copyNumber(rows, rows.first()))
    }

    @Test
    fun oneGlareFrameIsNotACardSwapped() {
        val frames = still("Mountain", List(5) { m21 }) + blanks(1) + still("Mountain", List(10) { spm440 })
        val scanner = Scanner().run(frames)
        assertEquals(1, scanner.scans.size)
        assertEquals(1, scanner.updates)
    }

    @Test
    fun aZoomStepIsNotTheCardMoving() {
        val frames = still("Mountain", List(5) { m21 }) +
            listOf(Frame("Mountain", null, SeenBox(0.05f, 0.08f, 0.8f, 0.14f, SeenBox.Kind.TITLE), zoom = true)) +
            still("Mountain", List(10) { spm440 }, SeenBox(0.05f, 0.08f, 0.8f, 0.14f, SeenBox.Kind.TITLE))
        val scanner = Scanner().run(frames)
        assertEquals(1, scanner.scans.size)
    }

    @Test
    fun aCardKnownBySightIsHeldThroughTheUnreadTitleFramesBetween() {
        // Title unread: blank frames between the looks by sight are the card still there, not a gap.
        val look = { p: Pair<String, String>? -> Frame("Forest", p, card(), bySight = true) }
        val frames = List(12) { i -> listOf(Frame(null), Frame(null), look(if (i % 2 == 0) fin306 else fin307), look(fin307), look(fin307)) }.flatten()
        assertEquals(1, Scanner().run(frames).scans.size)
    }

    @Test
    fun aCardKnownBySightSwappedForAnotherIsCounted() {
        val look = { p: Pair<String, String>?, box: SeenBox -> Frame("Forest", p, box, bySight = true) }
        val frames = List(4) { look(fin306, card()) } + listOf(Frame(null, noEdges = true)) + List(4) { look(fin307, card(0.12f, 0.11f)) }
        assertEquals(2, Scanner().run(frames).scans.size)
    }

    @Test
    fun boxesMoveOnlyBeyondTheWobble() {
        assertFalse(boxMoved(title(0.10f, 0.12f), title(0.12f, 0.13f)))
        assertTrue(boxMoved(title(0.10f, 0.12f), title(0.10f, 0.20f)))
        assertTrue(boxMoved(title(), SeenBox(0.10f, 0.12f, 0.6f, 0.18f, SeenBox.Kind.TITLE)))
        // A title box and a card box aren't the same box.
        assertFalse(boxMoved(title(), card(0.5f, 0.5f)))
    }
}
