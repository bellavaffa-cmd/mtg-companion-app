package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Playtesting a deck: London mulligan, play/draw, turns, the battlefield, tokens. */
class PlaytestTest {

    private fun entry(id: String, quantity: Int = 1) = DeckCardEntry(id, id, null, quantity)
    private val deck = Deck(
        "d", "Omnath", commander = entry("omnath"),
        cards = listOf(entry("omnath"), entry("forest", 30), entry("sol"), entry("bolt", 20))
    )

    private fun game(seed: Int = 1, onThePlay: Boolean = true, free: Boolean = false): PlaytestState {
        val (library, zone) = playCards(deck)
        return newGame(library, zone, Random(seed), onThePlay, free)
    }

    @Test
    fun `the commander starts in the command zone and seven are drawn`() {
        val g = game()
        assertEquals(listOf("omnath#cmd"), g.commandZone.map { it.id })
        assertEquals(7, g.hand.size)
        assertEquals(51 - 7, g.library.size)
        assertTrue(g.choosingHand)
        // The same seed shuffles the same way.
        assertEquals(g.hand, game().hand)
    }

    @Test
    fun `a London mulligan draws seven and puts one on the bottom per mulligan`() {
        var g = game().mulligan(Random(2)).mulligan(Random(3))
        assertEquals(7, g.hand.size)
        assertEquals(2, g.toBottom)
        assertFalse(g.canKeep)
        val first = g.hand[0]
        g = g.putOnBottom(first.id)
        assertEquals(first, g.library.last())
        g = g.putOnBottom(g.hand[0].id)
        assertEquals(5, g.hand.size)
        assertEquals(51, g.hand.size + g.library.size)
        assertEquals(g, g.putOnBottom(g.hand[0].id)) // no more asked for
        assertTrue(g.canKeep)
    }

    @Test
    fun `the first mulligan is free when asked`() {
        assertEquals(0, game(free = true).mulligan(Random(2)).toBottom)
        assertEquals(1, game(free = true).mulligan(Random(2)).mulligan(Random(3)).toBottom)
        assertEquals(1, cardsToBottom(1, freeMulligan = false))
    }

    @Test
    fun `no draw on turn one on the play, one on the draw`() {
        val onPlay = game().keep()
        assertEquals(1, onPlay.turn)
        assertEquals(7, onPlay.hand.size)
        val onDraw = game().withOnThePlay(false).keep()
        assertEquals(8, onDraw.hand.size)
        // Play or draw can't change once the game is under way.
        assertEquals(onPlay, onPlay.withOnThePlay(false))
    }

    @Test
    fun `next turn untaps everything and draws`() {
        var g = game().keep()
        val land = g.hand[0].id
        g = g.play(land).toggleTap(land)
        assertTrue(g.battlefield.single().tapped)
        val libraryBefore = g.library.size
        g = g.nextTurn()
        assertEquals(2, g.turn)
        assertFalse(g.battlefield.single().tapped)
        assertEquals(libraryBefore - 1, g.library.size)
        assertEquals(7, g.hand.size)
    }

    @Test
    fun `cards go to the graveyard, a commander to the command zone, a token nowhere`() {
        var g = game().keep()
        val card = g.hand[0].id
        g = g.toGraveyard(card)
        assertEquals(listOf(card), g.graveyard.map { it.id })
        g = g.play("omnath#cmd")
        assertTrue(g.commandZone.isEmpty())
        g = g.toGraveyard("omnath#cmd")
        assertEquals(listOf("omnath#cmd"), g.commandZone.map { it.id })
        g = g.createToken("Elemental", null).createToken("Elemental", null)
        assertEquals(listOf("token#1", "token#2"), g.battlefield.map { it.card.id })
        g = g.toGraveyard("token#1").toHand("token#2")
        assertTrue(g.battlefield.isEmpty())
        assertEquals(1, g.graveyard.size)
    }

    @Test
    fun `reset puts every card back and starts over`() {
        var g = game().keep()
        g = g.play(g.hand[0].id).play("omnath#cmd").createToken("Elemental", null).toGraveyard(g.hand[1].id).nextTurn()
        val fresh = g.reset(Random(5))
        assertEquals(0, fresh.turn)
        assertEquals(51, fresh.library.size + fresh.hand.size)
        assertEquals(listOf("omnath#cmd"), fresh.commandZone.map { it.id })
        assertTrue(fresh.battlefield.isEmpty() && fresh.graveyard.isEmpty())
    }
}
