package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "scan-sounds",
        "Scanning sounds by rarity (and value)",
        "The old camera-shutter click is gone. Open Settings › Scanner: Scan sounds is on, by rarity, at 60%. Tap Play all — you should hear a soft tick (common), a two-note blip (uncommon), a chime (rare), a rising flourish (mythic), a jackpot sting (value) and a tick with a sparkle (foil), each with its own buzz. Then scan a few cards of different rarities in the scanner: each card plays its rarity's sound as it's recognised. Scan quickly through a pile — there should never be a pile-up of sounds, at most one every quarter second (the rarer card wins). Switch Sound by to By value and set Worth at least to something low (say 1 in your currency): a card worth that or more plays the sting, the rest just tick; Both plays the sting for those and rarity for the rest. Try the same in Put away, Sort a pile, Check a box and Scan a page (one sound per page, for the best card on it, not nine). Put the phone on silent or vibrate: no sound, just the buzz — until Play in silent mode is on. The sounds follow the media volume. Turn Vibrate off: no buzz. With TalkBack on, the scanner's line now reads out with the card's rarity (\"Added Sol Ring, Uncommon\")."
    )
)
