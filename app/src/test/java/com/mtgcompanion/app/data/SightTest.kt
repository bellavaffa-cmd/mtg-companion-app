package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decisions from what the card looks like. The web app has the same checks — see
 * MtgCompanionWeb/tests/scan/sight.test.ts.
 */
class SightTest {
    private var row = 0
    private fun m(name: String, group: Int, score: Float, set: String = "x") =
        IndexMatch(IndexEntry(row, "id${row++}", 0, name, set, "1", group), score)

    @Test
    fun thePrintingWhosePictureIsClearlyNearestIsTheOne() {
        val pick = printingBySight(listOf(m("Sol Ring", 1, 0.86f, "msc"), m("Sol Ring", 2, 0.61f), m("Sol Ring", 3, 0.55f)))
        assertEquals("msc", pick?.entry?.set)
        assertEquals(true, pick?.certain)
    }

    @Test
    fun aPictureReprintedInSeveralSetsIsTheRightPictureButNotACertainPrinting() {
        val pick = printingBySight(listOf(m("Llanowar Elves", 4, 0.9f, "m19"), m("Llanowar Elves", 4, 0.9f, "dom"), m("Llanowar Elves", 5, 0.6f)))
        assertEquals(4, pick?.entry?.group)
        assertEquals(false, pick?.certain)
    }

    @Test
    fun twoDifferentPicturesTooCloseToCallPickNothing() {
        assertNull(printingBySight(listOf(m("Island", 1, 0.8f), m("Island", 2, 0.78f))))
        assertNull(printingBySight(emptyList()))
    }

    @Test
    fun aCardIsKnownBySightOnlyWhenItIsClearlyThatCard() {
        assertEquals("Lightning Bolt", cardBySight(listOf(m("Lightning Bolt", 1, 0.85f), m("Chain Lightning", 2, 0.66f)))?.name)
        assertNull(cardBySight(listOf(m("Lightning Bolt", 1, 0.7f), m("Chain Lightning", 2, 0.4f))))
        assertNull(cardBySight(listOf(m("Lightning Bolt", 1, 0.85f), m("Chain Lightning", 2, 0.8f))))
        assertEquals("Island", cardBySight(listOf(m("Island", 1, 0.8f), m("Island", 2, 0.79f)))?.name)
    }

    @Test
    fun eitherFacesNameIsTheSameCard() {
        assertTrue(sameCard("Delver of Secrets // Insectile Aberration", "Insectile Aberration"))
        assertTrue(sameCard("delver of secrets", "Delver of Secrets // Insectile Aberration"))
        assertFalse(sameCard("Lightning Bolt", "Lightning Helix"))
    }

    @Test
    fun aMisreadTitleShowsWhenTheCardPlainlyLooksLikeAnotherCard() {
        val anywhere = listOf(m("Lightning Helix", 7, 0.88f), m("Boros Charm", 8, 0.6f))
        assertEquals("Lightning Helix", looksLikeAnotherCard("Lightning Bolt", listOf(m("Lightning Bolt", 9, 0.55f)), anywhere)?.name)
        assertNull(looksLikeAnotherCard("Lightning Bolt", listOf(m("Lightning Bolt", 9, 0.84f)), anywhere))
        assertNull(looksLikeAnotherCard("Lightning Helix", listOf(m("Lightning Helix", 7, 0.88f)), anywhere))
    }
}
