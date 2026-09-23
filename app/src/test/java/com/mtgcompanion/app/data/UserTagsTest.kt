package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** A tag belongs to the copy, so it reads the same wherever that copy is held. */
class UserTagsTest {

    private fun card(id: String, tags: List<String> = emptyList()) =
        DeckCardEntry(scryfallId = id, name = "Lightning Bolt", imageUrl = null, quantity = 1, userTags = tags)

    private fun deck(cards: List<DeckCardEntry>, commander: DeckCardEntry? = null, considering: List<DeckCardEntry> = emptyList()) =
        Deck(id = "d1", name = "Burn", cards = cards, commander = commander, considering = considering)

    private fun binder(entries: List<CollectionEntry>) =
        Collection(id = "c1", name = "Binder", entries = entries)

    @Test
    fun `what the user typed is tidied, not taken literally`() {
        assertEquals(listOf("proxy", "signed"), tidyUserTags(listOf("  proxy ", "Proxy", "signed")))
        assertEquals(listOf("lent to Sam"), tidyUserTags(listOf("lent   to   Sam")))
        assertEquals(emptyList<String>(), tidyUserTags(listOf("", "   ")))
        assertEquals(MAX_USER_TAG_LENGTH, tidyUserTags(listOf("x".repeat(60))).first().length)
    }

    @Test
    fun `a tag put on a copy reads the same from the deck and from the binder`() {
        val d = deck(listOf(card("bolt"))).withUserTags("bolt", listOf("proxy"))
        val c = binder(listOf(CollectionEntry(scryfallId = "bolt", name = "Lightning Bolt", imageUrl = null, quantity = 2)))
            .withUserTags("bolt", listOf("proxy"))
        assertEquals(listOf("proxy"), d.cards[0].userTags)
        assertEquals(listOf("proxy"), c.entries[0].userTags)
        assertEquals(listOf("proxy"), userTagsOf(listOf(d), listOf(c), "bolt"))
    }

    @Test
    fun `it follows the copy, not the card - another printing is untouched`() {
        val d = deck(listOf(card("bolt"), card("other-art"))).withUserTags("bolt", listOf("signed"))
        assertEquals(listOf("signed"), d.cards[0].userTags)
        assertEquals(emptyList<String>(), d.cards[1].userTags)
    }

    @Test
    fun `commanders and cards being considered carry the tag too`() {
        val d = deck(emptyList(), commander = card("bolt"), considering = listOf(card("bolt")))
            .withUserTags("bolt", listOf("proxy"))
        assertEquals(listOf("proxy"), d.commander?.userTags)
        assertEquals(listOf("proxy"), d.considering[0].userTags)
    }

    // What the emulator caught: tagging a card in a binder, then moving it into a deck, lost the tag.
    // A move removes the binder entry and makes a fresh deck entry, which knows nothing about the copy.

    @Test
    fun `a tagged copy moved from a binder into a deck keeps its tag`() {
        val binders = listOf(binder(listOf(CollectionEntry(scryfallId = "bolt", name = "Lightning Bolt", imageUrl = null, quantity = 1, userTags = listOf("proxy")))))
        val ledger = rememberedUserTags(emptyMap(), collections = binders)
        assertEquals(mapOf("bolt" to listOf("proxy")), ledger)

        // The move: out of the binder, into a deck as a brand-new entry with no tags of its own.
        val decks = listOf(deck(listOf(card("bolt")))).withRememberedUserTags(ledger)
        assertEquals(listOf("proxy"), decks[0].cards[0].userTags)
    }

    @Test
    fun `a copy added again later is tagged as it was before`() {
        val ledger = rememberedUserTags(emptyMap(), decks = listOf(deck(listOf(card("bolt", listOf("signed"))))))
        // Removed from everywhere, then added back by a scan or a search.
        val again = listOf(deck(listOf(card("bolt")))).withRememberedUserTags(ledger)
        assertEquals(listOf("signed"), again[0].cards[0].userTags)
    }

    @Test
    fun `taking a tag off is not undone by the copies that still carry it`() {
        val had = rememberedUserTags(emptyMap(), decks = listOf(deck(listOf(card("bolt", listOf("proxy"))))))
        val cleared = had.ledgerWith("bolt", emptyList())
        val decks = listOf(deck(listOf(card("bolt")))).withRememberedUserTags(cleared)
        assertEquals(emptyMap<String, List<String>>(), cleared)
        assertEquals(emptyList<String>(), decks[0].cards[0].userTags)
    }

    @Test
    fun `tags already used are offered again, the most used first`() {
        val d = deck(listOf(card("bolt", listOf("proxy", "signed")), card("x", listOf("proxy"))))
        val c = binder(listOf(CollectionEntry(scryfallId = "y", name = "Y", imageUrl = null, quantity = 1, userTags = listOf("Proxy"))))
        assertEquals(listOf("proxy", "signed"), allUserTags(listOf(d), listOf(c)))
    }
}
