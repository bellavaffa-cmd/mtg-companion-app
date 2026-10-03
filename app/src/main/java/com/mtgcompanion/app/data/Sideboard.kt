package com.mtgcompanion.app.data

// A deck's sideboard: up to fifteen cards beside the main deck, for formats that have one
// (GameMode.hasSideboard). Kept in Deck.sideboard — out of the main list, so stats, size, price and
// combos never see it — and merged by the sync exactly like the Considering list. The changes here are
// plain functions on a Deck so they can be tested; DeckRepository writes them.

/** Where an imported decklist line goes in a deck. */
enum class DeckPart { MAIN, SIDEBOARD, CONSIDERING }

/**
 * Where a decklist line from [section] goes in a [mode] deck: a sideboard line into the sideboard
 * for a format that has one, onto Considering for Commander and Brawl; a maybeboard line onto
 * Considering always.
 */
fun importPart(section: ListSection, mode: GameMode): DeckPart = when (section) {
    ListSection.MAIN -> DeckPart.MAIN
    ListSection.SIDEBOARD -> if (mode.hasSideboard) DeckPart.SIDEBOARD else DeckPart.CONSIDERING
    ListSection.MAYBEBOARD -> DeckPart.CONSIDERING
}

/** How many cards the sideboard holds. */
val Deck.sideboardCount: Int get() = sideboard.sumOf { it.quantity }

/** [entries] with [entry]'s copies added: onto the row for the same printing, or as a new row. */
internal fun List<DeckCardEntry>.plusCopies(entry: DeckCardEntry): List<DeckCardEntry> =
    if (any { it.scryfallId == entry.scryfallId }) {
        map { if (it.scryfallId == entry.scryfallId) it.copy(quantity = it.quantity + entry.quantity) else it }
    } else this + entry

/** This deck with [entry]'s copies added to its sideboard. A sideboard card is never a cut candidate. */
fun Deck.withSideboardCopies(entry: DeckCardEntry): Deck =
    copy(sideboard = sideboard.plusCopies(entry.copy(replaceable = false)))

/** This deck with the sideboard's copies of [scryfallId] set to [quantity]; zero or less takes it off. */
fun Deck.withSideboardQuantity(scryfallId: String, quantity: Int): Deck = copy(
    sideboard = if (quantity <= 0) sideboard.filterNot { it.scryfallId == scryfallId }
    else sideboard.map { if (it.scryfallId == scryfallId) it.copy(quantity = quantity) else it }
)

/**
 * This deck with every main-deck copy of [scryfallId] moved to the sideboard (merging with copies
 * already there). A commander stays where it is: it isn't a card you side out. Unchanged when the
 * card isn't in the main deck.
 */
fun Deck.movedToSideboard(scryfallId: String): Deck {
    val entry = cards.firstOrNull { it.scryfallId == scryfallId } ?: return this
    if (scryfallId == commander?.scryfallId || scryfallId == partnerCommander?.scryfallId) return this
    return copy(
        cards = cards.filterNot { it.scryfallId == scryfallId },
        sideboard = sideboard.plusCopies(entry.copy(replaceable = false))
    )
}

/** This deck with every sideboard copy of [scryfallId] moved into the main deck. */
fun Deck.movedToMain(scryfallId: String): Deck {
    val entry = sideboard.firstOrNull { it.scryfallId == scryfallId } ?: return this
    return copy(
        cards = cards.plusCopies(entry),
        sideboard = sideboard.filterNot { it.scryfallId == scryfallId }
    )
}
