package com.mtgcompanion.app.data

/*
 * Which binders a deck card's real copies are sorted into (DeckCardEntry.binders). The copies stay the
 * deck's — what's owned and what it's worth don't change — a binder only shows them (BinderViews.kt).
 * Kept through every edit, through the sync's merge (as one value, whoever changed it — ItemMerge.kt)
 * and through backups. Mirrors the web app's src/decks/binderRefs.ts.
 */

/**
 * [theirs] with each main-deck card's binders taken from [source] where [theirs] came without them — a
 * deck saved by an app that doesn't know about them leaves them as they were. The same object when
 * there's nothing to keep.
 */
fun keepBindersFromOlderApp(source: Deck, theirs: Deck): Deck {
    val known = source.cards.filter { it.binders != null }.associate { it.scryfallId to it.binders!! }
    if (known.isEmpty() || theirs.cards.none { it.binders == null && it.scryfallId in known }) return theirs
    return theirs.copy(cards = theirs.cards.map { e -> if (e.binders != null) e else known[e.scryfallId]?.let { e.copy(binders = it) } ?: e })
}

/**
 * Two entries' binders as one, for two entries joined into one (a printing changed to one the deck
 * already has): the counts per binder added up. Null when neither had any set.
 */
fun joinedBinders(a: List<BinderRef>?, b: List<BinderRef>?): List<BinderRef>? {
    if (a == null && b == null) return null
    val out = ArrayList<BinderRef>()
    for (ref in a.orEmpty() + b.orEmpty()) {
        val at = out.indexOfFirst { it.id == ref.id }
        if (at >= 0) out[at] = out[at].copy(qty = out[at].qty + ref.qty) else out += ref
    }
    return out
}

/** [deck] with each card's binder ids switched by [ids] (a binder given a new id); the same object when none is. */
fun withBinderIds(deck: Deck, ids: Map<String, String>): Deck {
    fun moves(e: DeckCardEntry) = e.binders?.any { it.id in ids } == true
    if (deck.cards.none(::moves)) return deck
    return deck.copy(cards = deck.cards.map { e ->
        if (!moves(e)) e else e.copy(binders = e.binders!!.map { r -> ids[r.id]?.let { r.copy(id = it) } ?: r })
    })
}
