package com.mtgcompanion.app.data

import com.mtgcompanion.app.data.social.TradeCard
import com.mtgcompanion.app.data.social.TradeMatch
import com.mtgcompanion.app.data.supabase.ItemMerge
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sorting recipes. The cases in sortRecipeVectors.json are the web app's too (MtgCompanionWeb's
 * tests/collection/sortRecipes.test.ts, which keeps the file), so both apps make the same piles and
 * send every card to the same one.
 */
class SortRecipesTest {

    private val v: JSONObject = JSONObject(
        javaClass.classLoader!!.getResourceAsStream("sortRecipeVectors.json")!!.bufferedReader().use { it.readText() }
    )

    private fun whole(n: Double): String = if (n == Math.floor(n)) n.toLong().toString() else n.toString()
    private val fmt = { n: Double -> "$" + whole(n) }

    // ---- Reading the vectors ----

    private fun JSONObject.str(k: String): String? = if (isNull(k)) null else getString(k)
    private fun JSONObject.dbl(k: String): Double? = if (isNull(k)) null else getDouble(k)
    private fun JSONObject.bool(k: String): Boolean? = if (isNull(k)) null else getBoolean(k)
    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.strings(k: String): List<String>? = if (isNull(k)) null else getJSONArray(k).strings()

    private fun level(o: JSONObject) = SplitLevel(
        o.getString("by"),
        cuts = if (o.isNull("cuts")) null else o.getJSONArray("cuts").let { a -> (0 until a.length()).map { a.getDouble(it) } },
        letters = o.strings("letters"), sets = o.strings("sets"), lands = o.bool("lands"), restOn = o.bool("restOn")
    )

    private fun recipe(o: JSONObject) = SortRecipe(
        o.getString("id"), o.getString("name"), o.getJSONArray("pullOut").strings(), o.getJSONArray("levels").objects().map { level(it) },
        o.getJSONArray("apart").strings(),
        goTo = if (o.isNull("goTo")) null else o.getJSONArray("goTo").objects().map { PileGoTo(it.getString("pile"), it.getString("to")) },
        createdAt = o.getLong("createdAt")
    )

    private fun card(o: JSONObject) = RecipeCard(
        name = o.getString("name"), colors = o.strings("colors"), colorIdentity = o.strings("colorIdentity"), typeLine = o.str("typeLine"),
        set = o.str("set"), collectorNumber = o.str("collectorNumber"), cmc = o.dbl("cmc"), rarity = o.str("rarity"),
        usd = o.dbl("usd"), usdFoil = o.dbl("usdFoil"), foil = o.bool("foil") ?: false, lang = o.str("lang"), played = o.bool("played") ?: false
    )

    private fun facts(o: JSONObject) = CardFacts(o.getString("name"), o.strings("colors"), o.str("typeLine"), o.str("set"), o.str("collectorNumber"))

    private fun ctx(o: JSONObject): SmartContext {
        val needs = o.getJSONObject("deckNeeds")
        val wants = o.getJSONObject("friendWants")
        val owned = o.getJSONObject("owned")
        return SmartContext(
            deckNeeds = needs.keys().asSequence().associateWith { k -> needs.getJSONArray(k).objects().map { DeckNeed(it.getString("deckId"), it.getString("deck"), it.getInt("qty")) } },
            friendWants = wants.keys().asSequence().associateWith { k -> wants.getJSONArray(k).objects().map { FriendWant(it.getString("id"), it.getString("name")) } },
            binders = o.getJSONArray("binders").objects().map { b ->
                OrderedBinder(
                    b.getString("placeId"), b.getString("name"), b.str("rule"), b.getInt("pockets"),
                    b.getJSONArray("occupied").objects().map { Placed(it.getInt("index"), facts(it.getJSONObject("facts"))) },
                    b.getJSONArray("names").strings(), b.getJSONArray("sets").strings()
                )
            },
            owned = owned.keys().asSequence().associateWith { owned.getInt(it) }
        )
    }

    private fun scanOf(i: Int, card: RecipeCard, c: RecipeChoice) = RecipeScan(
        (i + 1).toLong(), "id$i", card.name, null, card, CardFacts(card.name, card.colors.orEmpty(), card.typeLine, card.set, card.collectorNumber),
        CollectionEntry("id$i", card.name, null), c.pile, c.key, c.reason, c.also
    )

    // ---- The shared cases ----

    @Test
    fun thePilesEachRecipeMakesInTableOrderCappedWithAWarning() {
        for (d in v.getJSONArray("derive").objects()) {
            val r = recipe(d.getJSONObject("recipe"))
            val got = derivePiles(r, fmt)
            assertEquals(r.name, d.getJSONArray("piles").strings(), got.piles.map { "${it.number} ${it.key} ${it.name} ${it.band}" })
            assertEquals(r.name, d.getInt("wanted"), got.wanted)
            assertEquals(r.name, d.getBoolean("capped"), got.capped)
            assertEquals(r.name, d.str("warning"), capWarning(got))
            assertEquals(r.name, d.getString("line"), recipeLine(r, fmt))
            assertTrue(got.piles.size <= MAX_RECIPE_PILES)
        }
    }

    @Test
    fun eachLevelsBucketsAndItsLine() {
        for (l in v.getJSONArray("labels").objects()) {
            val level = level(l.getJSONObject("level"))
            assertEquals(level.toString(), l.getJSONArray("labels").strings(), levelBuckets(level, fmt).map { "${it.key} ${it.label}" })
            assertEquals(level.toString(), l.getString("line"), levelLine(level, fmt))
        }
    }

    @Test
    fun theBucketACardFallsIn() {
        for (b in v.getJSONArray("buckets").objects()) {
            val level = level(b.getJSONObject("level"))
            val c = card(b.getJSONObject("card"))
            assertEquals("$level $c @${b.getDouble("rate")}", b.getString("key"), bucketOf(level, c, b.getDouble("rate")))
        }
    }

    @Test
    fun aSessionSmartPilesFirstThenKeepApartThenTheLevels() {
        val context = ctx(v.getJSONObject("ctx"))
        val cards = v.getJSONArray("session").objects().map { card(it) }
        for (s in v.getJSONArray("sessions").objects()) {
            val r = recipe(s.getJSONObject("recipe"))
            val rate = s.getDouble("rate")
            val d = derivePiles(r, fmt)
            val scans = mutableListOf<RecipeScan>()
            val expect = s.getJSONArray("expect").objects()
            cards.forEachIndexed { i, c ->
                val choice = sortCard(r, d, context, c, scans, rate)
                val pile = d.piles.first { it.number == choice.pile }
                val e = expect[i]
                val what = "${r.name} #$i ${c.name}"
                assertEquals(what, e.getInt("pile"), choice.pile)
                assertEquals(what, e.getString("key"), choice.key)
                assertEquals(what, e.str("reason"), choice.reason?.let { reasonLine(it) })
                assertEquals(what, e.getJSONArray("also").strings(), choice.also.map { alsoLine(it) })
                assertEquals(what, e.getString("spoken"), spokenPile(pile, choice.reason))
                assertEquals(what, e.getString("line"), cardLine(c, null, choice.reason, fmt))
                scans += scanOf(i, c, choice)
            }
            val sum = summarize(r, d, scans)
            val summary = s.getJSONObject("summary")
            assertEquals(summary.getInt("cards"), sum.cards)
            assertEquals(summary.getDouble("usd"), sum.usd, 1e-9)
            assertEquals(r.name, summary.getJSONArray("rows").strings(), sum.rows.map { "${it.from}-${it.to} ${it.name} ${it.cards} ${whole(it.usd)} ${it.detail ?: ""}".trim() })
            for (c in s.getJSONArray("checks").objects()) {
                val checked = mutableListOf<String>()
                val lines = c.getJSONArray("names").strings().map { n ->
                    val res = checkPileCard(d, scans, c.getInt("pile"), checked, n)
                    if (res.belongs) checked += n
                    res.line
                }
                assertEquals(c.getJSONArray("lines").strings(), lines)
            }
        }
    }

    @Test
    fun captureWithoutTappingSteadyFramesAndNeverTheSameCardTwice() {
        for (r in v.getJSONArray("handsFree").objects()) {
            val h = HandsFreeCapture(r.getInt("steady"), r.getInt("gap"))
            val takes = r.getJSONArray("steps").objects().map { s ->
                when {
                    s.has("captured") -> { h.captured(s.getString("captured")); null }
                    s.has("rescan") -> { h.rescan(); null }
                    s.has("missed") -> { h.missed(); null }
                    else -> h.onRead(s.str("read"))
                }
            }
            val want = r.getJSONArray("takes").let { a -> (0 until a.length()).map { if (a.isNull(it)) null else a.getBoolean(it) } }
            assertEquals(r.getJSONArray("steps").toString(), want, takes)
        }
    }

    // ---- The rest, as the web app checks them ----

    @Test
    fun templatesAndANewRecipe() {
        val t = recipeTemplates(listOf("dsk"))
        assertEquals(listOf("Commander by colour", "Binder by set", "Rares by value", "What my collection needs"), t.map { it.name })
        assertEquals(listOf("DSK · #1–99", "DSK · #100–199"), derivePiles(t[1], fmt).piles.subList(3, 5).map { it.name })
        assertEquals(listOf("$20+", "$5–$20", "$1–$5", "under $1"), derivePiles(t[2], fmt).piles.drop(3).map { it.name })
        assertEquals("Value $2+ apart · then colour · 12 piles", recipeLine(newRecipe("n1", 5), fmt))
        assertEquals("1st2nd3rd11th22nd", ordinal(1) + ordinal(2) + ordinal(3) + ordinal(11) + ordinal(22))
    }

    @Test
    fun aRecipeIsKeptTidy() {
        val r = sortRecipe(
            SortRecipe(
                "x", "  ", listOf("TRADE", "DECKS", "NOPE"),
                listOf(SplitLevel("VALUE", cuts = listOf(1.0, 5.0, 5.0, -2.0)), SplitLevel("NAME", letters = listOf("q", "c", "9")), SplitLevel("NUMBER", cuts = listOf(50.0)), SplitLevel("TYPE")),
                listOf("PLAYED", "FOIL"), goTo = listOf(PileGoTo("L:v0", "a"), PileGoTo("L:v0", "b")), createdAt = 3
            )
        )
        assertEquals(
            SortRecipe(
                "x", "My recipe", listOf("DECKS", "TRADE"),
                listOf(SplitLevel("VALUE", cuts = listOf(5.0, 1.0)), SplitLevel("NAME", letters = listOf("A", "C", "Q")), SplitLevel("NUMBER", cuts = listOf(1.0, 50.0))),
                listOf("FOIL", "PLAYED"), goTo = listOf(PileGoTo("L:v0", "b")), createdAt = 3
            ),
            r
        )
    }

    private fun pile(recipes: List<SortRecipe>? = null) =
        Collection(UNSORTED_COLLECTION_ID, UNSORTED_COLLECTION_NAME, createdAt = 0, type = CollectionType.OWNED.name, sortRecipes = recipes)
    private fun r(id: String, name: String, apart: List<String>? = null) = sortRecipe(newRecipe(id, 1).let { if (apart != null) it.copy(name = name, apart = apart) else it.copy(name = name) })

    @Test
    fun recipesMergeRecipeByRecipeEachFieldToWhoeverChangedIt() {
        val base = listOf(r("a", "Bulk"), r("b", "Rares"))
        val mine = listOf(r("a", "Bulk boxes"), r("b", "Rares"), r("c", "Mine"))
        val theirs = listOf(r("a", "Bulk", listOf("FOIL", "FOREIGN")), r("d", "Theirs"))
        val merged = mergeRecipes(base, mine, theirs, true)!!
        assertEquals(listOf("a", "c", "d"), merged.map { it.id })
        assertEquals("Bulk boxes", merged[0].name)
        assertEquals(listOf("FOIL", "FOREIGN"), merged[0].apart)
        assertNull(mergeRecipes(null, null, null, true))
        assertEquals("Theirs", mergeRecipes(base, listOf(r("a", "Mine")), listOf(r("a", "Theirs")), false)!![0].name)
    }

    @Test
    fun aPileSavedByAnAppFromBeforeRecipesKeepsThisDevices() {
        val here = pile(listOf(r("a", "Bulk")))
        val older = pile()
        assertEquals(here.sortRecipes, keepRecipesFromOlderApp(here, older).sortRecipes)
        assertEquals(0, keepRecipesFromOlderApp(here, pile(emptyList())).sortRecipes!!.size)
        assertEquals(here.sortRecipes, ItemMerge.mergeCollections(here, here, older, minePreferred = false).sortRecipes)
        val both = ItemMerge.mergeCollections(pile(emptyList()), pile(listOf(r("a", "Bulk"))), pile(listOf(r("b", "Rares"))), minePreferred = true)
        assertEquals(listOf("a", "b"), both.sortRecipes!!.map { it.id })
    }

    @Test
    fun savingAndDeletingRecipesOnTheUnsortedPile() {
        var cols: List<Collection> = emptyList()
        cols = saveRecipe(cols, r("a", "Bulk"))
        cols = saveRecipe(cols, r("a", "Bulk 2"))
        cols = saveRecipe(cols, r("b", "Rares"))
        assertEquals(listOf("Bulk 2", "Rares"), recipesOf(cols).map { it.name })
        assertTrue(cols[0].isUnsorted)
        assertEquals(listOf("b"), recipesOf(deleteRecipe(cols, "a")).map { it.id })
    }

    @Test
    fun filingBinderGapsIntoTheirBinderDeckNeedsWithNoPlaceTheRestByRule() {
        val red = StoragePlace("red", "Red box", PlaceKind.BOX.name, sortRule = SortRule.COLOUR.name, sections = listOf("Blue", "Red"), createdAt = 1)
        val dsk = StoragePlace("dsk", "Duskmourn", PlaceKind.BINDER.name, sortRule = SortRule.SET.name, createdAt = 2)
        val trade = StoragePlace("trade", "Trade binder", PlaceKind.BINDER.name, createdAt = 3)
        val cols = listOf(pile().copy(storagePlaces = listOf(red, dsk, trade)))
        var recipe = sortRecipe(SortRecipe("r", "Bulk", listOf("DECKS", "BINDER", "TRADE"), listOf(SplitLevel("COLOUR")), emptyList(), createdAt = 1))
        val d = derivePiles(recipe, fmt)
        val tradePile = d.piles.first { it.key == "S:TRADE" }
        assertEquals("", pileGoesTo(recipe, tradePile))
        assertEquals(BY_RULE, pileGoesTo(recipe, d.piles.first { it.key == "L:R" }))
        recipe = withGoTo(recipe, "S:TRADE", "trade")
        assertEquals("trade", pileGoesTo(recipe, tradePile))
        fun scan(id: String, name: String, key: String, reason: SortReason? = null, filed: Boolean? = null) = RecipeScan(
            id.drop(1).toLong(), id, name, null, RecipeCard(name, colors = listOf("R")), CardFacts(name, listOf("R"), "Instant"),
            CollectionEntry(id, name, null), d.piles.first { it.key == key }.number, key, reason, filed = filed
        )
        val scans = listOf(
            scan("s1", "Shock", "L:R"),
            scan("s2", "Goblin Chieftain", "S:DECKS", SortReason("DECKS", deckId = "k", deck = "Krenko")),
            scan("s3", "Unholy Annex", "S:BINDER", SortReason("BINDER", placeId = "dsk", binder = "Duskmourn", page = 1, slot = 2)),
            scan("s4", "Lightning Bolt", "S:TRADE", SortReason("TRADE", copy = 5)),
            scan("s5", "Fling", "L:R", filed = true)
        )
        val filed = fileRecipe(cols, recipe, d, scans)
        assertEquals(4, filed.added)
        assertEquals(
            listOf("Shock → Red box › Red", "Goblin Chieftain → No place yet", "Unholy Annex → Duskmourn", "Lightning Bolt → Trade binder"),
            filed.steps.map { "${it.scan.name} → ${it.to}" }
        )
        val entries = filed.collections.first { it.isUnsorted }.entries
        assertEquals(
            listOf("Goblin Chieftain 1 ", "Lightning Bolt 1 trade", "Shock 1 red:Red", "Unholy Annex 1 dsk"),
            entries.map { e -> "${e.name} ${e.quantity} " + e.places.orEmpty().joinToString(",") { p -> p.placeId + (p.section?.let { ":$it" } ?: "") } }.sorted()
        )
    }

    @Test
    fun sendToPileNInsteadTheNextSmartPileThatWantsItElseItsOwnPile() {
        val recipe = recipe(v.getJSONArray("sessions").getJSONObject(0).getJSONObject("recipe"))
        val d = derivePiles(recipe, fmt)
        val c = RecipeCard("Goblin Chieftain", colors = listOf("R"), typeLine = "Creature", usd = 0.6)
        val deck = SortReason("DECKS", deckId = "k", deck = "Krenko")
        val friend = SortReason("FRIENDS", friend = "Priya", friendId = "u-priya")
        assertEquals(2, otherPile(recipe, d, c, 1, deck, listOf(friend), 1.0)?.pile)
        assertEquals(listOf(deck), otherPile(recipe, d, c, 1, deck, listOf(friend), 1.0)?.also)
        assertEquals("L:v1/R", otherPile(recipe, d, c, 2, friend, emptyList(), 1.0)?.key)
        assertNull(otherPile(recipe, d, c, 11, null, emptyList(), 1.0))
    }

    @Test
    fun pileSignsEachPilesNumberAndNameTwoToAPage() {
        val d = derivePiles(recipe(v.getJSONArray("sessions").getJSONObject(0).getJSONObject("recipe")), fmt)
        val a4 = pileSignsHtml(d.piles.subList(6, 8), "A4", "Bulk <signs>")
        assertTrue(a4.contains("size:A4 portrait"))
        assertTrue(a4.contains("<div class=\"num\">7</div><div class=\"name\">$2 and up</div>"))
        assertTrue(a4.contains("<div class=\"num\">8</div><div class=\"name\">White</div>"))
        assertTrue(a4.contains("<title>Bulk &lt;signs&gt;</title>"))
        assertTrue(a4.contains("height:134.5mm"))
        val letter = pileSignsHtml(d.piles, "LETTER")
        assertTrue(letter.contains("size:letter portrait"))
        assertTrue(letter.contains("height:125.7mm"))
    }

    @Test
    fun whatTheSmartPilesGoByDeckNeedsFriendsWantsBindersInOrder() {
        val krenko = Deck(
            "k", "Krenko",
            cards = listOf(DeckCardEntry("matron", "Goblin Matron", null, quantity = 2), DeckCardEntry("shock", "Shock", null, quantity = 1)),
            considering = listOf(DeckCardEntry("fling", "Fling", null, quantity = 1)),
            ownership = DeckOwnership.VIRTUAL.name, createdAt = 1
        )
        val dsk = StoragePlace("dsk", "Duskmourn", PlaceKind.BINDER.name, sortRule = SortRule.SET.name, createdAt = 2)
        val cols = listOf(
            pile().copy(
                storagePlaces = listOf(dsk),
                entries = listOf(
                    CollectionEntry("shock", "Shock", null, quantity = 1),
                    CollectionEntry("a1", "Acrobat", null, quantity = 1, places = listOf(CopyPlace("dsk", 1, page = 1, slot = 1))),
                    CollectionEntry("z9", "Zimone", null, quantity = 1, places = listOf(CopyPlace("dsk", 1)))
                )
            )
        )
        assertEquals(
            mapOf("goblin matron" to listOf(DeckNeed("k", "Krenko", 2)), "fling" to listOf(DeckNeed("k", "Krenko", 1))),
            deckNeedsOf(cols, listOf(krenko))
        )
        val names = mapOf("u-priya" to "Priya", "u-jo" to "Jo")
        assertEquals(
            mapOf("shock" to listOf(FriendWant("u-priya", "Priya"), FriendWant("u-jo", "Jo")), "opt" to listOf(FriendWant("u-priya", "Priya"))),
            friendWantsOf(
                listOf(
                    TradeMatch("u-priya", emptyList(), listOf(TradeCard("s", "Shock"), TradeCard("o", "Opt"))),
                    TradeMatch("u-jo", emptyList(), listOf(TradeCard("s", "shock"))),
                    TradeMatch("u-gone", emptyList(), listOf(TradeCard("o", "Opt")))
                )
            ) { names[it] }
        )
        val facts = { id: String ->
            when (id) {
                "a1" -> CardFacts("Acrobat", set = "dsk", collectorNumber = "1")
                "z9" -> CardFacts("Zimone", set = "DSK", collectorNumber = "40")
                else -> null
            }
        }
        assertEquals(
            listOf(OrderedBinder("dsk", "Duskmourn", "SET", 9, listOf(Placed(0, CardFacts("Acrobat", set = "dsk", collectorNumber = "1"))), listOf("acrobat", "zimone"), listOf("dsk"))),
            orderedBinders(cols, facts)
        )
    }
}
