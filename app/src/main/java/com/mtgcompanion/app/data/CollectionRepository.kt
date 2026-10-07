package com.mtgcompanion.app.data

import com.mtgcompanion.app.data.supabase.noteDeleted
import com.mtgcompanion.app.data.withWishlistCardWantedAgain
import com.mtgcompanion.app.data.withWantedCards
import com.mtgcompanion.app.data.withoutWishlistCard
import com.mtgcompanion.app.data.social.CollectionChange
import com.mtgcompanion.app.data.social.applyCollectionChanges

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.collectionDataStore by preferencesDataStore(name = "collection")

private const val LEGACY_COLLECTION_ID = "default"

class CollectionRepository(private val context: Context) {

    private val key = stringPreferencesKey("collection_json")
    private val adapter = localMoshi.adapter(CollectionStore::class.java)

    val collectionsFlow: Flow<List<Collection>> = context.collectionDataStore.data.map { readCollections(it) }

    fun collectionFlow(collectionId: String): Flow<Collection?> =
        collectionsFlow.map { collections -> collections.find { it.id == collectionId } }

    suspend fun createCollection(name: String, type: CollectionType = CollectionType.DEFAULT): Collection {
        val collection = Collection(id = UUID.randomUUID().toString(), name = name, type = type.name)
        update { it + collection }
        return collection
    }

    /** Deletes a binder — never the Wishlist or the Unsorted pile, which are always there. */
    /** The user's own tags on a copy they own — see DeckRepository.setUserTags. */
    suspend fun setUserTags(scryfallId: String, tags: List<String>) {
        val tidy = tidyUserTags(tags)
        update(tagging = scryfallId to tidy) { collections -> collections.map { it.withUserTags(scryfallId, tidy) } }
    }

    suspend fun deleteCollection(collectionId: String) {
        if (collectionId == WISHLIST_ID || collectionId == UNSORTED_COLLECTION_ID) return
        update(deleting = collectionId) { collections -> collections.filterNot { it.id == collectionId } }
    }

    /** Puts [cards] on the Wishlist (making it if needed), keeping the larger count of any already there. */
    suspend fun addWanted(cards: List<CollectionEntry>) {
        update { collections -> withWantedCards(collections, cards) }
    }

    /**
     * Takes [cardName] off the Wishlist and leaves it off while decks still consider it — "not
     * interested". Adding the card back by hand undoes it.
     */
    suspend fun notInterested(cardName: String) {
        update { collections -> withoutWishlistCard(collections, cardName) }
    }

    /** Undoes "not interested" for [cardName]: it comes back while a deck considers it. */
    suspend fun wantAgain(cardName: String) {
        update { collections -> withWishlistCardWantedAgain(collections, cardName) }
    }

    suspend fun addCard(collectionId: String, card: ScryfallCard, foil: Boolean = false) {
        updateEntries(collectionId) { entries ->
            val existing = entries.find { it.scryfallId == card.id }
            if (existing != null) {
                entries.map {
                    if (it.scryfallId != card.id) return@map it
                    if (foil) it.copy(foilQuantity = it.foilQuantity + 1) else it.copy(quantity = it.quantity + 1)
                }
            } else {
                entries + CollectionEntry(
                    scryfallId = card.id,
                    name = card.name,
                    imageUrl = card.displayImageUrl,
                    quantity = if (foil) 0 else 1,
                    foilQuantity = if (foil) 1 else 0,
                    backImageUrl = card.backImageUrl,
                    tags = card.tags
                )
            }
        }
    }

    /** The condition and language of a binder card's copies (see CopyDetails.kt); null clears one. */
    suspend fun setCopyDetails(collectionId: String, scryfallId: String, condition: String?, language: String?) {
        updateEntries(collectionId) { entries ->
            entries.map { if (it.scryfallId == scryfallId) it.copy(condition = condition, language = language) else it }
        }
    }

    /** An owned card's "rises above" alert (USD); null turns it off. */
    suspend fun setPriceAlertAbove(collectionId: String, scryfallId: String, usd: Double?) {
        updateEntries(collectionId) { entries -> entries.map { if (it.scryfallId == scryfallId) it.copy(priceAlertAbove = usd) else it } }
    }

    /** A wishlist card's price alert — its target (USD); null turns it off. [options]: which prices count (WishlistTargets.kt). */
    suspend fun setPriceAlert(collectionId: String, scryfallId: String, usd: Double?, options: TargetOptions? = null) {
        updateEntries(collectionId) { entries -> entries.map { if (it.scryfallId == scryfallId) withTarget(it, usd, options) else it } }
    }

    /** Several wishlist cards' targets in one change (scryfallId -> USD): "Set targets for all…". */
    suspend fun setPriceAlerts(collectionId: String, targets: Map<String, Double>, options: TargetOptions) {
        if (targets.isEmpty()) return
        updateEntries(collectionId) { entries -> entries.map { e -> targets[e.scryfallId]?.let { withTarget(e, it, options) } ?: e } }
    }

    suspend fun setQuantity(collectionId: String, scryfallId: String, quantity: Int, foilQuantity: Int) {
        updateEntries(collectionId) { entries ->
            entries.mapNotNull {
                if (it.scryfallId != scryfallId) return@mapNotNull it
                val updated = it.copy(quantity = quantity.coerceAtLeast(0), foilQuantity = foilQuantity.coerceAtLeast(0))
                updated.takeIf { u -> u.quantity > 0 || u.foilQuantity > 0 }
            }
        }
    }

    /** Swap an entry to a different printing (art), keeping its quantities. */
    suspend fun changeEntryPrinting(collectionId: String, oldScryfallId: String, newCard: ScryfallCard) {
        // Merges into the new printing if the binder already holds it (see withEntryPrinting).
        updateEntries(collectionId) { entries -> withEntryPrinting(entries, oldScryfallId, newCard) }
    }

    suspend fun removeEntry(collectionId: String, scryfallId: String) {
        updateEntries(collectionId) { entries -> entries.filterNot { it.scryfallId == scryfallId } }
    }

    /** Adds a whole imported list to a binder in one change, copies of cards already there added on. */
    suspend fun addEntries(collectionId: String, added: List<CollectionEntry>) {
        updateEntries(collectionId) { entries -> entries.mergeIn(added) }
    }

    /**
     * Moves the cards [ids] from one binder into another in one change — or with [keep], copies
     * them, leaving them where they were too.
     */
    suspend fun transferEntries(fromId: String, ids: Set<String>, toId: String, keep: Boolean) {
        update { collections ->
            val moving = collections.firstOrNull { it.id == fromId }?.entries.orEmpty().filter { it.scryfallId in ids }
                // A copy made in the app has no place of its own (StoragePlaces.kt); moved, its places go with it.
                .map { if (keep && it.places != null) it.copy(places = null) else it }
            collections.map { c ->
                when {
                    c.id == toId -> c.copy(entries = c.entries.mergeIn(moving))
                    c.id == fromId && !keep -> c.copy(entries = c.entries.filterNot { it.scryfallId in ids })
                    else -> c
                }
            }
        }
    }

    /**
     * Gathers every copy of the cards [ids] from the user's other binders (the Unsorted pile too;
     * wishlists keep theirs) into the binder [toId], in one change.
     */
    suspend fun gatherInto(toId: String, ids: Set<String>) {
        update { collections ->
            val sources = collections.filter { it.id != toId && it.kind == CollectionType.OWNED }
            val moving = sources.flatMap { c -> c.entries.filter { it.scryfallId in ids } }
            collections.map { c ->
                when {
                    c.id == toId -> c.copy(entries = c.entries.mergeIn(moving))
                    c in sources -> c.copy(entries = c.entries.filterNot { it.scryfallId in ids })
                    else -> c
                }
            }
        }
    }

    /**
     * Changes the storage places or where copies are kept (StoragePlaces.kt): [change] gets the
     * binders as they are now and answers them changed, in one change.
     */
    suspend fun changeStorage(change: (List<Collection>) -> List<Collection>) {
        update(transform = change)
    }

    /** Sets where [scryfallId]'s copies in one binder are kept (tidied to its copies). */
    suspend fun setPlaces(collectionId: String, scryfallId: String, places: List<CopyPlace>) {
        updateEntries(collectionId) { entries -> entries.map { if (it.scryfallId == scryfallId) withPlaces(it, places) else it } }
    }

    /** Removes the cards [ids] from one binder, in one change. */
    suspend fun removeEntries(collectionId: String, ids: Set<String>) {
        updateEntries(collectionId) { entries -> entries.filterNot { it.scryfallId in ids } }
    }

    /** Removes the cards [ids] from every binder (the Unsorted pile too); wishlists keep theirs. */
    suspend fun removeEverywhere(ids: Set<String>) {
        update { collections ->
            collections.map { c -> if (c.kind == CollectionType.OWNED) c.copy(entries = c.entries.filterNot { it.scryfallId in ids }) else c }
        }
    }

    /** These entries with [added] merged in: copies of a card already here are added on. */
    private fun List<CollectionEntry>.mergeIn(added: List<CollectionEntry>): List<CollectionEntry> {
        // By printing, so importing thousands of cards doesn't search the binder once per card.
        val out = toMutableList()
        val at = HashMap<String, Int>()
        out.forEachIndexed { i, e -> at.putIfAbsent(e.scryfallId, i) }
        for (entry in added) {
            val i = at[entry.scryfallId]
            if (i == null) {
                at[entry.scryfallId] = out.size
                out += entry
            } else {
                out[i] = out[i].withCopiesOf(entry)
            }
        }
        return out
    }

    /** Adds cards to the Unsorted pile (owned, not in a binder yet), making the pile if there isn't one. */
    suspend fun addUnsorted(added: List<CollectionEntry>) {
        update { collections -> withUnsortedPile(collections) }
        addEntries(UNSORTED_COLLECTION_ID, added)
    }

    /** Empties a binder but keeps it — for the Unsorted pile, which is emptied rather than deleted. */
    suspend fun clearEntries(collectionId: String) {
        updateEntries(collectionId) { emptyList() }
    }

    /** Add a card entry (with its quantities) to a binder, merging into an existing copy. For moves. */
    suspend fun addEntry(collectionId: String, entry: CollectionEntry) {
        updateEntries(collectionId) { entries ->
            val existing = entries.find { it.scryfallId == entry.scryfallId }
            if (existing != null) {
                entries.map {
                    if (it.scryfallId != entry.scryfallId) it else it.withCopiesOf(entry)
                }
            } else {
                entries + entry
            }
        }
    }

    /**
     * Moves cards in and out of binders in one change — the user's side of a trade. Answers the
     * cards there weren't enough copies of to take out (see applyCollectionChanges).
     */
    suspend fun applyTrade(changes: List<CollectionChange>): List<CollectionChange> {
        var short = emptyList<CollectionChange>()
        update { current -> applyCollectionChanges(current, changes).also { short = it.short }.collections }
        return short
    }

    /** Overwrite all collections — used when restoring/pulling from Drive sync. */
    /**
     * Keeps the collections that are always there as they should be: the Wishlist for [decks]
     * (see [withWishlist]) and the Unsorted pile (see [withUnsortedPile]) — written only when
     * something changes.
     */
    suspend fun maintainStandingCollections(decks: List<Deck>) {
        context.collectionDataStore.edit { prefs ->
            val current = readCollections(prefs)
            val next = withStandingCollections(current, decks)
            if (next !== current) prefs[key] = adapter.toJson(CollectionStore(collections = next))
        }
    }

    /**
     * A restored backup's binders (Backup.kt): [transform] gets the binders as they are and answers them
     * restored. Ones that come back are no longer counted as deleted, so the sync sends them again.
     */
    suspend fun restoreBackup(restored: Set<String>, transform: (List<Collection>) -> List<Collection>) {
        update(undeleting = restored, transform = transform)
    }

    /** Writes what a sync pulled, as a change to the binders as they are at that moment. */
    suspend fun applySync(transform: (List<Collection>) -> List<Collection>) {
        update(transform = transform)
    }

    /**
     * The stored JSON read once per change, not once for each screen and flow watching it: a big
     * collection is megabytes of JSON, and a dozen collectors each parsing every change was most of the
     * time a change took. Everyone gets the same (immutable) objects.
     */
    @Volatile private var parsed: Pair<String, CollectionStore?>? = null

    private fun storeOf(json: String?): CollectionStore? {
        if (json == null) return null
        parsed?.let { (was, store) -> if (was === json || was == json) return store }
        val store = runCatching { adapter.fromJson(json) }.getOrNull()
        parsed = json to store
        return store
    }

    private fun readCollections(prefs: Preferences): List<Collection> {
        val store = storeOf(prefs[key]) ?: return emptyList()
        if (store.collections.isNotEmpty()) return store.collections
        // Migrate a legacy single-collection store into one default collection.
        val legacy = store.entries.orEmpty()
        return if (legacy.isEmpty()) emptyList()
        else listOf(Collection(id = LEGACY_COLLECTION_ID, name = "My Collection", entries = legacy))
    }

    /**
     * [count] copies of a card ([scryfallId], [name]) have gone into [deck]: if it holds the user's
     * own copies, they're the loose ones, and come out of the Unsorted pile (see takenFromUnsorted).
     * Answers how many did. The scanner doesn't call this — its cards are new copies in hand — nor do
     * moves and copies out of a binder, which say for themselves where the card is.
     */
    suspend fun takeIntoDeck(deck: Deck?, scryfallId: String, name: String, count: Int = 1): Int {
        if (deck == null || !deck.holdsOwnCopies || count <= 0) return 0
        var taken = 0
        updateEntries(UNSORTED_COLLECTION_ID) { entries ->
            val (left, n) = takenFromUnsorted(entries, scryfallId, name, count)
            taken = n
            left
        }
        return taken
    }

    /**
     * [entry]'s count in [deck] went down to [newQuantity]: its real copies that left go back to the
     * Unsorted pile (see realCopiesLeaving). Answers how many did.
     */
    suspend fun returnFromDeck(deck: Deck?, entry: DeckCardEntry, newQuantity: Int = 0): Int {
        if (deck == null) return 0
        val n = realCopiesLeaving(deck, entry, newQuantity)
        if (n > 0) addUnsorted(listOf(pileEntryOf(entry, n)))
        return n
    }

    private suspend fun updateEntries(collectionId: String, transform: (List<CollectionEntry>) -> List<CollectionEntry>) {
        update { collections ->
            collections.map { if (it.id == collectionId) it.copy(entries = transform(it.entries)) else it }
        }
    }

    /** Which binders the user has deleted here — see DeckRepository.deletedFlow. */
    val deletedFlow: Flow<Map<String, Long>> = context.collectionDataStore.data.map { prefs ->
        storeOf(prefs[key])?.deleted ?: emptyMap()
    }

    private suspend fun update(
        deleting: String? = null,
        undeleting: Set<String> = emptySet(),
        tagging: Pair<String, List<String>>? = null,
        transform: (List<Collection>) -> List<Collection>
    ) {
        context.collectionDataStore.edit { prefs ->
            val current = readCollections(prefs)
            val store = storeOf(prefs[key])
            val next = transform(current)
            // See DeckRepository.update: one place where a copy gets back the tags it already had.
            val was = store?.userTags.orEmpty().let { if (tagging == null) it else it.ledgerWith(tagging.first, tagging.second) }
            val ledger = rememberedUserTags(was, collections = next)
            prefs[key] = adapter.toJson(
                CollectionStore(
                    collections = next.withRememberedUserTagsIn(ledger),
                    deleted = noteDeleted(store?.deleted, deleting) - undeleting,
                    userTags = ledger
                )
            )
        }
    }
}
