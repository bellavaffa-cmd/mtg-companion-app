package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cards the user's decks use more copies of than they own. The web app has the same checks — see
 * MtgCompanionWeb/tests/collection/spreadThin.test.ts.
 */
class SpreadThinTest {

    private fun entry(name: String, quantity: Int = 1, foilQuantity: Int = 0) = CollectionEntry("id-$name", name, null, quantity = quantity, foilQuantity = foilQuantity)
    private fun binder(id: String, entries: List<CollectionEntry>, type: CollectionType = CollectionType.OWNED) =
        Collection(id, id, entries, type = type.name)

    private fun card(name: String, quantity: Int = 1, id: String = "id-$name", proxyQuantity: Int? = null) =
        DeckCardEntry(id, name, null, quantity = quantity, proxyQuantity = proxyQuantity)

    private fun deck(name: String, ownership: DeckOwnership, vararg cards: DeckCardEntry) =
        Deck(name, name, cards = cards.toList(), ownership = ownership.name)

    @Test
    fun copiesInBindersAndDecksYouHoldAreOwnedAndEveryDeckAsksForItsOwn() {
        val decks = listOf(
            deck("Shelf", DeckOwnership.PHYSICAL, card("Sol Ring")),
            deck("Building", DeckOwnership.PROTOTYPE, card("Sol Ring")),
            deck("Dream", DeckOwnership.VIRTUAL, card("Sol Ring"), card("Rhystic Study")),
            deck("Another", DeckOwnership.PROTOTYPE, card("Sol Ring", id = "id-sol-2"))
        )
        // One foil in a binder, one in the Physical deck; a wishlist copy isn't one.
        val collections = listOf(
            binder("Artifacts", listOf(entry("Sol Ring", 0, 1))),
            binder("Unsorted", listOf(entry("Rhystic Study"))),
            binder("wish", listOf(entry("Sol Ring", 3)), CollectionType.WISHLIST)
        )
        val thin = spreadThin(collections, decks)
        assertEquals(listOf(listOf<Any>("Sol Ring", 2, 4, 2)), thin.map { listOf(it.name, it.owned, it.used, it.short) })
        assertEquals("You own 2 · 4 decks use 4", thinLine(thin[0]))
        // Any printing fills a slot: both printings the decks play are priced.
        assertEquals(listOf("id-Sol Ring", "id-sol-2"), thin[0].scryfallIds)
    }

    @Test
    fun enoughToGoRoundIsNotShortAndBasicLandsNeverAre() {
        val decks = listOf(deck("A", DeckOwnership.PROTOTYPE, card("Swamp", 30), card("Ponder")), deck("B", DeckOwnership.PHYSICAL, card("Ponder")))
        assertTrue(spreadThin(listOf(binder("Blue", listOf(entry("Ponder")))), decks).isEmpty())
    }

    @Test
    fun aProxyDeckAsksForNothingButAPhysicalDeckShortOfItsProxiesIs() {
        assertTrue(spreadThin(emptyList(), listOf(deck("Pile", DeckOwnership.PROXY, card("Black Lotus")))).isEmpty())
        val physical = deck("Shelf", DeckOwnership.PHYSICAL, card("Mana Crypt", proxyQuantity = 1))
        assertEquals(listOf("Mana Crypt" to 1), spreadThin(emptyList(), listOf(physical)).map { it.name to it.short })
    }

    @Test
    fun shortestFirstAndTwoPrintingsInOneDeckAreOneDeck() {
        val decks = listOf(
            deck("Burn", DeckOwnership.PROTOTYPE, card("Lightning Bolt", 2, "bolt-a"), card("Lightning Bolt", 2, "bolt-b")),
            deck("Red", DeckOwnership.VIRTUAL, card("Lightning Bolt", 1, "bolt-a"), card("Sol Ring"))
        )
        val thin = spreadThin(emptyList(), decks)
        assertEquals(listOf("Lightning Bolt" to 5, "Sol Ring" to 1), thin.map { it.name to it.short })
        assertEquals(listOf("Burn" to 4, "Red" to 1), thin[0].decks.map { it.deckName to it.copies })
        // The cheapest printing, for every copy short; no price at all, no cost.
        assertEquals(2.5, shortCost(thin[0], mapOf("bolt-a" to 2.0, "bolt-b" to 0.5))!!, 0.0001)
        assertNull(shortCost(thin[1], mapOf("id-Sol Ring" to null)))
        assertEquals("5 Lightning Bolt\n1 Sol Ring", shortBuyList(thin))
    }
}
