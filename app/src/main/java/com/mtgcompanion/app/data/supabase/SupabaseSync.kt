package com.mtgcompanion.app.data.supabase

import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mtgcompanion.app.BuildConfig
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionRepository
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckRepository
import com.mtgcompanion.app.data.localMoshi
import com.squareup.moshi.JsonAdapter
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
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private val Context.supabaseSyncStore by preferencesDataStore(name = "supabase_sync")

data class CloudSyncStatus(
    val syncing: Boolean = false,
    val lastSyncedAt: Long = 0L,
    val message: String? = null,
    val failed: Boolean = false,
    /** Decks and binders the last pass brought down from other devices, and sent up from this one. */
    val pulled: Int = 0,
    val pushed: Int = 0
)

/** What this device last agreed with the server for one item. [hash] is 0 for a deletion. */
internal data class ItemMeta(val hash: Int = 0, val editedMs: Long = 0L, val deleted: Boolean = false)

/** Per-device sync bookkeeping, stored as one JSON blob. Keys are "deck:<id>" / "collection:<id>". */
internal data class CloudSyncState(
    val items: Map<String, ItemMeta> = emptyMap(),
    /** Local edits not yet on the server: key -> edit time (0 = existed before this device first synced). */
    val pending: Map<String, Long> = emptyMap(),
    /** server_updated_at of the newest row already pulled. */
    val cursor: String? = null,
    val userId: String? = null,
    val lastSyncedAt: Long = 0L
)

/**
 * Keeps decks and binders in sync with Supabase, one row per item, so edits to different decks on
 * different devices never overwrite each other. Same-item conflicts resolve last-edit-wins by the
 * time of the edit — the server enforces it too (push_library_items), so a slow device can't clobber
 * a newer change. Runs on launch, a moment after local edits, when the app returns to the front, and
 * every [POLL_INTERVAL_MS] while it stays in the front so other devices' edits show up without reopening it.
 *
 * The local DataStores stay the source the UI reads from, so everything keeps working offline and
 * while signed out.
 */
class SupabaseSync(
    private val context: Context,
    val auth: SupabaseAuth,
    private val deckRepository: DeckRepository,
    private val collectionRepository: CollectionRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val stateKey = stringPreferencesKey("state_json")
    private val stateAdapter: JsonAdapter<CloudSyncState> = localMoshi.adapter(CloudSyncState::class.java)
    private val deckAdapter: JsonAdapter<Deck> = localMoshi.adapter(Deck::class.java)
    private val collectionAdapter: JsonAdapter<Collection> = localMoshi.adapter(Collection::class.java)
    private var lastResumeSync = 0L
    private var pollJob: Job? = null

    companion object {
        /** How often the app checks for other devices' edits while it's in the front. */
        const val POLL_INTERVAL_MS = 20_000L
        /** Returning to the app checks at once, unless a check ran moments ago. */
        private const val RESUME_SYNC_GAP_MS = 5_000L
    }

    private val _status = MutableStateFlow(CloudSyncStatus())
    val status: StateFlow<CloudSyncStatus> = _status.asStateFlow()

    init {
        if (auth.configured) {
            scope.launch {
                auth.restore()
                _status.value = _status.value.copy(lastSyncedAt = loadState().lastSyncedAt)
                if (auth.account.value != null) runSync()
                observeLocalChanges()
            }
        }
    }

    fun syncNow() {
        scope.launch { runSync() }
    }

    /**
     * A sync the user asked for (pull to refresh): waits for it to finish and returns the outcome, or
     * null when there's no account to sync with.
     */
    suspend fun refresh(): CloudSyncStatus? {
        if (!auth.configured || auth.account.value == null) return null
        runSync()
        return _status.value
    }

    /**
     * Called when the app comes to the foreground: checks for other devices' edits straight away (unless
     * a check ran moments ago) and then every [POLL_INTERVAL_MS] until [onAppPaused].
     */
    fun onAppResumed() {
        val now = System.currentTimeMillis()
        if (now - lastResumeSync >= RESUME_SYNC_GAP_MS) {
            lastResumeSync = now
            if (auth.account.value != null) syncNow()
        }
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                if (auth.account.value != null) {
                    lastResumeSync = System.currentTimeMillis()
                    runSync(quiet = true)
                }
            }
        }
    }

    /** The app left the foreground: stop checking in the background. */
    fun onAppPaused() {
        pollJob?.cancel()
        pollJob = null
    }

    suspend fun signIn(email: String, password: String) {
        auth.signIn(email, password)
        runSync()
    }

    /** Returns true if the new account is already signed in (no email confirmation required). */
    suspend fun signUp(email: String, password: String): Boolean {
        val signedIn = auth.signUp(email, password)
        if (signedIn) runSync()
        return signedIn
    }

    suspend fun resendConfirmation(email: String) = auth.resendConfirmation(email)

    suspend fun sendPasswordReset(email: String) = auth.sendPasswordReset(email)

    /** Signs in from an email link that reopened the app, then syncs. */
    suspend fun completeLinkSignIn(link: android.net.Uri): SupabaseAccount {
        val account = auth.completeFromLink(link)
        scope.launch { runSync() }
        return account
    }

    /** Signs out and forgets this device's sync bookkeeping. Local decks and binders stay on the device. */
    suspend fun signOut() {
        mutex.withLock {
            auth.signOut()
            context.supabaseSyncStore.edit { it.clear() }
            _status.value = CloudSyncStatus()
        }
    }

    /** Whether this device last saw [key] ("deck:<id>" / "collection:<id>") deleted on the server. */
    suspend fun isDeletedInCloud(key: String): Boolean = loadState().items[key]?.deleted == true

    private suspend fun observeLocalChanges() {
        combine(deckRepository.decksFlow, collectionRepository.collectionsFlow) { d, c -> d to c }
            .collectLatest {
                if (auth.account.value == null) return@collectLatest
                delay(2_000) // debounce: collectLatest restarts this if another edit lands first
                runSync()
            }
    }

    private suspend fun loadState(): CloudSyncState =
        context.supabaseSyncStore.data.first()[stateKey]?.let { runCatching { stateAdapter.fromJson(it) }.getOrNull() } ?: CloudSyncState()

    private suspend fun saveState(state: CloudSyncState) {
        context.supabaseSyncStore.edit { it[stateKey] = stateAdapter.toJson(state) }
    }

    /** A [quiet] pass (the periodic check) is skipped if one is already running and doesn't show as syncing. */
    private suspend fun runSync(quiet: Boolean = false) {
        if (quiet && mutex.isLocked) return
        mutex.withLock { syncPass(quiet) }
    }

    private suspend fun syncPass(quiet: Boolean) {
        val account = auth.account.value ?: return
        if (!quiet) _status.value = _status.value.copy(syncing = true, message = null, failed = false)
        try {
            var state = loadState()
            // A different account on this device starts from a clean slate (its items get merged in).
            if (state.userId != account.userId) state = CloudSyncState(userId = account.userId)

            val decks = deckRepository.decksFlow.first()
            val collections = collectionRepository.collectionsFlow.first()
            val local = LinkedHashMap<String, String>() // key -> JSON
            decks.forEach { local["deck:${it.id}"] = deckAdapter.toJson(it) }
            collections.forEach { local["collection:${it.id}"] = collectionAdapter.toJson(it) }

            // 1. Note what changed locally since the last agreement with the server.
            val now = System.currentTimeMillis()
            val pending = state.pending.toMutableMap()
            local.forEach { (key, json) ->
                val meta = state.items[key]
                if (meta == null || meta.deleted || meta.hash != json.hashCode()) {
                    pending.putIfAbsent(key, if (meta == null && state.cursor == null) 0L else now)
                } else {
                    pending.remove(key)
                }
            }
            state.items.forEach { (key, meta) ->
                if (!meta.deleted && key !in local) pending.putIfAbsent(key, now)
            }
            // Keep when each change was first noticed before going near the network. Otherwise an edit
            // made offline is stamped with the time the device next reaches the server, and could
            // overwrite a newer edit of the same deck made on another device in the meantime.
            if (pending != state.pending) {
                state = state.copy(pending = pending)
                saveState(state)
            }

            // 2. Pull everything newer than the cursor, applying remote changes that aren't older
            //    than a pending local edit of the same item.
            var token = auth.accessToken() ?: throw SupabaseAuthException("Signed out — sign in again to sync.")
            // A 401 means this access token was refused (a clock that's off, say): refresh once and retry.
            suspend fun freshToken(): String {
                auth.invalidateAccessToken()
                return auth.accessToken() ?: throw SupabaseAuthException("Signed out — sign in again to sync.")
            }
            val rows = try {
                pull(token, state.cursor)
            } catch (e: UnauthorizedException) {
                token = freshToken()
                pull(token, state.cursor)
            }
            val items = state.items.toMutableMap()
            val deckList = decks.toMutableList()
            val collectionList = collections.toMutableList()
            var cursor = state.cursor
            var changedDecks = false
            var pulledCount = 0
            var pushedCount = 0
            var changedCollections = false
            rows.forEach { row ->
                cursor = row.serverUpdatedAt
                val localEdit = pending[row.key]
                if (localEdit != null && localEdit > row.editedMs) return@forEach // ours is newer; pushed below
                if (row.kind == "deck") {
                    val index = deckList.indexOfFirst { it.id == row.id }
                    if (row.deleted) {
                        if (index >= 0) { deckList.removeAt(index); changedDecks = true; pulledCount++ }
                        items[row.key] = ItemMeta(0, row.editedMs, deleted = true)
                    } else {
                        val deck = row.data?.let { runCatching { deckAdapter.fromJson(it) }.getOrNull() } ?: return@forEach
                        val hash = deckAdapter.toJson(deck).hashCode()
                        // Our own push coming back, or already identical: just record agreement.
                        if (local[row.key]?.hashCode() != hash) {
                            if (index >= 0) deckList[index] = deck else deckList.add(deck)
                            changedDecks = true
                            pulledCount++
                        }
                        items[row.key] = ItemMeta(hash, row.editedMs)
                    }
                } else {
                    val index = collectionList.indexOfFirst { it.id == row.id }
                    if (row.deleted) {
                        if (index >= 0) { collectionList.removeAt(index); changedCollections = true; pulledCount++ }
                        items[row.key] = ItemMeta(0, row.editedMs, deleted = true)
                    } else {
                        val collection = row.data?.let { runCatching { collectionAdapter.fromJson(it) }.getOrNull() } ?: return@forEach
                        val hash = collectionAdapter.toJson(collection).hashCode()
                        if (local[row.key]?.hashCode() != hash) {
                            if (index >= 0) collectionList[index] = collection else collectionList.add(collection)
                            changedCollections = true
                            pulledCount++
                        }
                        items[row.key] = ItemMeta(hash, row.editedMs)
                    }
                }
                pending.remove(row.key)
            }
            // Record the pulled state before writing it locally, so the change observer sees it as synced.
            state = state.copy(items = items, pending = pending, cursor = cursor)
            saveState(state)
            if (changedDecks) deckRepository.replaceAll(deckList)
            if (changedCollections) collectionRepository.replaceAll(collectionList)

            // 3. Push what's still pending. The server skips anything older than what it has; the
            //    next pull then brings that newer version down.
            if (pending.isNotEmpty()) {
                val batch = JSONArray()
                val pushed = mutableMapOf<String, ItemMeta>()
                pending.forEach { (key, editedAt) ->
                    val (kind, id) = key.split(":", limit = 2)
                    val editedMs = if (editedAt == 0L) now else editedAt
                    val json = when (kind) {
                        "deck" -> deckList.firstOrNull { it.id == id }?.let { deckAdapter.toJson(it) }
                        else -> collectionList.firstOrNull { it.id == id }?.let { collectionAdapter.toJson(it) }
                    }
                    val item = JSONObject().put("kind", kind).put("id", id).put("edited_ms", editedMs)
                    if (json == null) {
                        item.put("deleted", true)
                        pushed[key] = ItemMeta(0, editedMs, deleted = true)
                    } else {
                        item.put("deleted", false).put("data", JSONObject(json))
                        pushed[key] = ItemMeta(json.hashCode(), editedMs)
                    }
                    batch.put(item)
                }
                try {
                    push(token, batch)
                } catch (e: UnauthorizedException) {
                    token = freshToken()
                    push(token, batch)
                }
                pushedCount = pending.size
                state = state.copy(items = state.items + pushed, pending = emptyMap())
            }
            state = state.copy(lastSyncedAt = System.currentTimeMillis())
            saveState(state)
            _status.value = CloudSyncStatus(
                syncing = false, lastSyncedAt = state.lastSyncedAt, message = "Synced", pulled = pulledCount, pushed = pushedCount
            )
        } catch (e: SupabaseAuthException) {
            _status.value = _status.value.copy(syncing = false, message = e.message, failed = true)
        } catch (e: IOException) {
            val message = if (e is SyncServerUnavailableException) e.message else "Offline — will sync when you're back online."
            _status.value = _status.value.copy(syncing = false, message = message, failed = true)
        } catch (e: Exception) {
            _status.value = _status.value.copy(syncing = false, message = "Sync failed: ${e.message ?: e.javaClass.simpleName}", failed = true)
        }
    }

    private data class RemoteRow(
        val kind: String,
        val id: String,
        val data: String?,
        val editedMs: Long,
        val deleted: Boolean,
        val serverUpdatedAt: String
    ) {
        val key get() = "$kind:$id"
    }

    /** Pages through rows newer than [cursor], oldest first. */
    private suspend fun pull(token: String, cursor: String?): List<RemoteRow> = withContext(Dispatchers.IO) {
        val out = mutableListOf<RemoteRow>()
        var after = cursor
        while (true) {
            val url = (BuildConfig.SUPABASE_URL + "/rest/v1/library_items").toHttpUrl().newBuilder()
                .addQueryParameter("select", "kind,id,data,edited_ms,deleted,server_updated_at")
                .addQueryParameter("order", "server_updated_at.asc")
                .addQueryParameter("limit", "500")
                .apply { if (after != null) addQueryParameter("server_updated_at", "gt.$after") }
                .build()
            val request = Request.Builder().url(url)
                .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer $token")
                .get().build()
            val page = auth.http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (response.code == 401) throw UnauthorizedException(serverError(response.code, text))
                if (!response.isSuccessful) throw IllegalStateException(serverError(response.code, text))
                JSONArray(text)
            }
            for (i in 0 until page.length()) {
                val o = page.getJSONObject(i)
                out += RemoteRow(
                    kind = o.getString("kind"),
                    id = o.getString("id"),
                    data = if (o.isNull("data")) null else o.get("data").toString(),
                    editedMs = o.optLong("edited_ms"),
                    deleted = o.optBoolean("deleted"),
                    serverUpdatedAt = o.getString("server_updated_at")
                )
            }
            if (page.length() < 500) break
            after = out.last().serverUpdatedAt
        }
        out
    }

    private suspend fun push(token: String, items: JSONArray) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(BuildConfig.SUPABASE_URL + "/rest/v1/rpc/push_library_items")
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer $token")
            .post(JSONObject().put("items", items).toString().toRequestBody(JSON_MEDIA))
            .build()
        auth.http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 401) throw UnauthorizedException(serverError(response.code, text))
            if (!response.isSuccessful) throw IllegalStateException(serverError(response.code, text))
        }
    }

    private fun serverError(code: Int, body: String): String {
        val message = runCatching { JSONObject(body).optString("message") }.getOrNull().orEmpty()
        return when {
            code == 404 || message.contains("library_items", ignoreCase = true) && message.contains("does not exist", ignoreCase = true) ->
                "The sync tables aren't set up yet — run the SQL setup script in Supabase."
            code == 401 -> "The server didn't accept this sign-in. Try again, or sign out and back in."
            message.isNotBlank() -> "$message (HTTP $code)"
            else -> "Server error (HTTP $code)"
        }
    }
}

/** A data request's access token was refused (HTTP 401). */
private class UnauthorizedException(message: String) : Exception(message)
