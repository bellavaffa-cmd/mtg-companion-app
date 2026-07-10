package com.mtgcompanion.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.ComboRepository
import com.mtgcompanion.app.data.EdhrecRepository
import com.mtgcompanion.app.data.SettingsRepository
import com.mtgcompanion.app.data.TcgPlayerRepository
import com.mtgcompanion.app.network.edhrec.EdhrecCardList
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.spellbook.Variant
import com.mtgcompanion.app.network.tcgplayer.TcgPriceResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CardDetailUiState(
    val loading: Boolean = true,
    val card: ScryfallCard? = null,
    val error: String? = null,
    val edhrecLists: List<EdhrecCardList>? = null,
    val edhrecLoading: Boolean = false,
    val combos: List<Variant> = emptyList(),
    val combosLoading: Boolean = false,
    val tcgPrices: List<TcgPriceResult>? = null,
    val tcgPricesConfigured: Boolean = false
)

class CardDetailViewModel(
    private val cardName: String,
    private val cardRepository: CardRepository,
    private val edhrecRepository: EdhrecRepository,
    private val comboRepository: ComboRepository,
    private val tcgPlayerRepository: TcgPlayerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CardDetailUiState())
    val uiState: StateFlow<CardDetailUiState> = _uiState.asStateFlow()

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

    class Factory(
        private val cardName: String,
        private val settingsRepository: SettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CardDetailViewModel(
                cardName = cardName,
                cardRepository = CardRepository(),
                edhrecRepository = EdhrecRepository(),
                comboRepository = ComboRepository(),
                tcgPlayerRepository = TcgPlayerRepository(settingsRepository)
            ) as T
        }
    }
}
