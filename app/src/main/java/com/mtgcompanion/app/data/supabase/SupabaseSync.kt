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
    private var lastResumeSync = 0L
    private var pollJob: Job? = null

    companion object {
        /** How often the app checks for other devices' edits while it's in the front. */
        const val POLL_INTERVAL_MS = 15_000L
        /** How soon after an edit it's sent: soon, so little is ever unsynced if the session ends. */
        private const val EDIT_SYNC_DELAY_MS = 1_000L
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
            if (signedIn != null && account?.userId != signedIn) mutex.withLock { removeLocalLibrary() }
            signedIn = account?.userId
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

    /** The app left the foreground: send anything not yet synced, then stop checking. */
    fun onAppPaused() {
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
        val state = loadState()
        val local = HashMap<String, String>()
        deckRepository.decksFlow.first().forEach { local["deck:${it.id}"] = deckAdapter.toJson(it) }
        collectionRepository.collectionsFlow.first().forEach { local["collection:${it.id}"] = collectionAdapter.toJson(it) }
        val changed = local.count { (key, json) ->
            val meta = state.items[key]
            meta == null || meta.deleted || meta.hash != json.hashCode()
        }
        val removed = state.items.count { (key, meta) -> !meta.deleted && key !in local }
        return changed + removed
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
            // key -> JSON. A merge below replaces an entry here, so the merged version is what gets pushed.
            val local = LinkedHashMap<String, String>()
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
                orWithoutCas { pull(token, state.cursor) }
            } catch (e: UnauthorizedException) {
                token = freshToken()
                orWithoutCas { pull(token, state.cursor) }
            }
            // Rows a skipped push left behind, and those of a push whose answer never came — unless
            // the cursor pull already brought them.
            val inPull = rows.mapTo(HashSet()) { it.key }
            val refetch = (state.refetch + state.sent.keys).distinct()
            val again = if (refetch.isEmpty()) emptyList() else try {
                orWithoutCas { fetchRows(token, refetch) }
            } catch (e: UnauthorizedException) {
                token = freshToken()
                orWithoutCas { fetchRows(token, refetch) }
            }.filter { it.key !in inPull }

            val items = state.items.toMutableMap()
            // Remote changes to write locally: id -> the new version, or null to delete it.
            val deckChanges = LinkedHashMap<String, Deck?>()
            val collectionChanges = LinkedHashMap<String, Collection?>()
            var cursor = state.cursor
            var pulledCount = 0
            var pushedCount = 0

            /** Takes in one row; false if this device can't read it, so it's left for a later pass. */
            fun <T : Any> takeRow(
                row: RemoteRow,
                adapter: JsonAdapter<T>,
                changes: MutableMap<String, T?>,
                emptyBase: (T) -> T,
                merge: (base: T, mine: T, theirs: T, minePreferred: Boolean) -> T
            ): Boolean {
                val key = row.key
                val localEdit = pending[key]
                val mineJson = local[key]
                if (row.deleted) {
                    // Deleted elsewhere: it goes, unless this device edited it more recently.
                    if (localEdit != null && localEdit > row.editedMs) return true
                    if (mineJson != null) { changes[row.id] = null; pulledCount++ }
                    items[key] = ItemMeta(0, row.editedMs, deleted = true, baseMs = row.editedMs)
                    pending.remove(key)
                    return true
                }
                val theirs = row.data?.let { runCatching { adapter.fromJson(it) }.getOrNull() } ?: return false
                val theirJson = adapter.toJson(theirs)
                val meta = state.items[key]
                val sent = state.sent[key]
                // A push whose answer was lost did land: the row still carries its stamp and content.
                val sentLanded = sent != null && sent.ms == row.editedMs && sent.json?.hashCode() == theirJson.hashCode()
                // This device's own write coming back (or a row it already agreed on): stamped with the
                // edit time it last pushed, and holding what it pushed. That's now the agreed version.
                // The content check matters: another device merging from the same row can land on the
                // very same stamp.
                if (sentLanded || meta != null && !meta.deleted && meta.editedMs == row.editedMs &&
                    (meta.hash == theirJson.hashCode() || meta.base == theirJson)
                ) {
                    val ownHash = if (sentLanded) sent!!.json!!.hashCode() else meta!!.hash
                    items[key] = ItemMeta(ownHash, row.editedMs, base = theirJson, baseMs = row.editedMs)
                    if (mineJson != null && mineJson.hashCode() != ownHash) {
                        // Edited again since: that edit is simply pushed — nothing from elsewhere to merge.
                        pending[key] = maxOf(pending[key] ?: now, row.editedMs + 1)
                    } else if (mineJson != null) {
                        pending.remove(key)
                    }
                    return true
                }
                // Another device merged from a push of ours whose answer was lost: it landed, and it's
                // the version to merge against — merging from the older one would count our change twice.
                val builtOnOurs = sent != null && row.baseEditedMs != null && row.baseEditedMs == sent.ms
                val baseJson = if (builtOnOurs) sent!!.json else meta?.base
                val base = baseJson?.let { runCatching { adapter.fromJson(it) }.getOrNull() }
                // This device has diverged if its copy differs from the version both sides last
                // agreed on — which stays true even when a push was skipped as stale server-side.
                val diverged = mineJson != null && base != null && mineJson != baseJson
                // This device has the item but has never agreed a version of it with the server — its
                // first sync, say, with the same deck already in the cloud (both from the old Drive
                // sync). With nothing to compare against, keep every card from both rather than letting
                // the cloud copy replace this one. Not tied to a pending edit: a first push the server
                // skipped leaves none.
                val firstMeeting = mineJson != null && meta == null && !builtOnOurs
                // Both devices changed it since they last agreed: keep both sets of edits.
                if ((diverged || firstMeeting) && mineJson != theirJson) {
                    val mine = adapter.fromJson(mineJson!!) ?: return false
                    // First meeting: an empty base makes every card an addition from both sides, and
                    // the cloud's name and settings win.
                    val merged = merge(base ?: emptyBase(mine), mine, theirs, (localEdit ?: 0L) > row.editedMs)
                    val mergedJson = adapter.toJson(merged)
                    if (mergedJson != mineJson) { changes[row.id] = merged; pulledCount++ }
                    // Push the merged version, stamped past their edit so the server can't reject it
                    // as stale, and keep their version as the new base.
                    local[key] = mergedJson
                    pending[key] = maxOf(now, row.editedMs + 1)
                    items[key] = ItemMeta(theirJson.hashCode(), row.editedMs, base = theirJson, baseMs = row.editedMs)
                    return true
                }
                if (localEdit != null && localEdit > row.editedMs) return true // ours is newer; pushed below
                if (mineJson != theirJson) { changes[row.id] = theirs; pulledCount++ }
                items[key] = ItemMeta(theirJson.hashCode(), row.editedMs, base = theirJson, baseMs = row.editedMs)
                pending.remove(key)
                return true
            }

            // Once a row can't be read, the cursor stays behind it — later rows are read again next
            // time, which changes nothing — so an update that can read it still gets to see it.
            var stuck = false
            (again + rows).forEach { row ->
                val taken = if (row.kind == "deck") {
                    takeRow(row, deckAdapter, deckChanges, { mine ->
                        mine.copy(cards = emptyList(), considering = emptyList(), tags = emptyList(), gameResults = emptyList(), versions = emptyList())
                    }) { b, m, t, p -> ItemMerge.mergeDecks(b, m, t, minePreferred = p) }
                } else {
                    takeRow(row, collectionAdapter, collectionChanges, { mine -> mine.copy(entries = emptyList()) }) { b, m, t, p ->
                        ItemMerge.mergeCollections(b, m, t, minePreferred = p)
                    }
                }
                if (!taken) stuck = true
                // A re-fetched row, or one re-read from the overlap, never moves the cursor backwards.
                if (taken && !stuck && row.key in inPull && (cursor == null || row.serverUpdatedAt > cursor!!)) {
                    cursor = row.serverUpdatedAt
                }
            }
            // Write what was pulled into the library first, then record it as agreed. The other way
            // round, a pass stopped in between (the app killed, say) leaves it marked agreed but never
            // written — and the next pass pushes the old copies back over it. This way round, the
            // worst case is pushing what was just pulled, which changes nothing.
            // Written as changes to the library as it is now, not as it was when this pass started: an
            // item edited meanwhile gets the remote version merged in, and nothing else is touched.
            if (deckChanges.isNotEmpty()) {
                val before = decks.associateBy { it.id }
                deckRepository.applySync { current ->
                    applyRemoteChanges(current, before, deckChanges, { it.id }) { b, m, t -> ItemMerge.mergeDecks(b, m, t, minePreferred = true) }
                }
            }
            if (collectionChanges.isNotEmpty()) {
                val before = collections.associateBy { it.id }
                collectionRepository.applySync { current ->
                    applyRemoteChanges(current, before, collectionChanges, { it.id }) { b, m, t -> ItemMerge.mergeCollections(b, m, t, minePreferred = true) }
                }
            }
            // Every unanswered push has now been read back (a row it never reached leaves its edit pending).
            state = state.copy(items = items, pending = pending, cursor = cursor, refetch = emptyList(), sent = emptyMap())
            saveState(state)

            // 3. Push what's still pending. The server only overwrites the version each item was merged
            //    from (or, before the compare-and-swap migration, anything older than the push).
            if (pending.isNotEmpty()) {
                val batch = JSONArray()
                val sent = LinkedHashMap<String, Pair<Long, String?>>()
                pending.forEach { (key, editedAt) ->
                    val (kind, id) = key.split(":", limit = 2)
                    val editedMs = if (editedAt == 0L) now else editedAt
                    // A merged item was written into `local` above.
                    val json = local[key]
                    // The version this was merged from; the server only lets the push overwrite that one.
                    val base = state.items[key]?.let { it.baseMs ?: it.editedMs }
                    val item = JSONObject().put("kind", kind).put("id", id).put("edited_ms", editedMs)
                        .put("base_edited_ms", base ?: JSONObject.NULL)
                    if (json == null) item.put("deleted", true)
                    else item.put("deleted", false).put("data", JSONObject(json))
                    sent[key] = editedMs to json
                    batch.put(item)
                }
                // Note what's being sent first: if the answer is lost, the next pass can still tell
                // whether it landed.
                saveState(state.copy(sent = sent.mapValues { (_, v) -> SentItem(v.first, v.second) }))
                val wrote: Set<String>? = if (casServer == false) null else try {
                    try {
                        pushV2(token, batch)
                    } catch (e: UnauthorizedException) {
                        token = freshToken()
                        pushV2(token, batch)
                    }.also { casServer = true }
                } catch (e: CasUnavailableException) {
                    casServer = false // no migration yet: push the old way
                    null
                }
                if (wrote != null) {
                    pushedCount = wrote.size
                    val landedItems = LinkedHashMap<String, ItemMeta>()
                    sent.forEach { (key, sentItem) ->
                        if (key !in wrote) return@forEach
                        val (editedMs, json) = sentItem
                        landedItems[key] = if (json == null) ItemMeta(0, editedMs, deleted = true, baseMs = editedMs)
                        else ItemMeta(json.hashCode(), editedMs, base = json, baseMs = editedMs)
                    }
                    // Skipped: the row moved on from the version this was merged from. Keep the edit and
                    // its time; the next pass reads the row back and merges.
                    val skipped = pending.filterKeys { it !in wrote }
                    state = state.copy(
                        items = state.items + landedItems,
                        pending = skipped,
                        refetch = skipped.keys.toList(),
                        sent = emptyMap()
                    )
                } else {
                    val written = try {
                        push(token, batch)
                    } catch (e: UnauthorizedException) {
                        token = freshToken()
                        push(token, batch)
                    }
                    pushedCount = written
                    // Which items landed. Usually all of them; if the server skipped some (it held a newer
                    // edit), read the rows straight back — each one still carrying our stamp is ours. Left to
                    // the next pass, another device could build on one of our writes first, and our change
                    // would count twice.
                    var landed: Set<String> = if (written == batch.length()) sent.keys else emptySet()
                    if (landed.size < sent.size) {
                        landed = runCatching {
                            fetchRows(token, sent.keys.toList())
                                .filter { row -> sent[row.key]?.first == row.editedMs }
                                .mapTo(HashSet()) { it.key }
                        }.getOrDefault(emptySet()) // couldn't check: all read back next pass
                    }
                    val pushed = LinkedHashMap<String, ItemMeta>()
                    sent.forEach { (key, sentItem) ->
                        val (editedMs, json) = sentItem
                        // A first push the server skipped (another device already put this item in the
                        // cloud): still never agreed, so the next pass merges both copies as a first meeting.
                        if (key !in landed && key !in state.items) return@forEach
                        pushed[key] = if (json == null) ItemMeta(0, editedMs, deleted = true)
                        // Written: the pushed version is now what both sides agree on. Skipped: the old base
                        // stays, and the next pass reads the newer row back and merges.
                        else ItemMeta(json.hashCode(), editedMs, base = if (key in landed) json else state.items[key]?.base)
                    }
                    state = state.copy(
                        items = state.items + pushed,
                        pending = emptyMap(),
                        refetch = sent.keys.filter { it !in landed },
                        sent = emptyMap()
                    )
                }
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _status.value = _status.value.copy(syncing = false, message = "Sync failed: ${e.message ?: e.javaClass.simpleName}", failed = true)
        }
        return _status.value
    }

    private data class RemoteRow(
        val kind: String,
        val id: String,
        val data: String?,
        val editedMs: Long,
        val deleted: Boolean,
        val serverUpdatedAt: String,
        /** The version the writer merged from (servers with push_library_items_v2 only). */
        val baseEditedMs: Long? = null
    ) {
        val key get() = "$kind:$id"
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
