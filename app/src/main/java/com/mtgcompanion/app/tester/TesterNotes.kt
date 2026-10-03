package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "add-to-search",
        "One \"Add to…\" everywhere",
        "In Search, long-press a card and pick \"Add to…\", then A deck and a deck. A bar at the bottom says \"Added … to …\". Tap Undo and the card leaves the deck. Try it from a card's page, a binder, All cards, a tag binder and the scanner too: it should look and work the same each time."
    ),
    TesterNote(
        "add-to-copies",
        "Copies and foil when adding",
        "On a card's page, tap \"Add to binder\", turn on Foil, set 3 copies and pick a binder. The binder should have 3 foil copies."
    ),
    TesterNote(
        "add-to-new",
        "Make a binder or deck while adding",
        "In a deck, long-press a card, \"Move to…\", then \"New binder…\". Name it and confirm. Undo takes the card back and removes the new binder."
    ),
    TesterNote(
        "add-to-considering",
        "Suggestions go to Considering first",
        "In a deck's Suggestions, tap \"Add…\" on a card. Considering is already picked; switch to \"Into the deck\" to put it straight in the deck."
    ),
    TesterNote(
        "add-to-scan",
        "Scans use the same sheet",
        "Scan two cards, then \"Add all to…\" and pick a binder. Undo puts the scans back on the list."
    ),
    TesterNote(
        "wording",
        "Same words everywhere",
        "Menus now say Import list, Export list, Move to…, Copy to…, Delete deck, Remove from deck. Tell us if any old wording is left."
    ),
    TesterNote(
        "scratch-commander",
        "Start a deck from scratch",
        "Decks, then \"Start from scratch\", then Commander. Commanders appear most-played first, with \"Loading more…\" until all are in. Tap one, then \"Build with …\". Name the deck and it opens on Suggestions with the commander set."
    ),
    TesterNote(
        "scratch-colours",
        "Find a commander by colour",
        "In the commander list, tap U and B: only commanders within blue and black show, plus colourless ones. Try searching by name or rules text, and the Name and Newest sorts."
    ),
    TesterNote(
        "scratch-pair",
        "Two commanders",
        "Pick a commander with \"Choose a Background\" (for example Wilson, Refined Grizzly): an \"Add a Background\" step appears with Skip. Try a Partner commander too. The deck is named \"A & B\"."
    ),
    TesterNote(
        "pair-existing",
        "Backgrounds and partners in existing decks",
        "In a deck whose commander has Partner or Choose a Background, open another such card's menu: \"Set as partner commander\" or \"Set as Background\" is offered, and Legality accepts the pair."
    ),
    TesterNote(
        "scratch-other",
        "Other formats",
        "Start from scratch with Modern: it goes straight to the name (\"New Modern deck\") and opens an empty deck on Cards."
    ),
)
