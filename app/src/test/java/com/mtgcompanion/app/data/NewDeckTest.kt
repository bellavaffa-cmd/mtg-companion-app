package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.scryfall.ScryfallCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The new-deck flow's rules (NewDeck.kt): queries, the colour filter, sorting and the name. */
class NewDeckTest {

    @Test
    fun eachFormatsCommanderQuery() {
        assertEquals("is:commander legal:commander", commanderQuery(GameMode.COMMANDER))
        assertEquals(
            "legal:brawl (t:legendary (t:creature or t:planeswalker) or o:\"can be your commander\")",
            commanderQuery(GameMode.BRAWL)
        )
        assertNull(commanderQuery(GameMode.MODERN))
        assertNull(commanderQuery(GameMode.STANDARD))
        assertEquals("t:background legal:commander", BACKGROUND_QUERY)
    }

    @Test
    fun noColoursPickedShowsEverything() {
        assertTrue(identityFits(listOf("W", "U", "B"), emptySet()))
        assertTrue(identityFits(emptyList(), emptySet()))
    }

    @Test
    fun aCommanderShowsWhenAllItsColoursArePicked() {
        assertTrue(identityFits(listOf("U", "B"), setOf("U", "B")))
        assertTrue(identityFits(listOf("U"), setOf("U", "B")))
        assertFalse(identityFits(listOf("U", "B"), setOf("U")))
        assertFalse(identityFits(listOf("G"), setOf("U", "B")))
    }

    @Test
    fun colourlessFitsAnyPickAndColourlessAloneShowsOnlyThem() {
        assertTrue(identityFits(emptyList(), setOf("C")))
        assertTrue(identityFits(null, setOf("C", "W")))
        assertTrue(identityFits(emptyList(), setOf("W")))
        assertFalse(identityFits(listOf("W"), setOf("C")))
        assertTrue(identityFits(listOf("W"), setOf("C", "W")))
    }

    private fun card(name: String, rank: Int? = null, released: String? = null, type: String = "Legendary Creature", text: String? = null, identity: List<String> = emptyList()) =
        ScryfallCard(id = name, name = name, edhrecRank = rank, releasedAt = released, typeLine = type, oracleText = text, colorIdentity = identity)

    @Test
    fun sorting() {
        val a = card("Bravo", rank = 20, released = "2020-01-01")
        val b = card("alpha", rank = null, released = "2024-05-01")
        val c = card("Charlie", rank = 3, released = "2022-01-01")
        assertEquals(listOf("Charlie", "Bravo", "alpha"), sortCommanders(listOf(a, b, c), CommanderSort.POPULAR).map { it.name })
        assertEquals(listOf("alpha", "Bravo", "Charlie"), sortCommanders(listOf(a, b, c), CommanderSort.NAME).map { it.name })
        assertEquals(listOf("alpha", "Charlie", "Bravo"), sortCommanders(listOf(a, b, c), CommanderSort.NEWEST).map { it.name })
    }

    @Test
    fun searchLooksAtNameTypeAndRulesText() {
        val elf = card("Lathril", type = "Legendary Creature — Elf Noble", text = "Whenever you attack, create tokens.")
        assertTrue(matchesText(elf, "lath"))
        assertTrue(matchesText(elf, "ELF"))
        assertTrue(matchesText(elf, "create tokens"))
        assertTrue(matchesText(elf, "  "))
        assertFalse(matchesText(elf, "dragon"))
    }

    @Test
    fun pickerFiltersThenSorts() {
        val cards = listOf(
            card("Zur", rank = 5, identity = listOf("W", "U", "B")),
            card("Krenko", rank = 1, identity = listOf("R")),
            card("Kozilek", rank = 9, identity = emptyList())
        )
        assertEquals(listOf("Krenko", "Zur", "Kozilek"), pickerCards(cards, "", emptySet(), CommanderSort.POPULAR).map { it.name })
        assertEquals(listOf("Krenko", "Kozilek"), pickerCards(cards, "", setOf("R", "G"), CommanderSort.POPULAR).map { it.name })
        assertEquals(listOf("Kozilek"), pickerCards(cards, "", setOf("C"), CommanderSort.POPULAR).map { it.name })
        assertEquals(listOf("Kozilek", "Krenko"), pickerCards(cards, "k", setOf("R", "C"), CommanderSort.NAME).map { it.name })
    }

    @Test
    fun secondCommanderOptionsPairWithTheFirst() {
        val wilson = card("Wilson", text = "Choose a Background")
        val giants = card("Raised by Giants", type = "Legendary Enchantment — Background")
        val ring = card("Oblivion Ring", type = "Enchantment")
        assertEquals(listOf("Raised by Giants"), secondCommanderOptions(wilson, listOf(wilson, giants, ring)).map { it.name })
        val tymna = card("Tymna", text = "Partner")
        val thrasios = card("Thrasios", text = "Partner")
        assertEquals(listOf("Thrasios"), secondCommanderOptions(tymna, listOf(tymna, thrasios, wilson)).map { it.name })
    }

    @Test
    fun defaultNames() {
        assertEquals("Atraxa, Praetors' Voice", defaultDeckName(GameMode.COMMANDER, "Atraxa, Praetors' Voice", null))
        assertEquals("Tymna the Weaver & Thrasios, Triton Hero", defaultDeckName(GameMode.COMMANDER, "Tymna the Weaver", "Thrasios, Triton Hero"))
        assertEquals("New Modern deck", defaultDeckName(GameMode.MODERN, null, null))
        assertEquals("New Commander deck", defaultDeckName(GameMode.COMMANDER, null, null))
        assertEquals("Esika, God of the Tree", defaultDeckName(GameMode.COMMANDER, "Esika, God of the Tree // The Prismatic Bridge", null))
    }

    @Test
    fun commanderDecksOpenOnSuggestions() {
        assertEquals("Suggestions", landingTab(GameMode.COMMANDER))
        assertEquals("Suggestions", landingTab(GameMode.BRAWL))
        assertEquals("Cards", landingTab(GameMode.PIONEER))
    }

    @Test
    fun newestKeepsTheIncomingOrderOnATie() {
        val a = card("Zed", released = "2024-01-01")
        val b = card("Amy", released = "2024-01-01")
        assertEquals(listOf("Zed", "Amy"), sortCommanders(listOf(a, b), CommanderSort.NEWEST).map { it.name })
    }
}
