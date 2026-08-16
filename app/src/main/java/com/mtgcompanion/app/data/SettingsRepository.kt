package com.mtgcompanion.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** How a screen lays out its cards: a control-dense row, or a compact visual-browsing grid. */
enum class CardViewMode {
    LIST, GRID;

    companion object {
        val DEFAULT = LIST
        fun fromName(name: String?): CardViewMode = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** Valid range for the shared grid column count, and its default. */
val GRID_COLUMNS_RANGE = 3..10
const val GRID_COLUMNS_DEFAULT = 4

/** Overall light/dark luminance of the app's theme. SYSTEM follows the device's own dark-mode setting. */
enum class AppBrightness {
    DARK, LIGHT, SYSTEM;

    companion object {
        val DEFAULT = DARK
        fun fromName(name: String?): AppBrightness = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** Accent color, themed on Magic's five colors of mana. */
enum class AccentTheme(val label: String) {
    GOLD("Gold"), SAPPHIRE("Sapphire"), AMETHYST("Amethyst"), RUBY("Ruby"), EMERALD("Emerald");

    companion object {
        val DEFAULT = GOLD
        fun fromName(name: String?): AccentTheme = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

class SettingsRepository(private val context: Context) {

    private val clientIdKey = stringPreferencesKey("tcgplayer_client_id")
    private val clientSecretKey = stringPreferencesKey("tcgplayer_client_secret")
    private val searchViewModeKey = stringPreferencesKey("search_view_mode")
    private val collectionViewModeKey = stringPreferencesKey("collection_view_mode")
    private val deckViewModeKey = stringPreferencesKey("deck_view_mode")
    private val allCardsViewModeKey = stringPreferencesKey("allcards_view_mode")
    private val recViewModeKey = stringPreferencesKey("rec_view_mode")
    private val gridColumnsKey = intPreferencesKey("grid_columns")
    private val appBrightnessKey = stringPreferencesKey("app_brightness")
    private val accentThemeKey = stringPreferencesKey("accent_theme")
    private val lastOpenedDeckIdKey = stringPreferencesKey("last_opened_deck_id")
    private val cardOfDayDateKey = stringPreferencesKey("card_of_day_date")
    private val cardOfDayNameKey = stringPreferencesKey("card_of_day_name")
    private val cardOfDayImageUrlKey = stringPreferencesKey("card_of_day_image_url")

    val tcgPlayerClientId: Flow<String?> = context.dataStore.data.map { it[clientIdKey] }
    val tcgPlayerClientSecret: Flow<String?> = context.dataStore.data.map { it[clientSecretKey] }

    val searchViewMode: Flow<CardViewMode> = context.dataStore.data.map { CardViewMode.fromName(it[searchViewModeKey]) }
    val collectionViewMode: Flow<CardViewMode> = context.dataStore.data.map { CardViewMode.fromName(it[collectionViewModeKey]) }
    val deckViewMode: Flow<CardViewMode> = context.dataStore.data.map { CardViewMode.fromName(it[deckViewModeKey]) }
    val allCardsViewMode: Flow<CardViewMode> = context.dataStore.data.map { CardViewMode.fromName(it[allCardsViewModeKey]) }
    val recViewMode: Flow<CardViewMode> = context.dataStore.data.map { CardViewMode.fromName(it[recViewModeKey]) }

    /** Shared column count for every grid — the 5 row-grid tabs and the card-detail suggestion grid. */
    val gridColumns: Flow<Int> = context.dataStore.data.map {
        (it[gridColumnsKey] ?: GRID_COLUMNS_DEFAULT).coerceIn(GRID_COLUMNS_RANGE)
    }

    val appBrightness: Flow<AppBrightness> = context.dataStore.data.map { AppBrightness.fromName(it[appBrightnessKey]) }
    val accentTheme: Flow<AccentTheme> = context.dataStore.data.map { AccentTheme.fromName(it[accentThemeKey]) }

    /** The deck a user most recently opened, for Home's "continue where you left off" tile. */
    val lastOpenedDeckId: Flow<String?> = context.dataStore.data.map { it[lastOpenedDeckIdKey] }

    /** Home's "card of the day" spotlight, re-rolled once per calendar day. */
    val cardOfDayDate: Flow<String?> = context.dataStore.data.map { it[cardOfDayDateKey] }
    val cardOfDayName: Flow<String?> = context.dataStore.data.map { it[cardOfDayNameKey] }
    val cardOfDayImageUrl: Flow<String?> = context.dataStore.data.map { it[cardOfDayImageUrlKey] }

    suspend fun setSearchViewMode(mode: CardViewMode) {
        context.dataStore.edit { it[searchViewModeKey] = mode.name }
    }

    suspend fun setCollectionViewMode(mode: CardViewMode) {
        context.dataStore.edit { it[collectionViewModeKey] = mode.name }
    }

    suspend fun setDeckViewMode(mode: CardViewMode) {
        context.dataStore.edit { it[deckViewModeKey] = mode.name }
    }

    suspend fun setAllCardsViewMode(mode: CardViewMode) {
        context.dataStore.edit { it[allCardsViewModeKey] = mode.name }
    }

    suspend fun setRecViewMode(mode: CardViewMode) {
        context.dataStore.edit { it[recViewModeKey] = mode.name }
    }

    suspend fun setGridColumns(columns: Int) {
        context.dataStore.edit { it[gridColumnsKey] = columns.coerceIn(GRID_COLUMNS_RANGE) }
    }

    suspend fun setAppBrightness(brightness: AppBrightness) {
        context.dataStore.edit { it[appBrightnessKey] = brightness.name }
    }

    suspend fun setAccentTheme(theme: AccentTheme) {
        context.dataStore.edit { it[accentThemeKey] = theme.name }
    }

    suspend fun setLastOpenedDeckId(deckId: String) {
        context.dataStore.edit { it[lastOpenedDeckIdKey] = deckId }
    }

    suspend fun setCardOfDay(date: String, name: String, imageUrl: String?) {
        context.dataStore.edit {
            it[cardOfDayDateKey] = date
            it[cardOfDayNameKey] = name
            if (imageUrl != null) it[cardOfDayImageUrlKey] = imageUrl else it.remove(cardOfDayImageUrlKey)
        }
    }

    suspend fun currentCredentials(): Pair<String, String>? {
        val id = tcgPlayerClientId.first()
        val secret = tcgPlayerClientSecret.first()
        return if (!id.isNullOrBlank() && !secret.isNullOrBlank()) id to secret else null
    }

    suspend fun saveTcgPlayerCredentials(clientId: String, clientSecret: String) {
        context.dataStore.edit {
            it[clientIdKey] = clientId
            it[clientSecretKey] = clientSecret
        }
    }

    suspend fun clearTcgPlayerCredentials() {
        context.dataStore.edit {
            it.remove(clientIdKey)
            it.remove(clientSecretKey)
        }
    }
}
