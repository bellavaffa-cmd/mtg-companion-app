package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Collection's layout: the tabs and old routes, the location chips on a card row, Group by, the
 * quick filters and the Binders / Storage counts. The web app has the same cases — see
 * MtgCompanionWeb/tests/collection/collectionLayout.test.ts.
 */
class CollectionLayoutTest {

    private fun entry(id: String, name: String, qty: Int, foil: Int = 0, forSale: Int? = null, places: List<CopyPlace>? = null, tags: List<String> = emptyList()) =
        CollectionEntry(id, name, null, quantity = qty, foilQuantity = foil, forSale = forSale, places = places, userTags = tags)

    private val placesList = listOf(
        StoragePlace("shelf", "Shelf", PlaceKind.SHELF.name, createdAt = 1),
        StoragePlace("blue", "Blue box", PlaceKind.BOX.name, parentId = "shelf", createdAt = 1),
        StoragePlace("trade", "Trade binder", PlaceKind.BINDER.name, createdAt = 1)
    )
    private val pile = Collection(
        UNSORTED_COLLECTION_ID, UNSORTED_COLLECTION_NAME,
        listOf(
            // 2 in the Blue box's Instants, 1 lent to Sam, 1 with no place.
            entry("opt", "Opt", 4, places = listOf(CopyPlace("blue", 2, section = "Instants"))),
            entry("bolt", "Lightning Bolt", 3, forSale = 1),
            entry("elf", "Llanowar Elves", 1, tags = listOf("lent to Priya"))
        ),
        createdAt = 0,
        storagePlaces = placesList,
        graded = listOf(GradedCard("g1", "ring2", "Sol Ring", company = GradingCompany.PSA.name, grade = "9", createdAt = 1)),
        loans = listOf(Loan("l1", "Sam", cards = listOf(LoanCard("Opt", "opt", qty = 1, collectionId = UNSORTED_COLLECTION_ID)), lentAt = 1))
    )
    private val trades = Collection(
        "trades", "Trades",
        listOf(
            entry("opt", "Opt", 1, places = listOf(CopyPlace("trade", 1, page = 3, slot = 2))),
            entry("ring", "Sol Ring", 1, places = listOf(CopyPlace("blue", 1, section = "Artifacts")))
        ),
        createdAt = 0
    )
    private val wishlist = Collection(WISHLIST_ID, "Wishlist", listOf(entry("mox", "Mox Opal", 1)), createdAt = 0, type = CollectionType.WISHLIST.name)
    private val talrand = Deck(
        "talrand", "Talrand",
        cards = listOf(DeckCardEntry("opt", "Opt", null, quantity = 1), DeckCardEntry("ring", "Sol Ring", null, quantity = 1)),
        ownership = DeckOwnership.PHYSICAL.name, createdAt = 1
    )
    private val library = listOf(pile, trades, wishlist)
    private val index = locationIndex(library, listOf(talrand))

    @Test
    fun `old routes map onto the four tabs`() {
        assertEquals(CollectionRoute(CollectionTab.CARDS), collectionRoute("all"))
        assertEquals(CollectionRoute(CollectionTab.CARDS), collectionRoute("cards"))
        assertEquals(CollectionRoute(CollectionTab.CARDS, group = CardGroupBy.SET), collectionRoute("sets"))
        assertEquals(CollectionRoute(CollectionTab.BINDERS), collectionRoute("binders"))
        assertEquals(CollectionRoute(CollectionTab.STORAGE), collectionRoute("storage"))
        assertEquals(CollectionRoute(CollectionTab.STORAGE), collectionRoute("sealed"))
        assertEquals(CollectionRoute(CollectionTab.WISHLIST), collectionRoute("wishlist"))
        assertEquals(CollectionRoute(CollectionTab.CARDS, quick = QuickFilter.LENT_OUT), collectionRoute("loans"))
        assertEquals(CollectionRoute(CollectionTab.CARDS, quick = QuickFilter.FOR_SALE), collectionRoute("sell"))
        assertEquals(CollectionRoute(CollectionTab.CARDS, quick = QuickFilter.GRADED), collectionRoute("graded"))
        assertEquals(CollectionRoute(CollectionTab.CARDS, shared = true), collectionRoute("shared"))
        assertNull(collectionRoute(null))
        assertNull(collectionRoute("nonsense"))
        // No tab said: the one used last, else Cards.
        assertEquals(CollectionTab.STORAGE, startTab(null, "storage"))
        assertEquals(CollectionTab.CARDS, startTab(null, null))
        assertEquals(CollectionTab.CARDS, startTab(null, "sets"))
        assertEquals(CollectionTab.WISHLIST, startTab(collectionRoute("wishlist"), "storage"))
        assertEquals(listOf("Cards", "Binders", "Storage", "Wishlist"), CollectionTab.entries.map { it.label })
    }

    @Test
    fun `a printing's chips - places, decks, loans, no place`() {
        val opt = index.getValue("opt")
        assertEquals(
            listOf("Blue box › Instants ×2", "Trade binder ×1", "Talrand deck ×1", "Lent to Sam ×1", "No place yet · Put away"),
            opt.map(::chipText)
        )
        assertEquals(
            listOf(
                "Stored in Blue box, Instants section, 2 copies",
                "Stored in Trade binder, page 3, 1 copy",
                "In Talrand deck, 1 copy",
                "Lent to Sam, 1 copy",
                "No place yet, 1 copy. Put away"
            ),
            opt.map(::chipDescription)
        )
        assertEquals("blue", opt[0].placeId)
        assertEquals("Instants", opt[0].section)
        assertEquals(3, opt[1].page)
        assertEquals("talrand", opt[2].deckId)
        // A copy tagged "lent …" with no loan is lent out, not unplaced.
        assertEquals(listOf("Lent out ×1"), index.getValue("elf").map(::chipText))
        assertEquals(listOf("No place yet · Put away"), index.getValue("bolt").map(::chipText))
        assertEquals(listOf("Blue box › Artifacts ×1", "Talrand deck ×1"), index.getValue("ring").map(::chipText))
        // The wishlist's cards aren't owned; a slab has no chip.
        assertFalse("mox" in index)
        assertFalse("ring2" in index)
    }

    @Test
    fun `a row shows two chips then +N, the amber one always`() {
        val opt = index.getValue("opt")
        val (shown, more) = shownChips(opt)
        assertEquals(listOf("Blue box › Instants ×2", "No place yet · Put away"), shown.map(::chipText))
        assertEquals(3, more)
        val (ring, none) = shownChips(index.getValue("ring"))
        assertEquals(2, ring.size)
        assertEquals(0, none)
        assertEquals("3 more places", moreChipsDescription(3))
        assertEquals("1 more place", moreChipsDescription(1))
        assertEquals(emptyList<LocationChip>() to 0, shownChips(emptyList()))
    }

    @Test
    fun `a binder entry's own chips`() {
        val byId = placesList.associateBy { it.id }
        val lent = lentByEntry(lentCopies(library, listOf(talrand)))
        assertEquals(
            listOf("Blue box › Instants ×2", "No place yet · Put away"),
            entryChips(UNSORTED_COLLECTION_ID, pile.entries[0], byId, lent).map(::chipText)
        )
        assertEquals(listOf("Trade binder ×1"), entryChips("trades", trades.entries[0], byId, lent).map(::chipText))
        assertEquals(emptyList<LocationChip>(), entryChips("x", entry("z", "Z", 0), byId, lent))
    }

    @Test
    fun `group by set, binder and place`() {
        val cards = listOf("opt", "bolt", "ring", "elf")
        val sets = mapOf("opt" to ("m19" to "Core Set 2019"), "bolt" to ("m10" to "Magic 2010"), "ring" to ("c21" to ""))
        val bySet = groupLayout(cards, { setKeys(sets[it]) }, "Set not known yet") { code -> if (code == "m19") setGroupDetail(41, 92) else setGroupDetail(1, 0) }
        assertEquals(listOf("C21", "Core Set 2019", "Magic 2010", "Set not known yet"), bySet.map { it.title })
        assertEquals("41/92", bySet[1].detail)
        assertEquals("1", bySet[0].detail)
        assertNull(bySet[3].detail)
        assertEquals(listOf("elf"), bySet[3].items)

        val byPlace = groupLayout(cards, { placeKeys(index[it].orEmpty()) }, NO_PLACE_TITLE)
        assertEquals(listOf("Blue box", "Lent out", "Talrand deck", "Trade binder", "No place yet"), byPlace.map { it.title })
        assertEquals(listOf("opt", "ring"), byPlace[0].items)
        assertEquals(listOf("opt", "elf"), byPlace[1].items)
        assertEquals(listOf("opt", "bolt"), byPlace[4].items)

        val sources = mapOf(
            "opt" to listOf(Triple("binder", UNSORTED_COLLECTION_ID, "Unsorted"), Triple("binder", "trades", "Trades"), Triple("deck", "talrand", "Talrand")),
            "ring" to listOf(Triple("binder", "trades", "Trades"))
        )
        val byBinder = groupLayout(listOf("opt", "ring"), { binderKeys(sources[it].orEmpty()) }, "No binder")
        assertEquals(listOf("Talrand deck", "Trades", "Unsorted"), byBinder.map { it.title })
        assertEquals(listOf("opt", "ring"), byBinder[1].items)
    }

    @Test
    fun `quick filters`() {
        val sets = quickSets(library, index)
        val cards = listOf("opt" to "Opt", "bolt" to "Lightning Bolt", "ring" to "Sol Ring", "elf" to "Llanowar Elves")
        fun kept(q: QuickFilter?) = cards.filter { passesQuick(q, it.first, it.second, sets) }.map { it.first }
        assertEquals(listOf("opt", "elf"), kept(QuickFilter.LENT_OUT))
        assertEquals(listOf("bolt"), kept(QuickFilter.FOR_SALE))
        assertEquals(listOf("opt", "bolt"), kept(QuickFilter.NO_PLACE))
        // The slab is another printing of Sol Ring: the raw one shows.
        assertEquals(listOf("ring"), kept(QuickFilter.GRADED))
        assertEquals(cards.map { it.first }, kept(null))
        assertEquals(2, quickCount(QuickFilter.NO_PLACE, cards, sets))
        assertEquals(QuickFilter.FOR_SALE, QuickFilter.fromKey("sale"))
        assertEquals(listOf("Lent out", "For sale", "No place yet", "Graded"), QuickFilter.entries.map { it.label })
    }

    @Test
    fun `binders kept in places, places in binders, needs attention`() {
        val known = placesList.map { it.id }.toSet()
        assertEquals(1, placesUsed(pile, known))
        assertEquals(2, placesUsed(trades, known))
        assertEquals("kept in 2 places", keptInPlacesLine(2, 2))
        assertEquals("kept in 1 place", keptInPlacesLine(1, 9))
        assertEquals("no place yet", keptInPlacesLine(0, 3))
        assertEquals("", keptInPlacesLine(0, 0))
        val byPlace = bindersByPlace(library)
        // The Unsorted pile isn't a binder: only Trades counts.
        assertEquals(1, bindersIn(byPlace, placesList, "shelf"))
        assertEquals(1, bindersIn(byPlace, placesList, "trade"))
        assertEquals("in 1 binder", inBindersLine(1))
        assertEquals("in 3 binders", inBindersLine(3))
        assertEquals("", inBindersLine(0))
        assertEquals("Needs attention · 3", needsAttentionLabel(3))
        assertNull(needsAttentionLabel(0))
        assertTrue(CardGroupBy.fromKey("nope") == CardGroupBy.NONE)
    }

    @Test
    fun `find anything chips say themselves the same way`() {
        assertEquals("Stored in Red box, Colourless section, 1 copy", findChipDescription(FindChip(FindChipKind.PLACE, "Red box › Colourless", 1, placeId = "red")))
        assertEquals("Stored in Shelf, 3 copies", findChipDescription(FindChip(FindChipKind.PLACE, "Shelf", 3)))
        assertEquals("In Atraxa deck, 1 copy", findChipDescription(FindChip(FindChipKind.DECK, "Atraxa deck", 1)))
        assertEquals("Lent to Sam, 2 copies", findChipDescription(FindChip(FindChipKind.LENT, "Lent to Sam", 2)))
        assertEquals("No place yet, 1 copy", findChipDescription(FindChip(FindChipKind.NONE, "No place", 1)))
    }
}
