package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
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
    )
)
