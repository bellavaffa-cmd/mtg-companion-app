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
        "Open Scan: under the top bar on the right is − 1.8× + (with \"Auto\" under the number while auto zoom is on — the next note). Pinch two fingers apart on the camera picture: it zooms in and the chip follows (2.3×…); pinch together to zoom out, down to 1× (or 0.6× on a phone with a wide lens). Tap + and −: 2×, 2.25×, 2.5× — quarter steps, half steps from 3×; − greys out at the bottom and + at the top. Tap the chip itself: back to 1.8× (and to Auto). Zoom past 2.9× (where the phone may switch lens): \"Hold the card farther away\" shows under the chip, and goes when you come back under it. Set 2.25×, leave the scanner and open it again: still 2.25×. In Sort a pile with Capture without tapping on, hold a card still and pinch: it isn't taken while your fingers are on the screen, and after the zoom it's taken once it's held still again — a card already taken isn't counted twice. Taps still work everywhere (the menu, View list, the sort panel). Scan a binder page: the same − 1× + sits next to Scan this page, starting at 1× and remembered separately. With TalkBack: the chip reads \"Zoom 1.8 times\", the buttons \"Zoom out\" / \"Zoom in\", and a double-tap on the chip resets it."
    ),
    TesterNote(
        "scan-auto-focus-zoom",
        "Scanner: auto zoom and auto focus",
        "Settings › Scanner › Auto zoom and focus is on to start with. Open Scan: the chip says 1.8× with Auto under it. Hold a card well back, so it fills about two thirds of the gold outline: within a second or two the zoom creeps up (small steps, a few a second) until the card fills the outline, then stops; it never goes past 2.8×, and never so far the card would run off the outline. Bring the card closer: it zooms back out, down to 1× at most. Hold it at about the right size: nothing moves. Hold a card so it's slightly blurry and keep it still: within about a second the camera refocuses on it (not more than once every second and a half). Scan a foil, glare and all: the title stays bright and readable, not dark. Tap somewhere on the picture: it focuses there, and auto focus leaves it alone for 4 seconds. Pinch or tap − / +: Auto goes away from the chip and the zoom stays where you put it — also after leaving and coming back. Tap the chip: back to 1.8× with Auto. In Sort a pile with Capture without tapping on: a card isn't taken in the middle of a zoom step or a refocus, only once it's been still again for a moment, and never twice. Turn the setting off: no Auto on the chip, the zoom only moves when you move it, and focus is as before (the middle, every 3 seconds). A binder page scan has no auto zoom (one photo of the page), but a tap there focuses too."
    )
)
