package com.mtgcompanion.app.data

import kotlin.math.abs
import kotlin.math.max

/*
 * Auto zoom and auto focus for the card scanner (Settings › Scanner › Auto zoom and focus, on unless
 * turned off). Every few frames the scanner looks for the card's edges inside the guide and measures
 * how crisp the picture is (ScanViewModel's card look); these decide what the camera does about it:
 *
 *  - AutoZoom: zoom gently until the card fills about [AUTO_ZOOM_TARGET] of the guide.
 *  - Refocus: ask the camera to focus again — on the card, metered on its title bar so a foil's
 *    glare doesn't darken it — when the card has stayed soft for a while.
 *
 * Pinching or − / + hands the zoom back to the user until the chip is tapped (ScanZoomControl.kt).
 * The web app's scanner does the same with the same numbers.
 */

/** How much of the guide the card should fill: its height (or width) against the card-shaped guide. */
const val AUTO_ZOOM_TARGET = 0.875f

/** Nothing is done while the fill is within this much either side of the target (0.775–0.975). */
const val AUTO_ZOOM_DEADBAND = 0.10f

/** The most one adjustment moves the zoom, either way. */
const val AUTO_ZOOM_MAX_STEP = 0.15f

/** At most one adjustment this often. */
const val AUTO_ZOOM_EVERY_MS = 400L

/** Card looks in a row with the card found before the zoom moves — and again after each move. */
const val AUTO_ZOOM_SEEN_FRAMES = 3

/** Auto zoom stays this far under [LENS_SWITCH_ZOOM], so it never risks the telephoto. */
const val AUTO_ZOOM_LENS_MARGIN = 0.1f

/**
 * How far toward the guide's edge the card's farthest edge may reach (from the middle): zooming in
 * stops there, and a card already past it is zoomed out.
 */
const val AUTO_ZOOM_EDGE_REACH = 0.98f

/** How long the card has to stay soft before the camera is asked to focus again. */
const val REFOCUS_SOFT_MS = 700L

/** At most one auto refocus this often, so the lens doesn't hunt. */
const val REFOCUS_EVERY_MS = 1500L

/** The focus point is sent again only once the card's middle has moved this share of the frame. */
const val REMETER_MOVE = 0.05f

/** A tap on the preview focuses there, and auto refocus waits this long before taking over again. */
const val REFOCUS_TAP_PAUSE_MS = 4000L

/** Soft: under this share of the crispest the card has looked lately… */
const val SOFT_SHARE = 0.6f

/**
 * …or under this, whatever the card has looked like. A first guess on [sharpness]'s scale, to be
 * tuned from tester scan reports (each carries the frame's "sharpness" fact).
 */
const val SOFT_FLOOR = 40f

/** How fast the crispest-lately forgets, per card look (about halves in 7 seconds at 5 looks a second). */
const val SHARP_PEAK_DECAY = 0.98f

/** No card is taken this long after an auto zoom step or an auto refocus: the picture is moving. */
const val AUTO_SETTLE_MS = 350L
const val REFOCUS_SETTLE_MS = 600L

/** Where the exposure is metered: the title bar, this share of the card's height down from its top. */
const val TITLE_BAR_AT = 0.08f

/**
 * The card as one frame showed it: its bounds in fractions of the drawn guide (0..1 is inside it,
 * outside can go a little past), and [fill] — how much of the card-shaped guide it fills.
 */
data class CardSighting(val left: Float, val top: Float, val right: Float, val bottom: Float, val fill: Float) {
    val centreX: Float get() = (left + right) / 2
    val centreY: Float get() = (top + bottom) / 2
}

/** How much of the card-shaped guide ([guideWidth] x [guideHeight]) a card [cardWidth] x [cardHeight] fills. */
fun cardFit(cardWidth: Float, cardHeight: Float, guideWidth: Float, guideHeight: Float): Float {
    if (guideWidth <= 0f || guideHeight <= 0f) return 0f
    return max(cardWidth / guideWidth, cardHeight / guideHeight)
}

/** The highest zoom auto zoom will go to on a camera offering [range]. */
fun autoZoomCeiling(range: ZoomRange): Float = range.clamp(LENS_SWITCH_ZOOM - AUTO_ZOOM_LENS_MARGIN)

/**
 * How much more the zoom may grow before the card's farthest edge from the middle passes
 * [AUTO_ZOOM_EDGE_REACH] of the way to the guide's edge — under 1 when it's past already. Zooming
 * grows everything about the middle of the frame, which is the middle of the guide.
 */
fun roomToZoom(card: CardSighting): Float {
    val farthest = maxOf(abs(card.left - 0.5f), abs(card.right - 0.5f), abs(card.top - 0.5f), abs(card.bottom - 0.5f))
    if (farthest <= 0.001f) return Float.MAX_VALUE
    return (0.5f * AUTO_ZOOM_EDGE_REACH / farthest).coerceAtLeast(0f)
}

/**
 * One auto zoom decision, on its own: the zoom to go to from [zoom] for [card], or null to stay.
 * Toward [AUTO_ZOOM_TARGET] by at most [AUTO_ZOOM_MAX_STEP]; nothing inside the deadband; never past
 * [autoZoomCeiling] or the camera's minimum, never so far in that the card would run off the guide —
 * and out, whatever the fill, when it already has. [AutoZoom] adds the timing.
 */
fun autoZoomStep(card: CardSighting, zoom: Float, range: ZoomRange): Float? {
    if (card.fill <= 0f || !card.fill.isFinite()) return null
    val room = roomToZoom(card)
    val wanted = when {
        room < 1f -> zoom * room
        card.fill in (AUTO_ZOOM_TARGET - AUTO_ZOOM_DEADBAND)..(AUTO_ZOOM_TARGET + AUTO_ZOOM_DEADBAND) -> return null
        else -> zoom * AUTO_ZOOM_TARGET / card.fill
    }
    var target = zoom + (wanted - zoom).coerceIn(-AUTO_ZOOM_MAX_STEP, AUTO_ZOOM_MAX_STEP)
    if (target > zoom) {
        target = minOf(target, autoZoomCeiling(range), zoom * room)
        if (target <= zoom + 0.005f) return null
    } else {
        target = target.coerceAtLeast(range.clamp(0f))
        if (target >= zoom - 0.005f) return null
    }
    target = range.clamp(target)
    return if (abs(target - zoom) < 0.005f) null else target
}

/** Decides auto zoom's next step from one card look at a time. */
class AutoZoom {
    private var seen = 0
    private var lastStepAt = Long.MIN_VALUE / 2

    /** Start over: the card left, or the zoom was moved by hand. */
    fun reset() {
        seen = 0
    }

    /**
     * One look: [card] where the card was (null when it wasn't found), at zoom [zoom] on a camera
     * offering [range], at [now] ms. The zoom to go to, or null to leave it.
     */
    fun next(card: CardSighting?, zoom: Float, range: ZoomRange, now: Long): Float? {
        if (card == null || card.fill <= 0f || !card.fill.isFinite()) {
            seen = 0
            return null
        }
        seen++
        if (seen < AUTO_ZOOM_SEEN_FRAMES) return null
        if (now - lastStepAt < AUTO_ZOOM_EVERY_MS) return null
        val target = autoZoomStep(card, zoom, range) ?: return null
        lastStepAt = now
        seen = 0
        return target
    }
}

/**
 * Decides when to ask the camera to focus again: the card found, soft ([sharpness] low) and its title
 * not reading, for [REFOCUS_SOFT_MS] — at most every [REFOCUS_EVERY_MS], and not for a while after a tap.
 */
class Refocus {
    private var peak = 0f
    private var softSince: Long? = null
    private var lastAt = Long.MIN_VALUE / 2
    private var pausedUntil = Long.MIN_VALUE / 2

    /** A tap focused somewhere: leave the focus alone for [REFOCUS_TAP_PAUSE_MS]. */
    fun tapped(now: Long) {
        pausedUntil = now + REFOCUS_TAP_PAUSE_MS
        lastAt = now
        softSince = null
    }

    /** Whether a tap is still holding auto refocus off. */
    fun paused(now: Long): Boolean = now < pausedUntil

    /** The picture changed scale (the zoom moved): what counted as crisp before no longer compares. */
    fun reset() {
        peak = 0f
        softSince = null
    }

    /** Any refocus just went out (the periodic one, or a re-aim at a moved card): counts for the spacing. */
    fun focused(now: Long) {
        lastAt = now
    }

    /** Whether a refocus may go out now at all. */
    fun mayFocus(now: Long): Boolean = !paused(now) && now - lastAt >= REFOCUS_EVERY_MS

    /**
     * One look: how crisp it was ([sharp], from [sharpness]), whether the card was found, and whether
     * its title read just now ([titleRead] — a title that reads is sharp enough). True: refocus now.
     */
    fun onLook(sharp: Float, cardFound: Boolean, now: Long, titleRead: Boolean = false): Boolean {
        if (!cardFound || !sharp.isFinite()) {
            reset()
            return false
        }
        peak = max(sharp, peak * SHARP_PEAK_DECAY)
        val soft = !titleRead && (sharp < SOFT_FLOOR || sharp < SOFT_SHARE * peak)
        if (!soft) {
            softSince = null
            return false
        }
        val since = softSince ?: now.also { softSince = it }
        if (now - since < REFOCUS_SOFT_MS) return false
        if (!mayFocus(now)) return false
        lastAt = now
        reset()
        return true
    }
}

/** A point in the preview's own pixels. */
data class PreviewPoint(val x: Float, val y: Float)

/**
 * A point given in fractions of the drawn guide ([u] across, [v] down), in the preview's pixels
 * ([previewWidth] x [previewHeight]). The guide is [GUIDE_WIDTH] x [GUIDE_HEIGHT] of the preview,
 * centred — and the camera's picture is fitted to the preview evenly, so this is exact.
 */
fun guideToPreview(u: Float, v: Float, previewWidth: Int, previewHeight: Int): PreviewPoint {
    val gw = previewWidth * GUIDE_WIDTH
    val gh = previewHeight * GUIDE_HEIGHT
    val x = (previewWidth - gw) / 2 + u.coerceIn(0f, 1f) * gw
    val y = (previewHeight - gh) / 2 + v.coerceIn(0f, 1f) * gh
    return PreviewPoint(x, y)
}

/** Where to focus on [card] — its middle — and where to meter its exposure: its title bar. In guide fractions. */
fun cardMeteringPoints(card: CardSighting): Pair<Pair<Float, Float>, Pair<Float, Float>> {
    val focus = card.centreX to card.centreY
    val exposure = card.centreX to (card.top + (card.bottom - card.top) * TITLE_BAR_AT)
    return focus to exposure
}

/**
 * Whether the card has moved far enough from where focus was last aimed ([lastU], [lastV]) to aim
 * again: its middle more than [REMETER_MOVE] of the frame away. Points are in guide fractions.
 */
fun movedToRemeter(card: CardSighting, lastU: Float, lastV: Float): Boolean {
    val dx = (card.centreX - lastU) * GUIDE_WIDTH
    val dy = (card.centreY - lastV) * GUIDE_HEIGHT
    return kotlin.math.hypot(dx, dy) > REMETER_MOVE
}
