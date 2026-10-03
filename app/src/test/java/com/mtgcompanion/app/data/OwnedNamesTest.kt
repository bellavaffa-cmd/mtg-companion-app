package com.mtgcompanion.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Only cards I own" on a deck's suggestions: what counts as owned. */
class OwnedNamesTest {

    @Test
    fun `binder copies count, wishlists and empty rows don't, and a double-faced card matches by its front`() {
        val binders = listOf(
            Collection("b", "Main", entries = listOf(
                CollectionEntry("s", "Sol Ring", null, quantity = 1, foilQuantity = 0),
                CollectionEntry("d", "Delver of Secrets // Insectile Aberration", null, quantity = 0, foilQuantity = 1),
                CollectionEntry("e", "Empty", null, quantity = 0, foilQuantity = 0)
            )),
            Collection("w", "Wishlist", type = CollectionType.WISHLIST.name, entries = listOf(CollectionEntry("r", "Rhystic Study", null, quantity = 1, foilQuantity = 0)))
        )
        val keys = ownedNameKeys(binders)
        assertTrue(isOwnedName("sol ring", keys))
        assertTrue(isOwnedName("Delver of Secrets", keys))
        assertFalse(isOwnedName("Rhystic Study", keys))
        assertFalse(isOwnedName("Empty", keys))
    }
}
