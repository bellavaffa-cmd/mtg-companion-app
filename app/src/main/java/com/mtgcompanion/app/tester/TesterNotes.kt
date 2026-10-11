package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "collection-layout",
        "Collection: four tabs — Cards, Binders, Storage, Wishlist — and every card says where it is",
        "1) Open Collection. No tile page any more: one header (value, sync, ⋮ menu), one search bar, and four tabs Cards · Binders · Storage · Wishlist right away. Swipe or tap between them; TalkBack reads each as a tab. " +
            "2) Leave on Storage, go to Home and come back: it opens on Storage again. Back from any tab leaves the Collection (no tile page in between). " +
            "3) Tap the value: Value history. If Upkeep has something, an amber \"Needs attention · N\" sits under the search; tap it for Upkeep's list. With nothing to do it isn't there. " +
            "4) Cards: type in the search — only Cards narrows; at the bottom, \"Search everywhere for …\" opens Find anything with it typed. The filter button (and view button) are in the search bar; Advanced filters work as before. " +
            "5) Cards rows (list view) show where the copies are: \"Blue box › Instants ×2\", \"Talrand deck ×1\", \"Lent to Sam ×1\", two at most then \"+N\" (opens the card). A place chip opens the place at that section (a binder at its page), a deck chip the deck, a loan chip Loans. Copies with no place show an amber \"No place yet · Put away\": tap it, pick a place, and the chip turns into that place. " +
            "6) Group by: None / Set / Binder / Place. Set shows each set with \"41/92\" (owned/size) and opens the set's cards (this replaces the Sets tab). Place puts No place yet last. " +
            "7) Quick filters Lent out, For sale, No place yet, Graded (each with its count, one at a time); \"Manage…\" opens Loans, To sell, Upkeep or Graded cards. " +
            "8) Long-press a card: selecting still works (Add to…, Export, Remove), and a swipe doesn't leave Cards while picking. Tap a card: its zoom lists binders with where each binder's copies are kept (\"Red box ×2\"). " +
            "9) Binders: Unsorted, your binders (each saying \"kept in N places\"), New binder, By tag binders, and Goals (every open goal with its bar, New goal, See all). The Wishlist isn't here any more. Open a binder: its rows have the same chips. " +
            "10) Storage: \"Put cards away\" on top and ⋮ beside it (Upkeep, Set up storage, Value by place, Space, To sell, Sort a new pile, Data and speed, Sharing storage at home). Then your places (each with \"in N binders\"), Deck boxes, Sealed, Graded (new list of every slab: grade, value, place — tap opens the card), Lent out, Gear, and how much has a place. Search narrows the places. " +
            "11) Wishlist tab: the Wishlist as before — price targets, Under your price, spoiler cards, Buy these cards, said-no list — searched by the header's search. " +
            "12) New sets moved to Search (top of the page). Import list, New binder, Share my collection and (with an account) Shared with me are in the header's ⋮ menu. Scan stays in the bottom bar. " +
            "13) Links still land right: Friends › See what friends share opens Shared with me; Settings › What's new tour runs a new 5-step tour over the tabs."
    ),
    TesterNote(
        "owned-count",
        "Cards in Virtual and Prototype decks and cubes no longer count as owned",
        "1) Note the Cards tab's count and the collection value on its dashboard. " +
            "2) Make a Virtual deck and add a card you don't own anywhere else. Back on Cards: that card isn't listed, the count is the same and the value hasn't changed. Group by Set doesn't count it as owned either. " +
            "3) Do the same with a Prototype deck, and with a cube: their cards aren't on Cards and don't add to the value. " +
            "4) Make a Physical deck with a card you don't own: it does appear on Cards, the count goes up by one and the value by its price. " +
            "5) A Proxy deck's cards show on Cards marked proxy, but add nothing to the value. " +
            "6) Also check \"You own N\" on the scanner's Last scanned panel and in Sort a pile: copies in Virtual/Prototype decks and cubes aren't counted, real copies in Physical decks are. " +
            "7) Search for the card that's only in your Virtual deck (or on your Wishlist) and enlarge it: there's no \"In 1 deck\" / \"In 1 binder\" line for it. A card in a Physical deck still says \"In 1 deck\"."
    )
)
