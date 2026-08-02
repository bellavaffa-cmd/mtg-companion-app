package com.mtgcompanion.app.ui.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.DeckCardEntry
import com.mtgcompanion.app.data.DeckRepository
import com.mtgcompanion.app.data.GameMode
import com.mtgcompanion.app.data.PreconContents
import com.mtgcompanion.app.data.PreconInfo
import com.mtgcompanion.app.data.PreconRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PreconUiState(
    val loading: Boolean = true,
    val precons: List<PreconInfo> = emptyList(),
    val error: String? = null
)

class PreconsViewModel(
    private val deckRepository: DeckRepository,
    private val preconRepository: PreconRepository = PreconRepository(),
    private val cardRepository: CardRepository = CardRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(PreconUiState())
    val uiState: StateFlow<PreconUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = try {
                PreconUiState(loading = false, precons = preconRepository.listCommanderPrecons())
            } catch (e: Exception) {
                PreconUiState(loading = false, error = "Couldn't load the precon list: ${e.message ?: "unknown error"}")
            }
        }
    }

    /** For the read-only contents dialog. */
    suspend fun loadContents(precon: PreconInfo): PreconContents = preconRepository.getContents(precon.fileName)

    /** Resolves the precon's cards via Scryfall and creates it as a new Commander deck. */
    fun importAsDeck(precon: PreconInfo, onImported: (deckId: String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val contents = preconRepository.getContents(precon.fileName)
                val all = contents.commander + contents.cards
                val ids = all.mapNotNull { it.scryfallId }.distinct()
                if (ids.isEmpty()) {
                    onError("Couldn't resolve any cards for this precon.")
                    return@launch
                }
                val cardsById = cardRepository.getCardsByIds(ids).associateBy { it.id }
                val deckEntries = all.mapNotNull { entry ->
                    val id = entry.scryfallId ?: return@mapNotNull null
                    val card = cardsById[id] ?: return@mapNotNull null
                    DeckCardEntry(card.id, card.name, card.displayImageUrl, entry.quantity, card.canBeCommander, card.typeLine, card.partnerAbility)
                }
                if (deckEntries.isEmpty()) {
                    onError("None of this precon's cards could be found on Scryfall.")
                    return@launch
                }
                val deck = deckRepository.createDeck(precon.name, GameMode.COMMANDER)
                deckRepository.addEntries(deck.id, deckEntries)
                // MTGJSON lists 2 commanders for a partner precon — set both when present.
                val commanderScryfallIds = contents.commander.mapNotNull { it.scryfallId }
                val commanderEntries = commanderScryfallIds.mapNotNull { id -> deckEntries.firstOrNull { it.scryfallId == id } }
                commanderEntries.getOrNull(0)?.let { deckRepository.setCommander(deck.id, it) }
                commanderEntries.getOrNull(1)?.let { deckRepository.setPartnerCommander(deck.id, it) }
                onImported(deck.id)
            } catch (e: Exception) {
                onError("Import failed: ${e.message ?: "unknown error"}")
            }
        }
    }

    class Factory(private val deckRepository: DeckRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PreconsViewModel(deckRepository) as T
    }
}
