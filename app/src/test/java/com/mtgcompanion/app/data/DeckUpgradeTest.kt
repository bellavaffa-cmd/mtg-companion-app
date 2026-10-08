package com.mtgcompanion.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * "Upgrade with my cards". The cases in deckUpgradeVectors.json are the web app's too
 * (tests/decks/deckUpgrade.test.ts, which keeps the file), so both apps pair, guard and explain swaps
 * the same way.
 */
class DeckUpgradeTest {

    private val v: JSONObject = JSONObject(
        javaClass.classLoader!!.getResourceAsStream("deckUpgradeVectors.json")!!.bufferedReader().use { it.readText() }
    )

    private fun JSONObject.str(k: String): String? = if (!has(k) || isNull(k)) null else getString(k)
    private fun JSONObject.dbl(k: String): Double? = if (!has(k) || isNull(k)) null else getDouble(k)
    private fun JSONObject.int(k: String): Int? = if (!has(k) || isNull(k)) null else getInt(k)
    private fun JSONObject.flag(k: String): Boolean = has(k) && !isNull(k) && getBoolean(k)
    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.roles(): List<String> = if (has("roles")) getJSONArray("roles").strings() else emptyList()

    private fun deckCard(o: JSONObject) = UpgradeDeckCard(
        name = o.getString("name"), scryfallId = o.str("scryfallId") ?: o.getString("name"), typeLine = o.str("typeLine"), cmc = o.dbl("cmc"),
        roles = o.roles(), usd = o.dbl("usd"), gameChanger = o.flag("gameChanger"), edhrecRank = o.int("edhrecRank"), inclusion = o.int("inclusion"),
        commander = o.flag("commander"), replaceable = o.flag("replaceable"), keep = o.flag("keep"), comboPiece = o.flag("comboPiece")
    )

    private fun ownedCard(o: JSONObject) = UpgradeOwnedCard(
        name = o.getString("name"), scryfallId = o.str("scryfallId") ?: o.getString("name"), typeLine = o.str("typeLine"), cmc = o.dbl("cmc"),
        roles = o.roles(), usd = o.dbl("usd"), gameChanger = o.flag("gameChanger"), edhrecRank = o.int("edhrecRank"), inclusion = o.int("inclusion"),
        identity = o.str("identity"), legal = !(o.has("legal") && !o.getBoolean("legal")), completesCombo = o.flag("completesCombo"),
        spare = o.int("spare") ?: 0, heldBy = o.str("heldBy"), where = o.str("where"), placeKey = o.str("placeKey")
    )

    private fun inputOf(c: JSONObject): UpgradeInput {
        val i = c.getJSONObject("input")
        return UpgradeInput(
            commander = i.str("commander"),
            identity = i.str("identity"),
            edhrec = i.getBoolean("edhrec"),
            bracket = if (i.isNull("bracket")) null else i.getJSONObject("bracket").let { UpgradeBracket(it.getInt("gameChangers"), it.getInt("combos")) },
            deck = i.getJSONArray("deck").objects().map { deckCard(it) },
            owned = i.getJSONArray("owned").objects().map { ownedCard(it) },
            dismissed = i.getJSONArray("dismissed").strings().toSet()
        )
    }

    private fun whole(n: Double): String = if (n == Math.floor(n)) n.toLong().toString() else n.toString()

    @Test
    fun sharedCases() {
        for (c in v.getJSONArray("cases").objects()) {
            val name = c.getString("name")
            val got = upgradeSwaps(inputOf(c))
            val want = c.getJSONArray("expect").objects()
            assertEquals(name, want.map { it.getString("key") }, got.map { it.key })
            want.zip(got).forEach { (w, g) ->
                assertEquals(name, w.getString("role"), g.role)
                assertEquals(name, w.getString("reason"), g.reason)
                assertEquals(name, w.getString("where"), g.where)
                assertEquals(name, w.getDouble("gain"), g.gain, 1e-9)
                assertEquals(name, w.dbl("priceDelta"), g.priceDelta)
                assertEquals(name, w.int("raisesBracketTo"), g.raisesBracketTo)
            }
            val keeping = got.filter { it.raisesBracketTo == null }
            assertEquals(name, c.getString("summary"), upgradeSummary(keeping) { "$" + whole(it) })
        }
    }

    @Test
    fun wordsAndNumbers() {
        assertEquals("9,000", withThousands(9000.0))
        assertEquals("1,234,567", withThousands(1234567.0))
        assertEquals("12", withThousands(12.0))
        assertEquals("Krenko", shortCommander("Krenko, Mob Boss"))
        assertEquals("Esika", shortCommander("Esika, God of the Tree // The Prismatic Bridge"))
        assertEquals("mind stone>jeska's will", upgradePairKey(" Mind Stone", "Jeska's Will "))
        assertEquals("Would move the deck to bracket 4", bracketWarning(4))
        assertEquals("ramp", upgradeRoleOf(listOf("treasure", "draw", "ramp")))
        assertNull(upgradeRoleOf(listOf("treasure")))
        assertEquals(33.0, upgradeScore(UpgradeDeckCard("X", inclusion = 33), true), 0.0)
        assertEquals(0.0, upgradeScore(UpgradeDeckCard("X"), true), 0.0)
        assertEquals(44.0, upgradeScore(UpgradeDeckCard("X", edhrecRank = 1000, cmc = 2.0), false), 0.0)
        assertEquals("0 upgrades from your cards · would save $0", upgradeSummary(emptyList()) { "$" + whole(it) })
    }

    // ---- Where the owned cards are ----

    private fun entry(name: String, quantity: Int, places: List<CopyPlace>? = null) =
        CollectionEntry("id-$name", name, null, quantity = quantity, places = places)

    private fun dcard(name: String, quantity: Int = 1, proxyQuantity: Int? = null, replaceable: Boolean = false) =
        DeckCardEntry("id-$name", name, null, quantity = quantity, typeLine = "Artifact", replaceable = replaceable, proxyQuantity = proxyQuantity)

    private fun deckOf(id: String, name: String, ownership: DeckOwnership, cards: List<DeckCardEntry>) =
        Deck(id, name, cards = cards, ownership = ownership.name, createdAt = 0)

    @Test
    fun ownedSourcesFindSpareCopiesAndWhere() {
        val collections = listOf(
            Collection(
                UNSORTED_COLLECTION_ID, UNSORTED_COLLECTION_NAME,
                listOf(entry("Sol Ring", 1), entry("Arcane Signet", 1, listOf(CopyPlace("red", 1, section = "Artifacts")))),
                createdAt = 0, type = CollectionType.OWNED.name,
                storagePlaces = listOf(StoragePlace("red", "Red box", PlaceKind.BOX.name), StoragePlace("dsk", "Duskmourn binder", PlaceKind.BINDER.name))
            ),
            Collection("b1", "Trade binder", listOf(entry("Fellwar Stone", 2, listOf(CopyPlace("dsk", 1, page = 3, slot = 5))), entry("Mind Stone", 1)), createdAt = 0, type = CollectionType.OWNED.name),
            Collection("w", "Wishlist", listOf(entry("Mana Crypt", 1)), createdAt = 0, type = CollectionType.WISHLIST.name)
        )
        val decks = listOf(
            deckOf("me", "Krenko", DeckOwnership.PHYSICAL, listOf(dcard("Sol Ring"))),
            deckOf("v", "Brew", DeckOwnership.VIRTUAL, listOf(dcard("Mind Stone"), dcard("Fellwar Stone"))),
            deckOf("a", "Atraxa", DeckOwnership.PHYSICAL, listOf(dcard("Big Score"), dcard("Proxy Thing", proxyQuantity = 1)))
        )
        val got = ownedSources(collections, decks, "me")
        assertEquals(OwnedSource("Arcane Signet", "id-Arcane Signet", 1, null, "Red box › Artifacts", "place:red"), got["arcane signet"])
        assertEquals(OwnedSource("Fellwar Stone", "id-Fellwar Stone", 1, null, "Duskmourn binder p3 s5", "place:dsk"), got["fellwar stone"])
        assertEquals(OwnedSource("Sol Ring", "id-Sol Ring", 1, null, "Unsorted", "binder:unsorted"), got["sol ring"])
        assertEquals(OwnedSource("Mind Stone", "id-Mind Stone", 0, "Brew", null, null), got["mind stone"])
        assertEquals(OwnedSource("Big Score", "id-Big Score", 0, "Atraxa", null, null), got["big score"])
        assertFalse(got.containsKey("mana crypt"))
        assertFalse(got.containsKey("proxy thing"))
    }

    // ---- Swapping ----

    @Test
    fun aSwapPutsTheCutOnConsideringAndTheNewCardOnThePullList() {
        val physical = deckOf("me", "Krenko", DeckOwnership.PHYSICAL, listOf(dcard("Mind Stone", replaceable = true), dcard("Shock")))
        val after = withUpgrade(physical, "id-Mind Stone", dcard("Arcane Signet"))
        assertEquals(listOf(Triple("Shock", 1, null), Triple("Arcane Signet", 1, 1)), after.cards.map { Triple(it.name, it.quantity, it.proxyQuantity) })
        assertEquals(listOf("Mind Stone" to false), after.considering.map { it.name to it.replaceable })
        // A deck that doesn't hold its cards asks for every copy anyway.
        val virtual = withUpgrade(physical.copy(ownership = DeckOwnership.VIRTUAL.name), "id-Mind Stone", dcard("Arcane Signet", proxyQuantity = 0))
        assertNull(virtual.cards.first { it.name == "Arcane Signet" }.proxyQuantity)
        // Already in the deck, or the cut gone: nothing changes.
        assertSame(physical, withUpgrade(physical, "id-Mind Stone", dcard("Shock")))
        assertSame(physical, withUpgrade(physical, "id-Nope", dcard("Arcane Signet")))
    }

    @Test
    fun swapsAppliedTogetherSendTheRealCopiesCutToUnsorted() {
        val deck = deckOf("me", "Krenko", DeckOwnership.PHYSICAL, listOf(dcard("Mind Stone"), dcard("Shock", proxyQuantity = 1), dcard("Opt")))
        val cols = listOf(Collection(UNSORTED_COLLECTION_ID, UNSORTED_COLLECTION_NAME, emptyList(), createdAt = 0, type = CollectionType.OWNED.name))
        val (outCols, outDecks) = applyUpgrades(cols, listOf(deck), "me", listOf("id-Mind Stone" to dcard("Arcane Signet"), "id-Shock" to dcard("Chaos Warp")))
        assertEquals(listOf("Opt", "Arcane Signet", "Chaos Warp"), outDecks[0].cards.map { it.name })
        assertEquals(listOf("Mind Stone", "Shock"), outDecks[0].considering.map { it.name })
        assertEquals(listOf("Mind Stone" to 1), outCols[0].entries.map { it.name to it.quantity })
        val (sameCols, sameDecks) = applyUpgrades(cols, listOf(deck), "me", listOf("id-Gone" to dcard("Arcane Signet")))
        assertSame(deck, sameDecks[0])
        assertSame(cols, sameCols)
    }
}
