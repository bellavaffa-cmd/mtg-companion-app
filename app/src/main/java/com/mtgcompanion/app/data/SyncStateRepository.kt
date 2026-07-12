package com.mtgcompanion.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.syncDataStore by preferencesDataStore(name = "sync")

/** Bookkeeping for Drive sync: what we last pushed/pulled and when the library last changed locally. */
data class SyncState(
    val lastSyncedHash: String = "",
    val lastSyncedRev: Long = 0L,
    val lastSyncedAt: Long = 0L,
    val localUpdatedAt: Long = 0L
)

class SyncStateRepository(private val context: Context) {

    private val hashKey = stringPreferencesKey("last_synced_hash")
    private val revKey = longPreferencesKey("last_synced_rev")
    private val atKey = longPreferencesKey("last_synced_at")
    private val localKey = longPreferencesKey("local_updated_at")

    suspend fun current(): SyncState {
        val prefs = context.syncDataStore.data.first()
        return SyncState(
            lastSyncedHash = prefs[hashKey] ?: "",
            lastSyncedRev = prefs[revKey] ?: 0L,
            lastSyncedAt = prefs[atKey] ?: 0L,
            localUpdatedAt = prefs[localKey] ?: 0L
        )
    }

    /** Record a local content change at [at] (wall-clock), for later last-write-wins comparison. */
    suspend fun markLocalChanged(at: Long) {
        context.syncDataStore.edit { it[localKey] = at }
    }

    /** Record a successful push/pull: [rev] is the payload's updatedAt, [hash] the synced content. */
    suspend fun markSynced(rev: Long, hash: String, at: Long) {
        context.syncDataStore.edit {
            it[revKey] = rev
            it[hashKey] = hash
            it[atKey] = at
            // The just-synced content is the new baseline, so it isn't "dirty" anymore.
            it[localKey] = rev
        }
    }

    suspend fun clear() {
        context.syncDataStore.edit { it.clear() }
    }
}
