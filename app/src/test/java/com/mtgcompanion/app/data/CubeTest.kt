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
 * Cubes. The cases in cubeVectors.json are the web app's too (MtgCompanionWeb's
 * tests/decks/cube.test.ts, which keeps the file), so both apps balance, fill, filter, deal packs and
 * read lists the same way.
 */
class CubeTest {

    private val v: JSONObject = JSONObject(
        javaClass.classLoader!!.getResourceAsStream("cubeVectors.json")!!.bufferedReader().use { it.readText() }
    )

    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.ints(): List<Int> = (0 until length()).map { getInt(it) }

    private fun card(o: JSONObject) = CubeCard(
        id = o.getString("id"), name = o.getString("name"), colors = o.getJSONArray("colors").strings(), typeLine = o.getString("typeLine"),
        cmc = o.getDouble("cmc"), rarity = o.getString("rarity"), set = o.getString("set"),
        usd = if (o.isNull("usd")) null else o.getDouble("usd"), text = o.getString("text"), producedMana = o.getJSONArray("producedMana").strings()
    )

    private val cards = v.getJSONArray("cards").objects().map { card(it) }
    private val byName = cards.associateBy { it.name }

    private fun lines(a: JSONArray) = (0 until a.length()).map { i -> a.getJSONArray(i).let { CubeLine(byName.getValue(it.getString(0)), it.getInt(1)) } }
    private fun flat(xs: List<CubeCount>) = xs.map { "${it.key} ${it.count}/${it.target ?: "-"}" }

    @Test
    fun `each card's colour group, type and roles`() {
        for (f in v.getJSONArray("facts").objects()) {
            val c = byName.getValue(f.getString("name"))
            assertEquals(c.name, f.getString("group"), cubeGroupOf(c))
            assertEquals(c.name, f.getString("type"), cubeTypeOf(c))
            assertEquals(c.name, f.getJSONArray("roles").strings(), cubeRolesOf(c))
        }
    }

    @Test
    fun `targets for each size add up to it`() {
        for (t in v.getJSONArray("targets").objects()) {
            val g = t.getJSONObject("groups")
            val want = CUBE_GROUPS.associateWith { g.getInt(it) }
            assertEquals(want, cubeTargets(t.getInt("size")))
            assertEquals(t.getInt("size"), want.values.sum())
        }
        for (s in v.getJSONArray("splits").objects()) {
            assertEquals(s.getJSONArray("expect").ints(), splitByWeight(s.getInt("total"), s.getJSONArray("weights").ints()))
        }
    }

    @Test
    fun `the balance - counts against targets, curve, types, roles and what's off`() {
        for (b in v.getJSONArray("balances").objects()) {
            val r = cubeBalance(lines(b.getJSONArray("lines")), b.getInt("size"), b.getBoolean("singleton"))
            val e = b.getJSONObject("expect")
            val label = "${b.getInt("size")} ${b.getJSONArray("lines").length()}"
            assertEquals(label, e.getInt("total"), r.total)
            assertEquals(label, e.getJSONArray("groups").strings(), flat(r.groups))
            assertEquals(label, e.getJSONArray("curve").strings(), flat(r.curve))
            assertEquals(label, e.getDouble("averageMv"), r.averageMv, 0.0)
            assertEquals(label, e.getJSONArray("types").strings(), flat(r.types))
            assertEquals(label, e.getJSONArray("roles").strings(), flat(r.roles))
            assertEquals(label, e.getJSONArray("warnings").strings(), r.warnings)
        }
        assertTrue("Green is 50 short" in cubeBalance(emptyList(), 360, true).warnings)
    }

    @Test
    fun `fill from collection - balanced by colour, best first, never what's in already`() {
        for (f in v.getJSONArray("fills").objects()) {
            val got = cubeFill(lines(f.getJSONArray("cube")), f.getJSONArray("owned").strings().map { byName.getValue(it) }, f.getInt("size"))
            assertEquals(f.getJSONArray("expect").strings(), got.map { it.name })
        }
    }

    @Test
    fun `add from collection - the filters`() {
        for (f in v.getJSONArray("filters").objects()) {
            val o = f.getJSONObject("filter")
            val filter = CubeFilter(
                query = o.optString("query", ""),
                groups = o.optJSONArray("groups")?.strings()?.toSet().orEmpty(),
                rarities = o.optJSONArray("rarities")?.strings()?.toSet().orEmpty(),
                type = o.optString("type", ""),
                set = o.optString("set", ""),
                minUsd = if (o.has("minUsd") && !o.isNull("minUsd")) o.getDouble("minUsd") else null,
                maxUsd = if (o.has("maxUsd") && !o.isNull("maxUsd")) o.getDouble("maxUsd") else null
            )
            assertEquals(o.toString(), f.getJSONArray("expect").strings(), cards.filter { cubeFilterMatches(filter, it) }.map { it.name })
        }
    }

    @Test
    fun `packs - the same seed deals the same packs in both apps`() {
        for (r in v.getJSONArray("random").objects()) {
            val rnd = CubeRandom(r.getInt("seed"))
            val first = r.getJSONArray("first")
            for (i in 0 until first.length()) assertEquals(first.getDouble(i), rnd.next(), 0.0)
        }
        for (s in v.getJSONArray("shuffles").objects()) {
            assertEquals(s.getJSONArray("expect").strings(), cubeShuffle(s.getJSONArray("items").strings(), s.getInt("seed")))
        }
        for (p in v.getJSONArray("packs").objects()) {
            val got = cubePacks(p.getJSONArray("pool").strings(), p.getInt("seats"), p.getInt("packs"), p.getInt("packSize"), p.getInt("seed"))
            val e = p.getJSONObject("expect")
            assertEquals(e.getInt("needed"), got.needed)
            assertEquals(e.getInt("short"), got.short)
            val seats = e.getJSONArray("seats")
            assertEquals(seats.length(), got.seats.size)
            for (s in 0 until seats.length()) {
                val packs = seats.getJSONArray(s)
                assertEquals(packs.length(), got.seats[s].size)
                for (k in 0 until packs.length()) assertEquals(packs.getJSONArray(k).strings(), got.seats[s][k])
            }
        }
    }

    @Test
    fun `the plain list - export, and import from CubeCobra and others`() {
        for (e in v.getJSONArray("exports").objects()) {
            val list = e.getJSONArray("cards").objects().map { it.getString("name") to it.getInt("qty") }
            assertEquals(e.getString("text"), cubeListText(list))
        }
        for (i in v.getJSONArray("imports").objects()) {
            val want = i.getJSONArray("expect").objects().map { CubeListLine(it.getString("name"), it.getInt("qty")) }
            assertEquals(want, parseCubeList(i.getString("text")))
        }
        val text = cubeListText(listOf("Sol Ring" to 1, "Island" to 2))
        assertEquals(listOf(CubeListLine("Island", 2), CubeListLine("Sol Ring", 1)), parseCubeList(text))
    }

    // ---- Kept as a deck ----

    private fun entry(id: String, name: String, quantity: Int = 1, proxy: Int? = null) =
        DeckCardEntry(scryfallId = id, name = name, imageUrl = null, quantity = quantity, proxyQuantity = proxy)

    @Test
    fun `a new cube is a virtual, archived deck with its settings`() {
        val c = newCube("c1", "  Vintage ", 540, true, 7)
        assertTrue(c.isCube)
        assertEquals("Vintage", c.name)
        assertEquals(DeckOwnership.VIRTUAL.name, c.ownership)
        assertEquals(true, c.archived)
        assertEquals(CubeSettings(540, true), c.cube)
        assertEquals(40, newCube("c2", "", 5, false, 1).cube!!.size)
        val touched = c.copy(archived = false, ownership = DeckOwnership.PHYSICAL.name)
        assertEquals(true, asCube(touched).archived)
        assertEquals(DeckOwnership.VIRTUAL.name, asCube(touched).ownership)
        assertSame(c, asCube(c))
        // Saved and read back as both apps write it.
        val json = localMoshi.adapter(Deck::class.java).toJson(c)
        assertTrue(json, json.contains("\"cube\":{\"size\":540,\"singleton\":true}"))
        assertEquals(c.cube, localMoshi.adapter(Deck::class.java).fromJson(json)!!.cube)
    }

    @Test
    fun `adding - singleton leaves out a name already there, otherwise copies add up`() {
        val c = newCube("c", "C", 360, true, 1)
        val a = addToCube(c, listOf(entry("a", "Sol Ring", 2), entry("b", "Sol Ring"), entry("x", "Bolt")))
        assertEquals(listOf("1 Sol Ring", "1 Bolt"), a.cube.cards.map { "${it.quantity} ${it.name}" })
        assertEquals(2, a.added)
        assertEquals(listOf("Sol Ring"), a.skipped)
        val many = addToCube(c.copy(cube = CubeSettings(360, false)), listOf(entry("a", "Sol Ring", 2), entry("a", "Sol Ring", 1)))
        assertEquals(listOf("3 Sol Ring"), many.cube.cards.map { "${it.quantity} ${it.name}" })
        assertSame(a.cube, addToCube(a.cube, listOf(entry("a", "sol ring"))).cube)
        val proxied = markCubeProxy(a.cube, "x", true)
        assertEquals(1, proxied.cards[1].proxyQuantity)
        assertNull(markCubeProxy(proxied, "x", false).cards[1].proxyQuantity)
        assertEquals(listOf("Bolt"), removeFromCube(a.cube, "a").cards.map { it.name })
    }

    @Test
    fun `sync - settings merge field by field, and an older app's save keeps them`() {
        val base = newCube("c", "C", 360, true, 1).copy(cards = listOf(entry("a", "A")))
        val mine = base.copy(cube = base.cube!!.copy(size = 540), cards = listOf(entry("a", "A"), entry("b", "B")))
        val theirs = base.copy(cube = base.cube!!.copy(boxPlaceId = "box"), cards = listOf(entry("a", "A"), entry("c", "C")))
        val merged = ItemMerge.mergeDecks(base, mine, theirs, minePreferred = true)
        assertEquals(CubeSettings(540, true, "box"), merged.cube)
        assertEquals(listOf("A", "B", "C"), merged.cards.map { it.name })
        assertEquals(CUBE_MODE, merged.gameMode)
        // An app from before cubes reads the deck without the key, and saves it so.
        val older = theirs.copy(cube = null)
        assertEquals(CubeSettings(540, true), ItemMerge.mergeDecks(base, mine, older, minePreferred = false).cube)
        assertSame(base.cube, keepCubeFromOlderApp(base, older).cube)
        assertNull(mergeCubeSettings(null, null, null, true))
        // Cubes keep no versions: a list of hundreds would only bloat them.
        assertTrue(merged.versions.isEmpty())
    }

    // ---- The cube box ----

    private fun cEntry(id: String, name: String, quantity: Int, foil: Int = 0, places: List<CopyPlace>? = null) =
        CollectionEntry(id, name, null, quantity = quantity, foilQuantity = foil, places = places)

    private fun setup(): Pair<Deck, List<Collection>> {
        val cube = newCube("cube", "Pauper", 360, true, 1).copy(
            cards = listOf(entry("bolt", "Lightning Bolt"), entry("ring", "Sol Ring"), entry("ele", "Elvish Mystic"), entry("lotus", "Black Lotus"), entry("mox", "Mox Pearl", 1, proxy = 1))
        )
        val box = cubeBoxPlace(cube, "box", 2)
        val withBox = cube.copy(cube = cube.cube!!.copy(boxPlaceId = "box"))
        val collections = listOf(
            Collection(
                id = "unsorted", name = "Unsorted", createdAt = 0, storagePlaces = listOf(StoragePlace("red", "Red box", PlaceKind.BOX.name, createdAt = 1), box),
                entries = listOf(cEntry("bolt", "Lightning Bolt", 2, 0, listOf(CopyPlace("red", 1), CopyPlace("box", 1))), cEntry("ring", "Sol Ring", 1))
            ),
            Collection(id = "b1", name = "Trade binder", createdAt = 1, entries = listOf(cEntry("ele2", "Elvish Mystic", 0, 1, listOf(CopyPlace("red", 1, foil = true))))),
            Collection(id = "w", name = "Wishlist", type = "WISHLIST", createdAt = 1, entries = listOf(cEntry("lotus", "Black Lotus", 1)))
        )
        return withBox to collections
    }

    @Test
    fun `where each card stands - in the box, owned elsewhere, proxy or not owned`() {
        val (cube, collections) = setup()
        assertEquals("Pauper box", cubeBoxPlace(cube, "box", 2).name)
        assertEquals(
            listOf(
                "Lightning Bolt: IN_BOX 1/1 Red box ×1",
                "Sol Ring: OWNED 0/1 No place (Unsorted) ×1",
                "Elvish Mystic: OWNED 0/1 Red box ×1",
                "Black Lotus: NOT_OWNED 0/0 ",
                "Mox Pearl: PROXY 0/0 "
            ),
            cubeStatus(cube, collections).map { "${it.name}: ${it.state} ${it.inBox}/${it.elsewhere} ${it.where}" }
        )
        assertEquals(CubeCardState.OWNED, cubeStatus(cube.copy(cube = CubeSettings()), collections)[0].state)
    }

    @Test
    fun `the pull list fetches what's not in the box yet, and moving puts it there`() {
        val (cube, collections) = setup()
        val list = cubePullList(cube, collections, listOf(cube))
        assertEquals(
            listOf("PLACE Elvish Mystic 1", "LOOSE Sol Ring 1", "MISSING Black Lotus 1"),
            list.groups.flatMap { g -> g.rows.map { "${g.kind} ${it.name} ${it.qty}" } }
        )
        val ticked = list.groups.flatMap { it.rows }.map { it.key }.toSet()
        val (after, moved) = moveIntoCubeBox(list, ticked, collections, "box")
        assertEquals(2, moved)
        assertEquals(listOf(CopyPlace("box", 1)), placedCopies(after[0].entries[1]))
        assertEquals(listOf(CopyPlace("box", 1, foil = true)), placedCopies(after[1].entries[0]))
        assertEquals(1, after[0].entries[1].quantity)
        assertEquals(
            listOf(CubeCardState.IN_BOX, CubeCardState.IN_BOX, CubeCardState.IN_BOX, CubeCardState.NOT_OWNED, CubeCardState.PROXY),
            cubeStatus(cube, after).map { it.state }
        )
        assertEquals(0, cubePullList(cube, after, listOf(cube)).total)
        assertSame(collections, moveIntoCubeBox(list, emptySet(), collections, "box").first)
    }

    @Test
    fun `a cube's proxies aren't offered for swapping like a deck's`() {
        val (cube, _) = setup()
        val owned = listOf(Collection(id = "unsorted", name = "Unsorted", createdAt = 0, entries = listOf(cEntry("mox", "Mox Pearl", 1))))
        assertTrue(proxySwaps(owned, listOf(cube)).isEmpty())
    }

    @Test
    fun `draft - a limited deck from packs, its pool the picked cards`() {
        val (cube, _) = setup()
        assertEquals(listOf("a", "a", "b"), cubePool(cube.copy(cards = listOf(entry("a", "A", 2), entry("b", "B")))))
        val d = limitedDeckFromCube(cube, listOf("bolt", "ring", "bolt", "nope"), "d", "Pauper — seat 1", "From the cube", 5)
        assertEquals(GameMode.LIMITED.name, d.gameMode)
        assertEquals(DeckOwnership.VIRTUAL.name, d.ownership)
        assertEquals(listOf("2 Lightning Bolt", "1 Sol Ring"), d.sideboard.map { "${it.quantity} ${it.name}" })
        assertTrue(d.cards.isEmpty())
        assertEquals("From the cube", d.description)
        assertNull(limitedDeckFromCube(cube, emptyList(), "e", "x", " ", 5).description)
    }

    @Test
    fun `reset - Everything removes cubes, Collection and Cards only keep them`() {
        val cube = newCube("cube", "C", 360, true, 1)
        val deck = Deck(id = "d", name = "d", createdAt = 1)
        val cols = listOf(Collection(id = UNSORTED_COLLECTION_ID, name = "Unsorted", createdAt = 0))
        assertEquals("1 deck · 1 cube", resetCountsText(resetCounts(listOf(deck, cube), cols, ResetScope.EVERYTHING)))
        assertEquals(2, resetLibrary(listOf(deck, cube), cols, ResetScope.COLLECTION).decks.size)
        assertEquals(2, resetLibrary(listOf(deck, cube), cols, ResetScope.CARDS).decks.size)
        assertTrue(resetLibrary(listOf(deck, cube), cols, ResetScope.EVERYTHING).decks.isEmpty())
    }
}
