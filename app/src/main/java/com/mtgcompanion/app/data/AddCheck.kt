package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.scryfall.ScryfallCard

// The check run before cards go into a deck's main deck or sideboard: is each card legal in the
// deck's format, inside its commander's colours, and within the copy limit? The answers are shown
// in a confirmation (ui/common/AddCheckDialog) — the user can still add a card that fails. The web
// app asks the same, in the same words.

/**
 * One card about to go into a deck: [quantity] copies, into the [sideboard] or the main deck.
 * [typeLine] is what's known of the card without a lookup (a deck entry keeps it), so a basic land
 * is still recognised offline.
 */
data class AddCandidate(
    val scryfallId: String,
    val name: String,
    val quantity: Int = 1,
    val sideboard: Boolean = false,
    val typeLine: String? = null
)

/** What's wrong with adding [name]: one line per problem, empty when it's allowed. */
data class AddCheckResult(val scryfallId: String, val name: String, val problems: List<String>) {
    val allowed: Boolean get() = problems.isEmpty()
}

/** How a card name is compared: copies of every printing count together. */
fun cardNameKey(name: String): String = name.trim().lowercase()

/**
 * How many copies of a card a [mode] deck may hold, main deck and sideboard together; null for no
 * limit — a basic land, or a card that says a deck can have any number of it (Relentless Rats).
 * A restricted card allows one. [card] may be null
 * when it couldn't be looked up: then only the name and [typeLine] say whether it's a basic.
 */
fun copyLimitOf(mode: GameMode, name: String, card: ScryfallCard?, typeLine: String? = null): Int? {
    if (isBasicLandName(name) || typeLine?.contains("Basic", ignoreCase = true) == true ||
        card?.typeLine?.contains("Basic", ignoreCase = true) == true
    ) return null
    val text = card?.let { c -> listOfNotNull(c.oracleText).plus(c.cardFaces.orEmpty().mapNotNull { it.oracleText }).joinToString("\n") }.orEmpty()
    if (text.contains("A deck can have any number of cards named", ignoreCase = true)) return null
    // Limited has no copy limit: a pool can hold several of a card.
    if (mode.limited) return null
    if (card?.legalities?.get(mode.scryfallFormat) == "restricted") return 1
    return if (mode.singleton) 1 else mode.maxCopies
}

/**
 * Checks putting [adding] into [deck], with [cards] (Scryfall id -> card) for legalities and colour
 * identity — the cards being added and the deck's commanders. A card missing from [cards] (it
 * couldn't be looked up, offline say) skips the format and colour checks; the copy limit is still
 * checked, by name. Copies count every printing, the main deck and the sideboard together, and the
 * cards earlier in [adding]. One result per entry of [adding], in its order. With [copiesOnly] (one
 * more copy of a card already in, from a "+": its format and colours were accepted when it went in)
 * only the copy limit is checked. Going into the sideboard, it may hold at most
 * [GameMode.MAX_SIDEBOARD] cards. With [moving] (from the main deck to the sideboard: the same cards,
 * so nothing about them changes) only the sideboard's size is checked.
 */
fun checkAdd(
    deck: Deck,
    adding: List<AddCandidate>,
    cards: Map<String, ScryfallCard>,
    copiesOnly: Boolean = false,
    moving: Boolean = false
): List<AddCheckResult> {
    val mode = deck.mode
    val label = mode.label

    // The commanders' combined colour identity — only for a format with a commander, a deck that
    // has one, and when every commander could be looked up.
    val commanders = listOfNotNull(deck.commander, deck.partnerCommander)
    val commanderCards = commanders.map { cards[it.scryfallId] }
    val identity: Set<String>? =
        if (!mode.usesCommander || commanders.isEmpty() || commanderCards.any { it?.colorIdentity == null }) null
        else commanderCards.flatMap { it!!.colorIdentity.orEmpty() }.toSet()
    val commanderNames = commanders.joinToString(" & ") { it.name }
    val commanderKeys = commanders.map { cardNameKey(it.name) }.toSet()

    val copies = mutableMapOf<String, Int>()
    (deck.cards + deck.sideboard).forEach { copies.merge(cardNameKey(it.name), it.quantity, Int::plus) }

    var sideboardCount = deck.sideboard.sumOf { it.quantity }
    val sideLimit = mode.sideboardLimit

    return adding.map { item ->
        val card = cards[item.scryfallId]
        val problems = mutableListOf<String>()
        if (item.sideboard && mode.hasSideboard) {
            sideboardCount += item.quantity.coerceAtLeast(1)
            if (sideLimit != null && sideboardCount > sideLimit) problems += "Sideboard is full ($sideLimit max)"
        }
        if (moving) return@map AddCheckResult(item.scryfallId, item.name, problems)
        val legality = card?.legalities?.get(mode.scryfallFormat)
        when {
            copiesOnly -> Unit
            legality == null || legality == "legal" || legality == "restricted" -> Unit
            legality == "banned" -> problems += "Banned in $label"
            else -> problems += "Not legal in $label"
        }
        if (!copiesOnly && identity != null && card != null && cardNameKey(item.name) !in commanderKeys) {
            val own = card.colorIdentity.orEmpty().toSet()
            if (!identity.containsAll(own)) problems += "Outside $commanderNames's colours"
        }
        val key = cardNameKey(item.name)
        val total = (copies[key] ?: 0) + item.quantity.coerceAtLeast(1)
        copies[key] = total
        val limit = copyLimitOf(mode, item.name, card, item.typeLine)
        if (limit != null && total > limit) {
            problems += when {
                legality == "restricted" -> "Restricted in $label"
                mode.singleton -> "Singleton: only 1 copy allowed in $label"
                else -> "Over the copy limit ($limit max)"
            }
        }
        AddCheckResult(item.scryfallId, item.name, problems)
    }
}

// ---- What the confirmation says ----

/** The most failing cards the confirmation lists by name before "and N more". */
const val ADD_CHECK_LISTED = 8

/** The confirmation's title: "Add Sol Ring anyway?", or "2 of these cards aren't allowed in Atraxa". */
fun addCheckTitle(results: List<AddCheckResult>, deckName: String): String {
    val failing = results.filterNot { it.allowed }
    return if (results.size == 1) "Add ${results.single().name} anyway?"
    else "${failing.size} of these cards aren't allowed in $deckName"
}

/**
 * The confirmation's lines: a single card's problems, one a line; for several cards, "Card:
 * problem" for the first [ADD_CHECK_LISTED] that fail, then "and N more".
 */
fun addCheckLines(results: List<AddCheckResult>): List<String> {
    if (results.size == 1) return results.single().problems
    val failing = results.filterNot { it.allowed }
    val lines = failing.take(ADD_CHECK_LISTED).map { "${it.name}: ${it.problems.joinToString("; ")}" }
    val more = failing.size - ADD_CHECK_LISTED
    return if (more > 0) lines + "and $more more" else lines
}

/** Whether to offer "Add only allowed": several cards, and at least one of them is allowed. */
fun addCheckOffersAllowedOnly(results: List<AddCheckResult>): Boolean =
    results.size > 1 && results.any { it.allowed }
