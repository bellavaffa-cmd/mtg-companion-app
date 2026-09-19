package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one Wishlist: always there, old wishlists folded in, and the cards decks are considering that
 * aren't owned. The web app has the same checks — see MtgCompanionWeb/tests/collection/wishlist.test.ts.
 */
class WishlistTest {

    private fun entry(id: String, name: String, q: Int = 1, alert: Double? = null, auto: Boolean = false) =
        CollectionEntry(id, name, null, quantity = q, priceAlert = alert, auto = auto)

    private fun deck(name: String, vararg considering: String) =
        Deck(name, name, considering = considering.map { DeckCardEntry("id-$it", it, null) })

    @Test
    fun theWishlistIsAlwaysThereAndOldWishlistsFoldIntoIt() {
        val binder = Collection("b", "Binder", listOf(entry("sol", "Sol Ring")))
        val made = withWishlist(listOf(binder), emptyList())
        assertEquals(listOf("b", WISHLIST_ID), made.map { it.id })
        assertEquals(CollectionType.WISHLIST, made.last().kind)

        val old1 = Collection("w1", "Upgrades", listOf(entry("opal", "Mox Opal", 1, alert = 50.0), entry("dt", "Demonic Tutor")), type = CollectionType.WISHLIST.name)
        val old2 = Collection("w2", "Cheap stuff", listOf(entry("opal", "Mox Opal", 2)), type = CollectionType.WISHLIST.name)
        val merged = withWishlist(made + old1 + old2, emptyList())
        assertEquals(listOf("b", WISHLIST_ID), merged.map { it.id })
        val wish = merged.last().entries.associateBy { it.name }
        assertEquals(3, wish.getValue("Mox Opal").quantity)
        assertEquals(50.0, wish.getValue("Mox Opal").priceAlert!!, 0.0)
        assertTrue("Demonic Tutor" in wish)

        // Nothing to change: the very same list, so nothing is written.
        assertSame(merged, withWishlist(merged, emptyList()))
    }

    @Test
    fun cardsDecksAreConsideringThatArentOwnedComeAndGoByThemselves() {
        val binder = Collection("b", "Binder", listOf(entry("sol", "Sol Ring")))
        val mine = entry("x", "Rhystic Study")
        val start = withWishlist(listOf(binder, Collection(WISHLIST_ID, WISHLIST_NAME, listOf(mine), createdAt = 0, type = "WISHLIST")), emptyList())
        val decks = listOf(deck("Omnath", "Sol Ring", "Cultivate", "Rhystic Study"), deck("Krenko", "cultivate", "Goblin Bombardment"))

        val withConsidering = withWishlist(start, decks)
        val wish = withConsidering.first { it.isWishlist }.entries
        // Sol Ring is owned; Rhystic Study was added by hand already; Cultivate once, however many decks.
        assertEquals(listOf("Rhystic Study", "Cultivate", "Goblin Bombardment"), wish.map { it.name })
        assertEquals(listOf(false, true, true), wish.map { it.auto })
        assertEquals(listOf("Omnath", "Krenko"), decksConsidering(decks, "Cultivate"))

        // Bought a Cultivate, and Krenko stopped considering the Bombardment: both go. Rhystic Study,
        // added by hand, stays though no deck considers it any more.
        val bought = withConsidering.map { if (it.id == "b") it.copy(entries = it.entries + entry("cult", "Cultivate")) else it }
        val after = withWishlist(bought, listOf(deck("Omnath", "Sol Ring", "Cultivate")))
        assertEquals(listOf("Rhystic Study"), after.first { it.isWishlist }.entries.map { it.name })
    }
}
