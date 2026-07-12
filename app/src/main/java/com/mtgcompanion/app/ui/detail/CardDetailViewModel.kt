package com.mtgcompanion.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionRepository
import com.mtgcompanion.app.data.ComboRepository
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckRepository
import com.mtgcompanion.app.data.EdhrecRepository
import com.mtgcompanion.app.data.SettingsRepository
import com.mtgcompanion.app.data.TcgPlayerRepository
import com.mtgcompanion.app.network.edhrec.EdhrecCardList
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.spellbook.Variant
import com.mtgcompanion.app.network.tcgplayer.TcgPriceResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CardDetailUiState(
    val loading: Boolean = true,
    val card: ScryfallCard? = null,
    val error: String? = null,
    val edhrecLists: List<EdhrecCardList>? = null,
    val edhrecLoading: Boolean = false,
    val cardEdhrecLists: List<EdhrecCardList>? = null,
    val cardEdhrecLoading: Boolean = false,
    val combos: List<Variant> = emptyList(),
    val combosLoading: Boolean = false,
    val tcgPrices: List<TcgPriceResult>? = null,
    val tcgPricesConfigured: Boolean = false,
    val prints: List<ScryfallCard> = emptyList(),
    val addedToCollectionMessage: String? = null,
    val addedToDeckMessage: String? = null
)

class CardDetailViewModel(
    private val cardName: String,
    private val cardRepository: CardRepository,
    private val edhrecRepository: EdhrecRepository,
    private val comboRepository: ComboRepository,
    private val tcgPlayerRepository: TcgPlayerRepository,
    private val collectionRepository: CollectionRepository,
    private val deckRepository: DeckRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CardDetailUiState())
    val uiState: StateFlow<CardDetailUiState> = _uiState.asStateFlow()

    val decks: StateFlow<List<Deck>> = deckRepository.decksFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val collections: StateFlow<List<Collection>> = collectionRepository.collectionsFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    init {
        loadCard()
    }

    private fun loadCard() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val card = cardRepository.getByExactName(cardName)
                _uiState.value = _uiState.value.copy(loading = false, card = card)

                loadCombos()
                loadCardEdhrec()
                loadPrints()
                if (card.canBeCommander) loadEdhrec()
                card.tcgplayerId?.let { loadTcgPrice(it) }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = e.message ?: "Couldn't load this card from Scryfall."
                )
            }
        }
    }

    private fun loadEdhrec() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(edhrecLoading = true)
            val lists = try {
                edhrecRepository.getRecommendationsForCommander(cardName)
            } catch (e: Exception) {
                null
            }
            _uiState.value = _uiState.value.copy(edhrecLoading = false, edhrecLists = lists)
        }
    }

    private fun loadCardEdhrec() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cardEdhrecLoading = true)
            val lists = try {
                edhrecRepository.getCardPage(cardName)
            } catch (e: Exception) {
                null
            }
            _uiState.value = _uiState.value.copy(cardEdhrecLoading = false, cardEdhrecLists = lists)
        }
    }

    private fun loadCombos() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(combosLoading = true)
            val combos = try {
                comboRepository.findCombosUsing(cardName)
            } catch (e: Exception) {
                emptyList()
            }
            _uiState.value = _uiState.value.copy(combosLoading = false, combos = combos)
        }
    }

    private fun loadPrints() {
        viewModelScope.launch {
            val prints = try {
                cardRepository.getPrintings(cardName)
            } catch (e: Exception) {
                emptyList()
            }
            _uiState.value = _uiState.value.copy(prints = prints)
        }
    }

    /** Switch the detail view (and what gets added to a deck/binder) to a chosen printing/art. */
    fun selectPrinting(card: ScryfallCard) {
        _uiState.value = _uiState.value.copy(card = card)
        card.tcgplayerId?.let { loadTcgPrice(it) }
    }

    private fun loadTcgPrice(productId: Long) {
        viewModelScope.launch {
            val prices = try {
                tcgPlayerRepository.getMarketPrice(productId)
            } catch (e: Exception) {
                null
            }
            _uiState.value = _uiState.value.copy(
                tcgPrices = prices,
                tcgPricesConfigured = prices != null
            )
        }
    }

    fun addToCollection(collectionId: String) {
        val card = _uiState.value.card ?: return
        viewModelScope.launch {
            collectionRepository.addCard(collectionId, card)
            _uiState.value = _uiState.value.copy(addedToCollectionMessage = "Added to binder.")
        }
    }

    fun createCollectionAndAdd(name: String) {
        val card = _uiState.value.card ?: return
        viewModelScope.launch {
            val collection = collectionRepository.createCollection(name)
            collectionRepository.addCard(collection.id, card)
            _uiState.value = _uiState.value.copy(addedToCollectionMessage = "Added to \"${collection.name}\".")
        }
    }

    fun addToDeck(deckId: String) {
        val card = _uiState.value.card ?: return
        viewModelScope.launch {
            deckRepository.addCardToDeck(deckId, card)
            _uiState.value = _uiState.value.copy(addedToDeckMessage = "Added to deck.")
        }
    }

    fun createDeckAndAdd(name: String) {
        val card = _uiState.value.card ?: return
        viewModelScope.launch {
            val deck = deckRepository.createDeck(name)
            deckRepository.addCardToDeck(deck.id, card)
            _uiState.value = _uiState.value.copy(addedToDeckMessage = "Added to \"${deck.name}\".")
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(addedToCollectionMessage = null, addedToDeckMessage = null)
    }

    class Factory(
        private val cardName: String,
        private val settingsRepository: SettingsRepository,
        private val collectionRepository: CollectionRepository,
        private val deckRepository: DeckRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CardDetailViewModel(
                cardName = cardName,
                cardRepository = CardRepository(),
                edhrecRepository = EdhrecRepository(),
                comboRepository = ComboRepository(),
                tcgPlayerRepository = TcgPlayerRepository(settingsRepository),
                collectionRepository = collectionRepository,
                deckRepository = deckRepository
            ) as T
        }
    }
}
