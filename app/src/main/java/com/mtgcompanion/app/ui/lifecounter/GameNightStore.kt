package com.mtgcompanion.app.ui.lifecounter

import android.content.Context
import android.content.SharedPreferences
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.localMoshi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

// Tonight's game night and the one before it (for not repeating its pairings), kept on this phone
// so the night survives the app closing. And each deck's estimated bracket, for the suggestions, and
// the pod waiting to be seated at the life counter. The web app keeps the same in
// src/lifecounter/gameNightStore.ts.

/** What's stored: tonight's night, and the one before. */
data class SavedGameNights(val current: GameNight, val previous: GameNight? = null)

object GameNightStore {
    private const val PREFS = "game_night"
    private const val KEY = "nights_json"

    private val adapter by lazy { localMoshi.adapter(SavedGameNights::class.java) }
    private var prefs: SharedPreferences? = null

    private val _nights = MutableStateFlow(SavedGameNights(newNight(newId(), System.currentTimeMillis())))
    val nights: StateFlow<SavedGameNights> = _nights.asStateFlow()

    fun newId(): String = UUID.randomUUID().toString()

    /** Loads what's kept, once. A night left from another day starts over by itself, with the same players. */
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        p.getString(KEY, null)?.let { json -> runCatching { adapter.fromJson(json) }.getOrNull() }?.let { _nights.value = it }
        if (System.currentTimeMillis() - _nights.value.current.createdAt > NIGHT_STALE_MS) startNewNight()
    }

    private fun save(next: SavedGameNights) {
        _nights.value = next
        runCatching { prefs?.edit()?.putString(KEY, adapter.toJson(next))?.apply() }
    }

    /** Changes tonight's night as it stands now. */
    fun update(transform: (GameNight) -> GameNight) = save(_nights.value.copy(current = transform(_nights.value.current)))

    /**
     * Game nights kept here, for a loan due back "next game night" (Loans.kt): whether there's been
     * one (a night with players seated in pods, now or before, or players gathered), and when each
     * started.
     */
    fun gameNights(): Pair<Boolean, List<Long>> {
        val saved = _nights.value
        val all = listOfNotNull(saved.current, saved.previous).filter { it.pods.isNotEmpty() }
        return (all.isNotEmpty() || saved.current.players.size > 1) to all.map { it.createdAt }
    }

    /** A new night with tonight's players; tonight becomes the one before (when its pods were made). */
    fun startNewNight() {
        val old = _nights.value.current
        save(SavedGameNights(newNight(newId(), System.currentTimeMillis(), old), if (old.pods.isNotEmpty()) old else _nights.value.previous))
    }
}

/**
 * The pod game night is starting: the life counter seats it once it has loaded, then clears it.
 * (The life counter's ViewModel belongs to its screen, so the pod waits here rather than in a
 * navigation argument.)
 */
object LifeCounterSeed {
    val pending = MutableStateFlow<TableSeed?>(null)
}

/** Each deck's bracket estimated from its Game Changers, once per deck. */
object DeckBrackets {
    private val known = mutableMapOf<String, Int>()
    private fun key(deck: Deck) = "${deck.id}:${deck.cards.size}"

    /** The bracket already estimated for [deck], if it has been. */
    fun known(deck: Deck): Int? = known[key(deck)]

    /**
     * [deck]'s bracket from its Game Changers, as the deck's Stats estimate it (estimateBracket in
     * DeckDetailViewModel; combos aren't asked about here): none is 2, up to three is 3, more is 4.
     * Null when its cards couldn't be looked up (offline).
     */
    suspend fun estimate(deck: Deck, cardRepository: CardRepository): Int? {
        known[key(deck)]?.let { return it }
        if (deck.cards.isEmpty()) return null
        val cards = runCatching { cardRepository.getCardsByIds(deck.cards.map { it.scryfallId }) }.getOrDefault(emptyList())
        if (cards.isEmpty()) return null
        val byId = cards.associateBy { it.id }
        val gameChangers = deck.cards.filter { byId[it.scryfallId]?.gameChanger == true }.map { it.name }.distinct().size
        val bracket = when {
            gameChangers == 0 -> 2
            gameChangers <= 3 -> 3
            else -> 4
        }
        known[key(deck)] = bracket
        return bracket
    }
}
