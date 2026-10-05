package com.mtgcompanion.app.ui.lifecounter

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckRepository
import com.mtgcompanion.app.data.social.Overview
import com.mtgcompanion.app.data.social.Profile
import com.mtgcompanion.app.data.social.SocialRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.mtgcompanion.app.data.activeDecks
import kotlinx.coroutines.flow.map

/**
 * Game night (GameNightScreen): the players, their decks and the pods, kept in [GameNightStore].
 * The web app does the same in src/lifecounter/GameNightPage.tsx.
 */
class GameNightViewModel(
    context: Context,
    private val deckRepository: DeckRepository,
    private val social: SocialRepository,
    lifeCounterSettings: LifeCounterSettingsRepository,
    private val cardRepository: CardRepository = CardRepository()
) : ViewModel() {
    init { GameNightStore.init(context) }

    val nights: StateFlow<SavedGameNights> = GameNightStore.nights
    // Archived decks aren't offered (DeckFolders.kt).
    val decks: StateFlow<List<Deck>> = deckRepository.decksFlow.map { activeDecks(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    /** Friends and the decks they share with the user; null while signed out or not loaded. */
    val overview: StateFlow<Overview?> = social.overview
    /** The life counter's finished games, for each pod's result. */
    val tableGames: StateFlow<List<TableGame>> = lifeCounterSettings.tableGamesFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _suggesting = MutableStateFlow(false)
    /** Whether a deck suggestion is being worked out (its decks' cards looked up). */
    val suggesting: StateFlow<Boolean> = _suggesting.asStateFlow()

    init {
        if (social.overview.value == null) viewModelScope.launch { social.refresh() }
    }

    private val night: GameNight get() = GameNightStore.nights.value.current

    private fun add(player: NightPlayer) = GameNightStore.update { it.copy(players = it.players + player) }

    fun addMe() = add(NightPlayer(GameNightStore.newId(), overview.value?.me?.displayName?.ifBlank { null } ?: "Me", NightPlayerKind.ME))

    fun addFriend(friend: Profile) =
        add(NightPlayer(GameNightStore.newId(), friend.displayName.ifBlank { friend.username }, NightPlayerKind.FRIEND, userId = friend.userId))

    fun addGuest(name: String) {
        if (name.isBlank()) return
        add(NightPlayer(GameNightStore.newId(), name.trim(), NightPlayerKind.GUEST))
    }

    fun setPlayer(id: String, transform: (NightPlayer) -> NightPlayer) =
        GameNightStore.update { n -> n.copy(players = n.players.map { if (it.id == id) transform(it) else it }) }

    fun removePlayer(id: String) = GameNightStore.update { withoutPlayer(it, id) }

    /** The user's deck, its commander and bracket (estimated once its cards are looked up). */
    fun pickDeck(playerId: String, deck: Deck?) {
        if (deck == null) {
            setPlayer(playerId) { it.copy(deckId = null, deck = null, commander = null, bracket = null) }
            return
        }
        val commander = listOfNotNull(deck.commander?.name, deck.partnerCommander?.name).joinToString(" & ").ifBlank { null }
        setPlayer(playerId) { it.copy(deckId = deck.id, deck = deck.name, commander = commander, bracket = DeckBrackets.known(deck)) }
        viewModelScope.launch {
            val bracket = DeckBrackets.estimate(deck, cardRepository) ?: return@launch
            setPlayer(playerId) { if (it.deckId == deck.id) it.copy(bracket = bracket) else it }
        }
    }

    /** A deck close in power to everyone else's, played longest ago — another one each time it's asked. */
    fun suggest(playerId: String) {
        if (_suggesting.value) return
        _suggesting.value = true
        viewModelScope.launch {
            try {
                val all = decks.value
                val fitting = all.filter { deckFitsFormat(it.gameMode, night.format) }
                // Only the decks that could be picked are looked up (once each).
                val choices = fitting.ifEmpty { all }.map { d ->
                    val bracket = DeckBrackets.known(d) ?: if (night.players.size > 1) DeckBrackets.estimate(d, cardRepository) else null
                    DeckChoice(d.id, d.name, d.gameMode, bracket, d.gameResults.maxOfOrNull { it.playedAt } ?: 0L)
                }
                val player = night.players.firstOrNull { it.id == playerId } ?: return@launch
                val others = night.players.filter { it.id != playerId }.map { it.bracket }
                val next = nextSuggestion(suggestedDecks(choices, night.format, others), player.deckId) ?: return@launch
                pickDeck(playerId, all.firstOrNull { it.id == next.id })
            } finally {
                _suggesting.value = false
            }
        }
    }

    fun setFormat(format: NightFormat) = GameNightStore.update { it.copy(format = format, pods = emptyList()) }

    /** Fair pods, afresh (a reshuffle too). */
    fun makePods() = GameNightStore.update { withPods(it, newSeed(), GameNightStore.nights.value.previous) }

    fun move(playerId: String, toIndex: Int) =
        GameNightStore.update { it.copy(pods = movePlayer(it.pods, playerId, toIndex, "${it.seed}-${System.currentTimeMillis()}")) }

    /** Marks [pod] started and leaves it for the life counter to seat; the screen then opens the life counter. */
    fun start(pod: NightPod) {
        GameNightStore.update { n -> n.copy(pods = n.pods.map { if (it.id == pod.id) it.copy(startedAt = System.currentTimeMillis(), winnerId = null) else it }) }
        LifeCounterSeed.pending.value = tableSeedOf(pod, night.players)
    }

    /** A winner tapped (again: taken back). The user's game goes onto their deck. */
    fun tapWinner(pod: NightPod, winnerId: String) {
        val next = if (pod.winnerId == winnerId) null else winnerId
        GameNightStore.update { n -> n.copy(pods = n.pods.map { if (it.id == pod.id) it.copy(winnerId = next) else it }) }
        val (deckId, result) = nightResultOf(night.id, pod, night.players, next.orEmpty(), System.currentTimeMillis()) ?: return
        viewModelScope.launch {
            deckRepository.removeGameResult(deckId, result.id)
            if (next != null) deckRepository.addGameResult(deckId, result)
        }
    }

    fun startNewNight() = GameNightStore.startNewNight()

    class Factory(
        private val context: Context,
        private val deckRepository: DeckRepository,
        private val social: SocialRepository,
        private val lifeCounterSettings: LifeCounterSettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            GameNightViewModel(context.applicationContext, deckRepository, social, lifeCounterSettings) as T
    }
}
