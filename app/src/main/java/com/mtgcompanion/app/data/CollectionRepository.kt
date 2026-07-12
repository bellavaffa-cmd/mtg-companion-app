package com.mtgcompanion.app.data

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

    suspend fun createCollection(name: String): Collection {
        val collection = Collection(id = UUID.randomUUID().toString(), name = name)
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
                    foilQuantity = if (foil) 1 else 0
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
                else it.copy(scryfallId = newCard.id, name = newCard.name, imageUrl = newCard.displayImageUrl)
            }
        }
    }

    suspend fun removeEntry(collectionId: String, scryfallId: String) {
        updateEntries(collectionId) { entries -> entries.filterNot { it.scryfallId == scryfallId } }
    }

    /** Overwrite all collections — used when restoring/pulling from Drive sync. */
    suspend fun replaceAll(collections: List<Collection>) {
        update { collections }
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
