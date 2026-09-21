package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The Unsorted pile is always there, like the Wishlist. The web app has the same checks — see
 * MtgCompanionWeb/tests/collection/unsorted.test.ts.
 */
class UnsortedPileTest {

    private fun binder(id: String, type: CollectionType = CollectionType.OWNED, entries: List<CollectionEntry> = emptyList()) =
        Collection(id, id, entries, createdAt = 5, type = type.name)

    @Test
    fun aLibraryWithoutThePileGetsOneEmptyAndOwned() {
        val out = withUnsortedPile(listOf(binder("wishlist", CollectionType.WISHLIST), binder("Blue")))
        val pile = out.firstOrNull { it.isUnsorted }
        assertNotNull(pile)
        assertEquals(UNSORTED_COLLECTION_ID, pile!!.id)
        assertEquals("Unsorted", pile.name)
        assertEquals(CollectionType.OWNED.name, pile.type)
        assertEquals(emptyList<CollectionEntry>(), pile.entries)
        // The same on every device, so two made independently don't disagree about when.
        assertEquals(0L, pile.createdAt)
        assertEquals(3, out.size)
    }

    @Test
    fun aPileThatIsAlreadyThereIsLeftExactlyAsItIs() {
        val cards = listOf(CollectionEntry("id-Sol Ring", "Sol Ring", null, quantity = 2))
        val library = listOf(binder("Blue"), binder(UNSORTED_COLLECTION_ID, entries = cards))
        // The same list back, so nothing is written and nothing syncs.
        assertSame(library, withUnsortedPile(library))
    }

    @Test
    fun anEmptyLibraryStillGetsItsPile() {
        assertEquals(1, withUnsortedPile(emptyList()).count { it.isUnsorted })
    }
}
