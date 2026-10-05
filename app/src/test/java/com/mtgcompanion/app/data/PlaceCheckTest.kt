package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.Calendar

/**
 * Checking a place by scanning everything in it, the same on both apps. The web app has the same
 * checks — see MtgCompanionWeb/tests/collection/placeCheck.test.ts.
 */
class PlaceCheckTest {

    private fun at(placeId: String, qty: Int, foil: Boolean = false, section: String? = null, page: Int? = null, slot: Int? = null) =
        CopyPlace(placeId, qty, if (foil) true else null, section, page, slot)

    private fun entry(id: String, name: String, quantity: Int, foilQuantity: Int = 0, places: List<CopyPlace>? = null) =
        CollectionEntry(id, name, null, quantity = quantity, foilQuantity = foilQuantity, places = places)

    private val red = StoragePlace("red", "Red box", PlaceKind.BOX.name, sections = listOf("Blue", "Red"), createdAt = 1)
    private val trade = StoragePlace("trade", "Trade binder", PlaceKind.BINDER.name, createdAt = 2)

    private fun pile(entries: List<CollectionEntry>) =
        Collection(UNSORTED_COLLECTION_ID, UNSORTED_COLLECTION_NAME, entries, createdAt = 0, type = CollectionType.OWNED.name, storagePlaces = listOf(red, trade))

    private val krenko = Deck("krenko", "Krenko", cards = listOf(DeckCardEntry("matron", "Goblin Matron", null, quantity = 1)), ownership = DeckOwnership.PHYSICAL.name, createdAt = 1)

    private val cols = listOf(pile(listOf(
        entry("bolt", "Lightning Bolt", 2, 0, listOf(at("red", 2, section = "Red"))),
        entry("shock", "Shock", 1, 1, listOf(at("red", 1, section = "Red"), at("red", 1, foil = true, section = "Red"))),
        entry("counter", "Counterspell", 1, 0, listOf(at("red", 1, section = "Blue"))),
        entry("opt", "Opt", 1),
        entry("abrade", "Abrade", 1, 0, listOf(at("trade", 1, page = 1, slot = 1)))
    )))

    private fun scan(id: String, name: String, foil: Boolean? = null, exact: Boolean = true) = CheckScan(id, name, null, foil, exact)

    private val scope = CheckScope("red", "Red")
    private val scans = listOf(
        scan("bolt", "Lightning Bolt"),
        // Not sure of the printing: any printing of the card listed here counts.
        scan("bolt-m10", "Lightning Bolt", exact = false),
        scan("shock", "Shock"),
        scan("counter", "Counterspell"),
        scan("matron", "Goblin Matron"),
        scan("opt", "Opt"),
        scan("abrade", "Abrade"),
        scan("x", "Mystery Card"),
        scan("bolt", "Lightning Bolt"),
        scan("bolt-m10", "Lightning Bolt")
    )

    @Test
    fun whatsListedInASectionOrInTheWholePlace() {
        assertEquals(listOf("Lightning Bolt ×2", "Shock ×1", "Shock foil ×1"), listedIn(cols, scope).map { "${it.name}${if (it.line.isFoil) " foil" else ""} ×${it.qty}" })
        assertEquals(4, listedIn(cols, CheckScope("red", null)).size)
        assertEquals("Page 1, slot 1", listedWhere(listedIn(cols, CheckScope("trade", null))[0]))
    }

    @Test
    fun eachScanIsWhereItShouldBeOrSaysWhereItIsListed() {
        val r = reconcile(cols, listOf(krenko), scope, scans)
        assertEquals(
            listOf(
                // The scans sure of their printing count first: the third Bolt scanned is the one that's over.
                "belongs here", "one more than listed here", "belongs here", "should be in Blue", "listed in Krenko deck", "no place yet",
                "listed in Trade binder", "not in your collection", "belongs here", "another printing is listed here"
            ),
            r.lines.map { it.label }
        )
        assertEquals(4, r.expected)
        assertEquals(3, r.here)
        // The scanner couldn't tell foil, so the Shock scanned counted as the plain one; the foil wasn't scanned.
        assertEquals(listOf(Triple("Shock", true, 1)), r.missing.map { Triple(it.name, it.line.isFoil, it.qty) })
        assertEquals(1, r.missingCount)
        assertEquals(7, r.extra.size)
        assertEquals(true, r.foilIgnored)
        assertEquals("krenko", r.extra.firstOrNull { it.kind == CheckKind.DECK }?.deckId)
    }

    @Test
    fun aScanThatSaysFoilCountsAgainstTheFoilCopies() {
        val r = reconcile(cols, emptyList(), scope, listOf(scan("shock", "Shock", foil = true)))
        assertEquals(listOf("Lightning Bolt" to false, "Shock" to false), r.missing.map { it.name to it.line.isFoil })
        assertEquals(false, r.foilIgnored)
    }

    @Test
    fun missingCopiesNoPlaceYetOrOutOfTheCollection() {
        val missing = reconcile(cols, emptyList(), scope, scans).missing
        val unplaced = markNoPlace(cols, missing)
        assertEquals(listOf(at("red", 1, section = "Red")), unplaced[0].entries[1].places)
        assertEquals(1, unplaced[0].entries[1].foilQuantity)
        val removed = removeMissing(cols, missing)
        assertEquals(1 to 0, removed[0].entries[1].quantity to removed[0].entries[1].foilQuantity)
        assertEquals(listOf(at("red", 1, section = "Red")), removed[0].entries[1].places)
        // A card with no copies left goes.
        val one = listOf(pile(listOf(entry("bolt", "Lightning Bolt", 1, 0, listOf(at("red", 1, section = "Red"))))))
        assertEquals(0, removeMissing(one, reconcile(one, emptyList(), scope, emptyList()).missing)[0].entries.size)
    }

    @Test
    fun recordingTheExtraCardsHereNeverOutOfADeckWithoutAsking() {
        val r = reconcile(cols, listOf(krenko), scope, scans)
        val kept = recordHere(cols, listOf(krenko), scope, r.extra, takeFromDecks = false)
        assertEquals(6, kept.recorded)
        assertEquals(1, kept.leftInDecks)
        assertEquals(1, kept.decks[0].cards.size)
        val again = reconcile(kept.collections, kept.decks, scope, scans)
        assertEquals(listOf("listed in Krenko deck"), again.extra.map { it.label })
        // Counterspell moved section, Abrade moved from the binder, Opt was given its place, and the card
        // not in the collection and the Bolts over were added here.
        val byId = kept.collections[0].entries.associateBy { it.scryfallId }
        assertEquals(listOf(at("red", 1, section = "Red")), byId.getValue("counter").places)
        assertEquals(listOf(at("red", 1, section = "Red")), byId.getValue("abrade").places)
        assertEquals(listOf(at("red", 1, section = "Red")), byId.getValue("opt").places)
        assertEquals(1 to "Mystery Card", byId.getValue("x").quantity to byId.getValue("x").name)
        assertEquals(2 to listOf(at("red", 2, section = "Red")), byId.getValue("bolt").quantity to byId.getValue("bolt").places)
        assertEquals(2 to listOf(at("red", 2, section = "Red")), byId.getValue("bolt-m10").quantity to byId.getValue("bolt-m10").places)

        val taken = recordHere(cols, listOf(krenko), scope, r.extra, takeFromDecks = true)
        assertEquals(7, taken.recorded)
        assertEquals(0, taken.leftInDecks)
        assertEquals(0, taken.decks[0].cards.size)
        val all = reconcile(taken.collections, taken.decks, scope, scans)
        assertEquals(0, all.extra.size)
        assertEquals(10, all.here)
        assertEquals(listOf("Shock"), all.missing.map { it.name })
    }

    @Test
    fun savingTheResultsNotesWhenThePlaceWasCheckedAndThatOnlyMovesOn() {
        val checked = markChecked(cols, "red", 1_000)
        assertEquals(1_000L, placesOf(checked).first { it.id == "red" }.lastChecked)
        assertEquals(1_000L, placesOf(markChecked(checked, "red", 500)).first { it.id == "red" }.lastChecked)
        // Merging: the later check; a side that dropped the key (an older app) doesn't clear it.
        val b = red.copy(lastChecked = 100)
        assertEquals(300L, mergePlaceLists(listOf(b), listOf(red.copy(lastChecked = 300)), listOf(red.copy(lastChecked = 200)), false)!![0].lastChecked)
        assertEquals(100L, mergePlaceLists(listOf(b), listOf(b), listOf(red.copy(name = "Red box, top")), false)!![0].lastChecked)
        assertNull(mergePlaceLists(listOf(red), listOf(red), listOf(red), false)!![0].lastChecked)
        // A pile saved by an app that doesn't know about checks gets them back; one that does is left alone.
        val older = pile(emptyList())
        val healed = keepLastChecked(checked[0], older)
        assertEquals(1_000L, healed.storagePlaces!!.first { it.id == "red" }.lastChecked)
        assertSame(checked[0], keepLastChecked(older, checked[0]))
    }

    @Test
    fun lastCheckedInWords() {
        fun day(d: Int, h: Int = 12) = Calendar.getInstance().apply { clear(); set(2026, Calendar.OCTOBER, d, h, 0) }.timeInMillis
        assertEquals("today", lastCheckedLabel(day(5, 9), day(5, 20)))
        assertEquals("yesterday", lastCheckedLabel(day(4, 23), day(5, 1)))
        assertEquals("3 days ago", lastCheckedLabel(day(2), day(5)))
        assertEquals("5 Oct 2026", lastCheckedLabel(day(5), day(15)))
    }
}
