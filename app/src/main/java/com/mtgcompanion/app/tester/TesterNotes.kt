package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "scanner-modes",
        "One scanner, four jobs: Add cards · Sort a pile · Put away · Check",
        "1) Tap the bottom bar's Scan. Under the top row there's a switch: Add cards · Sort a pile · Put away · Check (TalkBack reads them as tabs, \"2 of 4\"). The first time, a tip points at Sort a pile: \"New: sort a pile while you scan\" — tap × (or Sort a pile) and it never comes back. Settings › Scanner › Show tips again brings it back. " +
            "2) Add cards works as before. Scan two cards, then tap Sort a pile: it asks \"Keep the 2 scanned cards in the list?\" — Keep them, or Clear the list. Either way the camera keeps running. Going to Put away or Check never asks; Add cards then shows \"Add cards · 2\" and the list is still there when you tap it. " +
            "3) Sort a pile, never sorted before: \"Choose a recipe\" opens the recipe picker over the camera (Your recipes, Start from, Make your own, and Keep, spares, decks, bulk). Pick one: its Lay out step, Start scanning, and you're back on the camera, sorting. After that, Sort a pile says \"By card type · last used\" with Start, Change recipe and Lay out piles first. A sort with cards in it just carries on. Done still opens what went where. " +
            "4) Put away: Pick a place, or — if a box has a rule — Let the app choose (each card goes to the box whose rule fits; one no rule fits waits in Add cards). Check: Pick a place, then the check as before (Finish check opens the results). Coming back to Put away or Check on the same visit goes back to the same place. " +
            "5) Leave the scanner on Sort a pile and open Scan from the bottom bar again: it opens on Sort a pile (the last mode you picked; only taps on the switch are remembered, on this phone only). " +
            "6) From a place: Put cards away opens on Put away for that place, Check on Check. Collection › Storage › ⋮ › Sort a new pile opens the scanner on Sort a pile. A deck's pull list or put-back list \"Scan to tick\" has no switch — it's its own job. " +
            "7) Back with cards in the list asks only on Add cards; from the other modes it just leaves, and the list is still there next time."
    )
)
