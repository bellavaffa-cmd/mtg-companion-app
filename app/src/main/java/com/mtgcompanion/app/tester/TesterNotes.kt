package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "collection-goals",
        "Collection goals: targets with progress",
        "1) Collection home: under New sets there's a Goals card (\"Set a goal…\" while you have none). Tap New goal: four kinds — Complete a set, Playsets of a list, Foil a deck, Custom list. " +
            "2) Complete a set: type \"Dusk\", pick Duskmourn, tap Uncommon (the line says \"Duskmourn uncommons · N cards\"), Make it a goal. The goal opens: have/need (e.g. 41/92), percent, \"51 missing · about £…\", and the missing cards with their number and price. Tap All cards: owned ones show green n/1. " +
            "3) Tap Add missing to Wishlist: \"N cards put on your Wishlist\". Tap it again: \"Your Wishlist already has them all.\" A card already wanted ×2 isn't lowered. " +
            "4) Signed in: Offer a trade lists friends whose shared binders have the missing cards, with Ask (starts a trade). " +
            "5) Playsets of a list: name it Shock lands, add Steam Vents by name (Done on the keyboard), paste \"Sacred Foundry\" and \"Blood Crypt\" on two lines, Make it a goal: each needs 4 (change Copies of each to 3 for 3). Custom list: \"4 Lightning Bolt\" / \"2 Counterspell\", with − / + per card. " +
            "6) A deck's ⋮ menu › Make this a goal › Foil this deck: \"Foil <deck>\", basics left out, Count cards in decks on; foils the deck's pull list brought in count. Add a card to the deck: the goal grows with it. Tick Count cards in decks off/on on any goal: copies in your physical decks count or not. " +
            "7) A set's page (Collection › Sets › a set): Make this a goal, with the same rarity and foil choices. " +
            "8) Scan a card a goal is missing (Scan, plain list mode): under \"Added …\" a small \"Goal: Duskmourn uncommons 42/92\"; a card the goal already has shows nothing. Scanning the same missing card again only counts while copies are still needed. " +
            "9) Complete a goal (e.g. a Custom list of one card you own, or add the last card): within a second confetti falls, the phone buzzes and \"Goal complete!\" shows with See goals; the goal moves to Completed on the Goals screen. With the phone's Settings › Accessibility › Remove animations on: no confetti and no buzz, just the note. " +
            "10) Two devices signed in: a goal made on one shows on the other; rename it on one and delete another on the other — both come through; completing on one shows Completed on both (no second celebration). " +
            "11) Settings › Data and speed › Reset collection: Cards only keeps the goals; Collection and Everything clear them (the count line says \"N goals\"); Undo brings them back. " +
            "Not in this build: an Activity feed entry (the feed is made on the server) and goals in Sort a pile's smart piles."
    )
)
