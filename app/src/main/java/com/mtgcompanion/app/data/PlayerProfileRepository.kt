package com.mtgcompanion.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.playerProfilesDataStore by preferencesDataStore(name = "player_profiles")

/** A saved Life Counter player preset (name + seat color) reusable across games. */
data class PlayerProfile(val name: String, val colorIndex: Int)

private data class PlayerProfileStore(val profiles: List<PlayerProfile> = emptyList())

class PlayerProfileRepository(private val context: Context) {
    private val key = stringPreferencesKey("profiles_json")
    private val adapter = localMoshi.adapter(PlayerProfileStore::class.java)

    val profilesFlow: Flow<List<PlayerProfile>> = context.playerProfilesDataStore.data.map { prefs ->
        prefs[key]?.let { json -> runCatching { adapter.fromJson(json)?.profiles }.getOrNull() } ?: emptyList()
    }

    /** Saving under an existing name overwrites that profile. */
    suspend fun saveProfile(profile: PlayerProfile) {
        update { profiles -> profiles.filterNot { it.name.equals(profile.name, ignoreCase = true) } + profile }
    }

    suspend fun deleteProfile(name: String) {
        update { profiles -> profiles.filterNot { it.name.equals(name, ignoreCase = true) } }
    }

    private suspend fun update(transform: (List<PlayerProfile>) -> List<PlayerProfile>) {
        context.playerProfilesDataStore.edit { prefs ->
            val current = prefs[key]?.let { runCatching { adapter.fromJson(it)?.profiles }.getOrNull() } ?: emptyList()
            prefs[key] = adapter.toJson(PlayerProfileStore(transform(current)))
        }
    }
}
