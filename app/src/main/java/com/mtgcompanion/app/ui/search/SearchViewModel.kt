package com.mtgcompanion.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.isOffline
import com.mtgcompanion.app.data.offline.OfflineCardRepository
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
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
    private val repository: CardRepository = CardRepository()
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filters = MutableStateFlow(SearchFilters())
    val filters: StateFlow<SearchFilters> = _filters.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        // Search as the user types or changes filters: debounce so we don't fire on every keystroke,
        // and collectLatest cancels an in-flight request when the query changes again.
        viewModelScope.launch {
            combine(_query, _filters) { q, f -> buildScryfallQuery(q.trim(), f) }
                .debounce(300)
                .distinctUntilChanged()
                .collectLatest { q -> runSearch(q) }
        }
    }

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    fun onFiltersChange(filters: SearchFilters) {
        _filters.value = filters
    }

    /** Immediate search (e.g. from the search icon / keyboard action), bypassing the debounce. */
    fun search() {
        viewModelScope.launch { runSearch(buildScryfallQuery(_query.value.trim(), _filters.value)) }
    }

    private suspend fun runSearch(query: String) {
        if (query.isBlank()) {
            _uiState.value = SearchUiState.Idle
            return
        }
        _uiState.value = SearchUiState.Loading
        _uiState.value = try {
            SearchUiState.Success(repository.search(query))
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

    class Factory(
        private val offlineRepository: OfflineCardRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SearchViewModel(offlineRepository) as T
    }
}
