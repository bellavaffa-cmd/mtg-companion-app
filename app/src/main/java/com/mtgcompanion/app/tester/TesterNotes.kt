package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "live-social",
        "Trades and friend requests update straight away",
        "Needs the server update for the other phone's side; your own changes show at once either way. With two phones signed in as friends, both on Friends: on phone A send a friend request (or a trade) — phone B should show it within a second or two, without leaving the screen. On B accept it: A should change to friends (or \"Accepted\") just as fast. On A open a trade under Waiting on them and tap Cancel request: on A it should drop straight to Done as one line saying Cancelled, and on B it should leave Your turn at the same moment. Try Decline, Counter, Update my binders, removing a friend and blocking the same way — the phone you tap on changes at once, the other one a moment later. Then turn on airplane mode on B for a minute, make a change on A, and turn it off: B should catch up within a few seconds; leaving the app and coming back should also bring it up to date. Game night answers, loans given back and household invites should show up on the other phone without reopening the screen too."
    )
)
