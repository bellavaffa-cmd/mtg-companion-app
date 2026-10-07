package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Collection's home: the tiles' numbers and the To do. The web app has the same cases — see
 * MtgCompanionWeb/tests/collection/collectionHome.test.ts.
 */
class CollectionHomeTest {

    private fun entry(id: String, qty: Int, forSale: Int? = null, places: List<CopyPlace>? = null) =
        CollectionEntry(id, id, null, quantity = qty, forSale = forSale, places = places)

    private fun card(id: String, qty: Int = 1) = DeckCardEntry(id, id, null, quantity = qty)

    private val pile = Collection(
        UNSORTED_COLLECTION_ID, UNSORTED_COLLECTION_NAME,
        listOf(entry("ring", 3, places = listOf(CopyPlace("red", 2))), entry("bolt", 4, forSale = 3)),
        createdAt = 0,
        storagePlaces = listOf(StoragePlace("red", "Red box", PlaceKind.BOX.name, createdAt = 1), StoragePlace("shelf", "Shelf", PlaceKind.SHELF.name, createdAt = 1)),
        sealed = listOf(SealedProduct("s1", "Play box", SealedKind.PLAY_BOX.name, count = 2, valueUsd = 100.0, createdAt = 1)),
        graded = listOf(GradedCard("g1", "ring", "ring", company = GradingCompany.PSA.name, grade = "9", valueUsd = 40.0, createdAt = 1)),
        loans = listOf(Loan("l1", "Sam", cards = listOf(LoanCard("bolt", "bolt", qty = 1, collectionId = UNSORTED_COLLECTION_ID)), lentAt = 1))
    )
    private val trades = Collection("trades", "Trade binder", listOf(entry("elf", 2), entry("ring", 1)), createdAt = 0)
    private val wishlist = Collection(WISHLIST_ID, "Wishlist", listOf(entry("dream", 1), entry("other", 1)), createdAt = 0, type = CollectionType.WISHLIST.name)
    private val deck = Deck("d1", "Krenko", cards = listOf(card("gob", 4), card("ring")), ownership = DeckOwnership.PHYSICAL.name, createdAt = 1)

    @Test
    fun `the tiles count cards, places, binders, sealed and graded, loans and selling`() {
        val n = homeNumbers(listOf(pile, trades, wishlist), listOf(deck))
        // ring, bolt, elf, gob: printings, not copies; the wishlist's don't count.
        assertEquals(4, n.cards)
        assertEquals(2, n.places)
        assertEquals(1, n.binders)
        assertEquals(2, n.wishlist)
        assertEquals(3, n.sealedAndGraded)
        assertEquals(240.0, n.sealedAndGradedUsd, 0.001)
        assertEquals(1, n.lentOut)
        assertEquals(3, n.toSell)
        // 3 + 4 + 2 + 1 binder copies and 5 in the deck: 2 in the Red box, 5 in the deck, 1 lent.
        assertEquals(8 * 100 / 15, n.placedPercent)
        val tiles = homeTiles(n) { "$" + it.toInt() }
        assertEquals(
            listOf(
                "All cards: 4 · filters",
                "Storage: 2 places · 53% placed",
                "Binders: 1 · wishlist",
                "Sets: completion",
                "Sealed and graded: 3 items · $240",
                "Loans and selling: 1 out · 3 to sell"
            ),
            tiles.map { "${it.title}: ${it.line}" }
        )
    }

    @Test
    fun `an empty collection asks to set up places`() {
        val tiles = homeTiles(homeNumbers(emptyList(), emptyList())) { "$" + it.toInt() }
        assertEquals("Set up your places", tiles[1].line)
        assertEquals("Boxes and slabs", tiles[4].line)
        assertEquals("0 out · 0 to sell", tiles[5].line)
    }

    private fun item(kind: UpkeepKind, title: String, detail: String = "", action: String? = null, deckId: String? = null) = UpkeepItem(
        kind, title, detail,
        action ?: when (kind) { UpkeepKind.PUT_AWAY -> "Put away"; UpkeepKind.CARRY_ON -> "Carry on"; else -> "Check" },
        deckId = deckId
    )

    @Test
    fun `the To do - one of each kind first, put away and a pull list on top, at most three`() {
        val items = listOf(
            item(UpkeepKind.PUT_AWAY, "118 copies have no place"),
            item(UpkeepKind.CHECK, "Red box not checked in 120 days"),
            item(UpkeepKind.CHECK, "Blue box not checked in 100 days"),
            item(UpkeepKind.SPLIT, "Old box is 96% full", action = "Split"),
            item(UpkeepKind.CARRY_ON, "Krenko deck pull list half done", "30 of 60 pulled, started Tuesday", deckId = "d1")
        )
        val decks = mapOf("d1" to "Krenko")
        assertEquals(
            listOf(
                "118 copies have no place · Put away",
                "Krenko pull list: 30 of 60 · Carry on",
                "Red box not checked in 120 days · Check"
            ),
            homeTodo(items, decks).map { "${it.title} · ${it.action}" }
        )
        assertEquals(5, homeTodo(items, decks, 10).size)
        // Blue box comes after the Split, as the second Check.
        assertEquals(listOf("Old box is 96% full", "Blue box not checked in 100 days"), homeTodo(items, decks, 10).drop(3).map { it.title })
        assertEquals(emptyList<HomeTodo>(), homeTodo(emptyList(), decks))
    }

    @Test
    fun `a pull list whose deck is gone keeps Upkeep words`() {
        val todo = homeTodo(listOf(item(UpkeepKind.CARRY_ON, "Old deck pull list started", "2 of 10 pulled", deckId = "gone")), emptyMap())
        assertEquals("Old deck pull list started", todo[0].title)
        val named = homeTodo(listOf(item(UpkeepKind.CARRY_ON, "x", "2 of 10 pulled", deckId = "g")), mapOf("g" to "Goblin deck"))
        assertEquals("Goblin pull list: 2 of 10", named[0].title)
    }
}
