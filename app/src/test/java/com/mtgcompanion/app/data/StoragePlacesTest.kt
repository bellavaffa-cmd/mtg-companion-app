package com.mtgcompanion.app.data

import com.mtgcompanion.app.data.supabase.ItemMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Storage places: which copies are where, kept the same way on both apps. The web app has the same
 * checks — see MtgCompanionWeb/tests/collection/storagePlaces.test.ts.
 */
class StoragePlacesTest {

    private fun place(id: String, name: String = id, kind: PlaceKind = PlaceKind.BOX, parentId: String? = null, note: String? = null,
                      sections: List<String>? = null, sortRule: SortRule? = null, createdAt: Long = 1) =
        StoragePlace(id, name, kind.name, parentId, note, sections, null, sortRule?.name, createdAt)

    private fun entry(id: String, quantity: Int, foilQuantity: Int = 0, places: List<CopyPlace>? = null, name: String = id, userTags: List<String> = emptyList()) =
        CollectionEntry(id, name, null, quantity = quantity, foilQuantity = foilQuantity, userTags = userTags, places = places)

    private fun at(placeId: String, qty: Int, foil: Boolean = false, section: String? = null, page: Int? = null, slot: Int? = null) =
        CopyPlace(placeId, qty, if (foil) true else null, section, page, slot)

    private fun pile(entries: List<CollectionEntry>, places: List<StoragePlace>? = null) =
        Collection(UNSORTED_COLLECTION_ID, UNSORTED_COLLECTION_NAME, entries, createdAt = 0, type = CollectionType.OWNED.name, storagePlaces = places)

    private fun binder(id: String, entries: List<CollectionEntry>) = Collection(id, id, entries, createdAt = 5, type = CollectionType.OWNED.name)

    private val shelf = place("shelf", "Shelf, study", PlaceKind.SHELF, createdAt = 1)
    private val red = place("red", "Red box", parentId = "shelf", note = "Bulk", sortRule = SortRule.COLOUR, createdAt = 2)
    private val trade = place("trade", "Trade binder", PlaceKind.BINDER, parentId = "shelf", createdAt = 3)
    private val places = listOf(shelf, red, trade)

    private fun deckOf(id: String, name: String, scryfallId: String, cardName: String, quantity: Int, ownership: DeckOwnership = DeckOwnership.PHYSICAL) =
        Deck(id, name, cards = listOf(DeckCardEntry(scryfallId, cardName, null, quantity = quantity)), ownership = ownership.name)

    @Test
    fun placesNestTheTreePathsAndNothingMovedInsideItself() {
        assertEquals(listOf("0:shelf", "1:red", "1:trade"), placeTree(places).map { "${it.depth}:${it.place.id}" })
        assertEquals("Shelf, study › Red box", placePath(places, "red"))
        assertEquals(listOf("red", "shelf", "trade"), placeAndInside(places, "shelf").sorted())
        assertFalse(canMoveInto(places, "shelf", "red"))
        assertTrue(canMoveInto(places, "red", "trade"))
        assertTrue(canMoveInto(places, "red", null))
        // Two devices each moved one into the other: both sit at the top rather than vanishing.
        val loop = listOf(place("a", parentId = "b"), place("b", parentId = "a"))
        assertNull(parentOf(loop, "a"))
        assertEquals(2, placeTree(loop).size)
    }

    @Test
    fun aPlaceSaysWhatItIs() {
        assertEquals("Bulk · by colour, then A–Z", placeSubtitle(red))
        assertEquals("Binder · 9 per page", placeSubtitle(trade))
        assertEquals("Deck box", placeSubtitle(place("x", kind = PlaceKind.DECK_BOX)))
    }

    @Test
    fun placesAreKeptOnTheUnsortedPile() {
        val out = withPlaceList(listOf(binder("b1", emptyList())), places)
        assertEquals(listOf("shelf", "red", "trade"), placesOf(out).map { it.id })
        assertEquals(3, out.first { it.isUnsorted }.storagePlaces?.size)
    }

    @Test
    fun neverMorePlacedThanTheCopiesPlainAndFoilApartTheFirstLinesKeepTheirs() {
        assertEquals(
            listOf(at("red", 2), at("red", 1, foil = true)),
            tidyPlaces(2, 1, listOf(at("red", 1), at("trade", 2), at("red", 1, foil = true), at("red", 1), at("x", 0)))
        )
        assertEquals(1 to 1, unplacedCopies(entry("bolt", 3, 1, listOf(at("red", 2)))))
    }

    @Test
    fun givingCopiesAPlaceMovingThemAndTakingItAway() {
        var e = entry("bolt", 3, 0)
        e = placeCopies(e, Spot("red", section = "Red"), 2, false).first
        assertEquals(listOf(at("red", 2, section = "Red")), e.places)
        // Only one copy is left with no place.
        assertEquals(1, placeCopies(e, Spot("trade"), 5, false).second)
        e = moveCopies(e, at("red", 1, section = "Red"), Spot("trade", page = 3, slot = 5), 1).first
        assertEquals(listOf(at("red", 1, section = "Red"), at("trade", 1, page = 3, slot = 5)), e.places)
        e = moveCopies(e, at("trade", 1, page = 3, slot = 5), null, 1).first
        // No place left for those: the key stays, so an older app's save can be told apart.
        e = moveCopies(e, at("red", 1, section = "Red"), null, 1).first
        assertEquals(emptyList<CopyPlace>(), e.places)
    }

    @Test
    fun copiesMovingToAnotherBinderTakeTheirPlacesTheUnplacedOnesFirst() {
        val e = entry("bolt", 4, 0, listOf(at("red", 2), at("trade", 1)))
        assertEquals(listOf(at("red", 2), at("trade", 1)) to emptyList<CopyPlace>(), splitPlaces(e, 1, 0))
        assertEquals(listOf(at("red", 1)) to listOf(at("red", 1), at("trade", 1)), splitPlaces(e, 3, 0))
    }

    @Test
    fun howMuchHasAPlacePlacesDeckBoxesAndLoans() {
        val collections = listOf(
            pile(listOf(entry("bolt", 3, 0, listOf(at("red", 2))), entry("lent", 2, userTags = listOf("lent to Sam"))), places),
            binder("b1", listOf(entry("ring", 1, 0, listOf(at("trade", 1), at("gone", 1))), entry("elf", 2))),
            Collection("wish", "wish", listOf(entry("want", 4)), createdAt = 5, type = CollectionType.WISHLIST.name)
        )
        val decks = listOf(
            deckOf("d1", "Goblins", "g", "Goblin", 4),
            deckOf("d2", "Online", "g", "Goblin", 4, DeckOwnership.VIRTUAL)
        )
        assertEquals(StorageSummary(12, 9, 3, 4, 2, mapOf("red" to 2, "trade" to 1)), storageSummary(collections, decks))
    }

    @Test
    fun whereACardIsPlacesWithTheirSpotDecksLoansAndNoPlaceYet() {
        val collections = listOf(
            pile(listOf(entry("bolt-a", 2, 1, listOf(at("red", 2, section = "Red"), at("trade", 1, foil = true, page = 3, slot = 5)), name = "Lightning Bolt")), places),
            binder("Rares", listOf(entry("bolt-b", 1, name = "Lightning Bolt")))
        )
        val decks = listOf(deckOf("d1", "Krenko goblins", "bolt-a", "Lightning Bolt", 1))
        val (lines, total) = whereItIs(collections, decks, "Lightning Bolt")
        assertEquals(5, total)
        assertEquals(
            listOf(
                listOf("PLACE", "Red box › Red", "Shelf, study · around “L”", "2"),
                listOf("PLACE", "Trade binder", "Shelf, study · Page 3, slot 5 · foil", "1"),
                listOf("DECK", "Deck: Krenko goblins", "In its deck box", "1"),
                listOf("NONE", "No place yet", "In Rares", "1")
            ),
            lines.map { listOf(it.kind.name, it.title, it.detail, it.qty.toString()) }
        )
    }

    @Test
    fun sortingRulesSuggestASectionAndASpot() {
        val bolt = CardFacts("Lightning Bolt", listOf("R"), "Instant", "2x2", "117")
        assertEquals("Red", colourSection(bolt))
        assertEquals("Colourless", colourSection(CardFacts("Golos", emptyList(), "Legendary Artifact Creature")))
        assertEquals("Multicolour", colourSection(CardFacts("Niv", listOf("W", "U"), "Creature")))
        assertEquals("Lands", colourSection(CardFacts("Dryad Arbor", listOf("G"), "Land Creature — Forest Dryad")))
        assertEquals("Creatures", typeSection(CardFacts("x", typeLine = "Artifact Creature — Golem")))
        assertEquals("Instants", typeSection(CardFacts("x", typeLine = "Kindred Instant — Elf")))
        assertEquals(Spot("red", section = "Red") to "Red › around “L”", suggestSpot(red, bolt, emptyList()))
        assertEquals("2X2 › around #117", suggestSpot(place("s", sortRule = SortRule.SET), bolt, emptyList()).second)
        // A–Z with letter sections: the one holding the name's letter.
        assertEquals("G–M › around “L”", suggestSpot(place("n", sortRule = SortRule.NAME, sections = listOf("A–F", "G–M", "N–Z")), bolt, emptyList()).second)
        // The box's own spelling of a section wins.
        assertEquals("RED", suggestSpot(place("c", sortRule = SortRule.COLOUR, sections = listOf("RED")), bolt, emptyList()).first.section)
    }

    @Test
    fun aBinderFillsPocketAfterPocketPageAfterPage() {
        val nine = place("nine", kind = PlaceKind.BINDER)
        assertEquals(1 to 1, nextPocket(nine, emptyList()))
        assertEquals(2 to 1, nextPocket(nine, listOf(pile(listOf(entry("a", 1, 0, listOf(at("nine", 1, page = 1, slot = 9))))))))
        assertEquals(2 to 4, nextPocket(nine, listOf(pile(listOf(entry("a", 2, 0, listOf(at("nine", 1, page = 2, slot = 3), at("nine", 1, page = 1, slot = 8))))))))
    }

    @Test
    fun aBoxsSectionsItsOwnInOrderThenOthersUsedThenCopiesInNone() {
        val box = place("box", sections = listOf("White", "Blue"))
        val collections = listOf(pile(listOf(entry("a", 3, 0, listOf(at("box", 1, section = "Blue"), at("box", 1, section = "Lands"), at("box", 1))))))
        assertEquals(
            listOf("White" to 0, "Blue" to 1, "Lands" to 1, null to 1),
            sectionsOf(box, cardsIn(collections, "box")).map { it.name to it.copies }
        )
    }

    private fun newEntry(id: String, name: String = id) = CollectionEntry(id, name, null)

    @Test
    fun puttingAwayACopyWithNoPlaceFirstTheUnsortedPileBeforeBinders() {
        val collections = listOf(binder("b1", listOf(entry("bolt", 1))), pile(listOf(entry("bolt", 1)), places))
        val out = putAway(collections, "bolt", "bolt", Spot("red", section = "Red"), newEntry("bolt"))
        assertEquals(PutAwayResult.PLACED, out.result)
        assertEquals("moved from Unsorted", out.label)
        assertEquals(listOf(at("red", 1, section = "Red")), out.collections[1].entries[0].places)
        assertNull(out.collections[0].entries[0].places)
        // Undo takes the place away again.
        assertEquals(emptyList<CopyPlace>(), undoPutAway(out.collections, out.step!!)[1].entries[0].places)
    }

    @Test
    fun puttingAwayAnotherPrintingOfTheCardCountsAfterTheSamePrinting() {
        val collections = listOf(pile(listOf(entry("bolt-old", 1, name = "Lightning Bolt")), places))
        val out = putAway(collections, "bolt-new", "Lightning Bolt", Spot("red"), newEntry("bolt-new", "Lightning Bolt"))
        assertEquals(PutAwayResult.PLACED, out.result)
        assertEquals("bolt-old", out.step?.scryfallId)
    }

    @Test
    fun puttingAwayACopyKeptElsewhereMovesHereAndUndoMovesItBack() {
        val collections = listOf(pile(listOf(entry("bolt", 1, 0, listOf(at("trade", 1, page = 1, slot = 1)))), places))
        val out = putAway(collections, "bolt", "bolt", Spot("red", section = "Red"), newEntry("bolt"))
        assertEquals(PutAwayResult.MOVED, out.result)
        assertEquals("moved from Trade binder", out.label)
        assertEquals(listOf(at("red", 1, section = "Red")), out.collections[0].entries[0].places)
        assertEquals(listOf(at("trade", 1, page = 1, slot = 1)), undoPutAway(out.collections, out.step!!)[0].entries[0].places)
    }

    @Test
    fun puttingAwayAlreadyHereOrNewToTheCollectionAndUndoTakesTheNewOneOut() {
        val here = listOf(pile(listOf(entry("bolt", 1, 0, listOf(at("red", 1)))), places))
        assertEquals(PutAwayResult.HERE, putAway(here, "bolt", "bolt", Spot("red"), newEntry("bolt")).result)
        val out = putAway(listOf(pile(emptyList(), places)), "elf", "Llanowar Elves", Spot("red", section = "Green"), newEntry("elf", "Llanowar Elves"))
        assertEquals(PutAwayResult.NEW, out.result)
        assertEquals("new to collection", out.label)
        assertEquals(listOf(newEntry("elf", "Llanowar Elves").copy(quantity = 1, places = listOf(at("red", 1, section = "Green")))), out.collections[0].entries)
        assertEquals(emptyList<CollectionEntry>(), undoPutAway(out.collections, out.step!!)[0].entries)
    }

    @Test
    fun puttingAwayNeverTakesACopyFromADeckACardOnlyInADeckIsANewCopy() {
        val out = putAway(listOf(pile(emptyList(), places)), "g", "Goblin", Spot("red"), newEntry("g", "Goblin"))
        assertEquals(PutAwayResult.NEW, out.result)
    }

    @Test
    fun deletingAPlaceItsCopiesHaveNoPlaceThePlacesInsideMoveUp() {
        val collections = listOf(pile(listOf(entry("bolt", 2, 0, listOf(at("red", 1), at("shelf", 1)))), places))
        val out = deletePlace(collections, "shelf")
        assertEquals(listOf("red" to null, "trade" to null), placesOf(out).map { it.id to it.parentId })
        assertEquals(listOf(at("red", 1)), out[0].entries[0].places)
    }

    @Test
    fun mergingPlacesLineByLineAdditionsKeptRemovalsStayCountsAddUp() {
        val base = listOf(at("red", 1), at("trade", 1))
        val mine = listOf(at("red", 2), at("trade", 1), at("shelf", 1))
        val theirs = listOf(at("red", 2), at("box", 1))
        assertEquals(listOf(at("red", 3), at("box", 1), at("shelf", 1)), mergeCopyPlaces(base, mine, theirs))
        assertNull(mergeCopyPlaces(null, null, null))
        assertEquals("red|foil||2|3", copyKey(at("red", 1, foil = true, page = 2, slot = 3)))
    }

    @Test
    fun mergingThePlacesThemselvesFieldsGoToWhoeverChangedThemDeletionsStay() {
        val base = listOf(red, trade)
        val mine = listOf(red.copy(name = "Big red box"), trade, shelf)
        val theirs = listOf(red.copy(sortRule = SortRule.NAME.name))
        assertEquals(listOf(red.copy(name = "Big red box", sortRule = SortRule.NAME.name), shelf), mergePlaceLists(base, mine, theirs, true))
    }

    @Test
    fun aBinderMergeKeepsEachSidesPlacesAndNoMoreThanTheCopies() {
        val base = pile(listOf(entry("bolt", 2, 0, listOf(at("red", 1)))), listOf(red))
        val mine = pile(listOf(entry("bolt", 2, 0, listOf(at("red", 2)))), listOf(red, trade))
        val theirs = pile(listOf(entry("bolt", 1, 0, listOf(at("red", 1)))), listOf(red))
        val merged = ItemMerge.mergeCollections(base, mine, theirs, true)
        assertEquals(entry("bolt", 1, 0, listOf(at("red", 1))), merged.entries[0])
        assertEquals(listOf("red", "trade"), merged.storagePlaces?.map { it.id })
    }

    @Test
    fun anOlderAppsSaveWithoutThePlacesLeavesThemAsTheyWere() {
        val withPlaces = pile(listOf(entry("bolt", 2, 0, listOf(at("red", 2))), entry("elf", 1)), listOf(red))
        // The older app dropped the keys and added a card.
        val older = pile(listOf(entry("bolt", 2), entry("elf", 1), entry("ring", 1)))
        assertTrue(writtenWithoutPlaces(older))
        assertFalse(writtenWithoutPlaces(withPlaces))
        val kept = keepPlacesFromOlderApp(withPlaces, older)
        assertEquals(listOf(red), kept.storagePlaces)
        assertEquals(listOf(listOf(at("red", 2)), null, null), kept.entries.map { it.places })
        // A merge treats the older side as not having changed them.
        val merged = ItemMerge.mergeCollections(withPlaces, withPlaces, older, false)
        assertEquals(listOf(listOf(at("red", 2)), null, null), merged.entries.map { it.places })
        assertEquals(listOf(red), merged.storagePlaces)
        // A save that knows about places and cleared them is believed.
        val cleared = pile(listOf(entry("bolt", 2, 0, emptyList())), emptyList())
        assertSame(cleared, keepPlacesFromOlderApp(withPlaces, cleared))
    }

    @Test
    fun placesAreWrittenAsTheWebAppWritesThemAndReadBack() {
        // Written as the web app writes it: optional keys left out, and read back with them.
        val adapter = localMoshi.adapter(Collection::class.java)
        val json = adapter.toJson(pile(listOf(entry("bolt", 2, 0, listOf(at("red", 2, foil = false, section = "Red")))), listOf(red)))
        assertTrue(json.contains("\"placeId\":\"red\""))
        // Left out when not said: never "foil":false, never a null.
        assertFalse(json.contains("\"foil\""))
        assertFalse(json.contains("null"))
        val back = adapter.fromJson(json)!!
        assertEquals(listOf(red), back.storagePlaces)
        assertEquals(listOf(at("red", 2, section = "Red")), back.entries[0].places)
    }

    @Test
    fun thePlaceFilterPlacesHoldingCopiesWithThePlacesTheySitInAndCopiesWithNone() {
        assertEquals(listOf("shelf", "red") to 2, placeFactsOf(entry("bolt", 3, 0, listOf(at("red", 1), at("gone", 1))), places))
        assertEquals(emptyList<String>() to 0, placeFactsOf(entry("lent", 2, userTags = listOf("Lent to Sam")), places))
        assertEquals(listOf(at("red", 1)), placedCopies(entry("x", 1, 0, listOf(at("red", 5)))))
    }

    @Test
    fun whereAPrintingIsKeptShortForBesideInTwoDecksAndOneBinder() {
        val collections = listOf(
            pile(listOf(entry("bolt", 3, 0, listOf(at("red", 2), at("trade", 1, page = 1, slot = 1)))), places),
            binder("b1", listOf(entry("bolt", 1, 0, listOf(at("red", 1))), entry("ring", 1)))
        )
        assertEquals("Red box ×3 · Trade binder ×1", keptInLabel(collections, "bolt"))
        assertEquals("", keptInLabel(collections, "ring"))
        assertEquals("Red box ×2", keptLabel(entry("bolt", 3, 0, listOf(at("red", 2), at("gone", 1))), places))
    }

    @Test
    fun givingACardAPlaceThatPrintingFirstTheUnsortedPileFirstPlainBeforeFoilNeverALentCopy() {
        val collections = listOf(
            binder("b1", listOf(entry("bolt-a", 1, name = "Lightning Bolt"))),
            pile(listOf(
                entry("bolt-b", 1, 1, name = "Lightning Bolt"),
                entry("bolt-a", 1, 0, name = "Lightning Bolt", userTags = listOf("lent to Sam"))
            ), places)
        )
        val (out, moved) = placeUnplaced(collections, "Lightning Bolt", "bolt-a", Spot("red"), 3)
        assertEquals(3, moved)
        assertEquals(listOf(at("red", 1)), out[0].entries[0].places)
        assertEquals(listOf(at("red", 1), at("red", 1, foil = true)), out[1].entries[0].places)
        assertNull(out[1].entries[1].places)
        assertEquals(0, placeUnplaced(out, "Lightning Bolt", null, Spot("red"), 1).second)
    }
}
