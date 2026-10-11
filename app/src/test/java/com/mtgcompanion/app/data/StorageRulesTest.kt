package com.mtgcompanion.app.data

import com.mtgcompanion.app.data.supabase.ItemMerge
import com.mtgcompanion.app.ui.collection.AdvancedFacts
import com.mtgcompanion.app.ui.collection.AdvancedFilter
import com.mtgcompanion.app.ui.collection.CollectionFilter
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.mtgcompanion.app.ui.collection.CardFacts as BasicFacts
import com.mtgcompanion.app.ui.collection.CardsFilter
import com.mtgcompanion.app.ui.collection.FilterFacts
import com.mtgcompanion.app.ui.collection.filterCopies

/**
 * A place's rules. The cases in storageRulesVectors.json are the web app's too (MtgCompanionWeb's
 * tests/collection/storageRules.test.ts, which keeps the file), so both apps put every card in the
 * same place, section and spot.
 */
class StorageRulesTest {

    private val v: JSONObject = JSONObject(
        javaClass.classLoader!!.getResourceAsStream("storageRulesVectors.json")!!.bufferedReader().use { it.readText() }
    )

    // ---- Reading the vectors ----

    private fun JSONObject.str(k: String): String? = if (isNull(k)) null else getString(k)
    private fun JSONObject.dbl(k: String): Double? = if (isNull(k)) null else getDouble(k)
    private fun JSONObject.bool(k: String): Boolean? = if (isNull(k)) null else getBoolean(k)
    private fun JSONObject.int(k: String): Int? = if (isNull(k)) null else getInt(k)
    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.strings(k: String): List<String>? = if (isNull(k)) null else getJSONArray(k).strings()

    private fun plain(x: Any?): Any? = when (x) {
        is JSONObject -> LinkedHashMap<String, Any?>().also { m -> x.keys().forEach { k -> m[k] = plain(x.opt(k)) } }
        is JSONArray -> (0 until x.length()).map { plain(x.opt(it)) }
        is Number -> x.toDouble()
        JSONObject.NULL -> null
        else -> x
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapOrNull(o: JSONObject, k: String): Map<String, Any?>? = if (o.isNull(k)) null else plain(o.getJSONObject(k)) as Map<String, Any?>

    private fun level(o: JSONObject) = SplitLevel(
        o.getString("by"),
        cuts = if (o.isNull("cuts")) null else o.getJSONArray("cuts").let { a -> (0 until a.length()).map { a.getDouble(it) } },
        letters = o.strings("letters"), sets = o.strings("sets"), lands = o.bool("lands"), restOn = o.bool("restOn")
    )

    private fun levelOrNull(o: JSONObject, k: String) = if (o.isNull(k)) null else level(o.getJSONObject(k))

    private fun place(o: JSONObject) = StoragePlace(
        id = o.getString("id"), name = o.getString("name"), kind = o.getString("kind"), parentId = o.str("parentId"),
        sections = o.strings("sections"), pocketsPerPage = o.int("pocketsPerPage"), sortRule = o.str("sortRule"),
        createdAt = o.getLong("createdAt"), capacity = o.int("capacity"), pages = o.int("pages"),
        holds = mapOrNull(o, "holds"), sectionsBy = levelOrNull(o, "sectionsBy"), sortBy = o.strings("sortBy")
    )

    private val places by lazy { v.getJSONArray("places").objects().map { place(it) } }
    private val order by lazy { v.getJSONArray("order").strings() }
    private fun placeOf(ref: Any): StoragePlace = if (ref is JSONObject) place(ref) else places.first { it.id == ref }
    private fun JSONObject.placeRef(k: String): Any = opt(k)

    private fun cardJson(key: String): JSONObject = v.getJSONObject("cards").getJSONObject(key)

    private fun factsOf(key: String): CardFacts {
        val c = cardJson(key)
        return CardFacts(
            c.getString("name"), c.strings("colors"), c.str("typeLine"), c.str("set"), c.str("collectorNumber"),
            colorIdentity = c.strings("colorIdentity"), cmc = c.dbl("cmc"), rarity = c.str("rarity"), usd = c.dbl("usd"), usdFoil = c.dbl("usdFoil"),
            foil = c.bool("foil") ?: false
        )
    }

    /** A vector card as the rules judge it: no filter facts when its data isn't here. */
    private fun ruleCard(key: String): RuleCard {
        val c = cardJson(key)
        val facts = factsOf(key)
        if (c.bool("noData") == true) return RuleCard(facts)
        val identity = c.strings("colorIdentity").orEmpty()
        return RuleCard(
            facts,
            basic = BasicFacts(identity.map { it[0] }.toSet(), c.getString("typeLine"), c.getString("rarity"), c.str("text") ?: "", c.getString("set").lowercase()),
            advanced = AdvancedFacts(
                colors = c.strings("colors").orEmpty(), identity = identity, cmc = c.dbl("cmc"), set = c.getString("set").lowercase(),
                usd = c.dbl("usd"), usdFoil = c.dbl("usdFoil")
            ),
            roleTags = c.strings("roleTags"),
            userTags = c.strings("userTags").orEmpty()
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun collection(o: JSONObject) = Collection(
        id = o.getString("id"), name = o.getString("name"), createdAt = o.getLong("createdAt"), type = o.getString("type"),
        storagePlaces = if (o.isNull("storagePlaces")) null else o.getJSONArray("storagePlaces").objects().map { place(it) },
        placeOrder = o.strings("placeOrder")
    )

    // ---- The shared cases ----

    @Test
    fun `the places with rules are tried in the order the user chose`() {
        for (c in v.getJSONArray("rulePlaces").objects()) {
            assertEquals(c.toString(), c.getJSONArray("ids").strings(), rulePlaces(places, c.getJSONArray("order").strings()).map { it.id })
        }
    }

    @Test
    fun `a card goes to the first place in rule order that holds it`() {
        for (c in v.getJSONArray("placeFor").objects()) {
            val o = c.strings("order") ?: order
            assertEquals(c.toString(), c.str("place"), placeFor(places, o, ruleCard(c.getString("card")))?.id)
        }
    }

    @Test
    fun `what a place holds is a Cards filter`() {
        for (c in v.getJSONArray("holds").objects()) {
            val h = holdsFromMap(mapOrNull(c, "holds"))
            for (k in c.getJSONArray("yes").strings()) assertTrue("$k fits", holdsMatch(h, ruleCard(k)))
            for (k in c.getJSONArray("no").strings()) assertFalse("$k doesn't fit", holdsMatch(h, ruleCard(k)))
        }
    }

    @Test
    fun `an older app's sorting rule reads as the rules it meant`() {
        for (c in v.getJSONArray("migration").objects()) {
            val p = place(c.getJSONObject("place"))
            val r = placeRules(p)
            assertEquals(c.toString(), levelOrNull(c, "sectionsBy"), r.sectionsBy)
            assertEquals(c.toString(), c.getJSONArray("sortBy").strings(), r.sortBy)
            assertEquals(c.toString(), c.getBoolean("hasRule"), hasRule(p))
        }
        for (c in v.getJSONArray("sortRuleFor").objects()) {
            assertEquals(c.toString(), c.str("sortRule"), sortRuleFor(levelOrNull(c, "sectionsBy"), c.getJSONArray("sortBy").strings())?.name)
        }
    }

    @Test
    fun `saving rules writes the new fields and the nearest old rule`() {
        for (c in v.getJSONArray("withRules").objects()) {
            val p = storagePlace(withRules(place(c.getJSONObject("place")), mapOrNull(c, "holds"), levelOrNull(c, "sectionsBy"), c.getJSONArray("sortBy").strings()))
            val e = c.getJSONObject("expect")
            assertEquals(c.toString(), mapOrNull(e, "holds"), p.holds)
            assertEquals(c.toString(), levelOrNull(e, "sectionsBy"), p.sectionsBy)
            assertEquals(c.toString(), e.strings("sortBy"), p.sortBy)
            assertEquals(c.toString(), e.str("sortRule"), p.sortRule)
        }
    }

    @Test
    fun `sections come from one level of the sorting recipes`() {
        for (c in v.getJSONArray("sections").objects()) {
            assertEquals(c.toString(), c.getString("label"), sectionLabel(level(c.getJSONObject("sectionsBy")), factsOf(c.getString("card"))))
        }
        for (c in v.getJSONArray("ruleSections").objects()) {
            assertEquals(c.toString(), c.getJSONArray("sections").strings(), ruleSections(levelOrNull(c, "sectionsBy")))
        }
        for (c in v.getJSONArray("sectionFor").objects()) {
            assertEquals(c.toString(), c.str("section"), ruleSectionFor(placeOf(c.placeRef("place")), factsOf(c.getString("card"))))
        }
    }

    @Test
    fun `where in a section a card goes`() {
        for (c in v.getJSONArray("hints").objects()) {
            val got = sortHint(c.getJSONArray("sortBy").strings(), factsOf(c.getString("card")), c.getJSONArray("others").strings().map { factsOf(it) })
            assertEquals(c.toString(), c.str("hint"), got)
        }
    }

    @Test
    fun `a place says its rules in a line`() {
        for (c in v.getJSONArray("lines").objects()) assertEquals(c.toString(), c.getString("line"), rulesLine(placeOf(c.placeRef("place"))))
        assertEquals("Box · Holds blue cards · by card type · A–Z", placeSubtitle(placeOf("blue")))
    }

    @Test
    fun `places whose rules overlap say which comes first`() {
        for (c in v.getJSONArray("overlaps").objects()) {
            val got = overlaps(placeOf(c.getString("draft")), places, c.strings("order") ?: order, c.getJSONArray("cards").strings().map { ruleCard(it) }).map { it.line }
            assertEquals(c.toString(), c.getJSONArray("lines").strings(), got)
        }
    }

    @Test
    fun `an older app's save keeps the rules`() {
        for (c in v.getJSONArray("keep").objects()) {
            val out = keepPlaceRules(collection(c.getJSONObject("source")), collection(c.getJSONObject("theirs")))
            val e = c.getJSONObject("expect")
            assertEquals(c.getString("why"), e.strings("placeOrder"), out.placeOrder)
            assertEquals(c.getString("why"), e.getJSONArray("storagePlaces").objects().map { place(it) }, out.storagePlaces)
        }
    }

    @Test
    fun `Into my storage - a pile per place in rule order, filed there`() {
        for (c in v.getJSONArray("templates").objects()) {
            val storage = c.getJSONArray("storage").let { a -> (0 until a.length()).map { a.getJSONArray(it).let { p -> p.getString(0) to p.getString(1) } } }
            val t = recipeTemplates(storage = storage)
            assertEquals(c.getString("first"), t[0].id)
            if (c.isNull("piles")) continue
            val d = derivePiles(t[0]) { "$$it" }
            assertEquals(c.getJSONArray("piles").strings(), d.piles.map { it.name })
            for (k in c.getJSONArray("cards").objects()) {
                val key = levelKey(t[0], RecipeCard("X", place = k.str("place")), 1.0)
                val pile = d.piles.first { it.key == key }
                assertEquals(k.getString("pile"), pile.name)
                assertEquals(k.getString("goesTo"), pileGoesTo(t[0], pile))
            }
        }
    }

    // ---- The app's own ----

    private fun entry(id: String, name: String, placeId: String, qty: Int, section: String? = null) =
        CollectionEntry(id, name, null, quantity = qty, places = listOf(CopyPlace(placeId, qty, section = section)))

    private fun library(list: List<StoragePlace> = places) = listOf(
        Collection(
            UNSORTED_COLLECTION_ID, UNSORTED_COLLECTION_NAME, createdAt = 0, storagePlaces = list, placeOrder = order,
            entries = listOf(
                entry("counterspell", "Counterspell", "blue", 1, "Instants"),
                entry("cryptic", "Cryptic Command", "blue", 1, "Sorceries"),
                entry("bolt", "Lightning Bolt", "blue", 2, "Instants"),
                entry("brainstorm", "Brainstorm", "blue", 1, "Instants")
            )
        )
    )

    @Test
    fun `put away - the place, the section and where in it`() {
        val cols = library()
        val best = bestPlaceByRule(placesOf(cols), factsOf("ponder"), cols, ruleCard("ponder"))!!
        assertEquals("blue", best.second.id)
        assertEquals(Spot("blue", section = "Sorceries"), best.first)
        assertEquals("Sorceries › after Cryptic Command", suggestSpot(placeOf("blue"), factsOf("ponder"), cols).second)
        assertEquals("Instants › next to Counterspell", ruleSpot(placeOf("blue"), factsOf("counterspell"), cardsIn(cols, "blue")).second)
        // Without its data, a card is judged by its sorting facts; with only its name, only the places holding everything take it.
        assertEquals("blue", bestPlaceByRule(placesOf(cols), factsOf("mystery"), cols)?.second?.id)
        assertEquals("bulk", bestPlaceByRule(placesOf(cols), CardFacts("Mystery Card"), cols)?.second?.id)
        assertTrue(canLetAppChoose(places))
        assertFalse(canLetAppChoose(listOf(placeOf("plain"))))
    }

    @Test
    fun `put away passes over a full place when asked`() {
        val full = places.map { if (it.id == "blue") it.copy(capacity = 5) else it }
        val cols = library(full)
        assertEquals("blue", bestPlaceByRule(full, factsOf("ponder"), cols, ruleCard("ponder"))?.second?.id)
        assertEquals("bulk", bestPlaceByRule(full, factsOf("ponder"), cols, ruleCard("ponder"), needRoom = true)?.second?.id)
    }

    @Test
    fun `check a place - what doesn't belong, and moving it`() {
        val cols = library()
        val cards = v.getJSONObject("cards")
        val found = misfits(placeOf("blue"), cols, { c -> if (cards.has(c.entry.scryfallId)) ruleCard(c.entry.scryfallId) else null })
        assertEquals(
            listOf("Cryptic Command: In Sorceries — belongs in Instants", "Lightning Bolt: Doesn't belong here — belongs in Red box"),
            found.map { "${it.card.entry.name}: ${it.line}" }
        )
        val bolt = found.first { it.wrongPlace }
        assertEquals(listOf(CopyPlace("red", 2)), moveMisfit(cols, bolt, Spot("red"))[0].entries.first { it.scryfallId == "bolt" }.places)
        val cryptic = found.first { !it.wrongPlace }
        assertEquals(listOf(CopyPlace("blue", 1, section = "Instants")), moveMisfit(cols, cryptic, null)[0].entries.first { it.scryfallId == "cryptic" }.places)
    }

    @Test
    fun `a section listed in its order`() {
        val cards = listOf("ponder", "cryptic", "brainstorm", "counterspell").map { factsOf(it) }
        assertEquals(listOf("Brainstorm", "Ponder", "Counterspell", "Cryptic Command"), sortedForRule(listOf("MANA_VALUE", "NAME"), cards) { it }.map { it.name })
        assertEquals(listOf("Ponder", "Cryptic Command", "Brainstorm", "Counterspell"), sortedForRule(emptyList(), cards) { it }.map { it.name })
        assertTrue(compareForSort(listOf("VALUE"), cards[1], cards[0]) < 0)
    }

    @Test
    fun `the rule order is kept on the Unsorted pile and moved place by place`() {
        val cols = withPlaceOrder(library(), listOf("blue", "red", "blue"))
        assertEquals(listOf("blue", "red"), placeOrderOf(cols))
        assertEquals(listOf("bulk", "red", "blue", "rares", "lands", "trade"), movedInOrder(places, listOf("red", "blue"), "bulk", 0))
        assertEquals(listOf("blue", "rares", "lands", "trade", "bulk", "red"), movedInOrder(places, listOf("red", "blue"), "red", 9))
    }

    @Test
    fun `two devices' rules merge field by field, and the rule order too`() {
        val base = library()[0]
        val mine = base.copy(placeOrder = listOf("blue", "red"))
        val blueTheirs = withRules(placeOf("blue"), holdsMap(CollectionFilter(colors = setOf('U', 'B')), AdvancedFilter()), SplitLevel("TYPE"), listOf("NAME"))
        val theirs = base.copy(storagePlaces = places.map { if (it.id == "blue") blueTheirs else it })
        val merged = ItemMerge.mergeCollections(base, mine, theirs, minePreferred = true)
        assertEquals(listOf("blue", "red"), merged.placeOrder)
        assertEquals(
            mapOf("basic" to mapOf("type" to "", "text" to "", "colors" to listOf("U", "B"), "rarities" to emptyList<String>())),
            merged.storagePlaces!!.first { it.id == "blue" }.holds
        )
        // An older app's save of the pile (no rules, no order) keeps both.
        val older = base.copy(placeOrder = null, storagePlaces = places.map { StoragePlace(it.id, it.name, it.kind, createdAt = it.createdAt, sortRule = it.sortRule, sections = it.sections) })
        val kept = ItemMerge.mergeCollections(base, base, older, minePreferred = true)
        assertEquals(order, kept.placeOrder)
        assertEquals(listOf("MANA_VALUE", "NAME"), kept.storagePlaces!!.first { it.id == "red" }.sortBy)
    }

    @Test
    fun `the Storage filters' Doesn't fit its place`() {
        val cols = library()
        val byId = places.associateBy { it.id }
        val cards = cardsIn(cols, "blue")
        assertEquals(
            mapOf("Brainstorm" to true, "Counterspell" to true, "Cryptic Command" to false, "Lightning Bolt" to false),
            cards.associate { it.entry.name to copyFitsItsPlace(byId, it, ruleCard(it.entry.scryfallId)) }
        )
        assertNull(copyFitsItsPlace(byId, cards[0], null))
        val facts = FilterFacts(fitsItsPlace = { c -> copyFitsItsPlace(byId, c, ruleCard(c.entry.scryfallId)) })
        assertEquals(listOf("Cryptic Command", "Lightning Bolt"), filterCopies(cards, CardsFilter(misfitsOnly = true), facts).map { it.entry.name })
        assertEquals(1, CardsFilter(misfitsOnly = true).count)
    }

    @Test
    fun `holds with nothing on is everything`() {
        assertNull(holdsMap(CollectionFilter(), AdvancedFilter()))
        assertNull(holdsFromMap(mapOf("basic" to mapOf("type" to "", "text" to "", "colors" to emptyList<String>(), "rarities" to emptyList<String>()))))
        assertTrue(fitsPlace(placeOf("plain"), ruleCard("mystery")))
    }

    @Test
    fun `a place's rules survive the app's own JSON`() {
        val cols = library()
        val adapter = localMoshi.adapter(Collection::class.java)
        val back = adapter.fromJson(adapter.toJson(cols[0]))!!
        assertEquals(cols[0].placeOrder, back.placeOrder)
        assertEquals(places, back.storagePlaces)
        assertEquals(holdsOf(placeOf("trade")), holdsOf(back.storagePlaces!!.first { it.id == "trade" }))
    }
}
