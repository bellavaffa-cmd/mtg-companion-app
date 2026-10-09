package com.mtgcompanion.app.tester

/** One thing a tester build asks to be tried: what changed, and how to see it. */
data class TesterNote(val id: String, val title: String, val howToTry: String)

/**
 * What's new in this tester build — shown the first time it opens, each with "Works" and "Problem".
 * Rewritten for every build: it's the list of what to look at before the change goes to the real app.
 */
val TESTER_NOTES: List<TesterNote> = listOf(
    TesterNote(
        "sort-by-type",
        "Sort a pile: a ready-made recipe by card type",
        "1) Collection › Sort a pile. Under Start from, second (after Commander by colour) is a new \"By card type\", its line \"Creatures · Instants · Sorceries · Artifacts · Enchantments · Lands — an artifact creature goes with Creatures\". " +
            "2) Tap it: the table shows 1 Decks need, 2 Friends want, 3 Binder gaps, then Creatures, Planeswalkers, Battles, Instants, Sorceries, Artifacts, Enchantments, Lands, Other (12 piles). " +
            "3) Sort a few cards: a creature, an instant, a land, and some with two types. A card goes on the first of those piles it fits: an artifact creature or enchantment creature (and Dryad Arbor) on Creatures, an artifact land on Artifacts, Urza's Saga on Enchantments, a spell // land card by its front face (Instant // Land on Instants). Anything none of those (a Conspiracy, a Scheme) goes on Other. " +
            "4) Make your own recipe: the Split by choices now read Value, Colour, Card type, Set, Mana value, Rarity, Colour identity, A–Z, Collector number — Card type in the first row. Your saved recipes are unchanged (open one that splits by colour identity: still colour identity). " +
            "5) The other templates are as before: Binder by set, Rares by value, What my collection needs."
    )
)
