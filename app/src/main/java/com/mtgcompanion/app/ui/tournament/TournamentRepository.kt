package com.mtgcompanion.app.ui.tournament

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mtgcompanion.app.data.tournament.Tournament
import com.mtgcompanion.app.data.tournament.withEvent
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.tournamentsDataStore by preferencesDataStore(name = "tournaments")

/**
 * The events run on this phone, kept so one survives the app closing mid-round. Stored as the same
 * JSON the web app keeps in its browser (src/tournament/events.ts).
 */
class TournamentRepository(private val context: Context) {
    private val key = stringPreferencesKey("tournaments_json")
    private val adapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        .adapter<List<Tournament>>(Types.newParameterizedType(List::class.java, Tournament::class.java))

    /** The events on this phone, newest first. */
    val events: Flow<List<Tournament>> = context.tournamentsDataStore.data.map { prefs ->
        prefs[key]?.let { json -> runCatching { adapter.fromJson(json) }.getOrNull() }.orEmpty()
    }

    private suspend fun update(transform: (List<Tournament>) -> List<Tournament>) {
        context.tournamentsDataStore.edit { prefs ->
            val current = prefs[key]?.let { runCatching { adapter.fromJson(it) }.getOrNull() }.orEmpty()
            prefs[key] = adapter.toJson(transform(current))
        }
    }

    suspend fun save(t: Tournament) = update { withEvent(it, t) }

    suspend fun delete(id: String) = update { events -> events.filterNot { it.id == id } }
}
