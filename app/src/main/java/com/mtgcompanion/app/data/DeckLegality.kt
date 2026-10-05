package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.scryfall.ScryfallCard

/** What kind of rule an issue broke — lets the UI offer a one-tap fix for the ones that have one. */
enum class LegalityIssueKind { DECK_SIZE, COMMANDER, LEGALITY, COPY_LIMIT, COLOR_IDENTITY, COMPANION }

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

/** Whether [name] is a basic land's (snow-covered and Wastes included). */
fun isBasicLandName(name: String): Boolean = name in BASIC_LAND_NAMES

private fun ScryfallCard?.isBasicLand(name: String): Boolean =
    name in BASIC_LAND_NAMES || this?.typeLine?.contains("Basic", ignoreCase = true) == true

/**
 * A commander as pairing sees it, its ability read from the card itself where [cards] has it: an
 * entry saved by an older version may lack a Friends forever or Background ability.
 */
private fun pairCardOf(entry: DeckCardEntry, cards: Map<String, ScryfallCard>): PairCard {
    val card = cards[entry.scryfallId] ?: return entry.pairCard
    return PairCard(card.name, card.typeLine ?: entry.typeLine, card.partnerAbility)
}

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
                if (!canPair(pairCardOf(deck.commander, cards), pairCardOf(partner, cards))) {
                    issues += LegalityIssue(
                        null, "${deck.commander.name} and ${partner.name} can't be commanders together.",
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

        // Format legality of the card itself. (Restricted cards' copy limit is checked below, with
        // every printing and the sideboard counted together.)
        when (card?.legalities?.get(format)) {
            "banned" -> issues += LegalityIssue(entry.name, "Banned in ${mode.label}.", kind = LegalityIssueKind.LEGALITY)
            "not_legal" -> issues += LegalityIssue(entry.name, "Not legal in ${mode.label}.", kind = LegalityIssueKind.LEGALITY)
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

    // The sideboard: only formats that have one, at most 15 cards (a Limited pool: any number), and
    // every card legal there too.
    val sideboardCount = deck.sideboard.sumOf { it.quantity }
    val sideLimit = mode.sideboardLimit
    // In Commander the companion waits outside the 100, where a sideboard would be: one copy of it is fine.
    val companionOutside = if (!mode.hasSideboard && companionEntry(deck) != null) 1 else 0
    if (sideboardCount - companionOutside > 0 && !mode.hasSideboard) {
        issues += LegalityIssue(
            null, "${mode.label} has no sideboard — $sideboardCount card${if (sideboardCount == 1) "" else "s"} still there.",
            kind = LegalityIssueKind.DECK_SIZE
        )
    } else if (sideLimit != null && sideboardCount > sideLimit) {
        issues += LegalityIssue(
            null, "Sideboard has $sideboardCount cards; ${mode.label} allows at most $sideLimit.",
            kind = LegalityIssueKind.DECK_SIZE
        )
    }
    deck.sideboard.forEach { entry ->
        when (cards[entry.scryfallId]?.legalities?.get(format)) {
            "banned" -> issues += LegalityIssue(entry.name, "Banned in ${mode.label} (sideboard).", kind = LegalityIssueKind.LEGALITY)
            "not_legal" -> issues += LegalityIssue(entry.name, "Not legal in ${mode.label} (sideboard).", kind = LegalityIssueKind.LEGALITY)
        }
    }

    issues += copyLimitIssues(deck, cards)
    issues += companionIssues(deck, cards, commanderIdentity)

    return LegalityReport(mode = mode, totalCards = totalCards, legal = issues.isEmpty(), issues = issues)
}

/**
 * Copy limits, with every printing of a card and its sideboard copies counted together (basics are
 * unlimited). The issue sits on the card's biggest main-deck row, and offers to cut that row down
 * when doing so is enough to fix it.
 */
private fun copyLimitIssues(deck: Deck, cards: Map<String, ScryfallCard>): List<LegalityIssue> {
    val mode = deck.mode
    val issues = mutableListOf<LegalityIssue>()
    val main = deck.cards.groupBy { it.name.trim().lowercase() }
    val side = deck.sideboard.groupBy { it.name.trim().lowercase() }
    for (key in (main.keys + side.keys).distinct()) {
        val mainRows = main[key].orEmpty()
        val sideRows = side[key].orEmpty()
        val first = mainRows.firstOrNull() ?: sideRows.first()
        val card = (mainRows + sideRows).firstNotNullOfOrNull { cards[it.scryfallId] }
        if (card.isBasicLand(first.name)) continue
        val restricted = (mainRows + sideRows).any { cards[it.scryfallId]?.legalities?.get(mode.scryfallFormat) == "restricted" }
        // No limit for a card a deck can have any number of (Relentless Rats).
        val limit = (if (restricted) 1 else copyLimitOf(mode, first.name, card, first.typeLine)) ?: continue
        val inMain = mainRows.sumOf { it.quantity }
        val inSide = sideRows.sumOf { it.quantity }
        val total = inMain + inSide
        if (total <= limit) continue
        val has = if (inSide > 0) "has $inMain + $inSide in the sideboard" else "has $total"
        val reason = when {
            restricted -> "Restricted in ${mode.label} — max 1 copy ($has)."
            mode.singleton -> "${mode.label} is singleton — only 1 copy allowed ($has)."
            else -> "Max ${mode.maxCopies} copies allowed ($has)."
        }
        val row = mainRows.maxByOrNull { it.quantity }
        val fix = row?.let { it.quantity - (total - limit) }?.takeIf { it >= 1 }
        issues += LegalityIssue(
            first.name, reason, kind = LegalityIssueKind.COPY_LIMIT,
            scryfallId = row?.scryfallId,
            fixQuantity = fix
        )
    }
    return issues
}

/**
 * The companion: a companion card, in the sideboard (outside the 100 in Commander, inside the
 * commander's colours), whose condition the starting deck — commanders included — meets. The web
 * app's companionIssues (src/decks/deckLegality.ts), in the same words.
 */
fun companionIssues(deck: Deck, cards: Map<String, ScryfallCard>, commanderIdentity: Set<String>? = null): List<LegalityIssue> {
    val named = deck.companion?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
    val companion = companionNamed(named) ?: return listOf(LegalityIssue(named, "Isn't a companion.", kind = LegalityIssueKind.COMPANION))
    val issues = mutableListOf<LegalityIssue>()
    val entry = companionEntry(deck)
    if (entry == null) {
        issues += LegalityIssue(
            companion.name,
            if (deck.mode.hasSideboard) "The companion isn't in the sideboard." else "The companion isn't with the deck — add it again from Details.",
            kind = LegalityIssueKind.COMPANION
        )
    } else if (commanderIdentity != null) {
        val outside = cards[entry.scryfallId]?.colorIdentity.orEmpty().filter { it !in commanderIdentity }
        if (outside.isNotEmpty()) {
            issues += LegalityIssue(companion.name, "Outside the commander's colour identity (${outside.joinToString("")}).", kind = LegalityIssueKind.COLOR_IDENTITY)
        }
    }
    val result = checkCompanion(companion.name, deck.cards.map { companionCard(it, cards[it.scryfallId]) }, deck.mode.deckSize)
    if (!result.met) issues += LegalityIssue(companion.name, companionReason(companion, result), kind = LegalityIssueKind.COMPANION)
    return issues
}
