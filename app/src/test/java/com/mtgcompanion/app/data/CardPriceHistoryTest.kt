package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.scryfall.ScryfallPrices
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** A card's own price history on the device. The web app has the same checks — tests/collection/cardPriceHistory.test.ts. */
class CardPriceHistoryTest {

    private fun p(day: Long, usd: Double?, foil: Double? = null, eur: Double? = null) = PricePoint(day, usd, foil, eur)

    @Test
    fun onlyDaysThePriceChangedAreKept() {
        var t: PriceTrack? = null
        t = t.withDay(p(100, 1.0))
        t = t.withDay(p(101, 1.0))
        t = t.withDay(p(102, 1.5))
        t = t.withDay(p(103, 1.5))
        assertEquals(listOf(100L, 102L), t.points.map { it.day })
        assertEquals(103L, t.lastDay)
        // The line runs on to the last day seen.
        assertEquals(listOf(100L to 1.0, 102L to 1.5, 103L to 1.5), priceSeries(t, PriceKind.USD))
    }

    @Test
    fun aLaterNoteTheSameDayReplacesItsPrice() {
        var t = (null as PriceTrack?).withDay(p(100, 1.0)).withDay(p(101, 2.0))
        t = t.withDay(p(101, 2.5))
        assertEquals(listOf(1.0, 2.5), t.points.map { it.usd })
        // Back to yesterday's price: today's point goes again.
        t = t.withDay(p(101, 1.0))
        assertEquals(listOf(100L), t.points.map { it.day })
        assertEquals(101L, t.lastDay)
        // An older note is ignored.
        assertSame(t, t.withDay(p(99, 9.0)))
    }

    @Test
    fun aYearIsKeptWithThePriceThatHeldAtItsStart() {
        var t = (null as PriceTrack?).withDay(p(0, 1.0))
        t = t.withDay(p(10, 2.0))
        t = t.withDay(p(400, 3.0), keepDays = 365)
        // The window starts on day 36: the 2.0 noted on day 10 still held then.
        assertEquals(listOf(36L to 2.0, 400L to 3.0), t.points.map { it.day to it.usd })
        assertEquals(365L, t.days)
    }

    @Test
    fun theMoveIsFromTheFirstPriceToTheLast() {
        val t = (null as PriceTrack?).withDay(p(100, 2.0, eur = 1.8)).withDay(p(130, 3.0, eur = 1.8))
        val move = priceMove(priceSeries(t, PriceKind.USD))!!
        assertEquals(1.0, move.change, 1e-9)
        assertEquals(50.0, move.percent!!, 1e-9)
        assertEquals(listOf(PriceKind.USD, PriceKind.EUR), kindsIn(t))
        assertEquals(emptyList<Pair<Long, Double>>(), priceSeries(t, PriceKind.USD_FOIL))
        assertNull(priceMove(emptyList()))
    }

    @Test
    fun pricesAreKeptInWholeCents() {
        val t = (null as PriceTrack?).withDay(p(1, 1.234, 5.678))
        assertEquals(1.23, t.points.single().usd!!, 1e-9)
        assertEquals(5.68, t.points.single().usdFoil!!, 1e-9)
    }

    @Test
    fun notingScryfallsPricesLeavesTheMapAloneWhenNothingChanged() {
        val first = withPricesNoted(emptyMap(), 100, mapOf("a" to ScryfallPrices(usd = "1.00", usdFoil = "2.00"), "b" to ScryfallPrices()))
        assertEquals(setOf("a"), first.keys)
        assertSame(first, withPricesNoted(first, 100, mapOf("a" to ScryfallPrices(usd = "1.00", usdFoil = "2.00"))))
        val next = withPricesNoted(first, 101, mapOf("a" to ScryfallPrices(usd = "1.00", usdFoil = "2.00")))
        assertEquals(101L, next.getValue("a").lastDay)
    }

    @Test
    fun theStoredShapeReadsBack() {
        val tracks = mapOf("id-1" to PriceTrack(listOf(p(20000, 1.23, null, 0.99), p(20010, 1.5, 4.0, null)), 20012))
        val json = priceHistoryToJson(tracks)
        assertEquals(1, json.getInt("v"))
        val card = json.getJSONObject("cards").getJSONObject("id-1")
        assertEquals(20012L, card.getLong("l"))
        assertEquals("[20000,123,null,99]", card.getJSONArray("p").getJSONArray(0).toString())
        assertEquals(tracks, priceHistoryFromJson(JSONObject(json.toString())))
        assertTrue(priceHistoryFromJson(JSONObject("{}")).isEmpty())
    }
}
