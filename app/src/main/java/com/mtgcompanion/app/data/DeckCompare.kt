package com.mtgcompanion.app.data

// Two deck lists side by side — this deck against another deck, or against one of its own saved
// versions: what only this one has, what only the other has, and what both have (with how many
// copies each). Cards are matched by name, so a different printing of the same card is the same
// card, the way versions already count them. The main deck only: commanders included, sideboard and
// Considering left out.

/** One card in a comparison: [here] copies in this deck, [there] in the other. */
data class CompareRow(val name: String, val here: Int, val there: Int) {
    val sameCount: Boolean get() = here == there
}

data class DeckDiff(
    val onlyHere: List<CompareRow>,
    val onlyThere: List<CompareRow>,
    /** Cards in both, the ones whose counts differ first. */
    val both: List<CompareRow>
) {
    val identical: Boolean get() = onlyHere.isEmpty() && onlyThere.isEmpty() && both.all { it.sameCount }
}

/** [deck]'s main deck as card name → copies, the way a DeckVersion keeps it. */
fun deckCounts(deck: Deck): Map<String, Int> {
    val counts = LinkedHashMap<String, Int>()
    deck.cards.forEach { counts[it.name] = (counts[it.name] ?: 0) + it.quantity }
    // A commander missing from the card list still belongs to the deck.
    listOfNotNull(deck.commander, deck.partnerCommander).forEach { if (it.name !in counts) counts[it.name] = 1 }
    return counts
}

/** A saved version's list, commanders included. */
fun versionCounts(version: DeckVersion): Map<String, Int> {
    val counts = LinkedHashMap(version.cards)
    version.commanders.forEach { if (it !in counts) counts[it] = 1 }
    return counts
}

/** The difference between [here] and [there] (card name → copies), names matched ignoring case. */
fun diffDecks(here: Map<String, Int>, there: Map<String, Int>): DeckDiff {
    fun byKey(m: Map<String, Int>): Map<String, Pair<String, Int>> {
        val out = LinkedHashMap<String, Pair<String, Int>>()
        m.forEach { (name, n) ->
            if (n <= 0) return@forEach
            val key = name.trim().lowercase()
            val had = out[key]
            out[key] = if (had == null) name to n else had.first to had.second + n
        }
        return out
    }
    val a = byKey(here)
    val b = byKey(there)
    val byName = compareBy<CompareRow> { it.name.lowercase() }
    val onlyHere = a.filterKeys { it !in b }.values.map { CompareRow(it.first, it.second, 0) }.sortedWith(byName)
    val onlyThere = b.filterKeys { it !in a }.values.map { CompareRow(it.first, 0, it.second) }.sortedWith(byName)
    val both = a.filterKeys { it in b }.map { (key, v) -> CompareRow(v.first, v.second, b.getValue(key).second) }
        .sortedWith(compareBy<CompareRow> { it.sameCount }.then(byName))
    return DeckDiff(onlyHere, onlyThere, both)
}
