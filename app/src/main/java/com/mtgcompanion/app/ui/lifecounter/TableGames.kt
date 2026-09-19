package com.mtgcompanion.app.ui.lifecounter

import com.mtgcompanion.app.data.GameResult

// The games played at this table, kept on the device the table runs on: who sat where, what they
// played, who won and how long it took. And, for a table owner playing without a remote of their
// own, their seat's game saved to their deck. Mirrors the web app's src/lifecounter/tableGames.ts.

/** One seat in a finished game. [out]: why they lost (LIFE, POISON…), null for the winner. [me]: the table owner's seat. */
data class TableGamePlayer(val seat: Int, val name: String, val commander: String? = null, val out: String? = null, val me: Boolean = false)

/** A finished game. [id] is the table's game id, so a result changed by an undo replaces it rather than adding one. */
data class TableGame(
    val id: String,
    val endedAt: Long,
    val turns: Int,
    val minutes: Int,
    /** Null when nobody was left standing. */
    val winnerSeat: Int?,
    val players: List<TableGamePlayer>
) {
    val winner: TableGamePlayer? get() = players.firstOrNull { it.seat == winnerSeat }
}

/** How many games the table keeps. */
const val TABLE_GAMES_KEPT = 50

/** [games] with [game] in its place (newest first), at most [TABLE_GAMES_KEPT]. */
fun withTableGame(games: List<TableGame>, game: TableGame): List<TableGame> =
    (listOf(game) + games.filterNot { it.id == game.id }).sortedByDescending { it.endedAt }.take(TABLE_GAMES_KEPT)

/** The id a table-owner's result is saved under on their deck: one per game, however often it's re-saved. */
fun meResultId(gameId: String) = "table-$gameId"

/**
 * The table owner's result for [game], to save to their deck — or null when there's nothing to
 * save: no seat is theirs, or someone joined that seat from a phone. A phone that joins saves the
 * result itself, and the same account on both would save the game twice.
 */
fun meResultOf(game: TableGame, meSeat: Int?, seatLinked: Boolean): GameResult? {
    if (meSeat == null || seatLinked) return null
    if (game.players.none { it.seat == meSeat }) return null
    val others = game.players.filter { it.seat != meSeat }
    return GameResult(
        id = meResultId(game.id),
        result = when (game.winnerSeat) {
            meSeat -> "WIN"
            null -> "DRAW"
            else -> "LOSS"
        },
        opponent = others.joinToString(", ") { it.name }.ifBlank { null },
        playedAt = game.endedAt,
        turns = game.turns.takeIf { it > 0 },
        minutes = game.minutes.takeIf { it > 0 },
        commanders = others.mapNotNull { it.commander }
    )
}
