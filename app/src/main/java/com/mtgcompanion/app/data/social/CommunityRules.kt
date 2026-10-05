package com.mtgcompanion.app.data.social

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * The community rules (Google Play's user-generated content policy): before someone first puts
 * something others can see — their profile (name, username, picture), a message, a trade message,
 * a shared deck or binder — they're shown the rules once and agree to them.
 *
 * Agreement is kept on the device and, when signed in, in the account's own user metadata
 * (Supabase Auth `user_metadata.community_rules_version`, which the user may write themselves — no
 * table or migration), so agreeing on the web or another phone counts here too. The web app keeps
 * the same key (src/social/communityRules.ts).
 *
 * Bump [VERSION] when the rules change in substance: everyone is asked again.
 */
object CommunityRulesPolicy {
    const val VERSION = 1

    /** The account metadata key; the same on the web. */
    const val METADATA_KEY = "community_rules_version"

    /** Whether posting needs the rules shown first, given what this device and the account say. */
    fun needsAgreement(deviceVersion: Int, accountVersion: Int?): Boolean =
        maxOf(deviceVersion, accountVersion ?: 0) < VERSION

    /** The version agreed to, from a Supabase Auth user object (`{"user_metadata": {...}}`); 0 if none. */
    fun versionFromUser(user: JSONObject?): Int {
        val meta = user?.optJSONObject("user_metadata") ?: return 0
        val value = meta.opt(METADATA_KEY) ?: return 0
        return when (value) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: 0
            else -> 0
        }.coerceAtLeast(0)
    }

    /** The rules themselves, short, as the sheet lists them (the web shows the same). */
    val RULES: List<Pair<String, String>> = listOf(
        "Be respectful" to "Treat other players the way you'd want to be treated at the table.",
        "No hate or harassment" to "No attacks on anyone for who they are, no threats, no bullying or stalking.",
        "No spam or scams" to "No advertising, no repeated unwanted messages, no fake trades or attempts to get money or accounts.",
        "No explicit content" to "No sexual, violent or shocking pictures, names or messages.",
    )

    const val ENFORCEMENT = "We can remove content and suspend or delete accounts that break these rules."

    const val HOW_TO_REPORT = "To report or block someone, tap \"Block or report\" (the flag) on their profile, a trade, " +
        "a shared deck or binder, or a conversation. Reports come to us and we act on them; a blocked person can't " +
        "contact you or see what you share."

    const val FULL_RULES_URL = "https://manabind.com/community-rules"
}

/**
 * This device's agreement, plus the one-time sheet's requests (shown by CommunityRulesHost).
 * [persist] stores the device's agreed version (SharedPreferences in the app; a plain lambda in tests).
 */
class CommunityRulesStore internal constructor(initialDeviceVersion: Int, private val persist: (Int) -> Unit) {
    private val _deviceVersion = MutableStateFlow(initialDeviceVersion)
    val deviceVersion: StateFlow<Int> = _deviceVersion.asStateFlow()

    /** What the signed-in account says, once known (null when signed out or not yet heard). */
    private val _accountVersion = MutableStateFlow<Int?>(null)
    val accountVersion: StateFlow<Int?> = _accountVersion.asStateFlow()

    /** The action waiting on the sheet's Agree, if it's open. */
    private val _pending = MutableStateFlow<(() -> Unit)?>(null)
    val pending: StateFlow<(() -> Unit)?> = _pending.asStateFlow()

    val agreed: Boolean get() = !CommunityRulesPolicy.needsAgreement(_deviceVersion.value, _accountVersion.value)

    /**
     * Runs [action] now if the rules were already agreed to; otherwise opens the sheet and runs it
     * after Agree. Nothing happens on Not now.
     */
    fun require(action: () -> Unit) {
        if (agreed) action() else _pending.value = action
    }

    /** Opens the sheet just to read (Settings, the privacy page); Agree there only records it. */
    fun show() {
        _pending.value = {}
    }

    /** Agree on the sheet: remembered on the device, then the waiting action runs. */
    fun agree(): (() -> Unit)? {
        recordDevice(CommunityRulesPolicy.VERSION)
        val action = _pending.value
        _pending.value = null
        return action
    }

    fun dismiss() {
        _pending.value = null
    }

    /** From the account's user metadata (sign-in or session refresh). */
    fun setAccountVersion(version: Int?) {
        _accountVersion.value = version
        // Agreed on another device: this one needn't ask again, even signed out later.
        if (version != null && version > _deviceVersion.value) recordDevice(version)
    }

    private fun recordDevice(version: Int) {
        if (version <= _deviceVersion.value) return
        persist(version)
        _deviceVersion.value = version
    }

    companion object {
        private const val KEY = "agreed_version"
        @Volatile private var instance: CommunityRulesStore? = null
        fun get(context: Context): CommunityRulesStore =
            instance ?: synchronized(this) {
                instance ?: run {
                    val prefs = context.applicationContext.getSharedPreferences("community_rules", Context.MODE_PRIVATE)
                    CommunityRulesStore(prefs.getInt(KEY, 0)) { prefs.edit().putInt(KEY, it).apply() }
                }.also { instance = it }
            }
    }
}
