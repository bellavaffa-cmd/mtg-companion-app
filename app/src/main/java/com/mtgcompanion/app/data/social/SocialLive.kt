package com.mtgcompanion.app.data.social

import org.json.JSONObject

/**
 * Live social updates. The server pings both people's private "dm:<user id>" channel whenever a
 * trade, friend request, loan, game night answer or household changes (event "social", payload
 * {what, id}: supabase/migrations/20261007000000_live_social_updates.sql), and the app reloads just
 * that area — debounced, so a burst of pings is one reload. The web app's twin is src/social/live.ts.
 */
enum class SocialArea {
    /** Trades: the overview (and the badge). */
    TRADES,
    /** Friend requests, friendships, blocks: the overview (and the badge). */
    FRIENDS,
    LOANS,
    NIGHTS,
    HOUSEHOLD;

    /** Whether the area is part of social_overview (trades, friends), so a ping reloads it. */
    val inOverview: Boolean get() = this == TRADES || this == FRIENDS
}

/** The area a live event on the dm channel is about, or null for one that isn't a social change. */
fun socialAreaFor(event: String, payload: JSONObject?): SocialArea? = when (event) {
    "social" -> socialAreaFor(payload?.optString("what").orEmpty())
    // Game nights already announce changes on the same channel (game_nights_chat.sql's night_changed).
    "game_night" -> SocialArea.NIGHTS
    else -> null
}

/** The area for a ping's "what". */
fun socialAreaFor(what: String): SocialArea? = when (what) {
    "trade" -> SocialArea.TRADES
    "friends" -> SocialArea.FRIENDS
    "loan" -> SocialArea.LOANS
    "night" -> SocialArea.NIGHTS
    "household" -> SocialArea.HOUSEHOLD
    else -> null
}

/** Something [SocialDebounce.Scheduler] scheduled, which can be called off. */
fun interface Cancellable {
    fun cancel()
}

/**
 * Gathers areas as pings arrive and hands them to [fire] together, [windowMs] after the first: a burst
 * (both sides of a trade, a night's answers) becomes one reload per area. Scheduling is passed in so
 * tests can run it on a fake clock.
 */
class SocialDebounce(
    private val windowMs: Long,
    private val scheduler: Scheduler,
    private val fire: (Set<SocialArea>) -> Unit
) {
    fun interface Scheduler {
        fun after(delayMs: Long, run: () -> Unit): Cancellable
    }

    private val pending = LinkedHashSet<SocialArea>()
    private var scheduled: Cancellable? = null

    fun add(area: SocialArea) = synchronized(this) {
        pending += area
        if (scheduled == null) scheduled = scheduler.after(windowMs, ::flush)
    }

    private fun flush() {
        val areas = synchronized(this) {
            scheduled = null
            pending.toSet().also { pending.clear() }
        }
        if (areas.isNotEmpty()) fire(areas)
    }

    /** Drops what was waiting (signing out). */
    fun cancel() {
        val job = synchronized(this) {
            pending.clear()
            scheduled.also { scheduled = null }
        }
        job?.cancel()
    }
}

// ---- The user's own changes, shown at once (the server's answer replaces them on the reload) ----

/** [tradeId] with its new [status] (cancel, accept, decline), moved to the top as just changed. */
fun Overview.withTradeStatus(tradeId: String, status: TradeStatus, now: String = java.time.Instant.now().toString()): Overview =
    copy(trades = trades.map { if (it.id == tradeId) it.copy(status = status, updatedAt = now) else it }.sortedByDescending { it.updatedAt })

/** The user's side of [tradeId] marked as done (Update my binders). */
fun Overview.withTradeApplied(tradeId: String, me: String): Overview =
    copy(trades = trades.map {
        when {
            it.id != tradeId -> it
            it.fromUser == me -> it.copy(fromApplied = true)
            it.toUser == me -> it.copy(toApplied = true)
            else -> it
        }
    })

/** [userId]'s request accepted: now a friend. */
fun Overview.withFriendAccepted(userId: String): Overview =
    copy(friends = friends.map { if (it.userId == userId) it.copy(accepted = true) else it })

/** [userId] gone from the user's friends and requests (declined, cancelled, removed, blocked). */
fun Overview.withoutFriend(userId: String): Overview =
    copy(friends = friends.filter { it.userId != userId })

/** The badge's counts, from an overview: requests waiting and trades waiting on the user. */
fun inboxOf(o: Overview, me: String): Inbox =
    Inbox(friendRequests = o.friends.count { it.incoming && !it.accepted }, trades = o.trades.count { waitingOnMe(it, me) })
