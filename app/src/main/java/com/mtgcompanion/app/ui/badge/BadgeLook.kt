package com.mtgcompanion.app.ui.badge

import android.content.Context

/**
 * How this player's badges are meant to look, remembered between writes.
 *
 * How dark the art comes out and whether the writing is reversed are decisions you make once, after
 * seeing the first badge come off the phone — not mid-game with three other people waiting. So the
 * badge screen is where they get chosen, and the sheet on the remote just follows them: in a game
 * you pick a token and hold the badge on, nothing else.
 */
class BadgeLook(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("badge_look", Context.MODE_PRIVATE)

    var ink: BadgeInk
        get() = runCatching { BadgeInk.valueOf(prefs.getString(KEY_INK, null) ?: "") }.getOrDefault(BadgeInk.NORMAL)
        set(value) { prefs.edit().putString(KEY_INK, value.name).apply() }

    var invert: Boolean
        get() = prefs.getBoolean(KEY_INVERT, false)
        set(value) { prefs.edit().putBoolean(KEY_INVERT, value).apply() }

    private companion object {
        const val KEY_INK = "ink"
        const val KEY_INVERT = "invert"
    }
}
