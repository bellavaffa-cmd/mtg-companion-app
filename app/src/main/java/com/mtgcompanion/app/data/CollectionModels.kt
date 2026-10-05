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
    val auto: Boolean = false,
    /**
     * Owned binders: tell the user when this card's price rises to this or more (US dollars; see
     * PriceAlertRules.kt). Checked against the non-foil price, or the foil price when every copy in
     * the entry is foil. The "above" twin of [priceAlert], which is the wishlist's "at or below".
     */
    val priceAlertAbove: Double? = null,
    /** The copies' condition: one of [CARD_CONDITIONS] ("NM", "LP", "MP", "HP", "DMG"); null = not said. */
    val condition: String? = null,
    /** The language the copies are printed in, as Scryfall codes it ([CARD_LANGUAGES]: "en", "ja"…); null = not said. */
    val language: String? = null,
    /**
     * Where the copies physically are: some in one storage place, some in another (see
     * StoragePlaces.kt). Never more than the entry's copies, plain and foil apart; the rest have no
     * place yet. Null (left out of the JSON) until a copy is given a place; then kept, as an empty
     * list once none have one — so a binder with no "places" key anywhere was written by an app that
     * doesn't know about places.
     */
    val places: List<CopyPlace>? = null
)

// The entry as JSON — locally, in sync and in shared binders — is these fields by name. Keys added
// for collecting, which the web app reads and writes the same way (all optional, left out when null):
//   "priceAlert":      number, USD — wishlists: notify when the price is at or below it
//   "priceAlertAbove": number, USD — owned binders: notify when the price is at or above it
//   "condition":       "NM" | "LP" | "MP" | "HP" | "DMG" — for every copy in the entry
//   "language":        "en" | "ja" | "de" | "fr" | "it" | "es" | "pt" | "ru" | "ko" | "zhs" | "zht"
//   "places":          [{ "placeId": "…", "qty": 2, "foil": true, "section": "Red" }, { "placeId": "…", "qty": 1, "page": 3, "slot": 5 }]
//                      where the copies are kept ([CopyPlace]); "foil", "section", "page" and "slot" left out when not said
// Copies of one printing in different conditions aren't split into entries: the entry says one.

/**
 * Some of an entry's copies in one storage place. [foil]: these are foil copies (null: plain — never
 * written as false, so the JSON is the web app's). In a box, [section] names the section ("Red",
 * "2X2"); in a binder, [page] and [slot] (from 1) say which pocket. The web app's CopyPlace, field for
 * field (src/types/models.ts).
 */
data class CopyPlace(
    val placeId: String,
    val qty: Int = 0,
    val foil: Boolean? = null,
    val section: String? = null,
    val page: Int? = null,
    val slot: Int? = null
) {
    val isFoil: Boolean get() = foil == true
}

/**
 * A physical place cards are kept — a box, a binder, a shelf — made by the user and nestable
 * ("Shelf › Red box"). Kept in the Unsorted pile's [Collection.storagePlaces], so it syncs with the
 * library. [kind] is a [PlaceKind] name, [sortRule] a [SortRule] name; optional fields are null (left
 * out of the JSON) when not set. The web app's StoragePlace, field for field.
 */
data class StoragePlace(
    val id: String,
    val name: String,
    val kind: String = PlaceKind.OTHER.name,
    /** The place it sits in; null at the top. */
    val parentId: String? = null,
    /** A word about it — "Bulk", "Trade fodder". */
    val note: String? = null,
    /** A box's sections, in order. */
    val sections: List<String>? = null,
    /** A binder's pockets per page (9 when null). */
    val pocketsPerPage: Int? = null,
    /** A box's sorting rule. */
    val sortRule: String? = null,
    val createdAt: Long = 0L
) {
    val placeKind: PlaceKind get() = PlaceKind.fromName(kind)
    val rule: SortRule? get() = SortRule.fromName(sortRule)
}

/** What a storage place is. */
enum class PlaceKind(val label: String) {
    BOX("Box"), BINDER("Binder"), DECK_BOX("Deck box"), SHELF("Shelf"), OTHER("Other");
    companion object {
        fun fromName(name: String?): PlaceKind = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

/** How a box is sorted, to suggest where a new card goes. [short] is how a line says it: "Bulk · by colour, then A–Z". */
enum class SortRule(val label: String, val short: String) {
    COLOUR("By colour, then A–Z", "by colour, then A–Z"),
    SET("By set, then number", "by set, then number"),
    TYPE("By type, then A–Z", "by type, then A–Z"),
    NAME("A–Z", "A–Z");
    companion object {
        fun fromName(name: String?): SortRule? = entries.firstOrNull { it.name == name }
    }
}

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
    val notWanted: List<String> = emptyList(),
    /**
     * The Unsorted pile only: the user's storage places (see StoragePlaces.kt). The pile is always
     * there and has the same id on every device, so the places ride along with it when it syncs.
     * Null (left out) until the first place is made; then kept, as an empty list once none are left.
     */
    val storagePlaces: List<StoragePlace>? = null
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
