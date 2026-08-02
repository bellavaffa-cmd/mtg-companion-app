package com.mtgcompanion.app.ui.lifecounter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.PlayerProfile
import com.mtgcompanion.app.data.PlayerProfileRepository
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * [commanderDamage] maps an opponent's player id to the damage their commander has dealt this
 * player. [colorIndex] indexes into the screen's player color palette — kept as a plain index
 * here rather than a Compose Color so this model has no UI-layer dependency. [backgroundImageUri]
 * is a content:// URI string from the system photo picker, session-only (not persisted).
 */
data class PlayerLife(
    val id: Int,
    val life: Int,
    val commanderDamage: Map<Int, Int> = emptyMap(),
    val poison: Int = 0,
    val experience: Int = 0,
    val commanderTax: Int = 0,
    val colorIndex: Int = (id - 1),
    val backgroundImageUri: String? = null,
    val name: String? = null,
    val victoryMessage: String = "Victory!",
    val defeatMessage: String = "Defeated"
) {
    val isDefeated: Boolean get() = life <= 0 || poison >= 10 || commanderDamage.values.any { it >= 21 }
}

enum class GameModeKind { NONE, PLANECHASE, ARCHENEMY }
enum class PlanarDieFace { BLANK, CHAOS, PLANESWALK }

data class GameModeState(
    val mode: GameModeKind = GameModeKind.NONE,
    val loading: Boolean = false,
    val planeDeck: List<ScryfallCard> = emptyList(),
    val currentPlane: ScryfallCard? = null,
    val schemeDeck: List<ScryfallCard> = emptyList(),
    val currentScheme: ScryfallCard? = null,
    val ongoingSchemes: List<ScryfallCard> = emptyList(),
    val archenemyPlayerId: Int? = null
)

/**
 * Session-only multiplayer life tracker — no persistence for game state (life totals only matter
 * for the game currently being played); held in a ViewModel purely so a config-change (rotation)
 * during a long Commander game doesn't reset everyone. Saved player profiles are the one thing
 * that IS persisted, via [profileRepository], since they're meant to be reused game after game.
 */
class LifeCounterViewModel(
    private val cardRepository: CardRepository = CardRepository(),
    private val profileRepository: PlayerProfileRepository
) : ViewModel() {
    private val _startingLife = MutableStateFlow(40)
    val startingLife: StateFlow<Int> = _startingLife.asStateFlow()

    private val _players = MutableStateFlow(defaultPlayers(4, 40))
    val players: StateFlow<List<PlayerLife>> = _players.asStateFlow()

    private val _currentTurnPlayerId = MutableStateFlow(1)
    val currentTurnPlayerId: StateFlow<Int> = _currentTurnPlayerId.asStateFlow()
    private val _turnNumber = MutableStateFlow(1)
    val turnNumber: StateFlow<Int> = _turnNumber.asStateFlow()
    private val _turnSeconds = MutableStateFlow(0)
    val turnSeconds: StateFlow<Int> = _turnSeconds.asStateFlow()
    private val _matchSeconds = MutableStateFlow(0)
    val matchSeconds: StateFlow<Int> = _matchSeconds.asStateFlow()
    private val _timerRunning = MutableStateFlow(true)
    val timerRunning: StateFlow<Boolean> = _timerRunning.asStateFlow()

    private val _gameMode = MutableStateFlow(GameModeState())
    val gameMode: StateFlow<GameModeState> = _gameMode.asStateFlow()

    val profiles: StateFlow<List<PlayerProfile>> = profileRepository.profilesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                if (_timerRunning.value) {
                    _matchSeconds.value += 1
                    _turnSeconds.value += 1
                }
            }
        }
    }

    fun setPlayerCount(count: Int) {
        _players.value = defaultPlayers(count, _startingLife.value)
        resetTurnAndTimer()
    }

    fun setStartingLife(value: Int) {
        _startingLife.value = value
        _players.value = _players.value.map { it.copy(life = value) }
    }

    fun adjust(playerId: Int, delta: Int) {
        _players.value = _players.value.map { if (it.id == playerId) it.copy(life = it.life + delta) else it }
    }

    /** Set a player's life to an exact value, e.g. from the numeric keypad. */
    fun setLife(playerId: Int, value: Int) {
        _players.value = _players.value.map { if (it.id == playerId) it.copy(life = value) else it }
    }

    fun setPlayerColor(playerId: Int, colorIndex: Int) {
        _players.value = _players.value.map { if (it.id == playerId) it.copy(colorIndex = colorIndex) else it }
    }

    fun setPlayerName(playerId: Int, name: String) {
        _players.value = _players.value.map { if (it.id == playerId) it.copy(name = name.ifBlank { null }) else it }
    }

    fun setBackgroundImage(playerId: Int, uri: String?) {
        _players.value = _players.value.map { if (it.id == playerId) it.copy(backgroundImageUri = uri) else it }
    }

    fun setVictoryMessage(playerId: Int, message: String) {
        _players.value = _players.value.map { if (it.id == playerId) it.copy(victoryMessage = message.ifBlank { "Victory!" }) else it }
    }

    fun setDefeatMessage(playerId: Int, message: String) {
        _players.value = _players.value.map { if (it.id == playerId) it.copy(defeatMessage = message.ifBlank { "Defeated" }) else it }
    }

    /**
     * Commander damage is tracked ALONGSIDE normal life loss, not instead of it — taking N combat
     * damage from an opponent's commander costs N life same as any other damage, it just also
     * accumulates toward that opponent's separate 21-damage kill condition. [delta] is clamped so
     * the counter can't go below 0; the life adjustment only reflects the amount actually applied.
     */
    fun adjustCommanderDamage(playerId: Int, fromOpponentId: Int, delta: Int) {
        _players.value = _players.value.map { player ->
            if (player.id != playerId) return@map player
            val current = player.commanderDamage[fromOpponentId] ?: 0
            val updated = (current + delta).coerceAtLeast(0)
            val applied = updated - current
            player.copy(life = player.life - applied, commanderDamage = player.commanderDamage + (fromOpponentId to updated))
        }
    }

    fun adjustPoison(playerId: Int, delta: Int) {
        _players.value = _players.value.map {
            if (it.id == playerId) it.copy(poison = (it.poison + delta).coerceAtLeast(0)) else it
        }
    }

    fun adjustExperience(playerId: Int, delta: Int) {
        _players.value = _players.value.map {
            if (it.id == playerId) it.copy(experience = (it.experience + delta).coerceAtLeast(0)) else it
        }
    }

    /** Commander tax rises in increments of 2 (colorless mana) each time that commander is recast. */
    fun adjustCommanderTax(playerId: Int, delta: Int) {
        _players.value = _players.value.map {
            if (it.id == playerId) it.copy(commanderTax = (it.commanderTax + delta).coerceAtLeast(0)) else it
        }
    }

    // ---- Turn tracker + match timer ----

    fun nextTurn() {
        val ids = _players.value.map { it.id }
        if (ids.isEmpty()) return
        val idx = ids.indexOf(_currentTurnPlayerId.value)
        _currentTurnPlayerId.value = if (idx == -1 || idx == ids.lastIndex) ids.first() else ids[idx + 1]
        _turnNumber.value += 1
        _turnSeconds.value = 0
    }

    fun toggleTimer() {
        _timerRunning.value = !_timerRunning.value
    }

    private fun resetTurnAndTimer() {
        _currentTurnPlayerId.value = _players.value.firstOrNull()?.id ?: 1
        _turnNumber.value = 1
        _turnSeconds.value = 0
        _matchSeconds.value = 0
        _timerRunning.value = true
    }

    // ---- Saved profiles ----

    fun saveProfile(playerId: Int) {
        val player = _players.value.firstOrNull { it.id == playerId } ?: return
        val name = player.name?.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch { profileRepository.saveProfile(PlayerProfile(name, player.colorIndex)) }
    }

    fun loadProfile(playerId: Int, profile: PlayerProfile) {
        _players.value = _players.value.map {
            if (it.id == playerId) it.copy(name = profile.name, colorIndex = profile.colorIndex) else it
        }
    }

    // ---- Planechase ----

    fun startPlanechase() {
        _gameMode.value = GameModeState(mode = GameModeKind.PLANECHASE, loading = true)
        viewModelScope.launch {
            val deck = try {
                cardRepository.search("t:plane or t:phenomenon").cards
                    .filter { it.typeLine?.contains("Plane") == true || it.typeLine?.contains("Phenomenon") == true }
                    .shuffled()
            } catch (e: Exception) {
                emptyList()
            }
            _gameMode.value = GameModeState(
                mode = GameModeKind.PLANECHASE,
                currentPlane = deck.firstOrNull(),
                planeDeck = deck.drop(1)
            )
        }
    }

    /** Move to a new plane, cycling the current one back into the deck. */
    fun planeswalk() {
        val state = _gameMode.value
        if (state.mode != GameModeKind.PLANECHASE || state.planeDeck.isEmpty()) return
        _gameMode.value = state.copy(
            currentPlane = state.planeDeck.first(),
            planeDeck = state.planeDeck.drop(1) + listOfNotNull(state.currentPlane)
        )
    }

    /** A real planar die: 4 blank faces, 1 Chaos symbol, 1 Planeswalk symbol. */
    fun rollPlanarDie(): PlanarDieFace {
        val face = when (Random.nextInt(6)) {
            0 -> PlanarDieFace.CHAOS
            1 -> PlanarDieFace.PLANESWALK
            else -> PlanarDieFace.BLANK
        }
        if (face == PlanarDieFace.PLANESWALK) planeswalk()
        return face
    }

    // ---- Archenemy ----

    fun startArchenemy(archenemyPlayerId: Int) {
        _gameMode.value = GameModeState(mode = GameModeKind.ARCHENEMY, archenemyPlayerId = archenemyPlayerId, loading = true)
        viewModelScope.launch {
            val deck = try {
                cardRepository.search("t:scheme").cards
                    .filter { it.typeLine?.contains("Scheme") == true }
                    .shuffled()
            } catch (e: Exception) {
                emptyList()
            }
            _gameMode.value = GameModeState(
                mode = GameModeKind.ARCHENEMY,
                archenemyPlayerId = archenemyPlayerId,
                schemeDeck = deck
            )
        }
    }

    /** Ongoing schemes stay face up (tracked separately); one-shot schemes are used and discarded. */
    fun revealNextScheme() {
        val state = _gameMode.value
        if (state.mode != GameModeKind.ARCHENEMY || state.schemeDeck.isEmpty()) return
        val next = state.schemeDeck.first()
        val isOngoing = next.typeLine?.contains("Ongoing", ignoreCase = true) == true
        _gameMode.value = state.copy(
            currentScheme = next,
            schemeDeck = state.schemeDeck.drop(1),
            ongoingSchemes = if (isOngoing) state.ongoingSchemes + next else state.ongoingSchemes
        )
    }

    fun stopGameMode() {
        _gameMode.value = GameModeState()
    }

    fun resetAll() {
        _players.value = defaultPlayers(_players.value.size, _startingLife.value)
        resetTurnAndTimer()
    }

    private companion object {
        fun defaultPlayers(count: Int, life: Int) = (1..count).map { PlayerLife(id = it, life = life, colorIndex = it - 1) }
    }

    class Factory(private val profileRepository: PlayerProfileRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LifeCounterViewModel(profileRepository = profileRepository) as T
    }
}
