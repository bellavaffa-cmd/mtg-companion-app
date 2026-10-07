package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "print-proxies",
        "Print proxies",
        "Open a deck › menu › Print proxies…: the cards you don't own should be picked already (a deck you hold: its proxies), with − and + to choose how many of each. Pick A4 or Letter, \"PROXY — not for sale\", Low ink and Back faces too, then Print or save PDF and save it as a PDF. Nine cards a page, cut lines along every edge; printed at 100%, a card should measure 63 × 88 mm. Also try Print proxies under Not owned on a deck's pull list (with \"Mark as proxies in …\" on, the deck should show those cards as proxies afterwards) and on Collection › Spread thin, which picks the copies each card is short."
    ),
    TesterNote(
        "hand-stats",
        "Hand stats in the playtest",
        "Open a deck › menu › Playtest and tap the chart button at the top: Hand stats should show the chance of 2–4 lands in your opening seven, lands on average, how often you'd mulligan keeping 2–5 lands, a land drop on turns 1 to 4 on the play and on the draw, and a two-drop on turn 2. A Commander deck counts the 99, not the commander. The numbers come from 10,000 shuffles with a fixed seed, so they stay the same each time you open it for the same deck. Mulligan, put cards on the bottom, keep and draw should all still work as before."
    )
)
