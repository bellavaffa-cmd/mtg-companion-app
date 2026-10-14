package com.mtgcompanion.app.data

import com.mtgcompanion.app.data.supabase.ItemMerge
import com.mtgcompanion.app.ui.collection.allCardEntries
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Copies moving between binders and decks (BinderFlows.kt). The cases in returnToBinderVectors.json
 * are the web app's too (tests/collection/binderFlows.test.ts), byte for byte.
 */
class BinderFlowsTest {

    private val v: JSONObject = JSONObject(
        javaClass.classLoader!!.getResourceAsStream("returnToBinderVectors.json")!!.bufferedReader().use { it.readText() }
    )

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.str(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null

    private fun placeOf(o: JSONObject) = CopyPlace(o.getString("placeId"), o.getInt("qty"), if (o.optBoolean("foil", false)) true else null, o.str("section"))
    private fun cameOf(o: JSONObject) = CameFrom(o.getString("name"), o.getString("placeId"), o.getInt("qty"), if (o.optBoolean("foil", false)) true else null, o.str("section"))
    private fun refsOf(o: JSONObject): List<BinderRef>? =
        if (!o.has("binders")) null else o.getJSONArray("binders").objects().map { binderRef(it.getString("id"), it.getInt("qty"), it.optInt("foil", 0)) }

    private fun collectionsOf(c: JSONObject): List<Collection> {
        val cols = c.getJSONArray("collections").objects().map { b ->
            Collection(
                b.getString("id"), b.getString("id"),
                b.getJSONArray("entries").objects().map { e ->
                    CollectionEntry(
                        e.getString("id"), e.str("name") ?: e.getString("id"), null, quantity = e.getInt("qty"), foilQuantity = e.optInt("foil", 0),
                        places = if (e.has("places")) e.getJSONArray("places").objects().map(::placeOf) else null
                    )
                },
                createdAt = 1,
                type = b.getString("type")
            )
        }
        val places = c.getJSONArray("places").let { a -> (0 until a.length()).map { StoragePlace(a.getString(it), a.getString(it)) } }
        return if (places.isEmpty()) cols else withPlaceList(cols, places)
    }

    private fun deckOf(d: JSONObject) = Deck(
        d.getString("id"), d.getString("id"),
        cards = d.getJSONArray("cards").objects().map { e ->
            DeckCardEntry(
                e.getString("id"), e.str("name") ?: e.getString("id"), null, quantity = e.getInt("qty"),
                proxyQuantity = if (e.has("proxies")) e.getInt("proxies") else null,
                binders = refsOf(e)
            )
        },
        ownership = d.optString("ownership", DeckOwnership.PHYSICAL.name),
        cameFrom = if (d.has("cameFrom")) d.getJSONArray("cameFrom").objects().map(::cameOf) else null,
        createdAt = 1
    )

    // What's compared, as plain strings, so a failure says what differs.
    private fun refsText(refs: List<BinderRef>?) = refs?.joinToString(",", "[", "]") { "${it.id}×${it.qty}" + (if (it.foils > 0) " foil ${it.foils}" else "") }
    private fun placesText(p: List<CopyPlace>?) = p?.takeIf { it.isNotEmpty() }?.joinToString(",", "@") { "${it.placeId}×${it.qty}" + (if (it.isFoil) " foil" else "") + (it.section?.let { s -> " $s" } ?: "") }
    private fun cameText(l: List<CameFrom>?) = l?.joinToString(",", "[", "]") { "${it.name} ${it.placeId}×${it.qty}" + (if (it.foil == true) " foil" else "") + (it.section?.let { s -> " $s" } ?: "") }
    private fun entryText(e: CollectionEntry) = "${e.scryfallId} ${e.quantity}+${e.foilQuantity}" + (placesText(e.places)?.let { " $it" } ?: "")
    private fun cardText(e: DeckCardEntry) = "${e.scryfallId} ${e.quantity}" + (e.proxyQuantity?.let { " proxies $it" } ?: "") + (refsText(e.binders)?.let { " $it" } ?: "")
    private fun deckText(d: Deck?) = d?.let { "${it.ownership} " + it.cards.joinToString("; ") { e -> cardText(e) } + (cameText(it.cameFrom)?.let { c -> " came $c" } ?: "") }

    /** The deck expected, its ownership the deck's own unless it says. */
    private fun expectedDeck(o: JSONObject, was: Deck): String? {
        if (o.isNull("deck")) return null
        val d = JSONObject(o.getJSONObject("deck").toString()).put("id", was.id)
        if (!d.has("ownership")) d.put("ownership", was.ownership)
        return deckText(deckOf(d))
    }

    private fun realCopies(collections: List<Collection>, decks: List<Deck>) = allCardEntries(collections, decks).sumOf { it.total - it.proxies }

    private fun run(c: JSONObject, collections: List<Collection>, deck: Deck): Pair<List<Collection>, Deck?> {
        val d = c.getJSONObject("do")
        return when (d.getString("flow")) {
            "fromBinder" -> sendToDeck(collections, deck, d.getString("binder"), d.getString("card"), d.getInt("qty"))
            "countDown" -> returnCopies(collections, deck, withCardCount(deck, d.getString("card"), d.getInt("to"))).let { it.collections to it.deck }
            "remove" -> returnCopies(collections, deck, withCardCount(deck, d.getString("card"), 0)).let { it.collections to it.deck }
            "deleteDeck" -> returnCopies(collections, deck, null).let { it.collections to it.deck }
            "ownership" -> returnCopies(collections, deck, deck.copy(ownership = d.getString("to"))).let { it.collections to it.deck }
            "deleteBinder" -> collections.filterNot { it.id == d.getString("binder") } to deck
            "swapIn" -> withSwapIn(collections, listOf(deck), deck.id, d.getString("card"))!!.let { it.collections to it.decks.single() }
            "pull" -> {
                val list = pullList(deck, collections, listOf(deck))
                movePulled(deck, list, list.groups.flatMap { g -> g.rows.map { it.key } }.toSet(), collections, listOf(deck)).let { it.collections to it.decks.single() }
            }
            "putBack" -> takeApart(deck, putBackList(deck, collections, PutBackMode.ORIGIN), collections).let { it.collections to it.deck }
            else -> error("unknown flow")
        }
    }

    @Test
    fun `the shared vectors`() {
        for (c in v.getJSONArray("cases").objects()) {
            val name = c.getString("name")
            val collections = collectionsOf(c)
            val deck = deckOf(c.getJSONObject("deck"))
            val (cols, after) = run(c, collections, deck)
            val expect = c.getJSONObject("expect")

            val want = expect.getJSONObject("collections")
            val shown = cols.filter { it.kind != CollectionType.WISHLIST }
            assertEquals(name, want.keys().asSequence().toSet(), shown.map { it.id }.toSet())
            for (col in shown) {
                val wantEntries = want.getJSONArray(col.id).objects().map { e ->
                    entryText(CollectionEntry(e.getString("id"), e.getString("id"), null, e.getInt("qty"), e.optInt("foil", 0), places = if (e.has("places")) e.getJSONArray("places").objects().map(::placeOf) else null))
                }
                assertEquals("$name: ${col.id}", wantEntries, col.entries.map(::entryText))
            }
            assertEquals("$name: deck", expectedDeck(expect, deck), deckText(after))

            val decks = listOfNotNull(after)
            val wantBinders = expect.getJSONObject("binders")
            val binders = cols.filter { it.kind == CollectionType.OWNED && !it.isUnsorted }.map { it.id }
            assertEquals(name, wantBinders.keys().asSequence().toSet(), binders.toSet())
            var inViews = 0
            for (id in binders) {
                val view = binderView(id, cols, decks)
                assertEquals("$name: view $id", viewOf(wantBinders.getJSONObject(id)), view.ownCopies to view.deckCopies.map { Triple(it.deckId, it.entry.scryfallId, it.qty) })
                inViews += view.total
            }
            val none = unsortedView(cols, decks)
            assertEquals("$name: no binder", viewOf(expect.getJSONObject("noBinder")), none.ownCopies to none.deckCopies.map { Triple(it.deckId, it.entry.scryfallId, it.qty) })
            inViews += none.total

            // No copy made or lost (a deleted binder's own go with it), and every one in exactly one view.
            val gone = if (c.getJSONObject("do").getString("flow") == "deleteBinder")
                collections.first { it.id == c.getJSONObject("do").getString("binder") }.entries.sumOf { it.quantity + it.foilQuantity } else 0
            assertEquals("$name: copies", realCopies(collections, listOf(deck)) - gone, realCopies(cols, decks))
            assertEquals("$name: views", realCopies(cols, decks), inViews)
        }
    }

    private fun viewOf(o: JSONObject): Pair<Int, List<Triple<String, String, Int>>> =
        o.getInt("own") to o.getJSONArray("deck").let { a -> (0 until a.length()).map { i -> a.getJSONArray(i).let { Triple(it.getString(0), it.getString(1), it.getInt(2)) } } }

    // ---- Two devices ----

    private fun card(id: String, qty: Int = 1, binders: List<BinderRef>? = null) = DeckCardEntry(id, id, null, quantity = qty, binders = binders)
    private fun binder(id: String, vararg entries: CollectionEntry) = Collection(id, id, entries.toList(), createdAt = 1, type = CollectionType.OWNED.name)
    private fun own(id: String, qty: Int) = CollectionEntry(id, id, null, quantity = qty)

    private fun mergedBothWays(base: Deck, a: Deck, b: Deck): Deck {
        val one = ItemMerge.mergeDecks(base, a, b, true)
        val other = ItemMerge.mergeDecks(base, b, a, false)
        assertEquals(one.cards, other.cards)
        return one
    }

    @Test
    fun `one device moves a card from a binder into a deck while the other edits the deck`() {
        val baseCols = listOf(binder("b1", own("sol", 1), own("bolt", 1)), binder(UNSORTED_COLLECTION_ID))
        val baseDeck = Deck("d1", "Talrand", cards = listOf(card("path", 1)), createdAt = 1)
        val before = realCopies(baseCols, listOf(baseDeck))

        // Phone A: Sol Ring out of b1 into the deck.
        val (colsA, deckA) = sendToDeck(baseCols, baseDeck, "b1", "sol", 1)
        // Phone B: the deck's other card's count changed by hand, and the deck renamed (no copies of Sol Ring touched).
        val deckB = baseDeck.copy(name = "Talrand, Sky Summoner", cards = listOf(card("path", 1, binders = emptyList())))

        val deck = mergedBothWays(baseDeck, deckA, deckB)
        val b1 = ItemMerge.mergeCollections(baseCols[0], colsA[0], baseCols[0], true)
        val cols = listOf(b1, baseCols[1])
        assertEquals(listOf("path" to 1, "sol" to 1), deck.cards.map { it.scryfallId to it.quantity }.sortedBy { it.first })
        assertEquals(listOf(BinderRef("b1", 1)), deck.cards.first { it.scryfallId == "sol" }.binders)
        assertEquals(listOf("bolt"), b1.entries.map { it.scryfallId })
        // Sol Ring is in one place only, and b1 still lists it.
        assertEquals(before, realCopies(cols, listOf(deck)))
        assertEquals(listOf("sol"), binderView("b1", cols, listOf(deck)).deckCopies.map { it.entry.scryfallId })
    }

    @Test
    fun `one device takes a card out of a deck back to its binder while the other edits the deck`() {
        val baseCols = listOf(binder("b1"), binder(UNSORTED_COLLECTION_ID))
        val baseDeck = Deck("d1", "Talrand", cards = listOf(card("sol", 1, listOf(BinderRef("b1", 1))), card("path", 1)), createdAt = 1)
        val before = realCopies(baseCols, listOf(baseDeck))

        // Phone A: Sol Ring taken out of the deck — back to b1.
        val r = returnCopies(baseCols, baseDeck, withCardCount(baseDeck, "sol", 0))
        // Phone B: Path's copies put in b1 too.
        val deckB = withCardBinders(baseDeck, "path", listOf(BinderRef("b1", 1)))

        val deck = mergedBothWays(baseDeck, r.deck!!, deckB)
        val b1 = ItemMerge.mergeCollections(baseCols[0], r.collections[0], baseCols[0], true)
        val cols = listOf(b1, baseCols[1])
        assertEquals(listOf("path"), deck.cards.map { it.scryfallId })
        assertEquals(listOf("sol" to 1), b1.entries.map { it.scryfallId to it.quantity })
        assertEquals(before, realCopies(cols, listOf(deck)))
        assertEquals(2, binderView("b1", cols, listOf(deck)).total)
    }

    // ---- Smaller things ----

    @Test
    fun `refs written without a foil count when there are none, and an older app's plain refs still go back`() {
        assertNull(binderRef("b1", 2, 0).foil)
        assertEquals(1, binderRef("b1", 2, 1).foil)
        // A 3.8.7 phone saved the ref without "foil": the copy goes back plain, still to its binder.
        val deck = Deck("d1", "D", cards = listOf(card("sol", 1, listOf(BinderRef("b1", 1)))), createdAt = 1)
        val r = returnCopies(listOf(binder("b1"), binder(UNSORTED_COLLECTION_ID)), deck, null)
        assertEquals(listOf("b1" to (1 to 0)), r.back.map { it.collectionId to (it.plain to it.foil) })
    }

    @Test
    fun `a virtual deck's cards never go anywhere`() {
        val deck = Deck("d1", "D", cards = listOf(card("sol", 2, listOf(BinderRef("b1", 2)))), ownership = DeckOwnership.VIRTUAL.name, createdAt = 1)
        val cols = listOf(binder("b1"), binder(UNSORTED_COLLECTION_ID))
        val r = returnCopies(cols, deck, null)
        assertTrue(r.back.isEmpty())
        assertTrue(r.collections === cols)
    }

    @Test
    fun `the deck and its copies the binder lists add up after a card is sent and taken back`() {
        val cols = listOf(binder("b1", own("sol", 1)), binder(UNSORTED_COLLECTION_ID))
        val deck = Deck("d1", "D", createdAt = 1)
        val (sent, inDeck) = sendToDeck(cols, deck, "b1", "sol", 1)
        val r = returnCopies(sent, inDeck, withCardCount(inDeck, "sol", 0))
        assertEquals(listOf("sol" to 1), r.collections.first { it.id == "b1" }.entries.map { it.scryfallId to it.quantity })
        assertEquals(emptyList<DeckCardEntry>(), r.deck!!.cards)
        // Undo of the way back takes it out of the binder again.
        assertEquals(emptyList<CollectionEntry>(), takeBack(r.collections, r.back).first { it.id == "b1" }.entries)
    }
}
