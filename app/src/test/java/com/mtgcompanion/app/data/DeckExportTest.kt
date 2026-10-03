package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** A deck as text for other apps — Simple, Exact printing, Arena and MTGO. */
class DeckExportTest {

    private fun card(id: String, name: String, quantity: Int = 1, typeLine: String? = "Instant", back: String? = null) =
        DeckCardEntry(id, name, null, quantity, typeLine = typeLine, backImageUrl = back)

    private val atraxa = card("atraxa", "Atraxa, Praetors' Voice", typeLine = "Legendary Creature")
    private val commanderDeck = Deck(
        "d", "Atraxa", commander = atraxa,
        cards = listOf(card("sol", "Sol Ring", typeLine = "Artifact"), atraxa, card("forest", "Forest", 30, "Basic Land — Forest")),
        considering = listOf(card("x", "Rhystic Study"))
    )
    private val burn = Deck(
        "b", "Burn", gameMode = GameMode.MODERN.name,
        cards = listOf(card("bolt", "Lightning Bolt", 4), card("delver", "Delver of Secrets // Insectile Aberration", 2, "Creature — Human Wizard // Creature — Human Insect", back = "back.jpg")),
        sideboard = listOf(card("fire", "Fire // Ice", 2, "Instant // Instant"), card("smash", "Smash to Smithereens", 3))
    )
    private val printings = mapOf("bolt" to ("2xm" to "117"), "delver" to ("isd" to "51"), "fire" to ("mh2" to "290"), "atraxa" to ("cm2" to "10"), "sol" to ("cmr" to "472"))

    @Test
    fun `simple lists commanders first, then the rest by name, never Considering`() {
        assertEquals(
            "1 Atraxa, Praetors' Voice\n30 Forest\n1 Sol Ring",
            deckExportText(commanderDeck, DeckExportFormat.SIMPLE)
        )
    }

    @Test
    fun `simple and exact add a Sideboard section that reads back into the sideboard`() {
        val text = deckExportText(burn, DeckExportFormat.EXACT, printings)
        assertEquals(
            "2 Delver of Secrets // Insectile Aberration (ISD) 51\n4 Lightning Bolt (2XM) 117\n\n" +
                "Sideboard\n2 Fire // Ice (MH2) 290\n3 Smash to Smithereens",
            text
        )
        val parsed = parseCardList(text).lines
        assertEquals(listOf(ListSection.MAIN, ListSection.MAIN, ListSection.SIDEBOARD, ListSection.SIDEBOARD), parsed.map { it.section })
    }

    @Test
    fun `arena has Commander, Deck and Sideboard sections and front-face names`() {
        assertEquals(
            "Commander\n1 Atraxa, Praetors' Voice (CM2) 10\n\nDeck\n30 Forest\n1 Sol Ring (CMR) 472",
            deckExportText(commanderDeck, DeckExportFormat.ARENA, printings)
        )
        assertEquals(
            "Deck\n2 Delver of Secrets (ISD) 51\n4 Lightning Bolt (2XM) 117\n\nSideboard\n2 Fire // Ice (MH2) 290\n3 Smash to Smithereens",
            deckExportText(burn, DeckExportFormat.ARENA, printings)
        )
    }

    @Test
    fun `mtgo is plain lines, the sideboard after a blank line, no set codes`() {
        assertEquals(
            "2 Delver of Secrets\n4 Lightning Bolt\n\n2 Fire/Ice\n3 Smash to Smithereens",
            deckExportText(burn, DeckExportFormat.MTGO, printings)
        )
        // A Commander deck's commander goes where MTGO looks for it: the sideboard part.
        assertEquals("30 Forest\n1 Sol Ring\n\n1 Atraxa, Praetors' Voice", deckExportText(commanderDeck, DeckExportFormat.MTGO))
    }

    @Test
    fun `adventures and omens use the front face, split cards keep both halves`() {
        val giant = card("g", "Bonecrusher Giant // Stomp", typeLine = "Creature — Giant // Instant — Adventure")
        assertEquals("Bonecrusher Giant", clientCardName(giant, DeckExportFormat.MTGO))
        assertEquals("Bonecrusher Giant", clientCardName(giant, DeckExportFormat.ARENA))
        val fire = card("f", "Fire // Ice", typeLine = "Instant // Instant")
        assertEquals("Fire // Ice", clientCardName(fire, DeckExportFormat.ARENA))
        assertEquals("Fire // Ice", clientCardName(fire, DeckExportFormat.SIMPLE))
    }
}
