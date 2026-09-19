package com.mtgcompanion.app.ui.lifecounter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The games a table keeps, and the table owner's result saved to their deck. The web app has the
 * same checks — see MtgCompanionWeb/tests/lifecounter/tableGames.test.ts.
 */
class TableGamesTest {

    private fun game(id: String, at: Long, winner: Int? = 1) = TableGame(
        id = id, endedAt = at, turns = 9, minutes = 42, winnerSeat = winner,
        players = listOf(
            TableGamePlayer(1, "Me", "Omnath, Locus of Mana", out = if (winner == 1) null else "LIFE", me = true),
            TableGamePlayer(2, "Bob", "Atraxa, Praetors' Voice", out = if (winner == 2) null else "LIFE"),
            TableGamePlayer(3, "Carol", null, out = "POISON")
        )
    )

    @Test
    fun theTableKeepsEachGameOnceNewestFirst() {
        var games = emptyList<TableGame>()
        games = withTableGame(games, game("a", 1))
        games = withTableGame(games, game("b", 2))
        // An undo that changes how "a" ended replaces it.
        games = withTableGame(games, game("a", 3, winner = 2))
        assertEquals(listOf("a", "b"), games.map { it.id })
        assertEquals("Bob", games.first().winner?.name)
        assertEquals(TABLE_GAMES_KEPT, (1..60).fold(emptyList<TableGame>()) { g, i -> withTableGame(g, game("g$i", i.toLong())) }.size)
    }

    @Test
    fun theOwnersSeatIsSavedToTheirDeckOnlyWithoutAPhoneThere() {
        val won = meResultOf(game("a", 5), meSeat = 1, seatLinked = false)!!
        assertEquals("table-a", won.id)
        assertEquals("WIN", won.result)
        assertEquals("Bob, Carol", won.opponent)
        assertEquals(listOf("Atraxa, Praetors' Voice"), won.commanders)
        assertEquals(9, won.turns)
        assertEquals(42, won.minutes)
        assertEquals("LOSS", meResultOf(game("a", 5, winner = 2), 1, false)!!.result)
        assertEquals("DRAW", meResultOf(game("a", 5, winner = null), 1, false)!!.result)
        // The safeguard: a phone that joined the seat saves the game itself.
        assertNull(meResultOf(game("a", 5), meSeat = 1, seatLinked = true))
        assertNull(meResultOf(game("a", 5), meSeat = null, seatLinked = false))
        assertNull(meResultOf(game("a", 5), meSeat = 7, seatLinked = false))
    }
}
