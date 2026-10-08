package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "deck-upgrade",
        "Decks: upgrade with my cards",
        "Open a Commander deck you've built (with a commander) whose binders/boxes/Unsorted hold some cards that aren't in it. 1) Open the Suggestions tab: \"Upgrade with my cards\" is the first section. After \"Looking through your collection…\" it lists swaps — the deck's card (orange) → a card you own (gold) — and above them a line like \"5 upgrades from your cards · would save $12.40 · pull from 3 places\" (would save = what buying the new cards would cost). 2) Check a row: both cards do the same job (ramp for ramp, removal for removal…); the reason reads like \"Both are ramp; Arcane Signet is in 61% of Krenko decks, Wayfarer's Bauble in 22%.\"; under it is where the new card is (\"Red box › Artifacts\", \"Trade binder p3 s5\", \"Unsorted\", or \"in Atraxa deck\" when another deck has your only copy) and the price difference. The commander, lands, combo pieces and cards you tagged \"keep\" are never offered as cuts; cards outside the commander's colours or not legal in the format are never offered as adds; a card you marked as a cut is offered first. 3) Tap Swap now: a message says \"X in for Y — on the pull list (where)\"; the cut card is on Considering, the new card is in the deck, and the deck's menu › Pull list shows it with its place. Tap Consider on another row: the new card goes on Considering, the deck is unchanged. Tap Not this one: the row goes, and stays gone after leaving and reopening the deck; \"Bring back N dismissed\" undoes it. 4) Untick a row and tap Apply all checked (n): only the ticked swaps are made. 5) A deck at bracket 2–3 with a Game Changer (or combo piece) you own: the footer shows \"Show N that would raise the bracket\"; tap it — those rows have an orange outline and \"Would move the deck to bracket 4\" (or 3), and are unticked. 6) Deck menu (⋮) › Upgrade with my cards: jumps to the Suggestions tab. 7) Turn on airplane mode and reopen the deck's Suggestions: a note says EDHREC can't be reached and swaps are matched by role and EDHREC rank (\"Consider ranks #900 on EDHREC, Divination #9,000\"). A Modern/Standard deck works the same way with no bracket and no commander percentages. Compare with manabind.com: the same deck and collection should give the same swaps."
    )
)
