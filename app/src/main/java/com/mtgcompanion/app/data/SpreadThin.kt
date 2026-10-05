package com.mtgcompanion.app.data

/**
 * Cards spread too thin: the cards the user's decks between them use more copies of than they own —
 * Sol Ring in four decks with two copies to go round. Counted the way a deck's missing cards are
 * ([missingCards]): by card name, since any printing fills a slot; what they own is the copies in
 * their binders (the Unsorted pile too, never the Wishlist) plus the real copies sitting in the
 * decks they hold ([copiesHeld]: Physical, and the cards swapped into a Proxy deck). A Proxy deck's
 * proxies are print-outs it already has, so they ask for nothing. Basic lands are left out.
 * Mirrors the web app's src/collection/spreadThin.ts.
 */

/** One deck playing the card, and how many copies. */
data class ThinUse(val deckId: String, val deckName: String, val copies: Int)

data class ThinCard(
    val name: String,
    val imageUrl: String?,
    /** The printings the decks play, for a price. */
    val scryfallIds: List<String>,
    /** Copies owned: binders plus the real copies in decks the user holds. */
    val owned: Int,
    /** Copies the decks use between them. */
    val used: Int,
    /** How many more they'd need for every deck to have its own. */
    val short: Int,
    /** Most copies first. */
    val decks: List<ThinUse>
)

/** Copies of each card [deck] asks for: all of them, except a Proxy deck's proxies. */
private fun copiesUsed(deck: Deck): Map<String, Int> =
    deck.cards.groupBy { it.name.lowercase() }
        .mapValues { (_, printings) ->
            printings.sumOf { it.quantity - if (deck.ownershipType == DeckOwnership.PROXY) proxyCopies(deck, it) else 0 }
        }
        .filterValues { it > 0 }

/** Every card the decks use more copies of than the user owns, the shortest first. */
fun spreadThin(collections: List<Collection>, decks: List<Deck>): List<ThinCard> {
    val owned = mutableMapOf<String, Int>()
    collections.filter { it.kind == CollectionType.OWNED }.forEach { collection ->
        collection.entries.forEach { entry ->
            val key = entry.name.lowercase()
            owned[key] = (owned[key] ?: 0) + entry.quantity + entry.foilQuantity
        }
    }
    decks.forEach { deck -> copiesHeld(deck).forEach { (key, copies) -> owned[key] = (owned[key] ?: 0) + copies } }

    class Acc(val name: String, val imageUrl: String?) {
        val ids = mutableListOf<String>()
        val uses = mutableListOf<ThinUse>()
    }
    val byName = LinkedHashMap<String, Acc>()
    decks.forEach { deck ->
        val used = copiesUsed(deck)
        deck.cards.forEach { entry ->
            val key = entry.name.lowercase()
            val copies = used[key] ?: return@forEach
            if (isBasicLand(entry.name)) return@forEach
            val acc = byName.getOrPut(key) { Acc(entry.name, entry.imageUrl) }
            if (entry.scryfallId !in acc.ids) acc.ids += entry.scryfallId
            // A deck holding two printings of a card is one deck using it.
            if (acc.uses.none { it.deckId == deck.id }) acc.uses += ThinUse(deck.id, deck.name, copies)
        }
    }
    return byName.map { (key, acc) ->
        val used = acc.uses.sumOf { it.copies }
        val have = owned[key] ?: 0
        ThinCard(
            name = acc.name,
            imageUrl = acc.imageUrl,
            scryfallIds = acc.ids.toList(),
            owned = have,
            used = used,
            short = used - have,
            decks = acc.uses.sortedWith(compareByDescending<ThinUse> { it.copies }.thenBy { it.deckName })
        )
    }
        .filter { it.short > 0 }
        .sortedWith(compareByDescending<ThinCard> { it.short }.thenByDescending { it.used }.thenBy { it.name })
}

/**
 * What the copies [card] is short of cost, in US dollars: its cheapest printing among the decks' (by
 * [prices], scryfallId → non-foil price). Null when none of them has a price.
 */
fun shortCost(card: ThinCard, prices: Map<String, Double?>): Double? =
    card.scryfallIds.mapNotNull { prices[it] }.filter { it.isFinite() }.minOrNull()?.let { it * card.short }

/** The copies to buy as text, "2 Sol Ring" a line — the list format the app exports and TCGplayer reads. */
fun shortBuyList(cards: List<ThinCard>): String =
    buildCardListText(cards.map { CollectionEntry(it.scryfallIds.firstOrNull().orEmpty(), it.name, null, quantity = it.short) })

/** "You own 2 · 4 decks use 4". */
fun thinLine(card: ThinCard): String =
    "You own ${card.owned} · ${card.decks.size} ${if (card.decks.size == 1) "deck uses" else "decks use"} ${card.used}"
