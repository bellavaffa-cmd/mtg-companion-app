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
    )
)
