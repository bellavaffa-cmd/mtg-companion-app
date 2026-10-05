package com.mtgcompanion.app.data.tournament

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.floor

/**
 * Small tournaments: the seeded shuffle, Swiss pairings and byes, pods, results, standings and
 * tiebreakers, and the round clock. The web app has the same checks, with the same seeds and the
 * same expected pairings — see MtgCompanionWeb/tests/tournament/tournament.test.ts.
 */
class TournamentTest {

    private fun event(format: String, n: Int, seed: Int, bestOf: Int = 3, roundCount: Int = suggestedRounds(format, n)) = newTournament(
        id = "e1", name = "Friday", format = format, bestOf = bestOf, roundCount = roundCount, roundMinutes = 50, seed = seed, createdAt = 0,
        players = (1..n).map { Entrant("P$it") }
    )

    /** Plays [start] to the end, each result picked by a generator seeded with seed + 1; one line a round. */
    private fun simulate(start: Tournament): Pair<Tournament, List<String>> {
        var t = start
        val pick = seededRandom(t.seed + 1)
        val lines = mutableListOf<String>()
        while (canPairNext(t)) {
            t = pairNextRound(t)
            currentRound(t)!!.tables.forEachIndexed { i, tb ->
                if (tb.result != null) return@forEachIndexed
                t = if (t.format == EventFormat.SWISS) {
                    val choices = matchChoices(t.bestOf)
                    withResult(t, i, choices[floor(pick() * choices.size).toInt()].result)
                } else {
                    val k = floor(pick() * (tb.players.size + 1)).toInt()
                    withResult(t, i, podResult(tb.players, tb.players.getOrNull(k)))
                }
            }
            lines += currentRound(t)!!.tables.joinToString(" ") { tb -> "${tb.players.joinToString("-")}:${resultText(t, tb)}" }
        }
        lines += standings(t).joinToString(" ") { "${it.id}=${it.points}" }
        return t to lines
    }

    private fun pairsMet(rounds: List<EventRound>) =
        rounds.flatMap { r -> r.tables.filter { it.players.size == 2 }.map { it.players.sorted().joinToString("|") } }

    private fun byesOf(t: Tournament) = t.rounds.flatMap { r -> r.tables.filter { it.players.size == 1 }.map { it.players[0] } }

    @Test
    fun theSeededGeneratorAndShuffleGiveTheSameNumbersInBothApps() {
        val next = seededRandom(42)
        assertEquals(listOf(0.6011037519201636, 0.44829055899754167, 0.8524657934904099), listOf(next(), next(), next()))
        val ids = listOf("p1", "p2", "p3", "p4", "p5", "p6", "p7", "p8")
        assertEquals(listOf("p4", "p1", "p2", "p6", "p5", "p7", "p3", "p8"), seededShuffle(ids, 12345))
        assertEquals(listOf("p2", "p5", "p6", "p1", "p7", "p8", "p3", "p4"), seededShuffle(ids, -7))
        assertEquals(seededShuffle(ids, 12345), seededShuffle(ids, 12345))
        assertEquals(ids, seededShuffle(ids, 9).sorted())
    }

    @Test
    fun roundsAreSuggestedFromThePlayerCountPodsMadeOfFoursAndThrees() {
        assertEquals(listOf(2, 3, 3, 4, 4, 5, 5), listOf(4, 5, 8, 9, 16, 17, 32).map { suggestedRounds(EventFormat.SWISS, it) })
        assertEquals(listOf(2, 2, 3, 3, 4), listOf(4, 8, 9, 16, 32).map { suggestedRounds(EventFormat.PODS, it) })
        assertEquals(listOf(4), podSizes(4))
        assertEquals(listOf(3, 2), podSizes(5))
        assertEquals(listOf(3, 3), podSizes(6))
        assertEquals(listOf(4, 3), podSizes(7))
        assertEquals(listOf(3, 3, 3), podSizes(9))
        assertEquals(listOf(4, 3, 3), podSizes(10))
        assertEquals(listOf(4, 4, 3), podSizes(11))
        assertEquals(listOf(4, 3, 3, 3), podSizes(13))
        for (n in 3..32) assertEquals(n, podSizes(n).sum())
    }

    @Test
    fun anEventNeeds4To32PlayersWithDifferentNames() {
        assertEquals("Add at least 4 players", playersProblem(listOf("A", "B", "C")))
        assertEquals("Two players have the same name", playersProblem(listOf("A", "B", "C", "a ")))
        assertEquals("Every player needs a name", playersProblem(listOf("A", "B", "C", " ")))
        assertEquals("At most 32 players", playersProblem((0 until 33).map { "P$it" }))
        assertNull(playersProblem(listOf("A", "B", "C", "D")))
    }

    @Test
    fun round1PairsTheSeededOrderTheNextRoundWaitsForEveryResult() {
        var t = pairNextRound(event(EventFormat.SWISS, 8, 12345))
        // The same order as seededShuffle(p1…p8, 12345).
        assertEquals(
            listOf(listOf("p4", "p1"), listOf("p2", "p6"), listOf("p5", "p7"), listOf("p3", "p8")),
            currentRound(t)!!.tables.map { it.players }
        )
        assertFalse(canPairNext(t))
        assertSame(t, pairNextRound(t))
        for (i in 0 until 4) t = withResult(t, i, TableResult(listOf(2, 0)))
        assertTrue(canPairNext(t))
        // A result can change until the next round is paired.
        t = withResult(t, 0, TableResult(listOf(1, 2)))
        t = pairNextRound(t)
        assertEquals(2, t.rounds.size)
        // Winners meet winners: p1 (who won the changed result), p2, p5, p3.
        val winners = setOf("p1", "p2", "p5", "p3")
        for (tb in currentRound(t)!!.tables) assertEquals(tb.players[0] in winners, tb.players[1] in winners)
        // Round 1 is settled now.
        val settled = withResult(t, 0, TableResult(listOf(0, 2)))
        assertEquals(t.rounds[0], settled.rounds[0])
    }

    @Test
    fun aFullSwissEventPlaysOutTheSameInBothApps() {
        val (t, lines) = simulate(event(EventFormat.SWISS, 9, 2024, 3, 5))
        assertEquals(
            listOf(
                "p4-p1:1–2 p2-p9:0–2 p3-p7:2–1 p5-p6:2–1 p8:Bye",
                "p9-p1:1–1 p3-p5:Draw p8-p4:2–0 p7-p6:0–2 p2:Bye",
                "p8-p9:0–2 p5-p1:1–2 p3-p2:2–1 p6-p4:1–1 p7:Bye",
                "p9-p3:0–2 p1-p8:2–0 p5-p7:Draw p6-p2:0–2 p4:Bye",
                "p3-p1:Draw p9-p5:2–1 p2-p8:1–2 p4-p7:2–0 p6:Bye",
                "p1=11 p3=11 p9=10 p8=9 p4=7 p6=7 p2=6 p5=5 p7=4"
            ),
            lines
        )
        // Nobody had two byes, nobody met anyone twice.
        val byes = byesOf(t)
        assertEquals(byes.size, byes.toSet().size)
        val met = pairsMet(t.rounds)
        assertEquals(met.size, met.toSet().size)
    }

    @Test
    fun swissAvoidsRematchesAndSecondByesWheneverItCan() {
        for (n in 4..16) {
            for (seed in listOf(1, 2, 3)) {
                val (t, _) = simulate(event(EventFormat.SWISS, n, seed * 101 + n))
                for (r in t.rounds) {
                    val seated = r.tables.flatMap { it.players }
                    assertEquals("everyone plays once (n=$n)", n, seated.size)
                    assertEquals(n, seated.toSet().size)
                    assertEquals(n % 2, r.tables.count { it.players.size == 1 })
                }
                val met = pairsMet(t.rounds)
                assertEquals("no rematch (n=$n, seed=$seed)", met.size, met.toSet().size)
                val byes = byesOf(t)
                assertEquals("no second bye (n=$n)", byes.size, byes.toSet().size)
            }
        }
    }

    @Test
    fun withFourPlayersAndMoreRoundsThanOpponentsARematchIsAllowedRatherThanStopping() {
        val (t, _) = simulate(event(EventFormat.SWISS, 4, 5, 3, 4))
        assertEquals(4, t.rounds.size)
        assertEquals(8, pairsMet(t.rounds).size)
    }

    @Test
    fun aByeIsA20WinAndGoesToTheLowestPlacedPlayerWithoutOne() {
        var t = pairNextRound(event(EventFormat.SWISS, 5, 12345))
        val bye = currentRound(t)!!.tables.first { it.players.size == 1 }
        assertEquals("Bye", resultText(t, bye))
        // The last of the seeded order.
        val order = seededShuffle(listOf("p1", "p2", "p3", "p4", "p5"), 12345)
        assertEquals(order[4], bye.players[0])
        val row = standings(t).first { it.id == bye.players[0] }
        assertEquals(3, row.points)
        assertEquals(1, row.byes)
        assertEquals(1.0, row.gw, 0.0)
        // A bye has no opponent: it doesn't count toward OMW.
        assertEquals(0.0, row.omw, 0.0)
        // Its result can't be changed.
        assertSame(t, withResult(t, currentRound(t)!!.tables.indexOf(bye), null))
        t = withDropped(t, "p1", true)
        assertTrue(t.players.first { it.id == "p1" }.dropped)
    }

    // A known event: A beat B 2–0 and C beat D 2–1; then A beat C 2–1 and B beat D 2–1.
    private fun known(): Tournament {
        val t = event(EventFormat.SWISS, 4, 1, 3, 3)
        return t.copy(
            name = "Known",
            players = t.players.mapIndexed { i, p -> p.copy(name = "ABCD"[i].toString()) },
            rounds = listOf(
                EventRound(1, listOf(EventTable(listOf("p1", "p2"), TableResult(listOf(2, 0))), EventTable(listOf("p3", "p4"), TableResult(listOf(2, 1))))),
                EventRound(2, listOf(EventTable(listOf("p1", "p3"), TableResult(listOf(2, 1))), EventTable(listOf("p2", "p4"), TableResult(listOf(2, 1)))))
            )
        )
    }

    @Test
    fun standingsUseMatchPointsThenOmwGwAndOgwWithThe33PercentFloor() {
        val rows = standings(known())
        assertEquals(listOf("A", "C", "B", "D"), rows.map { it.name })
        val (a, c, b, d) = rows
        assertEquals(listOf(6, 3, 3, 0), listOf(a.points, c.points, b.points, d.points))
        assertEquals(listOf(2, 0, 0), listOf(a.wins, a.losses, a.draws))
        // A's opponents B and C both won half their matches.
        assertEquals(0.5, a.omw, 0.0)
        // C and B tie on OMW — A (100%) and D (0%, floored to 33%) — so GW% decides: 9/18 against 6/15.
        assertEquals((1 + 0.33) / 2, c.omw, 0.0)
        assertEquals(c.omw, b.omw, 0.0)
        assertEquals(0.5, c.gw, 0.0)
        assertEquals(0.4, b.gw, 0.0)
        // D won 2 of 6 games: 33.3%, above the floor.
        assertEquals(6.0 / 18, d.gw, 0.0)
        assertEquals(12.0 / 15, a.gw, 0.0)
        assertEquals((12.0 / 15 + 6.0 / 18) / 2, b.ogw, 0.0)
        assertEquals("66.5%", percentText(c.omw))
        assertEquals("33.3%", percentText(1.0 / 3))
        assertEquals("100.0%", percentText(1.0))
    }

    @Test
    fun theStandingsAsPlainText() {
        val t = known().copy(finished = true)
        assertEquals(
            listOf(
                "Known",
                "1v1 Swiss · best of 3 · 4 players · 2 rounds",
                "Final standings",
                "",
                "1. A — 6 pts · 2–0–0 · OMW 50.0% · GW 80.0% · OGW 45.0%",
                "2. C — 3 pts · 1–1–0 · OMW 66.5% · GW 50.0% · OGW 56.7%",
                "3. B — 3 pts · 1–1–0 · OMW 66.5% · GW 40.0% · OGW 56.7%",
                "4. D — 0 pts · 0–2–0 · OMW 50.0% · GW 33.3% · OGW 45.0%"
            ).joinToString("\n"),
            standingsText(t)
        )
        assertTrue(canFinish(known()))
        assertFalse(canPairNext(t))
    }

    @Test
    fun aDroppedPlayerIsntPairedAgainButStillCountsForTheirOpponents() {
        var t = pairNextRound(event(EventFormat.SWISS, 6, 77))
        for (i in 0 until 3) t = withResult(t, i, TableResult(listOf(2, 1)))
        val loser = currentRound(t)!!.tables[0].players[1]
        val winner = currentRound(t)!!.tables[0].players[0]
        t = withDropped(t, loser, true)
        t = pairNextRound(t)
        val seated = currentRound(t)!!.tables.flatMap { it.players }
        assertFalse(loser in seated)
        // Five left: one of them has a bye.
        assertEquals(1, currentRound(t)!!.tables.count { it.players.size == 1 })
        val rows = standings(t)
        assertTrue(rows.first { it.id == loser }.dropped)
        // The winner's OMW is the dropped player's 0%, floored.
        assertEquals(0.33, rows.first { it.id == winner }.omw, 0.0)
        assertTrue(standingsText(t).contains(" · dropped"))
    }

    @Test
    fun bestOfOneHasItsOwnResultsA11IsADrawnMatch() {
        assertEquals(listOf("1–0", "0–1", "Draw"), matchChoices(1).map { it.label })
        assertEquals(listOf("2–0", "2–1", "1–2", "0–2", "1–1", "Draw"), matchChoices(3).map { it.label })
        var t = pairNextRound(event(EventFormat.SWISS, 4, 3))
        t = withResult(t, 0, TableResult(listOf(1, 1)))
        val (x, y) = currentRound(t)!!.tables[0].players
        val rows = standings(t)
        assertEquals(1, rows.first { it.id == x }.points)
        assertEquals(1, rows.first { it.id == y }.draws)
        assertEquals("1–1", resultText(t, currentRound(t)!!.tables[0]))
    }

    @Test
    fun aFullPodsEventPlaysOutTheSameInBothApps() {
        val (_, lines) = simulate(event(EventFormat.PODS, 11, 99, 1, 4))
        assertEquals(
            listOf(
                "p4-p11-p2-p8:P11 won p7-p10-p1-p6:P10 won p5-p9-p3:P3 won",
                "p11-p10-p3-p5:Draw p9-p4-p7-p2:P7 won p8-p1-p6:P6 won",
                "p10-p9-p8-p3:P10 won p11-p6-p7-p5:P7 won p1-p4-p2:P1 won",
                "p10-p7-p8-p5:P8 won p3-p6-p4-p11:P11 won p1-p9-p2:P1 won",
                "p10=7 p11=7 p7=6 p1=6 p3=4 p6=3 p8=3 p5=1 p4=0 p9=0 p2=0"
            ),
            lines
        )
    }

    @Test
    fun podsScore3ForAWin1EachForADrawTiesBrokenByOpponentsAveragePoints() {
        var t = pairNextRound(event(EventFormat.PODS, 8, 12345))
        val pods = currentRound(t)!!.tables
        assertEquals(listOf(listOf("p4", "p1", "p2", "p6"), listOf("p5", "p7", "p3", "p8")), pods.map { it.players })
        t = withResult(t, 0, podResult(pods[0].players, "p2"))
        t = withResult(t, 1, podResult(pods[1].players, null))
        assertEquals("P2 won", resultText(t, currentRound(t)!!.tables[0]))
        assertEquals("Draw", resultText(t, currentRound(t)!!.tables[1]))
        val rows = standings(t)
        assertEquals("p2", rows[0].id)
        assertEquals(3, rows[0].points)
        // The draw's four have 1 point each and met players averaging 1; p4, p1 and p6 met p2's 3.
        val p5 = rows.first { it.id == "p5" }
        val p4 = rows.first { it.id == "p4" }
        assertEquals(1, p5.points)
        assertEquals(1.0, p5.oppPoints, 0.0)
        assertEquals(0, p4.points)
        assertEquals(1.0, p4.oppPoints, 0.0)
        assertEquals(listOf(1, 1, 1, 1), rows.subList(1, 5).map { it.points })
        assertTrue(standingsText(t).contains("Opp. avg 1.0"))
    }

    @Test
    fun podsGroupByStandingAndKeepAwayFromLastRoundsPodMatesWhereTheyCan() {
        var t = pairNextRound(event(EventFormat.PODS, 16, 4))
        val first = currentRound(t)!!.tables
        first.forEachIndexed { i, pod -> t = withResult(t, i, podResult(pod.players, pod.players[0])) }
        t = pairNextRound(t)
        val second = currentRound(t)!!.tables
        // The four winners sit together.
        assertEquals(first.map { it.players[0] }.sorted(), second[0].players.sorted())
        val repeats = second.sumOf { pod ->
            first.sumOf { old ->
                val shared = pod.players.count { it in old.players }
                shared * (shared - 1) / 2
            }
        }
        assertEquals(0, repeats)
    }

    @Test
    fun theRoundClockRunsPausesAndSurvivesAReloadByItsEndTime() {
        val minutes = 50
        var timer = RoundTimer()
        assertEquals(50 * 60_000L, timeLeft(timer, minutes, 0))
        timer = startTimer(timer, minutes, 1_000)
        assertEquals(1_000 + 50 * 60_000L, timer.endsAt)
        assertEquals(49 * 60_000L, timeLeft(timer, minutes, 61_000))
        timer = pauseTimer(timer, minutes, 61_000)
        assertNull(timer.endsAt)
        assertEquals(49 * 60_000L, timeLeft(timer, minutes, 999_999))
        timer = startTimer(timer, minutes, 100_000)
        assertEquals(-30_000L, timeLeft(timer, minutes, 100_000 + 49 * 60_000L + 30_000))
        assertEquals("49:05", clockText(49 * 60_000L + 5_000))
        assertEquals("-2:30", clockText(-150_000))
        assertEquals("0:01", clockText(400))
        assertEquals("0:00", clockText(0))
    }
}
