package com.mtgcompanion.app.ui.collection

import com.mtgcompanion.app.data.userTagsOf
import com.mtgcompanion.app.data.allUserTags
import com.mtgcompanion.app.data.WISHLIST_ID
import com.mtgcompanion.app.data.Deck
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
import com.mtgcompanion.app.ui.common.AddToOps
import com.mtgcompanion.app.ui.common.AddToPick
import com.mtgcompanion.app.ui.common.asTarget
import com.mtgcompanion.app.ui.common.copiesTaken
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



    /**
     * Every printing's user tags, by scryfallId, gathered from every deck and binder — a copy added
     * to a deck after it was tagged in a binder still shows the tag, since a tag belongs to the copy.
     */
    val userTagsByCard: StateFlow<Map<String, List<String>>> =
        combine(deckRepository.decksFlow, repository.collectionsFlow) { decks, collections ->
            (decks.flatMap { it.cards + it.considering + listOfNotNull(it.commander, it.partnerCommander) }
                .map { it.scryfallId } + collections.flatMap { c -> c.entries.map { it.scryfallId } })
                .distinct()
                .associateWith { id -> userTagsOf(decks, collections, id) }
                .filterValues { it.isNotEmpty() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Tags the user has written on their own copies, for offering them again while typing. */
    val knownUserTags: StateFlow<List<String>> =
        combine(deckRepository.decksFlow, repository.collectionsFlow) { decks, collections ->
            allUserTags(decks, collections)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The user's own tags on a copy they own — every deck and binder holding that printing. */
    fun setUserTags(scryfallId: String, tags: List<String>) {
        viewModelScope.launch {
            repository.setUserTags(scryfallId, tags)
            deckRepository.setUserTags(scryfallId, tags)
        }
    }

    /** List or grid, as set in Settings > Card Display. */
    val viewMode: StateFlow<CardViewMode> = settingsRepository.collectionViewMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CardViewMode.DEFAULT)

    /** Grid column count, when [viewMode] is Grid. */
    val gridColumns: StateFlow<Int> = settingsRepository.gridColumns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GRID_COLUMNS_DEFAULT)

    /** Decks and other binders this binder's cards can be moved into. */
    val moveTargets: StateFlow<List<MoveTarget>> =
        combine(deckRepository.decksFlow, repository.collectionsFlow) { decks, collections ->
            decks.map { it.asTarget() } + collections.filter { it.id != collectionId }.map { it.asTarget() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The user's decks — for "Considering in …" on a card the Wishlist has because a deck is considering it. */
    val decks: StateFlow<List<Deck>> = deckRepository.decksFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    /**
     * Takes a card off. On the Wishlist, one the app added by itself means "not interested": it
     * stays off while decks consider it, instead of coming straight back.
     */
    fun remove(entry: CollectionEntry) {
        viewModelScope.launch {
            if (collectionId == WISHLIST_ID && entry.auto) repository.notInterested(entry.name)
            else repository.removeEntry(collectionId, entry.scryfallId)
        }
    }

    /**
     * Sends [pick]'s quantity of [entry]'s copies (plain ones first, then foils) to a deck or another
     * binder — moving them out of this binder, or with [keep], copying them. Onto a deck's
     * Considering list they're copied either way: it's a list of cards to think about, and the
     * copies stay where they are. Run by the add confirmation ([ops]), which can undo it.
     */
    suspend fun sendEntry(entry: CollectionEntry, pick: AddToPick, keep: Boolean, ops: AddToOps) {
        val target = ops.resolve(pick)
        val (plain, foil) = copiesTaken(entry, pick.quantity)
        val moving = entry.copy(quantity = plain, foilQuantity = foil)
        when (target.kind) {
            SourceKind.DECK -> {
                val deckEntry = cardRepository.withFullCardInfo(listOf(moving.toDeckEntry())).first()
                if (pick.considering) deckRepository.addConsideringEntry(target.id, deckEntry.copy(quantity = 1))
                else deckRepository.addEntry(target.id, deckEntry)
            }
            SourceKind.BINDER -> repository.addEntry(target.id, moving)
        }
        if (!keep && !pick.considering) {
            val leftPlain = entry.quantity - plain
            val leftFoil = entry.foilQuantity - foil
            if (leftPlain + leftFoil <= 0) repository.removeEntry(collectionId, entry.scryfallId)
            else repository.setQuantity(collectionId, entry.scryfallId, leftPlain, leftFoil)
        }
    }

    /**
     * Sends the picked cards [ids] (all their copies) to a deck or another binder — moving them, or
     * with [keep], copying them; see [sendEntry] for Considering.
     */
    suspend fun sendEntries(ids: Set<String>, pick: AddToPick, keep: Boolean, ops: AddToOps) {
        val target = ops.resolve(pick)
        when (target.kind) {
            SourceKind.BINDER -> repository.transferEntries(collectionId, ids, target.id, keep)
            SourceKind.DECK -> {
                val picked = collection.value?.entries.orEmpty().filter { it.scryfallId in ids }
                // One lookup for all the picked cards, rather than one each.
                val entries = cardRepository.withFullCardInfo(picked.map { it.toDeckEntry() })
                if (pick.considering) deckRepository.addConsideringEntries(target.id, entries.map { it.copy(quantity = 1) })
                else deckRepository.addEntries(target.id, entries)
                if (!keep && !pick.considering) repository.removeEntries(collectionId, ids)
            }
        }
    }

    /** Undoes "not interested" for [cardName] — it comes back while a deck considers it. */
    fun wantAgain(cardName: String) {
        viewModelScope.launch { repository.wantAgain(cardName) }
    }

    /** Removes the picked cards [ids] from this binder — see [remove] for the Wishlist. */
    fun removeEntries(ids: Set<String>) {
        viewModelScope.launch {
            val picked = collection.value?.entries.orEmpty().filter { it.scryfallId in ids }
            if (collectionId == WISHLIST_ID) picked.filter { it.auto }.forEach { repository.notInterested(it.name) }
            repository.removeEntries(collectionId, ids)
        }
    }

    /** Removes every card but keeps the binder (the Unsorted pile is emptied, not deleted). */
    fun clearAll(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.clearEntries(collectionId)
            onDone()
        }
    }

    /**
     * A deck entry for every copy of a binder card, foil or not. It has only what the binder keeps;
     * cardRepository.withFullCardInfo fills in the rest (type, commander-ness).
     */
    private fun CollectionEntry.toDeckEntry() =
        DeckCardEntry(scryfallId, name, imageUrl, quantity = quantity + foilQuantity, backImageUrl = backImageUrl, tags = tags)

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
