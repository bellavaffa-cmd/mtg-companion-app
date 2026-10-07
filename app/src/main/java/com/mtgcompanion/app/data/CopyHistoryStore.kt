package com.mtgcompanion.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.Instant
import java.time.ZoneId

/**
 * The copy history log (CopyHistory.kt), kept on this phone only — it isn't synced. Writing is best
 * effort: a file that can't be written just loses the history, never the change itself. The web app
 * keeps its own in the browser (src/collection/copyHistoryStore.ts).
 */
object CopyHistoryStore {
    /** What's stored: the moves, oldest first. */
    data class Saved(val moves: List<CopyMove> = emptyList())

    private val adapter by lazy { localMoshi.adapter(Saved::class.java) }
    private var file: File? = null
    private val _moves = MutableStateFlow<List<CopyMove>>(emptyList())
    /** The log, oldest first. */
    val moves: StateFlow<List<CopyMove>> = _moves.asStateFlow()

    /** Loads what's kept, once. */
    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        val f = File(context.applicationContext.filesDir, "copy_history.json")
        file = f
        val saved = runCatching { if (f.exists()) adapter.fromJson(f.readText()) else null }.getOrNull()
        _moves.value = pruneMoves(saved?.moves.orEmpty(), System.currentTimeMillis())
    }

    /** Puts [moves] in place of the log — a restored backup's, put together with this phone's (Backup.kt). */
    @Synchronized
    fun replace(moves: List<CopyMove>) {
        _moves.value = moves
        runCatching { file?.writeText(adapter.toJson(Saved(moves))) }
    }

    /** Adds [moves] to the log. */
    @Synchronized
    fun record(moves: List<CopyMove>) {
        if (moves.isEmpty()) return
        val next = appendMoves(_moves.value, moves, System.currentTimeMillis())
        _moves.value = next
        runCatching { file?.writeText(adapter.toJson(Saved(next))) }
    }
}

/** A calendar day on this phone, as CopyHistory.moveDay and the loans want it ("2026-10-05"). */
fun dayOf(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toString()
