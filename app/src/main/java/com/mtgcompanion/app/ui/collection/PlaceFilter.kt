package com.mtgcompanion.app.ui.collection

import com.mtgcompanion.app.data.LocationChip
import com.mtgcompanion.app.data.LocationKind
import com.mtgcompanion.app.data.PlacedCard
import com.mtgcompanion.app.data.PullListData
import com.mtgcompanion.app.data.StoragePlace
import com.mtgcompanion.app.data.binderPockets
import com.mtgcompanion.app.data.pocketAt
import com.mtgcompanion.app.data.pockets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.NumberFormat
import java.util.Locale

/*
 * Filtering what's in storage: a place's page and the Storage tab search and filter the copies kept
 * there with the Cards tab's own filters — the search, the filter panel (CollectionFilter.kt), the
 * Advanced filters (AdvancedFilter.kt, saved filters too) and Spares only — judged copy by copy: a
 * copy's own finish, condition, language and binder, and the card's Scryfall data. Then what the
 * results say: "14 of 312 cards · $118", the chips with one Clear, the binder pockets to pick out,
 * and the Storage tab's heading. Also the filters kept per place while the app runs, and the list
 * being walked. Pure, so it can be tested. Mirrors the web app's src/collection/placeFilter.ts,
 * placeFilterStore.ts and walkSession.ts, with the same tests (PlaceFilterTest.kt ↔
 * tests/collection/placeFilter.test.ts).
 */

/** A place's (or the Storage tab's) search and filters: the Cards tab's, kept together. */
data class CardsFilter(
    val query: String = "",
    val basic: CollectionFilter = CollectionFilter(),
    val advanced: AdvancedFilter = AdvancedFilter(),
    /** Only copies of cards no deck of yours plays (Spares.kt). */
    val sparesOnly: Boolean = false
) {
    /** Whether anything narrows the copies: the search or a filter. */
    val active: Boolean get() = query.isNotBlank() || basic.active || advanced.active || sparesOnly

    /** The filters on, for the filter button's badge (the search is in its own field). */
    val count: Int get() = basic.count + advanced.count + (if (sparesOnly) 1 else 0)
}

/** What judging copies needs besides the copies: the cards' data, the collection's, and the search's tags. */
class FilterFacts(
    /** By scryfallId; a card whose data hasn't loaded is left out while a filter is on, as on Cards. */
    val card: (String) -> CardFacts? = { null },
    val advanced: (String) -> AdvancedFacts? = { null },
    /** copyFactsOf: every copy of each printing, for In a deck and Copies. */
    val copies: Map<String, CopyFacts> = emptyMap(),
    /** Every place's id with the places it sits in (placeChains). */
    val chains: Map<String, List<String>> = emptyMap(),
    /** The card names your decks use (namesDecksUse), for Spares only: a copy of any other card is a spare. Null: not known. */
    val usedNames: Set<String>? = null,
    /** An amount typed in the chosen currency, in US dollars. */
    val toUsd: (Double) -> Double = { it },
    /** Whether the search finds a copy — its name and tags, as Cards searches. Plain name by default. */
    val searchMatches: (PlacedCard, String) -> Boolean = { c, q -> c.entry.name.contains(q.trim(), ignoreCase = true) }
)

/**
 * The Your copies facts of one line of copies: its own finish, condition, language, binder and place
 * (with the places it sits in) — so "Foil" finds the foil copies here, not every copy of a card that
 * has a foil somewhere. In a deck and Copies are the card's, from [all] (copyFactsOf).
 */
fun copyFactsOfCopy(copy: PlacedCard, all: CopyFacts?, chains: Map<String, List<String>>): CopyFacts {
    val foil = copy.line.isFoil
    return CopyFacts(
        nonfoil = if (foil) 0 else copy.line.qty,
        foil = if (foil) copy.line.qty else 0,
        conditions = listOfNotNull(copy.entry.condition?.takeIf { it.isNotEmpty() }),
        languages = listOf(copy.entry.language?.takeIf { it.isNotEmpty() } ?: "en"),
        binders = listOf(copy.collectionId),
        inDeck = all?.inDeck ?: false,
        copies = all?.copies ?: copy.line.qty,
        places = chains[copy.line.placeId] ?: listOf(copy.line.placeId),
        unplaced = 0
    )
}

/** Whether one line of copies passes the search, the filter panel, the Advanced filters and Spares only. */
fun copyMatches(f: CardsFilter, copy: PlacedCard, facts: FilterFacts): Boolean {
    val id = copy.entry.scryfallId
    val q = f.query.trim()
    if (q.isNotEmpty() && !facts.searchMatches(copy, q)) return false
    if (f.sparesOnly && facts.usedNames?.contains(copy.entry.name.trim().lowercase()) != false) return false
    if (!f.basic.matches(facts.card(id))) return false
    if (f.advanced.active) {
        val mine = copyFactsOfCopy(copy, facts.copies[id], facts.chains)
        if (!advancedMatches(f.advanced, facts.advanced(id), mine, facts.toUsd)) return false
    }
    return true
}

/** The copies that pass [f]; all of them when nothing is on. */
fun filterCopies(copies: List<PlacedCard>, f: CardsFilter, facts: FilterFacts): List<PlacedCard> =
    if (f.active) copies.filter { copyMatches(f, it, facts) } else copies

// ---- What the results say ----

/** A line of copies' price each in US dollars: the foil price for foil copies, else the plain one (either when the other's missing). */
fun placedCopyUsd(copy: PlacedCard, usd: Double?, usdFoil: Double?): Double? =
    if (copy.line.isFoil) usdFoil ?: usd else usd ?: usdFoil

data class FilterTotals(
    /** Copies that pass. */
    val matching: Int,
    /** Copies here at all. */
    val total: Int,
    /** What the copies that pass are worth (US dollars); null until prices have loaded. */
    val usd: Double?
)

/**
 * The totals of a filtered place: copies matching of copies here, and what the matching copies are
 * worth. Copies in places are binder copies, so every one is a real one (decks' proxies are never in
 * a place); a copy with no price counts as nothing. [priceOf] null: prices not loaded yet.
 */
fun filterTotals(all: List<PlacedCard>, matching: List<PlacedCard>, priceOf: ((PlacedCard) -> Double?)?): FilterTotals =
    FilterTotals(
        matching = matching.sumOf { it.line.qty },
        total = all.sumOf { it.line.qty },
        usd = priceOf?.let { p -> matching.sumOf { (p(it) ?: 0.0) * it.line.qty } }
    )

private fun count(n: Int): String = NumberFormat.getIntegerInstance(Locale.UK).format(n)

/** "14 of 312 cards · $118" ([format] shows dollars in the chosen currency); without the value until prices load. */
fun totalsLine(t: FilterTotals, format: (Double) -> String): String {
    val head = "${count(t.matching)} of ${count(t.total)} ${if (t.total == 1) "card" else "cards"}"
    return if (t.usd == null) head else "$head · ${format(t.usd)}"
}

/**
 * The chips for what's on: the search, the panel's and Advanced filters' own chips, Spares only —
 * each taken off by its tap, and one Clear for the lot.
 */
fun cardsFilterChips(
    f: CardsFilter,
    binderName: (String) -> String?,
    formatLocal: (Double) -> String,
    placeName: (String) -> String? = { null }
): List<ActiveChip> {
    val q = f.query.trim()
    return listOfNotNull(if (q.isNotEmpty()) ActiveChip("search", "“$q”") else null) +
        filterChips(f.basic, f.advanced, binderName, formatLocal, placeName) +
        listOfNotNull(if (f.sparesOnly) ActiveChip("spares", "Spares only") else null)
}

/** [f] with the chip [key] taken off. */
fun removeCardsFilterChip(f: CardsFilter, key: String): CardsFilter = when (key) {
    "search" -> f.copy(query = "")
    "spares" -> f.copy(sparesOnly = false)
    else -> {
        val (b, a) = removeChip(f.basic, f.advanced, key)
        f.copy(basic = b, advanced = a)
    }
}

/** The binder's pockets (pocketIndex) holding a matching copy — picked out on its pages, the rest dimmed. */
fun matchingPockets(place: StoragePlace, all: List<PlacedCard>, matching: List<PlacedCard>): Set<Int> {
    val hits = matching.toSet()
    return binderPockets(place, all).filter { p -> p.cards.any { it in hits } }.map { it.index }.toSet()
}

/** The pages with a matching copy, in order: "Matches on pages 2, 5". */
fun matchingPages(place: StoragePlace, all: List<PlacedCard>, matching: List<PlacedCard>): List<Int> =
    matchingPockets(place, all, matching).map { pocketAt(it, place.pockets).first }.distinct().sorted()

/** The Storage tab's heading over its results: "Cards in storage matching “ramp”" (+ "· 2 filters"). */
fun storageMatchingTitle(f: CardsFilter): String {
    val q = f.query.trim()
    val n = f.count
    val filters = "$n ${if (n == 1) "filter" else "filters"}"
    return when {
        q.isNotEmpty() && n > 0 -> "Cards in storage matching “$q” · $filters"
        q.isNotEmpty() -> "Cards in storage matching “$q”"
        else -> "Cards in storage matching your ${if (n == 1) "filter" else "filters"}"
    }
}

/** A result row's place chip: "Red box › Red ×2", opening the place at that section or page (LocationChipsUi.kt). */
fun copyChip(copy: PlacedCard, place: StoragePlace?): LocationChip = LocationChip(
    kind = LocationKind.PLACE,
    qty = copy.line.qty,
    place = place?.name.orEmpty(),
    placeId = copy.line.placeId,
    section = copy.line.section?.takeIf { it.isNotEmpty() },
    page = copy.line.page?.takeIf { it > 0 }
)

// ---- Kept per place while the app runs ----

/** The Storage tab's own filters, kept beside the places'. */
const val STORAGE_FILTER_KEY = "storage"

fun filterIn(memory: Map<String, CardsFilter>, key: String): CardsFilter = memory[key] ?: CardsFilter()

/** [memory] with [key]'s filters set — dropped when nothing's on, so Clear forgets them. */
fun withFilter(memory: Map<String, CardsFilter>, key: String, f: CardsFilter): Map<String, CardsFilter> =
    if (f.active) memory + (key to f) else memory - key

/**
 * Each place's search and filters (and the Storage tab's), kept while the app runs — coming back to
 * a place finds them as they were — and forgotten when it's closed. The web app's placeFilterStore.ts.
 */
object PlaceFilterStore {
    private val _filters = MutableStateFlow<Map<String, CardsFilter>>(emptyMap())
    val filters: StateFlow<Map<String, CardsFilter>> = _filters.asStateFlow()

    fun set(key: String, f: CardsFilter) { _filters.value = withFilter(_filters.value, key, f) }
}

/** "Walk this list": what was searched for ("Red box · “goblin”"), the list, and the rows ticked. */
data class Walk(val title: String, val list: PullListData, val ticked: Set<String> = emptySet())

/**
 * The list being walked (walkPullList), ticked off by hand on WalkListScreen or with the scanner's
 * scan-to-tick. One at a time, kept while the app runs — a trip round the shelves — and not across a
 * restart. The web app's walkSession.ts.
 */
object WalkSession {
    /** The scan-to-tick list id (TickList.deckId) that means this walk, not a deck. */
    const val TICK_ID = "walk"

    private val _walk = MutableStateFlow<Walk?>(null)
    val walk: StateFlow<Walk?> = _walk.asStateFlow()

    /** Starts walking [list]; ticks of an earlier walk are kept for the rows still on it. */
    fun start(title: String, list: PullListData) {
        val keys = list.groups.flatMap { g -> g.rows.map { it.key } }.toSet()
        _walk.value = Walk(title, list, _walk.value?.ticked.orEmpty().filter { it in keys }.toSet())
    }

    fun setTicked(ticked: Set<String>) { _walk.value = _walk.value?.copy(ticked = ticked) }
}
