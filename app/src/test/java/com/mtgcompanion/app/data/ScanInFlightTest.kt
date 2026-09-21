package com.mtgcompanion.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The camera no longer waits for a card's lookup, so a card still sitting in view while its lookup
 * is out has to be recognised as the one already on its way — see ScanInFlight.
 */
class ScanInFlightTest {

    /** The scanner's own resemblance check: a flicker of a letter is still the same card. */
    private val same = { a: String, b: String ->
        val x = a.lowercase().filter { it.isLetterOrDigit() }
        val y = b.lowercase().filter { it.isLetterOrDigit() }
        x.contains(y) || y.contains(x) || x.commonPrefixWith(y).length >= 4
    }

    @Test
    fun aCardStillInViewWhileItsLookupIsOutIsNotLookedUpAgain() {
        val flight = ScanInFlight()
        flight.start("sol ring")
        assertTrue(flight.isOnItsWay("sol ring", same))
        // OCR flickers: a misread letter on the next frame is still the same card.
        assertTrue(flight.isOnItsWay("sol rinq", same))
    }

    @Test
    fun aDifferentCardIsReadWhileTheLastOneIsStillBeingLookedUp() {
        val flight = ScanInFlight()
        flight.start("sol ring")
        // The point of the change: the next card isn't held up by the last one's network trip.
        assertFalse(flight.isOnItsWay("lightning bolt", same))
    }

    @Test
    fun onceTheLookupIsBackTheGuardStepsAside() {
        val flight = ScanInFlight()
        val token = flight.start("sol ring")
        assertTrue(flight.finished(token, "sol ring"))
        // From here the card-in-view guard is the one it's always been: the card that was added.
        assertFalse(flight.isOnItsWay("sol ring", same))
    }

    @Test
    fun aCardThatLeftBeforeItsLookupCameBackIsNotTakenForTheNextCopy() {
        val flight = ScanInFlight()
        val token = flight.start("sol ring")
        flight.cardLeft()
        // A second Sol Ring slid in: it's a new card, not the one on its way.
        assertFalse(flight.isOnItsWay("sol ring", same))
        // And the first lookup, landing now, mustn't claim to be the card in view.
        assertFalse(flight.finished(token, "sol ring"))
    }

    @Test
    fun anEarlierLookupFinishingDoesNotReleaseTheGuardOnALaterCard() {
        val flight = ScanInFlight()
        val first = flight.start("sol ring")
        flight.start("lightning bolt")
        // Sol Ring's lookup lands while Lightning Bolt is still out: Bolt stays guarded.
        assertTrue(flight.finished(first, "sol ring"))
        assertTrue(flight.isOnItsWay("lightning bolt", same))
    }
}
