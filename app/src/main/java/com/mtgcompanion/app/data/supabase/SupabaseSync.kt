package com.mtgcompanion.app.data.supabase

import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException
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
internal data class ItemMeta(
    val hash: Int = 0,
    val editedMs: Long = 0L,
    val deleted: Boolean = false,
    /**
     * The item's JSON as last agreed with the server. When this device and another have both changed
     * the same deck since then, this is what the merge compares them against (see [ItemMerge]).
     * Null for items last synced by an older version, which fall back to newest-edit-wins.
     */
    val base: String? = null,
    /**
     * The edit time of the version in [base]. A push names it, and the server only lets the push
     * overwrite that version (push_library_items_v2).
     */
    val baseMs: Long? = null
)

/** One item of a push whose answer hasn't come back: its stamp, and its JSON (null for a deletion). */
internal data class SentItem(val ms: Long = 0L, val json: String? = null)

/** Per-device sync bookkeeping, stored as one JSON blob. Keys are "deck:<id>" / "collection:<id>". */
internal data class CloudSyncState(
    val items: Map<String, ItemMeta> = emptyMap(),
    /** Local edits not yet on the server: key -> edit time (0 = existed before this device first synced). */
    val pending: Map<String, Long> = emptyMap(),
    /** server_updated_at of the newest row already pulled. */
    val cursor: String? = null,
    val userId: String? = null,
    val lastSyncedAt: Long = 0L,
    /**
     * Items from a push the server partly skipped (it held a newer edit of some of them). The next
     * pass reads these rows back even if the cursor is past them, and merges where needed.
     */
    val refetch: List<String> = emptyList(),
    /**
     * A push sent whose answer hasn't come back (it may be lost). The next pass reads those rows back
     * to learn whether it landed — the row carries our stamp, or another device built on it — so our
     * change is never counted twice.
     */
    val sent: Map<String, SentItem> = emptyMap()
)

/**
 * [changes] (id -> new version, or null for deleted) applied to [current]. An item that changed on
 * this device since [snapshot] was taken gets the remote version merged into it rather than replaced;
 * a local deletion stands, and so does a local edit of something deleted elsewhere (the next pass
 * pushes it back).
 */
internal fun <T : Any> applyRemoteChanges(
    current: List<T>,
    snapshot: Map<String, T>,
    changes: Map<String, T?>,
    id: (T) -> String,
    merge: (base: T, mine: T, theirs: T) -> T
): List<T> {
    val out = current.toMutableList()
    changes.forEach { (itemId, item) ->
        val index = out.indexOfFirst { id(it) == itemId }
        val live = out.getOrNull(index)
        val was = snapshot[itemId]
        when {
            live == was -> when {
                item == null -> if (index >= 0) out.removeAt(index)
                index >= 0 -> out[index] = item
                else -> out.add(item)
            }
            item != null && live != null && was != null -> out[index] = merge(was, live, item)
        }
    }
    return out
}

/**
 * Keeps decks and binders in sync with Supabase, one row per item, so edits to different decks on
 * different devices never overwrite each other. Two devices that changed the SAME deck since they
 * last agreed have their edits merged card by card (see [ItemMerge]); only a field both changed
 * differently falls back to the more recent edit. Same-item conflicts resolve last-edit-wins by the
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
    private val core = SyncCore(deckAdapter, collectionAdapter)
    private var lastResumeSync = 0L
    private var pollJob: Job? = null
    private val realtime = SupabaseRealtime(auth, scope)
    private var liveChangeJob: Job? = null
    /** The app is on screen: live updates run only then. */
    @Volatile private var inForeground = false

    companion object {
        /** How often the app checks for other devices' edits while it's in the front. */
        const val POLL_INTERVAL_MS = 15_000L
        /** How soon after an edit it's sent: soon, so little is ever unsynced if the session ends. */
        private const val EDIT_SYNC_DELAY_MS = 1_000L
        /** While live updates are coming in ([SupabaseRealtime]), a check this often is enough as a backup. */
        private const val LIVE_POLL_INTERVAL_MS = 60_000L
        /** Several saves in quick succession (a whole push from another device) make one sync. */
        private const val LIVE_CHANGE_DELAY_MS = 400L
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
            scope.launch { removeLibraryOnSignOut() }
        }
    }

    /**
     * However an account goes — Sign out, or a session the server ended — its decks and binders leave
     * this phone. They're in the account, and come back on signing in again.
     */
    private suspend fun removeLibraryOnSignOut() {
        var signedIn: String? = null
        auth.account.collect { account ->
            // Also on a switch straight to another account, in case the signed-out moment was missed.
            if (signedIn != null && account?.userId != signedIn) {
                realtime.stop()
                mutex.withLock { removeLocalLibrary() }
            }
            signedIn = account?.userId
            // Signed in while the app is open: start listening for other devices' saves.
            if (account != null && inForeground) startLiveUpdates()
        }
    }

    /**
     * Removes the library and this device's sync bookkeeping. Called holding [mutex], so a pass that
     * was running finishes first — and whatever it saved is removed with the rest, rather than left to
     * make the next sign-in read the empty library as deletions.
     */
    private suspend fun removeLocalLibrary() {
        deckRepository.applySync { emptyList() }
        collectionRepository.applySync { emptyList() }
        context.supabaseSyncStore.edit { it.clear() }
        _status.value = CloudSyncStatus()
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
        return runSync()
    }

    /**
     * Called when the app comes to the foreground: checks for other devices' edits straight away (unless
     * a check ran moments ago) and then every [POLL_INTERVAL_MS] until [onAppPaused].
     */
    fun onAppResumed() {
        inForeground = true
        startLiveUpdates()
        val now = System.currentTimeMillis()
        if (now - lastResumeSync >= RESUME_SYNC_GAP_MS) {
            lastResumeSync = now
            if (auth.account.value != null) syncNow()
        }
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                // While live updates are coming in, a check a minute is enough as a backup.
                if (realtime.live && System.currentTimeMillis() - lastResumeSync < LIVE_POLL_INTERVAL_MS) continue
                if (auth.account.value != null) {
                    lastResumeSync = System.currentTimeMillis()
                    runSync(quiet = true)
                }
            }
        }
    }

    /** The app left the foreground: send anything not yet synced, then stop checking. */
    fun onAppPaused() {
        inForeground = false
        realtime.stop()
        pollJob?.cancel()
        pollJob = null
        if (auth.account.value != null) scope.launch { runSync(quiet = true) }
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

    /**
     * Syncs, then signs out and removes the account's decks and binders from this phone (they stay in
     * the account). If some changes couldn't be synced first, nothing happens and this returns how
     * many — pass [force] to sign out anyway. Returns 0 once signed out.
     */
    suspend fun signOut(force: Boolean = false): Int {
        if (!force && auth.account.value != null) {
            runSync()
            // A second pass picks up anything the first had to merge with another device's edit.
            if (unsyncedCount() > 0) runSync()
            val unsynced = unsyncedCount()
            if (unsynced > 0) return unsynced
        }
        mutex.withLock {
            auth.signOut()
            removeLocalLibrary()
        }
        return 0
    }

    /** Local changes not on the server yet, found the way a sync pass finds them. */
    private suspend fun unsyncedCount(): Int {
        val local = core.localJson(deckRepository.decksFlow.first(), collectionRepository.collectionsFlow.first())
        return core.notePending(loadState(), local, System.currentTimeMillis()).pending.size
    }

    /** Listens for other devices' saves while the app is open: one arrives here within a moment. */
    private fun startLiveUpdates() {
        val account = auth.account.value ?: return
        realtime.start(account.userId) {
            liveChangeJob?.cancel()
            liveChangeJob = scope.launch {
                delay(LIVE_CHANGE_DELAY_MS)
                lastResumeSync = System.currentTimeMillis()
                runSync(quiet = true)
            }
        }
    }

    /** Whether this device last saw [key] ("deck:<id>" / "collection:<id>") deleted on the server. */
    suspend fun isDeletedInCloud(key: String): Boolean = loadState().items[key]?.deleted == true

    private suspend fun observeLocalChanges() {
        combine(deckRepository.decksFlow, collectionRepository.collectionsFlow) { d, c -> d to c }
            .collectLatest {
                if (auth.account.value == null) return@collectLatest
                delay(EDIT_SYNC_DELAY_MS) // debounce: collectLatest restarts this if another edit lands first
                // Run the pass outside collectLatest: its own library write emits here, and a pass
                // cancelled halfway through could leave pulled changes recorded but not written.
                scope.launch { runSync() }
            }
    }

    private suspend fun loadState(): CloudSyncState =
        context.supabaseSyncStore.data.first()[stateKey]?.let { runCatching { stateAdapter.fromJson(it) }.getOrNull() } ?: CloudSyncState()

    private suspend fun saveState(state: CloudSyncState) {
        context.supabaseSyncStore.edit { it[stateKey] = stateAdapter.toJson(state) }
    }

    /** A [quiet] pass (the periodic check) is skipped if one is already running and doesn't show as syncing. */
    /** Returns the status this pass ended with, or null if it was skipped. */
    private suspend fun runSync(quiet: Boolean = false): CloudSyncStatus? {
        if (quiet && mutex.isLocked) return null
        return mutex.withLock { syncPass(quiet) }
    }

    private suspend fun syncPass(quiet: Boolean): CloudSyncStatus {
        val account = auth.account.value ?: return _status.value
        if (!quiet) _status.value = _status.value.copy(syncing = true, message = null, failed = false)
        try {
            var state = loadState()
            // A different account on this device starts from a clean slate (its items get merged in).
            if (state.userId != account.userId) state = CloudSyncState(userId = account.userId)

            val decks = deckRepository.decksFlow.first()
            val collections = collectionRepository.collectionsFlow.first()
            val local = core.localJson(decks, collections)

            // 1. Note what changed locally since the last agreement with the server. Kept before going
            //    near the network: otherwise an edit made offline is stamped with the time the device
            //    next reaches the server, and could overwrite a newer edit made elsewhere meanwhile.
            val now = System.currentTimeMillis()
            val noted = core.notePending(state, local, now)
            if (noted !== state) {
                state = noted
                saveState(state)
            }

            // 2. Pull everything newer than the cursor, plus rows to read back by key, and take them in.
            var token = auth.accessToken() ?: throw SupabaseAuthException("Signed out — sign in again to sync.")
            // A 401 means this access token was refused (a clock that's off, say): refresh once and retry.
            suspend fun freshToken(): String {
                auth.invalidateAccessToken()
                return auth.accessToken() ?: throw SupabaseAuthException("Signed out — sign in again to sync.")
            }
            val rows = try {
                orWithoutCas { pull(token, state.cursor) }
            } catch (e: UnauthorizedException) {
                token = freshToken()
                orWithoutCas { pull(token, state.cursor) }
            }
            val refetch = core.refetchKeys(state)
            val again = if (refetch.isEmpty()) emptyList() else try {
                orWithoutCas { fetchRows(token, refetch) }
            } catch (e: UnauthorizedException) {
                token = freshToken()
                orWithoutCas { fetchRows(token, refetch) }
            }
            val pulled = core.pull(state, local, rows, again, now)
            // Write what was pulled into the library first, then record it as agreed. The other way
            // round, a pass stopped in between (the app killed, say) leaves it marked agreed but never
            // written — and the next pass pushes the old copies back over it. This way round, the
            // worst case is pushing what was just pulled, which changes nothing.
            // Written as changes to the library as it is now, not as it was when this pass started: an
            // item edited meanwhile gets the remote version merged in, and nothing else is touched.
            if (pulled.deckChanges.isNotEmpty()) {
                val before = decks.associateBy { it.id }
                deckRepository.applySync { current ->
                    applyRemoteChanges(current, before, pulled.deckChanges, { it.id }) { b, m, t -> ItemMerge.mergeDecks(b, m, t, minePreferred = true) }
                }
            }
            if (pulled.collectionChanges.isNotEmpty()) {
                val before = collections.associateBy { it.id }
                collectionRepository.applySync { current ->
                    applyRemoteChanges(current, before, pulled.collectionChanges, { it.id }) { b, m, t -> ItemMerge.mergeCollections(b, m, t, minePreferred = true) }
                }
            }
            state = pulled.state
            saveState(state)

            // 3. Push what's still pending. The server only overwrites the version each item was merged
            //    from (or, before the compare-and-swap migration, anything older than the push).
            var pushedCount = 0
            val batch = core.pushBatch(state, pulled.local, now)
            if (batch.isNotEmpty()) {
                val body = JSONArray().apply { batch.forEach { put(it.toJsonObject()) } }
                // Note what's being sent first: if the answer is lost, the next pass can still tell
                // whether it landed.
                saveState(core.withSent(state, batch))
                val wrote: Set<String>? = if (casServer == false) null else try {
                    try {
                        pushV2(token, body)
                    } catch (e: UnauthorizedException) {
                        token = freshToken()
                        pushV2(token, body)
                    }.also { casServer = true }
                } catch (e: CasUnavailableException) {
                    casServer = false // no migration yet: push the old way
                    null
                }
                state = if (wrote != null) {
                    pushedCount = wrote.size
                    core.afterPushV2(state, batch, wrote)
                } else {
                    val written = try {
                        push(token, body)
                    } catch (e: UnauthorizedException) {
                        token = freshToken()
                        push(token, body)
                    }
                    pushedCount = written
                    // Which items landed. Usually all of them; if the server skipped some (it held a
                    // newer edit), read the rows straight back — each one still carrying our stamp is
                    // ours. Left to the next pass, another device could build on one of our writes
                    // first, and our change would count twice.
                    var landed: Set<String> = if (written == batch.size) batch.mapTo(HashSet()) { it.key } else emptySet()
                    if (landed.size < batch.size) {
                        val stamps = batch.associate { it.key to it.editedMs }
                        landed = runCatching {
                            fetchRows(token, batch.map { it.key }).filter { stamps[it.key] == it.editedMs }.mapTo(HashSet()) { it.key }
                        }.getOrDefault(emptySet()) // couldn't check: all read back next pass
                    }
                    core.afterPushV1(state, batch, landed)
                }
            }
            state = state.copy(lastSyncedAt = System.currentTimeMillis())
            saveState(state)
            _status.value = CloudSyncStatus(
                syncing = false, lastSyncedAt = state.lastSyncedAt, message = "Synced", pulled = pulled.pulled, pushed = pushedCount
            )
        } catch (e: SupabaseAuthException) {
            _status.value = _status.value.copy(syncing = false, message = e.message, failed = true)
        } catch (e: IOException) {
            val message = if (e is SyncServerUnavailableException) e.message else "Offline — will sync when you're back online."
            _status.value = _status.value.copy(syncing = false, message = message, failed = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _status.value = _status.value.copy(syncing = false, message = "Sync failed: ${e.message ?: e.javaClass.simpleName}", failed = true)
        }
        return _status.value
    }

    private fun PushItem.toJsonObject(): JSONObject {
        val item = JSONObject().put("kind", kind).put("id", id).put("edited_ms", editedMs)
            .put("base_edited_ms", base ?: JSONObject.NULL)
        return if (json == null) item.put("deleted", true) else item.put("deleted", false).put("data", JSONObject(json))
    }

    /**
     * Pages through rows newer than [cursor], oldest first — starting [PULL_OVERLAP_SECONDS] earlier.
     * A row is stamped when it's written but only seen once its push commits, so a slow push can land
     * behind rows this device already pulled past. Reading a row again is harmless: one this device
     * already has counts as agreed and changes nothing.
     */
    private suspend fun pull(token: String, cursor: String?): List<RemoteRow> = withContext(Dispatchers.IO) {
        val out = mutableListOf<RemoteRow>()
        var after = cursor?.let {
            runCatching { java.time.OffsetDateTime.parse(it).minusSeconds(PULL_OVERLAP_SECONDS).toString() }.getOrDefault(it)
        }
        while (true) {
            val url = (BuildConfig.SUPABASE_URL + "/rest/v1/library_items").toHttpUrl().newBuilder()
                .addQueryParameter("select", rowFields())
                .addQueryParameter("order", "server_updated_at.asc")
                .addQueryParameter("limit", "500")
                .apply { if (after != null) addQueryParameter("server_updated_at", "gt.$after") }
                .build()
            val page = fetchPage(token, url)
            out += page
            if (page.size < 500) break
            after = out.last().serverUpdatedAt
        }
        out
    }

    /** The current rows for [keys], wherever they sit relative to the cursor. */
    private suspend fun fetchRows(token: String, keys: List<String>): List<RemoteRow> = withContext(Dispatchers.IO) {
        keys.chunked(100).flatMap { chunk ->
            val ids = chunk.map { it.substringAfter(':') }.distinct()
                .joinToString(",") { "\"" + it.replace("\"", "\\\"") + "\"" }
            val url = (BuildConfig.SUPABASE_URL + "/rest/v1/library_items").toHttpUrl().newBuilder()
                .addQueryParameter("select", rowFields())
                .addQueryParameter("id", "in.($ids)")
                .build()
            fetchPage(token, url).filter { it.key in chunk }
        }
    }

    private fun fetchPage(token: String, url: okhttp3.HttpUrl): List<RemoteRow> {
        val request = Request.Builder().url(url)
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer $token")
            .get().build()
        val page = auth.http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 401) throw UnauthorizedException(serverError(response.code, text))
            if (isMissingCas(text)) throw CasUnavailableException()
            if (!response.isSuccessful) throw IllegalStateException(serverError(response.code, text))
            JSONArray(text)
        }
        return (0 until page.length()).map { i ->
            val o = page.getJSONObject(i)
            RemoteRow(
                kind = o.getString("kind"),
                id = o.getString("id"),
                data = if (o.isNull("data")) null else o.get("data").toString(),
                editedMs = o.optLong("edited_ms"),
                deleted = o.optBoolean("deleted"),
                serverUpdatedAt = o.getString("server_updated_at"),
                baseEditedMs = if (o.has("base_edited_ms") && !o.isNull("base_edited_ms")) o.getLong("base_edited_ms") else null
            )
        }
    }

    /**
     * Whether the server has compare-and-swap pushes (supabase/migrations/…_library_sync_cas.sql).
     * Until it does, sync works as before; learned from the first request that needs it.
     */
    @Volatile private var casServer: Boolean? = null

    private fun rowFields() = if (casServer == false) ROW_FIELDS else "$ROW_FIELDS,base_edited_ms"

    /** An unknown column (42703) or function (PGRST202): the migration hasn't been run. */
    private fun isMissingCas(body: String): Boolean =
        runCatching { JSONObject(body).optString("code") }.getOrNull() in setOf("42703", "PGRST202")

    /** Runs [call], and once more the old way if the server turns out not to have the migration. */
    private suspend fun <T> orWithoutCas(call: suspend () -> T): T = try {
        call()
    } catch (e: CasUnavailableException) {
        if (casServer == false) throw IllegalStateException("The sync server couldn't read the request.")
        casServer = false
        call()
    }

    /**
     * Pushes [items], each overwriting only the version it was merged from (its base_edited_ms).
     * Returns the keys the server wrote; anything else it skipped.
     */
    private suspend fun pushV2(token: String, items: JSONArray): Set<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(BuildConfig.SUPABASE_URL + "/rest/v1/rpc/push_library_items_v2")
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer $token")
            .post(JSONObject().put("items", items).toString().toRequestBody(JSON_MEDIA))
            .build()
        auth.http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 401) throw UnauthorizedException(serverError(response.code, text))
            if (isMissingCas(text)) throw CasUnavailableException()
            if (!response.isSuccessful) throw IllegalStateException(serverError(response.code, text))
            val list = JSONArray(text)
            (0 until list.length()).mapTo(HashSet()) { i ->
                val o = list.getJSONObject(i)
                o.getString("kind") + ":" + o.getString("id")
            }
        }
    }

    /** Returns how many rows the server wrote — it skips any item older than what it already holds. */
    private suspend fun push(token: String, items: JSONArray): Int = withContext(Dispatchers.IO) {
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
            text.trim().toIntOrNull() ?: items.length()
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

private const val ROW_FIELDS = "kind,id,data,edited_ms,deleted,server_updated_at"
private const val PULL_OVERLAP_SECONDS = 60L

/** The server doesn't have the compare-and-swap migration yet. */
private class CasUnavailableException : Exception("The sync server hasn't been updated yet.")

/** A data request's access token was refused (HTTP 401). */
private class UnauthorizedException(message: String) : Exception(message)
