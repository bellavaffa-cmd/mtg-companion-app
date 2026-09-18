package com.mtgcompanion.app.data

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.mtgcompanion.app.network.drive.GoogleDriveClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** OAuth scope for files this app creates in Drive (not broad Drive access). */
const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

class DriveImportException(message: String) : Exception(message)

/** What an import added. [foundBackup] is false when the Google account had no backup file. */
data class DriveImportResult(val foundBackup: Boolean, val addedDecks: Int = 0, val addedCollections: Int = 0)

/**
 * One-time move off the retired Google Drive sync. Drive sync kept the whole library in a single
 * backup file and overwrote it last-write-wins, which fights the per-item account sync — so it no
 * longer runs. Instead, people who used it can import their last backup once: decks and binders
 * missing on this phone are added (and then sync to their account like any other change), anything
 * already here is kept as-is. Afterwards the app disconnects from Google.
 */
class DriveImporter(
    private val context: Context,
    private val deckRepository: DeckRepository,
    private val collectionRepository: CollectionRepository,
    private val syncState: SyncStateRepository,
    private val drive: GoogleDriveClient = GoogleDriveClient()
) {
    private val payloadAdapter = localMoshi.adapter(SyncPayload::class.java)

    private val _usedDrive = MutableStateFlow(GoogleSignIn.getLastSignedInAccount(context) != null)
    /** True while this device still has a Drive connection or Drive sync history to move over. */
    val usedDrive: StateFlow<Boolean> = _usedDrive.asStateFlow()

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            if (syncState.current().lastSyncedAt > 0) _usedDrive.value = true
        }
    }

    val isGoogleSignedIn: Boolean get() = GoogleSignIn.getLastSignedInAccount(context) != null

    fun signInClient(): GoogleSignInClient = GoogleSignIn.getClient(
        context,
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DRIVE_FILE_SCOPE))
            .build()
    )

    /** Turns a failed Google sign-in into something readable (null when the user just backed out). */
    fun signInError(error: Throwable?): String? = when (val code = (error as? ApiException)?.statusCode) {
        GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> null
        GoogleSignInStatusCodes.NETWORK_ERROR -> "Can't reach Google — check your connection."
        GoogleSignInStatusCodes.DEVELOPER_ERROR -> "Google sign-in isn't set up for this build (code 10)."
        null -> "Google sign-in failed: ${error?.message ?: "unknown error"}"
        else -> "Google sign-in failed: ${GoogleSignInStatusCodes.getStatusCodeString(code)} (code $code)"
    }

    /**
     * Adds the backup's decks and binders that aren't on this phone. [skip] vetoes items by
     * "deck:<id>" / "collection:<id>" key — used to keep decks deleted through account sync deleted.
     * Throws [com.google.android.gms.auth.UserRecoverableAuthException] when Google needs the user
     * to approve Drive access again; launch its intent and retry.
     */
    suspend fun import(skip: suspend (String) -> Boolean): DriveImportResult = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context)?.account
            ?: throw DriveImportException("Sign in to Google first.")
        val token = GoogleAuthUtil.getToken(context, account, "oauth2:$DRIVE_FILE_SCOPE")
        val fileId = drive.findFolder(token)?.let { drive.findBackup(token, it) }
        if (fileId == null) {
            disconnect()
            return@withContext DriveImportResult(foundBackup = false)
        }
        val backup = runCatching { payloadAdapter.fromJson(drive.downloadText(token, fileId)) }.getOrNull()
            ?: throw DriveImportException("The Google Drive backup couldn't be read.")

        val decks = deckRepository.decksFlow.first()
        val deckIds = decks.map { it.id }.toSet()
        val newDecks = backup.decks.filter { it.id !in deckIds && !skip("deck:${it.id}") }
        if (newDecks.isNotEmpty()) deckRepository.applySync { current -> current + newDecks.filter { n -> current.none { it.id == n.id } } }

        val collections = collectionRepository.collectionsFlow.first()
        val collectionIds = collections.map { it.id }.toSet()
        val newCollections = backup.collections.filter { it.id !in collectionIds && !skip("collection:${it.id}") }
        if (newCollections.isNotEmpty()) collectionRepository.applySync { current -> current + newCollections.filter { n -> current.none { it.id == n.id } } }

        disconnect()
        DriveImportResult(foundBackup = true, addedDecks = newDecks.size, addedCollections = newCollections.size)
    }

    /** Forgets the Google connection and old Drive bookkeeping. The backup file in Drive is left alone. */
    suspend fun disconnect() {
        runCatching { signInClient().signOut() }
        syncState.clear()
        _usedDrive.value = false
    }
}
