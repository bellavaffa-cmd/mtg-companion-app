package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Finding a card's edges in a picture and flattening it. The web app has the same checks — see
 * MtgCompanionWeb/tests/scan/cardEdges.test.ts.
 */
class CardEdgesTest {

    private val w = 200
    private val h = 260

    /** The guide the card is expected in: card-shaped, in the middle of the picture. */
    private val guide = ScanBox(35, 32, 165, 214)

    /** A card [cw] × [ch] centred at ([cx], [cy]), turned [degrees], on a [background]. */
    private fun scene(
        cx: Float = 100f, cy: Float = 123f, cw: Float = 130f, ch: Float = 182f, degrees: Float = 0f,
        background: (Int, Int) -> Int = { x, y -> 205 + ((x * 7 + y * 13) % 9) - 4 }
    ): IntArray {
        val rad = Math.toRadians(degrees.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        return IntArray(w * h) { i ->
            val x = i % w + 0.5f
            val y = i / w + 0.5f
            val lx = (x - cx) * c + (y - cy) * s
            val ly = -(x - cx) * s + (y - cy) * c
            val grey = when {
                kotlin.math.abs(lx) > cw / 2 || kotlin.math.abs(ly) > ch / 2 -> background(i % w, i / w)
                // The black border, then the frame and art inside it: a second rectangle, just in.
                kotlin.math.abs(lx) > cw / 2 * 0.9f || kotlin.math.abs(ly) > ch / 2 * 0.93f -> 22
                else -> 150 + ((lx.toInt() / 6 + ly.toInt() / 9) % 3) * 30
            }
            (0xFF shl 24) or (grey shl 16) or (grey shl 8) or grey
        }
    }

    /** The corners of that card. */
    private fun corners(cx: Float = 100f, cy: Float = 123f, cw: Float = 130f, ch: Float = 182f, degrees: Float = 0f): List<Pt> {
        val rad = Math.toRadians(degrees.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        return listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f).map { (sx, sy) ->
            val lx = sx * cw / 2
            val ly = sy * ch / 2
            Pt(cx + lx * c - ly * s, cy + lx * s + ly * c)
        }
    }

    private fun assertCorners(expected: List<Pt>, quad: CardQuad?, within: Float) {
        assertNotNull(quad)
        val found = listOf(quad!!.topLeft, quad.topRight, quad.bottomRight, quad.bottomLeft)
        expected.zip(found).forEach { (e, f) ->
            assertTrue("expected $e, found $f", hypot(e.x - f.x, e.y - f.y) <= within)
        }
    }

    private fun find(px: IntArray) = findCard(greyOf(px), w, h, guide)

    @Test
    fun aCardHeldStraightInTheGuideIsFoundToThePixel() {
        assertCorners(corners(), find(scene()), within = 0.75f)
    }

    @Test
    fun aCardHeldOffCentreAndSmallerThanTheGuideIsFound() {
        val at = corners(cx = 108f, cy = 116f, cw = 116f, ch = 162f)
        assertCorners(at, find(scene(cx = 108f, cy = 116f, cw = 116f, ch = 162f)), within = 1.5f)
    }

    @Test
    fun aTiltedCardIsFoundAtItsTilt() {
        assertCorners(corners(degrees = 7f), find(scene(degrees = 7f)), within = 2.5f)
        assertCorners(corners(degrees = -5f), find(scene(degrees = -5f)), within = 2.5f)
    }

    @Test
    fun theCardsEdgeIsFoundNotTheFrameJustInsideIt() {
        // The border-to-art line inside is as sharp as the edge; the card is the outer rectangle.
        val quad = find(scene())
        assertNotNull(quad)
        assertEquals(130f, quad!!.width, 2f)
        assertEquals(182f, quad.height, 2f)
    }

    @Test
    fun aDarkCardOnABusyTableIsStillFound() {
        val busy = { x: Int, y: Int -> 90 + ((x / 3 + y / 5) % 4) * 18 }
        assertCorners(corners(), find(scene(background = busy)), within = 2f)
    }

    @Test
    fun nothingIsFoundWhereThereIsNoCard() {
        assertNull(find(IntArray(w * h) { 0xFF7F7F7F.toInt() }))
        // A card the same shade as the table has no edges to find.
        assertNull(find(scene(background = { _, _ -> 22 }).map { if (it == 0xFF161616.toInt()) it else 0xFF161616.toInt() }.toIntArray()))
    }

    @Test
    fun theFlatteningMapPutsTheCardsCornersOnTheQuadsCorners() {
        val quad = CardQuad(Pt(10f, 20f), Pt(110f, 25f), Pt(115f, 160f), Pt(5f, 150f))
        val map = CardMap(quad)
        assertEquals(10f, map.x(0f, 0f), 0.01f); assertEquals(20f, map.y(0f, 0f), 0.01f)
        assertEquals(110f, map.x(1f, 0f), 0.01f); assertEquals(25f, map.y(1f, 0f), 0.01f)
        assertEquals(115f, map.x(1f, 1f), 0.01f); assertEquals(160f, map.y(1f, 1f), 0.01f)
        assertEquals(5f, map.x(0f, 1f), 0.01f); assertEquals(150f, map.y(0f, 1f), 0.01f)
    }

    @Test
    fun aTiltedCardFlattensBackToTheStraightCard() {
        // Flattened, the tilted card should look like the straight one flattened.
        val straight = scene()
        val tilted = scene(degrees = 7f)
        val a = flatten(straight, w, h, find(straight)!!, 63, 88)
        val b = flatten(tilted, w, h, find(tilted)!!, 63, 88)
        val off = a.indices.count { kotlin.math.abs((a[it] and 0xFF) - (b[it] and 0xFF)) > 40 }
        assertTrue("$off of ${a.size} pixels differ", off < a.size / 20)
    }
}
