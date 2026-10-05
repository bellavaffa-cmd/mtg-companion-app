package com.mtgcompanion.app.data.social

import org.junit.Assert.assertEquals
import org.junit.Test

/** The Friends screen's tabs. The web app has the same checks — see tests/social/friendsTabs.test.ts. */
class FriendsTabsTest {

    @Test
    fun messagesAndActivityOnlyShowWhenTheServerHasThem() {
        assertEquals(listOf(FriendsTab.PEOPLE, FriendsTab.MESSAGES, FriendsTab.TRADES, FriendsTab.ACTIVITY), friendsTabs(true))
        assertEquals(listOf(FriendsTab.PEOPLE, FriendsTab.TRADES), friendsTabs(false))
        assertEquals(listOf(FriendsTab.PEOPLE, FriendsTab.TRADES), friendsTabs(null))
    }

    @Test
    fun aLinkOrNotificationOpensItsTab() {
        assertEquals(FriendsTab.TRADES, friendsTabFor("trades", true))
        assertEquals(FriendsTab.TRADES, friendsTabFor("trades", false))
        assertEquals(FriendsTab.MESSAGES, friendsTabFor("messages", true))
        assertEquals(FriendsTab.ACTIVITY, friendsTabFor("activity", true))
        assertEquals(FriendsTab.PEOPLE, friendsTabFor("friends", true))
        assertEquals(FriendsTab.PEOPLE, friendsTabFor("people", true))
        assertEquals(FriendsTab.PEOPLE, friendsTabFor(null, true))
        assertEquals(FriendsTab.PEOPLE, friendsTabFor("nonsense", true))
    }

    @Test
    fun withoutSocialMoreMessagesAndActivityFallBackToPeople() {
        assertEquals(FriendsTab.PEOPLE, friendsTabFor("messages", false))
        assertEquals(FriendsTab.PEOPLE, friendsTabFor("activity", null))
    }

    @Test
    fun eachTabCarriesWhatWaitsOnIt() {
        val waiting = FriendsWaiting(requests = 2, unread = 3, trades = 1)
        assertEquals(mapOf(0 to 2, 1 to 3, 2 to 1), friendsTabCounts(friendsTabs(true), waiting))
        assertEquals(mapOf(0 to 2, 1 to 1), friendsTabCounts(friendsTabs(false), waiting))
        assertEquals(mapOf(2 to 4), friendsTabCounts(friendsTabs(true), FriendsWaiting(0, 0, 4)))
        assertEquals(emptyMap<Int, Int>(), friendsTabCounts(friendsTabs(true), FriendsWaiting(0, 0, 0)))
    }
}
