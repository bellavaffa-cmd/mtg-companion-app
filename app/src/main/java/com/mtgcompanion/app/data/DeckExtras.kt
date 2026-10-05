package com.mtgcompanion.app.data

// How a deck's primer, folder, archive flag, companion and categories sync (Deck.description, folder,
// archived, companion, categoryTargets and each card's categories). They ride in the deck's JSON like
// everything else, and merge like it: the primer, folder, flag and companion go to whichever device
// changed them (the later edit when both did); each category's target the same, one by one; a card's
// categories keep both devices' additions, and one either took off stays off (supabase/ItemMerge.kt).
//
// An app from before these existed drops them when it saves the deck. So each is left out until it's
// first set and kept afterwards — as "", false or {} once cleared — and a deck saved without the key
// gets this device's back rather than losing it on every device. The cards' categories ride on
// categoryTargets: a deck with none was saved by an older app, which dropped the cards' too.
// The web app's src/decks/deckExtras.ts, line for line.

/** [list] with each card's categories from [from] where the card has none. */
private fun withCategoriesFrom(list: List<DeckCardEntry>, from: List<DeckCardEntry>): List<DeckCardEntry> {
    val known = from.filter { !it.categories.isNullOrEmpty() }.associate { it.scryfallId to it.categories!! }
    if (known.isEmpty()) return list
    return list.map { e -> if (!e.categories.isNullOrEmpty() || e.scryfallId !in known) e else e.copy(categories = known[e.scryfallId]) }
}

/**
 * [theirs] with [source]'s primer, folder, archive flag, companion and categories put back where
 * [theirs] was written by an app that doesn't know them (no key) — the same object when nothing was missing.
 */
fun keepDeckExtrasFromOlderApp(source: Deck, theirs: Deck): Deck {
    var out = theirs
    if (out.description == null && source.description != null) out = out.copy(description = source.description)
    if (out.folder == null && source.folder != null) out = out.copy(folder = source.folder)
    if (out.archived == null && source.archived != null) out = out.copy(archived = source.archived)
    if (out.companion == null && source.companion != null) out = out.copy(companion = source.companion)
    if (out.categoryTargets == null && source.categoryTargets != null) {
        out = out.copy(
            categoryTargets = source.categoryTargets,
            cards = withCategoriesFrom(out.cards, source.cards),
            sideboard = withCategoriesFrom(out.sideboard, source.sideboard),
            considering = withCategoriesFrom(out.considering, source.considering)
        )
    }
    return out
}

/** A field's value after a merge: whoever changed it, or the more recent edit when both did. */
private fun <T> pickExtra(base: T, mine: T, theirs: T, minePreferred: Boolean): T = when {
    mine == theirs -> mine
    mine == base -> theirs
    theirs == base -> mine
    else -> if (minePreferred) mine else theirs
}

/** Each category's target merged on its own; null when no side has any. */
fun mergeCategoryTargets(base: Map<String, Int>?, mine: Map<String, Int>?, theirs: Map<String, Int>?, minePreferred: Boolean): Map<String, Int>? {
    if (mine == null && theirs == null) return null
    val out = LinkedHashMap<String, Int>()
    (base.orEmpty().keys + mine.orEmpty().keys + theirs.orEmpty().keys).distinct().sorted().forEach { k ->
        pickExtra(base?.get(k), mine?.get(k), theirs?.get(k), minePreferred)?.let { out[k] = it }
    }
    return out
}

/** [merged] with the deck-level extras merged: each to whoever changed it, the targets one by one. */
fun withMergedExtras(merged: Deck, base: Deck, mine: Deck, theirs: Deck, minePreferred: Boolean): Deck = merged.copy(
    description = pickExtra(base.description, mine.description, theirs.description, minePreferred),
    folder = pickExtra(base.folder, mine.folder, theirs.folder, minePreferred),
    archived = pickExtra(base.archived, mine.archived, theirs.archived, minePreferred),
    companion = pickExtra(base.companion, mine.companion, theirs.companion, minePreferred),
    categoryTargets = mergeCategoryTargets(base.categoryTargets, mine.categoryTargets, theirs.categoryTargets, minePreferred)
)
