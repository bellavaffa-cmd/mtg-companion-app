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
 * Collection goals. The cases in collectionGoalVectors.json are the web app's too (MtgCompanionWeb's
 * tests/collection/collectionGoals.test.ts, which keeps the file), so both apps count, complete,
 * merge and name goals the same way.
 */
class CollectionGoalsTest {

    private val v: JSONObject = JSONObject(
        javaClass.classLoader!!.getResourceAsStream("collectionGoalVectors.json")!!.bufferedReader().use { it.readText() }
    )

    // ---- Reading the vectors ----

    private fun JSONObject.str(k: String): String? = if (!has(k) || isNull(k)) null else getString(k)
    private fun JSONObject.dbl(k: String): Double? = if (!has(k) || isNull(k)) null else getDouble(k)
    private fun JSONObject.lng(k: String): Long? = if (!has(k) || isNull(k)) null else getLong(k)
    private fun JSONObject.bool(k: String): Boolean? = if (!has(k) || isNull(k)) null else getBoolean(k)
    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
    private fun JSONObject.objects(k: String): List<JSONObject> = if (!has(k) || isNull(k)) emptyList() else getJSONArray(k).objects()

    private fun card(o: JSONObject) = GoalCard(
        o.getString("name"), o.str("scryfallId"), o.getInt("qty"), o.str("imageUrl"), o.dbl("usd"), o.dbl("usdFoil"), o.str("rarity"), o.str("number")
    )

    private fun goal(o: JSONObject) = CollectionGoal(
        id = o.getString("id"), name = o.getString("name"), kind = o.getString("kind"), setCode = o.str("setCode"),
        rarities = if (o.has("rarities")) o.getJSONArray("rarities").strings() else null, foil = o.bool("foil"), deckId = o.str("deckId"),
        cards = o.objects("cards").map { card(it) }, countDecks = o.bool("countDecks"), createdAt = o.getLong("createdAt"),
        updatedAt = o.getLong("updatedAt"), completedAt = o.lng("completedAt")
    )

    private fun goals(a: Any?): List<CollectionGoal>? = if (a == null || a == JSONObject.NULL) null else (a as JSONArray).objects().map { goal(it) }

    private fun entry(o: JSONObject) = CollectionEntry(o.getString("scryfallId"), o.getString("name"), null, o.getInt("quantity"), o.getInt("foilQuantity"))

    private fun deckCard(o: JSONObject) = DeckCardEntry(
        scryfallId = o.getString("scryfallId"), name = o.getString("name"), imageUrl = null, quantity = o.getInt("quantity"),
        proxyQuantity = if (o.has("proxyQuantity")) o.getInt("proxyQuantity") else null
    )

    private val collections: List<Collection> = v.getJSONObject("library").getJSONArray("collections").objects().map { c ->
        Collection(c.getString("id"), c.getString("id"), c.getJSONArray("entries").objects().map { entry(it) }, createdAt = 0, type = c.getString("type"))
    }

    private val decks: List<Deck> = v.getJSONObject("library").getJSONArray("decks").objects().map { d ->
        Deck(
            id = d.getString("id"), name = d.getString("name"), ownership = d.getString("ownership"),
            commander = if (d.has("commander")) deckCard(d.getJSONObject("commander")) else null,
            cards = d.objects("cards").map { deckCard(it) },
            cameFrom = if (d.has("cameFrom")) d.getJSONArray("cameFrom").objects().map { CameFrom(it.getString("name"), it.getString("placeId"), it.getInt("qty"), it.bool("foil")) } else null,
            sample = d.bool("sample")
        )
    }

    private val allGoals: List<CollectionGoal> = v.getJSONArray("goals").objects().map { goal(it) }
    private fun goalById(id: String) = allGoals.first { it.id == id }

    private val prices: Map<String, GoalPrice> = v.getJSONObject("prices").let { p ->
        p.keys().asSequence().associateWith { k -> p.getJSONObject(k).let { GoalPrice(it.dbl("usd"), it.dbl("usdFoil")) } }
    }

    // ---- Comparing goals as JSON: keys sorted, whole numbers without ".0", nulls left out ----

    private fun num(n: Number): String {
        val d = n.toDouble()
        return if (d == Math.floor(d) && !d.isInfinite()) d.toLong().toString() else d.toString()
    }

    private fun canon(x: Any?): String = when (x) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> x.keys().asSequence().sorted().filter { !x.isNull(it) }.joinToString(",", "{", "}") { "\"$it\":" + canon(x.get(it)) }
        is JSONArray -> (0 until x.length()).joinToString(",", "[", "]") { canon(x.get(it)) }
        is Map<*, *> -> x.entries.filter { it.value != null }.sortedBy { it.key as String }.joinToString(",", "{", "}") { "\"${it.key}\":" + canon(it.value) }
        is List<*> -> x.joinToString(",", "[", "]") { canon(it) }
        is Number -> num(x)
        is Boolean -> x.toString()
        else -> JSONObject.quote(x.toString())
    }

    private fun mapOfCard(c: GoalCard): Map<String, Any?> = mapOf(
        "name" to c.name, "scryfallId" to c.scryfallId, "qty" to c.qty, "imageUrl" to c.imageUrl, "usd" to c.usd, "usdFoil" to c.usdFoil,
        "rarity" to c.rarity, "number" to c.number
    )

    private fun mapOfGoal(g: CollectionGoal): Map<String, Any?> = mapOf(
        "id" to g.id, "name" to g.name, "kind" to g.kind, "setCode" to g.setCode, "rarities" to g.rarities, "foil" to g.foil, "deckId" to g.deckId,
        "cards" to g.cards.map { mapOfCard(it) }, "countDecks" to g.countDecks, "createdAt" to g.createdAt, "updatedAt" to g.updatedAt,
        "completedAt" to g.completedAt
    )

    private fun assertGoal(expect: Any?, got: CollectionGoal?, what: String) =
        assertEquals(what, canon(expect), if (got == null) "null" else canon(mapOfGoal(got)))

    private fun assertGoals(expect: Any?, got: List<CollectionGoal>?, what: String) =
        assertEquals(what, canon(expect), if (got == null) "null" else canon(got.map { mapOfGoal(it) }))

    private val fmt = { n: Double -> "$" + num(n) }

    // ---- The shared cases ----

    @Test
    fun `progress - have and need, percent, value of what's missing, each card's copies`() {
        for (e in v.getJSONArray("progress").objects()) {
            val id = e.getString("goal")
            val p = goalProgress(goalById(id), collections, decks, prices)
            assertEquals(id, e.getInt("have"), p.have)
            assertEquals(id, e.getInt("need"), p.need)
            assertEquals(id, e.getInt("percent"), p.percent)
            assertEquals(id, e.getDouble("missingUsd"), p.missingUsd, 1e-9)
            assertEquals(id, e.getInt("unpriced"), p.unpriced)
            assertEquals(id, e.getBoolean("complete"), p.complete)
            val lines = e.getJSONArray("lines").objects()
            assertEquals(id, lines.size, p.lines.size)
            for ((l, got) in lines.zip(p.lines)) {
                assertEquals(id, l.getString("name"), got.name)
                assertEquals(id, listOf(l.getInt("need"), l.getInt("owned"), l.getInt("have"), l.getInt("missing")), listOf(got.need, got.owned, got.have, got.missing))
                assertEquals("$id ${got.name}", l.dbl("usd"), got.usd)
            }
        }
    }

    @Test
    fun `completion - goals complete now are marked once, at that time`() {
        val c = v.getJSONObject("complete")
        val r = completeGoals(allGoals, collections, decks, c.getLong("now"))
        assertEquals(c.getJSONArray("done").strings(), r.done)
        val at = c.getJSONObject("completedAt")
        for (g in r.goals) assertEquals(g.id, at.lng(g.id), g.completedAt)
        val again = completeGoals(r.goals, collections, decks, c.getLong("now") + 1)
        assertTrue(again.done.isEmpty())
        assertSame(r.goals, again.goals)
        for (g in r.goals) assertEquals(goalById(g.id).updatedAt, g.updatedAt)
    }

    @Test
    fun `the scanner - which goals a scanned card moves on`() {
        for (h in v.getJSONArray("hits").objects()) {
            val c = h.getJSONObject("card")
            val got = goalHits(allGoals, collections, decks, c.getString("scryfallId"), c.getString("name"), h.getBoolean("foil"), h.getInt("pending")).map { hitLine(it) }
            assertEquals(h.toString(), h.getJSONArray("expect").strings(), got)
        }
    }

    @Test
    fun `Add missing to Wishlist - only what the Wishlist doesn't already want`() {
        val wishlist = collections.first { it.kind == CollectionType.WISHLIST }.entries
        for (w in v.getJSONArray("wishlist").objects()) {
            val got = goalWishlistAdds(goalProgress(goalById(w.getString("goal")), collections, decks), wishlist).map { "${it.name} ${it.quantity}" }
            assertEquals(w.getString("goal"), w.getJSONArray("expect").strings(), got)
        }
    }

    @Test
    fun `a goal as both apps write it`() {
        for (n in v.getJSONArray("normalize").objects()) {
            val got = collectionGoal(goal(n.getJSONObject("in")))
            assertGoal(n.getJSONObject("out"), got, n.getJSONObject("in").getString("id"))
            assertEquals(got, collectionGoal(got))
        }
    }

    @Test
    fun `making goals - set names, a set goal, a deck goal`() {
        for (s in v.getJSONArray("setNames").objects()) {
            assertEquals(s.getString("name"), setGoalName(s.getString("set"), s.getJSONArray("rarities").strings(), s.getBoolean("foil")))
        }
        val sg = v.getJSONObject("setGoal")
        val set = sg.getJSONObject("set")
        val cards = sg.getJSONArray("cards").objects().map {
            GoalSetCard(it.getString("id"), it.getString("name"), it.str("rarity"), it.str("number"), it.str("imageUrl"), it.dbl("usd"), it.dbl("usdFoil"))
        }
        for (c in sg.getJSONArray("cases").objects()) {
            val got = newSetGoal("s", set.getString("code"), set.getString("name"), cards, c.getJSONArray("rarities").strings(), c.getBoolean("foil"), 10)
            assertGoal(c.getJSONObject("goal"), got, c.toString())
        }
        for (c in v.getJSONArray("deckGoals").objects()) {
            val got = newDeckGoal("d", decks.first { it.id == c.getString("deckId") }, c.getBoolean("foil"), 10)
            assertGoal(c.getJSONObject("goal"), got, c.toString())
        }
    }

    @Test
    fun `merging two devices' goals, goal by goal`() {
        for (m in v.getJSONArray("merge").objects()) {
            val got = mergeGoals(goals(m.opt("base")), goals(m.opt("mine")), goals(m.opt("theirs")), m.getBoolean("minePreferred"))
            assertGoals(m.opt("expect"), got, m.getString("about"))
        }
    }

    @Test
    fun `the lines under a goal`() {
        for (m in v.getJSONArray("missingLines").objects()) {
            val p = goalProgress(goalById(m.getString("goal")), collections, decks, prices)
            assertEquals(m.getString("goal"), m.getString("line"), missingLine(p, fmt))
            assertEquals(m.getString("goal"), m.getString("progress"), progressLine(p))
        }
    }

    // ---- This app's own ----

    @Test
    fun `a playset goal - every card at the playset's count`() {
        val g = newListGoal("p", "PLAYSET", "Shocks", listOf(GoalCard("Steam Vents", qty = 1), GoalCard("Sacred Foundry", qty = 2)), null, 5)
        assertEquals(listOf(4, 4), g.cards.map { it.qty })
        assertEquals(3, newListGoal("p", "PLAYSET", "Shocks", listOf(GoalCard("Steam Vents", qty = 1)), 3, 5).cards[0].qty)
        assertEquals(listOf(GoalCard("Opt", qty = 3)), newListGoal("c", "CUSTOM", "Mine", listOf(GoalCard("Opt", qty = 2), GoalCard("Opt", qty = 1)), null, 5).cards)
    }

    @Test
    fun `missing names - once each, to ask friends`() {
        assertEquals(listOf("Gamma Uncommon", "Alpha Uncommon"), missingNames(goalProgress(goalById("g-set"), collections, decks)))
    }

    @Test
    fun `open goals most nearly done first, completed ones last, newest first`() {
        val r = completeGoals(allGoals, collections, decks, 100)
        val (open, done) = sortedGoals(r.goals) { goalProgress(it, collections, decks) }
        assertEquals("g-custom", open.first().id)
        assertEquals(listOf("g-deck-gone", "g-done", "g-was-done"), done.map { it.id })
    }

    @Test
    fun `kept on the Unsorted pile - saved, replaced, deleted`() {
        val g = goalById("g-done")
        var c: List<Collection> = emptyList()
        c = saveGoal(c, g)
        assertEquals(1, c.size)
        assertTrue(c[0].isUnsorted)
        assertEquals(listOf("g-done"), goalsOf(c).map { it.id })
        c = saveGoal(c, g.copy(name = "Renamed", updatedAt = 9))
        assertEquals(listOf("Renamed"), goalsOf(c).map { it.name })
        c = deleteGoal(c, "g-done")
        assertEquals(emptyList<CollectionGoal>(), c[0].collectionGoals)
    }

    private fun pile(goals: List<CollectionGoal>?) = Collection(UNSORTED_COLLECTION_ID, UNSORTED_COLLECTION_NAME, createdAt = 0, type = CollectionType.OWNED.name, collectionGoals = goals)

    @Test
    fun `a pile saved by an app from before goals keeps this device's`() {
        val mine = pile(listOf(goalById("g-done")))
        val older = pile(null)
        assertEquals(mine.collectionGoals, keepGoalsFromOlderApp(mine, older).collectionGoals)
        assertSame(older, keepGoalsFromOlderApp(pile(null), older))
        assertEquals(mine.collectionGoals, ItemMerge.mergeCollections(mine, mine, older, minePreferred = false).collectionGoals)
        val other = goalById("g-set").copy(updatedAt = 3)
        val both = ItemMerge.mergeCollections(pile(emptyList()), pile(listOf(goalById("g-done"))), pile(listOf(other)), minePreferred = true)
        assertEquals(listOf("g-done", "g-set"), both.collectionGoals!!.map { it.id })
        assertNull(ItemMerge.mergeCollections(pile(null), pile(null), pile(null), minePreferred = true).collectionGoals)
    }

    @Test
    fun `Reset collection - Collection and Everything clear goals, Cards only keeps them`() {
        val lib = listOf(pile(listOf(goalById("g-done"), goalById("g-set"))))
        assertEquals(2, resetLibrary(emptyList(), lib, ResetScope.CARDS).collections[0].collectionGoals!!.size)
        assertEquals(0, resetLibrary(emptyList(), lib, ResetScope.COLLECTION).collections[0].collectionGoals!!.size)
        assertEquals(0, resetLibrary(emptyList(), lib, ResetScope.EVERYTHING).collections[0].collectionGoals!!.size)
        assertEquals("2 goals", resetCountsText(resetCounts(emptyList(), lib, ResetScope.COLLECTION)))
        assertEquals(RESET_NOTHING, resetCountsText(resetCounts(emptyList(), lib, ResetScope.CARDS)))
    }

    @Test
    fun `goals survive the JSON round trip`() {
        val moshi = com.squareup.moshi.Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(Collection::class.java)
        val p = pile(allGoals)
        val back = adapter.fromJson(adapter.toJson(p))!!
        assertEquals(p.collectionGoals, back.collectionGoals)
        // Left out when null, so an older app's pile reads as "doesn't know about goals".
        assertTrue(!adapter.toJson(pile(null)).contains("collectionGoals"))
    }
}
