package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "sideboard-limit",
        "The sideboard's 15 cards are checked too",
        "In a Modern or Standard deck with a sideboard of 15, add one more card to the sideboard (Add to…, its +, Import list) or use Move to sideboard: the app asks first (\"Sideboard is full (15 max)\"). Cancel leaves it as it was; Add anyway adds it."
    ),
)
