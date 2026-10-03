package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** How much of each set the user has. The web app has the same checks — tests/collection/setCompletion.test.ts. */
class SetCompletionTest {

    private val sets = mapOf(
        "mh3" to SetInfo("mh3", "Modern Horizons 3", 4, "2024-06-14"),
        "lea" to SetInfo("lea", "Limited Edition Alpha", 2, "1993-08-05"),
        "cmr" to SetInfo("cmr", "Commander Legends", 10, "2020-11-20")
    )

    @Test
    fun printingsAreCountedOncePerSet() {
        val owned = mapOf("a" to "mh3", "b" to "MH3", "c" to "lea", "d" to "lea", "e" to "xyz")
        val progress = setProgress(owned, sets).associateBy { it.set.code }
        assertEquals(2, progress.getValue("mh3").owned)
        assertEquals(50, progress.getValue("mh3").percent)
        assertTrue(progress.getValue("lea").complete)
        // A set Scryfall's list lacks is still there, its size unknown.
        val unknown = progress.getValue("xyz")
        assertEquals("XYZ", unknown.set.name)
        assertEquals(0f, unknown.fraction)
        assertFalse(unknown.complete)
        assertFalse("sets with nothing owned aren't listed", "cmr" in progress)
    }

    @Test
    fun sortsByCompletionNameOrRelease() {
        val list = setProgress(mapOf("a" to "mh3", "c" to "lea", "d" to "lea", "f" to "cmr"), sets)
        assertEquals(listOf("lea", "mh3", "cmr"), sortedSets(list, SetSort.PERCENT).map { it.set.code })
        assertEquals(listOf("cmr", "lea", "mh3"), sortedSets(list, SetSort.NAME).map { it.set.code })
        assertEquals(listOf("mh3", "cmr", "lea"), sortedSets(list, SetSort.RELEASE).map { it.set.code })
    }

    @Test
    fun percentOnlyReadsHundredWhenComplete() {
        assertEquals(99, SetProgress(SetInfo("x", "X", 200), 199).percent)
        assertEquals(100, SetProgress(SetInfo("x", "X", 200), 200).percent)
        assertEquals(1f, SetProgress(SetInfo("x", "X", 2), 3).fraction)
    }

    @Test
    fun ownedIsWhatAllCardsCounts() {
        val binder = Collection("b", "Binder", listOf(CollectionEntry("a", "A", null, quantity = 1, foilQuantity = 1)))
        val wishlist = Collection(WISHLIST_ID, "Wishlist", listOf(CollectionEntry("w", "W", null, quantity = 1)), type = CollectionType.WISHLIST.name)
        val deck = Deck(id = "d", name = "Deck", cards = listOf(
            DeckCardEntry("a", "A", null, quantity = 1),
            DeckCardEntry("p", "P", null, quantity = 2, proxyQuantity = 2)
        ))
        assertEquals(mapOf("a" to 3), ownedPrintings(listOf(binder, wishlist), listOf(deck)))
        assertEquals(listOf("b"), missingFromSet(listOf("a", "b"), setOf("a")) { it })
    }
}
