package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
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
        "print-proxies",
        "Print proxies",
        "Open a deck › menu › Print proxies…: the cards you don't own should be picked already (a deck you hold: its proxies), with − and + to choose how many of each. Pick A4 or Letter, \"PROXY — not for sale\", Low ink and Back faces too, then Print or save PDF and save it as a PDF. Nine cards a page, cut lines along every edge; printed at 100%, a card should measure 63 × 88 mm. Also try Print proxies under Not owned on a deck's pull list (with \"Mark as proxies in …\" on, the deck should show those cards as proxies afterwards) and on Collection › Spread thin, which picks the copies each card is short."
    ),
    TesterNote(
        "hand-stats",
        "Hand stats in the playtest",
        "Open a deck › menu › Playtest and tap the chart button at the top: Hand stats should show the chance of 2–4 lands in your opening seven, lands on average, how often you'd mulligan keeping 2–5 lands, a land drop on turns 1 to 4 on the play and on the draw, and a two-drop on turn 2. A Commander deck counts the 99, not the commander. The numbers come from 10,000 shuffles with a fixed seed, so they stay the same each time you open it for the same deck. Mulligan, put cards on the bottom, keep and draw should all still work as before."
    ),
    TesterNote(
        "trade-fairness",
        "Is the trade fair?",
        "Friends › a friend › Propose a trade: pick a dear card of theirs and a cheap one of yours. Under the cards it should show You get and You give at today's prices (in your currency from Settings), a bar with your side filled in against the middle line, and \"You give \$12.00 more\" (or \"Within \$1.50 — a fair trade\" when it's close). When it's uneven and they have cards you want (or want cards you have spare or for trade), \"To even it out…\" lists up to three, closest to the gap first: tap Add and it goes on the right side and the bar moves. A card with no price should say \"1 card has no price and is left out.\" On Trades, a trade a friend sent you shows the same; tapping Counter with it opens a counter-offer with that card added."
    ),
    TesterNote(
        "trade-matches-tonight",
        "Trade matches tonight",
        "Play › Game night: add a friend whose wishlist you share and a guest by name. Below the pods, \"Trade matches tonight\" should show the friend with the cards on their wishlist you have spare or marked for trade, each with where it is (\"Trade binder · Page 4, slot 6\", \"Red box\"), and their for-trade cards you want. Propose a trade should open the composer with both sides filled in; Bring them should put the cards on the \"Bring to game night\" pull list and turn into Open pull list. The guest should say \"Add Priya as a friend to see what they want.\" Pack your bag › a bag with that friend in Who's coming should show the same under the checklist."
    ),
    TesterNote(
        "pod-league",
        "League mode for your playgroup",
        "Play › Playgroup, pick a pod: under the pod's name, League › Start Season 1. Pick when it ends (after 8 game nights, on a date, or when you end it) and the points (Standard: 3 for a win, 1 for second, 1 for first blood; Wins only; Everyone scores; or change any number). Record a game: with Standard, it should ask for Second place and First blood too. The table should show points, games, wins, win % and streak (\"W2\"), and Points per game night. Someone else in the pod should see the same table on their phone or on manabind.com. End season: it should say who is champion (\"Season 1 champion: Priya\") and move to Past seasons with its final table, and Start Season 2 should come back. Game night with a season running: \"This counts for Season 1\" at the top; pick winners, then Send results to the league — the games should turn up in the pod. Until the server is updated it should say \"Leagues aren't available yet\"."
    ),
    TesterNote(
        "value-over-time",
        "Collection value over time",
        "Tap the value at the top of Collection (or Home's Collection value): the chart should now be worked out from each card's saved prices, with 1M (daily), 6M and 1Y (weekly) and All. Under it: \"Value history starts 12 Sep, when this device began saving prices\" with your own date — nothing before that. Touch the line for a day's value and how many cards were priced. Below: Risers and fallers over the range (card, change and %; tap one for its page) and, with more than one binder, By binder. With TalkBack on, the chart should read the trend out (\"up \$76 (6.2%), from … to …, lowest … highest …\")."
    ),
    TesterNote(
        "new-sets",
        "New sets",
        "Collection home › New sets (\"2 coming soon · 1 just out\"): Just out and Coming soon, with release date and how many cards Scryfall has shown. Open one with cards: Cards for your decks lists, per Commander deck, cards in the commander's colours that share a creature type or theme with at least four of its cards (\"Elf, like 14 cards in the deck\"), and On your Wishlist shows new printings of Wishlist cards. Tap the bell to follow a set coming soon (allow notifications): on its release day you should get \"<set> is out today\", once; tapping it opens the set."
    )
)
