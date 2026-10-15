package com.mtgcompanion.app.data

import com.mtgcompanion.app.data.supabase.ItemMerge
import com.mtgcompanion.app.ui.collection.allCardEntries
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Cards sent between decks and in and out of a sideboard (BinderFlows.kt sendDeckCard, toSideboard,
 * toMain). The cases in moveVectors.json are the web app's too (tests/collection/moveFlows.test.ts),
 * byte for byte.
 */
class MoveFlowsTest {

    private val v: JSONObject = JSONObject(
        javaClass.classLoader!!.getResourceAsStream("moveVectors.json")!!.bufferedReader().use { it.readText() }
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

    private fun cardOf(e: JSONObject) = DeckCardEntry(
        e.getString("id"), e.str("name") ?: e.getString("id"), null, quantity = e.getInt("qty"),
        proxyQuantity = if (e.has("proxies")) e.getInt("proxies") else null,
        binders = refsOf(e)
    )

    private fun cardsOf(d: JSONObject, key: String) = if (d.has(key)) d.getJSONArray(key).objects().map(::cardOf) else emptyList()

    private fun deckOf(d: JSONObject) = Deck(
        d.getString("id"), d.getString("id"),
        cards = cardsOf(d, "cards"),
        sideboard = cardsOf(d, "sideboard"),
        considering = cardsOf(d, "considering"),
        gameMode = GameMode.STANDARD.name,
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

    /** [d] as text: its main deck always, its sideboard and Considering list when [want] gives them. */
    private fun deckText(d: Deck, want: JSONObject) = "${d.ownership} " + d.cards.joinToString("; ") { cardText(it) } +
        (if (want.has("sideboard")) " side " + d.sideboard.joinToString("; ") { cardText(it) } else "") +
        (if (want.has("considering")) " considering " + d.considering.joinToString("; ") { cardText(it) } else "") +
        (cameText(d.cameFrom)?.let { " came $it" } ?: "")

    private fun expectedDeck(want: JSONObject, was: Deck): String {
        val d = JSONObject(want.toString()).put("id", was.id)
        if (!d.has("ownership")) d.put("ownership", was.ownership)
        return deckText(deckOf(d), want)
    }

    private fun realCopies(collections: List<Collection>, decks: List<Deck>) = allCardEntries(collections, decks).sumOf { it.total - it.proxies }

    private data class Out(val collections: List<Collection>, val decks: List<Deck>, val sent: List<Int>?)

    private fun run(c: JSONObject, collections: List<Collection>, decks: List<Deck>): Out {
        val d = c.getJSONObject("do")
        fun find(id: String) = decks.first { it.id == id }
        fun put(vararg changed: Deck?) = decks.map { x -> changed.firstOrNull { it?.id == x.id } ?: x }
        return when (d.getString("flow")) {
            "send" -> {
                val t = d.getJSONObject("to")
                val to = if (t.has("binder")) SendTo.ToBinder(t.getString("binder"))
                else SendTo.ToDeck(find(t.getString("deck")), DeckPart.valueOf(t.getString("part").uppercase()))
                val r = sendDeckCard(collections, find(d.getString("from")), d.getString("card"), d.getInt("qty"), to, d.optBoolean("keep", false))
                Out(r.collections, put(r.from, r.to), listOf(r.real, r.taken, r.toPull, r.added))
            }
            "toSideboard" -> toSideboard(collections, find(d.getString("deck")), d.getString("card")).let { Out(it.collections, put(it.deck), null) }
            "toMain" -> toMain(collections, find(d.getString("deck")), d.getString("card")).let { Out(it.collections, put(it.deck), null) }
            else -> error("unknown flow")
        }
    }

    @Test
    fun `the shared vectors`() {
        for (c in v.getJSONArray("cases").objects()) {
            val name = c.getString("name")
            val collections = collectionsOf(c)
            val decks = c.getJSONArray("decks").objects().map(::deckOf)
            val out = run(c, collections, decks)
            val expect = c.getJSONObject("expect")

            val want = expect.getJSONObject("collections")
            val shown = out.collections.filter { it.kind != CollectionType.WISHLIST }
            assertEquals(name, want.keys().asSequence().toSet(), shown.map { it.id }.toSet())
            for (col in shown) {
                val wantEntries = want.getJSONArray(col.id).objects().map { e ->
                    entryText(CollectionEntry(e.getString("id"), e.getString("id"), null, e.getInt("qty"), e.optInt("foil", 0), places = if (e.has("places")) e.getJSONArray("places").objects().map(::placeOf) else null))
                }
                assertEquals("$name: ${col.id}", wantEntries, col.entries.map(::entryText))
            }
            val wantDecks = expect.getJSONObject("decks")
            for (id in wantDecks.keys()) {
                val w = wantDecks.getJSONObject(id)
                assertEquals("$name: deck $id", expectedDeck(w, decks.first { it.id == id }), deckText(out.decks.first { it.id == id }, w))
            }
            val sent = expect.optJSONObject("sent")
            if (sent != null) assertEquals("$name: sent", listOf(sent.getInt("real"), sent.getInt("taken"), sent.getInt("toPull"), sent.getInt("added")), out.sent)

            // No copy made or lost (but the ones a deck that holds none of its cards adds to a binder), and every one in exactly one view.
            val added = sent?.getInt("added") ?: 0
            assertEquals("$name: copies", realCopies(collections, decks) + added, realCopies(out.collections, out.decks))
            var inViews = unsortedView(out.collections, out.decks).total
            for (b in out.collections) if (b.kind == CollectionType.OWNED && !b.isUnsorted) inViews += binderView(b.id, out.collections, out.decks).total
            assertEquals("$name: views", realCopies(out.collections, out.decks), inViews)
        }
    }

    // ---- Two devices ----

    @Test
    fun `one device moves a card to another deck while the other edits that deck`() {
        fun card(id: String, qty: Int, binders: List<BinderRef>? = null) = DeckCardEntry(id, id, null, quantity = qty, binders = binders)
        val cols = listOf(
            Collection("b1", "b1", emptyList(), createdAt = 1, type = CollectionType.OWNED.name),
            Collection(UNSORTED_COLLECTION_ID, UNSORTED_COLLECTION_ID, emptyList(), createdAt = 1, type = CollectionType.OWNED.name)
        )
        val d1 = Deck("d1", "d1", cards = listOf(card("sol", 1, listOf(BinderRef("b1", 1)))), createdAt = 1)
        val d2 = Deck("d2", "d2", cards = listOf(card("path", 1)), createdAt = 1)
        val before = realCopies(cols, listOf(d1, d2))

        // Phone A: Sol Ring moved from d1 to d2, its binder with it.
        val a = sendDeckCard(cols, d1, "sol", 1, SendTo.ToDeck(d2, DeckPart.MAIN), keep = false)
        // Phone B: d2's Path put in b1 and one more copy of it added by hand.
        val d2b = d2.copy(cards = listOf(card("path", 2, listOf(BinderRef("b1", 1)))))

        val one = ItemMerge.mergeDecks(d2, a.to!!, d2b, true)
        val other = ItemMerge.mergeDecks(d2, d2b, a.to!!, false)
        assertEquals(one.cards, other.cards)
        assertEquals(listOf(BinderRef("b1", 1)), one.cards.first { it.scryfallId == "sol" }.binders)
        // Sol Ring is in d2 only, still listed in b1; the only new copy is B's Path.
        assertEquals(before + 1, realCopies(a.collections, listOf(a.from, one)))
        assertEquals(
            listOf("d2" to "path", "d2" to "sol"),
            binderView("b1", a.collections, listOf(a.from, one)).deckCopies.map { it.deckId to it.entry.scryfallId }.sortedBy { it.second }
        )
    }
}
