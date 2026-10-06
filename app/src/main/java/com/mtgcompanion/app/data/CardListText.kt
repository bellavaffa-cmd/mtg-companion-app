package com.mtgcompanion.app.data

// Card lists as text, for moving a collection between this app and others (Moxfield, Archidekt,
// ManaBox, Deckbox, TCGplayer, Dragon Shield…). Reads the usual pasted/exported shapes, one card per line:
//
//   4 Lightning Bolt
//   2x Counterspell
//   1 Sol Ring (CMR) 472
//   1 Sol Ring [CMR] 472 *F*        (foil; *E* etched, "(foil)"/"[foil]" work too)
//
// and the CSV collection exports those apps make (a header row naming Count/Quantity and Name, and
// optionally the set code, collector number, foil, condition, language and Scryfall ID; a leading
// "sep=," line, as Dragon Shield writes for Excel, sets the separator). Writes the
// plain text form, which all of them read back, and a CSV that keeps condition and language too.
// Mirrors the web app's collection/cardListText.ts.

/** Which part of a decklist a card line sits in. Binder imports take every part alike. */
enum class ListSection { MAIN, SIDEBOARD, MAYBEBOARD }

/** One line of a list: how many, which card (by id, printing or name), whether foil, and its part. */
data class ListLine(
    val quantity: Int,
    val name: String?,
    val set: String? = null,
    val number: String? = null,
    val scryfallId: String? = null,
    val foil: Boolean = false,
    val section: ListSection = ListSection.MAIN,
    /** From a CSV's Condition column, as a code (see CopyDetails.kt); null when it hasn't one. */
    val condition: String? = null,
    /** From a CSV's Language column, as a Scryfall code; null when it hasn't one. */
    val language: String? = null,
    /**
     * From a CSV's location column — "Binder 1", "Box R" (see ImportPlaces.kt); null when the list
     * has no such column or the cell is blank.
     */
    val location: String? = null
)

/** [locationColumn]: the header of the column that says where cards are kept, as the file spells it; null when none. */
data class ParsedList(val lines: List<ListLine>, val skipped: List<String>, val locationColumn: String? = null) {
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

/** "SB: 2 Duress" — the older one-line way of marking a sideboard card. */
private val SIDEBOARD_PREFIX = Regex("^SB:\\s*", RegexOption.IGNORE_CASE)

/**
 * The part a header line starts: "Sideboard", "Maybeboard (12)" and the like, or back to the main
 * deck for "Deck", "Commander" and "Companion". Null for headers that don't change the part
 * ("Creatures (30)", "Tokens").
 */
private fun headerSection(line: String): ListSection? =
    when (line.lowercase().substringBefore('(').substringBefore(':').trim()) {
        "sideboard" -> ListSection.SIDEBOARD
        "maybeboard" -> ListSection.MAYBEBOARD
        "deck", "commander", "companion" -> ListSection.MAIN
        else -> null
    }

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

/** The cells of one CSV row: commas (or [sep]) outside quotes separate, "" inside quotes is a quote. */
fun csvCells(row: String, sep: Char = ','): List<String> {
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
            c == sep -> { cells += cell.toString(); cell.clear() }
            else -> cell.append(c)
        }
        i++
    }
    cells += cell.toString()
    return cells.map { it.trim() }
}

// Which header each app uses, best first:
//   ManaBox      Name, Set code, Collector number, Foil, Quantity, Scryfall ID, Condition, Language
//   Moxfield     Count, Name, Edition (a code), Collector Number, Foil, Condition, Language
//   Deckbox      Count, Name, Edition (a set name), Card Number, Condition, Language, Foil
//   Archidekt    Quantity, Name, Finish, Condition, Language, Edition Code, Scryfall ID, Collector Number
//   TCGplayer    Quantity, Name, Simple Name, Set, Card Number, Set Code, Printing, Condition, Language
//                (its seller export: Product Name, Number, Condition, Total Quantity)
//   Dragon Shield  Quantity, Card Name, Set Code, Card Number, Condition, Printing, Language
private object Columns {
    val quantity = listOf("count", "quantity", "qty", "amount", "total quantity")
    // TCGplayer's Name can carry the treatment ("Sol Ring (Foil Etched)"); its Simple Name doesn't.
    val name = listOf("simple name", "name", "card name", "card", "product name")
    val set = listOf("set code", "edition code", "set", "edition")
    val number = listOf("collector number", "card number", "collector_number", "number", "cn")
    val foil = listOf("foil", "finish", "printing")
    val id = listOf("scryfall id", "scryfall_id", "scryfallid")
    // Moxfield, Deckbox and TCGplayer write words ("Near Mint", "English"); ManaBox writes
    // near_mint and en. Read the same way whichever it is (see conditionCode / languageCode).
    val condition = listOf("condition")
    val language = listOf("language", "lang")
}

private fun List<String>.column(names: List<String>): Int = names.firstNotNullOfOrNull { n -> indexOf(n).takeIf { it != -1 } } ?: -1

private fun isFoilValue(v: String) =
    Regex("foil|etched|^(true|yes|1)$", RegexOption.IGNORE_CASE).containsMatchIn(v) &&
        !Regex("non|normal|^(false|no|0)$", RegexOption.IGNORE_CASE).containsMatchIn(v)

private val FOIL_CONDITION = Regex("\\s(foil|etched)$", RegexOption.IGNORE_CASE)

private val SET_CODE = Regex("[A-Za-z0-9]{2,6}")
private val SCRYFALL_ID = Regex("[0-9a-fA-F-]{36}")

private fun parseCsv(rows: List<String>, sep: Char): ParsedList {
    val header = csvCells(rows.first(), sep).map { it.lowercase() }
    val qtyAt = header.column(Columns.quantity)
    val nameAt = header.column(Columns.name)
    val setAt = header.column(Columns.set)
    val numberAt = header.column(Columns.number)
    val foilAt = header.column(Columns.foil)
    val idAt = header.column(Columns.id)
    val conditionAt = header.column(Columns.condition)
    val languageAt = header.column(Columns.language)
    val locationAt = locationColumnIn(header)
    val lines = mutableListOf<ListLine>()
    val skipped = mutableListOf<String>()
    for (row in rows.drop(1)) {
        if (row.isBlank()) continue
        val cells = csvCells(row, sep)
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
            // TCGplayer puts the finish in the condition: "Near Mint Foil".
            foil = isFoilValue(get(foilAt)) || FOIL_CONDITION.containsMatchIn(get(conditionAt)),
            condition = conditionCode(get(conditionAt)),
            language = languageCode(get(languageAt)),
            location = get(locationAt).ifEmpty { null }
        )
    }
    val column = if (locationAt >= 0) csvCells(rows.first(), sep).getOrNull(locationAt) else null
    return ParsedList(lines, skipped, column)
}

private fun looksLikeCsv(firstRow: String, sep: Char): Boolean {
    if (sep !in firstRow) return false
    val header = csvCells(firstRow, sep).map { it.lowercase() }
    return (header.column(Columns.name) != -1 || header.column(Columns.id) != -1) && header.column(Columns.quantity) != -1
}

private val SEP_HINT = Regex("^\\s*\"?sep=(.)\"?\\s*$", RegexOption.IGNORE_CASE)

/** Reads a pasted or exported card list (text or CSV). */
fun parseCardList(text: String): ParsedList {
    var rows = text.removePrefix("﻿").split(Regex("\\r?\\n"))
    // "sep=," (or "sep=;") before the header is Excel's hint, which Dragon Shield writes.
    val hint = SEP_HINT.matchEntire(rows.firstOrNull { it.isNotBlank() }.orEmpty())
    val sep = hint?.groupValues?.get(1)?.first() ?: ','
    if (hint != null) rows = rows.drop(rows.indexOfFirst { it.isNotBlank() } + 1)
    val first = rows.firstOrNull { it.isNotBlank() } ?: return ParsedList(emptyList(), emptyList())
    if (looksLikeCsv(first, sep)) return parseCsv(rows.drop(rows.indexOf(first)), sep)
    val lines = mutableListOf<ListLine>()
    val skipped = mutableListOf<String>()
    // Arena exports start with "Deck" and put the sideboard after a blank line, with no header of
    // its own. Only lists that start that way get the blank-line rule, so a plain list with gaps
    // in it stays all one deck.
    val arena = first.trim().lowercase().removeSuffix(":").trim() == "deck"
    var section = ListSection.MAIN
    var cardsInSection = false
    for (row in rows) {
        val trimmed = row.trim()
        if (trimmed.isEmpty()) {
            if (arena && section == ListSection.MAIN && cardsInSection) {
                section = ListSection.SIDEBOARD
                cardsInSection = false
            }
            continue
        }
        if (isHeader(trimmed)) {
            headerSection(trimmed)?.let { section = it; cardsInSection = false }
            continue
        }
        val sideboardLine = SIDEBOARD_PREFIX.containsMatchIn(trimmed)
        when (val line = parseTextLine(trimmed.replace(SIDEBOARD_PREFIX, ""))) {
            null -> Unit
            UNREADABLE -> skipped += trimmed
            else -> {
                lines += line.copy(section = if (sideboardLine) ListSection.SIDEBOARD else section)
                cardsInSection = true
            }
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

/** A CSV cell, quoted when it has to be. */
private fun csvCell(value: String): String =
    if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value

/**
 * The header [buildCardListCsv] writes — Moxfield's own column names, which the others read too, and
 * "Place": where the copies are kept, which an import here reads back (ImportPlaces.kt).
 */
const val CARD_LIST_CSV_HEADER = "Count,Name,Edition,Collector Number,Foil,Condition,Language,Scryfall ID,Place"

/**
 * The binder's cards as a CSV collection file: one row per card, finish and place (foils on their own
 * row, "foil" in the Foil column; copies in two places two rows, those with no place a row with a
 * blank Place), with the copies' condition and language in words ("Near Mint", "Japanese") when
 * they've been set. [printings] (scryfallId → set code to collector number) fills Edition and
 * Collector Number; [places] (the user's storage places) names each row's place. Reads back in with
 * [parseCardList], here and in other apps.
 */
fun buildCardListCsv(
    entries: List<CollectionEntry>,
    printings: Map<String, Pair<String, String>> = emptyMap(),
    places: List<StoragePlace> = emptyList()
): String {
    val names = places.associate { it.id to it.name }
    val rows = entries.sortedBy { it.name.lowercase() }.flatMap { e ->
        val p = printings[e.scryfallId]
        fun row(count: Int, foil: Boolean, place: String) = listOf(
            count.toString(),
            e.name,
            p?.first?.lowercase().orEmpty(),
            p?.second.orEmpty(),
            if (foil) "foil" else "",
            e.condition?.let(::conditionName).orEmpty(),
            e.language?.let(::languageName).orEmpty(),
            e.scryfallId,
            place
        ).joinToString(",") { csvCell(it) }
        val out = mutableListOf<String>()
        for (foil in listOf(false, true)) {
            var left = if (foil) e.foilQuantity else e.quantity
            if (left <= 0) continue
            val byPlace = LinkedHashMap<String, Int>()
            for (line in placedCopies(e)) {
                if (line.isFoil != foil) continue
                val name = names[line.placeId] ?: continue
                byPlace[name] = (byPlace[name] ?: 0) + line.qty
            }
            for ((name, n) in byPlace) {
                val take = minOf(n, left)
                if (take <= 0) continue
                out += row(take, foil, name)
                left -= take
            }
            if (left > 0) out += row(left, foil, "")
        }
        out
    }
    return (listOf(CARD_LIST_CSV_HEADER) + rows).joinToString("\n")
}
