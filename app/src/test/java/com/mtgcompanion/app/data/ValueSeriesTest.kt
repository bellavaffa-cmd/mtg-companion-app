package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/** The collection's value over time from each card's own price history. The web app has the same checks — tests/collection/valueSeries.test.ts. */
class ValueSeriesTest {

    private fun track(lastDay: Long, vararg points: Pair<Long, Double?>) =
        PriceTrack(points.map { (day, usd) -> PricePoint(day, usd, null, null) }, lastDay)

    private fun hold(id: String, copies: Int, binderId: String = "b1", binderName: String = "Binder") =
        Holding(id, id.uppercase(), null, binderId, binderName, copies)

    // Day 20000 is 2024-10-04, a Friday.
    private val d = 20000L

    @Test
    fun aPriceHoldsUntilTheNextOneAndAfterTheLastDaySeen() {
        val t = track(d + 10, d to 1.0, d + 5 to 2.0)
        assertEquals(listOf(null, 1.0, 1.0, 2.0, 2.0), pricesOn(t, listOf(d - 1, d, d + 4, d + 5, d + 20)))
        assertEquals(listOf<Double?>(null), pricesOn(null, listOf(d)))
    }

    @Test
    fun weeksStartOnMondayAndMonthsOnThe1st() {
        // d+3 is Monday 7 Oct 2024.
        assertEquals(bucketKey(d - 4, Bucket.WEEK), bucketKey(d + 2, Bucket.WEEK))
        assertNotEquals(bucketKey(d + 2, Bucket.WEEK), bucketKey(d + 3, Bucket.WEEK))
        // 31 Oct (d+27) and 1 Nov (d+28).
        assertNotEquals(bucketKey(d + 27, Bucket.MONTH), bucketKey(d + 28, Bucket.MONTH))
        // The first day, then each week's last (Sunday), then the end.
        assertEquals(listOf(d, d + 2, d + 9, d + 12), bucketDays(d, d + 12, Bucket.WEEK))
        assertEquals(listOf(d, d + 1, d + 2), bucketDays(d, d + 2, Bucket.DAY))
    }

    @Test
    fun theLineStartsWhereTheHistoryStartsFromSparsePointsWithoutBackFilling() {
        val tracks = mapOf(
            "a" to track(d + 20, d to 1.0, d + 10 to 3.0),
            // Saved only from day 15: it doesn't count before that.
            "b" to track(d + 20, d + 15 to 5.0)
        )
        val s = valueSeries(tracks, listOf(hold("a", 2), hold("b", 1), hold("c", 4)), SeriesRange.MONTH, d + 20)!!
        assertEquals(d, s.historyStart)
        assertEquals(Bucket.DAY, s.bucket)
        assertEquals(d, s.points[0].day)
        assertEquals(21, s.points.size)
        assertEquals(2.0, s.points[0].usd, 0.001)
        assertEquals(2, s.points[0].priced)
        assertEquals(6.0, s.points[10].usd, 0.001)
        assertEquals(11.0, s.points[15].usd, 0.001)
        assertEquals(7, s.points[20].copies)
        // c has no saved price; b came in late.
        assertEquals(4, s.unpriced)
        assertEquals(1, s.lateCards)
    }

    @Test
    fun aRangeShorterThanTheHistoryStartsAtTheRangeWeeklyForSixMonths() {
        val tracks = mapOf("a" to track(d + 300, d to 1.0, d + 250 to 2.0))
        val month = valueSeries(tracks, listOf(hold("a", 1)), SeriesRange.MONTH, d + 300)!!
        assertEquals(d + 271, month.points[0].day)
        val half = valueSeries(tracks, listOf(hold("a", 1)), SeriesRange.HALF_YEAR, d + 300)!!
        assertEquals(Bucket.WEEK, half.bucket)
        assertEquals(d + 300 - 181, half.points[0].day)
        assertEquals(d + 300, half.points.last().day)
        val all = valueSeries(tracks, listOf(hold("a", 1)), SeriesRange.ALL, d + 300)!!
        assertEquals(d, all.points[0].day)
        assertEquals(Bucket.WEEK, all.bucket)
    }

    @Test
    fun theLineEndsOnTheLastDayPricesWereSavedAndNoHistoryIsNoLine() {
        val tracks = mapOf("a" to track(d + 5, d to 1.0))
        val s = valueSeries(tracks, listOf(hold("a", 1)), SeriesRange.MONTH, d + 40)!!
        assertEquals(d + 5, s.points.last().day)
        assertNull(valueSeries(emptyMap(), listOf(hold("a", 1)), SeriesRange.MONTH, d))
    }

    @Test
    fun risersAndFallersByWhatTheyDidToTheValue() {
        val tracks = mapOf(
            "up" to track(d + 30, d to 1.0, d + 20 to 4.0),
            "bigup" to track(d + 30, d to 10.0, d + 20 to 12.0),
            "down" to track(d + 30, d to 5.0, d + 20 to 2.0),
            "flat" to track(d + 30, d to 3.0),
            "late" to track(d + 30, d + 25 to 2.0, d + 28 to 3.0)
        )
        val holdings = listOf(hold("up", 1), hold("bigup", 1), hold("bigup", 2, "b2", "Other"), hold("down", 1), hold("flat", 1), hold("late", 1))
        val m = valueMovers(tracks, holdings, d, d + 30)
        // bigup: +2 × 3 copies = +6; up: +3; late: +1 since its first saved price.
        assertEquals(listOf("bigup" to 6.0, "up" to 3.0, "late" to 1.0), m.risers.map { it.id to it.change })
        assertEquals(3, m.risers[0].copies)
        assertEquals(d + 25, m.risers[2].sinceDay)
        assertEquals(300.0, m.risers[1].percent!!, 0.001)
        assertEquals(listOf("down" to -3.0), m.fallers.map { it.id to it.change })
    }

    @Test
    fun eachBinderAndTheChangeLikeForLike() {
        val tracks = mapOf(
            "a" to track(d + 30, d to 1.0, d + 20 to 2.0),
            "b" to track(d + 30, d + 25 to 10.0)
        )
        val holdings = listOf(hold("a", 2, "b1", "Red"), hold("b", 1, "b1", "Red"), hold("a", 1, "b2", "Blue"))
        assertEquals(
            listOf(BinderValue("b1", "Red", 14.0, 2.0), BinderValue("b2", "Blue", 2.0, 1.0)),
            binderValues(tracks, holdings, d, d + 30)
        )
        // b had no price on day d, so it isn't in the change.
        assertEquals(LikeForLike(3.0, 3.0, 100.0), likeForLike(tracks, holdings, d, d + 30))
    }

    @Test
    fun theTrendInWordsForScreenReaders() {
        val money = { v: Double -> "$" + String.format(Locale.US, "%.2f", v) }
        val day = { x: Long -> "day ${x - d}" }
        val points = listOf(SeriesPoint(d, 100.0, 1, 1), SeriesPoint(d + 1, 90.0, 1, 1), SeriesPoint(d + 2, 110.0, 1, 1))
        assertEquals(
            "Collection value from day 0 to day 2: up \$10.00 (10.0%), from \$100.00 to \$110.00. Lowest \$90.00 on day 1; highest \$110.00 on day 2.",
            trendSummary(points, money, day)
        )
        assertEquals("No collection value saved yet.", trendSummary(emptyList(), money, day))
    }
}
