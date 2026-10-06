package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.scryfall.ScryfallPart

/**
 * What to bring besides the deck: the tokens and emblems its cards make.
 *
 * Scryfall prints this on the cards themselves — a card's allParts lists everything published
 * alongside it, each tagged with what it is. We keep the tokens, which is what a player has to have
 * in the box, and say which cards ask for each one, since that's how you check you haven't missed
 * any. Meld halves and combo pieces are real cards, not things to bring, so they're left out.
 *
 * A token is identified by what's printed on it rather than by its Scryfall id: the same 1/1 white
 * Soldier is a different id in every set, and a player needs one of it, not six.
 *
 * The web app's src/decks/tokens.ts makes the same decisions.
 */
data class TokenNeeded(
    /** A Scryfall id for one printing of it — enough to fetch the art. */
    val id: String,
    val name: String,
    val typeLine: String?,
    /** The cards in the deck that make it, by name, in the order they were found. */
    val madeBy: List<String>,
    /** Emblems and the like: still worth bringing, but not a creature token. */
    val isEmblem: Boolean
)

/** Two tokens are the same token when they'd be the same piece of cardboard. */
private fun sameness(part: ScryfallPart) =
    "${part.name.trim().lowercase()}|${part.typeLine.orEmpty().trim().lowercase()}"

private fun isEmblem(part: ScryfallPart) = part.typeLine.orEmpty().lowercase().contains("emblem")

/**
 * Every token [deck]'s cards make, with the cards that make each. [cardsById] is what the deck
 * screen already holds; cards still loading are simply not counted yet.
 */
fun tokensNeeded(deck: Deck, cardsById: Map<String, ScryfallCard>): List<TokenNeeded> {
    val entries = listOfNotNull(deck.commander, deck.partnerCommander) + deck.cards
    val found = LinkedHashMap<String, TokenNeeded>()
    for (entry in entries) {
        val card = cardsById[entry.scryfallId] ?: continue
        for (part in card.allParts.orEmpty()) {
            // 'token' covers emblems and dungeons too; meld halves and combo pieces are cards you'd own.
            if (part.component != "token") continue
            val key = sameness(part)
            val already = found[key]
            if (already != null) {
                if (entry.name !in already.madeBy) found[key] = already.copy(madeBy = already.madeBy + entry.name)
            } else {
                found[key] = TokenNeeded(part.id, part.name, part.typeLine, listOf(entry.name), isEmblem(part))
            }
        }
    }
    // Tokens first, then emblems; within each, the ones most cards ask for.
    return found.values.sortedWith(
        compareBy<TokenNeeded> { it.isEmblem }
            .thenByDescending { it.madeBy.size }
            .thenBy { it.name.lowercase() }
    )
}

/** "Llanowar Elves" / "Captain and 2 more" — what to say under a token. */
fun madeByLabel(token: TokenNeeded): String = when (token.madeBy.size) {
    1 -> token.madeBy[0]
    2 -> "${token.madeBy[0]} and ${token.madeBy[1]}"
    else -> "${token.madeBy[0]} and ${token.madeBy.size - 1} more"
}

// ---- How many to bring, and the counters ----

/** "Create X …" counts as this many. */
const val X_TOKENS = 10
/** No token is worth bringing more of than this. */
const val MAX_TOKENS = 30

private val NUMBER_WORDS = mapOf(
    "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8,
    "nine" to 9, "ten" to 10, "x" to X_TOKENS
)
private val CREATE_HOW_MANY = Regex("\\bcreates? (an?|one|two|three|four|five|six|seven|eight|nine|ten|x|\\d+)\\b", RegexOption.IGNORE_CASE)

/** How many tokens a card's text makes at once: "create two" is 2, "create X" is [X_TOKENS], and 1 when it doesn't say. */
fun makesHowMany(text: String): Int {
    val word = CREATE_HOW_MANY.find(text)?.groupValues?.get(1)?.lowercase() ?: return 1
    val n = word.toIntOrNull()
    return if (n != null && n > 0) n else NUMBER_WORDS[word] ?: 1
}

/** A card's rules text, every face of it. */
fun rulesTextOf(card: ScryfallCard): String =
    (listOf(card.oracleText.orEmpty()) + card.cardFaces.orEmpty().map { it.oracleText.orEmpty() }).joinToString("\n")

/** A token to bring for a deck, and how many: what each card making it makes at once, added up (1 to [MAX_TOKENS]). */
data class TokenToBring(val name: String, val count: Int, val madeBy: List<String>)

/** The tokens (not emblems) to bring for [deck], most-made first, with how many of each. */
fun tokensToBring(deck: Deck, cardsById: Map<String, ScryfallCard>): List<TokenToBring> {
    val textByName = LinkedHashMap<String, String>()
    for (e in listOfNotNull(deck.commander, deck.partnerCommander) + deck.cards) {
        val card = cardsById[e.scryfallId] ?: continue
        if (e.name !in textByName) textByName[e.name] = rulesTextOf(card)
    }
    return tokensNeeded(deck, cardsById).filter { !it.isEmblem }.map { t ->
        TokenToBring(t.name, t.madeBy.sumOf { makesHowMany(textByName[it].orEmpty()) }.coerceIn(1, MAX_TOKENS), t.madeBy)
    }
}

/** The counters worth bringing, in the order they're checked; "+1/+1" and "-1/-1" as printed. */
val COUNTER_KINDS = listOf(
    "+1/+1", "-1/-1", "poison", "energy", "experience", "loyalty", "charge", "oil", "shield", "stun", "lore", "time", "quest", "age", "level",
    "rad", "ticket", "finality", "bounty", "blood", "corpse", "divinity", "doom", "fade", "fate", "flood", "fungus", "ice", "incarnation",
    "ki", "luck", "omen", "page", "plague", "slime", "spore", "study", "tide", "training", "verse", "vitality", "void", "wish"
)
private val COUNTER_PATTERNS = COUNTER_KINDS.associateWith { Regex("(^|[^a-z0-9+/-])${Regex.escape(it)} counters?\\b") }

/** Which counters a card's text asks for: "poison counter", infect and toxic for poison, {E} for energy… */
fun countersOn(card: ScryfallCard): List<String> {
    val text = rulesTextOf(card).lowercase()
    val keywords = card.keywords.orEmpty().map { it.lowercase() }
    return COUNTER_KINDS.filter { kind ->
        when {
            // Planeswalkers come with their loyalty; only another card adding some counts.
            kind == "loyalty" -> "loyalty counter" in text && "planeswalker" !in card.typeLine.orEmpty().lowercase()
            kind == "poison" && listOf("infect", "toxic", "poisonous").any { it in keywords } -> true
            kind == "energy" && "{e}" in text -> true
            else -> COUNTER_PATTERNS.getValue(kind).containsMatchIn(text)
        }
    }
}

/** The counters [deck]'s cards ask for, the ones most cards ask for first. */
fun countersNeeded(deck: Deck, cardsById: Map<String, ScryfallCard>): List<String> {
    val counts = LinkedHashMap<String, Int>()
    val seen = mutableSetOf<String>()
    for (e in listOfNotNull(deck.commander, deck.partnerCommander) + deck.cards) {
        val card = cardsById[e.scryfallId] ?: continue
        if (!seen.add(card.id)) continue
        for (kind in countersOn(card)) counts[kind] = (counts[kind] ?: 0) + 1
    }
    return counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { COUNTER_KINDS.indexOf(it.key) }).map { it.key }
}

/** "Poison and +1/+1 counters", "Charge counters". */
fun countersLabel(kinds: List<String>): String {
    val names = kinds.mapIndexed { i, k -> if (i == 0) k.replaceFirstChar { it.uppercase() } else k }
    val list = if (names.size <= 1) names.firstOrNull().orEmpty() else "${names.dropLast(1).joinToString(", ")} and ${names.last()}"
    return "$list counters"
}
