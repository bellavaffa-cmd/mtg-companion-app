package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Opening-hand odds, against figures worked out separately (Python's math.comb). The web app has
 * the same checks — see MtgCompanionWeb/tests/decks/handOdds.test.ts.
 */
class HandOddsTest {

    private fun near(expected: Double, actual: Double) = assertEquals(expected, actual, 0.0001)

    @Test
    fun aCommanderDeck() {
        // 100 cards less the commander: 37 lands, 10 ramp. Multiplayer draws on turn 1.
        val odds = handOdds(99, 37, 10, drawsOnTurnOne = true)!!
        listOf(0.0330, 0.1528, 0.2895, 0.2912, 0.1678, 0.0554, 0.0097, 0.0007).zip(odds.landSpread).forEach { (e, a) -> near(e, a) }
        near(1.0, odds.landSpread.sum())
        near(0.7484, odds.keepable)
        near(0.9367, odds.keepableWithMulligan)
        near(0.5372, odds.rampInHand)
        assertEquals(listOf(3, 4), odds.landDrops.map { it.first })
        near(0.8004, odds.landDrops[0].second)
        near(0.6488, odds.landDrops[1].second)
        near(0.5670, odds.landsAndRampByTurn2)
    }

    @Test
    fun aSixtyCardDeckOnThePlay() {
        val odds = handOdds(60, 24, 4, drawsOnTurnOne = false)!!
        near(0.7887, odds.landDrops[0].second)
        near(0.3904, odds.landsAndRampByTurn2)
    }

    @Test
    fun tooFewCardsAndHowChancesRead() {
        assertNull(handOdds(10, 4, 0, drawsOnTurnOne = true))
        assertEquals("<1%", oddsPercent(0.0007))
        assertEquals(">99%", oddsPercent(0.998))
        assertEquals("75%", oddsPercent(0.7484))
    }
}
