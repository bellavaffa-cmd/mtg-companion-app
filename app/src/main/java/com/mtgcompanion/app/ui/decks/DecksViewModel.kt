package com.mtgcompanion.app.ui.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DecksViewModel(private val repository: DeckRepository) : ViewModel() {

    val decks: StateFlow<List<Deck>> = repository.decksFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    fun createDeck(name: String, onCreated: (Deck) -> Unit) {
        viewModelScope.launch { onCreated(repository.createDeck(name)) }
    }

    fun deleteDeck(deckId: String) {
        viewModelScope.launch { repository.deleteDeck(deckId) }
    }

    class Factory(private val repository: DeckRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DecksViewModel(repository) as T
    }
}
