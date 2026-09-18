package com.mtgcompanion.app.data.supabase

import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckCardEntry
import org.junit.Assert.assertEquals
import org.junit.Test

/** Writing a sync's pulled changes into a library that may have been edited while the sync ran. */
class ApplyRemoteChangesTest {

    private fun card(id: String, quantity: Int = 1) =
        DeckCardEntry(scryfallId = id, name = id, imageUrl = null, quantity = quantity, typeLine = "Creature")

    private fun deck(id: String, vararg cards: String) =
        Deck(id = id, name = id, cards = cards.map { card(it) }, createdAt = 100)

    private fun apply(current: List<Deck>, snapshot: List<Deck>, changes: Map<String, Deck?>) =
        applyRemoteChanges(current, snapshot.associateBy { it.id }, changes, { it.id }) { b, m, t ->
            ItemMerge.mergeDecks(b, m, t, minePreferred = true)
        }

    private fun show(decks: List<Deck>) = decks.map { d -> d.id + ":" + d.cards.joinToString(",") { it.scryfallId } }

    @Test
    fun `untouched items take the remote version`() {
        val before = listOf(deck("d1", "a"), deck("d2", "b"))
        val out = apply(before, before, mapOf("d1" to deck("d1", "a", "y"), "d2" to null, "d3" to deck("d3", "z")))
        assertEquals(listOf("d1:a,y", "d3:z"), show(out))
    }

    @Test
    fun `an edit made during the sync is merged with the remote change, not overwritten`() {
        val before = listOf(deck("d1", "a"))
        val now = listOf(deck("d1", "a", "z"))
        val out = apply(now, before, mapOf("d1" to deck("d1", "a", "y")))
        assertEquals(listOf("d1:a,y,z"), show(out))
    }

    @Test
    fun `other decks edited or created during the sync are left alone`() {
        val before = listOf(deck("d1", "a"), deck("d2", "b"))
        val now = listOf(deck("d1", "a"), deck("d2", "b", "c"), deck("new", "n"))
        val out = apply(now, before, mapOf("d1" to deck("d1", "a", "y")))
        assertEquals(listOf("d1:a,y", "d2:b,c", "new:n"), show(out))
    }

    @Test
    fun `a local edit survives a remote deletion, and a local deletion survives a remote edit`() {
        val before = listOf(deck("d1", "a"), deck("d2", "b"))
        val now = listOf(deck("d1", "a", "z"))
        val out = apply(now, before, mapOf("d1" to null, "d2" to deck("d2", "b", "y")))
        assertEquals(listOf("d1:a,z"), show(out))
    }
}
