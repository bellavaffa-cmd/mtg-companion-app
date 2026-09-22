package com.mtgcompanion.app.data

/**
 * The Unsorted pile: cards the user owns that aren't in a binder or a deck yet. Like the Wishlist
 * it's always there — it used to appear only once the scanner or an import dropped something in it,
 * which left nowhere obvious to put a loose card by hand. It can be emptied but not deleted, and it
 * isn't a binder: it isn't counted as one, and sits above the binders rather than among them.
 * Mirrors the web app's src/collection/unsorted.ts.
 */

/**
 * [collections] with the Unsorted pile in it. The same list when it's already there, so a caller
 * can tell nothing changed. Made with createdAt 0, like the Wishlist, so two devices that each make
 * one agree on it exactly rather than trading timestamps back and forth.
 */
fun withUnsortedPile(collections: List<Collection>): List<Collection> =
    if (collections.any { it.isUnsorted }) collections
    else collections + Collection(UNSORTED_COLLECTION_ID, UNSORTED_COLLECTION_NAME, createdAt = 0, type = CollectionType.OWNED.name)

/**
 * Whether this is one of the user's binders — not the Wishlist, and not the Unsorted pile. Both of
 * those are always there, so counting them as binders told an empty library it had two.
 */
val Collection.isBinder: Boolean get() = !isUnsorted && !isWishlist

/** The collections that are always there: the Wishlist, kept up for [decks], and the Unsorted pile. */
fun withStandingCollections(collections: List<Collection>, decks: List<Deck>): List<Collection> =
    withUnsortedPile(withWishlist(collections, decks))

/** The same card by name: equal once case is ignored, and either face of a double-faced card counts. */
private fun sameCardByName(a: String, b: String): Boolean {
    val fa = a.lowercase().split(" // ")
    return b.lowercase().split(" // ").any { it in fa }
}

/**
 * The Unsorted pile's [entries] once [count] copies of a card ([scryfallId], [name]) have gone into
 * one of the user's physical decks — the loose copies are the ones that went: that printing's first,
 * then other printings of the same card; plain before foil. Entries left with no copies go. Also how
 * many were taken: fewer than [count] when the pile didn't have that many. Mirrors the web app's
 * takenFromUnsorted in src/collection/unsorted.ts.
 */
fun takenFromUnsorted(entries: List<CollectionEntry>, scryfallId: String, name: String, count: Int): Pair<List<CollectionEntry>, Int> {
    var left = count
    val order = entries.filter { it.scryfallId == scryfallId } +
        entries.filter { it.scryfallId != scryfallId && sameCardByName(it.name, name) }
    val after = HashMap<CollectionEntry, CollectionEntry>()
    for (entry in order) {
        if (left <= 0) break
        val plain = minOf(entry.quantity, left)
        left -= plain
        val foil = minOf(entry.foilQuantity, left)
        left -= foil
        after[entry] = entry.copy(quantity = entry.quantity - plain, foilQuantity = entry.foilQuantity - foil)
    }
    if (left == count) return entries to 0
    return entries.map { after[it] ?: it }.filter { it.quantity + it.foilQuantity > 0 } to count - left
}

/** Whether a deck holds the user's own copies — only then does adding to it take them out of Unsorted. */
val Deck.holdsOwnCopies: Boolean get() = DeckOwnership.fromName(ownership) == DeckOwnership.PHYSICAL

/**
 * The real copies a deck holds, as Unsorted entries — where its cards go when the deck is deleted but
 * the cards kept. Only a physical deck holds the user's own copies, and not its proxies (see
 * proxyCopies); its commander is one of its cards. Mirrors the web app's realCopiesOf.
 */
fun realCopiesOf(deck: Deck): List<CollectionEntry> =
    if (!deck.holdsOwnCopies) emptyList()
    else deck.cards.map {
        CollectionEntry(it.scryfallId, it.name, it.imageUrl, quantity = it.quantity - proxyCopies(deck, it), backImageUrl = it.backImageUrl, tags = it.tags)
    }.filter { it.quantity > 0 }
