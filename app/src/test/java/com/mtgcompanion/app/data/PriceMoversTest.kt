package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which of the user's cards moved in price. The web app has the same checks — see
 * MtgCompanionWeb/tests/collection/priceMovers.test.ts.
 */
class PriceMoversTest {

    private val sol = PricedCard("sol", "Sol Ring", null, 2)
    private val rhystic = PricedCard("rhy", "Rhystic Study", null, 1)
    private val bolt = PricedCard("bolt", "Lightning Bolt", null, 4)

    @Test
    fun theCardsThatMovedTheValueMost() {
        var store = PriceStore()
        store = withPrices(store, "2026-09-01", listOf(sol, rhystic), mapOf("sol" to 1.00, "rhy" to 40.00))
        store = withPrices(store, "2026-09-13", listOf(sol, rhystic, bolt), mapOf("sol" to 1.50, "rhy" to 38.00, "bolt" to 0.50))
        store = withPrices(store, "2026-09-19", listOf(sol, rhystic, bolt), mapOf("sol" to 2.00, "rhy" to 35.00, "bolt" to 0.50))
        // A later note the same day replaces the earlier.
        store = withPrices(store, "2026-09-20", listOf(sol, rhystic, bolt), mapOf("sol" to 2.50, "rhy" to 30.00, "bolt" to 0.60))
        store = withPrices(store, "2026-09-20", listOf(sol, rhystic, bolt), mapOf("sol" to 3.00, "rhy" to 30.00, "bolt" to 0.60))
        assertEquals(listOf("2026-09-01", "2026-09-13", "2026-09-19", "2026-09-20"), store.days.map { it.date })

        val day = moversOf(store, MoverRange.DAY)!!
        assertEquals("2026-09-19", day.since)
        // Sol Ring: +$1 a copy, two copies. Bolt: +$0.10 × 4.
        assertEquals(listOf("Sol Ring", "Lightning Bolt"), day.up.map { it.card.name })
        assertEquals(2.0, day.up.first().change, 0.001)
        assertEquals(50.0, day.up.first().percent, 0.001)
        assertEquals(listOf("Rhystic Study"), day.down.map { it.card.name })
        assertEquals(-5.0, day.down.first().change, 0.001)

        // A week: from the oldest note within it (the 13th); Bolt had a price then too.
        val week = moversOf(store, MoverRange.WEEK)!!
        assertEquals("2026-09-13", week.since)
        assertEquals(-8.0, week.down.first().change, 0.001)
        // A month: from the 1st, before Bolt was owned — so it isn't among them.
        assertEquals(listOf("Sol Ring"), moversOf(store, MoverRange.MONTH)!!.up.map { it.card.name })
    }

    @Test
    fun oldDaysAndGoneCardsAreDropped() {
        var store = withPrices(PriceStore(), "2026-08-01", listOf(sol, bolt), mapOf("sol" to 1.0, "bolt" to 0.5))
        store = withPrices(store, "2026-09-20", listOf(sol), mapOf("sol" to 2.0))
        // The 1st of August is over a month before; Bolt, sold since, has no price left.
        assertEquals(listOf("2026-09-20"), store.days.map { it.date })
        assertEquals(listOf("sol"), store.cards.map { it.id })
        assertNull(moversOf(store, MoverRange.WEEK))
        assertNull(moversOf(PriceStore(), MoverRange.DAY))
    }
}
