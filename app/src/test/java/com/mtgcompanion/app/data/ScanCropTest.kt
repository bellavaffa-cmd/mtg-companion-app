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
}
