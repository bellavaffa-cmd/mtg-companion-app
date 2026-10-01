package com.mtgcompanion.app.tester

import android.content.Context
import com.mtgcompanion.app.data.supabase.ParkedSession
import com.mtgcompanion.app.data.supabase.SupabaseSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A second account for the tester app, so adding, moving and deleting can be tried on a throwaway
 * collection instead of the real one. One account is in use and the other is put aside; a tap swaps
 * them. Each account's decks and binders leave the phone with it and come back from the server, the
 * way signing out and in works, so the two never mix.
 *
 * Only the account put aside is kept here. A copy of the one in use would go stale: its sign-in is
 * replaced every time it's refreshed.
 */
class TesterAccounts(context: Context, private val sync: SupabaseSync) {
    private val prefs = context.applicationContext.getSharedPreferences("tester_accounts", Context.MODE_PRIVATE)

    private val _parked = MutableStateFlow(load())
    /** The account put aside, if there is one. */
    val parked: StateFlow<ParkedSession?> = _parked.asStateFlow()

    private fun load(): ParkedSession? {
        val id = prefs.getString("user_id", null) ?: return null
        val token = prefs.getString("refresh_token", null) ?: return null
        return ParkedSession(id, prefs.getString("email", "").orEmpty(), token)
    }

    private fun keep(session: ParkedSession?) {
        prefs.edit().apply {
            if (session == null) clear()
            else putString("user_id", session.userId).putString("email", session.email).putString("refresh_token", session.refreshToken)
        }.apply()
        _parked.value = session
    }

    /**
     * Puts the account in use aside and signs out on this phone, ready for the second account to be
     * signed in to (or made) the usual way. Returns a line to show.
     */
    suspend fun makeRoomForSecond(): String {
        if (sync.auth.account.value == null) return "Sign in first: this puts the account you're using aside."
        if (_parked.value != null) return "There's already an account put aside. Switch to it, or forget it first."
        val result = sync.switchAccount(null)
        if (result.unsynced > 0) return unsyncedLine(result.unsynced)
        keep(result.parked)
        TesterLog.add("app", "Put ${result.parked?.email} aside")
        return "Put ${result.parked?.email} aside. Now sign in to your test account (or create one) in Settings."
    }

    /** Swaps the account in use with the one put aside. Returns a line to show. */
    suspend fun switch(): String {
        val to = _parked.value ?: return "No account is put aside."
        val result = sync.switchAccount(to)
        if (result.unsynced > 0) return unsyncedLine(result.unsynced)
        // Signed out when asked to switch: there's nothing to put aside in exchange.
        keep(result.parked)
        TesterLog.add("app", "Switched to ${to.email}")
        return "Now using ${to.email}. Its decks and binders are loading."
    }

    /** Drops the account put aside from this phone. It isn't signed out anywhere, just no longer kept. */
    fun forget() {
        TesterLog.add("app", "Forgot ${_parked.value?.email}")
        keep(null)
    }

    private fun unsyncedLine(count: Int) =
        "$count ${if (count == 1) "change hasn't" else "changes haven't"} synced yet, so nothing was switched. Check your connection and try again."
}
