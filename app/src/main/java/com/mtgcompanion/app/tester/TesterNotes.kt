package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "storage-filters",
        "Storage: search and filter the cards in a place, or in all your places, and walk the list",
        "1) Collection › Storage › open a box. Under Put cards away / Check / Label there's a search bar with the filter button. Type part of a card name (or a tag, e.g. ramp): the box keeps its sections, only the ones with a match show, already open. A line says \"14 of 312 cards · $118\" — copies matching of the copies here, and what the matching ones are worth (foils at the foil price). " +
            "2) Tap the filter button: the same panel as Cards (Type, Text, Colors, Rarity), Advanced filters, and Spares only. Advanced filters and saved filters are the Cards tab's — save one on Cards and pick it here. Finish, condition and language are judged copy by copy: \"Foil\" shows only the foil copies in this box. " +
            "3) Shut the panel: each filter is a chip — tap one to take it off — and one \"Clear\" chip clears the search and every filter. " +
            "4) Open a binder and filter it. Pages view: matching pockets get a gold border, the other cards are dimmed, and \"Matches on pages 2, 5\" turns to a page with a tap. List view shows only the matches, with Page N, slot M. " +
            "5) A shelf (a place with places inside): its matches show grouped by place › section, each with a place chip that opens the place there. " +
            "6) Leave the place and come back: its search and filters are still on. Another place starts clean. Close the app (swipe it away) and reopen: they're gone. " +
            "7) Storage tab: the header's search bar now has a filter button too. Typing still narrows the places by name, and adds \"Cards in storage matching …\" with every matching copy in every place, grouped by place › section, each row with its place chip. Filters work with or without typing. " +
            "8) \"Walk this list\" (on a filtered place or the Storage results) opens the matches as a pull list in walking order — place by place, section by section, page and pocket — with tick boxes. \"Scan to tick\" opens the scanner: each card scanned ticks its row (\"Ticking off: walk list …\"). The ticks stay while the app is open; nothing moves in your collection."
    ),
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
    ),
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
