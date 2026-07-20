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
import com.mtgcompanion.app.data.GRID_COLUMNS_DEFAULT
import com.mtgcompanion.app.data.SettingsRepository
import com.mtgcompanion.app.data.isOffline
import com.mtgcompanion.app.data.offline.OfflineCardRepository
import com.mtgcompanion.app.data.TcgPlayerRepository
import com.mtgcompanion.app.network.edhrec.EdhrecCardList
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.scryfall.ScryfallIdentifier
import com.mtgcompanion.app.network.spellbook.Variant
import com.mtgcompanion.app.network.tcgplayer.TcgPriceResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How many EDHREC tiles each section shows — also what gets resolved for prices. */
internal const val TILES_PER_SECTION = 12

data class CardDetailUiState(
    val loading: Boolean = true,
    val card: ScryfallCard? = null,
    val error: String? = null,
    val edhrecLists: List<EdhrecCardList>? = null,
    val edhrecLoading: Boolean = false,
    val cardEdhrecLists: List<EdhrecCardList>? = null,
    val cardEdhrecLoading: Boolean = false,
    /** Suggested cards resolved on Scryfall, keyed by lowercase name (and front face), for prices. */
    val suggestionCards: Map<String, ScryfallCard> = emptyMap(),
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
    private val deckRepository: DeckRepository,
    private val offlineRepository: OfflineCardRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CardDetailUiState())
    val uiState: StateFlow<CardDetailUiState> = _uiState.asStateFlow()

    /** Column count for this page's suggestion grid — the same shared count used by every grid tab. */
    val gridColumns: StateFlow<Int> = settingsRepository.gridColumns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GRID_COLUMNS_DEFAULT)

    val decks: StateFlow<List<Deck>> = deckRepository.decksFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val collections: StateFlow<List<Collection>> = collectionRepository.collectionsFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    /**
     * Copies owned of each card name, across every binder and deck — shown on the enlarged card.
     * Counted under the front face as well as the full name, since a double-faced card is
     * "Tony Stark // The Invincible Iron Man" here but just "Tony Stark" to EDHREC.
     */
    val ownedByName: StateFlow<Map<String, Int>> = combine(
        collectionRepository.collectionsFlow,
        deckRepository.decksFlow
    ) { colls, decks ->
        buildMap<String, Int> {
            fun count(name: String, quantity: Int) {
                val full = name.lowercase()
                merge(full, quantity) { a, b -> a + b }
                val front = full.substringBefore(" // ")
                if (front != full) merge(front, quantity) { a, b -> a + b }
            }
            colls.forEach { collection ->
                collection.entries.forEach { entry -> count(entry.name, entry.quantity + entry.foilQuantity) }
            }
            decks.forEach { deck ->
                deck.cards.forEach { entry -> count(entry.name, entry.quantity) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

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
                // Offline: fall back to the locally downloaded card database if it's there.
                val offlineCard = if (isOffline(e)) offlineRepository.getByName(cardName) else null
                if (offlineCard != null) {
                    _uiState.value = _uiState.value.copy(loading = false, card = offlineCard)
                    loadPrints()
                } else {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = if (isOffline(e)) {
                            "You're offline and this card isn't in your downloaded database."
                        } else {
                            e.message ?: "Couldn't load this card from Scryfall."
                        }
                    )
                }
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
            lists?.let { loadSuggestionCards(it) }
        }
    }

    /**
     * Resolve the suggested cards on Scryfall so the enlarged card can show a price and be added to
     * a deck/binder. EDHREC only gives us names, so batch them through /cards/collection rather than
     * looking each one up (which would hit the rate limit).
     */
    private fun loadSuggestionCards(lists: List<EdhrecCardList>) {
        viewModelScope.launch {
            val names = lists
                .flatMap { it.cardviews.take(TILES_PER_SECTION) }
                .map { it.name }
                .distinct()
            if (names.isEmpty()) return@launch
            val cards = try {
                names.chunked(75).flatMap { chunk ->
                    cardRepository.getCollection(chunk.map { ScryfallIdentifier(name = it) }).data
                }
            } catch (e: Exception) {
                emptyList()
            }
            // Key on the front face too: EDHREC names a double-faced card by either half.
            val byName = buildMap {
                cards.forEach { card ->
                    val full = card.name.lowercase()
                    put(full, card)
                    put(full.substringBefore(" // "), card)
                }
            }
            _uiState.value = _uiState.value.copy(suggestionCards = byName)
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

    // [card] is passed in rather than read from the state: the enlarged card can add a suggested
    // card, which isn't the one the page is about.
    fun addToCollection(collectionId: String, card: ScryfallCard) {
        viewModelScope.launch {
            collectionRepository.addCard(collectionId, card)
            _uiState.value = _uiState.value.copy(addedToCollectionMessage = "Added ${card.name} to binder.")
        }
    }

    fun createCollectionAndAdd(name: String, card: ScryfallCard) {
        viewModelScope.launch {
            val collection = collectionRepository.createCollection(name)
            collectionRepository.addCard(collection.id, card)
            _uiState.value = _uiState.value.copy(
                addedToCollectionMessage = "Added ${card.name} to \"${collection.name}\"."
            )
        }
    }

    fun addToDeck(deckId: String, card: ScryfallCard) {
        viewModelScope.launch {
            deckRepository.addCardToDeck(deckId, card)
            _uiState.value = _uiState.value.copy(addedToDeckMessage = "Added ${card.name} to deck.")
        }
    }

    fun createDeckAndAdd(name: String, card: ScryfallCard) {
        viewModelScope.launch {
            val deck = deckRepository.createDeck(name)
            deckRepository.addCardToDeck(deck.id, card)
            _uiState.value = _uiState.value.copy(addedToDeckMessage = "Added ${card.name} to \"${deck.name}\".")
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(addedToCollectionMessage = null, addedToDeckMessage = null)
    }

    class Factory(
        private val cardName: String,
        private val settingsRepository: SettingsRepository,
        private val collectionRepository: CollectionRepository,
        private val deckRepository: DeckRepository,
        private val offlineRepository: OfflineCardRepository
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
                deckRepository = deckRepository,
                offlineRepository = offlineRepository,
                settingsRepository = settingsRepository
            ) as T
        }
    }
}
