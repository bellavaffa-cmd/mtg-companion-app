package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "sealed",
        "Sealed product",
        "Collection › Storage › Sealed › + Add sealed product: type a set (\"dusk\") — it should offer Duskmourn Play Booster Box, Collector Box, Bundle…; type a precon (\"Blame Game\") for \"Precon: Blame Game\"; or keep any words as your own product. Set how many, the place, paid each and worth now each, Save. The row should read \"×2 · Cupboard, hall · paid \$210 each\" with the value and +13% (green) or −9% (orange), the total at the top, and \"value you entered\" where there's no price paid. Open a booster box: one should come off and the scanner open as Sorting a new pile, named after the box. Open the precon: one should come off and a deck with its list filled in should open. Value by place should count them, \"2 sealed\". Check the list on your other device and on manabind.com."
    ),
    TesterNote(
        "graded",
        "Graded cards",
        "On a card you own, Where it is › Mark a copy as graded: pick PSA, BGS, CGC or Other, a grade, the cert number and your value, which copy and where it's kept, then Mark as graded. Where it is should show \"PSA 10\" with a Graded label, its place and cert, and your value. The copy should leave its binder: a deck that needs the card should now list it as missing, it shouldn't count as a spare or turn up on a pull list. Value by place should count it at your value in its place. Tap it: Out of its slab should make it a raw copy again, back where it was; Remove takes it out of the collection."
    )
)
