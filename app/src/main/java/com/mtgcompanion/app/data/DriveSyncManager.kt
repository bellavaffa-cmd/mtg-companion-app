package com.mtgcompanion.app.data

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.mtgcompanion.app.network.drive.GoogleDriveClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/** OAuth scope for files this app creates in Drive (not broad Drive access). */
const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

data class SyncUiStatus(
    val connectedEmail: String? = null,
    val syncing: Boolean = false,
    val lastSyncedAt: Long = 0L,
    val message: String? = null
)

/**
 * Keeps the local deck + collection library in sync with a single backup file in Google Drive.
 *
 * Sync is automatic: it runs on launch (when connected) and, debounced, whenever the library
 * changes locally. Change detection is by content hash so re-applying a pulled backup doesn't loop.
 * Conflicts resolve last-write-wins by the wall-clock time of the edit — the most recently changed
 * device wins the whole library.
 */
class DriveSyncManager(
    private val context: Context,
    private val deckRepository: DeckRepository,
    private val collectionRepository: CollectionRepository,
    private val syncState: SyncStateRepository,
    private val drive: GoogleDriveClient = GoogleDriveClient()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val payloadAdapter = localMoshi.adapter(SyncPayload::class.java)
    private val applyingRemote = AtomicBoolean(false)
    private val syncMutex = Mutex()

    private val _status = MutableStateFlow(SyncUiStatus())
    val status: StateFlow<SyncUiStatus> = _status.asStateFlow()

    init {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        _status.value = SyncUiStatus(connectedEmail = account?.email)
        scope.launch {
            _status.value = _status.value.copy(lastSyncedAt = syncState.current().lastSyncedAt)
            if (account != null) runSync()
        }
        observeLocalChanges()
    }

    fun signInClient(): GoogleSignInClient = GoogleSignIn.getClient(context, signInOptions())

    fun onSignedIn(account: GoogleSignInAccount) {
        _status.value = _status.value.copy(connectedEmail = account.email, message = null)
        syncNow()
    }

    fun syncNow() {
        scope.launch { runSync() }
    }

    fun signOut() {
        scope.launch {
            runCatching { signInClient().signOut() }
            syncState.clear()
            _status.value = SyncUiStatus(message = "Disconnected.")
        }
    }

    private fun signInOptions(): GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DRIVE_FILE_SCOPE))
            .build()

    private fun observeLocalChanges() {
        scope.launch {
            combine(deckRepository.decksFlow, collectionRepository.collectionsFlow) { decks, collections ->
                decks to collections
            }.collectLatest { (decks, collections) ->
                if (applyingRemote.get()) return@collectLatest
                if (contentHash(decks, collections) == syncState.current().lastSyncedHash) return@collectLatest
                syncState.markLocalChanged(System.currentTimeMillis())
                if (GoogleSignIn.getLastSignedInAccount(context) == null) return@collectLatest
                delay(1500) // collectLatest cancels this if another edit lands first (debounce)
                runSync()
            }
        }
    }

    private suspend fun runSync() = syncMutex.withLock {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return
        _status.value = _status.value.copy(syncing = true, message = null)
        try {
            val token = GoogleAuthUtil.getToken(context, account.account!!, "oauth2:$DRIVE_FILE_SCOPE")
            val folderId = drive.ensureFolder(token)
            val remoteId = drive.findBackup(token, folderId)
            val remote = remoteId?.let {
                runCatching { payloadAdapter.fromJson(drive.downloadText(token, it)) }.getOrNull()
            }

            val decks = deckRepository.decksFlow.first()
            val collections = collectionRepository.collectionsFlow.first()
            val localHash = contentHash(decks, collections)
            val st = syncState.current()
            val localDirty = localHash != st.lastSyncedHash
            val remoteRev = remote?.updatedAt ?: -1L
            val remoteDirty = remote != null && remoteRev != st.lastSyncedRev
            val localRev = if (st.localUpdatedAt > st.lastSyncedRev) st.localUpdatedAt else System.currentTimeMillis()

            when {
                remote == null -> pushLocal(token, folderId, remoteId, decks, collections, localRev)
                remoteDirty && !localDirty -> applyRemote(remote)
                localDirty && !remoteDirty -> pushLocal(token, folderId, remoteId, decks, collections, localRev)
                localDirty && remoteDirty ->
                    if (remoteRev > localRev) applyRemote(remote)
                    else pushLocal(token, folderId, remoteId, decks, collections, localRev)
                else -> { /* already in sync */ }
            }
            _status.value = _status.value.copy(
                syncing = false,
                lastSyncedAt = syncState.current().lastSyncedAt,
                message = "Synced"
            )
        } catch (e: Exception) {
            _status.value = _status.value.copy(syncing = false, message = e.message ?: "Sync failed")
        }
    }

    private suspend fun pushLocal(
        token: String,
        folderId: String,
        fileId: String?,
        decks: List<Deck>,
        collections: List<Collection>,
        rev: Long
    ) {
        val json = payloadAdapter.toJson(SyncPayload(decks, collections, rev))
        drive.uploadBackup(token, folderId, fileId, json)
        syncState.markSynced(rev, contentHash(decks, collections), System.currentTimeMillis())
    }

    private suspend fun applyRemote(remote: SyncPayload) {
        applyingRemote.set(true)
        try {
            deckRepository.replaceAll(remote.decks)
            collectionRepository.replaceAll(remote.collections)
            syncState.markSynced(remote.updatedAt, contentHash(remote.decks, remote.collections), System.currentTimeMillis())
        } finally {
            applyingRemote.set(false)
        }
    }

    /** Stable hash of the library content (order-preserving), independent of the updatedAt stamp. */
    private fun contentHash(decks: List<Deck>, collections: List<Collection>): String =
        payloadAdapter.toJson(SyncPayload(decks, collections, 0L)).hashCode().toString()
}
