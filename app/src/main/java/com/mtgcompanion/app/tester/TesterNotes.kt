package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "scan-smallprint",
        "Scanner: the small print names the printing",
        "Scan on Accurate (Settings › Scanner), with the card index downloaded. " +
            "1) Your report: scan a pile of different Final Fantasy Forests (FIN, \"L 0306 FFIX\" / \"FIN • EN\"), each laid on top of the last. Each goes in as its own printing (tap the row: FIN #306, #307…), not \"Forest again — copy 3\". Do it again with a pile of the same set's Plains or Islands, and once with the card underneath showing its own small print below the top card's: the top card's printing is the one taken. " +
            "2) Title covered: hold a card with a finger or a sleeve sticker over the name (or one whose title is hard to read — a showcase or full-art frame), the bottom-left small print in view. After a moment it's recognised from the small print alone, as that exact printing; the row has no Best guess. Move the card so a different card's small print shows: it isn't taken until it reads the same twice. " +
            "3) Layouts: a card from before 2023 (\"0123/0277 R\" over \"M21 • EN\"), a newer one (\"R 0123\" over \"DSK • EN\"), a Secret Lair with a four-digit number, a set code with digits (2X2, 40K, M21), a promo with a letter after its number, and a non-English card (\"• JA\", \"• DE\"): each goes in as its printing. " +
            "4) Zoom: pinch out to about 1.25× and scan, then in to about 2.5× and scan, and once with the card well off-centre in the guide or filling it to the edges: the printing is still read. " +
            "5) What you've taught it still counts, but only where the set couldn't be read: a printing you picked twice for a basic land doesn't override a Forest whose small print reads. " +
            "If one goes wrong, send a scan report from that row — the small print the scanner read is in it."
    ),
    TesterNote(
        "scan-memory",
        "Scanner: learns from your corrections",
        "1) Open Scan and scan a basic land (say a Plains) whose set code can't be read, so its row says \"Best guess · pick art\". Open View list, tap Best guess · pick art and pick a different Plains. 2) Take that row off, scan the same Plains again: it still comes in as the usual one (a printing is only preferred once picked twice). Pick the same printing again. 3) Scan it a third time: it comes in as your printing straight away, the status ends \"· learned from your correction\", and the row has a gold \"Learned\" tag. 4) Tap Learned: \"You corrected this before…\" with Forget it and Pick another printing. Pick another printing → a different one: it's remembered instead (and needs picking twice again). 5) A misread: scan a card whose small print reads (the row has no Best guess). Tap its set name under the card name to change the printing, pick another. Scan the same card again: it comes in as your pick the very first time, tagged Learned. Pick the original printing back on that row: next scan goes by the small print again. 6) Sort a pile: start a sort and scan a card whose small print reads; Wrong card? › Pick the printing, pick another. Take the next copy of that card (or Undo and show it again): it goes in as your pick and the panel shows a gold Learned chip after Wrong card?; tap it › Forget it. For a card whose set won't read, pick the same printing on two copies; the third is put right. 6b) A different card: on a scan-list row tap its set line (or Best guess · pick art) › It's a different card, or in Sort a pile Wrong card? › It's a different card. Type a few letters, pick the name, then its printing: the row (or the sort's card) becomes that card — in Sort a pile it moves to that card's pile, with the count and Undo still right. Scan the same card again the same way: it comes in as the card you named, tagged Learned. 7) Settings › Scanner › Learned corrections (N): tap to open — each line says what was read (\"Plains, set unread\" or the title and set as read), → the card it becomes, and how often it was corrected and used; Forget removes one, Forget all (tap twice) clears the list. 8) Signed in on two devices: a correction made on one shows in the other's list after a sync; Forget on one removes it on the other. 9) Settings › Data and speed › Reset collection: \"Cards only\" keeps the learned corrections, \"Collection\" and \"Everything\" clear them (the wording of both says so)."
    ),
    TesterNote(
        "deck-upgrade",
        "Decks: upgrade with my cards",
        "Open a Commander deck you've built (with a commander) whose binders/boxes/Unsorted hold some cards that aren't in it. 1) Open the Suggestions tab: \"Upgrade with my cards\" is the first section. After \"Looking through your collection…\" it lists swaps — the deck's card (orange) → a card you own (gold) — and above them a line like \"5 upgrades from your cards · $12.40 of cards you already own · pull from 3 places\" (the money part is left out when the cards have no price). 2) Check a row: both cards do the same job (ramp for ramp, removal for removal…); the reason reads like \"Both are ramp; Arcane Signet is in 61% of Krenko decks, Wayfarer's Bauble in 22%.\"; under it is where the new card is (\"Red box › Artifacts\", \"Trade binder p3 s5\", \"Unsorted\", or \"in Atraxa deck\" when another deck has your only copy) and the price difference. The commander, lands, combo pieces and cards you tagged \"keep\" are never offered as cuts; cards outside the commander's colours or not legal in the format are never offered as adds; a card you marked as a cut is offered first. 3) Tap Swap now: a message says \"X in for Y — on the pull list (where)\"; the cut card is on Considering, the new card is in the deck, and the deck's menu › Pull list shows it with its place. Tap Undo on that message: the deck is exactly as before — the cut card back in its place (and back out of Considering), the new card gone from the deck and the pull list, and for a physical deck the cut card's copy back out of Unsorted. Tap Consider on another row: the new card goes on Considering, the deck is unchanged. Tap Not this one: the row goes, and stays gone after leaving and reopening the deck; \"Bring back N dismissed\" undoes it. 4) Untick a row and tap Apply all checked (n): only the ticked swaps are made, and one Undo takes them all back. 5) A deck at bracket 2–3 with a Game Changer (or combo piece) you own: the footer shows \"Show N that would raise the bracket\"; tap it — those rows have an orange outline and \"Would move the deck to bracket 4\" (or 3), and are unticked. 6) Deck menu (⋮) › Upgrade with my cards: jumps to the Suggestions tab. 7) Turn on airplane mode and reopen the deck's Suggestions: a note says EDHREC can't be reached and swaps are matched by role and EDHREC rank (\"Consider ranks #900 on EDHREC, Divination #9,000\"). A Modern/Standard deck works the same way with no bracket and no commander percentages. Compare with manabind.com: the same deck and collection should give the same swaps."
    ),
    TesterNote(
        "collection-goals",
        "Collection goals: targets with progress",
        "1) Collection home: under New sets there's a Goals card (\"Set a goal…\" while you have none). Tap New goal: four kinds — Complete a set, Playsets of a list, Foil a deck, Custom list. " +
            "2) Complete a set: type \"Dusk\", pick Duskmourn, tap Uncommon (the line says \"Duskmourn uncommons · N cards\"), Make it a goal. The goal opens: have/need (e.g. 41/92), percent, \"51 missing · about £…\", and the missing cards with their number and price. Tap All cards: owned ones show green n/1. " +
            "3) Tap Add missing to Wishlist: \"N cards put on your Wishlist\". Tap it again: \"Your Wishlist already has them all.\" A card already wanted ×2 isn't lowered. " +
            "4) Signed in: Offer a trade lists friends whose shared binders have the missing cards, with Ask (starts a trade). " +
            "5) Playsets of a list: name it Shock lands, add Steam Vents by name (Done on the keyboard), paste \"Sacred Foundry\" and \"Blood Crypt\" on two lines, Make it a goal: each needs 4 (change Copies of each to 3 for 3). Custom list: \"4 Lightning Bolt\" / \"2 Counterspell\", with − / + per card. " +
            "6) A deck's ⋮ menu › Make this a goal › Foil this deck: \"Foil <deck>\", basics left out, Count cards in decks on; foils the deck's pull list brought in count. Add a card to the deck: the goal grows with it. Tick Count cards in decks off/on on any goal: copies in your physical decks count or not. " +
            "7) A set's page (Collection › Sets › a set): Make this a goal, with the same rarity and foil choices. " +
            "8) Scan a card a goal is missing (Scan, plain list mode): under \"Added …\" a small \"Goal: Duskmourn uncommons 42/92\"; a card the goal already has shows nothing. Scanning the same missing card again only counts while copies are still needed. " +
            "9) Complete a goal (e.g. a Custom list of one card you own, or add the last card): within a second confetti falls, the phone buzzes and \"Goal complete!\" shows with See goals; the goal moves to Completed on the Goals screen. With the phone's Settings › Accessibility › Remove animations on: no confetti and no buzz, just the note. " +
            "10) Two devices signed in: a goal made on one shows on the other; rename it on one and delete another on the other — both come through; completing on one shows Completed on both (no second celebration). " +
            "11) Settings › Data and speed › Reset collection: Cards only keeps the goals; Collection and Everything clear them (the count line says \"N goals\"); Undo brings them back. " +
            "Not in this build: an Activity feed entry (the feed is made on the server) and goals in Sort a pile's smart piles."
    ),
    TesterNote(
        "trade-crash",
        "Friends: Propose a trade no longer crashes",
        "Your crash report (build 33): \"Placement happened before lookahead\" on Propose a trade. It came from friends' profile pictures swapping in as they loaded. " +
            "1) Friends › open a friend › New trade: the screen opens, their picture (or initial) at the top. " +
            "2) Pick cards on both sides, take some off again, add a suggested card under the value. " +
            "3) Turn the phone while it's open, and back. " +
            "4) Switch quickly between Friends, Trades and back into New trade a few times, and open it for a friend with a photo and one without. " +
            "Pictures still show (a GIF still moves); while one loads, or if it can't, the initial shows instead. Nothing crashes."
    )
)
