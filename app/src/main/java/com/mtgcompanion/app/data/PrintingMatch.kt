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

/**
 * Where the card in the frame might really be, around the guide. Nobody holds a card exactly inside
 * the guide, and a picture cut from the guide alone was card and table and a sliver of whatever was
 * next to it — tried on Sol Ring's printings photographed a little off centre, it named the printing
 * 25 times in 48 and got it wrong twice. So the camera's card is measured at a small grid of
 * positions and sizes around the guide, and each printing is compared at whichever suits it best:
 * 48 in 48, none wrong. Mirrors the web app's lookBoxes.
 */
val LOOK_SCALES = floatArrayOf(0.9f, 1f, 1.1f)
val LOOK_SHIFTS = floatArrayOf(-0.08f, -0.04f, 0f, 0.04f, 0.08f)

/** The card-shaped boxes, around [guide], that the camera's card is measured at. */
fun lookBoxes(guide: ScanBox): List<ScanBox> {
    val card = guide.cardShaped()
    val w = (card.right - card.left).toFloat()
    val h = (card.bottom - card.top).toFloat()
    val cx = card.left + w / 2
    val cy = card.top + h / 2
    val out = ArrayList<ScanBox>(LOOK_SCALES.size * LOOK_SHIFTS.size * LOOK_SHIFTS.size)
    for (s in LOOK_SCALES) for (ox in LOOK_SHIFTS) for (oy in LOOK_SHIFTS) {
        val bw = w * s
        val bh = h * s
        val left = cx + ox * w - bw / 2
        val top = cy + oy * h - bh / 2
        out += ScanBox(left.toInt(), top.toInt(), (left + bw).toInt(), (top + bh).toInt())
    }
    return out
}

/**
 * A signature straight from packed-colour pixels [width] x [height] for the part of them inside
 * [box]: each grid cell's colour averaged, then levelled — the same as cutting the box out and
 * shrinking it, without making seventy-five small pictures to do it. Null when the box doesn't lie
 * inside the pixels.
 */
fun signatureOfRegion(px: IntArray, width: Int, height: Int, box: ScanBox): FloatArray? {
    if (box.left < 0 || box.top < 0 || box.right > width || box.bottom > height) return null
    val bw = box.right - box.left
    val bh = box.bottom - box.top
    if (bw < GRID_W || bh < GRID_H) return null
    val raw = FloatArray(GRID_W * GRID_H * 3)
    for (gy in 0 until GRID_H) {
        val y0 = box.top + gy * bh / GRID_H
        val y1 = maxOf(y0 + 1, box.top + (gy + 1) * bh / GRID_H)
        for (gx in 0 until GRID_W) {
            val x0 = box.left + gx * bw / GRID_W
            val x1 = maxOf(x0 + 1, box.left + (gx + 1) * bw / GRID_W)
            var r = 0L
            var g = 0L
            var b = 0L
            var n = 0
            for (y in y0 until y1) {
                val row = y * width
                for (x in x0 until x1) {
                    val c = px[row + x]
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

/** The share of the card compared: the fifth that agrees worst is left out. */
private const val TRIM_KEEP = 0.8f

/**
 * How unlike a photographed card is to a printing, leaving out the fifth of the card that agrees
 * worst. A reflection, a thumb, a sleeve's edge washes out part of the picture, and on a plain
 * average that one patch decides the answer; left out, the rest of the card does. Photos with a
 * reflection went from 2 in 12 to 6 in 12, and still none wrong.
 */
fun trimmedDistance(camera: FloatArray, printing: FloatArray): Float {
    if (camera.size != printing.size || camera.isEmpty()) return Float.MAX_VALUE
    val cells = camera.size / 3
    val diffs = FloatArray(cells)
    for (i in 0 until cells) {
        var sum = 0f
        for (k in 0 until 3) { val d = camera[i * 3 + k] - printing[i * 3 + k]; sum += d * d }
        diffs[i] = sum / 3
    }
    diffs.sort()
    val keep = maxOf(1, (cells * TRIM_KEEP).toInt())
    var total = 0f
    for (i in 0 until keep) total += diffs[i]
    return total / keep
}

/** How unlike the camera's card is to a printing: at whichever of its measurings suits it best. */
private fun lookDistance(looks: List<FloatArray>, printing: FloatArray): Float =
    looks.minOfOrNull { trimmedDistance(it, printing) } ?: Float.MAX_VALUE

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
fun <T> bestPrinting(camera: FloatArray, candidates: List<Candidate<T>>): PrintingMatch<T>? =
    bestPrinting(listOf(camera), candidates)

/** As [bestPrinting] for one picture, with the camera's card measured at several places (see lookBoxes). */
fun <T> bestPrinting(looks: List<FloatArray>, candidates: List<Candidate<T>>): PrintingMatch<T>? {
    if (candidates.isEmpty() || looks.isEmpty()) return null
    val ranked = candidates.map { it to lookDistance(looks, it.signature) }.sortedBy { it.second }
    val (best, distance) = ranked.first()
    if (distance > MATCH_MAX) return null

    val others = ranked.drop(1)
    val sharesLook = others.any { artDistance(best.signature, it.first.signature) <= SAME_LOOK }
    // The nearest printing that isn't just this one reprinted — the one it could be confused with.
    val rival = others.firstOrNull { artDistance(best.signature, it.first.signature) > SAME_LOOK }
    if (rival != null && distance > rival.second * CLEAR_BY) return null

    return PrintingMatch(best.item, distance, only = !sharesLook)
}
