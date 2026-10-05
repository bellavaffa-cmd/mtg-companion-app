package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A deck's categories, the other ways to group its cards, and "Suggest categories". The web app's categories.test.ts runs the same cases. */
class DeckCategoriesTest {
    private fun card(id: String, quantity: Int = 1, categories: List<String>? = null) =
        DeckCardEntry(scryfallId = id, name = id, imageUrl = null, quantity = quantity, categories = categories)

    private fun deckOf(cards: List<DeckCardEntry>) = Deck("d", "D", cards = cards)

    @Test
    fun `setting a card's categories tidies them and marks the deck's categories known`() {
        val deck = deckOf(listOf(card("sol"), card("bolt"))).withCardCategories("sol", listOf(" Ramp ", "ramp", "Mana  rocks", ""))
        assertEquals(listOf("Ramp", "Mana rocks"), deck.cards[0].categories)
        assertEquals(emptyMap<String, Int>(), deck.categoryTargets)
        val cleared = deck.withCardCategories("sol", emptyList())
        assertNull(cleared.cards[0].categories)
        assertEquals(emptyMap<String, Int>(), cleared.categoryTargets)
        assertEquals(40, tidyCategory("x".repeat(50)).length)
    }

    @Test
    fun `counts and targets`() {
        var deck = deckOf(listOf(card("sol", 1, listOf("Ramp")), card("signet", 2, listOf("ramp", "Draw")), card("bolt", 4)))
        deck = deck.withCategoryTarget("Ramp", 12)
        deck = deck.withCategoryTarget("Win cons", 3)
        assertEquals(listOf("Draw", "Ramp", "Win cons"), deckCategoryNames(deck))
        assertEquals(mapOf("Draw" to 2, "Ramp" to 3, "Win cons" to 0), categoryCounts(deck))
        assertEquals("Ramp 3/12", categoryLine("Ramp", 3, 12))
        assertEquals("Draw 2", categoryLine("Draw", 2, null))
        assertEquals(mapOf("Win cons" to 3), deck.withCategoryTarget("ramp", null).categoryTargets)
        assertEquals(mapOf("Win cons" to 3), deck.withCategoryTarget("Ramp", 0).categoryTargets)
    }

    @Test
    fun `renaming and removing a category`() {
        var deck = deckOf(listOf(card("sol", 1, listOf("Ramp")), card("signet", 1, listOf("Rocks", "Ramp")))).withCategoryTarget("Rocks", 5)
        deck = deck.renamedCategory("rocks", "Ramp")
        assertEquals(listOf(listOf("Ramp"), listOf("Ramp")), deck.cards.map { it.categories })
        assertEquals(mapOf("Ramp" to 5), deck.categoryTargets)
        deck = deck.removedCategory("RAMP")
        assertEquals(listOf(null, null), deck.cards.map { it.categories })
        assertEquals(emptyMap<String, Int>(), deck.categoryTargets)
    }

    @Test
    fun `suggesting categories from role tags fills only cards without any`() {
        val tags = mapOf("sol" to listOf("mana-rock", "ramp"), "bolt" to listOf("removal", "burn"), "wrath" to listOf("board-wipe"), "bear" to emptyList(), "cs" to listOf("counterspell"))
        val deck = deckOf(listOf(card("sol"), card("bolt"), card("wrath", 1, listOf("Mine")), card("bear"), card("cs")))
        assertEquals(
            mapOf("sol" to listOf("Ramp"), "bolt" to listOf("Removal", "Burn"), "cs" to listOf("Counterspells")).toList(),
            suggestedCategories(deck) { tags[it].orEmpty() }.toList()
        )
        val (filled, n) = deck.withSuggestedCategories { tags[it].orEmpty() }
        assertEquals(3, n)
        assertEquals(listOf(listOf("Ramp"), listOf("Removal", "Burn"), listOf("Mine"), null, listOf("Counterspells")), filled.cards.map { it.categories })
        assertEquals(emptyMap<String, Int>(), filled.categoryTargets)
        assertEquals(0, deckOf(listOf(card("bear"))).withSuggestedCategories { emptyList() }.second)
    }

    private val facts = mapOf(
        "sol" to GroupingFacts(1.0, emptyList(), false, listOf("Mana rock", "Mana ramp")),
        "bolt" to GroupingFacts(1.0, listOf("R"), false, listOf("Removal")),
        "hoof" to GroupingFacts(8.0, listOf("G"), false, emptyList()),
        "kolaghan" to GroupingFacts(2.0, listOf("B", "R"), false, listOf("Removal")),
        "forest" to GroupingFacts(0.0, emptyList(), true, emptyList())
    )
    private val cards = listOf(card("sol", 1, listOf("Ramp")), card("bolt", 4, listOf("Removal", "Ramp")), card("hoof"), card("kolaghan", 1, listOf("Removal")), card("forest", 10), card("mystery"))
    private fun shown(groups: List<CardGroup>) = groups.map { listOf(it.label, it.cards.map { c -> c.scryfallId }, it.count, it.target) }

    @Test
    fun `grouped by category, mana value, colour and role tag`() {
        val f: (DeckCardEntry) -> GroupingFacts? = { facts[it.scryfallId] }
        assertEquals(
            listOf(
                listOf("Draw", emptyList<String>(), 0, 0),
                listOf("Ramp", listOf("bolt", "sol"), 5, 10),
                listOf("Removal", listOf("bolt", "kolaghan"), 5, null),
                listOf("Win cons", emptyList<String>(), 0, 2),
                listOf("No category", listOf("forest", "hoof", "mystery"), 12, null)
            ),
            shown(groupCards(cards, DeckGrouping.CATEGORY, f, mapOf("Ramp" to 10, "Win cons" to 2, "Draw" to 0)))
        )
        assertEquals(
            listOf(
                listOf("1 mana", listOf("bolt", "sol"), 5, null),
                listOf("2 mana", listOf("kolaghan"), 1, null),
                listOf("7+ mana", listOf("hoof"), 1, null),
                listOf("Lands", listOf("forest"), 10, null),
                listOf("Not known yet", listOf("mystery"), 1, null)
            ),
            shown(groupCards(cards, DeckGrouping.MANA_VALUE, f))
        )
        assertEquals(
            listOf(
                listOf("Red", listOf("bolt"), 4, null),
                listOf("Green", listOf("hoof"), 1, null),
                listOf("Multicolour", listOf("kolaghan"), 1, null),
                listOf("Colourless", listOf("sol"), 1, null),
                listOf("Lands", listOf("forest"), 10, null),
                listOf("Not known yet", listOf("mystery"), 1, null)
            ),
            shown(groupCards(cards, DeckGrouping.COLOUR, f))
        )
        assertEquals(
            listOf(
                listOf("Removal", listOf("bolt", "kolaghan"), 5, null),
                listOf("Mana ramp", listOf("sol"), 1, null),
                listOf("Mana rock", listOf("sol"), 1, null),
                listOf("No role tag", listOf("forest", "hoof", "mystery"), 12, null)
            ),
            shown(groupCards(cards, DeckGrouping.ROLE, f))
        )
    }
}
