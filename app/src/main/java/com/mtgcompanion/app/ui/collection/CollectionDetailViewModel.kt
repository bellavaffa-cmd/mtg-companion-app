package com.mtgcompanion.app.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionEntry
import com.mtgcompanion.app.data.CollectionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionDetailViewModel(
    private val collectionId: String,
    private val repository: CollectionRepository,
    private val cardRepository: CardRepository = CardRepository()
) : ViewModel() {

    val collection: StateFlow<Collection?> = repository.collectionFlow(collectionId).stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null
    )

    /** Dashboard totals for this binder's cards (unaffected by the search query). */
    val dashboard: StateFlow<CollectionDashboard?> = collection.mapLatest { c ->
        computeDashboard(cardRepository, c?.entries.orEmpty().map { it.scryfallId to (it.quantity + it.foilQuantity) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** scryfallId -> USD price for this binder's cards, for the enlarged-card value/total display. */
    val prices: StateFlow<Map<String, Double>> = collection.mapLatest { c ->
        fetchPrices(cardRepository, c?.entries.orEmpty().map { it.scryfallId })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** The collection's cards, filtered by the search query (case-insensitive name match). */
    val entries: StateFlow<List<CollectionEntry>> = combine(collection, _query) { coll, q ->
        val all = coll?.entries.orEmpty()
        if (q.isBlank()) all else all.filter { it.name.contains(q.trim(), ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    fun setQuantity(entry: CollectionEntry, quantity: Int, foilQuantity: Int) {
        viewModelScope.launch { repository.setQuantity(collectionId, entry.scryfallId, quantity, foilQuantity) }
    }

    fun remove(entry: CollectionEntry) {
        viewModelScope.launch { repository.removeEntry(collectionId, entry.scryfallId) }
    }

    /** Swap an entry to a different printing/art, keeping its quantities. */
    fun changePrinting(oldScryfallId: String, newCard: com.mtgcompanion.app.network.scryfall.ScryfallCard) {
        viewModelScope.launch { repository.changeEntryPrinting(collectionId, oldScryfallId, newCard) }
    }

    fun deleteCollection(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteCollection(collectionId)
            onDeleted()
        }
    }

    class Factory(
        private val collectionId: String,
        private val repository: CollectionRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CollectionDetailViewModel(collectionId, repository) as T
    }
}
