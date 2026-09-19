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

// The collection's value over time: one point a day, the owned binders' value (in US dollars, like
// every price the app keeps) as Home last worked it out that day. Kept on this device. Mirrors the
// web app's src/collection/valueHistory.ts.

/** [date]: "2026-09-20". [cards]: how many cards the value is of. */
data class ValuePoint(val date: String, val usd: Double, val cards: Int)

/** How the value moved over a stretch: from [from] to [to]. */
data class ValueChange(val from: ValuePoint, val to: ValuePoint) {
    val usd: Double get() = to.usd - from.usd
    val percent: Double? get() = if (from.usd > 0) (to.usd - from.usd) / from.usd * 100 else null
}

/** The stretches the chart can show: the last [days] (null: everything). */
enum class ValueRange(val label: String, val days: Long?) {
    MONTH("1M", 30), QUARTER("3M", 91), YEAR("1Y", 365), ALL("All", null)
}

/** The points within [range] of the newest one, oldest first. */
fun pointsIn(points: List<ValuePoint>, range: ValueRange): List<ValuePoint> {
    val last = points.lastOrNull() ?: return emptyList()
    val days = range.days ?: return points
    val since = LocalDate.parse(last.date).minusDays(days).toString()
    return points.filter { it.date >= since }
}

/** Change from the first to the last of [points]; null under two points. */
fun changeOf(points: List<ValuePoint>): ValueChange? =
    if (points.size < 2) null else ValueChange(points.first(), points.last())

/** [points] with [point] in its day's place (a later value that day replaces the earlier), oldest first, at most [keep]. */
fun withPoint(points: List<ValuePoint>, point: ValuePoint, keep: Int = ValueHistory.KEEP): List<ValuePoint> =
    (points.filterNot { it.date == point.date } + point).sortedBy { it.date }.takeLast(keep)

object ValueHistory {
    private const val FILE = "value_history.json"
    /** About three years of days. */
    const val KEEP = 1100

    private var dir: File? = null
    private val _points = MutableStateFlow<List<ValuePoint>>(emptyList())
    /** Oldest first. */
    val points: StateFlow<List<ValuePoint>> = _points.asStateFlow()

    fun init(context: Context) {
        dir = context.filesDir
        _points.value = runCatching {
            val a = JSONArray(File(context.filesDir, FILE).readText())
            (0 until a.length()).map { i -> a.getJSONObject(i).let { ValuePoint(it.getString("d"), it.getDouble("v"), it.optInt("n")) } }
        }.getOrDefault(emptyList())
    }

    /** Notes today's value of the owned binders. */
    suspend fun record(usd: Double, cards: Int) = withContext(Dispatchers.IO) {
        val next = withPoint(_points.value, ValuePoint(LocalDate.now().toString(), Math.round(usd * 100) / 100.0, cards))
        _points.value = next
        runCatching {
            val a = JSONArray()
            next.forEach { a.put(JSONObject().put("d", it.date).put("v", it.usd).put("n", it.cards)) }
            dir?.let { File(it, FILE).writeText(a.toString()) }
        }
    }
}
