package com.mtgcompanion.app.ui.collection

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.mtgcompanion.app.data.CardListImporter
import com.mtgcompanion.app.data.CollectionEntry
import com.mtgcompanion.app.data.DeckCardEntry
import com.mtgcompanion.app.data.buildCardListText
import com.mtgcompanion.app.ui.common.MoveTarget
import com.mtgcompanion.app.data.parseCardList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.CardViewMode
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionRepository
import com.mtgcompanion.app.data.CollectionType
import com.mtgcompanion.app.data.DeckRepository
import com.mtgcompanion.app.data.GRID_COLUMNS_DEFAULT
import com.mtgcompanion.app.data.SettingsRepository
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.ui.common.CardSource
import com.mtgcompanion.app.ui.common.SourceKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One card aggregated across every collection and deck, with the total copies and where they are. */
data class AllCardEntry(
    val scryfallId: String,
    val name: String,
    val imageUrl: String?,
    val total: Int,
    val sources: List<CardSource> = emptyList(),
    val backImageUrl: String? = null,
    val tags: List<String> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionsViewModel(
    private val repository: CollectionRepository,
    private val deckRepository: DeckRepository,
    private val settingsRepository: SettingsRepository,
    private val cardRepository: CardRepository = CardRepository()
) : ViewModel() {

    /** List or grid for the All Cards tab, and the shared grid column count, from Settings > Card Display. */
    val viewMode: StateFlow<CardViewMode> = settingsRepository.allCardsViewMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CardViewMode.DEFAULT)
    val gridColumns: StateFlow<Int> = settingsRepository.gridColumns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GRID_COLUMNS_DEFAULT)

    val collections: StateFlow<List<Collection>> = repository.collectionsFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    private val decks: StateFlow<List<com.mtgcompanion.app.data.Deck>> = deckRepository.decksFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    /** Every card owned anywhere (all collections + all decks), deduped by card and summed. */
    val allCards: StateFlow<List<AllCardEntry>> =
        combine(repository.collectionsFlow, deckRepository.decksFlow) { collections, decks ->
            // Accumulate total copies plus the list of binders/decks holding each card.
            class Acc(val name: String, val imageUrl: String?, val backImageUrl: String?, val tags: List<String>) {
                var total = 0
                val sources = mutableListOf<CardSource>()
            }
            val byCard = LinkedHashMap<String, Acc>()
            fun add(id: String, name: String, imageUrl: String?, backImageUrl: String?, tags: List<String>, qty: Int, source: CardSource) {
                if (qty <= 0) return
                val acc = byCard.getOrPut(id) { Acc(name, imageUrl, backImageUrl, tags) }
                acc.total += qty
                acc.sources += source
            }
            // Wishlist binders track cards not yet owned, so they don't count toward "owned" totals.
            collections.filter { it.kind == CollectionType.OWNED }.forEach { collection ->
                collection.entries.forEach {
                    val qty = it.quantity + it.foilQuantity
                    add(it.scryfallId, it.name, it.imageUrl, it.backImageUrl, it.tags, qty, CardSource(SourceKind.BINDER, collection.id, collection.name, qty))
                }
            }
            decks.forEach { deck ->
                deck.cards.forEach {
                    add(it.scryfallId, it.name, it.imageUrl, it.backImageUrl, it.tags, it.quantity, CardSource(SourceKind.DECK, deck.id, deck.name, it.quantity))
                }
            }
            byCard.map { (id, acc) -> AllCardEntry(id, acc.name, acc.imageUrl, acc.total, acc.sources.toList(), acc.backImageUrl, acc.tags) }
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

    /**
     * An All Cards entry is one exact printing shared by every binder/deck listed in its
     * [AllCardEntry.sources], so re-arting it means updating that printing everywhere it's held,
     * not just one place.
     */
    fun changePrintingEverywhere(oldScryfallId: String, newCard: ScryfallCard) {
        viewModelScope.launch {
            collections.value.forEach { collection ->
                if (collection.entries.any { it.scryfallId == oldScryfallId }) {
                    repository.changeEntryPrinting(collection.id, oldScryfallId, newCard)
                }
            }
            decks.value.forEach { deck ->
                if (deck.cards.any { it.scryfallId == oldScryfallId }) {
                    deckRepository.changeCardPrinting(deck.id, oldScryfallId, newCard)
                }
            }
        }
    }

    private val _importProgress = MutableStateFlow<ImportProgress>(ImportProgress.Idle)
    /** Where importing a binder from another app is up to. */
    val importProgress: StateFlow<ImportProgress> = _importProgress.asStateFlow()

    /** Binders picked cards can be gathered into: owned ones and the Unsorted pile, not wishlists. */
    val binderTargets: StateFlow<List<MoveTarget>> = repository.collectionsFlow
        .map { all -> all.filter { it.kind == CollectionType.OWNED }.map { MoveTarget(SourceKind.BINDER, it.id, it.name) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Decks picked cards can be added to. */
    val deckTargets: StateFlow<List<MoveTarget>> = deckRepository.decksFlow
        .map { all -> all.map { MoveTarget(SourceKind.DECK, it.id, it.name) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Gathers every copy of the picked cards [ids] from the user's binders into [binderId]. */
    fun gatherIntoBinder(ids: Set<String>, binderId: String) {
        viewModelScope.launch { repository.gatherInto(binderId, ids) }
    }

    /** Makes a binder named [name] and gathers the picked cards [ids] into it. */
    fun gatherIntoNewBinder(ids: Set<String>, name: String) {
        viewModelScope.launch {
            val binder = repository.createCollection(name.trim().ifBlank { "New binder" }, CollectionType.OWNED)
            repository.gatherInto(binder.id, ids)
        }
    }

    /** Adds one copy of each picked card to the deck [deckId] — cards already in it are left as they are. */
    fun addToDeck(ids: Set<String>, deckId: String) {
        viewModelScope.launch {
            val inDeck = decks.value.firstOrNull { it.id == deckId }?.cards.orEmpty().map { it.scryfallId }.toSet()
            allCards.value.filter { it.scryfallId in ids && it.scryfallId !in inDeck }.forEach { c ->
                deckRepository.addEntry(deckId, DeckCardEntry(c.scryfallId, c.name, c.imageUrl, quantity = 1, backImageUrl = c.backImageUrl, tags = c.tags))
            }
        }
    }

    /** Removes the picked cards [ids] from all the user's binders; decks and wishlists keep theirs. */
    fun removeFromCollection(ids: Set<String>) {
        viewModelScope.launch { repository.removeEverywhere(ids) }
    }

    /** How many copies of the cards [ids] the user's binders hold (what removing them takes away). */
    fun copiesInBinders(ids: Set<String>): Int =
        collections.value.filter { it.kind == CollectionType.OWNED }.sumOf { c -> c.entries.filter { it.scryfallId in ids }.sumOf { it.quantity + it.foilQuantity } }

    /**
     * The picked cards [ids] as text for other apps: the copies in binders (foils kept apart), or
     * a deck's copies for a card only in decks. [exact] names each card's printing.
     */
    suspend fun exportText(ids: Set<String>, exact: Boolean): String {
        val owned = collections.value.filter { it.kind == CollectionType.OWNED }.flatMap { it.entries }.filter { it.scryfallId in ids }
        val byCard = owned.groupBy { it.scryfallId }.map { (_, copies) ->
            copies.first().copy(quantity = copies.sumOf { it.quantity }, foilQuantity = copies.sumOf { it.foilQuantity })
        }
        val deckOnly = allCards.value.filter { it.scryfallId in ids && byCard.none { e -> e.scryfallId == it.scryfallId } }
            .map { CollectionEntry(it.scryfallId, it.name, it.imageUrl, quantity = it.total) }
        val entries = byCard + deckOnly
        if (!exact) return buildCardListText(entries)
        val printings = cardRepository.getCardsByIds(entries.map { it.scryfallId })
            .mapNotNull { c -> if (c.set != null && c.collectorNumber != null) c.id to (c.set to c.collectorNumber) else null }
            .toMap()
        return buildCardListText(entries, printings)
    }

    /** The pile of cards not in a binder yet, if there is one. */
    val unsorted: StateFlow<Collection?> = repository.collectionsFlow.map { all -> all.firstOrNull { it.isUnsorted } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Imports a pasted or loaded card list (text or CSV): into a new binder named [name], or with no
     * name into the Unsorted pile, to be sorted into binders later.
     */
    fun importBinder(name: String?, text: String) {
        val lines = parseCardList(text).lines
        if (lines.isEmpty()) return
        viewModelScope.launch {
            _importProgress.value = ImportProgress.Working(0, lines.size)
            _importProgress.value = try {
                val result = CardListImporter(cardRepository).resolve(lines) { done, total -> _importProgress.value = ImportProgress.Working(done, total) }
                val binderName = name?.ifBlank { "Imported" }
                if (result.cards.isNotEmpty()) {
                    if (binderName == null) {
                        repository.addUnsorted(result.cards.map { it.toEntry() })
                    } else {
                        val binder = repository.createCollection(binderName, CollectionType.OWNED)
                        repository.addEntries(binder.id, result.cards.map { it.toEntry() })
                    }
                }
                ImportProgress.Done(result, binderName ?: "your collection (Unsorted)")
            } catch (e: java.io.IOException) {
                ImportProgress.Failed("You're offline — try again when you're connected.")
            } catch (e: Exception) {
                ImportProgress.Failed(e.message ?: "Something went wrong.")
            }
        }
    }

    fun resetImport() { _importProgress.value = ImportProgress.Idle }

    fun createCollection(name: String, type: CollectionType = CollectionType.DEFAULT, onCreated: (Collection) -> Unit) {
        viewModelScope.launch { onCreated(repository.createCollection(name, type)) }
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
