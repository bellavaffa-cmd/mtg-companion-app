package com.mtgcompanion.app.data

/*
 * Getting started with storage: three steps from an empty Storage tab to places with labels on them.
 *  1. What the cards are kept in — rough numbers of binders (and their pockets per page), bulk boxes
 *     (and how they're sorted), a shelf or cupboard to group the rest. Deck boxes come by themselves:
 *     each physical deck is its own.
 *  2. Their names ("Binder 1", "Box 1"…, changed as the user likes).
 *  3. Labels to print, then putting cards away one box at a time, with how far that's got.
 *
 * Pure, so it can be tested. Mirrors the web app's src/collection/storageSetup.ts rule for rule, with
 * the same tests (StorageSetupTest.kt ↔ tests/collection/storageSetup.test.ts).
 */

/** Step 1's numbers. [boxRule]: how the bulk boxes are sorted; null, not sorted. */
data class SetupCounts(
    val binders: Int = 2,
    val pockets: Int = DEFAULT_POCKETS,
    val boxes: Int = 3,
    val boxRule: SortRule? = SortRule.COLOUR,
    val shelves: Int = 1
) {
    val total: Int get() = binders.coerceAtLeast(0) + boxes.coerceAtLeast(0) + shelves.coerceAtLeast(0)
}

/** The pockets per page step 1 offers, in turn. */
val SETUP_POCKETS = listOf(9, 12, 18, 4)

/** The pockets after [n] in [SETUP_POCKETS]. */
fun nextPockets(n: Int): Int = SETUP_POCKETS[(SETUP_POCKETS.indexOf(n) + 1).mod(SETUP_POCKETS.size)]

/** The sorting rule after [rule] for step 1's bulk boxes: by colour, by set, by type, A–Z, not sorted. */
fun nextBoxRule(rule: SortRule?): SortRule? {
    val all = listOf<SortRule?>(*SortRule.entries.toTypedArray(), null)
    return all[(all.indexOf(rule) + 1).mod(all.size)]
}

/** One place step 2 names. [key]: "binder-1", "box-2", "shelf-1". */
data class SetupDraft(val key: String, val kind: PlaceKind, val name: String)

/** A name not already taken (case aside): [base], or "[base] 2", "[base] 3"… */
private fun freeName(base: String, taken: MutableSet<String>): String {
    var name = base
    var n = 2
    while (name.lowercase() in taken) name = "$base ${n++}"
    taken += name.lowercase()
    return name
}

/**
 * The places [counts] makes, named — shelves first, then binders, then boxes: "Shelf", "Binder 1",
 * "Binder 2", "Box 1"… ("Binder" alone when there's one), none the same as one of [existing].
 */
fun setupDrafts(counts: SetupCounts, existing: List<StoragePlace> = emptyList()): List<SetupDraft> {
    val taken = existing.map { it.name.trim().lowercase() }.toMutableSet()
    val out = mutableListOf<SetupDraft>()
    fun add(kind: PlaceKind, prefix: String, word: String, n: Int) {
        for (i in 1..n.coerceAtLeast(0)) out += SetupDraft("$prefix-$i", kind, freeName(if (n == 1) word else "$word $i", taken))
    }
    add(PlaceKind.SHELF, "shelf", "Shelf", counts.shelves)
    add(PlaceKind.BINDER, "binder", "Binder", counts.binders)
    add(PlaceKind.BOX, "box", "Box", counts.boxes)
    return out
}

/**
 * The places themselves: each draft with its name ([names] by key, blank keeping the draft's), binders
 * with their pockets, boxes sorted by [SetupCounts.boxRule] with that rule's sections, and the binders
 * and boxes inside the first shelf when there is one. Made in order, one millisecond apart from [now].
 */
fun setupPlaces(counts: SetupCounts, drafts: List<SetupDraft>, names: Map<String, String>, now: Long, newId: () -> String): List<StoragePlace> {
    val ids = drafts.associate { it.key to newId() }
    val shelf = drafts.firstOrNull { it.kind == PlaceKind.SHELF }?.let { ids[it.key] }
    return drafts.mapIndexed { i, d ->
        storagePlace(StoragePlace(
            id = ids.getValue(d.key),
            name = names[d.key]?.trim()?.takeIf { it.isNotEmpty() } ?: d.name,
            kind = d.kind.name,
            parentId = if (d.kind == PlaceKind.SHELF) null else shelf,
            sections = if (d.kind == PlaceKind.BOX) defaultSections(counts.boxRule).takeIf { it.isNotEmpty() } else null,
            pocketsPerPage = if (d.kind == PlaceKind.BINDER && counts.pockets != DEFAULT_POCKETS) counts.pockets else null,
            sortRule = if (d.kind == PlaceKind.BOX) counts.boxRule?.name else null,
            createdAt = now + i
        ))
    }
}

/** [collections] with [made] added to the places. */
fun applySetup(collections: List<Collection>, made: List<StoragePlace>): List<Collection> =
    made.fold(collections) { c, p -> savePlace(c, p) }

/** How many physical decks there are — each its own deck box. */
fun deckBoxCount(decks: List<Deck>): Int = decks.count { it.holdsOwnCopies }

/** "92%": the share of copies with a place, rounded down — 100% only when every one has. */
fun placedPercent(summary: StorageSummary): Int =
    if (summary.total <= 0) 0 else (summary.placed * 100L / summary.total).toInt()
