package com.mtgcompanion.app.data

// Mulligans in real games: how many a player took (saved as GameResult.mulligans), the hand it left
// them, and a deck's or a playgroup's mulligan rate and how often a game is won after one. Pure.
// Mirrors the web app's src/decks/mulligans.ts.

/** The most mulligans there are: down to no cards. */
const val MAX_MULLIGANS = 7

/** A mulligan count read from storage or the wire: 0 to 7, else not recorded. */
fun cleanMulligans(raw: Int?): Int? = raw?.takeIf { it in 0..MAX_MULLIGANS }

/**
 * The cards kept after [mulligans]: seven, less one for each — but in a multiplayer game
 * ([freeFirst]) the first mulligan is free.
 */
fun handSize(mulligans: Int, freeFirst: Boolean): Int {
    val counted = if (freeFirst) maxOf(0, mulligans - 1) else mulligans
    return maxOf(0, 7 - counted)
}

/** "Kept 7", "1 mulligan, to 6", "2 mulligans, to 6" (multiplayer). */
fun mulliganText(mulligans: Int, freeFirst: Boolean): String =
    if (mulligans <= 0) "Kept 7"
    else "$mulligans ${if (mulligans == 1) "mulligan" else "mulligans"}, to ${handSize(mulligans, freeFirst)}"

/**
 * [recorded]: games with mulligans recorded; [mulliganed]: of those, with at least one; [rate]: that
 * share, 0 to 100; [winsAfter]: wins in games with a mulligan; [winRateAfter] / [winRateKept]: win
 * rates after a mulligan and when kept at seven, 0 to 100 — null with no such games.
 */
data class MulliganStats(
    val recorded: Int,
    val mulliganed: Int,
    val rate: Int,
    val winsAfter: Int,
    val winRateAfter: Int?,
    val winRateKept: Int?
)

private fun percent(part: Int, whole: Int) = part * 100 / whole

/** Mulligans over [results]; games without a mulligan count recorded don't count. */
fun mulliganStats(results: List<GameResult>): MulliganStats {
    val recorded = results.filter { cleanMulligans(it.mulligans) != null }
    val after = recorded.filter { it.mulligans!! > 0 }
    val kept = recorded.filter { it.mulligans == 0 }
    val winsAfter = after.count { it.result == "WIN" }
    return MulliganStats(
        recorded = recorded.size,
        mulliganed = after.size,
        rate = if (recorded.isEmpty()) 0 else percent(after.size, recorded.size),
        winsAfter = winsAfter,
        winRateAfter = if (after.isEmpty()) null else percent(winsAfter, after.size),
        winRateKept = if (kept.isEmpty()) null else percent(kept.count { it.result == "WIN" }, kept.size)
    )
}

/** "Mulligan in 25% of 8 games · won 50% after one" — or null with nothing recorded. */
fun mulliganSummary(s: MulliganStats): String? {
    if (s.recorded == 0) return null
    val games = "${s.recorded} ${if (s.recorded == 1) "game" else "games"}"
    return "Mulligan in ${s.rate}% of $games" + (s.winRateAfter?.let { " · won $it% after one" } ?: "")
}
