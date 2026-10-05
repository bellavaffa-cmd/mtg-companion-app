package com.mtgcompanion.app.data

// The playgroup: every deck's games together — the user's overall record, how they do against each
// person and each commander, which decks do best, who beats them most, and their streaks. Built on a
// deck's own game stats (GameStats.kt). Mirrors the web app's src/decks/playgroupStats.ts.

/** Games a deck needs before it's ranked, and before a player or commander can be a nemesis. */
const val PLAYGROUP_MIN_GAMES = 3

/** One deck's games. */
data class DeckRecord(val deckId: String, val name: String, val games: Int, val wins: Int, val losses: Int, val draws: Int) {
    val winRate: Int get() = if (games == 0) 0 else wins * 100 / games
}

data class PlaygroupStats(
    val games: Int,
    val wins: Int,
    val losses: Int,
    val draws: Int,
    /** The current run of one result: e.g. ("WIN", 3). Null under two games. */
    val streak: Pair<String, Int>?,
    /** The most wins in a row, ever. */
    val longestWinStreak: Int,
    /** Averages over the games that kept time; null when none did. */
    val averageMinutes: Int?,
    val averageTurns: Int?,
    /** Everyone played, most-played first. */
    val opponents: List<Matchup>,
    /** Every commander faced, most-faced first. */
    val commanders: List<Matchup>,
    /** Decks with at least [PLAYGROUP_MIN_GAMES] games, best win rate first. */
    val ranked: List<DeckRecord>,
    /** Decks with fewer, most-played first. */
    val unranked: List<DeckRecord>,
    /** The person and the commander the user does worst against (enough games, more losses than wins). */
    val nemesis: Matchup?,
    val nemesisCommander: Matchup?
) {
    val winRate: Int get() = if (games == 0) 0 else wins * 100 / games
}

/** The most wins in a row among [results], oldest to newest. */
fun longestWinStreak(results: List<GameResult>): Int {
    var best = 0
    var run = 0
    for (g in results.sortedBy { it.playedAt }) {
        run = if (g.result == "WIN") run + 1 else 0
        best = maxOf(best, run)
    }
    return best
}

/**
 * The matchup the user does worst against: the lowest share of wins, then the most losses, then
 * the most games. Only ones with [min] games or more and a losing record count — a nemesis beats you.
 */
fun nemesisOf(matchups: List<Matchup>, min: Int = PLAYGROUP_MIN_GAMES): Matchup? =
    matchups.filter { it.games >= min && it.losses > it.wins }
        .sortedWith(
            compareBy<Matchup> { it.wins.toDouble() / it.games }
                .thenByDescending { it.losses }
                .thenByDescending { it.games }
                .thenBy { it.name.lowercase() }
        )
        .firstOrNull()

fun playgroupStats(decks: List<Deck>): PlaygroupStats {
    val all = decks.flatMap { it.gameResults }
    val overall = gameStats(all, Int.MAX_VALUE)
    val records = decks.filter { it.gameResults.isNotEmpty() }.map { d ->
        val s = gameStats(d.gameResults, 0)
        DeckRecord(d.id, d.name, s.games, s.wins, s.losses, s.draws)
    }
    return PlaygroupStats(
        games = overall.games,
        wins = overall.wins,
        losses = overall.losses,
        draws = overall.draws,
        streak = overall.streak,
        longestWinStreak = longestWinStreak(all),
        averageMinutes = overall.averageMinutes,
        averageTurns = overall.averageTurns,
        opponents = overall.opponents,
        commanders = overall.commanders,
        ranked = records.filter { it.games >= PLAYGROUP_MIN_GAMES }.sortedWith(
            compareByDescending<DeckRecord> { it.wins.toDouble() / it.games }
                .thenByDescending { it.games }
                .thenBy { it.name.lowercase() }
        ),
        unranked = records.filter { it.games < PLAYGROUP_MIN_GAMES }
            .sortedWith(compareByDescending<DeckRecord> { it.games }.thenBy { it.name.lowercase() }),
        nemesis = nemesisOf(overall.opponents),
        nemesisCommander = nemesisOf(overall.commanders)
    )
}
