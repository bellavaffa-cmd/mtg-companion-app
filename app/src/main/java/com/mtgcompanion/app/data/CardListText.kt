package com.mtgcompanion.app.data

// Card lists as text, for moving a collection between this app and others (Moxfield, Archidekt,
// ManaBox, Deckbox, TCGplayer…). Reads the usual pasted/exported shapes, one card per line:
//
//   4 Lightning Bolt
//   2x Counterspell
//   1 Sol Ring (CMR) 472
//   1 Sol Ring [CMR] 472 *F*        (foil; *E* etched, "(foil)"/"[foil]" work too)
//
// and the CSV collection exports those apps make (a header row naming Count/Quantity and Name, and
// optionally the set code, collector number, foil and Scryfall ID). Writes the plain text form,
// which all of them read back. Mirrors the web app's collection/cardListText.ts.

/** One line of a list: how many, which card (by id, printing or name), and whether foil. */
data class ListLine(
    val quantity: Int,
    val name: String?,
    val set: String? = null,
    val number: String? = null,
    val scryfallId: String? = null,
    val foil: Boolean = false
)

data class ParsedList(val lines: List<ListLine>, val skipped: List<String>) {
    val cardCount: Int get() = lines.sumOf { it.quantity }
}

private const val MAX_COPIES = 999

private val QTY = Regex("^(\\d+)\\s*[xX]?\\s+(.+)$")
/** "(SLD) 1962", "[MH3] 285", or just "(SLD)". */
private val PRINTING = Regex("[(\\[]([A-Za-z0-9]{2,6})[)\\]](?:\\s+([A-Za-z0-9\\-★]+))?")
private val FOIL = Regex("\\*(?:f|foil|e|etched)\\*|[(\\[](?:foil|etched)[)\\]]", RegexOption.IGNORE_CASE)
private val SECTION_WORDS = setOf("deck", "commander", "companion", "sideboard", "maybeboard", "tokens", "about", "name", "collection", "binder")
private val HEADER = Regex("^[A-Za-z][^\\d]*(\\(\\d+\\)|:\\s*\\d+)\\s*$")

private fun isHeader(line: String): Boolean =
    line.lowercase().removeSuffix(":").trim() in SECTION_WORDS || HEADER.matches(line)

/** A card line, null for lines to pass over, or [UNREADABLE]. */
private fun parseTextLine(raw: String): ListLine? {
    val line = raw.trim()
    if (line.isEmpty() || line.startsWith("#") || line.startsWith("//") || isHeader(line)) return null
    val qty = QTY.find(line)
    val quantity = qty?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, MAX_COPIES) ?: 1
    var rest = qty?.groupValues?.get(2) ?: line
    val foil = FOIL.containsMatchIn(rest)
    rest = FOIL.replace(rest, " ")
    val printing = PRINTING.find(rest)
    // The name is whatever comes before the printing; trailing tags ("#Trade") go too.
    val name = rest.replace(Regex("\\s*[(\\[][A-Za-z0-9]{2,6}[)\\]].*$"), "")
        .replace(Regex("\\s+#\\S.*$"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
    if (name.isEmpty()) return UNREADABLE
    return ListLine(
        quantity = quantity,
        name = name,
        set = printing?.groupValues?.get(1)?.lowercase(),
        number = printing?.groupValues?.get(2)?.takeIf { it.isNotEmpty() },
        foil = foil
    )
}

private val UNREADABLE = ListLine(0, null)

/** The cells of one CSV row: commas outside quotes separate, "" inside quotes is a quote. */
fun csvCells(row: String): List<String> {
    val cells = mutableListOf<String>()
    val cell = StringBuilder()
    var quoted = false
    var i = 0
    while (i < row.length) {
        val c = row[i]
        when {
            quoted && c == '"' && row.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
            quoted && c == '"' -> quoted = false
            quoted -> cell.append(c)
            c == '"' -> quoted = true
            c == ',' -> { cells += cell.toString(); cell.clear() }
            else -> cell.append(c)
        }
        i++
    }
    cells += cell.toString()
    return cells.map { it.trim() }
}

private object Columns {
    val quantity = listOf("count", "quantity", "qty", "amount")
    val name = listOf("name", "card name", "card")
    val set = listOf("set code", "edition code", "set", "edition")
    val number = listOf("collector number", "card number", "collector_number", "number", "cn")
    val foil = listOf("foil", "finish", "printing")
    val id = listOf("scryfall id", "scryfall_id", "scryfallid")
}

private fun List<String>.column(names: List<String>): Int = names.firstNotNullOfOrNull { n -> indexOf(n).takeIf { it != -1 } } ?: -1

private fun isFoilValue(v: String) =
    Regex("foil|etched|^(true|yes|1)$", RegexOption.IGNORE_CASE).containsMatchIn(v) &&
        !Regex("non|normal|^(false|no|0)$", RegexOption.IGNORE_CASE).containsMatchIn(v)

private val SET_CODE = Regex("[A-Za-z0-9]{2,6}")
private val SCRYFALL_ID = Regex("[0-9a-fA-F-]{36}")

private fun parseCsv(rows: List<String>): ParsedList {
    val header = csvCells(rows.first()).map { it.lowercase() }
    val qtyAt = header.column(Columns.quantity)
    val nameAt = header.column(Columns.name)
    val setAt = header.column(Columns.set)
    val numberAt = header.column(Columns.number)
    val foilAt = header.column(Columns.foil)
    val idAt = header.column(Columns.id)
    val lines = mutableListOf<ListLine>()
    val skipped = mutableListOf<String>()
    for (row in rows.drop(1)) {
        if (row.isBlank()) continue
        val cells = csvCells(row)
        fun get(i: Int) = if (i >= 0) cells.getOrNull(i).orEmpty() else ""
        val name = get(nameAt).ifEmpty { null }
        val id = get(idAt).takeIf { SCRYFALL_ID.matches(it) }?.lowercase()
        if (name == null && id == null) { skipped += row; continue }
        lines += ListLine(
            quantity = (get(qtyAt).toIntOrNull() ?: 1).coerceIn(1, MAX_COPIES),
            name = name,
            // Some apps put the set's full name in "Edition" — only a short code is usable.
            set = get(setAt).takeIf { SET_CODE.matches(it) }?.lowercase(),
            number = get(numberAt).ifEmpty { null },
            scryfallId = id,
            foil = isFoilValue(get(foilAt))
        )
    }
    return ParsedList(lines, skipped)
}

private fun looksLikeCsv(firstRow: String): Boolean {
    if (',' !in firstRow) return false
    val header = csvCells(firstRow).map { it.lowercase() }
    return (header.column(Columns.name) != -1 || header.column(Columns.id) != -1) && header.column(Columns.quantity) != -1
}

/** Reads a pasted or exported card list (text or CSV). */
fun parseCardList(text: String): ParsedList {
    val rows = text.removePrefix("﻿").split(Regex("\\r?\\n"))
    val first = rows.firstOrNull { it.isNotBlank() } ?: return ParsedList(emptyList(), emptyList())
    if (looksLikeCsv(first)) return parseCsv(rows.drop(rows.indexOf(first)))
    val lines = mutableListOf<ListLine>()
    val skipped = mutableListOf<String>()
    for (row in rows) {
        when (val line = parseTextLine(row)) {
            null -> Unit
            UNREADABLE -> skipped += row.trim()
            else -> lines += line
        }
    }
    return ParsedList(lines, skipped)
}

/**
 * The binder's cards as text: "4 Lightning Bolt", foil copies on their own line ending in *F*.
 * With [printings] (scryfallId → set code to collector number), each line names its exact
 * printing — "(CMR) 472" — so importing it elsewhere keeps the same art.
 */
fun buildCardListText(entries: List<CollectionEntry>, printings: Map<String, Pair<String, String>>? = null): String =
    entries.sortedBy { it.name.lowercase() }.flatMap { e ->
        val p = printings?.get(e.scryfallId)
        val card = if (p != null) "${e.name} (${p.first.uppercase()}) ${p.second}" else e.name
        listOfNotNull(
            if (e.quantity > 0) "${e.quantity} $card" else null,
            if (e.foilQuantity > 0) "${e.foilQuantity} $card *F*" else null
        )
    }.joinToString("\n")
