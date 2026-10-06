package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Import with locations: a CSV's binder, box or location column, matched to places. The web app has
 * the same checks — see MtgCompanionWeb/tests/collection/importPlaces.test.ts.
 */
class ImportPlacesTest {

    private val sol = "0afa0e33-4804-4b00-b625-c2d6b61090fc"
    private val bolt = "11111111-2222-3333-4444-555555555555"

    @Test
    fun theLocationColumnIsFoundInEachAppsExport() {
        assertEquals(8, locationColumnIn(CARD_LIST_CSV_HEADER.lowercase().split(",")))
        // ManaBox: "Binder Name" beside "Binder Type".
        assertEquals(1, locationColumnIn(listOf("binder type", "binder name", "name", "quantity")))
        assertEquals(2, locationColumnIn(listOf("count", "name", "location")))
        assertEquals(2, locationColumnIn(listOf("count", "name", "folder")))
        assertEquals(2, locationColumnIn(listOf("count", "name", "box")))
        assertEquals(2, locationColumnIn(listOf("count", "name", "my binder")))
        assertEquals(-1, locationColumnIn(listOf("count", "name", "binder id", "edition")))
        assertEquals(-1, locationColumnIn(listOf("count", "name", "edition")))
    }

    @Test
    fun eachValueIsCountedBlanksLast() {
        val csv = """
            Binder Name,Name,Quantity,Foil
            Binder 1,Sol Ring,2,
            ,Lightning Bolt,3,
            box r,Lightning Bolt,1,foil
            Box R,Sol Ring,4,
            binder 1,Lightning Bolt,1,
        """.trimIndent()
        val parsed = parseCardList(csv)
        assertEquals("Binder Name", parsed.locationColumn)
        assertEquals(listOf("Binder 1", null, "box r", "Box R", "binder 1"), parsed.lines.map { it.location })
        val counts = locationCounts(parsed.lines)
        assertEquals(listOf("Binder 1" to 3, "box r" to 5, "" to 3), counts.map { it.value to it.copies })
        // A list without such a column has none.
        assertNull(parseCardList("Count,Name\n1,Sol Ring").locationColumn)
        assertEquals(emptyList<LocationCount>(), locationCounts(parseCardList("4 Lightning Bolt").lines))
    }

    @Test
    fun valuesStartMatchedToThePlaceOfThatName() {
        val places = listOf(
            StoragePlace("shelf", "Shelf", PlaceKind.SHELF.name),
            StoragePlace("red", "Red box", PlaceKind.BOX.name, parentId = "shelf")
        )
        val counts = listOf(LocationCount("red box", 2), LocationCount("Shelf › Red box", 1), LocationCount("Box B", 4), LocationCount("", 3))
        val t = suggestTargets(counts, places)
        assertEquals(PlaceTarget.Existing("red"), t["red box"])
        assertEquals(PlaceTarget.Existing("red"), t["shelf › red box"])
        assertEquals(PlaceTarget.New, t["box b"])
        assertEquals(PlaceTarget.None, t[""])
        assertEquals(1, newPlaceCount(counts, t))
        assertEquals(PlaceKind.BINDER, importedPlaceKind("Trade binder", null))
        assertEquals(PlaceKind.BOX, importedPlaceKind("Box B", null))
        assertEquals(PlaceKind.SHELF, importedPlaceKind("Cupboard", null))
        assertEquals(PlaceKind.DECK_BOX, importedPlaceKind("Krenko deck box", null))
        assertEquals(PlaceKind.BINDER, importedPlaceKind("Rares", "Binder Name"))
        assertEquals(PlaceKind.BOX, importedPlaceKind("Rares", "Location"))
    }

    @Test
    fun importedCopiesGetTheirPlaces() {
        val pile = Collection(UNSORTED_COLLECTION_ID, UNSORTED_COLLECTION_NAME, listOf(
            CollectionEntry(sol, "Sol Ring", null, quantity = 6),
            CollectionEntry(bolt, "Lightning Bolt", null, quantity = 4, foilQuantity = 1)
        ), storagePlaces = listOf(StoragePlace("rares", "Rares binder", PlaceKind.BINDER.name)))
        val placements = listOf(
            ImportedPlacement(sol, listOf(ImportedLocation("Binder 1", 2, false), ImportedLocation("Box R", 4, false))),
            ImportedPlacement(bolt, listOf(ImportedLocation("box r", 1, true), ImportedLocation("Binder 1", 1, false), ImportedLocation("Trades", 9, false)))
        )
        val targets = mapOf("binder 1" to PlaceTarget.Existing("rares"), "box r" to PlaceTarget.New, "trades" to PlaceTarget.None)
        var n = 0
        val out = applyImportedPlaces(listOf(pile), UNSORTED_COLLECTION_ID, placements, targets, "Binder Name", 50, { "new${++n}" })
        val places = placesOf(out)
        assertEquals(listOf("Rares binder", "Box R"), places.map { it.name })
        assertEquals(PlaceKind.BOX, places[1].placeKind)
        assertEquals(50L, places[1].createdAt)
        val entries = out.single().entries
        assertEquals(listOf("rares" to 2, "new1" to 4), entries[0].places!!.map { it.placeId to it.qty })
        assertEquals(listOf(Triple("new1", 1, true), Triple("rares", 1, false)), entries[1].places!!.map { Triple(it.placeId, it.qty, it.isFoil) })
        // Nothing matched: nothing changes.
        assertSame(pile, applyImportedPlaces(listOf(pile), UNSORTED_COLLECTION_ID, placements, emptyMap(), null, 0, { "x" }).single())
    }

    @Test
    fun theCsvExportCarriesPlacesAndReadsBackWhereItWas() {
        val places = listOf(StoragePlace("red", "Red box", PlaceKind.BOX.name), StoragePlace("rares", "Rares binder", PlaceKind.BINDER.name))
        val entries = listOf(
            CollectionEntry(sol, "Sol Ring", null, quantity = 3, foilQuantity = 1, places = listOf(
                CopyPlace("red", 2), CopyPlace("rares", 1, foil = true)
            )),
            CollectionEntry(bolt, "Lightning Bolt", null, quantity = 2)
        )
        val csv = buildCardListCsv(entries, emptyMap(), places)
        assertTrue(csv.startsWith(CARD_LIST_CSV_HEADER))
        val parsed = parseCardList(csv)
        assertEquals("Place", parsed.locationColumn)
        assertEquals(
            listOf(Triple("Lightning Bolt", 2, null), Triple("Sol Ring", 2, "Red box"), Triple("Sol Ring", 1, null), Triple("Sol Ring", 1, "Rares binder")),
            parsed.lines.map { Triple(it.name, it.quantity, it.location) }
        )
        val counts = locationCounts(parsed.lines)
        val targets = suggestTargets(counts, places)
        assertEquals(0, newPlaceCount(counts, targets))
        // Imported into an empty pile on another device that has the same places: back where they were.
        val fresh = listOf(Collection(UNSORTED_COLLECTION_ID, UNSORTED_COLLECTION_NAME, listOf(
            CollectionEntry(sol, "Sol Ring", null, quantity = 3, foilQuantity = 1),
            CollectionEntry(bolt, "Lightning Bolt", null, quantity = 2)
        ), storagePlaces = places))
        val placements = listOf(sol, bolt).map { id ->
            ImportedPlacement(id, parsed.lines.filter { it.scryfallId == id && it.location != null }.map { ImportedLocation(it.location!!, it.quantity, it.foil) })
        }
        val back = applyImportedPlaces(fresh, UNSORTED_COLLECTION_ID, placements, targets, parsed.locationColumn, 0, { "x" })
        assertEquals(entries.map { placedCopies(it) }, back.single().entries.map { placedCopies(it) })
    }
}
