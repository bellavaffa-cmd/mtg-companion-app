package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Reading and writing card lists for other apps. The web app has the same checks — see
 * MtgCompanionWeb/tests/collection/cardListText.test.ts.
 */
class CardListTextTest {

    @Test
    fun `plain text lists in the usual shapes are read`() {
        val parsed = parseCardList(
            listOf(
                "4 Lightning Bolt",
                "2x Counterspell",
                "1 Sol Ring (CMR) 472",
                "1 Sol Ring [CMR] 472 *F*",
                "3 Llanowar Elves (M19) 314 *E*",
                "1 Arcane Signet (foil)",
                "Swords to Plowshares",
                "1 Delver of Secrets // Insectile Aberration (ISD) 51",
                "1 Rhystic Study (PCMR) #Trade"
            ).joinToString("\n")
        )
        assertEquals(
            listOf(
                ListLine(4, "Lightning Bolt"),
                ListLine(2, "Counterspell"),
                ListLine(1, "Sol Ring", "cmr", "472"),
                ListLine(1, "Sol Ring", "cmr", "472", foil = true),
                ListLine(3, "Llanowar Elves", "m19", "314", foil = true),
                ListLine(1, "Arcane Signet", foil = true),
                ListLine(1, "Swords to Plowshares"),
                ListLine(1, "Delver of Secrets // Insectile Aberration", "isd", "51"),
                ListLine(1, "Rhystic Study", "pcmr", null)
            ),
            parsed.lines
        )
        assertEquals(emptyList<String>(), parsed.skipped)
    }

    @Test
    fun `headers, comments and blank lines are passed over`() {
        assertEquals(listOf(ListLine(1, "Forest")), parseCardList("// my binder\n\nCreatures (30)\nLands: 36\n1 Forest\n# note").lines)
    }

    @Test
    fun `cards under a Sideboard or Maybeboard header are marked as such`() {
        val parsed = parseCardList(
            listOf(
                "Commander",
                "1 Atraxa, Praetors' Voice",
                "Creatures (2)",
                "2 Llanowar Elves",
                "SIDEBOARD:",
                "1 Duress",
                "Maybeboard (1)",
                "1 Rhystic Study",
                "Deck",
                "1 Forest"
            ).joinToString("\n")
        )
        assertEquals(
            listOf(
                "Atraxa, Praetors' Voice" to ListSection.MAIN,
                "Llanowar Elves" to ListSection.MAIN,
                "Duress" to ListSection.SIDEBOARD,
                "Rhystic Study" to ListSection.MAYBEBOARD,
                "Forest" to ListSection.MAIN
            ),
            parsed.lines.map { it.name to it.section }
        )
    }

    @Test
    fun `an SB prefix marks one sideboard card`() {
        assertEquals(
            listOf(ListLine(4, "Lightning Bolt"), ListLine(2, "Duress", section = ListSection.SIDEBOARD), ListLine(1, "Island")),
            parseCardList("4 Lightning Bolt\nSB: 2 Duress\n1 Island").lines
        )
    }

    @Test
    fun `an Arena export's sideboard after a blank line is the sideboard`() {
        val arena = "Deck\n4 Lightning Bolt (M10) 146\n20 Mountain\n\n2 Duress\n1 Pyroblast"
        assertEquals(
            listOf(ListSection.MAIN, ListSection.MAIN, ListSection.SIDEBOARD, ListSection.SIDEBOARD),
            parseCardList(arena).lines.map { it.section }
        )
        // A plain list with a gap in it is still all one deck.
        assertEquals(
            listOf(ListSection.MAIN, ListSection.MAIN),
            parseCardList("4 Lightning Bolt\n\n20 Mountain").lines.map { it.section }
        )
        // A Commander section after the Arena sideboard goes back to the main deck.
        assertEquals(
            listOf(ListSection.MAIN, ListSection.SIDEBOARD, ListSection.MAIN),
            parseCardList("Deck\n1 Sol Ring\n\n1 Duress\n\nCommander\n1 Kenrith, the Returned King").lines.map { it.section }
        )
    }

    @Test
    fun `CSV rows keep their quotes and commas`() {
        assertEquals(listOf("1", "Borrowing 100,000 Arrows", "pls", "He said \"hi\""), csvCells("1,\"Borrowing 100,000 Arrows\",pls,\"He said \"\"hi\"\"\""))
    }

    @Test
    fun `ManaBox CSV uses set code, number, foil and Scryfall id`() {
        val csv = listOf(
            "Name,Set code,Set name,Collector number,Foil,Rarity,Quantity,ManaBox ID,Scryfall ID,Purchase price",
            "Sol Ring,CMR,Commander Legends,472,foil,uncommon,2,123,a5f3e6a9-1234-4bcd-9e8f-0123456789ab,1.5",
            "\"Borrowing 100,000 Arrows\",PLS,Portal Three Kingdoms,25,normal,uncommon,1,124,,"
        ).joinToString("\n")
        assertEquals(
            listOf(
                ListLine(2, "Sol Ring", "cmr", "472", "a5f3e6a9-1234-4bcd-9e8f-0123456789ab", foil = true),
                ListLine(1, "Borrowing 100,000 Arrows", "pls", "25")
            ),
            parseCardList(csv).lines
        )
    }

    @Test
    fun `Moxfield and Deckbox CSV - a set name in Edition is ignored, a code is used`() {
        val moxfield = "Count,Tradelist Count,Name,Edition,Condition,Language,Foil,Tags,Last Modified,Collector Number\r\n3,0,Counterspell,mh2,Near Mint,English,foil,,2024-01-01,267\r\n"
        assertEquals(listOf(ListLine(3, "Counterspell", "mh2", "267", foil = true)), parseCardList(moxfield).lines)
        val deckbox = "Count,Tradelist Count,Name,Edition,Card Number,Condition,Language,Foil\n1,0,Lightning Bolt,Magic 2010,146,Near Mint,English,"
        assertEquals(listOf(ListLine(1, "Lightning Bolt", null, "146")), parseCardList(deckbox).lines)
    }

    @Test
    fun `the list is written back one line per finish, with printings when given`() {
        val entries = listOf(
            CollectionEntry("b", "Sol Ring", null, quantity = 2, foilQuantity = 1),
            CollectionEntry("a", "Lightning Bolt", null, quantity = 4, foilQuantity = 0),
            CollectionEntry("c", "Mox Opal", null, quantity = 0, foilQuantity = 1)
        )
        assertEquals("4 Lightning Bolt\n1 Mox Opal *F*\n2 Sol Ring\n1 Sol Ring *F*", buildCardListText(entries))
        val printed = buildCardListText(entries, mapOf("b" to ("cmr" to "472")))
        assertEquals("4 Lightning Bolt\n1 Mox Opal *F*\n2 Sol Ring (CMR) 472\n1 Sol Ring (CMR) 472 *F*", printed)
        // What this app writes, it reads back the same.
        assertEquals(
            listOf(Triple(4, "Lightning Bolt", false), Triple(1, "Mox Opal", true), Triple(2, "Sol Ring", false), Triple(1, "Sol Ring", true)),
            parseCardList(printed).lines.map { Triple(it.quantity, it.name, it.foil) }
        )
    }
}
