package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "deck-search-thin",
        "The deck's search box is one line tall again",
        "From your note. Open a deck: the search box reads \"Find or add a card\" on a single line, the same height as before. It still searches by name or tag and offers new cards to add."
    ),
)
