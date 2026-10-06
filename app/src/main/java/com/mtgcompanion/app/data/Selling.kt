package com.mtgcompanion.app.data

import java.util.Locale

/*
 * Selling: the To sell list. A binder entry says how many of its copies are to sell (its "forSale",
 * synced like "forTrade"); the list shows each with where those copies are — their place and pocket or
 * section — and what they're worth, with the total. Two quick rules fill it: "Spares over 4" (the copies
 * beyond four of a card, counting the decks' too) and "Not in any deck, over $5". It exports as
 * TCGplayer's mass entry text and as a Cardmarket CSV, makes a pull list to fetch them (sellPullList in
 * PullList.kt), and ticking cards off and "Mark N sold" takes those copies out of the collection — their
 * pockets show empty.
 *
 * Which copies are to sell: the entry's plain ones before its foils; of those, the ones with no place
 * first, then the last lines' — the way copies leave a binder (splitPlaces in StoragePlaces.kt).
 *
 * Pure, so it can be tested. Mirrors the web app's src/collection/selling.ts rule for rule, with the
 * same tests (SellingTest.kt ↔ tests/collection/selling.test.ts).
 */

/** A printing's facts for selling: set, number and prices (US dollars, and Cardmarket's euros; null: none). */
data class SellPrinting(val set: String, val number: String, val usd: Double?, val usdFoil: Double?, val eur: Double? = null)

/** How many of the entry's copies are to sell: never more than it has. */
fun forSaleOf(entry: CollectionEntry): Int = (entry.forSale ?: 0).coerceIn(0, (entry.quantity + entry.foilQuantity).coerceAtLeast(0))

/** [entry] with [n] copies to sell (no more than it has). Once set the key stays, as 0 when none are. */
fun withForSale(entry: CollectionEntry, n: Int): CollectionEntry {
    val v = n.coerceIn(0, (entry.quantity + entry.foilQuantity).coerceAtLeast(0))
    if (v == 0 && entry.forSale == null) return entry
    return entry.copy(forSale = v)
}

/** The copies to sell, plain to foil: plain ones first. */
fun sellSplit(entry: CollectionEntry): Pair<Int, Int> {
    val n = forSaleOf(entry)
    val plain = minOf(n, entry.quantity.coerceAtLeast(0))
    return plain to (n - plain)
}

/**
 * One line of the To sell list: an entry's copies to sell ([plain] and [foil]), the place lines they
 * come off ([lines]) and how many have no place ([loose]). [where]: "Rares binder · p2 s1 · NM".
 */
data class SellRow(
    val key: String,
    val collectionId: String,
    val scryfallId: String,
    val name: String,
    val plain: Int,
    val foil: Int,
    val lines: List<CopyPlace>,
    val loose: Int,
    val condition: String?,
    val where: String,
    /** The copies' language code ("ja"); null: not said. */
    val language: String? = null
) {
    val qty: Int get() = plain + foil
}

/** "p2 s1" in a binder, "› Red" in a box. */
private fun spotShort(place: StoragePlace, line: CopyPlace): String = when {
    line.page != null && line.slot != null -> "${place.name} · p${line.page} s${line.slot}"
    line.section != null -> "${place.name} › ${line.section}"
    else -> place.name
}

private fun owned(collections: List<Collection>) = collections.filter { it.kind != CollectionType.WISHLIST }

/** Every entry with copies to sell, A–Z: the Unsorted pile's and the binders'. */
fun sellRows(collections: List<Collection>): List<SellRow> {
    val places = placesOf(collections).associateBy { it.id }
    val out = mutableListOf<SellRow>()
    for (c in owned(collections)) for (e in c.entries) {
        val (plain, foil) = sellSplit(e)
        if (plain + foil <= 0) continue
        val going = splitPlaces(e, plain, foil).second.filter { it.placeId in places }
        val loose = plain + foil - going.sumOf { it.qty }
        val spots = going.map { spotShort(places.getValue(it.placeId), it) }.distinct().toMutableList()
        if (loose > 0) spots += "No place yet"
        val where = (spots + listOfNotNull(e.condition)).joinToString(" · ")
        out += SellRow("${c.id}|${e.scryfallId}", c.id, e.scryfallId, e.name, plain, foil, going, loose, e.condition, where, e.language)
    }
    return out.sortedWith(compareBy<SellRow>({ it.name.lowercase() }, { it.where.lowercase() }))
}

/** "Fact or Fiction ×3". */
fun sellRowTitle(row: SellRow): String = row.name + if (row.qty > 1) " ×${row.qty}" else ""

/** What a row's copies are worth, in US dollars (unitPrice, ValueByPlace.kt); null when there's no price. */
fun sellRowUsd(row: SellRow, facts: (String) -> SellPrinting?): Double? {
    val p = facts(row.scryfallId) ?: return null
    val printing = PrintingFacts(p.set, p.number, p.usd, p.usdFoil)
    val plain = if (row.plain > 0) (unitPrice(printing, false) ?: return null) else 0.0
    val foil = if (row.foil > 0) (unitPrice(printing, true) ?: return null) else 0.0
    return plain * row.plain + foil * row.foil
}

/** The list's worth: every row with a price. */
fun sellTotalUsd(rows: List<SellRow>, facts: (String) -> SellPrinting?): Double = rows.sumOf { sellRowUsd(it, facts) ?: 0.0 }

// ---- Quick rules ----

private fun nameKey(name: String) = name.trim().lowercase().split(" // ")[0].trim()

/** The entries the rules may mark: owned, not tagged as lent out — the Unsorted pile's first. */
private fun sellable(collections: List<Collection>): List<Pair<Collection, CollectionEntry>> {
    val mine = owned(collections)
    return (mine.filter { it.isUnsorted } + mine.filter { !it.isUnsorted })
        .flatMap { c -> c.entries.filter { it.quantity + it.foilQuantity > 0 && lentTag(it) == null }.map { c to it } }
}

private fun withMarks(collections: List<Collection>, marks: Map<String, Int>): List<Collection> =
    if (marks.isEmpty()) collections
    else collections.map { c ->
        if (c.entries.none { "${c.id}|${it.scryfallId}" in marks }) c
        else c.copy(entries = c.entries.map { e -> marks["${c.id}|${e.scryfallId}"]?.let { withForSale(e, it) } ?: e })
    }

/**
 * "Spares over 4": every card owned more than [keep] times — the binders', the Unsorted pile's and the
 * physical decks' copies together — has the copies beyond [keep] marked to sell, from the binders only
 * (the Unsorted pile's first). Copies already to sell count. Basic lands are left out. Also how many
 * copies were newly marked.
 */
fun markSparesToSell(collections: List<Collection>, decks: List<Deck>, keep: Int = 4): Pair<List<Collection>, Int> {
    val owned = ownedCounts(collections, decks)
    val entries = sellable(collections)
    val marks = LinkedHashMap<String, Int>()
    var added = 0
    for ((name, have) in owned) {
        if (have <= keep || isBasicLand(name)) continue
        val mine = entries.filter { nameKey(it.second.name) == name }
        var need = have - keep - mine.sumOf { forSaleOf(it.second) }
        for ((c, e) in mine) {
            if (need <= 0) break
            val free = e.quantity + e.foilQuantity - forSaleOf(e)
            val take = minOf(need, free)
            if (take <= 0) continue
            marks["${c.id}|${e.scryfallId}"] = forSaleOf(e) + take
            need -= take
            added += take
        }
    }
    return withMarks(collections, marks) to added
}

/**
 * "Not in any deck, over $5": every copy of a card no deck plays, is short of or is considering
 * (namesDecksUse, Spares.kt) whose price is over [over] dollars — a foil's foil price. Also how many
 * copies were newly marked.
 */
fun markUnusedToSell(collections: List<Collection>, decks: List<Deck>, over: Double, facts: (String) -> SellPrinting?): Pair<List<Collection>, Int> {
    val used = namesDecksUse(decks)
    val marks = LinkedHashMap<String, Int>()
    var added = 0
    for ((c, e) in sellable(collections)) {
        if (e.name.trim().lowercase() in used || isBasicLand(e.name)) continue
        val p = facts(e.scryfallId) ?: continue
        val printing = PrintingFacts(p.set, p.number, p.usd, p.usdFoil)
        val worth = if (e.quantity > 0) unitPrice(printing, false) else unitPrice(printing, true)
        if (worth == null || worth <= over) continue
        val all = e.quantity + e.foilQuantity
        if (forSaleOf(e) >= all) continue
        added += all - forSaleOf(e)
        marks["${c.id}|${e.scryfallId}"] = all
    }
    return withMarks(collections, marks) to added
}

/** [collections] with [row]'s copies no longer to sell. */
fun unmarkToSell(collections: List<Collection>, row: SellRow): List<Collection> =
    collections.map { c ->
        if (c.id != row.collectionId) c else c.copy(entries = c.entries.map { if (it.scryfallId == row.scryfallId) withForSale(it, 0) else it })
    }

/** Copies owned of the card called [name] in the binders and the Unsorted pile, and how many of them are to sell. */
fun sellCountsByName(collections: List<Collection>, name: String): Pair<Int, Int> {
    var copies = 0
    var toSell = 0
    for (c in owned(collections)) for (e in c.entries) {
        if (!sameCardName(e.name, name)) continue
        copies += (e.quantity + e.foilQuantity).coerceAtLeast(0)
        toSell += forSaleOf(e)
    }
    return copies to toSell
}

/**
 * [collections] with [n] copies of the card called [name] to sell — "Sell…" on a card's Where it is:
 * the entries filled in order (the Unsorted pile's first, then the binders'), the rest with none.
 */
fun setForSaleByName(collections: List<Collection>, name: String, n: Int): List<Collection> {
    var left = n.coerceAtLeast(0)
    val marks = LinkedHashMap<String, Int>()
    val mine = owned(collections)
    for (c in mine.filter { it.isUnsorted } + mine.filter { !it.isUnsorted }) for (e in c.entries) {
        if (!sameCardName(e.name, name)) continue
        val take = minOf(left, (e.quantity + e.foilQuantity).coerceAtLeast(0))
        if (take == 0 && e.forSale == null) continue
        marks["${c.id}|${e.scryfallId}"] = take
        left -= take
    }
    return withMarks(collections, marks)
}

// ---- Sold ----

/** What "Mark N sold" did: the collection after, and the rows sold. */
data class SoldResult(val collections: List<Collection>, val sold: List<SellRow>) {
    val copies: Int get() = sold.sumOf { it.qty }
}

/**
 * "Mark N sold": the copies to sell of each row in [keys] leave the collection — off their places too,
 * so their pockets show empty. An entry with no copies left goes. What it's for trade comes down to
 * what's left.
 */
fun markSold(collections: List<Collection>, keys: Set<String>): SoldResult {
    val rows = sellRows(collections).filter { it.key in keys }
    if (rows.isEmpty()) return SoldResult(collections, emptyList())
    val byKey = rows.associateBy { it.key }
    val next = collections.map { c ->
        if (c.entries.none { "${c.id}|${it.scryfallId}" in byKey }) return@map c
        c.copy(entries = c.entries.mapNotNull { e ->
            val row = byKey["${c.id}|${e.scryfallId}"] ?: return@mapNotNull e
            val staying = splitPlaces(e, row.plain, row.foil).first
            val plain = (e.quantity - row.plain).coerceAtLeast(0)
            val foil = (e.foilQuantity - row.foil).coerceAtLeast(0)
            if (plain + foil <= 0) return@mapNotNull null
            val left = e.copy(
                quantity = plain,
                foilQuantity = foil,
                forSale = 0,
                forTrade = e.forTrade?.let { minOf(it, plain + foil) }?.takeIf { it > 0 }
            )
            if (e.places != null) withPlaces(left, staying) else left
        })
    }
    return SoldResult(next, rows)
}

// ---- Exports ----

/** TCGplayer's mass entry: "3 Fact or Fiction [MH2]" a line, by name. */
fun tcgplayerMassEntry(rows: List<SellRow>, facts: (String) -> SellPrinting?): String =
    rows.joinToString("\n") { r ->
        val set = facts(r.scryfallId)?.set?.uppercase()?.takeIf { it.isNotEmpty() }
        "${r.qty} ${r.name}" + if (set != null) " [$set]" else ""
    }

/** Cardmarket's words for a condition: NM, EX, GD, PL, PO for this app's NM, LP, MP, HP, DMG. */
fun cardmarketCondition(code: String?): String = when (code) {
    "NM" -> "NM"
    "LP" -> "EX"
    "MP" -> "GD"
    "HP" -> "PL"
    "DMG" -> "PO"
    else -> ""
}

private fun csvCell(value: String): String =
    if (value.any { it == '"' || it == ',' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value

/**
 * A Cardmarket CSV: count, name, set, number, condition (Cardmarket's grades), language, foil and
 * Cardmarket's price in euros when Scryfall has one (plain copies only). Plain and foil copies are lines
 * of their own.
 */
fun cardmarketCsv(rows: List<SellRow>, facts: (String) -> SellPrinting?): String {
    val out = mutableListOf("Count,Name,Expansion,Number,Condition,Language,Foil,Price (EUR)")
    for (r in rows) {
        val p = facts(r.scryfallId)
        val language = r.language?.let { languageName(it) } ?: "English"
        for ((foil, n) in listOf(false to r.plain, true to r.foil)) {
            if (n <= 0) continue
            val eur = if (!foil) p?.eur?.let { String.format(Locale.US, "%.2f", it) } ?: "" else ""
            out += listOf(
                n.toString(), r.name, p?.set?.uppercase() ?: "", p?.number ?: "", cardmarketCondition(r.condition), language,
                if (foil) "Foil" else "", eur
            ).joinToString(",") { csvCell(it) }
        }
    }
    return out.joinToString("\n")
}

// ---- Sync ----

/**
 * [theirs] with each entry's "forSale" put back where [source] (the same binder, as this device has it)
 * has one and [theirs] doesn't say — an entry saved by an app that doesn't know about selling comes
 * without it. One no longer to sell is kept as 0, so it isn't put back. The same object when nothing
 * changes.
 */
fun keepForSaleFromOlderApp(source: Collection, theirs: Collection): Collection {
    val mine = source.entries.filter { it.forSale != null }.associate { it.scryfallId to it.forSale!! }
    if (mine.isEmpty() || theirs.entries.none { it.forSale == null && it.scryfallId in mine }) return theirs
    return theirs.copy(entries = theirs.entries.map { e ->
        if (e.forSale != null) e else mine[e.scryfallId]?.let { e.copy(forSale = it.coerceIn(0, (e.quantity + e.foilQuantity).coerceAtLeast(0))) } ?: e
    })
}
