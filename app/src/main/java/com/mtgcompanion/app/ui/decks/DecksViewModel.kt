package com.mtgcompanion.app.ui.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckRepository
import com.mtgcompanion.app.data.GameMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class DecksViewModel(
    private val repository: DeckRepository,
    private val cardRepository: CardRepository = CardRepository()
) : ViewModel() {

    val decks: StateFlow<List<Deck>> = repository.decksFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    /** deckId -> commander colour identity (e.g. ["U","B"]) for the mana pips on each deck tile. */
    val commanderColors: StateFlow<Map<String, List<String>>> = decks.mapLatest { deckList ->
        val ids = deckList.mapNotNull { it.commander?.scryfallId }
        if (ids.isEmpty()) return@mapLatest emptyMap()
        val byId = cardRepository.getCardsByIds(ids).associateBy { it.id }
        deckList.mapNotNull { deck ->
            val commanderId = deck.commander?.scryfallId ?: return@mapNotNull null
            val colors = byId[commanderId]?.colorIdentity ?: return@mapNotNull null
            deck.id to colors
        }.toMap()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun createDeck(name: String, gameMode: GameMode, onCreated: (Deck) -> Unit) {
        viewModelScope.launch { onCreated(repository.createDeck(name, gameMode)) }
    }

    fun deleteDeck(deckId: String) {
        viewModelScope.launch { repository.deleteDeck(deckId) }
    }

    class Factory(private val repository: DeckRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DecksViewModel(repository) as T
    }
}
