package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "binder-or-deck",
        "Binder or deck is asked first",
        "Search for a card, open its menu and choose Add to binder/deck. Also try Move and Copy from inside a deck or a binder. It should ask \"A binder\" or \"A deck\", then list only those."
    ),
    TesterNote(
        "report",
        "Report a problem from anywhere",
        "Shake the phone, or tap the bug button at the edge of the screen. It takes a picture of the screen and sends your note with what the app was doing."
    ),
    TesterNote(
        "tools",
        "Tester tools",
        "Hold the bug button, or open Settings and choose Tester tools: the activity log, sync details, switches, a second account to test with, and Suggest an idea."
    ),
    TesterNote(
        "scan-debug",
        "Scanner readout",
        "Turn on \"Scanner readout\" in Tester tools, then scan a card. A strip shows what it read and how long it took, with a button for a scan that came out wrong."
    ),
    TesterNote(
        "update",
        "Tester builds update themselves",
        "When a newer tester build is published, this app offers it when it opens. Settings, App Updates checks by hand."
    ),
)
