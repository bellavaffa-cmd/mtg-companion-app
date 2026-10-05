package com.mtgcompanion.app.data

import java.util.Calendar

/*
 * Checking a place: scan everything in a box, a section of it or a binder, and see what's where it
 * should be, what's missing (listed here, not scanned) and what's extra (scanned here, but listed
 * somewhere else or not at all).
 *  - A scan counts against the copies listed here by printing when the scanner was sure of it, by
 *    name otherwise; and by finish when it says foil or plain — the scanner can't see foil, so its
 *    scans match either and the results say so.
 *  - Missing copies stay where they're listed until the user says otherwise: Mark No place yet takes
 *    their place away, Remove from collection takes them out of the collection.
 *  - Record them here gives the extra cards this place: a copy with no place first, then one from
 *    another place (as putting a card away does); a card only a deck has is taken out of the deck
 *    only when the user says so, and a card not in the collection is added to the Unsorted pile.
 *  - Save results notes when the place was last checked, on the place ("lastChecked", milliseconds).
 *    It only ever moves on: two devices' checks merge to the later one, and a place saved by an app
 *    that doesn't know about it keeps it (see mergePlaceLists and keepLastChecked in StoragePlaces.kt).
 *
 * Pure, so it can be tested. Mirrors the web app's src/collection/placeCheck.ts rule for rule, with
 * the same tests (PlaceCheckTest.kt ↔ tests/collection/placeCheck.test.ts).
 */

/** What's being checked: a place, or one section of it. */
data class CheckScope(val placeId: String, val section: String?)

/** One card scanned while checking. [foil] null: the scanner couldn't tell. [exact]: it was sure of the printing. */
data class CheckScan(
    val scryfallId: String,
    val name: String,
    val imageUrl: String?,
    val foil: Boolean?,
    val exact: Boolean
)

/**
 * What a scan turned out to be: HERE "belongs here"; SECTION "should be in Blue" (this place, another
 * section); UNPLACED "no place yet"; ELSEWHERE "listed in Red box › Blue"; DECK "listed in Krenko
 * deck"; EXTRA "one more than listed here" or "another printing is listed here"; UNKNOWN "not in your
 * collection".
 */
enum class CheckKind { HERE, SECTION, UNPLACED, ELSEWHERE, DECK, EXTRA, UNKNOWN }

/** A scan and what it was. [deckId]: DECK, the deck that lists it. */
data class CheckLine(val scan: CheckScan, val kind: CheckKind, val label: String, val deckId: String? = null)

/** Copies listed at one spot in the place: what's expected, or (in the results) what wasn't scanned. */
data class ListedCopies(val collectionId: String, val scryfallId: String, val name: String, val line: CopyPlace, val qty: Int)

data class CheckResult(
    /** Every scan, in the order scanned, with what it was. */
    val lines: List<CheckLine>,
    /** Copies listed in what's checked. */
    val expected: Int,
    /** Scans that belong here. */
    val here: Int,
    /** Copies listed here and not scanned, spot by spot. */
    val missing: List<ListedCopies>,
    val missingCount: Int,
    /** Scans that don't belong here. */
    val extra: List<CheckLine>,
    /** Some scans couldn't tell foil, so foil and plain copies were counted together. */
    val foilIgnored: Boolean
)

private fun sameSection(a: String?, b: String?) = (a ?: "").equals(b ?: "", ignoreCase = true)

/** The place ids a check covers: the place and the places inside it, or only the place for one section. */
private fun scopeIds(collections: List<Collection>, scope: CheckScope): Set<String> =
    if (scope.section != null) setOf(scope.placeId) else placeAndInside(placesOf(collections), scope.placeId)

/** Every line of copies listed in what's checked, place by place (in tree order), each by card name. */
fun listedIn(collections: List<Collection>, scope: CheckScope): List<ListedCopies> {
    val ids = scopeIds(collections, scope)
    val order = listOf(scope.placeId) + placeTree(placesOf(collections)).map { it.place.id }.filter { it != scope.placeId && it in ids }
    return order.flatMap { cardsIn(collections, it) }
        .filter { scope.section == null || sameSection(it.line.section, scope.section) }
        .map { ListedCopies(it.collectionId, it.entry.scryfallId, it.entry.name, it.line, it.line.qty) }
}

/** Whether a copy of [scryfallId] / [name] is the card scanned: the same printing when it was sure, the same card otherwise. */
private fun isCard(scan: CheckScan, scryfallId: String, name: String) =
    if (scan.exact) scryfallId == scan.scryfallId else sameCardName(name, scan.name)

private val DECK_END = Regex("\\bdeck$", RegexOption.IGNORE_CASE)

/** "listed in Krenko deck" — or "listed in Goblin deck" for a deck already called that. */
private fun deckWords(name: String) = if (DECK_END.containsMatchIn(name.trim())) "listed in ${name.trim()}" else "listed in ${name.trim()} deck"

/** [e] with only the lines in places that still exist — a line in a deleted place has no place. */
private fun knownOnlyLines(e: CollectionEntry, known: Set<String>): CollectionEntry {
    val placed = placedCopies(e)
    return if (placed.all { it.placeId in known }) e else e.copy(places = placed.filter { it.placeId in known })
}

/** Where a scan that doesn't belong here is listed instead. [listed]: what's checked. */
private fun elsewhere(collections: List<Collection>, decks: List<Deck>, scope: CheckScope, scan: CheckScan, listed: List<ListedCopies>): CheckLine {
    val places = placesOf(collections)
    val byId = places.associateBy { it.id }
    val ids = scopeIds(collections, scope)
    // In this place, another section.
    if (scope.section != null) {
        for (c in cardsIn(collections, scope.placeId)) {
            if (!isCard(scan, c.entry.scryfallId, c.entry.name) || sameSection(c.line.section, scope.section)) continue
            if (scan.foil != null && c.line.isFoil != scan.foil) continue
            val label = c.line.section?.let { "should be in $it" } ?: "listed in ${byId[scope.placeId]?.name ?: "this place"}, no section"
            return CheckLine(scan, CheckKind.SECTION, label)
        }
    }
    val known = places.map { it.id }.toSet()
    // A copy with no place yet.
    for (c in collections) {
        if (c.kind == CollectionType.WISHLIST) continue
        for (e in c.entries) {
            if (!isCard(scan, e.scryfallId, e.name)) continue
            val (plain, foil) = unplacedCopies(knownOnlyLines(e, known))
            val free = when (scan.foil) { true -> foil; false -> plain; null -> plain + foil }
            if (free > 0) return CheckLine(scan, CheckKind.UNPLACED, "no place yet")
        }
    }
    // Another place.
    for (node in placeTree(places)) {
        if (node.place.id in ids) continue
        for (c in cardsIn(collections, node.place.id)) {
            if (!isCard(scan, c.entry.scryfallId, c.entry.name)) continue
            if (scan.foil != null && c.line.isFoil != scan.foil) continue
            val path = (parentsOf(places, node.place.id).map { it.name } + node.place.name).joinToString(" › ")
            return CheckLine(scan, CheckKind.ELSEWHERE, "listed in " + (c.line.section?.let { "$path › $it" } ?: path))
        }
    }
    // A deck.
    for (d in decks) {
        if (realCopiesOf(d).any { isCard(scan, it.scryfallId, it.name) }) return CheckLine(scan, CheckKind.DECK, deckWords(d.name), deckId = d.id)
    }
    if (listed.any { isCard(scan, it.scryfallId, it.name) }) return CheckLine(scan, CheckKind.EXTRA, "one more than listed here")
    if (listed.any { sameCardName(it.name, scan.name) }) return CheckLine(scan, CheckKind.EXTRA, "another printing is listed here")
    return CheckLine(scan, CheckKind.UNKNOWN, "not in your collection")
}

/** Which scans match first: sure of the printing and the finish, then of the printing, then the rest. */
private fun sureness(scan: CheckScan) = (if (scan.exact) 0 else 2) + (if (scan.foil == null) 1 else 0)

/**
 * The check so far: each scan matched against the copies listed in what's checked — the scans sure
 * of their printing first, each in the order scanned; that printing first (any printing of the card
 * when the scanner wasn't sure), plain before foil when it couldn't tell — then what's left over.
 */
fun reconcile(collections: List<Collection>, decks: List<Deck>, scope: CheckScope, scans: List<CheckScan>): CheckResult {
    val listed = listedIn(collections, scope)
    val left = listed.map { it.qty }.toMutableList()
    val lines = arrayOfNulls<CheckLine>(scans.size)
    val order = scans.withIndex().sortedWith(compareBy({ sureness(it.value) }, { it.index }))
    for ((i, scan) in order) {
        var found = -1
        val passes = listOf<(ListedCopies) -> Boolean>({ it.scryfallId == scan.scryfallId }) +
            if (scan.exact) emptyList() else listOf<(ListedCopies) -> Boolean>({ sameCardName(it.name, scan.name) })
        val finishes = scan.foil?.let { listOf(it) } ?: listOf(false, true)
        search@ for (pass in passes) for (foil in finishes) {
            found = listed.indices.firstOrNull { j -> left[j] > 0 && listed[j].line.isFoil == foil && pass(listed[j]) } ?: -1
            if (found >= 0) break@search
        }
        lines[i] = if (found >= 0) {
            left[found]--
            CheckLine(scan, CheckKind.HERE, "belongs here")
        } else elsewhere(collections, decks, scope, scan, listed)
    }
    val done = lines.map { it!! }
    val missing = listed.indices.filter { left[it] > 0 }.map { listed[it].copy(qty = left[it]) }
    return CheckResult(
        lines = done,
        expected = listed.sumOf { it.qty },
        here = done.count { it.kind == CheckKind.HERE },
        missing = missing,
        missingCount = missing.sumOf { it.qty },
        extra = done.filter { it.kind != CheckKind.HERE },
        foilIgnored = scans.any { it.foil == null }
    )
}

/** Where a missing line is, short: "Red", "Page 3, slot 5", or "" when it's just in the place. */
fun listedWhere(l: ListedCopies): String {
    val page = l.line.page
    val slot = l.line.slot
    return if (page != null && slot != null) pocketLabel(page, slot) else l.line.section ?: ""
}

// ---- What to do about it ----

private fun mapEntryOf(collections: List<Collection>, collectionId: String, scryfallId: String, fn: (CollectionEntry) -> CollectionEntry): List<Collection> =
    collections.map { c -> if (c.id != collectionId) c else c.copy(entries = c.entries.map { if (it.scryfallId == scryfallId) fn(it) else it }) }

/** Mark No place yet: the missing copies keep being in the collection, with no place. */
fun markNoPlace(collections: List<Collection>, missing: List<ListedCopies>): List<Collection> =
    missing.fold(collections) { out, m -> mapEntryOf(out, m.collectionId, m.scryfallId) { moveCopies(it, m.line, null, m.qty).first } }

/** Remove from collection: the missing copies go, and an entry left with none goes too. */
fun removeMissing(collections: List<Collection>, missing: List<ListedCopies>): List<Collection> {
    var out = collections
    for (m in missing) {
        val c = out.firstOrNull { it.id == m.collectionId } ?: continue
        val e = c.entries.firstOrNull { it.scryfallId == m.scryfallId } ?: continue
        val (entry, moved) = moveCopies(e, m.line, null, m.qty)
        if (moved == 0) continue
        val foil = m.line.isFoil
        val smaller = tidied(entry.copy(quantity = entry.quantity - if (foil) 0 else moved, foilQuantity = entry.foilQuantity - if (foil) moved else 0))
        val gone = smaller.quantity <= 0 && smaller.foilQuantity <= 0
        out = out.map { x ->
            if (x.id != c.id) x
            else x.copy(entries = if (gone) x.entries.filter { it.scryfallId != e.scryfallId } else x.entries.map { if (it.scryfallId == e.scryfallId) smaller else it })
        }
    }
    return out
}

/** One copy of [scan]'s card taken out of the deck [deckId] — its real copies, not its proxies. Null when it couldn't be. */
private fun takeFromDeck(decks: List<Deck>, deckId: String, scan: CheckScan): List<Deck>? {
    val deck = decks.firstOrNull { it.id == deckId } ?: return null
    fun real(e: DeckCardEntry) = e.quantity - proxyCopies(deck, e) > 0
    val card = deck.cards.firstOrNull { it.scryfallId == scan.scryfallId && real(it) }
        ?: deck.cards.firstOrNull { sameCardName(it.name, scan.name) && real(it) } ?: return null
    val cards = if (card.quantity <= 1) deck.cards.filter { it !== card }
    else deck.cards.map { e ->
        if (e !== card) e else e.copy(quantity = e.quantity - 1, proxyQuantity = e.proxyQuantity?.let { minOf(it, e.quantity - 1) })
    }
    return decks.map { if (it.id == deckId) it.copy(cards = cards) else it }
}

/** Where a recorded card goes: the section checked, a binder's pocket (or none, for a binder in order), or just the place. */
private fun spotFor(collections: List<Collection>, scope: CheckScope): Spot {
    val place = placesOf(collections).firstOrNull { it.id == scope.placeId }
    if (scope.section != null) return Spot(scope.placeId, section = scope.section)
    if (place?.placeKind == PlaceKind.BINDER) return suggestSpot(place, null, collections).first
    return Spot(scope.placeId)
}

/** What Record them here did: the binders and decks after, how many were recorded and how many were left in their decks. */
data class Recorded(val collections: List<Collection>, val decks: List<Deck>, val recorded: Int, val leftInDecks: Int)

/**
 * Record them here: each extra scan given this place. A card listed in another section of this place
 * moves section; a copy with no place gets this one, else a copy from another place moves here (as
 * putting a card away does); a card only a deck has comes out of the deck only when [takeFromDecks];
 * one more than listed here, or one not in the collection, is added to the Unsorted pile, here.
 */
fun recordHere(collections: List<Collection>, decks: List<Deck>, scope: CheckScope, extras: List<CheckLine>, takeFromDecks: Boolean): Recorded {
    var cols = collections
    var ds = decks
    var recorded = 0
    var leftInDecks = 0
    for (x in extras) {
        if (x.kind == CheckKind.HERE) continue
        val scan = x.scan
        val foil = scan.foil ?: false
        val spot = spotFor(cols, scope)
        val newEntry = CollectionEntry(scan.scryfallId, scan.name, scan.imageUrl)
        if (x.kind == CheckKind.DECK) {
            val taken = if (takeFromDecks && x.deckId != null) takeFromDeck(ds, x.deckId, scan) else null
            if (taken == null) { leftInDecks++; continue }
            ds = taken
            cols = addedHere(cols, scan.scryfallId, spot, newEntry, foil).first
            recorded++
            continue
        }
        if (x.kind == CheckKind.SECTION) {
            val other = cardsIn(cols, scope.placeId).firstOrNull {
                isCard(scan, it.entry.scryfallId, it.entry.name) && !sameSection(it.line.section, scope.section) && (scan.foil == null || it.line.isFoil == scan.foil)
            }
            if (other != null) {
                cols = mapEntryOf(cols, other.collectionId, other.entry.scryfallId) { moveCopies(it, other.line, spot, 1).first }
                recorded++
                continue
            }
        }
        val o = putAway(cols, scan.scryfallId, scan.name, spot, newEntry, foil)
        cols = if (o.result == PutAwayResult.HERE) addedHere(cols, scan.scryfallId, spot, newEntry, foil).first else o.collections
        recorded++
    }
    return Recorded(cols, ds, recorded, leftInDecks)
}

/** Save results: the place was checked at [at]. */
fun markChecked(collections: List<Collection>, placeId: String, at: Long): List<Collection> {
    val place = placesOf(collections).firstOrNull { it.id == placeId } ?: return collections
    return savePlace(collections, place.copy(lastChecked = maxOf(at, place.lastChecked ?: 0L)))
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** The local date of [ms] as a day count, for counting days between two dates. */
private fun dayNumber(ms: Long): Long {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    val utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
    }
    return utc.timeInMillis / 86_400_000L
}

/** When a place was last checked, as the place page says it: "today", "yesterday", "3 days ago", "5 Oct 2026". */
fun lastCheckedLabel(at: Long, now: Long): String {
    val days = dayNumber(now) - dayNumber(at)
    if (days <= 0) return "today"
    if (days == 1L) return "yesterday"
    if (days < 7) return "$days days ago"
    val c = Calendar.getInstance().apply { timeInMillis = at }
    return "${c.get(Calendar.DAY_OF_MONTH)} ${MONTHS[c.get(Calendar.MONTH)]} ${c.get(Calendar.YEAR)}"
}

/**
 * The check going on now: what's being checked and the cards scanned so far, kept in memory while the
 * scanner runs and the results are open. One at a time; starting a check of another place starts
 * afresh. The web app keeps the same in the tab (src/collection/checkSession.ts).
 */
object CheckSessions {
    data class Session(val placeId: String, val section: String?, val scans: List<CheckScan>)

    @Volatile
    private var current: Session? = null

    /** The check of [placeId] going on now, or null. */
    fun load(placeId: String): Session? = current?.takeIf { it.placeId == placeId }

    fun save(session: Session) { current = session }

    fun clear() { current = null }
}
