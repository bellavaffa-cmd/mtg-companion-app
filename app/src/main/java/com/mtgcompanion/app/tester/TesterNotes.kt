package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "picker-scrolls",
        "The deck list scrolls when adding a card",
        "From your report: with 10 decks only 7 showed and the list wouldn't scroll. Search for a card, open its menu, choose Add to binder/deck, then A deck. All your decks should be there, and the list should scroll."
    ),
    TesterNote(
        "binder-or-deck",
        "Binder or deck is asked first",
        "Still to confirm from the last build. Add from Search, and Move or Copy from inside a deck or a binder: it should ask \"A binder\" or \"A deck\", then list only those."
    ),
)
