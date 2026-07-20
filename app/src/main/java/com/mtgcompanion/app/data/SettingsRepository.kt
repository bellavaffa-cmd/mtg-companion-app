package com.mtgcompanion.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
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

/** How large grid tiles are, wherever a screen is in Grid mode. */
enum class GridSize {
    SMALL, MEDIUM, LARGE;

    companion object {
        val DEFAULT = MEDIUM
        fun fromName(name: String?): GridSize = entries.firstOrNull { it.name == name } ?: DEFAULT
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
    private val gridSizeKey = stringPreferencesKey("grid_size")
    private val cardDetailGridSizeKey = stringPreferencesKey("card_detail_grid_size")

    val tcgPlayerClientId: Flow<String?> = context.dataStore.data.map { it[clientIdKey] }
    val tcgPlayerClientSecret: Flow<String?> = context.dataStore.data.map { it[clientSecretKey] }

    val searchViewMode: Flow<CardViewMode> = context.dataStore.data.map { CardViewMode.fromName(it[searchViewModeKey]) }
    val collectionViewMode: Flow<CardViewMode> = context.dataStore.data.map { CardViewMode.fromName(it[collectionViewModeKey]) }
    val deckViewMode: Flow<CardViewMode> = context.dataStore.data.map { CardViewMode.fromName(it[deckViewModeKey]) }
    val allCardsViewMode: Flow<CardViewMode> = context.dataStore.data.map { CardViewMode.fromName(it[allCardsViewModeKey]) }
    val recViewMode: Flow<CardViewMode> = context.dataStore.data.map { CardViewMode.fromName(it[recViewModeKey]) }

    /** Shared tile size for every row-grid tab (Search/Binder/Deck/All Cards/REC). */
    val gridSize: Flow<GridSize> = context.dataStore.data.map { GridSize.fromName(it[gridSizeKey]) }

    /** Separate size for the card-detail page's suggestion grid, which is always a grid (no list mode). */
    val cardDetailGridSize: Flow<GridSize> = context.dataStore.data.map { GridSize.fromName(it[cardDetailGridSizeKey]) }

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

    suspend fun setGridSize(size: GridSize) {
        context.dataStore.edit { it[gridSizeKey] = size.name }
    }

    suspend fun setCardDetailGridSize(size: GridSize) {
        context.dataStore.edit { it[cardDetailGridSizeKey] = size.name }
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
