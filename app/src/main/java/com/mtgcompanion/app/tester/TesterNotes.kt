package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "built-in-cloud",
        "This build was made by GitHub, not the PC",
        "Nothing in the app changed. If you're reading this, the update installed over build 8, which means GitHub built and signed it with the right key. Open a deck and sign-in still works as before."
    ),
)
