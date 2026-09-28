package com.mtgcompanion.app.ui.lifecounter

import com.mtgcompanion.app.data.SettingsRepository
import com.mtgcompanion.app.data.SeatMemory
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckRepository
import com.mtgcompanion.app.data.GameResult
import com.mtgcompanion.app.data.social.Giphy
import com.mtgcompanion.app.data.social.SocialApi
import com.mtgcompanion.app.data.social.SocialException
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.scryfall.ScryfallRuling
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.badge.BadgeToken
import com.mtgcompanion.app.ui.badge.badgeTokensFor
import com.mtgcompanion.app.ui.social.avatarBytes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

/** A card about to be shown on the table, with its official rulings. */
data class CardPreview(val card: ScryfallCard, val rulings: List<ScryfallRuling>)

/** What goes behind this player's tile at the table. */
enum class TileBackground { COMMANDER, PROFILE, COLOUR, CUSTOM }

/**
 * A player's phone as the remote for their seat at someone's life counter (opened after joining a
 * seat by QR code). Every change is a request the table applies — the table's game is the one
 * truth, and it comes back here as [state]. The web app's twin is src/lifecounter/RemotePage.tsx.
 */
class RemoteViewModel(
    private val social: SocialRepository,
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    context: Context,
    val matchId: String,
    val seat: Int
) : ViewModel() {
    private val prefs = context.applicationContext.getSharedPreferences("life_counter_remote", Context.MODE_PRIVATE)
    private val settings = SettingsRepository(context.applicationContext)

    private val _state = MutableStateFlow<RemoteState?>(null)
    val state: StateFlow<RemoteState?> = _state.asStateFlow()

    private val _live = MutableStateFlow(false)
    val live: StateFlow<Boolean> = _live.asStateFlow()

    private val _heardAt = MutableStateFlow(0L)
    /** When the table last sent the game (it does at least every 25 s while it's there). */
    val heardAt: StateFlow<Long> = _heardAt.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _gone = MutableStateFlow(false)
    /** The seat was freed or the table ended. */
    val gone: StateFlow<Boolean> = _gone.asStateFlow()

    val decks: StateFlow<List<Deck>> = deckRepository.decksFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val userId: String? get() = social.userId
    val avatarUrl: String? get() = SocialApi.avatarUrl(social.overview.value?.me?.avatarPath)
    val socialRepository: SocialRepository get() = social

    private val _background = MutableStateFlow(TileBackground.entries.firstOrNull { it.name == prefs.getString(KEY_BACKGROUND, null) } ?: TileBackground.PROFILE)
    val background: StateFlow<TileBackground> = _background.asStateFlow()
    private val _customUrl = MutableStateFlow(prefs.getString(KEY_CUSTOM, null))
    val customUrl: StateFlow<String?> = _customUrl.asStateFlow()
    private val _deckId = MutableStateFlow(prefs.getString(KEY_DECK, null))
    val deckId: StateFlow<String?> = _deckId.asStateFlow()

    private val _busy = MutableStateFlow(false)
    /** A picture uploading, or a card being looked up. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private var appliedPrefs = false

    init {
        if (social.overview.value == null) viewModelScope.launch { social.refresh() }
        social.matchChannel.watch(
            viewModelScope, matchId,
            onEvent = { event, payload ->
                if (event != "state") return@watch
                val next = payload.optJSONObject("state")?.let { RemoteState.parse(it) } ?: return@watch
                viewModelScope.launch {
                    _state.value = next
                    _heardAt.value = System.currentTimeMillis()
                    onState(next)
                }
            },
            onJoined = { send(RemoteActions.hello()) },
            onLive = { _live.value = it }
        )
    }

    fun deck(): Deck? = decks.value.firstOrNull { it.id == _deckId.value }

    private fun commanderArt(deck: Deck?): String? = deck?.commander?.imageUrl.toArtCropUrl()

    /** The deck's commander, "A & B" with a partner — what the other players' records will say they faced. */
    private fun commanderOf(deck: Deck?): String? =
        listOfNotNull(deck?.commander?.name, deck?.partnerCommander?.name).joinToString(" & ").ifEmpty { null }

    private fun urlFor(kind: TileBackground, deck: Deck?, custom: String?): String? = when (kind) {
        TileBackground.COMMANDER -> commanderArt(deck)
        TileBackground.PROFILE -> avatarUrl
        TileBackground.CUSTOM -> custom
        TileBackground.COLOUR -> null
    }

    fun send(action: JSONObject) {
        _error.value = null
        viewModelScope.launch {
            try {
                social.api.sendMatchAction(matchId, action)
            } catch (e: SocialException) {
                if (e.code == "not_seated") _gone.value = true else _error.value = e.message
            } catch (e: Exception) {
                _error.value = e.message ?: "Something went wrong."
            }
        }
    }

    // ---- Your turn, and what just happened at the table ----

    /** First state since this screen opened: what's already true then isn't news. */
    private var heardFirst = false
    private var lastTurnSeat: Int? = null
    private var lastAnnounceId: String? = null

    private val _buzz = MutableStateFlow(0)
    /** Goes up each time the turn comes to this seat, so the screen can buzz the phone. */
    val buzz: StateFlow<Int> = _buzz.asStateFlow()

    private val _toast = MutableStateFlow<RemoteAnnounce?>(null)
    /** The latest roll, emote or pointing from the table, while it's up. */
    val toast: StateFlow<RemoteAnnounce?> = _toast.asStateFlow()

    fun clearToast(id: String) { if (_toast.value?.id == id) _toast.value = null }

    private fun noticeNews(s: RemoteState) {
        val first = !heardFirst
        heardFirst = true
        val turnSeat = s.turn?.seat
        if (!first && turnSeat == seat && lastTurnSeat != seat) _buzz.value += 1
        lastTurnSeat = turnSeat
        val a = s.announce
        if (a != null && a.id != lastAnnounceId) {
            lastAnnounceId = a.id
            if (!first) _toast.value = a
        }
        if (s.gameId != countsGame) loadTokenCounts(s.gameId)
    }

    private fun onState(s: RemoteState) {
        noticeNews(s)
        val mine = s.players.firstOrNull { it.seat == seat } ?: return
        // Sitting down at a new table: the tile starts bare, so put on it the picture and deck this
        // player chose last time — never over one that's already there.
        if (!appliedPrefs && s.remotes) {
            appliedPrefs = true
            if (mine.background == null && mine.deck == null && prefs.contains(KEY_BACKGROUND)) {
                val deck = deck()
                val url = urlFor(_background.value, deck, _customUrl.value)
                if (url != null || deck != null) send(RemoteActions.background(url, deck?.name, commanderOf(deck)))
            }
        }
        s.over?.let { logResult(s, it) }
    }

    fun chooseBackground(kind: TileBackground, custom: String? = _customUrl.value, deckId: String? = _deckId.value) {
        _background.value = kind
        _customUrl.value = custom
        _deckId.value = deckId
        prefs.edit().putString(KEY_BACKGROUND, kind.name).putString(KEY_CUSTOM, custom).putString(KEY_DECK, deckId).apply()
        val deck = decks.value.firstOrNull { it.id == deckId }
        send(RemoteActions.background(urlFor(kind, deck, custom), deck?.name, commanderOf(deck)))
    }

    fun chooseDeck(deck: Deck?) {
        val kind = if (deck != null && _background.value == TileBackground.COLOUR && commanderArt(deck) != null) TileBackground.COMMANDER
        else if (deck == null && _background.value == TileBackground.COMMANDER) TileBackground.COLOUR
        else _background.value
        chooseBackground(kind, deckId = deck?.id)
    }

    /** A GIF from Giphy's picker, shown straight from Giphy. */
    fun chooseGiphy(link: String) {
        if (Giphy.id(link) == null) throw IllegalArgumentException("That isn't a Giphy link.")
        chooseBackground(TileBackground.CUSTOM, custom = Giphy.directUrl(link))
    }

    /** A photo from the phone: uploaded to the user's own picture folder, so the table can load it. */
    fun choosePhoto(context: Context, uri: Uri) {
        val user = social.userId ?: return
        _busy.value = true
        viewModelScope.launch {
            try {
                val (bytes, type) = avatarBytes(context, uri)
                val path = social.api.uploadAvatar(user, bytes, type)
                chooseBackground(TileBackground.CUSTOM, custom = SocialApi.avatarUrl(path))
            } catch (e: Exception) {
                _error.value = e.message ?: "The picture didn't upload."
            } finally {
                _busy.value = false
            }
        }
    }

    suspend fun cardNames(query: String): List<String> = if (query.trim().length < 2) emptyList() else cardRepository.autocomplete(query.trim()).take(12)

    private val _preview = MutableStateFlow<CardPreview?>(null)
    /** The card picked to show, with its rulings, before it goes up on the table. */
    val preview: StateFlow<CardPreview?> = _preview.asStateFlow()

    /** Looks up [name] and its official rulings, so the player can check them before showing it. */
    fun previewCard(name: String) {
        _busy.value = true
        viewModelScope.launch {
            try {
                // The rulings are a nice-to-have: without them the card can still be shown.
                val (card, rulings) = runCatching { cardRepository.getRulings(name) }
                    .getOrElse { cardRepository.getByExactName(name) to emptyList() }
                _preview.value = CardPreview(card, rulings)
            } catch (e: Exception) {
                _error.value = "Couldn't find that card — check your connection."
            } finally {
                _busy.value = false
            }
        }
    }

    fun closePreview() { _preview.value = null }

    fun showCard(card: ScryfallCard) {
        val url = card.displayImageUrl
        if (url == null) _error.value = "That card has no picture." else send(RemoteActions.showCard(card.name, url))
        _preview.value = null
    }

    // ---- Tokens from the deck ----

    private val _tokens = MutableStateFlow<List<BadgeToken>?>(null)
    /** The tokens the chosen deck makes; null while loading or when they couldn't be found. */
    val tokens: StateFlow<List<BadgeToken>?> = _tokens.asStateFlow()
    private val _tokensLoading = MutableStateFlow(false)
    val tokensLoading: StateFlow<Boolean> = _tokensLoading.asStateFlow()
    private var tokensDeck: String? = null

    fun loadTokens() {
        val deck = deck() ?: return
        if (deck.id == tokensDeck && _tokens.value != null) return
        tokensDeck = deck.id
        _tokensLoading.value = true
        viewModelScope.launch {
            _tokens.value = badgeTokensFor(deck, cardRepository)
            _tokensLoading.value = false
        }
    }

    private val _tokenCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    /** How many of each token this player has out, kept on this phone for the game being played. */
    val tokenCounts: StateFlow<Map<String, Int>> = _tokenCounts.asStateFlow()
    private var countsGame: String? = null

    /** Only the current game's counts are kept: a new game at the table starts them over. */
    private fun loadTokenCounts(gameId: String) {
        countsGame = gameId
        val saved = runCatching { JSONObject(prefs.getString(KEY_TOKEN_COUNTS, null) ?: "{}") }.getOrNull()
        val counts = saved?.takeIf { it.optString("game") == "$matchId:$gameId" }?.optJSONObject("counts")
        _tokenCounts.value = counts?.let { c -> c.keys().asSequence().associateWith { c.optInt(it) } } ?: emptyMap()
    }

    /** One more (or fewer) of token [id]; the table's Tokens counter follows, one for one. */
    fun changeToken(id: String, delta: Int) {
        val current = _tokenCounts.value[id] ?: 0
        val next = (current + delta).coerceAtLeast(0)
        if (next == current) return
        _tokenCounts.value = _tokenCounts.value + (id to next)
        prefs.edit().putString(
            KEY_TOKEN_COUNTS,
            JSONObject().put("game", "$matchId:$countsGame").put("counts", JSONObject(_tokenCounts.value as Map<*, *>)).toString()
        ).apply()
        send(RemoteActions.counter(PlayerCounter.TOKENS.wire(), next - current))
    }

    var notes: String
        get() = prefs.getString("notes:$matchId", "").orEmpty()
        set(value) { prefs.edit().putString("notes:$matchId", value).apply() }

    fun leaveSeat() {
        social.leaveSeatInBackground(matchId, seat)
        forgetSeat()
    }

    /**
     * Stepping off this screen doesn't leave the seat, so remember it and let Home offer the way
     * back — the QR that got you here is on someone else's phone (see SeatMemory).
     */
    fun rememberSeat() {
        viewModelScope.launch { settings.setRemoteSeat(SeatMemory(matchId, seat, System.currentTimeMillis())) }
    }

    /** The seat isn't ours any more: the table ended, or someone else took it. */
    fun forgetSeat() {
        viewModelScope.launch { settings.setRemoteSeat(null) }
    }

    // ---- Game over ----

    private val _logged = MutableStateFlow<String?>(null)
    /** "Saved to <deck> · now W–L" once this game's result is on the deck. */
    val logged: StateFlow<String?> = _logged.asStateFlow()

    private fun logResult(s: RemoteState, over: RemoteOver) {
        val key = "$matchId:${s.gameId}"
        val deck = deck() ?: return
        val done = prefs.getStringSet(KEY_LOGGED, emptySet()).orEmpty()
        if (key !in done) {
            prefs.edit().putStringSet(KEY_LOGGED, (done + key).toList().takeLast(100).toSet()).apply()
            val others = s.players.filter { it.seat != seat }
            val opponents = others.joinToString(", ") { it.name }.ifBlank { null }
            val result = GameResult(
                UUID.randomUUID().toString(),
                if (over.winner == seat) "WIN" else "LOSS",
                opponents,
                turns = over.turns.takeIf { it > 0 },
                minutes = over.minutes.takeIf { it > 0 },
                commanders = others.mapNotNull { it.commander }
            )
            viewModelScope.launch {
                deckRepository.addGameResult(deck.id, result)
                delay(300) // the saved deck comes back through decksFlow
                recordLine()
            }
        } else {
            recordLine()
        }
    }

    private fun recordLine() {
        val deck = deck() ?: return
        _logged.value = "Saved to ${deck.name} · now ${deck.gameResults.count { it.result == "WIN" }}–${deck.gameResults.count { it.result == "LOSS" }}"
    }

    /** After picking a deck on the game-over screen: log this game to it. */
    fun logAfterPicking() {
        val s = _state.value ?: return
        s.over?.let { logResult(s, it) }
    }

    private companion object {
        const val KEY_BACKGROUND = "background"
        const val KEY_CUSTOM = "custom_url"
        const val KEY_DECK = "deck_id"
        const val KEY_LOGGED = "logged_games"
        const val KEY_TOKEN_COUNTS = "token_counts"
    }

    class Factory(
        private val social: SocialRepository,
        private val deckRepository: DeckRepository,
        private val context: Context,
        private val matchId: String,
        private val seat: Int
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RemoteViewModel(social, deckRepository, CardRepository(), context, matchId, seat) as T
    }
}
