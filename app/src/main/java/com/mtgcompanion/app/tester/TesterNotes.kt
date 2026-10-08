package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "cubes",
        "Cubes: build, balance, box, share and draft a cube",
        "1) Decks tab: a new grid button (left of Browse precons) opens Cubes. Tap + New cube: name it, pick 360 (or 540, 720, Custom — try 45), leave Singleton on, Make it. The cube opens on its Cards tab, \"0 / 360 cards · singleton\". It is not in the Decks list, not offered when adding a card to a deck, and not on Home's deck rail or the life counter's deck picker. " +
            "2) Cards tab › Add from collection: filter by colour chips (White … Lands), rarity, a type (\"creature\"), a set code, and Min/Max $. Tick some, Add N. Each row shows where it stands: \"Owned · Red box ×1\" (or \"No place (Unsorted) ×1\"). Add one again: in a singleton cube it's left out (\"… already in the cube left out\"). " +
            "3) Fill from collection: suggestions from your cards for the colours that are short, a colour at a time, rarest and dearest first, never one already in; untick some, Add. " +
            "4) Any card: search Scryfall (\"Black Lotus\"), tap +: the row says \"Not owned\" in orange. Tap Mark proxy: it says \"Proxy\" (Not a proxy undoes it). The × takes a card out. " +
            "5) Balance tab: White … Lands with count / target bars (360: 51 white, 50 each other colour, 40 multicolour, 29 colourless, 40 lands), warnings like \"Green is 12 short\" and \"40 cards short of 360\", the curve (1 … 6+) with its average, types (creatures against a target) and roles — removal, fixing, card draw, counterspells — against targets. ⋮ › Size and singleton: change to 540: the targets and warnings change. " +
            "6) Box tab: Make the cube box — a new storage place \"<cube> box\" (Collection › Storage shows it). The pull list lists each owned card not in the box yet by where it is now (place by place, then No place yet, In another deck, Not owned). Tick some (or Tick all) › Move N into the cube box: those rows say \"In the cube box\" on the Cards tab, the box's own page lists them, and the counts at the top change. Cards in another deck stay there. Delete the box in Storage: the Box tab says it's gone and offers to make it again. " +
            "7) ⋮ › Copy the list: a card a line, A–Z — paste it into CubeCobra. ⋮ › Import a list: paste CubeCobra's plain text (or load its CSV export, or \"2x Lightning Bolt (M10) 146\" lines): \"Finding n of N cards…\", then \"Added N cards\", what was already in left out, and anything not found named. " +
            "8) Share (top right, signed in): the deck sharing — all friends, one friend, a pod or a link. On the friend's phone or manabind.com the shared item reads \"CUBE · 360 CARDS\" with its cards; Copy to my cubes makes their own copy in their Cubes. \"Share all my decks\" includes cubes. " +
            "9) Draft tab: Seats (pod size) 8, Packs 3, Cards 15 (the line says how many the cube has and how many are needed). Deal packs: each seat's three packs listed by name; Shuffle and deal again changes them. Too few cards: \"The cube is N cards short of 360\". Sealed deck (on a seat) makes a Limited deck whose pool is that seat's 45 cards and opens it — strongest pairs and Add basic lands work as for any draft or sealed deck. Start a draft deck: a Limited deck whose pool is every dealt card, to move your picks into the main deck. Copy pack lists copies them as text. " +
            "10) Sync: signed in on two devices (or the phone and manabind.com), add different cards to the same cube on each: both sets arrive; change its size on one and make the box on the other: both stick. An older app version shows the cube under Decks › Archived and leaves it alone. " +
            "11) Settings › Data and speed › Reset collection: Cards only and Collection keep the cube (Collection removes its box with the storage places — the cards stay in the cube); Everything removes it, the count line saying \"… · 1 cube\"."
    )
)
