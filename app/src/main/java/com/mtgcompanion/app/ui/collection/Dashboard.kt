package com.mtgcompanion.app.ui.collection

import com.mtgcompanion.app.data.CardRepository

/** Aggregate stats over a set of owned cards, for a dashboard. */
data class CollectionDashboard(
    val totalUsd: Double,
    val pricedCount: Int,
    // Ordered W, U, B, R, G, Colorless — copies contributing to each colour identity.
    val colorCounts: List<Pair<String, Int>>,
    // Card-type category -> total copies, sorted by count desc.
    val typeCounts: List<Pair<String, Int>>
)

/**
 * Fetch full card data (price/colour/type) for [quantities] (scryfallId -> total copies) and roll
 * it up into a [CollectionDashboard]. Returns null if empty or nothing could be fetched.
 */
suspend fun computeDashboard(
    cardRepository: CardRepository,
    quantities: List<Pair<String, Int>>
): CollectionDashboard? {
    if (quantities.isEmpty()) return null
    val cardsById = cardRepository.getCardsByIds(quantities.map { it.first }).associateBy { it.id }
    if (cardsById.isEmpty()) return null

    var totalUsd = 0.0
    var pricedCount = 0
    val colorTotals = linkedMapOf("W" to 0, "U" to 0, "B" to 0, "R" to 0, "G" to 0, "Colorless" to 0)
    val typeTotals = LinkedHashMap<String, Int>()

    quantities.forEach { (id, qty) ->
        val card = cardsById[id] ?: return@forEach

        card.prices?.usd?.toDoubleOrNull()?.let {
            totalUsd += it * qty
            pricedCount += qty
        }

        val identity = card.colorIdentity ?: card.colors
        if (identity.isNullOrEmpty()) {
            colorTotals["Colorless"] = (colorTotals["Colorless"] ?: 0) + qty
        } else {
            identity.forEach { c -> colorTotals[c]?.let { colorTotals[c] = it + qty } }
        }

        val type = primaryType(card.typeLine)
        typeTotals[type] = (typeTotals[type] ?: 0) + qty
    }

    return CollectionDashboard(
        totalUsd = totalUsd,
        pricedCount = pricedCount,
        colorCounts = colorTotals.entries.filter { it.value > 0 }.map { it.key to it.value },
        typeCounts = typeTotals.entries.sortedByDescending { it.value }.map { it.key to it.value }
    )
}

/** Coarse card-type category from a Scryfall type line, creatures taking priority over other types. */
private fun primaryType(typeLine: String?): String {
    val line = typeLine ?: return "Other"
    val order = listOf("Creature", "Planeswalker", "Instant", "Sorcery", "Enchantment", "Artifact", "Battle", "Land")
    return order.firstOrNull { line.contains(it, ignoreCase = true) } ?: "Other"
}
