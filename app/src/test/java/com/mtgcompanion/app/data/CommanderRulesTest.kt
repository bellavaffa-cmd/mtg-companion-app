package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.scryfall.ScryfallCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which cards can lead a deck, by format. */
class CommanderRulesTest {
    private val walker = "Legendary Planeswalker — Teferi"
    private val creature = "Legendary Creature — Human Wizard"

    @Test
    fun `Commander takes legendary creatures and cards that say so, Brawl planeswalkers too`() {
        assertTrue(canLeadDeck(creature, saysSo = false, mode = GameMode.COMMANDER))
        assertTrue(canLeadDeck(creature, saysSo = false, mode = GameMode.BRAWL))
        assertFalse(canLeadDeck(walker, saysSo = false, mode = GameMode.COMMANDER))
        assertTrue(canLeadDeck(walker, saysSo = false, mode = GameMode.BRAWL))
        assertTrue(canLeadDeck(walker, saysSo = true, mode = GameMode.COMMANDER))
        // Not legendary, or a format without a commander.
        assertFalse(canLeadDeck("Planeswalker — Jace", saysSo = false, mode = GameMode.BRAWL))
        assertFalse(canLeadDeck("Creature — Elf", saysSo = false, mode = GameMode.COMMANDER))
        assertFalse(canLeadDeck(creature, saysSo = false, mode = GameMode.STANDARD))
        assertFalse(canLeadDeck(null, saysSo = false, mode = GameMode.BRAWL))
    }

    @Test
    fun `a deck entry uses its stored answer and type line`() {
        val teferi = DeckCardEntry("t", "Teferi, Time Raveler", null, canBeCommander = false, typeLine = walker)
        assertFalse(teferi.canLead(GameMode.COMMANDER))
        assertTrue(teferi.canLead(GameMode.BRAWL))
        val atraxa = DeckCardEntry("a", "Atraxa", null, canBeCommander = true, typeLine = creature)
        assertTrue(atraxa.canLead(GameMode.COMMANDER))
        assertFalse(atraxa.canLead(GameMode.MODERN))
    }

    @Test
    fun `a Scryfall card answers per format, and canBeCommander stays the Commander rule`() {
        val teferi = ScryfallCard(id = "t", name = "Teferi, Time Raveler", typeLine = walker)
        assertFalse(teferi.canBeCommander)
        assertTrue(teferi.canLead(GameMode.BRAWL))
        val saysSo = ScryfallCard(id = "d", name = "Daretti", typeLine = walker, oracleText = "Daretti, Scrap Savant can be your commander.")
        assertTrue(saysSo.canBeCommander)
    }

    @Test
    fun `a Brawl deck led by a planeswalker has no commander problem`() {
        val teferi = DeckCardEntry("t", "Teferi, Time Raveler", null, typeLine = walker)
        val deck = Deck("d", "Teferi", commander = teferi, cards = listOf(teferi), gameMode = GameMode.BRAWL.name)
        val cards = mapOf("t" to ScryfallCard(id = "t", name = "Teferi, Time Raveler", typeLine = walker, colorIdentity = listOf("W", "U"), legalities = mapOf("brawl" to "legal")))
        val report = evaluateLegality(deck, cards)
        assertEquals(emptyList<LegalityIssue>(), report.issues.filter { it.kind == LegalityIssueKind.COMMANDER })
    }

    @Test
    fun `a binder card put in a deck gets the card's commander details, keeping its own count`() {
        val bare = DeckCardEntry("a", "Atraxa, Praetors' Voice", "art.jpg", quantity = 2, tags = listOf("Old"))
        assertFalse(bare.canLead(GameMode.COMMANDER))
        val card = ScryfallCard(
            id = "a", name = "Atraxa, Praetors' Voice", typeLine = creature,
            oracleText = "Flying, vigilance, deathtouch, lifelink", keywords = listOf("Flying")
        )
        val full = bare.withCardInfo(card)
        assertTrue(full.canBeCommander)
        assertTrue(full.canLead(GameMode.COMMANDER))
        assertEquals(creature, full.typeLine)
        assertEquals(2, full.quantity)
        assertEquals("art.jpg", full.imageUrl)
        assertTrue("Flying" in full.tags)
    }
}
