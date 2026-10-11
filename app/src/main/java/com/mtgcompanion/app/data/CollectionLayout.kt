package com.mtgcompanion.app.data

import java.text.NumberFormat
import java.util.Locale

/*
 * The Collection's layout: one header and four tabs — Cards, Binders, Storage, Wishlist — always there,
 * no tile home in front. Here, pure so it can be tested:
 *  - the tabs, and how the old routes (?tab=all, sets, shared, the home's tiles) map onto them;
 *  - where a card's copies are, as the chips on a card row say it ("Blue box › Instants ×2",
 *    "Talrand deck ×1", "Lent to Sam ×1", "No place yet · Put away") — one formatter for every row;
 *  - the Cards tab's Group by (none, set, binder, place) and its quick filters (Lent out, For sale,
 *    No place yet, Graded);
 *  - the small counts on the Binders and Storage rows ("kept in 3 places", "in 2 binders").
 * Mirrors the web app's src/collection/collectionLayout.ts rule for rule (tests: CollectionLayoutTest.kt
 * ↔ tests/collection/collectionLayout.test.ts, the same cases).
 */

/** The Collection's four tabs, in order. [key] is the web app's ?tab= value and what's remembered. */
enum class CollectionTab(val key: String, val label: String) {
    CARDS("cards", "Cards"), BINDERS("binders", "Binders"), STORAGE("storage", "Storage"), WISHLIST("wishlist", "Wishlist");
    companion object {
        fun fromKey(key: String?): CollectionTab? = entries.firstOrNull { it.key == key }
    }
}

/** The Cards tab's Group by. */
enum class CardGroupBy(val key: String, val label: String) {
    NONE("none", "None"), SET("set", "Set"), BINDER("binder", "Binder"), PLACE("place", "Place");
    companion object {
        fun fromKey(key: String?): CardGroupBy = entries.firstOrNull { it.key == key } ?: NONE
    }
}

/** The Cards tab's quick filters; one at a time. [manage]: the full screen its "Manage…" opens. */
enum class QuickFilter(val key: String, val label: String, val manage: String) {
    LENT_OUT("lent", "Lent out", "Loans"),
    FOR_SALE("sale", "For sale", "To sell"),
    NO_PLACE("noplace", "No place yet", "Put cards away"),
    GRADED("graded", "Graded", "Graded cards");
    companion object {
        fun fromKey(key: String?): QuickFilter? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Where the Collection opens: a tab, and on Cards a grouping or quick filter to start with. [shared]:
 * what friends share (no longer a tab — it opens from the header's menu).
 */
data class CollectionRoute(
    val tab: CollectionTab,
    val group: CardGroupBy? = null,
    val quick: QuickFilter? = null,
    val shared: Boolean = false
)

/**
 * The tab an old or new route means: the old tabs (all, binders, storage, sets, shared) and the old
 * home's tiles (sealed, loans, sell, graded, wishlist) keep working. Null: no tab said (open the one used
 * last) or one not known.
 */
fun collectionRoute(tab: String?): CollectionRoute? = when (tab?.trim()?.lowercase()) {
    "cards", "all" -> CollectionRoute(CollectionTab.CARDS)
    "sets" -> CollectionRoute(CollectionTab.CARDS, group = CardGroupBy.SET)
    "binders" -> CollectionRoute(CollectionTab.BINDERS)
    "goals" -> CollectionRoute(CollectionTab.BINDERS)
    "storage", "sealed" -> CollectionRoute(CollectionTab.STORAGE)
    "wishlist" -> CollectionRoute(CollectionTab.WISHLIST)
    "loans", "lent" -> CollectionRoute(CollectionTab.CARDS, quick = QuickFilter.LENT_OUT)
    "sell", "sale" -> CollectionRoute(CollectionTab.CARDS, quick = QuickFilter.FOR_SALE)
    "graded" -> CollectionRoute(CollectionTab.CARDS, quick = QuickFilter.GRADED)
    "noplace" -> CollectionRoute(CollectionTab.CARDS, quick = QuickFilter.NO_PLACE)
    "shared" -> CollectionRoute(CollectionTab.CARDS, shared = true)
    else -> null
}

/** The tab to open: the route's, else the one used last, else Cards. */
fun startTab(route: CollectionRoute?, remembered: String?): CollectionTab =
    route?.tab ?: CollectionTab.fromKey(remembered) ?: CollectionTab.CARDS

// ---- Where a card's copies are: the chips on a card row ----

enum class LocationKind { PLACE, DECK, LENT, NONE }

/**
 * Some of a card's copies in one spot. [place]: the place's name; [section] a box's section; [page] the
 * first binder page they're on; [deckId] a deck; [loanTo] who has them (null: tagged "lent" with no loan).
 */
data class LocationChip(
    val kind: LocationKind,
    val qty: Int,
    val place: String? = null,
    val placeId: String? = null,
    val section: String? = null,
    val page: Int? = null,
    val deck: String? = null,
    val deckId: String? = null,
    val loanTo: String? = null,
    val loanId: String? = null
)

private fun count(n: Int): String = NumberFormat.getIntegerInstance(Locale.UK).format(n)

/** A chip's words: "Blue box › Instants ×2", "Talrand deck ×1", "Lent to Sam ×1", "No place yet · Put away". */
fun chipText(chip: LocationChip): String = when (chip.kind) {
    LocationKind.PLACE -> (if (chip.section != null) "${chip.place} › ${chip.section}" else chip.place.orEmpty()) + " ×${count(chip.qty)}"
    LocationKind.DECK -> "${deckTitle(chip.deck.orEmpty())} ×${count(chip.qty)}"
    LocationKind.LENT -> (if (chip.loanTo != null) "Lent to ${chip.loanTo}" else "Lent out") + " ×${count(chip.qty)}"
    LocationKind.NONE -> "No place yet · Put away"
}

private fun copies(n: Int) = "${count(n)} ${if (n == 1) "copy" else "copies"}"

/** What a screen reader says for a chip: "Stored in Blue box, Instants section, 2 copies". */
fun chipDescription(chip: LocationChip): String = when (chip.kind) {
    LocationKind.PLACE -> "Stored in ${chip.place}" + (chip.section?.let { ", $it section" } ?: "") + (chip.page?.let { ", page $it" } ?: "") + ", ${copies(chip.qty)}"
    LocationKind.DECK -> "In ${deckTitle(chip.deck.orEmpty())}, ${copies(chip.qty)}"
    LocationKind.LENT -> (if (chip.loanTo != null) "Lent to ${chip.loanTo}" else "Lent out") + ", ${copies(chip.qty)}"
    LocationKind.NONE -> "No place yet, ${copies(chip.qty)}. Put away"
}

/** What a screen reader says for a Find anything chip, in the same words as a row's (chipDescription). */
fun findChipDescription(chip: FindChip): String = when (chip.kind) {
    FindChipKind.PLACE -> {
        val parts = chip.label.split(" › ")
        "Stored in ${parts[0]}" + (if (parts.size > 1) ", ${parts.drop(1).joinToString(" › ")} section" else "") + ", ${copies(chip.qty)}"
    }
    FindChipKind.DECK -> "In ${chip.label}, ${copies(chip.qty)}"
    FindChipKind.NONE -> "No place yet, ${copies(chip.qty)}"
    else -> "${chip.label}, ${copies(chip.qty)}"
}

/** At most this many chips on a row, then "+N". */
const val ROW_CHIPS_MAX = 2

/**
 * The chips a row shows and how many more there are ("+N"). The amber No place yet chip is always
 * shown — it's what to do about the card — taking the last of the [max] spots when there are others.
 */
fun shownChips(chips: List<LocationChip>, max: Int = ROW_CHIPS_MAX): Pair<List<LocationChip>, Int> {
    val none = chips.firstOrNull { it.kind == LocationKind.NONE }
    val rest = chips.filter { it.kind != LocationKind.NONE }
    val room = (if (none != null) max - 1 else max).coerceAtLeast(0)
    val shown = rest.take(room) + listOfNotNull(none)
    return shown to (chips.size - shown.size)
}

/** "+3", said: "3 more places". */
fun moreChipsDescription(more: Int): String = "$more more ${if (more == 1) "place" else "places"}"

private val ORDER = listOf(LocationKind.PLACE, LocationKind.DECK, LocationKind.LENT, LocationKind.NONE)

private class ChipBuild {
    val chips = LinkedHashMap<String, LocationChip>()
    fun add(c: LocationChip) {
        if (c.qty <= 0) return
        val key = "${c.kind}|${c.placeId ?: ""}|${c.section ?: ""}|${c.deckId ?: ""}|${c.loanId ?: ""}|${c.loanTo ?: ""}"
        val had = chips[key]
        chips[key] = if (had == null) c else had.copy(qty = had.qty + c.qty, page = listOfNotNull(had.page, c.page).minOrNull())
    }
    fun list(): List<LocationChip> = chips.values.sortedBy { ORDER.indexOf(it.kind) }
}

private fun nameKey(name: String) = name.trim().lowercase()

/**
 * Every owned printing's chips, by scryfallId, in one walk over the library: a chip per place and
 * section its binder copies are in, per physical deck holding real copies (less those lent from it),
 * per loan, copies tagged "lent", and the copies with no place yet. The same rules as Where it is
 * (whereItIs) and Find anything, a printing at a time. Graded copies are kept apart and have none.
 */
fun locationIndex(collections: List<Collection>, decks: List<Deck>): Map<String, List<LocationChip>> {
    val places = placesOf(collections).associateBy { it.id }
    val lent = lentCopies(collections, decks)
    val byEntry = lentByEntry(lent)
    val out = HashMap<String, ChipBuild>()
    fun of(id: String) = out.getOrPut(id) { ChipBuild() }
    for (c in collections) {
        if (c.kind == CollectionType.WISHLIST) continue
        for (e in c.entries) for (chip in entryChips(c.id, e, places, byEntry)) of(e.scryfallId).add(chip)
    }
    val fromDeck = HashMap<String, Int>()
    for (l in lent) {
        val d = l.card.deckId ?: continue
        val k = "$d|${l.card.scryfallId}|${nameKey(l.card.name)}"
        fromDeck[k] = (fromDeck[k] ?: 0) + l.qty
    }
    for (d in decks) for (e in realCopiesOf(d)) {
        val k = "${d.id}|${e.scryfallId}|${nameKey(e.name)}"
        of(e.scryfallId).add(LocationChip(LocationKind.DECK, e.quantity - (fromDeck.remove(k) ?: 0), deck = d.name, deckId = d.id))
    }
    for (l in lent) of(l.card.scryfallId).add(LocationChip(LocationKind.LENT, l.qty, loanTo = l.loan.to, loanId = l.loan.id))
    return out.mapValues { it.value.list() }
}

/**
 * One binder entry's chips — for a binder's own card rows: its copies in each place, those tagged
 * "lent" and those with no place yet. [lent]: lentByEntry. Loans show on the printing's chips
 * ([locationIndex]); here lent copies just aren't counted as having no place.
 */
fun entryChips(collectionId: String, entry: CollectionEntry, places: Map<String, StoragePlace>, lent: Map<String, Int>): List<LocationChip> {
    val total = maxOf(0, entry.quantity) + maxOf(0, entry.foilQuantity)
    if (total <= 0) return emptyList()
    val b = ChipBuild()
    var here = 0
    for (line in placedCopies(entry)) {
        val place = places[line.placeId] ?: continue
        here += line.qty
        b.add(LocationChip(LocationKind.PLACE, line.qty, place = place.name, placeId = place.id, section = line.section, page = line.page))
    }
    val (plain, foil) = lentOf(lent, collectionId, entry)
    val left = total - here - plain - foil
    if (left > 0) b.add(if (lentTag(entry) != null) LocationChip(LocationKind.LENT, left) else LocationChip(LocationKind.NONE, left))
    return b.list()
}

// ---- Cards tab: Group by ----

/** A group of rows under its header: "Dominaria" with "41/92", "Blue box", "No place yet". */
data class LayoutGroup<T>(val key: String, val title: String, val detail: String?, val items: List<T>)

/** One group a card goes in: its [key] and [title]. A card can be in more than one (two binders). */
data class GroupKey(val key: String, val title: String)

/** The key that sorts last: no set known, no binder, no place yet. */
const val GROUP_NONE = ""

/**
 * [items] in groups by [keysOf] — each item in every group it names, in its order there — groups A–Z by
 * title, the empty key last. [detailOf]: a group's line (a set's "41/92"). With no keys an item goes in
 * the [noneTitle] group.
 */
fun <T> groupLayout(
    items: List<T>,
    keysOf: (T) -> List<GroupKey>,
    noneTitle: String,
    detailOf: (String) -> String? = { null }
): List<LayoutGroup<T>> {
    val groups = LinkedHashMap<String, Pair<String, MutableList<T>>>()
    for (item in items) {
        val keys = keysOf(item).distinctBy { it.key }.ifEmpty { listOf(GroupKey(GROUP_NONE, noneTitle)) }
        for (k in keys) groups.getOrPut(k.key) { k.title to mutableListOf() }.second.add(item)
    }
    return groups.entries
        .sortedWith(compareBy<Map.Entry<String, Pair<String, MutableList<T>>>> { it.key == GROUP_NONE }.thenBy { it.value.first.lowercase() })
        .map { (key, v) -> LayoutGroup(key, v.first, if (key == GROUP_NONE) null else detailOf(key), v.second) }
}

/** By set: the printing's set ([setOf]: code to name), one group a set. */
fun setKeys(set: Pair<String, String>?): List<GroupKey> =
    if (set == null || set.first.isBlank()) emptyList() else listOf(GroupKey(set.first.lowercase(), set.second.ifBlank { set.first.uppercase() }))

/** A set group's line: "41/92" — printings owned of its size; just the count when its size isn't known. */
fun setGroupDetail(owned: Int, total: Int): String = if (total > 0) "${count(owned)}/${count(total)}" else count(owned)

/** By place: each place the chips name (a box, not its sections), each deck, "Lent out", and No place yet last. */
fun placeKeys(chips: List<LocationChip>): List<GroupKey> = chips.map {
    when (it.kind) {
        LocationKind.PLACE -> GroupKey("place:${it.placeId}", it.place.orEmpty())
        LocationKind.DECK -> GroupKey("deck:${it.deckId}", deckTitle(it.deck.orEmpty()))
        LocationKind.LENT -> GroupKey("lent", "Lent out")
        LocationKind.NONE -> GroupKey(GROUP_NONE, NO_PLACE_TITLE)
    }
}

/** By binder: each binder holding copies, and each deck. [sources]: kind ("binder"/"deck"), id, name. */
fun binderKeys(sources: List<Triple<String, String, String>>): List<GroupKey> = sources.map { (kind, id, name) ->
    if (kind == "deck") GroupKey("deck:$id", deckTitle(name)) else GroupKey("binder:$id", name)
}

/** The last group's title when grouping by place. */
const val NO_PLACE_TITLE = "No place yet"

// ---- Cards tab: quick filters ----

/** The printings each quick filter keeps, by scryfallId; [gradedNames]: cards with a graded copy, by name. */
data class QuickSets(
    val lent: Set<String> = emptySet(),
    val forSale: Set<String> = emptySet(),
    val noPlace: Set<String> = emptySet(),
    val graded: Set<String> = emptySet(),
    val gradedNames: Set<String> = emptySet()
)

/** What each quick filter keeps, from the library and its chips ([locationIndex]). */
fun quickSets(collections: List<Collection>, chips: Map<String, List<LocationChip>>): QuickSets {
    val forSale = HashSet<String>()
    for (c in collections) {
        if (c.kind == CollectionType.WISHLIST) continue
        for (e in c.entries) if (forSaleOf(e) > 0) forSale += e.scryfallId
    }
    val graded = gradedOf(collections)
    return QuickSets(
        lent = chips.filterValues { list -> list.any { it.kind == LocationKind.LENT } }.keys,
        forSale = forSale,
        noPlace = chips.filterValues { list -> list.any { it.kind == LocationKind.NONE } }.keys,
        graded = graded.map { it.scryfallId }.toSet(),
        gradedNames = graded.map { nameKey(it.name) }.toSet()
    )
}

/** Whether a card ([scryfallId], [name]) passes the quick filter [quick] (null: none on). */
fun passesQuick(quick: QuickFilter?, scryfallId: String, name: String, sets: QuickSets): Boolean = when (quick) {
    null -> true
    QuickFilter.LENT_OUT -> scryfallId in sets.lent
    QuickFilter.FOR_SALE -> scryfallId in sets.forSale
    QuickFilter.NO_PLACE -> scryfallId in sets.noPlace
    // A slab is kept apart from the raw copies: any printing of a card with one is shown.
    QuickFilter.GRADED -> scryfallId in sets.graded || nameKey(name) in sets.gradedNames || nameKey(name).split(" // ")[0] in sets.gradedNames
}

/** How many cards each quick filter would keep, for its chip. */
fun quickCount(quick: QuickFilter, ids: List<Pair<String, String>>, sets: QuickSets): Int = ids.count { passesQuick(quick, it.first, it.second, sets) }

// ---- Binders and Storage rows ----

/** How many places a binder's copies are kept in (places that are still there). */
fun placesUsed(collection: Collection, known: Set<String>): Int {
    val used = HashSet<String>()
    for (e in collection.entries) for (line in placedCopies(e)) if (line.placeId in known) used += line.placeId
    return used.size
}

/** "Kept in 3 places", "Kept in 1 place", "No place yet" (nothing placed, but cards), "" for an empty binder. */
fun keptInPlacesLine(places: Int, cards: Int): String = when {
    places > 0 -> "kept in ${count(places)} ${if (places == 1) "place" else "places"}"
    cards > 0 -> "no place yet"
    else -> ""
}

/** The binders (not the Unsorted pile, not a wishlist) with copies in each place itself, by place id. */
fun bindersByPlace(collections: List<Collection>): Map<String, Set<String>> {
    val out = HashMap<String, MutableSet<String>>()
    for (c in collections) {
        if (c.kind == CollectionType.WISHLIST || c.isUnsorted) continue
        for (e in c.entries) for (line in placedCopies(e)) out.getOrPut(line.placeId) { HashSet() } += c.id
    }
    return out
}

/** Binders with copies in a place or any place inside it. */
fun bindersIn(byPlace: Map<String, Set<String>>, places: List<StoragePlace>, id: String): Int =
    placeAndInside(places, id).flatMap { byPlace[it].orEmpty() }.toSet().size

/** "in 2 binders"; "" for none. */
fun inBindersLine(n: Int): String = if (n <= 0) "" else "in ${count(n)} ${if (n == 1) "binder" else "binders"}"

/** The header's chip: "Needs attention · 3"; null (hidden) at 0. */
fun needsAttentionLabel(n: Int): String? = if (n <= 0) null else "Needs attention · ${count(n)}"
