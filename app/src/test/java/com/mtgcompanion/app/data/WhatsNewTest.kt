package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The What's new tour: when it opens by itself, and its steps. The web app has the same cases — see
 * MtgCompanionWeb/tests/onboarding/whatsNew.test.ts.
 */
class WhatsNewTest {

    @Test
    fun `shown once, to someone with a collection`() {
        assertTrue(shouldShowTour(null, cards = 10, decks = 0))
        assertTrue(shouldShowTour(null, cards = 0, decks = 2))
        assertFalse(shouldShowTour(TOUR_ID, cards = 10, decks = 2))
        // An older tour seen: this one is new.
        assertTrue(shouldShowTour("older-tour", cards = 10, decks = 2))
        // Nothing yet: the welcome steps come first.
        assertFalse(shouldShowTour(null, cards = 0, decks = 0))
    }

    @Test
    fun `the seen id as stored`() {
        assertNull(parseTourSeen(null))
        assertNull(parseTourSeen("  "))
        assertEquals(TOUR_ID, parseTourSeen(TOUR_ID))
    }

    @Test
    fun `steps, their counter and buttons`() {
        assertEquals(5, TOUR_STEPS.size)
        assertEquals("New · 2 of 5", tourEyebrow(1, 5))
        assertEquals("Next", tourNextLabel(0, 5))
        assertEquals("Done", tourNextLabel(4, 5))
        assertEquals("Set it up", tourSteps(false)[1].cta?.label)
        assertNull(tourSteps(true)[1].cta)
        assertEquals("Try it", tourSteps(true)[2].cta?.label)
        assertEquals("Know where every card is", TOUR_STEPS[1].title)
    }
}
