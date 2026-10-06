package com.mtgcompanion.app.data

import java.util.Locale

/*
 * Value by place: what the collection is worth, in total and place by place — each binder and box,
 * the deck boxes, the copies lent out and the ones with no place yet — with every copy as a row for a
 * spreadsheet (CSV) or a printed report, for insurance or a move.
 *
 * Prices are Scryfall's, in US dollars; the CSV converts them to the user's currency (Prices.kt).
 * Graded copies (Graded.kt) and sealed product (Sealed.kt) count at the value the user entered — card
 * prices are for raw copies, and no app has prices for sealed product — with a "Graded" or "Sealed" label.
 *
 * Pure, so it can be tested. Mirrors the web app's src/collection/valueByPlace.ts rule for rule, with
 * the same tests (ValueByPlaceTest.kt ↔ tests/collection/valueByPlace.test.ts).
 */

/** A printing's set, collector number and prices (US dollars; null: none). */
data class PrintingFacts(val set: String, val number: String, val usd: Double?, val usdFoil: Double?)

enum class ValueKind { PLACE, DECKS, LENT, NONE }

/**
 * Copies of one printing in one spot, with their price. [group]: the place's id, or "decks", "lent",
 * "none"; [where]: "Shelf › Red box", "Deck boxes › Atraxa", "Lent out › Sam", "No place yet"; [spot]:
 * "Red", "Page 3, slot 5" or ""; [unitUsd]: one copy's price, null when there's none.
 */
data class ValueRow(
    val name: String,
    val scryfallId: String,
    val set: String,
    val number: String,
    val foil: Boolean,
    val condition: String,
    val language: String,
    val qty: Int,
    val kind: ValueKind,
    val group: String,
    val where: String,
    val spot: String,
    val unitUsd: Double?,
    /** "Graded" (its value the one entered) or "Sealed"; null for a raw copy. */
    val label: String? = null,
    /** A graded copy's slab: "PSA 10". */
    val grade: String? = null
)

/** One copy's price: a foil's foil price (or else the plain one), a plain copy's plain price (or else the foil one). */
fun unitPrice(p: PrintingFacts?, foil: Boolean): Double? = when {
    p == null -> null
    foil -> p.usdFoil ?: p.usd
    else -> p.usd ?: p.usdFoil
}

/** Every copy owned as rows: in places, in deck boxes, lent out, and with no place yet. */
fun valueRows(collections: List<Collection>, decks: List<Deck>, facts: (String) -> PrintingFacts?): List<ValueRow> {
    val places = placesOf(collections)
    val known = places.map { it.id }.toSet()
    val lent = lentCopies(collections, decks)
    val byEntry = lentByEntry(lent)
    val rows = mutableListOf<ValueRow>()
    fun row(name: String, scryfallId: String, condition: String, language: String, foil: Boolean, qty: Int, kind: ValueKind, group: String, where: String, spot: String) {
        val p = facts(scryfallId)
        rows += ValueRow(name, scryfallId, p?.set?.uppercase() ?: "", p?.number ?: "", foil, condition, language, qty, kind, group, where, spot, unitPrice(p, foil))
    }
    for (c in collections) {
        if (c.kind == CollectionType.WISHLIST) continue
        for (e in c.entries) {
            if (e.quantity + e.foilQuantity <= 0) continue
            val condition = e.condition?.let { conditionName(it) } ?: ""
            val language = e.language?.let { languageName(it) } ?: ""
            for (line in placedCopies(e)) {
                if (line.placeId !in known) continue
                val spot = line.section ?: if (line.page != null && line.slot != null) pocketLabel(line.page, line.slot) else ""
                row(e.name, e.scryfallId, condition, language, line.isFoil, line.qty, ValueKind.PLACE, line.placeId, placePath(places, line.placeId), spot)
            }
            val clean = if (placedCopies(e).all { it.placeId in known }) e else withPlaces(e, placedCopies(e).filter { it.placeId in known })
            val (free, freeFoil) = unplacedCopies(clean)
            val (plainOut, foilOut) = lentOf(byEntry, c.id, e)
            val tagged = lentTag(e) != null
            for (foil in listOf(false, true)) {
                val n = if (foil) freeFoil - foilOut else free - plainOut
                if (n <= 0) continue
                row(
                    e.name, e.scryfallId, condition, language, foil, n,
                    if (tagged) ValueKind.LENT else ValueKind.NONE, if (tagged) "lent" else "none", if (tagged) "Lent out" else "No place yet", ""
                )
            }
        }
    }
    for (d in decks) {
        // Copies lent from the deck come off its printings in order.
        val out = HashMap<String, Int>()
        for (e in realCopiesOf(d)) {
            val key = e.name.trim().lowercase()
            val left = out.getOrPut(key) { lentFromDeck(lent, d.id, e.name) }
            val away = minOf(left, e.quantity)
            out[key] = left - away
            val n = e.quantity - away
            if (n > 0) row(e.name, e.scryfallId, "", "", false, n, ValueKind.DECKS, "decks", "Deck boxes › ${d.name}", "")
        }
    }
    for (l in lent) {
        val entry = l.collectionId?.let { id -> collections.firstOrNull { it.id == id }?.entries?.firstOrNull { it.scryfallId == l.card.scryfallId } }
        row(
            l.card.name, l.card.scryfallId, entry?.condition?.let { conditionName(it) } ?: "", entry?.language?.let { languageName(it) } ?: "",
            l.card.isFoil, l.qty, ValueKind.LENT, "lent", "Lent out › ${l.loan.to}", ""
        )
    }
    // Graded copies and sealed product, at the value entered; in their place, or with no place yet.
    fun inPlace(placeId: String?) = placeId != null && placeId in known
    for (g in gradedOf(collections)) {
        val p = facts(g.scryfallId)
        val here = inPlace(g.placeId)
        rows += ValueRow(
            g.name, g.scryfallId, p?.set?.uppercase() ?: "", p?.number ?: "", g.isFoil, "", "", 1,
            if (here) ValueKind.PLACE else ValueKind.NONE, if (here) g.placeId!! else "none", if (here) placePath(places, g.placeId!!) else "No place yet",
            if (here) g.section.orEmpty() else "", g.valueUsd, label = "Graded", grade = gradeLabel(g)
        )
    }
    for (s in sealedOf(collections)) {
        if (s.count <= 0) continue
        val here = inPlace(s.placeId)
        rows += ValueRow(
            s.name, "", s.setCode?.uppercase().orEmpty(), "", false, "", "", s.count,
            if (here) ValueKind.PLACE else ValueKind.NONE, if (here) s.placeId!! else "none", if (here) placePath(places, s.placeId!!) else "No place yet",
            "", s.valueUsd, label = "Sealed"
        )
    }
    return rows
}

/** A row's finish, as the spreadsheet and the report say it: "Foil", "Normal", "Graded PSA 10", "Sealed". */
fun finishOf(r: ValueRow): String = when (r.label) {
    "Sealed" -> "Sealed"
    "Graded" -> listOf("Graded ${r.grade.orEmpty()}".trim(), if (r.foil) "foil" else "").filter { it.isNotEmpty() }.joinToString(", ")
    else -> if (r.foil) "Foil" else "Normal"
}

/** One bar on the screen: a place (its name, and the places it's in), the deck boxes, lent out or no place. */
data class ValueGroup(
    val key: String,
    val kind: ValueKind,
    val label: String,
    val detail: String,
    val usd: Double,
    /** Cards in it, graded copies too. */
    val copies: Int,
    /** Sealed products in it (each box, bundle or precon). */
    val sealed: Int = 0
)

/** The rows added up: a group per place holding copies, the deck boxes, lent out and no place yet; the total. */
data class ValueTotals(val groups: List<ValueGroup>, val usd: Double, val copies: Int, val sealed: Int = 0)

/** The rows added up — the most valuable first, no place yet last. */
fun valueGroups(rows: List<ValueRow>, collections: List<Collection>): ValueTotals {
    val places = placesOf(collections)
    val groups = LinkedHashMap<String, ValueGroup>()
    for (r in rows) {
        val g = groups[r.group] ?: run {
            val place = if (r.kind == ValueKind.PLACE) places.firstOrNull { it.id == r.group } else null
            ValueGroup(
                r.group, r.kind,
                place?.name ?: when (r.kind) { ValueKind.DECKS -> "Deck boxes"; ValueKind.LENT -> "Lent out"; else -> "No place yet" },
                if (place != null) parentsOf(places, place.id).joinToString(" › ") { it.name } else "",
                0.0, 0
            )
        }
        val isSealed = r.label == "Sealed"
        groups[r.group] = g.copy(
            usd = g.usd + (r.unitUsd ?: 0.0) * r.qty,
            copies = g.copies + if (isSealed) 0 else r.qty,
            sealed = g.sealed + if (isSealed) r.qty else 0
        )
    }
    val all = groups.values.toList()
    val order = compareByDescending<ValueGroup> { it.usd }.thenByDescending { it.copies }.thenBy { it.label }
    return ValueTotals(
        all.filter { it.kind != ValueKind.NONE }.sortedWith(order) + all.filter { it.kind == ValueKind.NONE },
        all.sumOf { it.usd },
        all.sumOf { it.copies },
        all.sumOf { it.sealed }
    )
}

/** A CSV cell, quoted when it has to be. */
private fun cell(value: String): String =
    if (value.any { it == '"' || it == ',' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value

/** How the CSV writes money: in the currency [code], at [rate] to the dollar, to [decimals] places. */
data class CsvMoney(val code: String, val rate: Double, val decimals: Int)

/**
 * The rows as a spreadsheet: name, set, number, finish, condition, language, quantity, place, spot,
 * unit price and total in the user's currency — by place, then name. A copy with no price leaves both
 * prices empty.
 */
fun valueCsv(rows: List<ValueRow>, money: CsvMoney): String {
    fun amount(usd: Double) = String.format(Locale.US, "%.${money.decimals}f", usd * money.rate)
    fun cmp(a: String, b: String) = a.lowercase().compareTo(b.lowercase())
    val sorted = rows.sortedWith { a, b ->
        cmp(a.where, b.where).takeIf { it != 0 } ?: cmp(a.spot, b.spot).takeIf { it != 0 } ?: cmp(a.name, b.name).takeIf { it != 0 }
            ?: a.foil.compareTo(b.foil)
    }
    val out = mutableListOf("Name,Set,Number,Finish,Condition,Language,Quantity,Place,Spot,Unit price (${money.code}),Total (${money.code})")
    for (r in sorted) {
        out += listOf(
            r.name, r.set, r.number, finishOf(r), r.condition, r.language, r.qty.toString(), r.where, r.spot,
            r.unitUsd?.let { amount(it) } ?: "", r.unitUsd?.let { amount(it * r.qty) } ?: ""
        ).joinToString(",") { cell(it) }
    }
    return out.joinToString("\n")
}
