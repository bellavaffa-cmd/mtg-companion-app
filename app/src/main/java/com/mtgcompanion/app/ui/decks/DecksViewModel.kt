package com.mtgcompanion.app.ui.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckRepository
import com.mtgcompanion.app.data.DeckValueHistory
import com.mtgcompanion.app.data.decksDue
import com.mtgcompanion.app.data.isCube
import kotlinx.coroutines.flow.map
import com.mtgcompanion.app.data.deckValueOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class DecksViewModel(
    private val repository: DeckRepository,
    private val cardRepository: CardRepository = CardRepository()
) : ViewModel() {

    // Cubes are kept as decks but have their own list (CubesScreen.kt).
    val decks: StateFlow<List<Deck>> = repository.decksFlow.map { all -> all.filterNot { it.isCube } }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    /** deckId -> commander colour identity (e.g. ["U","B"]) for the mana pips on each deck tile. */
    val commanderColors: StateFlow<Map<String, List<String>>> = decks.mapLatest { deckList ->
        val ids = deckList.flatMap { listOfNotNull(it.commander?.scryfallId, it.partnerCommander?.scryfallId) }
        if (ids.isEmpty()) return@mapLatest emptyMap()
        val byId = cardRepository.getCardsByIds(ids).associateBy { it.id }
        deckList.mapNotNull { deck ->
            val commanderId = deck.commander?.scryfallId ?: return@mapNotNull null
            val colors = byId[commanderId]?.colorIdentity ?: return@mapNotNull null
            val partnerColors = deck.partnerCommander?.scryfallId?.let { byId[it]?.colorIdentity }.orEmpty()
            deck.id to (colors + partnerColors).distinct()
        }.toMap()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Changes several decks in one step: filing them in folders, archiving them (DeckFolders.kt). */
    fun changeDecks(transform: (List<Deck>) -> List<Deck>) {
        viewModelScope.launch { repository.change(transform) }
    }

    init {
        // Once a day, today's value of every deck that has none yet, from one lookup of all their
        // cards (DeckValueHistory.kt); points of decks that are gone are forgotten.
        viewModelScope.launch {
            val all = repository.decksFlow.first()
            if (all.isNotEmpty()) DeckValueHistory.prune(all.map { it.id })
            val today = LocalDate.now().toString()
            if (DeckValueHistory.sampledOn == today) return@launch
            val due = decksDue(DeckValueHistory.points.value, all, today)
            if (due.isEmpty()) return@launch
            DeckValueHistory.sampledOn = today
            val ids = due.flatMap { d -> d.cards.map { it.scryfallId } }.distinct()
            val cards = runCatching { cardRepository.getCardsByIds(ids) }.getOrElse {
                DeckValueHistory.sampledOn = ""
                return@launch
            }
            val prices = cards.associate { it.id to it.prices?.usd?.toDoubleOrNull() }
            due.forEach { d -> deckValueOf(d, prices)?.let { (usd, n) -> DeckValueHistory.record(d.id, usd, n) } }
        }
    }

    class Factory(private val repository: DeckRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DecksViewModel(repository) as T
    }
}
