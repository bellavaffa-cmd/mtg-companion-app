package com.mtgcompanion.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val clientIdKey = stringPreferencesKey("tcgplayer_client_id")
    private val clientSecretKey = stringPreferencesKey("tcgplayer_client_secret")

    val tcgPlayerClientId: Flow<String?> = context.dataStore.data.map { it[clientIdKey] }
    val tcgPlayerClientSecret: Flow<String?> = context.dataStore.data.map { it[clientSecretKey] }

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
