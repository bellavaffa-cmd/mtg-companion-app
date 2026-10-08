package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where the framing guide sits in the camera's picture, so only text inside it is read. */
class ScanCropTest {

    @Test
    fun theGuideSitsInTheMiddleOfWhatThePreviewShows() {
        // A portrait phone showing a portrait picture, the preview taller than the picture's shape:
        // the picture is scaled to cover, so its sides are cropped away.
        val box = guideInImage(imageWidth = 1080, imageHeight = 1920, previewWidth = 1080, previewHeight = 2400, widthFraction = 0.8f, heightFraction = 0.55f)!!
        // Centred, give or take the odd pixel of rounding.
        assertTrue(kotlin.math.abs(1080 / 2 - (box.left + box.right) / 2) <= 1)
        assertTrue(kotlin.math.abs(1920 / 2 - (box.top + box.bottom) / 2) <= 1)
        // Visible width is 1080 / (2400/1920) = 864, of which the guide is 80%.
        assertEquals((864 * 0.8f).toInt(), box.right - box.left)
        assertEquals((1920 * 0.55f).toInt(), box.bottom - box.top)
        assertNull(guideInImage(0, 1920, 1080, 2400, 0.8f, 0.55f))
    }

    @Test
    fun onlyTextInsideTheGuideCounts() {
        val box = ScanBox(left = 100, top = 400, right = 900, bottom = 1400)
        // The card's own title, in the middle of the guide.
        assertTrue(box.holdsCentreOf(200, 500, 800, 560))
        // A card lying next to it, off to the side.
        assertFalse(box.holdsCentreOf(920, 500, 1070, 560))
        // Another card's title above the guide.
        assertFalse(box.holdsCentreOf(200, 100, 800, 160))
        // A line that starts outside but sits mostly inside still counts: its middle is inside.
        assertTrue(box.holdsCentreOf(60, 500, 700, 560))
    }

    @Test
    fun aCardHeldALittleLargeStillReads() {
        val grown = ScanBox(100, 400, 900, 1400).grownBy(GUIDE_SLACK)
        // A fifth bigger overall: a tenth of the width added on each side, and of the height.
        assertEquals((800 * GUIDE_SLACK / 2).toInt(), 100 - grown.left)
        assertEquals((1000 * GUIDE_SLACK / 2).toInt(), 400 - grown.top)
        assertTrue(grown.holdsCentreOf(200, 320, 800, 380))
    }

    /**
     * A point in the sensor's picture turned upright the way Matrix.postRotate turns it — clockwise,
     * y pointing down — for a sensor picture [w] x [h].
     */
    private fun turned(x: Int, y: Int, rotation: Int, w: Int, h: Int): Pair<Int, Int> = when (rotation) {
        90 -> (h - y) to x
        180 -> (w - x) to (h - y)
        270 -> y to (w - x)
        else -> x to y
    }

    @Test
    fun theGuideIsFoundInTheSensorsPictureWhicheverWayItIsTurned() {
        val w = 1920
        val h = 1080
        for (rotation in listOf(0, 90, 180, 270)) {
            for ((sx, sy) in listOf(100 to 200, 1500 to 900, 960 to 540, 30 to 1000)) {
                val (ux, uy) = turned(sx, sy, rotation, w, h)
                // A box in the upright picture around where the point ended up...
                val upright = ScanBox(ux - 10, uy - 20, ux + 30, uy + 40)
                // ...found in the sensor's picture, holds the point it came from.
                val sensor = sensorBox(upright, rotation, w, h)
                assertTrue("rotation $rotation, point ($sx, $sy) not in $sensor", sx in sensor.left..sensor.right && sy in sensor.top..sensor.bottom)
                // Turning a box keeps its area; a quarter turn swaps its sides.
                val (uw, uh) = (upright.right - upright.left) to (upright.bottom - upright.top)
                val (bw, bh) = (sensor.right - sensor.left) to (sensor.bottom - sensor.top)
                if (rotation == 90 || rotation == 270) assertEquals(uw to uh, bh to bw) else assertEquals(uw to uh, bw to bh)
            }
        }
    }

    @Test
    fun aPortraitPhonesGuideLandsWhereTheSensorSeesIt() {
        // The usual case: a landscape sensor (1920x1080) on a phone held upright (turned 90).
        val guide = guideInImage(1080, 1920, 1080, 2400, GUIDE_WIDTH, GUIDE_HEIGHT)!!.grownBy(GUIDE_SLACK).clampedTo(1080, 1920)
        val sensor = sensorBox(guide, 90, 1920, 1080)
        // Inside the sensor's picture, and the upright guide's height is the sensor box's width.
        assertTrue(sensor.left >= 0 && sensor.top >= 0 && sensor.right <= 1920 && sensor.bottom <= 1080)
        assertEquals(guide.bottom - guide.top, sensor.right - sensor.left)
        // Far fewer pixels than the whole frame — the point of reading only the guide.
        val share = (sensor.right - sensor.left).toFloat() * (sensor.bottom - sensor.top) / (1920f * 1080f)
        assertTrue("guide is $share of the frame", share < 0.75f)
    }

    @Test
    fun aBoxGrownPastThePicturesEdgeIsCutBackToIt() {
        assertEquals(ScanBox(0, 0, 100, 50), ScanBox(-20, -5, 130, 60).clampedTo(100, 50))
    }

    @Test
    fun theGuideIsPlacedWithinTheCutOutPicture() {
        val cut = ScanBox(100, 200, 900, 1400)
        assertEquals(ScanBox(20, 30, 780, 1170), ScanBox(120, 230, 880, 1370).relativeTo(cut))
    }

    @Test
    fun theSmallPrintIsBlownUpOnlyAsFarAsItsLettersNeed() {
        // A card filling a 1080p guide (about 1056 px tall): letters of ~16 px, blown up to ~32.
        val scale = smallPrintScale(1056)
        assertTrue("scale $scale", scale in 1.9f..2.1f)
        // Well under the old fixed 3x — the strip read was the slowest step in a scan.
        assertTrue(scale < 3f)
        // A small card in a low-resolution frame still gets the most it ever did, and a huge one
        // is never shrunk.
        assertEquals(3f, smallPrintScale(300), 0f)
        assertEquals(1f, smallPrintScale(5000), 0f)
        assertEquals(3f, smallPrintScale(0), 0f)
    }

    /**
     * How tall the set line's letters land on the phone this was measured on. The small print's
     * crop is exact — it is cut from the flattened card, not the guide — so whether the set code
     * reads at all comes down to this number, and the reader's floor is about 16 px.
     */
    /** The set line's letters, for a card filling the guide in a frame this size. */
    private fun letterHeight(frameWidth: Int, frameHeight: Int): Float {
        val guide = guideInImage(frameWidth, frameHeight, 1080, 2227, GUIDE_WIDTH, GUIDE_HEIGHT)!!
        return (guide.cardShaped().let { it.bottom - it.top }) * SMALL_PRINT_SHARE
    }

    @Test
    fun theSetLineGetsEnoughPixelsToRead() {
        // An S23 Ultra: the preview is 1080x2227 once the system bars are out of it, and the
        // analyser must be giving 16:9 for this to hold — see the ResolutionSelector in ScanScreen.
        val letterPx = letterHeight(1080, 1920)
        assertTrue("letters are only $letterPx px tall", letterPx >= 17.8f)
    }

    /**
     * Why the analyser is pinned to 16:9. Asking for 1920x1080 without an aspect-ratio strategy
     * gets 4:3 — the selector defaults to it — and 1080x1440 was what the phone actually handed
     * over. Measured, not assumed: the captured frames worked back to exactly that.
     *
     * At 4:3 even a card filling the guide leaves the letters under the reader's floor, so the set
     * code cannot read however well the card is framed. This is the whole reason it never did.
     */
    @Test
    fun fourThirdsCannotReachTheReadersFloor() {
        assertTrue("4:3 would give ${letterHeight(1080, 1440)} px", letterHeight(1080, 1440) < 16f)
        assertTrue("16:9 should clear it", letterHeight(1080, 1920) >= 16f)
    }

    /**
     * The guide may not grow past what the reader can still search. It looks at the guide grown by
     * GUIDE_SLACK, so a guide that big already fills the frame leaves no room for a card held a
     * little large — and a card whose edges run off the frame loses them altogether.
     */
    @Test
    fun theSearchAreaStillFitsInsideTheFrame() {
        val guide = guideInImage(1080, 1920, 1080, 2227, GUIDE_WIDTH, GUIDE_HEIGHT)!!
        val searched = guide.cardShaped().grownBy(GUIDE_SLACK)
        assertTrue("search box ${searched.right - searched.left} wide, frame only 1080", searched.right - searched.left <= 1080)
        assertTrue("search box ${searched.bottom - searched.top} tall, frame only 1920", searched.bottom - searched.top <= 1920)
    }

    /**
     * The zoom has to stay on the main lens. Past [LENS_SWITCH_ZOOM] the phone hands over to a
     * telephoto that cannot focus nearer than 40 cm, and a card held to be scanned would never come
     * into focus at all — a far worse failure than the small one being fixed.
     */
    @Test
    fun theZoomStaysOnTheLensThatCanFocusOnACard() {
        assertTrue("$SCAN_ZOOM would risk the telephoto", SCAN_ZOOM < LENS_SWITCH_ZOOM)
        assertTrue("zooming out would make things worse", SCAN_ZOOM > 1f)
    }

    /**
     * The whole chain, against what was actually measured on the phone: a card held comfortably
     * filled 55% of the guide, and at 4:3 with no zoom that left the letters at 7.8 px. The guide,
     * the aspect ratio and the zoom each fix part of it and only together clear the reader's floor.
     */
    @Test
    fun theMeasuredFramingClearsTheFloorOnceZoomed() {
        val filled = 0.55f
        assertEquals("the measured starting point", 7.8f, letterHeight(1080, 1440) * filled, 0.4f)
        val zoomed = letterHeight(1080, 1920) * filled * SCAN_ZOOM
        assertTrue("zoomed and 16:9 gives only $zoomed px", zoomed >= 16f)
    }

    /** Widening the guide only helps while the width is what bounds the card. */
    @Test
    fun theWidthIsWhatBoundsTheCard() {
        val guide = guideInImage(1080, 1920, 1080, 2227, GUIDE_WIDTH, GUIDE_HEIGHT)!!
        val card = guide.cardShaped()
        val slack = (guide.right - guide.left) - (card.right - card.left)
        assertTrue("the card should fill the guide's width, but is $slack px narrower", slack <= 2)
    }


    @Test
    fun theSmallPrintIsLookedForAtTheBottomOfTheCardNotOfTheGuide() {
        // The guide is wider and taller than a card's shape; the card fits it by width.
        val guide = ScanBox(110, 325, 970, 1595)
        val card = guide.cardShaped()
        val band = smallPrintBand(guide, 0.88f, 0.99f)
        assertEquals(card.left, band.left)
        assertEquals(card.right, band.right)
        // Inside the card, not below it — where the guide's own bottom band reached, onto the next card.
        assertTrue(band.bottom <= card.bottom)
        assertTrue(band.top >= card.top + (card.bottom - card.top) * 0.87f)
        val guideBandBottom = guide.top + ((guide.bottom - guide.top) * 0.99f).toInt()
        assertTrue(guideBandBottom > card.bottom)
    }
}
