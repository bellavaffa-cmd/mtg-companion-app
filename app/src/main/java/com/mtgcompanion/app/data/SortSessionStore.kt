package com.mtgcompanion.app.data

import android.content.Context

/**
 * Sorting a new pile (SortPiles.kt): the piles' rules, kept on this phone for next time, and the sort
 * under way, so leaving the scanner doesn't lose it. The web app keeps the same in the browser
 * (src/collection/sortSession.ts).
 */
class SortSessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("sort_piles", Context.MODE_PRIVATE)
    private val rulesAdapter by lazy { localMoshi.adapter(SavedPiles::class.java) }
    private val sessionAdapter by lazy { localMoshi.adapter(SortSession::class.java) }

    private data class SavedPiles(val rules: List<PileRule> = emptyList())

    /** The piles last used here, or the first sort's (defaultPiles). */
    fun piles(collections: List<Collection>): List<PileRule> {
        val saved = prefs.getString(RULES, null)?.let { runCatching { rulesAdapter.fromJson(it) }.getOrNull() }?.rules.orEmpty()
        return if (saved.isNotEmpty()) saved.take(MAX_PILES).map { pileRule(it) } else defaultPiles(collections)
    }

    fun savePiles(rules: List<PileRule>) {
        prefs.edit().putString(RULES, rulesAdapter.toJson(SavedPiles(rules.map { pileRule(it) }))).apply()
    }

    fun session(): SortSession? = prefs.getString(SESSION, null)?.let { runCatching { sessionAdapter.fromJson(it) }.getOrNull() }

    fun saveSession(session: SortSession?) {
        prefs.edit().apply { if (session == null) remove(SESSION) else putString(SESSION, sessionAdapter.toJson(session)) }.apply()
    }

    private companion object {
        const val RULES = "rules"
        const val SESSION = "session"
    }
}

/** Each pile's colour on the scanner, by its number (1 to 6) — the web app's PILE_COLOURS. */
val PILE_COLOURS = listOf(0xFFE6B45EL, 0xFFE2694AL, 0xFF5BCB8FL, 0xFF6AA8F0L, 0xFFC58AF0L, 0xFFF07FA8L)
