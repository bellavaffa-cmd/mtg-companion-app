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

class SettingsRepository(private val context: Context) {

    private val clientIdKey = stringPreferencesKey("tcgplayer_client_id")
    private val clientSecretKey = stringPreferencesKey("tcgplayer_client_secret")
    private val searchViewModeKey = stringPreferencesKey("search_view_mode")
    private val collectionViewModeKey = stringPreferencesKey("collection_view_mode")
    private val deckViewModeKey = stringPreferencesKey("deck_view_mode")
    private val allCardsViewModeKey = stringPreferencesKey("allcards_view_mode")
    private val recViewModeKey = stringPreferencesKey("rec_view_mode")
    private val gridColumnsKey = intPreferencesKey("grid_columns")

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
