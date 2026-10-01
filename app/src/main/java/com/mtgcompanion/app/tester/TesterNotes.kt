package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "zoom-same-height",
        "Enlarged cards are all the same size",
        "From your note. In Search results, enlarge a card and swipe through several. The card should stay the same size whether or not it has other printings; one without says \"No other printings\" where the strip would be."
    ),
    TesterNote(
        "search-filter-wording",
        "Search filters are worded like the web app",
        "Open Search. The fields now read Type, Rules text, Colors (at least), Fits commander colors, Rarity, Finish, Price, Power, Toughness, Sets, Artist — the same names and order as manabind.com."
    ),
    TesterNote(
        "deck-legal-chip",
        "Legal / Not legal beside the bracket",
        "Open a deck. Next to \"Bracket N\" there's a Legal chip, or a red Not legal one; the Legality tab says why."
    ),
)
