package com.mtgcompanion.app.data.tournament

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The top 8 / 4 / 2 after the Swiss, and the pods' final table. The web app has the same checks, with
 * the same seeds and the same brackets — see MtgCompanionWeb/tests/tournament/playoff.test.ts.
 */
class PlayoffTest {

    private fun num(id: String) = id.drop(1).toInt()

    /** An event of [n] players, every round played with the lower-numbered player winning (2–0, or the pod). */
    private fun played(format: String, n: Int, seed: Int): Tournament {
        var t = newTournament(
            id = "e1", name = "Friday", format = format, bestOf = 3, roundCount = if (format == EventFormat.SWISS) 3 else 2,
            roundMinutes = 50, seed = seed, createdAt = 0, players = (1..n).map { Entrant("P$it") }
        )
        while (canPairNext(t)) {
            t = pairNextRound(t)
            currentRound(t)!!.tables.forEachIndexed { i, tb ->
                if (tb.result != null) return@forEachIndexed
                t = if (format == EventFormat.SWISS) {
                    withResult(t, i, if (num(tb.players[0]) < num(tb.players[1])) TableResult(listOf(2, 0), 0) else TableResult(listOf(0, 2), 0))
                } else {
                    withResult(t, i, podResult(tb.players, tb.players.minByOrNull { num(it) }))
                }
            }
        }
        return t
    }

    private fun pairs(round: List<PlayoffMatch>) = round.map { m -> m.players.joinToString("v") }

    @Test
    fun `seeds meet in bracket order, 1 v 8, 4 v 5, 2 v 7, 3 v 6`() {
        assertEquals(listOf(1, 8, 4, 5, 2, 7, 3, 6), seedOrder(8))
        assertEquals(listOf(1, 4, 2, 3), seedOrder(4))
        assertEquals(listOf(1, 2), seedOrder(2))
    }

    @Test
    fun `the cut is offered once the Swiss is played, for tops that fit the players still in`() {
        val t = played(EventFormat.SWISS, 8, 7)
        assertTrue(canCut(t))
        assertEquals(listOf(8, 4, 2), cutSizes(t))
        assertEquals(listOf(4, 2), cutSizes(withDropped(withDropped(withDropped(t, "p1", true), "p2", true), "p3", true)))
        val early = pairNextRound(newTournament("e", "E", EventFormat.SWISS, 1, 3, 50, 1, 0, listOf("A", "B", "C", "D").map { Entrant(it) }))
        assertFalse(canCut(early))
    }

    @Test
    fun `a top 8 seeded from the standings, played through to a champion`() {
        val swiss = played(EventFormat.SWISS, 8, 7)
        assertEquals(listOf("p1", "p2", "p4", "p3", "p5", "p6", "p7", "p8"), standings(swiss).map { it.id })
        var t = startPlayoff(swiss, 8)
        assertTrue(t.finished)
        assertFalse(canCut(t))
        val p = t.playoff!!
        assertEquals(PlayoffKind.BRACKET, p.kind)
        assertEquals(listOf(4, 2, 1), p.rounds.map { it.size })
        assertEquals(listOf("p1vp8", "p3vp5", "p2vp7", "p4vp6"), pairs(p.rounds[0]))
        assertEquals(listOf("Quarterfinals", "Semifinals", "Final"), (0..2).map { playoffRoundName(p, it) })
        assertFalse("a semifinal waits for its players", playoffEditable(p, 1, 0))

        // The higher seed wins every quarterfinal.
        for (i in 0 until 4) t = withPlayoffResult(t, 0, i, TableResult(listOf(2, 1), 0))
        assertEquals(listOf("p1vp3", "p2vp4"), pairs(t.playoff!!.rounds[1]))
        // A quarterfinal cleared before its semifinal is played takes its winner back out.
        val cleared = withPlayoffResult(t, 0, 1, null)
        assertEquals(listOf("p1", null), cleared.playoff!!.rounds[1][0].players)
        // The underdog takes the first semifinal; then that quarterfinal is settled.
        t = withPlayoffResult(t, 1, 0, TableResult(listOf(0, 2), 0))
        assertSame(t, withPlayoffResult(t, 0, 0, TableResult(listOf(0, 2), 0)))
        t = withPlayoffResult(t, 1, 1, TableResult(listOf(2, 0), 0))
        assertEquals(listOf("p3", "p2"), t.playoff!!.rounds[2][0].players)
        assertNull(playoffChampion(t))
        assertEquals("Top 8", playoffStatus(t) { it.uppercase() })
        t = withPlayoffResult(t, 2, 0, TableResult(listOf(1, 2), 0))
        assertEquals("p2", playoffChampion(t))
        assertEquals("Champion: P2", playoffStatus(t) { it.uppercase() })
    }

    @Test
    fun `a top 2 is one final, a top 4 two rounds`() {
        val swiss = played(EventFormat.SWISS, 6, 3)
        val seeds = standings(swiss).map { it.id }
        val top2 = startPlayoff(swiss, 2).playoff!!
        assertEquals(listOf(listOf("${seeds[0]}v${seeds[1]}")), top2.rounds.map { pairs(it) })
        val top4 = startPlayoff(swiss, 4).playoff!!
        assertEquals(listOf("${seeds[0]}v${seeds[3]}", "${seeds[1]}v${seeds[2]}"), pairs(top4.rounds[0]))
        assertSame("not enough players for a top 8", swiss, startPlayoff(swiss, 8))
    }

    @Test
    fun `bracket matches have a winner, no draws`() {
        assertEquals(listOf("1–0", "0–1"), playoffChoices(1).map { it.label })
        assertEquals(listOf("2–0", "2–1", "1–2", "0–2"), playoffChoices(3).map { it.label })
        assertNull(matchWinner(PlayoffMatch(listOf("a", "b"), TableResult(listOf(1, 1), 0))))
        assertNull(matchWinner(PlayoffMatch(listOf("a", "b"), null)))
    }

    @Test
    fun `pods end with a final table, the top 4 play one game`() {
        val pods = played(EventFormat.PODS, 9, 11)
        assertEquals(listOf(4), cutSizes(pods))
        var t = startPlayoff(pods, 4)
        val p = t.playoff!!
        assertEquals(PlayoffKind.FINAL_TABLE, p.kind)
        assertEquals(standings(pods).take(4).map { it.id }, p.rounds[0][0].players)
        assertEquals("Final table", playoffRoundName(p, 0))
        val third = p.rounds[0][0].players[2]!!
        t = withPlayoffResult(t, 0, 0, podResult(p.rounds[0][0].players.filterNotNull(), third))
        assertEquals(third, playoffChampion(t))
    }

    @Test
    fun `an event saved before playoffs has none`() {
        val old = played(EventFormat.SWISS, 4, 5)
        assertNull(old.playoff)
        assertNull(playoffChampion(old))
        assertNull(playoffStatus(old) { it })
        assertTrue(canCut(old))
    }
}
