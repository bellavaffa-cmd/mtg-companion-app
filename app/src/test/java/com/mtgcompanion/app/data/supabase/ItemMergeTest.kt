package com.mtgcompanion.app.data.supabase

import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionEntry
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckCardEntry
import com.mtgcompanion.app.data.GameResult
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The merge rules two devices rely on to agree. The web app has the same checks — see
 * MtgCompanionWeb/src/sync/mergeItems.ts.
 */
class ItemMergeTest {

    private fun card(id: String, quantity: Int = 1) =
        DeckCardEntry(scryfallId = id, name = id, imageUrl = null, quantity = quantity, typeLine = "Creature")

    private fun deck(cards: List<DeckCardEntry>, name: String = "Deck", tags: List<String> = emptyList(), games: List<GameResult> = emptyList()) =
        Deck(id = "d1", name = name, cards = cards, createdAt = 100, tags = tags, gameResults = games)

    private fun names(deck: Deck) = deck.cards.map { "${it.scryfallId}x${it.quantity}" }

    @Test
    fun `keeps a card added on each side`() {
        val merged = ItemMerge.mergeDecks(
            base = deck(listOf(card("a"))),
            mine = deck(listOf(card("a"), card("b"))),
            theirs = deck(listOf(card("a"), card("c"))),
            minePreferred = true
        )
        assertEquals(listOf("ax1", "bx1", "cx1"), names(merged))
    }

    @Test
    fun `a removal beats the other side's edit`() {
        val base = deck(listOf(card("a"), card("b", 2)))
        val removed = deck(listOf(card("a")))
        val edited = deck(listOf(card("a"), card("b", 3)))
        assertEquals(listOf("ax1"), names(ItemMerge.mergeDecks(base, removed, edited, minePreferred = true)))
        assertEquals(listOf("ax1"), names(ItemMerge.mergeDecks(base, edited, removed, minePreferred = true)))
    }

    @Test
    fun `counts changed on both sides add up`() {
        val merged = ItemMerge.mergeDecks(
            base = deck(listOf(card("a", 1))),
            mine = deck(listOf(card("a", 2))),
            theirs = deck(listOf(card("a", 3))),
            minePreferred = true
        )
        assertEquals(listOf("ax4"), names(merged))
    }

    @Test
    fun `the same card added on both sides stays one copy`() {
        val merged = ItemMerge.mergeDecks(deck(emptyList()), deck(listOf(card("a"))), deck(listOf(card("a"))), true)
        assertEquals(listOf("ax1"), names(merged))
    }

    @Test
    fun `a rename on one side is kept, and the newer one wins when both renamed`() {
        val base = deck(emptyList(), name = "Old")
        val mine = deck(emptyList(), name = "Mine")
        val theirs = deck(emptyList(), name = "Theirs")
        assertEquals("Mine", ItemMerge.mergeDecks(base, mine, deck(emptyList(), name = "Old"), false).name)
        assertEquals("Mine", ItemMerge.mergeDecks(base, mine, theirs, minePreferred = true).name)
        assertEquals("Theirs", ItemMerge.mergeDecks(base, mine, theirs, minePreferred = false).name)
    }

    @Test
    fun `tags gain both additions and respect removals`() {
        val merged = ItemMerge.mergeDecks(
            base = deck(emptyList(), tags = listOf("edh", "budget")),
            mine = deck(emptyList(), tags = listOf("edh", "budget", "mine")),
            theirs = deck(emptyList(), tags = listOf("edh", "theirs")),
            minePreferred = true
        )
        assertEquals(listOf("edh", "theirs", "mine"), merged.tags)
    }

    @Test
    fun `games logged on either device are kept and deletions respected`() {
        fun game(id: String, at: Long) = GameResult(id = id, result = "WIN", playedAt = at)
        val merged = ItemMerge.mergeDecks(
            base = deck(emptyList(), games = listOf(game("1", 10))),
            mine = deck(emptyList(), games = listOf(game("1", 10), game("2", 20))),
            theirs = deck(emptyList(), games = listOf(game("3", 30))), // deleted 1, added 3
            minePreferred = true
        )
        assertEquals(listOf("3", "2"), merged.gameResults.map { it.id })
    }

    @Test
    fun `both devices reach the same result`() {
        val base = deck(listOf(card("a", 1), card("b", 1)), name = "Base", tags = listOf("x"))
        val mine = deck(listOf(card("a", 3), card("c", 1)), name = "Mine", tags = listOf("x", "m"))
        val theirs = deck(listOf(card("a", 2)), name = "Theirs", tags = emptyList()) // removed b and the tag
        val onMine = ItemMerge.mergeDecks(base, mine, theirs, minePreferred = true)
        val onTheirs = ItemMerge.mergeDecks(base, theirs, mine, minePreferred = false)
        assertEquals(onMine.cards.map { it.scryfallId }.sorted(), onTheirs.cards.map { it.scryfallId }.sorted())
        assertEquals(onMine.name, onTheirs.name)
        assertEquals(onMine.tags.sorted(), onTheirs.tags.sorted())
    }

    @Test
    fun `two cuts of the same card leave the lower count, not nothing`() {
        val merged = ItemMerge.mergeDecks(
            base = deck(listOf(card("a", 4))),
            mine = deck(listOf(card("a", 1))),
            theirs = deck(listOf(card("a", 2))),
            minePreferred = true
        )
        assertEquals(listOf("ax1"), names(merged))
    }

    @Test
    fun `marking a card replaceable survives a merge`() {
        val base = deck(listOf(card("a")))
        val mine = deck(listOf(card("a").copy(replaceable = true)))
        val merged = ItemMerge.mergeDecks(base, mine, base, minePreferred = false)
        assertEquals(true, merged.cards.single().replaceable)
    }

    @Test
    fun `both devices produce the same card order`() {
        val base = deck(listOf(card("a"), card("b")))
        val mine = deck(listOf(card("a"), card("b"), card("m")))
        val theirs = deck(listOf(card("a"), card("b"), card("t")))
        val onMine = ItemMerge.mergeDecks(base, mine, theirs, minePreferred = true)
        val onTheirs = ItemMerge.mergeDecks(base, theirs, mine, minePreferred = false)
        assertEquals(onMine.cards.map { it.scryfallId }, onTheirs.cards.map { it.scryfallId })
    }

    @Test
    fun `the considering list merges like the deck itself`() {
        val merged = ItemMerge.mergeDecks(
            base = deck(emptyList()).copy(considering = listOf(card("a"))),
            mine = deck(emptyList()).copy(considering = listOf(card("a"), card("b"))),
            theirs = deck(emptyList()).copy(considering = listOf(card("a"), card("c"))),
            minePreferred = true
        )
        assertEquals(listOf("a", "b", "c"), merged.considering.map { it.scryfallId })
    }

    @Test
    fun `binder counts add up per finish and an emptied card goes`() {
        fun entry(id: String, quantity: Int, foil: Int) =
            CollectionEntry(scryfallId = id, name = id, imageUrl = null, quantity = quantity, foilQuantity = foil)
        fun binder(entries: List<CollectionEntry>) = Collection(id = "c1", name = "Binder", entries = entries, createdAt = 1)

        val merged = ItemMerge.mergeCollections(
            base = binder(listOf(entry("a", 1, 0))),
            mine = binder(listOf(entry("a", 2, 0))),
            theirs = binder(listOf(entry("a", 1, 3))),
            minePreferred = true
        )
        assertEquals(listOf(2 to 3), merged.entries.map { it.quantity to it.foilQuantity })

        val emptied = ItemMerge.mergeCollections(
            base = binder(listOf(entry("a", 1, 1))),
            mine = binder(listOf(entry("a", 0, 1))),
            theirs = binder(listOf(entry("a", 1, 0))),
            minePreferred = true
        )
        assertEquals(emptyList<CollectionEntry>(), emptied.entries)
    }
}
