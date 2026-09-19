package com.mtgcompanion.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

// Which of the user's cards moved in price: each owned card's price (US dollars) noted once a day,
// alongside the collection's value (see ValueHistory), for the last month. Kept on this device.
// Mirrors the web app's src/collection/priceMovers.ts.

/** A card the user owns (or did), by printing: [copies] as of the last note — 0 once it's gone. */
data class PricedCard(val id: String, val name: String, val imageUrl: String?, val copies: Int)

/** One day's prices, in the same order as [PriceStore.cards]; null where there was none. */
data class PriceDay(val date: String, val prices: List<Double?>)

data class PriceStore(val cards: List<PricedCard> = emptyList(), val days: List<PriceDay> = emptyList())

/** One card's move: [from] and [to] per copy; [change] is what it did to the collection's value. */
data class Mover(val card: PricedCard, val from: Double, val to: Double) {
    val change: Double get() = (to - from) * card.copies
    val percent: Double get() = if (from > 0) (to - from) / from * 100 else 0.0
}

/** The biggest risers and fallers since [since]. */
data class Movers(val since: String, val up: List<Mover>, val down: List<Mover>)

/** How far back the movers look. */
enum class MoverRange(val label: String, val days: Long) { DAY("1D", 1), WEEK("7D", 7), MONTH("30D", 30) }

/** How many days of prices are kept. */
const val PRICE_DAYS_KEPT = 31L

/**
 * [store] with [date]'s prices of [owned] noted (a later note that day replaces the earlier). Cards
 * no longer owned stay while they have prices in the days kept, at 0 copies; days older than
 * [PRICE_DAYS_KEPT] go.
 */
fun withPrices(store: PriceStore, date: String, owned: List<PricedCard>, prices: Map<String, Double>): PriceStore {
    val ownedById = owned.associateBy { it.id }
    val cards = store.cards.map { old -> ownedById[old.id] ?: old.copy(copies = 0) } +
        owned.filter { o -> store.cards.none { it.id == o.id } }
    val widened = store.days.filterNot { it.date == date }.map { d -> d.copy(prices = d.prices + List(cards.size - d.prices.size) { null }) }
    val since = LocalDate.parse(date).minusDays(PRICE_DAYS_KEPT).toString()
    val days = (widened + PriceDay(date, cards.map { prices[it.id] })).sortedBy { it.date }.filter { it.date >= since }
    // A card that's gone and has no price left in the days kept is dropped from every list.
    val keep = cards.indices.filter { i -> cards[i].copies > 0 || days.any { it.prices.getOrNull(i) != null } }
    return PriceStore(keep.map { cards[it] }, days.map { d -> d.copy(prices = keep.map { d.prices.getOrNull(it) }) })
}

/**
 * The owned cards whose price moved most since [range] ago — by what it did to the collection's
 * value. Measured from the oldest note within the range (a shorter history counts from its start);
 * null until there are two days to compare.
 */
fun moversOf(store: PriceStore, range: MoverRange, limit: Int = 10): Movers? {
    val latest = store.days.lastOrNull() ?: return null
    val target = LocalDate.parse(latest.date).minusDays(range.days).toString()
    val base = store.days.firstOrNull { it.date >= target && it.date < latest.date } ?: return null
    val moves = store.cards.indices.mapNotNull { i ->
        val card = store.cards[i]
        val from = base.prices.getOrNull(i)
        val to = latest.prices.getOrNull(i)
        if (card.copies <= 0 || from == null || to == null || kotlin.math.abs(to - from) < 0.01) null else Mover(card, from, to)
    }
    return Movers(
        since = base.date,
        up = moves.filter { it.change > 0 }.sortedByDescending { it.change }.take(limit),
        down = moves.filter { it.change < 0 }.sortedBy { it.change }.take(limit)
    )
}

object PriceMovers {
    private const val FILE = "card_prices.json"
    private var dir: File? = null
    private val _store = MutableStateFlow(PriceStore())
    val store: StateFlow<PriceStore> = _store.asStateFlow()

    fun init(context: Context) {
        dir = context.filesDir
        _store.value = runCatching { parse(JSONObject(File(context.filesDir, FILE).readText())) }.getOrDefault(PriceStore())
    }

    /** Notes today's prices of the owned cards. */
    suspend fun record(owned: List<PricedCard>, prices: Map<String, Double>) = withContext(Dispatchers.IO) {
        val next = withPrices(_store.value, LocalDate.now().toString(), owned, prices)
        _store.value = next
        runCatching { dir?.let { File(it, FILE).writeText(toJson(next).toString()) } }
    }

    private fun parse(o: JSONObject): PriceStore {
        val c = o.getJSONArray("cards")
        val cards = (0 until c.length()).map { i ->
            c.getJSONObject(i).let { PricedCard(it.getString("id"), it.getString("n"), if (it.isNull("i")) null else it.optString("i"), it.optInt("c")) }
        }
        val d = o.getJSONArray("days")
        val days = (0 until d.length()).map { i ->
            val day = d.getJSONObject(i)
            val p = day.getJSONArray("p")
            // Prices are kept in cents, to keep the file small.
            PriceDay(day.getString("d"), (0 until p.length()).map { j -> if (p.isNull(j)) null else p.getInt(j) / 100.0 })
        }
        return PriceStore(cards, days)
    }

    private fun toJson(s: PriceStore): JSONObject = JSONObject()
        .put("cards", JSONArray(s.cards.map { JSONObject().put("id", it.id).put("n", it.name).put("i", it.imageUrl ?: JSONObject.NULL).put("c", it.copies) }))
        .put("days", JSONArray(s.days.map { d ->
            JSONObject().put("d", d.date).put("p", JSONArray(d.prices.map { p -> p?.let { Math.round(it * 100) } ?: JSONObject.NULL }))
        }))
}
