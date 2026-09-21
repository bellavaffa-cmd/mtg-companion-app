package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.scryfall.ScryfallCard

/**
 * Switching a card to a different printing — another art, another set — wherever it sits: a
 * binder, a deck, or everywhere the collection holds it. What changes is which card it is and what
 * it looks like; how many there are, which are foil, a price alert, and which copies are proxies
 * all stay. Mirrors the web app's src/collection/printings.ts.
 *
 * If the printing chosen is one the binder or deck already holds, the two become one entry with
 * the copies added together. Switching used to leave two entries for the same printing, each
 * showing half the count.
 */

/** [entries] with the copies of [oldId] moved to [card]'s printing. The same list when there's nothing to change. */
fun withEntryPrinting(entries: List<CollectionEntry>, oldId: String, card: ScryfallCard): List<CollectionEntry> {
    if (oldId == card.id) return entries
    val old = entries.firstOrNull { it.scryfallId == oldId } ?: return entries
    if (entries.any { it.scryfallId == card.id }) {
        return entries
            .filter { it.scryfallId != oldId }
            .map {
                if (it.scryfallId != card.id) it
                else it.copy(quantity = it.quantity + old.quantity, foilQuantity = it.foilQuantity + old.foilQuantity)
            }
    }
    return entries.map {
        if (it.scryfallId != oldId) it
        else it.copy(scryfallId = card.id, name = card.name, imageUrl = card.displayImageUrl, backImageUrl = card.backImageUrl, tags = card.tags)
    }
}

/**
 * The regular version among one set's printings of a card: the lowest collector number, since the
 * special versions — borderless, showcase, extended art — are numbered after the set's main run.
 * Numbers with no digits ("★") come last.
 */
fun regularInSet(printings: List<ScryfallCard>): ScryfallCard? =
    printings.minWithOrNull(compareBy<ScryfallCard> {
        it.collectorNumber?.takeWhile { c -> c.isDigit() }?.toIntOrNull() ?: Int.MAX_VALUE
    }.thenBy { it.collectorNumber.orEmpty() })

/** A deck entry turned into [card]'s printing, everything about the copies kept. */
private fun DeckCardEntry.retargeted(card: ScryfallCard) = copy(
    scryfallId = card.id,
    name = card.name,
    imageUrl = card.displayImageUrl,
    typeLine = card.typeLine ?: typeLine,
    partnerAbility = card.partnerAbility,
    backImageUrl = card.backImageUrl,
    tags = card.tags
)

/** [deck] with [oldId] switched to [card]'s printing — its cards and, if it's one, its commander. The same deck when there's nothing to change. */
fun withDeckPrinting(deck: Deck, oldId: String, card: ScryfallCard): Deck {
    if (oldId == card.id) return deck
    val old = deck.cards.firstOrNull { it.scryfallId == oldId }
    val isCommander = deck.commander?.scryfallId == oldId || deck.partnerCommander?.scryfallId == oldId
    if (old == null && !isCommander) return deck

    val already = deck.cards.firstOrNull { it.scryfallId == card.id }
    val cards = when {
        old != null && already != null -> {
            // One entry, the copies added together. Proxies are counted out of each before they're
            // joined: "unset" means something different on its own than it does beside a number.
            val bothUnset = old.proxyQuantity == null && already.proxyQuantity == null
            val merged = already.copy(
                quantity = already.quantity + old.quantity,
                proxyQuantity = if (bothUnset) null else proxyCopies(deck, already) + proxyCopies(deck, old)
            )
            deck.cards.filter { it.scryfallId != oldId }.map { if (it.scryfallId == card.id) merged else it }
        }
        old != null -> deck.cards.map { if (it.scryfallId == oldId) it.retargeted(card) else it }
        else -> deck.cards
    }
    return deck.copy(
        cards = cards,
        commander = deck.commander?.let { if (it.scryfallId == oldId) it.retargeted(card) else it },
        partnerCommander = deck.partnerCommander?.let { if (it.scryfallId == oldId) it.retargeted(card) else it }
    )
}
