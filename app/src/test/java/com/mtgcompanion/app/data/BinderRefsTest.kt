package com.mtgcompanion.app.data

import com.mtgcompanion.app.data.supabase.ItemMerge
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which binders a deck card's copies are in (DeckCardEntry.binders, BinderRefs.kt): through the sync's
 * merge, an older app's save, backups and every edit that rebuilds a deck card. The merge cases in
 * binderRefMergeVectors.json are the web app's too (tests/sync/binderRefs.test.ts), byte for byte.
 */
class BinderRefsTest {

    private val v: JSONObject = JSONObject(
        javaClass.classLoader!!.getResourceAsStream("binderRefMergeVectors.json")!!.bufferedReader().use { it.readText() }
    )

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun refsOf(o: JSONObject): List<BinderRef>? =
        if (!o.has("binders") || o.isNull("binders")) null
        else o.getJSONArray("binders").objects().map { BinderRef(it.getString("id"), it.getInt("qty")) }

    private fun cardsOf(a: JSONArray): List<DeckCardEntry> = a.objects().map { DeckCardEntry(it.getString("id"), it.getString("id"), null, quantity = it.getInt("qty"), binders = refsOf(it)) }
    private fun deckOf(a: JSONArray) = Deck("d", "Deck", cards = cardsOf(a), createdAt = 1)
    private fun shown(d: Deck): List<Triple<String, Int, List<BinderRef>?>> = d.cards.map { Triple(it.scryfallId, it.quantity, it.binders) }

    @Test
    fun `the shared merge vectors`() {
        for (c in v.getJSONArray("merge").objects()) {
            val name = c.getString("name")
            val base = deckOf(c.getJSONArray("base"))
            val mine = deckOf(c.getJSONArray("mine"))
            val theirs = deckOf(c.getJSONArray("theirs"))
            val p = c.getBoolean("minePreferred")
            val expect = shown(deckOf(c.getJSONArray("expect")))
            assertEquals(name, expect, shown(ItemMerge.mergeDecks(base, mine, theirs, p)))
            // The other device runs the same merge from its side and lands on the same cards.
            assertEquals("$name (the other way round)", expect, shown(ItemMerge.mergeDecks(base, theirs, mine, !p)))
        }
    }

    @Test
    fun `the shared keep vectors`() {
        for (c in v.getJSONArray("keep").objects()) {
            val name = c.getString("name")
            val theirs = deckOf(c.getJSONArray("theirs"))
            val out = keepBindersFromOlderApp(deckOf(c.getJSONArray("source")), theirs)
            assertEquals(name, c.getBoolean("sameObject"), out === theirs)
            assertEquals(name, shown(deckOf(c.getJSONArray("expect"))), shown(out))
        }
    }

    private fun card(id: String, qty: Int = 1, binders: List<BinderRef>? = null) = DeckCardEntry(id, id, null, quantity = qty, binders = binders)

    @Test
    fun `the binders never land on the sideboard or Considering by the merge`() {
        val side = listOf(card("sol"))
        val d = Deck("d", "D", sideboard = side, considering = side, createdAt = 1)
        val out = ItemMerge.mergeDecks(Deck("d", "D", createdAt = 1), d, d.copy(sideboard = side + card("bolt")), true)
        assertTrue(out.sideboard.all { it.binders == null })
        assertTrue(out.considering.all { it.binders == null })
    }

    @Test
    fun `left out of the JSON until set, then kept as an empty list`() {
        val adapter = localMoshi.adapter(DeckCardEntry::class.java)
        val none = adapter.toJson(card("sol"))
        assertTrue(none, !none.contains("binders"))
        assertNull(adapter.fromJson("""{"scryfallId":"sol","name":"sol","quantity":1}""")!!.binders)
        assertEquals(emptyList<BinderRef>(), adapter.fromJson(adapter.toJson(card("sol", binders = emptyList())))!!.binders)
        assertEquals(listOf(BinderRef("b1", 2)), adapter.fromJson(adapter.toJson(card("sol", 2, listOf(BinderRef("b1", 2)))))!!.binders)
        assertTrue(adapter.toJson(card("sol", 2, listOf(BinderRef("b1", 2)))).contains(""""binders":[{"id":"b1","qty":2}]"""))
    }

    @Test
    fun `a printing changed to one the deck has adds the counts per binder up`() {
        val deck = Deck("d", "D", cards = listOf(card("a", 2, listOf(BinderRef("b1", 1), BinderRef("b2", 1))), card("b", 1, listOf(BinderRef("b1", 1)))), createdAt = 1)
        val sb = com.mtgcompanion.app.network.scryfall.ScryfallCard(id = "b", name = "b")
        val out = withDeckPrinting(deck, "a", sb)
        assertEquals(listOf(BinderRef("b1", 2), BinderRef("b2", 1)), out.cards.single().binders)
        assertEquals(null, joinedBinders(null, null))
        assertEquals(emptyList<BinderRef>(), joinedBinders(emptyList(), null))
    }

    @Test
    fun `a binder given a new id takes its deck cards with it`() {
        val d = Deck("d", "D", cards = listOf(card("a", 1, listOf(BinderRef("old", 1))), card("b")), createdAt = 1)
        assertEquals(listOf(BinderRef("new", 1)), withBinderIds(d, mapOf("old" to "new")).cards[0].binders)
        assertSame(d, withBinderIds(d, mapOf("x" to "y")))
    }

    @Test
    fun `to the sideboard or Considering and back the binders come along as on the web`() {
        val d = Deck("d", "D", cards = listOf(card("a", 1, listOf(BinderRef("b1", 1)))), createdAt = 1, gameMode = GameMode.MODERN.name)
        val side = d.movedToSideboard("a")
        assertEquals(listOf(BinderRef("b1", 1)), side.sideboard.single().binders)
        // Only the main deck's show in a binder (BinderViews.kt), so it's in none while it's there.
        assertTrue(deckCopiesByBinder(listOf(Collection("b1", "b1", createdAt = 1)), listOf(side)).byBinder.isEmpty())
    }
}
