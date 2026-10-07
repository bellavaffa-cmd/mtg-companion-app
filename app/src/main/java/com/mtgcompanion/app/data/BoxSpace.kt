package com.mtgcompanion.app.data

import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Box space: how full a place is. A place can have a size — a box (or a shelf, or anything but a
 * binder) the cards it holds (StoragePlace.capacity), a binder its pages (StoragePlace.pages) of
 * pockets — and then shows "96% full · 612 of 640" and "Room for about 28 more". Putting cards away
 * and sorting a pile warn when a box will overflow, and a full box sorted into sections can be split
 * into two on whole sections ("Red box" keeps White, Blue, Black; "Red box 2" takes Red, Green…), the
 * copies in the sections that go moving with them.
 *
 * Pure, so it can be tested. Mirrors the web app's src/collection/boxSpace.ts rule for rule, with the
 * same tests (BoxSpaceTest.kt ↔ tests/collection/boxSpace.test.ts).
 */

/** A place this full (per cent) or more is shown as nearly full. */
const val NEARLY_FULL = 90

/** How many cards [place] holds: a binder's pages × its pockets, anything else its capacity. Null when no size is set. */
fun placeSize(place: StoragePlace): Int? =
    if (place.placeKind == PlaceKind.BINDER) place.pages?.takeIf { it > 0 }?.let { it * place.pockets }
    else place.capacity?.takeIf { it > 0 }

/** What a size is counted in: "pockets" for a binder, "cards" for anything else. */
fun sizeUnit(place: StoragePlace): String = if (place.placeKind == PlaceKind.BINDER) "pockets" else "cards"

/** [place] with its size set to [n] — a binder's pages, anything else's cards; 0 takes the size off. */
fun withSize(place: StoragePlace, n: Int): StoragePlace =
    if (place.placeKind == PlaceKind.BINDER) place.copy(pages = n.coerceAtLeast(0)) else place.copy(capacity = n.coerceAtLeast(0))

/** The number the size is set by: a binder's pages, anything else's cards; null when not set. */
fun sizeSetting(place: StoragePlace): Int? =
    (if (place.placeKind == PlaceKind.BINDER) place.pages else place.capacity)?.takeIf { it > 0 }

/**
 * How much of a place is used by [cards] (cardsIn — the copies in it itself): a binder's pockets in use
 * and the copies waiting beside it for one, anything else's copies.
 */
fun spaceUsed(place: StoragePlace, cards: List<PlacedCard>): Int =
    if (place.placeKind == PlaceKind.BINDER) binderPockets(place, cards).size + looseCopies(place, cards).size
    else cards.sumOf { it.line.qty }

/** How full a place is: [used] of [size]. */
data class Space(val placeId: String, val used: Int, val size: Int) {
    /** Per cent full, rounded: 96. */
    val percent: Int get() = if (size > 0) (used * 100.0 / size).roundToInt() else 0
    /** Room left; below 0 when it's over. */
    val room: Int get() = size - used
    val nearlyFull: Boolean get() = size > 0 && used * 100 >= size * NEARLY_FULL
}

/** How full [place] is, or null when it has no size. */
fun spaceOf(place: StoragePlace, collections: List<Collection>, cards: List<PlacedCard>? = null): Space? {
    val size = placeSize(place) ?: return null
    // [cards]: the place's own copies when the caller has them already (cardsByPlace), in any order.
    return Space(place.id, spaceUsed(place, cards ?: cardsIn(collections, place.id)), size)
}

/** "96% full · 612 of 640" when nearly full, else "288 of 360 pockets" (a binder) or "312 of 640". */
fun spaceLabel(place: StoragePlace, space: Space): String {
    val of = "${space.used} of ${space.size}" + if (place.placeKind == PlaceKind.BINDER) " pockets" else ""
    return if (space.nearlyFull) "${space.percent}% full · $of" else of
}

/** "Room for about 28 more. Your last pile added 38." — "Full." or "Over by 4." when there's no room. */
fun roomLine(space: Space, lastPile: Int? = null): String {
    val room = when {
        space.room > 0 -> "Room for about ${space.room} more."
        space.room == 0 -> "Full."
        else -> "Over by ${-space.room}."
    }
    return if (lastPile != null && lastPile > 0) "$room Your last pile added $lastPile." else room
}

/** Moves of a card that go together, as one pile: no more than this apart. */
const val PILE_GAP_MS = 30L * 60 * 1000

/**
 * How many copies the last pile put into [placeId] (the copy history, CopyHistory.kt — oldest first):
 * the cards added or put away into it within half an hour of each other, back from the latest. Null
 * when none have been.
 */
fun lastPileAdded(log: List<CopyMove>, placeId: String): Int? {
    val into = log.filter {
        (it.kind == MoveKind.ADDED.name || it.kind == MoveKind.PUT_AWAY.name) && it.places.orEmpty().firstOrNull() == placeId
    }
    if (into.isEmpty()) return null
    var total = 0
    var after = into.last().at
    for (m in into.asReversed()) {
        if (after - m.at > PILE_GAP_MS) break
        total += m.qty
        after = m.at
    }
    return total
}

// ---- Overflowing ----

/** A place that won't hold what's going in: [adding] cards with room for [room] (0 or less: full already). */
data class Overflow(val placeId: String, val name: String, val adding: Int, val room: Int)

/** The places that [adding] (cards going in, by place id) would overflow, in tree order. */
fun overflows(collections: List<Collection>, adding: Map<String, Int>): List<Overflow> {
    val out = mutableListOf<Overflow>()
    for (node in placeTree(placesOf(collections))) {
        val n = adding[node.place.id] ?: 0
        if (n <= 0) continue
        val space = spaceOf(node.place, collections) ?: continue
        if (n > space.room) out += Overflow(node.place.id, node.place.name, n, space.room)
    }
    return out
}

/** "Red box will overflow: room for about 28, this adds 38." or "Red box is full already." */
fun overflowLine(o: Overflow): String =
    if (o.room <= 0) "${o.name} is full already" + (if (o.room < 0) " (over by ${-o.room})" else "") + "."
    else "${o.name} will overflow: room for about ${o.room}, this adds ${o.adding}."

/** The cards a sort session files into each place (SortPiles.kt), by place id. */
fun pileAdds(collections: List<Collection>, session: SortSession): Map<String, Int> {
    val out = LinkedHashMap<String, Int>()
    for (scan in session.scans) {
        val spot = pileDestination(session.rules.getOrNull(scan.pile), scan.facts, collections).spot ?: continue
        out[spot.placeId] = (out[spot.placeId] ?: 0) + 1
    }
    return out
}

// ---- Splitting a box ----

/** A box split in two: the sections that stay and the ones that go to the new box, and the copies each way. */
data class SplitPlan(val stay: List<SectionGroup>, val go: List<SectionGroup>, val stayCopies: Int, val goCopies: Int)

/**
 * Where to split [place] (its [cards], cardsIn) into two boxes: on whole sections, in their order —
 * the first ones stay, the rest go — as near half and half as the sections allow (the earlier split
 * when two are as near). Copies in no section stay. Null when it has fewer than two sections.
 */
fun planSplit(place: StoragePlace, cards: List<PlacedCard>): SplitPlan? {
    val all = sectionsOf(place, cards)
    val named = all.filter { it.name != null }
    if (named.size < 2) return null
    val loose = all.filter { it.name == null }.sumOf { it.copies }
    val total = named.sumOf { it.copies }
    var best = 1
    var bestGap = Int.MAX_VALUE
    var run = 0
    for (k in 1 until named.size) {
        run += named[k - 1].copies
        val gap = abs(total - 2 * run)
        if (gap < bestGap) { bestGap = gap; best = k }
    }
    val stay = named.take(best)
    val go = named.drop(best)
    return SplitPlan(stay, go, stay.sumOf { it.copies } + loose, go.sumOf { it.copies })
}

/** "Red box 2" for "Red box" (and "Red box 3" when that's taken, or for "Red box 2"). */
fun nextBoxName(places: List<StoragePlace>, name: String): String {
    val base = name.trim().replace(Regex("\\s+\\d+$"), "").ifEmpty { name.trim() }
    val taken = places.map { it.name.trim().lowercase() }.toSet()
    var n = 2
    while ("$base $n".lowercase() in taken) n++
    return "$base $n"
}

/** "White, Blue, Black · 289". */
fun splitSideLabel(sections: List<SectionGroup>, copies: Int): String =
    sections.mapNotNull { it.name }.joinToString(", ") + " · $copies"

/**
 * [collections] with the box [placeId] split by [plan]: a new box [newId] beside it (same kind, rule,
 * size and note, named by nextBoxName) takes the sections that go, and the copies in them move to it,
 * in the same sections. The same list when the box isn't there.
 */
fun splitBox(collections: List<Collection>, placeId: String, plan: SplitPlan, newId: String, now: Long): List<Collection> {
    val places = placesOf(collections)
    val place = places.firstOrNull { it.id == placeId } ?: return collections
    val going = plan.go.mapNotNull { it.name }
    val goingKeys = going.map { it.lowercase() }.toSet()
    val made = storagePlace(place.copy(id = newId, name = nextBoxName(places, place.name), sections = going, createdAt = now, lastChecked = null))
    val kept = storagePlace(place.copy(sections = place.sections?.filter { it.lowercase() !in goingKeys }))
    val moved = collections.map { c ->
        if (c.entries.none { e -> e.places.orEmpty().any { it.placeId == placeId && (it.section?.lowercase() ?: "") in goingKeys } }) c
        else c.copy(entries = c.entries.map { e ->
            val lines = placedCopies(e)
            if (lines.none { it.placeId == placeId && (it.section?.lowercase() ?: "") in goingKeys }) e
            else withPlaces(e, lines.map { if (it.placeId == placeId && (it.section?.lowercase() ?: "") in goingKeys) it.copy(placeId = newId) else it })
        })
    }
    return withPlaceList(moved, places.flatMap { if (it.id == placeId) listOf(kept, made) else listOf(it) })
}
