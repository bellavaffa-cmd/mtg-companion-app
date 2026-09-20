package com.mtgcompanion.app.data

/**
 * Where the framing guide sits in the camera's own picture. The reader sees the whole frame, so
 * without this a card lying next to the one being scanned is read as readily as the card itself —
 * and half of someone else's title is what fouls up a scan. Only text inside the guide counts.
 *
 * The web app does the same with guideInVideo (src/scan/guide.ts); the arithmetic is the same
 * because the preview fills its space and crops what doesn't fit (FILL_CENTER).
 */

/** A box in the picture's own pixels. */
data class ScanBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {

    /** Whether the middle of a line of text falls inside this box. */
    fun holdsCentreOf(left: Int, top: Int, right: Int, bottom: Int): Boolean {
        val x = (left + right) / 2
        val y = (top + bottom) / 2
        return x in this.left..this.right && y in this.top..this.bottom
    }

    /** This box with [by] of its size added on each side — room for a card held a little large. */
    fun grownBy(by: Float): ScanBox {
        val dx = ((right - left) * by / 2).toInt()
        val dy = ((bottom - top) * by / 2).toInt()
        return ScanBox(left - dx, top - dy, right + dx, bottom + dy)
    }
}

/** The framing guide's share of the preview — drawn by ScanScreen, read by the scanner. */
const val GUIDE_WIDTH = 0.8f
const val GUIDE_HEIGHT = 0.55f

/** How much bigger than the drawn guide the reader looks, so a card held a little large still reads. */
const val GUIDE_SLACK = 0.2f

/**
 * Frames in a row with text read but none of it inside the guide before the guide is set aside.
 * Some phone's reader may measure its picture differently from what's worked out here; scanning
 * nothing at all would be worse than reading the whole frame as the app used to.
 */
const val GUIDE_GIVE_UP_FRAMES = 30

/**
 * The guide's box in a picture [imageWidth] × [imageHeight] (as the reader sees it, already turned
 * upright), shown in a preview [previewWidth] × [previewHeight] that fills its space and crops the
 * rest. [widthFraction] and [heightFraction] are the guide's size against the preview — see the
 * framing guide in ScanScreen. Null when any side is zero.
 */
fun guideInImage(
    imageWidth: Int,
    imageHeight: Int,
    previewWidth: Int,
    previewHeight: Int,
    widthFraction: Float,
    heightFraction: Float
): ScanBox? {
    if (imageWidth <= 0 || imageHeight <= 0 || previewWidth <= 0 || previewHeight <= 0) return null
    // The picture is scaled up until it covers the preview; what sticks out is cropped away.
    val scale = maxOf(previewWidth.toFloat() / imageWidth, previewHeight.toFloat() / imageHeight)
    val visibleWidth = previewWidth / scale
    val visibleHeight = previewHeight / scale
    val halfWidth = visibleWidth * widthFraction / 2
    val halfHeight = visibleHeight * heightFraction / 2
    val centreX = imageWidth / 2f
    val centreY = imageHeight / 2f
    return ScanBox(
        left = (centreX - halfWidth).toInt().coerceAtLeast(0),
        top = (centreY - halfHeight).toInt().coerceAtLeast(0),
        right = (centreX + halfWidth).toInt().coerceAtMost(imageWidth),
        bottom = (centreY + halfHeight).toInt().coerceAtMost(imageHeight)
    )
}
