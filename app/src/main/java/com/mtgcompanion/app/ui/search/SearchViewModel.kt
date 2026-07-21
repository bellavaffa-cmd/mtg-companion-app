package com.mtgcompanion.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.CardViewMode
import com.mtgcompanion.app.data.CollectionRepository
import com.mtgcompanion.app.data.DeckRepository
import com.mtgcompanion.app.data.GRID_COLUMNS_DEFAULT
import com.mtgcompanion.app.data.SettingsRepository
import com.mtgcompanion.app.data.isOffline
import com.mtgcompanion.app.data.offline.OfflineCardRepository
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.ui.common.MoveTarget
import com.mtgcompanion.app.ui.common.SourceKind
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    /** [offline] is true when the results came from the local database rather than Scryfall. */
    data class Success(val cards: List<ScryfallCard>, val offline: Boolean = false) : SearchUiState
    data class Error(val message: String) : SearchUiState
    /** Offline and no card database has been downloaded yet. */
    data object OfflineNoDatabase : SearchUiState
}

/** How results are ordered. [order]/[dir] map straight to Scryfall's `order`/`dir` query params. */
enum class SortOption(val label: String, val order: String?, val dir: String?) {
    RELEVANCE("Relevance", null, null),
    NAME("Name", "name", "asc"),
    PRICE_LOW("Price: Low to High", "usd", "asc"),
    PRICE_HIGH("Price: High to Low", "usd", "desc"),
    NEWEST("Newest", "released", "desc")
}

/** Structured search filters, compiled into a Scryfall query alongside the free-text field. */
data class SearchFilters(
    val typeLine: String = "",
    val oracle: String = "",
    val manaCost: String = "",
    val sets: String = "",
    val rarities: Set<String> = emptySet(),      // common, uncommon, rare, mythic
    val priceMin: String = "",
    val priceMax: String = "",
    val powerMin: String = "",
    val powerMax: String = "",
    val toughnessMin: String = "",
    val toughnessMax: String = "",
    val finishes: Set<String> = emptySet(),       // nonfoil, foil, etched
    val artist: String = ""
) {
    val isActive: Boolean
        get() = typeLine.isNotBlank() || oracle.isNotBlank() || manaCost.isNotBlank() ||
            sets.isNotBlank() || rarities.isNotEmpty() || priceMin.isNotBlank() || priceMax.isNotBlank() ||
            powerMin.isNotBlank() || powerMax.isNotBlank() || toughnessMin.isNotBlank() ||
            toughnessMax.isNotBlank() || finishes.isNotEmpty() || artist.isNotBlank()
}

private fun quoteIfNeeded(value: String): String =
    if (value.any { it.isWhitespace() }) "\"$value\"" else value

private fun asNumber(value: String): String? =
    value.trim().takeIf { it.isNotBlank() && it.toDoubleOrNull() != null }

/** Turn the free-text query and [filters] into a single Scryfall search query. */
fun buildScryfallQuery(text: String, filters: SearchFilters): String {
    val parts = mutableListOf<String>()
    if (text.isNotBlank()) parts += text.trim()

    // Each word of the type line is its own constraint (e.g. "legendary creature").
    filters.typeLine.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        .forEach { parts += "type:${it.lowercase()}" }

    if (filters.oracle.isNotBlank()) parts += "oracle:${quoteIfNeeded(filters.oracle.trim())}"
    if (filters.manaCost.isNotBlank()) parts += "mana:${filters.manaCost.trim().replace(" ", "")}"

    val sets = filters.sets.split(Regex("[,\\s]+")).filter { it.isNotBlank() }.map { it.lowercase() }
    if (sets.size == 1) parts += "set:${sets[0]}"
    else if (sets.size > 1) parts += "(" + sets.joinToString(" or ") { "set:$it" } + ")"

    if (filters.rarities.isNotEmpty()) {
        parts += "(" + filters.rarities.joinToString(" or ") { "rarity:$it" } + ")"
    }

    asNumber(filters.priceMin)?.let { parts += "usd>=$it" }
    asNumber(filters.priceMax)?.let { parts += "usd<=$it" }
    asNumber(filters.powerMin)?.let { parts += "pow>=$it" }
    asNumber(filters.powerMax)?.let { parts += "pow<=$it" }
    asNumber(filters.toughnessMin)?.let { parts += "tou>=$it" }
    asNumber(filters.toughnessMax)?.let { parts += "tou<=$it" }

    if (filters.finishes.isNotEmpty()) {
        parts += "(" + filters.finishes.joinToString(" or ") { "is:$it" } + ")"
    }

    if (filters.artist.isNotBlank()) parts += "artist:${quoteIfNeeded(filters.artist.trim())}"

    return parts.joinToString(" ")
}

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val offlineRepository: OfflineCardRepository,
    private val settingsRepository: SettingsRepository,
    private val collectionRepository: CollectionRepository,
    private val deckRepository: DeckRepository,
    private val repository: CardRepository = CardRepository()
) : ViewModel() {

    /** List or grid, as set in Settings > Card Display. */
    val viewMode: StateFlow<CardViewMode> = settingsRepository.searchViewMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CardViewMode.DEFAULT)

    /** Grid column count, when [viewMode] is Grid. */
    val gridColumns: StateFlow<Int> = settingsRepository.gridColumns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GRID_COLUMNS_DEFAULT)

    /** Every binder and deck, for the long-press "Add to…" picker. */
    val addTargets: StateFlow<List<MoveTarget>> = combine(
        deckRepository.decksFlow, collectionRepository.collectionsFlow
    ) { decks, collections ->
        decks.map { MoveTarget(SourceKind.DECK, it.id, it.name) } +
            collections.map { MoveTarget(SourceKind.BINDER, it.id, it.name) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filters = MutableStateFlow(SearchFilters())
    val filters: StateFlow<SearchFilters> = _filters.asStateFlow()

    private val _sortBy = MutableStateFlow(SortOption.RELEVANCE)
    val sortBy: StateFlow<SortOption> = _sortBy.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /** As-you-type name suggestions, shown above results until a real search replaces them. */
    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    init {
        // Search as the user types or changes filters/sort: debounce so we don't fire on every
        // keystroke, and collectLatest cancels an in-flight request when the query changes again.
        viewModelScope.launch {
            combine(_query, _filters, _sortBy) { q, f, sort -> Triple(q, f, sort) }
                .debounce(300)
                .distinctUntilChanged()
                .collectLatest { (q, f, sort) -> runSearch(buildScryfallQuery(q.trim(), f), sort) }
        }
        // A separate, shorter-debounced pipeline for the autocomplete dropdown — it's a lightweight
        // endpoint meant for exactly this, so it can react faster than the real search.
        viewModelScope.launch {
            _query.debounce(150).distinctUntilChanged().collectLatest { q ->
                _suggestions.value = if (q.isBlank()) emptyList() else repository.autocomplete(q.trim())
            }
        }
    }

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    fun onFiltersChange(filters: SearchFilters) {
        _filters.value = filters
    }

    fun onSortChange(sort: SortOption) {
        _sortBy.value = sort
    }

    /** Fill the query with [name] and search immediately — the autocomplete dropdown's tap action. */
    fun pickSuggestion(name: String) {
        _suggestions.value = emptyList()
        _query.value = name
        search()
    }

    /** Immediate search (e.g. from the search icon / keyboard action), bypassing the debounce. */
    fun search() {
        viewModelScope.launch { runSearch(buildScryfallQuery(_query.value.trim(), _filters.value), _sortBy.value) }
    }

    /** Fetch one random card, for the Search tab's discovery button. */
    fun randomCard(onResult: (ScryfallCard) -> Unit) {
        viewModelScope.launch {
            val card = try {
                repository.getRandom()
            } catch (e: Exception) {
                null
            }
            card?.let { onResult(it) }
        }
    }

    private suspend fun runSearch(query: String, sort: SortOption) {
        _suggestions.value = emptyList()
        if (query.isBlank()) {
            _uiState.value = SearchUiState.Idle
            return
        }
        _uiState.value = SearchUiState.Loading
        _uiState.value = try {
            SearchUiState.Success(repository.search(query, sort.order, sort.dir))
        } catch (e: Exception) {
            if (isOffline(e)) {
                // Fall back to the locally downloaded card database, if there is one.
                if (offlineRepository.status.value.hasData) {
                    SearchUiState.Success(
                        offlineRepository.search(_query.value.trim(), _filters.value),
                        offline = true
                    )
                } else {
                    SearchUiState.OfflineNoDatabase
                }
            } else {
                SearchUiState.Error(e.message ?: "Something went wrong searching Scryfall.")
            }
        }
    }

    /** Add [card] straight into [target], for the long-press "Add to…" action. */
    fun addToTarget(card: ScryfallCard, target: MoveTarget) {
        viewModelScope.launch {
            when (target.kind) {
                SourceKind.DECK -> deckRepository.addCardToDeck(target.id, card)
                SourceKind.BINDER -> collectionRepository.addCard(target.id, card)
            }
        }
    }

    class Factory(
        private val offlineRepository: OfflineCardRepository,
        private val settingsRepository: SettingsRepository,
        private val collectionRepository: CollectionRepository,
        private val deckRepository: DeckRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SearchViewModel(offlineRepository, settingsRepository, collectionRepository, deckRepository) as T
    }
}
