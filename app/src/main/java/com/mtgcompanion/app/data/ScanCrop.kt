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

/**
 * The framing guide's share of the preview — drawn by ScanScreen, read by the scanner.
 *
 * These decide how many pixels the small print gets, which is the whole reason the set code does or
 * doesn't read. The card is fitted to the guide by [cardShaped], so the guide's smaller side bounds
 * it. At 0.8 x 0.55, a 1080x1920 frame shown in a 1080x2316 preview put the card at about 716x1001
 * — the set line's letters being about [SMALL_PRINT_SHARE] of a card's height, that left them 15 px
 * tall, under the reader's floor of roughly 16. Which is why the set code read on under 2% of scans
 * rather than never: it was always a near miss.
 *
 * The width is the scarce side and it is scarcer than the frame suggests. The preview is taller than
 * the picture's shape, so the sides are cropped away and only about 895 of the 1080 columns are ever
 * visible; the guide is a share of *that*. A full-width guide would give the letters 18.8 px and
 * that is the ceiling at this resolution — 0.96 takes 18.0 of it and keeps a margin to hold the card
 * against. Going further means more sensor pixels, not a bigger guide.
 *
 * The height only has to stay out of the way: below about 0.63 it becomes the binding side again and
 * widening the guide buys nothing at all.
 */
const val GUIDE_WIDTH = 0.96f
const val GUIDE_HEIGHT = 0.66f

/** How much bigger than the drawn guide the reader looks, so a card held a little large still reads. */
const val GUIDE_SLACK = 0.2f

/**
 * How far the camera is zoomed in while scanning.
 *
 * The guide can only divide up the pixels the frame already has; the zoom decides how many of them
 * land on the card. Measured, a card held comfortably filled about 55% of the guide, which left the
 * set line's letters at well under half the size the reader needs. Holding the card closer is not
 * the answer — the main lens cannot focus nearer than 10 cm, and the nearer it gets the likelier it
 * blurs. Zooming moves the card's size in the frame without moving the card.
 *
 * This is not the empty magnification it sounds like: the frame is a small downsample of a much
 * larger sensor, so zooming crops the sensor's own readout before that downsample and puts real
 * sensor pixels on the card rather than interpolated ones.
 *
 * 1.8x takes that 55% to roughly a filled guide. It must stay below [LENS_SWITCH_ZOOM].
 *
 * This is where the scanner starts and what a reset goes back to; the user can pinch or step it
 * from there (ScanZoom.kt), and the last zoom chosen is kept. Nothing here depends on the zoom: the
 * camera zooms every stream alike, so the guide sits at the same share of the frame at any zoom, and
 * the small print is scaled by the card's measured height ([smallPrintScale]), not by a zoom assumed.
 */
const val SCAN_ZOOM = 1.8f

/**
 * Where the phone stops cropping the main lens and switches to a telephoto one. The telephotos
 * cannot focus closer than 40 cm (the 10x, not until 80 cm), so a card held to be scanned would
 * simply never come into focus. The default zoom stays underneath this; the user may go past it
 * when the camera offers it, with a "Hold the card farther away" hint while there ([showFartherHint]).
 */
const val LENS_SWITCH_ZOOM = 2.9f

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
const val SMALL_PRINT_SHARE = 0.015f

/** How tall the reader wants the small print's letters, in pixels. */
private const val SMALL_PRINT_TEXT_PX = 32f

/**
 * Where the small print is looked for when the card's own edges weren't found: the band [top]..[bottom]
 * (shares of a card's height) of the card the guide holds — fitted to a card's shape ([cardShaped]) —
 * not of the guide. The guide is a different shape from a card, so its bottom band sat partly below
 * the card, on whatever lay there: in a pile, the next card's small print.
 */
fun smallPrintBand(guide: ScanBox, top: Float, bottom: Float): ScanBox {
    val card = guide.cardShaped()
    val height = card.bottom - card.top
    return ScanBox(card.left, card.top + (height * top).toInt(), card.right, card.top + (height * bottom).toInt())
}
