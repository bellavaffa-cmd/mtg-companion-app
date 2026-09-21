package com.mtgcompanion.app.data

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Finding the card itself in the camera's picture — its four edges, wherever it sits in the guide
 * and however it's tilted — and flattening it into a straight-on, card-shaped picture. Everything
 * that looks at the card afterwards (its look, its small print) then sees exactly the card: no
 * table round the edges, no guessing where inside the guide it was held.
 *
 * A card is held roughly in the guide, roughly upright, so each edge is looked for as a straight
 * line near where the guide says it should be: every nearly-upright line across a band either side
 * of the guide's left edge, scored by how sharp a step it runs along, and the same for the other
 * three. The strongest few candidates per side are then tried together, and the most card-shaped
 * four win — the outermost when there's a choice, since a card's frame draws a second rectangle
 * just inside its edge. When nothing card-shaped stands out (a black card on a black mat), there's
 * no answer and the guide is used as before.
 *
 * All of it is plain arithmetic on a small grey picture, so it's the same here and in the web app —
 * see src/scan/cardEdges.ts.
 */

data class Pt(val x: Float, val y: Float)

/** A card's four corners, clockwise from the top left, in the picture's pixels. */
data class CardQuad(val topLeft: Pt, val topRight: Pt, val bottomRight: Pt, val bottomLeft: Pt) {
    /** This quad in a picture [by] times the size, shifted by ([dx], [dy]) after scaling. */
    fun scaled(by: Float, dx: Float = 0f, dy: Float = 0f): CardQuad {
        fun Pt.s() = Pt(x * by + dx, y * by + dy)
        return CardQuad(topLeft.s(), topRight.s(), bottomRight.s(), bottomLeft.s())
    }

    val corners: List<Pt> get() = listOf(topLeft, topRight, bottomRight, bottomLeft)
    val width: Float get() = (dist(topLeft, topRight) + dist(bottomLeft, bottomRight)) / 2
    val height: Float get() = (dist(topLeft, bottomLeft) + dist(topRight, bottomRight)) / 2
}

private fun dist(a: Pt, b: Pt) = hypot(a.x - b.x, a.y - b.y)

/** How much of the guide's size either side of each of its edges the card's edge is looked for in. */
const val EDGE_BAND = 0.2f

/** The steepest lean an edge is looked for at, as sideways pixels per pixel along it (~11°). */
private const val MAX_LEAN = 0.2f
private const val LEAN_STEPS = 21

/** How many of the strongest lines per side are tried against the other sides. */
private const val PER_SIDE = 4

/**
 * How sharp a step counts in full towards a line's score, in grey levels (of 255). Past this, a
 * sharper step counts no more: a line printed inside the card (the title bar, the text box) is often
 * far crisper than the card's own edge against the table, and it mustn't win on that alone. What
 * then counts is how much of its length a line is an edge at all — a straight edge all along, not a
 * few sharp spots where it happens to cross something.
 */
const val EDGE_CAP = 20f

/**
 * How sharp an edge has to be, on average along its length, to count — with steps capped at
 * [EDGE_CAP], an edge along about 70% of its length. Below this it's texture, a shadow, or nothing.
 */
const val MIN_EDGE = 14f

/** A tilted card is still near this shape (63 × 88 mm), but not outside these. */
private const val MIN_ASPECT = 0.62f
private const val MAX_ASPECT = 0.82f

/** Of the four-edge sets scoring within this share of the best, the biggest is the card. */
private const val NEAR_BEST = 0.85f

/** Grey levels (0-255) of packed-colour pixels. */
fun greyOf(px: IntArray): FloatArray = FloatArray(px.size) {
    val c = px[it]
    0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)
}

/** One side's candidate: `across = at + lean * (along - mid)`, and how sharp an edge runs along it. */
private data class Line(val at: Float, val lean: Float, val score: Float)

/**
 * The card's corners in a grey picture [width] × [height], held roughly where [expected] says, or
 * null when no four edges make a card of it. Corners are in the picture's continuous coordinates:
 * pixel (0, 0) covers 0 to 1 each way.
 */
fun findCard(grey: FloatArray, width: Int, height: Int, expected: ScanBox): CardQuad? =
    findCards(grey, width, height, expected, 1).firstOrNull()

/**
 * The likeliest few outlines of the card, best first — for whatever reads the card next to try each
 * and keep the one that makes sense. The edges alone can't always tell a card's edge from a crisp
 * line printed just inside it (the title bar, the text box); what the card turns out to look like can.
 */
fun findCards(grey: FloatArray, width: Int, height: Int, expected: ScanBox, most: Int): List<CardQuad> {
    if (width < 16 || height < 16) return emptyList()
    val (gx, gy) = gradients(grey, width, height)
    val ew = (expected.right - expected.left).toFloat()
    val eh = (expected.bottom - expected.top).toFloat()
    if (ew < 8 || eh < 8) return emptyList()
    val midX = expected.left + ew / 2
    val midY = expected.top + eh / 2

    // Upright sides run down the picture: x depends on y. Level sides run across it: y on x.
    // Only the middle of each side is sampled — a card's corners are rounded.
    val left = outermost(sideLines(gx, width, height, vertical = true, around = expected.left.toFloat(), band = ew * EDGE_BAND, from = expected.top + eh * 0.15f, to = expected.bottom - eh * 0.15f, mid = midY), outward = -1, size = ew)
    val right = outermost(sideLines(gx, width, height, vertical = true, around = expected.right.toFloat(), band = ew * EDGE_BAND, from = expected.top + eh * 0.15f, to = expected.bottom - eh * 0.15f, mid = midY), outward = 1, size = ew)
    val top = outermost(sideLines(gy, width, height, vertical = false, around = expected.top.toFloat(), band = eh * EDGE_BAND, from = expected.left + ew * 0.15f, to = expected.right - ew * 0.15f, mid = midX), outward = -1, size = eh)
    val bottom = outermost(sideLines(gy, width, height, vertical = false, around = expected.bottom.toFloat(), band = eh * EDGE_BAND, from = expected.left + ew * 0.15f, to = expected.right - ew * 0.15f, mid = midX), outward = 1, size = eh)
    if (left.isEmpty() || right.isEmpty() || top.isEmpty() || bottom.isEmpty()) return emptyList()

    data class Option(val quad: CardQuad, val score: Float, val area: Float)
    val options = ArrayList<Option>()
    for (l in left) for (r in right) for (t in top) for (b in bottom) {
        val quad = CardQuad(
            corner(l, t, midX, midY), corner(r, t, midX, midY),
            corner(r, b, midX, midY), corner(l, b, midX, midY)
        )
        val w = quad.width
        val h = quad.height
        if (h <= 0f || w < ew * 0.5f || h < eh * 0.5f) continue
        val aspect = w / h
        if (aspect < MIN_ASPECT || aspect > MAX_ASPECT) continue
        options += Option(quad, l.score + r.score + t.score + b.score, w * h)
    }
    val best = options.maxOfOrNull { it.score } ?: return emptyList()
    // First the biggest of those scoring near the best; then the rest by score, leaving out any
    // that's all but the same outline as one already listed.
    val ranked = options.sortedByDescending { it.score }
    val first = options.filter { it.score >= best * NEAR_BEST }.maxByOrNull { it.area } ?: ranked.first()
    val picked = mutableListOf(first)
    fun same(a: CardQuad, b: CardQuad) = a.corners.zip(b.corners).all { (p, q) -> dist(p, q) <= ew * 0.015f }
    for (o in ranked) {
        if (picked.size >= most) break
        if (picked.none { same(it.quad, o.quad) }) picked += o
    }
    // Lines were placed by pixel; a pixel's middle is half a pixel in from its corner.
    return picked.map { it.quad.scaled(1f, 0.5f, 0.5f) }
}

/**
 * A card's border draws two lines along each side: the card's edge, and just inside it the frame
 * or art, often the sharper of the two. So a line with a fainter parallel partner a border's width
 * further out ([outward] is which way out is) gives its place to that partner — the card is the
 * outer one. [size] is the card's size across the side, which the border's width is a share of.
 */
private fun outermost(lines: List<Line>, outward: Int, size: Float): List<Line> = lines.map { line ->
    lines.filter { other ->
        val out = (other.at - line.at) * outward
        out >= size * BORDER_MIN && out <= size * BORDER_MAX &&
            abs(other.lean - line.lean) <= 0.03f && other.score >= line.score * PARTNER_SHARE
    }.maxByOrNull { (it.at - line.at) * outward }?.let { it.copy(score = maxOf(it.score, line.score)) } ?: line
}.distinct()

/** A card's border is about 2.5 mm of its 63 mm width: between these shares of the card, with give. */
private const val BORDER_MIN = 0.02f
private const val BORDER_MAX = 0.08f

/** The edge outside a frame line only needs to be this much as sharp to be taken as the card's. */
private const val PARTNER_SHARE = 0.4f

/** Where a side's line and a level line cross. */
private fun corner(upright: Line, level: Line, midX: Float, midY: Float): Pt {
    // x = u.at + u.lean (y - midY);  y = l.at + l.lean (x - midX)
    val x = (upright.at + upright.lean * (level.at - level.lean * midX - midY)) / (1 - upright.lean * level.lean)
    val y = level.at + level.lean * (x - midX)
    return Pt(x, y)
}

/** Sobel gradients, each roughly "grey levels of step" across the pixel. */
private fun gradients(g: FloatArray, w: Int, h: Int): Pair<FloatArray, FloatArray> {
    val gx = FloatArray(w * h)
    val gy = FloatArray(w * h)
    for (y in 1 until h - 1) for (x in 1 until w - 1) {
        val i = y * w + x
        val a = g[i - w - 1]; val b = g[i - w]; val c = g[i - w + 1]
        val d = g[i - 1]; val f = g[i + 1]
        val p = g[i + w - 1]; val q = g[i + w]; val r = g[i + w + 1]
        gx[i] = ((c + 2 * f + r) - (a + 2 * d + p)) / 4
        gy[i] = ((p + 2 * q + r) - (a + 2 * b + c)) / 4
    }
    return gx to gy
}

/**
 * The strongest few lines for one side: across a band [band] either side of [around], at every lean,
 * sampled from [from] to [to] along the side. [vertical] sides are found in x, level ones in y.
 * A line scores the average sharpness of the step it runs along; only a line that beats its
 * neighbours a few pixels either side counts, so one edge doesn't fill every place on the list.
 */
private fun sideLines(
    grad: FloatArray, w: Int, h: Int, vertical: Boolean,
    around: Float, band: Float, from: Float, to: Float, mid: Float
): List<Line> {
    val acrossMax = if (vertical) w - 2 else h - 2
    val alongMax = if (vertical) h - 2 else w - 2
    val lo = (around - band).roundToInt().coerceIn(1, acrossMax)
    val hi = (around + band).roundToInt().coerceIn(1, acrossMax)
    val a0 = from.roundToInt().coerceIn(1, alongMax)
    val a1 = to.roundToInt().coerceIn(1, alongMax)
    if (hi <= lo || a1 - a0 < 4) return emptyList()
    val step = maxOf(1, (a1 - a0) / 80)

    // The best lean at each place across.
    val best = FloatArray(hi - lo + 1)
    val bestLean = FloatArray(hi - lo + 1)
    for (at in lo..hi) {
        for (k in 0 until LEAN_STEPS) {
            val lean = -MAX_LEAN + 2 * MAX_LEAN * k / (LEAN_STEPS - 1)
            var sum = 0f
            var n = 0
            var along = a0
            while (along <= a1) {
                val across = (at + lean * (along - mid)).roundToInt()
                if (across in 1..acrossMax) {
                    sum += minOf(EDGE_CAP, abs(if (vertical) grad[along * w + across] else grad[across * w + along]))
                    n++
                }
                along += step
            }
            // Mostly off the picture isn't a line that was seen.
            val score = if (n * step * 2 < a1 - a0) 0f else sum / n
            if (score > best[at - lo]) { best[at - lo] = score; bestLean[at - lo] = lean }
        }
    }
    val lines = ArrayList<Line>()
    for (i in best.indices) {
        val s = best[i]
        if (s < MIN_EDGE) continue
        var peak = true
        for (j in maxOf(0, i - 3)..minOf(best.size - 1, i + 3)) {
            if (j != i && (best[j] > s || (best[j] == s && j < i))) { peak = false; break }
        }
        if (!peak) continue
        // Where between pixels the edge really is: the top of a parabola through the peak and its
        // neighbours. A step between two pixels scores the same on both, and sits halfway.
        val before = if (i > 0) best[i - 1] else s
        val after = if (i < best.size - 1) best[i + 1] else s
        val bend = before - 2 * s + after
        val shift = if (bend < 0f) (0.5f * (before - after) / bend).coerceIn(-0.5f, 0.5f) else 0f
        lines += Line(lo + i + shift, bestLean[i], s)
    }
    return lines.sortedByDescending { it.score }.take(PER_SIDE)
}

/**
 * Where a point of the flattened card lands in the picture — [u] and [v] from 0 to 1 across and
 * down the card. A perspective map (a plane seen at an angle), so a card tilted away from the
 * camera flattens back to the right shape and not just a straightened one.
 */
class CardMap(quad: CardQuad) {
    private val a: Float; private val b: Float; private val c: Float
    private val d: Float; private val e: Float; private val f: Float
    private val g: Float; private val h: Float

    init {
        val x0 = quad.topLeft.x; val y0 = quad.topLeft.y
        val x1 = quad.topRight.x; val y1 = quad.topRight.y
        val x2 = quad.bottomRight.x; val y2 = quad.bottomRight.y
        val x3 = quad.bottomLeft.x; val y3 = quad.bottomLeft.y
        val sx = x0 - x1 + x2 - x3
        val sy = y0 - y1 + y2 - y3
        if (abs(sx) < 1e-4f && abs(sy) < 1e-4f) {
            g = 0f; h = 0f
        } else {
            val dx1 = x1 - x2; val dx2 = x3 - x2
            val dy1 = y1 - y2; val dy2 = y3 - y2
            val den = dx1 * dy2 - dx2 * dy1
            g = if (den == 0f) 0f else (sx * dy2 - dx2 * sy) / den
            h = if (den == 0f) 0f else (dx1 * sy - sx * dy1) / den
        }
        a = x1 - x0 + g * x1; b = x3 - x0 + h * x3; c = x0
        d = y1 - y0 + g * y1; e = y3 - y0 + h * y3; f = y0
    }

    fun x(u: Float, v: Float): Float = (a * u + b * v + c) / (g * u + h * v + 1)
    fun y(u: Float, v: Float): Float = (d * u + e * v + f) / (g * u + h * v + 1)
}

/**
 * Part of the card flattened into [outW] × [outH] packed-colour pixels: the card from [u0], [v0] to
 * [u1], [v1] (0 to 1 across and down it), picked out of [px] ([width] × [height]) through [quad].
 * Pixels that fall off the picture come out black.
 */
fun flatten(
    px: IntArray, width: Int, height: Int, quad: CardQuad, outW: Int, outH: Int,
    u0: Float = 0f, v0: Float = 0f, u1: Float = 1f, v1: Float = 1f
): IntArray {
    val map = CardMap(quad)
    val out = IntArray(outW * outH)
    for (oy in 0 until outH) {
        val v = v0 + (v1 - v0) * (oy + 0.5f) / outH
        for (ox in 0 until outW) {
            val u = u0 + (u1 - u0) * (ox + 0.5f) / outW
            out[oy * outW + ox] = sample(px, width, height, map.x(u, v) - 0.5f, map.y(u, v) - 0.5f)
        }
    }
    return out
}

/** The colour at a point between pixels, blended from the four around it. */
private fun sample(px: IntArray, w: Int, h: Int, x: Float, y: Float): Int {
    if (x < -0.5f || y < -0.5f || x > w - 0.5f || y > h - 0.5f) return 0xFF000000.toInt()
    val xi = x.toInt().coerceIn(0, w - 1)
    val yi = y.toInt().coerceIn(0, h - 1)
    val x1 = minOf(xi + 1, w - 1)
    val y1 = minOf(yi + 1, h - 1)
    val fx = (x - xi).coerceIn(0f, 1f)
    val fy = (y - yi).coerceIn(0f, 1f)
    val p00 = px[yi * w + xi]; val p10 = px[yi * w + x1]
    val p01 = px[y1 * w + xi]; val p11 = px[y1 * w + x1]
    var out = 0xFF000000.toInt()
    for (shift in intArrayOf(16, 8, 0)) {
        val top = ((p00 shr shift) and 0xFF) * (1 - fx) + ((p10 shr shift) and 0xFF) * fx
        val bottom = ((p01 shr shift) and 0xFF) * (1 - fx) + ((p11 shr shift) and 0xFF) * fx
        out = out or ((top * (1 - fy) + bottom * fy).roundToInt().coerceIn(0, 255) shl shift)
    }
    return out
}
