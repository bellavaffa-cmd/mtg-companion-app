package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A deck's games summed up for its Stats. The web app has the same checks — see
 * MtgCompanionWeb/tests/decks/gameStats.test.ts.
 */
class GameStatsTest {

    private fun game(n: Int, result: String, opponent: String? = null, commanders: List<String> = emptyList(), minutes: Int? = null, turns: Int? = null) =
        GameResult("g$n", result, opponent, playedAt = n * 1000L, turns = turns, minutes = minutes, commanders = commanders)

    @Test
    fun theRecordFormAndLengthOfADecksGames() {
        val stats = gameStats(
            listOf(
                game(1, "LOSS", "Bob, Carol", listOf("Atraxa, Praetors' Voice", "Krenko, Mob Boss"), minutes = 60, turns = 10),
                game(2, "WIN", "Bob", listOf("atraxa, praetors' voice"), minutes = 40, turns = 8),
                game(3, "DRAW"),
                game(4, "WIN", "Carol", listOf("Krenko, Mob Boss")),
                game(5, "WIN", "Bob ,  Dave")
            )
        )
        assertEquals(5, stats.games)
        assertEquals(60, stats.winRate)
        assertEquals(listOf("WIN", "WIN", "DRAW", "WIN", "LOSS"), stats.recent)
        assertEquals("WIN" to 2, stats.streak)
        // Only the games that kept time count toward the averages.
        assertEquals(50, stats.averageMinutes)
        assertEquals(9, stats.averageTurns)
        // Most-faced first, names matched whatever their case; ties by name.
        assertEquals(
            listOf(Matchup("Atraxa, Praetors' Voice", 2, 1, 1), Matchup("Krenko, Mob Boss", 2, 1, 1)),
            stats.commanders
        )
        assertEquals(listOf(Matchup("Bob", 3, 2, 1), Matchup("Carol", 2, 1, 1), Matchup("Dave", 1, 1, 0)), stats.opponents)
        assertEquals("2–1", stats.opponents.first().record())
    }

    @Test
    fun noGamesAndOneGame() {
        val none = gameStats(emptyList())
        assertEquals(0, none.winRate)
        assertNull(none.averageMinutes)
        assertNull(gameStats(listOf(game(1, "WIN"))).streak)
    }
}
