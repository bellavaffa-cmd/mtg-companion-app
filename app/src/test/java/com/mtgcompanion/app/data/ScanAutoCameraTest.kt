package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Auto zoom's steps and auto focus's refocusing, decided from card looks. */
class ScanAutoCameraTest {

    private val phone = ZoomRange(1f, 10f)

    /** A centred card filling [fill] of the guide. */
    private fun centred(fill: Float) = CardSighting(0.5f - fill / 2, 0.5f - fill / 2, 0.5f + fill / 2, 0.5f + fill / 2, fill)

    /** Feeds [card] [times] times, 100 ms apart from [from]; the first step it takes, if any. */
    private fun AutoZoom.feed(card: CardSighting?, zoom: Float, times: Int, from: Long = 1000L): Float? {
        var out: Float? = null
        repeat(times) { i -> next(card, zoom, phone, from + i * 100L)?.let { if (out == null) out = it } }
        return out
    }

    @Test
    fun aSmallCardIsZoomedInGently() {
        val z = AutoZoom()
        assertNull("not until it's been seen a few times", z.next(centred(0.6f), 1.8f, phone, 0))
        assertNull(z.next(centred(0.6f), 1.8f, phone, 100))
        val step = z.next(centred(0.6f), 1.8f, phone, 200)
        assertNotNull(step)
        assertEquals(1.8f + AUTO_ZOOM_MAX_STEP, step!!, 0.001f)
    }

    @Test
    fun aBigCardIsZoomedOut() {
        val step = AutoZoom().feed(centred(1.05f).copy(left = 0f, right = 1f, top = 0f, bottom = 1f), 2f, 3)
        assertNotNull(step)
        assertTrue(step!! < 2f)
        assertTrue(step >= 2f - AUTO_ZOOM_MAX_STEP - 0.001f)
    }

    @Test
    fun aCardNearTheTargetIsLeftAlone() {
        assertNull(AutoZoom().feed(centred(AUTO_ZOOM_TARGET), 1.8f, 5))
        assertNull(AutoZoom().feed(centred(0.78f), 1.8f, 5))
        assertNull(AutoZoom().feed(centred(0.97f), 1.8f, 5))
        assertNotNull(AutoZoom().feed(centred(0.76f), 1.8f, 5))
        assertNull(autoZoomStep(centred(0.9f), 1.8f, phone))
    }

    @Test
    fun smallTargetsTakeSmallSteps() {
        // 0.77 at 1× wants 0.875 / 0.77 = 1.14×: less than a full step, so it goes straight there.
        val step = AutoZoom().feed(centred(0.77f), 1f, 3)!!
        assertEquals(AUTO_ZOOM_TARGET / 0.77f, step, 0.001f)
    }

    @Test
    fun stepsAreSpacedOut() {
        val z = AutoZoom()
        assertNotNull(z.feed(centred(0.5f), 1.5f, 3, from = 0))
        // Seen three more times at once, but not 400 ms since the last step.
        assertNull(z.next(centred(0.5f), 1.65f, phone, 250))
        assertNull(z.next(centred(0.5f), 1.65f, phone, 300))
        assertNull(z.next(centred(0.5f), 1.65f, phone, 350))
        assertNotNull(z.next(centred(0.5f), 1.65f, phone, 600))
    }

    @Test
    fun theCardMustBeSeenInARow() {
        val z = AutoZoom()
        z.next(centred(0.5f), 1.8f, phone, 0)
        z.next(centred(0.5f), 1.8f, phone, 100)
        assertNull(z.next(null, 1.8f, phone, 200))
        assertNull(z.next(centred(0.5f), 1.8f, phone, 300))
    }

    @Test
    fun neverPastTheLensSwitch() {
        val step = AutoZoom().feed(centred(0.5f), 2.75f, 3)
        assertEquals(LENS_SWITCH_ZOOM - AUTO_ZOOM_LENS_MARGIN, step!!, 0.001f)
        assertNull(AutoZoom().feed(centred(0.5f), LENS_SWITCH_ZOOM - AUTO_ZOOM_LENS_MARGIN, 3))
        // Nor past what the camera has.
        val small = ZoomRange(1f, 2f)
        val z = AutoZoom()
        var last: Float? = null
        repeat(3) { last = z.next(centred(0.5f), 1.95f, small, it * 100L) }
        assertEquals(2f, last!!, 0.001f)
    }

    @Test
    fun neverBelowTheCameraMinimum() {
        val huge = CardSighting(0f, 0f, 1f, 1f, 1.3f)
        val out = AutoZoom().feed(huge, 1.05f, 3)!!
        assertTrue(out < 1.05f && out >= 1f)
        assertNull(AutoZoom().feed(huge, 1f, 3))
    }

    @Test
    fun neverSoFarTheCardWouldLeaveTheGuide() {
        // Small, but held off to one side: its left edge is 0.47 from the middle already.
        val offside = CardSighting(0.03f, 0.3f, 0.53f, 0.7f, 0.6f)
        val room = roomToZoom(offside)
        assertEquals(0.5f * AUTO_ZOOM_EDGE_REACH / 0.47f, room, 0.001f)
        val step = AutoZoom().feed(offside, 1.8f, 3)!!
        assertEquals(1.8f * room, step, 0.001f)
        // Past the edge already, even at the right size: zoomed out until it's back inside.
        val over = CardSighting(-0.02f, 0.06f, 0.86f, 0.94f, AUTO_ZOOM_TARGET)
        val out = autoZoomStep(over, 1.8f, phone)!!
        assertEquals(1.8f * roomToZoom(over), out, 0.001f)
        assertTrue(out < 1.8f)
    }

    @Test
    fun theFillIsTheBiggerSide() {
        assertEquals(0.9f, cardFit(90f, 50f, 100f, 100f), 0.001f)
        assertEquals(0.8f, cardFit(50f, 80f, 100f, 100f), 0.001f)
        assertEquals(0f, cardFit(50f, 80f, 0f, 100f), 0.001f)
    }

    @Test
    fun aCardThatStaysSoftIsRefocused() {
        val f = Refocus()
        assertFalse(f.onLook(300f, true, 0))
        // Soft against what it looked like: but only once it's stayed soft a while.
        assertFalse(f.onLook(100f, true, 100))
        assertFalse(f.onLook(100f, true, 500))
        assertTrue(f.onLook(100f, true, 100 + REFOCUS_SOFT_MS))
        // Not again straight away, however soft.
        assertFalse(f.onLook(10f, true, 1000))
        assertFalse(f.onLook(10f, true, 100 + REFOCUS_SOFT_MS + 1000))
        assertTrue(f.onLook(10f, true, 100 + REFOCUS_SOFT_MS + REFOCUS_EVERY_MS))
    }

    @Test
    fun aCrispCardOrNoCardIsLeftAlone() {
        val f = Refocus()
        repeat(20) { assertFalse(f.onLook(300f, true, it * 150L)) }
        val g = Refocus()
        repeat(20) { assertFalse(g.onLook(5f, false, it * 150L)) }
        // A soft spell broken by a crisp frame starts over.
        val h = Refocus()
        h.onLook(10f, true, 0)
        h.onLook(300f, true, 400)
        assertFalse(h.onLook(10f, true, 800))
        assertFalse(h.onLook(10f, true, 1200))
    }

    @Test
    fun aTapHoldsAutoRefocusOff() {
        val f = Refocus()
        f.tapped(0)
        assertTrue(f.paused(REFOCUS_TAP_PAUSE_MS - 1))
        repeat(25) { assertFalse(f.onLook(5f, true, it * 150L)) }
        assertFalse(f.paused(REFOCUS_TAP_PAUSE_MS))
        // Soft all through the pause: refocused as soon as it's over.
        assertTrue(f.onLook(5f, true, REFOCUS_TAP_PAUSE_MS))
    }

    @Test
    fun aTitleThatReadsIsSharpEnough() {
        val f = Refocus()
        f.onLook(300f, true, 0)
        repeat(20) { assertFalse(f.onLook(5f, true, 100 + it * 150L, titleRead = true)) }
    }

    @Test
    fun focusIsReAimedOnlyOnceTheCardHasMoved() {
        val card = CardSighting(0.2f, 0.1f, 0.8f, 0.9f, 0.85f)
        assertFalse(movedToRemeter(card, 0.5f, 0.5f))
        assertFalse(movedToRemeter(card, 0.53f, 0.52f))
        assertTrue(movedToRemeter(card, 0.6f, 0.5f))
        val f = Refocus()
        assertTrue(f.mayFocus(0))
        f.focused(0)
        assertFalse(f.mayFocus(REFOCUS_EVERY_MS - 1))
        assertTrue(f.mayFocus(REFOCUS_EVERY_MS))
    }

    @Test
    fun guidePointsLandInThePreview() {
        val middle = guideToPreview(0.5f, 0.5f, 1000, 2000)
        assertEquals(500f, middle.x, 0.01f)
        assertEquals(1000f, middle.y, 0.01f)
        val corner = guideToPreview(0f, 0f, 1000, 2000)
        assertEquals(1000 * (1 - GUIDE_WIDTH) / 2, corner.x, 0.01f)
        assertEquals(2000 * (1 - GUIDE_HEIGHT) / 2, corner.y, 0.01f)
        val far = guideToPreview(1f, 1f, 1000, 2000)
        assertEquals(1000 * (1 + GUIDE_WIDTH) / 2, far.x, 0.01f)
        // Off the guide is kept to its edge.
        assertEquals(far, guideToPreview(1.4f, 1.2f, 1000, 2000))
    }

    @Test
    fun exposureIsMeteredOnTheTitleBar() {
        val card = CardSighting(0.2f, 0.1f, 0.8f, 0.9f, 0.85f)
        val (focus, exposure) = cardMeteringPoints(card)
        assertEquals(0.5f, focus.first, 0.001f)
        assertEquals(0.5f, focus.second, 0.001f)
        assertEquals(0.5f, exposure.first, 0.001f)
        assertEquals(0.1f + 0.8f * TITLE_BAR_AT, exposure.second, 0.001f)
    }
}
