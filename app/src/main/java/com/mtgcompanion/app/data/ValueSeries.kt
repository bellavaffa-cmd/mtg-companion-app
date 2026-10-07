package com.mtgcompanion.app.data

import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

// The whole collection's value over time, worked out from each card's own price history
// (CardPriceHistory.kt) and the copies owned now: for each day on the chart, every owned card at the
// price it had that day. Nothing is made up — a card counts from the first day this phone saved its
// price, and the line starts on the first day any of them was saved. Shown by day, week or month,
// with the cards that rose and fell most and each binder's change. Pure, so it can be tested.
// Mirrors the web app's src/collection/valueSeries.ts, with the same tests
// (ValueSeriesTest.kt ↔ tests/collection/valueSeries.test.ts).

/** Copies of one printing in one binder. */
data class Holding(val id: String, val name: String, val imageUrl: String?, val binderId: String, val binderName: String, val copies: Int)

/** The stretches the chart offers: the last [days] (null: all of the history). */
enum class SeriesRange(val label: String, val days: Long?) {
    MONTH("1M", 30), HALF_YEAR("6M", 182), YEAR("1Y", 365), ALL("All", null)
}

/** How far apart the chart's points are. */
enum class Bucket(val label: String) { DAY("Daily"), WEEK("Weekly"), MONTH("Monthly") }

/** One point: the value on [day] (an epoch day), of the [priced] copies that had a price then, out of [copies]. */
data class SeriesPoint(val day: Long, val usd: Double, val priced: Int, val copies: Int)

data class ValueSeries(
    val points: List<SeriesPoint>,
    val bucket: Bucket,
    /** The first day any owned card's price was saved. */
    val historyStart: Long,
    /** Copies owned now that have no saved price at all. */
    val unpriced: Int,
    /** Owned cards (printings) whose first saved price came after the chart's first day. */
    val lateCards: Int
)

/** One card's move over the range: per-copy [from] and [to], and what it did to the value ([change]). */
data class ValueMover(
    val id: String, val name: String, val imageUrl: String?, val copies: Int,
    val from: Double, val to: Double, val change: Double, val percent: Double?, val sinceDay: Long
)

/** A binder's value on the last day, and how much it changed (cards with a price on both days). */
data class BinderValue(val id: String, val name: String, val usd: Double, val change: Double)

/** How the value moved, counting only cards with a price on both days. */
data class LikeForLike(val from: Double, val change: Double, val percent: Double?)

data class ValueMovers(val risers: List<ValueMover>, val fallers: List<ValueMover>)

private fun round2(v: Double): Double = (v * 100).roundToLong() / 100.0

/** The owned binders' copies (not wishlists, not samples): one holding per printing per binder. */
fun holdingsOf(collections: List<Collection>): List<Holding> =
    collections.filter { it.kind != CollectionType.WISHLIST && !isSample(it) }.flatMap { c ->
        c.entries.mapNotNull { e ->
            val copies = e.quantity + e.foilQuantity
            if (copies > 0) Holding(e.scryfallId, e.name, e.imageUrl, c.id, c.name, copies) else null
        }
    }

/** The first day [track] had a US dollar price; null if it never had one. */
fun firstPricedDay(track: PriceTrack?): Long? = track?.points?.firstOrNull { it.usd != null }?.day

/**
 * The US dollar price [track] had on each of [days] (sorted, oldest first): the price noted last on
 * or before that day — after the last day the card was seen, its last price. Null before its first.
 */
fun pricesOn(track: PriceTrack?, days: List<Long>): List<Double?> {
    val points = track?.points.orEmpty()
    var i = -1
    return days.map { d ->
        while (i + 1 < points.size && points[i + 1].day <= d) i++
        if (i >= 0) points[i].usd else null
    }
}

fun priceOn(track: PriceTrack?, day: Long): Double? = pricesOn(track, listOf(day)).first()

/** Which day, week (Monday first) or month [day] falls in. */
fun bucketKey(day: Long, bucket: Bucket): Long = when (bucket) {
    Bucket.DAY -> day
    // Epoch day 0 was a Thursday: +3 puts Mondays on a multiple of 7.
    Bucket.WEEK -> Math.floorDiv(day + 3, 7L)
    Bucket.MONTH -> LocalDate.ofEpochDay(day).let { it.year * 12L + (it.monthValue - 1) }
}

/** The days the chart's points sit on, between [start] and [end]: the first day, then each bucket's last. */
fun bucketDays(start: Long, end: Long, bucket: Bucket): List<Long> {
    if (end < start) return emptyList()
    val days = mutableListOf(start)
    for (d in start..end) {
        val last = d == end || bucketKey(d, bucket) != bucketKey(d + 1, bucket)
        if (last && d != start) days += d
    }
    return days
}

/** Daily for a month, weekly up to a year, monthly beyond. */
fun bucketFor(range: SeriesRange, spanDays: Long): Bucket = when (range) {
    SeriesRange.MONTH -> Bucket.DAY
    SeriesRange.HALF_YEAR, SeriesRange.YEAR -> Bucket.WEEK
    SeriesRange.ALL -> if (spanDays <= 62) Bucket.DAY else if (spanDays <= 400) Bucket.WEEK else Bucket.MONTH
}

/**
 * The collection's value over [range], ending on the last day prices were saved (never after
 * [today]); null when no owned card has a saved price.
 */
fun valueSeries(tracks: Map<String, PriceTrack>, holdings: List<Holding>, range: SeriesRange, today: Long): ValueSeries? {
    val copies = LinkedHashMap<String, Int>()
    holdings.forEach { copies.merge(it.id, it.copies) { a, b -> a + b } }
    var historyStart: Long? = null
    var end: Long? = null
    var unpriced = 0
    for ((id, n) in copies) {
        val t = tracks[id]
        val first = firstPricedDay(t)
        if (first == null || t == null) { unpriced += n; continue }
        if (historyStart == null || first < historyStart) historyStart = first
        if (end == null || t.lastDay > end) end = t.lastDay
    }
    val from = historyStart ?: return null
    val last = minOf(end ?: return null, today)
    if (last < from) return null
    val days = range.days
    val start = if (days == null) from else maxOf(from, last - (days - 1))
    val bucket = bucketFor(range, last - start)
    val at = bucketDays(start, last, bucket)
    val usd = DoubleArray(at.size)
    val priced = IntArray(at.size)
    var total = 0
    var lateCards = 0
    for ((id, n) in copies) {
        total += n
        val t = tracks[id] ?: continue
        val first = firstPricedDay(t)
        if (first != null && first > start) lateCards++
        pricesOn(t, at).forEachIndexed { i, p ->
            if (p != null) {
                usd[i] += p * n
                priced[i] += n
            }
        }
    }
    val points = at.mapIndexed { i, day -> SeriesPoint(day, round2(usd[i]), priced[i], total) }
    return ValueSeries(points, bucket, from, unpriced, lateCards)
}

/** How the value moved from [fromDay] to [toDay], counting only cards with a price on both days. */
fun likeForLike(tracks: Map<String, PriceTrack>, holdings: List<Holding>, fromDay: Long, toDay: Long): LikeForLike {
    var from = 0.0
    var change = 0.0
    for (h in holdings) {
        val (a, b) = pricesOn(tracks[h.id], listOf(fromDay, toDay))
        if (a == null || b == null) continue
        from += a * h.copies
        change += (b - a) * h.copies
    }
    return LikeForLike(from, change, if (from > 0) change / from * 100 else null)
}

/**
 * The cards that rose and fell most from [fromDay] to [toDay], by what they did to the value: each
 * from its price on [fromDay], or the first one saved after it. At most [limit] each way.
 */
fun valueMovers(tracks: Map<String, PriceTrack>, holdings: List<Holding>, fromDay: Long, toDay: Long, limit: Int = 5): ValueMovers {
    val byId = LinkedHashMap<String, Pair<Holding, Int>>()
    holdings.forEach { h -> byId[h.id] = byId[h.id]?.let { it.first to it.second + h.copies } ?: (h to h.copies) }
    val all = byId.mapNotNull { (id, pair) ->
        val (h, total) = pair
        val t = tracks[id]
        val first = firstPricedDay(t) ?: return@mapNotNull null
        if (first > toDay) return@mapNotNull null
        val since = maxOf(fromDay, first)
        val (from, to) = pricesOn(t, listOf(since, toDay))
        if (from == null || to == null || from == to) return@mapNotNull null
        val change = round2((to - from) * total)
        if (change == 0.0) return@mapNotNull null
        ValueMover(id, h.name, h.imageUrl, total, from, to, change, if (from > 0) (to - from) / from * 100 else null, since)
    }
    return ValueMovers(
        risers = all.filter { it.change > 0 }.sortedWith(compareByDescending<ValueMover> { it.change }.thenBy { it.name }).take(limit),
        fallers = all.filter { it.change < 0 }.sortedWith(compareBy<ValueMover> { it.change }.thenBy { it.name }).take(limit)
    )
}

/** Each binder's value on [toDay], and its change since [fromDay] (cards with a price on both days); dearest first. */
fun binderValues(tracks: Map<String, PriceTrack>, holdings: List<Holding>, fromDay: Long, toDay: Long): List<BinderValue> {
    val out = LinkedHashMap<String, BinderValue>()
    for (h in holdings) {
        val b = out[h.binderId] ?: BinderValue(h.binderId, h.binderName, 0.0, 0.0)
        val (from, to) = pricesOn(tracks[h.id], listOf(fromDay, toDay))
        out[h.binderId] = b.copy(
            usd = b.usd + (to?.let { it * h.copies } ?: 0.0),
            change = b.change + (if (from != null && to != null) (to - from) * h.copies else 0.0)
        )
    }
    return out.values.map { it.copy(usd = round2(it.usd), change = round2(it.change)) }
        .sortedWith(compareByDescending<BinderValue> { it.usd }.thenBy { it.name })
}

/**
 * The trend in words, for screen readers: "Collection value from 12 Sep to 7 Oct: up $76.00 (6.2%),
 * from $1,234.00 to $1,310.00. Lowest $1,200.00 on 20 Sep; highest $1,320.00 on 5 Oct."
 */
fun trendSummary(points: List<SeriesPoint>, money: (Double) -> String, day: (Long) -> String): String {
    if (points.isEmpty()) return "No collection value saved yet."
    val first = points.first()
    val last = points.last()
    if (points.size == 1) return "Collection value on ${day(last.day)}: ${money(last.usd)}."
    val change = last.usd - first.usd
    val percent = if (first.usd > 0) " (${String.format(Locale.US, "%.1f", abs(change / first.usd * 100))}%)" else ""
    val move = when {
        change > 0 -> "up ${money(change)}$percent"
        change < 0 -> "down ${money(-change)}$percent"
        else -> "no change"
    }
    val low = points.reduce { a, b -> if (b.usd < a.usd) b else a }
    val high = points.reduce { a, b -> if (b.usd > a.usd) b else a }
    return "Collection value from ${day(first.day)} to ${day(last.day)}: $move, from ${money(first.usd)} to ${money(last.usd)}. " +
        "Lowest ${money(low.usd)} on ${day(low.day)}; highest ${money(high.usd)} on ${day(high.day)}."
}
