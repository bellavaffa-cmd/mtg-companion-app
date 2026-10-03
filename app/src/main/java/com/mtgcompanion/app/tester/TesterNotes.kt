package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "trade-once",
        "A trade's cards move only once",
        "Accept a trade and tap \"Update my binders\", then try it again (or on a second phone that still shows the button). The cards move once; the second time it says your binders were already updated."
    ),
    TesterNote(
        "sideboard-considering",
        "Imported sideboards go to Considering",
        "In a deck, use Import decklist with a list that has a \"Sideboard\" or \"Maybeboard\" section, \"SB:\" lines, or an Arena export. Those cards land in Considering, not the deck, and the summary says how many."
    ),
    TesterNote(
        "binder-commander",
        "Cards from binders can be commanders",
        "Add a legendary creature to a deck from All cards (To deck) or from a binder's Move or Copy. Open it in the deck: \"Set as commander\" is offered."
    ),
    TesterNote(
        "brawl-commander",
        "Brawl decks can pick their commander",
        "In a Brawl deck, open a legendary creature's or legendary planeswalker's menu: \"Set as commander\" is there, and the star on the row works too."
    ),
    TesterNote(
        "search-order",
        "Search results match the last search",
        "Search for something, then quickly search for something else, or scroll for more and then search again. Only cards from the last search show."
    ),
    TesterNote(
        "random-offline",
        "Random card says when it can't",
        "Turn on airplane mode and tap the random card button in Search. A message says it couldn't get one."
    ),
    TesterNote(
        "card-retry",
        "Try again on a card page",
        "Open a card page while offline so it fails, go back online and tap \"Try again\". The card loads."
    ),
    TesterNote(
        "game-delete",
        "Deleting a logged game asks first",
        "In a deck's match record, tap the ✕ beside a game. It asks \"Delete this game?\" before deleting."
    ),
    TesterNote(
        "cleanup",
        "Unused code removed",
        "Nothing new to see. Just use the app as usual and report anything that looks or works differently from before."
    ),
)
