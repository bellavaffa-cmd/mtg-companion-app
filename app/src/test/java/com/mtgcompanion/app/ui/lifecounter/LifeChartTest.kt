package com.mtgcompanion.app.ui.lifecounter

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A finished game's log, its life chart and its recap. The web app has the same checks, on the same
 * game — see MtgCompanionWeb/tests/lifecounter/lifeChart.test.ts.
 */
class LifeChartTest {

    // Three players at 40; the table tracks turns, seat 1 goes first.
    private val entries = listOf(
        LogEntry(1, 1_000, 1, 37),
        LogEntry(2, 60_000, 1, 40, turnStart = true),
        LogEntry(2, 90_000, 1, 30),
        LogEntry(3, 200_000, 1, 40, turnStart = true),
        LogEntry(1, 260_000, 2, 37, turnStart = true),
        LogEntry(3, 300_000, 2, 25),
        LogEntry(3, 320_000, 2, 25),
        LogEntry(2, 400_000, 2, 30, turnStart = true),
        LogEntry(2, 410_000, 2, 36),
        LogEntry(3, 500_000, 2, 25, turnStart = true),
        LogEntry(3, 520_000, 2, 0, out = "LIFE"),
        LogEntry(1, 600_000, 3, 37, turnStart = true),
        LogEntry(2, 650_000, 3, -2, out = "LIFE")
    )
    private val start = listOf(SeatLife(1, 40), SeatLife(2, 40), SeatLife(3, 40))
    private val finalOuts = listOf(1 to null, 2 to "LIFE", 3 to "LIFE")
    private val damage = listOf(DamageTotal(3, 1, 15), DamageTotal(2, 3, 4), DamageTotal(3, 2, 3), DamageTotal(1, 2, 0))
    private val log = buildGameLog(entries, start, finalOuts, damage, 700_000, true, 1)
    private val names = mapOf(1 to "Ana", 2 to "Ben", 3 to "Cat")

    @Test
    fun `the log keeps each change of life, who went out when, and the turns`() {
        assertEquals(listOf("1@1000:37", "2@90000:30", "3@300000:25", "2@410000:36", "3@520000:0", "2@650000:-2"), log.points.map { "${it.seat}@${it.ms}:${it.life}" })
        assertEquals(listOf(Knockout(3, 520_000, 2, "LIFE"), Knockout(2, 650_000, 3, "LIFE")), log.outs)
        assertEquals(
            listOf("1/1@0", "2/1@60000", "3/1@200000", "1/2@260000", "2/2@400000", "3/2@500000", "1/3@600000"),
            log.turns.map { "${it.seat}/${it.turn}@${it.ms}" }
        )
        assertEquals(3, log.damage.size)
    }

    @Test
    fun `without the turn tracker there are no turns, and a new first player starts them over`() {
        assertEquals(emptyList<TurnStart>(), buildGameLog(entries, start, finalOuts, damage, 700_000, false, 1).turns)
        val restarted = buildGameLog(listOf(LogEntry(2, 5_000, 1, 40, turnStart = true, first = true)) + entries.drop(1), start, finalOuts, damage, 700_000, true, 1)
        assertEquals(listOf("2/1@5000", "2/1@60000"), restarted.turns.take(2).map { "${it.seat}/${it.turn}@${it.ms}" })
    }

    @Test
    fun `someone out with nothing in the history to say when went out at the end`() {
        val quiet = buildGameLog(emptyList(), start, listOf(1 to null, 2 to "KILLED", 3 to null), emptyList(), 90_000, false, 1)
        assertEquals(listOf(Knockout(2, 90_000, 1, "KILLED")), quiet.outs)
    }

    @Test
    fun `by round, each player's life at the end of each round, stopping where they went out`() {
        val chart = lifeChart(log, byTurn = true)
        assertEquals(
            listOf("0:40 1:37 2:37 3:37", "0:40 1:30 2:36 3:-2", "0:40 1:40 2:0"),
            chart.series.map { s -> s.points.joinToString(" ") { "${it.x}:${it.life}" } }
        )
        assertEquals(listOf(3L, -2L, 40L), listOf(chart.xMax, chart.yMin.toLong(), chart.yMax.toLong()))
    }

    @Test
    fun `by time, a step at each change, to the end of the game or until they went out`() {
        val chart = lifeChart(log, byTurn = false)
        assertEquals(
            listOf("0:40 1000:37 700000:37", "0:40 90000:30 410000:36 650000:-2", "0:40 300000:25 520000:0"),
            chart.series.map { s -> s.points.joinToString(" ") { "${it.x}:${it.life}" } }
        )
        assertEquals(700_000L, chart.xMax)
    }

    @Test
    fun `the recap, most commander damage, biggest swing, longest turn and knock-outs`() {
        val recap = gameRecap(log)
        assertEquals(DamageLeader(1, 15), recap.mostCommanderDamage)
        assertEquals(Swing(3, 2, -40), recap.biggestSwing)
        assertEquals(LongTurn(2, 1, 140_000), recap.longestTurn)
        assertEquals(listOf(3, 2), recap.knockouts.map { it.seat })
        assertEquals(
            listOf(
                "Ana dealt the most commander damage: 15",
                "Biggest swing: Cat, −40 in round 2",
                "Longest turn: Ben's, round 1 (2:20)",
                "Cat went out in round 2 (out of life)",
                "Ben went out in round 3 (out of life)"
            ),
            recapLines(recap) { names.getValue(it) }
        )
    }

    @Test
    fun `a quiet game has nothing to recap`() {
        val recap = gameRecap(buildGameLog(emptyList(), start, listOf(1 to null, 2 to null, 3 to null), emptyList(), 1_000, false, 1))
        assertEquals(GameRecap(null, null, null, emptyList()), recap)
    }

    @Test
    fun `a long game keeps the last point of each player in each round`() {
        val points = (0 until 400).map { i -> LifePoint((i % 2) + 1, i * 1_000L, i / 20 + 1, 40 - i) }
        val kept = compactPoints(points)
        assertEquals(40, kept.size)
        assertEquals(listOf(18_000L, 19_000L), kept.take(2).map { it.ms })
        assertEquals(10, compactPoints(points.take(10)).size)
    }

    @Test
    fun `lengths of time read as minutes and seconds`() {
        assertEquals("2:20", durationText(140_000))
        assertEquals("1:02:03", durationText(3_723_000))
        assertEquals("0:00", durationText(0))
    }
}
