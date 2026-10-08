package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.scryfall.ScryfallCard
import kotlin.math.abs
import kotlin.math.max

/**
 * Where the card sat in one frame, as shares of the picture's longer side: the title line's box when
 * the title was read ([Kind.TITLE]), or the card's own edges when it was known by sight ([Kind.CARD]).
 * Only boxes of the same kind are compared — the title line and the whole card are not the same box.
 */
data class SeenBox(val left: Float, val top: Float, val right: Float, val bottom: Float, val kind: Kind) {
    enum class Kind { TITLE, CARD }

    val height: Float get() = bottom - top
}

/** A card's printing, set code and collector number, when it has both. */
fun printingOf(card: ScryfallCard): Pair<String, String>? =
    card.set?.let { s -> card.collectorNumber?.let { n -> s to n } }

/** The title line's box ([left]..[bottom] in a [width] x [height] picture) as a [SeenBox]. */
fun titleBox(left: Int, top: Int, right: Int, bottom: Int, width: Int, height: Int): SeenBox? {
    val side = max(width, height).toFloat()
    if (side <= 0f || right <= left || bottom <= top) return null
    return SeenBox(left / side, top / side, right / side, bottom / side, SeenBox.Kind.TITLE)
}

/** The card's corners ([xs], [ys] in a [width] x [height] picture) as a [SeenBox]. */
fun cardBox(xs: List<Float>, ys: List<Float>, width: Int, height: Int): SeenBox? {
    val side = max(width, height).toFloat()
    if (side <= 0f || xs.isEmpty() || ys.isEmpty()) return null
    return SeenBox(xs.min() / side, ys.min() / side, xs.max() / side, ys.max() / side, SeenBox.Kind.CARD)
}

/** How far a box may drift before the card counts as moved: hands shake, the reader's box wobbles. */
const val MOVE_SHARE = 0.05f
/** How much a box may grow or shrink before the card counts as moved (another card, or picked up). */
const val RESIZE_SHARE = 0.25f

/** Whether the card plainly moved between [a] and [b]. Boxes of different kinds can't say. */
fun boxMoved(a: SeenBox, b: SeenBox): Boolean {
    if (a.kind != b.kind) return false
    if (abs(a.left - b.left) > MOVE_SHARE || abs(a.top - b.top) > MOVE_SHARE) return true
    if (a.height <= 0f || b.height <= 0f) return false
    val ratio = b.height / a.height
    return ratio > 1 + RESIZE_SHARE || ratio < 1 / (1 + RESIZE_SHARE)
}

/**
 * The card just taken, while it stays under the camera — so it's counted once however its printing
 * reads frame to frame.
 *
 * The scanner's own name check keeps a card of the same name from being taken twice; this decides the
 * one exception, a pile of one name (basic lands, the FIN Forests) laid one on top of another. A card
 * of the same name is a NEW card only when its small print names another printing on
 * [printingFrames] reads with none against it, AND the card was swapped: its box moved, or there was
 * a gap ([gapFrames] frames running with no title — a hand laying the next one on). Otherwise the read
 * printing is the same card read better, and the scan's printing is put right instead
 * ([Verdict.UPDATE_PRINTING]) — once per printing, so a read flickering between two never adds a card
 * and never flip-flops the row.
 *
 * Build 40 went by "a different printing read on two frames running", against the printing the card
 * was taken as. The printing taken is often not what the small print reads (the name's usual printing,
 * the art match's best guess, a learned correction), and the small print flickers, so one card held
 * still was counted again and again ("Mountain again — copy 4").
 */
class CardHeld(
    private val same: (String, String) -> Boolean,
    private val printingFrames: Int = 3,
    private val gapFrames: Int = 2
) {
    enum class Verdict { SAME, NEW_CARD, UPDATE_PRINTING }

    private var name: String? = null
    private var bySight = false
    /** Every printing the card taken is known as: read at its lookup, and the printing it went in as. */
    private val known = mutableListOf<Pair<String, String>>()
    /** Where the card sat when it was confirmed; null until the next box after a zoom. */
    private var anchor: SeenBox? = null
    private var lastBox: SeenBox? = null
    private var disturbed = false
    private var blanks = 0
    private var streak: Pair<String, String>? = null
    private var streakCount = 0

    /** The printing [Verdict.UPDATE_PRINTING] was about, or null. */
    var printing: Pair<String, String>? = null
        private set

    /** Whether a card is held. */
    val holding: Boolean get() = name != null

    /** One frame with a card read as [title] — by sight when [bySightRead] — its small print [read], where it sat [box]. */
    fun frame(title: String, read: Pair<String, String>?, box: SeenBox?, bySightRead: Boolean = false): Verdict {
        printing = null
        blanks = 0
        if (box != null) {
            val from = anchor
            if (from == null || from.kind != box.kind) anchor = box
            else if (boxMoved(from, box)) disturbed = true
            lastBox = box
        }
        val held = name ?: return Verdict.SAME
        if (!same(title, held)) {
            streak = null
            streakCount = 0
            return Verdict.SAME
        }
        if (bySightRead && !bySight) bySight = true
        if (read != null) {
            val s = streak
            if (s != null && samePrinting(s, read)) streakCount++ else {
                streak = read
                streakCount = 1
            }
        }
        val reading = streak ?: return Verdict.SAME
        if (streakCount < printingFrames || known.any { samePrinting(it, reading) }) return Verdict.SAME
        if (disturbed) {
            forget()
            return Verdict.NEW_CARD
        }
        known += reading
        printing = reading
        return Verdict.UPDATE_PRINTING
    }

    /** A frame with no title read. A run of them is a gap — for a card taken by its title. */
    fun blank() {
        blanks++
        if (blanks >= gapFrames && name != null && !bySight) disturbed = true
    }

    /** A look for the card's edges found none: nothing is under the camera. */
    fun noCard() {
        if (name != null) disturbed = true
    }

    /** The zoom moved: the card's box changes size without the card moving, so it's measured afresh. */
    fun zoomed() {
        anchor = null
    }

    /** The card in view is going to be looked up: from here on, a move or a gap counts against it. */
    fun confirmed() {
        anchor = lastBox
        disturbed = false
        streak = null
        streakCount = 0
    }

    /** The card looked up went in as [taken], having been read as [printings] (any may be null). */
    fun taken(taken: String, printings: List<Pair<String, String>?>, seenBySight: Boolean = false) {
        name = taken
        bySight = seenBySight
        known.clear()
        for (p in printings) if (p != null && known.none { samePrinting(it, p) }) known += p
        if (anchor == null) anchor = lastBox
    }

    /** The card has left (or was removed from the list): whatever comes next is new. */
    fun reset() {
        forget()
        anchor = null
        lastBox = null
        blanks = 0
    }

    private fun forget() {
        name = null
        bySight = false
        known.clear()
        disturbed = false
        streak = null
        streakCount = 0
    }
}
