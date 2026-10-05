package com.mtgcompanion.app.data.tournament

import kotlin.math.ln
import kotlin.math.roundToInt

// After the Swiss rounds: a single-elimination top 8, 4 or 2, seeded by the standings (1 v 8, 4 v 5,
// 2 v 7, 3 v 6, so the top two seeds can only meet in the final), or for Commander pods a final table
// where the top 4 play one game. Saved with the event as Tournament.playoff, in the same JSON as the
// web app; an event saved before this has none. Pure. Mirrors the web app's src/tournament/playoff.ts.

object PlayoffKind {
    const val BRACKET = "BRACKET"
    const val FINAL_TABLE = "FINAL_TABLE"
}

/** One playoff table: its players (null: waiting on an earlier match), higher seed first, and its result. */
data class PlayoffMatch(val players: List<String?>, val result: TableResult? = null)

/** [size]: how many made the cut. [rounds]: the first round first; each round's matches in bracket order. */
data class Playoff(val kind: String, val size: Int, val rounds: List<List<PlayoffMatch>>)

/** The cuts a 1v1 event can make. */
val BRACKET_SIZES = listOf(8, 4, 2)

/** Seeds in bracket order for a bracket of [size] (a power of two): 8 gives 1, 8, 4, 5, 2, 7, 3, 6. */
fun seedOrder(size: Int): List<Int> {
    var order = listOf(1)
    while (order.size < size) {
        val n = order.size * 2
        order = order.flatMap { listOf(it, n + 1 - it) }
    }
    return order
}

/** Whether [t] can cut to a playoff now: its Swiss rounds are all played (or it's finished) and none has been made. */
fun canCut(t: Tournament): Boolean {
    if (t.playoff != null || t.rounds.isEmpty() || !roundComplete(currentRound(t))) return false
    return t.finished || t.rounds.size >= t.roundCount
}

/** The cuts [t] can make: tops that fit its players still in (Swiss), or a final table of up to 4 (pods). */
fun cutSizes(t: Tournament): List<Int> {
    val n = activePlayers(t).size
    if (t.format == EventFormat.PODS) return if (n >= 2) listOf(minOf(4, n)) else emptyList()
    return BRACKET_SIZES.filter { it <= n }
}

/** How many rounds a playoff of [size] has. */
fun playoffRoundCount(kind: String, size: Int): Int = if (kind == PlayoffKind.FINAL_TABLE) 1 else (ln(size.toDouble()) / ln(2.0)).roundToInt()

/** [t] cut to its top [size]: seeded by the standings (dropped players don't make it), the Swiss finished. */
fun startPlayoff(t: Tournament, size: Int): Tournament {
    if (!canCut(t) || size !in cutSizes(t)) return t
    val seeds = standings(t).filter { !it.dropped }.take(size).map { it.id }
    if (t.format == EventFormat.PODS) {
        return t.copy(finished = true, playoff = Playoff(PlayoffKind.FINAL_TABLE, size, listOf(listOf(PlayoffMatch(seeds)))))
    }
    val order = seedOrder(size)
    val first = order.chunked(2).map { (a, b) -> PlayoffMatch(listOf(seeds[a - 1], seeds[b - 1])) }
    val rounds = mutableListOf(first)
    var n = size / 4
    while (n >= 1) {
        rounds += List(n) { PlayoffMatch(listOf(null, null)) }
        n /= 2
    }
    return t.copy(finished = true, playoff = Playoff(PlayoffKind.BRACKET, size, rounds))
}

/** Who won [match], or null while it has no result. */
fun matchWinner(match: PlayoffMatch): String? {
    val r = match.result ?: return null
    var best = 0
    var winner: String? = null
    match.players.forEachIndexed { i, p ->
        val w = r.wins.getOrElse(i) { 0 }
        if (w > best) { best = w; winner = p } else if (w == best) winner = null
    }
    return winner
}

/** The results a bracket match can be given, from the first player's side: no draws, someone goes through. */
fun playoffChoices(bestOf: Int): List<ResultChoice> =
    if (bestOf == 1) listOf(ResultChoice("1–0", listOf(1, 0), 0), ResultChoice("0–1", listOf(0, 1), 0))
    else listOf(
        ResultChoice("2–0", listOf(2, 0), 0),
        ResultChoice("2–1", listOf(2, 1), 0),
        ResultChoice("1–2", listOf(1, 2), 0),
        ResultChoice("0–2", listOf(0, 2), 0)
    )

/** Whether match [index] of round [round] can still change: both players known, and the match it feeds not yet played. */
fun playoffEditable(p: Playoff, round: Int, index: Int): Boolean {
    val match = p.rounds.getOrNull(round)?.getOrNull(index) ?: return false
    if (match.players.any { it == null }) return false
    val next = p.rounds.getOrNull(round + 1)?.getOrNull(index / 2)
    return next?.result == null
}

/**
 * [t] with playoff match [index] of round [round] given [result] (null clears it). Its winner goes
 * through to the next round; a match whose next one has been played is settled and doesn't change.
 */
fun withPlayoffResult(t: Tournament, round: Int, index: Int, result: TableResult?): Tournament {
    val p = t.playoff ?: return t
    if (!playoffEditable(p, round, index)) return t
    val rounds = p.rounds.map { it.toMutableList() }.toMutableList()
    val played = rounds[round][index].copy(result = result)
    rounds[round][index] = played
    rounds.getOrNull(round + 1)?.let { next ->
        val target = next[index / 2]
        next[index / 2] = target.copy(players = target.players.toMutableList().also { it[index % 2] = matchWinner(played) })
    }
    return t.copy(playoff = p.copy(rounds = rounds))
}

/** The event's champion: the playoff's last winner, or null while it isn't decided (or there's no playoff). */
fun playoffChampion(t: Tournament): String? {
    val last = t.playoff?.rounds?.lastOrNull() ?: return null
    return if (last.size == 1) matchWinner(last.first()) else null
}

/** "Quarterfinals", "Semifinals", "Final", "Final table". */
fun playoffRoundName(p: Playoff, round: Int): String {
    if (p.kind == PlayoffKind.FINAL_TABLE) return "Final table"
    return when (val matches = p.rounds.getOrNull(round)?.size ?: 1) {
        1 -> "Final"
        2 -> "Semifinals"
        4 -> "Quarterfinals"
        else -> "Top ${matches * 2}"
    }
}

/** "Cut to top 8", "Final table (top 4)". */
fun cutLabel(format: String, size: Int): String = if (format == EventFormat.PODS) "Final table (top $size)" else "Cut to top $size"

/** "Top 8", "Final table" — what an event with a playoff is up to, before a champion. */
fun playoffLabel(p: Playoff): String = if (p.kind == PlayoffKind.FINAL_TABLE) "Final table" else "Top ${p.size}"

/** "Top 8", "Champion: Ana" — an event's status once its playoff is in; null without one. */
fun playoffStatus(t: Tournament, nameOf: (String) -> String): String? {
    val p = t.playoff ?: return null
    return playoffChampion(t)?.let { "Champion: ${nameOf(it)}" } ?: playoffLabel(p)
}
