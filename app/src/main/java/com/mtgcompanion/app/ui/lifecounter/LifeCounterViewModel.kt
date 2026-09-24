package com.mtgcompanion.app.ui.lifecounter

import com.mtgcompanion.app.data.DeckRepository
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.social.Match
import com.mtgcompanion.app.data.social.SocialApi
import com.mtgcompanion.app.data.social.SocialRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.PlayerProfile
import com.mtgcompanion.app.data.PlayerProfileRepository
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID
import kotlin.random.Random

/** Per-player counters beyond life. [resetsEachTurn] ones are cleared when the turn passes. */
enum class PlayerCounter(val label: String, val resetsEachTurn: Boolean = false) {
    POISON("Poison"),
    EXPERIENCE("Experience"),
    ENERGY("Energy"),
    CHARGE("Charge"),
    STORM("Storm", resetsEachTurn = true),
    TOKENS("Tokens"),
    LOYALTY("Loyalty")
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

/** Someone with an account sitting at a seat, having scanned its QR code. */
data class LinkedPlayer(val userId: String, val username: String, val displayName: String, val avatarPath: String?)

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
    val defeatMessage: String? = null,
    /** The account sitting here, when someone joined the seat by QR code: its name and picture show on the tile. */
    val linked: LinkedPlayer? = null,
    /** The deck the player said they're playing, from their remote. */
    val deck: String? = null,
    /** That deck's commander, from their remote — or set at the table ([LifeCounterViewModel.setSeatCommander]). */
    val commander: String? = null,
    /** The art of a commander set at the table, while it's the tile's background. */
    val commanderArt: String? = null
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

    /** Close to losing: 8+ poison, or 18+ damage from one commander. */
    val inDanger: Boolean get() = counter(PlayerCounter.POISON) >= 8 || commanderDamage.values.any { it >= 18 }
}

/**
 * The one player left standing, once someone has actually been knocked out — a fresh game with
 * nobody defeated has no winner.
 */
/** Whose turn it is next, and whether that completes a round (see [nextTurnFrom]). */
data class NextTurn(val turnPlayerId: Int, val roundComplete: Boolean)

/**
 * Whose turn it is next, and whether that completes a round.
 *
 * Players who are out are passed over — the turn used to land on them and stick, because someone
 * who has lost has no reason to be passing turns. A round is counted from whoever started: the
 * number goes up when play comes back to them, or past them when they're out, rather than once per
 * seat. The web app's src/lifecounter/game.ts makes the same decisions.
 */
fun nextTurnFrom(
    players: List<PlayerLife>,
    turnPlayerId: Int,
    firstPlayerId: Int,
    autoKill: Boolean
): NextTurn? {
    val n = players.size
    if (n == 0) return null
    val ids = players.map { it.id }
    val cur = ids.indexOf(turnPlayerId).let { if (it == -1) 0 else it }
    val start = ids.indexOf(firstPlayerId).coerceAtLeast(0)

    // The next seat still in the game; if everyone is out, the turn doesn't move.
    val next = (1..n).map { (cur + it) % n }.firstOrNull { !players[it].isDefeated(autoKill) } ?: return null

    // Counted from the starting seat: coming back to it, or passing it, is a new round.
    fun place(i: Int) = (i - start + n) % n
    return NextTurn(ids[next], place(next) <= place(cur))
}

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
    val atMillis: Long
)

/** Every player's d20 roll, and who rolled highest. Never a tie: a tie for highest re-rolls everyone. */
data class HighRollResult(val rolls: Map<Int, Int>, val winnerId: Int)

/**
 * Session-only multiplayer life tracker — no persistence for game state (life totals only matter
 * for the game currently being played); held in a ViewModel so a config change or a trip into card
 * search mid-game doesn't reset everyone. Saved player profiles and [LifeCounterSettings] are what
 * IS persisted, since they're meant to be reused game after game.
 */
class LifeCounterViewModel(
    private val cardRepository: CardRepository = CardRepository(),
    private val profileRepository: PlayerProfileRepository,
    private val settingsRepository: LifeCounterSettingsRepository,
    private val social: SocialRepository? = null,
    /** Where the table owner's own games are saved (see [LifeCounterSettings.meSeat]). */
    private val deckRepository: DeckRepository? = null
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

    /** Who started, so a round can be measured from them rather than counted per seat. */
    private val _firstPlayerId = MutableStateFlow(1)

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

    // ---- Undo (see undoable) ----

    /**
     * One change that can be undone: the players it touched as they were before, the turn if it
     * passed, and the log as it was. [by] is the seat whose remote made it, or null for the table
     * itself. Quick taps on the same thing fold into one entry, so one undo takes back the burst.
     */
    private data class UndoEntry(
        val by: Int?,
        val key: String,
        val at: Long,
        val before: List<PlayerLife>,
        val turn: Pair<Int, Int>?,
        val history: List<HistoryEntry>
    )

    /** Newest first. */
    private val undoStack = ArrayDeque<UndoEntry>()
    /** Bumped whenever [undoStack] changes, so the remotes hear about it. */
    private val _undoVersion = MutableStateFlow(0)
    private val _canUndo = MutableStateFlow(false)
    /** Whether the table has anything to undo (changes from anywhere). */
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    /** The seat whose remote is being obeyed right now, so its changes are marked as its own. */
    private var actingSeat: Int? = null

    // ---- The game as players' remotes see it (see remoteState) ----

    private var gameId = UUID.randomUUID().toString()
    private var startedAt = System.currentTimeMillis()

    private val _shownCard = MutableStateFlow<RemoteShownCard?>(null)
    /** A card a player is showing the table from their remote, until someone taps it away. */
    val shownCard: StateFlow<RemoteShownCard?> = _shownCard.asStateFlow()

    private val _match = MutableStateFlow<Match?>(null)
    /** The table players join by QR code, once the host has shown one. */
    val match: StateFlow<Match?> = _match.asStateFlow()

    val profiles: StateFlow<List<PlayerProfile>> = profileRepository.profilesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The user's decks, for choosing the one their own games at this table are saved to. */
    val decks: StateFlow<List<Deck>> = (deckRepository?.decksFlow ?: kotlinx.coroutines.flow.flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The games played at this table, newest first. */
    val tableGames: StateFlow<List<TableGame>> = settingsRepository.tableGamesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The last finished game noted, so taps after the end don't note it again. */
    private var lastRecorded: String? = null

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
        // A game that's over goes into the table's games (and, for the owner's seat, onto their deck).
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(_players, _settings) { players, settings -> players to settings }.collect { (players, settings) ->
                val alive = players.filterNot { it.isDefeated(settings.autoKill) }
                if (_ready.value && players.size >= 2 && alive.size <= 1) recordGame(players, settings, alive.singleOrNull()?.id)
                // Losing on your own turn used to leave the turn there: a player who is out has no
                // End turn control, on the table or on their phone, so nobody could pass it on.
                // The turn moves itself now, and stops once the game is over.
                if (settings.turnTrackerEnabled && players.size >= 2 && alive.size > 1) {
                    val active = players.firstOrNull { it.id == _currentTurnPlayerId.value }
                    if (active != null && active.isDefeated(settings.autoKill)) nextTurn()
                }
            }
        }
    }

    /**
     * Notes a finished game — once, or again if an undo changed how it ended (it replaces itself,
     * on the table and on the deck). The owner's seat is saved to their deck only while no phone has
     * joined it: a phone that joins saves the result itself.
     */
    private fun recordGame(players: List<PlayerLife>, settings: LifeCounterSettings, winnerSeat: Int?) {
        val outcome = gameId + ":" + winnerSeat + ":" + players.joinToString(",") { "${it.id}=${it.lossReason(settings.autoKill)}" }
        if (outcome == lastRecorded) return
        lastRecorded = outcome
        val lastAt = _history.value.lastOrNull()?.atMillis ?: System.currentTimeMillis()
        val game = TableGame(
            id = gameId,
            endedAt = System.currentTimeMillis(),
            turns = _turnNumber.value,
            minutes = ((lastAt - startedAt) / 60_000).toInt().coerceAtLeast(1),
            winnerSeat = winnerSeat,
            players = players.map { p ->
                TableGamePlayer(p.id, p.displayName, p.commander, p.lossReason(settings.autoKill)?.name, me = p.id == settings.meSeat && p.linked == null)
            }
        )
        viewModelScope.launch {
            settingsRepository.updateTableGames { withTableGame(it, game) }
            val deckId = settings.meDeckId ?: return@launch
            val deckRepository = deckRepository ?: return@launch
            val seatLinked = players.firstOrNull { it.id == settings.meSeat }?.linked != null
            val result = meResultOf(game, settings.meSeat, seatLinked) ?: return@launch
            if (decks.value.none { it.id == deckId }) return@launch
            deckRepository.removeGameResult(deckId, result.id)
            deckRepository.addGameResult(deckId, result)
        }
    }

    // ---- The table owner's seat, seats' commanders, and the table's games ----

    /** Marks [seat] as the table owner's (null: none), their games saved to [deckId]. */
    fun setMe(seat: Int?, deckId: String?) = updateSettings { it.copy(meSeat = seat, meDeckId = deckId) }

    /**
     * What [seat] is playing, set at the table for a player without a phone of their own: their
     * commander (for everyone's game records), and its art behind a tile that has no picture yet.
     */
    fun setSeatCommander(seat: Int, card: ScryfallCard?) = updatePlayer(seat) { p ->
        if (p.linked != null) return@updatePlayer p
        val art = card?.imageUris?.artCrop ?: card?.cardFaces?.firstOrNull()?.imageUris?.artCrop
        val oldArt = p.commanderArt
        p.copy(
            commander = card?.name,
            commanderArt = art,
            // The commander's art goes behind a bare tile, and follows the commander while it's there.
            backgroundImageUri = when {
                p.backgroundImageUri == null || p.backgroundImageUri == oldArt -> art
                else -> p.backgroundImageUri
            }
        )
    }

    /** Commanders whose names start with [query], for picking one at the table. */
    suspend fun searchCommanders(query: String): List<ScryfallCard> =
        if (query.isBlank()) emptyList() else runCatching { cardRepository.search("is:commander name:\"${query.replace("\"", "")}\"").cards }.getOrDefault(emptyList())

    fun deleteTableGame(id: String) = viewModelScope.launch { settingsRepository.updateTableGames { games -> games.filterNot { it.id == id } } }

    fun clearTableGames() = viewModelScope.launch { settingsRepository.updateTableGames { emptyList() } }

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
        // A restart at the same table keeps who's sitting where; a different number of seats is a new table.
        val before = _players.value
        val sameTable = _match.value != null && before.size == count
        if (!sameTable) endMatch()
        _players.value = (1..count).map { id ->
            val fresh = PlayerLife(id = id, life = life, colorIndex = colors[(id - 1) % colors.size])
            val linked = if (sameTable) before.firstOrNull { it.id == id }?.linked else null
            val old = before.firstOrNull { it.id == id }
            if (linked == null) fresh else fresh.copy(
                linked = linked,
                name = linked.displayName,
                backgroundImageUri = old?.backgroundImageUri ?: SocialApi.avatarUrl(linked.avatarPath),
                deck = old?.deck,
                commander = old?.commander
            )
        }
        _gameNumber.value += 1
        _currentTurnPlayerId.value = 1
        _firstPlayerId.value = 1
        _turnNumber.value = 1
        _monarchPlayerId.value = null
        _initiativePlayerId.value = null
        _dayNight.value = null
        _history.value = emptyList()
        _highRollRequested.value = settings.highRollAtStart && count > 1
        undoStack.clear()
        bumpUndo()
        _shownCard.value = null
        gameId = UUID.randomUUID().toString()
        startedAt = System.currentTimeMillis()
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

    fun adjust(playerId: Int, delta: Int) = undoable("life:$playerId") {
        val player = player(playerId) ?: return@undoable
        updatePlayer(playerId) { it.copy(life = it.life + delta) }
        log(HistoryEvent.Life, playerId, player.life, player.life + delta)
    }

    /** Set a player's life to an exact value, e.g. from the numeric keypad. */
    fun setLife(playerId: Int, value: Int) = undoable("life:$playerId") {
        val player = player(playerId) ?: return@undoable
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
    fun adjustCommanderDamage(playerId: Int, source: CommanderSource, delta: Int) = undoable("cmd:$playerId:${source.opponentId}:${source.slot}") {
        val player = player(playerId) ?: return@undoable
        val current = player.commanderDamage[source] ?: 0
        val updated = (current + delta).coerceAtLeast(0)
        val applied = updated - current
        if (applied == 0) return@undoable
        val lifeLoss = if (_settings.value.commanderDamageCostsLife) applied else 0
        updatePlayer(playerId) {
            it.copy(life = it.life - lifeLoss, commanderDamage = it.commanderDamage + (source to updated))
        }
        log(HistoryEvent.CommanderDamage(source), playerId, current, updated)
    }

    fun adjustCounter(playerId: Int, kind: PlayerCounter, delta: Int) = undoable("counter:$playerId:$kind") {
        val player = player(playerId) ?: return@undoable
        val current = player.counter(kind)
        val updated = (current + delta).coerceAtLeast(0)
        if (updated == current) return@undoable
        updatePlayer(playerId) { it.copy(counters = it.counters + (kind to updated)) }
        log(HistoryEvent.Counter(kind), playerId, current, updated)
    }

    fun adjustMana(playerId: Int, color: String, delta: Int) = undoable("mana:$playerId:$color") {
        val player = player(playerId) ?: return@undoable
        val current = player.manaPool[color] ?: 0
        val updated = (current + delta).coerceAtLeast(0)
        if (updated == current) return@undoable
        updatePlayer(playerId) { it.copy(manaPool = it.manaPool + (color to updated)) }
        log(HistoryEvent.Mana(color), playerId, current, updated)
    }

    /** Commander tax rises in increments of 2 (colorless mana) each time that commander is recast. */
    fun adjustCommanderTax(playerId: Int, slot: Int, delta: Int) = undoable("tax:$playerId:$slot") {
        val player = player(playerId) ?: return@undoable
        val current = player.commanderTax.getOrElse(slot) { 0 }
        val updated = (current + delta).coerceAtLeast(0)
        if (updated == current) return@undoable
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
    fun kill(playerId: Int) = undoable("out:$playerId") {
        if (player(playerId)?.killed != false) return@undoable
        updatePlayer(playerId) { it.copy(killed = true) }
        log(HistoryEvent.Killed, playerId, null, null)
    }

    /**
     * Brings a defeated player back into the game: every loss condition is cleared — a manual kill
     * undone, life back to the starting total, poison removed, commander damage taken wiped. Name,
     * color, commander tax and their other counters are kept, since those describe the player
     * rather than their defeat.
     */
    fun revive(playerId: Int) = undoable("out:$playerId") {
        val player = player(playerId) ?: return@undoable
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

    // ---- Turn tracker ----

    /** Passing the turn also empties every mana pool and resets storm counts, which don't carry over. */
    fun nextTurn() = undoable("turn") {
        val moved = nextTurnFrom(
            _players.value,
            _currentTurnPlayerId.value,
            _firstPlayerId.value,
            _settings.value.autoKill
        ) ?: return@undoable
        _currentTurnPlayerId.value = moved.turnPlayerId
        if (moved.roundComplete) _turnNumber.value += 1
        val perTurn = PlayerCounter.entries.filter { it.resetsEachTurn }.toSet()
        _players.value = _players.value.map { it.copy(manaPool = emptyMap(), counters = it.counters - perTurn) }
        log(HistoryEvent.TurnStarted, _currentTurnPlayerId.value, null, null)
    }

    /** Rolls a d20 for every player; a tie for highest re-rolls everyone, so there's always one winner. */
    fun rollHighRoll(): HighRollResult = highRoll(_players.value.map { it.id })

    /** Starts the turn count over with [playerId] going first (the high-roll winner). */
    fun setFirstPlayer(playerId: Int) {
        _currentTurnPlayerId.value = playerId
        _firstPlayerId.value = playerId
        _turnNumber.value = 1
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

    // ---- Players joining with their profiles (QR code per seat) ----

    private val _seatCode = MutableStateFlow<Int?>(null)
    /** The seat whose QR code is showing. */
    val seatCode: StateFlow<Int?> = _seatCode.asStateFlow()

    private val _seatError = MutableStateFlow<String?>(null)
    val seatError: StateFlow<String?> = _seatError.asStateFlow()

    val canLinkSeats: Boolean get() = social?.configured == true

    /** For the Giphy search (tile backgrounds); signed in only, since it goes through the account. */
    val socialRepository: SocialRepository? get() = social?.takeIf { it.userId != null }

    /** Shows [seat]'s QR code, opening a table on the server first if there isn't one yet. */
    fun showSeatCode(seat: Int) {
        val social = social ?: return
        _seatError.value = null
        _seatCode.value = seat
        if (_match.value != null) return
        if (social.userId == null) {
            _seatError.value = "Sign in on this device (Settings) to let players join with their profiles."
            return
        }
        viewModelScope.launch {
            try {
                _match.value = social.api.startMatch(_players.value.size)
            } catch (e: Exception) {
                _seatError.value = e.message ?: "Something went wrong."
            }
        }
    }

    fun closeSeatCode() { _seatCode.value = null }

    /** Frees a seat: the player goes back to a plain seat. */
    fun unlinkSeat(seat: Int) {
        applyLink(seat, null)
        val m = _match.value ?: return
        val social = social ?: return
        viewModelScope.launch { runCatching { social.api.clearMatchSeat(m.id, seat) } }
    }

    /** One look at who sits where; the screen calls this on a timer while it's in front. */
    suspend fun pollSeats() {
        val social = social ?: return
        val m = _match.value ?: return
        val seats = runCatching { social.api.matchSeats(m.id) }.getOrNull() ?: return
        if (_match.value?.id != m.id) return
        for (p in _players.value) {
            val seated = seats.firstOrNull { it.seat == p.id }?.profile
            val next = seated?.let { LinkedPlayer(it.userId, it.username, it.displayName, it.avatarPath) }
            if (next != p.linked) applyLink(p.id, next)
        }
        val showing = _seatCode.value
        if (showing != null && seats.any { it.seat == showing }) _seatCode.value = null
    }

    /**
     * Seats [linked] at [seat]: their name, and their picture as the tile's background. A picture the
     * player chose themselves stays unless the profile brings one; unlinking takes the profile's away.
     */
    private fun applyLink(seat: Int, linked: LinkedPlayer?) = updatePlayer(seat) { p ->
        val oldAvatar = SocialApi.avatarUrl(p.linked?.avatarPath)
        val ownBackground = p.backgroundImageUri.takeIf { it != oldAvatar }
        p.copy(
            linked = linked,
            name = linked?.displayName ?: if (p.linked != null) null else p.name,
            backgroundImageUri = SocialApi.avatarUrl(linked?.avatarPath) ?: ownBackground,
            deck = if (linked?.userId == p.linked?.userId) p.deck else null,
            commander = if (linked?.userId == p.linked?.userId) p.commander else null
        )
    }

    /** Nobody can join a table the game has moved on from. */
    private fun endMatch() {
        val m = _match.value ?: return
        _match.value = null
        _seatCode.value = null
        _shownCard.value = null
        social?.endMatchInBackground(m.id)
    }

    // ---- Undo ----


    private inline fun undoable(key: String, change: () -> Unit) {
        val playersBefore = _players.value
        val turnBefore = _currentTurnPlayerId.value to _turnNumber.value
        val historyBefore = _history.value
        change()
        val after = _players.value
        val changed = playersBefore.filter { b -> after.firstOrNull { it.id == b.id }?.let { !samePlay(b, it) } == true }
        val turnMoved = (_currentTurnPlayerId.value to _turnNumber.value) != turnBefore
        if (changed.isEmpty() && !turnMoved) return
        val now = System.currentTimeMillis()
        val by = actingSeat
        val top = undoStack.firstOrNull()
        if (top != null && top.key == key && top.by == by && now - top.at < UNDO_MERGE_MS && !turnMoved) {
            undoStack[0] = top.copy(at = now, before = top.before + changed.filter { c -> top.before.none { it.id == c.id } })
        } else {
            undoStack.addFirst(UndoEntry(by, key, now, changed, if (turnMoved) turnBefore else null, historyBefore))
            while (undoStack.size > UNDO_LIMIT) undoStack.removeLast()
        }
        bumpUndo()
    }

    private fun samePlay(a: PlayerLife, b: PlayerLife) =
        a.life == b.life && a.killed == b.killed && a.commanderDamage == b.commanderDamage &&
            a.counters == b.counters && a.manaPool == b.manaPool && a.commanderTax == b.commanderTax

    /**
     * Takes back the newest change — the newest one made from seat [by]'s remote when [by] is set.
     * The players it touched get their life, damage and counters back; who they are and how their
     * tile looks stay as they are now. Undoing the very newest change puts the log back too.
     */
    fun undo(by: Int? = null) {
        val index = if (by == null) 0 else undoStack.indexOfFirst { it.by == by }
        val entry = undoStack.getOrNull(index) ?: return
        undoStack.removeAt(index)
        _players.value = _players.value.map { p ->
            val was = entry.before.firstOrNull { it.id == p.id } ?: return@map p
            p.copy(life = was.life, killed = was.killed, commanderDamage = was.commanderDamage, counters = was.counters, manaPool = was.manaPool, commanderTax = was.commanderTax)
        }
        entry.turn?.let { (seat, number) -> _currentTurnPlayerId.value = seat; _turnNumber.value = number }
        if (index == 0) _history.value = entry.history
        bumpUndo()
    }

    private fun canUndoFor(seat: Int) = undoStack.any { it.by == seat }

    private fun bumpUndo() {
        _undoVersion.value += 1
        _canUndo.value = undoStack.isNotEmpty()
    }

    // ---- Players' phones as remotes (LifeCounterRemote.kt) ----

    fun hideShownCard() { _shownCard.value = null }

    private var hostJob: Job? = null
    private var publishJob: Job? = null
    private var lastSent: String? = null

    init {
        // While players can join (a match is open), keep its channel open.
        viewModelScope.launch {
            _match.collect { m ->
                hostJob?.cancel()
                hostJob = null
                lastSent = null
                val social = social ?: return@collect
                if (m == null || social.userId == null) return@collect
                hostJob = launch {
                    val channel = social.matchChannel.watch(
                        this, m.id,
                        onEvent = { event, payload -> if (event == "action") viewModelScope.launch { onRemoteAction(payload) } },
                        onJoined = { viewModelScope.launch { publish(force = true) } },
                        onLive = {}
                    )
                    // Sent again now and then even when nothing changes, so remotes can tell the table is still there.
                    while (isActive) {
                        delay(REMOTE_HEARTBEAT_MS)
                        publish(force = true)
                    }
                    channel.cancel()
                }
            }
        }
        // Any change to the game goes out to the remotes (a burst of taps, once).
        viewModelScope.launch {
            merge(_players, _currentTurnPlayerId, _turnNumber, _settings, _shownCard, _undoVersion, _history).collect {
                if (_match.value == null) return@collect
                publishJob?.cancel()
                publishJob = launch {
                    delay(PUBLISH_DEBOUNCE_MS)
                    publish(force = false)
                }
            }
        }
    }

    /** The game as the remotes see it. */
    fun remoteState(): RemoteState {
        val settings = _settings.value
        val players = _players.value
        val alive = players.filterNot { it.isDefeated(settings.autoKill) }
        val over = players.size >= 2 && alive.size <= 1
        val lastAt = _history.value.lastOrNull()?.atMillis ?: startedAt
        return RemoteState(
            v = REMOTE_VERSION,
            gameId = gameId,
            remotes = settings.remotesEnabled,
            turn = if (settings.turnTrackerEnabled && players.size > 1) RemoteTurn(_currentTurnPlayerId.value, _turnNumber.value) else null,
            startedAt = startedAt,
            longPress = settings.longPressAmount,
            players = players.map { p ->
                val (hex, ink) = seatHex(p.colorIndex)
                RemoteSeat(
                    seat = p.id,
                    name = p.displayName,
                    color = hex,
                    ink = ink,
                    life = p.life,
                    out = p.lossReason(settings.autoKill)?.name,
                    poison = p.counter(PlayerCounter.POISON),
                    counters = p.counters.filterKeys { it != PlayerCounter.POISON }.mapKeys { it.key.wire() },
                    commanderDamage = p.commanderDamage.filterValues { it > 0 }.map { (src, amount) -> RemoteDamage(src.opponentId, src.slot, amount) },
                    // A photo picked on this phone (content://) means nothing anywhere else.
                    background = p.backgroundImageUri?.takeIf { it.startsWith("https://") },
                    deck = p.deck,
                    commander = p.commander,
                    userId = p.linked?.userId,
                    avatarPath = p.linked?.avatarPath,
                    canUndo = canUndoFor(p.id),
                    partner = p.hasPartner
                )
            },
            shownCard = _shownCard.value,
            over = if (over) RemoteOver(alive.singleOrNull()?.id, _turnNumber.value, ((lastAt - startedAt) / 60_000).toInt().coerceAtLeast(1)) else null
        )
    }

    private suspend fun publish(force: Boolean) {
        val social = social ?: return
        val m = _match.value ?: return
        val state = remoteState().toJson()
        val text = state.toString()
        if (!force && text == lastSent) return
        lastSent = text
        try {
            social.api.publishMatchState(m.id, state)
        } catch (e: Exception) {
            lastSent = null // try again with the next change
        }
    }

    /**
     * A request from a seated player's remote. The table trusts nothing it's sent beyond the seat the
     * server stamped on it — and only from the account the table has sitting there.
     */
    private suspend fun onRemoteAction(payload: JSONObject) {
        val seat = payload.optInt("seat", -1)
        val userId = payload.optString("user_id")
        val action = payload.optJSONObject("action") ?: return
        val player = player(seat) ?: return
        if (player.linked?.userId != userId) return
        val type = action.optString("type")
        if (type == "hello" || !_settings.value.remotesEnabled) {
            publish(force = true)
            return
        }
        fun delta(limit: Int): Int? = action.optInt("delta", 0).takeIf { it != 0 && kotlin.math.abs(it) <= limit }
        fun seated(key: String): Int? = action.optInt(key, -1).takeIf { id -> id != seat && _players.value.any { it.id == id } }
        val before = remoteState().toJson().toString()
        actingSeat = seat
        try {
            when (type) {
                "life" -> delta(1000)?.let { adjust(seat, it) }
                "counter" -> delta(100)?.let { d ->
                    val name = action.optString("counter")
                    (if (name == "poison") PlayerCounter.POISON else counterOfWire(name))?.let { adjustCounter(seat, it, d) }
                }
                "commanderDamage" -> {
                    val d = delta(100)
                    val from = seated("from")
                    val slot = action.optInt("slot", 0)
                    if (d != null && from != null && (slot == 0 || (slot == 1 && player(from)?.hasPartner == true))) {
                        adjustCommanderDamage(seat, CommanderSource(from, slot), d)
                    }
                }
                "dealtDamage" -> {
                    val d = delta(100)
                    val to = seated("to")
                    val slot = action.optInt("slot", 0)
                    if (d != null && to != null && (slot == 0 || (slot == 1 && player.hasPartner))) {
                        adjustCommanderDamage(to, CommanderSource(seat, slot), d)
                    }
                }
                "endTurn" -> if (_settings.value.turnTrackerEnabled && _currentTurnPlayerId.value == seat) nextTurn()
                "undo" -> undo(by = seat)
                "background" -> {
                    val url = if (action.isNull("url")) null else action.optString("url")
                    if (url == null || allowedRemoteImage(url)) {
                        val deck = if (action.has("deck")) (if (action.isNull("deck")) null else action.optString("deck").take(80)) else player.deck
                        // An older remote sends no commander: a new deck clears the old one's.
                        val commander = if (action.has("commander")) (if (action.isNull("commander")) null else action.optString("commander").take(150))
                        else if (deck == player.deck) player.commander else null
                        updatePlayer(seat) { it.copy(backgroundImageUri = url, deck = deck, commander = commander) }
                    }
                }
                "showCard" -> {
                    val url = action.optString("imageUrl")
                    val name = action.optString("name").take(150)
                    if (name.isNotEmpty() && allowedRemoteImage(url, scryfallOnly = true)) _shownCard.value = RemoteShownCard(name, url, seat)
                }
                "hideCard" -> if (_shownCard.value?.seat == seat) _shownCard.value = null
            }
        } finally {
            actingSeat = null
        }
        // Nothing changed (not theirs to do, say): let the remote see the game as it is.
        if (remoteState().toJson().toString() == before) publish(force = true)
    }

    override fun onCleared() {
        hostJob?.cancel()
        endMatch()
        super.onCleared()
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
        private const val UNDO_LIMIT = 60
        /** Changes to the same thing within this long of each other undo together. */
        private const val UNDO_MERGE_MS = 2_000L
        /** How long the game settles before it's sent to the remotes again. */
        private const val PUBLISH_DEBOUNCE_MS = 120L
        private const val REMOTE_HEARTBEAT_MS = 25_000L

        private fun defaultPlayers(count: Int, life: Int) =
            (1..count).map { PlayerLife(id = it, life = life, colorIndex = (it - 1) % PLAYER_COLOR_COUNT) }

        /** Pure so it can be reasoned about independently of the ViewModel's state. */
        fun highRoll(playerIds: List<Int>, random: Random = Random): HighRollResult {
            require(playerIds.isNotEmpty()) { "High roll needs at least one player" }
            // A tie for highest re-rolls the whole table, so what everyone sees is one clean roll
            // with a single, highest winner.
            while (true) {
                val rolls = playerIds.associateWith { random.nextInt(1, 21) }
                val top = rolls.values.max()
                val leaders = rolls.filterValues { it == top }.keys
                if (leaders.size == 1) return HighRollResult(rolls, leaders.first())
            }
        }
    }

    class Factory(
        private val profileRepository: PlayerProfileRepository,
        private val settingsRepository: LifeCounterSettingsRepository,
        private val social: SocialRepository,
        private val deckRepository: DeckRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LifeCounterViewModel(profileRepository = profileRepository, settingsRepository = settingsRepository, social = social, deckRepository = deckRepository) as T
    }
}
