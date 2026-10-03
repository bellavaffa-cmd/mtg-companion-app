package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** When price alerts go off, both kinds. The web app has the same checks — tests/collection/priceAlerts.test.ts. */
class PriceAlertRulesTest {

    private fun entry(id: String, below: Double? = null, above: Double? = null, quantity: Int = 1, foil: Int = 0) =
        CollectionEntry(id, id, null, quantity = quantity, foilQuantity = foil, priceAlert = below, priceAlertAbove = above)

    private val wishlist = Collection(WISHLIST_ID, "Wishlist", listOf(entry("w", below = 5.0), entry("w0")), type = CollectionType.WISHLIST.name)
    private val binder = Collection("b1", "Binder", listOf(entry("o", above = 40.0), entry("o2", below = 1.0)))

    @Test
    fun wishlistsWatchForDropsAndBindersForRises() {
        val watches = alertWatches(listOf(wishlist, binder))
        assertEquals(listOf("w" to AlertDirection.BELOW, "o" to AlertDirection.ABOVE), watches.map { it.entry.scryfallId to it.direction })
        assertEquals(listOf("w", "above:o"), watches.map { it.memoryKey })
    }

    @Test
    fun aRiseAlertGoesOffAtOrAboveItsPrice() {
        val w = alertWatches(listOf(binder)).single()
        assertEquals(AlertStep.Forget, alertStep(w, 39.99, told = null))
        assertEquals(AlertStep.Tell(40.0), alertStep(w, 40.0, told = null))
        // Told at 41: quiet at 41 or less, told again when it climbs further.
        assertEquals(AlertStep.Quiet, alertStep(w, 41.0, told = 41.0))
        assertEquals(AlertStep.Quiet, alertStep(w, 40.5, told = 41.0))
        assertEquals(AlertStep.Tell(45.0), alertStep(w, 45.0, told = 41.0))
        // Back under: forgotten, so the next rise tells again.
        assertEquals(AlertStep.Forget, alertStep(w, 30.0, told = 45.0))
    }

    @Test
    fun aDropAlertWorksAsBefore() {
        val w = alertWatches(listOf(wishlist)).single()
        assertEquals(AlertStep.Forget, alertStep(w, 5.01, told = 4.0))
        assertEquals(AlertStep.Tell(5.0), alertStep(w, 5.0, told = null))
        assertEquals(AlertStep.Quiet, alertStep(w, 4.5, told = 4.0))
        assertEquals(AlertStep.Tell(3.0), alertStep(w, 3.0, told = 4.0))
    }

    @Test
    fun aRiseAlertOnFoilOnlyCopiesWatchesTheFoilPrice() {
        val foils = AlertWatch("b1", entry("f", above = 10.0, quantity = 0, foil = 2), AlertDirection.ABOVE, 10.0)
        assertEquals(12.0, alertPrice(foils, usd = 3.0, usdFoil = 12.0))
        assertEquals(3.0, alertPrice(foils, usd = 3.0, usdFoil = null))
        val mixed = foils.copy(entry = foils.entry.copy(quantity = 1))
        assertEquals(3.0, alertPrice(mixed, usd = 3.0, usdFoil = 12.0))
    }

    @Test
    fun homeListsEveryAlertPastItsLine() {
        val watches = alertWatches(listOf(wishlist, binder))
        val hits = alertHits(watches, mapOf("w" to (4.0 to null), "o" to (39.0 to 80.0)))
        assertEquals(listOf("w"), hits.map { it.watch.entry.scryfallId })
        val both = alertHits(watches, mapOf("w" to (4.0 to null), "o" to (42.0 to null)))
        assertEquals(listOf(4.0, 42.0), both.map { it.price })
    }
}
