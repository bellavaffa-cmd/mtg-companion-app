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
