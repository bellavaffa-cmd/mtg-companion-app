package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.scryfall.ScryfallCardFace
import com.mtgcompanion.app.ui.common.AddVerb
import com.mtgcompanion.app.ui.common.addToMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Draft and sealed: the Limited format's rules, the pool by colour, its strongest pairs, and the
 * basic lands to add. The web app has the same cases — see tests/decks/limited.test.ts.
 */
class LimitedTest {

    private fun entry(id: String, quantity: Int = 1, typeLine: String = "Creature") =
        DeckCardEntry(scryfallId = id, name = id, imageUrl = null, quantity = quantity, typeLine = typeLine)

    private fun card(
        id: String,
        colors: List<String>? = null,
        type: String = "Creature",
        cost: String? = null,
        legal: Map<String, String> = mapOf("standard" to "not_legal"),
        faces: List<ScryfallCardFace>? = null
    ) = ScryfallCard(id = id, name = id, colors = colors, typeLine = type, manaCost = cost, legalities = legal, cardFaces = faces)

    private fun known(vararg cards: ScryfallCard) = cards.associateBy { it.id }

    private fun limited(cards: List<DeckCardEntry>, sideboard: List<DeckCardEntry> = emptyList()) =
        Deck("d", "Draft", cards = cards, sideboard = sideboard, gameMode = GameMode.LIMITED.name)

    @Test
    fun `Limited is a format, called that, with a pool for a sideboard`() {
        assertTrue(GameMode.entries.contains(GameMode.LIMITED))
        assertEquals("Limited", GameMode.LIMITED.label)
        assertEquals(GameMode.LIMITED, GameMode.fromName("LIMITED"))
        assertTrue(GameMode.LIMITED.limited)
        assertFalse(GameMode.MODERN.limited)
        assertTrue(GameMode.LIMITED.hasSideboard)
        assertNull(GameMode.LIMITED.sideboardLimit)
        assertEquals(15, GameMode.MODERN.sideboardLimit)
        assertEquals("Pool", sideboardName(GameMode.LIMITED))
        assertEquals("Sideboard", sideboardName(GameMode.MODERN))
        assertEquals("Pool", sideboardChoice(listOf(true)))
        assertEquals("Sideboard", sideboardChoice(listOf(true, false)))
        assertEquals("Moved Duress to the pool in Draft", addToMessage(AddVerb.MOVE, "Duress", "Draft", sideboard = true, pool = true))
    }

    @Test
    fun `an unknown mode reads as Commander, keeping its name`() {
        // What an older app does with a mode it doesn't know: the deck's string stays as it was.
        val deck = Deck("d", "Future", gameMode = "SOMETHING_NEW")
        assertEquals(GameMode.COMMANDER, deck.mode)
        assertEquals("SOMETHING_NEW", deck.gameMode)
    }

    @Test
    fun `a Limited deck needs 40 cards, takes any card, any number of copies, and any size of pool`() {
        val cards = known(card("a"), card("b"))
        val short = evaluateLegality(limited(listOf(entry("a", 39)), listOf(entry("b", 60))), cards)
        assertEquals(listOf("Deck has 39 cards; Limited requires at least 40."), short.issues.map { it.reason })
        assertTrue(evaluateLegality(limited(listOf(entry("a", 40)), listOf(entry("b", 60))), cards).legal)
        assertFalse(short.issues.any { it.kind == LegalityIssueKind.COMMANDER })
    }

    @Test
    fun `adding to a Limited deck or its pool is never stopped`() {
        val deck = limited(listOf(entry("a", 6)), listOf(entry("b", 30)))
        val cards = known(card("a", legal = mapOf("standard" to "banned")), card("b"))
        assertTrue(checkAdd(deck, listOf(AddCandidate("a", "a", 2)), cards).single().allowed)
        assertTrue(checkAdd(deck, listOf(AddCandidate("b", "b", 5, sideboard = true)), cards).single().allowed)
        assertNull(copyLimitOf(GameMode.LIMITED, "a", cards["a"]))
    }

    @Test
    fun `Limited land advice is about 17`() {
        assertEquals(emptyList<String>(), manaBaseAdvice(emptyList(), emptyList(), 17, GameMode.LIMITED))
        assertTrue(manaBaseAdvice(emptyList(), emptyList(), 14, GameMode.LIMITED).first().contains("16–18"))
        assertTrue(manaBaseAdvice(emptyList(), emptyList(), 20, GameMode.LIMITED).first().contains("heavy"))
    }

    @Test
    fun `mana symbols count each colour, hybrid towards both`() {
        assertEquals(mapOf("W" to 2), manaPips("{2}{W}{W}"))
        assertEquals(mapOf("W" to 1, "U" to 1, "B" to 1, "G" to 1), manaPips("{W/U}{B/P}{G}"))
        assertEquals(emptyMap<String, Int>(), manaPips(null))
        assertEquals(emptyMap<String, Int>(), manaPips("{X}{C}"))
    }

    @Test
    fun `a card's colours - Scryfall's, or its front face's cost`() {
        assertEquals(listOf("W", "G"), cardColours(card("a", colors = listOf("G", "W"))))
        assertEquals(listOf("R"), cardColours(card("dfc", faces = listOf(ScryfallCardFace(name = "Front", manaCost = "{1}{R}"), ScryfallCardFace(name = "Back")))))
        assertEquals(emptyList<String>(), cardColours(null))
    }

    @Test
    fun `the pool by colour - each colour, multicolour, colourless, lands`() {
        val cards = known(
            card("angel", listOf("W")), card("drake", listOf("U")), card("gold", listOf("W", "U")),
            card("golem", emptyList(), "Artifact Creature"), card("gate", emptyList(), "Land — Gate")
        )
        assertEquals("L", poolGroupOf(entry("gate", 1, "Land"), cards["gate"]))
        // An unknown card is sorted by its saved type line: a land is still a land.
        assertEquals("L", poolGroupOf(entry("x", 1, "Basic Land — Forest"), null))
        val groups = poolGroups(listOf(entry("gate"), entry("golem"), entry("gold"), entry("drake", 2), entry("angel")), cards)
        assertEquals(
            listOf(Triple("W", "White", 1), Triple("U", "Blue", 2), Triple("M", "Multicolour", 1), Triple("C", "Colourless", 1), Triple("L", "Lands", 1)),
            groups.map { Triple(it.key, it.label, it.count) }
        )
    }

    @Test
    fun `the strongest pairs count playable cards in the pair or colourless, top three`() {
        val cards = known(
            card("w", listOf("W")), card("u", listOf("U")), card("b", listOf("B")),
            card("wu", listOf("W", "U")), card("c", emptyList()), card("land", emptyList(), "Land")
        )
        val pool = listOf(entry("w", 3), entry("u", 2), entry("b", 2), entry("wu"), entry("c"), entry("land", 5, "Land"))
        // WU: 3 + 2 + 1 + 1 = 7; WB: 3 + 2 + 1 = 6; UB: 2 + 2 + 1 = 5; WR only 4.
        assertEquals(listOf(ColourPair("WU", 7), ColourPair("WB", 6), ColourPair("UB", 5)), strongestPairs(pool, cards))
        assertEquals(emptyList<ColourPair>(), strongestPairs(emptyList(), cards))
    }

    @Test
    fun `basic lands - 17 for 40 cards, split by mana symbols, at least one of each colour`() {
        assertEquals(mapOf("W" to 11, "U" to 6), basicLandSplit(mapOf("W" to 10, "U" to 5), 17))
        assertEquals(mapOf("W" to 16, "G" to 1), basicLandSplit(mapOf("W" to 20, "G" to 1), 17))
        assertEquals(mapOf("R" to 17), basicLandSplit(mapOf("R" to 3), 17))
        assertEquals(mapOf("W" to 1, "U" to 1, "B" to 1), basicLandSplit(mapOf("W" to 1, "U" to 1, "B" to 1), 2))
        assertEquals(emptyMap<String, Int>(), basicLandSplit(emptyMap(), 17))
        assertEquals(emptyMap<String, Int>(), basicLandSplit(mapOf("W" to 4), 0))
    }

    @Test
    fun `the main deck's symbols leave out lands, and lands already in come off the 17`() {
        val cards = known(card("a", cost = "{1}{W}"), card("b", cost = "{U}{U}"), card("dual", type = "Land", cost = ""))
        val main = listOf(entry("a", 2), entry("b"), entry("dual", 2, "Land"))
        assertEquals(mapOf("W" to 2, "U" to 2), mainDeckPips(main, cards))
        assertEquals(15, basicsWanted(main, cards))
        assertEquals(0, basicsWanted(listOf(entry("x", 20, "Basic Land — Plains")), emptyMap()))
    }

    @Test
    fun `the pool for a binder - deck and pool together, one row per printing`() {
        val deck = limited(listOf(entry("a", 2), entry("b")), listOf(entry("a", 1), entry("c", 3)))
        assertEquals(listOf("a" to 3, "b" to 1, "c" to 3), poolCopies(deck).map { it.scryfallId to it.quantity })
    }
}
