package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "add-to",
        "One \"Add to…\" everywhere",
        "Long-press a card in Search, then \"Add to…\": binder or deck first, then the list. A bar says \"Added … to …\" with Undo. Try it from a card page, a binder, All cards, a tag binder and the scanner: it should look and work the same each time."
    ),
    TesterNote(
        "add-to-copies",
        "Copies, foil and printing when adding",
        "From a card page, Add to binder: set 3 copies, turn on Foil, tap \"Printing\" to pick another art. The binder gets 3 foils of that printing."
    ),
    TesterNote(
        "add-to-new",
        "Make a binder or deck while adding",
        "Long-press a deck card, \"Move to…\", \"New binder…\", name it. Undo puts the card back and removes the new binder."
    ),
    TesterNote(
        "add-to-considering",
        "Suggestions go to Considering first",
        "In a deck's Suggestions, tap \"Add…\": Considering is already picked; switch to \"Into the deck\" to add it straight in."
    ),
    TesterNote(
        "wording",
        "Same words everywhere",
        "Menus say Import list, Export list, Move to…, Copy to…, Delete deck, Remove from deck. Report any old wording."
    ),
    TesterNote(
        "scratch",
        "Start a deck from scratch",
        "Decks, \"Start from scratch\", Commander: commanders load most-played first. Try the colour chips, search and sorts. Tap one, \"Build with …\", name it: the deck opens on Suggestions."
    ),
    TesterNote(
        "scratch-pair",
        "Two commanders",
        "Pick a \"Choose a Background\" or Partner commander: an \"Add a Background\"/\"Add a partner\" step appears with Skip. In existing decks, \"Set as Background\" / \"Set as partner commander\" works and Legality accepts the pair."
    ),
    TesterNote(
        "brawl",
        "Brawl commanders",
        "Start a Brawl deck: legendary planeswalkers are offered as commanders too."
    ),
    TesterNote(
        "sideboard",
        "Sideboard",
        "In a Modern or Standard deck, long-press a card, \"Move to sideboard\": a \"Sideboard (N)\" group appears. Import a list with a Sideboard section; Legality checks 15 max and copy limits across both."
    ),
    TesterNote(
        "playtest",
        "Playtest",
        "Deck ⋮, Playtest: choose play or draw, Mulligan (pick cards to bottom), Keep, play cards from hand, tap permanents, Next turn, make a token, Reset."
    ),
    TesterNote(
        "owned-only",
        "Only cards I own",
        "Suggestions tab: turn on \"Only cards I own\". EDHREC picks and budget swaps shrink to cards in your binders."
    ),
    TesterNote(
        "compare",
        "Compare decks",
        "Deck ⋮, Compare with…: pick another deck or an earlier version. Check \"Only in…\" and \"In both\" (\"4 → 2\" for different counts)."
    ),
    TesterNote(
        "export",
        "Arena and MTGO export",
        "Deck ⋮, Export list: try Arena and MTGO, and paste into those programs if you have them."
    ),
    TesterNote(
        "sets",
        "Set completion",
        "Collection, Sets: each set's bar and %. Open a set to see owned and missing cards and \"Add missing to Wishlist\"."
    ),
    TesterNote(
        "condition",
        "Condition and language",
        "In a binder, open a card and set its condition and language: rows show badges like \"LP\" and \"JA\". Export list has a CSV option that keeps them."
    ),
    TesterNote(
        "price-history",
        "Price history",
        "A card page shows \"Price history (on this phone)\". It starts empty and fills in day by day."
    ),
    TesterNote(
        "price-rise",
        "Alert when a price rises",
        "In an owned binder, open a card and set \"Tell me when it rises to \$X\". Home lists alerts that went off."
    ),
    TesterNote(
        "breakdown",
        "Collection breakdown",
        "Collection, All cards, Breakdown: value by set, colour, rarity and type, and your 10 most valuable cards."
    ),
    TesterNote(
        "scan-foil",
        "Foil when scanning",
        "After scanning, each row that can be foil has a Foil switch; foil scans are saved as foil."
    ),
    TesterNote(
        "play-tab",
        "Play tab",
        "New Play tab in the bottom bar: start a life counter, join a table, return to your seat, see recent games."
    ),
    TesterNote(
        "deck-tokens",
        "Your deck's tokens at the table",
        "Mark your seat \"This is me\" with a deck (or pick a deck on a remote): your deck's tokens show as chips on your tile. Tap to add, − to remove."
    ),
    TesterNote(
        "clock",
        "Game clock and turn timer",
        "The menu shows a game clock with pause. Settings, Turn timer: the active tile counts down and the phone buzzes at 0."
    ),
    TesterNote(
        "triggers",
        "Trigger reminders",
        "With a deck on your seat, a chip lists your \"at the beginning of your upkeep / draw / combat / end step\" cards when your turn starts. Tap to dismiss."
    ),
    TesterNote(
        "menu",
        "Grouped game menu",
        "The centre menu is grouped into Game, Table and Tools."
    ),
    TesterNote(
        "remote",
        "Remote extras",
        "On a phone joined to a table: hold on and \"OK, go on\", point at a player, your tokens, buzz on your turn, look up a card with rulings, concede, the plane and planar die, the clock and timer."
    ),
    TesterNote(
        "legality-badge",
        "Legality in Stats",
        "Decks have no Legality tab now: the top of Stats shows \"Legal\" or \"N problems\". Tap it for the list and fixes."
    ),
    TesterNote(
        "grouped-actions",
        "Grouped card actions",
        "Long-press a deck card: actions are grouped (Commander, In this deck, Elsewhere) with Remove last in red."
    ),
    TesterNote(
        "menu-sheets",
        "Deck and binder menus",
        "Tap ⋮ on a deck or binder: a sheet opens with an icon and short description for each action."
    ),
    TesterNote(
        "stats-fold",
        "Tidier Stats",
        "Stats starts with a summary. Tap a panel's title to fold it; it stays folded next time."
    ),
    TesterNote(
        "settings-list",
        "Settings as a list",
        "Settings is now a list of sections; tap one to open it."
    ),
    TesterNote(
        "look",
        "Colours and back buttons",
        "Red delete buttons and back arrows look the same everywhere; check light mode and each accent colour."
    ),
)
