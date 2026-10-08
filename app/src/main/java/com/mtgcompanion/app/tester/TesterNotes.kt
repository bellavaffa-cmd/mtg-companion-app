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
    ),
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
    ),
    TesterNote(
        "trade-nights",
        "Game nights: trade at the night",
        "Needs two accounts (A and B — a second phone, or manabind.com signed in as the other) in the same pod, and the server update " +
            "for trade nights applied (until then a game night simply has no Trades section — that's expected). " +
            "Get ready: on A make a binder whose name has \"trade\" in it (\"Trade binder\") with a few cards, mark one for trade, and put a card on the Wishlist that B owns. " +
            "On B do the same the other way round: a trade binder holding A's wishlist card, and a wishlist card that A's trade binder has. Try to make the two cards worth about the same. " +
            "1) A plans a game night in the pod (Friends › pod › Plan a game night). On B open the invite and answer Maybe: no Trades section. Answer Going: \"Trades\" appears under Ready for the night. " +
            "2) Bring for trades: the trade binder is ticked already, but it says \"Nothing is shared until you do\". Tick another binder, untick it; if you have a \"Bring to game night\" list (Trade matches tonight › Bring them), Event bag is offered too. Tap Share N lines with the table: it says \"Shared with the people going\". " +
            "3) On A (Going): within a few seconds, without reloading, Wanted here lists B's card from A's wishlist with \"B · Wishlist\"; share A's trade binder too, and on B \"They want from you\" names A with the card. A card one of your decks is missing (not owned at all) shows as \"A deck needs it\"; a collection goal's missing card as \"Goal\". " +
            "4) Suggested trades: B appears with \"you get … / you give …\" and both values (within \$2 or a tenth of each other). A card one of your decks uses that isn't marked for trade is never offered. Make one side much dearer (add an expensive wishlist card): the suggestion drops cards until it's fair, or disappears if it can't be. " +
            "5) Tap Propose this trade: the trade screen opens filled in, \"At game night\" under B's name, Pick cards on their side shows \"Bringing tonight\". Send it: you're back on the night, and the Trade table shows it as waiting. It works even if A and B aren't friends (pod members), as long as B shared a list. " +
            "6) On B: the trade is in Trades as usual — accept it. On both phones the Trade table now says agreed; tap Swapped on each: Update my binders moves the cards (gives out of your binder, gets into the one you pick), the row gets a tick and \"Done\"; tapping again can't move them twice. " +
            "7) Privacy: A sets Maybe — B no longer sees A's list (and A sees nobody's); Stop sharing takes your list away from the others; someone not invited never sees anything; after blocking someone their list is gone. " +
            "8) Change the night to yesterday or call it off: lists close (\"Lists are closed\"), the Trade table stays."
    )
)
