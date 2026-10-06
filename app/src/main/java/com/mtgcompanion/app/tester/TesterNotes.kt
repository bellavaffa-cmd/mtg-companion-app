package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "qr-sign-in",
        "Sign in with a QR code",
        "On a phone that's signed out (or the tester app after signing out), Settings › Account & sync › Sign in with a QR code. Scan it with another phone that's signed in (Manabind › Scan, or the camera opening manabind.com): approve, and the first phone should sign in and sync by itself. Let a code run out to check a new one appears."
    ),
    TesterNote(
        "search-view",
        "List or grid in search results",
        "Search for something, then tap the grid/list button at the top right of Results. Your choice should stick next time, and match Settings › Card Display › Search results."
    ),
    TesterNote(
        "android-16",
        "Built for Android 16 — check the edges and the keyboard",
        "Type in Messages, Profile, a trade message, Search and a deck rename: the field and send button should sit just above the keyboard. Rename a player on the life counter: is the name field hidden by the keyboard? Swipe the status and navigation bars into view in light and dark themes: icons readable? Open the scanner and the QR scanner in portrait and landscape."
    ),
    TesterNote(
        "back-gesture",
        "New back gesture",
        "With Android 15 or 16, swipe back from a tab, from card zoom, from a sheet and from selection mode: each should close the right thing. On the life counter, back still needs pressing twice."
    ),
    TesterNote(
        "community-rules",
        "Community rules",
        "The first time you save your profile, send a message or share something, a Community rules sheet appears once. Not now should post nothing; Agree lets it go ahead and it shouldn't ask again."
    ),
    TesterNote(
        "accessibility",
        "Accessibility",
        "Turn on TalkBack and try the life counter: each seat reads as one line (seat, name, life, commander damage) and the new total is read once after you stop tapping. Try a deck page and Settings too. Then set the font size to the largest: Settings, tabs and chips should still fit. Check the accent buttons in the Sapphire, Amethyst and Ruby colours now have dark text."
    ),
    TesterNote(
        "welcome",
        "Welcome steps and sample content",
        "Settings › Getting started: go through the welcome steps (import a CSV from another app if you have one). Try Add the sample deck and binder, look around, then Remove samples — and check samples never show on your other device."
    ),
    TesterNote(
        "play-friends-layout",
        "New Play and Friends layout",
        "Play: Start a game, Your group tiles and Recent games. Friends: People, Messages, Trades and Activity tabs, with badges. Tap a message or trade notification and check it opens the right tab."
    ),
    TesterNote(
        "empty-states",
        "Empty screens tell you what to do",
        "Look at a few empty places (a new binder, Events, Loans, Messages): each should say what goes there and offer a button."
    ),
    TesterNote(
        "privacy-account",
        "Privacy, usage counts and deleting your account",
        "Settings › Privacy: the usage counts switch. Account & sync › Delete my account should open (it says it isn't available yet until the server part is added — don't use your real account to test it later)."
    ),
    TesterNote(
        "visual-fixes",
        "Layout fixes",
        "Pull lists, put back, loans, place pages and labels had text overlapping or squeezed; check they look tidy now."
    ),
    TesterNote(
        "sort-pile",
        "Sorting a new pile",
        "Scan › Sort a pile: scan a handful of new cards. Each shows a big pile number and where it goes; check the piles make sense, then Done: file every pile and look the cards up in Where it is."
    ),
    TesterNote(
        "loans",
        "Lending cards",
        "From a card's Where it is, Lend a copy to a friend (and one to someone not on Manabind). Check Friends › Loans, then Got them back: each card should go back where it came from. If your friend has the tester app, they should see it under Borrowed and get a notification; try Remind."
    ),
    TesterNote(
        "history-value",
        "A copy's history and value by place",
        "Open a card you've moved around › Where it is › History. Then Storage › Value by place: check the totals, and try Spreadsheet and PDF report."
    ),
    TesterNote(
        "deck-extras",
        "Primers, categories, companions, folders",
        "On a deck: write an About (try [[Sol Ring]] as a link), Group by Category and Suggest categories, set a target like Ramp 10. Add a companion to a 60-card deck and check the legality message. On Decks, make a folder and archive a deck."
    ),
    TesterNote(
        "game-extras",
        "Dungeons, new counters, life chart, mulligans",
        "In a life counter game: venture into a dungeon (and take the initiative for Undercity), try rad, speed and the Ring counters, record a mulligan. After the game open Life chart from the menu, and check the mulligan shows on your deck's match record."
    ),
    TesterNote(
        "top-cut",
        "Top 8 playoff",
        "Run a small event to the end of Swiss, then Standings › Cut to top 4 or 8: play the bracket through to a champion."
    ),
    TesterNote(
        "friends-extras",
        "Messages, block and report, activity, for trade",
        "Friends: message a friend, check the unread count. Mark a few binder cards for trade and look at Friends want / trade matches. Open Activity. Try Block on someone you don't mind (and unblock in Settings)."
    ),
    TesterNote(
        "binder-pages",
        "Binder pages and fitting cards in order",
        "Open a binder in Storage › Pages: flip through (arrows or swipe), tap a card, try Move and Edit (swap two pockets). Give the binder an order in Change place, put a few cards away into it, then Fit in order: follow the steps with real cards and check Show on pages matches your binder."
    ),
    TesterNote(
        "box-check",
        "Checking a box",
        "Open a box › Check, pick a section, and scan everything in it, including a card that belongs elsewhere. Finish check: the missing and extra cards should be right. Try Record them here, then check Last checked shows on the box."
    ),
    TesterNote(
        "lifecounter-crash",
        "Life counter crash fixed",
        "Build 13 could crash while a life counter panel slid in. Open and close the life counter menus and dialogs a few times: nothing should crash."
    ),
    TesterNote(
        "pull-list",
        "Pull lists and putting a deck back",
        "On a Virtual or Prototype deck tap Build this deck: cards are grouped by place with where to find them. Tick some (or Scan to tick), then Move pulled into deck box and check the deck and places changed. On a Physical deck try Take apart: each card should go back where it came from."
    ),
    TesterNote(
        "box-labels",
        "Box labels",
        "Open a place › Label: try the sizes, then Print or save PDF (and All labels from Storage). Scan the printed label with the scanner: you should get Put cards away here, Open box and, with a pull list open, Pull from here."
    ),
    TesterNote(
        "storage-places",
        "Storage: where your physical cards are",
        "Collection › Storage › New place: make a shelf, a box inside it (try the colour sorting rule) and a binder. Open the box › Put cards away and scan a few cards: each says where to file it and what happened; try Undo last. Then open one of those cards: Where it is should list the box, any decks and No place yet. Try Move a copy, the Place filter in Advanced filters, and check the places show on your other device after sync."
    ),
    TesterNote(
        "advanced-filters",
        "Advanced filters for your collection",
        "Collection › All cards › Filters › Advanced filters. Try commander identity at most two colours, mana value ≤ 3, legal in Commander, a set and Foil, then Show N cards: each filter is a chip you can remove. Check the Scryfall line (Copy, Open on Scryfall), and Save as… then apply the saved filter again."
    ),
    TesterNote(
        "spread-thin",
        "Spread thin: cards your decks fight over",
        "Collection › All cards › Spread thin. Each card your decks use more copies of than you own, with how many you're short, what the rest would cost and the decks using it (tap one to open it). Try Copy buy list and paste it somewhere."
    ),
    TesterNote(
        "playgroup",
        "Playgroup: your record across every deck",
        "Play › Playgroup. Check your overall record, the record against each person and commander, streaks, the deck ranking and your nemesis against what you remember of your games."
    ),
    TesterNote(
        "pod-games",
        "Pod games: one log for your playgroup",
        "Play › Playgroup, then pick one of your pods at the top (make one under Friends if you have none). Record a game with Record a game, with Add to my deck's record too on, then check it shows in the pod's table and in Just me. Delete it again from the latest games."
    ),
    TesterNote(
        "game-night",
        "Game night: fair pods by power",
        "Play › Game night. Add yourself, a friend or two and a guest, pick decks (try Suggest), then make pods. Reshuffle, move someone to another pod, then Start a pod: the life counter should open with those players and commanders. Finish the game and check the result shows on Game night and on your deck."
    ),
    TesterNote(
        "events",
        "Events: run a small tournament",
        "Play › Events › New event. Add 5 or more names, start it, enter results for round 1 and pair round 2: nobody should meet the same opponent twice, and the bye moves on. Start the round clock, leave the screen and come back. Check Standings, then copy them."
    ),
    TesterNote(
        "limited",
        "Draft and sealed",
        "New deck › Limited. Add cards to the pool (search, or the scanner sending to the pool), look at the pool by colour and the strongest pairs, move cards into the deck, then Add basic lands from the deck menu. Try Add pool to a binder at the end."
    ),
    TesterNote(
        "widget",
        "Home-screen widget",
        "Long-press the home screen › Widgets › Manabind Tester. It shows collection value and this week's change, recent price alerts, and Life counter and Scan buttons. Open Home in the app once so it has a value; check each tap opens the right place."
    ),
)
