package com.mtgcompanion.app.data

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

    suspend fun deleteCollection(collectionId: String) {
        update { collections -> collections.filterNot { it.id == collectionId } }
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
        updateEntries(collectionId) { entries ->
            entries.map {
                if (it.scryfallId != oldScryfallId) it
                else it.copy(scryfallId = newCard.id, name = newCard.name, imageUrl = newCard.displayImageUrl, backImageUrl = newCard.backImageUrl, tags = newCard.tags)
            }
        }
    }

    suspend fun removeEntry(collectionId: String, scryfallId: String) {
        updateEntries(collectionId) { entries -> entries.filterNot { it.scryfallId == scryfallId } }
    }

    /** Add a card entry (with its quantities) to a binder, merging into an existing copy. For moves. */
    /** Adds a whole imported list to a binder in one change, copies of cards already there added on. */
    suspend fun addEntries(collectionId: String, added: List<CollectionEntry>) {
        updateEntries(collectionId) { entries ->
            added.fold(entries) { list, entry ->
                if (list.any { it.scryfallId == entry.scryfallId }) {
                    list.map { if (it.scryfallId != entry.scryfallId) it else it.copy(quantity = it.quantity + entry.quantity, foilQuantity = it.foilQuantity + entry.foilQuantity) }
                } else {
                    list + entry
                }
            }
        }
    }

    /** Adds cards to the Unsorted pile (owned, not in a binder yet), making the pile if there isn't one. */
    suspend fun addUnsorted(added: List<CollectionEntry>) {
        update { collections ->
            if (collections.any { it.isUnsorted }) collections
            else collections + Collection(UNSORTED_COLLECTION_ID, UNSORTED_COLLECTION_NAME, type = CollectionType.OWNED.name)
        }
        addEntries(UNSORTED_COLLECTION_ID, added)
    }

    /** Empties a binder but keeps it — for the Unsorted pile, which is emptied rather than deleted. */
    suspend fun clearEntries(collectionId: String) {
        updateEntries(collectionId) { emptyList() }
    }

    suspend fun addEntry(collectionId: String, entry: CollectionEntry) {
        updateEntries(collectionId) { entries ->
            val existing = entries.find { it.scryfallId == entry.scryfallId }
            if (existing != null) {
                entries.map {
                    if (it.scryfallId != entry.scryfallId) it
                    else it.copy(
                        quantity = it.quantity + entry.quantity,
                        foilQuantity = it.foilQuantity + entry.foilQuantity
                    )
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
    /** Writes what a sync pulled, as a change to the binders as they are at that moment. */
    suspend fun applySync(transform: (List<Collection>) -> List<Collection>) {
        update(transform)
    }

    private fun readCollections(prefs: Preferences): List<Collection> {
        val store = prefs[key]?.let { runCatching { adapter.fromJson(it) }.getOrNull() } ?: return emptyList()
        if (store.collections.isNotEmpty()) return store.collections
        // Migrate a legacy single-collection store into one default collection.
        val legacy = store.entries.orEmpty()
        return if (legacy.isEmpty()) emptyList()
        else listOf(Collection(id = LEGACY_COLLECTION_ID, name = "My Collection", entries = legacy))
    }

    private suspend fun updateEntries(collectionId: String, transform: (List<CollectionEntry>) -> List<CollectionEntry>) {
        update { collections ->
            collections.map { if (it.id == collectionId) it.copy(entries = transform(it.entries)) else it }
        }
    }

    private suspend fun update(transform: (List<Collection>) -> List<Collection>) {
        context.collectionDataStore.edit { prefs ->
            val current = readCollections(prefs)
            prefs[key] = adapter.toJson(CollectionStore(collections = transform(current)))
        }
    }
}
