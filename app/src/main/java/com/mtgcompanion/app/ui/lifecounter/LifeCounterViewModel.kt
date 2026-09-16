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

/** Per-player counters beyond life. [resetsEachTurn] ones are cleared when the turn passes. */
enum class PlayerCounter(val label: String, val resetsEachTurn: Boolean = false) {
    POISON("Poison"),
    EXPERIENCE("Experience"),
    ENERGY("Energy"),
    CHARGE("Charge"),
    STORM("Storm", resetsEachTurn = true)
}

/** Mana pool colors, in canonical WUBRG order with colorless last — keys match Scryfall symbol codes. */
val ManaPoolColors = listOf("W", "U", "B", "R", "G", "C")

/**
 * One commander a player can take damage from. [slot] 0 is the opponent's commander, 1 their
 * partner. Partners are separate commanders under the rules — 21 damage from EITHER one is lethal,
 * but 11 from each is not — so they need separate tallies rather than one per opponent.
 */
data class CommanderSource(val opponentId: Int, val slot: Int = 0)

/** Why a player is out, which also picks which list their defeat message is drawn from. */
enum class LossReason { LIFE, POISON, COMMANDER_DAMAGE, KILLED }

/**
 * [commanderDamage] maps each commander that has hit this player to the damage it has dealt.
 * [colorIndex] indexes into the screen's player color palette — kept as a plain index here rather
 * than a Compose Color so this model has no UI-layer dependency. [backgroundImageUri] is a
 * content:// URI string from the system photo picker, session-only (not persisted).
 * [commanderTax] is per commander: index 0 the commander, 1 the partner. A null
 * [victoryMessage]/[defeatMessage] means "use the table-wide message lists from settings".
 */
data class PlayerLife(
    val id: Int,
    val life: Int,
    val commanderDamage: Map<CommanderSource, Int> = emptyMap(),
    val counters: Map<PlayerCounter, Int> = emptyMap(),
    val manaPool: Map<String, Int> = emptyMap(),
    val hasPartner: Boolean = false,
    val commanderTax: List<Int> = listOf(0, 0),
    val killed: Boolean = false,
    val colorIndex: Int = (id - 1),
    val backgroundImageUri: String? = null,
    val name: String? = null,
    val victoryMessage: String? = null,
    val defeatMessage: String? = null
) {
    fun counter(kind: PlayerCounter): Int = counters[kind] ?: 0

    val displayName: String get() = name ?: "Player $id"

    /** Null while still in the game. With [autoKill] off, only a manual Kill takes a player out. */
    fun lossReason(autoKill: Boolean): LossReason? = when {
        killed -> LossReason.KILLED
        !autoKill -> null
        commanderDamage.values.any { it >= 21 } -> LossReason.COMMANDER_DAMAGE
        counter(PlayerCounter.POISON) >= 10 -> LossReason.POISON
        life <= 0 -> LossReason.LIFE
        else -> null
    }

    fun isDefeated(autoKill: Boolean): Boolean = lossReason(autoKill) != null
}

/**
 * The one player left standing, once someone has actually been knocked out — a fresh game with
 * nobody defeated has no winner.
 */
fun winnerIdOf(players: List<PlayerLife>, autoKill: Boolean): Int? {
    if (players.size < 2) return null
    val alive = players.filterNot { it.isDefeated(autoKill) }
    return if (alive.size == 1) alive.first().id else null
}

enum class GameModeKind { NONE, PLANECHASE, ARCHENEMY, BOUNTY }
enum class PlanarDieFace { BLANK, CHAOS, PLANESWALK }
enum class DayNight { DAY, NIGHT }

data class GameModeState(
    val mode: GameModeKind = GameModeKind.NONE,
    val loading: Boolean = false,
    val planeDeck: List<ScryfallCard> = emptyList(),
    val currentPlane: ScryfallCard? = null,
    val schemeDeck: List<ScryfallCard> = emptyList(),
    val currentScheme: ScryfallCard? = null,
    val ongoingSchemes: List<ScryfallCard> = emptyList(),
    val archenemyPlayerId: Int? = null,
    val bountyDeck: List<ScryfallCard> = emptyList(),
    val currentBounty: ScryfallCard? = null,
    /** How to play Bounty, from the "Wanted!" back face every bounty card shares. */
    val bountyRules: String? = null
)

/** What a game-history entry records. Numeric events carry a before/after on the entry itself. */
sealed interface HistoryEvent {
    data object Life : HistoryEvent
    data class CommanderDamage(val source: CommanderSource) : HistoryEvent
    data class Counter(val kind: PlayerCounter) : HistoryEvent
    data class Mana(val color: String) : HistoryEvent
    data class CommanderTax(val slot: Int) : HistoryEvent
    data object BecameMonarch : HistoryEvent
    data object TookInitiative : HistoryEvent
    data object Killed : HistoryEvent
    data object Revived : HistoryEvent
    data object TurnStarted : HistoryEvent
    data object WonHighRoll : HistoryEvent
    data class BecameDayOrNight(val state: DayNight) : HistoryEvent
}

/**
 * One line of the game log. Player names are resolved when the log is displayed, not stored, so a
 * rename mid-game relabels its earlier entries too. [from]/[to] are null for non-numeric events.
 */
data class HistoryEntry(
    val id: Long,
    val event: HistoryEvent,
    val playerId: Int?,
    val from: Int?,
    val to: Int?,
    val turn: Int,
    val matchSeconds: Int,
    val atMillis: Long
)

/** Every player's d20 rolls, in order — more than one roll means they were in a tie for highest. */
data class HighRollResult(val rolls: Map<Int, List<Int>>, val winnerId: Int)

/**
 * Session-only multiplayer life tracker — no persistence for game state (life totals only matter
 * for the game currently being played); held in a ViewModel so a config change or a trip into card
 * search mid-game doesn't reset everyone. Saved player profiles and [LifeCounterSettings] are what
 * IS persisted, since they're meant to be reused game after game.
 */
class LifeCounterViewModel(
    private val cardRepository: CardRepository = CardRepository(),
    private val profileRepository: PlayerProfileRepository,
    private val settingsRepository: LifeCounterSettingsRepository
) : ViewModel() {
    private val _settings = MutableStateFlow(LifeCounterSettings())
    val settings: StateFlow<LifeCounterSettings> = _settings.asStateFlow()

    /** False until stored settings have loaded, so the first game uses the saved layout, not the default. */
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _players = MutableStateFlow(defaultPlayers(TableLayouts.byId(TableLayouts.DEFAULT_ID).playerCount, 40))
    val players: StateFlow<List<PlayerLife>> = _players.asStateFlow()

    /** Bumped every new game, so per-game random picks (e.g. defeat messages) re-roll between games only. */
    private val _gameNumber = MutableStateFlow(0)
    val gameNumber: StateFlow<Int> = _gameNumber.asStateFlow()

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

    /** The Monarch and the Initiative are each held by at most one player at a time. */
    private val _monarchPlayerId = MutableStateFlow<Int?>(null)
    val monarchPlayerId: StateFlow<Int?> = _monarchPlayerId.asStateFlow()
    private val _initiativePlayerId = MutableStateFlow<Int?>(null)
    val initiativePlayerId: StateFlow<Int?> = _initiativePlayerId.asStateFlow()

    /** Null while nothing in the game has made it day or night yet. */
    private val _dayNight = MutableStateFlow<DayNight?>(null)
    val dayNight: StateFlow<DayNight?> = _dayNight.asStateFlow()

    /** Set when a new game starts with "high roll at game start" on; the screen shows the roll then clears it. */
    private val _highRollRequested = MutableStateFlow(false)
    val highRollRequested: StateFlow<Boolean> = _highRollRequested.asStateFlow()

    /** Newest last. */
    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()
    private var nextHistoryId = 0L

    private val _gameMode = MutableStateFlow(GameModeState())
    val gameMode: StateFlow<GameModeState> = _gameMode.asStateFlow()

    val profiles: StateFlow<List<PlayerProfile>> = profileRepository.profilesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Settings writes still in flight. While any are pending, stored values arriving from disk are
     * older than what's already in memory, and applying them would briefly flip a just-tapped
     * switch back.
     */
    private var pendingSettingsWrites = 0

    init {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { stored ->
                if (pendingSettingsWrites == 0) _settings.value = stored
                if (!_ready.value) {
                    newGame()
                    _ready.value = true
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(1000)
                if (_timerRunning.value && _settings.value.gameTimerEnabled) {
                    _matchSeconds.value += 1
                    _turnSeconds.value += 1
                }
            }
        }
    }

    // ---- Settings & new games ----

    /** Applied immediately in memory so the UI doesn't wait on disk, then persisted. */
    fun updateSettings(transform: (LifeCounterSettings) -> LifeCounterSettings) {
        _settings.value = transform(_settings.value)
        pendingSettingsWrites++
        viewModelScope.launch {
            try {
                settingsRepository.update(transform)
            } finally {
                pendingSettingsWrites--
            }
        }
    }

    /** Changing the seating starts a fresh game, since the number of players may change. */
    fun selectLayout(layoutId: String) {
        updateSettings { it.copy(layoutId = layoutId) }
        newGame()
    }

    /**
     * Starting life takes effect straight away on a game nobody has touched yet; mid-game it waits
     * for the next restart instead of silently resetting everyone's life.
     */
    fun setStartingLife(twoPlayer: Boolean, life: Int) {
        updateSettings { if (twoPlayer) it.copy(twoPlayerStartingLife = life) else it.copy(multiplayerStartingLife = life) }
        if (_history.value.isEmpty()) newGame()
    }

    fun newGame() {
        val settings = _settings.value
        val count = TableLayouts.byId(settings.layoutId).playerCount
        val life = settings.startingLifeFor(count)
        val colors = if (settings.shuffleColors) List(PLAYER_COLOR_COUNT) { it }.shuffled() else List(PLAYER_COLOR_COUNT) { it }
        _players.value = (1..count).map { id -> PlayerLife(id = id, life = life, colorIndex = colors[(id - 1) % colors.size]) }
        _gameNumber.value += 1
        _currentTurnPlayerId.value = 1
        _turnNumber.value = 1
        _turnSeconds.value = 0
        _matchSeconds.value = 0
        _timerRunning.value = true
        _monarchPlayerId.value = null
        _initiativePlayerId.value = null
        _dayNight.value = null
        _history.value = emptyList()
        _highRollRequested.value = settings.highRollAtStart && count > 1
    }

    fun consumeHighRollRequest() {
        _highRollRequested.value = false
    }

    fun markTipsSeen() = updateSettings { it.copy(tipsSeen = true) }

    /** Clears every player's background image and restores the default seat colors. */
    fun resetPlayerBackgrounds() {
        _players.value = _players.value.map { it.copy(backgroundImageUri = null, colorIndex = (it.id - 1) % PLAYER_COLOR_COUNT) }
    }

    // ---- Life & player details ----

    fun adjust(playerId: Int, delta: Int) {
        val player = player(playerId) ?: return
        updatePlayer(playerId) { it.copy(life = it.life + delta) }
        log(HistoryEvent.Life, playerId, player.life, player.life + delta)
    }

    /** Set a player's life to an exact value, e.g. from the numeric keypad. */
    fun setLife(playerId: Int, value: Int) {
        val player = player(playerId) ?: return
        updatePlayer(playerId) { it.copy(life = value) }
        log(HistoryEvent.Life, playerId, player.life, value)
    }

    fun setPlayerColor(playerId: Int, colorIndex: Int) = updatePlayer(playerId) { it.copy(colorIndex = colorIndex) }

    fun setPlayerName(playerId: Int, name: String) = updatePlayer(playerId) { it.copy(name = name.ifBlank { null }) }

    fun setBackgroundImage(playerId: Int, uri: String?) = updatePlayer(playerId) { it.copy(backgroundImageUri = uri) }

    fun setVictoryMessage(playerId: Int, message: String) =
        updatePlayer(playerId) { it.copy(victoryMessage = message.ifBlank { null }) }

    fun setDefeatMessage(playerId: Int, message: String) =
        updatePlayer(playerId) { it.copy(defeatMessage = message.ifBlank { null }) }

    /**
     * Commander damage is tracked ALONGSIDE normal life loss by default — taking N combat damage
     * from an opponent's commander costs N life same as any other damage, it just also accumulates
     * toward that commander's separate 21-damage kill condition. The "commander damage costs life"
     * setting turns the life half off for tables that track them separately. [delta] is clamped so
     * the counter can't go below 0; the life adjustment only reflects the amount actually applied.
     */
    fun adjustCommanderDamage(playerId: Int, source: CommanderSource, delta: Int) {
        val player = player(playerId) ?: return
        val current = player.commanderDamage[source] ?: 0
        val updated = (current + delta).coerceAtLeast(0)
        val applied = updated - current
        if (applied == 0) return
        val lifeLoss = if (_settings.value.commanderDamageCostsLife) applied else 0
        updatePlayer(playerId) {
            it.copy(life = it.life - lifeLoss, commanderDamage = it.commanderDamage + (source to updated))
        }
        log(HistoryEvent.CommanderDamage(source), playerId, current, updated)
    }

    fun adjustCounter(playerId: Int, kind: PlayerCounter, delta: Int) {
        val player = player(playerId) ?: return
        val current = player.counter(kind)
        val updated = (current + delta).coerceAtLeast(0)
        if (updated == current) return
        updatePlayer(playerId) { it.copy(counters = it.counters + (kind to updated)) }
        log(HistoryEvent.Counter(kind), playerId, current, updated)
    }

    fun adjustMana(playerId: Int, color: String, delta: Int) {
        val player = player(playerId) ?: return
        val current = player.manaPool[color] ?: 0
        val updated = (current + delta).coerceAtLeast(0)
        if (updated == current) return
        updatePlayer(playerId) { it.copy(manaPool = it.manaPool + (color to updated)) }
        log(HistoryEvent.Mana(color), playerId, current, updated)
    }

    /** Commander tax rises in increments of 2 (colorless mana) each time that commander is recast. */
    fun adjustCommanderTax(playerId: Int, slot: Int, delta: Int) {
        val player = player(playerId) ?: return
        val current = player.commanderTax.getOrElse(slot) { 0 }
        val updated = (current + delta).coerceAtLeast(0)
        if (updated == current) return
        updatePlayer(playerId) {
            it.copy(commanderTax = it.commanderTax.toMutableList().also { tax -> tax[slot] = updated })
        }
        log(HistoryEvent.CommanderTax(slot), playerId, current, updated)
    }

    /**
     * Turning a partner off drops everything tracked for that second commander — the damage it
     * dealt to every other player and its own tax — rather than leaving a hidden tally that could
     * still count as lethal. The life those hits cost stays lost: it was real damage.
     */
    fun setHasPartner(playerId: Int, enabled: Boolean) {
        if (enabled) {
            updatePlayer(playerId) { it.copy(hasPartner = true) }
            return
        }
        val partner = CommanderSource(playerId, slot = 1)
        _players.value = _players.value.map { player ->
            when (player.id) {
                playerId -> player.copy(hasPartner = false, commanderTax = listOf(player.commanderTax.getOrElse(0) { 0 }, 0))
                else -> player.copy(commanderDamage = player.commanderDamage - partner)
            }
        }
    }

    /** Takes a player out regardless of their totals — concessions, alternate win conditions, etc. */
    fun kill(playerId: Int) {
        if (player(playerId)?.killed != false) return
        updatePlayer(playerId) { it.copy(killed = true) }
        log(HistoryEvent.Killed, playerId, null, null)
    }

    /**
     * Brings a defeated player back into the game: every loss condition is cleared — a manual kill
     * undone, life back to the starting total, poison removed, commander damage taken wiped. Name,
     * color, commander tax and their other counters are kept, since those describe the player
     * rather than their defeat.
     */
    fun revive(playerId: Int) {
        val player = player(playerId) ?: return
        val life = _settings.value.startingLifeFor(_players.value.size)
        updatePlayer(playerId) {
            it.copy(
                killed = false,
                life = life,
                counters = it.counters - PlayerCounter.POISON,
                commanderDamage = emptyMap()
            )
        }
        log(HistoryEvent.Revived, playerId, player.life, life)
    }

    // ---- Table-wide designations ----

    /** Null clears it. Handing it to the current holder is a no-op. */
    fun setMonarch(playerId: Int?) {
        if (_monarchPlayerId.value == playerId) return
        _monarchPlayerId.value = playerId
        if (playerId != null) log(HistoryEvent.BecameMonarch, playerId, null, null)
    }

    fun setInitiative(playerId: Int?) {
        if (_initiativePlayerId.value == playerId) return
        _initiativePlayerId.value = playerId
        if (playerId != null) log(HistoryEvent.TookInitiative, playerId, null, null)
    }

    /** It always becomes day first. */
    fun startDayNight() {
        if (_dayNight.value != null) return
        _dayNight.value = DayNight.DAY
        log(HistoryEvent.BecameDayOrNight(DayNight.DAY), null, null, null)
    }

    fun toggleDayNight() {
        val next = if (_dayNight.value == DayNight.DAY) DayNight.NIGHT else DayNight.DAY
        _dayNight.value = next
        log(HistoryEvent.BecameDayOrNight(next), null, null, null)
    }

    fun stopDayNight() {
        _dayNight.value = null
    }

    // ---- Turn tracker + match timer ----

    /** Passing the turn also empties every mana pool and resets storm counts, which don't carry over. */
    fun nextTurn() {
        val ids = _players.value.map { it.id }
        if (ids.isEmpty()) return
        val idx = ids.indexOf(_currentTurnPlayerId.value)
        _currentTurnPlayerId.value = if (idx == -1 || idx == ids.lastIndex) ids.first() else ids[idx + 1]
        _turnNumber.value += 1
        _turnSeconds.value = 0
        val perTurn = PlayerCounter.entries.filter { it.resetsEachTurn }.toSet()
        _players.value = _players.value.map { it.copy(manaPool = emptyMap(), counters = it.counters - perTurn) }
        log(HistoryEvent.TurnStarted, _currentTurnPlayerId.value, null, null)
    }

    fun toggleTimer() {
        _timerRunning.value = !_timerRunning.value
    }

    /** Rolls a d20 for every player, rerolling only those tied for highest until one remains. */
    fun rollHighRoll(): HighRollResult = highRoll(_players.value.map { it.id })

    /** Starts the clock and turn count over with [playerId] going first (the high-roll winner). */
    fun setFirstPlayer(playerId: Int) {
        _currentTurnPlayerId.value = playerId
        _turnNumber.value = 1
        _turnSeconds.value = 0
        _matchSeconds.value = 0
        _timerRunning.value = true
        log(HistoryEvent.WonHighRoll, playerId, null, null)
    }

    // ---- Game history ----

    /**
     * Appends to the game log. Consecutive changes of the same kind to the same player within a few
     * seconds merge into one entry, so tapping +1 five times reads as "40 → 45", not five lines —
     * and a change that nets back to where it started disappears from the log entirely.
     */
    private fun log(event: HistoryEvent, playerId: Int?, from: Int?, to: Int?) {
        val now = System.currentTimeMillis()
        val entries = _history.value
        val last = entries.lastOrNull()
        val mergeable = last != null && from != null && to != null && last.from != null &&
            last.event == event && last.playerId == playerId && last.turn == _turnNumber.value &&
            now - last.atMillis <= MERGE_WINDOW_MILLIS
        _history.value = if (mergeable) {
            val merged = last!!.copy(to = to, atMillis = now)
            if (merged.from == merged.to) entries.dropLast(1) else entries.dropLast(1) + merged
        } else {
            val entry = HistoryEntry(
                id = nextHistoryId++,
                event = event,
                playerId = playerId,
                from = from,
                to = to,
                turn = _turnNumber.value,
                matchSeconds = _matchSeconds.value,
                atMillis = now
            )
            (entries + entry).takeLast(MAX_HISTORY)
        }
    }

    // ---- Saved profiles ----

    fun saveProfile(playerId: Int) {
        val player = player(playerId) ?: return
        val name = player.name?.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch { profileRepository.saveProfile(PlayerProfile(name, player.colorIndex)) }
    }

    fun loadProfile(playerId: Int, profile: PlayerProfile) =
        updatePlayer(playerId) { it.copy(name = profile.name, colorIndex = profile.colorIndex) }

    fun deleteProfile(name: String) {
        viewModelScope.launch { profileRepository.deleteProfile(name) }
    }

    // ---- Card search ----

    suspend fun searchCards(query: String): List<ScryfallCard> =
        if (query.isBlank()) emptyList() else cardRepository.search(query).cards

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

    // ---- Bounty ----

    /**
     * Bounty cards (Outlaws of Thunder Junction Commander) aren't a searchable card type — Scryfall
     * files them as double-faced token-set extras named "Bounty: <outlaw> // Wanted!", so they're
     * found by set and name instead. The front face is the bounty; the shared back face is the rules.
     */
    fun startBounty() {
        _gameMode.value = GameModeState(mode = GameModeKind.BOUNTY, loading = true)
        viewModelScope.launch {
            val cards = try {
                cardRepository.search("set:totc name:\"Bounty:\" include:extras").cards
                    .filter { it.name.startsWith("Bounty:") }
            } catch (e: Exception) {
                emptyList()
            }
            _gameMode.value = GameModeState(
                mode = GameModeKind.BOUNTY,
                bountyDeck = cards.shuffled(),
                bountyRules = cards.firstNotNullOfOrNull { it.cardFaces?.getOrNull(1)?.oracleText }
            )
        }
    }

    /** Claimed bounties go to the bottom of the pile, so the deck never runs dry mid-game. */
    fun revealNextBounty() {
        val state = _gameMode.value
        if (state.mode != GameModeKind.BOUNTY || state.bountyDeck.isEmpty()) return
        _gameMode.value = state.copy(
            currentBounty = state.bountyDeck.first(),
            bountyDeck = state.bountyDeck.drop(1) + listOfNotNull(state.currentBounty)
        )
    }

    fun stopGameMode() {
        _gameMode.value = GameModeState()
    }

    private fun player(id: Int): PlayerLife? = _players.value.firstOrNull { it.id == id }

    private inline fun updatePlayer(id: Int, transform: (PlayerLife) -> PlayerLife) {
        _players.value = _players.value.map { if (it.id == id) transform(it) else it }
    }

    companion object {
        /** Must match the size of the screen's player palette. */
        const val PLAYER_COLOR_COUNT = 10
        private const val MERGE_WINDOW_MILLIS = 4_000L
        private const val MAX_HISTORY = 500

        private fun defaultPlayers(count: Int, life: Int) =
            (1..count).map { PlayerLife(id = it, life = life, colorIndex = (it - 1) % PLAYER_COLOR_COUNT) }

        /** Pure so it can be reasoned about independently of the ViewModel's state. */
        fun highRoll(playerIds: List<Int>, random: Random = Random): HighRollResult {
            require(playerIds.isNotEmpty()) { "High roll needs at least one player" }
            val rolls = playerIds.associateWith { mutableListOf<Int>() }
            var contenders = playerIds
            while (true) {
                contenders.forEach { rolls.getValue(it) += random.nextInt(1, 21) }
                val top = contenders.maxOf { rolls.getValue(it).last() }
                contenders = contenders.filter { rolls.getValue(it).last() == top }
                if (contenders.size == 1) return HighRollResult(rolls, contenders.first())
            }
        }
    }

    class Factory(
        private val profileRepository: PlayerProfileRepository,
        private val settingsRepository: LifeCounterSettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LifeCounterViewModel(profileRepository = profileRepository, settingsRepository = settingsRepository) as T
    }
}
