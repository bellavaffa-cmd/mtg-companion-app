package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Proxies in a deck, and swapping them for the real card. The web app has the same checks — see
 * MtgCompanionWeb/tests/decks/proxies.test.ts.
 */
class ProxiesTest {

    private fun entry(name: String, quantity: Int = 1, foilQuantity: Int = 0) =
        CollectionEntry("id-$name", name, null, quantity = quantity, foilQuantity = foilQuantity)

    private fun binder(id: String, entries: List<CollectionEntry>, type: CollectionType = CollectionType.OWNED) =
        Collection(id, id, entries, type = type.name)

    private fun card(name: String, quantity: Int = 1, proxyQuantity: Int? = null) =
        DeckCardEntry("id-$name", name, null, quantity = quantity, proxyQuantity = proxyQuantity)

    private fun deck(name: String, ownership: DeckOwnership, vararg cards: DeckCardEntry) =
        Deck(name, name, cards = cards.toList(), ownership = ownership.name)

    @Test
    fun aDeckMarkedProxyIsProxiesUntilCopiesAreSwappedIn() {
        val proxy = deck("Pile", DeckOwnership.PROXY, card("Sol Ring"), card("Lightning Bolt", 4), card("Cultivate", 2, 1))
        assertEquals(1, proxyCopies(proxy, proxy.cards[0]))
        assertEquals(4, proxyCopies(proxy, proxy.cards[1]))
        // Half swapped in already: one of the two copies is the real card.
        assertEquals(1, proxyCopies(proxy, proxy.cards[2]))
        assertEquals(6, deckProxyCopies(proxy))

        // Any other deck holds no proxies unless it says so.
        val real = deck("Real", DeckOwnership.PHYSICAL, card("Sol Ring"), card("Mox Opal", 1, 1))
        assertEquals(1, deckProxyCopies(real))
    }

    @Test
    fun onlyProxiesYouOwnASpareOfAreOfferedAndASpareIsOfferedOnce() {
        val collections = listOf(
            binder("Blue", listOf(entry("Sol Ring"), entry("Cultivate", quantity = 0, foilQuantity = 1))),
            binder("wish", listOf(entry("Lightning Bolt", 4)), CollectionType.WISHLIST)
        )
        assertEquals(mapOf("sol ring" to 1, "cultivate" to 1), spareCopies(collections))

        val a = deck("A", DeckOwnership.PROXY, card("Sol Ring"), card("Lightning Bolt", 4))
        val b = deck("B", DeckOwnership.PROXY, card("Sol Ring"))
        // A wishlist copy isn't a card you hold, and the one Sol Ring can only go in one deck.
        assertEquals(
            listOf(Triple("A", "Sol Ring", 1)),
            proxySwaps(collections, listOf(a, b)).map { Triple(it.deck.name, it.entry.name, it.spare) }
        )
    }

    @Test
    fun swappingOneInTakesTheCopyOutOfTheBinderAndOffTheProxyCount() {
        val collections = listOf(binder("Blue", listOf(entry("Sol Ring", 2))))
        val decks = listOf(deck("Pile", DeckOwnership.PROXY, card("Sol Ring", 2)))
        val first = withSwapIn(collections, decks, "Pile", "id-Sol Ring")!!
        assertEquals(1, proxyCopies(first.decks[0], first.decks[0].cards[0]))
        assertEquals(1, first.collections[0].entries[0].quantity)

        // And again: the last binder copy goes, and the entry with it.
        val second = withSwapIn(first.collections, first.decks, "Pile", "id-Sol Ring")!!
        assertEquals(0, deckProxyCopies(second.decks[0]))
        assertEquals(emptyList<CollectionEntry>(), second.collections[0].entries)

        // Nothing left to swap.
        assertNull(withSwapIn(second.collections, second.decks, "Pile", "id-Sol Ring"))
    }
}
