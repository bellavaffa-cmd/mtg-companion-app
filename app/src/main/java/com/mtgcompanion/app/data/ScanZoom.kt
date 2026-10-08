package com.mtgcompanion.app.data

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/*
 * The scanner's zoom, as the user moves it: pinch on the preview, or − and + beside the "1.8×" chip
 * (a tap on the chip goes back to the default). Starts at [SCAN_ZOOM]; the last zoom chosen is kept
 * on the phone for next time (ui/scan/ScanZoomControl.kt). The web app's scanner does the same.
 *
 * Past [LENS_SWITCH_ZOOM] the phone may hand over to a telephoto that can't focus on a card held
 * close: allowed when the camera offers it, with a "Hold the card farther away" hint while there.
 * The default and the reset always stay under it.
 */

/** − and + move by this much below [ZOOM_COARSE_FROM]… */
const val ZOOM_FINE_STEP = 0.25f

/** …and by this much from there up, where a quarter is too small to see. */
const val ZOOM_COARSE_STEP = 0.5f

/** Where the steps get bigger. */
const val ZOOM_COARSE_FROM = 3f

/** How near two ratios are to count as the same (float rounding, a pinch landing on a step). */
private const val ZOOM_EPSILON = 0.01f

/** What this camera can zoom between (its zoomState's min and max ratios). */
data class ZoomRange(val min: Float, val max: Float) {

    /** [ratio] kept inside the range; a camera reporting nothing sensible stays at its minimum. */
    fun clamp(ratio: Float): Float {
        val lo = if (min.isFinite() && min > 0f) min else 1f
        val hi = if (max.isFinite() && max >= lo) max else lo
        return if (ratio.isFinite()) ratio.coerceIn(lo, hi) else lo
    }

    /** Whether the camera has any zoom to give at all; without it the control isn't shown. */
    val canZoom: Boolean get() = clamp(Float.MAX_VALUE) - clamp(0f) > ZOOM_EPSILON

    fun canZoomIn(ratio: Float): Boolean = zoomIn(ratio, this) > ratio + ZOOM_EPSILON / 2
    fun canZoomOut(ratio: Float): Boolean = zoomOut(ratio, this) < ratio - ZOOM_EPSILON / 2

    companion object {
        /** No zoom: a camera not bound yet, or one with nothing to give. */
        val NONE = ZoomRange(1f, 1f)
    }
}

/**
 * Where a scanner starts, and where a reset takes it: [preferred], kept under [LENS_SWITCH_ZOOM] and
 * inside what this camera offers. [SCAN_ZOOM] for a card; 1× for a binder page (PageScanScreen).
 */
fun defaultZoom(range: ZoomRange, preferred: Float = SCAN_ZOOM): Float =
    range.clamp(preferred.coerceAtMost(LENS_SWITCH_ZOOM))

/** The zoom to open on: the last one chosen on this phone ([saved]), or the default. */
fun startingZoom(saved: Float?, range: ZoomRange, preferred: Float = SCAN_ZOOM): Float =
    if (saved == null || !saved.isFinite() || saved <= 0f) defaultZoom(range, preferred) else range.clamp(saved)

/** + : the next step up — 1.8× goes to 2×, 2× to 2.25×, 3× to 3.5×. */
fun zoomIn(ratio: Float, range: ZoomRange): Float {
    val step = if (ratio + ZOOM_EPSILON >= ZOOM_COARSE_FROM) ZOOM_COARSE_STEP else ZOOM_FINE_STEP
    val k = floor((ratio + ZOOM_EPSILON) / step)
    return range.clamp((k + 1) * step)
}

/** − : the next step down — 1.8× goes to 1.75×, 3× to 2.75×, 3.5× to 3×. */
fun zoomOut(ratio: Float, range: ZoomRange): Float {
    val step = if (ratio - ZOOM_EPSILON > ZOOM_COARSE_FROM) ZOOM_COARSE_STEP else ZOOM_FINE_STEP
    val k = ceil((ratio - ZOOM_EPSILON) / step)
    return range.clamp((k - 1) * step)
}

/** A pinch: the zoom when it started times how far the fingers have spread, kept in range. */
fun pinchZoom(ratio: Float, factor: Float, range: ZoomRange): Float =
    if (!factor.isFinite() || factor <= 0f) range.clamp(ratio) else range.clamp(ratio * factor)

/** The chip's text: "1.8×", "2×", "2.25×" — quarters exactly, anything else to a tenth. */
fun zoomLabel(ratio: Float): String = zoomNumber(ratio) + "×"

/** What TalkBack says for the chip: "Zoom 1.8 times". */
fun zoomSpoken(ratio: Float): String = "Zoom ${zoomNumber(ratio)} times"

private fun zoomNumber(ratio: Float): String {
    val hundredths = (ratio * 100).roundToInt()
    if (hundredths % 25 == 0 && hundredths % 10 != 0) {
        return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
    }
    val tenths = (ratio * 10).roundToInt()
    return if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
}

/** Whether the zoom is where the phone may have switched to a lens that can't focus close. */
fun showFartherHint(ratio: Float): Boolean = ratio >= LENS_SWITCH_ZOOM - ZOOM_EPSILON / 2

/** The hint shown while [showFartherHint]: "Hold the card farther away". */
fun fartherHint(what: String = "card"): String = "Hold the $what farther away"
