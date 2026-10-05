package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.scryfall.ScryfallCard

// Draft and sealed (GameMode.LIMITED): a deck built from a pool of cards opened at the table. The
// pool is the deck's sideboard (Deck.sideboard, JSON key "sideboard"), so nothing new is synced:
// cards move between the pool and the 40-card main deck with the sideboard's own moves. Here: the
// pool sorted by colour, the colour pairs it supports best, and the basic lands to add. Plain
// functions so they can be tested. Mirrors the web app's src/decks/limited.ts.

/** A Limited deck's usual lands, for its 40 cards. */
const val LIMITED_LANDS = 17

private val COLOURS = listOf("W", "U", "B", "R", "G")

/** The pool's groups, in the order they're shown: each colour, then multicolour, colourless and lands. */
val POOL_GROUP_LABELS: Map<String, String> = linkedMapOf(
    "W" to "White", "U" to "Blue", "B" to "Black", "R" to "Red", "G" to "Green",
    "M" to "Multicolour", "C" to "Colourless", "L" to "Lands"
)

/** The basic land that makes each colour. */
val BASIC_LAND_FOR: Map<String, String> = linkedMapOf(
    "W" to "Plains", "U" to "Island", "B" to "Swamp", "R" to "Mountain", "G" to "Forest"
)

private val SYMBOL = Regex("\\{([^}]+)\\}")

/**
 * Coloured mana symbols in a mana cost, by colour: "{1}{W}{W}" is two white. A hybrid or Phyrexian
 * symbol counts towards each colour in it ("{W/U}" is one white and one blue), as the Stats tab's
 * mana symbols do.
 */
fun manaPips(cost: String?): Map<String, Int> {
    val pips = linkedMapOf<String, Int>()
    SYMBOL.findAll(cost.orEmpty()).forEach { m ->
        m.groupValues[1].uppercase().split("/").forEach { part ->
            if (part in COLOURS) pips[part] = (pips[part] ?: 0) + 1
        }
    }
    return pips
}

/** The card's mana cost — the front face's, for a card whose faces have their own. */
private fun ScryfallCard.costOf(): String? = manaCost?.takeIf { it.isNotEmpty() } ?: cardFaces?.firstOrNull()?.manaCost

/** Whether [entry] is a land: by the card when it's known, by the type line saved on the entry otherwise. */
private fun isLand(entry: DeckCardEntry, card: ScryfallCard?): Boolean =
    (card?.typeLine ?: entry.typeLine).orEmpty().substringBefore(" // ").contains("Land")

/**
 * A card's colours, in W U B R G order: Scryfall's colours, or for a card whose faces carry them
 * (a double-faced card), the colours of its front face's mana cost. None for an unknown card.
 */
fun cardColours(card: ScryfallCard?): List<String> {
    if (card == null) return emptyList()
    val own = card.colors ?: manaPips(card.costOf()).keys.toList()
    return COLOURS.filter { it in own }
}

/** Which group of the pool a card goes in (a key of [POOL_GROUP_LABELS]): a land, one colour, several, or none. */
fun poolGroupOf(entry: DeckCardEntry, card: ScryfallCard?): String {
    if (isLand(entry, card)) return "L"
    val colours = cardColours(card)
    return when {
        colours.isEmpty() -> "C"
        colours.size > 1 -> "M"
        else -> colours.single()
    }
}

/** One group of the pool: its key and name, its cards by name, and how many copies. */
data class PoolGroup(val key: String, val label: String, val cards: List<DeckCardEntry>, val count: Int)

/** The pool's cards by colour (see [POOL_GROUP_LABELS]), each group by name; empty groups left out. */
fun poolGroups(entries: List<DeckCardEntry>, cards: Map<String, ScryfallCard>): List<PoolGroup> =
    POOL_GROUP_LABELS.mapNotNull { (key, label) ->
        val inGroup = entries.filter { poolGroupOf(it, cards[it.scryfallId]) == key }.sortedBy { it.name.lowercase() }
        if (inGroup.isEmpty()) null else PoolGroup(key, label, inGroup, inGroup.sumOf { it.quantity })
    }

/** A two-colour pair ("WU", in W U B R G order) and the playable cards the pool has for it. */
data class ColourPair(val colours: String, val count: Int)

/**
 * The two-colour pairs the pool supports best: for each pair, the non-land cards (copies) whose
 * colours all fall within it — either colour, both, or none — most first; [top] of them, none with
 * nothing. Ties keep W U B R G order (WU, WB, WR, WG, UB…).
 */
fun strongestPairs(entries: List<DeckCardEntry>, cards: Map<String, ScryfallCard>, top: Int = 3): List<ColourPair> {
    val playable = entries
        .filterNot { isLand(it, cards[it.scryfallId]) }
        .map { cardColours(cards[it.scryfallId]) to it.quantity }
    val pairs = mutableListOf<ColourPair>()
    COLOURS.forEachIndexed { i, a ->
        COLOURS.drop(i + 1).forEach { b ->
            val count = playable.filter { (colours, _) -> colours.all { it == a || it == b } }.sumOf { it.second }
            pairs += ColourPair(a + b, count)
        }
    }
    return pairs.filter { it.count > 0 }.sortedByDescending { it.count }.take(top)
}

/** The coloured mana symbols across the main deck's non-land cards (copies counted), by colour. */
fun mainDeckPips(entries: List<DeckCardEntry>, cards: Map<String, ScryfallCard>): Map<String, Int> {
    val pips = linkedMapOf<String, Int>()
    entries.forEach { entry ->
        val card = cards[entry.scryfallId] ?: return@forEach
        if (isLand(entry, card)) return@forEach
        manaPips(card.costOf()).forEach { (c, n) -> pips[c] = (pips[c] ?: 0) + n * entry.quantity }
    }
    return pips
}

/** How many basics to add: 17 lands for 40 cards, less the lands the main deck already has. */
fun basicsWanted(entries: List<DeckCardEntry>, cards: Map<String, ScryfallCard>): Int {
    val lands = entries.filter { isLand(it, cards[it.scryfallId]) }.sumOf { it.quantity }
    return (LIMITED_LANDS - lands).coerceAtLeast(0)
}

/**
 * [total] basic lands split between the colours in [pips], in proportion to their symbols, with at
 * least one of each colour used (even if that goes over [total]). Colours left out have none; with
 * no symbols at all, nothing is suggested. Rounded so the counts add up to [total]: the colours
 * whose share was rounded down most get the lands left over.
 */
fun basicLandSplit(pips: Map<String, Int>, total: Int): Map<String, Int> {
    val used = COLOURS.filter { (pips[it] ?: 0) > 0 }
    if (total <= 0 || used.isEmpty()) return emptyMap()
    val sum = used.sumOf { pips.getValue(it) }
    val share = used.associateWith { total.toDouble() * pips.getValue(it) / sum }
    val counts = used.associateWith { maxOf(1, Math.floor(share.getValue(it)).toInt()) }.toMutableMap()
    fun added() = used.sumOf { counts.getValue(it) }
    fun gap(c: String) = share.getValue(c) - counts.getValue(c)
    // Short: one more to the colour furthest under its share. Over: one fewer from the furthest over.
    while (added() < total) {
        val c = used.reduce { best, x -> if (gap(x) > gap(best)) x else best }
        counts[c] = counts.getValue(c) + 1
    }
    while (added() > total) {
        val over = used.filter { counts.getValue(it) > 1 }
        if (over.isEmpty()) break
        val c = over.reduce { best, x -> if (gap(x) < gap(best)) x else best }
        counts[c] = counts.getValue(c) - 1
    }
    return used.associateWith { counts.getValue(it) }
}

/**
 * Every card the deck holds — its main deck and its pool — one row per printing, copies added
 * together. What "Add pool to a binder" copies once the event is over.
 */
fun poolCopies(deck: Deck): List<DeckCardEntry> {
    val rows = linkedMapOf<String, DeckCardEntry>()
    (deck.cards + deck.sideboard).forEach { e ->
        val had = rows[e.scryfallId]
        rows[e.scryfallId] = had?.copy(quantity = had.quantity + e.quantity) ?: e
    }
    return rows.values.toList()
}
