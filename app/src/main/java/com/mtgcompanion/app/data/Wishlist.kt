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

    // Owned: in any binder that isn't a wishlist (the Unsorted pile too).
    val owned = collections.filter { it.kind != CollectionType.WISHLIST }
        .flatMap { it.entries }.filter { it.quantity + it.foilQuantity > 0 }.map { key(it.name) }.toSet()
    val considered = LinkedHashMap<String, DeckCardEntry>()
    for (deck in decks) for (card in deck.considering) considered.putIfAbsent(key(card.name), card)
    val wanted = considered.filterKeys { it !in owned }

    entries = entries.filter { !it.auto || key(it.name) in wanted }
    val have = entries.map { key(it.name) }.toSet()
    entries = entries + wanted.filterKeys { it !in have }.values.map { card ->
        CollectionEntry(card.scryfallId, card.name, card.imageUrl, quantity = 1, backImageUrl = card.backImageUrl, auto = true)
    }

    val wishlist = (existing ?: Collection(WISHLIST_ID, WISHLIST_NAME, createdAt = 0, type = CollectionType.WISHLIST.name))
        .copy(name = WISHLIST_NAME, type = CollectionType.WISHLIST.name, entries = entries)
    if (others.isEmpty() && existing == wishlist) return collections
    return collections.filterNot { it.kind == CollectionType.WISHLIST || it.isWishlist } + wishlist
}

/** The names of [decks] considering [card] — for "Considering in …" on a card added from them. */
fun decksConsidering(decks: List<Deck>, cardName: String): List<String> =
    decks.filter { d -> d.considering.any { key(it.name) == key(cardName) } }.map { it.name }
