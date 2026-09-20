package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A deck built with proxies: the cards are there — it isn't a deck to go and buy — but they're
 * print-outs. The web app has the same checks — see MtgCompanionWeb/tests/collection/allCards.test.ts.
 */
class ProxyDeckTest {

    private fun card(name: String, quantity: Int = 1) = DeckCardEntry("id-$name", name, null, quantity = quantity)
    private fun deck(name: String, ownership: DeckOwnership, vararg cards: DeckCardEntry) =
        Deck(name, name, cards = cards.toList(), ownership = ownership.name)

    @Test
    fun aProxyDeckIsBuiltSoNothingAboutItIsMissing() {
        val proxy = deck("Proxy pile", DeckOwnership.PROXY, card("Lightning Bolt", 4), card("Sol Ring"))
        assertEquals(emptyList<MissingCard>(), missingCards(proxy, emptyList(), listOf(proxy)))

        // The same deck as a real one is a shopping list.
        val real = deck("Real pile", DeckOwnership.VIRTUAL, card("Lightning Bolt", 4), card("Sol Ring"))
        assertEquals(listOf("Lightning Bolt" to 4, "Sol Ring" to 1), missingCards(real, emptyList(), listOf(real)).map { it.entry.name to it.need })
    }

    @Test
    fun aProxyDecksCardsArentRealCopiesForAnotherDeck() {
        val proxy = deck("Proxy pile", DeckOwnership.PROXY, card("Lightning Bolt", 4))
        val building = deck("Burn", DeckOwnership.VIRTUAL, card("Lightning Bolt", 4))
        // Only a Physical deck's cards count as copies you have; the proxies don't fill this list.
        assertEquals(listOf("Lightning Bolt" to 4), missingCards(building, emptyList(), listOf(proxy, building)).map { it.entry.name to it.need })

        val physical = deck("Old burn", DeckOwnership.PHYSICAL, card("Lightning Bolt", 4))
        assertEquals(emptyList<MissingCard>(), missingCards(building, emptyList(), listOf(physical, building)))
    }
}
