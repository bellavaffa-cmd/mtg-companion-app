package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The To sell list: where each card to sell is, its worth, the quick rules, the exports, the pull list
 * and marking cards sold — the same on both apps. The web app has the same checks — see
 * MtgCompanionWeb/tests/collection/selling.test.ts.
 */
class SellingTest {

    private fun at(placeId: String, qty: Int, foil: Boolean = false, section: String? = null, page: Int? = null, slot: Int? = null) =
        CopyPlace(placeId, qty, if (foil) true else null, section, page, slot)

    private val rares = StoragePlace("rares", "Rares binder", PlaceKind.BINDER.name, createdAt = 1)
    private val trade = StoragePlace("trade", "Trade binder", PlaceKind.BINDER.name, createdAt = 2)
    private val red = StoragePlace("red", "Red box", PlaceKind.BOX.name, sections = listOf("Blue"), createdAt = 3)
    private val cols = listOf(
        Collection(
            UNSORTED_COLLECTION_ID, UNSORTED_COLLECTION_NAME,
            listOf(
                CollectionEntry("ring", "The One Ring", null, quantity = 1, places = listOf(at("rares", 1, page = 2, slot = 1)), condition = "NM", forSale = 1),
                CollectionEntry("rag", "Ragavan, Nimble Pilferer", null, quantity = 1, places = listOf(at("rares", 1, page = 6, slot = 3)), condition = "LP", forSale = 1),
                CollectionEntry("fof", "Fact or Fiction", null, quantity = 4, places = listOf(at("trade", 4, page = 4, slot = 6)), forSale = 3, forTrade = 4),
                CollectionEntry(
                    "opt", "Opt", null, quantity = 2, foilQuantity = 1,
                    places = listOf(at("red", 1, section = "Blue"), at("rares", 1, foil = true, page = 1, slot = 1)), language = "ja", forSale = 3
                ),
                CollectionEntry("bolt", "Lightning Bolt", null, quantity = 5),
                CollectionEntry("island", "Island", null, quantity = 30),
                CollectionEntry("sheoldred", "Sheoldred, the Apocalypse", null, quantity = 2),
                CollectionEntry("sol", "Sol Ring", null, quantity = 1, forSale = 0)
            ),
            createdAt = 0, type = CollectionType.OWNED.name, storagePlaces = listOf(rares, trade, red)
        ),
        Collection("stuff", "Trade stuff", listOf(CollectionEntry("bolt", "Lightning Bolt", null, quantity = 2)), createdAt = 1, type = CollectionType.OWNED.name),
        Collection("wish", "Wishlist", listOf(CollectionEntry("bolt", "Lightning Bolt", null, quantity = 9)), createdAt = 2, type = CollectionType.WISHLIST.name)
    )
    private val deck = Deck(
        "d", "Burn", cards = listOf(DeckCardEntry("bolt", "Lightning Bolt", null, quantity = 1), DeckCardEntry("sol", "Sol Ring", null, quantity = 1)),
        ownership = DeckOwnership.PHYSICAL.name, createdAt = 1
    )
    private val prices = mapOf(
        "ring" to SellPrinting("ltr", "246", 62.0, null, 55.0),
        "rag" to SellPrinting("mh2", "138", 48.0, null, 40.0),
        "fof" to SellPrinting("tsr", "64", 1.5, null),
        "opt" to SellPrinting("xln", "65", 0.1, 0.5, 0.08),
        "bolt" to SellPrinting("m10", "146", 2.0, null),
        "sheoldred" to SellPrinting("dmu", "107", 80.0, null),
        "sol" to SellPrinting("cmm", "410", 10.0, null)
    )
    private val facts = { id: String -> prices[id] }

    @Test
    fun copiesToSellNeverMoreThanTheEntryHas() {
        val e = CollectionEntry("x", "X", null, quantity = 2, foilQuantity = 1, forSale = 9)
        assertEquals(3, forSaleOf(e))
        assertEquals(2 to 1, sellSplit(e))
        assertEquals(1, withForSale(e, 1).forSale)
        assertEquals(0, withForSale(e, 0).forSale)
        val plain = CollectionEntry("x", "X", null, quantity = 2)
        assertSame(plain, withForSale(plain, 0))
    }

    @Test
    fun theListSaysWhereEachCopyIsAndWhatItsWorth() {
        val rows = sellRows(cols)
        assertEquals(listOf("Fact or Fiction ×3", "Opt ×3", "Ragavan, Nimble Pilferer", "The One Ring"), rows.map { sellRowTitle(it) })
        assertEquals(
            listOf("Trade binder · p4 s6", "Red box › Blue · Rares binder · p1 s1 · No place yet", "Rares binder · p6 s3 · LP", "Rares binder · p2 s1 · NM"),
            rows.map { it.where }
        )
        val opt = rows[1]
        assertEquals(2, opt.plain)
        assertEquals(1, opt.foil)
        assertEquals(1, opt.loose)
        assertEquals(listOf(at("red", 1, section = "Blue"), at("rares", 1, foil = true, page = 1, slot = 1)), opt.lines)
        assertEquals(4.5, sellRowUsd(rows[0], facts)!!, 1e-9)
        assertEquals(0.7, sellRowUsd(opt, facts)!!, 1e-9)
        assertNull(sellRowUsd(rows[0], { null }))
        assertEquals(115.2, sellTotalUsd(rows, facts), 1e-9)
    }

    @Test
    fun sparesOverFourMarksTheCopiesBeyondFourFromTheBindersUnsortedFirst() {
        val (after, added) = markSparesToSell(cols, listOf(deck))
        // 5 + 2 in binders and 1 in the deck: 4 beyond four. Islands are basic lands.
        assertEquals(4, added)
        assertEquals(4, after[0].entries.first { it.scryfallId == "bolt" }.forSale)
        assertNull(after[1].entries[0].forSale)
        assertNull(after[0].entries.first { it.scryfallId == "island" }.forSale)
        // Again: nothing more to mark.
        assertEquals(0, markSparesToSell(after, listOf(deck)).second)
    }

    @Test
    fun notInAnyDeckOverFiveMarksEveryCopy() {
        val (after, added) = markUnusedToSell(cols, listOf(deck), 5.0, facts)
        assertEquals(2, added)
        assertEquals(2, after[0].entries.first { it.scryfallId == "sheoldred" }.forSale)
        // Sol Ring is in the deck; the One Ring is to sell already; Fact or Fiction is cheap.
        assertEquals(0, after[0].entries.first { it.scryfallId == "sol" }.forSale)
        assertEquals(3, after[0].entries.first { it.scryfallId == "fof" }.forSale)
    }

    @Test
    fun soldCopiesLeaveTheCollectionAndTheirPockets() {
        val result = markSold(cols, setOf("unsorted|ring", "unsorted|fof", "unsorted|opt"))
        assertEquals(7, result.copies)
        val pile = result.collections[0].entries.associateBy { it.scryfallId }
        assertNull(pile["ring"])
        assertNull(pile["opt"])
        val fof = pile.getValue("fof")
        assertEquals(1, fof.quantity)
        assertEquals(0, fof.forSale)
        assertEquals(1, fof.forTrade)
        assertEquals(listOf(at("trade", 1, page = 4, slot = 6)), fof.places)
        assertEquals(emptyList<PlacedCard>(), cardsIn(result.collections, "red"))
        assertEquals(listOf("rag"), cardsIn(result.collections, "rares").map { it.entry.scryfallId })
        assertSame(cols, markSold(cols, setOf("nothing")).collections)
    }

    @Test
    fun theExports() {
        val rows = sellRows(cols)
        assertEquals(
            "3 Fact or Fiction [TSR]\n3 Opt [XLN]\n1 Ragavan, Nimble Pilferer [MH2]\n1 The One Ring [LTR]",
            tcgplayerMassEntry(rows, facts)
        )
        assertEquals(
            listOf(
                "Count,Name,Expansion,Number,Condition,Language,Foil,Price (EUR)",
                "3,Fact or Fiction,TSR,64,,English,,",
                "2,Opt,XLN,65,,Japanese,,0.08",
                "1,Opt,XLN,65,,Japanese,Foil,",
                "1,\"Ragavan, Nimble Pilferer\",MH2,138,EX,English,,40.00",
                "1,The One Ring,LTR,246,NM,English,,55.00"
            ).joinToString("\n"),
            cardmarketCsv(rows, facts)
        )
        assertEquals("PO", cardmarketCondition("DMG"))
        assertEquals("", cardmarketCondition(null))
    }

    @Test
    fun thePullListWalksRoundThePlaces() {
        val list = sellPullList(cols)
        assertEquals(listOf("Rares binder", "Trade binder", "Red box › Blue", "No place yet"), list.groups.map { it.title })
        assertEquals(listOf("Opt", "The One Ring", "Ragavan, Nimble Pilferer"), list.groups[0].rows.map { it.name })
        assertEquals("Page 1, slot 1 · foil", list.groups[0].rows[0].hint)
        assertEquals(listOf(3), list.groups[1].rows.map { it.qty })
        assertEquals("In Unsorted", list.groups[3].rows[0].hint)
        assertEquals(8, list.total)
        assertEquals(3, list.places)
    }

    @Test
    fun anEntrySavedByAnOlderAppKeepsItsCopiesToSell() {
        val pile = cols[0]
        val older = pile.copy(entries = pile.entries.map { it.copy(forSale = null) })
        assertEquals(pile.entries.map { it.forSale }, keepForSaleFromOlderApp(pile, older).entries.map { it.forSale })
        // One no longer to sell (0) stays so.
        val cleared = pile.copy(entries = pile.entries.map { if (it.forSale != null) it.copy(forSale = 0) else it })
        assertSame(cleared, keepForSaleFromOlderApp(pile, cleared))
    }
}
