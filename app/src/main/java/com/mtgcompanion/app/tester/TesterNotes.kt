package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "tag-fixes",
        "Card tags reach every card — not only the ones a screen has shown",
        "1) Update and open the app with the network on; leave it a minute (a big collection, a few). Settings › Data and speed › Card tags says \"N of M tagged\" and N climbs to nearly M without opening any deck or binder (it says \"· tagging…\" while it works). " +
            "2) Collection › Cards › filter: the new Tags row has \"Untagged\" — tap it: only cards that got no tag at all are left (expect few: tokens with no text, odd cards). Lands now show \"Land\" (and dual/tri lands, fetches and any-colour rocks \"Fixing\"), creatures with no rules text \"Vanilla\". " +
            "3) Your own tags (the card zoom's tag row) show as chips in the same Tags row; tap one and only cards carrying it are left. Typing your tag in the search finds those cards too. The chips show in the bar above the list and × removes them; a saved filter keeps them. " +
            "4) A proxy or a card you renamed: it's tagged by its printing now, so it gets the real card's tags (search its role, e.g. \"ramp\"). " +
            "5) Turn on aeroplane mode, add a few new cards, then turn the network back on: within a few minutes they're tagged without opening anything. " +
            "6) Tap \"Retag now\": tags stay on screen while it re-checks every card, and the count comes back to the same or higher. " +
            "7) Older cards in binders/decks now show their keyword chips (Flying, Lifelink…) in the card zoom after the first pass; it syncs once, not card by card. " +
            "8) Deck view: the role counts (Ramp, Card draw, Removal) are at least as high as before; cards Tagger doesn't know yet (a brand-new set) now get Ramp/Card draw/Removal from their text."
    )
)
