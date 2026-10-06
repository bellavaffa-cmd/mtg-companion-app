package com.mtgcompanion.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Scanning a whole binder page: the pocket grid, what each pocket holds, the summary, the check
 * against the record and recording the page. The web app has the same checks — see
 * MtgCompanionWeb/tests/collection/pageScan.test.ts.
 */
class PageScanTest {

    private var row = 0
    private fun m(name: String, score: Float, group: Int = row, id: String? = null, set: String = "set") =
        IndexMatch(IndexEntry(row, id ?: "id${row++}", 0, name, set, "1", group), score).also { if (id != null) row++ }

    private val binder = StoragePlace("b", "Trade binder", PlaceKind.BINDER.name, createdAt = 1)

    private fun at(qty: Int, page: Int? = null, slot: Int? = null, placeId: String = "b") = CopyPlace(placeId, qty, null, null, page, slot)

    private fun entry(id: String, name: String, quantity: Int, places: List<CopyPlace>? = null) =
        CollectionEntry(id, name, null, quantity = quantity, places = places)

    private fun pile(vararg entries: CollectionEntry) =
        listOf(Collection(UNSORTED_COLLECTION_ID, UNSORTED_COLLECTION_NAME, entries.toList(), createdAt = 0, type = CollectionType.OWNED.name, storagePlaces = listOf(binder)))

    private fun newEntry(c: CellCard) = CollectionEntry(c.scryfallId, c.name, null)

    private fun read(slot: Int, id: String, name: String) = PageCell(slot, CellState.READ, CellCard(id, name))
    private fun empty(slot: Int) = PageCell(slot, CellState.EMPTY)

    @Test
    fun aPageIsCutIntoItsPocketsRowByRow() {
        val boxes = pageCellBoxes(ScanBox(0, 0, 300, 420), 9)
        assertEquals(9, boxes.size)
        assertEquals(ScanBox(0, 0, 100, 140), boxes[0])
        assertEquals(ScanBox(100, 0, 200, 140), boxes[1])
        assertEquals(ScanBox(0, 140, 100, 280), boxes[3])
        assertEquals(ScanBox(200, 280, 300, 420), boxes[8])
        val twelve = pageCellBoxes(ScanBox(10, 20, 310, 580), 12)
        assertEquals(ScanBox(110, 160, 210, 300), twelve[4])
        assertEquals(ScanBox(210, 440, 310, 580), twelve[11])
        assertEquals(1f, pageAspect(9) * 88f / 63f, 0.001f)
        assertEquals(4, pageCellBoxes(ScanBox(0, 0, 200, 280), 4).size)
    }

    @Test
    fun aPocketIsReadAskedAboutOrEmpty() {
        // Nothing found, or nothing alike: empty.
        assertEquals(CellState.EMPTY, readCell(1, found = false, anywhere = listOf(m("Opt", 0.9f))).state)
        assertEquals(CellState.EMPTY, readCell(1, found = true, anywhere = listOf(m("Opt", 0.3f))).state)
        // Clearly one card, one picture: read.
        val opt = readCell(2, true, listOf(m("Opt", 0.86f), m("Ponder", 0.4f)))
        assertEquals(CellState.READ, opt.state)
        assertEquals("Opt", opt.card?.name)
        // Clearly Counterspell, but two of its pictures too close to call: which one?
        val counter = readCell(5, true, listOf(m("Counterspell", 0.85f), m("Counterspell", 0.84f), m("Ponder", 0.5f)))
        assertEquals(CellState.CHOOSE, counter.state)
        assertEquals(2, counter.options.size)
        assertEquals("Counterspell (2 printings)", couldBe(counter))
        // Something's there, but not clearly one card: the likeliest names.
        val unsure = readCell(7, true, listOf(m("Opt", 0.6f), m("Ponder", 0.58f), m("Opt", 0.55f)))
        assertEquals(CellState.CHOOSE, unsure.state)
        assertEquals(listOf("Opt", "Ponder"), unsure.options.map { it.name })
        assertEquals("Opt or Ponder", couldBe(unsure))
        // A title read decides among its own printings.
        val named = readCell(3, true, listOf(m("Island", 0.5f)), named = listOf(m("Brainstorm", 0.7f), m("Brainstorm", 0.6f)))
        assertEquals(CellState.READ, named.state)
        assertEquals("Brainstorm", named.card?.name)
        // Settled by hand.
        assertEquals(CellState.READ, chooseCell(counter, counter.options[1]).state)
        assertEquals(CellState.EMPTY, chooseCell(counter, null).state)
    }

    @Test
    fun theSummarySaysWhatWasReadAndWhatNeedsALook() {
        val cells = (1..6).map { read(it, "x$it", "Card $it") } +
            PageCell(7, CellState.CHOOSE, options = listOf(CellCard("c1", "Counterspell"), CellCard("c2", "Counterspell"))) + empty(8) + empty(9)
        assertEquals("6 read · 1 to check · 2 empty", pageScanSummary(cells))
        assertEquals("Slot 7 could be Counterspell (2 printings). Tap it to choose.", pageScanHint(cells))
        assertEquals("2 read", pageScanSummary(cells.take(2)))
        assertNull(pageScanHint(cells.take(2)))
        val two = listOf(PageCell(2, CellState.CHOOSE), PageCell(5, CellState.CHOOSE), PageCell(8, CellState.CHOOSE))
        assertEquals("Slots 2, 5 and 8 need a look. Tap each to choose.", pageScanHint(two))
    }

    @Test
    fun checkingAPageAgainstTheRecord() {
        val recorded = mapOf(1 to listOf("Brainstorm"), 2 to listOf("Ponder"), 3 to listOf("Opt"), 5 to listOf("Counterspell"))
        val cells = listOf(
            read(1, "a", "Brainstorm"), read(2, "b", "Preordain"), empty(3), read(4, "d", "Impulse"),
            PageCell(5, CellState.CHOOSE, options = listOf(CellCard("c", "Counterspell"))), empty(6)
        )
        val diff = pageDiff(recorded, cells)
        assertEquals(
            listOf(PageDiffKind.SAME, PageDiffKind.DIFFERENT, PageDiffKind.MISSING, PageDiffKind.EXTRA, PageDiffKind.UNSURE, PageDiffKind.SAME),
            diff.map { it.kind }
        )
        assertEquals("2 as recorded · 1 missing · 1 not in the record · 1 different · 1 to check", pageDiffSummary(diff))
        assertEquals("Slot 2: Preordain — the record says Ponder", pageDiffText(diff[1]))
        assertEquals("Slot 3: empty — the record says Opt", pageDiffText(diff[2]))
        assertEquals("Slot 4: Impulse — not in the record", pageDiffText(diff[3]))
        assertNull(pageDiffText(diff[0]))
        assertEquals("All 2 pockets as recorded", pageDiffSummary(pageDiff(recorded, listOf(read(1, "a", "brainstorm"), empty(9)))))
    }

    @Test
    fun theRecordComesFromThePagesPockets() {
        val cols = pile(
            entry("a", "Brainstorm", 1, listOf(at(1, 4, 1))),
            entry("p", "Ponder", 1, listOf(at(1, 4, 2))),
            entry("o", "Opt", 1, listOf(at(1, 5, 1)))
        )
        assertEquals(mapOf(1 to listOf("Brainstorm"), 2 to listOf("Ponder")), recordedOnPage(binder, cols, 4))
    }

    @Test
    fun recordingAPageWritesItsPockets() {
        val cols = pile(
            // As recorded.
            entry("a", "Brainstorm", 1, listOf(at(1, 4, 1))),
            // Recorded in slot 2, but the photo shows another card there: out of its pocket.
            entry("p", "Ponder", 1, listOf(at(1, 4, 2))),
            // Waiting in the binder for a pocket.
            entry("o", "Opt", 1, listOf(at(1))),
            // Owned, with no place.
            entry("i", "Impulse", 1),
            // In a pocket on another page.
            entry("f", "Fact or Fiction", 1, listOf(at(1, 7, 3)))
        )
        val cells = listOf(
            read(1, "a", "Brainstorm"), read(2, "o", "Opt"), read(3, "i", "Impulse"), read(4, "f", "Fact or Fiction"),
            read(5, "n", "Preordain"), PageCell(6, CellState.CHOOSE), empty(7)
        )
        val r = recordPage(cols, binder, 4, cells, ::newEntry)
        val here = cardsIn(r.collections, "b")
        fun slotOf(name: String) = here.filter { it.entry.name == name }.map { it.line.page to it.line.slot }
        assertEquals(listOf(4 to 1), slotOf("Brainstorm"))
        assertEquals(listOf(null to null), slotOf("Ponder"))
        assertEquals(listOf(4 to 2), slotOf("Opt"))
        assertEquals(listOf(4 to 3), slotOf("Impulse"))
        assertEquals(listOf(4 to 4), slotOf("Fact or Fiction"))
        assertEquals(listOf(4 to 5), slotOf("Preordain"))
        assertEquals(3, r.placed)
        assertEquals(1, r.added)
        assertEquals(1, r.cleared)
        assertEquals("Recorded page 4: 3 cards in their pockets, 1 new to your collection, 1 taken out of its pocket", recordedLine(4, r))
        // Recording it again changes nothing.
        val again = recordPage(r.collections, binder, 4, cells, ::newEntry)
        assertEquals(0, again.placed + again.added + again.cleared)
        assertEquals("Page 4 was already as recorded", recordedLine(4, again))
    }
}
