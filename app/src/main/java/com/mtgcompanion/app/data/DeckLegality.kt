package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.scryfall.ScryfallCard

/** A single problem found while checking a deck against its format's rules. */
data class LegalityIssue(val card: String?, val reason: String)

data class LegalityReport(
    val mode: GameMode,
    val totalCards: Int,
    val legal: Boolean,
    val issues: List<LegalityIssue>
)

private val BASIC_LAND_NAMES = setOf(
    "Plains", "Island", "Swamp", "Mountain", "Forest", "Wastes",
    "Snow-Covered Plains", "Snow-Covered Island", "Snow-Covered Swamp",
    "Snow-Covered Mountain", "Snow-Covered Forest"
)

private fun ScryfallCard?.isBasicLand(name: String): Boolean =
    name in BASIC_LAND_NAMES || this?.typeLine?.contains("Basic", ignoreCase = true) == true

/**
 * Check [deck] against the construction rules of its game mode, using [cards] (a Scryfall id ->
 * card map) for legalities, types and colour identity. Cards missing from [cards] are skipped for
 * per-card checks but still counted toward deck size.
 */
fun evaluateLegality(deck: Deck, cards: Map<String, ScryfallCard>): LegalityReport {
    val mode = deck.mode
    val format = mode.scryfallFormat
    val issues = mutableListOf<LegalityIssue>()
    val totalCards = deck.cards.sumOf { it.quantity }

    // Commander requirement + colour identity source.
    val commanderIdentity: Set<String>? = if (mode.usesCommander) {
        if (deck.commander == null) {
            issues += LegalityIssue(null, "No commander set — ${mode.label} needs a commander.")
            null
        } else {
            cards[deck.commander.scryfallId]?.colorIdentity?.toSet() ?: emptySet()
        }
    } else null

    // Deck size.
    if (mode.exactSize) {
        if (totalCards != mode.deckSize) {
            issues += LegalityIssue(null, "Deck has $totalCards cards; ${mode.label} requires exactly ${mode.deckSize}.")
        }
    } else if (totalCards < mode.deckSize) {
        issues += LegalityIssue(null, "Deck has $totalCards cards; ${mode.label} requires at least ${mode.deckSize}.")
    }

    deck.cards.forEach { entry ->
        val card = cards[entry.scryfallId]
        val basic = card.isBasicLand(entry.name)

        // Format legality of the card itself.
        when (card?.legalities?.get(format)) {
            "banned" -> issues += LegalityIssue(entry.name, "Banned in ${mode.label}.")
            "not_legal" -> issues += LegalityIssue(entry.name, "Not legal in ${mode.label}.")
            "restricted" -> if (entry.quantity > 1) {
                issues += LegalityIssue(entry.name, "Restricted in ${mode.label} — max 1 copy (has ${entry.quantity}).")
            }
        }

        // Copy limits (basics are unlimited).
        if (!basic) {
            if (mode.singleton) {
                if (entry.quantity > 1) {
                    issues += LegalityIssue(entry.name, "${mode.label} is singleton — only 1 copy allowed (has ${entry.quantity}).")
                }
            } else if (entry.quantity > mode.maxCopies) {
                issues += LegalityIssue(entry.name, "Max ${mode.maxCopies} copies allowed (has ${entry.quantity}).")
            }
        }

        // Commander colour identity.
        if (commanderIdentity != null && card != null && entry.scryfallId != deck.commander?.scryfallId) {
            val cardIdentity = card.colorIdentity?.toSet() ?: emptySet()
            if (!commanderIdentity.containsAll(cardIdentity)) {
                val outside = (cardIdentity - commanderIdentity).joinToString("")
                issues += LegalityIssue(entry.name, "Outside the commander's colour identity ($outside).")
            }
        }
    }

    return LegalityReport(mode = mode, totalCards = totalCards, legal = issues.isEmpty(), issues = issues)
}
