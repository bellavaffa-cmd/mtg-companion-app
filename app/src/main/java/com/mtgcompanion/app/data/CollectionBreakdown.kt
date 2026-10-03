package com.mtgcompanion.app.data

// Where the collection's value sits: by set, by colour, by rarity and by card type, and the cards
// worth the most. Every copy counts at the card's non-foil US dollar price, as the dashboard's total
// does; proxies aren't counted. Mirrors the web app's src/collection/breakdown.ts.

/** One owned card (a printing) and what's known about it. [usd]: one copy's price; null when there's none. */
data class BreakdownCard(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val copies: Int,
    val usd: Double?,
    val setCode: String = "",
    val setName: String = "",
    /** Colour identity as WUBRG letters; empty for a colourless card. */
    val colors: Set<Char> = emptySet(),
    val rarity: String = "",
    val typeLine: String = ""
) {
    val value: Double get() = (usd ?: 0.0) * copies
}

/** One part of a breakdown: its [label], what it's worth and how many copies are in it. */
data class Slice(val label: String, val usd: Double, val copies: Int)

data class CollectionBreakdown(
    val totalUsd: Double,
    val bySet: List<Slice>,
    val byColor: List<Slice>,
    val byRarity: List<Slice>,
    val byType: List<Slice>,
    /** The cards worth most, all copies counted, dearest first. */
    val mostValuable: List<BreakdownCard>
)

/** The colour buckets, in this order: one per colour, then two or more, then none. */
val COLOR_BUCKETS = listOf("White", "Blue", "Black", "Red", "Green", "Multicolor", "Colorless")

private val COLOR_NAMES = mapOf('W' to "White", 'U' to "Blue", 'B' to "Black", 'R' to "Red", 'G' to "Green")

/** A card's colour bucket: its one colour, Multicolor for two or more, Colorless for none. */
fun colorBucket(colors: Set<Char>): String {
    val known = colors.filter { it in COLOR_NAMES }
    return when (known.size) {
        0 -> "Colorless"
        1 -> COLOR_NAMES.getValue(known.first())
        else -> "Multicolor"
    }
}

/** Rarities, commonest first; anything else (special, bonus) is Other. */
val RARITY_BUCKETS = listOf("Common", "Uncommon", "Rare", "Mythic", "Other")

fun rarityBucket(rarity: String): String = when (rarity.lowercase()) {
    "common" -> "Common"
    "uncommon" -> "Uncommon"
    "rare" -> "Rare"
    "mythic" -> "Mythic"
    else -> "Other"
}

/** A card's main type, creatures first (an artifact creature is a creature), as the dashboard sorts them. */
fun typeBucket(typeLine: String): String =
    listOf("Creature", "Planeswalker", "Instant", "Sorcery", "Enchantment", "Artifact", "Battle", "Land")
        .firstOrNull { typeLine.contains(it, ignoreCase = true) } ?: "Other"

private fun slices(cards: List<BreakdownCard>, label: (BreakdownCard) -> String): List<Slice> =
    cards.groupBy(label).map { (l, cs) -> Slice(l, cs.sumOf { it.value }, cs.sumOf { it.copies }) }

/**
 * The breakdown of [cards] (cards with no copies are left out): sets by value, the [topSets] worth
 * most and the rest together as "Other sets"; colours, rarities and types in their usual order with
 * empty ones left out; and the [topCards] most valuable cards.
 */
fun collectionBreakdown(cards: List<BreakdownCard>, topSets: Int = 8, topCards: Int = 10): CollectionBreakdown {
    val owned = cards.filter { it.copies > 0 }
    val sets = slices(owned) { it.setName.ifBlank { it.setCode.uppercase().ifBlank { "Unknown set" } } }
        .sortedWith(compareByDescending<Slice> { it.usd }.thenByDescending { it.copies }.thenBy { it.label })
    val bySet = if (sets.size <= topSets + 1) sets else {
        val rest = sets.drop(topSets)
        sets.take(topSets) + Slice("Other sets", rest.sumOf { it.usd }, rest.sumOf { it.copies })
    }
    fun ordered(list: List<Slice>, order: List<String>) = list.sortedBy { order.indexOf(it.label).let { i -> if (i < 0) order.size else i } }
    return CollectionBreakdown(
        totalUsd = owned.sumOf { it.value },
        bySet = bySet,
        byColor = ordered(slices(owned) { colorBucket(it.colors) }, COLOR_BUCKETS),
        byRarity = ordered(slices(owned) { rarityBucket(it.rarity) }, RARITY_BUCKETS),
        byType = slices(owned) { typeBucket(it.typeLine) }.sortedWith(compareByDescending<Slice> { it.usd }.thenByDescending { it.copies }),
        mostValuable = owned.filter { it.usd != null && it.value > 0 }
            .sortedWith(compareByDescending<BreakdownCard> { it.value }.thenBy { it.name.lowercase() })
            .take(topCards)
    )
}
