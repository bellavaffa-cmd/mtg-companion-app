package com.mtgcompanion.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.syncDataStore by preferencesDataStore(name = "sync")

/**
 * Bookkeeping left by the retired Google Drive sync. All that's still read is whether this device
 * used it (so the Drive import is offered); the rest of what it stored is cleared with [clear].
 */
class SyncStateRepository(private val context: Context) {

    private val lastSyncedAtKey = longPreferencesKey("last_synced_at")

    /** Whether this device ever synced through Google Drive. */
    suspend fun usedDrive(): Boolean = (context.syncDataStore.data.first()[lastSyncedAtKey] ?: 0L) > 0

    suspend fun clear() {
        context.syncDataStore.edit { it.clear() }
    }
}
