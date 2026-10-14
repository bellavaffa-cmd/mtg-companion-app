package com.mtgcompanion.app.data

import com.mtgcompanion.app.ui.collection.allCardEntries
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Binders as views (BinderViews.kt). The cases in binderViewVectors.json are the web app's too
 * (tests/collection/binderViews.test.ts), byte for byte.
 */
class BinderViewsTest {

    private val v: JSONObject = JSONObject(
        javaClass.classLoader!!.getResourceAsStream("binderViewVectors.json")!!.bufferedReader().use { it.readText() }
    )

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun collectionsOf(c: JSONObject): List<Collection> = c.getJSONArray("collections").objects().map { b ->
        Collection(
            b.getString("id"), b.getString("id"),
            b.getJSONArray("entries").objects().map { e -> CollectionEntry(e.getString("id"), e.getString("id"), null, quantity = e.getInt("qty"), foilQuantity = e.optInt("foil", 0)) },
            createdAt = 1,
            type = b.getString("type")
        )
    }

    private fun decksOf(c: JSONObject): List<Deck> = c.getJSONArray("decks").objects().map { d ->
        Deck(
            d.getString("id"), d.getString("id"),
            cards = d.getJSONArray("cards").objects().map { e ->
                DeckCardEntry(
                    e.getString("id"), e.getString("id"), null, quantity = e.getInt("qty"),
                    proxyQuantity = if (e.has("proxies")) e.getInt("proxies") else null,
                    binders = if (e.has("binders")) e.getJSONArray("binders").objects().map { BinderRef(it.getString("id"), it.getInt("qty")) } else null
                )
            },
            ownership = d.optString("ownership", DeckOwnership.PHYSICAL.name),
            gameMode = d.optString("gameMode", GameMode.COMMANDER.name),
            sample = if (d.optBoolean("sample", false)) true else null,
            createdAt = 1
        )
    }

    private fun expectOf(o: JSONObject): Pair<Int, List<Triple<String, String, Int>>> =
        o.getInt("own") to o.getJSONArray("deck").let { a -> (0 until a.length()).map { i -> a.getJSONArray(i).let { Triple(it.getString(0), it.getString(1), it.getInt(2)) } } }

    private fun shown(view: BinderView) = view.ownCopies to view.deckCopies.map { Triple(it.deckId, it.entry.scryfallId, it.qty) }

    @Test
    fun `the shared vectors`() {
        for (c in v.getJSONArray("cases").objects()) {
            val name = c.getString("name")
            val collections = collectionsOf(c)
            val decks = decksOf(c)
            val expect = c.getJSONObject("expect")
            val wantBinders = expect.getJSONObject("binders")
            val binders = collections.filter { it.kind == CollectionType.OWNED && !it.isUnsorted }.map { it.id }
            assertEquals(name, wantBinders.keys().asSequence().toSet(), binders.toSet())
            var shownCopies = 0
            for (id in binders) {
                val view = binderView(id, collections, decks)
                assertEquals("$name: $id", expectOf(wantBinders.getJSONObject(id)), shown(view))
                shownCopies += view.total
            }
            val none = unsortedView(collections, decks)
            assertEquals("$name: no binder", expectOf(expect.getJSONObject("noBinder")), shown(none))
            shownCopies += none.total
            // Every real copy shows in exactly one view: the views add up to All cards' real copies.
            assertEquals(name, allCardEntries(collections, decks).sumOf { it.total - it.proxies }, shownCopies)
        }
    }

    private fun deck(vararg cards: DeckCardEntry, ownership: DeckOwnership = DeckOwnership.PHYSICAL, id: String = "d", name: String = "Talrand") =
        Deck(id, name, cards = cards.toList(), ownership = ownership.name, createdAt = 1)
    private fun card(id: String, qty: Int = 1, binders: List<BinderRef>? = null, proxies: Int? = null) =
        DeckCardEntry(id, id, null, quantity = qty, binders = binders, proxyQuantity = proxies)
    private fun binder(id: String, entries: List<CollectionEntry> = emptyList(), type: CollectionType = CollectionType.OWNED, name: String = id) =
        Collection(id, name, entries, createdAt = 1, type = type.name)

    @Test
    fun `the index is worked out once for the same library`() {
        val collections = listOf(binder("b1"))
        val decks = listOf(deck(card("sol", binders = listOf(BinderRef("b1", 1)))))
        assertSame(deckCopiesByBinder(collections, decks), deckCopiesByBinder(collections, decks))
    }

    @Test
    fun `valued totals are unchanged - sorting a deck card only changes where it shows`() {
        val collections = listOf(binder("b1", listOf(CollectionEntry("bolt", "bolt", null, quantity = 2))), binder(UNSORTED_COLLECTION_ID))
        val before = listOf(deck(card("sol", 2), card("path")))
        val after = listOf(allIntoBinder(before[0], "b1", sortingBinderIds(collections)).first)
        fun real(decks: List<Deck>) = allCardEntries(collections, decks).map { it.scryfallId to it.total - it.proxies }
        assertEquals(real(before), real(after))
        assertEquals(3, binderView("b1", collections, after).inDecks)
        assertEquals(0, unsortedView(collections, after).inDecks)
    }

    @Test
    fun `clamping walks the refs in order`() {
        val d = deck(card("sol", 2, listOf(BinderRef("a", 1), BinderRef("b", 5), BinderRef("c", 1))))
        assertEquals(listOf(BinderRef("a", 1), BinderRef("b", 1)), assignedCopies(d, d.cards[0]))
        assertEquals(emptyList<BinderRef>(), assignedCopies(d.copy(ownership = DeckOwnership.VIRTUAL.name), d.cards[0]))
    }

    @Test
    fun `put all cards in a binder leaves sorted copies where they are and fixes ones in a binder that is gone`() {
        val d = deck(card("sol", 2, listOf(BinderRef("b2", 1))), card("bolt", 1, listOf(BinderRef("gone", 1))), card("path", 2, proxies = 2))
        val (out, moved) = allIntoBinder(d, "b1", setOf("b1", "b2"))
        assertEquals(2, moved)
        assertEquals(listOf(listOf(BinderRef("b2", 1), BinderRef("b1", 1)), listOf(BinderRef("b1", 1)), null), out.cards.map { it.binders })
        assertEquals(0, allIntoBinder(out, "b1", setOf("b1", "b2")).second)
        assertEquals(0, allIntoBinder(d.copy(ownership = DeckOwnership.PROTOTYPE.name), "b1", setOf("b1")).second)
    }

    @Test
    fun `move to another binder and take out of a binder`() {
        val valid = setOf("b1", "b2")
        val refs = listOf(BinderRef("b1", 2), BinderRef("b2", 1))
        assertEquals(listOf(BinderRef("b2", 3)), movedRefs(refs, "b1", "b2", 2, valid))
        assertEquals(listOf(BinderRef("b2", 1)), movedRefs(refs, "b1", null, 2, valid))
        assertEquals(emptyList<BinderRef>(), movedRefs(listOf(BinderRef("b1", 1)), "b1", null, 1, valid))
        // From No binder: copies in no binder, or one that's gone.
        assertEquals(listOf(BinderRef("b1", 2)), movedRefs(listOf(BinderRef("gone", 1)), null, "b1", 2, valid))
        val d = deck(card("sol", 2))
        assertEquals(listOf(BinderRef("b1", 2)), withCardBinders(d, "sol", listOf(BinderRef("b1", 5))).cards[0].binders)
        assertEquals(emptyList<BinderRef>(), withCardBinders(d, "sol", emptyList()).cards[0].binders)
        val set = withCardBinders(d, "sol", listOf(BinderRef("b1", 2)))
        assertSame(set, withCardBinders(set, "sol", listOf(BinderRef("b1", 2))))
    }

    @Test
    fun `rows a printing, and the words`() {
        val decks = listOf(deck(card("sol", binders = listOf(BinderRef("b1", 1)))), deck(card("sol", binders = listOf(BinderRef("b1", 1))), id = "d2", name = "Atraxa"))
        val view = binderView("b1", listOf(binder("b1")), decks)
        val rows = deckCopyRows(view.deckCopies)
        assertEquals(1, rows.size)
        assertEquals(2, rows[0].qty)
        assertEquals("In 2 decks", inDecksBadge(rows[0].decks.map { it.second }))
        assertEquals("In Talrand deck", inDecksBadge(listOf("Talrand")))
        assertEquals("74 cards · 2 in decks", binderCountLine(BinderView("b", listOf(CollectionEntry("x", "x", null, quantity = 72)), view.deckCopies)))
        assertEquals("All sorted", noBinderLine(BinderView(UNSORTED_COLLECTION_ID, emptyList(), emptyList())))
        assertEquals("3 cards, not in a binder", noBinderLine(BinderView(UNSORTED_COLLECTION_ID, listOf(CollectionEntry("x", "x", null, quantity = 3)), emptyList())))
        assertEquals("2 cards · 2 in decks, not in a binder", noBinderLine(BinderView(UNSORTED_COLLECTION_ID, emptyList(), view.deckCopies)))
        assertEquals("kept in 3 places · 2 decks", keptInPlacesAndDecksLine(3, 10, 2))
        assertEquals("1 deck", keptInPlacesAndDecksLine(0, 0, 1))
        assertEquals("No binder", binder(UNSORTED_COLLECTION_ID, name = "Unsorted").shownName)
        assertEquals("Binder: Rares ×1", cardBindersLine(listOf(BinderRef("b1", 1), BinderRef("gone", 1)), mapOf("b1" to "Rares")))
    }

    @Test
    fun `a wishlist or unknown id shows no deck copies`() {
        val decks = listOf(deck(card("sol", binders = listOf(BinderRef(WISHLIST_ID, 1)))))
        assertTrue(binderView(WISHLIST_ID, listOf(binder(WISHLIST_ID, type = CollectionType.WISHLIST)), decks).deckCopies.isEmpty())
        assertTrue(binderView("nope", emptyList(), decks).deckCopies.isEmpty())
    }

    @Test
    fun `a proxy deck's real swapped-in copies get its deck chip and its proxies don't`() {
        val d = deck(card("sol", 3, proxies = 1), card("bolt", 2), ownership = DeckOwnership.PROXY)
        val chips = locationIndex(emptyList(), listOf(d))
        assertEquals(listOf(LocationKind.DECK to 2), chips["sol"]?.map { it.kind to it.qty })
        assertEquals(null, chips["bolt"])
    }

    @Test
    fun `group by binder - deck copies under their binder, the rest under No binder, no deck groups`() {
        val collections = listOf(binder("b1", name = "Rares"), binder(UNSORTED_COLLECTION_ID, listOf(CollectionEntry("bolt", "bolt", null, quantity = 1))))
        val decks = listOf(deck(card("sol", 2, listOf(BinderRef("b1", 1))), card("bolt")))
        val keys = binderGroupIndex(collections, decks)
        assertEquals(listOf("binder:b1" to "Rares", GROUP_NONE to NO_BINDER_TITLE), keys["sol"]?.map { it.key to it.title })
        assertEquals(listOf(GROUP_NONE to NO_BINDER_TITLE), keys["bolt"]?.map { it.key to it.title })
    }
}
