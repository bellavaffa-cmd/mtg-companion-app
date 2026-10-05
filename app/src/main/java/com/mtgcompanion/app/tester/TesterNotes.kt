package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
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
