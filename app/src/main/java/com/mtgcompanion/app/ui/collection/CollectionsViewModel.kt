package com.mtgcompanion.app.ui.collection

import com.mtgcompanion.app.data.usage.Usage
import com.mtgcompanion.app.data.usage.UsageAction
import com.mtgcompanion.app.data.spares
import com.mtgcompanion.app.data.spreadThin
import com.mtgcompanion.app.data.Spare
import com.mtgcompanion.app.data.proxyCopies
import com.mtgcompanion.app.data.DeckOwnership
import com.mtgcompanion.app.data.RoleTags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.mtgcompanion.app.data.CardListImporter
import com.mtgcompanion.app.data.CollectionEntry
import com.mtgcompanion.app.data.DeckCardEntry
import com.mtgcompanion.app.data.buildCardListText
import com.mtgcompanion.app.data.buildCardListCsv
import com.mtgcompanion.app.data.PlaceTarget
import com.mtgcompanion.app.data.UNSORTED_COLLECTION_ID
import com.mtgcompanion.app.data.afterImport
import com.mtgcompanion.app.data.placesOf
import com.mtgcompanion.app.data.BreakdownCard
import com.mtgcompanion.app.data.CollectionBreakdown
import com.mtgcompanion.app.data.SetInfo
import com.mtgcompanion.app.data.SetProgress
import com.mtgcompanion.app.data.collectionBreakdown
import com.mtgcompanion.app.data.setProgress as progressInSets
import com.mtgcompanion.app.ui.common.MoveTarget
import com.mtgcompanion.app.ui.common.AddToOps
import kotlinx.coroutines.flow.first
import com.mtgcompanion.app.ui.common.AddToPick
import com.mtgcompanion.app.ui.common.asTarget
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import com.mtgcompanion.app.data.DataAndSpeed
import kotlinx.coroutines.launch

/** One card aggregated across every collection and deck, with the total copies and where they are. */
data class AllCardEntry(
    val scryfallId: String,
    val name: String,
    val imageUrl: String?,
    val total: Int,
    /** How many of [total] are proxies — held, but worth nothing and not copies you can trade. */
    val proxies: Int = 0,
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

    /** "By tag · automatic": a binder per tag of the cards the user owns (their tags looked up as needed). */
    val tagBinders: StateFlow<List<TagBinder>> = tagBindersFlow(repository, viewModelScope, cardRepository)
    /** Tags still being looked up, (done, total). */
    val tagging: StateFlow<Pair<Int, Int>?> = RoleTags.progress

    /** List or grid for the All Cards tab, and the shared grid column count, from Settings > Card Display. */
    val viewMode: StateFlow<CardViewMode> = settingsRepository.allCardsViewMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CardViewMode.DEFAULT)

    fun setViewMode(mode: CardViewMode) {
        viewModelScope.launch { settingsRepository.setAllCardsViewMode(mode) }
    }

    val gridColumns: StateFlow<Int> = settingsRepository.gridColumns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GRID_COLUMNS_DEFAULT)

    val collections: StateFlow<List<Collection>> = repository.collectionsFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val decks: StateFlow<List<com.mtgcompanion.app.data.Deck>> = deckRepository.decksFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    /** Whether All cards has been worked out since this screen opened (for Data and speed's timing). */
    private val opened = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Every card owned anywhere (all collections + all decks), deduped by card and summed (AllCards.kt).
     * Worked out off the main thread: a big collection is tens of thousands of copies. How long it took
     * when the screen opened is what Settings › Data and speed shows as "Opening All cards".
     */
    val allCards: StateFlow<List<AllCardEntry>> =
        combine(repository.collectionsFlow, deckRepository.decksFlow) { collections, decks ->
            val started = System.nanoTime()
            allCardEntries(collections, decks).also {
                if (it.isNotEmpty() && opened.compareAndSet(false, true)) {
                    DataAndSpeed.noteAllCardsOpened((System.nanoTime() - started) / 1_000_000L, it.size)
                }
            }
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // All cards is searched by tag too, and lists the decks' cards as well as the binders'.
        viewModelScope.launch {
            allCards.map { cards -> cards.map { it.name } }.distinctUntilChanged().collectLatest { names ->
                if (names.isNotEmpty()) RoleTags.ensure(names, cardRepository)
            }
        }
    }

    /**
     * Dashboard totals for the All Cards tab. Recomputes whenever [allCards] changes by fetching
     * full card data (price/colour/type) from Scryfall in bulk. Null while empty or still loading.
     */
    val dashboard: StateFlow<CollectionDashboard?> = allCards.mapLatest { entries ->
        // Proxies are print-outs: held, but they add nothing to what the collection is worth.
        computeDashboard(cardRepository, entries.map { it.scryfallId to (it.total - it.proxies) }.filter { it.second > 0 })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** scryfallId -> USD price across all owned cards, for the enlarged-card value/total display. */
    val prices: StateFlow<Map<String, Double>> = allCards.mapLatest { entries ->
        fetchPrices(cardRepository, entries.map { it.scryfallId })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** scryfallId -> Scryfall's data for each owned card, for the All cards filters. Empty until loaded. */
    private val ownedCards: StateFlow<Map<String, ScryfallCard>> = allCards.mapLatest { entries ->
        if (entries.isEmpty()) emptyMap()
        else cardRepository.getCardsByIds(entries.map { it.scryfallId }).associateBy { it.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** scryfallId -> colors, type and rarity of each owned card, for the All cards filter. */
    val cardFacts: StateFlow<Map<String, CardFacts>> = ownedCards.map { cards -> cards.mapValues { CardFacts.of(it.value) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** scryfallId -> what the Advanced filters judge each owned card by (see AdvancedFilter.kt). */
    val advancedFacts: StateFlow<Map<String, AdvancedFacts>> = ownedCards.map { cards -> cards.mapValues { advancedFactsOf(it.value) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** scryfallId -> the copies owned (finish, condition, language, binders, decks), for Your copies. */
    val copyFacts: StateFlow<Map<String, CopyFacts>> =
        combine(repository.collectionsFlow, deckRepository.decksFlow) { collections, decks -> copyFactsOf(collections, decks) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** The filters saved by name on Advanced filters, kept on this device. */
    val savedFilters: StateFlow<List<SavedFilter>> = settingsRepository.savedFiltersJson.map { savedFiltersFromJson(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Saves the filters under [name]; one already called that is replaced. */
    fun saveFilter(name: String, basic: CollectionFilter, advanced: AdvancedFilter) {
        val n = name.trim()
        if (n.isEmpty()) return
        Usage.action(UsageAction.FILTER_SAVED)
        viewModelScope.launch {
            settingsRepository.updateSavedFiltersJson { json ->
                val list = savedFiltersFromJson(json)
                val same = list.firstOrNull { it.name.equals(n, ignoreCase = true) }
                val item = SavedFilter(same?.id ?: java.util.UUID.randomUUID().toString(), n, basic, advanced)
                savedFiltersToJson(if (same != null) list.map { if (it.id == same.id) item else it } else list + item)
            }
        }
    }

    fun renameFilter(id: String, name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        viewModelScope.launch {
            settingsRepository.updateSavedFiltersJson { json -> savedFiltersToJson(savedFiltersFromJson(json).map { if (it.id == id) it.copy(name = n) else it }) }
        }
    }

    fun deleteFilter(id: String) {
        viewModelScope.launch {
            settingsRepository.updateSavedFiltersJson { json -> savedFiltersToJson(savedFiltersFromJson(json).filterNot { it.id == id }) }
        }
    }

    /**
     * Where the collection's value sits (by set, colour, rarity, type) and its dearest cards, for the
     * dashboard's Breakdown. Null until the cards' details have loaded.
     */
    val breakdown: StateFlow<CollectionBreakdown?> = combine(allCards, cardFacts, prices) { cards, facts, priced ->
        if (cards.isEmpty() || facts.isEmpty()) return@combine null
        collectionBreakdown(cards.map { c ->
            val f = facts[c.scryfallId]
            BreakdownCard(
                id = c.scryfallId,
                name = c.name,
                imageUrl = c.imageUrl,
                // Proxies are print-outs: held, but worth nothing.
                copies = c.total - c.proxies,
                usd = priced[c.scryfallId],
                setCode = f?.set.orEmpty(),
                setName = f?.setName.orEmpty(),
                colors = f?.colors.orEmpty(),
                rarity = f?.rarity.orEmpty(),
                typeLine = f?.typeLine.orEmpty()
            )
        })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _sets = MutableStateFlow<Map<String, SetInfo>?>(null)
    private val _setsFailed = MutableStateFlow(false)
    /** Scryfall's sets couldn't be fetched (offline, say): the Sets tab says so and offers to try again. */
    val setsFailed: StateFlow<Boolean> = _setsFailed.asStateFlow()

    /** Fetches Scryfall's sets for the Sets tab, the first time it's opened (or to try again). */
    fun loadSets() {
        if (_sets.value != null) return
        viewModelScope.launch {
            _setsFailed.value = false
            val sets = runCatching { cardRepository.getSets() }.getOrNull()
            if (sets.isNullOrEmpty()) _setsFailed.value = true else _sets.value = sets
        }
    }

    /**
     * Every set the user owns a printing from, with how much of it they have. Printings whose set
     * hasn't been looked up yet wait; a set Scryfall's list lacks shows with its size unknown.
     */
    val setProgress: StateFlow<List<SetProgress>?> = combine(allCards, cardFacts, _sets) { cards, facts, sets ->
        if (sets == null) return@combine null
        val owned = cards.filter { it.total - it.proxies > 0 }
            .mapNotNull { c -> facts[c.scryfallId]?.set?.takeIf { it.isNotBlank() }?.let { c.scryfallId to it } }
            .toMap()
        progressInSets(owned, sets)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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

    /** Cards in the binders no deck plays — the obvious things to trade away (see Spares.kt). */
    val spares: StateFlow<List<Spare>> =
        combine(repository.collectionsFlow, deckRepository.decksFlow) { collections, decks ->
            spares(collections, decks)
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** How many cards the decks use more copies of than the user owns (see SpreadThin.kt); null with no decks. */
    val thinCount: StateFlow<Int?> =
        combine(repository.collectionsFlow, deckRepository.decksFlow) { collections, decks ->
            if (decks.isEmpty()) null else spreadThin(collections, decks).size
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Binders picked cards can be gathered into: owned ones and the Unsorted pile, not wishlists. */
    val binderTargets: StateFlow<List<MoveTarget>> = repository.collectionsFlow
        .map { all -> all.filter { it.kind == CollectionType.OWNED }.map { it.asTarget() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Decks picked cards can be added to. */
    val deckTargets: StateFlow<List<MoveTarget>> = deckRepository.decksFlow
        .map { all -> all.map { it.asTarget() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * The picked cards [ids] where [pick] says, run by the add confirmation ([ops]): into a binder,
     * every copy in the user's binders is gathered there; into a deck, one copy of each card the
     * deck doesn't have yet (a loose one from the Unsorted pile, when the deck holds real cards), or
     * onto its Considering list.
     */
    suspend fun sendPicked(ids: Set<String>, pick: AddToPick, ops: AddToOps) {
        val target = ops.resolve(pick)
        if (target.kind == SourceKind.BINDER) {
            // All cards lists deck cards too, but gathering only moves copies out of binders: say
            // what actually moved rather than counting the whole selection (tester report, build 19).
            val owned = repository.collectionsFlow.first().filter { it.kind == CollectionType.OWNED }
            val already = owned.firstOrNull { it.id == target.id }?.entries.orEmpty().map { it.scryfallId }.toSet()
            val elsewhere = owned.filter { it.id != target.id }.flatMap { c -> c.entries.map { it.scryfallId } }.toSet()
            val moving = ids.count { it in elsewhere }
            val deckOnly = ids.count { it !in elsewhere && it !in already }
            repository.gatherInto(target.id, ids)
            if (moving < ids.size) {
                ops.message = if (moving == 0) "Nothing moved to ${target.name}"
                else "Moved $moving ${if (moving == 1) "card" else "cards"} to ${target.name}"
                if (deckOnly > 0) ops.addNote(
                    "$deckOnly ${if (deckOnly == 1) "is" else "are"} only in decks and stayed there — a copy is in a deck or a binder, not both."
                )
                val there = ids.count { it in already && it !in elsewhere }
                if (there > 0) ops.addNote("$there ${if (there == 1) "was" else "were"} already there.")
            }
            return
        }
        val deck = deckRepository.decksFlow.first().firstOrNull { it.id == target.id }
        val have = (if (pick.considering) deck?.considering else deck?.cards).orEmpty().map { it.scryfallId }.toSet()
        val picked = allCards.value.filter { it.scryfallId in ids && it.scryfallId !in have }
        val skipped = ids.size - picked.size
        // Not allowed in the deck, and the user chose to leave them out (see AddCheck): said by the confirmation.
        val allowed = if (pick.considering) picked else picked.filterNot { ops.leaves(it.name) }
        if (skipped > 0) ops.addNote("$skipped ${if (skipped == 1) "was" else "were"} already there.")
        if (allowed.isEmpty()) {
            ops.message = "Nothing added to ${target.name}"
            return
        }
        // Looked up again so the deck knows each card's type and whether it can be the commander.
        val entries = cardRepository.withFullCardInfo(
            allowed.map { c -> DeckCardEntry(c.scryfallId, c.name, c.imageUrl, quantity = 1, backImageUrl = c.backImageUrl, tags = c.tags) }
        )
        if (pick.considering) {
            deckRepository.addConsideringEntries(target.id, entries)
        } else {
            deckRepository.addEntries(target.id, entries)
            // A loose copy in the Unsorted pile is the one that went into the deck.
            allowed.forEach { c -> repository.takeIntoDeck(deck, c.scryfallId, c.name) }
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
        val owned = repository.collectionsFlow.first().filter { it.kind == CollectionType.OWNED }.flatMap { it.entries }.filter { it.scryfallId in ids }
        val byCard = owned.groupBy { it.scryfallId }.map { (_, copies) ->
            copies.first().copy(quantity = copies.sumOf { it.quantity }, foilQuantity = copies.sumOf { it.foilQuantity })
        }
        val inBinders = byCard.mapTo(HashSet()) { it.scryfallId }
        val deckOnly = allCards.value.filter { it.scryfallId in ids && it.scryfallId !in inBinders }
            .map { CollectionEntry(it.scryfallId, it.name, it.imageUrl, quantity = it.total) }
        val entries = byCard + deckOnly
        if (!exact) return buildCardListText(entries)
        val printings = cardRepository.getCardsByIds(entries.map { it.scryfallId })
            .mapNotNull { c -> if (c.set != null && c.collectorNumber != null) c.id to (c.set to c.collectorNumber) else null }
            .toMap()
        return buildCardListText(entries, printings)
    }

    /**
     * The picked cards [ids] as a CSV file — each binder's copies a row of their own, so their
     * condition and language go too; a card only in decks as one row of its copies there.
     */
    suspend fun exportCsv(ids: Set<String>): String {
        val owned = repository.collectionsFlow.first().filter { it.kind == CollectionType.OWNED }.flatMap { it.entries }.filter { it.scryfallId in ids }
        val inBinders = owned.mapTo(HashSet()) { it.scryfallId }
        val deckOnly = allCards.value.filter { it.scryfallId in ids && it.scryfallId !in inBinders }
            .map { CollectionEntry(it.scryfallId, it.name, it.imageUrl, quantity = it.total - it.proxies) }
            .filter { it.quantity > 0 }
        val entries = owned + deckOnly
        val printings = cardRepository.getCardsByIds(entries.map { it.scryfallId }.distinct())
            .mapNotNull { c -> if (c.set != null && c.collectorNumber != null) c.id to (c.set to c.collectorNumber) else null }
            .toMap()
        return buildCardListCsv(entries, printings, placesOf(repository.collectionsFlow.first()))
    }

    /** The pile of cards not in a binder yet, if there is one. */
    val unsorted: StateFlow<Collection?> = repository.collectionsFlow.map { all -> all.firstOrNull { it.isUnsorted } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Imports a pasted or loaded card list (text or CSV): into a new binder named [name], or with no
     * name into the Unsorted pile, to be sorted into binders later.
     */
    fun importBinder(name: String?, text: String, targets: Map<String, PlaceTarget> = emptyMap()) {
        val parsed = parseCardList(text)
        val lines = parsed.lines
        if (lines.isEmpty()) return
        viewModelScope.launch {
            _importProgress.value = ImportProgress.Working(0, lines.size)
            _importProgress.value = try {
                val result = CardListImporter(cardRepository).resolve(lines) { done, total -> _importProgress.value = ImportProgress.Working(done, total) }
                val binderName = name?.ifBlank { "Imported" }
                if (result.cards.isNotEmpty()) {
                    if (binderName == null) {
                        repository.addUnsorted(result.cards.map { it.toEntry() })
                        repository.afterImport(UNSORTED_COLLECTION_ID, result, targets, parsed.locationColumn)
                    } else {
                        val binder = repository.createCollection(binderName, CollectionType.OWNED)
                        repository.addEntries(binder.id, result.cards.map { it.toEntry() })
                        repository.afterImport(binder.id, result, targets, parsed.locationColumn)
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

    /** Changes the storage places or where copies are kept (see StoragePlaces.kt). */
    fun changeStorage(change: (List<Collection>) -> List<Collection>) {
        viewModelScope.launch { repository.changeStorage(change) }
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
