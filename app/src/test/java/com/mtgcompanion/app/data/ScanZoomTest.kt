package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The scanner's zoom: where it starts, how − and + step, the chip's text and the farther-away hint. */
class ScanZoomTest {

    private val phone = ZoomRange(1f, 10f)
    private val noTele = ZoomRange(1f, 2.5f)

    @Test
    fun itStartsAtTheScanZoomUnderTheLensSwitch() {
        assertEquals(SCAN_ZOOM, defaultZoom(phone), 0.001f)
        assertTrue(defaultZoom(phone) < LENS_SWITCH_ZOOM)
        // A phone with less zoom than that gets what it has; one with none stays at 1×.
        assertEquals(1.5f, defaultZoom(ZoomRange(1f, 1.5f)), 0.001f)
        assertEquals(1f, defaultZoom(ZoomRange.NONE), 0.001f)
        // A default asked for past the switch is still kept under it.
        assertEquals(LENS_SWITCH_ZOOM, defaultZoom(phone, preferred = 5f), 0.001f)
        // The page scanner starts at 1×.
        assertEquals(1f, defaultZoom(phone, preferred = 1f), 0.001f)
    }

    @Test
    fun theLastZoomChosenIsWhereItOpens() {
        assertEquals(SCAN_ZOOM, startingZoom(null, phone), 0.001f)
        assertEquals(2.25f, startingZoom(2.25f, phone), 0.001f)
        // Remembered past the switch, on a phone that offers it: kept (the hint shows).
        assertEquals(4f, startingZoom(4f, phone), 0.001f)
        // A zoom this camera can't do is brought into its range.
        assertEquals(2.5f, startingZoom(4f, noTele), 0.001f)
        assertEquals(SCAN_ZOOM, startingZoom(Float.NaN, phone), 0.001f)
        assertEquals(SCAN_ZOOM, startingZoom(0f, phone), 0.001f)
    }

    @Test
    fun plusAndMinusStepByQuartersThenHalves() {
        assertEquals(2f, zoomIn(1.8f, phone), 0.001f)
        assertEquals(1.75f, zoomOut(1.8f, phone), 0.001f)
        assertEquals(2.25f, zoomIn(2f, phone), 0.001f)
        assertEquals(1.5f, zoomOut(1.75f, phone), 0.001f)
        assertEquals(3f, zoomIn(2.75f, phone), 0.001f)
        assertEquals(3.5f, zoomIn(3f, phone), 0.001f)
        assertEquals(2.75f, zoomOut(3f, phone), 0.001f)
        assertEquals(3f, zoomOut(3.5f, phone), 0.001f)
        // From wherever a pinch left it, the next step on the grid.
        assertEquals(3.5f, zoomIn(3.1f, phone), 0.001f)
        assertEquals(3f, zoomOut(3.1f, phone), 0.001f)
        assertEquals(2.25f, zoomIn(2.0000002f, phone), 0.001f)
    }

    @Test
    fun stepsStopAtWhatTheCameraOffers() {
        assertEquals(1f, zoomOut(1f, phone), 0.001f)
        assertEquals(1f, zoomOut(1.1f, phone), 0.001f)
        assertEquals(10f, zoomIn(10f, phone), 0.001f)
        assertEquals(2.5f, zoomIn(2.4f, noTele), 0.001f)
        assertFalse(phone.canZoomOut(1f))
        assertTrue(phone.canZoomIn(1f))
        assertFalse(noTele.canZoomIn(2.5f))
        assertTrue(noTele.canZoomOut(2.5f))
        // A wide lens below 1×: down to its minimum.
        val wide = ZoomRange(0.6f, 8f)
        assertEquals(0.75f, zoomOut(1f, wide), 0.001f)
        assertEquals(0.6f, zoomOut(0.75f, wide), 0.001f)
        assertFalse(ZoomRange.NONE.canZoom)
        assertTrue(phone.canZoom)
    }

    @Test
    fun aPinchMultipliesWithinTheRange() {
        assertEquals(3.6f, pinchZoom(1.8f, 2f, phone), 0.001f)
        assertEquals(1f, pinchZoom(1.8f, 0.1f, phone), 0.001f)
        assertEquals(10f, pinchZoom(8f, 3f, phone), 0.001f)
        assertEquals(1.8f, pinchZoom(1.8f, Float.NaN, phone), 0.001f)
        assertEquals(1.8f, pinchZoom(1.8f, 0f, phone), 0.001f)
    }

    @Test
    fun theChipSaysTheZoomShortly() {
        assertEquals("1.8×", zoomLabel(1.8f))
        assertEquals("2×", zoomLabel(2f))
        assertEquals("1.75×", zoomLabel(1.75f))
        assertEquals("2.25×", zoomLabel(2.25f))
        assertEquals("2.5×", zoomLabel(2.5f))
        assertEquals("1×", zoomLabel(1f))
        assertEquals("0.6×", zoomLabel(0.6f))
        assertEquals("2.1×", zoomLabel(2.137f))
        assertEquals("10×", zoomLabel(10f))
        assertEquals("Zoom 1.8 times", zoomSpoken(1.8f))
    }

    @Test
    fun theHintShowsOnlyPastTheLensSwitch() {
        assertFalse(showFartherHint(SCAN_ZOOM))
        assertFalse(showFartherHint(2.75f))
        assertTrue(showFartherHint(LENS_SWITCH_ZOOM))
        assertTrue(showFartherHint(3f))
        assertFalse(showFartherHint(defaultZoom(phone)))
        assertEquals("Hold the card farther away", fartherHint())
        assertEquals("Hold the page farther away", fartherHint("page"))
    }

    @Test
    fun aRangeThatMakesNoSenseStillClamps() {
        val broken = ZoomRange(Float.NaN, Float.NaN)
        assertEquals(1f, broken.clamp(2f), 0.001f)
        assertEquals(2f, ZoomRange(2f, 1f).clamp(5f), 0.001f)
    }

    /** A zoom change counts as movement: the reads in a row start again, but a card taken stays held. */
    @Test
    fun handsFreeStartsCountingAgainWhenTheZoomMoves() {
        val hands = HandsFreeCapture(steadyFrames = 3)
        assertFalse(hands.onRead("Sol Ring"))
        assertFalse(hands.onRead("Sol Ring"))
        hands.moved()
        assertFalse(hands.onRead("Sol Ring"))
        assertFalse(hands.onRead("Sol Ring"))
        assertTrue(hands.onRead("Sol Ring"))
        hands.captured("Sol Ring")
        hands.moved()
        repeat(5) { assertFalse("the same card isn't taken again after a zoom", hands.onRead("Sol Ring")) }
    }
}
