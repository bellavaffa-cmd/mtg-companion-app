package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
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
