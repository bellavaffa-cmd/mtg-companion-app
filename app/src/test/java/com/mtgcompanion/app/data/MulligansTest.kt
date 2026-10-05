package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Mulligans in real games. The web app has the same checks — see MtgCompanionWeb/tests/decks/mulligans.test.ts. */
class MulligansTest {

    private fun g(id: String, result: String, mulligans: Int? = null) = GameResult(id = id, result = result, playedAt = 0, mulligans = mulligans)

    @Test
    fun `the hand left after mulligans, the first one free in multiplayer`() {
        assertEquals(listOf(7, 6, 5, 0), listOf(0, 1, 2, 8).map { handSize(it, false) })
        assertEquals(listOf(7, 7, 6), listOf(0, 1, 2).map { handSize(it, true) })
        assertEquals("Kept 7", mulliganText(0, false))
        assertEquals("1 mulligan, to 6", mulliganText(1, false))
        assertEquals("2 mulligans, to 6", mulliganText(2, true))
    }

    @Test
    fun `a mulligan count from the other app is a whole number from 0 to 7`() {
        assertEquals(listOf(3, 0, 7), listOf(3, 0, 7).map { cleanMulligans(it) })
        assertEquals(listOf(null, null, null), listOf(8, -1, null).map { cleanMulligans(it) })
    }

    @Test
    fun `mulligan rate and win rate after a mulligan, over the games that recorded them`() {
        val s = mulliganStats(listOf(g("a", "WIN", 0), g("b", "LOSS", 1), g("c", "WIN", 1), g("d", "WIN", 2), g("e", "LOSS", null), g("f", "LOSS", 0), g("h", "WIN")))
        assertEquals(MulliganStats(recorded = 5, mulliganed = 3, rate = 60, winsAfter = 2, winRateAfter = 66, winRateKept = 50), s)
        assertEquals("Mulligan in 60% of 5 games · won 66% after one", mulliganSummary(s))
        assertNull(mulliganSummary(mulliganStats(listOf(g("a", "WIN")))))
        assertEquals(MulliganStats(1, 0, 0, 0, null, 100), mulliganStats(listOf(g("a", "WIN", 0))))
        assertEquals("Mulligan in 0% of 1 game", mulliganSummary(mulliganStats(listOf(g("a", "WIN", 0)))))
    }
}
