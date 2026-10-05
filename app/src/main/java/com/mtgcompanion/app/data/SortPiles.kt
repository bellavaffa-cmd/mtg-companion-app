package com.mtgcompanion.app.data

/*
 * Sorting a new pile with the scanner: up to six piles, each defined by a rule, checked in order —
 * "Rares and mythics worth over $2 → Rares binder", "Spares (more than 4 owned) → Trade binder",
 * "Wanted by a deck", and "Bulk: everything else → the box whose sorting rule fits". Each card scanned
 * gets the first pile whose rule fits it (Bulk only when no other does), shown big and coloured with
 * where that pile goes; the session tallies the piles, and "Done: file every pile" adds every card to
 * the collection at its pile's place (addedHere — or, for cards already owned, putAway — in
 * StoragePlaces.kt).
 *
 * The rules are kept on this device (SortSessionStore.kt), as is the session until it's filed.
 *
 * Pure, so it can be tested. Mirrors the web app's src/collection/sortPiles.ts rule for rule, with the
 * same tests (SortPilesTest.kt ↔ tests/collection/sortPiles.test.ts).
 */

enum class PileKind { VALUE, PRICE, SPARES, WANTED, BULK;
    companion object {
        fun fromName(name: String?): PileKind = entries.firstOrNull { it.name == name } ?: BULK
    }
}

/** A pile's destination meaning "the box whose sorting rule fits the card". */
const val BY_RULE = "RULE"
const val MAX_PILES = 6
const val MIN_PILES = 1

/**
 * One pile: what goes in it ([kind], a [PileKind] name) and where it goes. [over]: VALUE and PRICE —
 * worth more than this, in US dollars. [keep]: SPARES — copies owned beyond this many. [to]: a place's
 * id, [BY_RULE], or null: no place (the Unsorted pile). The web app's PileRule, field for field.
 */
data class PileRule(val kind: String, val over: Double? = null, val keep: Int? = null, val to: String? = null) {
    val pileKind: PileKind get() = PileKind.fromName(kind)
}

/** The four piles a first sort starts with: rares to a binder, bulk by the boxes' rules, spares to a trade binder, wanted by a deck. */
fun defaultPiles(collections: List<Collection>): List<PileRule> {
    val binders = placeTree(placesOf(collections)).map { it.place }.filter { it.placeKind == PlaceKind.BINDER }
    val trade = binders.firstOrNull { Regex("trade", RegexOption.IGNORE_CASE).containsMatchIn(it.name) }
    val rares = binders.firstOrNull { it != trade && Regex("rare|mythic|value|good", RegexOption.IGNORE_CASE).containsMatchIn(it.name) }
        ?: binders.firstOrNull { it != trade }
    return listOf(
        PileRule(PileKind.VALUE.name, over = 2.0, to = rares?.id),
        PileRule(PileKind.BULK.name, to = BY_RULE),
        PileRule(PileKind.SPARES.name, keep = 4, to = trade?.id),
        PileRule(PileKind.WANTED.name)
    )
}

/** A pile as both apps keep it: only the fields its kind uses. */
fun pileRule(r: PileRule): PileRule {
    val kind = r.pileKind
    return PileRule(
        kind.name,
        over = if (kind == PileKind.VALUE || kind == PileKind.PRICE) maxOf(0.0, r.over ?: 0.0) else null,
        keep = if (kind == PileKind.SPARES) maxOf(0, r.keep ?: 4) else null,
        to = r.to?.takeIf { it.isNotEmpty() }
    )
}

/** "Rares and mythics over $2", "Spares over 4", "Wanted by a deck", "Bulk: everything else". [fmt] writes a dollar amount. */
fun pileTitle(r: PileRule, fmt: (Double) -> String): String = when (r.pileKind) {
    PileKind.VALUE -> "Rares and mythics over ${fmt(r.over ?: 0.0)}"
    PileKind.PRICE -> "Any card over ${fmt(r.over ?: 0.0)}"
    PileKind.SPARES -> "Spares over ${r.keep ?: 4}"
    PileKind.WANTED -> "Wanted by a deck"
    PileKind.BULK -> "Bulk: everything else"
}

/** Where a pile goes, in words: a place's name, "The box whose rule fits", or "No place (Unsorted)". */
fun pileGoesTo(r: PileRule, collections: List<Collection>): String {
    if (r.to == BY_RULE) return "The box whose rule fits"
    return r.to?.let { id -> placesOf(collections).firstOrNull { it.id == id } }?.name ?: "No place (Unsorted)"
}

// ---- What each card is ----

/**
 * What a pile's rule needs to know of a scanned card: its [rarity] as Scryfall says ("common",
 * "rare", "mythic"…), its price in US dollars when known, the copies [owned] before this one (the
 * collection's and the ones scanned before it this session), and the decks that still want it.
 */
data class SortFacts(val name: String, val rarity: String? = null, val usd: Double? = null, val owned: Int = 0, val wantedBy: List<String> = emptyList())

/** The pile a card goes in: its index, why in a few words, and the decks wanting it (the Wanted pile). */
data class PileChoice(val index: Int, val why: String, val decks: List<String>)

/** The pile a card goes in; null when no rule takes it. */
fun pileFor(rules: List<PileRule>, f: SortFacts): PileChoice? {
    rules.forEachIndexed { i, r ->
        val usd = f.usd
        when (r.pileKind) {
            PileKind.VALUE -> if ((f.rarity == "rare" || f.rarity == "mythic") && usd != null && usd > (r.over ?: 0.0)) return PileChoice(i, "Worth keeping safe", emptyList())
            PileKind.PRICE -> if (usd != null && usd > (r.over ?: 0.0)) return PileChoice(i, "Worth keeping safe", emptyList())
            PileKind.SPARES -> if (f.owned >= (r.keep ?: 4)) return PileChoice(i, "You have ${f.owned} already", emptyList())
            PileKind.WANTED -> if (f.wantedBy.isNotEmpty()) return PileChoice(i, "For ${f.wantedBy.joinToString(", ")}", f.wantedBy)
            PileKind.BULK -> Unit
        }
    }
    val bulk = rules.indexOfFirst { it.pileKind == PileKind.BULK }
    return if (bulk >= 0) PileChoice(bulk, "Bulk", emptyList()) else null
}

private fun key(name: String) = name.trim().lowercase().split(" // ")[0].trim()

/** Copies of each card owned (by name): the binders' and the Unsorted pile's, and physical decks' real ones. */
fun ownedCounts(collections: List<Collection>, decks: List<Deck>): Map<String, Int> {
    val out = LinkedHashMap<String, Int>()
    for (c in collections) {
        if (c.kind == CollectionType.WISHLIST) continue
        for (e in c.entries) out[key(e.name)] = (out[key(e.name)] ?: 0) + e.quantity + e.foilQuantity
    }
    for (d in decks) for (e in realCopiesOf(d)) out[key(e.name)] = (out[key(e.name)] ?: 0) + e.quantity
    return out
}

/** Cards decks want: the decks, and how many copies they want between them. */
data class Wanted(val decks: List<String>, val qty: Int)

/** Cards decks want, by name: missing from a deck, or on its Considering list. */
fun wantedByDecks(collections: List<Collection>, decks: List<Deck>): Map<String, Wanted> {
    val out = LinkedHashMap<String, Wanted>()
    fun want(name: String, deck: String, qty: Int) {
        val w = out[key(name)] ?: Wanted(emptyList(), 0)
        out[key(name)] = Wanted(if (deck in w.decks) w.decks else w.decks + deck, w.qty + qty)
    }
    for (d in decks) {
        for (m in missingCards(d, collections, decks)) want(m.entry.name, d.name, m.need)
        for (e in d.considering) want(e.name, d.name, 1)
    }
    return out
}

// ---- A session ----

/** One card scanned while sorting. [entry]: the card as a new binder entry with no copies yet; [pile]: its pile's index (-1: none). */
data class SortScan(
    val id: Long,
    val scryfallId: String,
    val name: String,
    val rarity: String? = null,
    val usd: Double? = null,
    val facts: CardFacts,
    val entry: CollectionEntry,
    val pile: Int,
    val why: String = "",
    /** The decks wanting it, when it's in the Wanted pile. */
    val decks: List<String>? = null
)

/**
 * A sort under way: the pile's [source] ("Booster box, Duskmourn"), its rules and its scans, newest
 * last. [newCards]: the cards are new to the collection (a booster box) — or already in it.
 */
data class SortSession(val source: String = "", val rules: List<PileRule>, val newCards: Boolean = true, val scans: List<SortScan> = emptyList())

/**
 * The pile for one more card of [session], given the collection's [owned] counts and what decks
 * [wanted] (see ownedCounts, wantedByDecks): copies scanned earlier this session count as owned
 * when the cards are new, and copies already in the Wanted pile come off what decks want.
 */
fun nextPile(session: SortSession, name: String, rarity: String?, usd: Double?, owned: Map<String, Int>, wanted: Map<String, Wanted>): PileChoice? {
    val before = session.scans.filter { sameCardName(it.name, name) }
    val w = wanted[key(name)]
    val takenForDecks = before.count { session.rules.getOrNull(it.pile)?.pileKind == PileKind.WANTED }
    return pileFor(
        session.rules,
        SortFacts(
            name, rarity, usd,
            owned = (owned[key(name)] ?: 0) + if (session.newCards) before.size else 0,
            wantedBy = if (w != null && w.qty > takenForDecks) w.decks else emptyList()
        )
    )
}

/** Each pile's count, value (US dollars) and the decks its cards are for. */
data class PileTally(val index: Int, val cards: Int, val usd: Double, val decks: List<String>)

fun pileTallies(session: SortSession): List<PileTally> = session.rules.mapIndexed { index, r ->
    val scans = session.scans.filter { it.pile == index }
    val decks = if (r.pileKind == PileKind.WANTED) scans.flatMap { it.decks.orEmpty() }.distinct() else emptyList()
    PileTally(index, scans.size, scans.sumOf { it.usd ?: 0.0 }, decks)
}

/** Where a card in pile [rule] goes: a spot in a place, or null — no place. And that in words. */
data class PileDestination(val spot: Spot?, val label: String)

fun pileDestination(rule: PileRule?, facts: CardFacts, collections: List<Collection>): PileDestination {
    val places = placesOf(collections)
    if (rule?.to == BY_RULE) {
        val (spot, place) = bestPlaceByRule(places, facts, collections) ?: return PileDestination(null, "No place yet")
        return PileDestination(spot, listOfNotNull(placePath(places, place.id), spot.section).joinToString(" › "))
    }
    val place = rule?.to?.let { id -> places.firstOrNull { it.id == id } } ?: return PileDestination(null, "No place yet")
    val spot = suggestSpot(place, facts, collections).first
    return PileDestination(spot, listOfNotNull(placePath(places, place.id), spot.section).joinToString(" › "))
}

/** One card filed: the step that put it in a place (null when it has none), and where, in words. */
data class FiledStep(val scan: SortScan, val step: PutAwayStep?, val to: String)

/** What filing did: the collection after, a step per card, and how many cards were added. */
data class FiledPiles(val collections: List<Collection>, val steps: List<FiledStep>, val added: Int)

/**
 * "Done: file every pile": every card scanned goes into the collection at its pile's place. New cards
 * are added to the Unsorted pile (kept at that place, or with no place); cards already owned are put
 * away there as the put-away scanner does (a copy with no place first, then one from another place).
 */
fun fileEveryPile(collections: List<Collection>, session: SortSession): FiledPiles {
    var out = collections
    val steps = mutableListOf<FiledStep>()
    var added = 0
    for (scan in session.scans) {
        val (spot, label) = pileDestination(session.rules.getOrNull(scan.pile), scan.facts, out)
        if (session.newCards) {
            if (spot != null) {
                val (next, step) = addedHere(out, scan.scryfallId, spot, scan.entry)
                out = next
                steps += FiledStep(scan, step, label)
            } else {
                out = withUnsortedPile(out).map { c ->
                    if (!c.isUnsorted) return@map c
                    val had = c.entries.firstOrNull { it.scryfallId == scan.scryfallId }
                    c.copy(
                        entries = if (had != null) c.entries.map { if (it === had) it.copy(quantity = it.quantity + 1) else it }
                        else c.entries + scan.entry.copy(scryfallId = scan.scryfallId, quantity = 1, foilQuantity = 0)
                    )
                }
                steps += FiledStep(scan, null, label)
            }
            added++
        } else if (spot != null) {
            val r = putAway(out, scan.scryfallId, scan.name, spot, scan.entry)
            out = r.collections
            if (r.result == PutAwayResult.NEW) added++
            steps += FiledStep(scan, r.step, label)
        }
    }
    return FiledPiles(out, steps, added)
}
