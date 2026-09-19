package com.mtgcompanion.app.ui.lifecounter

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.lifeCounterSettingsDataStore by preferencesDataStore(name = "life_counter_settings")

/**
 * Everything about how the Life Counter behaves and looks that should survive between games and
 * app launches. Game state itself (life totals, counters) is deliberately NOT here — see
 * [LifeCounterViewModel]. Defaults follow the Lotus life counter this screen is modeled on,
 * e.g. 20 life for two-player games and the turn tracker/timer off until switched on.
 */
data class LifeCounterSettings(
    val layoutId: String = TableLayouts.DEFAULT_ID,
    val multiplayerStartingLife: Int = 40,
    val twoPlayerStartingLife: Int = 20,

    val turnTrackerEnabled: Boolean = false,
    val highRollAtStart: Boolean = false,
    /** Off means players are only ever out when someone taps Kill. */
    val autoKill: Boolean = true,
    /** Off tracks commander damage purely as its own tally, without also subtracting life. */
    val commanderDamageCostsLife: Boolean = true,

    val countersOnTile: Boolean = true,
    /** Counters someone has used stay on the tile after dropping back to 0. */
    val keepZeroCounters: Boolean = false,
    /** Shown on every tile in every game regardless of value. */
    val pinnedCounters: Set<PlayerCounter> = emptySet(),
    val showCommanderDamageOnTile: Boolean = true,
    val playerNamesOnTile: Boolean = true,

    /** Draw defeat/victory messages from the lists below instead of a plain "Defeated"/"Victory!". */
    val saltyMessages: Boolean = true,
    val cycleMessages: Boolean = false,
    val shuffleColors: Boolean = false,

    val tapLifeToSet: Boolean = true,
    /** Top half adds / bottom half subtracts, instead of right adds / left subtracts. */
    val verticalTapAreas: Boolean = false,
    /** Hides the +/- hints on each tile. */
    val minimalist: Boolean = false,
    /** Underlines 6 and 9 so they can't be misread from across the table. */
    val underlineSixNine: Boolean = true,
    val lowLifeWarning: Boolean = true,
    val tapAmount: Int = 1,
    val longPressAmount: Int = 10,

    val defeatMessages: List<String> = DEFAULT_DEFEAT_MESSAGES,
    val commanderDefeatMessages: List<String> = DEFAULT_COMMANDER_DEFEAT_MESSAGES,
    val poisonDefeatMessages: List<String> = DEFAULT_POISON_DEFEAT_MESSAGES,
    val victoryMessages: List<String> = DEFAULT_VICTORY_MESSAGES,

    val tipsSeen: Boolean = false,
    /** Players who joined a seat by QR code can change their own seat from their phone. */
    val remotesEnabled: Boolean = true,
    /** The table owner's own seat, when they play without a phone of their own — see [meResultOf]. */
    val meSeat: Int? = null,
    /** The deck their games there are saved to. */
    val meDeckId: String? = null
) {
    fun startingLifeFor(playerCount: Int): Int = if (playerCount == 2) twoPlayerStartingLife else multiplayerStartingLife

    companion object {
        val DEFAULT_DEFEAT_MESSAGES = listOf(
            "Defeated", "Out of the game", "Better luck next game", "Off to the graveyard", "That's the game for you"
        )
        val DEFAULT_COMMANDER_DEFEAT_MESSAGES = listOf(
            "Taken out by a commander", "21 and done", "Commander damage claims another"
        )
        val DEFAULT_POISON_DEFEAT_MESSAGES = listOf(
            "Poisoned", "Ten counters too many", "A toxic ending"
        )
        val DEFAULT_VICTORY_MESSAGES = listOf(
            "Victory!", "Last one standing", "The table is yours", "Winner"
        )
    }
}

class LifeCounterSettingsRepository(private val context: Context) {
    private val key = stringPreferencesKey("settings_json")

    // Its own Moshi rather than the shared data-layer one, which is internal to that package.
    // Missing fields in older stored JSON fall back to the data class defaults.
    private val adapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        .adapter(LifeCounterSettings::class.java)

    val settingsFlow: Flow<LifeCounterSettings> = context.lifeCounterSettingsDataStore.data.map { prefs ->
        prefs[key]?.let { json -> runCatching { adapter.fromJson(json) }.getOrNull() } ?: LifeCounterSettings()
    }

    private val gamesKey = stringPreferencesKey("table_games_json")
    private val gamesAdapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        .adapter<List<TableGame>>(com.squareup.moshi.Types.newParameterizedType(List::class.java, TableGame::class.java))

    /** The games played at this table, newest first. */
    val tableGamesFlow: Flow<List<TableGame>> = context.lifeCounterSettingsDataStore.data.map { prefs ->
        prefs[gamesKey]?.let { json -> runCatching { gamesAdapter.fromJson(json) }.getOrNull() }.orEmpty()
    }

    suspend fun updateTableGames(transform: (List<TableGame>) -> List<TableGame>) {
        context.lifeCounterSettingsDataStore.edit { prefs ->
            val current = prefs[gamesKey]?.let { runCatching { gamesAdapter.fromJson(it) }.getOrNull() }.orEmpty()
            prefs[gamesKey] = gamesAdapter.toJson(transform(current))
        }
    }

    suspend fun update(transform: (LifeCounterSettings) -> LifeCounterSettings) {
        context.lifeCounterSettingsDataStore.edit { prefs ->
            val current = prefs[key]?.let { runCatching { adapter.fromJson(it) }.getOrNull() } ?: LifeCounterSettings()
            prefs[key] = adapter.toJson(transform(current))
        }
    }
}
