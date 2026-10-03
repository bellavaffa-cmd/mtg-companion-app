package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Comparing a deck with another deck or with one of its saved versions. */
class DeckCompareTest {

    @Test
    fun `cards only here, only there, and in both with their counts`() {
        val diff = diffDecks(
            mapOf("Sol Ring" to 1, "Forest" to 30, "Cultivate" to 1),
            mapOf("sol ring" to 1, "Forest" to 28, "Rampant Growth" to 1)
        )
        assertEquals(listOf(CompareRow("Cultivate", 1, 0)), diff.onlyHere)
        assertEquals(listOf(CompareRow("Rampant Growth", 0, 1)), diff.onlyThere)
        // The counts that differ first.
        assertEquals(listOf(CompareRow("Forest", 30, 28), CompareRow("Sol Ring", 1, 1)), diff.both)
    }

    @Test
    fun `a deck reads like a saved version of itself, so an unchanged deck is identical`() {
        val atraxa = DeckCardEntry("a", "Atraxa", null)
        val deck = Deck(
            "d", "A", commander = atraxa,
            cards = listOf(atraxa, DeckCardEntry("f1", "Forest", null, 10), DeckCardEntry("f2", "Forest", null, 5)),
            sideboard = listOf(DeckCardEntry("x", "Duress", null))
        )
        assertEquals(mapOf("Atraxa" to 1, "Forest" to 15), deckCounts(deck))
        val version = DeckVersion("v", 0, cards = mapOf("Forest" to 15, "Atraxa" to 1), commanders = listOf("Atraxa"))
        assertTrue(diffDecks(deckCounts(deck), versionCounts(version)).identical)
    }
}
