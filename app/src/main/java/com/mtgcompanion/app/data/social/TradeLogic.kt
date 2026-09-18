package com.mtgcompanion.app.data.social

import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionEntry

// What an accepted trade does to one side's binders: the cards they hand over come out of the
// binders they were in, and the cards they get go into the binder they choose. Each side applies
// only their own half, on their own device — nobody's app ever writes into someone else's library.
// Mirrors the web app's social/tradeLogic.ts.

data class CollectionChange(
    val collectionId: String,
    val card: TradeCard,
    /** Change in regular copies (negative: taken out). */
    val quantity: Int,
    /** Change in foil copies. */
    val foilQuantity: Int
)

data class TradeSides(val give: List<TradeCard>, val get: List<TradeCard>, val other: String)

/** The user's side of [trade]: what they give, what they get, and who with. */
fun tradeSides(trade: Trade, me: String): TradeSides =
    if (trade.fromUser == me) TradeSides(trade.give, trade.want, trade.toUser)
    else TradeSides(trade.want, trade.give, trade.fromUser)

/** Whether the user still has to update their binders for [trade]. */
fun awaitingMyUpdate(trade: Trade, me: String): Boolean =
    trade.status == TradeStatus.ACCEPTED && if (trade.fromUser == me) !trade.fromApplied else !trade.toApplied

/** Whether [trade] waits on the user: an answer, or updating their binders. */
fun waitingOnMe(trade: Trade, me: String): Boolean =
    (trade.status == TradeStatus.OPEN && trade.toUser == me) || awaitingMyUpdate(trade, me)

/**
 * The binder changes for the user's side of an accepted [trade]: each card given comes out of the
 * binder it was offered from (or [fallbackFrom]), and each card received goes into [receiveInto].
 */
fun tradeChanges(trade: Trade, me: String, receiveInto: String, fallbackFrom: String?): List<CollectionChange> {
    val sides = tradeSides(trade, me)
    val out = mutableListOf<CollectionChange>()
    for (c in sides.give) {
        val from = c.collectionId ?: fallbackFrom ?: continue
        out += CollectionChange(from, c, if (c.foil) 0 else -c.quantity, if (c.foil) -c.quantity else 0)
    }
    for (c in sides.get) {
        out += CollectionChange(receiveInto, c, if (c.foil) 0 else c.quantity, if (c.foil) c.quantity else 0)
    }
    return out
}

data class ApplyResult(val collections: List<Collection>, val short: List<CollectionChange>)

/**
 * Applies [changes] to [collections]. Copies taken out never go below zero, and a card with no
 * copies left leaves the binder. [ApplyResult.short] lists the cards that couldn't be taken out in
 * full (no longer in that binder, or fewer copies than the trade says).
 */
fun applyCollectionChanges(collections: List<Collection>, changes: List<CollectionChange>): ApplyResult {
    val short = mutableListOf<CollectionChange>()
    val next = collections.map { collection ->
        val mine = changes.filter { it.collectionId == collection.id }
        if (mine.isEmpty()) return@map collection
        var entries = collection.entries
        for (ch in mine) {
            val existing = entries.firstOrNull { it.scryfallId == ch.card.scryfallId }
            val quantity = (existing?.quantity ?: 0) + ch.quantity
            val foil = (existing?.foilQuantity ?: 0) + ch.foilQuantity
            if (quantity < 0 || foil < 0) short += ch
            val q = quantity.coerceAtLeast(0)
            val f = foil.coerceAtLeast(0)
            entries = when {
                existing != null && q == 0 && f == 0 -> entries.filterNot { it.scryfallId == ch.card.scryfallId }
                existing != null -> entries.map { if (it.scryfallId == ch.card.scryfallId) it.copy(quantity = q, foilQuantity = f) else it }
                q > 0 || f > 0 -> entries + CollectionEntry(ch.card.scryfallId, ch.card.name, ch.card.imageUrl, q, f)
                else -> entries
            }
        }
        collection.copy(entries = entries)
    }
    for (ch in changes) {
        if (collections.none { it.id == ch.collectionId } && (ch.quantity < 0 || ch.foilQuantity < 0)) short += ch
    }
    return ApplyResult(next, short)
}

/** How many cards a list holds, counting copies. */
fun List<TradeCard>.cardTotal(): Int = sumOf { it.quantity }

/** Sets how many of [card] are in the list (0 removes it). */
fun List<TradeCard>.withQuantity(card: TradeCard, quantity: Int): List<TradeCard> {
    val rest = filterNot { it.key == card.key }
    if (quantity <= 0) return rest
    val i = indexOfFirst { it.key == card.key }
    val next = card.copy(quantity = quantity)
    return if (i == -1) this + next else map { if (it.key == card.key) next else it }
}
