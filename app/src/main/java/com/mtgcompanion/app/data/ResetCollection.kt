package com.mtgcompanion.app.data

import java.util.Locale

// Settings › Data and speed › Danger zone › Reset collection: what each choice removes, how that's
// said, and the library after it. The web app's src/settings/resetCollection.ts, rule for rule and
// word for word.
//
//  - Cards only: every binder and the Unsorted pile emptied (their cards' places, prices to watch and
//    copies to sell go with the cards). Binders, storage places, the Wishlist, decks, sealed product,
//    graded cards, gear, loans and collection goals stay.
//  - Collection: every card, every binder but the Unsorted pile and the Wishlist (which stay, empty),
//    the storage places, sealed product, graded cards, gear, loans and collection goals. Decks stay, and so does card
//    price history — it's market data, not the collection.
//  - Everything: the collection and every deck, with the decks' history and games logged. Settings,
//    friends and the account stay.
//
// Binders and decks that go are noted as deleted (supabase.noteDeleted), the same as deleting one by
// hand, so the sync pushes their deletion rather than reading an emptied library as one that went
// missing. Emptied ones are pushed empty: the merge (ItemMerge.kt) keeps a card removed on one side
// removed, so other devices' copies of them don't bring the cards back.

enum class ResetScope(val title: String, val detail: String) {
    CARDS(
        "Cards only",
        "Empties every binder and the Unsorted pile. Your binders, storage places, Wishlist, decks, sealed product, graded cards, gear, loans and goals stay."
    ),
    COLLECTION(
        "Collection",
        "Every card and binder, storage places, sealed product, graded cards, gear, loans, goals, and your copies’ photos and history. The Unsorted pile and Wishlist stay, empty. Decks and card prices stay."
    ),
    EVERYTHING(
        "Everything",
        "The whole collection and every deck, with their history and games logged. Settings, friends and your account stay."
    );

    /** Binders go, not just their cards. */
    val whole: Boolean get() = this != CARDS
}

/** The word to type before Reset can be pressed. */
const val RESET_WORD = "RESET"

/** How long Undo is offered, and the deletions held back from the sync meanwhile. */
const val RESET_UNDO_MS = 10_000L

/** When the choice would remove nothing (Reset stays off). */
const val RESET_NOTHING = "Nothing to remove"

/** The message shown with Undo. */
const val RESET_DONE = "Collection reset"

/** Under the Reset button when signed in. */
const val RESET_SYNC_NOTE = "Signed in, the reset reaches your other devices once Undo has gone. Offline, it goes when you’re back online."

/** Whether [typed] is the word, in any case, spaces around it aside. */
fun resetConfirmed(typed: String): Boolean = typed.trim().uppercase(Locale.ROOT) == RESET_WORD

private val Collection.isWishlistType get() = kind == CollectionType.WISHLIST
/** The two that are always there: emptied, never removed. */
private val Collection.isStanding get() = id == UNSORTED_COLLECTION_ID || id == WISHLIST_ID

data class ResetCounts(
    val copies: Int = 0,
    val binders: Int = 0,
    val wishlistCards: Int = 0,
    val places: Int = 0,
    val sealed: Int = 0,
    val graded: Int = 0,
    val gear: Int = 0,
    val loans: Int = 0,
    val goals: Int = 0,
    val decks: Int = 0
)

/** What [scope] would remove from the library. */
fun resetCounts(decks: List<Deck>, collections: List<Collection>, scope: ResetScope): ResetCounts {
    val owned = collections.filterNot { it.isWishlistType }
    val whole = scope.whole
    val pile = collections.firstOrNull { it.id == UNSORTED_COLLECTION_ID }
    return ResetCounts(
        copies = owned.sumOf { c -> c.entries.sumOf { it.quantity + it.foilQuantity } },
        binders = if (whole) collections.count { !it.isStanding }
            else owned.count { it.id != UNSORTED_COLLECTION_ID && it.entries.isNotEmpty() },
        wishlistCards = if (whole) collections.filter { it.isWishlistType }.sumOf { it.entries.size } else 0,
        places = if (whole) pile?.storagePlaces?.size ?: 0 else 0,
        sealed = if (whole) pile?.sealed?.size ?: 0 else 0,
        graded = if (whole) pile?.graded?.size ?: 0 else 0,
        gear = if (whole) pile?.gear?.size ?: 0 else 0,
        loans = if (whole) pile?.loans?.size ?: 0 else 0,
        goals = if (whole) pile?.collectionGoals?.size ?: 0 else 0,
        decks = if (scope == ResetScope.EVERYTHING) decks.size else 0
    )
}

private fun number(n: Int) = String.format(Locale.UK, "%,d", n)
private fun count(n: Int, one: String, many: String) = "${number(n)} ${if (n == 1) one else many}"

/** "1,402 copies in 8 binders · 23 places · 3 decks" — what will go, or that there's nothing to. */
fun resetCountsText(c: ResetCounts): String {
    val cards = when {
        c.copies > 0 && c.binders > 0 -> "${count(c.copies, "copy", "copies")} in ${count(c.binders, "binder", "binders")}"
        c.copies > 0 -> count(c.copies, "copy", "copies")
        c.binders > 0 -> count(c.binders, "binder", "binders")
        else -> ""
    }
    val parts = listOf(
        cards,
        if (c.wishlistCards > 0) count(c.wishlistCards, "wishlist card", "wishlist cards") else "",
        if (c.places > 0) count(c.places, "place", "places") else "",
        if (c.sealed > 0) "${number(c.sealed)} sealed" else "",
        if (c.graded > 0) "${number(c.graded)} graded" else "",
        if (c.gear > 0) count(c.gear, "piece of gear", "pieces of gear") else "",
        if (c.loans > 0) count(c.loans, "loan", "loans") else "",
        if (c.goals > 0) count(c.goals, "goal", "goals") else "",
        if (c.decks > 0) count(c.decks, "deck", "decks") else ""
    ).filter { it.isNotEmpty() }
    return if (parts.isEmpty()) RESET_NOTHING else parts.joinToString(" · ")
}

/** The library after a reset, and the ids of the decks and binders that went (samples aside: they never reach the account). */
data class ResetLibrary(
    val decks: List<Deck>,
    val collections: List<Collection>,
    val deletedDecks: Set<String>,
    val deletedCollections: Set<String>
)

/** An emptied Unsorted pile: its cards gone, and when [whole] what rides on it too — as empty lists, never left out (see Collection.loans). */
private fun emptiedPile(c: Collection, whole: Boolean): Collection =
    if (!whole) c.copy(entries = emptyList())
    else c.copy(
        entries = emptyList(),
        storagePlaces = c.storagePlaces?.let { emptyList() },
        loans = c.loans?.let { emptyList() },
        sealed = c.sealed?.let { emptyList() },
        graded = c.graded?.let { emptyList() },
        gear = c.gear?.let { emptyList() },
        collectionGoals = c.collectionGoals?.let { emptyList() }
    )

/** The library after a reset of [scope]: what goes listed, the rest emptied as the scope says. */
fun resetLibrary(decks: List<Deck>, collections: List<Collection>, scope: ResetScope): ResetLibrary {
    val whole = scope.whole
    val kept = collections.flatMap { c ->
        when {
            c.id == UNSORTED_COLLECTION_ID -> listOf(emptiedPile(c, whole))
            c.id == WISHLIST_ID -> listOf(if (whole) c.copy(entries = emptyList()) else c)
            whole -> emptyList()
            c.isWishlistType || c.entries.isEmpty() -> listOf(c)
            else -> listOf(c.copy(entries = emptyList()))
        }
    }
    val everything = scope == ResetScope.EVERYTHING
    return ResetLibrary(
        decks = if (everything) emptyList() else decks,
        collections = kept,
        deletedDecks = if (everything) decks.filterNot { isSample(it) }.mapTo(LinkedHashSet()) { it.id } else emptySet(),
        deletedCollections = if (whole) collections.filter { !it.isStanding && !isSample(it) }.mapTo(LinkedHashSet()) { it.id } else emptySet()
    )
}

/**
 * A reset waiting out its Undo: [previous] holds whatever puts the library back exactly as it was
 * (the stores' own saved text, on the phone). Undo and the commit each happen at most once, and never both.
 */
class PendingReset<T>(val scope: ResetScope, val previous: T, val until: Long) {
    private var settled = false

    /** What to put back, or null when the reset has already been committed or undone. */
    @Synchronized
    fun undo(): T? {
        if (settled) return null
        settled = true
        return previous
    }

    /** True the one time the reset is committed: the sync may send it now. False when undone or done. */
    @Synchronized
    fun commit(): Boolean {
        if (settled) return false
        settled = true
        return true
    }

    val open: Boolean
        @Synchronized get() = !settled
}
