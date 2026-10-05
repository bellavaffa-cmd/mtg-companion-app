package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
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
