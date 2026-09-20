package com.mtgcompanion.app.data

/**
 * Which printing is in your hand, from what the card looks like.
 *
 * The camera reads a card's name easily. The tiny line that says *which* printing it is — the
 * alternate art, the borderless one, the full-art basic — is a few pixels tall and often can't be
 * read at all, and then the card comes in as whatever printing Scryfall considers the usual one.
 * So the card in the frame is boiled down to a small grid of colour and held up against every
 * printing of that name; the closest one wins.
 *
 * The comparison is deliberately coarse. A photo taken under a lamp at a slight angle will never
 * match a clean scan pixel for pixel, but what makes printings tell apart — where the picture is
 * light and dark, what colours it leans on, whether there's a border around it at all — survives
 * the lamp, the angle and a camera's guesswork.
 *
 * This is not the art recognition in [com.mtgcompanion.app.data.artrecognition]: that one answers
 * "what card is this?" against a downloaded index of every card there is, for when the text can't
 * be read at all. This one only ever chooses between the printings of a name already read, needs
 * nothing downloaded, and is what runs on every scan. Mirrors the web app's src/scan/printingMatch.ts.
 */

/** The grid a card is boiled down to. A card is taller than it is wide, and so is this. */
const val GRID_W = 8
const val GRID_H = 11
private const val CELLS = GRID_W * GRID_H

/** A Magic card is 63 x 88 mm. */
const val CARD_ASPECT = 63f / 88f

/**
 * How much is trimmed off each edge before comparing, as a share of the whole box. A card held to
 * fill the guide never fills it exactly, so the outermost sliver is as likely to be the table
 * behind it as the card.
 */
const val PRINTING_INSET = 0.04f

/** Closer than this and two printings are the same picture — the same art in the same frame. */
const val SAME_LOOK = 0.08f

/**
 * Further than this from every printing and the camera saw something that can't be placed.
 *
 * Magic cards are far more alike than they look: they share a frame, a border and a layout, so two
 * cards with nothing to do with each other still score around 0.2 — a Lightning Bolt against Sol
 * Ring's printings came out at 0.23. Photographed printings of the *right* card land between 0.02
 * and 0.25. Those two ranges touch, which is why this is a sanity check and not the decision: what
 * actually picks the printing is having to beat the runner-up, below.
 */
const val MATCH_MAX = 0.6f

/** The winner has to be at least this much closer than the nearest printing that looks different. */
const val CLEAR_BY = 0.8f

/**
 * The biggest card-shaped box that fits inside this one, centred. The framing guide isn't quite a
 * card's shape, and a card squeezed into the wrong shape doesn't look like itself any more.
 */
fun ScanBox.cardShaped(): ScanBox {
    val wide = (right - left).toFloat()
    val tall = (bottom - top).toFloat()
    if (wide <= 0f || tall <= 0f) return this
    val width = minOf(wide, tall * CARD_ASPECT)
    val height = width / CARD_ASPECT
    val x = left + ((wide - width) / 2).toInt()
    val y = top + ((tall - height) / 2).toInt()
    return ScanBox(x, y, x + width.toInt(), y + height.toInt())
}

/**
 * Takes the lighting out of a grid of raw colour: each channel is re-centred on its own average and
 * scaled by how much it varies across the card. A dim photo and a bright scan of the same card come
 * out of this the same, which is the whole point.
 */
fun levelled(raw: FloatArray): FloatArray {
    val out = FloatArray(raw.size)
    val cells = raw.size / 3
    if (cells == 0) return out
    for (channel in 0 until 3) {
        var sum = 0f
        var i = channel
        while (i < raw.size) { sum += raw[i]; i += 3 }
        val mean = sum / cells
        var spread = 0f
        i = channel
        while (i < raw.size) { val d = raw[i] - mean; spread += d * d; i += 3 }
        // A blank card — every cell the same — has nothing to scale by, so it's left flat rather
        // than divided by zero into noise.
        val deviation = Math.sqrt((spread / cells).toDouble()).toFloat().takeIf { it > 0f } ?: 1f
        i = channel
        while (i < raw.size) { out[i] = (raw[i] - mean) / deviation; i += 3 }
    }
    return out
}

/**
 * Boils packed-colour pixels [width] x [height] down to the grid, averaging each cell, then levels
 * it. [px] is the ARGB a Bitmap hands over.
 */
fun signatureFromPixels(px: IntArray, width: Int, height: Int): FloatArray {
    val raw = FloatArray(CELLS * 3)
    for (gy in 0 until GRID_H) {
        val y0 = gy * height / GRID_H
        val y1 = maxOf(y0 + 1, (gy + 1) * height / GRID_H)
        for (gx in 0 until GRID_W) {
            val x0 = gx * width / GRID_W
            val x1 = maxOf(x0 + 1, (gx + 1) * width / GRID_W)
            var r = 0L
            var g = 0L
            var b = 0L
            var n = 0
            for (y in y0 until y1) {
                for (x in x0 until x1) {
                    val c = px[y * width + x]
                    r += (c shr 16) and 0xFF
                    g += (c shr 8) and 0xFF
                    b += c and 0xFF
                    n++
                }
            }
            val cell = (gy * GRID_W + gx) * 3
            raw[cell] = r.toFloat() / n
            raw[cell + 1] = g.toFloat() / n
            raw[cell + 2] = b.toFloat() / n
        }
    }
    return levelled(raw)
}

/** How unlike two cards look. 0 is the same picture; two unrelated cards land somewhere near 0.2. */
fun artDistance(a: FloatArray, b: FloatArray): Float {
    if (a.size != b.size || a.isEmpty()) return Float.MAX_VALUE
    var sum = 0f
    for (i in a.indices) { val d = a[i] - b[i]; sum += d * d }
    return sum / a.size
}

/** A printing and what it looks like. */
data class Candidate<T>(val item: T, val signature: FloatArray)

data class PrintingMatch<T>(
    /** The printing the card in the frame looks most like. */
    val pick: T,
    /** How close it was — smaller is a better likeness. */
    val distance: Float,
    /**
     * Whether that picture belongs to this printing alone. False when several printings share it —
     * the same art in the same frame, reprinted — which no camera can tell apart, and then only the
     * set code can say which one it really is.
     */
    val only: Boolean
)

/**
 * The printing [camera] looks most like, or null when nothing is close enough or two printings that
 * genuinely look different are too near to call. Saying nothing is the right answer there: a wrong
 * printing recorded silently is worse than none, and the scan falls back to asking.
 */
fun <T> bestPrinting(camera: FloatArray, candidates: List<Candidate<T>>): PrintingMatch<T>? {
    if (candidates.isEmpty()) return null
    val ranked = candidates.sortedBy { artDistance(camera, it.signature) }
    val best = ranked.first()
    val distance = artDistance(camera, best.signature)
    if (distance > MATCH_MAX) return null

    val others = ranked.drop(1)
    val sharesLook = others.any { artDistance(best.signature, it.signature) <= SAME_LOOK }
    // The nearest printing that isn't just this one reprinted — the one it could be confused with.
    val rival = others.firstOrNull { artDistance(best.signature, it.signature) > SAME_LOOK }
    if (rival != null && distance > artDistance(camera, rival.signature) * CLEAR_BY) return null

    return PrintingMatch(best.item, distance, only = !sharesLook)
}
