package com.mtgcompanion.app.widget

import com.mtgcompanion.app.data.AlertDirection
import com.mtgcompanion.app.data.Money
import com.mtgcompanion.app.data.ValuePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the home-screen widget shows: the value's week, and the price alerts that went off lately. */
class WidgetDataTest {

    @Test
    fun theWeeksChangeRunsFromItsFirstPointToTheNewest() {
        val points = listOf(ValuePoint("2026-09-20", 900.0, 10), ValuePoint("2026-09-28", 1000.0, 10), ValuePoint("2026-10-03", 1050.0, 11))
        val change = weekChangeOf(points)!!
        assertEquals("2026-09-28", change.from.date)
        assertEquals(50.0, change.usd, 0.001)
        assertEquals("+$50.00 (+5.0%) this week", weekChangeText(change, Money.USD))
        assertNull(weekChangeOf(points.take(1)))
        assertNull(weekChangeOf(emptyList()))
        // Only one point in the week: nothing to compare it with.
        assertNull(weekChangeOf(listOf(ValuePoint("2026-09-01", 900.0, 10), ValuePoint("2026-10-03", 1050.0, 11))))
        assertEquals("Its change shows from tomorrow", weekChangeText(null, Money.USD))
        val down = weekChangeOf(listOf(ValuePoint("2026-10-01", 1200.0, 10), ValuePoint("2026-10-03", 1000.0, 10)))
        assertEquals("−$200 (−16.7%) this week", weekChangeText(down, Money.USD))
    }

    @Test
    fun theNewestAlertsAreKeptOnePerCardForAWeek() {
        val day = 24L * 60 * 60 * 1000
        val now = 30 * day
        val old = listOf(
            FiredAlert("Sol Ring", 1.5, AlertDirection.BELOW, now - 2 * day),
            FiredAlert("Rhystic Study", 40.0, AlertDirection.ABOVE, now - 8 * day)
        )
        val kept = withFiredAlerts(old, listOf(FiredAlert("sol ring", 1.2, AlertDirection.BELOW, now), FiredAlert("Cyclonic Rift", 30.0, AlertDirection.ABOVE, now)), now)
        assertEquals(listOf("sol ring", "Cyclonic Rift"), kept.map { it.name })
        val many = (1..5).map { FiredAlert("Card $it", 1.0, AlertDirection.BELOW, now - it) }
        assertEquals(listOf("Card 1", "Card 2", "Card 3"), withFiredAlerts(emptyList(), many, now).map { it.name })
        assertEquals("Cyclonic Rift  ↑ $30.00", firedAlertText(kept[1], Money.USD))
    }
}
