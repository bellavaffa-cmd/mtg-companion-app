package com.mtgcompanion.app.data

import com.mtgcompanion.app.ui.collection.allCardEntries
import com.mtgcompanion.app.ui.collection.copyFactsOf
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which deck copies count as owned (OwnedCopies.kt): Virtual and Prototype decks and cubes own
 * nothing, a Proxy deck's proxies are copies but worth nothing, a Physical deck (or one saved with no
 * ownership) still counts. The cases in ownedCopiesVectors.json are the web app's too
 * (tests/collection/ownedCopies.test.ts).
 */
class OwnedCopiesTest {

    private val v: JSONObject = JSONObject(
        javaClass.classLoader!!.getResourceAsStream("ownedCopiesVectors.json")!!.bufferedReader().use { it.readText() }
    )

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.ints(): Map<String, Int> = keys().asSequence().associateWith { getInt(it) }

    private fun collectionsOf(c: JSONObject): List<Collection> = c.getJSONArray("binders").objects().mapIndexed { i, b ->
        val wishlist = b.optBoolean("wishlist", false)
        Collection(
            if (wishlist) WISHLIST_ID else "b$i", "Binder $i",
            b.getJSONArray("cards").objects().map { e -> val id = e.getString("id"); CollectionEntry(id, id, null, quantity = e.optInt("qty", 1)) },
            createdAt = 1,
            type = if (wishlist) CollectionType.WISHLIST.name else CollectionType.OWNED.name
        )
    }

    private fun decksOf(c: JSONObject): List<Deck> = c.getJSONArray("decks").objects().mapIndexed { i, d ->
        val cube = d.optBoolean("cube", false)
        Deck(
            "d$i", "Deck $i",
            cards = d.getJSONArray("cards").objects().map { e ->
                val id = e.getString("id")
                DeckCardEntry(id, id, null, quantity = e.optInt("qty", 1), proxyQuantity = if (e.has("proxies")) e.getInt("proxies") else null)
            },
            ownership = d.optString("ownership", DeckOwnership.PHYSICAL.name),
            gameMode = if (cube) CUBE_MODE else GameMode.COMMANDER.name,
            archived = if (cube) true else null,
            sample = if (d.optBoolean("sample", false)) true else null,
            createdAt = 1
        )
    }

    @Test
    fun `the shared vectors`() {
        for (c in v.getJSONArray("cases").objects()) {
            val name = c.getString("name")
            val collections = collectionsOf(c)
            val decks = decksOf(c)
            val expect = c.getJSONObject("expect")
            val all = allCardEntries(collections, decks)
            val wantAll = expect.getJSONObject("allCards").let { o -> o.keys().asSequence().associateWith { k -> o.getJSONArray(k).let { it.getInt(0) to it.getInt(1) } } }
            assertEquals(name, wantAll, all.associate { it.scryfallId to (it.total to it.proxies) })
            assertEquals(name, expect.getJSONObject("valued").ints(), all.filter { it.total - it.proxies > 0 }.associate { it.scryfallId to it.total - it.proxies })
            assertEquals(name, expect.getInt("homeCards"), homeNumbers(collections, decks).cards)
            assertEquals(name, expect.getJSONObject("ownedPrintings").ints(), ownedPrintings(collections, decks))
            assertEquals(name, expect.getJSONObject("youOwn").ints(), ownedCounts(collections, decks))
            // All cards' filters see the same copies.
            assertEquals(name, wantAll.mapValues { it.value.first }, copyFactsOf(collections, decks).mapValues { it.value.copies })
        }
    }

    private fun deck(ownership: DeckOwnership, vararg cards: DeckCardEntry, gameMode: String = GameMode.COMMANDER.name) =
        Deck("d", "D", cards = cards.toList(), ownership = ownership.name, gameMode = gameMode, createdAt = 1)
    private fun card(name: String, qty: Int = 1, proxies: Int? = null) = DeckCardEntry(name, name, null, quantity = qty, proxyQuantity = proxies)

    @Test
    fun `only Physical and Proxy decks count, never a cube or a sample`() {
        assertEquals(listOf(true, true, false, false), DeckOwnership.entries.map { deck(it).countsAsOwned })
        assertFalse(deck(DeckOwnership.PHYSICAL, gameMode = CUBE_MODE).countsAsOwned)
        assertFalse(deck(DeckOwnership.PHYSICAL).copy(sample = true).countsAsOwned)
        assertTrue(deck(DeckOwnership.PHYSICAL).copy(ownership = "").countsAsOwned)
    }

    @Test
    fun `a proxy is a copy but never a valued one`() {
        val d = deck(DeckOwnership.PROXY, card("Lotus"), card("Bolt", 4, proxies = 3))
        assertEquals(listOf(0, 1), d.cards.map { ownedCopies(d, it) })
        assertEquals(listOf(1, 3), d.cards.map { ownedProxies(d, it) })
        val v = deck(DeckOwnership.VIRTUAL, card("Bolt", 4, proxies = 1))
        assertEquals(0, ownedCopies(v, v.cards[0]))
        assertEquals(0, ownedProxies(v, v.cards[0]))
    }

    @Test
    fun `the scan panel's You own N leaves unowned decks out`() {
        val virtual = deck(DeckOwnership.VIRTUAL, card("Sol Ring", 2)).copy(id = "v")
        val physical = deck(DeckOwnership.PHYSICAL, card("Sol Ring")).copy(id = "p", name = "Krenko")
        val s = ownedSummary(emptyList(), listOf(virtual, physical), "Sol Ring", "Sol Ring")
        assertEquals(1, s.samePrinting)
        assertEquals(listOf("Krenko deck" to 1), s.places)
    }
}
