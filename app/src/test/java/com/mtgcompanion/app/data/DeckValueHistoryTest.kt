package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A deck's value over time: worked out, sampled once a day, and how it moved. The web app's deckValueHistory.test.ts runs the same cases. */
class DeckValueHistoryTest {
    private fun card(id: String, quantity: Int) = DeckCardEntry(scryfallId = id, name = id, imageUrl = null, quantity = quantity)

    @Test
    fun `a deck's value - every copy at its price, and nothing when too few were looked up`() {
        val deck = Deck("d", "D", cards = listOf(card("a", 2), card("b", 1), card("c", 97)))
        assertEquals(13.19 to 100, deckValueOf(deck, mapOf("a" to 1.5, "b" to null, "c" to 0.105)))
        assertEquals(12.7 to 100, deckValueOf(deck, mapOf("a" to 1.5, "c" to 0.1)))
        assertNull(deckValueOf(deck, mapOf("c" to 0.1)))
        assertNull(deckValueOf(Deck("e", "E"), emptyMap()))
    }

    @Test
    fun `one point a day per deck`() {
        var h = withDeckPoint(emptyMap(), "d", ValuePoint("2026-10-01", 100.0, 60))
        h = withDeckPoint(h, "d", ValuePoint("2026-10-01", 110.0, 60))
        h = withDeckPoint(h, "d", ValuePoint("2026-10-02", 120.0, 60), 1)
        assertEquals(mapOf("d" to listOf(ValuePoint("2026-10-02", 120.0, 60))), h)
    }

    @Test
    fun `which decks still need a point today`() {
        val decks = listOf(
            Deck("a", "A", cards = listOf(card("x", 1))),
            Deck("b", "B", cards = listOf(card("x", 1))),
            Deck("c", "C", cards = listOf(card("x", 1)), archived = true),
            Deck("e", "E")
        )
        val h = mapOf("a" to listOf(ValuePoint("2026-10-05", 1.0, 1)), "b" to listOf(ValuePoint("2026-10-04", 1.0, 1)))
        assertEquals(listOf("b"), decksDue(h, decks, "2026-10-05").map { it.id })
        assertEquals(setOf("a", "b"), prunedDeckHistory(h + ("gone" to emptyList()), listOf("a", "b")).keys)
    }

    @Test
    fun `how it moved this month`() {
        val points = listOf(
            ValuePoint("2026-08-01", 50.0, 60),
            ValuePoint("2026-09-10", 100.0, 60),
            ValuePoint("2026-09-20", 105.0, 60),
            ValuePoint("2026-10-05", 112.0, 60)
        )
        val change = monthChange(points)!!
        assertEquals("2026-09-10", change.from.date)
        assertEquals(12.0, change.usd, 1e-9)
        assertEquals(12.0, change.percent!!, 1e-9)
        assertNull(monthChange(points.take(1)))
    }
}
