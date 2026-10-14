package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "binder-flows",
        "Cards going into and out of decks keep their binder",
        "Before you start, note the Collection header's card count and the dashboard's value — none of these steps should change them. " +
            "1) Open a binder (say Rares) with Sol Ring in it, long-press Sol Ring › Move › a Physical deck (say Talrand): \"Moved Sol Ring to Talrand\" and \"Still listed in Rares: In Talrand deck\". Rares lists it under IN DECKS with the badge \"In Talrand deck\"; Talrand's card says \"Binder: Rares ×1\". Picking cards in Rares › Copy to… › a Physical deck does the same (\"Added …\"), rather than counting the card twice; into a Virtual or Prototype deck the card is only listed and your copy stays in Rares. " +
            "2) Give Sol Ring a box spot first (Storage) and do step 1 again; then on the deck lower Sol Ring's count (or remove it): Sol Ring is back in Rares as a loose card, at its old box spot (Where it is shows it). A copy that came from search or No binder goes back to No binder, first. " +
            "3) Delete a deck that has cards from your binders, choosing \"Delete deck, keep cards\": the dialog says they go back to the binders they're listed in. Each card is back in its own binder (and spot); the rest in No binder. Making a Physical deck Virtual or Prototype (Keep cards) does the same. " +
            "4) Delete a binder whose cards are in a deck: the dialog adds \"N cards in decks stay in their decks.\" The deck keeps them; they now show in No binder. " +
            "5) On a Proxy deck with a proxy of a card you own in a binder: Stats › Proxies › swap it in. The binder lists it in the deck (\"In … deck\"); lower the count and it goes back to that binder. The pull list's \"Move pulled into deck box\" does the same for cards pulled from a binder. A foil copy goes back foil. " +
            "6) Undo on the confirmation puts everything back. Through all of this the header count and value never change; sync with the web app or another phone on this build and nothing is doubled or lost."
    )
)
