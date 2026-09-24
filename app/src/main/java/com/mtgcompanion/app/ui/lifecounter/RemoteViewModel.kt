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
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
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

    private fun onState(s: RemoteState) {
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

    fun showCard(name: String) {
        _busy.value = true
        viewModelScope.launch {
            try {
                val card = cardRepository.getByExactName(name)
                val url = card.displayImageUrl
                if (url == null) _error.value = "That card has no picture." else send(RemoteActions.showCard(card.name, url))
            } catch (e: Exception) {
                _error.value = "Couldn't find that card — check your connection."
            } finally {
                _busy.value = false
            }
        }
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
