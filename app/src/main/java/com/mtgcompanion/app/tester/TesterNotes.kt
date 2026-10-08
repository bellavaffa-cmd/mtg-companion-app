package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "sort-recipes",
        "Sort a pile: recipes",
        "Collection › Sort a pile now asks how the pile should split. Pick Commander by colour: Lay out 11 piles shows numbered tiles with colour bands (1–3 gold: Decks need, Friends want, Binder gaps). Back, then Make your own recipe: name it, leave Cards my decks need / Cards friends want / New for a binder ticked, change level 1 Value to 2 (\"$2 and up apart, rest continue\"), add Colour, tick Foils — the count at the bottom says 12 piles. Add a third level of Set with five sets and Card type: a warning says only 24 fit and the rest share pile 24, Everything else. Save and lay out: it's under Your recipes as Last used, and on the web app after a sync. Tap a tile to choose where that pile is filed. The old sorter is still there as Keep, spares, decks, bulk."
    ),
    TesterNote(
        "hands-free-scanning",
        "Sort a pile: hands-free scanning",
        "On Lay out, leave Say the pile out loud and Capture without tapping on, and Start scanning. Hold a card still under the camera: it's taken without a tap, the pile shows big in its colour (\"7 BLUE\", the card, rarity · price · set) and the phone says \"Seven, blue\". Keep the same card there: it is not counted again. Take it away and show the next (or the same card again after taking it away): counted once each. Scan sounds still play; a smart pile buzzes twice. Undo takes the last off; Wrong card? lets you pick the printing or rescan it. Foil / Not English / Played under the card move it to that pile. Turn Capture without tapping off on Lay out: only Scan now takes a card. TalkBack reads each card's pile. Leave the app or restart the phone mid-sort: Sort a pile offers to keep going with the same count."
    ),
    TesterNote(
        "smart-piles",
        "Sort a pile: smart piles",
        "Have a deck missing a card (or considering it), a friend whose wishlist matches a card you own, a binder kept in order by set, and four of a card already. Scan the missing card: pile 1, \"KRENKO NEEDS IT\", \"missing from …\", Also wanted lists the friend and how many you own; Send to pile 2 instead moves it to the friend's pile; Put in deck now puts it with the deck straight away. A card of a set your set-sorted binder collects but doesn't have: \"NEW FOR … BINDER · p3 s5\". A fifth copy (with More than a playset ticked): \"5th COPY · TRADE\"."
    ),
    TesterNote(
        "sort-summary",
        "Sort a pile: done, what went where",
        "Tap Done while sorting: Sorted N cards with each pile's count and value (Decks need · \"Krenko 2\", Friends want · \"Priya 1\", binder names), and colour piles as one Bulk by colour line when there are more than six. Cards it couldn't read are listed under Check them. Check a pile: pick one, scan it again — cards that don't belong are flagged with the pile they should be in. Offer pile 2 to … opens a trade with that friend, the cards already on your side. Print pile signs on Lay out prints the numbers big (A4 or Letter). File everything adds the cards to your collection: bulk into the boxes whose rules fit (or the place you chose for a pile), binder gaps into their binder with Fit in order offered, deck needs for the decks' pull lists. Keep going goes back to scanning."
    )
)
