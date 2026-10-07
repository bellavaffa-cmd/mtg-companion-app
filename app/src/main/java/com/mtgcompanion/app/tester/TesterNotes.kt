package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "collection-home",
        "Collection home",
        "Tap Collection: it should open on a home, with the collection's value at the top right, \"Find a card, a place or a deck\", six tiles — All cards (\"1,402 · filters\"), Storage (\"8 places · 92% placed\"), Binders (\"5 · wishlist\"), Sets, Sealed and graded (\"4 items · \$1,240\") and Loans and selling (\"7 out · 3 to sell\") — a To do from Upkeep (\"118 copies have no place · Put away\", \"Krenko pull list: 30 of 60 · Carry on\") and Scan / Sort a pile / Import. Each tile should open its page (All cards, Binders, Storage, Sets; Sealed; Loans or To sell); Back, the arrow at the top or tapping Collection again should bring the home back. Open a place from Storage and come back: you should still be on Storage. Friends › See what friends share should still open Shared."
    ),
    TesterNote(
        "data-and-speed",
        "Data and speed",
        "Settings › Data and speed (or Collection › Storage › Data and speed): it should show your copies, \"Card data saved for offline\" (how many of your printings the Offline Search database has — download it under Settings › Offline Search if it says 0), \"Opening All cards\" (open the Collection's All cards first; under a second shows green) and when it last synced (\"2 min ago\", or Not signed in)."
    ),
    TesterNote(
        "backup",
        "Save a backup, and restore it",
        "Settings › Data and speed › Save a backup: pick where to keep the file (Downloads or Drive). Change something — rename a deck, move a card to another place, delete a binder — then Restore and pick the file. It should say when the backup was made and what's in it (decks, binders, copies in places, loans, sealed, graded, gear, deck history, photos). Merge: the renamed deck keeps its new name, the deleted binder comes back, nothing you've changed since is lost. Try Replace on another go: the deck goes back to its old name, settings too. Signed in, the restored binder should turn up on manabind.com too. A backup saved on manabind.com should restore here as well."
    ),
    TesterNote(
        "big-collections",
        "Big collections feel quick",
        "Behind the scenes, this round tests every screen with a 25,000-card collection and fixes anything slow, so big collections feel as quick as small ones. With a big collection (import a long list), open All cards, search it, use the filters, open Storage, Upkeep and Value by place, and add a card: none should pause or stutter."
    ),
    TesterNote(
        "find-anything",
        "Find anything",
        "Collection › Find a card, a place or a deck: type \"sol ri\". Your cards should show Sol Ring with its copies and every place they are — \"Red box › Colourless ×1\", \"Atraxa deck ×1\", \"Lent to Sam ×1\" (orange), \"Graded PSA 9 ×1\", For trade or To sell — then Places (\"Shelf, study · 3 places inside\"), Decks using it (\"Commander · proxy\" for a proxy copy) and Not yours: Search all cards for \"sol ri\", which should open Search with those results. Typing should feel instant even with a big collection. Tap a card, a place or a deck: it should open, and Back should return to your search."
    ),
    TesterNote(
        "whats-new-tour",
        "What's new tour",
        "After this update, open Collection: a short tour should light up the home, Storage (\"Know where every card is\", with Set it up when you have no places yet), Find anything, Sealed and graded and To do — \"New · 2 of 5\", Skip tour, Next. It should show once on this phone: close and reopen the app and it shouldn't come back. Settings › What's new tour should show it again. Set it up should open Getting started with storage; Try it should open Find anything."
    )
)
