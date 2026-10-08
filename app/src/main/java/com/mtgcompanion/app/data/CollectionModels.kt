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
    /** Wishlists: tell the user when this card's price (USD, non-foil) is at or under this — its target (see PriceAlerts). */
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
    val places: List<CopyPlace>? = null,
    /**
     * Owned binders: how many of these copies the user offers for trade — friends see them (see
     * social/SocialMoreLogic.kt). Never more than the copies; null (left out) when none.
     */
    val forTrade: Int? = null,
    /**
     * Owned binders: how many of these copies the user means to sell (see Selling.kt). Never more than
     * the copies. Null (left out) until the entry is first marked to sell; then kept, as 0 once none
     * are — so an entry with no "forSale" key on a device that had one was saved by an app that
     * doesn't know about selling, and keepForSaleFromOlderApp puts it back.
     */
    val forSale: Int? = null,
    /**
     * Wishlists: any printing of the card counts for [priceAlert] — the cheapest is checked (see
     * WishlistTargets.kt). Null (left out) until a target is set with it; then true or false, so an
     * entry without it on a device that had it was saved by an app that doesn't know it.
     */
    val alertAnyPrinting: Boolean? = null,
    /** Wishlists: only a foil copy will do — [priceAlert] is checked against the foil price. Null and kept as [alertAnyPrinting]. */
    val alertFoilOnly: Boolean? = null
)

// The entry as JSON — locally, in sync and in shared binders — is these fields by name. Keys added
// for collecting, which the web app reads and writes the same way (all optional, left out when null):
//   "priceAlert":      number, USD — wishlists: notify when the price is at or below it
//   "priceAlertAbove": number, USD — owned binders: notify when the price is at or above it
//   "alertAnyPrinting": boolean — wishlists: any printing counts for "priceAlert" (the cheapest is checked)
//   "alertFoilOnly":   boolean — wishlists: "priceAlert" is checked against the foil price
//   "condition":       "NM" | "LP" | "MP" | "HP" | "DMG" — for every copy in the entry
//   "language":        "en" | "ja" | "de" | "fr" | "it" | "es" | "pt" | "ru" | "ko" | "zhs" | "zht"
//   "places":          [{ "placeId": "…", "qty": 2, "foil": true, "section": "Red" }, { "placeId": "…", "qty": 1, "page": 3, "slot": 5 }]
//                      where the copies are kept ([CopyPlace]); "foil", "section", "page" and "slot" left out when not said
//   "forTrade":        number — owned binders: how many of the copies are for trade (friends can see them)
//   "forSale":         number — owned binders: how many of the copies are to sell (0 once none are; Selling.kt)
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
    /** A box's sorting rule, or a binder's order (see BinderPages.kt). */
    val sortRule: String? = null,
    val createdAt: Long = 0L,
    /**
     * When the place was last checked by scanning everything in it (PlaceCheck.kt), in milliseconds;
     * null (left out) until then. It only moves on: two devices' checks merge to the later one, and a
     * place saved by an app that doesn't know it keeps it.
     */
    val lastChecked: Long? = null,
    /**
     * How many cards a box (or any place but a binder) holds, for how full it is (BoxSpace.kt). Null
     * (left out) until a size is set; 0 once it's taken off — so a place with no "capacity" on a
     * device that had one was saved by an app that doesn't know about sizes (keepPlaceSizes).
     */
    val capacity: Int? = null,
    /** A binder's pages: its size is pages × pockets per page. Null and 0 as [capacity]. */
    val pages: Int? = null
) {
    val placeKind: PlaceKind get() = PlaceKind.fromName(kind)
    val rule: SortRule? get() = SortRule.fromName(sortRule)
}

// The pile's "loans" as JSON — locally and in sync, the web app's exactly:
//   "loans": [{ "id": "…", "to": "Sam", "friendId": "…", "lentAt": 1790000000000, "backBy": "2026-10-12",
//               "gameNight": true, "note": "for Saturday", "returnedAt": 1790000000000,
//               "cards": [{ "name": "Sol Ring", "scryfallId": "…", "qty": 1, "deckId": "…" },
//                         { "name": "The One Ring", "scryfallId": "…", "qty": 1, "foil": true, "collectionId": "unsorted",
//                           "placeId": "…", "page": 3, "slot": 5, "back": 1 }] }]
// Optional keys are null (left out) when not said, as in a binder entry's "places".

/**
 * Copies of one card lent in a loan, with where they came from: a deck ([deckId]), or a binder's
 * entry ([collectionId]) — and, when they were in a place, that spot ([placeId], [section], [page],
 * [slot]), so getting them back puts them there again. [back]: how many have come back. [foil] is
 * true or null, never false. The web app's LoanCard, field for field (src/types/models.ts).
 */
data class LoanCard(
    val name: String,
    val scryfallId: String,
    val qty: Int = 0,
    val foil: Boolean? = null,
    val collectionId: String? = null,
    val placeId: String? = null,
    val section: String? = null,
    val page: Int? = null,
    val slot: Int? = null,
    val deckId: String? = null,
    val back: Int? = null
) {
    val isFoil: Boolean get() = foil == true
}

/**
 * Cards lent to someone: a friend by account ([friendId]) or anyone by name ([to] — a friend's name
 * too). [backBy]: the day they're due back ("2026-10-12"); [gameNight]: true when due at the next game
 * night (null otherwise). [returnedAt]: when the last card came back. The web app's Loan, field for field.
 */
data class Loan(
    val id: String,
    val to: String,
    val friendId: String? = null,
    val cards: List<LoanCard> = emptyList(),
    val lentAt: Long = 0L,
    val backBy: String? = null,
    val gameNight: Boolean? = null,
    val note: String? = null,
    val returnedAt: Long? = null
)

// The pile's "sealed" and "graded" as JSON — locally and in sync, the web app's exactly:
//   "sealed": [{ "id": "…", "name": "Duskmourn Play Booster Box", "kind": "PLAY_BOX", "setCode": "dsk", "count": 2,
//                "placeId": "…", "paidUsd": 210, "valueUsd": 238, "valueAt": 1790000000000, "createdAt": 1790000000000 },
//              { "id": "…", "name": "Precon: Blame Game", "kind": "PRECON", "preconFile": "BlameGame_DSC", "count": 1 }]
//   "graded": [{ "id": "…", "scryfallId": "…", "name": "Sheoldred, the Apocalypse", "imageUrl": "…", "foil": true,
//                "company": "PSA", "companyName": "…", "grade": "10", "cert": "…", "valueUsd": 450, "placeId": "…",
//                "section": "Slabs", "collectionId": "…", "createdAt": 1790000000000 }]
// Optional keys are null (left out) when not said. Values are what the user entered, in US dollars: no
// app has prices for sealed product or graded copies.

/**
 * Sealed product the user keeps: [count] of one product in one place ([placeId]). [kind] is a
 * [SealedKind] name. [paidUsd] and [valueUsd] are each, in US dollars, as the user entered them
 * ([valueAt]: when the value was last entered). A precon names its MTGJSON deck ([preconFile]) so
 * opening it makes the deck. The web app's SealedProduct, field for field (src/types/models.ts).
 */
data class SealedProduct(
    val id: String,
    val name: String,
    val kind: String = SealedKind.OTHER.name,
    val setCode: String? = null,
    val preconFile: String? = null,
    val count: Int = 0,
    val placeId: String? = null,
    val paidUsd: Double? = null,
    val valueUsd: Double? = null,
    val valueAt: Long? = null,
    val createdAt: Long = 0L
) {
    val sealedKind: SealedKind get() = SealedKind.fromName(kind)
}

/** What a sealed product is. */
enum class SealedKind(val label: String) {
    PLAY_BOX("Play Booster Box"), COLLECTOR_BOX("Collector Box"), SET_BOX("Set Booster Box"), DRAFT_BOX("Draft Booster Box"),
    BUNDLE("Bundle"), PRECON("Precon"), OTHER("Other");
    companion object {
        fun fromName(name: String?): SealedKind = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

/**
 * One graded copy (a slab), kept apart from the raw copies: it isn't in any binder's counts, so it
 * never fills a deck slot or counts as a spare. [company] is a [GradingCompany] name; [grade] as the
 * slab says it ("10", "9.5"); [cert]: its cert number; [valueUsd]: what the user says it's worth (card
 * prices are for ungraded copies); where it is ([placeId], [section]); [collectionId]: the binder the
 * copy came from, to go back to if it's cracked out. [companyName]: who, when [company] is OTHER.
 * [foil] is true or null, never false. The web app's GradedCard, field for field.
 */
data class GradedCard(
    val id: String,
    val scryfallId: String,
    val name: String,
    val imageUrl: String? = null,
    val foil: Boolean? = null,
    val company: String = GradingCompany.OTHER.name,
    val companyName: String? = null,
    val grade: String = "",
    val cert: String? = null,
    val valueUsd: Double? = null,
    val placeId: String? = null,
    val section: String? = null,
    val collectionId: String? = null,
    val createdAt: Long = 0L
) {
    val isFoil: Boolean get() = foil == true
    val grader: GradingCompany get() = GradingCompany.fromName(company)
}

/** Who graded a copy. */
enum class GradingCompany(val label: String) {
    PSA("PSA"), BGS("BGS"), CGC("CGC"), OTHER("Other");
    companion object {
        fun fromName(name: String?): GradingCompany = entries.firstOrNull { it.name == name } ?: OTHER
    }
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
    val storagePlaces: List<StoragePlace>? = null,
    /**
     * The Unsorted pile only: the user's loans — cards lent to a friend or anyone else (see Loans.kt).
     * They ride along with the pile when it syncs, like [storagePlaces], and merge loan by loan. Null
     * (left out) until the first loan; then kept, as an empty list once none are left — so a pile with
     * no "loans" key was saved by an app that doesn't know about loans.
     */
    val loans: List<Loan>? = null,
    /**
     * The Unsorted pile only: sealed product the user keeps — booster boxes, bundles, precons (see
     * Sealed.kt). Rides along with the pile like [loans], merged product by product. Null (left out)
     * until the first one; then kept, as an empty list once none are left — so a pile with no "sealed"
     * key was saved by an app that doesn't know about sealed product.
     */
    val sealed: List<SealedProduct>? = null,
    /**
     * The Unsorted pile only: graded copies — slabs, kept apart from raw copies (see Graded.kt). Rides
     * along like [sealed], merged slab by slab; null and kept the same way.
     */
    val graded: List<GradedCard>? = null,
    /**
     * The Unsorted pile only: the user's gear — sleeves, deck boxes, tokens, dice, playmats (see
     * Gear.kt). Rides along with the pile like [loans] and merges item by item. Null (left out) until
     * the first item; then kept, as an empty list once none are left.
     */
    val gear: List<GearItem>? = null,
    /**
     * The Unsorted pile only: the user's own sorting recipes (see SortRecipes.kt). Rides along like
     * [gear] and merges recipe by recipe. Null (left out) until the first is saved; then kept, as an
     * empty list once none are left — so a pile with no "sortRecipes" key was saved by an app that
     * doesn't know about them.
     */
    val sortRecipes: List<SortRecipe>? = null,
    /**
     * The Unsorted pile only: the user's collection goals (see CollectionGoals.kt). Rides along like
     * [sortRecipes] and merges goal by goal. Null (left out) until the first is saved; then kept, as an
     * empty list once none are left — so a pile with no "collectionGoals" key was saved by an app that
     * doesn't know about them.
     */
    val collectionGoals: List<CollectionGoal>? = null,
    /** A sample binder from the welcome flow — see Deck.sample. Null (left out) on everything else. */
    val sample: Boolean? = null
) {
    val kind: CollectionType get() = CollectionType.fromName(type)
    /** The pile of cards not in a binder yet (see [UNSORTED_COLLECTION_ID]) — not a binder itself. */
    val isUnsorted: Boolean get() = id == UNSORTED_COLLECTION_ID
}

/**
 * A piece of gear (Gear.kt). [kind] is a [GearKind] name. [count]: sleeves left, tokens of that name,
 * dice… (1 for a deck box). [usedBy]: the decks, binders or places a pack of sleeves is on. [holds]:
 * the deck (or binder) a deck box holds. [placeId]: where it's kept. Optional fields are null (left
 * out of the JSON) when not set. The web app's GearItem, field for field.
 */
data class GearItem(
    val id: String,
    val kind: String = GearKind.OTHER.name,
    val name: String,
    val count: Int = 0,
    val usedBy: List<String>? = null,
    val holds: String? = null,
    val placeId: String? = null,
    val note: String? = null,
    val createdAt: Long = 0L
) {
    val gearKind: GearKind get() = GearKind.fromName(kind)
}

/** What a piece of gear is. */
enum class GearKind(val label: String) {
    SLEEVES("Sleeves"), INNER_SLEEVES("Inner sleeves"), DECK_BOX("Deck box"), TOKENS("Tokens"), DICE("Dice"), PLAYMAT("Playmat"), OTHER("Other");

    companion object {
        fun fromName(name: String?): GearKind = entries.firstOrNull { it.name == name } ?: OTHER
    }
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
