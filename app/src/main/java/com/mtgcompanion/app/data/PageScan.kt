package com.mtgcompanion.app.data

/*
 * Scanning a whole binder page: one photo of the page, cut into the binder's pockets (3 × 3 for 9 a
 * page, 3 × 4 for 12…), each pocket looked at by the same card recogniser a single scan uses
 * (CardRecognizer / CardIndex). Each pocket comes out as
 *  - READ: a card, its printing known;
 *  - CHOOSE ("Which one?"): a card whose printing (or name) the picture can't settle — tap it to choose;
 *  - EMPTY: no card in the pocket.
 * Then "Record this page" writes what was read into those pockets — copies listed in a pocket the
 * photo shows something else in leave the pocket (they stay in the binder, waiting for a pocket), and
 * each card read goes in its pocket: a copy waiting in the binder first, then one with no place or in
 * another place (as putting a card away does), and a card not owned at all is added. "Check against
 * record" lists, pocket by pocket, where the photo and the binder's record disagree.
 *
 * Pure, so it can be tested. Mirrors the web app's src/collection/pageScan.ts rule for rule, with the
 * same tests (PageScanTest.kt ↔ tests/collection/pageScan.test.ts).
 */

enum class CellState { READ, CHOOSE, EMPTY }

/** A card a pocket may hold: one printing. */
data class CellCard(val scryfallId: String, val name: String, val set: String = "", val number: String = "")

/** One pocket of the page as the photo showed it; [slot] counts from 1. [options]: CHOOSE's candidates, best first. */
data class PageCell(
    val slot: Int,
    val state: CellState,
    val card: CellCard? = null,
    val options: List<CellCard> = emptyList()
)

/** Below this likeness to anything in the index a pocket is taken to be empty (a bare pocket, a sleeve's glare). */
const val EMPTY_BELOW = 0.5f

/** How many candidates a CHOOSE pocket offers. */
const val CELL_OPTIONS = 4

private fun cellCard(e: IndexEntry) = CellCard(e.id, e.name, e.set, e.number)

/**
 * The pockets of a page photographed inside [area]: the page's grid (pageGrid) laid evenly over it,
 * row by row — slot 1 top left. Each box is where one pocket's card is expected.
 */
fun pageCellBoxes(area: ScanBox, pockets: Int): List<ScanBox> {
    val (cols, rows) = pageGrid(pockets)
    val w = (area.right - area.left).toFloat() / cols
    val h = (area.bottom - area.top).toFloat() / rows
    return (0 until pockets).map { i ->
        val col = i % cols
        val row = i / cols
        ScanBox(
            (area.left + col * w).toInt(),
            (area.top + row * h).toInt(),
            (area.left + (col + 1) * w).toInt(),
            (area.top + (row + 1) * h).toInt()
        )
    }
}

/** A page's shape, wide over tall, for the framing guide: its pockets side by side, card-shaped. */
fun pageAspect(pockets: Int): Float {
    val (cols, rows) = pageGrid(pockets)
    return cols * 63f / (rows * 88f)
}

/**
 * What one pocket holds, from what the recogniser made of it: [found] whether a card's outline was
 * found there at all; [anywhere] the nearest pictures over the whole index; [named] the nearest
 * among the printings of a title read off the card, if one was.
 *  - Nothing found, or nothing alike: EMPTY.
 *  - A title read: its printings decide — the one by sight, or CHOOSE among them when two pictures are
 *    too close to call.
 *  - Else known by sight (cardBySight): READ, unless its own printings are too close to call (CHOOSE).
 *  - Else something's there but not clearly one card: CHOOSE among the likeliest names.
 */
fun readCell(slot: Int, found: Boolean, anywhere: List<IndexMatch>, named: List<IndexMatch> = emptyList()): PageCell {
    val best = anywhere.firstOrNull()
    if (!found || ((best == null || best.score < EMPTY_BELOW) && named.isEmpty())) return PageCell(slot, CellState.EMPTY)
    if (named.isNotEmpty()) return byPrinting(slot, named)
    val sight = cardBySight(anywhere)
    if (sight != null) return byPrinting(slot, anywhere.filter { sameCard(it.entry.name, sight.name) })
    val names = anywhere.distinctBy { it.entry.name.lowercase() }.take(CELL_OPTIONS)
    return PageCell(slot, CellState.CHOOSE, options = names.map { cellCard(it.entry) })
}

/** A pocket whose card is known: its printing by sight, or CHOOSE among the printings too close to call. */
private fun byPrinting(slot: Int, matches: List<IndexMatch>): PageCell {
    val pick = printingBySight(matches)
    if (pick != null) return PageCell(slot, CellState.READ, cellCard(pick.entry))
    val printings = matches.distinctBy { it.entry.id }.take(CELL_OPTIONS).map { cellCard(it.entry) }
    return PageCell(slot, CellState.CHOOSE, options = printings)
}

/** A pocket settled by hand: [card] chosen (or typed), or null for empty. */
fun chooseCell(cell: PageCell, card: CellCard?): PageCell =
    if (card == null) PageCell(cell.slot, CellState.EMPTY) else PageCell(cell.slot, CellState.READ, card)

/** "6 read · 1 to check · 2 empty" — the parts with none left out, except what was read. */
fun pageScanSummary(cells: List<PageCell>): String {
    val read = cells.count { it.state == CellState.READ }
    val check = cells.count { it.state == CellState.CHOOSE }
    val empty = cells.count { it.state == CellState.EMPTY }
    return listOfNotNull("$read read", if (check > 0) "$check to check" else null, if (empty > 0) "$empty empty" else null).joinToString(" · ")
}

/** What a CHOOSE pocket could be: "Counterspell (2 printings)", "Opt or Ponder". */
fun couldBe(cell: PageCell): String {
    val names = cell.options.map { it.name }.distinctBy { it.lowercase() }
    return when {
        names.isEmpty() -> "anything"
        names.size == 1 -> "${names[0]} (${cell.options.size} ${if (cell.options.size == 1) "printing" else "printings"})"
        else -> names.dropLast(1).joinToString(", ") + " or " + names.last()
    }
}

/** The line under the summary: "Slot 5 could be Counterspell (2 printings). Tap it to choose." — or null when nothing needs a look. */
fun pageScanHint(cells: List<PageCell>): String? {
    val unsure = cells.filter { it.state == CellState.CHOOSE }
    return when {
        unsure.isEmpty() -> null
        unsure.size == 1 -> "Slot ${unsure[0].slot} could be ${couldBe(unsure[0])}. Tap it to choose."
        else -> "Slots ${unsure.dropLast(1).joinToString(", ") { "${it.slot}" }} and ${unsure.last().slot} need a look. Tap each to choose."
    }
}

// ---- Check against record ----

/** SAME: as recorded. MISSING: recorded, the pocket's empty. EXTRA: a card the record doesn't have there. DIFFERENT: another card. UNSURE: not settled yet. */
enum class PageDiffKind { SAME, MISSING, EXTRA, DIFFERENT, UNSURE }

/** One pocket compared: what the record says is in it ([recorded], names) and what the photo showed ([found]). */
data class PageDiffLine(val slot: Int, val kind: PageDiffKind, val recorded: List<String>, val found: String?)

/** The names recorded in each pocket of [page] of the binder [place], slot by slot. */
fun recordedOnPage(place: StoragePlace, collections: List<Collection>, page: Int): Map<Int, List<String>> =
    cardsIn(collections, place.id)
        .filter { it.line.page == page && (it.line.slot ?: 0) in 1..place.pockets }
        .groupBy({ it.line.slot!! }, { it.entry.name })

/** The page as photographed against the binder's record, pocket by pocket. */
fun pageDiff(recorded: Map<Int, List<String>>, cells: List<PageCell>): List<PageDiffLine> = cells.map { cell ->
    val listed = recorded[cell.slot].orEmpty()
    when (cell.state) {
        CellState.CHOOSE -> PageDiffLine(cell.slot, PageDiffKind.UNSURE, listed, couldBe(cell))
        CellState.EMPTY -> PageDiffLine(cell.slot, if (listed.isEmpty()) PageDiffKind.SAME else PageDiffKind.MISSING, listed, null)
        CellState.READ -> {
            val name = cell.card?.name.orEmpty()
            val kind = when {
                listed.isEmpty() -> PageDiffKind.EXTRA
                listed.any { sameCardName(it, name) } -> PageDiffKind.SAME
                else -> PageDiffKind.DIFFERENT
            }
            PageDiffLine(cell.slot, kind, listed, name)
        }
    }
}

/** "All 9 pockets as recorded", or "6 as recorded · 1 missing · 1 not in the record · 1 different". */
fun pageDiffSummary(lines: List<PageDiffLine>): String {
    val same = lines.count { it.kind == PageDiffKind.SAME }
    if (same == lines.size) return "All ${lines.size} pockets as recorded"
    fun n(kind: PageDiffKind) = lines.count { it.kind == kind }
    return listOfNotNull(
        "$same as recorded",
        n(PageDiffKind.MISSING).takeIf { it > 0 }?.let { "$it missing" },
        n(PageDiffKind.EXTRA).takeIf { it > 0 }?.let { "$it not in the record" },
        n(PageDiffKind.DIFFERENT).takeIf { it > 0 }?.let { "$it different" },
        n(PageDiffKind.UNSURE).takeIf { it > 0 }?.let { "$it to check" }
    ).joinToString(" · ")
}

/** One pocket that disagrees, in words: "Slot 2: Opt — the record says Ponder". Null for SAME. */
fun pageDiffText(line: PageDiffLine): String? {
    val said = if (line.recorded.isEmpty()) "nothing recorded" else "the record says ${line.recorded.joinToString(", ")}"
    return when (line.kind) {
        PageDiffKind.SAME -> null
        PageDiffKind.MISSING -> "Slot ${line.slot}: empty — $said"
        PageDiffKind.EXTRA -> "Slot ${line.slot}: ${line.found} — not in the record"
        PageDiffKind.DIFFERENT -> "Slot ${line.slot}: ${line.found} — $said"
        PageDiffKind.UNSURE -> "Slot ${line.slot}: could be ${line.found} — $said"
    }
}

// ---- Record this page ----

/** What recording a page did: [placed] copies given their pocket, [added] new to the collection, [cleared] taken out of a pocket. */
data class RecordedPage(val collections: List<Collection>, val placed: Int, val added: Int, val cleared: Int)

/** "Recorded page 4: 5 cards in their pockets, 1 new to your collection, 2 taken out of their pockets". */
fun recordedLine(page: Int, r: RecordedPage): String {
    val parts = listOfNotNull(
        r.placed.takeIf { it > 0 }?.let { "$it ${if (it == 1) "card" else "cards"} in ${if (it == 1) "its pocket" else "their pockets"}" },
        r.added.takeIf { it > 0 }?.let { "$it new to your collection" },
        r.cleared.takeIf { it > 0 }?.let { "$it taken out of ${if (it == 1) "its pocket" else "their pockets"}" }
    )
    return if (parts.isEmpty()) "Page $page was already as recorded" else "Recorded page $page: " + parts.joinToString(", ")
}

private fun moveLine(collections: List<Collection>, c: PlacedCard, to: Spot, qty: Int): List<Collection> = collections.map { col ->
    if (col.id != c.collectionId) col
    else col.copy(entries = col.entries.map { e -> if (e.scryfallId == c.entry.scryfallId) moveCopies(e, c.line, to, qty).first else e })
}

private fun inAPocket(line: CopyPlace, pockets: Int) = (line.page ?: 0) > 0 && (line.slot ?: 0) in 1..pockets

/**
 * [collections] with [page] of the binder [place] recorded as [cells] show it. CHOOSE pockets are left
 * as they are. [newEntry]: the entry for a card not in the collection, with no copies yet.
 */
fun recordPage(collections: List<Collection>, place: StoragePlace, page: Int, cells: List<PageCell>, newEntry: (CellCard) -> CollectionEntry): RecordedPage {
    var out = collections
    var placed = 0
    var added = 0
    var cleared = 0
    val waiting = Spot(place.id)
    // Copies the photo doesn't bear out leave their pocket; they stay in the binder, waiting for one.
    for (cell in cells) {
        if (cell.state == CellState.CHOOSE) continue
        val listed = cardsIn(out, place.id).filter { it.line.page == page && it.line.slot == cell.slot }
        for (c in listed) {
            val card = cell.card
            if (cell.state == CellState.READ && card != null && sameCardName(c.entry.name, card.name)) continue
            out = moveLine(out, c, waiting, c.line.qty)
            cleared += c.line.qty
        }
    }
    for (cell in cells) {
        val card = cell.card
        if (cell.state != CellState.READ || card == null) continue
        val here = cardsIn(out, place.id)
        if (here.any { it.line.page == page && it.line.slot == cell.slot && sameCardName(it.entry.name, card.name) }) continue
        val to = Spot(place.id, page = page, slot = cell.slot)
        fun firstOf(list: List<PlacedCard>) = list.firstOrNull { it.entry.scryfallId == card.scryfallId } ?: list.firstOrNull { sameCardName(it.entry.name, card.name) }
        // A copy waiting in this binder for a pocket.
        val loose = firstOf(here.filter { !inAPocket(it.line, place.pockets) })
        if (loose != null) {
            out = moveLine(out, loose, to, 1)
            placed++
            continue
        }
        val outcome = putAway(out, card.scryfallId, card.name, to, newEntry(card))
        when (outcome.result) {
            PutAwayResult.PLACED, PutAwayResult.MOVED -> { out = outcome.collections; placed++ }
            PutAwayResult.NEW -> { out = outcome.collections; added++ }
            PutAwayResult.HERE -> {
                // Listed in a pocket on another page: the photo says it's here now.
                val elsewhere = firstOf(here.filter { inAPocket(it.line, place.pockets) && it.line.page != page })
                if (elsewhere != null) {
                    out = moveLine(out, elsewhere, to, 1)
                    placed++
                } else {
                    out = addedHere(out, card.scryfallId, to, newEntry(card)).first
                    added++
                }
            }
        }
    }
    return RecordedPage(out, placed, added, cleared)
}
