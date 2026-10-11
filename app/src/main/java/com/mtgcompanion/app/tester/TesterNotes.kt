package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "owned-count",
        "Cards in Virtual and Prototype decks and cubes no longer count as owned",
        "1) Note Collection's All cards count (on the home tile and at the top of All cards) and the collection value on the All cards dashboard. " +
            "2) Make a Virtual deck and add a card you don't own anywhere else. Back on All cards: that card isn't listed, the count is the same and the value hasn't changed. Sets doesn't show it as owned either. " +
            "3) Do the same with a Prototype deck, and with a cube: their cards aren't on All cards and don't add to the value. " +
            "4) Make a Physical deck with a card you don't own: it does appear on All cards, the count goes up by one and the value by its price. " +
            "5) A Proxy deck's cards show on All cards marked proxy, but add nothing to the value. " +
            "6) Also check \"You own N\" on the scanner's Last scanned panel and in Sort a pile: copies in Virtual/Prototype decks and cubes aren't counted, real copies in Physical decks are."
    )
)
