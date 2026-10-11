package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "storage-rules",
        "Storage rules: say what a place holds, how its sections go and the order inside them — and which place wins",
        "1) Collection › Storage › open a box › ⋯ › Change place (or New place). Under \"Rules\": Holds — Everything, Choose… or one of your saved filters. Choose… opens the Cards filter panel (Type, Text, Colors, Rarity, Tags incl. Untagged) with Advanced filters behind it; it says \"Holds blue cards\" and how many of your cards fit. Done. " +
            "2) Sections by: None, or any way Sort a pile splits (Colour, Card type, Set, Value, Mana value, Rarity, Colour identity, A–Z, Collector number). Picking one fills Sections (still yours to rename); Value / Mana value / A–Z / Number / Set take their starting points (\"20, 5, 1\"), Colour has \"Lands get their own section\". Then sorted by: A–Z, Collector number, Mana value then A–Z, Price high to low, Set then number. Save: the place's line in Storage says it, e.g. \"Box · Holds blue cards · by card type · A–Z\". A binder keeps Pockets per page and In order, and gets Holds. " +
            "3) A box you made before this build with \"Sorted: by colour\" now reads \"by colour · A–Z\" and works as before; nothing to redo. An older app on another phone still sees its old rule. " +
            "4) Make two places whose rules overlap (a blue box and a red box, then an Izzet card): the editor warns \"Also fits Red box (Red box comes first)\" with a Rule order link. Storage › ⋮ › Rule order: drag a place (long-press the row) or use its arrows. \"When a card fits more than one place, it goes to the first one here.\" " +
            "5) Scan › Put away › Let the app choose: each card goes to the first place in rule order whose rules fit it and that isn't full; the line says the section and where in it — \"Instants › between Counterspell and Cryptic Command\". A card no place fits waits in Add cards (\"No place fits … choose a place, or make a rule\"). Putting away into a chosen box says the section and spot the same way. " +
            "6) Open a place with rules: \"N cards don't fit this place's rules · Review\" lists copies it doesn't hold (\"Doesn't belong here — belongs in Blue box\") and copies in the wrong section (\"In Red — belongs in Blue\"), each with Move, and Move all. Each section's cards are listed in the place's order. " +
            "7) Storage › ⋮ › Sort a new pile: once a place holds something particular, Start from has \"Into my storage\" — a pile per place in rule order and \"No place fits\"; the smart piles (decks need, friends want, binder gaps) still come first. Done › File everything puts each pile's cards into its place and section. " +
            "8) Sync with another device (or the web app): the rules and the rule order arrive there; changing them on both merges field by field."
    )
)
