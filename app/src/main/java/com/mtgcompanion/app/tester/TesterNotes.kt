package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "singleton-plus",
        "Singleton is checked on + too",
        "From your note. In a Commander or Brawl deck, tap + on a card that isn't a basic land: the app asks first (\"Singleton: only 1 copy allowed\"). Cancel keeps one copy; Add anyway adds the second. Adding from search or a binder says the same."
    ),
)
