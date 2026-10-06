package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "renumbered",
        "Tester builds count from 1 again after each release",
        "This is 3.6.0 build 1 (the band at the top and Tester tools say so). Everything from builds 15–25 is now in the real app as 3.6.0. When build 2 comes out, check the tester app offers it by itself: Settings › App Updates › Check."
    ),
)
