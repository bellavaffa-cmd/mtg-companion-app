package com.mtgcompanion.app.data

/**
 * Proxies in a deck, and swapping them for the real thing. A deck marked Proxy is proxies all
 * through to begin with; as real copies turn up in the user's binders they're swapped in one at a
 * time, so a deck can say "7 proxies left" and stop counting the ones that aren't proxies any more.
 * Mirrors the web app's src/decks/proxies.ts.
 */

private fun key(name: String) = name.trim().lowercase()

/**
 * How many of this entry's copies are proxies. A deck marked Proxy is all proxies until copies are
 * swapped in; any other deck holds proxies only where it says so.
 */
fun proxyCopies(deck: Deck, entry: DeckCardEntry): Int {
    val held = entry.proxyQuantity ?: if (deck.ownershipType == DeckOwnership.PROXY) entry.quantity else 0
    return held.coerceIn(0, entry.quantity)
}

/** How many proxies the whole deck holds. */
fun deckProxyCopies(deck: Deck): Int = deck.cards.sumOf { proxyCopies(deck, it) }

/** Real copies sitting in binders, by card name — what a proxy could be swapped for. */
fun spareCopies(collections: List<Collection>): Map<String, Int> {
    val spare = mutableMapOf<String, Int>()
    collections.filter { it.kind != CollectionType.WISHLIST }.forEach { collection ->
        collection.entries.forEach { entry ->
            val copies = entry.quantity + entry.foilQuantity
            if (copies > 0) spare[key(entry.name)] = (spare[key(entry.name)] ?: 0) + copies
        }
    }
    return spare
}

/** A proxy that could become the real card, and how many spare copies are sitting in binders. */
data class ProxySwap(val deck: Deck, val entry: DeckCardEntry, val spare: Int)

/**
 * The proxies the user already owns a real copy of, deck by deck. A binder copy is only offered
 * once, however many decks are playing a proxy of that card.
 */
fun proxySwaps(collections: List<Collection>, decks: List<Deck>): List<ProxySwap> {
    val spare = spareCopies(collections).toMutableMap()
    val out = mutableListOf<ProxySwap>()
    for (deck in decks) {
        // A cube's proxies are filled from the collection by its own pull list, into the cube box (Cube.kt).
        if (deck.isCube) continue
        for (entry in deck.cards) {
            val proxies = proxyCopies(deck, entry)
            if (proxies == 0) continue
            val left = spare[key(entry.name)] ?: 0
            if (left <= 0) continue
            val swappable = minOf(proxies, left)
            spare[key(entry.name)] = left - swappable
            out += ProxySwap(deck, entry, swappable)
        }
    }
    return out
}

/** A proxy the user owns a real copy of, but only in another deck — where it is, and how many. */
data class ProxyHeldElsewhere(
    val entry: DeckCardEntry,
    /** Proxies of it still left once the binder copies have been swapped in. */
    val proxies: Int,
    /** The other decks holding a real copy, and how many each has. */
    val decks: List<Pair<Deck, Int>>
)

/**
 * Proxies in [deck] the user already owns for real, but only in another deck. These aren't offered
 * as a swap: moving the card would leave that deck a card short without anyone saying so. Knowing
 * where it is lets the user decide which deck gets it. A proxy a binder copy can cover is left to
 * the swap, and only decks the user actually holds count, the same as for the cards a deck is
 * missing. Mirrors proxiesHeldElsewhere in the web app's src/decks/proxies.ts.
 */
fun proxiesHeldElsewhere(collections: List<Collection>, decks: List<Deck>, deck: Deck): List<ProxyHeldElsewhere> {
    val swappable = proxySwaps(collections, listOf(deck)).associate { it.entry.scryfallId to it.spare }
    val held = decks.filter { it.id != deck.id }.map { it to copiesHeld(it) }
    return deck.cards.mapNotNull { entry ->
        val proxies = proxyCopies(deck, entry) - (swappable[entry.scryfallId] ?: 0)
        if (proxies <= 0) return@mapNotNull null
        val found = held.mapNotNull { (other, copies) ->
            (copies[entry.name.lowercase()] ?: 0).takeIf { it > 0 }?.let { other to it }
        }
        if (found.isEmpty()) null else ProxyHeldElsewhere(entry, proxies, found)
    }
}

/** What one swap changes: the deck's cards and the binders, or null when there's nothing to swap. */
data class SwapResult(val collections: List<Collection>, val decks: List<Deck>)

/**
 * One proxy swapped for the real card: the deck keeps the same card, one copy of it stops being a
 * proxy, and the binder copy that took its place is gone from the binder — it's in the deck now, and
 * the deck's card is sorted into that binder, so the binder still lists it (BinderFlows.kt), with the
 * spot it was kept in noted on the deck (Deck.cameFrom). Taken from No binder, it's in no binder.
 * Null when there's no proxy of that card, or no copy to swap in.
 */
fun withSwapIn(collections: List<Collection>, decks: List<Deck>, deckId: String, scryfallId: String): SwapResult? {
    val deck = decks.firstOrNull { it.id == deckId } ?: return null
    val entry = deck.cards.firstOrNull { it.scryfallId == scryfallId } ?: return null
    val proxies = proxyCopies(deck, entry)
    if (proxies == 0) return null
    val binder = collections.firstOrNull { c ->
        c.kind != CollectionType.WISHLIST && c.entries.any { key(it.name) == key(entry.name) && it.quantity + it.foilQuantity > 0 }
    } ?: return null
    val taking = binder.entries.first { key(it.name) == key(entry.name) && it.quantity + it.foilQuantity > 0 }
    // A plain copy first; a foil only if that's all there is. One with no place first, then the last place's.
    val foil = taking.quantity <= 0
    val (staying, going) = splitPlaces(taking, if (foil) 0 else 1, if (foil) 1 else 0)
    val left = taking.copy(
        quantity = taking.quantity - (if (foil) 0 else 1),
        foilQuantity = taking.foilQuantity - (if (foil) 1 else 0),
        places = if (taking.places == null) null else staying
    )

    val swapped = deck.copy(cards = deck.cards.map { if (it.scryfallId != scryfallId) it else it.copy(proxyQuantity = proxies - 1) })
    val valid = sortingBinderIds(collections)
    val sorted = if (binder.id !in valid) swapped
    else withCardBinders(swapped, scryfallId, withRef(assignedCopies(deck, entry), binderRef(binder.id, 1, if (foil) 1 else 0)))
    val came = going.map { cameFromLine(entry.name, it) }
    val next = if (came.isEmpty()) sorted else sorted.copy(cameFrom = tidyCameFrom(sorted.cameFrom.orEmpty() + came))
    val nextDecks = decks.map { d -> if (d.id != deckId) d else next }
    val nextCollections = collections.map { c ->
        if (c.id != binder.id) c
        else c.copy(entries = c.entries.mapNotNull { e -> if (e !== taking) e else left.takeIf { it.quantity + it.foilQuantity > 0 } })
    }
    return SwapResult(nextCollections, nextDecks)
}
