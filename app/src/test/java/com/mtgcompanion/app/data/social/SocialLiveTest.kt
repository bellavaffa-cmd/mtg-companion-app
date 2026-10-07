package com.mtgcompanion.app.data.social

import com.mtgcompanion.app.ui.social.openKey
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Live social updates (SocialLive.kt). The web app has the same checks — see tests/social/live.test.ts. */
class SocialLiveTest {

    private fun trade(id: String, from: String, to: String, status: TradeStatus, updated: String) =
        Trade(id, from, to, emptyList(), emptyList(), null, null, status, false, false, updated)

    private val me = Profile("me", "me", "Me", null)

    private fun overview(trades: List<Trade> = emptyList(), friends: List<FriendLink> = emptyList()) =
        Overview(me, emptyMap(), friends, emptyList(), emptyList(), emptyList(), trades)

    /** A clock the test moves by hand. */
    private class FakeScheduler : SocialDebounce.Scheduler {
        var now = 0L
        val waiting = mutableListOf<Pair<Long, () -> Unit>>()
        override fun after(delayMs: Long, run: () -> Unit): Cancellable {
            val entry = (now + delayMs) to run
            waiting += entry
            return Cancellable { waiting.remove(entry) }
        }
        fun advance(ms: Long) {
            now += ms
            waiting.filter { it.first <= now }.forEach { waiting.remove(it); it.second() }
        }
    }

    // ---- Event → area ----

    @Test
    fun eachPingReloadsItsArea() {
        fun ping(what: String) = socialAreaFor("social", JSONObject().put("what", what).put("id", "x"))
        assertEquals(SocialArea.TRADES, ping("trade"))
        assertEquals(SocialArea.FRIENDS, ping("friends"))
        assertEquals(SocialArea.LOANS, ping("loan"))
        assertEquals(SocialArea.NIGHTS, ping("night"))
        assertEquals(SocialArea.HOUSEHOLD, ping("household"))
        assertNull(ping("something new"))
        assertNull(socialAreaFor("social", null))
    }

    @Test
    fun otherChannelEventsAreLeftAlone() {
        assertEquals(SocialArea.NIGHTS, socialAreaFor("game_night", JSONObject().put("nightId", "n")))
        assertNull(socialAreaFor("message", JSONObject()))
        assertNull(socialAreaFor("pod_message", JSONObject()))
    }

    @Test
    fun onlyTradesAndFriendsAreInTheOverview() {
        assertEquals(setOf(SocialArea.TRADES, SocialArea.FRIENDS), SocialArea.entries.filter { it.inOverview }.toSet())
    }

    // ---- Debounce ----

    @Test
    fun aBurstOfPingsIsOneReload() {
        val clock = FakeScheduler()
        val fired = mutableListOf<Set<SocialArea>>()
        val d = SocialDebounce(300, clock) { fired += it }
        d.add(SocialArea.TRADES)
        clock.advance(100)
        d.add(SocialArea.TRADES)
        d.add(SocialArea.FRIENDS)
        clock.advance(150)
        assertTrue(fired.isEmpty())
        clock.advance(50)
        assertEquals(listOf(setOf(SocialArea.TRADES, SocialArea.FRIENDS)), fired)
        // A later ping starts a new window.
        d.add(SocialArea.LOANS)
        clock.advance(299)
        assertEquals(1, fired.size)
        clock.advance(1)
        assertEquals(setOf(SocialArea.LOANS), fired.last())
    }

    @Test
    fun cancellingDropsWhatWasWaiting() {
        val clock = FakeScheduler()
        val fired = mutableListOf<Set<SocialArea>>()
        val d = SocialDebounce(300, clock) { fired += it }
        d.add(SocialArea.TRADES)
        d.cancel()
        clock.advance(1_000)
        assertTrue(fired.isEmpty())
        d.add(SocialArea.NIGHTS)
        clock.advance(300)
        assertEquals(listOf(setOf(SocialArea.NIGHTS)), fired)
    }

    // ---- The user's own changes, at once ----

    @Test
    fun aCancelledTradeLeavesWaitingAtOnce() {
        val o = overview(listOf(trade("t1", "me", "sam", TradeStatus.OPEN, "2026-10-01T10:00:00Z"), trade("t2", "sam", "me", TradeStatus.DECLINED, "2026-10-02T10:00:00Z")))
        assertEquals(listOf("t1"), tradeInbox(o.trades, "me").waitingOnThem.map { it.id })
        val after = o.withTradeStatus("t1", TradeStatus.CANCELLED, now = "2026-10-07T10:00:00Z")
        val inbox = tradeInbox(after.trades, "me")
        assertTrue(inbox.waitingOnThem.isEmpty())
        assertEquals(listOf("t1", "t2"), inbox.done.map { it.id })
        assertEquals(TradeStatus.CANCELLED, inbox.done.first().status)
    }

    @Test
    fun anAcceptedTradeAsksForTheBinders() {
        val o = overview(listOf(trade("t1", "sam", "me", TradeStatus.OPEN, "2026-10-01T10:00:00Z")))
        assertEquals(Inbox(0, 1), inboxOf(o, "me"))
        val accepted = o.withTradeStatus("t1", TradeStatus.ACCEPTED)
        assertTrue(awaitingMyUpdate(accepted.trades.single(), "me"))
        val applied = accepted.withTradeApplied("t1", "me")
        assertTrue(applied.trades.single().toApplied)
        assertEquals(Inbox(0, 0), inboxOf(applied, "me"))
    }

    @Test
    fun answeringARequestShowsAtOnce() {
        val o = overview(friends = listOf(FriendLink("sam", accepted = false, incoming = true), FriendLink("jo", accepted = false, incoming = false)))
        assertEquals(Inbox(1, 0), inboxOf(o, "me"))
        val accepted = o.withFriendAccepted("sam")
        assertTrue(accepted.isFriend("sam"))
        assertEquals(Inbox(0, 0), inboxOf(accepted, "me"))
        val cancelled = o.withoutFriend("jo")
        assertEquals(listOf("sam"), cancelled.friends.map { it.userId })
    }

    @Test
    fun anOpenedTradeClosesWhenItsStatusChanges() {
        val t = trade("t1", "me", "sam", TradeStatus.OPEN, "2026-10-01T10:00:00Z")
        assertNotEquals(openKey(t), openKey(t.copy(status = TradeStatus.CANCELLED)))
        assertEquals(openKey(t), openKey(t.copy(updatedAt = "2026-10-02T10:00:00Z")))
    }
}
