package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Mirrors the web app's tests/scan/printingMatch.test.ts. */
class PrintingMatchTest {

    private val size = 64

    /** Packed-colour pixels [size] x [size], coloured by [at]. */
    private fun picture(at: (Int, Int) -> Triple<Int, Int, Int>): IntArray {
        val px = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val (r, g, b) = at(x, y)
                px[y * size + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return px
    }

    private fun clamp(v: Double) = v.toInt().coerceIn(0, 255)

    /** A made-up card picture: the same one every time for a given [seed]. */
    private fun art(seed: Int): FloatArray = signatureFromPixels(
        picture { x, y ->
            Triple(
                clamp(sin(x / 5.0 + seed) * 90 + 128),
                clamp(cos(y / 4.0 + seed * 2) * 90 + 128),
                clamp(sin((x + y) / 6.0 + seed * 3) * 90 + 128)
            )
        },
        size, size
    )

    /** The same picture seen through a camera: dimmer, lower contrast, with a little noise. */
    private fun throughACamera(seed: Int): FloatArray = signatureFromPixels(
        picture { x, y ->
            val noise = ((x * 7 + y * 13) % 11) - 5
            Triple(
                clamp((sin(x / 5.0 + seed) * 90 + 128) * 0.55 + 20 + noise),
                clamp((cos(y / 4.0 + seed * 2) * 90 + 128) * 0.55 + 20 + noise),
                clamp((sin((x + y) / 6.0 + seed * 3) * 90 + 128) * 0.55 + 20 + noise)
            )
        },
        size, size
    )

    private fun printing(name: String, signature: FloatArray) = Candidate(name, signature)

    @Test
    fun aSignatureHasACellOfColourPerGridSquare() {
        assertEquals(8 * 11 * 3, art(1).size)
    }

    @Test
    fun levellingLeavesAFlatPictureFlatInsteadOfDividingByNothing() {
        val flat = signatureFromPixels(picture { _, _ -> Triple(40, 40, 40) }, size, size)
        assertTrue(flat.all { it == 0f })
    }

    @Test
    fun aDimLowContrastPhotoStillLooksLikeTheCardItIsOf() {
        // Lighting is levelled out, so the same picture through a camera stays far closer than a
        // different card — which is what makes matching from a photo work at all.
        val same = artDistance(art(3), throughACamera(3))
        val different = artDistance(art(3), throughACamera(8))
        assertTrue("same card through a camera scored $same", same < 0.2f)
        assertTrue("a different card scored $different against $same", different > same * 4)
    }

    @Test
    fun anUnrelatedCardIsNowhereNearAMatch() {
        assertTrue(artDistance(art(1), art(9)) > MATCH_MAX)
    }

    @Test
    fun theClosestPrintingWins() {
        val found = bestPrinting(
            throughACamera(4),
            listOf(printing("borderless", art(4)), printing("usual", art(7)), printing("showcase", art(11)))
        )
        assertEquals("borderless", found?.pick)
        assertEquals(true, found?.only)
    }

    @Test
    fun nothingIsPickedWhenTheCardInTheFrameMatchesNoneOfThePrintings() {
        assertNull(bestPrinting(throughACamera(20), listOf(printing("usual", art(2)), printing("alt", art(5)))))
    }

    @Test
    fun nothingIsPickedWhenTwoPrintingsThatLookDifferentAreTooCloseToCall() {
        // Halfway between two unrelated printings: whichever is nearer, it is nearer by too little
        // to act on, so the scan asks instead of guessing.
        val a = art(2)
        val b = art(5)
        val between = FloatArray(a.size) { (a[it] + b[it]) / 2 }
        assertNull(bestPrinting(between, listOf(printing("one", a), printing("two", b))))
    }

    @Test
    fun aPictureSharedBySeveralPrintingsIsMatchedButNotClaimedAsOnePrinting() {
        // The same art in the same frame, reprinted: the art says which picture, never which printing.
        val shared = art(6)
        val found = bestPrinting(
            throughACamera(6),
            listOf(printing("2019 printing", shared), printing("2023 reprint", shared), printing("alternate art", art(12)))
        )
        assertNotNull(found)
        assertEquals(false, found?.only)
    }

    @Test
    fun onePrintingOfACardIsPickedWithoutAnythingToCompareItAgainst() {
        val found = bestPrinting(throughACamera(1), listOf(printing("only one", art(1))))
        assertEquals("only one", found?.pick)
        assertEquals(true, found?.only)
    }

    @Test
    fun aCardWithNoPrintingsAtAllMatchesNothing() {
        assertNull(bestPrinting(art(1), emptyList<Candidate<String>>()))
    }

    @Test
    fun theGuideIsSquaredOffToACardWhateverShapeItIs() {
        // Wider than a card: the height is what the card fills, and the sides are trimmed evenly.
        val wide = ScanBox(0, 0, 200, 100).cardShaped()
        assertTrue(abs((wide.right - wide.left).toFloat() / (wide.bottom - wide.top) - CARD_ASPECT) < 0.02f)
        assertEquals(100, wide.bottom - wide.top)

        // Taller than a card: the width is what it fills.
        val tall = ScanBox(10, 20, 110, 420).cardShaped()
        assertEquals(100, tall.right - tall.left)
        assertTrue(abs((tall.right - tall.left).toFloat() / (tall.bottom - tall.top) - CARD_ASPECT) < 0.02f)
    }

    @Test
    fun levellingCentresEachChannelOnItsOwnAverage() {
        val out = levelled(floatArrayOf(0f, 10f, 20f, 100f, 10f, 20f, 200f, 10f, 20f))
        var red = 0f
        var i = 0
        while (i < out.size) { red += out[i]; i += 3 }
        assertTrue(abs(red) < 1e-5)
    }

    @Test
    fun theCameraCardIsMeasuredAtAGridOfPlacesAndSizesAroundTheGuide() {
        val boxes = lookBoxes(ScanBox(100, 100, 400, 519))
        assertEquals(LOOK_SCALES.size * LOOK_SHIFTS.size * LOOK_SHIFTS.size, boxes.size)
        for (b in boxes) {
            val aspect = (b.right - b.left).toFloat() / (b.bottom - b.top)
            assertTrue("aspect $aspect", abs(aspect - CARD_ASPECT) < 0.02f)
        }
    }

    @Test
    fun aReflectionOverPartOfTheCardNoLongerDecidesTheMatch() {
        // The same card with twelve of its 88 cells washed out: with the worst fifth left out, the
        // rest of the card still says which card it is.
        val card = art(3)
        val glared = card.copyOf()
        for (i in 0 until 12 * 3) glared[i] = 3f
        assertTrue(trimmedDistance(glared, card) < trimmedDistance(glared, art(9)))
        assertTrue(trimmedDistance(glared, card) < artDistance(glared, card))
    }

    @Test
    fun aPrintingIsMatchedAtWhicheverMeasuringOfTheCameraCardSuitsIt() {
        // One measuring of the table beside the card, one of the card: the card's own printing wins.
        val found = bestPrinting(
            listOf(art(40), throughACamera(4)),
            listOf(printing("borderless", art(4)), printing("usual", art(7)))
        )
        assertEquals("borderless", found?.pick)
    }

    @Test
    fun measuringARegionOfThePixelsAgreesWithMeasuringThePictureItself() {
        // The whole picture as a region comes out as the picture's own signature.
        val px = picture { x, y -> Triple(clamp(x * 3.0), clamp(y * 2.0), clamp((x + y) * 1.5)) }
        val region = signatureOfRegion(px, size, size, ScanBox(0, 0, size, size))!!
        val whole = signatureFromPixels(px, size, size)
        assertTrue(artDistance(region, whole) < 1e-4f)
        // A box running off the pixels is left out rather than measured half empty.
        assertNull(signatureOfRegion(px, size, size, ScanBox(-5, 0, size, size)))
    }

    @Test
    fun theSetsVersionThatLooksLikeTheCardIsTheOne() {
        // The set code said which set: of its regular and full-art versions, the look says which.
        val decision = decideInSet(
            listOf(throughACamera(4)),
            listOf(printing("regular", art(7)), printing("full art", art(4))),
            regular = "regular"
        )
        assertEquals(SetDecision.Found("full art", only = true), decision)
    }

    @Test
    fun aSetWhoseVersionsLookNothingLikeTheCardMeansTheSetCodeWasMisread() {
        // Even a set holding just one version of the card is checked against the look.
        assertEquals(SetDecision.Misread, decideInSet(listOf(throughACamera(20)), listOf(printing("regular", art(2))), "regular"))
        assertEquals(SetDecision.Misread, decideInSet(listOf(throughACamera(1)), emptyList(), "regular"))
    }

    @Test
    fun aSetsVersionsTooAlikeToCallFallBackToItsRegularOne() {
        // Close to both, clearly nearer neither: in the set, but which of its versions can't be said.
        val a = art(2)
        val b = FloatArray(a.size) { a[it] * 0.8f + art(5)[it] * 0.2f }
        val between = FloatArray(a.size) { (a[it] + b[it]) / 2 }
        val decision = decideInSet(listOf(between), listOf(printing("showcase", b), printing("regular", a)), "regular")
        assertTrue("got $decision", decision == SetDecision.Unsure("regular") || (decision is SetDecision.Found && !decision.only))
    }
}
