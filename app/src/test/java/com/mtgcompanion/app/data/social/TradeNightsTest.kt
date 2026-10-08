package com.mtgcompanion.app.data.social

import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionEntry
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckCardEntry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Trade nights. The cases in tradeNightVectors.json are the web app's too (MtgCompanionWeb's
 * tests/social/tradeNights.test.ts, which keeps the same file), so both apps match wants, build
 * lists and suggest the same fair trades.
 */
class TradeNightsTest {

    private val v: JSONObject = JSONObject(
        javaClass.classLoader!!.getResourceAsStream("tradeNightVectors.json")!!.bufferedReader().use { it.readText() }
    )

    // ---- Reading the vectors ----

    private fun JSONObject.str(k: String): String? = if (!has(k) || isNull(k)) null else getString(k)
    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

    private val prices: Map<String, CardPrice> = v.getJSONObject("prices").let { p ->
        p.keys().asSequence().associateWith { id ->
            val o = p.getJSONObject(id)
            CardPrice(if (o.isNull("usd")) null else o.getDouble("usd"), if (o.isNull("foil")) null else o.getDouble("foil"))
        }
    }

    private fun want(o: JSONObject) = NightWant(o.getString("name"), o.getInt("weight"))
    private fun card(o: JSONObject) = NightCard(
        o.getString("scryfallId"), o.getString("name"), o.str("imageUrl"), o.optBoolean("foil"), o.getInt("quantity"),
        o.str("collectionId"), o.str("condition"), o.optBoolean("spare")
    )
    private fun tradeCard(o: JSONObject) = TradeCard(
        o.getString("scryfallId"), o.getString("name"), o.str("imageUrl"), o.optBoolean("foil"), o.getInt("quantity"), o.str("collectionId"), o.str("condition")
    )
    private fun list(o: JSONObject) = NightList(
        o.getString("userId"), o.getString("name"), o.getJSONArray("cards").objects().map(::card), o.getJSONArray("wants").objects().map(::want)
    )
    private fun source(o: JSONObject) = NightSource(o.getString("kind"), o.str("id"), o.getString("name"))
    private fun suggestion(o: JSONObject) = NightSuggestion(
        o.getString("userId"), o.getString("name"), o.getJSONArray("get").objects().map(::tradeCard), o.getJSONArray("give").objects().map(::tradeCard),
        o.getDouble("getValue"), o.getDouble("giveValue")
    )

    private val bring = v.getJSONObject("bring")
    private val collections: List<Collection> = bring.getJSONArray("collections").objects().map { c ->
        Collection(c.getString("id"), c.getString("name"), c.getJSONArray("entries").objects().map { e ->
            CollectionEntry(
                e.getString("scryfallId"), e.getString("name"), e.str("imageUrl"), e.getInt("quantity"), e.getInt("foilQuantity"),
                forTrade = if (e.has("forTrade")) e.getInt("forTrade") else null, condition = e.str("condition")
            )
        }, createdAt = 0, type = c.getString("type"))
    }

    private fun assertSuggestion(expected: NightSuggestion?, actual: NightSuggestion?, about: String) {
        if (expected == null) { assertNull(about, actual); return }
        assertEquals(about, expected.copy(getValue = 0.0, giveValue = 0.0), actual!!.copy(getValue = 0.0, giveValue = 0.0))
        assertEquals(about, expected.getValue, actual.getValue, 1e-9)
        assertEquals(about, expected.giveValue, actual.giveValue, 1e-9)
    }

    // ---- The cases ----

    @Test
    fun wantsWishlistOverDecksOverGoals() {
        for (c in v.getJSONArray("mergeWants").objects()) {
            assertEquals(
                c.getString("about"),
                c.getJSONArray("expect").objects().map(::want),
                mergeWants(c.getJSONArray("wishlist").strings(), c.getJSONArray("decks").strings(), c.getJSONArray("goals").strings())
            )
        }
    }

    @Test
    fun bringListFromBindersAndTheEventBag() {
        assertEquals(bring.getJSONArray("defaultSources").objects().map(::source), defaultSources(collections))
        val decksUse = bring.getJSONArray("decksUse").strings().toSet()
        for (c in bring.getJSONArray("cases").objects()) {
            assertEquals(
                c.getString("about"),
                c.getJSONArray("expect").objects().map(::card),
                bringCards(collections, c.getJSONArray("sources").objects().map(::source), c.getJSONArray("bagNames").strings(), decksUse)
            )
        }
    }

    @Test
    fun wantedHereAndTheyWantFromYou() {
        val l = v.getJSONObject("lists")
        val me = list(l.getJSONObject("me"))
        val others = l.getJSONArray("others").objects().map(::list)
        val here = l.getJSONArray("wantedHere").objects().map { r ->
            WantedHereRow(r.getString("name"), r.getInt("weight"), r.getJSONArray("from").objects().map { f -> BroughtBy(f.getString("userId"), f.getString("name"), card(f.getJSONObject("card"))) })
        }
        assertEquals(here, wantedHere(me.wants, others))
        val they = l.getJSONArray("theyWant").objects().map { r ->
            TheyWantRow(r.getString("userId"), r.getString("name"), r.getJSONArray("cards").objects().map { c -> WantedCard(card(c.getJSONObject("card")), c.getInt("weight")) })
        }
        assertEquals(they, theyWantFromYou(me.cards, others))
    }

    @Test
    fun suggestedTradesAreFairBundles() {
        for (c in v.getJSONArray("suggest").objects()) {
            val expected = if (c.isNull("expect")) null else suggestion(c.getJSONObject("expect"))
            assertSuggestion(expected, suggestTrade(list(c.getJSONObject("me")), list(c.getJSONObject("them")), prices), c.getString("about"))
        }
        val l = v.getJSONObject("lists")
        val me = list(l.getJSONObject("me"))
        val others = l.getJSONArray("others").objects().map(::list)
        val expected = l.getJSONArray("suggested").objects().map(::suggestion)
        val actual = suggestedTrades(me, others, prices)
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) -> assertSuggestion(e, a, "lists") }
        assertEquals(l.getJSONArray("priceIds").strings(), priceIdsNeeded(me, others))
        for (s in actual) assertTrue(abs(s.getValue - s.giveValue) <= maxOf(2.0, 0.1 * maxOf(s.getValue, s.giveValue)))
    }

    @Test
    fun tradeTableAgreedThenWaitingThenDone() {
        val t = v.getJSONObject("table")
        val trades = t.getJSONArray("trades").objects().map(::parseTrade)
        val rows = tradeTable(trades, t.getString("me"))
        assertEquals(
            t.getJSONArray("expect").objects().map { Triple(it.getString("id"), it.getString("other"), it.getString("state")) },
            rows.map { Triple(it.trade.id, it.other, it.state.wire) }
        )
        assertEquals(t.str("line"), tableLine(rows))
        assertNull(tableLine(emptyList()))
    }

    // ---- The user's own library (the web app's test has the same case) ----

    @Test
    fun wantsFromTheLibrary() {
        val cols = listOf(
            Collection("wl", "Wishlist", listOf(CollectionEntry("tithe", "Smothering Tithe", null)), createdAt = 0, type = "WISHLIST"),
            Collection("b", "Binder", listOf(CollectionEntry("sol", "Sol Ring", null, quantity = 1)), createdAt = 0, type = "OWNED")
        )
        fun deck(id: String, name: String, cards: List<String>, archived: Boolean? = null) =
            Deck(id = id, name = name, cards = cards.map { DeckCardEntry(it, it, null, quantity = 1) }, ownership = "VIRTUAL", archived = archived)
        val decks = listOf(
            deck("d1", "Krenko", listOf("Sol Ring", "Goblin Bombardment")),
            deck("d2", "Old", listOf("Archived Card"), archived = true),
            deck("d3", "Bring to game night", listOf("Bag Card"))
        )
        assertEquals(listOf(NightWant("Smothering Tithe", 3), NightWant("Goblin Bombardment", 2)), nightWantsOf(cols, decks))
        assertEquals(listOf("Bag Card"), bagNamesOf(decks))
        assertEquals(setOf("archived card", "goblin bombardment", "sol ring"), nightDecksUse(decks))
    }

    // ---- Reading trade_night ----

    @Test
    fun readingTradeNight() {
        assertNull(parseTradeNight("null"))
        assertNull(parseTradeNight("{}"))
        val n = parseTradeNight(
            """{"nightId":"n1","going":true,"open":true,
               "mine":{"user":{"user_id":"me","username":"me","display_name":"Me","avatar_path":null},"sources":[{"kind":"binder","id":"tb","name":"Trade binder"},{"kind":"x"}],
                       "cards":[{"scryfallId":"s","name":"Sol Ring","quantity":1,"spare":true},{"name":"bad"}],"wants":[{"name":"A","weight":3},{"name":"B"}],"updatedAt":5},
               "others":[{"user":{"user_id":"bob","display_name":"Bob"},"cards":[],"wants":[]},{"user":{}}],
               "trades":[{"id":"t","from_user":"me","to_user":"bob","status":"accepted","from_applied":true},{"nope":1}]}"""
        )!!
        assertTrue(n.going)
        assertEquals(listOf(NightSource.binder("tb", "Trade binder")), n.mine?.sources)
        assertEquals(listOf(NightCard("s", "Sol Ring", spare = true)), n.mine?.cards)
        assertEquals(listOf(NightWant("A", 3)), n.mine?.wants)
        assertEquals(listOf("Bob"), n.others.map { it.user.displayName })
        assertEquals(1, n.trades.size)
        assertEquals(TradeStatus.ACCEPTED, n.trades[0].status)
        assertTrue(n.trades[0].fromApplied)
        assertNull(parseTradeNight("""{"nightId":"n","mine":null}""")?.mine)
    }

    @Test
    fun listsGoToTheServerAsTheyCameBack() {
        val cards = listOf(NightCard("s", "Sol Ring", "img", foil = true, quantity = 2, collectionId = "tb", condition = "LP", spare = true))
        val back = nightCardsJson(cards).let { a -> (0 until a.length()).map { parseNightCard(a.getJSONObject(it)) } }
        assertEquals(cards, back)
        val sources = sourcesJson(listOf(NightSource.binder("tb", "Trade binder"), NightSource.bag()))
        assertEquals(listOf("binder", "bag"), (0 until sources.length()).map { sources.getJSONObject(it).getString("kind") })
        assertEquals("tb", sources.getJSONObject(0).getString("id"))
        assertTrue(!sources.getJSONObject(1).has("id"))
        assertEquals(BAG_SOURCE_NAME, sources.getJSONObject(1).getString("name"))
        val wants = wantsJson(listOf(NightWant("A", 3)))
        assertEquals("A", wants.getJSONObject(0).getString("name"))
        assertEquals(3, wants.getJSONObject(0).getInt("weight"))
    }
}
