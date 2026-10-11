package com.mtgcompanion.app.data

/*
 * Which deck copies count as the user's own — one rule for every number that says "owned" or what
 * the collection is worth: All cards, the Collection home's count, the dashboard's value, set
 * completion, collection goals, "You own N", the smart piles, a deck's upgrade spares.
 *
 * - A Physical deck's cards are owned (a deck saved with no ownership, or one this app doesn't know,
 *   is Physical — DeckOwnership.fromName).
 * - A Proxy deck counts as built: its proxies show among the copies on All cards but are worth
 *   nothing, and the real copies swapped into it are owned like a Physical deck's.
 * - Virtual and Prototype decks hold nothing the user owns, and neither does a cube (kept as an
 *   archived Virtual deck) or a welcome-flow sample.
 *
 * Storage, Where it is and the Unsorted pile keep their own, narrower rule: realCopiesOf (Physical
 * decks only). Mirrors the web app's src/collection/ownedCopies.ts.
 */

/** Whether this deck's cards count toward what the user owns (see above). */
val Deck.countsAsOwned: Boolean
    get() = sample != true && !isCube &&
        (ownershipType == DeckOwnership.PHYSICAL || ownershipType == DeckOwnership.PROXY)

/** The real copies of [entry] the deck holds that are the user's own — the ones with a value. */
fun ownedCopies(deck: Deck, entry: DeckCardEntry): Int =
    if (!deck.countsAsOwned) 0 else (entry.quantity - proxyCopies(deck, entry)).coerceAtLeast(0)

/** The proxies of [entry] in a deck that counts as owned: shown among its copies, never valued. */
fun ownedProxies(deck: Deck, entry: DeckCardEntry): Int =
    if (!deck.countsAsOwned) 0 else proxyCopies(deck, entry)
