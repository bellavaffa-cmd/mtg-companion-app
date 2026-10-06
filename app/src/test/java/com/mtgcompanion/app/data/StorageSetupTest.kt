package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Getting started with storage: the three steps' numbers become named places. The web app has the
 * same checks — see MtgCompanionWeb/tests/collection/storageSetup.test.ts.
 */
class StorageSetupTest {

    private fun ids(): () -> String { var n = 0; return { "p${++n}" } }

    @Test
    fun theNumbersBecomeNamedPlaces() {
        val drafts = setupDrafts(SetupCounts(binders = 2, boxes = 3, shelves = 1))
        assertEquals(listOf("Shelf", "Binder 1", "Binder 2", "Box 1", "Box 2", "Box 3"), drafts.map { it.name })
        assertEquals(listOf("shelf-1", "binder-1", "binder-2", "box-1", "box-2", "box-3"), drafts.map { it.key })
        // One of a kind has no number; a name already used gets one.
        val one = setupDrafts(SetupCounts(binders = 1, boxes = 1, shelves = 0), listOf(StoragePlace("x", "Box")))
        assertEquals(listOf("Binder", "Box 2"), one.map { it.name })
        assertEquals(0, setupDrafts(SetupCounts(0, 9, 0, null, 0)).size)
    }

    @Test
    fun boxesAreSortedAndEverythingSitsOnTheShelf() {
        val counts = SetupCounts(binders = 1, pockets = 12, boxes = 1, boxRule = SortRule.COLOUR, shelves = 1)
        val drafts = setupDrafts(counts)
        val made = setupPlaces(counts, drafts, mapOf("box-1" to "  Red box ", "binder-1" to ""), now = 1000, newId = ids())
        assertEquals(listOf("Shelf", "Binder", "Red box"), made.map { it.name })
        val (shelf, binder, box) = made
        assertNull(shelf.parentId)
        assertEquals(shelf.id, binder.parentId)
        assertEquals(shelf.id, box.parentId)
        assertEquals(12, binder.pocketsPerPage)
        assertEquals(SortRule.COLOUR.name, box.sortRule)
        assertEquals(COLOUR_SECTIONS, box.sections)
        assertEquals(listOf(1000L, 1001L, 1002L), made.map { it.createdAt })
        assertEquals("Bulk · by colour, then A–Z", placeSubtitle(box.copy(note = "Bulk")))
        // Nine pockets is the default, so it isn't written; nor sections for a box not sorted.
        val plain = SetupCounts(binders = 1, boxes = 1, boxRule = null, shelves = 0)
        val (b, x) = setupPlaces(plain, setupDrafts(plain), emptyMap(), 0, ids())
        assertNull(b.pocketsPerPage)
        assertNull(x.sortRule)
        assertNull(x.sections)
        assertNull(x.parentId)
        // Saved, they're the user's places, in that order.
        val cols = applySetup(emptyList(), made)
        assertEquals(listOf("Shelf", "Binder", "Red box"), placeTree(placesOf(cols)).map { it.place.name })
    }

    @Test
    fun stepOneCyclesItsChoices() {
        assertEquals(12, nextPockets(9))
        assertEquals(9, nextPockets(4))
        assertEquals(9, nextPockets(7))
        assertEquals(SortRule.SET, nextBoxRule(SortRule.COLOUR))
        assertNull(nextBoxRule(SortRule.NAME))
        assertEquals(SortRule.COLOUR, nextBoxRule(null))
    }

    @Test
    fun theShareWithAPlaceRoundsDown() {
        assertEquals(92, placedPercent(StorageSummary(1000, 925, 75, 0, 0, emptyMap())))
        assertEquals(99, placedPercent(StorageSummary(1000, 999, 1, 0, 0, emptyMap())))
        assertEquals(100, placedPercent(StorageSummary(4, 4, 0, 0, 0, emptyMap())))
        assertEquals(0, placedPercent(StorageSummary(0, 0, 0, 0, 0, emptyMap())))
        val decks = listOf(Deck("a", "A", ownership = DeckOwnership.PHYSICAL.name), Deck("b", "B", ownership = DeckOwnership.VIRTUAL.name))
        assertEquals(1, deckBoxCount(decks))
    }
}
