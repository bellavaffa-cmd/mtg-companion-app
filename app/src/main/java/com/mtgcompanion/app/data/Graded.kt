package com.mtgcompanion.app.data

import kotlin.math.roundToLong

/*
 * Graded cards: a copy sent to PSA, BGS, CGC or another grader and back in a slab. A graded copy is
 * kept apart from the raw copies — marking one takes it out of its binder's counts — so it never fills
 * a deck slot, counts as a spare or turns up on a pull list. Its value is what the user enters, since
 * card prices are for ungraded copies. It has a place like any copy, and shows on the card's Where it
 * is and in Value by place with a "Graded" label.
 *
 * Where they're kept: the Unsorted pile's "graded" (Collection.graded, JSON in CollectionModels.kt), so
 * they sync like the loans and the sealed product. Two devices' slabs merge slab by slab (mergeGraded):
 * one added on either side is kept, one taken off on either side stays off, each field goes to whoever
 * changed it. A pile saved by an app from before graded cards keeps this device's (keepGradedFromOlderApp).
 *
 * Pure, so it can be tested. Mirrors the web app's src/collection/graded.ts rule for rule, with the
 * same tests (GradedTest.kt ↔ tests/collection/graded.test.ts).
 */

/** A slab as both apps write it: optional fields null (left out) when not said, money to the cent. */
fun gradedCard(g: GradedCard): GradedCard {
    val company = GradingCompany.fromName(g.company)
    val value = g.valueUsd?.takeIf { !it.isNaN() && !it.isInfinite() && it >= 0 }?.let { (it * 100).roundToLong() / 100.0 }
    val placeId = g.placeId?.takeIf { it.isNotEmpty() }
    return GradedCard(
        id = g.id,
        scryfallId = g.scryfallId,
        name = g.name,
        imageUrl = g.imageUrl?.takeIf { it.isNotEmpty() },
        foil = if (g.isFoil) true else null,
        company = company.name,
        companyName = if (company == GradingCompany.OTHER) g.companyName?.trim()?.takeIf { it.isNotEmpty() } else null,
        grade = g.grade.trim(),
        cert = g.cert?.trim()?.takeIf { it.isNotEmpty() },
        valueUsd = value,
        placeId = placeId,
        section = if (placeId != null) g.section?.takeIf { it.isNotEmpty() } else null,
        collectionId = g.collectionId?.takeIf { it.isNotEmpty() },
        createdAt = g.createdAt
    )
}

/** The user's graded copies, kept on the Unsorted pile. */
fun gradedOf(collections: List<Collection>): List<GradedCard> = collections.firstOrNull { it.isUnsorted }?.graded.orEmpty()

/** [collections] with the graded copies set to [list] (on the Unsorted pile, made if it isn't there). */
fun withGraded(collections: List<Collection>, list: List<GradedCard>): List<Collection> =
    withUnsortedPile(collections).map { if (it.isUnsorted) it.copy(graded = list.map { g -> gradedCard(g) }) else it }

/** Who graded it, as a label says it: "PSA", or the name given for Other. */
fun graderName(company: GradingCompany, companyName: String?): String =
    if (company == GradingCompany.OTHER) companyName?.trim()?.takeIf { it.isNotEmpty() } ?: "Other" else company.label

/** The slab short: "PSA 10", "BGS 9.5". */
fun gradeLabel(g: GradedCard): String = listOf(graderName(g.grader, g.companyName), g.grade.trim()).filter { it.isNotEmpty() }.joinToString(" ")

/** A raw copy that could be the one graded: in a place (its [line]) or with none, in a binder. */
data class RawSource(
    val key: String,
    val label: String,
    val collectionId: String,
    val scryfallId: String,
    val name: String,
    val imageUrl: String?,
    val foil: Boolean,
    /** The place line it's in; null: one of the copies with no place. */
    val line: CopyPlace?,
    val qty: Int
)

/** The raw copies of the card called [name] (any printing) that could be marked graded, a line per spot and finish. */
fun rawSources(collections: List<Collection>, name: String): List<RawSource> {
    val places = placesOf(collections)
    val out = mutableListOf<RawSource>()
    for (c in collections) {
        if (c.kind == CollectionType.WISHLIST) continue
        for (e in c.entries) {
            if (!sameCardName(e.name, name) || e.quantity + e.foilQuantity <= 0) continue
            for (line in placedCopies(e)) {
                val place = places.firstOrNull { it.id == line.placeId } ?: continue
                out += RawSource(
                    "${c.id}|${e.scryfallId}|${line.placeId}|${if (line.isFoil) "f" else ""}|${line.section.orEmpty()}|${line.page ?: ""}|${line.slot ?: ""}",
                    listOf(if (line.section != null) "${place.name} › ${line.section}" else place.name, if (line.isFoil) "foil" else "").filter { it.isNotEmpty() }.joinToString(" · "),
                    c.id, e.scryfallId, e.name, e.imageUrl, line.isFoil, line, line.qty
                )
            }
            val (plain, foils) = unplacedCopies(e)
            for (foil in listOf(false, true)) {
                val n = if (foil) foils else plain
                if (n <= 0) continue
                out += RawSource(
                    "${c.id}|${e.scryfallId}|none|${if (foil) "f" else ""}",
                    listOf("No place yet (${c.name})", if (foil) "foil" else "").filter { it.isNotEmpty() }.joinToString(" · "),
                    c.id, e.scryfallId, e.name, e.imageUrl, foil, null, n
                )
            }
        }
    }
    return out
}

private fun sameSpotLine(a: CopyPlace, b: CopyPlace) =
    a.placeId == b.placeId && a.isFoil == b.isFoil && a.section.orEmpty() == b.section.orEmpty() && (a.page ?: 0) == (b.page ?: 0) && (a.slot ?: 0) == (b.slot ?: 0)

/** [entry] with one copy fewer — from [line] when given, else (foil if [foil]) one with no place first. Null once none are left. */
private fun oneCopyFewer(entry: CollectionEntry, line: CopyPlace?, foil: Boolean): CollectionEntry? {
    val plain = maxOf(0, entry.quantity - if (foil) 0 else 1)
    val foils = maxOf(0, entry.foilQuantity - if (foil) 1 else 0)
    if (plain + foils <= 0) return null
    val places = if (line != null) {
        var taken = false
        placedCopies(entry).map { p ->
            if (taken || !sameSpotLine(p, line)) p else { taken = true; p.copy(qty = p.qty - 1) }
        }
    } else {
        splitPlaces(entry, if (foil) 0 else 1, if (foil) 1 else 0).first
    }
    val left = plain + foils
    val out = entry.copy(
        quantity = plain,
        foilQuantity = foils,
        forTrade = entry.forTrade?.let { minOf(it, left) },
        forSale = entry.forSale?.let { minOf(it, left) }
    )
    return if (entry.places != null) withPlaces(out, places) else out
}

/**
 * Marks a copy graded: [card] joins the graded copies and, when it was one of the raw copies ([from]),
 * that copy leaves its binder — so it no longer fills a deck slot or counts as a spare. The binder
 * entry goes once it has no copies left.
 */
fun markGraded(collections: List<Collection>, from: RawSource?, card: GradedCard): List<Collection> {
    val next = if (from == null) collections else collections.map { c ->
        if (c.id != from.collectionId) c
        else c.copy(entries = c.entries.mapNotNull { e -> if (e.scryfallId != from.scryfallId) e else oneCopyFewer(e, from.line, from.foil) })
    }
    val g = if (from != null) card.copy(collectionId = from.collectionId, foil = if (from.foil) true else null) else card
    return withGraded(next, gradedOf(next) + g)
}

/** [collections] with the slab [g] changed (its grade, cert, value or place). */
fun saveGraded(collections: List<Collection>, g: GradedCard): List<Collection> {
    val list = gradedOf(collections)
    return withGraded(collections, if (list.any { it.id == g.id }) list.map { if (it.id == g.id) g else it } else list + g)
}

/** [collections] without the slab [id] — sold or gone. */
fun removeGraded(collections: List<Collection>, id: String): List<Collection> =
    withGraded(collections, gradedOf(collections).filter { it.id != id })

/**
 * Out of its slab: the copy [id] is a raw copy again, back in the binder it came from (the Unsorted
 * pile when that's gone) and in the slab's place, so it fills deck slots and counts again.
 */
fun backToRaw(collections: List<Collection>, id: String): List<Collection> {
    val g = gradedOf(collections).firstOrNull { it.id == id } ?: return collections
    val rest = withGraded(collections, gradedOf(collections).filter { it.id != id })
    val places = placesOf(rest)
    val home = if (rest.any { it.id == g.collectionId && it.kind != CollectionType.WISHLIST }) g.collectionId!! else UNSORTED_COLLECTION_ID
    val spot = if (g.placeId != null && places.any { it.id == g.placeId }) copyPlace(Spot(g.placeId, g.section), 1, g.isFoil) else null
    return withUnsortedPile(rest).map { c ->
        if (c.id != home) return@map c
        val had = c.entries.firstOrNull { it.scryfallId == g.scryfallId }
        val base = had?.copy(quantity = had.quantity + if (g.isFoil) 0 else 1, foilQuantity = had.foilQuantity + if (g.isFoil) 1 else 0)
            ?: CollectionEntry(g.scryfallId, g.name, g.imageUrl, quantity = if (g.isFoil) 0 else 1, foilQuantity = if (g.isFoil) 1 else 0)
        val entry = if (spot != null) withPlaces(base, placedCopies(base) + spot) else base
        c.copy(entries = if (had != null) c.entries.map { if (it.scryfallId == g.scryfallId) entry else it } else c.entries + entry)
    }
}

/** One graded copy on a card's Where it is. [title]: "PSA 10"; [detail]: "Safe, study › Slabs · cert 12345678", "No place yet". */
data class GradedLine(val id: String, val title: String, val detail: String, val valueUsd: Double?, val placeId: String?)

/** Where a slab is, as its page says it: "Safe, study › Slabs" — the place's path, then its section. */
fun gradedWhereLabel(collections: List<Collection>, placeId: String?, section: String?): String {
    val places = placesOf(collections)
    val place = placeId?.let { id -> places.firstOrNull { it.id == id } } ?: return "No place yet"
    return (parentsOf(places, place.id) + place).joinToString(" › ") { it.name } + (section?.let { " › $it" } ?: "")
}

/** The graded copies of the card called [name] (any printing), for its Where it is. */
fun gradedWhere(collections: List<Collection>, name: String): List<GradedLine> {
    val places = placesOf(collections)
    return gradedOf(collections).filter { sameCardName(it.name, name) }.map { g ->
        val place = g.placeId?.let { id -> places.firstOrNull { it.id == id } }
        GradedLine(
            g.id, gradeLabel(g),
            listOf(gradedWhereLabel(collections, g.placeId, g.section), if (g.isFoil) "foil" else "", g.cert?.let { "cert $it" }.orEmpty()).filter { it.isNotEmpty() }.joinToString(" · "),
            g.valueUsd, place?.id
        )
    }
}

// ---- Sync: merging two devices' slabs ----

private fun <T> pick(base: T, mine: T, theirs: T, minePreferred: Boolean): T = when {
    mine == theirs -> mine
    mine == base -> theirs
    theirs == base -> mine
    else -> if (minePreferred) mine else theirs
}

/**
 * Merges two devices' graded copies: one added on either side is kept, one taken off on either stays
 * off, each field goes to whoever changed it. The order both last agreed on, then additions by id. Null
 * when no side has the key.
 */
fun mergeGraded(base: List<GradedCard>?, mine: List<GradedCard>?, theirs: List<GradedCard>?, minePreferred: Boolean): List<GradedCard>? {
    if (base == null && mine == null && theirs == null) return null
    val b = base.orEmpty().associateBy { it.id }
    val m = mine.orEmpty().associateBy { it.id }
    val t = theirs.orEmpty().associateBy { it.id }
    val added = (t.keys + m.keys).filter { it !in b }.distinct().sorted()
    val out = mutableListOf<GradedCard>()
    for (id in b.keys.toList() + added) {
        val bg = b[id]
        val mg = m[id]
        val tg = t[id]
        if (bg != null && (mg == null || tg == null)) continue
        if (bg == null) { out += (tg ?: mg!!); continue }
        fun <T> f(of: (GradedCard) -> T): T = pick(of(bg), of(mg!!), of(tg!!), minePreferred)
        val spot = f { it.placeId to it.section }
        out += gradedCard(
            GradedCard(
                id = id, scryfallId = f { it.scryfallId }, name = f { it.name }, imageUrl = f { it.imageUrl }, foil = f { it.foil },
                company = f { it.company }, companyName = f { it.companyName }, grade = f { it.grade }, cert = f { it.cert },
                valueUsd = f { it.valueUsd }, placeId = spot.first, section = spot.second, collectionId = f { it.collectionId },
                createdAt = minOf(mg!!.createdAt, tg!!.createdAt)
            )
        )
    }
    return out
}

/**
 * [theirs] with [source]'s graded copies, when [theirs] is the Unsorted pile saved by an app that
 * doesn't know about graded cards (no "graded" key) — the same object otherwise.
 */
fun keepGradedFromOlderApp(source: Collection, theirs: Collection): Collection {
    if (theirs.graded != null || source.graded == null || !theirs.isUnsorted) return theirs
    return theirs.copy(graded = source.graded)
}
