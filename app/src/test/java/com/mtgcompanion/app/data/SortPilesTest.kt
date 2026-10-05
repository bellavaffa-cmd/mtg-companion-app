package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Sorting a new pile into piles by rules, the same on both apps. The web app has the same checks —
 * see MtgCompanionWeb/tests/collection/sortPiles.test.ts.
 */
class SortPilesTest {

    private val red = StoragePlace("red", "Red box", PlaceKind.BOX.name, sortRule = SortRule.COLOUR.name, sections = listOf("Blue", "Red"), createdAt = 1)
    private val rares = StoragePlace("rares", "Rares binder", PlaceKind.BINDER.name, createdAt = 2)
    private val trade = StoragePlace("trade", "Trade binder", PlaceKind.BINDER.name, createdAt = 3)
    private fun entry(id: String, name: String, quantity: Int) = CollectionEntry(id, name, null, quantity = quantity)
    private val cols = listOf(
        Collection(UNSORTED_COLLECTION_ID, UNSORTED_COLLECTION_NAME, listOf(entry("shock", "Shock", 3)), createdAt = 0, type = CollectionType.OWNED.name, storagePlaces = listOf(red, rares, trade))
    )
    private val krenko = Deck(
        "krenko", "Krenko",
        cards = listOf(DeckCardEntry("matron", "Goblin Matron", null, quantity = 1), DeckCardEntry("shock", "Shock", null, quantity = 1)),
        considering = listOf(DeckCardEntry("fling", "Fling", null, quantity = 1)),
        ownership = DeckOwnership.VIRTUAL.name, createdAt = 1
    )
    private val rules = defaultPiles(cols)
    private fun facts(rarity: String? = "common", usd: Double? = 0.1, owned: Int = 0, wantedBy: List<String> = emptyList()) =
        SortFacts("Card", rarity, usd, owned, wantedBy)
    private val usd = { n: Double -> "$" + (if (n == Math.floor(n)) n.toLong().toString() else n.toString()) }

    @Test
    fun thePilesAFirstSortStartsWith() {
        assertEquals(
            listOf(
                PileRule("VALUE", over = 2.0, to = "rares"),
                PileRule("BULK", to = BY_RULE),
                PileRule("SPARES", keep = 4, to = "trade"),
                PileRule("WANTED")
            ),
            rules
        )
        assertEquals(listOf("Rares and mythics over $2", "Bulk: everything else", "Spares over 4", "Wanted by a deck"), rules.map { pileTitle(it, usd) })
    }

    @Test
    fun eachCardGoesInTheFirstPileWhoseRuleFitsBulkOnlyWhenNoneDoes() {
        fun pile(f: SortFacts) = pileFor(rules, f)?.let { "${it.index} ${it.why}" }
        assertEquals("0 Worth keeping safe", pile(facts(rarity = "rare", usd = 5.0)))
        assertEquals("0 Worth keeping safe", pile(facts(rarity = "mythic", usd = 3.0, owned = 6)))
        assertEquals("1 Bulk", pile(facts(rarity = "rare", usd = 2.0)))
        assertEquals("1 Bulk", pile(facts(rarity = "uncommon", usd = 9.0)))
        assertEquals("1 Bulk", pile(facts(rarity = "rare", usd = null)))
        assertEquals("2 You have 4 already", pile(facts(owned = 4)))
        assertEquals("1 Bulk", pile(facts(owned = 3)))
        assertEquals("3 For Krenko, Atraxa", pile(facts(wantedBy = listOf("Krenko", "Atraxa"))))
        val pricey = listOf(PileRule("WANTED"), PileRule("PRICE", over = 1.0))
        assertEquals(1, pileFor(pricey, facts(usd = 1.5))?.index)
        assertNull(pileFor(pricey, facts(usd = 0.5)))
    }

    @Test
    fun whatDecksWantAndHowManyOfEachCardIsOwned() {
        val wanted = wantedByDecks(cols, listOf(krenko))
        assertEquals(listOf("goblin matron: Krenko ×1", "fling: Krenko ×1"), wanted.map { (k, w) -> "$k: ${w.decks.joinToString(", ")} ×${w.qty}" })
        assertEquals(mapOf("shock" to 3), ownedCounts(cols, listOf(krenko)))
    }

    private fun scan(id: String, name: String, pile: Int, why: String = "", decks: List<String>? = null, usd: Double = 0.1) =
        SortScan(0, id, name, "common", usd, CardFacts(name, listOf("R"), "Instant"), entry(id, name, 0), pile, why, decks)

    @Test
    fun aSessionCopiesScannedCountAsOwnedAndCopiesDecksWantAreOnlyWantedOnce() {
        val owned = ownedCounts(cols, listOf(krenko))
        val wanted = wantedByDecks(cols, listOf(krenko))
        var session = SortSession("Booster box, Duskmourn", rules, true)
        fun add(id: String, name: String, rarity: String? = null, price: Double? = null): Int {
            val p = nextPile(session, name, rarity, price, owned, wanted)!!
            session = session.copy(scans = session.scans + scan(id, name, p.index, p.why, p.decks, price ?: 0.1))
            return p.index
        }
        assertEquals(
            listOf(1, 2, 3, 1, 0),
            listOf(add("shock", "Shock"), add("shock", "Shock"), add("matron", "Goblin Matron"), add("matron", "Goblin Matron"), add("ring", "The One Ring", "mythic", 60.0))
        )
        assertEquals(
            listOf("1 60.00 ", "2 0.20 ", "1 0.10 ", "1 0.10 Krenko"),
            pileTallies(session).map { "${it.cards} ${String.format(java.util.Locale.US, "%.2f", it.usd)} ${it.decks.joinToString(",")}" }
        )
        // Cards already owned: scanning them doesn't make more.
        val tidy = session.copy(newCards = false, scans = emptyList())
        assertEquals(1, nextPile(tidy.copy(scans = listOf(scan("shock", "Shock", 1))), "Shock", null, null, owned, wanted)?.index)
    }

    @Test
    fun whereAPilesCardsGo() {
        val bolt = CardFacts("Lightning Bolt", listOf("R"), "Instant")
        assertEquals(PileDestination(Spot("red", "Red"), "Red box › Red"), pileDestination(rules[1], bolt, cols))
        assertEquals(PileDestination(null, "No place yet"), pileDestination(rules[1], CardFacts("Island", emptyList(), "Basic Land — Island"), cols))
        assertEquals(PileDestination(Spot("rares", page = 1, slot = 1), "Rares binder"), pileDestination(rules[0], bolt, cols))
        assertEquals(PileDestination(null, "No place yet"), pileDestination(rules[3], bolt, cols))
    }

    @Test
    fun doneEveryPileIsFiledNewCardsAddedAtTheirPlaces() {
        val session = SortSession(
            "", rules, true,
            listOf(scan("ring", "The One Ring", 0), scan("bolt", "Lightning Bolt", 1), scan("matron", "Goblin Matron", 3), scan("ring", "The One Ring", 0))
        )
        val filed = fileEveryPile(cols, session)
        assertEquals(4, filed.added)
        assertEquals(
            listOf("The One Ring → Rares binder", "Lightning Bolt → Red box › Red", "Goblin Matron → No place yet", "The One Ring → Rares binder"),
            filed.steps.map { "${it.scan.name} → ${it.to}" }
        )
        val pile = filed.collections.first { it.isUnsorted }
        assertEquals(
            listOf(
                "Shock ×3 null",
                "The One Ring ×2 [rares 1 p1 s1, rares 1 p1 s2]",
                "Lightning Bolt ×1 [red 1 Red]",
                "Goblin Matron ×1 null"
            ),
            pile.entries.map { e ->
                "${e.name} ×${e.quantity} " + (e.places?.joinToString(", ", "[", "]") { p ->
                    listOfNotNull(p.placeId, p.qty.toString(), p.section, p.page?.let { "p$it" }, p.slot?.let { "s$it" }).joinToString(" ")
                } ?: "null")
            }
        )
        // Cards already owned are put away instead: the Shocks with no place go into the box.
        val tidy = fileEveryPile(cols, session.copy(newCards = false, scans = listOf(scan("shock", "Shock", 1))))
        assertEquals(0, tidy.added)
        assertEquals(listOf(CopyPlace("red", 1, section = "Red")), tidy.collections.first { it.isUnsorted }.entries[0].places)
    }
}
