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
