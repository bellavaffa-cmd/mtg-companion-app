package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The cards in a deck the user doesn't own. The web app has the same rule — see
 * MtgCompanionWeb/tests/decks/missing.test.ts.
 */
class MissingCardsTest {

    private fun entry(name: String, quantity: Int = 1) = CollectionEntry("id-$name", name, null, quantity = quantity)
    private fun binder(id: String, entries: List<CollectionEntry>, type: CollectionType = CollectionType.OWNED) =
        Collection(id, id, entries, type = type.name)

    private fun card(name: String, quantity: Int = 1, proxyQuantity: Int? = null) =
        DeckCardEntry("id-$name", name, null, quantity = quantity, proxyQuantity = proxyQuantity)

    private fun deck(name: String, ownership: DeckOwnership, vararg cards: DeckCardEntry) =
        Deck(name, name, cards = cards.toList(), ownership = ownership.name)

    @Test
    fun aCardSittingInAnotherDeckYouHoldIsACardYouOwn() {
        val building = deck("Building", DeckOwnership.PROTOTYPE, card("Sol Ring"), card("Rhystic Study"))
        val physical = deck("Old deck", DeckOwnership.PHYSICAL, card("Sol Ring"))
        val virtual = deck("Dream", DeckOwnership.VIRTUAL, card("Rhystic Study"))
        // Sol Ring is in a deck on the shelf; Rhystic Study only in one that doesn't exist.
        assertEquals(
            listOf("Rhystic Study" to 1),
            missingCards(building, emptyList(), listOf(building, physical, virtual)).map { it.entry.name to it.need }
        )
    }

    @Test
    fun basicLandsAreNeverMissingAndAWishlistCopyIsNotACopy() {
        val d = deck("Mono-black", DeckOwnership.PROTOTYPE, card("Swamp", 30), card("Snow-Covered Swamp", 4), card("Sol Ring"))
        val wish = binder("wish", listOf(entry("Sol Ring")), CollectionType.WISHLIST)
        assertEquals(listOf("Sol Ring"), missingCards(d, listOf(wish), listOf(d)).map { it.entry.name })
    }

    @Test
    fun onlyWhatTheBindersAndDecksAreShortOfIsListed() {
        val d = deck("Burn", DeckOwnership.PROTOTYPE, card("Lightning Bolt", 4))
        val binders = listOf(binder("Red", listOf(entry("Lightning Bolt", 1))))
        val other = deck("Old burn", DeckOwnership.PHYSICAL, card("Lightning Bolt", 2))
        assertEquals(
            listOf("Lightning Bolt" to 1),
            missingCards(d, binders, listOf(d, other)).map { it.entry.name to it.need }
        )
    }

    @Test
    fun aDeckYouHoldCoversItselfAndAProxyDeckIsBuilt() {
        val physical = deck("Shelf", DeckOwnership.PHYSICAL, card("Sol Ring"), card("Mox Opal"))
        assertEquals(emptyList<MissingCard>(), missingCards(physical, emptyList(), listOf(physical)))

        val proxy = deck("Pile", DeckOwnership.PROXY, card("Black Lotus"))
        assertEquals(emptyList<MissingCard>(), missingCards(proxy, emptyList(), listOf(proxy)))
        // Its proxies aren't copies for another deck, but a card swapped in for real is.
        val swapped = deck("Pile", DeckOwnership.PROXY, card("Black Lotus"), card("Sol Ring", 1, 0))
        assertEquals(mapOf("sol ring" to 1), copiesHeld(swapped))
    }
}
