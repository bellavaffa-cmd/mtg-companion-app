package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "add-check",
        "Cards are checked before going into a deck",
        "From your note. Add a card that's banned, not legal in the deck's format, outside the commander's colours, or over the copy limit: the app now asks first and says why. \"Add anyway\" adds it; Cancel doesn't. Adding several at once lists the ones that fail."
    ),
)
