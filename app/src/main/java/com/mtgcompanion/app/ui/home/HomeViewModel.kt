package com.mtgcompanion.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtgcompanion.app.data.CollectionRepository
import com.mtgcompanion.app.data.DeckRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Small at-a-glance counts for the Home screen. */
class HomeViewModel(
    deckRepository: DeckRepository,
    collectionRepository: CollectionRepository
) : ViewModel() {

    val deckCount: StateFlow<Int> = deckRepository.decksFlow
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val binderCount: StateFlow<Int> = collectionRepository.collectionsFlow
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    class Factory(
        private val deckRepository: DeckRepository,
        private val collectionRepository: CollectionRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(deckRepository, collectionRepository) as T
    }
}
