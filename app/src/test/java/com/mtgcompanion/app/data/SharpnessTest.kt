package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Choosing the crispest frame — the one thing that decides whether the card index can work. */
class SharpnessTest {

    private fun grey(width: Int, height: Int, at: (Int, Int) -> Int): IntArray =
        IntArray(width * height) { i ->
            val v = at(i % width, i / width).coerceIn(0, 255)
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }

    /**
     * A checkerboard: as much edge as a picture can have. The squares are a share of the picture,
     * not a fixed number of pixels, so the same scene at two sizes really is the same scene.
     */
    private fun crisp(size: Int) = grey(size, size) { x, y ->
        val cell = size / 32
        if ((x / cell + y / cell) % 2 == 0) 20 else 235
    }

    /** The same board with its edges smeared over a few pixels. */
    private fun soft(size: Int, spread: Int) = grey(size, size) { x, y ->
        val fx = ((x % 16) - 8).toFloat() / spread
        val fy = ((y % 16) - 8).toFloat() / spread
        val ramp = (fx.coerceIn(-1f, 1f) * fy.coerceIn(-1f, 1f) + 1f) / 2f
        (20 + ramp * 215).toInt()
    }

    @Test
    fun `a flat picture has nothing to measure`() {
        assertEquals(0f, sharpness(grey(64, 64) { _, _ -> 128 }, 64, 64), 0.001f)
    }

    @Test
    fun `a crisp picture scores far above a soft one`() {
        val sharp = sharpness(crisp(256), 256, 256)
        val blurred = sharpness(soft(256, 6), 256, 256)
        assertTrue("crisp $sharp should beat soft $blurred", sharp > blurred * 2)
    }

    @Test
    fun `softer and softer scores lower and lower`() {
        val a = sharpness(soft(256, 2), 256, 256)
        val b = sharpness(soft(256, 5), 256, 256)
        val c = sharpness(soft(256, 8), 256, 256)
        assertTrue("$a > $b > $c", a > b && b > c)
    }

    @Test
    fun `the same scene at two sizes scores about the same`() {
        // Frames don't all arrive at one size, and a bigger one must not win for being bigger.
        val big = sharpness(crisp(512), 512, 512)
        val small = sharpness(crisp(256), 256, 256)
        assertTrue("big $big vs small $small", big in (small * 0.6f)..(small * 1.6f))
    }

    @Test
    fun `nonsense sizes are refused rather than crashing`() {
        assertEquals(0f, sharpness(IntArray(0), 0, 0), 0.001f)
        assertEquals(0f, sharpness(IntArray(4), 2, 2), 0.001f)
        assertEquals(0f, sharpness(IntArray(4), 64, 64), 0.001f)
    }

    @Test
    fun `noise alone does not read as sharpness above a real edge`() {
        val noisy = grey(256, 256) { _, _ -> 128 + Random(4).nextInt(-6, 7) }
        assertTrue(sharpness(crisp(256), 256, 256) > sharpness(noisy, 256, 256))
    }
}
