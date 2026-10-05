package com.mtgcompanion.app.ui.lifecounter

import kotlin.math.abs

// A finished game's life totals over its turns (or its time), and a short recap: who dealt the most
// commander damage, the biggest swing in one turn, the longest turn and who was knocked out when.
// Built from the game's history (each entry notes the player's life after it) into a small log that
// is kept with the table's game. Pure. Mirrors the web app's src/lifecounter/lifeChart.ts — the same
// log, series and recap from the same entries. Stored as the same JSON keys.

/** One player's life after something changed it. [ms]: game-clock time; [turn]: the round. */
data class LifePoint(val seat: Int, val ms: Long, val turn: Int, val life: Int)
/** When a player went out, and why (LIFE, POISON, COMMANDER_DAMAGE, KILLED). */
data class Knockout(val seat: Int, val ms: Long, val turn: Int, val reason: String)
/** When a turn began, and whose it was. */
data class TurnStart(val seat: Int, val turn: Int, val ms: Long)
/** Commander damage [to] took from [from]'s commanders (both partners together). */
data class DamageTotal(val to: Int, val from: Int, val amount: Int)
data class SeatLife(val seat: Int, val life: Int)

/** What a finished game keeps for its chart and recap. [points] oldest first; [turns] empty when turns weren't tracked. */
data class GameLog(
    val start: List<SeatLife>,
    val points: List<LifePoint>,
    val outs: List<Knockout> = emptyList(),
    val turns: List<TurnStart> = emptyList(),
    val damage: List<DamageTotal> = emptyList(),
    /** The game clock when it ended. */
    val endMs: Long
)

/**
 * One line of the game's history: who it was about, when, in which round, their life after it and
 * whether that had them out (with automatic knock-outs on; [outKnown] false: the entry doesn't say),
 * whether it began a turn, and whether that was a new first player (which starts the turns over).
 */
data class LogEntry(
    val seat: Int?,
    val ms: Long,
    val turn: Int,
    val life: Int? = null,
    val out: String? = null,
    val outKnown: Boolean = true,
    val turnStart: Boolean = false,
    val first: Boolean = false
)

/** Points kept with a game; past this, only the last of each player's points in each round stays. */
const val LOG_POINT_LIMIT = 300

/** [points] cut down to [limit]: the last point of each player in each round, then the newest. */
fun compactPoints(points: List<LifePoint>, limit: Int = LOG_POINT_LIMIT): List<LifePoint> {
    if (points.size <= limit) return points
    val lastOf = HashMap<Pair<Int, Int>, Int>()
    points.forEachIndexed { i, p -> lastOf[p.seat to p.turn] = i }
    val kept = points.filterIndexed { i, p -> lastOf[p.seat to p.turn] == i }
    return if (kept.size <= limit) kept else kept.takeLast(limit)
}

/**
 * The log of a game from its history [entries] (oldest first). [start]: each seat's starting life;
 * [finalOuts]: why each seat is out at the end (null: still in); [firstSeat]: who started.
 */
fun buildGameLog(
    entries: List<LogEntry>,
    start: List<SeatLife>,
    finalOuts: List<Pair<Int, String?>>,
    damage: List<DamageTotal>,
    endMs: Long,
    turnsTracked: Boolean,
    firstSeat: Int
): GameLog {
    val life = start.associate { it.seat to it.life }.toMutableMap()
    val points = mutableListOf<LifePoint>()
    val wasOut = mutableMapOf<Int, Boolean>()
    val wentOut = mutableMapOf<Int, LogEntry>()
    var turns = if (turnsTracked) mutableListOf(TurnStart(firstSeat, 1, 0)) else mutableListOf<TurnStart>()
    for (e in entries) {
        val seat = e.seat ?: continue
        if (e.life != null && life[seat] != e.life) {
            life[seat] = e.life
            points += LifePoint(seat, e.ms, e.turn, e.life)
        }
        if (e.outKnown) {
            val out = e.out != null
            if (out && wasOut[seat] != true) wentOut[seat] = e
            wasOut[seat] = out
        }
        if (turnsTracked && e.turnStart) {
            // A new first player starts the count over.
            if (e.first) turns = mutableListOf()
            turns += TurnStart(seat, e.turn, e.ms)
        }
    }
    val lastTurn = maxOf(1, entries.maxOfOrNull { it.turn } ?: 1)
    val outs = finalOuts.mapNotNull { (seat, out) ->
        out ?: return@mapNotNull null
        val e = wentOut[seat]
        Knockout(seat, e?.ms ?: endMs, e?.turn ?: lastTurn, out)
    }.sortedWith(compareBy({ it.ms }, { it.seat }))
    return GameLog(start, compactPoints(points), outs, turns, damage.filter { it.amount > 0 }, endMs)
}

/** The last round anything in [log] happened in, at least 1. */
fun lastTurnOf(log: GameLog): Int =
    maxOf(1, log.points.maxOfOrNull { it.turn } ?: 1, log.outs.maxOfOrNull { it.turn } ?: 1, log.turns.maxOfOrNull { it.turn } ?: 1)

/** Seat [seat]'s life at the end of round [turn] (round 0: the start). */
fun lifeAtEndOf(log: GameLog, seat: Int, turn: Int): Int {
    var life = log.start.firstOrNull { it.seat == seat }?.life ?: 0
    for (p in log.points) if (p.seat == seat && p.turn <= turn) life = p.life
    return life
}

data class ChartPoint(val x: Long, val life: Int)
data class ChartSeries(val seat: Int, val points: List<ChartPoint>)
/** [xMax]: x runs from 0 to this — rounds, or game-clock milliseconds. */
data class LifeChartData(val series: List<ChartSeries>, val xMax: Long, val yMin: Int, val yMax: Int)

/**
 * Each player's line: by round ([byTurn], x = the round, life at its end) or by time (x = game-clock
 * ms, a step at each change). A player's line stops where they went out.
 */
fun lifeChart(log: GameLog, byTurn: Boolean): LifeChartData {
    val lastTurn = lastTurnOf(log)
    val series = log.start.map { (seat, life) ->
        val out = log.outs.firstOrNull { it.seat == seat }
        val points = mutableListOf(ChartPoint(0, life))
        if (byTurn) {
            val end = out?.turn ?: lastTurn
            for (k in 1..end) points += ChartPoint(k.toLong(), lifeAtEndOf(log, seat, k))
        } else {
            val end = out?.ms ?: log.endMs
            for (p in log.points) if (p.seat == seat && p.ms <= end) points += ChartPoint(p.ms, p.life)
            val last = points.last()
            if (end > last.x) points += ChartPoint(end, last.life)
        }
        ChartSeries(seat, points)
    }
    val lives = series.flatMap { s -> s.points.map { it.life } }
    return LifeChartData(
        series,
        if (byTurn) lastTurn.toLong() else maxOf(1L, log.endMs),
        minOf(0, lives.minOrNull() ?: 0),
        maxOf(1, lives.maxOrNull() ?: 1)
    )
}

data class DamageLeader(val seat: Int, val amount: Int)
data class Swing(val seat: Int, val turn: Int, val delta: Int)
data class LongTurn(val seat: Int, val turn: Int, val ms: Long)

/**
 * [mostCommanderDamage]: who dealt the most, in total. [biggestSwing]: the biggest change in one
 * player's life over one round. [longestTurn]: when the table tracked turns. [knockouts]: first first.
 */
data class GameRecap(
    val mostCommanderDamage: DamageLeader?,
    val biggestSwing: Swing?,
    val longestTurn: LongTurn?,
    val knockouts: List<Knockout>
)

fun gameRecap(log: GameLog): GameRecap {
    val dealt = sortedMapOf<Int, Int>()
    for (d in log.damage) dealt[d.from] = (dealt[d.from] ?: 0) + d.amount
    var most: DamageLeader? = null
    for ((seat, amount) in dealt) if (amount > 0 && (most == null || amount > most.amount)) most = DamageLeader(seat, amount)

    var swing: Swing? = null
    val lastTurn = lastTurnOf(log)
    val seats = log.start.map { it.seat }.sorted()
    for (turn in 1..lastTurn) {
        for (seat in seats) {
            val delta = lifeAtEndOf(log, seat, turn) - lifeAtEndOf(log, seat, turn - 1)
            if (delta != 0 && (swing == null || abs(delta) > abs(swing.delta))) swing = Swing(seat, turn, delta)
        }
    }

    var longest: LongTurn? = null
    val turns = log.turns.sortedBy { it.ms }
    turns.forEachIndexed { i, t ->
        val ms = (if (i + 1 < turns.size) turns[i + 1].ms else log.endMs) - t.ms
        if (ms > 0 && (longest == null || ms > longest!!.ms)) longest = LongTurn(t.seat, t.turn, ms)
    }

    return GameRecap(most, swing, longest, log.outs.sortedWith(compareBy({ it.ms }, { it.seat })))
}

/** "12:05", "1:02:30": a length of game-clock time. */
fun durationText(ms: Long): String {
    val total = maxOf(0L, ms / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    fun two(n: Long) = n.toString().padStart(2, '0')
    return if (h > 0) "$h:${two(m)}:${two(s)}" else "$m:${two(s)}"
}

/** Why someone went out, as a few words. */
fun outText(reason: String): String = when (reason) {
    "LIFE" -> "out of life"
    "POISON" -> "poisoned"
    "COMMANDER_DAMAGE" -> "commander damage"
    else -> "knocked out"
}

/**
 * The recap as short lines, [nameOf] giving each seat's name: "Ana dealt the most commander
 * damage: 23", "Biggest swing: Ben, −12 in round 4", "Longest turn: Cat's, round 3 (6:40)",
 * "Ben went out in round 5 (out of life)".
 */
fun recapLines(recap: GameRecap, nameOf: (Int) -> String): List<String> = buildList {
    recap.mostCommanderDamage?.let { add("${nameOf(it.seat)} dealt the most commander damage: ${it.amount}") }
    recap.biggestSwing?.let { add("Biggest swing: ${nameOf(it.seat)}, ${if (it.delta > 0) "+" else "−"}${abs(it.delta)} in round ${it.turn}") }
    recap.longestTurn?.let { add("Longest turn: ${nameOf(it.seat)}'s, round ${it.turn} (${durationText(it.ms)})") }
    for (k in recap.knockouts) add("${nameOf(k.seat)} went out in round ${k.turn} (${outText(k.reason)})")
}
