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
    val tags: List<String> = emptyList()
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
    val type: String = CollectionType.DEFAULT.name
) {
    val kind: CollectionType get() = CollectionType.fromName(type)
    /** The pile of cards not in a binder yet (see [UNSORTED_COLLECTION_ID]) — not a binder itself. */
    val isUnsorted: Boolean get() = id == UNSORTED_COLLECTION_ID
}

data class CollectionStore(
    val collections: List<Collection> = emptyList(),
    // Legacy single-collection field, kept so a pre-multi-collection store migrates
    // into one default collection instead of being lost.
    val entries: List<CollectionEntry>? = null
)
