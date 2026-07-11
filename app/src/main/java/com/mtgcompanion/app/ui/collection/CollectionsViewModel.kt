package com.mtgcompanion.app.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionRepository
import com.mtgcompanion.app.data.DeckRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One card aggregated across every collection and deck, with the total number of copies. */
data class AllCardEntry(
    val scryfallId: String,
    val name: String,
    val imageUrl: String?,
    val total: Int
)

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionsViewModel(
    private val repository: CollectionRepository,
    deckRepository: DeckRepository,
    private val cardRepository: CardRepository = CardRepository()
) : ViewModel() {

    val collections: StateFlow<List<Collection>> = repository.collectionsFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    /** Every card owned anywhere (all collections + all decks), deduped by card and summed. */
    val allCards: StateFlow<List<AllCardEntry>> =
        combine(repository.collectionsFlow, deckRepository.decksFlow) { collections, decks ->
            val byCard = LinkedHashMap<String, AllCardEntry>()
            fun add(id: String, name: String, imageUrl: String?, qty: Int) {
                val current = byCard[id]
                byCard[id] = AllCardEntry(id, name, imageUrl, (current?.total ?: 0) + qty)
            }
            collections.forEach { collection ->
                collection.entries.forEach { add(it.scryfallId, it.name, it.imageUrl, it.quantity + it.foilQuantity) }
            }
            decks.forEach { deck ->
                deck.cards.forEach { add(it.scryfallId, it.name, it.imageUrl, it.quantity) }
            }
            byCard.values.sortedBy { it.name.lowercase() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Dashboard totals for the All Cards tab. Recomputes whenever [allCards] changes by fetching
     * full card data (price/colour/type) from Scryfall in bulk. Null while empty or still loading.
     */
    val dashboard: StateFlow<CollectionDashboard?> = allCards.mapLatest { entries ->
        computeDashboard(cardRepository, entries.map { it.scryfallId to it.total })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun createCollection(name: String, onCreated: (Collection) -> Unit) {
        viewModelScope.launch { onCreated(repository.createCollection(name)) }
    }

    fun deleteCollection(collectionId: String) {
        viewModelScope.launch { repository.deleteCollection(collectionId) }
    }

    class Factory(
        private val repository: CollectionRepository,
        private val deckRepository: DeckRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CollectionsViewModel(repository, deckRepository) as T
    }
}
