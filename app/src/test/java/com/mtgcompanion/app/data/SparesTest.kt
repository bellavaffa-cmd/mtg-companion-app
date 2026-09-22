package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Cards in the binders that no deck plays. The web app has the same checks — see
 * MtgCompanionWeb/tests/collection/spares.test.ts.
 */
class SparesTest {

    private fun entry(name: String, quantity: Int = 1, foilQuantity: Int = 0) =
        CollectionEntry("id-$name", name, null, quantity = quantity, foilQuantity = foilQuantity)

    private fun binder(id: String, entries: List<CollectionEntry>, type: CollectionType = CollectionType.OWNED) =
        Collection(id, id, entries, type = type.name)

    private fun card(name: String) = DeckCardEntry("id-$name", name, null)
    private fun deck(name: String, cards: List<String>, considering: List<String> = emptyList()) =
        Deck(name, name, cards = cards.map { card(it) }, considering = considering.map { card(it) })

    @Test
    fun aCardADeckPlaysWantsOrIsThinkingAboutIsNotASpare() {
        val decks = listOf(deck("Omnath", listOf("Sol Ring"), listOf("Cultivate")))
        assertEquals(setOf("sol ring", "cultivate"), namesDecksUse(decks))

        val collections = listOf(
            binder("Blue", listOf(entry("Sol Ring"), entry("Cultivate"), entry("Rhystic Study", 2), entry("Ponder", 1, 3))),
            binder("wish", listOf(entry("Mox Opal")), CollectionType.WISHLIST)
        )
        // Most copies first; the wishlist card isn't theirs to trade.
        assertEquals(
            listOf(Triple("Ponder", 4, 3), Triple("Rhystic Study", 2, 0)),
            spares(collections, decks).map { Triple(it.entry.name, it.copies, it.foils) }
        )
    }

    @Test
    fun copiesAcrossBindersAddUpAndSeveralCanBeAskedFor() {
        val collections = listOf(
            binder("A", listOf(entry("Ponder"))),
            binder("B", listOf(entry("Ponder", 2))),
            binder("C", listOf(entry("Brainstorm")))
        )
        assertEquals(
            listOf(Triple("Ponder", 3, listOf("A", "B")), Triple("Brainstorm", 1, listOf("C"))),
            spares(collections, emptyList()).map { Triple(it.entry.name, it.copies, it.binders) }
        )
        assertEquals(listOf("Ponder"), spares(collections, emptyList(), minCopies = 2).map { it.entry.name })
    }

    @Test
    fun whatThePileIsWorthPutsTheMoneyOnTop() {
        val collections = listOf(binder("A", listOf(entry("Ponder", 4), entry("Black Lotus"))))
        val prices = mapOf("id-Ponder" to 1.0, "id-Black Lotus" to 5000.0)
        assertEquals(
            listOf("Black Lotus", "Ponder"),
            spares(collections, emptyList()).sortedByDescending { spareValue(it, prices) }.map { it.entry.name }
        )
    }

    @Test
    fun sparesAreOfferedInATradeDearestFirstAndNoMoreThanTheLimit() {
        val cheap = Spare(entry("Llanowar Elves", 3), listOf("Green"), copies = 3, foils = 0)
        val dear = Spare(entry("Sol Ring", 1, 1), listOf("Artifacts"), copies = 1, foils = 1)
        val prices = mapOf(cheap.entry.scryfallId to 0.25, dear.entry.scryfallId to 2.0)
        val offer = offerCards(listOf(cheap, dear), prices)
        assertEquals(listOf("Sol Ring" to 1, "Llanowar Elves" to 3), offer.map { it.name to it.quantity })
        // Foil only when every copy is.
        assertEquals(listOf(true, false), offer.map { it.foil })
        // No prices: the order they came in. And no more than the limit.
        assertEquals("Llanowar Elves", offerCards(listOf(cheap, dear)).first().name)
        assertEquals(1, offerCards(listOf(cheap, dear), prices, limit = 1).size)
    }
}
