package com.mtgcompanion.app.data.supabase

import com.mtgcompanion.app.data.keepCubeFromOlderApp
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.isSample
import com.mtgcompanion.app.data.keepCameFromFromOlderApp
import com.mtgcompanion.app.data.keepDeckExtrasFromOlderApp
import com.mtgcompanion.app.data.keepAlertOptionsFromOlderApp
import com.mtgcompanion.app.data.keepPreReleaseFromOlderApp
import com.mtgcompanion.app.data.keepHistoryFromOlderApp
import com.mtgcompanion.app.data.keepForSaleFromOlderApp
import com.mtgcompanion.app.data.keepLastChecked
import com.mtgcompanion.app.data.keepGearFromOlderApp
import com.mtgcompanion.app.data.keepRecipesFromOlderApp
import com.mtgcompanion.app.data.keepCorrectionsFromOlderApp
import com.mtgcompanion.app.data.keepGoalsFromOlderApp
import com.mtgcompanion.app.data.keepLoansFromOlderApp
import com.mtgcompanion.app.data.keepGradedFromOlderApp
import com.mtgcompanion.app.data.keepSealedFromOlderApp
import com.mtgcompanion.app.data.keepPlaceSizes
import com.mtgcompanion.app.data.keepPlacesFromOlderApp
import com.squareup.moshi.JsonAdapter

/**
 * How many items of a kind must be synced before their all going missing at once counts as a lost
 * library rather than a deletion (see SyncCore.lostKinds). Two: deleting your only deck should still
 * reach your other devices.
 */
internal const val LOST_AT_LEAST = 2

/** How long a deletion is remembered after the fact, in case it can't be pushed for a while. */
private const val REMEMBER_DELETED_MS = 90L * 24 * 60 * 60 * 1000

/**
 * [was] with [deleting] added, old records dropped. Stored with the library itself (DeckStore /
 * CollectionStore), so that losing the library loses these too — which is what lets the sync tell a
 * deletion it was told about from a library that simply isn't there any more.
 */
fun noteDeleted(was: Map<String, Long>?, deleting: String?, now: Long = System.currentTimeMillis()): Map<String, Long> {
    val kept = (was ?: emptyMap()).filterValues { now - it < REMEMBER_DELETED_MS }
    return if (deleting == null) kept else kept + (deleting to now)
}

/** One row of public.library_items, as pulled from the server. */
internal data class RemoteRow(
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

/** One item of a push. [json] is null for a deletion; [base] is the edit time of the version it was merged from. */
internal data class PushItem(val kind: String, val id: String, val editedMs: Long, val json: String?, val base: Long?) {
    val key get() = "$kind:$id"
}

/** What a pull decided. Nothing is written yet: apply the changes to the library, then keep [state]. */
internal class PullResult(
    val state: CloudSyncState,
    /** Every item as it should be pushed: this device's copies, with merged items in place of the originals. */
    val local: Map<String, String>,
    /** Remote changes to write locally: id -> the new version, or null to delete it. */
    val deckChanges: Map<String, Deck?>,
    val collectionChanges: Map<String, Collection?>,
    /** How many items changed here because of other devices. */
    val pulled: Int
)

/**
 * Every sync decision, with no storage or network: noting local edits, taking in pulled rows (merging
 * where both sides changed an item), building a push and recording what landed. [SupabaseSync] does
 * the I/O around it; SyncCoreTest runs it through simulated devices and a fake server. The web app's
 * src/sync/cloudSync.ts makes the same decisions.
 */
internal class SyncCore(
    private val deckAdapter: JsonAdapter<Deck>,
    private val collectionAdapter: JsonAdapter<Collection>
) {
    /**
     * key -> JSON of every deck and binder the sync looks after. Keys are "deck:<id>" /
     * "collection:<id>". Samples from the welcome flow are left out (Onboarding.kt): never pushed,
     * never counted as deleted when they're removed, and left alone by what's pulled. The web app's
     * libraryJson (src/sync/cloudSync.ts) does the same.
     */
    fun localJson(decks: List<Deck>, collections: List<Collection>): LinkedHashMap<String, String> {
        val local = LinkedHashMap<String, String>()
        val seen = java.util.IdentityHashMap<Any, String>()
        decks.filterNot { isSample(it) }.forEach { local["deck:${it.id}"] = jsonOf(it, seen) { d -> deckAdapter.toJson(d) } }
        collections.filterNot { isSample(it) }.forEach { local["collection:${it.id}"] = jsonOf(it, seen) { c -> collectionAdapter.toJson(c) } }
        lastJson = seen
        return local
    }

    /**
     * Each deck's and binder's JSON from the last pass, by the very object: the repositories hand out
     * the same objects until something changes, so a pass with nothing new (the regular check for
     * other devices' edits) doesn't write a big library out again.
     */
    private var lastJson = java.util.IdentityHashMap<Any, String>()

    private inline fun <T : Any> jsonOf(item: T, seen: java.util.IdentityHashMap<Any, String>, write: (T) -> String): String =
        (lastJson[item] ?: write(item)).also { seen[item] = it }

    /**
     * Notes what changed locally since the last agreement with the server, each change stamped with
     * when it was first noticed (0 = it was here before this device first synced).
     *
     * An item this device had agreed on and no longer holds is a deletion when [deleted] says the
     * user deleted it, or when some of its kind is still here. Every deck, or every binder, gone at
     * once with nothing to say why is a lost library (see [lostKinds]): those are forgotten and read
     * back instead, so a device whose storage went fills up again rather than emptying the account.
     */
    fun notePending(
        state: CloudSyncState,
        local: Map<String, String>,
        now: Long,
        deleted: Set<String> = emptySet()
    ): CloudSyncState {
        val pending = state.pending.toMutableMap()
        local.forEach { (key, json) ->
            val meta = state.items[key]
            if (meta == null || meta.deleted || meta.hash != json.hashCode()) {
                pending.putIfAbsent(key, if (meta == null && state.cursor == null) 0L else now)
            } else {
                pending.remove(key)
            }
        }
        val lost = lostKinds(state, local, deleted)
        val forget = LinkedHashSet<String>()
        state.items.forEach { (key, meta) ->
            if (meta.deleted || key in local) return@forEach
            // A deletion the app told us about is always a deletion, however many go at once.
            if (kindOf(key) in lost && key !in deleted) forget += key else pending.putIfAbsent(key, now)
        }
        if (forget.isEmpty()) return if (pending == state.pending) state else state.copy(pending = pending)
        // Forgotten, not deleted: with no agreed version left for these, the next pull writes the
        // server's copies to this device (SyncCore.pull, where meta == null and we hold nothing).
        return state.copy(
            items = state.items - forget,
            pending = pending - forget,
            refetch = (state.refetch + forget).distinct()
        )
    }

    /**
     * Kinds whose every synced item has vanished from [local] between two passes: a library that's
     * been lost — app data cleared, a backup half-restored, a browser's storage wiped — rather than
     * deletions the user asked for. Deleting by hand pushes each item as it goes, so a whole kind
     * going at once is not something a person did through the app.
     *
     * The cost of being wrong each way is what sets the rule: a deletion that doesn't propagate is
     * an annoyance the user can repeat, while a wrongly-pushed one empties the account and takes the
     * card lists with it — the server keeps no copy of a deleted item.
     */
    private fun lostKinds(state: CloudSyncState, local: Map<String, String>, deleted: Set<String>): Set<String> =
        state.items.filterValues { !it.deleted }.keys
            .groupBy { kindOf(it) }
            .filterValues { keys -> keys.size >= LOST_AT_LEAST && keys.none { it in local || it in deleted } }
            .keys

    private fun kindOf(key: String) = key.substringBefore(':')

    /**
     * Rows to read back by key besides the cursor pull: ones a skipped push left behind, ones this
     * version couldn't read, and those of a push whose answer never came.
     */
    fun refetchKeys(state: CloudSyncState): List<String> = (state.refetch + state.sent.keys).distinct()

    /**
     * Takes in [rows] (the cursor pull, oldest first) and [again] (rows read back by key). Merges where
     * both this device and another changed an item since they last agreed; otherwise the newer edit
     * wins, and a pending local edit newer than the row is kept to be pushed.
     */
    fun pull(state: CloudSyncState, local: Map<String, String>, rows: List<RemoteRow>, again: List<RemoteRow>, now: Long): PullResult {
        val local = LinkedHashMap(local)
        val pending = state.pending.toMutableMap()
        val items = state.items.toMutableMap()
        val deckChanges = LinkedHashMap<String, Deck?>()
        val collectionChanges = LinkedHashMap<String, Collection?>()
        val unreadable = LinkedHashSet<String>()
        var cursor = state.cursor
        var pulled = 0

        /** Takes in one row; false if this version can't read it. */
        fun <T : Any> takeRow(
            row: RemoteRow,
            adapter: JsonAdapter<T>,
            changes: MutableMap<String, T?>,
            emptyBase: (T) -> T,
            merge: (base: T, mine: T, theirs: T, minePreferred: Boolean) -> T,
            heal: (mine: T, theirs: T) -> T = { _, t -> t }
        ): Boolean {
            val key = row.key
            val localEdit = pending[key]
            val mineJson = local[key]
            if (row.deleted) {
                // Deleted elsewhere: it goes, unless this device edited it more recently.
                if (localEdit != null && localEdit > row.editedMs) return true
                if (mineJson != null) { changes[row.id] = null; pulled++ }
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
            // This device's own write coming back (or a row it already agreed on): stamped with the edit
            // time it last pushed, and holding what it pushed. That's now the agreed version. The content
            // check matters: another device merging from the same row can land on the very same stamp.
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
            // Another device merged from a push of ours whose answer was lost: it landed, and it's the
            // version to merge against — merging from the older one would count our change twice.
            val builtOnOurs = sent != null && row.baseEditedMs != null && row.baseEditedMs == sent.ms
            val baseJson = if (builtOnOurs) sent!!.json else meta?.base
            val base = baseJson?.let { runCatching { adapter.fromJson(it) }.getOrNull() }
            // This device has diverged if its copy differs from the version both sides last agreed on —
            // which stays true even when a push was skipped as stale server-side.
            val diverged = mineJson != null && base != null && mineJson != baseJson
            // This device has the item but has never agreed a version of it with the server — its first
            // sync, say, with the same deck already in the cloud (both from the old Drive sync). With
            // nothing to compare against, keep every card from both rather than letting the cloud copy
            // replace this one. Not tied to a pending edit: a first push the server skipped leaves none.
            val firstMeeting = mineJson != null && meta == null && !builtOnOurs
            // Both devices changed it since they last agreed: keep both sets of edits.
            if ((diverged || firstMeeting) && mineJson != theirJson) {
                val mine = adapter.fromJson(mineJson!!) ?: return false
                // First meeting: an empty base makes every card an addition from both sides, and the
                // cloud's name and settings win.
                val merged = merge(base ?: emptyBase(mine), mine, theirs, (localEdit ?: 0L) > row.editedMs)
                val mergedJson = adapter.toJson(merged)
                if (mergedJson != mineJson) { changes[row.id] = merged; pulled++ }
                // Push the merged version, stamped past their edit so the server can't reject it as
                // stale, and keep their version as the new base.
                local[key] = mergedJson
                pending[key] = maxOf(now, row.editedMs + 1)
                items[key] = ItemMeta(theirJson.hashCode(), row.editedMs, base = theirJson, baseMs = row.editedMs)
                return true
            }
            if (localEdit != null && localEdit > row.editedMs) return true // ours is newer; pushed below
            // A binder saved by an app that doesn't know about storage places comes without them: this
            // device's are kept and pushed back, rather than the older app's save clearing them everywhere.
            val mineItem = mineJson?.let { runCatching { adapter.fromJson(it) }.getOrNull() }
            val healed = if (mineItem != null) heal(mineItem, theirs) else theirs
            if (healed !== theirs) {
                val healedJson = adapter.toJson(healed)
                if (healedJson != mineJson) { changes[row.id] = healed; pulled++ }
                local[key] = healedJson
                pending[key] = maxOf(now, row.editedMs + 1)
                items[key] = ItemMeta(theirJson.hashCode(), row.editedMs, base = theirJson, baseMs = row.editedMs)
                return true
            }
            if (mineJson != theirJson) { changes[row.id] = theirs; pulled++ }
            items[key] = ItemMeta(theirJson.hashCode(), row.editedMs, base = theirJson, baseMs = row.editedMs)
            pending.remove(key)
            return true
        }

        val inPull = rows.mapTo(HashSet()) { it.key }
        (again.filter { it.key !in inPull } + rows).forEach { row ->
            val taken = if (row.kind == "deck") {
                // A deck saved by an app that doesn't know where its copies came from keeps this device's note,
                // and its primer, folder, archive flag, companion and categories (DeckExtras.kt), its history and a cube's settings (Cube.kt).
                takeRow(row, deckAdapter, deckChanges, { mine ->
                    mine.copy(cards = emptyList(), considering = emptyList(), sideboard = emptyList(), tags = emptyList(), gameResults = emptyList(), versions = emptyList(), cameFrom = null)
                }, merge = { b, m, t, p -> ItemMerge.mergeDecks(b, m, t, minePreferred = p) }, heal = { mine, theirs -> keepCubeFromOlderApp(mine, keepHistoryFromOlderApp(mine, keepDeckExtrasFromOlderApp(mine, keepCameFromFromOlderApp(mine, theirs)))) })
            } else {
                // First meeting: the places too are each device's own, kept as additions.
                takeRow(
                    row, collectionAdapter, collectionChanges, { mine -> mine.copy(entries = emptyList(), storagePlaces = null, loans = null, sealed = null, graded = null, gear = null, sortRecipes = null, scanCorrections = null, collectionGoals = null) },
                    { b, m, t, p -> ItemMerge.mergeCollections(b, m, t, minePreferred = p) },
                    // ...and a place saved without when it was last checked (PlaceCheck.kt) keeps this device's,
                    // and the Unsorted pile saved without its loans (Loans.kt) keeps this device's, as do places saved
                    // without their size (BoxSpace.kt) and cards without their copies to sell (Selling.kt), and
                    // wishlist targets without their options (WishlistTargets.kt).
                    // ...and the pile saved without its sealed product, graded copies or gear (Sealed.kt, Graded.kt, Gear.kt).
                    // ...and its sorting recipes (SortRecipes.kt), what the scanner learned (ScanCorrections.kt), and its collection goals (CollectionGoals.kt).
                    heal = { mine, theirs -> keepGoalsFromOlderApp(mine, keepCorrectionsFromOlderApp(mine, keepRecipesFromOlderApp(mine, keepGearFromOlderApp(mine, keepGradedFromOlderApp(mine, keepSealedFromOlderApp(mine, keepAlertOptionsFromOlderApp(mine, keepPreReleaseFromOlderApp(mine, keepForSaleFromOlderApp(mine, keepPlaceSizes(mine, keepLoansFromOlderApp(mine, keepLastChecked(mine, keepPlacesFromOlderApp(mine, theirs))))))))))))) }
                )
            }
            // A row this version can't read is read back by key every pass until an update can; the
            // cursor moves on, so everything after it isn't downloaded again and again.
            if (!taken) unreadable += row.key
            // A re-fetched row, or one re-read from the overlap, never moves the cursor backwards.
            if (row.key in inPull && (cursor == null || row.serverUpdatedAt > cursor!!)) cursor = row.serverUpdatedAt
        }
        // Every unanswered push has now been read back (a row it never reached leaves its edit pending).
        val newState = state.copy(items = items, pending = pending, cursor = cursor, refetch = unreadable.toList(), sent = emptyMap())
        return PullResult(newState, local, deckChanges, collectionChanges, pulled)
    }

    /** What's still pending after [pull], as a push: each item with the version it was merged from. */
    fun pushBatch(state: CloudSyncState, local: Map<String, String>, now: Long): List<PushItem> =
        state.pending.map { (key, editedAt) ->
            val (kind, id) = key.split(":", limit = 2)
            val base = state.items[key]?.let { it.baseMs ?: it.editedMs }
            PushItem(kind, id, if (editedAt == 0L) now else editedAt, local[key], base)
        }

    /** [state] noting [batch] as sent, saved before the request so a lost answer can be resolved next pass. */
    fun withSent(state: CloudSyncState, batch: List<PushItem>): CloudSyncState =
        state.copy(sent = batch.associate { it.key to SentItem(it.editedMs, it.json) })

    /**
     * After a compare-and-swap push that wrote [wrote]: those are now agreed. The rest were skipped —
     * the row moved on from the version they were merged from — and keep their edit and its time; the
     * next pass reads them back and merges.
     */
    fun afterPushV2(state: CloudSyncState, batch: List<PushItem>, wrote: Set<String>): CloudSyncState {
        val landed = batch.filter { it.key in wrote }.associate { item ->
            item.key to if (item.json == null) ItemMeta(0, item.editedMs, deleted = true, baseMs = item.editedMs)
            else ItemMeta(item.json.hashCode(), item.editedMs, base = item.json, baseMs = item.editedMs)
        }
        val skipped = state.pending.filterKeys { it !in wrote }
        return state.copy(
            items = state.items + landed,
            pending = skipped,
            refetch = (state.refetch + skipped.keys).distinct(),
            sent = emptyMap()
        )
    }

    /**
     * After a push to a server without compare-and-swap, where [landed] are the items known to be
     * written. Skipped items keep their old base, and are read back next pass and merged.
     */
    fun afterPushV1(state: CloudSyncState, batch: List<PushItem>, landed: Set<String>): CloudSyncState {
        val pushed = LinkedHashMap<String, ItemMeta>()
        batch.forEach { item ->
            // A first push the server skipped (another device already put this item in the cloud):
            // still never agreed, so the next pass merges both copies as a first meeting.
            if (item.key !in landed && item.key !in state.items) return@forEach
            pushed[item.key] = if (item.json == null) ItemMeta(0, item.editedMs, deleted = true)
            // Written: the pushed version is now what both sides agree on. Skipped: the old base
            // stays, and the next pass reads the newer row back and merges.
            else ItemMeta(item.json.hashCode(), item.editedMs, base = if (item.key in landed) item.json else state.items[item.key]?.base)
        }
        return state.copy(
            items = state.items + pushed,
            pending = emptyMap(),
            refetch = (state.refetch + batch.map { it.key }.filter { it !in landed }).distinct(),
            sent = emptyMap()
        )
    }
}

/** One kept edit: this device's copy (null if deleted here) and the version it was based on. */
internal data class RescueItem(val json: String? = null, val base: String? = null)

/**
 * Edits that hadn't reached the server when a session ended on its own (the server refused the
 * sign-in), kept so signing back in to the same account can put them back. Keys are "deck:<id>" /
 * "collection:<id>".
 */
internal data class Rescue(val userId: String = "", val savedAt: Long = 0L, val items: Map<String, RescueItem> = emptyMap())

/**
 * Whether [state] is bookkeeping for a library [accountUserId] doesn't own — another account's, or a
 * removal that didn't finish. Such a library is removed, never synced or merged in. A library never
 * synced (no bookkeeping) is the device's own.
 */
internal fun belongsElsewhere(state: CloudSyncState, accountUserId: String?): Boolean =
    state.userId != null && state.userId != accountUserId

/**
  * The edits in [local] that [state] shows as not yet on the server, or null if there are none.
  * [deleted] carries the deletions the user made but hasn't pushed: signing out takes the library
  * with them, so this is the only thing that keeps them.
  */
internal fun SyncCore.captureRescue(
    state: CloudSyncState,
    local: Map<String, String>,
    userId: String,
    now: Long,
    deleted: Set<String> = emptySet()
): Rescue? {
    if (state.userId != userId) return null
    val pending = notePending(state, local, now, deleted).pending
    if (pending.isEmpty()) return null
    return Rescue(userId, now, pending.keys.associateWith { RescueItem(local[it], state.items[it]?.base) })
}

/**
 * [decks] (the account's, just pulled after signing back in) with [rescue]'s deck edits merged back
 * in, each against the version it was made from, so changes made on other devices meanwhile are
 * kept. A deletion made here only goes through if the deck hasn't changed elsewhere since.
 */
internal fun rescueDecks(decks: List<Deck>, rescue: Rescue, adapter: JsonAdapter<Deck>): List<Deck> =
    rescueItems(decks, rescue, "deck", adapter, { it.id }, { mine ->
        mine.copy(cards = emptyList(), considering = emptyList(), sideboard = emptyList(), tags = emptyList(), gameResults = emptyList(), versions = emptyList(), cameFrom = null)
    }) { b, m, t -> ItemMerge.mergeDecks(b, m, t, minePreferred = true) }

/** The same, for binders. */
internal fun rescueCollections(collections: List<Collection>, rescue: Rescue, adapter: JsonAdapter<Collection>): List<Collection> =
    rescueItems(collections, rescue, "collection", adapter, { it.id }, { mine -> mine.copy(entries = emptyList(), storagePlaces = null, loans = null, sealed = null, graded = null, gear = null, sortRecipes = null, scanCorrections = null, collectionGoals = null) }) { b, m, t ->
        ItemMerge.mergeCollections(b, m, t, minePreferred = true)
    }

private fun <T : Any> rescueItems(
    list: List<T>,
    rescue: Rescue,
    kind: String,
    adapter: JsonAdapter<T>,
    idOf: (T) -> String,
    emptyBase: (T) -> T,
    merge: (base: T, mine: T, theirs: T) -> T
): List<T> {
    var out = list
    rescue.items.forEach { (key, kept) ->
        val (itemKind, id) = key.split(":", limit = 2)
        if (itemKind != kind) return@forEach
        val current = out.firstOrNull { idOf(it) == id }
        if (kept.json == null) {
            if (current != null && (kept.base == null || adapter.toJson(current) == kept.base)) out = out.filter { idOf(it) != id }
            return@forEach
        }
        val mine = runCatching { adapter.fromJson(kept.json) }.getOrNull() ?: return@forEach
        out = if (current == null) out + mine else {
            val base = kept.base?.let { runCatching { adapter.fromJson(it) }.getOrNull() } ?: emptyBase(mine)
            val merged = merge(base, mine, current)
            out.map { if (idOf(it) == id) merged else it }
        }
    }
    return out
}
