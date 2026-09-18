package com.mtgcompanion.app.data.social

import android.content.Context

/**
 * When the user last looked at what each friend shares — the newest edit they'd seen — so the
 * Shared page can mark friends who've changed something since. Kept on this device only.
 */
object SharedSeen {
    private const val PREFS = "shared_seen"

    fun lastSeen(context: Context, owner: String): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(owner, 0L)

    fun markSeen(context: Context, owner: String, editedMs: Long) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (editedMs > prefs.getLong(owner, 0L)) prefs.edit().putLong(owner, editedMs).apply()
    }
}
