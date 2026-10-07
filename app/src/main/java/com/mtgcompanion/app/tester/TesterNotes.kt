package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "reset-collection",
        "Reset collection (Settings › Data and speed)",
        "Try this on a test account or after Save a backup — it really removes things. Open Settings › Data and speed and scroll to the red Danger zone. Pick each of Cards only, Collection and Everything in turn: the Removes line should count what goes (\"1,402 copies in 8 binders · 23 places · 3 decks\"). Tap Save a backup first and save the file: the button turns into ✓ Backup saved. Reset stays greyed out until you type reset (any case). Choose Cards only and Reset: every binder and the Unsorted pile are empty, but the binders, storage places, Wishlist, decks, sealed, graded and gear are still there. A Collection reset snackbar with Undo stays about 10 seconds — tap Undo and everything is back exactly as it was. Reset again and wait it out: if you're signed in, open the web app or another phone, sync, and the cards are gone there too and don't come back after a few syncs. Collection also removes the binders, places, sealed, graded, gear, loans and copy photos, leaving an empty Unsorted pile and Wishlist; Everything removes the decks as well. Try once in airplane mode: the reset happens at once and reaches the account when you're back online. Leaving the app during the 10 seconds sends it straight away (no Undo after that)."
    )
)
