package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Prices in the chosen currency, and the collection's value over time. The web app has the same
 * checks — see MtgCompanionWeb/tests/money/money.test.ts.
 */
class MoneyTest {

    @Test
    fun pricesReadInTheChosenCurrency() {
        assertEquals("$1,234.50", Money.USD.format(1234.5))
        assertEquals("$1,235", Money.USD.format(1234.5, whole = true))
        val peso = Money(Currencies.of("PHP"), 62.803)
        assertEquals("₱628.03", peso.format(10.0))
        assertEquals("₱628.03", peso.format("10.00"))
        assertNull(peso.format(null as String?))
        // No cents for yen; the symbol after the amount for kronor.
        assertEquals("¥1,579", Money(Currencies.of("JPY"), 157.89).format(10.0))
        assertEquals("98.53 kr", Money(Currencies.of("SEK"), 9.853).format(10.0))
        // A price typed in pesos goes back to dollars.
        assertEquals(10.0, peso.toUsd(628.03), 0.0001)
        // An unknown code falls back to dollars.
        assertEquals("USD", Currencies.of("XYZ").code)
    }

    @Test
    fun theValueOverTime() {
        var points = emptyList<ValuePoint>()
        points = withPoint(points, ValuePoint("2026-06-01", 100.0, 10))
        points = withPoint(points, ValuePoint("2026-09-19", 150.0, 12))
        points = withPoint(points, ValuePoint("2026-08-25", 120.0, 11))
        // A later value the same day takes that day's place.
        points = withPoint(points, ValuePoint("2026-09-19", 160.0, 12))
        assertEquals(listOf("2026-06-01", "2026-08-25", "2026-09-19"), points.map { it.date })
        assertEquals(160.0, points.last().usd, 0.0)

        assertEquals(listOf("2026-08-25", "2026-09-19"), pointsIn(points, ValueRange.MONTH).map { it.date })
        assertEquals(3, pointsIn(points, ValueRange.ALL).size)
        val month = changeOf(pointsIn(points, ValueRange.MONTH))!!
        assertEquals(40.0, month.usd, 0.0001)
        assertEquals(33.333, month.percent!!, 0.001)
        assertNull(changeOf(points.take(1)))

        // Only the newest are kept.
        assertEquals(listOf("2026-08-25", "2026-09-19"), withPoint(points.take(2), points.last(), keep = 2).map { it.date })
    }
}
