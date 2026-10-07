package com.mtgcompanion.app.data.social

import com.mtgcompanion.app.data.supabase.MatchChannel
import com.mtgcompanion.app.data.supabase.SupabaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Friends, pods, shares and trades for the signed-in account, fetched when a screen needs them. None
 * of it is stored on the device, so nothing lingers after signing out.
 */
class SocialRepository(private val auth: SupabaseAuth) {
    val api = SocialApi(auth)
    /** Life counter tables and their players' remotes talk over this. */
    val matchChannel = MatchChannel(auth)
    /** Blocking, messages, reputation, activity and cards for trade (SocialMore.kt), once the server has them. */
    val more = SocialMore(api)
    /** The richer Activity feed, its Privacy switches and deck comments (ActivityComments.kt). */
    val activity = ActivityComments(api, more)
    /** Sharing storage at home (HouseholdApi.kt), once the server has it. */
    val household = HouseholdApi(api)
    /** Game night invites and pod chat (GameNightsApi.kt), once the server has them. */
    val nights = GameNightsApi(api)
    /** New direct messages, live (DmChannel.kt). */
    val dmChannel = DmChannel(auth)
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

    private val _changes = MutableStateFlow<Map<SocialArea, Int>>(emptyMap())
    /**
     * How many times each area has changed on the server since the app started (live pings, the poll,
     * the user's own changes). Screens that load loans, game nights or households themselves key their
     * loading on [changesOf] so they reload when it moves.
     */
    val changes: StateFlow<Map<SocialArea, Int>> = _changes.asStateFlow()
    fun changesOf(area: SocialArea): Int = _changes.value[area] ?: 0

    /** Marks [areas] changed, for the screens that watch [changes]. */
    fun bump(vararg areas: SocialArea) {
        _changes.update { m -> m.toMutableMap().apply { areas.forEach { put(it, (get(it) ?: 0) + 1) } } }
    }

    private val _unread = MutableStateFlow(0)
    /** Unread direct messages, for the Friends tab's badge (0 without the social_more functions). */
    val unread: StateFlow<Int> = _unread.asStateFlow()

    /** The Friends screen tells the badge what it just read. */
    fun setUnread(n: Int) { _unread.value = maxOf(0, n) }

    private val _podUnread = MutableStateFlow(0)
    /** Unread pod chat messages (PodChat.kt), counted with [unread] on the Friends badge and Chats tab. */
    val podUnread: StateFlow<Int> = _podUnread.asStateFlow()

    fun setPodUnread(n: Int) { _podUnread.value = maxOf(0, n) }

    val configured: Boolean get() = auth.configured
    val accountFlow = auth.account
    val userId: String? get() = auth.account.value?.userId
    val email: String? get() = auth.account.value?.email

    // Answers are numbered so a slow reload can't land on top of a newer one (or of the user's own
    // change, shown at once by [mutate]).
    private val generation = AtomicLong(0)
    private val inFlight = AtomicInteger(0)

    /** Reloads everything the Friends screens show. */
    suspend fun refresh() {
        val who = userId ?: return
        val mine = generation.incrementAndGet()
        inFlight.incrementAndGet()
        _loading.value = true
        try {
            val o = api.overview()
            if (userId != who || generation.get() != mine) return
            _overview.value = o
            _error.value = null
            if (o.me != null) _inbox.value = inboxOf(o, who)
        } catch (e: Exception) {
            if (userId == who && generation.get() == mine) _error.value = e.message ?: "Something went wrong."
        } finally {
            _loading.value = inFlight.decrementAndGet() > 0
        }
    }

    /** Reloads the overview in the background, when one has been loaded (a screen showed it). */
    fun refreshInBackground() {
        scope.launch { if (_overview.value != null) refresh() else refreshInbox() }
    }

    /** Shows [change] on the overview at once (its badge counts too), before the server confirms it. */
    fun applyLocal(change: (Overview) -> Overview) {
        val who = userId ?: return
        val o = _overview.value ?: return
        generation.incrementAndGet() // a reload already on its way would undo this
        val next = change(o)
        _overview.value = next
        if (next.me != null) _inbox.value = inboxOf(next, who)
    }

    /**
     * A change the user makes ([action]: the server call): [optimistic] shows it on screen at once,
     * then the overview reloads from the server — in the repository's own scope, so leaving the
     * screen can't stop it. On failure the reload puts back what the server has, and the error is
     * thrown for the screen to show. [areas] are marked changed for screens that load them themselves.
     */
    suspend fun <T> mutate(
        vararg areas: SocialArea,
        optimistic: ((Overview) -> Overview)? = null,
        action: suspend () -> T
    ): T {
        optimistic?.let(::applyLocal)
        try {
            return action()
        } finally {
            if (areas.isNotEmpty()) bump(*areas)
            val reload = scope.launch { refresh() }
            // The screen waits for the reload when it can, so what it shows next is the server's.
            runCatching { reload.join() }
        }
    }

    // ---- Live: pings on the dm channel, the app coming to the front, and a slow poll as a fallback ----

    private val debounce = SocialDebounce(DEBOUNCE_MS, { ms, run ->
        val job = scope.launch { delay(ms); run() }
        Cancellable { job.cancel() }
    }) { areas -> scope.launch { reload(areas) } }

    /** Reloads what [areas] need: the overview for trades and friends, the badge, and the others' ticks. */
    internal suspend fun reload(areas: Set<SocialArea>) {
        bump(*areas.toTypedArray())
        if (areas.any { it.inOverview } && _overview.value != null) refresh()
        refreshInbox()
    }

    /** A live event from the dm channel ("social", "game_night"…). */
    fun onLiveEvent(event: String, payload: org.json.JSONObject?) {
        socialAreaFor(event, payload)?.let(debounce::add)
    }

    @Volatile private var foreground = false
    @Volatile private var live: Job? = null

    /**
     * The app came to the front (MainActivity.onResume): reload what's showing and listen live until
     * [stopLive]. The channel reconnects by itself after a drop, and reloads everything when it does.
     */
    fun startLive() {
        foreground = true
        val uid = userId ?: return
        if (live?.isActive == true) return
        // Whatever changed while the app was in the back (or offline): every area reloads once.
        SocialArea.entries.forEach(debounce::add)
        live = scope.launch {
            launch {
                dmChannel.watch(this, uid,
                    onMessage = {},
                    onRejoined = { SocialArea.entries.forEach(debounce::add) },
                    onOther = { event, payload -> onLiveEvent(event, payload) })
            }
            // The fallback: pings can be missed (a server without them, a dropped connection).
            while (isActive) {
                delay(POLL_MS)
                if (_overview.value != null) refresh() else refreshInbox()
            }
        }
    }

    /** The app went to the back (MainActivity.onPause): no channel or poll while nobody looks. */
    fun stopLive() {
        foreground = false
        live?.cancel()
        live = null
    }

    suspend fun refreshInbox() {
        val who = userId ?: return
        runCatching { api.inbox() }.onSuccess { if (userId == who) _inbox.value = it }
        runCatching { if (more.check()) more.unread() else 0 }.onSuccess { if (userId == who) _unread.value = maxOf(0, it) }
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

    /**
     * Set to open Friends on one of its tabs (a FriendsTab key: "trades", "messages"…) — from a tapped
     * notification. The Friends screen takes it, even when it's already showing.
     */
    val openFriendsTab = MutableStateFlow<String?>(null)

    /** Ends a life counter table after its screen has gone (so no coroutine of its own is left). */
    fun endMatchInBackground(matchId: String) { scope.launch { runCatching { api.endMatch(matchId) } } }

    /** Gets up from a life counter seat, after the remote screen has gone. */
    fun leaveSeatInBackground(matchId: String, seat: Int) { scope.launch { runCatching { api.clearMatchSeat(matchId, seat) } } }

    /** Tells friends' feeds about cards newly marked for trade, after the screen has gone. */
    fun noteForTradeInBackground(cards: List<Pair<String, String?>>) { scope.launch { more.noteForTrade(cards) } }

    /** The badge checks in whenever the app comes back to the front (MainActivity.onResume). */
    fun refreshInboxInBackground() { scope.launch { refreshInbox() } }

    // Last, so everything it uses is set up before its coroutine can run.
    init {
        // A different account (or none) starts from nothing.
        scope.launch {
            auth.account.map { it?.userId }.distinctUntilChanged().collect {
                _overview.value = null
                _inbox.value = Inbox()
                _unread.value = 0
                _podUnread.value = 0
                _error.value = null
                debounce.cancel()
                more.reset()
                activity.reset()
                household.reset()
                nights.reset()
                if (it != null) refreshInbox()
                // The live channel belongs to the account: a new one for whoever is signed in now.
                live?.cancel()
                live = null
                if (foreground) startLive()
            }
        }
    }


    companion object {
        /** Pings that arrive together become one reload. */
        const val DEBOUNCE_MS = 300L
        /** The fallback poll while the app is in front. */
        const val POLL_MS = 60_000L
    }
}
