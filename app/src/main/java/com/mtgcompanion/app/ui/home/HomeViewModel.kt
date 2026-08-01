package com.mtgcompanion.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.CollectionRepository
import com.mtgcompanion.app.data.CollectionType
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckRepository
import com.mtgcompanion.app.data.DriveSyncManager
import com.mtgcompanion.app.data.NewsItem
import com.mtgcompanion.app.data.NewsRepository
import com.mtgcompanion.app.data.SettingsRepository
import com.mtgcompanion.app.data.offline.OfflineCardRepository
import com.mtgcompanion.app.ui.collection.computeDashboard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Home's random-card spotlight, re-rolled once per calendar day. */
data class CardOfDay(val name: String, val imageUrl: String?)

/** Wins/losses/draws logged across every deck, for Home's match-record summary. */
data class MatchSummary(val wins: Int = 0, val losses: Int = 0, val draws: Int = 0) {
    val total: Int get() = wins + losses + draws
}

/** Small at-a-glance counts and highlights for the Home screen. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    deckRepository: DeckRepository,
    collectionRepository: CollectionRepository,
    private val cardRepository: CardRepository,
    private val settingsRepository: SettingsRepository,
    offlineCardRepository: OfflineCardRepository,
    driveSyncManager: DriveSyncManager,
    private val newsRepository: NewsRepository = NewsRepository()
) : ViewModel() {

    private val decks: StateFlow<List<Deck>> = deckRepository.decksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deckCount: StateFlow<Int> = decks.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val binderCount: StateFlow<Int> = collectionRepository.collectionsFlow
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Total value of OWNED binders only (wishlist binders don't count toward this). */
    val collectionValue: StateFlow<Double?> = collectionRepository.collectionsFlow.mapLatest { collections ->
        val quantities = collections.filter { it.kind == CollectionType.OWNED }
            .flatMap { it.entries }
            .groupBy { it.scryfallId }
            .map { (id, entries) -> id to entries.sumOf { it.quantity + it.foilQuantity } }
        computeDashboard(cardRepository, quantities)?.totalUsd
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** The deck a user most recently opened, so Home can offer to jump straight back in. */
    val lastOpenedDeck: StateFlow<Deck?> = combine(decks, settingsRepository.lastOpenedDeckId) { list, id ->
        list.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val matchSummary: StateFlow<MatchSummary> = decks.map { list ->
        val results = list.flatMap { it.gameResults }
        MatchSummary(
            wins = results.count { it.result == "WIN" },
            losses = results.count { it.result == "LOSS" },
            draws = results.count { it.result == "DRAW" }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MatchSummary())

    /** A stale offline database or a Drive sync problem, surfaced proactively; null when all's well. */
    val alert: StateFlow<String?> = combine(offlineCardRepository.status, driveSyncManager.status) { offline, sync ->
        val staleDays = if (offline.hasData) TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - offline.updatedAt) else -1
        when {
            sync.connectedEmail != null && !sync.syncing && sync.message != null && sync.message != "Synced" ->
                "Drive sync: ${sync.message}"
            offline.hasData && staleDays >= 30 ->
                "Offline card database is $staleDays days old — update it in Settings."
            else -> null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _cardOfDay = MutableStateFlow<CardOfDay?>(null)
    val cardOfDay: StateFlow<CardOfDay?> = _cardOfDay

    private val _news = MutableStateFlow<List<NewsItem>>(emptyList())
    val news: StateFlow<List<NewsItem>> = _news

    init {
        viewModelScope.launch { loadCardOfDay() }
        viewModelScope.launch { _news.value = newsRepository.fetchLatest() }
    }

    private suspend fun loadCardOfDay() {
        val today = LocalDate.now().toString()
        if (settingsRepository.cardOfDayDate.first() == today) {
            val name = settingsRepository.cardOfDayName.first()
            if (name != null) {
                _cardOfDay.value = CardOfDay(name, settingsRepository.cardOfDayImageUrl.first())
                return
            }
        }
        try {
            val card = cardRepository.getRandom()
            settingsRepository.setCardOfDay(today, card.name, card.displayImageUrl)
            _cardOfDay.value = CardOfDay(card.name, card.displayImageUrl)
        } catch (e: Exception) {
            // Offline or Scryfall unreachable — Home just skips the spotlight for today.
        }
    }

    class Factory(
        private val deckRepository: DeckRepository,
        private val collectionRepository: CollectionRepository,
        private val settingsRepository: SettingsRepository,
        private val offlineCardRepository: OfflineCardRepository,
        private val driveSyncManager: DriveSyncManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(
                deckRepository, collectionRepository, CardRepository(),
                settingsRepository, offlineCardRepository, driveSyncManager
            ) as T
    }
}
