package com.mtgcompanion.app.data

import com.mtgcompanion.app.data.supabase.ItemMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * How a deck's primer, folder, archive flag, companion and categories merge, and survive a save by an
 * app from before them. The web app's deckExtras.test.ts runs the same cases.
 */
class DeckExtrasTest {
    private fun card(id: String, categories: List<String>? = null) = DeckCardEntry(scryfallId = id, name = id, imageUrl = null, categories = categories)
    private fun deck(
        cards: List<DeckCardEntry> = listOf(card("a"), card("b")),
        description: String? = null, folder: String? = null, archived: Boolean? = null, companion: String? = null,
        categoryTargets: Map<String, Int>? = null, name: String = "D"
    ) = Deck("d", name, cards = cards, createdAt = 1, description = description, folder = folder, archived = archived, companion = companion, categoryTargets = categoryTargets)

    @Test
    fun `an older app's save keeps this device's primer, folder, flag, companion and categories`() {
        val mine = deck(
            description = "Go wide", folder = "Modern", archived = false, companion = "Lurrus of the Dream-Den",
            categoryTargets = mapOf("Ramp" to 10), cards = listOf(card("a", listOf("Ramp")), card("b"))
        )
        val old = deck(name = "Renamed", cards = listOf(card("a"), card("b"), card("c")))
        val healed = keepDeckExtrasFromOlderApp(mine, old)
        assertEquals("Renamed", healed.name)
        assertEquals("Go wide", healed.description)
        assertEquals("Modern", healed.folder)
        assertEquals(false, healed.archived)
        assertEquals("Lurrus of the Dream-Den", healed.companion)
        assertEquals(mapOf("Ramp" to 10), healed.categoryTargets)
        assertEquals(listOf(listOf("Ramp"), null, null), healed.cards.map { it.categories })
        // A save that knows them is left as it is — cleared ones included.
        val knowing = deck(description = "", folder = "", categoryTargets = emptyMap())
        assertEquals("", keepDeckExtrasFromOlderApp(mine, knowing).description)
        assertSame(old, keepDeckExtrasFromOlderApp(deck(), old))
    }

    @Test
    fun `each category's target merges on its own`() {
        assertEquals(mapOf("Ramp" to 12, "Removal" to 5), mergeCategoryTargets(mapOf("Ramp" to 10, "Draw" to 8), mapOf("Ramp" to 12, "Draw" to 8), mapOf("Ramp" to 10, "Removal" to 5), false))
        assertNull(mergeCategoryTargets(null, null, null, true))
        assertEquals(mapOf("Ramp" to 11), mergeCategoryTargets(mapOf("Ramp" to 10), mapOf("Ramp" to 11), mapOf("Ramp" to 9), true))
    }

    @Test
    fun `primer, folder and companion go to whoever changed them, categories keep both sides`() {
        val base = deck(description = "v1", folder = "A", categoryTargets = emptyMap(), cards = listOf(card("a", listOf("Ramp")), card("b")))
        val mine = deck(description = "v2", folder = "A", archived = true, categoryTargets = emptyMap(), cards = listOf(card("a", listOf("Ramp", "Draw")), card("b")))
        val theirs = deck(description = "v1", folder = "B", companion = "Yorion, Sky Nomad", categoryTargets = mapOf("Ramp" to 9), cards = listOf(card("a"), card("b", listOf("Removal"))))
        val merged = ItemMerge.mergeDecks(base, mine, theirs, minePreferred = false)
        assertEquals("v2", merged.description)
        assertEquals("B", merged.folder)
        assertEquals(true, merged.archived)
        assertEquals("Yorion, Sky Nomad", merged.companion)
        assertEquals(mapOf("Ramp" to 9), merged.categoryTargets)
        // Draw added here, Ramp taken off there; Removal added there.
        assertEquals(listOf(listOf("Draw"), listOf("Removal")), merged.cards.map { it.categories })
        // Both devices land on the same deck.
        assertEquals(merged, ItemMerge.mergeDecks(base, theirs, mine, minePreferred = true))
        // An older app's side doesn't clear them.
        val old = deck(cards = listOf(card("a"), card("b")))
        val kept = ItemMerge.mergeDecks(base, mine, old, minePreferred = false)
        assertEquals("v2", kept.description)
        assertEquals(listOf(listOf("Ramp", "Draw"), null), kept.cards.map { it.categories })
    }
}
