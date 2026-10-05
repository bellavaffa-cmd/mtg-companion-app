package com.mtgcompanion.app.ui.lifecounter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Game night's pods, suggestions and results. The web app has the same checks, with the same
 * numbers — see MtgCompanionWeb/tests/lifecounter/gameNight.test.ts.
 */
class GameNightTest {

    private fun player(id: String, bracket: Int?, kind: NightPlayerKind = NightPlayerKind.GUEST) =
        NightPlayer(id = id, name = id, kind = kind, bracket = bracket)

    private fun night(players: List<NightPlayer>, pods: List<NightPod> = emptyList()) =
        GameNight(id = "n", createdAt = 0, players = players, pods = pods)

    private fun pod(id: String, vararg ids: String, startedAt: Long? = null, winnerId: String? = null) =
        NightPod(id, ids.toList(), startedAt, winnerId)

    @Test
    fun theSeededNumbersAreTheSameEveryTimeAndOnTheWeb() {
        val r = seededRandom(42)
        assertEquals(listOf(601, 448, 852), List(3) { (r() * 1000).toInt() })
        assertEquals(601, (seededRandom(42)() * 1000).toInt())
    }

    @Test
    fun podsAreThreeOrFourForCommanderPairsForOneVsOne() {
        assertEquals(emptyList<Int>(), podSizes(0, NightFormat.COMMANDER))
        assertEquals(listOf(4), podSizes(4, NightFormat.COMMANDER))
        assertEquals(listOf(5), podSizes(5, NightFormat.COMMANDER))
        assertEquals(listOf(3, 3), podSizes(6, NightFormat.COMMANDER))
        assertEquals(listOf(4, 3), podSizes(7, NightFormat.COMMANDER))
        assertEquals(listOf(4, 4), podSizes(8, NightFormat.COMMANDER))
        assertEquals(listOf(3, 3, 3), podSizes(9, NightFormat.COMMANDER))
        assertEquals(listOf(4, 4, 3), podSizes(11, NightFormat.COMMANDER))
        assertEquals(listOf(2), podSizes(2, NightFormat.DUEL))
        assertEquals(listOf(3), podSizes(3, NightFormat.DUEL))
        assertEquals(listOf(2, 2, 2), podSizes(6, NightFormat.DUEL))
        assertEquals(listOf(2, 2, 3), podSizes(7, NightFormat.DUEL))
    }

    @Test
    fun playersCloseInPowerShareAPod() {
        val players = listOf(player("a", 4), player("b", 2), player("c", 4), player("d", 2), player("e", 4), player("f", 2), player("g", 4), player("h", 2))
        assertEquals(listOf(listOf("a", "c", "e", "g"), listOf("b", "d", "f", "h")), splitPods(players, NightFormat.COMMANDER, 7))
        // Not knowing a bracket counts as the middle.
        val mixed = splitPods(listOf(player("a", 1), player("b", null), player("c", 5), player("d", 1), player("e", 5), player("f", 3)), NightFormat.DUEL, 3)
        assertEquals(listOf(listOf("a", "d"), listOf("b", "f"), listOf("c", "e")), mixed)
        assertEquals(0.0, powerSpread(listOf(2, 2, 2)), 0.0)
        assertEquals(2.0, powerSpread(listOf(1, 3)), 0.0)
    }

    @Test
    fun theSameSeedGivesTheSamePodsAsOnTheWeb() {
        val players = List(8) { player("p$it", null) }
        val one = splitPods(players, NightFormat.COMMANDER, 11)
        assertEquals(one, splitPods(players, NightFormat.COMMANDER, 11))
        assertEquals(listOf(listOf("p0", "p1", "p5", "p7"), listOf("p2", "p3", "p4", "p6")), one)
        assertEquals(listOf(listOf("p0", "p2", "p3", "p4"), listOf("p1", "p5", "p6", "p7")), splitPods(players, NightFormat.COMMANDER, 12))
    }

    @Test
    fun lastNightsPairingsArentRepeatedWhenTheyNeedntBe() {
        val players = listOf(player("a", 3), player("b", 3), player("c", 3), player("d", 3))
        val previous = pairingsOf(night(players, listOf(pod("1", "a", "b"), pod("2", "c", "d"))))
        assertTrue("g:a|g:b" in previous)
        for (seed in 1..5) {
            val pods = splitPods(players, NightFormat.DUEL, seed, previous)
            assertFalse("seed $seed: $pods", pods.any { it == listOf("a", "b") || it == listOf("c", "d") })
        }
        // Power still comes first: two 1s and two 5s pair up as before rather than 1 against 5.
        val uneven = listOf(player("a", 1), player("b", 1), player("c", 5), player("d", 5))
        val before = pairingsOf(night(uneven, listOf(pod("1", "a", "b"), pod("2", "c", "d"))))
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), splitPods(uneven, NightFormat.DUEL, 1, before))
    }

    @Test
    fun aPlayerMovesBetweenPodsAndAnEmptiedPodGoes() {
        val pods = listOf(pod("1", "a", "b", "c", startedAt = 5, winnerId = "a"), pod("2", "d", "e", "f"))
        val moved = movePlayer(pods, "c", 1, "new")
        assertEquals(listOf(listOf("a", "b"), listOf("d", "e", "f", "c")), moved.map { it.playerIds })
        assertNull(moved[0].winnerId)
        assertEquals(listOf("1", "2", "new"), movePlayer(pods, "c", 2, "new").map { it.id })
        assertEquals(listOf(listOf("b", "a")), movePlayer(listOf(pod("1", "a"), pod("2", "b")), "a", 1, "x").map { it.playerIds })
        val gone = withoutPlayer(night(listOf(player("a", 1), player("b", 1)), listOf(pod("1", "a", "b", winnerId = "a"))), "a")
        assertEquals(listOf(listOf("b")), gone.pods.map { it.playerIds })
        assertNull(gone.pods[0].winnerId)
    }

    @Test
    fun withPodsNumbersItsPodsFromTheSeed() {
        val n = withPods(night(listOf(player("a", 2), player("b", 2), player("c", 2))), 9, null)
        assertEquals(listOf("9-1"), n.pods.map { it.id })
        assertEquals(9, n.seed)
    }

    @Test
    fun aDeckIsSuggestedCloseToTheTableThenTheOnePlayedLongestAgo() {
        val decks = listOf(
            DeckChoice("1", "Atraxa", "COMMANDER", 4, 100),
            DeckChoice("2", "Omnath", "COMMANDER", 2, 300),
            DeckChoice("3", "Krenko", "COMMANDER", 2, 200),
            DeckChoice("4", "Burn", "MODERN", null, 0)
        )
        assertEquals(listOf("3", "2", "1"), suggestedDecks(decks, NightFormat.COMMANDER, listOf(2, 2, null)).map { it.id })
        assertEquals(listOf("1", "3", "2"), suggestedDecks(decks, NightFormat.COMMANDER, listOf(null)).map { it.id })
        assertEquals(listOf("4"), suggestedDecks(decks, NightFormat.DUEL, emptyList()).map { it.id })
        val ordered = suggestedDecks(decks, NightFormat.COMMANDER, listOf(2))
        assertEquals("3", nextSuggestion(ordered, null)?.id)
        assertEquals("2", nextSuggestion(ordered, "3")?.id)
        assertEquals("3", nextSuggestion(ordered, "1")?.id)
    }

    @Test
    fun aPodsTableIsSeededWithItsPlayersAndTheUsersDeck() {
        val players = listOf(
            player("me", 3, NightPlayerKind.ME).copy(name = "Sam", deckId = "deck-1", commander = "Krenko, Mob Boss"),
            player("b", 2), player("c", 2)
        )
        assertEquals(
            TableSeed(listOf(SeedPlayer("b", null), SeedPlayer("Sam", "Krenko, Mob Boss"), SeedPlayer("c", null)), meSeat = 2, meDeckId = "deck-1"),
            tableSeedOf(pod("1", "b", "me", "c"), players)
        )
        assertNull(tableSeedOf(pod("2", "b", "c"), players).meSeat)
        assertEquals("3-top", layoutIdFor(3, "4-grid"))
        assertEquals("4-ends", layoutIdFor(4, "4-ends"))
        assertEquals("2-facing", layoutIdFor(2, "4-grid"))
    }

    @Test
    fun aPodsWinnerComesFromItsLifeCounterGameOrTheTap() {
        val players = listOf(player("Ann", 2), player("Bo", 2), player("Cy", 2))
        val started = pod("1", "Ann", "Bo", "Cy", startedAt = 1000)
        fun game(endedAt: Long, winnerSeat: Int?, names: List<String> = listOf("ann", "Bo", "Cy")) = TableGame(
            id = "g$endedAt", endedAt = endedAt, turns = 5, minutes = 30, winnerSeat = winnerSeat,
            players = names.mapIndexed { i, name -> TableGamePlayer(i + 1, name) }
        )
        assertNull(podWinner(started, players, emptyList()))
        // Before the pod started, or other people: not this pod's game.
        assertNull(podWinner(started, players, listOf(game(500, 1), game(2000, 1, listOf("Ann", "Bo", "Di")))))
        assertEquals(PodResult("Ann", fromTable = true), podWinner(started, players, listOf(game(2000, 2), game(3000, 1))))
        assertEquals(PodResult(null, fromTable = true), podWinner(started, players, listOf(game(2000, null))))
        assertEquals(PodResult("Cy", fromTable = false), podWinner(started.copy(winnerId = "Cy"), players, emptyList()))
    }

    @Test
    fun aWinnerTappedOnGameNightGoesOntoTheUsersDeck() {
        val players = listOf(
            player("me", 3, NightPlayerKind.ME).copy(name = "Sam", deckId = "deck-1"),
            player("b", 2).copy(commander = "Krenko, Mob Boss"), player("c", 2)
        )
        val (deckId, result) = nightResultOf("n1", pod("p1", "me", "b", "c"), players, "me", 50)!!
        assertEquals("deck-1", deckId)
        assertEquals("night-n1-p1", result.id)
        assertEquals("WIN", result.result)
        assertEquals("b, c", result.opponent)
        assertEquals(50L, result.playedAt)
        assertEquals(listOf("Krenko, Mob Boss"), result.commanders)
        assertEquals("LOSS", nightResultOf("n1", pod("p1", "me", "b"), players, "b", 50)?.second?.result)
        assertNull(nightResultOf("n1", pod("p2", "b", "c"), players, "b", 50))
    }
}
