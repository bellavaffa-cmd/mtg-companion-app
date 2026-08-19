package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.scryfall.ScryfallCard

/** What kind of rule an issue broke — lets the UI offer a one-tap fix for the ones that have one. */
enum class LegalityIssueKind { DECK_SIZE, COMMANDER, LEGALITY, COPY_LIMIT, COLOR_IDENTITY }

/**
 * A single problem found while checking a deck against its format's rules. [scryfallId] +
 * [fixQuantity] are set only for [LegalityIssueKind.COPY_LIMIT] issues — tapping such an issue can
 * set the card's quantity straight to [fixQuantity] to resolve it.
 */
data class LegalityIssue(
    val card: String?,
    val reason: String,
    val kind: LegalityIssueKind = LegalityIssueKind.LEGALITY,
    val scryfallId: String? = null,
    val fixQuantity: Int? = null
)

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

    // Commander requirement + colour identity source (union of both, if a partner is set).
    val commanderIdentity: Set<String>? = if (mode.usesCommander) {
        if (deck.commander == null) {
            issues += LegalityIssue(null, "No commander set — ${mode.label} needs a commander.", kind = LegalityIssueKind.COMMANDER)
            null
        } else {
            val mainIdentity = cards[deck.commander.scryfallId]?.colorIdentity?.toSet() ?: emptySet()
            val partner = deck.partnerCommander
            if (partner != null) {
                if (!partnersWith(deck.commander, partner)) {
                    issues += LegalityIssue(
                        null, "${deck.commander.name} and ${partner.name} don't have a valid Partner pairing.",
                        kind = LegalityIssueKind.COMMANDER
                    )
                }
                mainIdentity + (cards[partner.scryfallId]?.colorIdentity?.toSet() ?: emptySet())
            } else {
                mainIdentity
            }
        }
    } else null

    // Deck size.
    if (mode.exactSize) {
        if (totalCards != mode.deckSize) {
            issues += LegalityIssue(
                null, "Deck has $totalCards cards; ${mode.label} requires exactly ${mode.deckSize}.",
                kind = LegalityIssueKind.DECK_SIZE
            )
        }
    } else if (totalCards < mode.deckSize) {
        issues += LegalityIssue(
            null, "Deck has $totalCards cards; ${mode.label} requires at least ${mode.deckSize}.",
            kind = LegalityIssueKind.DECK_SIZE
        )
    }

    deck.cards.forEach { entry ->
        val card = cards[entry.scryfallId]
        val basic = card.isBasicLand(entry.name)

        // Format legality of the card itself.
        when (card?.legalities?.get(format)) {
            "banned" -> issues += LegalityIssue(entry.name, "Banned in ${mode.label}.", kind = LegalityIssueKind.LEGALITY)
            "not_legal" -> issues += LegalityIssue(entry.name, "Not legal in ${mode.label}.", kind = LegalityIssueKind.LEGALITY)
            "restricted" -> if (entry.quantity > 1) {
                issues += LegalityIssue(
                    entry.name, "Restricted in ${mode.label} — max 1 copy (has ${entry.quantity}).",
                    kind = LegalityIssueKind.COPY_LIMIT, scryfallId = entry.scryfallId, fixQuantity = 1
                )
            }
        }

        // Copy limits (basics are unlimited).
        if (!basic) {
            if (mode.singleton) {
                if (entry.quantity > 1) {
                    issues += LegalityIssue(
                        entry.name, "${mode.label} is singleton — only 1 copy allowed (has ${entry.quantity}).",
                        kind = LegalityIssueKind.COPY_LIMIT, scryfallId = entry.scryfallId, fixQuantity = 1
                    )
                }
            } else if (entry.quantity > mode.maxCopies) {
                issues += LegalityIssue(
                    entry.name, "Max ${mode.maxCopies} copies allowed (has ${entry.quantity}).",
                    kind = LegalityIssueKind.COPY_LIMIT, scryfallId = entry.scryfallId, fixQuantity = mode.maxCopies
                )
            }
        }

        // Commander colour identity.
        val isCommanderCard = entry.scryfallId == deck.commander?.scryfallId || entry.scryfallId == deck.partnerCommander?.scryfallId
        if (commanderIdentity != null && card != null && !isCommanderCard) {
            val cardIdentity = card.colorIdentity?.toSet() ?: emptySet()
            if (!commanderIdentity.containsAll(cardIdentity)) {
                val outside = (cardIdentity - commanderIdentity).joinToString("")
                issues += LegalityIssue(
                    entry.name, "Outside the commander's colour identity ($outside).",
                    kind = LegalityIssueKind.COLOR_IDENTITY, scryfallId = entry.scryfallId
                )
            }
        }
    }

    return LegalityReport(mode = mode, totalCards = totalCards, legal = issues.isEmpty(), issues = issues)
}

/**
 * Null if adding [addingQuantity] more cop(ies) of [card] to [deck] stays within its format's copy
 * limit; otherwise a short warning to show the user (the card is still added — this is informational,
 * not a block, since testing/sideboard scenarios are legitimate). Basic lands are always unlimited.
 */
fun duplicateWarning(deck: Deck, card: ScryfallCard, addingQuantity: Int = 1): String? {
    val mode = deck.mode
    if (card.isBasicLand(card.name)) return null
    val existingQuantity = deck.cards.find { it.scryfallId == card.id }?.quantity ?: 0
    val newQuantity = existingQuantity + addingQuantity
    return when {
        mode.singleton && newQuantity > 1 ->
            "${mode.label} is singleton — you'll have $newQuantity copies of \"${card.name}\"."
        !mode.singleton && newQuantity > mode.maxCopies ->
            "Max ${mode.maxCopies} copies allowed in ${mode.label} — you'll have $newQuantity of \"${card.name}\"."
        else -> null
    }
}
