package com.mtgcompanion.app.data

import android.content.Context
import com.mtgcompanion.app.network.scryfall.ScryfallPrices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

// Each card's own price over time, noted on this device whenever the app already fetches the prices
// of the user's cards (Home working out the collection's value, the price-alert check). Kept for the
// last year, per printing. Nothing goes to a server: a phone's history starts the day it first sees
// the card, and fills in day by day.
//
// Stored in filesDir/card_price_history.json, only the days a price changed:
//   {"v":1,"cards":{"<scryfallId>":{"l":20364,"p":[[20000,123,456,110],[20012,130,null,110]]}}}
// "l" is the last day the card was seen (an epoch day: days since 1970-01-01); "p" its points, oldest
// first, each [day, usd, usd_foil, eur] in cents (null where Scryfall had no price). A point's
// prices hold until the next point, or to "l". The web app keeps the same shape in localStorage.

/** One day's prices: US dollars (non-foil and foil) and euros; null where there was none. */
data class PricePoint(val day: Long, val usd: Double?, val usdFoil: Double?, val eur: Double?) {
    fun samePrices(other: PricePoint): Boolean = usd == other.usd && usdFoil == other.usdFoil && eur == other.eur

    fun value(kind: PriceKind): Double? = when (kind) {
        PriceKind.USD -> usd
        PriceKind.USD_FOIL -> usdFoil
        PriceKind.EUR -> eur
    }

    /** Whole cents, as kept. */
    fun rounded(): PricePoint = copy(usd = usd?.cents(), usdFoil = usdFoil?.cents(), eur = eur?.cents())
}

private fun Double.cents(): Double = Math.round(this * 100) / 100.0

/** One card's history: the points where its prices changed, oldest first, seen up to [lastDay]. */
data class PriceTrack(val points: List<PricePoint>, val lastDay: Long) {
    val firstDay: Long get() = points.firstOrNull()?.day ?: lastDay
    /** How many days it covers, counting the first and the last. */
    val days: Long get() = lastDay - firstDay + 1
}

/** Which of a card's prices a chart shows. */
enum class PriceKind(val label: String) { USD("Normal"), USD_FOIL("Foil"), EUR("Cardmarket") }

/** How many days of a card's prices are kept. */
const val PRICE_HISTORY_DAYS = 365

/**
 * This history with [seen] noted: a new point only when a price changed (a later note the same day
 * replaces that day's), and nothing older than [keepDays] before it — except the price that held
 * at the start of that window, moved up to its first day. A note older than the last one is ignored.
 */
fun PriceTrack?.withDay(seen: PricePoint, keepDays: Int = PRICE_HISTORY_DAYS): PriceTrack {
    val p = seen.rounded()
    if (this == null || points.isEmpty()) return PriceTrack(listOf(p), p.day)
    if (p.day < lastDay) return this
    val last = points.last()
    var next = when {
        // Noted again today: today's own point is replaced — or dropped, if it's back to yesterday's.
        last.day == p.day -> {
            val before = points.dropLast(1)
            if (before.isNotEmpty() && before.last().samePrices(p)) before else before + p
        }
        last.samePrices(p) -> points
        else -> points + p
    }
    val start = p.day - (keepDays - 1)
    val held = next.lastOrNull { it.day <= start }
    next = listOfNotNull(held?.copy(day = start)) + next.filter { it.day > start }
    return PriceTrack(next, p.day)
}

/** The [kind] prices to draw, (day, price), oldest first, running on to the last day the card was seen. */
fun priceSeries(track: PriceTrack, kind: PriceKind): List<Pair<Long, Double>> {
    val series = track.points.mapNotNull { p -> p.value(kind)?.let { p.day to it } }
    if (series.isEmpty()) return series
    val holdsToEnd = track.points.last().value(kind) != null
    return if (holdsToEnd && series.last().first < track.lastDay) series + (track.lastDay to series.last().second) else series
}

/** From the first price to the last over a stretch. */
data class PriceMove(val fromDay: Long, val from: Double, val toDay: Long, val to: Double) {
    val change: Double get() = to - from
    val percent: Double? get() = if (from > 0) (to - from) / from * 100 else null
}

/** How [series] moved from its first price to its last; null when empty. */
fun priceMove(series: List<Pair<Long, Double>>): PriceMove? {
    val first = series.firstOrNull() ?: return null
    val last = series.last()
    return PriceMove(first.first, first.second, last.first, last.second)
}

/** The prices this history has any of, in the order a chart offers them. */
fun kindsIn(track: PriceTrack): List<PriceKind> = PriceKind.entries.filter { k -> track.points.any { it.value(k) != null } }

/** [prices] as a point on [day]; null when Scryfall gave none of the three. */
fun pricePointOf(day: Long, prices: ScryfallPrices?): PricePoint? {
    val usd = prices?.usd?.toDoubleOrNull()
    val foil = prices?.usdFoil?.toDoubleOrNull()
    val eur = prices?.eur?.toDoubleOrNull()
    return if (usd == null && foil == null && eur == null) null else PricePoint(day, usd, foil, eur)
}

/** [tracks] with [today]'s [prices] (scryfallId -> Scryfall's prices) noted. The same map when nothing changed. */
fun withPricesNoted(tracks: Map<String, PriceTrack>, today: Long, prices: Map<String, ScryfallPrices?>): Map<String, PriceTrack> {
    var out: MutableMap<String, PriceTrack>? = null
    for ((id, p) in prices) {
        val point = pricePointOf(today, p) ?: continue
        val had = tracks[id]
        val next = had.withDay(point)
        if (next != had) (out ?: HashMap(tracks).also { out = it })[id] = next
    }
    return out ?: tracks
}

internal fun priceHistoryToJson(tracks: Map<String, PriceTrack>): JSONObject {
    fun cents(v: Double?): Any = v?.let { Math.round(it * 100) } ?: JSONObject.NULL
    val cards = JSONObject()
    for ((id, t) in tracks) {
        cards.put(id, JSONObject().put("l", t.lastDay).put("p", JSONArray(t.points.map { p ->
            JSONArray().put(p.day).put(cents(p.usd)).put(cents(p.usdFoil)).put(cents(p.eur))
        })))
    }
    return JSONObject().put("v", 1).put("cards", cards)
}

internal fun priceHistoryFromJson(o: JSONObject): Map<String, PriceTrack> {
    val cards = o.optJSONObject("cards") ?: return emptyMap()
    fun JSONArray.price(i: Int): Double? = if (isNull(i)) null else getLong(i) / 100.0
    val out = HashMap<String, PriceTrack>()
    for (id in cards.keys()) {
        val c = cards.optJSONObject(id) ?: continue
        val p = c.optJSONArray("p") ?: continue
        val points = (0 until p.length()).mapNotNull { i ->
            val a = p.optJSONArray(i) ?: return@mapNotNull null
            if (a.length() < 4) null else PricePoint(a.getLong(0), a.price(1), a.price(2), a.price(3))
        }
        if (points.isNotEmpty()) out[id] = PriceTrack(points, c.optLong("l", points.last().day))
    }
    return out
}

object CardPriceHistory {
    private const val FILE = "card_price_history.json"
    private var dir: File? = null
    private val lock = Mutex()
    private val _tracks = MutableStateFlow<Map<String, PriceTrack>?>(null)
    /** Every card's history by scryfallId; null until it's been read from the device ([load]). */
    val tracks: StateFlow<Map<String, PriceTrack>?> = _tracks.asStateFlow()

    fun init(context: Context) {
        dir = context.filesDir
    }

    /** Reads the history from the device the first time it's wanted; it's large, so not at start-up. */
    suspend fun load(): Map<String, PriceTrack> = lock.withLock { loaded() }

    private suspend fun loaded(): Map<String, PriceTrack> = _tracks.value ?: withContext(Dispatchers.IO) {
        runCatching { dir?.let { priceHistoryFromJson(JSONObject(File(it, FILE).readText())) } }.getOrNull() ?: emptyMap()
    }.also { _tracks.value = it }

    /** The last day anything was noted, as an epoch day; null before the first. */
    suspend fun lastDay(): Long? = load().values.maxOfOrNull { it.lastDay }

    /** Notes today's prices of these cards (scryfallId -> Scryfall's prices); written only when something changed. */
    suspend fun record(prices: Map<String, ScryfallPrices?>) {
        if (prices.isEmpty()) return
        lock.withLock {
            val had = loaded()
            val next = withPricesNoted(had, LocalDate.now().toEpochDay(), prices)
            if (next === had) return@withLock
            _tracks.value = next
            withContext(Dispatchers.IO) {
                runCatching { dir?.let { File(it, FILE).writeText(priceHistoryToJson(next).toString()) } }
            }
            Unit
        }
    }
}
