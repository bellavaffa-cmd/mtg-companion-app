package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.scryfall.ScryfallCard

/*
 * Storage places: the boxes, binders and shelves the cards physically sit in, and which copies are
 * where. Places nest ("Shelf, study › Red box"); a binder has pages of pockets, a box has sections
 * and may have a sorting rule that suggests where a new card goes ("Red › around “L”").
 *
 * Where things are kept, as JSON (see CollectionModels.kt):
 *  - The places themselves: the Unsorted pile's "storagePlaces" — the pile is always there and has
 *    the same id on every device, so they sync with the library like any binder does.
 *  - Which copies are where: each binder entry's "places", [{placeId, qty, foil?, section?, page?,
 *    slot?}]. Never more than the entry's copies, plain and foil apart; the rest have no place yet.
 *  - Physical decks and copies out on loan count as places of their own, read from the decks and the
 *    loans (the Unsorted pile's "loans", see Loans.kt) rather than stored — and, until they're turned
 *    into loans, copies tagged "lent to …".
 *
 * Pure, so it can be tested. Mirrors the web app's src/collection/storagePlaces.ts rule for rule,
 * with the same tests (StoragePlacesTest.kt ↔ tests/collection/storagePlaces.test.ts).
 */

/** A binder's pockets per page unless it says otherwise. */
const val DEFAULT_POCKETS = 9
val COLOUR_SECTIONS = listOf("White", "Blue", "Black", "Red", "Green", "Multicolour", "Colourless", "Lands")
val TYPE_SECTIONS = listOf("Creatures", "Planeswalkers", "Battles", "Instants", "Sorceries", "Artifacts", "Enchantments", "Lands", "Other")

/** The sections a new box sorted by [rule] starts with. */
fun defaultSections(rule: SortRule?): List<String> = when (rule) {
    SortRule.COLOUR -> COLOUR_SECTIONS
    SortRule.TYPE -> TYPE_SECTIONS
    else -> emptyList()
}

val StoragePlace.pockets: Int get() = pocketsPerPage?.takeIf { it > 0 } ?: DEFAULT_POCKETS

// ---- The places ----

/** The user's places, kept on the Unsorted pile. */
fun placesOf(collections: List<Collection>): List<StoragePlace> =
    collections.firstOrNull { it.isUnsorted }?.storagePlaces ?: emptyList()

/** [collections] with the places set to [places] (on the Unsorted pile, made if it isn't there). */
fun withPlaceList(collections: List<Collection>, places: List<StoragePlace>): List<Collection> =
    withUnsortedPile(collections).map { if (it.isUnsorted) it.copy(storagePlaces = places) else it }

/** A place written as both apps write it: optional fields null when not set. */
fun storagePlace(p: StoragePlace): StoragePlace = p.copy(
    kind = PlaceKind.fromName(p.kind).name,
    parentId = p.parentId?.takeIf { it.isNotEmpty() },
    note = p.note?.trim()?.takeIf { it.isNotEmpty() },
    sections = p.sections?.takeIf { it.isNotEmpty() },
    pocketsPerPage = p.pocketsPerPage?.takeIf { it > 0 },
    sortRule = SortRule.fromName(p.sortRule)?.name,
    lastChecked = p.lastChecked?.takeIf { it > 0 },
    // A size taken off stays as 0, so an older app's save can be told from it (keepPlaceSizes).
    capacity = p.capacity?.coerceAtLeast(0),
    pages = p.pages?.coerceAtLeast(0)
)

/** [collections] with [place] added, or put in place of the one with its id. */
fun savePlace(collections: List<Collection>, place: StoragePlace): List<Collection> {
    val places = placesOf(collections)
    val clean = storagePlace(place)
    val next = if (places.any { it.id == place.id }) places.map { if (it.id == place.id) clean else it } else places + clean
    return withPlaceList(collections, next)
}

/** Whether [id]'s chain of parents comes back round to it (two devices each moved one into the other). */
private fun inLoop(byId: Map<String, StoragePlace>, id: String): Boolean {
    val seen = HashSet<String>()
    var p = byId[id]?.parentId
    while (p != null && byId.containsKey(p)) {
        if (p == id) return true
        if (!seen.add(p)) return false
        p = byId[p]?.parentId
    }
    return false
}

/** The place [id] sits in, or null at the top — also for a parent that's gone, or a loop. */
fun parentOf(places: List<StoragePlace>, id: String): String? = parentIn(places.associateBy { it.id }, id)

/** [parentOf] with the places by id made once, for callers asking about many places. */
private fun parentIn(byId: Map<String, StoragePlace>, id: String): String? {
    val parent = byId[id]?.parentId
    return if (parent != null && byId.containsKey(parent) && !inLoop(byId, id)) parent else null
}

private val byAge = compareBy<StoragePlace>({ it.createdAt }, { it.id })

data class PlaceNode(val place: StoragePlace, val depth: Int)

/** Every place, each followed by the places inside it, oldest first at each level. */
fun placeTree(places: List<StoragePlace>): List<PlaceNode> {
    val byId = places.associateBy { it.id }
    val kids = places.groupBy { parentIn(byId, it.id) }
    val out = mutableListOf<PlaceNode>()
    fun walk(parent: String?, depth: Int) {
        kids[parent].orEmpty().sortedWith(byAge).forEach {
            out += PlaceNode(it, depth)
            walk(it.id, depth + 1)
        }
    }
    walk(null, 0)
    return out
}

/** The places directly inside [id] (null: the top level), oldest first. */
fun childrenOf(places: List<StoragePlace>, id: String?): List<StoragePlace> {
    val byId = places.associateBy { it.id }
    return places.filter { parentIn(byId, it.id) == id }.sortedWith(byAge)
}

/** [id] and every place inside it, however deep. */
fun placeAndInside(places: List<StoragePlace>, id: String): Set<String> {
    val out = linkedSetOf(id)
    val byId = places.associateBy { it.id }
    var grew = true
    while (grew) {
        grew = false
        for (p in places) {
            val parent = parentIn(byId, p.id)
            if (parent != null && parent in out && p.id !in out) { out += p.id; grew = true }
        }
    }
    return out
}

/** The places [id] sits in, outermost first. */
fun parentsOf(places: List<StoragePlace>, id: String): List<StoragePlace> {
    val out = mutableListOf<StoragePlace>()
    val byId = places.associateBy { it.id }
    var p = parentIn(byId, id)
    while (p != null) {
        val place = byId[p] ?: break
        if (place in out) break
        out.add(0, place)
        p = parentIn(byId, p)
    }
    return out
}

/**
 * For each place, its id after the ids of the places it sits in, outermost first — what [parentsOf]
 * answers, for every place at once (the Advanced filters ask about each copy's places).
 */
fun placeChains(places: List<StoragePlace>): Map<String, List<String>> =
    places.associate { p -> p.id to parentsOf(places, p.id).map { it.id } + p.id }

/** "Shelf, study › Red box". */
fun placePath(places: List<StoragePlace>, id: String): String =
    (parentsOf(places, id) + listOfNotNull(places.firstOrNull { it.id == id })).joinToString(" › ") { it.name }

/** Whether [id] can go inside [parentId] (null: the top level) — not inside itself or a place within it. */
fun canMoveInto(places: List<StoragePlace>, id: String, parentId: String?): Boolean =
    parentId == null || (places.any { it.id == parentId } && parentId !in placeAndInside(places, id))

/** "Bulk · by colour, then A–Z", "Binder · 9 per page". */
fun placeSubtitle(place: StoragePlace): String {
    val parts = mutableListOf(place.note?.trim()?.takeIf { it.isNotEmpty() } ?: place.placeKind.label)
    if (place.placeKind == PlaceKind.BINDER) parts += "${place.pockets} per page"
    place.rule?.let { parts += it.short }
    return parts.joinToString(" · ")
}

/**
 * [collections] without the place [id]: the places inside it move up to where it was, and its
 * copies have no place any more.
 */
fun deletePlace(collections: List<Collection>, id: String): List<Collection> {
    val places = placesOf(collections)
    if (places.none { it.id == id }) return collections
    val up = parentOf(places, id)
    val next = places.filter { it.id != id }.map { if (it.parentId == id) it.copy(parentId = up) else it }
    val emptied = collections.map { c ->
        if (c.entries.none { e -> e.places.orEmpty().any { it.placeId == id } }) c
        else c.copy(entries = c.entries.map { e ->
            if (e.places.orEmpty().any { it.placeId == id }) withPlaces(e, e.places.orEmpty().filter { it.placeId != id }) else e
        })
    }
    return withPlaceList(emptied, next)
}

// ---- Copies and their places ----

/** Where in a place: a box's section, or a binder's page and pocket. */
data class Spot(val placeId: String, val section: String? = null, val page: Int? = null, val slot: Int? = null)

val CopyPlace.spot: Spot get() = Spot(placeId, section, page, slot)

private fun sameSpot(a: Spot, b: Spot) =
    a.placeId == b.placeId && (a.section ?: "") == (b.section ?: "") && (a.page ?: 0) == (b.page ?: 0) && (a.slot ?: 0) == (b.slot ?: 0)
private fun sameLine(a: CopyPlace, b: CopyPlace) = sameSpot(a.spot, b.spot) && a.isFoil == b.isFoil

/** What tells two lines apart: the place, the spot in it and the finish. */
fun copyKey(c: CopyPlace): String =
    listOf(c.placeId, if (c.isFoil) "foil" else "", c.section ?: "", c.page?.toString() ?: "", c.slot?.toString() ?: "").joinToString("|")

/** A line written as both apps write it: optional fields null when not said. */
fun copyPlace(spot: Spot, qty: Int, foil: Boolean): CopyPlace = CopyPlace(
    placeId = spot.placeId,
    qty = qty,
    foil = if (foil) true else null,
    section = spot.section?.takeIf { it.isNotEmpty() },
    page = spot.page?.takeIf { it > 0 },
    slot = spot.slot?.takeIf { it > 0 }
)

/**
 * [places] as kept: one line per spot and finish, none at zero, and no more than the entry's copies —
 * plain and foil apart, the first lines keeping theirs.
 */
fun tidyPlaces(quantity: Int, foilQuantity: Int, places: List<CopyPlace>): List<CopyPlace> {
    // Nearly always already tidy: then it's the same list, rather than a copy of every line each time
    // a screen asks (thousands of entries, many times over).
    if (isTidy(quantity, foilQuantity, places)) return places
    val merged = mutableListOf<CopyPlace>()
    for (p in places) {
        if (p.qty <= 0) continue
        val i = merged.indexOfFirst { sameLine(it, p) }
        if (i >= 0) merged[i] = merged[i].copy(qty = merged[i].qty + p.qty)
        else merged += copyPlace(p.spot, p.qty, p.isFoil)
    }
    var plain = quantity.coerceAtLeast(0)
    var foil = foilQuantity.coerceAtLeast(0)
    val out = mutableListOf<CopyPlace>()
    for (p in merged) {
        val take = minOf(p.qty, if (p.isFoil) foil else plain)
        if (take <= 0) continue
        out += if (take == p.qty) p else p.copy(qty = take)
        if (p.isFoil) foil -= take else plain -= take
    }
    return out
}

/** A line as copyPlace writes it: optional fields null rather than false, empty or 0. */
private fun isWritten(p: CopyPlace): Boolean =
    (p.foil == null || p.foil == true) && (p.section == null || p.section.isNotEmpty()) &&
        (p.page == null || p.page > 0) && (p.slot == null || p.slot > 0)

/** Whether [places] is already as tidyPlaces leaves it. */
private fun isTidy(quantity: Int, foilQuantity: Int, places: List<CopyPlace>): Boolean {
    var plain = quantity.coerceAtLeast(0)
    var foil = foilQuantity.coerceAtLeast(0)
    for (i in places.indices) {
        val p = places[i]
        if (p.qty <= 0 || !isWritten(p)) return false
        for (j in 0 until i) if (sameLine(places[j], p)) return false
        if (p.isFoil) foil -= p.qty else plain -= p.qty
        if (plain < 0 || foil < 0) return false
    }
    return true
}

/** The entry's copies that have a place, as they stand (see tidyPlaces). */
fun placedCopies(entry: CollectionEntry): List<CopyPlace> = tidyPlaces(entry.quantity, entry.foilQuantity, entry.places.orEmpty())

/** How many of the entry's copies have no place: plain to foil. */
fun unplacedCopies(entry: CollectionEntry): Pair<Int, Int> {
    val placed = placedCopies(entry)
    return (entry.quantity - placed.filter { !it.isFoil }.sumOf { it.qty }) to (entry.foilQuantity - placed.filter { it.isFoil }.sumOf { it.qty })
}

/**
 * [entry] with its places set to [places], tidied. Once an entry has had places it keeps the key, as
 * an empty list when none are left — see CollectionEntry.places.
 */
fun withPlaces(entry: CollectionEntry, places: List<CopyPlace>): CollectionEntry {
    val tidy = tidyPlaces(entry.quantity, entry.foilQuantity, places)
    if (tidy.isEmpty() && entry.places == null) return entry
    if (tidy === entry.places) return entry
    return entry.copy(places = tidy)
}

/** [entry] with its places tidied to its copies (after its counts changed). */
fun tidied(entry: CollectionEntry): CollectionEntry = entry.places?.let { withPlaces(entry, it) } ?: entry

/** Gives up to [count] of the entry's copies with no place — foil ones if [foil] — the spot [to]. Also how many. */
fun placeCopies(entry: CollectionEntry, to: Spot, count: Int, foil: Boolean): Pair<CollectionEntry, Int> {
    val (plain, foils) = unplacedCopies(entry)
    val n = minOf(count, if (foil) foils else plain)
    if (n <= 0) return entry to 0
    return withPlaces(entry, placedCopies(entry) + copyPlace(to, n, foil)) to n
}

/**
 * Moves up to [count] copies from the line [from] — or, when null, from the copies with no place
 * (foil ones if [foil]) — to the spot [to], or to no place when null. Also how many.
 */
fun moveCopies(entry: CollectionEntry, from: CopyPlace?, to: Spot?, count: Int, foil: Boolean = false): Pair<CollectionEntry, Int> {
    if (from == null) return if (to != null) placeCopies(entry, to, count, foil) else entry to 0
    val placed = placedCopies(entry)
    val i = placed.indexOfFirst { sameLine(it, from) }
    if (i < 0) return entry to 0
    val n = minOf(count, placed[i].qty)
    if (n <= 0) return entry to 0
    val rest = placed.mapIndexed { j, p -> if (j == i) p.copy(qty = p.qty - n) else p }
    return withPlaces(entry, if (to != null) rest + copyPlace(to, n, from.isFoil) else rest) to n
}

/**
 * When [plain] and [foil] copies of [entry] go to another binder in the app: the places that stay and
 * the ones that go with them. Copies with no place go first, then the last lines'.
 */
fun splitPlaces(entry: CollectionEntry, plain: Int, foil: Int): Pair<List<CopyPlace>, List<CopyPlace>> {
    val placed = placedCopies(entry)
    val (freePlain, freeFoil) = unplacedCopies(entry)
    var plainLeft = (plain - freePlain).coerceAtLeast(0)
    var foilLeft = (foil - freeFoil).coerceAtLeast(0)
    val staying = placed.toMutableList()
    val going = mutableListOf<CopyPlace>()
    for (i in staying.indices.reversed()) {
        val p = staying[i]
        val take = minOf(if (p.isFoil) foilLeft else plainLeft, p.qty)
        if (take <= 0) continue
        staying[i] = p.copy(qty = p.qty - take)
        going.add(0, copyPlace(p.spot, take, p.isFoil))
        if (p.isFoil) foilLeft -= take else plainLeft -= take
    }
    return staying.filter { it.qty > 0 } to going
}

// ---- Copies out on loan ----

private val LENT = Regex("^lent\\b", RegexOption.IGNORE_CASE)

/** A tag of the user's that says the copies are out on loan: "lent", "lent to Sam". */
fun lentTag(entry: CollectionEntry): String? = entry.userTags.firstOrNull { LENT.containsMatchIn(it.trim()) }

private fun owned(collections: List<Collection>) = collections.filter { it.kind != CollectionType.WISHLIST }

/** The user's loans, kept on the Unsorted pile (see Loans.kt). */
fun loansOf(collections: List<Collection>): List<Loan> = collections.firstOrNull { it.isUnsorted }?.loans.orEmpty()

/** Copies of a loan's card not back yet. */
fun stillOut(card: LoanCard): Int = maxOf(0, card.qty - maxOf(0, card.back ?: 0))

/** Whether some of a loan's cards are still out. */
fun isOpen(loan: Loan): Boolean = loan.cards.any { stillOut(it) > 0 }

/**
 * Copies of one loan's card that count as lent out now: [qty] of them, from the binder [collectionId]
 * (where they were found — a card lent from a binder) or from the card's deck.
 */
data class LentCopy(val loan: Loan, val card: LoanCard, val qty: Int, val collectionId: String? = null)

private fun nameKeyOf(name: String) = name.trim().lowercase()
private fun entryKey(collectionId: String, scryfallId: String, foil: Boolean) = "$collectionId|$scryfallId|${if (foil) "foil" else ""}"
private fun deckKey(deckId: String, name: String) = "$deckId|${nameKeyOf(name)}"

/**
 * Every copy out on loan that's still there to be lent: a card lent from a binder is one of its
 * entry's copies with no place (lending took it off its place), a card lent from a deck one of the
 * deck's real copies. A loan can't count more copies than that — the oldest loans first — so one whose
 * copies were since removed from the collection counts only what's left. A card whose binder has gone
 * is looked for in the others, the Unsorted pile first.
 */
fun lentCopies(collections: List<Collection>, decks: List<Deck> = emptyList()): List<LentCopy> {
    val loans = loansOf(collections).filter { isOpen(it) }
    if (loans.isEmpty()) return emptyList()
    val known = placesOf(collections).map { it.id }.toSet()
    val budget = HashMap<String, Int>()
    fun add(key: String, n: Int) { if (n > 0) budget[key] = (budget[key] ?: 0) + n }
    val mine = owned(collections)
    val piles = mine.filter { it.isUnsorted } + mine.filter { !it.isUnsorted }
    for (c in piles) for (e in c.entries) {
        val (plain, foil) = unplacedCopies(knownOnly(e, known))
        add(entryKey(c.id, e.scryfallId, false), plain)
        add(entryKey(c.id, e.scryfallId, true), foil)
    }
    for (d in decks) for (e in realCopiesOf(d)) add(deckKey(d.id, e.name), e.quantity)
    fun take(key: String, want: Int): Int {
        val n = minOf(want, budget[key] ?: 0)
        if (n > 0) budget[key] = (budget[key] ?: 0) - n
        return n
    }
    val out = mutableListOf<LentCopy>()
    for (loan in loans.sortedBy { it.lentAt }) for (card in loan.cards) {
        val want = stillOut(card)
        if (want <= 0) continue
        if (card.deckId != null) {
            val got = take(deckKey(card.deckId, card.name), want)
            if (got > 0) out += LentCopy(loan, card, got)
            continue
        }
        var left = want
        for (c in piles.filter { it.id == card.collectionId } + piles.filter { it.id != card.collectionId }) {
            if (left <= 0) break
            val got = take(entryKey(c.id, card.scryfallId, card.isFoil), left)
            if (got <= 0) continue
            left -= got
            out += LentCopy(loan, card, got, c.id)
        }
    }
    return out
}

/** How many copies of each binder entry are out on loan, by "collectionId|scryfallId|foil" ("" for plain). */
fun lentByEntry(lent: List<LentCopy>): Map<String, Int> {
    val out = HashMap<String, Int>()
    for (l in lent) {
        val c = l.collectionId ?: continue
        val key = entryKey(c, l.card.scryfallId, l.card.isFoil)
        out[key] = (out[key] ?: 0) + l.qty
    }
    return out
}

/** Copies of [entry] (in [collectionId]) out on loan, plain and foil, from [lentByEntry]. */
fun lentOf(lent: Map<String, Int>, collectionId: String, entry: CollectionEntry): Pair<Int, Int> =
    (lent[entryKey(collectionId, entry.scryfallId, false)] ?: 0) to (lent[entryKey(collectionId, entry.scryfallId, true)] ?: 0)

/** How many of a deck's real copies of the card called [name] are out on loan. */
fun lentFromDeck(lent: List<LentCopy>, deckId: String, name: String): Int =
    lent.filter { it.card.deckId == deckId && sameCardName(it.card.name, name) }.sumOf { it.qty }

// ---- How much has a place ----

data class StorageSummary(
    /** Every copy owned: in binders, the Unsorted pile and physical decks. */
    val total: Int,
    /** Those with a place: a storage place, a deck box, or lent out. */
    val placed: Int,
    val unplaced: Int,
    /** Real copies in physical decks (not proxies), less those lent out from them. */
    val inDecks: Int,
    /** Copies out on loan, and copies with no other place whose entry is tagged "lent …". */
    val lent: Int,
    /** Copies in each place itself — not counting the places inside it — by id. */
    val own: Map<String, Int>
)

fun storageSummary(collections: List<Collection>, decks: List<Deck>): StorageSummary {
    val known = placesOf(collections).map { it.id }.toSet()
    val lentNow = lentCopies(collections, decks)
    val byEntry = lentByEntry(lentNow)
    val own = LinkedHashMap<String, Int>()
    var total = 0
    var inPlaces = 0
    var lent = 0
    for (c in owned(collections)) for (e in c.entries) {
        val copies = e.quantity + e.foilQuantity
        if (copies <= 0) continue
        total += copies
        var here = 0
        for (p in placedCopies(e)) {
            if (p.placeId !in known) continue
            own[p.placeId] = (own[p.placeId] ?: 0) + p.qty
            here += p.qty
        }
        inPlaces += here
        val (plain, foil) = lentOf(byEntry, c.id, e)
        lent += plain + foil
        if (lentTag(e) != null) lent += copies - here - plain - foil
    }
    val fromDecks = lentNow.filter { it.card.deckId != null }.sumOf { it.qty }
    val inDecks = decks.sumOf { d -> realCopiesOf(d).sumOf { it.quantity } } - fromDecks
    total += inDecks + fromDecks
    lent += fromDecks
    val placed = inPlaces + inDecks + lent
    return StorageSummary(total, placed, total - placed, inDecks, lent, own)
}

/** Copies in [id] and every place inside it. */
fun copiesWithin(summary: StorageSummary, places: List<StoragePlace>, id: String): Int =
    placeAndInside(places, id).sumOf { summary.own[it] ?: 0 }

/** Copies of one entry at one spot of a place. */
data class PlacedCard(val collectionId: String, val entry: CollectionEntry, val line: CopyPlace)

/**
 * Every line of copies kept in each place itself, by place id, in no particular order — [cardsIn] for
 * every place in one pass over the collection, for screens that ask about all of them (Upkeep).
 */
fun cardsByPlace(collections: List<Collection>): Map<String, List<PlacedCard>> {
    val out = HashMap<String, MutableList<PlacedCard>>()
    for (c in owned(collections)) for (e in c.entries) for (line in placedCopies(e)) {
        out.getOrPut(line.placeId) { mutableListOf() } += PlacedCard(c.id, e, line)
    }
    return out
}

/** Every line of copies kept in [placeId] itself, by card name. */
fun cardsIn(collections: List<Collection>, placeId: String): List<PlacedCard> {
    val out = mutableListOf<PlacedCard>()
    for (c in owned(collections)) for (e in c.entries) for (line in placedCopies(e)) {
        if (line.placeId == placeId) out += PlacedCard(c.id, e, line)
    }
    return out.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.entry.name })
}

/** A box's section with its cards; [name] null: copies with no section said. */
data class SectionGroup(val name: String?, val copies: Int, val cards: List<PlacedCard>)

/** A box's sections with their cards: its own sections in order (even empty), then any others used, then copies in none. */
fun sectionsOf(place: StoragePlace, cards: List<PlacedCard>): List<SectionGroup> {
    val names = place.sections.orEmpty().toMutableList()
    for (c in cards) {
        val s = c.line.section
        if (s != null && names.none { it.equals(s, ignoreCase = true) }) names += s
    }
    val groups = names.map { name ->
        val inIt = cards.filter { (it.line.section ?: "").equals(name, ignoreCase = true) }
        SectionGroup(name, inIt.sumOf { it.line.qty }, inIt)
    }.toMutableList()
    val loose = cards.filter { it.line.section == null }
    if (loose.isNotEmpty()) groups += SectionGroup(null, loose.sumOf { it.line.qty }, loose)
    return groups
}

data class BinderPage(val page: Int, val slots: List<List<PlacedCard>>)

/** A binder's pages, each with its pockets, up to the last page used; and copies in no pocket. */
fun pagesOf(place: StoragePlace, cards: List<PlacedCard>): Pair<List<BinderPage>, List<PlacedCard>> {
    val inPockets = cards.filter { (it.line.page ?: 0) > 0 && (it.line.slot ?: 0) > 0 }
    val last = inPockets.maxOfOrNull { it.line.page!! } ?: 0
    val pages = (1..last).map { page ->
        val here = inPockets.filter { it.line.page == page }
        val size = maxOf(place.pockets, here.maxOfOrNull { it.line.slot!! } ?: 0)
        BinderPage(page, (1..size).map { slot -> here.filter { it.line.slot == slot } })
    }
    return pages to cards.filter { it !in inPockets }
}

/** The pocket after the last one used in a binder: page 1, pocket 1 for an empty one. */
fun nextPocket(place: StoragePlace, collections: List<Collection>): Pair<Int, Int> {
    var page = 0
    var slot = 0
    for (c in cardsIn(collections, place.id)) {
        val p = c.line.page ?: 0
        val s = c.line.slot ?: 0
        if (p > page || (p == page && s > slot)) { page = p; slot = s }
    }
    if (page == 0) return 1 to 1
    return if (slot >= place.pockets) (page + 1) to 1 else page to (slot + 1)
}

// ---- Where a card is ----

enum class WhereKind { PLACE, DECK, LENT, NONE }

/**
 * One line of "Where it is". [placeId], [collectionId], [scryfallId] and [line] for a place; [deckId]
 * for a deck; [loanId] for copies out on loan (null for copies tagged "lent …").
 */
data class WhereLine(
    val kind: WhereKind,
    val title: String,
    val detail: String,
    val qty: Int,
    val placeId: String? = null,
    val collectionId: String? = null,
    val scryfallId: String? = null,
    val line: CopyPlace? = null,
    val deckId: String? = null,
    val loanId: String? = null
)

private fun nameKeys(n: String): List<String> {
    val full = n.trim().lowercase()
    return listOf(full, full.split(" // ")[0].trim())
}

/** Whether two names are the same card: case aside, and either face of a double-faced card. */
fun sameCardName(a: String, b: String): Boolean {
    val ka = nameKeys(a)
    return nameKeys(b).any { it in ka }
}

/** "Page 3, slot 5". */
fun pocketLabel(page: Int, slot: Int) = "Page $page, slot $slot"

/**
 * Where a loan's card came from, short: "Red box › Red", "Atraxa deck", or the binder it had no place
 * in ("Unsorted"). "Somewhere" when that's all gone.
 */
fun loanCardFrom(card: LoanCard, collections: List<Collection>, decks: List<Deck>): String {
    if (card.deckId != null) {
        val deck = decks.firstOrNull { it.id == card.deckId }
        return if (deck != null) "${deck.name} deck" else "a deck"
    }
    val place = card.placeId?.let { id -> placesOf(collections).firstOrNull { it.id == id } }
    if (place != null) return if (card.section != null) "${place.name} › ${card.section}" else place.name
    return collections.firstOrNull { it.id == card.collectionId }?.name ?: "Somewhere"
}

/**
 * Where every copy of the card called [name] is (any printing): a line per spot in a place, one per
 * physical deck (less the copies lent out from it), one per loan, and the ones with no place yet.
 * With their total.
 */
fun whereItIs(collections: List<Collection>, decks: List<Deck>, name: String): Pair<List<WhereLine>, Int> {
    val places = placesOf(collections)
    val byId = places.associateBy { it.id }
    val lent = lentCopies(collections, decks).filter { sameCardName(it.card.name, name) }
    val byEntry = lentByEntry(lent)
    val lines = mutableListOf<WhereLine>()
    var tagged = 0
    var lentWords = ""
    var none = 0
    val noneIn = mutableListOf<String>()
    for (c in owned(collections)) for (e in c.entries) {
        if (!sameCardName(e.name, name)) continue
        val copies = e.quantity + e.foilQuantity
        if (copies <= 0) continue
        var here = 0
        for (line in placedCopies(e)) {
            val place = byId[line.placeId] ?: continue
            here += line.qty
            val parents = parentsOf(places, place.id).joinToString(" › ") { it.name }
            val hint = if (line.section != null) place.rule?.let { positionHint(it, CardFacts(e.name)) } else null
            val pocket = if (line.page != null && line.slot != null) pocketLabel(line.page, line.slot) else ""
            val detail = listOf(parents, pocket, hint ?: "", if (line.isFoil) "foil" else "").filter { it.isNotEmpty() }.joinToString(" · ")
            lines += WhereLine(
                WhereKind.PLACE, if (line.section != null) "${place.name} › ${line.section}" else place.name, detail, line.qty,
                placeId = place.id, collectionId = c.id, scryfallId = e.scryfallId, line = line
            )
        }
        val (plainOut, foilOut) = lentOf(byEntry, c.id, e)
        val left = copies - here - plainOut - foilOut
        if (left <= 0) continue
        val tag = lentTag(e)
        if (tag != null) {
            tagged += left
            if (lentWords.isEmpty()) lentWords = tag
        } else {
            none += left
            if (c.name !in noneIn) noneIn += c.name
        }
    }
    for (d in decks) {
        val qty = realCopiesOf(d).filter { sameCardName(it.name, name) }.sumOf { it.quantity } - lentFromDeck(lent, d.id, name)
        if (qty > 0) lines += WhereLine(WhereKind.DECK, "Deck: ${d.name}", "In its deck box", qty, deckId = d.id)
    }
    for (id in lent.map { it.loan.id }.distinct()) {
        val mine = lent.filter { it.loan.id == id }
        val from = mine.map { loanCardFrom(it.card, collections, decks) }.distinct().joinToString(", ")
        lines += WhereLine(WhereKind.LENT, "Lent to ${mine[0].loan.to}", "From $from", mine.sumOf { it.qty }, loanId = id)
    }
    if (tagged > 0) lines += WhereLine(WhereKind.LENT, "Lent out", lentWords.replaceFirstChar { it.uppercase() }, tagged)
    if (none > 0) lines += WhereLine(WhereKind.NONE, "No place yet", "In ${noneIn.joinToString(", ")}", none)
    return lines to lines.sumOf { it.qty }
}

/**
 * Where a printing's binder copies are kept, short — "Red box ×2 · Trade binder ×1" — for beside "In 2
 * decks and 1 binder". "" when none has a place.
 */
fun keptInLabel(collections: List<Collection>, scryfallId: String): String {
    val places = placesOf(collections)
    val counts = LinkedHashMap<String, Int>()
    for (c in owned(collections)) for (e in c.entries) {
        if (e.scryfallId != scryfallId) continue
        for (line in placedCopies(e)) {
            val name = places.firstOrNull { it.id == line.placeId }?.name ?: continue
            counts[name] = (counts[name] ?: 0) + line.qty
        }
    }
    return counts.entries.joinToString(" · ") { "${it.key} ×${it.value}" }
}

/** Where one entry's copies are kept, short — "Red box ×2 · Trade binder ×1"; "" when none has a place. */
fun keptLabel(entry: CollectionEntry, places: List<StoragePlace>): String {
    val counts = LinkedHashMap<String, Int>()
    for (line in placedCopies(entry)) {
        val name = places.firstOrNull { it.id == line.placeId }?.name ?: continue
        counts[name] = (counts[name] ?: 0) + line.qty
    }
    return counts.entries.joinToString(" · ") { "${it.key} ×${it.value}" }
}

// ---- Sorting rules: where a new card goes ----

/** What a sorting rule needs to know about a card: its colours (a double-faced card's front face's), type, set and number. */
data class CardFacts(
    val name: String,
    val colors: List<String>? = null,
    val typeLine: String? = null,
    val set: String? = null,
    val collectorNumber: String? = null
)

/** A Scryfall card's facts for the sorting rules. */
fun cardFactsOf(card: ScryfallCard): CardFacts = CardFacts(
    name = card.name,
    colors = card.colors ?: card.cardFaces?.firstOrNull()?.colors ?: emptyList(),
    typeLine = card.typeLine ?: card.cardFaces?.firstOrNull()?.typeLine,
    set = card.set,
    collectorNumber = card.collectorNumber
)

private val COLOUR_NAMES = mapOf("W" to "White", "U" to "Blue", "B" to "Black", "R" to "Red", "G" to "Green")
private fun frontType(f: CardFacts) = (f.typeLine ?: "").split(" // ")[0]
private fun hasWord(line: String, word: String) = Regex("\\b$word\\b").containsMatchIn(line)

/** White … Green, Multicolour, Colourless — or Lands for a land. */
fun colourSection(f: CardFacts): String {
    if (hasWord(frontType(f), "Land")) return "Lands"
    val colours = f.colors.orEmpty().filter { it in COLOUR_NAMES }
    return when {
        colours.size > 1 -> "Multicolour"
        colours.size == 1 -> COLOUR_NAMES.getValue(colours[0])
        else -> "Colourless"
    }
}

private val TYPE_ORDER = listOf(
    "Creature" to "Creatures", "Planeswalker" to "Planeswalkers", "Battle" to "Battles", "Instant" to "Instants",
    "Sorcery" to "Sorceries", "Artifact" to "Artifacts", "Enchantment" to "Enchantments", "Land" to "Lands"
)

/** Creatures, Planeswalkers, … Lands, or Other — the first that fits. */
fun typeSection(f: CardFacts): String {
    val line = frontType(f)
    return TYPE_ORDER.firstOrNull { hasWord(line, it.first) }?.second ?: "Other"
}

/** The letter a name files under: A–Z, or # for anything else. */
fun letterOf(name: String): String {
    val c = name.trim().firstOrNull()?.uppercaseChar() ?: return "#"
    return if (c in 'A'..'Z') c.toString() else "#"
}

private val LETTER_RANGE = Regex("^([A-Z])\\s*[–-]\\s*([A-Z])$", RegexOption.IGNORE_CASE)

/** Whether a section named like "A–F", "A-F" or "A" holds names starting [letter]. */
private fun sectionHoldsLetter(section: String, letter: String): Boolean {
    val range = LETTER_RANGE.find(section.trim())
    if (range != null) return letter >= range.groupValues[1].uppercase() && letter <= range.groupValues[2].uppercase()
    return section.trim().uppercase() == letter
}

/** Where in its section a card goes: "around “L”", or "around #117" by set. */
fun positionHint(rule: SortRule, f: CardFacts): String? =
    if (rule == SortRule.SET) f.collectorNumber?.let { "around #$it" } else "around “${letterOf(f.name)}”"

/** The section [rule] puts a card in, spelled as the box spells it when it has one by that name. */
fun ruleSection(rule: SortRule, f: CardFacts, sections: List<String> = emptyList()): String? {
    if (rule == SortRule.NAME) return sections.firstOrNull { sectionHoldsLetter(it, letterOf(f.name)) }
    val section = when (rule) {
        SortRule.COLOUR -> colourSection(f)
        SortRule.TYPE -> typeSection(f)
        else -> f.set?.uppercase()?.takeIf { it.isNotEmpty() }
    } ?: return null
    return sections.firstOrNull { it.equals(section, ignoreCase = true) } ?: section
}

/** Where a card should go in [place], and that as words: "Red › around “L”", "Page 3, slot 6". */
fun suggestSpot(place: StoragePlace, f: CardFacts?, collections: List<Collection>): Pair<Spot, String?> {
    if (place.placeKind == PlaceKind.BINDER) {
        // A binder in order: the card waits beside it, to be fitted in with Add cards in order (BinderPages.kt).
        if (place.rule != null) return Spot(place.id) to null
        val (page, slot) = nextPocket(place, collections)
        return Spot(place.id, page = page, slot = slot) to pocketLabel(page, slot)
    }
    val rule = place.rule
    if (rule != null && f != null) {
        val section = ruleSection(rule, f, place.sections.orEmpty())
        val hint = listOfNotNull(section, positionHint(rule, f)).joinToString(" › ")
        return Spot(place.id, section = section) to hint.ifEmpty { null }
    }
    return Spot(place.id) to null
}

// ---- Putting cards away ----

enum class PutAwayResult { PLACED, MOVED, HERE, NEW }

/** One card put away, as much as Undo needs to take it back. [from] null: it had no place (or, when [added], it's new). */
data class PutAwayStep(
    val collectionId: String,
    val scryfallId: String,
    val from: CopyPlace?,
    val to: CopyPlace,
    val added: Boolean
)

/** [label]: "moved from Unsorted", "moved from Trade binder", "already here", "new to collection". */
data class PutAwayOutcome(
    val collections: List<Collection>,
    val result: PutAwayResult,
    val label: String,
    val step: PutAwayStep?
)

private fun mapEntry(collections: List<Collection>, collectionId: String, scryfallId: String, fn: (CollectionEntry) -> CollectionEntry): List<Collection> =
    collections.map { c -> if (c.id != collectionId) c else c.copy(entries = c.entries.map { if (it.scryfallId == scryfallId) fn(it) else it }) }

private fun knownOnly(e: CollectionEntry, known: Set<String>): CollectionEntry =
    if (placedCopies(e).all { it.placeId in known }) e else withPlaces(e, placedCopies(e).filter { it.placeId in known })

/**
 * One scanned card ([cardId], [cardName]) put away into [to]. A copy the user owns with no place gets
 * it (the Unsorted pile's first, that printing before others); failing that, a copy in another place
 * moves here (never one in a deck — those are in their deck boxes); a card already here stays; and a
 * card not owned at all is added to the Unsorted pile, here. [newEntry] is the entry to add then, with
 * no copies yet.
 */
fun putAway(collections: List<Collection>, cardId: String, cardName: String, to: Spot, newEntry: CollectionEntry, foil: Boolean = false): PutAwayOutcome {
    val places = placesOf(collections)
    val known = places.map { it.id }.toSet()
    val here = placeAndInside(places, to.placeId)
    val mine = owned(collections)
    // The Unsorted pile first, then the binders; that printing before other printings.
    val piles = mine.filter { it.isUnsorted } + mine.filter { !it.isUnsorted }
    val candidates = mutableListOf<Pair<Collection, CollectionEntry>>()
    for (exact in listOf(true, false)) for (c in piles) for (e in c.entries) {
        if (e.quantity + e.foilQuantity <= 0) continue
        val match = if (exact) e.scryfallId == cardId else e.scryfallId != cardId && sameCardName(e.name, cardName)
        if (match) candidates += c to e
    }
    val finishes = if (foil) listOf(true, false) else listOf(false, true)
    // Copies out on loan have no place, but they aren't here to put away.
    val lent = lentByEntry(lentCopies(collections))

    // A copy with no place.
    for (f in finishes) for ((c, e) in candidates) {
        val clean = knownOnly(e, known)
        val (plain, foils) = unplacedCopies(clean)
        val (plainOut, foilOut) = lentOf(lent, c.id, e)
        if ((if (f) foils - foilOut else plain - plainOut) <= 0) continue
        val entry = placeCopies(clean, to, 1, f).first
        return PutAwayOutcome(
            mapEntry(collections, c.id, e.scryfallId) { entry },
            PutAwayResult.PLACED,
            if (c.isUnsorted) "moved from Unsorted" else "given a place",
            PutAwayStep(c.id, e.scryfallId, null, copyPlace(to, 1, f), added = false)
        )
    }
    // A copy somewhere else.
    for (f in finishes) for ((c, e) in candidates) {
        val line = placedCopies(e).firstOrNull { it.isFoil == f && it.placeId in known && it.placeId !in here } ?: continue
        val entry = moveCopies(e, line, to, 1).first
        return PutAwayOutcome(
            mapEntry(collections, c.id, e.scryfallId) { entry },
            PutAwayResult.MOVED,
            "moved from ${places.firstOrNull { it.id == line.placeId }?.name ?: "another place"}",
            PutAwayStep(c.id, e.scryfallId, copyPlace(line.spot, 1, f), copyPlace(to, 1, f), added = false)
        )
    }
    if (candidates.any { (_, e) -> placedCopies(e).any { it.placeId in here } }) {
        return PutAwayOutcome(collections, PutAwayResult.HERE, "already here", null)
    }
    val (added, step) = addedHere(collections, cardId, to, newEntry, foil)
    return PutAwayOutcome(added, PutAwayResult.NEW, "new to collection", step)
}

/** A new copy of the card [cardId] added to the Unsorted pile, kept at [to] — for a card already here that's really another copy too. */
fun addedHere(collections: List<Collection>, cardId: String, to: Spot, newEntry: CollectionEntry, foil: Boolean = false): Pair<List<Collection>, PutAwayStep> {
    val step = PutAwayStep(UNSORTED_COLLECTION_ID, cardId, null, copyPlace(to, 1, foil), added = true)
    val next = withUnsortedPile(collections).map { c ->
        if (!c.isUnsorted) return@map c
        val had = c.entries.firstOrNull { it.scryfallId == cardId }
        val grown = had?.copy(quantity = had.quantity + if (foil) 0 else 1, foilQuantity = had.foilQuantity + if (foil) 1 else 0)
            ?: newEntry.copy(scryfallId = cardId, quantity = if (foil) 0 else 1, foilQuantity = if (foil) 1 else 0)
        val placed = withPlaces(grown, placedCopies(grown) + copyPlace(to, 1, foil))
        c.copy(entries = if (had != null) c.entries.map { if (it === had) placed else it } else c.entries + placed)
    }
    return next to step
}

/** [collections] with [step] taken back: the copy goes back where it was, or out of the collection if it was new. */
fun undoPutAway(collections: List<Collection>, step: PutAwayStep): List<Collection> {
    val c = collections.firstOrNull { it.id == step.collectionId } ?: return collections
    val e = c.entries.firstOrNull { it.scryfallId == step.scryfallId } ?: return collections
    if (!step.added) return mapEntry(collections, c.id, e.scryfallId) { moveCopies(it, step.to, step.from?.spot, 1, step.to.isFoil).first }
    val unplaced = moveCopies(e, step.to, null, 1).first
    val foil = step.to.isFoil
    val smaller = tidied(unplaced.copy(quantity = unplaced.quantity - if (foil) 0 else 1, foilQuantity = unplaced.foilQuantity - if (foil) 1 else 0))
    val gone = smaller.quantity <= 0 && smaller.foilQuantity <= 0
    return collections.map { x ->
        if (x.id != c.id) x
        else x.copy(entries = if (gone) x.entries.filter { it.scryfallId != e.scryfallId } else x.entries.map { if (it.scryfallId == e.scryfallId) smaller else it })
    }
}

/**
 * Gives up to [count] copies of the card called [name] that have no place the spot [to] — copies of
 * the printing [preferId] first, the Unsorted pile's before the binders', plain before foil. Copies
 * out on loan, or tagged as lent out, are left alone. Also how many it gave.
 */
fun placeUnplaced(collections: List<Collection>, name: String, preferId: String?, to: Spot, count: Int): Pair<List<Collection>, Int> {
    val known = placesOf(collections).map { it.id }.toSet()
    val mine = owned(collections)
    val piles = mine.filter { it.isUnsorted } + mine.filter { !it.isUnsorted }
    val order = mutableListOf<Pair<Collection, CollectionEntry>>()
    for (exact in listOf(true, false)) for (c in piles) for (e in c.entries) {
        if (!sameCardName(e.name, name) || lentTag(e) != null) continue
        if ((e.scryfallId == preferId) == exact) order += c to e
    }
    var left = count
    var out = collections
    // Copies out on loan have no place, but they aren't here to be given one.
    val lent = lentByEntry(lentCopies(collections))
    for (foil in listOf(false, true)) for ((c, e) in order) {
        if (left <= 0) break
        val current = out.firstOrNull { it.id == c.id }?.entries?.firstOrNull { it.scryfallId == e.scryfallId } ?: continue
        val clean = knownOnly(current, known)
        val (plain, foils) = unplacedCopies(clean)
        val (plainOut, foilOut) = lentOf(lent, c.id, e)
        val (entry, moved) = placeCopies(clean, to, minOf(left, if (foil) foils - foilOut else plain - plainOut), foil)
        if (moved == 0) continue
        left -= moved
        out = mapEntry(out, c.id, e.scryfallId) { entry }
    }
    return out to (count - left)
}

// ---- Sync: merging two devices' places ----

/**
 * Whether [c] was written by an app that doesn't know about places: no "places" or "storagePlaces"
 * key anywhere in it. Such an app drops the keys when it saves a binder; its version is taken as
 * leaving the places as they were.
 */
fun writtenWithoutPlaces(c: Collection): Boolean = c.storagePlaces == null && c.entries.all { it.places == null }

/**
 * [theirs] with the places [source] knows put back, when [theirs] was written by an app that doesn't
 * know about them (see writtenWithoutPlaces) — the same object otherwise.
 */
fun keepPlacesFromOlderApp(source: Collection, theirs: Collection): Collection {
    if (!writtenWithoutPlaces(theirs) || writtenWithoutPlaces(source)) return theirs
    val kept = source.entries.filter { it.places != null }.associate { it.scryfallId to it.places!! }
    return theirs.copy(
        storagePlaces = source.storagePlaces ?: theirs.storagePlaces,
        entries = theirs.entries.map { e -> kept[e.scryfallId]?.let { withPlaces(e, it) } ?: e }
    )
}

private fun <T> pick(base: T, mine: T, theirs: T, minePreferred: Boolean): T = when {
    mine == theirs -> mine
    mine == base -> theirs
    theirs == base -> mine
    else -> if (minePreferred) mine else theirs
}

/**
 * Merges an entry's lines of places like the entries themselves: a line added on one side is kept,
 * one removed on one side stays removed, and counts both sides changed add up. Null when no side has
 * the key.
 */
fun mergeCopyPlaces(base: List<CopyPlace>?, mine: List<CopyPlace>?, theirs: List<CopyPlace>?): List<CopyPlace>? {
    if (base == null && mine == null && theirs == null) return null
    val b = base.orEmpty().associateBy(::copyKey)
    val m = mine.orEmpty().associateBy(::copyKey)
    val t = theirs.orEmpty().associateBy(::copyKey)
    val added = (t.keys + m.keys).distinct().filter { it !in b }.sorted()
    val out = mutableListOf<CopyPlace>()
    for (key in b.keys.toList() + added) {
        val bp = b[key]
        val mp = m[key]
        val tp = t[key]
        if (bp != null && (mp == null || tp == null)) continue
        if (bp == null) {
            val qty = maxOf(mp?.qty ?: 0, tp?.qty ?: 0)
            if (qty > 0) out += (tp ?: mp)!!.copy(qty = qty)
            continue
        }
        val summed = tp!!.qty + (mp!!.qty - bp.qty)
        val qty = if (summed <= 0 && mp.qty > 0 && tp.qty > 0) minOf(mp.qty, tp.qty) else summed.coerceAtLeast(0)
        if (qty > 0) out += tp.copy(qty = qty)
    }
    return out
}

/**
 * Merges two devices' lists of places: one made on either side is kept, one deleted on either side
 * stays deleted, and each field goes to whoever changed it (the more recent edit when both did).
 * Null when no side has the key.
 */
fun mergePlaceLists(base: List<StoragePlace>?, mine: List<StoragePlace>?, theirs: List<StoragePlace>?, minePreferred: Boolean): List<StoragePlace>? {
    if (base == null && mine == null && theirs == null) return null
    val b = base.orEmpty().associateBy { it.id }
    val m = mine.orEmpty().associateBy { it.id }
    val t = theirs.orEmpty().associateBy { it.id }
    val added = (t.keys + m.keys).distinct().filter { it !in b }.sorted()
    val out = mutableListOf<StoragePlace>()
    for (id in b.keys.toList() + added) {
        val bp = b[id]
        val mp = m[id]
        val tp = t[id]
        if (bp != null && (mp == null || tp == null)) continue
        if (bp == null) { out += (tp ?: mp)!!; continue }
        out += storagePlace(StoragePlace(
            id = id,
            name = pick(bp.name, mp!!.name, tp!!.name, minePreferred),
            kind = pick(bp.kind, mp.kind, tp.kind, minePreferred),
            parentId = pick(bp.parentId, mp.parentId, tp.parentId, minePreferred),
            note = pick(bp.note, mp.note, tp.note, minePreferred),
            sections = pick(bp.sections, mp.sections, tp.sections, minePreferred),
            pocketsPerPage = pick(bp.pocketsPerPage, mp.pocketsPerPage, tp.pocketsPerPage, minePreferred),
            sortRule = pick(bp.sortRule, mp.sortRule, tp.sortRule, minePreferred),
            createdAt = minOf(mp.createdAt, tp.createdAt),
            // Only ever moves on, so the later check wins — and a side that dropped it didn't clear it.
            lastChecked = maxOf(bp.lastChecked ?: 0L, mp.lastChecked ?: 0L, tp.lastChecked ?: 0L).takeIf { it > 0 },
            capacity = pick(bp.capacity, mp.capacity, tp.capacity, minePreferred),
            pages = pick(bp.pages, mp.pages, tp.pages, minePreferred)
        ))
    }
    return out
}

/**
 * [theirs] with each place's "lastChecked" no older than [source]'s — a place saved by an app that
 * doesn't know about checks comes without it. The same object when nothing changes.
 */
fun keepLastChecked(source: Collection, theirs: Collection): Collection {
    val theirPlaces = theirs.storagePlaces ?: return theirs
    val mine = source.storagePlaces?.associate { it.id to (it.lastChecked ?: 0L) } ?: return theirs
    if (theirPlaces.none { (mine[it.id] ?: 0L) > (it.lastChecked ?: 0L) }) return theirs
    return theirs.copy(storagePlaces = theirPlaces.map { p ->
        val kept = mine[p.id] ?: 0L
        if (kept > (p.lastChecked ?: 0L)) p.copy(lastChecked = kept) else p
    })
}

/**
 * [theirs] with each place's size ("capacity", a binder's "pages") put back where [source] has one and
 * [theirs] doesn't say — a place saved by an app that doesn't know about sizes comes without them. A
 * size taken off is kept as 0, so it isn't put back. The same object when nothing changes.
 */
fun keepPlaceSizes(source: Collection, theirs: Collection): Collection {
    val theirPlaces = theirs.storagePlaces ?: return theirs
    val mine = source.storagePlaces?.associateBy { it.id } ?: return theirs
    fun lost(p: StoragePlace): Boolean {
        val m = mine[p.id] ?: return false
        return (p.capacity == null && m.capacity != null) || (p.pages == null && m.pages != null)
    }
    if (theirPlaces.none(::lost)) return theirs
    return theirs.copy(storagePlaces = theirPlaces.map { p ->
        if (!lost(p)) p else {
            val m = mine.getValue(p.id)
            p.copy(capacity = p.capacity ?: m.capacity, pages = p.pages ?: m.pages)
        }
    })
}

// ---- The Advanced filters' "Place" ----

/** The filter's value for copies with no place yet. */
const val NO_PLACE = "none"

/** The places holding copies of an entry — each with the places it sits in — and how many have none. */
fun placeFactsOf(
    entry: CollectionEntry,
    places: List<StoragePlace>,
    chains: Map<String, List<String>> = placeChains(places)
): Pair<List<String>, Int> {
    val out = mutableListOf<String>()
    var here = 0
    for (line in placedCopies(entry)) {
        val chain = chains[line.placeId] ?: continue
        here += line.qty
        for (id in chain) if (id !in out) out += id
    }
    val copies = entry.quantity + entry.foilQuantity
    return out to if (lentTag(entry) != null) 0 else (copies - here).coerceAtLeast(0)
}
