package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * How full a place is, the overflow warnings and splitting a box on whole sections — the same on both
 * apps. The web app has the same checks — see MtgCompanionWeb/tests/collection/boxSpace.test.ts.
 */
class BoxSpaceTest {

    private fun at(placeId: String, qty: Int, section: String? = null, page: Int? = null, slot: Int? = null) =
        CopyPlace(placeId, qty, null, section, page, slot)

    private val red = StoragePlace(
        "red", "Red box", PlaceKind.BOX.name, sections = listOf("White", "Blue", "Black", "Red", "Green"),
        sortRule = SortRule.COLOUR.name, createdAt = 1, capacity = 10
    )
    private val rares = StoragePlace("rares", "Rares binder", PlaceKind.BINDER.name, pocketsPerPage = 4, createdAt = 2, pages = 2)
    private fun entry(id: String, name: String, qty: Int, vararg places: CopyPlace) =
        CollectionEntry(id, name, null, quantity = qty, places = places.toList())
    private val cols = listOf(
        Collection(
            UNSORTED_COLLECTION_ID, UNSORTED_COLLECTION_NAME,
            listOf(
                entry("w", "Swords to Plowshares", 2, at("red", 2, "White")),
                entry("u", "Counterspell", 1, at("red", 1, "Blue")),
                entry("b", "Dark Ritual", 2, at("red", 2, "Black")),
                entry("r", "Shock", 3, at("red", 3, "Red")),
                entry("g", "Giant Growth", 1, at("red", 1, "Green")),
                entry("x", "Mystery", 1, at("red", 1)),
                entry("ring", "The One Ring", 1, at("rares", 1, page = 1, slot = 1)),
                entry("bolt", "Lightning Bolt", 2, at("rares", 2, page = 1, slot = 2)),
                entry("opt", "Opt", 1, at("rares", 1))
            ),
            createdAt = 0, type = CollectionType.OWNED.name, storagePlaces = listOf(red, rares)
        )
    )

    @Test
    fun aBoxHoldsItsCapacityAndABinderItsPagesOfPockets() {
        assertEquals(10, placeSize(red))
        assertEquals(8, placeSize(rares))
        assertNull(placeSize(rares.copy(pages = null)))
        assertNull(placeSize(red.copy(capacity = 0)))
        assertEquals("pockets", sizeUnit(rares))
        assertEquals("cards", sizeUnit(red))
        assertEquals(40, withSize(rares, 40).pages)
        assertEquals(640, withSize(red, 640).capacity)
        assertEquals(0, withSize(red, -3).capacity)
        assertEquals(2, sizeSetting(rares))
        assertNull(sizeSetting(red.copy(capacity = 0)))
    }

    @Test
    fun howFullAPlaceIs() {
        val box = spaceOf(red, cols)!!
        assertEquals(Space("red", 10, 10), box)
        assertEquals("100% full · 10 of 10", spaceLabel(red, box))
        assertEquals("Full.", roomLine(box))
        // A binder: two pockets in use and one copy waiting for a pocket.
        val binder = spaceOf(rares, cols)!!
        assertEquals(Space("rares", 3, 8), binder)
        assertEquals("3 of 8 pockets", spaceLabel(rares, binder))
        assertEquals("Room for about 5 more. Your last pile added 4.", roomLine(binder, 4))
        val big = Space("red", 612, 640)
        assertEquals(96, big.percent)
        assertEquals("96% full · 612 of 640", spaceLabel(red, big))
        assertEquals("Room for about 28 more. Your last pile added 38.", roomLine(big, 38))
        assertEquals("Over by 2.", roomLine(Space("red", 12, 10)))
        assertEquals("575 of 640", spaceLabel(red, Space("red", 575, 640)))
        assertNull(spaceOf(red.copy(capacity = null), cols))
    }

    @Test
    fun theLastPileIsWhatWentInWithinHalfAnHourOfEachOther() {
        val hour = 60L * 60 * 1000
        val log = listOf(
            CopyMove(0, MoveKind.ADDED.name, "A", qty = 1, title = "", places = listOf("red")),
            CopyMove(hour, MoveKind.PUT_AWAY.name, "B", qty = 1, title = "", places = listOf("red", "rares")),
            CopyMove(hour + 10 * 60_000, MoveKind.PUT_AWAY.name, "C", qty = 2, title = "", places = listOf("red")),
            CopyMove(hour + 15 * 60_000, MoveKind.PUT_AWAY.name, "D", qty = 5, title = "", places = listOf("rares", "red")),
            CopyMove(hour + 20 * 60_000, MoveKind.MOVED.name, "E", qty = 1, title = "", places = listOf("rares", "red"))
        )
        assertEquals(3, lastPileAdded(log, "red"))
        assertEquals(5, lastPileAdded(log, "rares"))
        assertNull(lastPileAdded(log, "blue"))
    }

    @Test
    fun overflowingAPlace() {
        assertEquals(listOf(Overflow("red", "Red box", 2, 0)), overflows(cols, mapOf("red" to 2, "rares" to 5)))
        assertEquals(listOf(Overflow("rares", "Rares binder", 6, 5)), overflows(cols, mapOf("rares" to 6, "nowhere" to 3)))
        assertEquals("Red box is full already.", overflowLine(Overflow("red", "Red box", 2, 0)))
        assertEquals("Red box is full already (over by 3).", overflowLine(Overflow("red", "Red box", 2, -3)))
        assertEquals("Red box will overflow: room for about 28, this adds 38.", overflowLine(Overflow("red", "Red box", 38, 28)))
    }

    @Test
    fun aSortSessionAddsItsCardsToThePlacesTheirPilesGoTo() {
        val session = SortSession(
            rules = listOf(PileRule(PileKind.PRICE.name, over = 2.0, to = "rares"), PileRule(PileKind.BULK.name, to = BY_RULE)),
            scans = listOf(
                SortScan(1, "s1", "Shock", facts = CardFacts("Shock", listOf("R"), "Instant"), entry = CollectionEntry("s1", "Shock", null), pile = 1),
                SortScan(2, "s2", "Opt", facts = CardFacts("Opt", listOf("U"), "Instant"), entry = CollectionEntry("s2", "Opt", null), pile = 1),
                SortScan(3, "s3", "Ring", facts = CardFacts("Ring"), entry = CollectionEntry("s3", "Ring", null), pile = 0)
            )
        )
        assertEquals(mapOf("red" to 2, "rares" to 1), pileAdds(cols, session))
    }

    @Test
    fun aBoxSplitsOnWholeSectionsAsNearHalfAndHalfAsTheyAllow() {
        val plan = planSplit(red, cardsIn(cols, "red"))!!
        assertEquals(listOf("White", "Blue", "Black"), plan.stay.map { it.name })
        assertEquals(listOf("Red", "Green"), plan.go.map { it.name })
        // The copy in no section stays.
        assertEquals(6, plan.stayCopies)
        assertEquals(4, plan.goCopies)
        assertEquals("White, Blue, Black · 6", splitSideLabel(plan.stay, plan.stayCopies))
        assertEquals("Red, Green · 4", splitSideLabel(plan.go, plan.goCopies))
        assertNull(planSplit(rares, cardsIn(cols, "rares")))
    }

    @Test
    fun theNewBoxIsNumbered() {
        assertEquals("Red box 2", nextBoxName(listOf(red), "Red box"))
        assertEquals("Red box 3", nextBoxName(listOf(red, red.copy(id = "r2", name = "red box 2")), "Red box"))
        assertEquals("Red box 3", nextBoxName(listOf(red, red.copy(id = "r2", name = "Red box 2")), "Red box 2"))
    }

    @Test
    fun splittingMakesTheNewBoxAndMovesTheCopiesInTheSectionsThatGo() {
        val plan = planSplit(red, cardsIn(cols, "red"))!!
        val after = splitBox(cols, "red", plan, "red2", 99)
        val places = placesOf(after)
        assertEquals(listOf("red", "red2", "rares"), places.map { it.id })
        val made = places[1]
        assertEquals(StoragePlace("red2", "Red box 2", PlaceKind.BOX.name, sections = listOf("Red", "Green"), sortRule = SortRule.COLOUR.name, createdAt = 99, capacity = 10), made)
        assertEquals(listOf("White", "Blue", "Black"), places[0].sections)
        val pile = after.first { it.isUnsorted }.entries.associateBy { it.scryfallId }
        assertEquals(listOf(at("red2", 3, "Red")), pile.getValue("r").places)
        assertEquals(listOf(at("red2", 1, "Green")), pile.getValue("g").places)
        assertEquals(listOf(at("red", 1)), pile.getValue("x").places)
        assertEquals(Space("red", 6, 10), spaceOf(places[0], after))
        assertSame(cols, splitBox(cols, "gone", plan, "x", 1))
    }

    @Test
    fun aPlaceSavedByAnOlderAppKeepsItsSize() {
        val pile = cols[0]
        val older = pile.copy(storagePlaces = listOf(red.copy(capacity = null), rares.copy(pages = null)))
        assertEquals(listOf(red, rares), keepPlaceSizes(pile, older).storagePlaces)
        // A size taken off (0) stays off.
        val cleared = pile.copy(storagePlaces = listOf(red.copy(capacity = 0), rares))
        assertSame(cleared, keepPlaceSizes(pile, cleared))
        // Both devices: whoever changed it.
        val merged = mergePlaceLists(listOf(red), listOf(red.copy(capacity = 20)), listOf(red.copy(name = "Red")), minePreferred = false)!!
        assertEquals(20, merged[0].capacity)
        assertEquals("Red", merged[0].name)
        assertEquals(0, storagePlace(red.copy(capacity = -1)).capacity)
    }
}
