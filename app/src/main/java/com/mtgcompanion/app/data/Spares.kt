package com.mtgcompanion.app.data

/**
 * Spares: cards sitting in the user's binders that none of their decks play. They're the obvious
 * things to trade away or sell — a deck's own copies live in the deck, so a binder copy is only
 * spoken for if a deck is short of it or thinking about it.
 * Mirrors the web app's src/collection/spares.ts.
 */

private fun key(name: String) = name.trim().lowercase()

/** Every card name the decks play, are short of, or are considering — the ones to hold on to. */
fun namesDecksUse(decks: List<Deck>): Set<String> =
    decks.flatMap { deck ->
        (listOfNotNull(deck.commander, deck.partnerCommander) + deck.cards + deck.considering).map { key(it.name) }
    }.toSet()

/** A card the user could let go of: where it sits, and how many they have. */
data class Spare(
    /** The first printing found, for the picture and for trading. */
    val entry: CollectionEntry,
    /** Binders holding it, in the order they were looked at. */
    val binders: List<String>,
    val copies: Int,
    val foils: Int
)

/**
 * The cards in the binders that no deck uses, most copies first. Wishlists are cards the user
 * wants, so they're left out, and [minCopies] can ask for only the ones held several of.
 */
fun spares(collections: List<Collection>, decks: List<Deck>, minCopies: Int = 1): List<Spare> {
    val used = namesDecksUse(decks)
    val byName = LinkedHashMap<String, Spare>()
    collections.filter { it.kind != CollectionType.WISHLIST }.forEach { collection ->
        collection.entries.forEach { entry ->
            val copies = entry.quantity + entry.foilQuantity
            if (copies <= 0 || key(entry.name) in used) return@forEach
            val had = byName[key(entry.name)]
            byName[key(entry.name)] = if (had == null) {
                Spare(entry, listOf(collection.name), copies, entry.foilQuantity)
            } else {
                had.copy(
                    binders = if (collection.name in had.binders) had.binders else had.binders + collection.name,
                    copies = had.copies + copies,
                    foils = had.foils + entry.foilQuantity
                )
            }
        }
    }
    return byName.values
        .filter { it.copies >= minCopies }
        .sortedWith(compareByDescending<Spare> { it.copies }.thenBy { it.entry.name })
}

/** What a spare is worth, for sorting the most valuable to the top. */
fun spareValue(spare: Spare, prices: Map<String, Double>): Double =
    (prices[spare.entry.scryfallId] ?: 0.0) * spare.copies
