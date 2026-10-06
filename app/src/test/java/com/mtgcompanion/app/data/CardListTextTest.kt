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
        // Their Condition and Language columns come along too (see CopyDetailsTest).
        assertEquals(listOf(ListLine(3, "Counterspell", "mh2", "267", foil = true, condition = "NM", language = "en")), parseCardList(moxfield).lines)
        val deckbox = "Count,Tradelist Count,Name,Edition,Card Number,Condition,Language,Foil\n1,0,Lightning Bolt,Magic 2010,146,Near Mint,English,"
        assertEquals(listOf(ListLine(1, "Lightning Bolt", null, "146", condition = "NM", language = "en")), parseCardList(deckbox).lines)
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

    // Real export headers from each app the welcome flow names (trimmed to a row or two). The web
    // app's tests/collection/cardListText.test.ts has the same cases.
    @Test
    fun `Archidekt CSV - Edition Code over Edition Name, Finish says foil`() {
        val csv = listOf(
            "Quantity,Name,Finish,Condition,Date Added,Language,Purchase Price,Tags,Edition Name,Edition Code,Multiverse Id,Scryfall ID,MTGO ID,Collector Number",
            "1,Sol Ring,Foil,NM,2024-02-01,EN,,,Commander Legends,cmr,,a5f3e6a9-1234-4bcd-9e8f-0123456789ab,,472",
            "2,Counterspell,Normal,LP,2024-02-01,JA,,,Modern Horizons 2,mh2,,,,267"
        ).joinToString("\n")
        assertEquals(
            listOf(
                ListLine(1, "Sol Ring", "cmr", "472", "a5f3e6a9-1234-4bcd-9e8f-0123456789ab", foil = true, condition = "NM", language = "en"),
                ListLine(2, "Counterspell", "mh2", "267", condition = "LP", language = "ja")
            ),
            parseCardList(csv).lines
        )
    }

    @Test
    fun `TCGplayer CSV - Simple Name and Set Code are used, the finish can sit in Printing`() {
        val csv = listOf(
            "Quantity,Name,Simple Name,Set,Card Number,Set Code,Printing,Condition,Language,Rarity,Product ID,SKU",
            "1,Sol Ring (Foil Etched),Sol Ring,Commander Legends,472,CMR,Foil,Near Mint Foil,English,Uncommon,229952,4637171",
            "3,Lightning Bolt,Lightning Bolt,Magic 2010,146,M10,Normal,Lightly Played,English,Common,33350,123"
        ).joinToString("\n")
        assertEquals(
            listOf(
                ListLine(1, "Sol Ring", "cmr", "472", foil = true, condition = "NM", language = "en"),
                ListLine(3, "Lightning Bolt", "m10", "146", condition = "LP", language = "en")
            ),
            parseCardList(csv).lines
        )
        // The seller inventory export names things differently.
        val seller = "TCGplayer Id,Product Line,Set Name,Product Name,Title,Number,Rarity,Condition,TCG Market Price,Total Quantity,Add to Quantity\n" +
            "33350,Magic,Magic 2010,Lightning Bolt,,146,Common,Near Mint,1.20,4,0"
        assertEquals(listOf(ListLine(4, "Lightning Bolt", null, "146", condition = "NM")), parseCardList(seller).lines)
    }

    @Test
    fun `Dragon Shield CSV - the sep= line is passed over and run-together conditions are read`() {
        val csv = listOf(
            "\"sep=,\"",
            "Folder Name,Quantity,Trade Quantity,Card Name,Set Code,Set Name,Card Number,Condition,Printing,Language,Price Bought,Date Bought,LOW,MID,MARKET",
            "Binder,2,0,Arcane Signet,CMR,Commander Legends,297,NearMint,Foil,English,0.50,2024-03-01,0.3,0.4,0.5",
            "Binder,1,0,Counterspell,MH2,Modern Horizons 2,267,LightPlayed,Normal,German,1.00,2024-03-01,0.8,0.9,1.0"
        ).joinToString("\r\n")
        assertEquals(
            listOf(
                // Its Folder Name says where they're kept (ImportPlaces.kt).
                ListLine(2, "Arcane Signet", "cmr", "297", foil = true, condition = "NM", language = "en", location = "Binder"),
                ListLine(1, "Counterspell", "mh2", "267", condition = "LP", language = "de", location = "Binder")
            ),
            parseCardList(csv).lines
        )
        // Excel in much of Europe separates with semicolons and says so the same way.
        val semi = "sep=;\nQuantity;Card Name;Set Code;Card Number;Printing\n1;Sol Ring;CMR;472;Normal"
        assertEquals(listOf(ListLine(1, "Sol Ring", "cmr", "472")), parseCardList(semi).lines)
    }

    @Test
    fun `ManaBox, Moxfield and Deckbox headers still read in full`() {
        val manabox = "Name,Set code,Set name,Collector number,Foil,Rarity,Quantity,ManaBox ID,Scryfall ID,Purchase price,Misprint,Altered,Condition,Language,Purchase price currency\n" +
            "Sol Ring,CMR,Commander Legends,472,etched,uncommon,1,1,,0.5,false,false,near_mint,en,GBP"
        assertEquals(listOf(ListLine(1, "Sol Ring", "cmr", "472", foil = true, condition = "NM", language = "en")), parseCardList(manabox).lines)
        val moxfield = "\"Count\",\"Tradelist Count\",\"Name\",\"Edition\",\"Condition\",\"Language\",\"Foil\",\"Tags\",\"Last Modified\",\"Collector Number\",\"Alter\",\"Proxy\",\"Purchase Price\"\n" +
            "\"1\",\"0\",\"Sol Ring\",\"cmr\",\"Near Mint\",\"English\",\"etched\",\"\",\"2024-01-01 00:00:00.000000\",\"472\",\"False\",\"False\",\"\""
        assertEquals(listOf(ListLine(1, "Sol Ring", "cmr", "472", foil = true, condition = "NM", language = "en")), parseCardList(moxfield).lines)
        val deckbox = "Count,Tradelist Count,Name,Edition,Card Number,Condition,Language,Foil,Signed,Artist Proof,Altered Art,Misprint,Promo,Textless,My Price\n" +
            "2,0,Counterspell,Modern Horizons 2,267,Good (Lightly Played),Japanese,foil,,,,,,,\$1.00"
        assertEquals(listOf(ListLine(2, "Counterspell", null, "267", foil = true, condition = "LP", language = "ja")), parseCardList(deckbox).lines)
    }
}
