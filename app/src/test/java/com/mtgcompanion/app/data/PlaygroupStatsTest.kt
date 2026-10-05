package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Every deck's games together. The web app has the same checks — see
 * MtgCompanionWeb/tests/decks/playgroupStats.test.ts.
 */
class PlaygroupStatsTest {

    private fun game(n: Int, result: String, opponent: String? = null, commanders: List<String> = emptyList(), minutes: Int? = null, turns: Int? = null) =
        GameResult("g$n", result, opponent, playedAt = n * 1000L, turns = turns, minutes = minutes, commanders = commanders)

    private fun deck(name: String, vararg games: GameResult) = Deck(name, name, gameResults = games.toList())

    @Test
    fun theRecordAcrossEveryDeckAndAgainstEachPerson() {
        val stats = playgroupStats(
            listOf(
                deck("Omnath", game(1, "LOSS", "Bob, Carol", listOf("Atraxa, Praetors' Voice"), 60, 10), game(3, "WIN", "Bob"), game(5, "LOSS", "Bob", listOf("Atraxa, Praetors' Voice"))),
                deck("Krenko", game(2, "WIN", "Carol", minutes = 40, turns = 8), game(4, "DRAW", "Dave")),
                deck("Unplayed")
            )
        )
        assertEquals(listOf(5, 2, 2, 1, 40), listOf(stats.games, stats.wins, stats.losses, stats.draws, stats.winRate))
        assertEquals(listOf(Matchup("Bob", 3, 1, 2), Matchup("Carol", 2, 1, 1), Matchup("Dave", 1, 0, 0)), stats.opponents)
        assertEquals(50, stats.averageMinutes)
        assertEquals(9, stats.averageTurns)
        // Newest is a loss after a draw: no run of two.
        assertNull(stats.streak)
        // Games 2 and 3 were wins, in different decks.
        assertEquals(2, stats.longestWinStreak)
        // Omnath has three games and is ranked; Krenko has two; a deck with none isn't listed.
        assertEquals(listOf("Omnath" to 33), stats.ranked.map { it.name to it.winRate })
        assertEquals(listOf("Krenko"), stats.unranked.map { it.name })
        assertEquals("Bob", stats.nemesis?.name)
        // Atraxa only beat the user twice: not enough games.
        assertNull(stats.nemesisCommander)
    }

    @Test
    fun decksRankByWinRateThenGamesPlayed() {
        val stats = playgroupStats(
            listOf(
                deck("A", game(1, "WIN"), game(2, "LOSS"), game(3, "LOSS")),
                deck("B", game(4, "WIN"), game(5, "WIN"), game(6, "LOSS"), game(7, "LOSS"), game(8, "WIN"), game(9, "WIN")),
                deck("C", game(10, "WIN"), game(11, "WIN"), game(12, "LOSS"))
            )
        )
        assertEquals(listOf("B", "C", "A"), stats.ranked.map { it.name })
        assertNull(stats.streak)
    }

    @Test
    fun streaksRunInTheOrderTheGamesWerePlayedWhicheverDeck() {
        val games = listOf(game(5, "WIN"), game(1, "WIN"), game(2, "WIN"), game(3, "DRAW"), game(4, "WIN"), game(6, "WIN"))
        assertEquals(3, longestWinStreak(games))
        val stats = playgroupStats(listOf(deck("A", *games.take(3).toTypedArray()), deck("B", *games.drop(3).toTypedArray())))
        assertEquals("WIN" to 3, stats.streak)
    }

    @Test
    fun aNemesisBeatsYouMoreThanYouBeatThem() {
        assertNull(nemesisOf(listOf(Matchup("Even", 4, 2, 2), Matchup("Few", 2, 0, 2))))
        assertEquals("Dave", nemesisOf(listOf(Matchup("Bob", 4, 1, 3), Matchup("Carol", 6, 1, 5), Matchup("Dave", 3, 0, 3)))?.name)
        // Same share of wins: the one with more losses.
        assertEquals("Carol", nemesisOf(listOf(Matchup("Bob", 3, 0, 3), Matchup("Carol", 5, 0, 5)))?.name)
    }

    @Test
    fun noGamesAtAll() {
        val stats = playgroupStats(listOf(deck("A")))
        assertEquals(listOf(0, 0, 0, 0, 0), listOf(stats.games, stats.winRate, stats.longestWinStreak, stats.ranked.size, stats.unranked.size))
        assertNull(stats.nemesis)
        assertNull(stats.averageMinutes)
    }

    @Test
    fun mulligansAcrossEveryDeckOverTheGamesThatRecordedThem() {
        fun m(n: Int, result: String, mulligans: Int?) = game(n, result).copy(mulligans = mulligans)
        val stats = playgroupStats(listOf(deck("Omnath", m(1, "WIN", 1), m(2, "LOSS", 0)), deck("Krenko", m(3, "LOSS", 2), game(4, "WIN"))))
        assertEquals(MulliganStats(recorded = 3, mulliganed = 2, rate = 66, winsAfter = 1, winRateAfter = 50, winRateKept = 0), stats.mulligans)
    }
}
