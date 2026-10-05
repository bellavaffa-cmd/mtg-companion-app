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

// Each deck's value over time: one point a day, the deck's total (in US dollars, worked out the way
// its Stats do — every main-deck copy at its printing's price), noted on this device whenever the
// deck's prices are looked up — opening the deck, or once a day for every deck when the decks list
// opens. Kept on this device only, like the collection's value history (ValueHistory.kt), whose
// points and ranges it shares. Mirrors the web app's src/decks/deckValueHistory.ts.

/** About thirteen months of days, per deck. */
const val DECK_KEEP = 400

/**
 * The deck's value from [prices] (scryfallId → US dollars; null: looked up, but no price), and how
 * many cards it's of. Null when under 98% of the deck's copies were looked up — a dropped request
 * mustn't read as a crash in value.
 */
fun deckValueOf(deck: Deck, prices: Map<String, Double?>): Pair<Double, Int>? {
    var usd = 0.0
    var cards = 0
    var known = 0
    deck.cards.forEach { e ->
        cards += e.quantity
        if (!prices.containsKey(e.scryfallId)) return@forEach
        known += e.quantity
        usd += (prices[e.scryfallId] ?: 0.0) * e.quantity
    }
    if (cards == 0 || known < cards * 0.98) return null
    return Math.round(usd * 100) / 100.0 to cards
}

/** [history] with [point] as [deckId]'s for its day. */
fun withDeckPoint(history: Map<String, List<ValuePoint>>, deckId: String, point: ValuePoint, keep: Int = DECK_KEEP): Map<String, List<ValuePoint>> =
    history + (deckId to withPoint(history[deckId].orEmpty(), point, keep))

/** The decks that have no point for [day] yet — not archived, and with cards. */
fun decksDue(history: Map<String, List<ValuePoint>>, decks: List<Deck>, day: String): List<Deck> =
    decks.filter { d -> !d.isArchived && d.cards.isNotEmpty() && history[d.id]?.lastOrNull()?.date != day }

/** [history] with only the decks still there. */
fun prunedDeckHistory(history: Map<String, List<ValuePoint>>, deckIds: Iterable<String>): Map<String, List<ValuePoint>> =
    history.filterKeys { it in deckIds }

/** How the value moved over the last month of [points]; null under two points. */
fun monthChange(points: List<ValuePoint>): ValueChange? = changeOf(pointsIn(points, ValueRange.MONTH))

object DeckValueHistory {
    private const val FILE = "deck_value_history.json"

    private var dir: File? = null
    private val _points = MutableStateFlow<Map<String, List<ValuePoint>>>(emptyMap())
    /** By deck id, oldest first. */
    val points: StateFlow<Map<String, List<ValuePoint>>> = _points.asStateFlow()
    /** The day every due deck was last sampled, so the decks list asks only once a day. */
    @Volatile var sampledOn: String = ""

    fun init(context: Context) {
        dir = context.filesDir
        _points.value = runCatching {
            val o = JSONObject(File(context.filesDir, FILE).readText())
            o.keys().asSequence().associateWith { id ->
                val a = o.getJSONArray(id)
                (0 until a.length()).map { i -> a.getJSONObject(i).let { ValuePoint(it.getString("d"), it.getDouble("v"), it.optInt("n")) } }
            }
        }.getOrDefault(emptyMap())
    }

    private suspend fun save(next: Map<String, List<ValuePoint>>) = withContext(Dispatchers.IO) {
        _points.value = next
        runCatching {
            val o = JSONObject()
            next.forEach { (id, list) ->
                val a = JSONArray()
                list.forEach { a.put(JSONObject().put("d", it.date).put("v", it.usd).put("n", it.cards)) }
                o.put(id, a)
            }
            dir?.let { File(it, FILE).writeText(o.toString()) }
        }
    }

    /** Notes today's value of a deck. */
    suspend fun record(deckId: String, usd: Double, cards: Int) =
        save(withDeckPoint(_points.value, deckId, ValuePoint(LocalDate.now().toString(), Math.round(usd * 100) / 100.0, cards)))

    /** Forgets the points of decks that are gone. */
    suspend fun prune(deckIds: Iterable<String>) {
        if (_points.value.keys.any { it !in deckIds }) save(prunedDeckHistory(_points.value, deckIds))
    }
}
