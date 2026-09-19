package com.mtgcompanion.app.data

// A deck's games, summed up for its Stats: the record, recent form, how long games run, and how it
// does against each commander and each person it has faced. Mirrors the web app's
// src/decks/gameStats.ts.

/** Games against one commander, or one person. */
data class Matchup(val name: String, val games: Int, val wins: Int, val losses: Int)

data class GameStats(
    val games: Int,
    val wins: Int,
    val losses: Int,
    val draws: Int,
    /** The last ten results, newest first ("WIN", "LOSS", "DRAW"). */
    val recent: List<String>,
    /** The current run of one result: e.g. ("WIN", 3). Null under two games. */
    val streak: Pair<String, Int>?,
    /** Averages over the games that kept time; null when none did. */
    val averageMinutes: Int?,
    val averageTurns: Int?,
    val commanders: List<Matchup>,
    val opponents: List<Matchup>
) {
    val winRate: Int get() = if (games == 0) 0 else wins * 100 / games
}

/** Most-faced first; ties by name. At most [limit]. */
private fun matchups(results: List<GameResult>, keys: (GameResult) -> List<String>, limit: Int): List<Matchup> {
    val byKey = LinkedHashMap<String, Pair<String, MutableList<GameResult>>>()
    for (g in results) {
        // One game counts once against a name, however often it's listed.
        for (name in keys(g).map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }) {
            byKey.getOrPut(name.lowercase()) { name to mutableListOf() }.second += g
        }
    }
    return byKey.values
        .map { (name, games) -> Matchup(name, games.size, games.count { it.result == "WIN" }, games.count { it.result == "LOSS" }) }
        .sortedWith(compareByDescending<Matchup> { it.games }.thenBy { it.name.lowercase() })
        .take(limit)
}

fun gameStats(results: List<GameResult>, limit: Int = 5): GameStats {
    val newest = results.sortedByDescending { it.playedAt }
    val streak = newest.firstOrNull()?.let { first ->
        val n = newest.takeWhile { it.result == first.result }.size
        (first.result to n).takeIf { newest.size >= 2 && n >= 2 }
    }
    val timed = results.mapNotNull { it.minutes?.takeIf { m -> m > 0 } }
    val turned = results.mapNotNull { it.turns?.takeIf { t -> t > 0 } }
    return GameStats(
        games = results.size,
        wins = results.count { it.result == "WIN" },
        losses = results.count { it.result == "LOSS" },
        draws = results.count { it.result == "DRAW" },
        recent = newest.take(10).map { it.result },
        streak = streak,
        averageMinutes = if (timed.isEmpty()) null else Math.round(timed.average()).toInt(),
        averageTurns = if (turned.isEmpty()) null else Math.round(turned.average()).toInt(),
        commanders = matchups(results, { it.commanders }, limit),
        opponents = matchups(results, { it.opponent?.split(",").orEmpty() }, limit)
    )
}

/** "2–1" (or "2–1–1" with draws). */
fun Matchup.record(): String = "$wins–$losses" + (games - wins - losses).let { if (it > 0) "–$it" else "" }
