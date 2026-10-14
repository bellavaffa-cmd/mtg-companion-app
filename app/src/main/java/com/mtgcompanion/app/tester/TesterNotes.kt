package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "deck-tabs-crash",
        "Decks: switching tabs no longer crashes",
        "Your crash report (build 47): opening Stats in a deck crashed the app (two panels shared one id). Open a deck and switch between all its tabs, several times and quickly, including Stats with its panels open and closed; scroll Stats to the bottom. Nothing should crash."
    ),
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
        "binder-views",
        "Binders show the cards your decks hold — \"Unsorted\" is now \"No binder\"",
        "Before anything else: this build saved a backup of your library the first time it opened — Settings › Data and speed › Backup › \"Restore from before the binders update\". " +
            "1) Collection › Binders: the pile at the top is called \"No binder\" now (it was Unsorted). Its line counts your physical and proxy decks' real cards too: \"40 cards · 28 in decks, not in a binder\". Open it: your loose cards first, then IN DECKS — each deck card with a badge (\"In Talrand deck\", or \"In 2 decks\") and the deck's chip; tap the chip to open the deck. " +
            "2) Open a Physical (or Proxy) deck › ⋯ › \"Put all cards in a binder…\" › pick a binder: \"N cards put in Rares\". Proxies never go in. Open that binder: the deck's cards are listed under IN DECKS with their badge, and its row on the Binders tab says \"84 cards · 12 in decks\" and \"… · 1 deck\". No binder no longer lists that deck's cards. " +
            "3) Note the Collection header's count and the Cards tab's dashboard value before step 2 — they're the same after it: the cards are still the deck's; the binder only shows them. " +
            "4) In the binder, a deck card's ⋮ › \"Take out of binder\": it moves to No binder (still in the deck). ⋮ › \"Move to another binder…\" › pick one: it shows there instead. \"Open deck\" opens it. " +
            "5) On the deck, long-press a card: \"Add to binder…\" (or \"Binder: Rares ×1 · change\") puts just that card's real copies in a binder, or takes them out. " +
            "6) A Virtual or Prototype deck, a cube and the welcome samples: their cards never show in a binder or in No binder, and they don't offer \"Put all cards in a binder\". " +
            "7) Cards tab › Group by Binder: deck cards sit under their binder, the rest under \"No binder\"; decks aren't groups here any more (Group by Place still has them). " +
            "8) In a binder, the + / − steppers, Remove, Move/Copy and long-press picking are for the loose cards only — deck cards aren't picked (the IN DECKS note says so). " +
            "9) Look for \"Unsorted\" anywhere you'd see it before (scanner's Add to, imports, Sort a pile, Put back, deck delete): it says \"No binder\". A friend you share a binder with sees only its loose cards. " +
            "10) Sync with the web app or another phone on this build: which binder a deck card is in comes along."
    )
)
