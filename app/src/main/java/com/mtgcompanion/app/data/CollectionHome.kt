package com.mtgcompanion.app.data

import java.text.NumberFormat
import java.util.Locale

/*
 * The Collection's home: what it's worth, one search over everything, a tile for each part of the
 * collection with its numbers ("8 places · 92% placed"), the few things worth doing this week (from
 * Upkeep) and Scan / Sort a pile / Import. The numbers and the to-do lines are worked out here, pure,
 * so they can be tested. Mirrors the web app's src/collection/collectionHome.ts line for line (tests:
 * CollectionHomeTest.kt ↔ tests/collection/collectionHome.test.ts).
 */

/** What the home's tiles say. */
data class HomeNumbers(
    /** Different cards on All cards: owned printings in binders, the Unsorted pile and decks. */
    val cards: Int,
    val places: Int,
    /** The share of copies with a place, as Storage says it (placedPercent). */
    val placedPercent: Int,
    /** Binders, not counting the Unsorted pile and the Wishlist. */
    val binders: Int,
    /** Cards on the Wishlist. */
    val wishlist: Int,
    /** Sealed products (each counted) and graded copies. */
    val sealedAndGraded: Int,
    /** What they're worth, as the user entered it, in US dollars. */
    val sealedAndGradedUsd: Double,
    /** Copies out on loan now. */
    val lentOut: Int,
    /** Copies marked to sell. */
    val toSell: Int
)

/** The home's numbers, from the library. */
fun homeNumbers(collections: List<Collection>, decks: List<Deck>): HomeNumbers {
    val summary = storageSummary(collections, decks)
    val printings = HashSet<String>()
    for (c in collections) {
        if (c.kind == CollectionType.WISHLIST) continue
        for (e in c.entries) if (maxOf(0, e.quantity) + maxOf(0, e.foilQuantity) > 0) printings.add(e.scryfallId)
    }
    for (d in decks) for (e in d.cards) if (e.quantity > 0) printings.add(e.scryfallId)
    val sealed = sealedOf(collections)
    val graded = gradedOf(collections)
    return HomeNumbers(
        cards = printings.size,
        places = placesOf(collections).size,
        placedPercent = placedPercent(summary),
        binders = collections.count { !it.isUnsorted && !it.isWishlist && it.kind != CollectionType.WISHLIST },
        wishlist = collections.firstOrNull { it.isWishlist }?.entries?.size ?: 0,
        sealedAndGraded = sealed.sumOf { maxOf(0, it.count) } + graded.size,
        sealedAndGradedUsd = sealedTotalUsd(sealed) + graded.sumOf { it.valueUsd ?: 0.0 },
        lentOut = summary.lent,
        toSell = sellRows(collections).sumOf { it.qty }
    )
}

private fun count(n: Int): String = NumberFormat.getIntegerInstance(Locale.UK).format(n)

enum class HomeTileKey { ALL, STORAGE, BINDERS, SETS, SEALED, LOANS }

/** One tile: its name and the line under it. */
data class HomeTile(val key: HomeTileKey, val title: String, val line: String)

/** The six tiles, in the home's order, with [money] writing an amount the way the app shows prices. */
fun homeTiles(n: HomeNumbers, money: (Double) -> String): List<HomeTile> = listOf(
    HomeTile(HomeTileKey.ALL, "All cards", "${count(n.cards)} · filters"),
    HomeTile(
        HomeTileKey.STORAGE, "Storage",
        if (n.places == 0) "Set up your places" else "${count(n.places)} ${if (n.places == 1) "place" else "places"} · ${n.placedPercent}% placed"
    ),
    HomeTile(HomeTileKey.BINDERS, "Binders", "${count(n.binders)} · wishlist"),
    HomeTile(HomeTileKey.SETS, "Sets", "completion"),
    HomeTile(
        HomeTileKey.SEALED, "Sealed and graded",
        if (n.sealedAndGraded == 0) "Boxes and slabs"
        else "${count(n.sealedAndGraded)} ${if (n.sealedAndGraded == 1) "item" else "items"}" +
            (if (n.sealedAndGradedUsd > 0) " · ${money(n.sealedAndGradedUsd)}" else "")
    ),
    HomeTile(HomeTileKey.LOANS, "Loans and selling", "${count(n.lentOut)} out · ${count(n.toSell)} to sell")
)

/** One line of the home's To do: Upkeep's item, said short, with its button. */
data class HomeTodo(val item: UpkeepItem, val title: String, val action: String)

/** At most this many things to do on the home; the rest are on Upkeep. */
const val HOME_TODO_MAX = 3

/** Which kinds come first on the home: copies to put away, then a pull list half done, then the rest. */
private val HOME_ORDER = listOf(UpkeepKind.PUT_AWAY, UpkeepKind.CARRY_ON, UpkeepKind.REMIND, UpkeepKind.CHECK, UpkeepKind.SPLIT)

private val DECK_WORD_END = Regex("\\s+deck$", RegexOption.IGNORE_CASE)
private val PULLED = Regex("^(\\d+) of (\\d+)")

/**
 * The home's To do, from Upkeep's [items]: one of each kind first, in HOME_ORDER (so a long list of
 * places to check doesn't hide a pull list), then the rest, at most [max]. A pull list says how far
 * it got: "Krenko pull list: 30 of 60". [decks]: id to name.
 */
fun homeTodo(items: List<UpkeepItem>, decks: Map<String, String>, max: Int = HOME_TODO_MAX): List<HomeTodo> {
    // sortedBy is stable: items of a kind keep Upkeep's order.
    val sorted = items.sortedBy { HOME_ORDER.indexOf(it.kind) }
    val firsts = mutableListOf<UpkeepItem>()
    val rest = mutableListOf<UpkeepItem>()
    for (item in sorted) if (firsts.any { it.kind == item.kind }) rest.add(item) else firsts.add(item)
    return (firsts + rest).take(maxOf(0, max)).map { HomeTodo(it, todoTitle(it, decks), it.action) }
}

private fun todoTitle(item: UpkeepItem, decks: Map<String, String>): String {
    if (item.kind != UpkeepKind.CARRY_ON) return item.title
    val name = item.deckId?.let { decks[it] } ?: return item.title
    val done = PULLED.find(item.detail) ?: return item.title
    val short = deckTitle(name).replace(DECK_WORD_END, "")
    return "$short pull list: ${done.groupValues[1]} of ${done.groupValues[2]}"
}
