package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Test hands by the thousand. The web app's tests/decks/handSim.test.ts has the same cases and, with
 * the same seed, the very same numbers.
 */
class HandSimTest {

    private fun cards(n: Int, card: SimCard) = List(n) { card }

    /** 60 cards: 24 lands, 8 two-drops, 28 three-drops. */
    private val sixty = cards(24, SimCard(true)) + cards(8, SimCard(false, 2.0)) + cards(28, SimCard(false, 3.0))

    @Test
    fun `the seeded random numbers repeat, and match the web app`() {
        val r = SeededRandom(7)
        val first = listOf(r.next(), r.next(), r.next())
        val again = SeededRandom(7)
        assertEquals(first, listOf(again.next(), again.next(), again.next()))
        assertEquals(listOf(11705L, 61958L, 976908L), first.map { (it * 1e6).roundToLong() })
    }

    @Test
    fun `10,000 hands land close to the exact odds`() {
        val s = handStats(sixty)!!
        assertEquals(SIM_HANDS, s.hands)
        assertEquals(60, s.library)
        assertEquals(24, s.lands)
        assertEquals(8, s.twoDrops)
        val exact = handOdds(60, 24, 0, false)!!
        assertTrue("${s.twoToFourLands} vs ${exact.keepable}", abs(s.twoToFourLands - exact.keepable) < 0.02)
        assertTrue(abs(s.averageLands - 7.0 * 24 / 60) < 0.05)
        assertEquals(1.0, s.landsInOpener.sum(), 1e-9)
        // On the draw sees a card more, so it hits its land drops at least as often.
        s.landDrops.forEach { (_, d) -> assertTrue(d.onTheDraw >= d.onThePlay - 0.02) }
        assertTrue(abs(s.landDrops[2].second.onThePlay - exact.landDrops[0].second) < 0.02)
        assertTrue(s.twoDropOnTurn2.onTheDraw >= s.twoDropOnTurn2.onThePlay)
        assertTrue("${s.mulliganRate}", s.mulliganRate > 0.05 && s.mulliganRate < 0.2)
    }

    @Test
    fun `the same seed gives the same numbers, and the web app gets them too`() {
        val a = handStats(sixty, 2000, 42)!!
        assertEquals(a, handStats(sixty, 2000, 42))
        val got = listOf(a.twoToFourLands, a.averageLands, a.mulliganRate, a.twoDropOnTurn2.onThePlay, a.landDrops[3].second.onTheDraw)
        listOf(0.783, 2.861, 0.1405, 0.6455, 0.751).zip(got).forEach { (want, have) -> assertEquals(want, have, 1e-12) }
    }

    @Test
    fun `a deck of only lands always has its land drops, and never a two-drop`() {
        val s = handStats(cards(40, SimCard(true)), 500)!!
        assertEquals(1.0, s.landsInOpener[7], 0.0)
        assertEquals(1.0, s.mulliganRate, 0.0)
        assertTrue(s.landDrops.all { (_, d) -> d.onThePlay == 1.0 && d.onTheDraw == 1.0 })
        assertEquals(0.0, s.twoDropOnTurn2.onThePlay, 0.0)
    }

    @Test
    fun `too small a library has no numbers`() {
        assertNull(handStats(cards(10, SimCard(true))))
    }

    @Test
    fun `a Commander deck - 99 in the library, the commander in the command zone`() {
        fun e(name: String, quantity: Int, typeLine: String) = DeckCardEntry(name, name, null, quantity, typeLine = typeLine)
        val commander = e("Omnath", 1, "Legendary Creature — Elemental")
        val deck = Deck("d", "Omnath", commander = commander, cards = listOf(commander, e("Forest", 38, "Basic Land — Forest"), e("Bear", 61, "Creature — Bear")))
        val lib = simLibrary(deck, { it.typeLine }, { if (it.name == "Bear") 2.0 else null })
        assertEquals(99, lib.size)
        assertEquals(38, lib.count { it.land })
        assertEquals(61, lib.count { it.manaValue == 2.0 })
    }

    @Test
    fun `a London mulligan - seven again, then one to the bottom per mulligan`() {
        val library = List(60) { PlayCard("c#$it", "c$it", null) }
        var g = newGame(library, emptyList(), kotlin.random.Random(3)).mulligan(kotlin.random.Random(4)).mulligan(kotlin.random.Random(5))
        assertEquals(7, g.hand.size)
        assertEquals(2, g.toBottom)
        g = g.putOnBottom(g.hand[0].id)
        g = g.putOnBottom(g.hand[0].id)
        assertEquals(5, g.hand.size)
        assertEquals(55, g.library.size)
        g = g.keep().nextTurn()
        assertEquals(6, g.hand.size)
        assertEquals(0, cardsToBottom(1, true))
    }
}
