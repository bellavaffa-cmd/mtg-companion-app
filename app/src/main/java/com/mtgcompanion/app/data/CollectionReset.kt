package com.mtgcompanion.app.data

import com.mtgcompanion.app.data.supabase.SupabaseSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

/**
 * Reset collection (ResetCollection.kt holds the rules): made here at once, with Undo for
 * [RESET_UNDO_MS]. Meanwhile the sync is held ([SupabaseSync.holdUntil]), so nothing of the reset
 * leaves the phone until Undo has gone — or the app goes to the background, which commits it. Undo
 * puts both stores back exactly as they were, what they remembered as deleted included. The web app
 * does the same in SyncContext (resetCollection).
 */
class CollectionReset(
    private val decks: DeckRepository,
    private val collections: CollectionRepository,
    private val sync: SupabaseSync
) {
    /** The stores' saved text from before the reset; [decksTouched] false when the decks were left alone. */
    data class Saved(val collections: String?, val decksTouched: Boolean, val decks: String?)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private var timer: Job? = null
    private val _pending = MutableStateFlow<PendingReset<Saved>?>(null)
    /** The reset still offering Undo, if any. */
    val pending: StateFlow<PendingReset<Saved>?> = _pending.asStateFlow()

    /** Resets [what]; a reset already waiting is committed first, as if its Undo had run out. */
    suspend fun reset(what: ResetScope) {
        commit()
        lock.withLock {
            val until = System.currentTimeMillis() + RESET_UNDO_MS
            sync.holdUntil(until)
            val plan = resetLibrary(decks.decksFlow.first(), collections.collectionsFlow.first(), what)
            val savedCollections = collections.reset(plan.deletedCollections) { plan.collections }
            val touched = what == ResetScope.EVERYTHING
            val savedDecks = if (touched) decks.reset(plan.deletedDecks) { plan.decks } else null
            _pending.value = PendingReset(what, Saved(savedCollections, touched, savedDecks), until)
            timer = scope.launch {
                delay(RESET_UNDO_MS)
                commit()
            }
        }
    }

    /** Puts the library back as it was before the reset. Nothing of it was sent meanwhile. */
    fun undo() {
        scope.launch {
            lock.withLock {
                val saved = _pending.value?.undo() ?: return@withLock
                timer?.cancel()
                if (saved.decksTouched) decks.putBack(saved.decks)
                collections.putBack(saved.collections)
                _pending.value = null
                sync.release(send = false)
            }
        }
    }

    /** Ends the Undo now (the app going to the background) and lets the sync send the reset. */
    fun commitNow() {
        scope.launch { commit() }
    }

    private suspend fun commit() {
        lock.withLock {
            val pending = _pending.value ?: return
            if (!pending.commit()) return
            // Not the timer's own job: it's the one running this.
            timer?.takeIf { it != coroutineContext[Job] }?.cancel()
            timer = null
            _pending.value = null
            // The copies are gone, so are their photos and history; and with the collection, its value over time.
            val photos = CopyPhotoStore.saved.value.photos
            CopyPhotoStore.restore(emptyList(), emptyMap(), photos.flatMap { listOfNotNull(it.front, it.back) }, null)
            CopyHistoryStore.replace(emptyList())
            if (pending.scope.whole) ValueHistory.clear()
            sync.release(send = true)
        }
    }
}
