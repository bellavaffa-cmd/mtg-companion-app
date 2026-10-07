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
    ),
    TesterNote(
        "game-night-invites",
        "Game night invites",
        "Play › Playgroup, pick a pod › Plan a game night: pick the day and time, where (\"Priya's\"), a note, and any friends from outside the pod, then Invite the pod. Everyone in the pod should get a notification (\"Priya invited you to game night\") that opens the invite: the date in gold, \"At Priya's\", \"Thursday crew · counts for Season 2 · asked by Priya\" when a season runs, Going / Maybe / Can't, and Bringing to pick your deck (\"Going · Krenko\" in Who's coming). WHO'S COMING should say how many of how many, the organiser \"Going · hosting\". Ready for the night: Pack your bag should open a bag with who's coming and your deck; Trade matches tonight shows the friends coming; Give back … shows when you've borrowed from someone coming. Add to calendar opens your calendar app filled in; Make pods on the night opens Game night with everyone coming. The organiser can Change (everyone is told the new time or place) and Call it off. A day before, you should get \"Game night tomorrow\" unless you said Can't. On the pod's page the next night shows as a card. Until the server is updated it should say \"Game night invites aren't available yet\"."
    ),
    TesterNote(
        "pod-chat",
        "Pod chat",
        "Play › Playgroup, pick a pod › Pod chat: the pod's name with \"Priya, Sam and you\" under it. Send a message: everyone in the pod should get it at once (and a notification if the app is closed), with your name over a run of your messages and a heading for each day. Record a game in the pod: \"Priya won with Atraxa. Sam took first blood.\" should appear, marked LEAGUE · SEASON 2 with the table under it when a season runs. Planning a game night puts a card in the chat with Going?. The + button shares a game night, a deck or a card. Tap someone's name to report or block them; once blocked, their messages are gone from your chat. Until the server is updated it should say \"Pod chat isn't available yet\"."
    ),
    TesterNote(
        "friends-activity",
        "A richer friends' Activity",
        "Needs the server update; until then Activity shows what it did before, in the new layout. Friends › Activity, with a friend who marks a card for trade that's on your Wishlist: \"Priya added Rhystic Study to their trade binder\", \"It's on your wishlist · 1 h ago\" and \"Ask Priya for it\", which should open Propose a trade with that card asked for. A friend who shares a new deck: \"Sam built a new deck: …\" with \"Look and comment\" opening the deck on Comments. Record games in a pod with a season running: \"Thursday crew: Priya leads Season 2 by 3 points\" (and \"2 game nights left\" for a season of so many nights); ending it: \"Season 2 champion: Priya\". A friend who turned on their To sell list: \"Jo is selling 12 cards\", \"2 are on your wishlist\", and See them listing the cards with Ask Jo for them. Comments on your decks show up too, with Reply."
    ),
    TesterNote(
        "activity-privacy",
        "What friends see of you",
        "Settings › Privacy › Friends' activity: New and changed decks, Cards for trade and League results should be on, Your To sell list off. Turn one off and ask a friend to pull down their Activity: those items of yours should be gone (league news then says \"New results in Season 2\" instead of naming you). Turn the To sell list on and mark cards to sell: your friends should see \"<you> is selling N cards\". The line under Activity, \"Choose what's shared in Settings › Privacy\", should open that page."
    ),
    TesterNote(
        "deck-comments",
        "Comments on shared decks",
        "Open a deck a friend shares with you (Friends › Shared, or from Activity): Cards and Comments tabs. Write a comment, or tap On a card and pick one (\"Priya · on Gray Merchant of Asphodel\"), or pick one of your own cards for trade to suggest (\"on the deck · suggests Skullclamp\") — that comment gets \"Offer it in a trade\", opening Propose a trade with your copy offered. Reply to a comment (one level). The owner should get a notification and see it in Activity; on their deck they can hide (eye) or delete any comment, and Consider a swap opens their deck's Considering tab. Your own comments can be deleted; anyone else's has the flag to report or block. The tab shows the count (\"Comments · 3\"). Someone the deck isn't shared with can't open it at all."
    ),
    TesterNote(
        "friends-together",
        "Everything in the Friends tab together",
        "With a game night planned in one of your pods: Friends › People should show it at the top (date tile, \"Game night at Priya's\", Going? until you answer) and Play should show \"Next game night · Fri 10 Oct\" under Start a game; both open the invite. Tap a pod under Your pods: its chat opens; Plan a game night under the pod's name opens the form, Members its people. Friends › Chats should list pod chats among your messages, newest first by their last message, each with its unread count; \"Chats · N\" and the gold count on Friends should add pod messages to direct ones and drop once you've read them. Friends › Activity: \"Ask Priya for it\" opens Propose a trade with the card asked for, \"Look and comment\" and a comment open the deck on Comments, a league item opens Playgroup, \"Jo is selling 12 cards\" opens the list, and the Privacy line opens Settings › Privacy."
    )
)
