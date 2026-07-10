package com.mtgcompanion.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.collectionDataStore by preferencesDataStore(name = "collection")

class CollectionRepository(private val context: Context) {

    private val key = stringPreferencesKey("collection_json")
    private val adapter = localMoshi.adapter(CollectionStore::class.java)

    val collectionFlow: Flow<List<CollectionEntry>> = context.collectionDataStore.data.map { prefs ->
        prefs[key]?.let { json -> runCatching { adapter.fromJson(json)?.entries }.getOrNull() } ?: emptyList()
    }

    suspend fun addCard(card: ScryfallCard, foil: Boolean = false) {
        update { entries ->
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

    suspend fun setQuantity(scryfallId: String, quantity: Int, foilQuantity: Int) {
        update { entries ->
            entries.mapNotNull {
                if (it.scryfallId != scryfallId) return@mapNotNull it
                val updated = it.copy(quantity = quantity.coerceAtLeast(0), foilQuantity = foilQuantity.coerceAtLeast(0))
                updated.takeIf { u -> u.quantity > 0 || u.foilQuantity > 0 }
            }
        }
    }

    suspend fun removeEntry(scryfallId: String) {
        update { entries -> entries.filterNot { it.scryfallId == scryfallId } }
    }

    private suspend fun update(transform: (List<CollectionEntry>) -> List<CollectionEntry>) {
        context.collectionDataStore.edit { prefs ->
            val current = prefs[key]?.let { runCatching { adapter.fromJson(it)?.entries }.getOrNull() } ?: emptyList()
            prefs[key] = adapter.toJson(CollectionStore(transform(current)))
        }
    }
}
