package com.mtgcompanion.app.ui.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckCardEntry
import com.mtgcompanion.app.data.DeckRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DeckDetailViewModel(
    private val deckId: String,
    private val repository: DeckRepository
) : ViewModel() {

    val deck: StateFlow<Deck?> = repository.deckFlow(deckId).stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null
    )

    fun removeCard(scryfallId: String) {
        viewModelScope.launch { repository.removeCardFromDeck(deckId, scryfallId) }
    }

    fun setCommander(card: DeckCardEntry?) {
        viewModelScope.launch { repository.setCommander(deckId, card) }
    }

    fun deleteDeck(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteDeck(deckId)
            onDeleted()
        }
    }

    class Factory(
        private val deckId: String,
        private val repository: DeckRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DeckDetailViewModel(deckId, repository) as T
    }
}
