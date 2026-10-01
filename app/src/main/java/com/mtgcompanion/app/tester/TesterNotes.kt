package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "zoom-plus-visible",
        "The + is back on the enlarged card",
        "From your report: the + was pushed off the screen. Tap a card in a deck or binder to enlarge it. Value, total and the − qty + stepper are on one line, the action icons on the line below."
    ),
    TesterNote(
        "zoom-tags-button",
        "Your tags are one line on the enlarged card",
        "Enlarge a card you own. \"Add your own tags\" is now a single row; tapping it opens the tag editor in a pop-up."
    ),
    TesterNote(
        "zoom-sources-collapsed",
        "\"In your decks and binders\" starts closed",
        "Enlarge a card from Collection > All cards. It shows \"In N decks and M binders\" with Show; tap it to see the list."
    ),
    TesterNote(
        "zoom-stays-open",
        "Adding from an enlarged card keeps it open",
        "In Search results, enlarge a card and add it to a binder or deck. The card should stay enlarged so you can swipe to the next one."
    ),
    TesterNote(
        "deck-grid-toggle",
        "List / grid button inside a deck",
        "Open a deck's Cards tab. The button beside the search field switches between list and grid."
    ),
    TesterNote(
        "deck-grid-stepper",
        "Change quantity in a deck's grid view",
        "In a deck's grid view each card has − qty + under it. Taking the last copy away asks before removing the card."
    ),
    TesterNote(
        "grid-three",
        "Grids start at 3 cards across",
        "Only applies if you never changed Settings > Card Display > columns: grids show 3 across instead of 4."
    ),
    TesterNote(
        "collection-filter",
        "Filter All cards by color, type and rarity",
        "Collection > All cards: tap the filter icon in the search field. Pick colors (a card must have every color picked), types and rarities. It works together with the search text and Spares."
    ),
)
