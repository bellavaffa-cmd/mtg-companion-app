package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "household",
        "Sharing storage at home",
        "Needs a friend who lives with you, both with this build. Collection › Storage › Sharing storage at home › Start sharing, then Invite someone and pick them: they should get a notification and see the invitation on Friends › People. Once they accept, Share a place: the screen should say \"You and Alex keep cards on the same shelf\", each person's copies and value, and each place \"Yours 612 · Alex 0\". On their phone, your place should read \"Alex 342 · you can see, not change\" — tap it to see the cards, and Keep my cards here too (not on binders). Build a deck with a card only they have: its pull list should show Ask Alex, \"ask Alex · Red box\"; Borrow from Alex should put it under Loans › Borrowed. Stop sharing should hide each other's cards straight away. Before the server is updated, the screen should only say \"Household sharing isn't available yet\"."
    ),
    TesterNote(
        "storage-setup",
        "Getting started with storage",
        "On a phone with no storage places (or Collection › Storage › Set up storage): tap Get started. Step 1: set how many binders (tap \"9 per page\" to change the pockets), bulk boxes (tap \"by colour, then A–Z\" to change the sorting) and shelves; deck boxes should say Automatic. Step 2: rename a couple, then Make places. Step 3: Print labels should open a label (All labels from there), and Put away on a box should open the scanner; the bar should show the share of copies with a place. Check the places on your other device."
    ),
    TesterNote(
        "storage-upkeep",
        "Upkeep: what's worth doing this week",
        "Collection › Storage › Upkeep: it should say \"92% of copies have a place\" and \"N things worth doing this week\". Rows to look for: copies with no place (after an import it should say \"Most came from today's import\") → Put away; a place not checked in 90 days with value inside → Check; an overdue loan → Remind; a box 90% full or more → Split; a pull list half ticked → Carry on. Each button should open the right screen. Turn on Weekly reminder: a notification should come about a week later (only when there's something to do)."
    ),
    TesterNote(
        "import-places",
        "Import with locations",
        "Export a binder as CSV (it now has a Place column), or use a ManaBox, Dragon Shield or other CSV with a binder, box, folder or location column. Import it (Collection › Import list): under the cards, Import with locations should list each value with its count — your own places matched already, others \"Make a new place\", blanks \"No place yet\". Change one, import, and check the copies are in those places on the Storage tab."
    )
)
