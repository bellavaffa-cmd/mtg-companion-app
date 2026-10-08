package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "scan-memory",
        "Scanner: learns from your corrections",
        "1) Open Scan and scan a basic land (say a Plains) whose set code can't be read, so its row says \"Best guess · pick art\". Open View list, tap Best guess · pick art and pick a different Plains. 2) Take that row off, scan the same Plains again: it still comes in as the usual one (a printing is only preferred once picked twice). Pick the same printing again. 3) Scan it a third time: it comes in as your printing straight away, the status ends \"· learned from your correction\", and the row has a gold \"Learned\" tag. 4) Tap Learned: \"You corrected this before…\" with Forget it and Pick another printing. Pick another printing → a different one: it's remembered instead (and needs picking twice again). 5) A misread: scan a card whose small print reads (the row has no Best guess). Tap its set name under the card name to change the printing, pick another. Scan the same card again: it comes in as your pick the very first time, tagged Learned. Pick the original printing back on that row: next scan goes by the small print again. 6) Sort a pile: start a sort and scan a card whose small print reads; Wrong card? › Pick the printing, pick another. Take the next copy of that card (or Undo and show it again): it goes in as your pick and the panel shows a gold Learned chip after Wrong card?; tap it › Forget it. For a card whose set won't read, pick the same printing on two copies; the third is put right. 7) Settings › Scanner › Learned corrections (N): tap to open — each line says what was read (\"Plains, set unread\" or the title and set as read), → the card it becomes, and how often it was corrected and used; Forget removes one, Forget all (tap twice) clears the list. 8) Signed in on two devices: a correction made on one shows in the other's list after a sync; Forget on one removes it on the other. 9) Settings › Data and speed › Reset collection: \"Cards only\" keeps the learned corrections, \"Collection\" and \"Everything\" clear them (the wording of both says so)."
    )
)
