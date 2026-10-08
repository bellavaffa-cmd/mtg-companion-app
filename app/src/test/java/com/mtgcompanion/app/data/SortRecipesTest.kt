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
        createdAt = o.getLong("createdAt"), goals = o.bool("goals")
    )

    private fun card(o: JSONObject) = RecipeCard(
        name = o.getString("name"), scryfallId = o.str("scryfallId"), colors = o.strings("colors"), colorIdentity = o.strings("colorIdentity"), typeLine = o.str("typeLine"),
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
            owned = owned.keys().asSequence().associateWith { owned.getInt(it) },
            goals = if (o.isNull("goals")) emptyList() else o.getJSONArray("goals").objects().map { g ->
                val missing = g.getJSONObject("missing")
                GoalNeed(
                    g.getString("goalId"), g.getString("name"), g.getString("kind"), g.getBoolean("foil"), g.getInt("have"), g.getInt("need"),
                    missing.keys().asSequence().associateWith { missing.getInt(it) }, g.str("placeId")
                )
            }
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
    fun aSessionSmartPilesFirstThenKeepApartThenTheLevels() = runSessions(v)

    @Test
    fun theGoalsNeedPileEachGoalClaimsOnlyWhatItsMissingAfterDecksBeforeFriendsAndBinders() = runSessions(v.getJSONObject("goalSessions"))

    private fun runSessions(from: JSONObject) {
        val context = ctx(from.getJSONObject("ctx"))
        val cards = from.getJSONArray("session").objects().map { card(it) }
        for (s in from.getJSONArray("sessions").objects()) {
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
        // Goals need is off unless a goal is under way — then only "What my collection needs" pulls it out.
        assertTrue(t.none { "GOALS" in it.pullOut })
        val withGoals = recipeTemplates(listOf("dsk"), goals = true)
        assertEquals(listOf(false, false, false, true), withGoals.map { "GOALS" in it.pullOut })
        assertEquals("Decks need · Goals need · Friends want · Binder gaps · To trade · 6 piles", recipeLine(withGoals[3], fmt))
        assertTrue("GOALS" !in newRecipe("n1", 5).pullOut)
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
                listOf("FOIL", "PLAYED"), goTo = listOf(PileGoTo("L:v0", "b")), createdAt = 3, goals = false
            ),
            r
        )
        // "goals" says whether the Goals need pile is pulled out, always.
        val g = sortRecipe(newRecipe("y", 1).copy(pullOut = listOf("FRIENDS", "GOALS")))
        assertEquals(true, g.goals)
        assertEquals(listOf("GOALS", "FRIENDS"), g.pullOut)
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
    fun aRecipeSavedByAnAppFromBeforeTheGoalsPileKeepsThisDevicesPileOneTurnedOffStaysOff() {
        val goals = r("a", "Bulk").copy(pullOut = listOf("DECKS", "GOALS", "FRIENDS")).let { sortRecipe(it) }
        val here = pile(listOf(goals, r("b", "Rares")))
        // The older app drops GOALS and the "goals" key — and here it renamed the recipe too.
        val older = goals.copy(name = "Bulk boxes", pullOut = listOf("DECKS", "FRIENDS"), goals = null)
        val olderPile = pile(listOf(older, r("b", "Rares")))
        val kept = keepRecipesFromOlderApp(here, olderPile).sortRecipes!!
        assertEquals(listOf("DECKS", "GOALS", "FRIENDS"), kept[0].pullOut)
        assertEquals("Bulk boxes", kept[0].name)
        assertEquals(true, kept[0].goals)
        // Nothing to put back: the same object.
        assertTrue(keepRecipesFromOlderApp(here, here) === here)
        // Through the whole merge, both ways round: the rename comes through, the pile stays.
        for (minePreferred in listOf(true, false)) {
            val merged = ItemMerge.mergeCollections(here, here, olderPile, minePreferred).sortRecipes!!
            assertEquals(listOf("DECKS", "GOALS", "FRIENDS"), merged[0].pullOut)
            assertEquals("Bulk boxes", merged[0].name)
            assertEquals(listOf("DECKS", "GOALS", "FRIENDS"), ItemMerge.mergeCollections(here, olderPile, here, minePreferred).sortRecipes!![0].pullOut)
        }
        // A newer app that turned it off says so ("goals": false): it stays off.
        val off = pile(listOf(sortRecipe(r("a", "Bulk").copy(pullOut = listOf("DECKS", "FRIENDS"))), r("b", "Rares")))
        assertEquals(false, off.sortRecipes!![0].goals)
        assertEquals(listOf("DECKS", "FRIENDS"), ItemMerge.mergeCollections(here, here, off, true).sortRecipes!![0].pullOut)
    }

    @Test
    fun theGoalsTheGoalsPileGoesByOpenOnesMissingSomethingMostNearlyDoneFirstASetGoalWithItsBinder() {
        val dskCards = listOf(GoalSetCard("d1", "Fear of Exposure", "uncommon"), GoalSetCard("d2", "Grim Cellar", "uncommon"), GoalSetCard("d3", "Valgavoth", "mythic"))
        val dsk = newSetGoal("g-dsk", "DSK", "Duskmourn", dskCards, listOf("uncommon"), false, 1)
        val shocks = newListGoal("g-shock", "PLAYSET", "Shock lands", listOf(GoalCard("Steam Vents"), GoalCard("Sacred Foundry")), 4, 1)
        val done = newListGoal("g-done", "CUSTOM", "Done", listOf(GoalCard("Opt")), null, 1).copy(completedAt = 5)
        val full = newListGoal("g-full", "CUSTOM", "Full", listOf(GoalCard("Opt")), null, 1)
        val cols = listOf(
            pile().copy(
                entries = listOf(
                    CollectionEntry("d1", "Fear of Exposure", null, quantity = 1),
                    CollectionEntry("sv", "Steam Vents", null, quantity = 1),
                    CollectionEntry("opt", "Opt", null, quantity = 1)
                )
            )
        )
        fun binder(id: String, sets: List<String>) = OrderedBinder(id, id, "SET", 9, emptyList(), emptyList(), sets)
        val needs = goalNeedsOf(listOf(shocks, done, full, dsk), cols, emptyList(), listOf(binder("mixed", listOf("dsk", "m10")), binder("dskonly", listOf("dsk"))))
        assertEquals(
            listOf(
                GoalNeed("g-dsk", "Duskmourn uncommons", "SET", false, 1, 2, mapOf("id:d2" to 1), "dskonly"),
                GoalNeed("g-shock", "Shock lands", "PLAYSET", false, 1, 8, mapOf("n:steam vents" to 3, "n:sacred foundry" to 4))
            ),
            needs
        )
        assertEquals(listOf("n:steam vents", "n:sacred foundry"), needs[1].missing.keys.toList())
        assertEquals("mixed", goalNeedsOf(listOf(dsk), cols, emptyList(), listOf(binder("mixed", listOf("dsk", "m10"))))[0].placeId)
        assertNull(goalNeedsOf(listOf(dsk), cols, emptyList(), listOf(binder("m10", listOf("m10"))))[0].placeId)
        // A foil goal moves only for a foil copy; a set goal only for its printing.
        val ctx = SmartContext(goals = listOf(needs[0].copy(foil = true)))
        assertEquals(emptyList<SortReason>(), reasonsFor(ctx, RecipeCard("Grim Cellar", scryfallId = "d2"), emptyList()))
        assertEquals(emptyList<SortReason>(), reasonsFor(ctx, RecipeCard("Grim Cellar", scryfallId = "other-printing", foil = true), emptyList()))
        assertEquals(listOf("GOAL · DUSKMOURN UNCOMMONS"), reasonsFor(ctx, RecipeCard("Grim Cellar", scryfallId = "d2", foil = true), emptyList()).map { reasonLine(it) })
    }

    @Test
    fun filingTheGoalsPileIntoTheGoalsSetBinderElseNoPlaceOrWhereTheRecipeSays() {
        val dsk = StoragePlace("dsk", "Duskmourn", PlaceKind.BINDER.name, sortRule = SortRule.SET.name, createdAt = 2)
        val box = StoragePlace("box", "Goal box", PlaceKind.BOX.name, createdAt = 3)
        val cols = listOf(pile().copy(storagePlaces = listOf(dsk, box)))
        var recipe = sortRecipe(SortRecipe("r", "Goals", listOf("GOALS"), emptyList(), emptyList(), createdAt = 1))
        val d = derivePiles(recipe, fmt)
        assertEquals("", pileGoesTo(recipe, d.piles[0]))
        fun scan(id: String, name: String, placeId: String? = null) = RecipeScan(
            id.drop(1).toLong(), id, name, null, RecipeCard(name), CardFacts(name, set = "dsk"), CollectionEntry(id, name, null), 1, "S:GOALS",
            SortReason("GOALS", goalId = "g", goal = "Duskmourn uncommons", have = 91, need = 92, placeId = placeId)
        )
        val scans = listOf(scan("s1", "Grim Cellar", "dsk"), scan("s2", "Steam Vents"))
        assertEquals(listOf("dsk", null), scans.map { binderFiledInto(recipe, it) })
        var filed = fileRecipe(cols, recipe, d, scans)
        assertEquals(listOf("Grim Cellar → Duskmourn", "Steam Vents → No place yet"), filed.steps.map { "${it.scan.name} → ${it.to}" })
        recipe = withGoTo(recipe, "S:GOALS", "box")
        assertNull(binderFiledInto(recipe, scans[0]))
        filed = fileRecipe(cols, recipe, d, scans)
        assertEquals(listOf("Grim Cellar → Goal box", "Steam Vents → Goal box"), filed.steps.map { "${it.scan.name} → ${it.to}" })
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
        // From the Goals need pile, the next smart pile after it: a friend's.
        val goalRecipe = recipe(v.getJSONObject("goalSessions").getJSONArray("sessions").getJSONObject(0).getJSONObject("recipe"))
        val gd = derivePiles(goalRecipe, fmt)
        val goal = SortReason("GOALS", goalId = "g", goal = "Shock lands", have = 38, need = 40)
        assertEquals("S:FRIENDS", otherPile(goalRecipe, gd, c, 2, goal, listOf(deck, friend), 1.0)?.key)
        assertEquals("S:GOALS", otherPile(goalRecipe, gd, c, 1, deck, listOf(goal), 1.0)?.key)
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
