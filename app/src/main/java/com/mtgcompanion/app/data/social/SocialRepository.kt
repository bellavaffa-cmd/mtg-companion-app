package com.mtgcompanion.app.data.social

import com.mtgcompanion.app.data.supabase.MatchChannel
import com.mtgcompanion.app.data.supabase.SupabaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Friends, pods, shares and trades for the signed-in account, fetched when a screen needs them. None
 * of it is stored on the device, so nothing lingers after signing out.
 */
class SocialRepository(private val auth: SupabaseAuth) {
    val api = SocialApi(auth)
    /** Life counter tables and their players' remotes talk over this. */
    val matchChannel = MatchChannel(auth)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _overview = MutableStateFlow<Overview?>(null)
    /** Null until loaded, and while signed out. */
    val overview: StateFlow<Overview?> = _overview.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _inbox = MutableStateFlow(Inbox())
    /** Friend requests and trades waiting on the user, for the badge. */
    val inbox: StateFlow<Inbox> = _inbox.asStateFlow()

    val configured: Boolean get() = auth.configured
    val accountFlow = auth.account
    val userId: String? get() = auth.account.value?.userId
    val email: String? get() = auth.account.value?.email

    init {
        // A different account (or none) starts from nothing.
        scope.launch {
            auth.account.map { it?.userId }.distinctUntilChanged().collect {
                _overview.value = null
                _inbox.value = Inbox()
                _error.value = null
                if (it != null) refreshInbox()
            }
        }
    }

    /** Reloads everything the Friends screens show. */
    suspend fun refresh() {
        val who = userId ?: return
        _loading.value = true
        try {
            val o = api.overview()
            if (userId != who) return
            _overview.value = o
            _error.value = null
            if (o.me != null) {
                _inbox.value = Inbox(
                    friendRequests = o.friends.count { it.incoming && !it.accepted },
                    trades = o.trades.count { waitingOnMe(it, who) }
                )
            }
        } catch (e: Exception) {
            if (userId == who) _error.value = e.message ?: "Something went wrong."
        } finally {
            _loading.value = false
        }
    }

    fun refreshInBackground() { scope.launch { refresh() } }

    suspend fun refreshInbox() {
        val who = userId ?: return
        runCatching { api.inbox() }.onSuccess { if (userId == who) _inbox.value = it }
    }

    /** A trade being put together, kept here so it survives the screen turning. */
    data class TradeDraft(
        val to: String,
        val replyTo: String? = null,
        val want: List<TradeCard> = emptyList(),
        val give: List<TradeCard> = emptyList(),
        val message: String = ""
    )

    /** The trade the composer is working on, if any (the binder screen starts one with its picks). */
    var draft: TradeDraft? = null

    /** Set to open Collection on its Shared page (from Friends); the Collection screen takes it. */
    var openSharedTab: Boolean = false

    /** Ends a life counter table after its screen has gone (so no coroutine of its own is left). */
    fun endMatchInBackground(matchId: String) { scope.launch { runCatching { api.endMatch(matchId) } } }

    /** Gets up from a life counter seat, after the remote screen has gone. */
    fun leaveSeatInBackground(matchId: String, seat: Int) { scope.launch { runCatching { api.clearMatchSeat(matchId, seat) } } }

    /** The badge checks in whenever the app comes back to the front (MainActivity.onResume). */
    fun refreshInboxInBackground() { scope.launch { refreshInbox() } }
}
