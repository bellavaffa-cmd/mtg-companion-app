package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "scan-zoom",
        "Scanner: zoom in and out",
        "Open Scan: under the top bar on the right is − 1.8× +. Pinch two fingers apart on the camera picture: it zooms in and the chip follows (2.3×…); pinch together to zoom out, down to 1× (or 0.6× on a phone with a wide lens). Tap + and −: 2×, 2.25×, 2.5× — quarter steps, half steps from 3×; − greys out at the bottom and + at the top. Tap the chip itself: back to 1.8×. Zoom past 2.9× (where the phone may switch lens): \"Hold the card farther away\" shows under the chip, and goes when you come back under it. Set 2.25×, leave the scanner and open it again: still 2.25×. In Sort a pile with Capture without tapping on, hold a card still and pinch: it isn't taken while your fingers are on the screen, and after the zoom it's taken once it's held still again — a card already taken isn't counted twice. Taps still work everywhere (the menu, View list, the sort panel). Scan a binder page: the same − 1× + sits next to Scan this page, starting at 1× and remembered separately. With TalkBack: the chip reads \"Zoom 1.8 times\", the buttons \"Zoom out\" / \"Zoom in\", and a double-tap on the chip resets it."
    )
)
