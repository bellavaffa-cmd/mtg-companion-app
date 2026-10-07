package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Find anything: the index, the matching and how a card's copies are grouped. The web app has the
 * same cases — see MtgCompanionWeb/tests/collection/findAnything.test.ts.
 */
class FindAnythingTest {

    private fun entry(id: String, name: String, qty: Int, forTrade: Int? = null, forSale: Int? = null, places: List<CopyPlace>? = null) =
        CollectionEntry(id, name, null, quantity = qty, forTrade = forTrade, forSale = forSale, places = places)

    private fun card(id: String, name: String, qty: Int = 1) = DeckCardEntry(id, name, null, quantity = qty)

    private val pile = Collection(
        UNSORTED_COLLECTION_ID, UNSORTED_COLLECTION_NAME,
        listOf(
            entry("ring1", "Sol Ring", 2, forTrade = 1, places = listOf(CopyPlace("red", 1, section = "Colourless"))),
            entry("bolt", "Lightning Bolt", 3, forSale = 2),
            entry("vial", "Æther Vial", 1)
        ),
        createdAt = 0,
        storagePlaces = listOf(
            StoragePlace("shelf", "Shelf, study", PlaceKind.SHELF.name, createdAt = 1),
            StoragePlace("red", "Red box", PlaceKind.BOX.name, parentId = "shelf", createdAt = 1),
            StoragePlace("blue", "Blue box", PlaceKind.BOX.name, parentId = "shelf", createdAt = 1),
            StoragePlace("rares", "Rares binder", PlaceKind.BINDER.name, parentId = "red", createdAt = 1)
        ),
        graded = listOf(GradedCard("g1", "ring2", "Sol Ring", company = GradingCompany.PSA.name, grade = "9", createdAt = 1)),
        loans = listOf(Loan("l1", "Sam", cards = listOf(LoanCard("Sol Ring", "ring1", qty = 1, collectionId = UNSORTED_COLLECTION_ID)), lentAt = 1))
    )
    private val wishlist = Collection(WISHLIST_ID, "Wishlist", listOf(entry("mox", "Mox Opal", 1)), createdAt = 0, type = CollectionType.WISHLIST.name)
    private val atraxa = Deck("atraxa", "Atraxa", cards = listOf(card("ring3", "Sol Ring")), ownership = DeckOwnership.PHYSICAL.name, createdAt = 1)
    private val krenko = Deck("krenko", "Krenko goblins", cards = listOf(card("ring4", "Sol Ring"), card("gob", "Goblin Guide", 4)), ownership = DeckOwnership.PROXY.name, createdAt = 1)
    private val solDeck = Deck("sol", "Solar flare", ownership = DeckOwnership.PHYSICAL.name, createdAt = 1, gameMode = GameMode.MODERN.name)
    private val index = buildFindIndex(listOf(pile, wishlist), listOf(atraxa, krenko, solDeck))

    @Test
    fun `normalize and match`() {
        assertEquals("aether vial", normalizeFind("Æther Vial"))
        assertEquals("urzas saga", normalizeFind("Urza's  Saga!"))
        val ring = FindName("Sol Ring")
        assertEquals(4, matchScore(ring, "sol ring"))
        assertEquals(3, matchScore(ring, "sol r"))
        assertEquals(2, matchScore(ring, "ri so"))
        assertEquals(1, matchScore(ring, "l ri"))
        assertEquals(0, matchScore(ring, "ring sol x"))
        assertEquals(0, matchScore(ring, ""))
    }

    @Test
    fun `a card with every place its copies are`() {
        val found = findIn(index, "sol ri")
        assertEquals(listOf("Sol Ring"), found.cards.map { it.name })
        val ring = found.cards[0]
        // 2 in the pile (1 in the Red box, 1 lent to Sam), 1 in Atraxa, 1 graded; Krenko's is a proxy.
        assertEquals(4, ring.copies)
        assertEquals("4 copies", copiesLine(ring))
        assertEquals(
            listOf("Red box › Colourless ×1", "Atraxa deck ×1", "Lent to Sam ×1", "Graded PSA 9 ×1", "For trade ×1"),
            ring.chips.map { chipLabel(it) }
        )
        assertEquals(FindChipKind.LENT, ring.chips[2].kind)
        assertEquals(
            listOf(FoundDeck("atraxa", "Atraxa", "Commander"), FoundDeck("krenko", "Krenko goblins", "Commander · proxy")),
            found.decksUsing
        )
        // "sol" also names a deck: it's found by its name.
        assertEquals(listOf(FoundDeck("sol", "Solar flare", "Modern")), findIn(index, "sol").decks)
    }

    @Test
    fun `copies with no place, to sell, and cards only in decks as proxies`() {
        val bolt = findIn(index, "bolt").cards[0]
        assertEquals(listOf("No place ×3", "To sell ×2"), bolt.chips.map { chipLabel(it) })
        val gob = findIn(index, "goblin guide").cards[0]
        assertEquals(0, gob.copies)
        assertEquals("Proxy only", copiesLine(gob))
        assertEquals("Æther Vial", findIn(index, "aether").cards[0].name)
        // The wishlist isn't the user's.
        assertEquals(0, findIn(index, "mox").cards.size)
    }

    @Test
    fun `places, with what is inside`() {
        assertEquals(listOf(FoundPlace("shelf", "Shelf, study", "3 places inside")), findIn(index, "shelf").places)
        assertEquals(listOf(FoundPlace("red", "Red box", "1 place inside · in Shelf, study")), findIn(index, "red").places)
        assertEquals(listOf(FoundPlace("blue", "Blue box", "Box · 0 copies · in Shelf, study")), findIn(index, "blue").places)
    }

    @Test
    fun `typing on narrows the last results, and starting over searches everything`() {
        val finder = Finder(index)
        assertEquals(1, finder.find("s").cards.size)
        assertEquals("Sol Ring", finder.find("so").cards[0].name)
        assertEquals(0, finder.find("sol x").cards.size)
        // Back to a shorter query: everything again.
        assertEquals("Lightning Bolt,Æther Vial,Goblin Guide,Sol Ring", finder.find("l").cards.joinToString(",") { it.name })
        assertEquals(0, finder.find("").cards.size)
        assertEquals("Lightning Bolt", finder.find("bolt").cards[0].name)
    }

    @Test
    fun `better matches first`() {
        val idx = buildFindIndex(listOf(Collection("b", "B", listOf(entry("a", "Ring of Thune", 1), entry("b", "Sol Ring", 1), entry("c", "Ring", 1)), createdAt = 0)), emptyList())
        assertEquals(listOf("Ring", "Ring of Thune", "Sol Ring"), findIn(idx, "ring").cards.map { it.name })
    }
}
