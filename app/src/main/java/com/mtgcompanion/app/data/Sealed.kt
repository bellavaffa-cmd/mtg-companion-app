package com.mtgcompanion.app.data

import kotlin.math.roundToInt
import kotlin.math.roundToLong

/*
 * Sealed product: booster boxes, collector boxes, bundles, precons — still in their wrapping. Each
 * product is a line with how many, where it's kept (a storage place), what the user paid each and what
 * it's worth now each, with the change between them.
 *
 * No app has prices for sealed product (Scryfall prices single cards; the MTGJSON data the apps read
 * is decklists), so the value is what the user entered and says so ("value you entered") — never a
 * guessed price. Adding one searches Scryfall's sets (for the boxes and bundles) and MTGJSON's precons;
 * anything else is a product by name with a value typed in.
 *
 * "Open" takes one off the list: a box or bundle starts a sort of a new pile (SortPiles.kt) so every
 * card lands in the right place; a precon becomes a deck with its list filled in (the precon import).
 *
 * Where they're kept: the Unsorted pile's "sealed" (Collection.sealed, JSON in CollectionModels.kt), so
 * they sync with the library like the loans do. Two devices' lists merge product by product
 * (mergeSealed): one added on either side is kept, one deleted on either side stays deleted, counts
 * add up the way a card's do, every other field goes to whoever changed it. A pile saved by an app from
 * before sealed product comes without the key and keeps this device's (keepSealedFromOlderApp).
 *
 * Pure, so it can be tested. Mirrors the web app's src/collection/sealed.ts rule for rule, with the
 * same tests (SealedTest.kt ↔ tests/collection/sealed.test.ts).
 */

/** The kinds a set is sold as, in the order they're offered. */
val SET_PRODUCT_KINDS = listOf(SealedKind.PLAY_BOX, SealedKind.COLLECTOR_BOX, SealedKind.BUNDLE, SealedKind.SET_BOX, SealedKind.DRAFT_BOX)

private fun cents(v: Double?): Double? = v?.takeIf { !it.isNaN() && !it.isInfinite() && it >= 0 }?.let { (it * 100).roundToLong() / 100.0 }

/** A product as both apps write it: optional fields null (left out) when not said, money to the cent. */
fun sealedProduct(p: SealedProduct): SealedProduct {
    val value = cents(p.valueUsd)
    return SealedProduct(
        id = p.id,
        name = p.name.trim(),
        kind = SealedKind.fromName(p.kind).name,
        setCode = p.setCode?.takeIf { it.isNotEmpty() }?.lowercase(),
        preconFile = p.preconFile?.takeIf { it.isNotEmpty() },
        count = maxOf(0, p.count),
        placeId = p.placeId?.takeIf { it.isNotEmpty() },
        paidUsd = cents(p.paidUsd),
        valueUsd = value,
        valueAt = if (value != null) p.valueAt?.takeIf { it > 0 } else null,
        createdAt = p.createdAt
    )
}

/** The user's sealed product, kept on the Unsorted pile. */
fun sealedOf(collections: List<Collection>): List<SealedProduct> = collections.firstOrNull { it.isUnsorted }?.sealed.orEmpty()

/** [collections] with the sealed list set to [list] (on the Unsorted pile, made if it isn't there). */
fun withSealed(collections: List<Collection>, list: List<SealedProduct>): List<Collection> =
    withUnsortedPile(collections).map { if (it.isUnsorted) it.copy(sealed = list.map { p -> sealedProduct(p) }) else it }

/** [collections] with [p] added, or changed when one with its id is there. */
fun saveSealed(collections: List<Collection>, p: SealedProduct): List<Collection> {
    val list = sealedOf(collections)
    return withSealed(collections, if (list.any { it.id == p.id }) list.map { if (it.id == p.id) p else it } else list + p)
}

/** [collections] without the product [id]. */
fun removeSealed(collections: List<Collection>, id: String): List<Collection> =
    withSealed(collections, sealedOf(collections).filter { it.id != id })

val SealedProduct.isPrecon: Boolean get() = sealedKind == SealedKind.PRECON

/** The change from what was paid to what it's worth now, in whole percent; null without both. */
fun sealedChange(paidUsd: Double?, valueUsd: Double?): Int? {
    if (paidUsd == null || valueUsd == null || paidUsd <= 0) return null
    return jsRound((valueUsd - paidUsd) / paidUsd * 100)
}

fun sealedChange(p: SealedProduct): Int? = sealedChange(p.paidUsd, p.valueUsd)

/** Math.round as JavaScript does it (halves up), so both apps show the same percent. */
private fun jsRound(x: Double): Int = kotlin.math.floor(x + 0.5).roundToInt()

/** A change as a line says it: "+13%", "−9%" (a minus sign), "0%". */
fun changeLabel(pct: Int): String = when {
    pct > 0 -> "+$pct%"
    pct < 0 -> "−${-pct}%"
    else -> "0%"
}

/** What the whole list is worth: each product's value × its count. Products with no value add nothing. */
fun sealedTotalUsd(list: List<SealedProduct>): Double = list.sumOf { (it.valueUsd ?: 0.0) * it.count }

/** What was paid for the whole list (products with a price paid). */
fun sealedPaidUsd(list: List<SealedProduct>): Double = list.sumOf { (it.paidUsd ?: 0.0) * it.count }

/** A product's line under its name: "×2 · Cupboard, hall · paid $210 each", "×1 · No place yet". */
fun sealedLine(p: SealedProduct, collections: List<Collection>, money: (Double) -> String): String {
    val places = placesOf(collections)
    val where = if (p.placeId != null && places.any { it.id == p.placeId }) placePath(places, p.placeId) else "No place yet"
    val paid = p.paidUsd?.let { "paid ${money(it)}${if (p.count > 1) " each" else ""}" }.orEmpty()
    return listOf("×${p.count}", where, paid).filter { it.isNotEmpty() }.joinToString(" · ")
}

// ---- Adding one ----

/** One thing "+ Add sealed product" can add, from the search. [detail]: the set's code and year, "Commander precon", "Your own product". */
data class SealedOption(
    val key: String,
    val name: String,
    val kind: SealedKind,
    val setCode: String? = null,
    val preconFile: String? = null,
    val detail: String
)

/** A set, as Scryfall lists it (SetInfo, the fields this needs). */
data class SealedSet(val code: String, val name: String, val releasedAt: String? = null, val cardCount: Int? = null)

private fun words(s: String): List<String> = s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

private fun matches(query: String, vararg texts: String): Boolean {
    val q = words(query)
    if (q.isEmpty()) return false
    val hay = texts.flatMap { words(it) }
    return q.all { w -> hay.any { it.startsWith(w) } }
}

/** Strings compared as JavaScript's localeCompare does for plain names, near enough: case apart first. */
private val byName = Comparator<String> { a, b -> a.lowercase().compareTo(b.lowercase()).takeIf { it != 0 } ?: a.compareTo(b) }

/**
 * What a search for [query] offers: each set whose name or code fits as each product it's sold as
 * ("Duskmourn Play Booster Box"…), newest first, then each precon that fits ("Precon: Blame Game"),
 * then the words typed as a product of the user's own. At most [limit] sets and precons each.
 */
fun sealedOptions(query: String, sets: List<SealedSet>, precons: List<PreconInfo>, limit: Int = 6): List<SealedOption> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    val out = mutableListOf<SealedOption>()
    val fitSets = sets
        .filter { (it.cardCount ?: 1) > 0 && (matches(q, it.name) || it.code.equals(q, ignoreCase = true)) }
        .sortedWith(compareByDescending<SealedSet> { it.releasedAt.orEmpty() }.thenBy(byName) { it.name })
        .take(limit)
    for (s in fitSets) {
        for (kind in SET_PRODUCT_KINDS) {
            out += SealedOption(
                "${s.code}|${kind.name}", "${s.name} ${kind.label}", kind, setCode = s.code.lowercase(),
                detail = listOf(s.code.uppercase(), s.releasedAt?.take(4).orEmpty()).filter { it.isNotEmpty() }.joinToString(" · ")
            )
        }
    }
    val fitPrecons = precons
        .filter { matches(q, it.name) || it.setCode.equals(q, ignoreCase = true) }
        .sortedWith(compareByDescending<PreconInfo> { it.releaseDate.orEmpty() }.thenBy(byName) { it.name })
        .take(limit)
    for (p in fitPrecons) {
        out += SealedOption(
            "precon|${p.fileName}", "Precon: ${p.name}", SealedKind.PRECON, setCode = p.setCode.lowercase(), preconFile = p.fileName,
            detail = listOf("Commander precon", p.setCode.uppercase(), p.releaseDate?.take(4).orEmpty()).filter { it.isNotEmpty() }.joinToString(" · ")
        )
    }
    out += SealedOption("own|$q", q, SealedKind.OTHER, detail = "Your own product — you enter its value")
    return out
}

/** A new product from [option]: one of it, no place, paid and value not said yet. */
fun newSealed(option: SealedOption, id: String, now: Long): SealedProduct = sealedProduct(
    SealedProduct(id = id, name = option.name, kind = option.kind.name, setCode = option.setCode, preconFile = option.preconFile, count = 1, createdAt = now)
)

// ---- Opening one ----

/**
 * One [id] taken off the list — the product goes once none are left. Also the product as it was (null
 * when it isn't there or there are none to open).
 */
fun openSealed(collections: List<Collection>, id: String): Pair<List<Collection>, SealedProduct?> {
    val p = sealedOf(collections).firstOrNull { it.id == id }
    if (p == null || p.count <= 0) return collections to null
    val next = if (p.count > 1) withSealed(collections, sealedOf(collections).map { if (it.id == id) it.copy(count = it.count - 1) else it })
    else removeSealed(collections, id)
    return next to p
}

/** The pile an opened box's cards are from, in the sort: its name. */
fun openedSource(p: SealedProduct): String = p.name

/**
 * The sort to start when [p] is opened: the sort under way when it's of new cards and has scans (the
 * box's cards join it), or a new sort of new cards from [p], with the piles last used ([rules]).
 */
fun sortForOpened(existing: SortSession?, p: SealedProduct, rules: List<PileRule>): SortSession {
    if (existing != null && existing.newCards && existing.scans.isNotEmpty()) return existing
    return SortSession(source = openedSource(p), rules = rules, newCards = true, scans = emptyList())
}

/** The precons a list holds that can be opened as a deck (they name their MTGJSON decklist). */
fun openablePrecons(list: List<SealedProduct>): List<SealedProduct> = list.filter { it.isPrecon && !it.preconFile.isNullOrEmpty() && it.count > 0 }

/** The products that open into a pile to sort: everything but precons with a decklist. */
fun openableBoxes(list: List<SealedProduct>): List<SealedProduct> = list.filter { !(it.isPrecon && !it.preconFile.isNullOrEmpty()) && it.count > 0 }

/** The deck's name when a precon is opened: the precon's own, without "Precon: ". */
fun preconDeckName(p: SealedProduct): String = p.name.replace(Regex("^precon:\\s*", RegexOption.IGNORE_CASE), "").trim().ifEmpty { p.name }

// ---- Sync: merging two devices' lists ----

private fun <T> pick(base: T, mine: T, theirs: T, minePreferred: Boolean): T = when {
    mine == theirs -> mine
    mine == base -> theirs
    theirs == base -> mine
    else -> if (minePreferred) mine else theirs
}

/**
 * Merges two devices' sealed lists: one added on either side is kept (the larger count if both added
 * it), one deleted on either stays deleted, counts add up (one opened here and one there is two
 * opened) and a product left with none goes; every other field goes to whoever changed it. The order
 * both last agreed on, then additions by id. Null when no side has the key.
 */
fun mergeSealed(base: List<SealedProduct>?, mine: List<SealedProduct>?, theirs: List<SealedProduct>?, minePreferred: Boolean): List<SealedProduct>? {
    if (base == null && mine == null && theirs == null) return null
    val b = base.orEmpty().associateBy { it.id }
    val m = mine.orEmpty().associateBy { it.id }
    val t = theirs.orEmpty().associateBy { it.id }
    val added = (t.keys + m.keys).filter { it !in b }.distinct().sorted()
    val out = mutableListOf<SealedProduct>()
    for (id in b.keys.toList() + added) {
        val bp = b[id]
        val mp = m[id]
        val tp = t[id]
        if (bp != null && (mp == null || tp == null)) continue
        if (bp == null) {
            out += if (mp != null && tp != null) tp.copy(count = maxOf(mp.count, tp.count)) else (tp ?: mp!!)
            continue
        }
        val count = tp!!.count + (mp!!.count - bp.count)
        if (count <= 0) continue
        val value = pick(bp.valueUsd to bp.valueAt, mp.valueUsd to mp.valueAt, tp.valueUsd to tp.valueAt, minePreferred)
        out += sealedProduct(
            SealedProduct(
                id = id,
                name = pick(bp.name, mp.name, tp.name, minePreferred),
                kind = pick(bp.kind, mp.kind, tp.kind, minePreferred),
                setCode = pick(bp.setCode, mp.setCode, tp.setCode, minePreferred),
                preconFile = pick(bp.preconFile, mp.preconFile, tp.preconFile, minePreferred),
                count = count,
                placeId = pick(bp.placeId, mp.placeId, tp.placeId, minePreferred),
                paidUsd = pick(bp.paidUsd, mp.paidUsd, tp.paidUsd, minePreferred),
                valueUsd = value.first,
                valueAt = value.second,
                createdAt = minOf(mp.createdAt, tp.createdAt)
            )
        )
    }
    return out
}

/**
 * [theirs] with [source]'s sealed list, when [theirs] is the Unsorted pile saved by an app that
 * doesn't know about sealed product (no "sealed" key) — the same object otherwise.
 */
fun keepSealedFromOlderApp(source: Collection, theirs: Collection): Collection {
    if (theirs.sealed != null || source.sealed == null || !theirs.isUnsorted) return theirs
    return theirs.copy(sealed = source.sealed)
}
