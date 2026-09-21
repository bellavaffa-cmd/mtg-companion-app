package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.scryfall.ScryfallImageUris
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Switching a card to another printing. The web app has the same checks — see
 * MtgCompanionWeb/tests/collection/printings.test.ts.
 */
class PrintingsTest {

    private fun printing(id: String, name: String = "Sol Ring") =
        ScryfallCard(id = id, name = name, typeLine = "Artifact", imageUris = ScryfallImageUris(normal = "https://img/$id.jpg"))

    private fun entry(id: String, quantity: Int = 1, foil: Int = 0, priceAlert: Double? = null) =
        CollectionEntry(id, "Sol Ring", "https://img/$id.jpg", quantity = quantity, foilQuantity = foil, priceAlert = priceAlert)

    private fun card(id: String, quantity: Int = 1, proxyQuantity: Int? = null, name: String = "Sol Ring") =
        DeckCardEntry(id, name, null, quantity = quantity, proxyQuantity = proxyQuantity)

    private fun deck(ownership: DeckOwnership, cards: List<DeckCardEntry>, commander: DeckCardEntry? = null) =
        Deck("d", "Deck", commander = commander, cards = cards, ownership = ownership.name)

    @Test
    fun aBinderCardTakesTheNewPrintingsLookAndKeepsItsCopies() {
        val out = withEntryPrinting(listOf(entry("frc", 2, 1, priceAlert = 5.0)), "frc", printing("msc"))
        assertEquals(1, out.size)
        assertEquals("msc", out[0].scryfallId)
        assertEquals("https://img/msc.jpg", out[0].imageUrl)
        assertEquals(2, out[0].quantity)
        assertEquals(1, out[0].foilQuantity)
        assertEquals(5.0, out[0].priceAlert!!, 0.0)
    }

    @Test
    fun switchingToAPrintingTheBinderAlreadyHoldsAddsTheCopiesTogether() {
        // This used to leave two entries for the same printing, each showing half the count.
        val out = withEntryPrinting(listOf(entry("frc", 2, 0), entry("msc", 1, 1)), "frc", printing("msc"))
        assertEquals(listOf(Triple("msc", 3, 1)), out.map { Triple(it.scryfallId, it.quantity, it.foilQuantity) })
    }

    @Test
    fun nothingChangesForTheSamePrintingOrACardThatIsNotThere() {
        val entries = listOf(entry("frc"))
        assertSame(entries, withEntryPrinting(entries, "frc", printing("frc")))
        assertSame(entries, withEntryPrinting(entries, "nope", printing("msc")))
    }

    @Test
    fun aDeckCardTakesTheNewPrintingAndKeepsItsCopiesAndProxies() {
        val out = withDeckPrinting(deck(DeckOwnership.PHYSICAL, listOf(card("frc", 1, 1))), "frc", printing("msc"))
        assertEquals("msc", out.cards[0].scryfallId)
        assertEquals(1, out.cards[0].proxyQuantity)
    }

    @Test
    fun aCommanderIsSwitchedToo() {
        val commander = card("atraxa", name = "Atraxa")
        val out = withDeckPrinting(deck(DeckOwnership.PHYSICAL, listOf(commander), commander), "atraxa", printing("atraxa-alt", "Atraxa"))
        assertEquals("atraxa-alt", out.commander?.scryfallId)
        assertEquals("atraxa-alt", out.cards[0].scryfallId)
    }

    @Test
    fun switchingToAPrintingTheDeckAlreadyHoldsAddsTheCopiesAndProxiesTogether() {
        // In a proxy deck an unset proxy count means "all of them": counted out before it's added
        // to an entry that says a number.
        val out = withDeckPrinting(deck(DeckOwnership.PROXY, listOf(card("frc", 2), card("msc", 1, 0))), "frc", printing("msc"))
        assertEquals(1, out.cards.size)
        assertEquals(3, out.cards[0].quantity)
        assertEquals(2, out.cards[0].proxyQuantity)
        // Neither set: it stays unset, "whatever the deck is".
        val plain = withDeckPrinting(deck(DeckOwnership.PHYSICAL, listOf(card("frc", 2), card("msc", 1))), "frc", printing("msc"))
        assertNull(plain.cards[0].proxyQuantity)
    }

    @Test
    fun aSetsRegularVersionIsItsLowestNumberedOne() {
        fun numbered(n: String) = ScryfallCard(id = n, name = "Sol Ring", collectorNumber = n)
        // Borderless and showcase versions are numbered after the set's main run.
        assertEquals("12", regularInSet(listOf("300", "12a", "★", "12", "45").map(::numbered))?.id)
        assertEquals("★", regularInSet(listOf(numbered("★")))?.id)
        assertNull(regularInSet(emptyList()))
    }

    @Test
    fun theWishlistAndTheUnsortedPileAreNotBinders() {
        val all = listOf(
            Collection(WISHLIST_ID, "Wishlist", type = CollectionType.WISHLIST.name),
            Collection(UNSORTED_COLLECTION_ID, "Unsorted"),
            Collection("blue", "Blue")
        )
        assertEquals(listOf("blue"), all.filter { it.isBinder }.map { it.id })
    }
}
