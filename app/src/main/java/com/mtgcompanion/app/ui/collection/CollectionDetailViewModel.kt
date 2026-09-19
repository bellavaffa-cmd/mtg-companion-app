package com.mtgcompanion.app.ui.collection

import com.mtgcompanion.app.data.CardListImporter
import com.mtgcompanion.app.data.buildCardListText
import com.mtgcompanion.app.data.parseCardList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.CardViewMode
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionEntry
import com.mtgcompanion.app.data.CollectionRepository
import com.mtgcompanion.app.data.DeckCardEntry
import com.mtgcompanion.app.data.DeckRepository
import com.mtgcompanion.app.data.GRID_COLUMNS_DEFAULT
import com.mtgcompanion.app.data.SettingsRepository
import com.mtgcompanion.app.ui.common.CardSource
import com.mtgcompanion.app.data.CollectionType
import com.mtgcompanion.app.ui.common.MoveTarget
import com.mtgcompanion.app.ui.common.SourceKind
import com.mtgcompanion.app.ui.common.buildCardSources
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import com.mtgcompanion.app.data.RoleTags
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionDetailViewModel(
    private val collectionId: String,
    private val repository: CollectionRepository,
    private val deckRepository: DeckRepository,
    private val settingsRepository: SettingsRepository,
    private val cardRepository: CardRepository = CardRepository()
) : ViewModel() {

    /** List or grid, as set in Settings > Card Display. */
    val viewMode: StateFlow<CardViewMode> = settingsRepository.collectionViewMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CardViewMode.DEFAULT)

    /** Grid column count, when [viewMode] is Grid. */
    val gridColumns: StateFlow<Int> = settingsRepository.gridColumns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GRID_COLUMNS_DEFAULT)

    /** Decks and other binders this binder's cards can be moved into. */
    val moveTargets: StateFlow<List<MoveTarget>> =
        combine(deckRepository.decksFlow, repository.collectionsFlow) { decks, collections ->
            decks.map { MoveTarget(SourceKind.DECK, it.id, it.name) } +
                collections.filter { it.id != collectionId }.map { MoveTarget(SourceKind.BINDER, it.id, it.name) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val collection: StateFlow<Collection?> = repository.collectionFlow(collectionId).stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null
    )

    /** scryfallId -> every other binder/deck holding that card, for the zoom overlay's "also in" list. */
    val cardSources: StateFlow<Map<String, List<CardSource>>> =
        combine(repository.collectionsFlow, deckRepository.decksFlow) { collections, decks ->
            buildCardSources(collections, decks)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

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

    /** The collection's cards, filtered by the search: a card's name or one of its tags. */
    val entries: StateFlow<List<CollectionEntry>> = combine(collection, _query, RoleTags.version) { coll, q, _ ->
        val all = coll?.entries.orEmpty()
        if (q.isBlank()) all else all.filter { RoleTags.matches(it.name, RoleTags.tagsOf(it.name).orEmpty(), q) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Card name -> what it does (RoleTags ids), for the zoom and the search. */
    val cardTags: StateFlow<Map<String, List<String>>> = combine(collection, RoleTags.version) { c, _ ->
        c?.entries.orEmpty().associate { it.name to RoleTags.tagsOf(it.name).orEmpty() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Tags still being looked up, (done, total). */
    val tagging: StateFlow<Pair<Int, Int>?> = RoleTags.progress

    init {
        viewModelScope.launch {
            collection.map { c -> c?.entries.orEmpty().map { it.name } }.distinctUntilChanged().collectLatest { names ->
                if (names.isNotEmpty()) RoleTags.ensure(names, cardRepository)
            }
        }
    }

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    /** A wishlist card's price alert (USD); null turns it off. */
    fun setPriceAlert(entry: CollectionEntry, usd: Double?) {
        viewModelScope.launch { repository.setPriceAlert(collectionId, entry.scryfallId, usd) }
    }

    private val _importProgress = MutableStateFlow<ImportProgress>(ImportProgress.Idle)
    /** Where an import from another app is up to. */
    val importProgress: StateFlow<ImportProgress> = _importProgress.asStateFlow()

    /** Adds a pasted or loaded card list (text or CSV) to this binder. */
    fun importCards(text: String) {
        val lines = parseCardList(text).lines
        if (lines.isEmpty()) return
        viewModelScope.launch {
            _importProgress.value = ImportProgress.Working(0, lines.size)
            _importProgress.value = try {
                val result = CardListImporter(cardRepository).resolve(lines) { done, total -> _importProgress.value = ImportProgress.Working(done, total) }
                repository.addEntries(collectionId, result.cards.map { it.toEntry() })
                ImportProgress.Done(result, collection.value?.name ?: "this binder")
            } catch (e: java.io.IOException) {
                ImportProgress.Failed("You're offline — try again when you're connected.")
            } catch (e: Exception) {
                ImportProgress.Failed(e.message ?: "Something went wrong.")
            }
        }
    }

    fun resetImport() { _importProgress.value = ImportProgress.Idle }

    /**
     * This binder — or just the cards [ids] — as text for other apps; [exact] names each card's
     * printing ("(CMR) 472").
     */
    suspend fun exportText(exact: Boolean, ids: Set<String>? = null): String {
        val entries = collection.value?.entries.orEmpty().filter { ids == null || it.scryfallId in ids }
        if (!exact) return buildCardListText(entries)
        val printings = cardRepository.getCardsByIds(entries.map { it.scryfallId })
            .mapNotNull { c -> if (c.set != null && c.collectorNumber != null) c.id to (c.set to c.collectorNumber) else null }
            .toMap()
        return buildCardListText(entries, printings)
    }

    fun setQuantity(entry: CollectionEntry, quantity: Int, foilQuantity: Int) {
        viewModelScope.launch { repository.setQuantity(collectionId, entry.scryfallId, quantity, foilQuantity) }
    }

    fun remove(entry: CollectionEntry) {
        viewModelScope.launch { repository.removeEntry(collectionId, entry.scryfallId) }
    }

    /** Add a card not yet in this binder — e.g. one picked from the zoom overlay's "find similar" list. */
    fun addCard(card: com.mtgcompanion.app.network.scryfall.ScryfallCard) {
        viewModelScope.launch { repository.addCard(collectionId, card) }
    }

    /** Move a card (all its copies) out of this binder into [target] deck or binder. */
    fun moveEntry(entry: CollectionEntry, target: MoveTarget) {
        viewModelScope.launch {
            addCopyTo(entry, target)
            repository.removeEntry(collectionId, entry.scryfallId)
        }
    }

    /** Moves the picked cards [ids] into [target] deck or binder — or with [keepHere], copies them. */
    fun moveEntries(ids: Set<String>, target: MoveTarget, keepHere: Boolean) {
        viewModelScope.launch {
            when (target.kind) {
                SourceKind.BINDER -> repository.transferEntries(collectionId, ids, target.id, keepHere)
                SourceKind.DECK -> {
                    collection.value?.entries.orEmpty().filter { it.scryfallId in ids }.forEach { addCopyTo(it, target) }
                    if (!keepHere) repository.removeEntries(collectionId, ids)
                }
            }
        }
    }

    /** Makes a binder named [name] and moves the picked cards [ids] into it — or with [keepHere], copies them. */
    fun moveEntriesToNewBinder(ids: Set<String>, name: String, keepHere: Boolean) {
        viewModelScope.launch {
            val binder = repository.createCollection(name.trim().ifBlank { "New binder" }, CollectionType.OWNED)
            repository.transferEntries(collectionId, ids, binder.id, keepHere)
        }
    }

    /** Removes the picked cards [ids] from this binder. */
    fun removeEntries(ids: Set<String>) {
        viewModelScope.launch { repository.removeEntries(collectionId, ids) }
    }

    /** Makes a binder named [name] and moves the card into it — or with [keepHere], copies it. */
    fun moveToNewBinder(entry: CollectionEntry, name: String, keepHere: Boolean) {
        viewModelScope.launch {
            val binder = repository.createCollection(name.trim().ifBlank { "New binder" }, CollectionType.OWNED)
            addCopyTo(entry, MoveTarget(SourceKind.BINDER, binder.id, binder.name))
            if (!keepHere) repository.removeEntry(collectionId, entry.scryfallId)
        }
    }

    /** Removes every card but keeps the binder (the Unsorted pile is emptied, not deleted). */
    fun clearAll(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.clearEntries(collectionId)
            onDone()
        }
    }

    private suspend fun addCopyTo(entry: CollectionEntry, target: MoveTarget) {
        when (target.kind) {
            SourceKind.DECK -> deckRepository.addEntry(
                target.id,
                DeckCardEntry(entry.scryfallId, entry.name, entry.imageUrl, quantity = entry.quantity + entry.foilQuantity, backImageUrl = entry.backImageUrl, tags = entry.tags)
            )
            SourceKind.BINDER -> repository.addEntry(target.id, entry)
        }
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
        private val repository: CollectionRepository,
        private val deckRepository: DeckRepository,
        private val settingsRepository: SettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CollectionDetailViewModel(collectionId, repository, deckRepository, settingsRepository) as T
    }
}
