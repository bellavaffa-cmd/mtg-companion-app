package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A pod's games, summed up for the group. The web app has the same checks — see
 * MtgCompanionWeb/tests/decks/podStats.test.ts.
 */
class PodStatsTest {

    private val me = "11111111-1111-1111-1111-111111111111"
    private val bob = "22222222-2222-2222-2222-222222222222"

    private fun seat(name: String, result: String, commander: String? = null, userId: String? = null) =
        PodPlayer(userId, name, commander, null, result)

    private fun game(n: Int, players: List<PodPlayer>, minutes: Int? = null, turns: Int? = null) =
        PodGame("g$n", "c$n", me, n * 1000L, "COMMANDER", turns, minutes, players)

    @Test
    fun membersAreKnownByAccountGuestsByTheirNameInAnyCase() {
        assertEquals("u:$me", playerKey(me, "Me"))
        assertEquals(playerKey(null, "  Carol "), playerKey(null, "carol"))
        val stats = podStats(
            listOf(
                game(1, listOf(seat("Me", "WIN", userId = me), seat("Carol", "LOSS"))),
                // A renamed member is still the same player, under the newest name.
                game(2, listOf(seat("Me again", "LOSS", userId = me), seat(" CAROL", "WIN")))
            )
        )
        assertEquals(listOf(listOf("CAROL", 2, 1, 1), listOf("Me again", 2, 1, 1)), stats.players.map { listOf(it.name, it.games, it.wins, it.losses) })
    }

    @Test
    fun theGroupsTableCommandersLengthAndLatestGames() {
        val stats = podStats(
            listOf(
                game(1, listOf(seat("Me", "WIN", "atraxa ", me), seat("Bob", "LOSS", "Krenko", bob), seat("Carol", "LOSS", "Edgar")), 60, 10),
                game(2, listOf(seat("Me", "LOSS", "Atraxa", me), seat("Bob", "WIN", "Krenko", bob), seat("Carol", "LOSS", "Edgar")), 40, 8),
                game(3, listOf(seat("Me", "LOSS", "Atraxa", me), seat("Bob", "WIN", "Krenko", bob))),
                game(4, listOf(seat("Me", "DRAW", "Omnath", me), seat("Bob", "DRAW", "Krenko", bob), seat("Carol", "DRAW", "Edgar")))
            )
        )
        assertEquals(4, stats.games)
        assertEquals(
            listOf(listOf("Bob", 4, 2, 1, 1, 50), listOf("Me", 4, 1, 2, 1, 25), listOf("Carol", 3, 0, 2, 1, 0)),
            stats.players.map { listOf(it.name, it.games, it.wins, it.losses, it.draws, it.winRate) }
        )
        // Omnath has one game: not ranked. Atraxa's name is matched in any case.
        assertEquals(listOf("Krenko" to 4, "Atraxa" to 3, "Edgar" to 3), stats.mostPlayed.map { it.name to it.games })
        assertEquals(listOf("Krenko" to 50, "Atraxa" to 33, "Edgar" to 0), stats.best.map { it.name to it.winRate })
        assertEquals(50, stats.averageMinutes)
        assertEquals(9, stats.averageTurns)
        assertEquals(listOf("g4", "g3", "g2", "g1"), stats.latest.map { it.id })
    }

    @Test
    fun eachPlayersNemesisNeedsThreeGamesAndALosingRecord() {
        val stats = podStats(
            listOf(
                game(1, listOf(seat("Me", "LOSS", userId = me), seat("Bob", "WIN", userId = bob), seat("Carol", "LOSS"))),
                game(2, listOf(seat("Me", "LOSS", userId = me), seat("Bob", "WIN", userId = bob))),
                game(3, listOf(seat("Me", "WIN", userId = me), seat("Bob", "LOSS", userId = bob), seat("Carol", "LOSS"))),
                game(4, listOf(seat("Me", "LOSS", userId = me), seat("Bob", "WIN", userId = bob)))
            )
        )
        // Me: 1–3 against Bob. Carol only met each of them twice. Bob beats everyone.
        assertEquals(mapOf("Me" to Matchup("Bob", 4, 1, 3)), stats.nemeses.associate { it.player.name to it.nemesis })
    }

    @Test
    fun onlyTenLatestGames() {
        val stats = podStats((1..12).map { game(it, listOf(seat("A", "WIN"), seat("B", "LOSS"))) })
        assertEquals(10, stats.latest.size)
        assertEquals("g12", stats.latest[0].id)
    }

    @Test
    fun theRecorderOrThePodOwnerCanDeleteAGame() {
        val g = game(1, listOf(seat("A", "WIN"), seat("B", "LOSS")))
        assertTrue(canDeletePodGame(g, me, bob))
        assertTrue(canDeletePodGame(g, bob, bob))
        assertFalse(canDeletePodGame(g, bob, me))
    }

    @Test
    fun theGameOnTheUsersOwnDeckAsTheLifeCounterLogsIt() {
        val players = listOf(seat("Me", "LOSS", "Atraxa", me), seat("Bob", "WIN", "Krenko, Mob Boss", bob), seat(" Carol ", "LOSS"))
        assertEquals(
            GameResult("x", "LOSS", "Bob, Carol", playedAt = 5000L, turns = null, minutes = 45, commanders = listOf("Krenko, Mob Boss")),
            deckResultOf(5000L, 0, 45, players, me, "x")
        )
        assertNull(deckResultOf(5000L, 0, 45, players, "33333333-3333-3333-3333-333333333333", "x"))
    }

    @Test
    fun aGameIsCheckedBeforeItIsRecorded() {
        assertEquals("Pick at least two players.", podGameProblem(listOf(seat("A", "WIN"))))
        assertEquals("Every guest needs a name.", podGameProblem(listOf(seat("A", "WIN"), seat(" ", "LOSS"))))
        assertEquals("Someone is in the game twice.", podGameProblem(listOf(seat("A", "WIN"), seat("a", "LOSS"))))
        assertEquals("Only one player can win.", podGameProblem(listOf(seat("A", "WIN"), seat("B", "WIN"))))
        assertNull(podGameProblem(listOf(seat("A", "DRAW"), seat("B", "DRAW"))))
    }

    @Test
    fun readsThePodGamesAnswer() {
        val games = parsePodGames(
            """[{"id":"g1","clientId":"c1","recordedBy":"$me","playedAt":1700000000000,"format":"COMMANDER","turns":null,"minutes":45,
               "players":[{"userId":"$me","name":"Me","commander":"Atraxa","deck":null,"result":"WIN"},
                          {"userId":null,"name":"Carol","commander":null,"deck":"Elves","result":"LOSS"}]}]"""
        )
        assertEquals(
            listOf(PodGame("g1", "c1", me, 1700000000000L, "COMMANDER", null, 45, listOf(PodPlayer(me, "Me", "Atraxa", null, "WIN"), PodPlayer(null, "Carol", null, "Elves", "LOSS")))),
            games
        )
        assertEquals(emptyList<PodGame>(), parsePodGames("[]"))
        assertEquals(emptyList<PodGame>(), parsePodGames("null"))
    }
}
