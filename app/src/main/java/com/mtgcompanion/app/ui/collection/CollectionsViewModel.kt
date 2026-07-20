package com.mtgcompanion.app.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.CardViewMode
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionRepository
import com.mtgcompanion.app.data.DeckRepository
import com.mtgcompanion.app.data.GridSize
import com.mtgcompanion.app.data.SettingsRepository
import com.mtgcompanion.app.ui.common.CardSource
import com.mtgcompanion.app.ui.common.SourceKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One card aggregated across every collection and deck, with the total copies and where they are. */
data class AllCardEntry(
    val scryfallId: String,
    val name: String,
    val imageUrl: String?,
    val total: Int,
    val sources: List<CardSource> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionsViewModel(
    private val repository: CollectionRepository,
    deckRepository: DeckRepository,
    private val settingsRepository: SettingsRepository,
    private val cardRepository: CardRepository = CardRepository()
) : ViewModel() {

    /** List or grid for the All Cards tab, and the shared grid tile size, from Settings > Card Display. */
    val viewMode: StateFlow<CardViewMode> = settingsRepository.allCardsViewMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CardViewMode.DEFAULT)
    val gridSize: StateFlow<GridSize> = settingsRepository.gridSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GridSize.DEFAULT)

    val collections: StateFlow<List<Collection>> = repository.collectionsFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    /** Every card owned anywhere (all collections + all decks), deduped by card and summed. */
    val allCards: StateFlow<List<AllCardEntry>> =
        combine(repository.collectionsFlow, deckRepository.decksFlow) { collections, decks ->
            // Accumulate total copies plus the list of binders/decks holding each card.
            class Acc(val name: String, val imageUrl: String?) {
                var total = 0
                val sources = mutableListOf<CardSource>()
            }
            val byCard = LinkedHashMap<String, Acc>()
            fun add(id: String, name: String, imageUrl: String?, qty: Int, source: CardSource) {
                if (qty <= 0) return
                val acc = byCard.getOrPut(id) { Acc(name, imageUrl) }
                acc.total += qty
                acc.sources += source
            }
            collections.forEach { collection ->
                collection.entries.forEach {
                    val qty = it.quantity + it.foilQuantity
                    add(it.scryfallId, it.name, it.imageUrl, qty, CardSource(SourceKind.BINDER, collection.name, qty))
                }
            }
            decks.forEach { deck ->
                deck.cards.forEach {
                    add(it.scryfallId, it.name, it.imageUrl, it.quantity, CardSource(SourceKind.DECK, deck.name, it.quantity))
                }
            }
            byCard.map { (id, acc) -> AllCardEntry(id, acc.name, acc.imageUrl, acc.total, acc.sources.toList()) }
                .sortedBy { it.name.lowercase() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Dashboard totals for the All Cards tab. Recomputes whenever [allCards] changes by fetching
     * full card data (price/colour/type) from Scryfall in bulk. Null while empty or still loading.
     */
    val dashboard: StateFlow<CollectionDashboard?> = allCards.mapLatest { entries ->
        computeDashboard(cardRepository, entries.map { it.scryfallId to it.total })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** scryfallId -> USD price across all owned cards, for the enlarged-card value/total display. */
    val prices: StateFlow<Map<String, Double>> = allCards.mapLatest { entries ->
        fetchPrices(cardRepository, entries.map { it.scryfallId })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun createCollection(name: String, onCreated: (Collection) -> Unit) {
        viewModelScope.launch { onCreated(repository.createCollection(name)) }
    }

    fun deleteCollection(collectionId: String) {
        viewModelScope.launch { repository.deleteCollection(collectionId) }
    }

    class Factory(
        private val repository: CollectionRepository,
        private val deckRepository: DeckRepository,
        private val settingsRepository: SettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CollectionsViewModel(repository, deckRepository, settingsRepository) as T
    }
}
