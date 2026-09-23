package com.mtgcompanion.app.data

data class CollectionEntry(
    val scryfallId: String,
    val name: String,
    val imageUrl: String?,
    val quantity: Int = 0,
    val foilQuantity: Int = 0,
    // Cached from ScryfallCard.backImageUrl — see DeckCardEntry.backImageUrl for why.
    val backImageUrl: String? = null,
    // Cached from ScryfallCard.tags — see DeckCardEntry.tags for why.
    val tags: List<String> = emptyList(),
    /**
     * The user's own words about this copy — "proxy", "signed", "lent to Sam". They belong to the
     * copy rather than to the card, so they follow it from a binder into a deck and back, and every
     * entry holding the same printing carries the same set. Not [tags], which Scryfall writes and
     * the user can't change, and not [replaceable], which is only true inside one deck.
     */
    val userTags: List<String> = emptyList(),
    /** Wishlists: tell the user when this card's price (USD, non-foil) is at or under this (see PriceAlerts). */
    val priceAlert: Double? = null,
    /** In the Wishlist by itself: a deck is considering it and the user doesn't own it (see [withWishlist]). */
    val auto: Boolean = false
)

/** OWNED binders are the physical collection; WISHLIST binders track cards not owned yet. */
enum class CollectionType {
    OWNED, WISHLIST;
    companion object {
        val DEFAULT = OWNED
        fun fromName(name: String?): CollectionType = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * The one pile of owned cards that aren't in a binder yet — a whole collection imported before it's
 * sorted. The same id on every device, so piles made on two devices merge into one when they sync.
 */
const val UNSORTED_COLLECTION_ID = "unsorted"
const val UNSORTED_COLLECTION_NAME = "Unsorted"

data class Collection(
    val id: String,
    val name: String,
    val entries: List<CollectionEntry> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val type: String = CollectionType.DEFAULT.name,
    /**
     * The Wishlist: cards taken off it that a deck is still considering, so they aren't put back
     * (see [withWishlist]). Lower-cased names. Forgotten once no deck considers the card.
     */
    val notWanted: List<String> = emptyList()
) {
    val kind: CollectionType get() = CollectionType.fromName(type)
    /** The pile of cards not in a binder yet (see [UNSORTED_COLLECTION_ID]) — not a binder itself. */
    val isUnsorted: Boolean get() = id == UNSORTED_COLLECTION_ID
}

data class CollectionStore(
    val collections: List<Collection> = emptyList(),
    /** Binders the user deleted here and when, by id — see DeckStore.deleted. */
    val deleted: Map<String, Long> = emptyMap(),
    /**
     * What each printing is tagged, by scryfallId — this store's own note, so a copy keeps its tags
     * when it's moved or re-added and a fresh entry is made for it (see UserTags.kt). Local
     * bookkeeping, rebuilt from the entries, which are what sync.
     */
    val userTags: Map<String, List<String>> = emptyMap(),

    // Legacy single-collection field, kept so a pre-multi-collection store migrates
    // into one default collection instead of being lost.
    val entries: List<CollectionEntry>? = null
)
