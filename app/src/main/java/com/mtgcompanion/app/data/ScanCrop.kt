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

/** This box kept inside a picture [width] x [height]; a box grown past the edge is cut back to it. */
fun ScanBox.clampedTo(width: Int, height: Int): ScanBox = ScanBox(
    left = left.coerceIn(0, width),
    top = top.coerceIn(0, height),
    right = right.coerceIn(0, width),
    bottom = bottom.coerceIn(0, height)
)

/** This box as seen from inside [outer] — its position once [outer] is cut out as a picture of its own. */
fun ScanBox.relativeTo(outer: ScanBox): ScanBox =
    ScanBox(left - outer.left, top - outer.top, right - outer.left, bottom - outer.top)

/**
 * Where a box in the upright picture sits in the camera's own, sideways one. The camera hands over
 * its picture as the sensor sees it, with [rotation] saying how far to turn it upright — so to cut
 * the guide out *before* turning it (turning a whole frame is the costly part), the guide has to be
 * found in the sensor's picture first. [sensorWidth] x [sensorHeight] is that picture's size.
 */
fun sensorBox(upright: ScanBox, rotation: Int, sensorWidth: Int, sensorHeight: Int): ScanBox = when ((rotation % 360 + 360) % 360) {
    // Turned a quarter clockwise: the upright picture's x runs down the sensor's, backwards.
    90 -> ScanBox(upright.top, sensorHeight - upright.right, upright.bottom, sensorHeight - upright.left)
    180 -> ScanBox(sensorWidth - upright.right, sensorHeight - upright.bottom, sensorWidth - upright.left, sensorHeight - upright.top)
    270 -> ScanBox(sensorWidth - upright.bottom, upright.left, sensorWidth - upright.top, upright.right)
    else -> upright
}

/**
 * How far the strip of small print is blown up before it's read, for a card [cardHeight] pixels
 * tall in the picture. The set line's letters are about 1.5% of a card's height; the reader wants
 * them around 32 px, and past that it gains nothing but work — a fixed 3x made the strip more than
 * twice the pixels it needed at 1080p, and the strip was the slowest read in a scan. Never shrunk,
 * never more than 3x.
 */
fun smallPrintScale(cardHeight: Int): Float {
    if (cardHeight <= 0) return 3f
    return (SMALL_PRINT_TEXT_PX / (cardHeight * SMALL_PRINT_SHARE)).coerceIn(1f, 3f)
}

/** The small print's letters as a share of the card's height. */
private const val SMALL_PRINT_SHARE = 0.015f

/** How tall the reader wants the small print's letters, in pixels. */
private const val SMALL_PRINT_TEXT_PX = 32f
