package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "collection-type-text",
        "Filter All cards by type and text",
        "From your note. Collection > All cards > filter icon. Type takes words from the type line (\"legendary creature\", \"angel\"); Text takes a phrase from the card's text (\"draw a card\"). Both work with colors, rarity and the search field."
    ),
    TesterNote(
        "text-label",
        "\"Rules text\" is now just \"Text\"",
        "Search's filter and the Collection filter both say Text."
    ),
    TesterNote(
        "deck-search-adds",
        "Add new cards from a deck's search box",
        "Open a deck and type at least three letters of a card that isn't in it. Under the deck's own matches there's \"Add to this deck\" with cards from all of Magic; tap + to add one."
    ),
)
