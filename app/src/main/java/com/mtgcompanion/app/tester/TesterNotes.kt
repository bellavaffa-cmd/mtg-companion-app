package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "move-fixes",
        "Moving and copying cards never makes or loses a copy",
        "Before you start, note the Collection header's card count and the dashboard's value. Through every step below they stay the same — they only change when you add cards (search, scan, Add to a binder) or remove them. " +
            "1) Sideboard: in a Physical deck with a sideboard (say Modern), a card that's listed in a binder (\"Binder: Rares ×1\") › Move to sideboard: the confirmation says your copy is back in its binder, and Rares has it again as a loose card. The sideboard only lists a card, like Considering. Move it back to the main deck: a loose copy in No binder goes in; with none, the deck counts it as a proxy to pull (Pull list shows it, and fetches it from Rares). Moving a card from another deck to this deck's sideboard does the same. " +
            "2) Deck to a Virtual or Prototype deck: in a Physical deck, a card's ⋮ › Move to… › a Virtual deck: the Virtual deck lists it, and the copy goes back to its binder (or No binder) — the confirmation says so. Copy to… a Virtual deck: nothing leaves the Physical deck. " +
            "3) Deck to deck: Move a card from one Physical deck to another: it leaves the first and arrives in the second with its binder (\"Binder: Rares ×1\" on the new deck, Rares lists it there). Copy a card to another Physical deck: it isn't counted twice — a loose copy comes out of No binder (\"1 taken from No binder\"), the rest is to pull (\"1 to pull\") and shows on that deck's pull list as needed. " +
            "4) Deck to a binder: Move puts the deck's real copies in the binder (a proxy is no card and doesn't go). Copy to… a binder lists the deck's copy there (\"It stays in Talrand; Rares lists it\") instead of making a second one. " +
            "5) Copies of decks: a deck's History › an earlier version › Copy as new deck, and a friend's shared deck › Copy to my decks, now make a Virtual deck — make it Physical to build it from your binders (loose copies come out of No binder). " +
            "6) Undo on each confirmation puts everything back. Sync with the web app (Move to sideboard / back there does the same) and nothing is doubled or lost."
    )
)
