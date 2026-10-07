package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "friends-tab",
        "Friends is a tab of the bar",
        "The bottom bar should now read Home · Search · Play | Scan | Decks · Collection · Friends, with the highlight sliding to whichever you tap (Friends included). With a friend request, a trade waiting on you or an unread message, Friends shows a gold count. Try it on a small phone and with the phone's font size at the largest: all seven should fit, each still easy to tap; long labels may shorten. Back from Friends should go to Home, like the other tabs. Tap a trade or message notification: Friends should open on Trades or Chats. On a tablet, the rail and sidebar should list the same order."
    ),
    TesterNote(
        "friends-people",
        "Friends › People",
        "Friends opens on People: Your pods side by side, each with \"5 people · Season 2 · you're 2nd\" when a league season is running (\"no season\" otherwise), then Friends · N with one line each — \"Wants 3 of your cards · \$21\" (tap Trade: the composer opens with both sides), \"Has your Sol Ring · back by 10 Oct\" for a card you lent them (Loan opens what you lent), or \"Shares the shelf at home\". Add a friend is the person+ button at the top. The tabs read People · Chats · Trades · Activity, with counts in the labels (\"Chats · 3\"); Chats is your messages, as before."
    ),
    TesterNote(
        "trade-inbox",
        "Trade inbox",
        "Friends › Trades: Your turn first — each trade in full with the fairness bar, Counter and Accept (and Update my binders once accepted). Waiting on them: one line each (\"To Sam · Impulse for Lightning Greaves · Sent 7 Oct\"); tap one to open it, Cancel request is there. What friends want from you: a line per friend with how many cards, the binders they're in and what they're worth; tap a line or Make offers to start a trade. Done: \"With Alex · 28 Sep\" with your rating; tap to rate."
    ),
    TesterNote(
        "play-table",
        "Play is just the table",
        "Play: Start a game, then At the table with four tiles (Game night, Playgroup, Events, Pack your bag), Recent games, and a small note at the bottom: \"People, chats and trades moved to the Friends tab.\" Joining a table and Back to seat should work as before."
    )
)
