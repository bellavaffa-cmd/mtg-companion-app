package com.mtgcompanion.app.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtgcompanion.app.data.CollectionEntry
import com.mtgcompanion.app.data.CollectionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CollectionViewModel(private val repository: CollectionRepository) : ViewModel() {

    val entries: StateFlow<List<CollectionEntry>> = repository.collectionFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    fun setQuantity(entry: CollectionEntry, quantity: Int, foilQuantity: Int) {
        viewModelScope.launch { repository.setQuantity(entry.scryfallId, quantity, foilQuantity) }
    }

    fun remove(entry: CollectionEntry) {
        viewModelScope.launch { repository.removeEntry(entry.scryfallId) }
    }

    class Factory(private val repository: CollectionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CollectionViewModel(repository) as T
    }
}
