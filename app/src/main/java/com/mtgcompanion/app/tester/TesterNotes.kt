package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "scan-card-panel",
        "Scanner: the last scanned card stays over the camera",
        "Open Scan (the plain scanner, not Sort a pile). Before the first card nothing new shows. " +
            "1) Scan a card: a panel appears at the bottom, above View list, below the outline — a small picture of the exact printing, the name, and big in gold the bottom-left details: set code · collector number · rarity letter · language (e.g. \"FIN · 0306 · L · EN\"; C/U/R/M, L for a basic land, S special, T token). Under it the set name, the price in your currency and \"×N in this scan\", and a line \"You own 3 · 2 in Red box, 1 in Krenko deck · +4 in other printings\" (or \"You don't own this card yet\"). A chip says how it was recognised: From small print, By name, By sight, Learned, Picked by you — or Best guess, with an amber outline round the panel: check it. " +
            "2) It stays until the next card is scanned, then slides to the new one (with Remove animations on, it just changes). Turn the phone on its side and back: still there (on its side it sits at the bottom left, beside the card). " +
            "3) Tap the picture or the name: the card's details. Tap ⇄ (Change printing): the printing picker, with It's a different card; pick one and the panel and the row change, the chip says Picked by you. Tap ✦: foil on and off (the panel says Foil and shows the foil price; only for printings that come in foil). Tap Undo: that scan is off View list and the panel goes back to the card scanned before it — undo them all and it hides. " +
            "4) Camera reads another printing: scan a Forest whose small print reads (say FIN 306), keep it still, then tap Change printing and pick another Forest (say FIN 307) — keep the card under the camera. Within a moment the details line pulses amber (steady amber with Remove animations on) and \"Camera reads FIN · 306\" shows with a \"Use FIN 306\" button; tap it and the row goes back to what the camera reads. Taking the card away or showing the next card stops it; \"0306\" vs \"306\" never flashes. " +
            "5) TalkBack: on each new card it says it in one go — \"Forest, FIN 306, basic land, English, $0.40\". " +
            "6) Sort a pile, Put away, Check and Scan to tick: no panel (their own panels are unchanged). " +
            "7) Settings › Scanner › Show last scanned card: off, and the scanner is as before."
    ),
    TesterNote(
        "goal-piles",
        "Goals: a Goals need pile in Sort a pile, and completed goals in friends' Activity",
        "Before you start: have two open goals, e.g. Complete a set › Duskmourn › Uncommon, and Playsets of a list \"Shock lands\" (Steam Vents, Sacred Foundry), " +
            "and a few cards they're missing to hand (for a set goal, the exact printing). " +
            "A) GOALS NEED PILE. " +
            "1) Collection › Sort a pile › Make your own recipe: under First, pull out there's a new switch \"Cards my goals need\" (off). Tick it: the piles become 1 Decks need, 2 Goals need, 3 Friends want… " +
            "Start from \"What my collection needs\" (with a goal open): Goals need is already on there; the other templates and a new recipe leave it off. " +
            "2) Sort with that recipe and scan a card a goal is missing: it goes on the Goals need pile, the big line says \"GOAL · DUSKMOURN UNCOMMONS\" and under the card \"Uncommon · … · 41/92 → 42/92\"; the phone says \"Two, goal Duskmourn uncommons\". " +
            "3) Scan a second copy of a card the goal needs once: it's not pulled out again (it goes on its usual pile). For Shock lands (4 of each), the 1st–4th Steam Vents go to Goals need with the count going up, the 5th doesn't. " +
            "4) Order: a card one of your decks is missing goes to Decks need first, with \"Goal: … 42/92\" under Also wanted; a card a goal and a friend both want goes to Goals need, with \"Priya wants one\" under Also wanted; Send to pile N instead moves it to the friend's pile. A card new for a set binder that a goal also needs goes to Goals need (the binder line is under Also wanted). " +
            "5) Finish: the summary shows \"Goals need · 4\" with \"Duskmourn uncommons 3, Shock lands 1\". File everything: Duskmourn cards go into your Duskmourn binder (one kept in order, by set) and the summary offers Fit in order for it; the other goal cards go into Unsorted (or where you set the Goals need pile to go in the recipe's layout). Open Goals: the progress has moved. If the cards complete a goal, the \"Goal complete!\" celebration shows once. " +
            "6) Two devices, one with an older Manabind (before this build) if you have it: tick Goals need on a recipe here, rename that recipe on the older app — after a sync the recipe has the new name and still pulls out Goals need. Untick it here: it stays off on both. " +
            "B) COMPLETED GOALS IN ACTIVITY (needs the server update 20261008110000_goal_activity.sql — until it's applied there's nothing new here and nothing goes wrong). " +
            "7) Settings › Privacy › Friends' activity: a new switch \"Share completed goals\", on. " +
            "8) Complete a goal (a Custom list of one card you own is quickest). On a friend's phone (or the web app signed in as them), Friends › Activity: \"<you> completed a goal: <name>\", \"Set goal · 92 cards\" (or Playset/Deck/Card list goal) with a trophy and the goal's priciest card; with the Activity tab open it appears by itself within a few seconds. Tapping it opens your profile. " +
            "9) Turn Share completed goals off, complete another goal: friends don't see it — and they no longer see the earlier one either. Turn it back on: the earlier one is back, the one completed while it was off never shows. " +
            "10) Completing the same goal on two devices shows it once. Someone you've blocked (or who blocked you) never sees it."
    ),
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
