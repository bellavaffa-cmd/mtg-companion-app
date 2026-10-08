package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "spoilers",
        "Spoiler season: want cards before they're out",
        "1) Collection home › New sets. Each set says \"Releases in N days\" (or how long it's been out) and how much is revealed — \"48 of 286 revealed\" before release, \"286 cards\" once out. Open a set coming soon. " +
            "2) Its page: the countdown, then Revealed so far — a gallery, newest revealed first, 24 at a time (Show more). Each card shows \"Releases in N days\" instead of a price; on a set already out it shows the price. Tap the picture: the card opens. " +
            "3) Tap Want on a card, then + to want 2. Open your Wishlist: the card is there ×2, saying \"Releases in N days · no price yet\" and with no price. − back to 0 on the set's page takes it off the Wishlist. " +
            "4) Fits your decks: with a Commander deck that has a commander, cards in the commander's colours that share a creature type, theme or role (Mana ramp, Removal, Token maker…) with four or more of its cards show \"Fits\" and the deck's name. Tap the deck chip: it gets a tick, and the deck's Considering list has the card. Tap Only cards for my decks: just those cards remain (with no Commander decks the filter is greyed out). " +
            "5) Opening packs: after wanting a card or two, the set's page shows Opening packs · N wanted cards. Open it: your wanted cards from the set (A–Z, with how many wanted). Tap Pulled: the count drops by one, the card is in Collection › Unsorted, and Pulled so far lists it; at 0 it leaves the list and your Wishlist. Foil adds a foil copy instead. " +
            "6) Release day: on or after the set's release date, open the Wishlist — the card no longer says \"Releases in…\", its price shows (once Scryfall has one) and a price target you set on it works like any other. To try without waiting, want a card from a set out in the last few days: it goes on the Wishlist as a plain card. " +
            "7) Notifications: follow a coming set with the bell (allow notifications). The phone checks twice a day; the first check only notes what's revealed. When cards that fit your decks are revealed after that, you get \"N new cards revealed for <set> that fit your decks\" — once a day at most; tapping it opens the set. Opening the set's page counts as having seen what's there. " +
            "8) Two devices signed in (or manabind.com): a card wanted on one shows on the other with its \"Releases in…\", and Opening packs ticks come through as Unsorted copies."
    )
)
