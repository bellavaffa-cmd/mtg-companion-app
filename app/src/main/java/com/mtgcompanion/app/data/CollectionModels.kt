package com.mtgcompanion.app.data

data class CollectionEntry(
    val scryfallId: String,
    val name: String,
    val imageUrl: String?,
    val quantity: Int = 0,
    val foilQuantity: Int = 0
)

/** OWNED binders are the physical collection; WISHLIST binders track cards not owned yet. */
enum class CollectionType {
    OWNED, WISHLIST;
    companion object {
        val DEFAULT = OWNED
        fun fromName(name: String?): CollectionType = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

data class Collection(
    val id: String,
    val name: String,
    val entries: List<CollectionEntry> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val type: String = CollectionType.DEFAULT.name
) {
    val kind: CollectionType get() = CollectionType.fromName(type)
}

data class CollectionStore(
    val collections: List<Collection> = emptyList(),
    // Legacy single-collection field, kept so a pre-multi-collection store migrates
    // into one default collection instead of being lost.
    val entries: List<CollectionEntry>? = null
)
