package com.mtgcompanion.app.data

/*
 * Pull lists and put-back lists: building a deck out of storage, and taking it apart again.
 *
 * The pull list is every copy a deck still needs from storage, grouped as you'd walk round to fetch
 * them: place by place in the order of the Storage tree; within a box, section by section and then by
 * where the box's sorting rule files a card; within a binder, page by page and pocket by pocket. Then
 * the copies with no place yet, the ones only another deck holds (asked about each time), basic lands,
 * and the cards not owned at all.
 *
 * What a deck still needs: a deck you hold (Physical, or Proxy with real cards swapped in) is short
 * only of its proxies; any other deck is short of every copy except the ones marked as proxies.
 * "Move pulled into deck box" takes the pulled copies out of their binders (and off their places) —
 * they're the deck's real copies now, the way a physical deck's are (UnsortedPile.kt realCopiesOf) —
 * notes on the deck where each came from (Deck.cameFrom), and turns a deck that held nothing into a
 * physical one, the copies not pulled yet becoming its proxies.
 *
 * The put-back list is the reverse: every real copy in the deck, each with where it goes — where it
 * came from, or the first box whose sorting rule fits it — and "Done" puts them all in the Unsorted
 * pile at those places, leaving the deck as a virtual list.
 *
 * Pure, so it can be tested. Mirrors the web app's src/collection/pullList.ts rule for rule, with the
 * same tests (PullListTest.kt ↔ tests/collection/pullList.test.ts).
 */

private fun nameKey(name: String) = name.trim().lowercase()

/** Whether a deck holds real copies of its cards: Physical, or Proxy with real cards swapped in. */
val Deck.holdsCards: Boolean get() = ownershipType == DeckOwnership.PHYSICAL || ownershipType == DeckOwnership.PROXY

/** The deck's cards, its commanders among them (once). */
private fun deckEntries(deck: Deck): List<DeckCardEntry> {
    val out = deck.cards.toMutableList()
    for (c in listOfNotNull(deck.commander, deck.partnerCommander)) if (out.none { it.scryfallId == c.scryfallId }) out += c
    return out
}

/**
 * How many copies of [entry] the deck still needs from storage: a deck you hold, its proxies; any
 * other deck, every copy except the ones marked as proxies.
 */
fun stillToPull(deck: Deck, entry: DeckCardEntry): Int =
    if (deck.holdsCards) proxyCopies(deck, entry)
    else (entry.quantity - (entry.proxyQuantity ?: 0).coerceAtLeast(0)).coerceAtLeast(0)

/** A card the deck needs, by name: the copies of all its printings together. */
data class PullNeed(val name: String, val scryfallId: String, val qty: Int)

/** What the deck still needs from storage, a line per card name, A–Z. */
fun pullNeeds(deck: Deck): List<PullNeed> {
    val byName = LinkedHashMap<String, PullNeed>()
    for (e in deckEntries(deck)) {
        val n = stillToPull(deck, e)
        if (n <= 0) continue
        val key = nameKey(e.name)
        val had = byName[key]
        byName[key] = had?.copy(qty = had.qty + n) ?: PullNeed(e.name, e.scryfallId, n)
    }
    return byName.entries.sortedBy { it.key }.map { it.value }
}

// ---- Walking order ----

/** Each place's position in the Storage tree. */
private fun treeOrder(places: List<StoragePlace>): Map<String, Int> =
    placeTree(places).mapIndexed { i, n -> n.place.id to i }.toMap()

/** Where a section comes in its box: its own sections in order, then any others, then none. */
private fun sectionRank(place: StoragePlace?, section: String?): Int {
    if (section.isNullOrEmpty()) return 2000
    val i = place?.sections.orEmpty().indexOfFirst { it.equals(section, ignoreCase = true) }
    return if (i >= 0) i else 1000
}

private const val FAR = 1_000_000_000

/** Where a card is in its spot of a place: by page and pocket in a binder, by name in a box. */
private val inPlaceOrder: Comparator<Triple<String, Int?, Int?>> =
    compareBy<Triple<String, Int?, Int?>> { it.second ?: FAR }.thenBy { it.third ?: FAR }.thenBy { nameKey(it.first) }

/** The words for one spot of a place: "Page 2, slot 4" in a binder, "around “G”" in a sorted box. */
fun spotHint(place: StoragePlace?, page: Int?, slot: Int?, facts: CardFacts): String? {
    if (place == null) return null
    if (place.placeKind == PlaceKind.BINDER && page != null && page > 0 && slot != null && slot > 0) return pocketLabel(page, slot)
    return place.rule?.let { positionHint(it, facts) }
}

private data class PlaceGroupHead(val key: String, val title: String, val detail: String)

/** A group of rows that sits in one place (and section): its key, heading and the line under it. */
private fun placeGroup(places: List<StoragePlace>, placeId: String, section: String?): PlaceGroupHead {
    val place = places.firstOrNull { it.id == placeId }
    return PlaceGroupHead(
        key = "place:$placeId:${section ?: ""}",
        title = if (!section.isNullOrEmpty()) "${place?.name ?: ""} › $section" else place?.name ?: "",
        detail = parentsOf(places, placeId).joinToString(" › ") { it.name }
    )
}

/** Groups of place rows in walking order: places in tree order, then section by section. */
private fun <G> sortPlaceGroups(groups: List<G>, places: List<StoragePlace>, placeId: (G) -> String?, section: (G) -> String?): List<G> {
    val order = treeOrder(places)
    val byId = places.associateBy { it.id }
    return groups.sortedWith(
        compareBy<G> { order[placeId(it) ?: ""] ?: FAR }
            .thenBy { sectionRank(byId[placeId(it) ?: ""], section(it)) }
            .thenBy { section(it).orEmpty().lowercase() }
    )
}

// ---- The pull list ----

sealed interface PullSource {
    /** Copies kept at one spot of a place: [line] as it is in the binder entry. */
    data class Place(val collectionId: String, val scryfallId: String, val line: CopyPlace) : PullSource
    /** Copies owned with no place yet, in a binder or the Unsorted pile. */
    data class Loose(val collectionId: String, val scryfallId: String, val foil: Boolean) : PullSource
    /** Real copies in another deck you hold: taken only if you say so. */
    data class InDeck(val deckId: String) : PullSource
    /** Basic lands you don't keep track of: there's always a pile of them. */
    data object Basic : PullSource
    data object Missing : PullSource
}

enum class PullGroupKind { PLACE, LOOSE, DECK, BASIC, MISSING }

data class PullRow(
    /** Stays the same while the collection does, so a tick can be remembered. */
    val key: String,
    /** The card's name, as the deck has it. */
    val name: String,
    /** The deck's printing of it. */
    val scryfallId: String,
    val qty: Int,
    val source: PullSource,
    /** Where to look: "around “G”", "Page 2, slot 4", "In Unsorted", "in Atraxa — take it?". */
    val hint: String?,
    /** The same with its place, for the A–Z list: "Red box › Red · around “G”". */
    val where: String
)

data class PullGroup(
    val key: String,
    val kind: PullGroupKind,
    /** "Red box › Red", "Rares binder", "No place yet", "In another deck", "Basic lands", "Not owned". */
    val title: String,
    /** For a place, the places it sits in: "Shelf, study". */
    val detail: String,
    val placeId: String?,
    val section: String? = null,
    val rows: List<PullRow>
)

data class PullListData(
    val groups: List<PullGroup>,
    /** Copies to fetch: everything but the cards not owned. */
    val total: Int,
    /** Copies not owned. */
    val toBuy: Int,
    /** How many places the copies are in. */
    val places: Int
)

private class Source(val key: String, val name: String, val qty: Int, val source: PullSource, val rank: List<Int>)

private class GroupBuilder(val key: String, val kind: PullGroupKind, val title: String, val detail: String, val placeId: String?, val section: String?) {
    val rows = mutableListOf<PullRow>()
}

private val rankOrder = Comparator<Source> { a, b ->
    for (i in 0 until maxOf(a.rank.size, b.rank.size)) {
        val d = (a.rank.getOrNull(i) ?: 0) - (b.rank.getOrNull(i) ?: 0)
        if (d != 0) return@Comparator d
    }
    0
}

/** [e] without the lines in places that are gone (the same object when there are none). */
private fun knownOnly(e: CollectionEntry, known: Set<String>): CollectionEntry {
    val lines = placedCopies(e)
    return if (lines.all { it.placeId in known }) e else withPlaces(e, lines.filter { it.placeId in known })
}

/**
 * Every copy [deck] still needs (see pullNeeds), with where to fetch it from, grouped in walking
 * order. Each copy is found once: in a place first (in tree order), then with no place (the Unsorted
 * pile's before the binders'), then in another deck you hold; what's left is a basic land or a card
 * not owned. Wishlists, and copies tagged as lent out, aren't fetched from.
 */
fun pullList(deck: Deck, collections: List<Collection>, decks: List<Deck>): PullListData {
    val places = placesOf(collections)
    val byId = places.associateBy { it.id }
    val order = treeOrder(places)
    val owned = collections.filter { it.kind != CollectionType.WISHLIST }
    val piles = owned.filter { it.isUnsorted } + owned.filter { !it.isUnsorted }
    val sources = mutableListOf<Source>()
    piles.forEachIndexed { at, c ->
        for (e in c.entries) {
            if (e.quantity + e.foilQuantity <= 0 || lentTag(e) != null) continue
            for (line in placedCopies(e)) {
                if (line.placeId !in byId) continue
                sources += Source(
                    "p:${c.id}:${e.scryfallId}:${copyKey(line)}", e.name, line.qty, PullSource.Place(c.id, e.scryfallId, line),
                    listOf(0, order[line.placeId] ?: 0, sectionRank(byId[line.placeId], line.section), line.page ?: 0, line.slot ?: 0, if (line.isFoil) 1 else 0)
                )
            }
            // Copies in a place that's gone have no place any more.
            val (plain, foil) = unplacedCopies(knownOnly(e, byId.keys))
            if (plain > 0) sources += Source("l:${c.id}:${e.scryfallId}:", e.name, plain, PullSource.Loose(c.id, e.scryfallId, false), listOf(1, 0, at))
            if (foil > 0) sources += Source("l:${c.id}:${e.scryfallId}:foil", e.name, foil, PullSource.Loose(c.id, e.scryfallId, true), listOf(1, 1, at))
        }
    }
    decks.forEachIndexed { i, d ->
        if (d.id == deck.id || !d.holdsCards) return@forEachIndexed
        val held = LinkedHashMap<String, Pair<String, Int>>()
        for (e in d.cards) {
            val real = e.quantity - proxyCopies(d, e)
            if (real <= 0) continue
            val key = nameKey(e.name)
            val had = held[key]
            held[key] = if (had != null) had.first to had.second + real else e.name to real
        }
        for ((key, h) in held) sources += Source("d:${d.id}:$key", h.first, h.second, PullSource.InDeck(d.id), listOf(2, i))
    }
    val sorted = sources.sortedWith(rankOrder)
    val left = sorted.associate { it.key to it.qty }.toMutableMap()

    val groups = LinkedHashMap<String, GroupBuilder>()
    fun groupFor(kind: PullGroupKind, key: String, make: () -> GroupBuilder): GroupBuilder = groups.getOrPut(key, make)
    fun deckName(id: String) = decks.firstOrNull { it.id == id }?.name ?: "another deck"
    fun collectionName(id: String) = collections.firstOrNull { it.id == id }?.name ?: "a binder"

    for (need in pullNeeds(deck)) {
        var wanted = need.qty
        val facts = CardFacts(need.name)
        for (s in sorted) {
            if (wanted <= 0) break
            val have = left[s.key] ?: 0
            if (have <= 0 || !sameCardName(s.name, need.name)) continue
            val take = minOf(have, wanted)
            left[s.key] = have - take
            wanted -= take
            val key = "${nameKey(need.name)}|${s.key}"
            when (val src = s.source) {
                is PullSource.Place -> {
                    val head = placeGroup(places, src.line.placeId, src.line.section)
                    val g = groupFor(PullGroupKind.PLACE, head.key) {
                        GroupBuilder(head.key, PullGroupKind.PLACE, head.title, head.detail, src.line.placeId, src.line.section?.takeIf { it.isNotEmpty() })
                    }
                    val spot = spotHint(byId[src.line.placeId], src.line.page, src.line.slot, facts)
                    val hint = listOfNotNull(spot, if (src.line.isFoil) "foil" else null).joinToString(" · ").ifEmpty { null }
                    g.rows += PullRow(key, need.name, need.scryfallId, take, src, hint, listOfNotNull(g.title.ifEmpty { null }, hint).joinToString(" · "))
                }
                is PullSource.Loose -> {
                    val g = groupFor(PullGroupKind.LOOSE, "loose") { GroupBuilder("loose", PullGroupKind.LOOSE, "No place yet", "Owned, not put away", null, null) }
                    val hint = "In ${collectionName(src.collectionId)}${if (src.foil) " · foil" else ""}"
                    g.rows += PullRow(key, need.name, need.scryfallId, take, src, hint, hint)
                }
                is PullSource.InDeck -> {
                    val g = groupFor(PullGroupKind.DECK, "deck") { GroupBuilder("deck", PullGroupKind.DECK, "In another deck", "Only another deck has it", null, null) }
                    g.rows += PullRow(key, need.name, need.scryfallId, take, src, "in ${deckName(src.deckId)} — take it?", "In ${deckName(src.deckId)}")
                }
                else -> Unit
            }
        }
        if (wanted <= 0) continue
        if (isBasicLand(need.name)) {
            val g = groupFor(PullGroupKind.BASIC, "basic") { GroupBuilder("basic", PullGroupKind.BASIC, "Basic lands", "From your basics", null, null) }
            g.rows += PullRow("${nameKey(need.name)}|b", need.name, need.scryfallId, wanted, PullSource.Basic, null, "Basic lands")
        } else {
            val g = groupFor(PullGroupKind.MISSING, "missing") { GroupBuilder("missing", PullGroupKind.MISSING, "Not owned", "", null, null) }
            g.rows += PullRow("${nameKey(need.name)}|m", need.name, need.scryfallId, wanted, PullSource.Missing, null, "Not owned")
        }
    }

    val built = groups.values.map { g ->
        val rows = if (g.kind == PullGroupKind.PLACE) {
            g.rows.sortedWith(compareBy(inPlaceOrder) { r ->
                val line = (r.source as? PullSource.Place)?.line
                Triple(r.name, line?.page, line?.slot)
            })
        } else {
            g.rows.sortedBy { nameKey(it.name) }
        }
        PullGroup(g.key, g.kind, g.title, g.detail, g.placeId, g.section, rows)
    }
    val inPlaces = sortPlaceGroups(built.filter { it.kind == PullGroupKind.PLACE }, places, { it.placeId }, { it.section })
    val rest = listOf(PullGroupKind.LOOSE, PullGroupKind.DECK, PullGroupKind.BASIC, PullGroupKind.MISSING).flatMap { k -> built.filter { it.kind == k } }
    val rows = built.flatMap { it.rows }
    return PullListData(
        groups = inPlaces + rest,
        total = rows.filter { it.source != PullSource.Missing }.sumOf { it.qty },
        toBuy = rows.filter { it.source == PullSource.Missing }.sumOf { it.qty },
        places = inPlaces.mapNotNull { it.placeId }.distinct().size
    )
}

/** Every row of the list that can be pulled (not the cards not owned), A–Z. */
fun pullRowsAZ(list: PullListData): List<PullRow> =
    list.groups.filter { it.kind != PullGroupKind.MISSING }.flatMap { it.rows }
        .sortedWith(compareBy<PullRow> { nameKey(it.name) }.thenBy { it.where })

/** The groups of the list kept in [placeId] or a place inside it — "Pull from here" on a box's label. */
fun pullGroupsIn(list: PullListData, collections: List<Collection>, placeId: String): List<PullGroup> {
    val inside = placeAndInside(placesOf(collections), placeId)
    return list.groups.filter { it.kind == PullGroupKind.PLACE && it.placeId != null && it.placeId in inside }
}

/** Copies ticked off, of the rows that can be pulled. */
fun pulledCopies(rows: List<PullRow>, ticked: Set<String>): Int =
    rows.filter { it.source != PullSource.Missing && it.key in ticked }.sumOf { it.qty }

/**
 * The row a scanned card called [name] ticks: the first not ticked yet — not a card not owned, and
 * not one in another deck unless that's all there is (the list asks before taking one).
 */
fun pullRowToTick(rows: List<PullRow>, ticked: Set<String>, name: String): PullRow? {
    val open = rows.filter { it.key !in ticked && it.source != PullSource.Missing && sameCardName(it.name, name) }
    return open.firstOrNull { it.source !is PullSource.InDeck } ?: open.firstOrNull()
}

/** The same for the put-back list. */
fun putBackRowToTick(rows: List<PutBackRow>, ticked: Set<String>, name: String): PutBackRow? =
    rows.firstOrNull { it.key !in ticked && sameCardName(it.name, name) }

/** The cards not owned as a buy list, "2 Sol Ring" a line — the text "Copy buy list" copies everywhere. */
fun pullBuyList(list: PullListData): String =
    buildCardListText(list.groups.filter { it.kind == PullGroupKind.MISSING }.flatMap { it.rows }
        .map { CollectionEntry(it.scryfallId, it.name, null, quantity = it.qty) })

/**
 * "Mark as proxies": the cards not owned become proxies in a deck being built, so the list stops
 * asking for them. Only for a deck that doesn't hold its cards yet — in one that does, a card not
 * owned is a proxy already. The same deck when nothing changes.
 */
fun markMissingAsProxies(deck: Deck, list: PullListData): Deck {
    if (deck.holdsCards) return deck
    val missing = HashMap<String, Int>()
    for (r in list.groups.filter { it.kind == PullGroupKind.MISSING }.flatMap { it.rows }) missing[nameKey(r.name)] = (missing[nameKey(r.name)] ?: 0) + r.qty
    if (missing.isEmpty()) return deck
    val cards = deck.cards.map { e ->
        val want = missing[nameKey(e.name)] ?: 0
        val marked = (e.proxyQuantity ?: 0).coerceAtLeast(0)
        val take = minOf(want, e.quantity - marked)
        if (take <= 0) return@map e
        missing[nameKey(e.name)] = want - take
        e.copy(proxyQuantity = marked + take)
    }
    return withCommandersFrom(deck.copy(cards = cards))
}

/** The deck's commanders as its cards now have them. */
private fun withCommandersFrom(deck: Deck): Deck {
    fun fresh(c: DeckCardEntry?) = c?.let { deck.cards.firstOrNull { e -> e.scryfallId == it.scryfallId } ?: it }
    return deck.copy(commander = fresh(deck.commander), partnerCommander = fresh(deck.partnerCommander))
}

/** One card taken from another deck by "Move pulled into deck box". */
data class TakenFromDeck(val deckId: String, val deck: String, val name: String, val qty: Int)

data class MovePulledResult(
    val collections: List<Collection>,
    val decks: List<Deck>,
    /** Copies now in the deck. */
    val moved: Int,
    /** Cards taken out of other decks: each is a proxy there now, until a copy goes back. */
    val taken: List<TakenFromDeck>,
    /** The deck held no cards before, and is a physical deck now. */
    val nowPhysical: Boolean,
    /** Proxies the deck has after: the copies still to pull. */
    val proxies: Int
)

private fun proxiesOf(deck: Deck) = deck.cards.sumOf { proxyCopies(deck, it) }

/**
 * "Move pulled into deck box": the copies of the [ticked] rows go into the deck. Copies from a place
 * or with no place leave their binder (and their place — where each came from is noted on the deck,
 * see Deck.cameFrom); one taken from another deck becomes a proxy there, so that deck's list is still
 * whole and says it's a card short; a basic land just counts. The deck counts the copies as real — a
 * deck that held nothing becomes a physical one, its copies not pulled yet its proxies. Copies that
 * have gone since the list was made are skipped. Unchanged when nothing moves.
 */
fun movePulled(deck: Deck, list: PullListData, ticked: Set<String>, collections: List<Collection>, decks: List<Deck>): MovePulledResult {
    var cols = collections
    var others = decks
    val pulled = HashMap<String, Int>()
    val came = mutableListOf<CameFrom>()
    val taken = mutableListOf<TakenFromDeck>()
    fun note(name: String, n: Int) { pulled[nameKey(name)] = (pulled[nameKey(name)] ?: 0) + n }
    val known = placesOf(collections).map { it.id }.toSet()
    fun entryOf(collectionId: String, scryfallId: String) = cols.firstOrNull { it.id == collectionId }?.entries?.firstOrNull { it.scryfallId == scryfallId }
    fun setEntry(collectionId: String, scryfallId: String, to: CollectionEntry?) {
        cols = cols.map { c ->
            if (c.id != collectionId) c
            else c.copy(entries = c.entries.mapNotNull { if (it.scryfallId == scryfallId) to else it })
        }
    }
    fun fewer(e: CollectionEntry, n: Int, foil: Boolean): CollectionEntry? {
        val out = tidied(if (foil) e.copy(foilQuantity = e.foilQuantity - n) else e.copy(quantity = e.quantity - n))
        return if (out.quantity + out.foilQuantity > 0) out else null
    }

    for (row in list.groups.flatMap { it.rows }) {
        if (row.key !in ticked) continue
        when (val src = row.source) {
            is PullSource.Place -> {
                val e = entryOf(src.collectionId, src.scryfallId) ?: continue
                val (entry, moved) = moveCopies(e, src.line, null, row.qty)
                if (moved <= 0) continue
                setEntry(src.collectionId, src.scryfallId, fewer(entry, moved, src.line.isFoil))
                note(row.name, moved)
                val l = copyPlace(src.line.spot, moved, src.line.isFoil)
                came += CameFrom(row.name, l.placeId, l.qty, l.foil, l.section, l.page, l.slot)
            }
            is PullSource.Loose -> {
                val e = entryOf(src.collectionId, src.scryfallId) ?: continue
                val clean = knownOnly(e, known)
                val (plain, foil) = unplacedCopies(clean)
                val n = minOf(row.qty, if (src.foil) foil else plain)
                if (n <= 0) continue
                setEntry(src.collectionId, src.scryfallId, fewer(clean, n, src.foil))
                note(row.name, n)
            }
            is PullSource.InDeck -> {
                val other = others.firstOrNull { it.id == src.deckId } ?: continue
                var want = row.qty
                val cards = other.cards.map { e ->
                    if (want <= 0 || !sameCardName(e.name, row.name)) return@map e
                    val proxies = proxyCopies(other, e)
                    val take = minOf(want, e.quantity - proxies)
                    if (take <= 0) return@map e
                    want -= take
                    e.copy(proxyQuantity = proxies + take)
                }
                val n = row.qty - want
                if (n <= 0) continue
                val changed = withCommandersFrom(other.copy(cards = cards))
                others = others.map { if (it.id == other.id) changed else it }
                taken += TakenFromDeck(other.id, other.name, row.name, n)
                note(row.name, n)
            }
            PullSource.Basic -> note(row.name, row.qty)
            PullSource.Missing -> Unit
        }
    }
    val moved = pulled.values.sum()
    if (moved == 0) return MovePulledResult(collections, decks, 0, emptyList(), false, proxiesOf(deck))

    val wasHeld = deck.holdsCards
    val cards = deck.cards.map { e ->
        val left = pulled[nameKey(e.name)] ?: 0
        if (wasHeld) {
            val proxies = proxyCopies(deck, e)
            val take = minOf(left, proxies)
            if (take <= 0) return@map e
            pulled[nameKey(e.name)] = left - take
            withProxies(e, proxies - take, deck.ownershipType == DeckOwnership.PROXY)
        } else {
            val marked = (e.proxyQuantity ?: 0).coerceAtLeast(0)
            val take = minOf(left, (e.quantity - marked).coerceAtLeast(0))
            pulled[nameKey(e.name)] = left - take
            withProxies(e, e.quantity - take, false)
        }
    }
    val cameFrom = if (came.isNotEmpty() || deck.cameFrom != null) tidyCameFrom(deck.cameFrom.orEmpty() + came) else null
    val next = withCommandersFrom(
        deck.copy(
            cards = cards,
            ownership = if (wasHeld) deck.ownership else DeckOwnership.PHYSICAL.name,
            cameFrom = cameFrom
        )
    )
    return MovePulledResult(
        collections = cols,
        decks = others.map { if (it.id == deck.id) next else it },
        moved = moved,
        taken = taken,
        nowPhysical = !wasHeld,
        proxies = proxiesOf(next)
    )
}

/** [e] with [n] proxies; in a Physical deck none is no key at all, in a Proxy deck it has to say 0. */
private fun withProxies(e: DeckCardEntry, n: Int, explicit: Boolean): DeckCardEntry =
    if (n > 0 || explicit) e.copy(proxyQuantity = n.coerceAtLeast(0)) else e.copy(proxyQuantity = null)

// ---- Where a deck's copies came from ----

/** A cameFrom line as both apps write it: optional fields null when not said. */
fun cameFromLine(name: String, line: CopyPlace): CameFrom {
    val l = copyPlace(line.spot, line.qty, line.isFoil)
    return CameFrom(name, l.placeId, l.qty, l.foil, l.section, l.page, l.slot)
}

/** [lines] as kept: one line per card, spot and finish, none at zero. */
fun tidyCameFrom(lines: List<CameFrom>): List<CameFrom> {
    val out = mutableListOf<CameFrom>()
    for (l in lines) {
        if (l.qty <= 0) continue
        val i = out.indexOfFirst { nameKey(it.name) == nameKey(l.name) && copyKey(it.line) == copyKey(l.line) }
        if (i >= 0) out[i] = out[i].copy(qty = out[i].qty + l.qty)
        else out += cameFromLine(l.name, l.line)
    }
    return out
}

/**
 * Merges two devices' cameFrom card by card, each card's lines as a binder entry's places merge (see
 * mergeCopyPlaces). Null when no side has the key.
 */
fun mergeCameFrom(base: List<CameFrom>?, mine: List<CameFrom>?, theirs: List<CameFrom>?): List<CameFrom>? {
    if (base == null && mine == null && theirs == null) return null
    fun split(list: List<CameFrom>?): Map<String, Pair<String, List<CopyPlace>>> {
        val m = LinkedHashMap<String, Pair<String, List<CopyPlace>>>()
        for (l in list.orEmpty()) {
            val had = m[nameKey(l.name)]
            m[nameKey(l.name)] = if (had != null) had.first to had.second + l.line else l.name to listOf(l.line)
        }
        return m
    }
    val b = split(base)
    val m = split(mine)
    val t = split(theirs)
    val out = mutableListOf<CameFrom>()
    for (key in (b.keys + m.keys + t.keys).distinct().sorted()) {
        val name = (t[key] ?: m[key] ?: b[key])!!.first
        val lines = mergeCopyPlaces(b[key]?.second.orEmpty(), m[key]?.second.orEmpty(), t[key]?.second.orEmpty()).orEmpty()
        for (line in lines) out += cameFromLine(name, line)
    }
    return out
}

/**
 * [theirs] with [source]'s cameFrom put back when [theirs] was written by an app that doesn't know
 * about it (no "cameFrom" key) — the same object otherwise. See Deck.cameFrom.
 */
fun keepCameFromFromOlderApp(source: Deck, theirs: Deck): Deck =
    if (theirs.cameFrom != null || source.cameFrom == null) theirs else theirs.copy(cameFrom = source.cameFrom)

// ---- The put-back list ----

/** Where the cards go: where each came from, or the best place by the boxes' sorting rules. */
enum class PutBackMode { ORIGIN, RULE }

data class PutBackRow(
    /** The deck's card and which of its rows: stays the same while the deck does. */
    val key: String,
    val name: String,
    val scryfallId: String,
    val qty: Int,
    /** It came out of a binder as a foil copy, so it goes back as one. */
    val foil: Boolean,
    /** Where it goes; null: no place yet. */
    val dest: Spot?,
    /** "around “G”", "Page 2, slot 4"; for a basic land, where they go. */
    val hint: String?,
    /** The place it came from (not a rule's suggestion). */
    val fromOrigin: Boolean
)

enum class PutBackGroupKind { PLACE, NONE, BASIC }

data class PutBackGroup(
    val key: String,
    val kind: PutBackGroupKind,
    val title: String,
    val detail: String,
    val placeId: String?,
    val section: String? = null,
    val rows: List<PutBackRow>
)

data class PutBackListData(val groups: List<PutBackGroup>, val total: Int)

/**
 * The best place by rule for a card: the first box (in tree order) with a sorting rule it fits — one
 * with no sections takes any card, one with sections only cards its rule files in one of them.
 * Binders aren't sorted by rule. Null when none fits.
 */
fun bestPlaceByRule(places: List<StoragePlace>, facts: CardFacts, collections: List<Collection>): Pair<Spot, StoragePlace>? {
    for (node in placeTree(places)) {
        val place = node.place
        val rule = place.rule ?: continue
        if (place.placeKind == PlaceKind.BINDER) continue
        val sections = place.sections.orEmpty()
        val section = ruleSection(rule, facts, sections)
        if (sections.isNotEmpty() && !(section != null && sections.any { it.equals(section, ignoreCase = true) })) continue
        return suggestSpot(place, facts, collections).first to place
    }
    return null
}

/** "Mountains", "Plains", "Snow-Covered Islands". */
fun basicsTitle(name: String): String = name.trim().let { if (it.endsWith("s", ignoreCase = true)) it else "${it}s" }

/**
 * Every real copy in [deck], each with where it goes: in ORIGIN mode, where it came from when it was
 * pulled (Deck.cameFrom) while that place is still there; otherwise, and in RULE mode, the best place
 * by rule (bestPlaceByRule), or no place when none fits. [facts] gives what a rule needs to know of a
 * card (its colours, its type); its name alone otherwise. Grouped in walking order, then the copies
 * with no place, then the basic lands, a group for each.
 */
fun putBackList(deck: Deck, collections: List<Collection>, mode: PutBackMode, facts: ((DeckCardEntry) -> CardFacts?)? = null): PutBackListData {
    val places = placesOf(collections)
    val byId = places.associateBy { it.id }
    val origins = if (mode == PutBackMode.ORIGIN) deck.cameFrom.orEmpty().filter { it.placeId in byId }.toMutableList() else mutableListOf()
    val rows = mutableListOf<Pair<PutBackRow, Boolean>>()
    if (deck.holdsCards) {
        for (e in deck.cards) {
            var left = e.quantity - proxyCopies(deck, e)
            if (left <= 0) continue
            val f = facts?.invoke(e) ?: CardFacts(e.name, typeLine = e.typeLine)
            var n = 0
            fun add(qty: Int, foil: Boolean, dest: Spot?, fromOrigin: Boolean) {
                val place = dest?.let { byId[it.placeId] }
                rows += PutBackRow("${e.scryfallId}|${n++}", e.name, e.scryfallId, qty, foil, dest,
                    dest?.let { spotHint(place, it.page, it.slot, f) }, fromOrigin) to isBasicLand(e.name)
            }
            for (i in origins.indices) {
                if (left <= 0) break
                val o = origins[i]
                if (o.qty <= 0 || !sameCardName(o.name, e.name)) continue
                val take = minOf(o.qty, left)
                origins[i] = o.copy(qty = o.qty - take)
                left -= take
                add(take, o.isFoilLine, Spot(o.placeId, o.section?.takeIf { it.isNotEmpty() }, o.page?.takeIf { it > 0 }, o.slot?.takeIf { it > 0 }), true)
            }
            if (left > 0) add(left, false, bestPlaceByRule(places, f, collections)?.first, false)
        }
    }
    fun destTitle(dest: Spot?) = if (dest != null) placeGroup(places, dest.placeId, dest.section).title else "No place yet"
    val groups = LinkedHashMap<String, Triple<PutBackGroupKind, PlaceGroupHead, MutableList<PutBackRow>>>()
    val groupPlace = HashMap<String, Pair<String?, String?>>()
    for ((row, basic) in rows) {
        when {
            basic -> {
                val key = "basic:${nameKey(row.name)}"
                groups.getOrPut(key) { Triple(PutBackGroupKind.BASIC, PlaceGroupHead(key, basicsTitle(row.name), ""), mutableListOf()) }
                    .third += row.copy(hint = destTitle(row.dest))
            }
            row.dest != null -> {
                val head = placeGroup(places, row.dest.placeId, row.dest.section)
                groupPlace[head.key] = row.dest.placeId to row.dest.section
                groups.getOrPut(head.key) { Triple(PutBackGroupKind.PLACE, head, mutableListOf()) }.third += row
            }
            else -> groups.getOrPut("none") { Triple(PutBackGroupKind.NONE, PlaceGroupHead("none", "No place yet", "No box rule fits these"), mutableListOf()) }.third += row
        }
    }
    val all = groups.values.map { (kind, head, list) ->
        val sorted = when (kind) {
            PutBackGroupKind.PLACE -> list.sortedWith(compareBy(inPlaceOrder) { Triple(it.name, it.dest?.page, it.dest?.slot) })
            PutBackGroupKind.NONE -> list.sortedBy { nameKey(it.name) }
            PutBackGroupKind.BASIC -> list
        }
        val detail = if (kind == PutBackGroupKind.BASIC) "${sorted.map { it.hint }.distinct().joinToString(", ")} · or keep with the deck box" else head.detail
        val (placeId, section) = groupPlace[head.key] ?: (null to null)
        PutBackGroup(head.key, kind, head.title, detail, placeId, section?.takeIf { it.isNotEmpty() }, sorted)
    }
    return PutBackListData(
        groups = sortPlaceGroups(all.filter { it.kind == PutBackGroupKind.PLACE }, places, { it.placeId }, { it.section }) +
            all.filter { it.kind == PutBackGroupKind.NONE } +
            all.filter { it.kind == PutBackGroupKind.BASIC }.sortedBy { it.key },
        total = rows.sumOf { it.first.qty }
    )
}

private val CameFrom.isFoilLine: Boolean get() = foil == true

data class TakeApartResult(
    val collections: List<Collection>,
    val deck: Deck,
    /** Copies put in a place, and copies with none. */
    val placed: Int,
    val unplaced: Int
)

/**
 * "Done: deck taken apart": every copy on [list] goes back into the collection as the Unsorted pile's
 * — as a deck's cards do when it's deleted with its cards kept (intoPile) — each given its place. The
 * deck stays, as a virtual list: its proxies and where its cards came from are cleared, so building
 * it again starts afresh.
 */
fun takeApart(deck: Deck, list: PutBackListData, collections: List<Collection>): TakeApartResult {
    val withPile = withUnsortedPile(collections)
    var pile = withPile.first { it.isUnsorted }.entries
    var placed = 0
    var unplaced = 0
    for (row in list.groups.flatMap { it.rows }) {
        val e = deck.cards.firstOrNull { it.scryfallId == row.scryfallId } ?: continue
        if (row.qty <= 0) continue
        pile = intoPile(pile, listOf(pileEntryOf(e, if (row.foil) 0 else row.qty).copy(foilQuantity = if (row.foil) row.qty else 0)))
        val dest = row.dest
        if (dest != null) {
            val at = pile.indexOfFirst { it.scryfallId == row.scryfallId }
            val (entry, moved) = placeCopies(pile[at], dest, row.qty, row.foil)
            pile = pile.mapIndexed { i, x -> if (i == at) entry else x }
            placed += moved
            unplaced += row.qty - moved
        } else {
            unplaced += row.qty
        }
    }
    val out = withPile.map { if (it.isUnsorted) it.copy(entries = pile) else it }
    val next = deck.copy(
        ownership = DeckOwnership.VIRTUAL.name,
        cards = deck.cards.map { it.copy(proxyQuantity = null) },
        commander = deck.commander?.copy(proxyQuantity = null),
        partnerCommander = deck.partnerCommander?.copy(proxyQuantity = null),
        cameFrom = if (deck.cameFrom != null) emptyList() else null
    )
    return TakeApartResult(out, next, placed, unplaced)
}
