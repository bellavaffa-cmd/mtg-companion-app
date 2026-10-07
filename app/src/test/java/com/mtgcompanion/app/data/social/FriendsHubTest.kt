package com.mtgcompanion.app.data.social

import com.mtgcompanion.app.data.Loan
import com.mtgcompanion.app.data.LoanCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Friends tab's logic. The web app has the same checks — see tests/social/friendsHub.test.ts. */
class FriendsHubTest {

    private fun trade(
        id: String,
        from: String,
        to: String,
        status: TradeStatus,
        updated: String,
        fromApplied: Boolean = false,
        toApplied: Boolean = false,
        want: List<TradeCard> = emptyList(),
        give: List<TradeCard> = emptyList()
    ) = Trade(id, from, to, want, give, null, null, status, fromApplied, toApplied, updated)

    private fun card(name: String, qty: Int = 1, id: String = name, binder: String? = null, foil: Boolean = false) =
        TradeCard(id, name, quantity = qty, collectionId = binder, foil = foil)

    // ---- Badge ----

    @Test
    fun theBadgeAddsRequestsUnreadAndTrades() {
        assertEquals(6, friendsBadge(requests = 1, unread = 3, trades = 2))
        assertEquals(0, friendsBadge(0, 0, 0))
        assertEquals(2, friendsBadge(-1, 2, 0))
        assertEquals("5", badgeText(5))
        assertEquals("9+", badgeText(12))
    }

    @Test
    fun tabLabelsCarryTheirCounts() {
        assertEquals("Chats · 3", friendsTabLabel(FriendsTab.MESSAGES, 3))
        assertEquals("Activity", friendsTabLabel(FriendsTab.ACTIVITY, 0))
        assertEquals(FriendsTab.MESSAGES, friendsTabFor("chats", true))
    }

    // ---- Trade inbox ----

    @Test
    fun tradesAreGroupedByWhoseTurnItIs() {
        val me = "me"
        val incoming = trade("a", "priya", me, TradeStatus.OPEN, "2026-10-05")
        val sent = trade("b", me, "sam", TradeStatus.OPEN, "2026-10-06")
        val toUpdate = trade("c", "alex", me, TradeStatus.ACCEPTED, "2026-10-01", fromApplied = true)
        val theyUpdate = trade("d", me, "jo", TradeStatus.ACCEPTED, "2026-10-02", fromApplied = true)
        val finished = trade("e", me, "alex", TradeStatus.ACCEPTED, "2026-09-28", fromApplied = true, toApplied = true)
        val declined = trade("f", "sam", me, TradeStatus.DECLINED, "2026-09-30")
        val inbox = tradeInbox(listOf(incoming, sent, toUpdate, theyUpdate, finished, declined), me)
        assertEquals(listOf("a", "c"), inbox.yourTurn.map { it.id })
        assertEquals(listOf("b", "d"), inbox.waitingOnThem.map { it.id })
        assertEquals(listOf("f", "e"), inbox.done.map { it.id })
    }

    @Test
    fun blockedPeoplesTradesAreLeftOut() {
        val inbox = tradeInbox(listOf(trade("a", "priya", "me", TradeStatus.OPEN, "2026-10-05")), "me", blocked = setOf("priya"))
        assertEquals(0, inbox.yourTurn.size + inbox.waitingOnThem.size + inbox.done.size)
    }

    @Test
    fun aTradeReadsAsWhatYouGiveForWhatYouGet() {
        val t = trade("a", "me", "sam", TradeStatus.OPEN, "2026-10-05", want = listOf(card("Lightning Greaves")), give = listOf(card("Impulse")))
        assertEquals("Impulse for Lightning Greaves", tradeSummary(t, "me"))
        assertEquals("Lightning Greaves for Impulse", tradeSummary(t, "sam"))
        assertEquals("Fact or Fiction ×3, Cyclonic Rift", cardNames(listOf(card("Fact or Fiction", 3), card("Cyclonic Rift"))))
        assertEquals("A, B and 2 more", cardNames(listOf(card("A"), card("B"), card("C"), card("D"))))
        assertEquals("nothing", cardNames(emptyList()))
    }

    @Test
    fun aFinishedTradeShowsTheRatingOrHowItEnded() {
        val done = trade("e", "me", "alex", TradeStatus.ACCEPTED, "2026-09-28", fromApplied = true, toApplied = true)
        assertEquals("Rated good", doneLabel(done, "me", true))
        assertEquals("Rated poor", doneLabel(done, "me", false))
        assertEquals("Rate it", doneLabel(done, "me", null))
        assertEquals("Declined", doneLabel(trade("f", "sam", "me", TradeStatus.DECLINED, "2026-09-30"), "me", null))
    }

    // ---- What friends want from you ----

    @Test
    fun whatFriendsWantIsCountedPricedAndPlacedPerFriend() {
        val priya = TradeMatch("priya", emptyList(), listOf(card("Sol Ring", id = "s", binder = "b1"), card("Rhystic Study", id = "r", binder = "b2"), card("sol ring", id = "s2", binder = "b1")))
        val jo = TradeMatch("jo", emptyList(), listOf(card("Impulse", id = "i", binder = "b3")))
        val nobody = TradeMatch("sam", listOf(card("Mox")), emptyList())
        val prices = mapOf("s" to CardPrice(2.0, null), "r" to CardPrice(19.0, null), "i" to CardPrice(null, null))
        val names = mapOf("b1" to "Trade binder", "b2" to "Red box", "b3" to "Blue box")
        val wants = friendsWantFromYou(listOf(jo, priya, nobody), { names[it] }, prices)
        assertEquals(listOf("priya", "jo"), wants.map { it.friend })
        assertEquals(2, wants[0].cards)
        assertEquals("Trade binder, Red box", wants[0].where)
        assertEquals(21.0, wants[0].value!!, 0.001)
        assertNull(wants[1].value)
        assertEquals("Priya · 3 cards", wantFromYouLine("Priya", 3))
        assertEquals("Jo · 1 card", wantFromYouLine("Jo", 1))
    }

    // ---- A friend's line ----

    @Test
    fun aFriendsLineSaysTheFirstThingThatApplies() {
        assertEquals(FriendContext("Wants 3 of your cards · \$21", FriendAction.TRADE), friendContext(3, "\$21", listOf("Sol Ring"), "Back by 10 Oct", true))
        assertEquals(FriendContext("Has your Sol Ring · back by 10 Oct", FriendAction.LOAN), friendContext(0, null, listOf("Sol Ring"), "Back by 10 Oct", true))
        assertEquals(FriendContext("Has 2 of your cards", FriendAction.LOAN), friendContext(0, null, listOf("Sol Ring", "Sol Ring"), "No date", false))
        assertEquals(FriendContext("Shares the shelf at home", FriendAction.HOME), friendContext(0, null, emptyList(), null, true))
        assertNull(friendContext(0, null, emptyList(), null, false))
    }

    @Test
    fun lentToCountsTheCopiesStillOut() {
        val loans = listOf(
            Loan("1", "Sam", friendId = "sam", cards = listOf(LoanCard("Sol Ring", "x", qty = 2, back = 1))),
            Loan("2", "Sam", friendId = "sam", cards = listOf(LoanCard("Mox", "y", qty = 1, back = 1))),
            Loan("3", "Jo", friendId = "jo", cards = listOf(LoanCard("Impulse", "z", qty = 1)))
        )
        assertEquals(listOf("Sol Ring"), lentTo(loans, "sam"))
        assertEquals(emptyList<String>(), lentTo(loans, "alex"))
    }

    // ---- Pods ----

    @Test
    fun aPodsLineHasItsSeasonAndYourPlace() {
        assertEquals("5 people · Season 2 · you're 2nd", podLine(5, "Season 2", 2))
        assertEquals("5 people · Season 2", podLine(5, "Season 2", null))
        assertEquals("3 people · no season", podLine(3, null, null))
        assertEquals("1 person", peopleLine(1))
        assertEquals(listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "102nd"), listOf(1, 2, 3, 4, 11, 12, 13, 21, 102).map(::ordinal))
    }
}
