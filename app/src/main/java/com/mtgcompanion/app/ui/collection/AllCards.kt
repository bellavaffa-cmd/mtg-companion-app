package com.mtgcompanion.app.ui.collection

import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionType
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.proxyCopies
import com.mtgcompanion.app.ui.common.CardSource
import com.mtgcompanion.app.ui.common.SourceKind

/**
 * All cards: every card owned anywhere (all binders — the Unsorted pile too, never a wishlist — and
 * all decks), one entry a printing, with the copies added up and where they are; A to Z. Kept apart
 * from the view model so the speed guards (BigCollectionPerfTest) can time it on a 25,000-copy
 * collection. The web app's allCardsOf (src/collection/allCards.ts).
 */
fun allCardEntries(collections: List<Collection>, decks: List<Deck>): List<AllCardEntry> {
    // Accumulate total copies plus the list of binders/decks holding each card.
    class Acc(val name: String, val imageUrl: String?, val backImageUrl: String?, val tags: List<String>) {
        var total = 0
        var proxies = 0
        val sources = mutableListOf<CardSource>()
    }
    val byCard = LinkedHashMap<String, Acc>()
    fun add(id: String, name: String, imageUrl: String?, backImageUrl: String?, tags: List<String>, qty: Int, source: CardSource, proxy: Boolean = false) {
        if (qty <= 0) return
        val acc = byCard.getOrPut(id) { Acc(name, imageUrl, backImageUrl, tags) }
        acc.total += qty
        if (proxy) acc.proxies += qty
        acc.sources += source
    }
    // Wishlist binders track cards not yet owned, so they don't count toward "owned" totals.
    collections.filter { it.kind == CollectionType.OWNED }.forEach { collection ->
        collection.entries.forEach {
            val qty = it.quantity + it.foilQuantity
            add(it.scryfallId, it.name, it.imageUrl, it.backImageUrl, it.tags, qty, CardSource(SourceKind.BINDER, collection.id, collection.name, qty))
        }
    }
    decks.forEach { deck ->
        deck.cards.forEach {
            // A deck marked Proxy is proxies until real copies are swapped in, card by card.
            val proxies = proxyCopies(deck, it)
            add(it.scryfallId, it.name, it.imageUrl, it.backImageUrl, it.tags, it.quantity - proxies, CardSource(SourceKind.DECK, deck.id, deck.name, it.quantity - proxies))
            add(it.scryfallId, it.name, it.imageUrl, it.backImageUrl, it.tags, proxies, CardSource(SourceKind.DECK, deck.id, deck.name, proxies), proxy = true)
        }
    }
    // Each name lowered once, not once per comparison: sorting 18,000 printings did it hundreds of
    // thousands of times. The same order as sortedBy { it.name.lowercase() }.
    return byCard.map { (id, acc) -> AllCardEntry(id, acc.name, acc.imageUrl, acc.total, acc.proxies, acc.sources.toList(), acc.backImageUrl, acc.tags) }
        .map { it.name.lowercase() to it }
        .sortedBy { it.first }
        .map { it.second }
}
