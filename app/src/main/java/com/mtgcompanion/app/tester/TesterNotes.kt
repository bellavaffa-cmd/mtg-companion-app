package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "data-and-speed",
        "Data and speed",
        "Settings › Data and speed (or Collection › Storage › Data and speed): it should show your copies, \"Card data saved for offline\" (how many of your printings the Offline Search database has — download it under Settings › Offline Search if it says 0), \"Opening All cards\" (open the Collection's All cards first; under a second shows green) and when it last synced (\"2 min ago\", or Not signed in)."
    ),
    TesterNote(
        "backup",
        "Save a backup, and restore it",
        "Settings › Data and speed › Save a backup: pick where to keep the file (Downloads or Drive). Change something — rename a deck, move a card to another place, delete a binder — then Restore and pick the file. It should say when the backup was made and what's in it (decks, binders, copies in places, loans, sealed, graded, gear, deck history, photos). Merge: the renamed deck keeps its new name, the deleted binder comes back, nothing you've changed since is lost. Try Replace on another go: the deck goes back to its old name, settings too. Signed in, the restored binder should turn up on manabind.com too. A backup saved on manabind.com should restore here as well."
    ),
    TesterNote(
        "big-collections",
        "Big collections feel quick",
        "Behind the scenes, this round tests every screen with a 25,000-card collection and fixes anything slow, so big collections feel as quick as small ones. With a big collection (import a long list), open All cards, search it, use the filters, open Storage, Upkeep and Value by place, and add a card: none should pause or stutter."
    ),
    TesterNote(
        "sealed",
        "Sealed product",
        "Collection › Storage › Sealed › + Add sealed product: type a set (\"dusk\") — it should offer Duskmourn Play Booster Box, Collector Box, Bundle…; type a precon (\"Blame Game\") for \"Precon: Blame Game\"; or keep any words as your own product. Set how many, the place, paid each and worth now each, Save. The row should read \"×2 · Cupboard, hall · paid \$210 each\" with the value and +13% (green) or −9% (orange), the total at the top, and \"value you entered\" where there's no price paid. Open a booster box: one should come off and the scanner open as Sorting a new pile, named after the box. Open the precon: one should come off and a deck with its list filled in should open. Value by place should count them, \"2 sealed\". Check the list on your other device and on manabind.com."
    ),
    TesterNote(
        "event-bag",
        "Pack your bag for a game night or an event",
        "Play › Pack your bag (or Game night › Pack your bag): pick Tonight's game night, an event, or Pack for… with a name like \"Game night at Priya's\", a day and who's coming (\"Priya, Sam\"), and choose two decks. The checklist should show Decks (each with its deck box from Gear, or \"Sol Ring lent to Sam\" in orange when a card is lent from the deck), Tokens and extras (\"Goblin tokens ×20 · for Krenko\", \"Poison and +1/+1 counters · for Atraxa\", \"Dice, playmat · Gear\") and For trades (\"3 cards Priya wants · Trade binder p4, p7\" for a friend coming whose wishlist matches your cards, and \"Sam's borrowed cards · to give back\"). Tick a few, close the app and reopen: the ticks should still be there (this phone only). All packed ticks the rest. Coming home should list only what went out, with \"Still to come back: …\" until everything's ticked."
    ),
    TesterNote(
        "gear",
        "Gear: sleeves, deck boxes and tokens",
        "Collection › Storage › Gear › + Add gear: add sleeves (38 left, on two 100-card decks) — the row should say \"On Krenko and Atraxa · a deck needs 100 · running low\" with the count in orange. Add inner sleeves on a binder and a deck (\"Double-sleeving: …\"), four deck boxes holding three decks (\"4 · 1 empty\"), and Goblin, Treasure and Soldier tokens kept in a place (\"Goblin ×24, Treasure ×18, Soldier ×12 · Token box\"). This deck needs should say \"Krenko goblins: 100 sleeves, a deck box and Goblin tokens. You have them all.\" or what's missing; a deck's Stats should say the same under This deck needs. Check the gear shows on your other device and on manabind.com."
    ),
    TesterNote(
        "graded",
        "Graded cards",
        "On a card you own, Where it is › Mark a copy as graded: pick PSA, BGS, CGC or Other, a grade, the cert number and your value, which copy and where it's kept, then Mark as graded. Where it is should show \"PSA 10\" with a Graded label, its place and cert, and your value. The copy should leave its binder: a deck that needs the card should now list it as missing, it shouldn't count as a spare or turn up on a pull list. Value by place should count it at your value in its place. Tap it: Out of its slab should make it a raw copy again, back where it was; Remove takes it out of the collection."
    )
)
