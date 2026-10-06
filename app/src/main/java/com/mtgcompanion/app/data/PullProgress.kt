package com.mtgcompanion.app.data

import android.content.Context

/**
 * What's been ticked on a deck's pull list or put-back list (PullList.kt), kept on the phone so it
 * survives the app closing — not synced: it's one trip round the shelves, and ticking on two devices
 * at once isn't a thing anyone does. Also which pull list is open, for "Pull from here" on a scanned
 * box label. The web app keeps the same in the browser (src/collection/pullProgress.ts).
 */
class PullProgress(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("pull_progress", Context.MODE_PRIVATE)

    enum class ListKind(val prefix: String) { PULL("pull:"), PUT_BACK("put_back:") }

    /** The rows ticked on [deckId]'s list, by row key. */
    fun ticked(kind: ListKind, deckId: String): Set<String> =
        prefs.getStringSet(kind.prefix + deckId, null)?.toSet() ?: emptySet()

    fun setTicked(kind: ListKind, deckId: String, keys: Set<String>) {
        val started = kind == ListKind.PULL && keys.isNotEmpty() && ticked(kind, deckId).isEmpty()
        prefs.edit().apply {
            if (keys.isEmpty()) remove(kind.prefix + deckId) else putStringSet(kind.prefix + deckId, HashSet(keys))
            // When the pull list was started, for Upkeep's "started Tuesday".
            if (kind == ListKind.PULL && keys.isEmpty()) remove(STARTED_PREFIX + deckId)
            if (started) putLong(STARTED_PREFIX + deckId, System.currentTimeMillis())
        }.apply()
    }

    /** The decks' pull lists with something ticked, and since when (Upkeep.kt). */
    fun underway(): List<PullUnderway> = prefs.all.keys.filter { it.startsWith(ListKind.PULL.prefix) }.map { key ->
        val deckId = key.removePrefix(ListKind.PULL.prefix)
        PullUnderway(deckId, ticked(ListKind.PULL, deckId), prefs.getLong(STARTED_PREFIX + deckId, 0L).takeIf { it > 0 })
    }

    /** Ticks one more row (for the scanner), and answers the ticks now. */
    fun tick(kind: ListKind, deckId: String, key: String): Set<String> {
        val now = ticked(kind, deckId)
        if (key in now) return now
        val next = now + key
        setTicked(kind, deckId, next)
        return next
    }

    /** The put-back list's choice for [deckId]: where they came from, or the best place by rule. */
    fun putBackMode(deckId: String): PutBackMode =
        if (prefs.getString(MODE_PREFIX + deckId, null) == PutBackMode.RULE.name) PutBackMode.RULE else PutBackMode.ORIGIN

    fun setPutBackMode(deckId: String, mode: PutBackMode) {
        prefs.edit().putString(MODE_PREFIX + deckId, mode.name).apply()
    }

    fun clearPutBack(deckId: String) {
        prefs.edit().remove(ListKind.PUT_BACK.prefix + deckId).remove(MODE_PREFIX + deckId).apply()
    }

    /** The deck whose pull list is open, if any. */
    var openPullDeck: String?
        get() = prefs.getString(OPEN_KEY, null)
        set(value) {
            prefs.edit().apply { if (value == null) remove(OPEN_KEY) else putString(OPEN_KEY, value) }.apply()
        }

    private companion object {
        const val MODE_PREFIX = "put_back_mode:"
        const val STARTED_PREFIX = "pull_started:"
        const val OPEN_KEY = "open_pull"
    }
}
