package com.mtgcompanion.app.ui.scan

import android.graphics.Bitmap
import com.mtgcompanion.app.data.CardQuad
import com.mtgcompanion.app.data.GRID_H
import com.mtgcompanion.app.data.GRID_W
import com.mtgcompanion.app.data.PRINTING_INSET
import com.mtgcompanion.app.data.ScanBox
import com.mtgcompanion.app.data.cardShaped
import com.mtgcompanion.app.data.findCards
import com.mtgcompanion.app.data.flatten
import com.mtgcompanion.app.data.greyOf
import com.mtgcompanion.app.data.signatureFromPixels

/**
 * The card found in the camera's picture (see findCards in data/CardEdges.kt), ready to be looked at
 * straight on: its look for matching the printing, and its small print flattened out for reading.
 * Mirrors the web app's src/scan/flatCard.ts.
 */
class FlatCard private constructor(
    private val px: IntArray,
    private val width: Int,
    private val height: Int,
    /** The likeliest outlines of the card, best first, in the picture's pixels. */
    val quads: List<CardQuad>
) {
    /**
     * What the card looks like, for [com.mtgcompanion.app.data.bestPrinting]: through each of the
     * likeliest outlines, taken as the card's edge and as its printed frame, with the same trim as a
     * printing's picture and a hair either way of it. Whichever of them is really the card is the
     * one that will match.
     */
    fun signatures(): List<FloatArray> {
        val out = ArrayList<FloatArray>(quads.size * 2 * LOOK_JITTER.size * LOOK_JITTER.size)
        for (quad in quads) for (frame in listOf(false, true)) {
            // The card's own 0-1 across and down, in the outline's: the same, or reaching past the frame.
            fun u(c: Float) = if (frame) (c - BORDER_SIDE) / (1 - 2 * BORDER_SIDE) else c
            fun v(c: Float) = if (frame) (c - BORDER_TOP) / (1 - BORDER_TOP - BORDER_BOTTOM) else c
            for (du in LOOK_JITTER) for (dv in LOOK_JITTER) {
                val look = flatten(
                    px, width, height, quad, LOOK_W, LOOK_H,
                    u(PRINTING_INSET + du), v(PRINTING_INSET + dv), u(1 - PRINTING_INSET + du), v(1 - PRINTING_INSET + dv)
                )
                out += signatureFromPixels(look, LOOK_W, LOOK_H)
            }
        }
        return out
    }

    /**
     * The strip of small print along the bottom of the likeliest outline, flattened and blown up so
     * its letters stand about as tall as the reader wants them.
     */
    fun smallPrintStrip(): Bitmap? {
        val stripH = (SMALL_PRINT_LETTER_PX * (STRIP_BOTTOM - STRIP_TOP) / SMALL_PRINT_SHARE).toInt()
        val stripW = (stripH * (63f / 88f) / (STRIP_BOTTOM - STRIP_TOP)).toInt()
        val strip = flatten(px, width, height, quads.first(), stripW, stripH, 0f, STRIP_TOP, 1f, STRIP_BOTTOM)
        return runCatching { Bitmap.createBitmap(strip, stripW, stripH, Bitmap.Config.ARGB_8888) }.getOrNull()
    }

    companion object {
        /** How wide the picture is shrunk to for finding the edges: plenty to place them, and quick. */
        private const val SEARCH_W = 240

        /** How many of the likeliest outlines the card's look is measured through. */
        private const val OUTLINES = 3

        /**
         * Where a card's printed frame sits inside it: its black (or white) border is about this share
         * of the card on each side — thicker at the bottom, where the small print is. On a mat the same
         * colour as the border, the frame is the rectangle the edges find, and the card is this much bigger.
         */
        private const val BORDER_SIDE = 0.039f
        private const val BORDER_TOP = 0.028f
        private const val BORDER_BOTTOM = 0.065f

        private const val LOOK_W = GRID_W * 16
        private const val LOOK_H = GRID_H * 16
        private val LOOK_JITTER = floatArrayOf(-0.012f, 0f, 0.012f)

        private const val STRIP_TOP = 0.88f
        private const val STRIP_BOTTOM = 0.985f
        private const val SMALL_PRINT_SHARE = 0.015f
        private const val SMALL_PRINT_LETTER_PX = 32f

        /**
         * The card in [upright] (the camera's picture, turned upright), held near [guide] — or null
         * when its edges can't be made out, and the guide is used instead.
         */
        fun find(upright: Bitmap, guide: ScanBox?): FlatCard? {
            if (guide == null || upright.width < 40 || upright.height < 40) return null
            val scale = SEARCH_W.toFloat() / upright.width
            val sw = SEARCH_W
            val sh = maxOf(1, (upright.height * scale).toInt())
            val small = runCatching { Bitmap.createScaledBitmap(upright, sw, sh, true) }.getOrNull() ?: return null
            val smallPx = IntArray(sw * sh)
            small.getPixels(smallPx, 0, sw, 0, 0, sw, sh)
            val card = guide.cardShaped()
            val expected = ScanBox(
                (card.left * scale).toInt(), (card.top * scale).toInt(),
                (card.right * scale).toInt(), (card.bottom * scale).toInt()
            )
            val quads = findCards(greyOf(smallPx), sw, sh, expected, OUTLINES).ifEmpty { return null }
            val px = IntArray(upright.width * upright.height)
            upright.getPixels(px, 0, upright.width, 0, 0, upright.width, upright.height)
            return FlatCard(px, upright.width, upright.height, quads.map { it.scaled(1 / scale) })
        }
    }
}
