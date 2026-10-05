package com.mtgcompanion.app.data.tournament

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// Small tournaments run from one device, with no server: 1v1 Swiss or Commander pods — pairings,
// byes, results, the round clock, standings and tiebreakers. Pure: no storage, no clock of its own.
// Mirrors the web app's src/tournament/tournament.ts — the same seed gives the same pairings in both.
//
// Scoring. 1v1 Swiss: a match win is 3 points, a draw 1, a loss 0; a bye is a 2–0 win. Ties are
// broken by the standard MTG tiebreakers: opponents' match-win % (OMW%), game-win % (GW%) and
// opponents' game-win % (OGW%), each percentage floored at 33%; byes don't count as opponents.
// Commander pods: the winner of a pod game gets 3 points, a drawn pod gives everyone in it 1, the
// rest 0; ties are broken by the average points of everyone you've shared a pod with. Players
// still tied keep the event's seeded order.

/** An event's format, stored as its name ("SWISS", "PODS") as the web app stores it. */
object EventFormat {
    const val SWISS = "SWISS"
    const val PODS = "PODS"
}

/** A player in an event. [userId]: the friend they were picked from, if they were. */
data class EventPlayer(val id: String, val name: String, val userId: String? = null, val dropped: Boolean = false)

/**
 * One table's result: games won by each player, in the table's [EventTable.players] order, and
 * drawn games. A pod's winner has 1 and the rest 0; a drawn pod is all 0 with draws 1.
 */
data class TableResult(val wins: List<Int>, val draws: Int = 0)

/** One table in a round. A table of one is a bye, its result already in. */
data class EventTable(val players: List<String>, val result: TableResult? = null)

/** The round clock: running until [endsAt], or paused with [leftMs] to go. Neither: not started. */
data class RoundTimer(val endsAt: Long? = null, val leftMs: Long? = null)

data class EventRound(val number: Int, val tables: List<EventTable>, val timer: RoundTimer = RoundTimer())

data class Tournament(
    val id: String,
    val name: String,
    val format: String,
    /** 1 or 3 games a match (1v1 Swiss only). */
    val bestOf: Int,
    /** The rounds planned. */
    val roundCount: Int,
    val roundMinutes: Int,
    /** Shuffles the seating; the same seed always gives the same event. */
    val seed: Int,
    val createdAt: Long,
    val players: List<EventPlayer>,
    /** The rounds paired so far, oldest first. Only the last one's results can still change. */
    val rounds: List<EventRound> = emptyList(),
    val finished: Boolean = false,
    /** The top 8/4/2 bracket or the pods' final table, once cut to (Playoff.kt); null in events saved before playoffs. */
    val playoff: Playoff? = null
)

const val MIN_EVENT_PLAYERS = 4
const val MAX_EVENT_PLAYERS = 32

fun formatName(format: String): String = if (format == EventFormat.SWISS) "1v1 Swiss" else "Commander pods"

/** "1v1 Swiss · best of 3", "Commander pods". */
fun formatLabel(t: Tournament): String =
    if (t.format == EventFormat.SWISS) "${formatName(EventFormat.SWISS)} · best of ${t.bestOf}" else formatName(EventFormat.PODS)

/** A round's length unless changed: 50 minutes for 1v1, 75 for pods. */
fun defaultRoundMinutes(format: String): Int = if (format == EventFormat.SWISS) 50 else 75

/**
 * Rounds to suggest for [players]: Swiss needs ceil(log2 n) to find one unbeaten player. Pods meet
 * three others at a time, so one fewer (at least 2).
 */
fun suggestedRounds(format: String, players: Int): Int {
    var swiss = 0
    while ((1 shl swiss) < players) swiss++
    return if (format == EventFormat.SWISS) max(1, swiss) else max(2, swiss - 1)
}

// --- The seeded shuffle -------------------------------------------------------------------------

/** Mulberry32: a small seeded generator, written the same way in both apps. Numbers in [0, 1). */
fun seededRandom(seed: Int): () -> Double {
    var a = seed
    return {
        a += 0x6D2B79F5
        var t = a
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0
    }
}

/** [items] shuffled (Fisher–Yates) by [seed]. */
fun <T> seededShuffle(items: List<T>, seed: Int): List<T> {
    val out = items.toMutableList()
    val next = seededRandom(seed)
    for (i in out.size - 1 downTo 1) {
        val j = floor(next() * (i + 1)).toInt()
        val swap = out[i]
        out[i] = out[j]
        out[j] = swap
    }
    return out
}

// --- Making an event ----------------------------------------------------------------------------

data class Entrant(val name: String, val userId: String? = null)

/** A new event, nobody paired yet. Players are p1, p2… in the order they were added. */
fun newTournament(
    id: String, name: String, format: String, bestOf: Int, roundCount: Int, roundMinutes: Int, seed: Int, createdAt: Long, players: List<Entrant>
) = Tournament(
    id = id,
    name = name.trim(),
    format = format,
    bestOf = if (format == EventFormat.SWISS) bestOf else 1,
    roundCount = max(1, roundCount),
    roundMinutes = max(1, roundMinutes),
    seed = seed,
    createdAt = createdAt,
    players = players.mapIndexed { i, p -> EventPlayer("p${i + 1}", p.name.trim(), p.userId) }
)

/** Why [names] can't start an event, or null when they can. */
fun playersProblem(names: List<String>): String? {
    val clean = names.map { it.trim().lowercase() }
    return when {
        clean.size < MIN_EVENT_PLAYERS -> "Add at least $MIN_EVENT_PLAYERS players"
        clean.size > MAX_EVENT_PLAYERS -> "At most $MAX_EVENT_PLAYERS players"
        clean.any { it.isEmpty() } -> "Every player needs a name"
        clean.toSet().size != clean.size -> "Two players have the same name"
        else -> null
    }
}

// --- Results ------------------------------------------------------------------------------------

data class ResultChoice(val label: String, val wins: List<Int>, val draws: Int) {
    val result: TableResult get() = TableResult(wins, draws)
}

/** The results a 1v1 table can be given, from the first player's side. */
fun matchChoices(bestOf: Int): List<ResultChoice> =
    if (bestOf == 1) listOf(
        ResultChoice("1–0", listOf(1, 0), 0),
        ResultChoice("0–1", listOf(0, 1), 0),
        ResultChoice("Draw", listOf(0, 0), 1)
    ) else listOf(
        ResultChoice("2–0", listOf(2, 0), 0),
        ResultChoice("2–1", listOf(2, 1), 0),
        ResultChoice("1–2", listOf(1, 2), 0),
        ResultChoice("0–2", listOf(0, 2), 0),
        ResultChoice("1–1", listOf(1, 1), 0),
        ResultChoice("Draw", listOf(0, 0), 1)
    )

/** A pod's result: [winner] (a player id) won, or nobody did — a draw. */
fun podResult(players: List<String>, winner: String?): TableResult =
    if (winner == null) TableResult(players.map { 0 }, 1) else TableResult(players.map { if (it == winner) 1 else 0 }, 0)

/** A bye's result: a 2–0 win in Swiss, a pod won in pods. */
fun byeResult(format: String) = TableResult(listOf(if (format == EventFormat.SWISS) 2 else 1), 0)

/** "Alice won", "Draw", "2–1" — a table's result in a few characters. */
fun resultText(t: Tournament, table: EventTable): String? {
    val r = table.result ?: return null
    if (table.players.size == 1) return "Bye"
    if (t.format == EventFormat.SWISS) {
        return if (r.wins[0] == 0 && r.wins[1] == 0 && r.draws > 0) "Draw"
        else "${r.wins[0]}–${r.wins[1]}" + (if (r.draws > 0) "–${r.draws}" else "")
    }
    val winner = table.players.filterIndexed { i, _ -> r.wins.getOrElse(i) { 0 } > 0 }.firstOrNull()
    return if (winner != null) "${playerName(t, winner)} won" else "Draw"
}

fun playerName(t: Tournament, id: String): String = t.players.firstOrNull { it.id == id }?.name ?: "?"

/** The current round: the last one paired. */
fun currentRound(t: Tournament): EventRound? = t.rounds.lastOrNull()

/** Every table of the current round has its result. */
fun roundComplete(round: EventRound?): Boolean = round == null || round.tables.all { it.result != null }

/** [t] with table [index] of the current round given [result] (null clears it). Earlier rounds are settled. */
fun withResult(t: Tournament, index: Int, result: TableResult?): Tournament {
    val round = currentRound(t) ?: return t
    val table = round.tables.getOrNull(index)
    if (t.finished || table == null || table.players.size < 2) return t
    val tables = round.tables.mapIndexed { i, tb -> if (i == index) tb.copy(result = result) else tb }
    return t.copy(rounds = t.rounds.dropLast(1) + round.copy(tables = tables))
}

/** [t] with player [id] dropped (or back in): they aren't paired from the next round on. */
fun withDropped(t: Tournament, id: String, dropped: Boolean): Tournament =
    t.copy(players = t.players.map { if (it.id == id) it.copy(dropped = dropped) else it })

fun activePlayers(t: Tournament): List<EventPlayer> = t.players.filter { !it.dropped }

/** The next round can be paired: this one's results are all in, rounds are left and two can play. */
fun canPairNext(t: Tournament): Boolean =
    !t.finished && roundComplete(currentRound(t)) && t.rounds.size < t.roundCount && activePlayers(t).size >= 2

/** The event can end: at least one round is played and its results are all in. */
fun canFinish(t: Tournament): Boolean = !t.finished && t.rounds.isNotEmpty() && roundComplete(currentRound(t))

// --- Standings ----------------------------------------------------------------------------------

/** The lowest a match-win or game-win percentage counts as, as in MTG tournaments. */
const val PERCENT_FLOOR = 0.33

data class Standing(
    val id: String,
    val name: String,
    val dropped: Boolean,
    /** Match points (Swiss) or pod points. */
    val points: Int,
    val wins: Int,
    val losses: Int,
    val draws: Int,
    val byes: Int,
    /** Swiss tiebreakers, 0 to 1. */
    val omw: Double,
    val gw: Double,
    val ogw: Double,
    /** Pods' tiebreaker: the average points of everyone met in a pod. */
    val oppPoints: Double
)

private class Tally {
    var points = 0
    var matches = 0
    var wins = 0
    var losses = 0
    var draws = 0
    var byes = 0
    var gamePoints = 0
    var games = 0
    val opponents = mutableListOf<String>()
}

private fun tallies(t: Tournament): Map<String, Tally> {
    val out = LinkedHashMap<String, Tally>()
    t.players.forEach { out[it.id] = Tally() }
    for (round in t.rounds) {
        for (table in round.tables) {
            val r = table.result ?: continue
            if (table.players.size == 1) {
                val me = out[table.players[0]] ?: continue
                me.points += 3
                me.matches++
                me.wins++
                me.byes++
                // A Swiss bye is two games won.
                if (t.format == EventFormat.SWISS) { me.gamePoints += 6; me.games += 2 }
                continue
            }
            val podDraw = r.wins.all { it == 0 }
            table.players.forEachIndexed { i, id ->
                val me = out[id] ?: return@forEachIndexed
                me.opponents += table.players.filter { it != id }
                me.matches++
                if (t.format == EventFormat.SWISS) {
                    val mine = r.wins.getOrElse(i) { 0 }
                    val theirs = r.wins.getOrElse(1 - i) { 0 }
                    me.games += mine + theirs + r.draws
                    me.gamePoints += 3 * mine + r.draws
                    when {
                        mine > theirs -> { me.wins++; me.points += 3 }
                        mine < theirs -> me.losses++
                        else -> { me.draws++; me.points += 1 }
                    }
                } else if (podDraw) {
                    me.draws++
                    me.points += 1
                } else if (r.wins.getOrElse(i) { 0 } > 0) {
                    me.wins++
                    me.points += 3
                } else {
                    me.losses++
                }
            }
        }
    }
    return out
}

private fun average(xs: List<Double>): Double = if (xs.isEmpty()) 0.0 else xs.sum() / xs.size

private fun mwp(x: Tally): Double = if (x.matches == 0) PERCENT_FLOOR else max(PERCENT_FLOOR, x.points.toDouble() / (3 * x.matches))

private fun gwp(x: Tally): Double = if (x.games == 0) PERCENT_FLOOR else max(PERCENT_FLOOR, x.gamePoints.toDouble() / (3 * x.games))

/** Each player's place, best first: points, then the format's tiebreakers, then the seeded order. */
fun standings(t: Tournament): List<Standing> {
    val all = tallies(t)
    val seat = seededShuffle(t.players.map { it.id }, t.seed).withIndex().associate { (i, id) -> id to i }
    val swiss = t.format == EventFormat.SWISS
    val rows = t.players.map { p ->
        val x = all.getValue(p.id)
        val opps = x.opponents.mapNotNull { all[it] }
        Standing(
            id = p.id,
            name = p.name,
            dropped = p.dropped,
            points = x.points,
            wins = x.wins,
            losses = x.losses,
            draws = x.draws,
            byes = x.byes,
            omw = if (swiss) average(opps.map { mwp(it) }) else 0.0,
            gw = if (swiss) gwp(x) else 0.0,
            ogw = if (swiss) average(opps.map { gwp(it) }) else 0.0,
            oppPoints = if (swiss) 0.0 else average(opps.map { it.points.toDouble() })
        )
    }
    return rows.sortedWith(
        compareByDescending<Standing> { it.points }
            .thenByDescending { it.omw }
            .thenByDescending { it.gw }
            .thenByDescending { it.ogw }
            .thenByDescending { it.oppPoints }
            .thenBy { seat.getValue(it.id) }
    )
}

/** "55.6%": a tiebreaker to one decimal place, rounded the same way in both apps. */
fun percentText(x: Double): String {
    val tenths = Math.round(x * 1000)
    return "${tenths / 10}.${tenths % 10}%"
}

/** "4.3": an average to one decimal place. */
fun oneDecimal(x: Double): String {
    val tenths = Math.round(x * 10)
    return "${tenths / 10}.${tenths % 10}"
}

/** "3–1–0": wins, losses, draws. */
fun recordText(s: Standing): String = "${s.wins}–${s.losses}–${s.draws}"

/** "Final standings", "Standings after round 2". */
fun standingsHeading(t: Tournament): String = when {
    t.finished -> "Final standings"
    t.rounds.isEmpty() -> "Standings"
    else -> "Standings after round ${t.rounds.size}"
}

/** The standings as plain text, to copy or share. */
fun standingsText(t: Tournament): String {
    val lines = standings(t).mapIndexed { i, s ->
        val tiebreaks = if (t.format == EventFormat.SWISS) "OMW ${percentText(s.omw)} · GW ${percentText(s.gw)} · OGW ${percentText(s.ogw)}"
        else "Opp. avg ${oneDecimal(s.oppPoints)}"
        "${i + 1}. ${s.name} — ${s.points} pts · ${recordText(s)} · $tiebreaks" + (if (s.dropped) " · dropped" else "")
    }
    val rounds = "${t.rounds.size} ${if (t.rounds.size == 1) "round" else "rounds"}"
    return (listOf(t.name, "${formatLabel(t)} · ${t.players.size} players · $rounds", standingsHeading(t), "") + lines).joinToString("\n")
}

// --- Pairings -----------------------------------------------------------------------------------

private fun pairKey(a: String, b: String) = if (a < b) "$a|$b" else "$b|$a"

/** How often each two players have shared a table. */
private fun meetings(t: Tournament): Map<String, Int> {
    val met = HashMap<String, Int>()
    for (round in t.rounds) {
        for (table in round.tables) {
            for (i in table.players.indices) {
                for (j in i + 1 until table.players.size) {
                    val k = pairKey(table.players[i], table.players[j])
                    met[k] = (met[k] ?: 0) + 1
                }
            }
        }
    }
    return met
}

/** How long the pairing search may look for a way round repeats before settling for one more. */
private const val MAX_PAIRING_STEPS = 20_000

private class Steps(var n: Int = 0)

/**
 * [ids] (best first) in pairs, each player with the nearest below them they haven't played,
 * allowing at most [repeats] rematches — or null when that can't be done.
 */
private fun pairUp(ids: List<String>, met: Map<String, Int>, repeats: Int, steps: Steps): List<List<String>>? {
    if (ids.isEmpty()) return emptyList()
    val first = ids[0]
    val others = ids.drop(1)
    for (i in others.indices) {
        if (++steps.n > MAX_PAIRING_STEPS) return null
        val rematch = (met[pairKey(first, others[i])] ?: 0) > 0
        if (rematch && repeats == 0) continue
        val rest = pairUp(others.filterIndexed { j, _ -> j != i }, met, repeats - (if (rematch) 1 else 0), steps)
        if (rest != null) return listOf(listOf(first, others[i])) + rest
        if (steps.n > MAX_PAIRING_STEPS) return null
    }
    return null
}

/**
 * Swiss pairings for [order] (the active players, best first): within score groups, floating down
 * where a group is odd, and no rematch unless there's no way round it. An odd player out gets a bye
 * — the lowest-placed player who hasn't had one.
 */
private fun swissTables(t: Tournament, order: List<String>): List<EventTable> {
    val met = meetings(t)
    val hadBye = t.rounds.flatMap { r -> r.tables.filter { it.players.size == 1 }.map { it.players[0] } }.toSet()
    var byes: List<String?> = listOf(null)
    if (order.size % 2 == 1) {
        val fromBottom = order.reversed()
        val fresh = fromBottom.filter { it !in hadBye }
        byes = if (fresh.isNotEmpty()) fresh else fromBottom
    }
    for (repeats in 0..order.size) {
        for (bye in byes) {
            val pairs = pairUp(order.filter { it != bye }, met, repeats, Steps()) ?: continue
            val tables = pairs.map { EventTable(it) }
            return if (bye == null) tables else tables + EventTable(listOf(bye), byeResult(EventFormat.SWISS))
        }
    }
    return emptyList()
}

/** Pod sizes for [n] players: fours, with threes making up the rest (five: a three and a two). */
fun podSizes(n: Int): List<Int> {
    if (n <= 0) return emptyList()
    if (n < 3) return listOf(n)
    if (n == 5) return listOf(3, 2)
    val threes = (4 - n % 4) % 4
    return List((n - 3 * threes) / 4) { 4 } + List(threes) { 3 }
}

/** How far down the standings a pod looks for someone its players haven't met. */
private const val POD_WINDOW = 8

/** Pods for [order] (best first): grouped by standing, each seat going to whoever nearby has met the pod least. */
private fun podTables(t: Tournament, order: List<String>): List<EventTable> {
    val met = meetings(t)
    val left = order.toMutableList()
    return podSizes(order.size).map { size ->
        val pod = mutableListOf(left.removeAt(0))
        while (pod.size < size) {
            var best = 0
            var bestCost = Int.MAX_VALUE
            for (i in 0 until min(POD_WINDOW, left.size)) {
                val cost = pod.sumOf { p -> met[pairKey(p, left[i])] ?: 0 }
                if (cost < bestCost) { best = i; bestCost = cost }
            }
            pod += left.removeAt(best)
        }
        EventTable(pod.toList(), if (pod.size == 1) byeResult(EventFormat.PODS) else null)
    }
}

/** [t] with its next round paired (round 1 in the seeded order), or [t] when it can't be. */
fun pairNextRound(t: Tournament): Tournament {
    if (!canPairNext(t)) return t
    val order = standings(t).filter { !it.dropped }.map { it.id }
    val tables = if (t.format == EventFormat.SWISS) swissTables(t, order) else podTables(t, order)
    return t.copy(rounds = t.rounds + EventRound(t.rounds.size + 1, tables, RoundTimer()))
}

// --- The round clock ----------------------------------------------------------------------------

/** Time left on [timer] at [now]: negative once it's run out. */
fun timeLeft(timer: RoundTimer, minutes: Int, now: Long): Long =
    if (timer.endsAt != null) timer.endsAt - now else timer.leftMs ?: (minutes * 60_000L)

fun timerRunning(timer: RoundTimer): Boolean = timer.endsAt != null

fun startTimer(timer: RoundTimer, minutes: Int, now: Long): RoundTimer =
    if (timer.endsAt != null) timer else RoundTimer(endsAt = now + timeLeft(timer, minutes, now))

fun pauseTimer(timer: RoundTimer, minutes: Int, now: Long): RoundTimer =
    if (timer.endsAt == null) timer else RoundTimer(leftMs = timeLeft(timer, minutes, now))

/** [t] with the current round's clock set to [timer]. */
fun withTimer(t: Tournament, timer: RoundTimer): Tournament {
    val round = currentRound(t) ?: return t
    return t.copy(rounds = t.rounds.dropLast(1) + round.copy(timer = timer))
}

/** "49:05", "-2:30": the round clock, in minutes and seconds. */
fun clockText(ms: Long): String {
    val total = ceil(abs(ms) / 1000.0).toLong()
    val sign = if (ms < 0 && total > 0) "-" else ""
    return "$sign${total / 60}:" + (total % 60).toString().padStart(2, '0')
}

/** What a table does once the clock runs out. */
fun extraTurnsText(format: String): String = if (format == EventFormat.SWISS) "5 extra turns" else "Finish the turn cycle"

// --- Saved events -------------------------------------------------------------------------------

/** "Round 2 of 4", "Not started", "Finished". */
fun statusText(t: Tournament): String = when {
    t.finished -> "Finished"
    t.rounds.isEmpty() -> "Not started"
    else -> "Round ${t.rounds.size} of ${t.roundCount}"
}

/** [events] with [t] in its place, newest first. */
fun withEvent(events: List<Tournament>, t: Tournament): List<Tournament> =
    (listOf(t) + events.filter { it.id != t.id }).sortedByDescending { it.createdAt }
