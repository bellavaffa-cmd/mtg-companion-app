package com.mtgcompanion.app.data

import android.content.Context
import java.io.File

/**
 * The scanned pile, kept on the device. Scanning runs the camera for minutes at a time, which is
 * exactly when Android is most likely to shut the app down behind you — and the pile is work that
 * hasn't been filed anywhere yet. It's written on every scan and read back when the scanner opens,
 * so the cards are still there. The web app does the same with sessionStorage (pages/ScanPage.tsx).
 */
object ScanPile {

    /** A pile older than this is yesterday's session, not the one you're in the middle of. */
    const val KEEP_MS = 24L * 60 * 60 * 1000

    private const val FILE = "scan_pile.json"
    private val adapter = localMoshi.adapter(Stored::class.java)

    /** [at]: when the pile was last added to. */
    data class Stored(val at: Long, val rows: List<ScanRow>)

    private var dir: File? = null

    fun init(context: Context) {
        dir = context.filesDir
    }

    /** The pile as it was left, newest scan first, or nothing when there isn't one worth keeping. */
    fun read(now: Long = System.currentTimeMillis()): List<ScanRow> {
        val file = dir?.let { File(it, FILE) } ?: return emptyList()
        if (!file.exists()) return emptyList()
        val stored = runCatching { adapter.fromJson(file.readText()) }.getOrNull() ?: return emptyList()
        if (tooOld(stored.at, now)) {
            clear()
            return emptyList()
        }
        return stored.rows
    }

    /** Keeps [rows]; an empty pile clears the file rather than leaving an empty one behind. */
    fun write(rows: List<ScanRow>, now: Long = System.currentTimeMillis()) {
        if (rows.isEmpty()) {
            clear()
            return
        }
        val target = dir?.let { File(it, FILE) } ?: return
        runCatching { target.writeText(adapter.toJson(Stored(now, rows))) }
    }

    fun clear() {
        runCatching { dir?.let { File(it, FILE).delete() } }
    }

    /** Whether a pile last added to at [at] belongs to a session that's over. */
    internal fun tooOld(at: Long, now: Long): Boolean = now - at > KEEP_MS

    /** The next scan's number, so rows keep their own identity across a restart. */
    fun nextId(rows: List<ScanRow>): Long = (rows.maxOfOrNull { it.id } ?: 0L) + 1
}
