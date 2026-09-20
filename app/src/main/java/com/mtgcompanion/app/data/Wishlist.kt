package com.mtgcompanion.app.data

// The Wishlist: one built-in binder of cards the user wants, the same id on every device (so two
// devices' Wishlists merge into one when they sync). It can't be deleted, and — like any wishlist —
// its cards count toward nothing the user owns. It holds what the user adds, and by itself the
// cards their decks are considering that they don't own. Older wishlist binders fold into it.
// Mirrors the web app's src/collection/wishlist.ts.

const val WISHLIST_ID = "wishlist"
const val WISHLIST_NAME = "Wishlist"

val Collection.isWishlist: Boolean get() = id == WISHLIST_ID

private fun key(name: String) = name.trim().lowercase()

/**
 * [collections] with the Wishlist as it should be:
 *  - there, whatever else;
 *  - holding the cards of any other wishlist binder, which then go (their copies added together,
 *    a price alert kept);
 *  - with each card a deck in [decks] is considering and the user owns in no binder, added by
 *    itself ([CollectionEntry.auto]) — and taken off again once it's owned or no longer considered.
 *    A card the user added themselves is never taken off.
 * The same list (the same instance) when nothing needs to change.
 */
fun withWishlist(collections: List<Collection>, decks: List<Deck>): List<Collection> {
    val others = collections.filter { it.kind == CollectionType.WISHLIST && !it.isWishlist }
    val existing = collections.firstOrNull { it.isWishlist }
    var entries = existing?.entries.orEmpty()
    for (entry in others.flatMap { it.entries }) {
        val i = entries.indexOfFirst { it.scryfallId == entry.scryfallId }
        entries = if (i < 0) entries + entry.copy(auto = false)
        else entries.toMutableList().also {
            val had = it[i]
            it[i] = had.copy(
                quantity = had.quantity + entry.quantity,
                foilQuantity = had.foilQuantity + entry.foilQuantity,
                priceAlert = had.priceAlert ?: entry.priceAlert,
                auto = false
            )
        }
    }

    // Taken off by hand ("not interested"), and still considered — a card nobody considers any more
    // is forgotten, so putting it back in a deck's Considering list offers it again.
    val notWanted = existing?.notWanted.orEmpty()

    // Owned: in any binder that isn't a wishlist (the Unsorted pile too).
    val owned = collections.filter { it.kind != CollectionType.WISHLIST }
        .flatMap { it.entries }.filter { it.quantity + it.foilQuantity > 0 }.map { key(it.name) }.toSet()
    val considered = LinkedHashMap<String, DeckCardEntry>()
    for (deck in decks) for (card in deck.considering) considered.putIfAbsent(key(card.name), card)
    // A card the user put on the list themselves is wanted, whatever they said before.
    val byHand = entries.filterNot { it.auto }.map { key(it.name) }.toSet()
    val stillNotWanted = notWanted.filter { key(it) in considered.keys && key(it) !in byHand }
    val notWantedKeys = stillNotWanted.map { key(it) }.toSet()
    val wanted = considered.filterKeys { it !in owned && it !in notWantedKeys }

    entries = entries.filter { !it.auto || key(it.name) in wanted }
    val have = entries.map { key(it.name) }.toSet()
    entries = entries + wanted.filterKeys { it !in have }.values.map { card ->
        CollectionEntry(card.scryfallId, card.name, card.imageUrl, quantity = 1, backImageUrl = card.backImageUrl, auto = true)
    }

    val wishlist = (existing ?: Collection(WISHLIST_ID, WISHLIST_NAME, createdAt = 0, type = CollectionType.WISHLIST.name))
        .copy(name = WISHLIST_NAME, type = CollectionType.WISHLIST.name, entries = entries, notWanted = stillNotWanted)
    if (others.isEmpty() && existing == wishlist) return collections
    return collections.filterNot { it.kind == CollectionType.WISHLIST || it.isWishlist } + wishlist
}

/**
 * [collections] with [cards] on the Wishlist, making it if it isn't there. A card already on the
 * list keeps the larger count rather than doubling, and asking for a card by hand undoes an earlier
 * "not interested". [CollectionEntry.quantity] is the copies wanted — a deck's missing cards ask
 * for as many as the deck plays.
 */
fun withWantedCards(collections: List<Collection>, cards: List<CollectionEntry>): List<Collection> {
    if (cards.isEmpty()) return collections
    val existing = collections.firstOrNull { it.isWishlist }
    var entries = existing?.entries.orEmpty()
    for (card in cards) {
        val at = entries.indexOfFirst { key(it.name) == key(card.name) }
        val want = maxOf(1, card.quantity)
        entries = if (at < 0) entries + card.copy(quantity = want, auto = false)
        else entries.mapIndexed { i, e -> if (i != at) e else e.copy(quantity = maxOf(e.quantity, want), auto = false) }
    }
    val asked = cards.map { key(it.name) }.toSet()
    val wishlist = (existing ?: Collection(WISHLIST_ID, WISHLIST_NAME, createdAt = 0, type = CollectionType.WISHLIST.name))
        .copy(entries = entries, notWanted = existing?.notWanted.orEmpty().filterNot { key(it) in asked })
    return if (existing != null) collections.map { if (it.isWishlist) wishlist else it } else collections + wishlist
}

/**
 * [collections] with [cardName] off the Wishlist and left off while decks still consider it — what
 * taking off a card the Wishlist added by itself means. Adding it back by hand undoes this.
 */
fun withoutWishlistCard(collections: List<Collection>, cardName: String): List<Collection> =
    collections.map { c ->
        if (!c.isWishlist) c
        else c.copy(
            entries = c.entries.filterNot { key(it.name) == key(cardName) },
            notWanted = (c.notWanted + cardName.trim()).distinctBy { key(it) }
        )
    }

/**
 * [collections] with [cardName] wanted again — undoing "not interested". The card comes back by
 * itself while a deck considers it.
 */
fun withWishlistCardWantedAgain(collections: List<Collection>, cardName: String): List<Collection> =
    collections.map { c ->
        if (!c.isWishlist) c else c.copy(notWanted = c.notWanted.filterNot { key(it) == key(cardName) })
    }

/** The names of [decks] considering [card] — for "Considering in …" on a card added from them. */
fun decksConsidering(decks: List<Deck>, cardName: String): List<String> =
    decks.filter { d -> d.considering.any { key(it.name) == key(cardName) } }.map { it.name }
