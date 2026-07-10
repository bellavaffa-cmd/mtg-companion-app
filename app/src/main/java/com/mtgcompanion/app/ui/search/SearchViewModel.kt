package com.mtgcompanion.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Success(val cards: List<ScryfallCard>) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

class SearchViewModel(private val repository: CardRepository = CardRepository()) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    fun search() {
        val currentQuery = _query.value
        if (currentQuery.isBlank()) return
        viewModelScope.launch {
            _uiState.value = SearchUiState.Loading
            _uiState.value = try {
                SearchUiState.Success(repository.search(currentQuery))
            } catch (e: Exception) {
                SearchUiState.Error(e.message ?: "Something went wrong searching Scryfall.")
            }
        }
    }
}
