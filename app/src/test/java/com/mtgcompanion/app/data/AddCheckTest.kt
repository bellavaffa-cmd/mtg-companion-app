package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.scryfall.ScryfallCardFace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The check before cards go into a deck: format, commander's colours, copy limit — and what it says. */
class AddCheckTest {

    private fun card(
        id: String,
        name: String = id,
        identity: List<String> = emptyList(),
        legal: Map<String, String> = mapOf("commander" to "legal", "modern" to "legal", "vintage" to "legal"),
        type: String = "Instant",
        text: String? = null
    ) = ScryfallCard(id = id, name = name, colorIdentity = identity, legalities = legal, typeLine = type, oracleText = text)

    private fun entry(id: String, quantity: Int = 1, name: String = id, type: String = "Instant") =
        DeckCardEntry(scryfallId = id, name = name, imageUrl = null, quantity = quantity, typeLine = type)

    private fun commanderDeck(commander: DeckCardEntry?, partner: DeckCardEntry? = null, cards: List<DeckCardEntry> = emptyList()) =
        Deck("d", "Atraxa", commander = commander, partnerCommander = partner,
            cards = listOfNotNull(commander, partner) + cards, gameMode = GameMode.COMMANDER.name)

    private fun modern(cards: List<DeckCardEntry> = emptyList(), sideboard: List<DeckCardEntry> = emptyList()) =
        Deck("d", "Burn", cards = cards, sideboard = sideboard, gameMode = GameMode.MODERN.name)

    private fun cards(vararg c: ScryfallCard) = c.associateBy { it.id }

    private fun one(deck: Deck, add: AddCandidate, vararg known: ScryfallCard) = checkAdd(deck, listOf(add), cards(*known)).single()

    @Test
    fun `a legal card in the colours and under the limit is allowed`() {
        val atraxa = card("atraxa", "Atraxa", listOf("W", "U", "B", "G"), type = "Legendary Creature")
        val result = one(commanderDeck(entry("atraxa", name = "Atraxa")), AddCandidate("ponder", "Ponder"), atraxa, card("ponder", "Ponder", listOf("U")))
        assertTrue(result.allowed)
    }

    @Test
    fun `not legal and banned name the format`() {
        val notLegal = card("x", "Oko", legal = mapOf("modern" to "not_legal"))
        val banned = card("y", "Hogaak", legal = mapOf("modern" to "banned"))
        assertEquals(listOf("Not legal in Modern"), one(modern(), AddCandidate("x", "Oko"), notLegal).problems)
        assertEquals(listOf("Banned in Modern"), one(modern(), AddCandidate("y", "Hogaak"), banned).problems)
    }

    @Test
    fun `restricted is fine once, and over one says restricted rather than the copy limit`() {
        val vintage = Deck("d", "Shops", gameMode = GameMode.VINTAGE.name)
        val lotus = card("lotus", "Black Lotus", legal = mapOf("vintage" to "restricted"))
        assertTrue(one(vintage, AddCandidate("lotus", "Black Lotus"), lotus).allowed)
        assertEquals(listOf("Restricted in Vintage"), one(vintage, AddCandidate("lotus", "Black Lotus", quantity = 2), lotus).problems)
        val oneIn = vintage.copy(sideboard = listOf(entry("lotus", name = "Black Lotus")))
        assertEquals(listOf("Restricted in Vintage"), one(oneIn, AddCandidate("lotus", "Black Lotus"), lotus).problems)
    }

    @Test
    fun `a card outside the commander's colours is flagged with the commander's name`() {
        val atraxa = card("atraxa", "Atraxa", listOf("W", "U", "B", "G"))
        val bolt = card("bolt", "Lightning Bolt", listOf("R"))
        val result = one(commanderDeck(entry("atraxa", name = "Atraxa")), AddCandidate("bolt", "Lightning Bolt"), atraxa, bolt)
        assertEquals(listOf("Outside Atraxa's colours"), result.problems)
    }

    @Test
    fun `partners and backgrounds give their colours together`() {
        val tymna = card("tymna", "Tymna", listOf("W", "B"))
        val thrasios = card("thrasios", "Thrasios", listOf("G", "U"))
        val deck = commanderDeck(entry("tymna", name = "Tymna"), entry("thrasios", name = "Thrasios"))
        val growth = card("growth", "Growth Spiral", listOf("G", "U"))
        val bolt = card("bolt", "Lightning Bolt", listOf("R"))
        assertTrue(one(deck, AddCandidate("growth", "Growth Spiral"), tymna, thrasios, growth).allowed)
        assertEquals(listOf("Outside Tymna & Thrasios's colours"), one(deck, AddCandidate("bolt", "Lightning Bolt"), tymna, thrasios, bolt).problems)
    }

    @Test
    fun `a colourless commander takes only colourless cards`() {
        val karn = card("karn", "Karn", emptyList())
        val deck = commanderDeck(entry("karn", name = "Karn"))
        assertTrue(one(deck, AddCandidate("ring", "Sol Ring"), karn, card("ring", "Sol Ring")).allowed)
        assertEquals(listOf("Outside Karn's colours"), one(deck, AddCandidate("ponder", "Ponder"), karn, card("ponder", "Ponder", listOf("U"))).problems)
    }

    @Test
    fun `no commander, or a format without one, checks no colours`() {
        val bolt = card("bolt", "Lightning Bolt", listOf("R"))
        assertTrue(one(commanderDeck(null), AddCandidate("bolt", "Lightning Bolt"), bolt).allowed)
        assertTrue(one(modern(), AddCandidate("bolt", "Lightning Bolt"), bolt).allowed)
    }

    @Test
    fun `copies count every printing, main deck and sideboard together`() {
        val deck = modern(listOf(entry("bolt-a", 2, "Lightning Bolt")), listOf(entry("bolt-b", 1, "Lightning Bolt")))
        assertTrue(one(deck, AddCandidate("bolt-c", "Lightning Bolt"), card("bolt-c", "Lightning Bolt")).allowed)
        assertEquals(
            listOf("Over the copy limit (4 max)"),
            one(deck, AddCandidate("bolt-c", "Lightning Bolt", quantity = 2, sideboard = true), card("bolt-c", "Lightning Bolt")).problems
        )
    }

    @Test
    fun `singleton formats allow one, and cards in the same add count together`() {
        val deck = commanderDeck(null, cards = listOf(entry("ring", name = "Sol Ring")))
        assertEquals(listOf("Singleton: only 1 copy allowed in Commander"), one(deck, AddCandidate("ring2", "Sol Ring")).problems)
        val results = checkAdd(commanderDeck(null), listOf(AddCandidate("a", "Ponder"), AddCandidate("b", "Ponder")), emptyMap())
        assertEquals(listOf(true, false), results.map { it.allowed })
    }

    @Test
    fun `brawl says singleton too`() {
        val deck = Deck("d", "Brawl deck", cards = listOf(entry("ring", name = "Sol Ring")), gameMode = GameMode.BRAWL.name)
        assertEquals(listOf("Singleton: only 1 copy allowed in Brawl"), one(deck, AddCandidate("ring2", "Sol Ring")).problems)
    }

    @Test
    fun `one more copy from a plus checks only the copy limit`() {
        val atraxa = card("atraxa", "Atraxa", listOf("W", "U", "B", "G"))
        val bad = card("bad", "Bad", listOf("R"), legal = mapOf("commander" to "banned"))
        val deck = commanderDeck(
            entry("atraxa", name = "Atraxa"),
            cards = listOf(entry("bad", name = "Bad"), entry("mountain", 10, "Mountain", "Basic Land — Mountain"))
        )
        val known = cards(atraxa, bad)
        assertEquals(
            listOf("Singleton: only 1 copy allowed in Commander"),
            checkAdd(deck, listOf(AddCandidate("bad", "Bad")), known, copiesOnly = true).single().problems
        )
        assertTrue(checkAdd(deck, listOf(AddCandidate("mountain", "Mountain")), known, copiesOnly = true).single().allowed)
        val banned = card("bolt", "Lightning Bolt", legal = mapOf("modern" to "banned"))
        assertTrue(checkAdd(modern(listOf(entry("bolt", 3, "Lightning Bolt"))), listOf(AddCandidate("bolt", "Lightning Bolt")), cards(banned), copiesOnly = true).single().allowed)
        assertEquals(
            listOf("Over the copy limit (4 max)"),
            checkAdd(modern(listOf(entry("bolt", 4, "Lightning Bolt"))), listOf(AddCandidate("bolt", "Lightning Bolt")), cards(banned), copiesOnly = true).single().problems
        )
    }

    @Test
    fun `basics and any-number cards have no limit`() {
        val deck = commanderDeck(null, cards = listOf(entry("forest", 30, "Forest", "Basic Land — Forest")))
        assertTrue(one(deck, AddCandidate("forest", "Forest", quantity = 5)).allowed)
        assertTrue(one(deck, AddCandidate("snow", "Snow-Covered Island", quantity = 5)).allowed)
        val rats = card("rats", "Relentless Rats", listOf("B"), text = "A deck can have any number of cards named Relentless Rats.")
        assertTrue(one(deck, AddCandidate("rats", "Relentless Rats", quantity = 20), rats).allowed)
        val faced = ScryfallCard(id = "f", name = "Odd", cardFaces = listOf(ScryfallCardFace(oracleText = "A deck can have any number of cards named Odd.")))
        assertTrue(one(deck, AddCandidate("f", "Odd", quantity = 9), faced).allowed)
    }

    @Test
    fun `without card data only the copy limit is checked`() {
        val deck = commanderDeck(entry("atraxa", name = "Atraxa"), cards = listOf(entry("ring", name = "Sol Ring")))
        assertTrue(one(deck, AddCandidate("bolt", "Lightning Bolt")).allowed)
        assertEquals(listOf("Singleton: only 1 copy allowed in Commander"), one(deck, AddCandidate("ring2", "Sol Ring")).problems)
        // The card known but not the commander: no colour check.
        assertTrue(one(deck, AddCandidate("bolt", "Lightning Bolt"), card("bolt", "Lightning Bolt", listOf("R"))).allowed)
    }

    @Test
    fun `several problems are all said`() {
        val atraxa = card("atraxa", "Atraxa", listOf("W", "U", "B", "G"))
        val deck = commanderDeck(entry("atraxa", name = "Atraxa"), cards = listOf(entry("bad", name = "Bad")))
        val bad = card("bad2", "Bad", listOf("R"), legal = mapOf("commander" to "banned"))
        assertEquals(
            listOf("Banned in Commander", "Outside Atraxa's colours", "Singleton: only 1 copy allowed in Commander"),
            one(deck, AddCandidate("bad2", "Bad"), atraxa, bad).problems
        )
    }

    @Test
    fun `the confirmation for one card`() {
        val results = listOf(AddCheckResult("x", "Oko", listOf("Banned in Modern", "Over the copy limit (4 max)")))
        assertEquals("Add Oko anyway?", addCheckTitle(results, "Burn"))
        assertEquals(listOf("Banned in Modern", "Over the copy limit (4 max)"), addCheckLines(results))
        assertFalse(addCheckOffersAllowedOnly(results))
    }

    @Test
    fun `the confirmation for several cards lists eight and counts the rest`() {
        val failing = (1..10).map { AddCheckResult("$it", "Card $it", listOf("Not legal in Modern")) }
        val results = failing + AddCheckResult("ok", "Fine", emptyList())
        assertEquals("10 of these cards aren't allowed in Burn", addCheckTitle(results, "Burn"))
        val lines = addCheckLines(results)
        assertEquals(9, lines.size)
        assertEquals("Card 1: Not legal in Modern", lines.first())
        assertEquals("and 2 more", lines.last())
        assertTrue(addCheckOffersAllowedOnly(results))
        assertFalse(addCheckOffersAllowedOnly(failing))
    }
}
