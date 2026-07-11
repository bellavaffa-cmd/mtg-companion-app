package com.mtgcompanion.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Success(val cards: List<ScryfallCard>) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

@OptIn(FlowPreview::class)
class SearchViewModel(private val repository: CardRepository = CardRepository()) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        // Search as the user types: debounce so we don't fire on every keystroke,
        // and collectLatest cancels an in-flight request when the query changes again.
        viewModelScope.launch {
            _query
                .map { it.trim() }
                .debounce(300)
                .distinctUntilChanged()
                .collectLatest { q -> runSearch(q) }
        }
    }

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    /** Immediate search (e.g. from the search icon / keyboard action), bypassing the debounce. */
    fun search() {
        viewModelScope.launch { runSearch(_query.value.trim()) }
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
            SearchUiState.Error(e.message ?: "Something went wrong searching Scryfall.")
        }
    }
}
