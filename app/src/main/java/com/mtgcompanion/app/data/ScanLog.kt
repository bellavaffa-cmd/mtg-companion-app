package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.scryfall.ScryfallCard

/**
 * The scanning list: one row per scan, newest first, in the order the cards went past the camera.
 * A card read twice shows twice — that's the point, since a double scan and a card read wrongly
 * both have to be findable — and the rows say which copy they are. Copies are added together only
 * when the pile is put into a binder or deck. Mirrors the web app's src/scan/scanLog.ts.
 */

/** [id] counts up per scan, so rows stay apart even when they're the same card. */
data class ScanRow(val id: Long, val card: ScryfallCard, val at: Long)

/** A repeat within this long of the card's last scan reads as the camera catching it twice. */
const val DOUBLE_MS = 8_000L

/** Which copy of its card this row is, counting from the first scan. 1 the first time. */
fun copyNumber(rows: List<ScanRow>, row: ScanRow): Int =
    rows.count { it.card.id == row.card.id && it.id <= row.id }

/** Whether the same card was scanned again within [withinMs] — the camera catching one card twice. */
fun scannedTwiceOver(rows: List<ScanRow>, row: ScanRow, withinMs: Long = DOUBLE_MS): Boolean {
    // Rows are newest first, so the first match is the closest earlier scan.
    val last = rows.firstOrNull { it.card.id == row.card.id && it.id < row.id } ?: return false
    return row.at - last.at <= withinMs
}

/** The cards scanned more than once, by card id. */
fun repeatedCards(rows: List<ScanRow>): Set<String> {
    val seen = mutableSetOf<String>()
    val twice = mutableSetOf<String>()
    for (row in rows) {
        if (!seen.add(row.card.id)) twice += row.card.id
    }
    return twice
}

/** Just the rows of cards scanned more than once — "show me what I may have double-scanned". */
fun onlyRepeats(rows: List<ScanRow>): List<ScanRow> {
    val twice = repeatedCards(rows)
    return rows.filter { it.card.id in twice }
}

/** A card and how many of it the pile holds. */
data class ScanGroup(val card: ScryfallCard, val quantity: Int)

/** What goes into a binder or deck: the rows added together, oldest scan first. */
fun grouped(rows: List<ScanRow>): List<ScanGroup> {
    val out = LinkedHashMap<String, ScanGroup>()
    for (row in rows.asReversed()) {
        val had = out[row.card.id]
        out[row.card.id] = if (had == null) ScanGroup(row.card, 1) else had.copy(quantity = had.quantity + 1)
    }
    return out.values.toList()
}
