package com.mtgcompanion.app.ui.scan

import android.graphics.Bitmap
import android.graphics.Matrix
import com.mtgcompanion.app.data.ScanBox

/**
 * The tiny line at the bottom of a card — set code, language and collector number — is what says
 * *which* printing you're holding: the alternate art, the borderless one, the one from the Secret
 * Lair. At the camera's working resolution it's a few pixels tall and usually unreadable in one
 * pass over the whole frame, so when the first read doesn't find it the bottom strip of the card is
 * cut out and blown up, and read again on its own.
 *
 * The web app does the same with a zoomed canvas of the strip — see readSmallPrint in src/scan/ocr.ts.
 */

/** The strip's share of the card, from the bottom edge up — the set line and the number sit here. */
private const val STRIP_TOP = 0.88f
private const val STRIP_BOTTOM = 0.99f

/**
 * The camera's picture turned upright, or null if it couldn't be. Done once per scanned card and
 * shared, since the small print and the art match both need it and turning a full frame is costly.
 */
fun uprightFrame(frame: Bitmap, rotation: Int): Bitmap? {
    if (rotation % 360 == 0) return frame
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return runCatching { Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, matrix, true) }.getOrNull()
}

/** How much the strip is blown up before it's read. Past this the reader gains nothing. */
const val STRIP_SCALE = 3

/**
 * The bottom strip of the guide, upright and blown up, or null when there's nothing worth reading.
 * [frame] is the camera's picture, [rotation] how far it has to be turned to stand upright.
 */
fun smallPrintStrip(frame: Bitmap, rotation: Int, guide: ScanBox?): Bitmap? {
    val upright = if (rotation % 360 == 0) frame else {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        runCatching { Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, matrix, true) }.getOrNull() ?: return null
    }
    val box = guide ?: ScanBox(0, 0, upright.width, upright.height)
    val left = box.left.coerceIn(0, upright.width - 1)
    val right = box.right.coerceIn(left + 1, upright.width)
    val height = box.bottom - box.top
    val top = (box.top + height * STRIP_TOP).toInt().coerceIn(0, upright.height - 1)
    val bottom = (box.top + height * STRIP_BOTTOM).toInt().coerceIn(top + 1, upright.height)
    if (right - left < 40 || bottom - top < 8) return null

    val strip = runCatching { Bitmap.createBitmap(upright, left, top, right - left, bottom - top) }.getOrNull() ?: return null
    return runCatching {
        Bitmap.createScaledBitmap(strip, strip.width * STRIP_SCALE, strip.height * STRIP_SCALE, true)
    }.getOrNull()
}
