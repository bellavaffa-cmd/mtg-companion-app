package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/**
 * What the collection is worth, place by place, and the spreadsheet of it — the same on both apps.
 * The web app has the same checks — see MtgCompanionWeb/tests/collection/valueByPlace.test.ts.
 */
class ValueByPlaceTest {

    private fun at(placeId: String, qty: Int, foil: Boolean = false, section: String? = null, page: Int? = null, slot: Int? = null) =
        CopyPlace(placeId, qty, if (foil) true else null, section, page, slot)

    private val shelf = StoragePlace("shelf", "Shelf", PlaceKind.SHELF.name, createdAt = 1)
    private val red = StoragePlace("red", "Red box", PlaceKind.BOX.name, parentId = "shelf", createdAt = 2)
    private val rares = StoragePlace("rares", "Rares binder", PlaceKind.BINDER.name, createdAt = 3)
    private val loan = Loan(
        "L1", "Sam", lentAt = 1,
        cards = listOf(LoanCard("Sol Ring", "sol", 1, deckId = "atraxa"), LoanCard("Opt", "opt", 1, collectionId = UNSORTED_COLLECTION_ID))
    )
    private val cols = listOf(
        Collection(
            UNSORTED_COLLECTION_ID, UNSORTED_COLLECTION_NAME,
            listOf(
                CollectionEntry("bolt", "Lightning Bolt", null, quantity = 2, foilQuantity = 1, places = listOf(at("red", 2, section = "Red"), at("rares", 1, foil = true, page = 1, slot = 2)), condition = "NM", language = "ja"),
                CollectionEntry("opt", "Opt", null, quantity = 3),
                CollectionEntry("mystery", "Mystery, the \"Card\"", null, quantity = 1, places = listOf(at("rares", 1, page = 1, slot = 1)))
            ),
            createdAt = 0, type = CollectionType.OWNED.name, storagePlaces = listOf(shelf, red, rares), loans = listOf(loan)
        ),
        Collection("wish", "Wishlist", listOf(CollectionEntry("ring", "The One Ring", null, quantity = 1)), createdAt = 0, type = CollectionType.WISHLIST.name)
    )
    private val atraxa = Deck("atraxa", "Atraxa", cards = listOf(DeckCardEntry("sol", "Sol Ring", null, quantity = 2)), ownership = DeckOwnership.PHYSICAL.name, createdAt = 1)
    private val prices = mapOf(
        "bolt" to PrintingFacts("m10", "146", 1.5, 12.25),
        "opt" to PrintingFacts("xln", "65", 0.1, null),
        "sol" to PrintingFacts("cmm", "410", 2.0, 5.0)
    )
    private val rows = valueRows(cols, listOf(atraxa)) { prices[it] }

    private fun num(d: Double?): String = when {
        d == null -> "-"
        d == Math.floor(d) -> d.toLong().toString()
        else -> d.toString()
    }

    @Test
    fun aFoilCopyIsPricedAsAFoilOrAsPlainWhenThereIsNoFoilPrice() {
        assertEquals(12.25, unitPrice(prices["bolt"], true)!!, 0.0)
        assertEquals(0.1, unitPrice(prices["opt"], true)!!, 0.0)
        assertEquals(3.0, unitPrice(PrintingFacts("x", "1", null, 3.0), false)!!, 0.0)
        assertNull(unitPrice(null, false))
    }

    @Test
    fun everyCopyOwnedByWhereItIs() {
        assertEquals(
            listOf(
                "Lightning Bolt ×2 · Shelf › Red box · Red · 1.5",
                "Lightning Bolt foil ×1 · Rares binder · Page 1, slot 2 · 12.25",
                "Opt ×2 · No place yet · 0.1",
                "Mystery, the \"Card\" ×1 · Rares binder · Page 1, slot 1 · -",
                "Sol Ring ×1 · Deck boxes › Atraxa · 2",
                "Sol Ring ×1 · Lent out › Sam · 2",
                "Opt ×1 · Lent out › Sam · 0.1"
            ),
            rows.map { r -> "${r.name}${if (r.foil) " foil" else ""} ×${r.qty} · ${r.where}${if (r.spot.isNotEmpty()) " · ${r.spot}" else ""} · ${num(r.unitUsd)}" }
        )
    }

    @Test
    fun theTotalAndEachPlaceTheMostValuableFirstAndNoPlaceLast() {
        val v = valueGroups(rows, cols)
        fun f(d: Double) = String.format(Locale.US, "%.2f", d)
        assertEquals(
            listOf(
                "Rares binder () 12.25 ×2",
                "Red box (Shelf) 3.00 ×2",
                "Lent out () 2.10 ×2",
                "Deck boxes () 2.00 ×1",
                "No place yet () 0.20 ×2"
            ),
            v.groups.map { "${it.label} (${it.detail}) ${f(it.usd)} ×${it.copies}" }
        )
        assertEquals("19.55", f(v.usd))
        assertEquals(9, v.copies)
    }

    @Test
    fun theSpreadsheetInTheCurrencyChosen() {
        assertEquals(
            listOf(
                "Name,Set,Number,Finish,Condition,Language,Quantity,Place,Spot,Unit price (GBP),Total (GBP)",
                "Sol Ring,CMM,410,Normal,,,1,Deck boxes › Atraxa,,1.60,1.60",
                "Opt,XLN,65,Normal,,,1,Lent out › Sam,,0.08,0.08",
                "Sol Ring,CMM,410,Normal,,,1,Lent out › Sam,,1.60,1.60",
                "Opt,XLN,65,Normal,,,2,No place yet,,0.08,0.16",
                "\"Mystery, the \"\"Card\"\"\",,,Normal,,,1,Rares binder,\"Page 1, slot 1\",,",
                "Lightning Bolt,M10,146,Foil,Near Mint,Japanese,1,Rares binder,\"Page 1, slot 2\",9.80,9.80",
                "Lightning Bolt,M10,146,Normal,Near Mint,Japanese,2,Shelf › Red box,Red,1.20,2.40"
            ).joinToString("\n"),
            valueCsv(rows, CsvMoney("GBP", 0.8, 2))
        )
        assertEquals(
            "Lightning Bolt,M10,146,Normal,Near Mint,Japanese,2,Shelf › Red box,Red,225,450",
            valueCsv(rows.take(1), CsvMoney("JPY", 150.0, 0)).split("\n")[1]
        )
    }
}
