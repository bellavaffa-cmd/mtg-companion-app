package com.mtgcompanion.app.data.social

// The Friends tab's tabs: People, Chats, Trades and Activity — Chats and Activity only when the
// server has the social_more functions. Chats keeps the key "messages", which notifications and links
// already use; "chats" opens it too. Which tab a tapped notification ("friends" / "trades" /
// "messages") or a link opens, and the counts on each. Pure, so it's tested. The web app's twin is
// src/social/friendsTabs.ts (tests: FriendsTabsTest.kt / tests/social/friendsTabs.test.ts).

enum class FriendsTab(val key: String, val label: String) {
    PEOPLE("people", "People"),
    MESSAGES("messages", "Chats"),
    TRADES("trades", "Trades"),
    ACTIVITY("activity", "Activity")
}

/** The tabs shown: all four with social_more ([more] true), else People and Trades. */
fun friendsTabs(more: Boolean?): List<FriendsTab> =
    if (more == true) listOf(FriendsTab.PEOPLE, FriendsTab.MESSAGES, FriendsTab.TRADES, FriendsTab.ACTIVITY)
    else listOf(FriendsTab.PEOPLE, FriendsTab.TRADES)

/**
 * The tab a link or notification asks for ([asked], a tab's key), among those shown. Anything else —
 * no tab, "friends", "requests", or Messages/Activity without social_more — is People.
 */
fun friendsTabFor(asked: String?, more: Boolean?): FriendsTab {
    val tab = when (asked) {
        "trades" -> FriendsTab.TRADES
        "messages", "chats" -> FriendsTab.MESSAGES
        "activity" -> FriendsTab.ACTIVITY
        else -> FriendsTab.PEOPLE
    }
    return if (tab in friendsTabs(more)) tab else FriendsTab.PEOPLE
}

/** What waits on each tab: friend requests on People, unread messages, trades waiting on the user. */
data class FriendsWaiting(val requests: Int, val unread: Int, val trades: Int)

/** The badge counts for [tabs], by position; tabs with nothing waiting have none. */
fun friendsTabCounts(tabs: List<FriendsTab>, waiting: FriendsWaiting): Map<Int, Int> =
    tabs.mapIndexedNotNull { i, tab ->
        val n = when (tab) {
            FriendsTab.PEOPLE -> waiting.requests
            FriendsTab.MESSAGES -> waiting.unread
            FriendsTab.TRADES -> waiting.trades
            FriendsTab.ACTIVITY -> 0
        }
        if (n > 0) i to n else null
    }.toMap()

/** A tab's label with what waits on it: "Chats · 3", or plain "Activity". */
fun friendsTabLabel(tab: FriendsTab, count: Int): String = if (count > 0) "${tab.label} · $count" else tab.label
