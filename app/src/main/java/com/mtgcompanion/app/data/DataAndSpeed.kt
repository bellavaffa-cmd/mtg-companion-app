package com.mtgcompanion.app.data

import android.content.Context
import android.content.SharedPreferences
import com.mtgcompanion.app.data.social.timeAgo
import java.util.Locale

/**
 * Settings › Data and speed: how big the collection is, how much of its card data is kept for offline,
 * how long All cards took to open the last time and when it last synced — and the backup (Backup.kt).
 * This keeps the timing (on this phone) and says each figure the way the web app does
 * (src/settings/dataAndSpeed.ts and perfStats.ts).
 */
object DataAndSpeed {
    /** How long All cards took to work out its list when it last opened: [ms], over [cards] printings. */
    data class OpenTiming(val ms: Long, val cards: Int, val at: Long)

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("data_and_speed", Context.MODE_PRIVATE)
    }

    fun noteAllCardsOpened(ms: Long, cards: Int) {
        prefs?.edit()?.putLong(KEY_MS, ms)?.putInt(KEY_CARDS, cards)?.putLong(KEY_AT, System.currentTimeMillis())?.apply()
    }

    fun lastAllCardsOpen(): OpenTiming? {
        val p = prefs ?: return null
        if (!p.contains(KEY_MS)) return null
        return OpenTiming(p.getLong(KEY_MS, 0L), p.getInt(KEY_CARDS, 0), p.getLong(KEY_AT, 0L))
    }

    private const val KEY_MS = "all_cards_open_ms"
    private const val KEY_CARDS = "all_cards_open_cards"
    private const val KEY_AT = "all_cards_open_at"
}

private fun n(x: Int) = String.format(Locale.UK, "%,d", x)

/** "0.4 s", "0.05 s", "1.2 s" — the way Data and speed shows a time. */
fun secondsLabel(ms: Long): String = when {
    ms < 10 -> "0.01 s"
    ms < 100 -> String.format(Locale.UK, "%.2f", ms / 1000.0).trimEnd('0').trimEnd('.') + " s"
    ms < 1000 -> String.format(Locale.UK, "%.1f", ms / 1000.0).trimEnd('0').trimEnd('.') + " s"
    else -> String.format(Locale.UK, "%.1f", ms / 1000.0) + " s"
}

/** "18,402 of 18,402" — printings with their card data kept, of the printings owned. */
fun offlineLabel(saved: Int, total: Int): String = "${n(saved)} of ${n(total)}"

/** "0.4 s", or "Not opened yet". */
fun openLabel(timing: DataAndSpeed.OpenTiming?): String = timing?.let { secondsLabel(it.ms) } ?: "Not opened yet"

/** "2 min ago", "Just now", "Not yet", or "Not signed in". */
fun lastSyncedLabel(signedIn: Boolean, lastSyncedAt: Long, now: Long): String = when {
    !signedIn -> "Not signed in"
    lastSyncedAt <= 0L -> "Not yet"
    else -> timeAgo(lastSyncedAt, now).replaceFirstChar { it.uppercaseChar() }
}

/** Opening All cards counts as quick under this: the time shows green. */
const val QUICK_OPEN_MS = 1000L

const val BACKUP_NOTE = "Everything, including places, loans, history and photos, in one file you keep."
