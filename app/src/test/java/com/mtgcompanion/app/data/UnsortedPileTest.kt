package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The Unsorted pile is always there, like the Wishlist. The web app has the same checks — see
 * MtgCompanionWeb/tests/collection/unsorted.test.ts.
 */
class UnsortedPileTest {

    private fun binder(id: String, type: CollectionType = CollectionType.OWNED, entries: List<CollectionEntry> = emptyList()) =
        Collection(id, id, entries, createdAt = 5, type = type.name)

    @Test
    fun aLibraryWithoutThePileGetsOneEmptyAndOwned() {
        val out = withUnsortedPile(listOf(binder("wishlist", CollectionType.WISHLIST), binder("Blue")))
        val pile = out.firstOrNull { it.isUnsorted }
        assertNotNull(pile)
        assertEquals(UNSORTED_COLLECTION_ID, pile!!.id)
        assertEquals("Unsorted", pile.name)
        assertEquals(CollectionType.OWNED.name, pile.type)
        assertEquals(emptyList<CollectionEntry>(), pile.entries)
        // The same on every device, so two made independently don't disagree about when.
        assertEquals(0L, pile.createdAt)
        assertEquals(3, out.size)
    }

    @Test
    fun aPileThatIsAlreadyThereIsLeftExactlyAsItIs() {
        val cards = listOf(CollectionEntry("id-Sol Ring", "Sol Ring", null, quantity = 2))
        val library = listOf(binder("Blue"), binder(UNSORTED_COLLECTION_ID, entries = cards))
        // The same list back, so nothing is written and nothing syncs.
        assertSame(library, withUnsortedPile(library))
    }

    @Test
    fun anEmptyLibraryStillGetsItsPile() {
        assertEquals(1, withUnsortedPile(emptyList()).count { it.isUnsorted })
    }

    private fun loose(id: String, name: String, quantity: Int, foil: Int = 0) = CollectionEntry(id, name, null, quantity = quantity, foilQuantity = foil)

    @Test
    fun aCardPutInADeckLeavesThePileThatPrintingFirstPlainBeforeFoil() {
        val pile = listOf(loose("msc", "Sol Ring", 1, 1), loose("bolt", "Lightning Bolt", 2))
        val (after, taken) = takenFromUnsorted(pile, "msc", "Sol Ring", 1)
        assertEquals(1, taken)
        assertEquals(listOf(Triple("msc", 0, 1), Triple("bolt", 2, 0)), after.map { Triple(it.scryfallId, it.quantity, it.foilQuantity) })
        // The last copy gone, the entry goes too.
        assertEquals(listOf("bolt"), takenFromUnsorted(after, "msc", "Sol Ring", 1).first.map { it.scryfallId })
    }

    @Test
    fun anotherPrintingOfTheSameCardIsTakenWhenThatPrintingIsNotInThePile() {
        val pile = listOf(loose("cmr", "Sol Ring", 1), loose("msc", "Sol Ring", 1), loose("dfc", "Delver of Secrets // Insectile Aberration", 1))
        val (after, taken) = takenFromUnsorted(pile, "msc", "Sol Ring", 2)
        assertEquals(2, taken)
        assertEquals(listOf("dfc"), after.map { it.scryfallId })
        // Either face's name is the card.
        assertEquals(1, takenFromUnsorted(pile, "other", "Delver of Secrets", 1).second)
    }

    @Test
    fun onlyWhatThePileHasIsTakenAndACardNotInItLeavesThePileAsItWas() {
        val pile = listOf(loose("msc", "Sol Ring", 1))
        assertEquals(1, takenFromUnsorted(pile, "msc", "Sol Ring", 3).second)
        val (after, taken) = takenFromUnsorted(pile, "bolt", "Lightning Bolt", 1)
        assertEquals(0, taken)
        assertSame(pile, after)
    }

    @Test
    fun onlyPhysicalDecksHoldTheUsersOwnCopies() {
        fun deck(o: DeckOwnership) = Deck("d", "Deck", ownership = o.name)
        assertEquals(listOf(true, false, false, false), DeckOwnership.entries.map { deck(it).holdsOwnCopies })
    }

    private fun inDeck(id: String, name: String, quantity: Int, proxies: Int? = null) = DeckCardEntry(id, name, null, quantity = quantity, proxyQuantity = proxies)

    @Test
    fun aPhysicalDecksRealCopiesGoBackToThePileWhenItsDeletedWithItsCardsKept() {
        val deck = Deck("d", "D", cards = listOf(inDeck("sol", "Sol Ring", 1), inDeck("bolt", "Lightning Bolt", 4, proxies = 1)), ownership = DeckOwnership.PHYSICAL.name)
        // The proxy Bolt isn't a real copy and doesn't go.
        assertEquals(listOf("sol" to 1, "bolt" to 3), realCopiesOf(deck).map { it.scryfallId to it.quantity })
    }

    @Test
    fun aDeckThatHoldsNoRealCopiesGivesThePileNothing() {
        for (o in listOf(DeckOwnership.PROXY, DeckOwnership.VIRTUAL, DeckOwnership.PROTOTYPE)) {
            assertEquals(emptyList<CollectionEntry>(), realCopiesOf(Deck("d", "D", cards = listOf(inDeck("sol", "Sol Ring", 1)), ownership = o.name)))
        }
    }

    @Test
    fun aCardTakenOutOfAPhysicalDeckGoesBackToThePileItsProxiesDont() {
        val bolt = inDeck("bolt", "Lightning Bolt", 4, proxies = 1)
        val deck = Deck("d", "D", cards = listOf(bolt), ownership = DeckOwnership.PHYSICAL.name)
        // Out altogether: its 3 real copies go back; the proxy doesn't.
        assertEquals(3, realCopiesLeaving(deck, bolt, 0))
        // One fewer: a real copy goes, the proxy stays.
        assertEquals(1, realCopiesLeaving(deck, bolt, 3))
        // Down to just the proxy: the other 3 were the real ones.
        assertEquals(3, realCopiesLeaving(deck, bolt, 1))
        // More copies, or a deck holding no real cards: nothing goes back.
        assertEquals(0, realCopiesLeaving(deck, bolt, 5))
        assertEquals(0, realCopiesLeaving(deck.copy(ownership = DeckOwnership.PROXY.name), bolt.copy(proxyQuantity = null), 0))
    }
}
