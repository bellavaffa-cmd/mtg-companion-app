package com.mtgcompanion.app.ui.decks

import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckCardEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Goldfishing a deck. The web app has the same check — see MtgCompanionWeb/tests/decks/goldfish.test.ts. */
class GoldfishTest {

    private fun entry(id: String, quantity: Int = 1) = DeckCardEntry(id, id, null, quantity)

    @Test
    fun theLibraryIsEveryCopyLessTheCommandersInTheCommandZone() {
        // A deck's commander is in its card list too.
        val deck = Deck("d", "Omnath", commander = entry("omnath"), cards = listOf(entry("omnath"), entry("forest", 3), entry("sol")))
        val library = shuffledLibrary(deck)
        assertEquals(listOf("forest", "forest", "forest", "sol"), library.map { it.entry.scryfallId }.sorted())
        assertEquals(4, library.map { it.instanceId }.toSet().size)
        assertTrue((1..20).map { shuffledLibrary(deck).joinToString { it.entry.scryfallId } }.toSet().size > 1)
    }
}
