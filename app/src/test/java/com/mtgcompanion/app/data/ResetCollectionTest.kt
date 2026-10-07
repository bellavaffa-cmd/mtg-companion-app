package com.mtgcompanion.app.data

import com.mtgcompanion.app.data.supabase.ItemMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Settings › Data and speed › Reset collection: what each choice removes and says, and Undo. The web
 * app's tests/settings/resetCollection.test.ts has the same cases; the sync side is ResetSyncTest.
 */
class ResetCollectionTest {

    private fun card(id: String, quantity: Int, foil: Int = 0) = CollectionEntry(scryfallId = id, name = id, imageUrl = null, quantity = quantity, foilQuantity = foil)
    private fun place(id: String) = StoragePlace(id = id, name = id, kind = "BOX", createdAt = 1)

    private val pile = Collection(
        id = UNSORTED_COLLECTION_ID, name = "Unsorted", createdAt = 1,
        entries = listOf(card("a", 2, 1).copy(places = listOf(CopyPlace(placeId = "p1", qty = 2)), forSale = 1)),
        storagePlaces = listOf(place("p1"), place("p2")),
        loans = listOf(Loan(id = "l1", to = "Sam", lentAt = 1)),
        sealed = listOf(SealedProduct(id = "s1", name = "Box", kind = "PLAY_BOX", count = 1, createdAt = 1)),
        graded = listOf(GradedCard(id = "g1", scryfallId = "z", name = "z", company = "PSA", grade = "10", createdAt = 1)),
        gear = listOf(GearItem(id = "k1", kind = "DICE", name = "Dice", count = 6, createdAt = 1))
    )
    private val collections = listOf(
        pile,
        Collection(id = WISHLIST_ID, name = "Wishlist", type = "WISHLIST", createdAt = 1, entries = listOf(card("w", 1))),
        Collection(id = "b1", name = "b1", createdAt = 1, entries = listOf(card("b", 1000), card("c", 399))),
        Collection(id = "b2", name = "b2", createdAt = 1),
        Collection(id = "w2", name = "w2", type = "WISHLIST", createdAt = 1, entries = listOf(card("x", 1)))
    )
    private val decks = listOf(
        Deck(id = "d1", name = "d1", createdAt = 1, gameResults = listOf(GameResult(id = "g1", result = "WIN", playedAt = 5))),
        Deck(id = "d2", name = "d2", createdAt = 1),
        Deck(id = "s1", name = "s1", createdAt = 1, sample = true)
    )

    @Test
    fun `Cards only empties the binders and the pile, and keeps everything else`() {
        val after = resetLibrary(decks, collections, ResetScope.CARDS)
        assertSame(decks, after.decks)
        assertEquals(listOf("unsorted" to 0, "wishlist" to 1, "b1" to 0, "b2" to 0, "w2" to 1), after.collections.map { it.id to it.entries.size })
        val p = after.collections[0]
        assertEquals(2, p.storagePlaces!!.size)
        assertEquals(1, p.loans!!.size)
        assertEquals(1, p.sealed!!.size)
        assertEquals(1, p.graded!!.size)
        assertEquals(1, p.gear!!.size)
        assertTrue(after.deletedCollections.isEmpty() && after.deletedDecks.isEmpty())
        // Unchanged binders stay the same objects, so the sync has nothing to send for them.
        assertSame(collections[3], after.collections[3])
    }

    @Test
    fun `Collection removes binders, places, sealed, graded, gear and loans, and the pile and Wishlist stay empty`() {
        val after = resetLibrary(decks, collections, ResetScope.COLLECTION)
        assertEquals(3, after.decks.size)
        assertEquals(listOf("unsorted", "wishlist"), after.collections.map { it.id })
        val (p, wishlist) = after.collections
        assertEquals(listOf(0, 0, 0, 0, 0, 0), listOf(p.entries.size, p.storagePlaces!!.size, p.loans!!.size, p.sealed!!.size, p.graded!!.size, p.gear!!.size))
        assertTrue(wishlist.entries.isEmpty())
        // Noted as deleted, as deleting them by hand does, so the sync sends their deletion.
        assertEquals(setOf("b1", "b2", "w2"), after.deletedCollections)
        assertTrue(after.deletedDecks.isEmpty())
    }

    @Test
    fun `Everything removes every deck too, samples going without being noted`() {
        val after = resetLibrary(decks, collections, ResetScope.EVERYTHING)
        assertTrue(after.decks.isEmpty())
        assertEquals(setOf("d1", "d2"), after.deletedDecks)
        assertEquals(setOf("b1", "b2", "w2"), after.deletedCollections)
    }

    @Test
    fun `a pile that never had places or loans gets none`() {
        val bare = Collection(id = UNSORTED_COLLECTION_ID, name = "Unsorted", createdAt = 1, entries = listOf(card("a", 1)))
        assertEquals(bare.copy(entries = emptyList()), resetLibrary(emptyList(), listOf(bare), ResetScope.COLLECTION).collections.single())
    }

    @Test
    fun `what will go, counted and said`() {
        assertEquals("1,402 copies in 1 binder", resetCountsText(resetCounts(decks, collections, ResetScope.CARDS)))
        assertEquals(
            "1,402 copies in 3 binders · 2 wishlist cards · 2 places · 1 sealed · 1 graded · 1 piece of gear · 1 loan",
            resetCountsText(resetCounts(decks, collections, ResetScope.COLLECTION))
        )
        assertEquals(
            "1,402 copies in 3 binders · 2 wishlist cards · 2 places · 1 sealed · 1 graded · 1 piece of gear · 1 loan · 3 decks",
            resetCountsText(resetCounts(decks, collections, ResetScope.EVERYTHING))
        )
        val empty = Collection(id = UNSORTED_COLLECTION_ID, name = "Unsorted", createdAt = 1)
        assertEquals(RESET_NOTHING, resetCountsText(resetCounts(emptyList(), listOf(empty), ResetScope.EVERYTHING)))
        assertEquals("1 copy", resetCountsText(resetCounts(emptyList(), listOf(empty.copy(entries = listOf(card("a", 1)))), ResetScope.CARDS)))
    }

    @Test
    fun `RESET in any case enables Reset`() {
        assertTrue(resetConfirmed("reset"))
        assertTrue(resetConfirmed(" ReSeT "))
        assertFalse(resetConfirmed("rese"))
        assertFalse(resetConfirmed(""))
    }

    @Test
    fun `Undo gives back exactly what was saved, and only until the reset is committed`() {
        val pending = PendingReset(ResetScope.EVERYTHING, "the stores as they were", 10_000L)
        assertEquals("the stores as they were", pending.undo())
        assertNull(pending.undo())
        assertFalse(pending.commit())

        val committed = PendingReset(ResetScope.CARDS, "x", 10_000L)
        assertTrue(committed.commit())
        assertFalse(committed.commit())
        assertNull(committed.undo())
        assertFalse(committed.open)
    }

    @Test
    fun `an emptied pile merged with an older copy from another device stays empty`() {
        val base = pile
        val mine = resetLibrary(decks, collections, ResetScope.COLLECTION).collections[0]
        // The other device changed a count and a place since: the removal still wins.
        val theirs = base.copy(entries = listOf(card("a", 5, 1)), storagePlaces = listOf(place("p1").copy(name = "Red box"), place("p2")))
        for (minePreferred in listOf(true, false)) {
            val merged = ItemMerge.mergeCollections(base, mine, theirs, minePreferred)
            assertTrue(merged.entries.isEmpty())
            assertEquals(emptyList<StoragePlace>(), merged.storagePlaces)
            assertEquals(listOf(0, 0, 0, 0), listOf(merged.loans!!.size, merged.sealed!!.size, merged.graded!!.size, merged.gear!!.size))
            // And the same seen from the other device.
            assertTrue(ItemMerge.mergeCollections(base, theirs, mine, !minePreferred).entries.isEmpty())
        }
    }
}
