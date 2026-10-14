package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "legality-why",
        "The deck says why it isn't legal",
        "1) Open a Commander deck that breaks a rule (98 cards, a banned card such as Mana Crypt, no commander, an off-colour card or two copies of something). The pill under the deck name now says the main reason: \"Not legal · 98/100 cards\", \"Not legal · 1 banned\", \"Not legal · no commander\", or \"Not legal · 3 problems\" when it's several kinds of thing. A legal deck still says \"Legal\". " +
            "2) Tap the pill: a sheet lists every problem under a heading (Commander, Deck size, Banned or not legal in Commander, Outside the commander's colours, Too many copies, Companion). Tap a card's row to open the card. A banned or off-colour card offers Move to Considering and Remove (Remove asks first); too many copies offers Reduce; a short deck offers \"Add 2 cards · see suggestions\" (goes to Suggestions), an over-size one \"Remove 1 card · open the cards\". \"See in Stats\" goes to the Stats tab. " +
            "3) Stats tab: on a deck that isn't legal the Legality box is already open, with the same headings. " +
            "4) A Modern deck with a banned card in the sideboard says \"Banned in Modern — it's in the sideboard.\", and Remove there takes it out of the sideboard. " +
            "5) Turn on airplane mode and open a deck: while the cards can't be looked up the pill says \"Checking…\" rather than \"Legal\" or \"Not legal\" (unless something is wrong that needs no card data, like the deck's size), and the sheet says how many cards couldn't be checked."
    ),
    TesterNote(
        "wishlist-scroll",
        "Wishlist tab scrolls like the other tabs",
        "1) Collection › Wishlist (with a few cards on it). Swipe up: the dashboard, the \"Under your price\" box, the \"Cards you want…\" note and \"N cards you said no to\" scroll away with the cards — the whole page moves, not just the cards under them. The Collection's title, search and tabs stay put, the same as on Cards, Binders and Storage. " +
            "2) Scroll to the bottom: \"Set targets for all…\" and \"Buy these cards\" are the last row, under the last card, and nothing covers the last card any more. " +
            "3) Tap \"N cards you said no to\" to open it, scroll down and back up: it's still open. " +
            "4) Switch the view (grid / list button beside the total) and check the grid scrolls the same way. Search in the header: the \"3 cards\" line scrolls with the list too. " +
            "5) Collection › Binders › open a binder: its search box stays at the top, its dashboard scrolls away with the cards, in list and grid view. Long-press a card: the pick's bar at the bottom works as before."
    ),
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
