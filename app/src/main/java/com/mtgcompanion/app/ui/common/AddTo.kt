package com.mtgcompanion.app.ui.common

import com.mtgcompanion.app.data.CollectionEntry
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckCardEntry
import com.mtgcompanion.app.data.GameMode
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.data.Collection as Binder

// The plain parts of adding, moving and copying cards into a deck or binder: what the picker
// (AddToPicker) is titled and hands back, what the confirmation (AddToFeedback) says, and how its
// Undo puts things back. Kept free of Compose so they can be unit tested.

/**
 * A deck or binder cards can go into. [imageUrl] is a deck's commander, shown beside it in the
 * picker; [cards] how many cards it holds, when known.
 */
data class MoveTarget(
    val kind: SourceKind,
    val id: String,
    val name: String,
    val imageUrl: String? = null,
    val cards: Int? = null,
    /** A deck whose format has a sideboard — the picker can put cards there. */
    val hasSideboard: Boolean = false
)

/** A deck as a place to put cards, with its commander's picture. */
fun Deck.asTarget() = MoveTarget(SourceKind.DECK, id, name, imageUrl = commander?.imageUrl, cards = cards.sumOf { it.quantity }, hasSideboard = mode.hasSideboard)

/** A binder as a place to put cards. */
fun Binder.asTarget() = MoveTarget(SourceKind.BINDER, id, name, cards = entries.sumOf { it.quantity + it.foilQuantity })

/**
 * Which kind of place the picker asks about first — null when there's only one kind to offer, so
 * there's nothing to ask. Being able to make a new binder (or deck) counts as that kind being on offer.
 */
fun kindsToChoose(targets: List<MoveTarget>, canMakeBinder: Boolean, canMakeDeck: Boolean = false): List<SourceKind>? {
    val binders = canMakeBinder || targets.any { it.kind == SourceKind.BINDER }
    val decks = canMakeDeck || targets.any { it.kind == SourceKind.DECK }
    return if (binders && decks) listOf(SourceKind.BINDER, SourceKind.DECK) else null
}

/** What's being done with the cards: the picker's title and the confirmation both say it. */
enum class AddVerb(val label: String, val done: String) {
    ADD("Add", "Added"),
    MOVE("Move", "Moved"),
    COPY("Copy", "Copied")
}

/** What the cards are called in a title or message: the card's name, or "3 cards". */
fun cardsSubject(count: Int, onlyName: String?): String =
    if (count == 1 && onlyName != null) onlyName else "$count ${if (count == 1) "card" else "cards"}"

/** The picker's title: "Add Sol Ring to…", "Move 3 cards to…". */
fun addToTitle(verb: AddVerb, subject: String): String = "${verb.label} $subject to…"

/**
 * The confirmation: "Added Sol Ring to Atraxa", "Moved 3 cards to Trades", "Added Sol Ring to
 * Considering in Atraxa", "Moved Duress to the sideboard in Burn". [quantity] above one is said for
 * a single card: "Added 4 × Forest to Lands".
 */
fun addToMessage(verb: AddVerb, subject: String, place: String, considering: Boolean = false, quantity: Int = 1, sideboard: Boolean = false): String {
    val what = if (quantity > 1) "$quantity × $subject" else subject
    val where = when {
        considering -> "Considering in $place"
        sideboard -> "the sideboard in $place"
        else -> place
    }
    return "${verb.done} $what to $where"
}

/** How many copies the picker's stepper starts at, and how many it goes up to. */
data class QuantityLimits(val default: Int, val max: Int) {
    /** [current] moved by [by], kept between one and [max]. */
    fun step(current: Int, by: Int): Int = (current + by).coerceIn(1, max)
}

/** The most copies one add puts in. */
const val MAX_ADD_QUANTITY = 99

/**
 * Adding starts at one copy. Moving or copying a card that's already somewhere starts at all its
 * [copies] there, and can't take more than that.
 */
fun quantityLimits(verb: AddVerb, copies: Int? = null): QuantityLimits =
    if (verb == AddVerb.ADD || copies == null || copies < 1) QuantityLimits(1, MAX_ADD_QUANTITY)
    else QuantityLimits(copies, copies)

/**
 * Which of a binder entry's copies taking [quantity] of them takes: the plain ones first, then the
 * foils. Answers (plain, foil).
 */
fun copiesTaken(entry: CollectionEntry, quantity: Int): Pair<Int, Int> {
    val plain = minOf(entry.quantity, quantity.coerceAtLeast(0))
    val foil = minOf(entry.foilQuantity, quantity.coerceAtLeast(0) - plain)
    return plain to foil
}

/**
 * Where the picker says to put the cards. A new deck or binder named in the picker comes back
 * with [isNew] and an empty id; it's made when the cards go in (AddToOps.resolve). [newDeckMode] is
 * a new deck's format. [quantity] and [foil] are only meaningful where the picker offered them.
 */
data class AddToPick(
    val target: MoveTarget,
    val considering: Boolean = false,
    val quantity: Int = 1,
    val foil: Boolean = false,
    val isNew: Boolean = false,
    val newDeckMode: GameMode? = null,
    /** Into the deck's sideboard rather than its main deck (never with [considering]). */
    val sideboard: Boolean = false,
    /** The printing chosen in the picker for a card being added; null keeps the card as it came. */
    val printing: ScryfallCard? = null
) {
    val place: String get() = target.name
}

/** The confirmation for [pick]: "Added Sol Ring to the sideboard in Burn" and the like. */
fun addToMessage(verb: AddVerb, subject: String, pick: AddToPick, quantity: Int = pick.quantity): String =
    addToMessage(verb, subject, pick.place, pick.considering, quantity, pick.sideboard)

/** One thing Undo puts back as it was. */
sealed interface UndoStep {
    /** A card in a deck: [before] is how it was (null: it wasn't there); [stillThere]: it's there now. */
    data class DeckCard(val deckId: String, val scryfallId: String, val before: DeckCardEntry?, val stillThere: Boolean) : UndoStep
    /** A card on a deck's Considering list. */
    data class Considered(val deckId: String, val scryfallId: String, val before: DeckCardEntry?, val stillThere: Boolean) : UndoStep
    /** A card in a deck's sideboard. */
    data class Sideboard(val deckId: String, val scryfallId: String, val before: DeckCardEntry?, val stillThere: Boolean) : UndoStep
    /** A deck's commanders, as they were. */
    data class Commanders(val deckId: String, val commander: DeckCardEntry?, val partner: DeckCardEntry?) : UndoStep
    /** A card in a binder. */
    data class BinderCard(val binderId: String, val scryfallId: String, val before: CollectionEntry?, val stillThere: Boolean) : UndoStep
    /** A deck or binder made for the cards: it goes again. */
    data class DeleteDeck(val deckId: String) : UndoStep
    data class DeleteBinder(val binderId: String) : UndoStep
}

/**
 * What Undo has to do to put the decks and binders back from [afterDecks]/[afterBinders] to
 * [beforeDecks]/[beforeBinders]: every card whose entry changed, in every deck and binder. The decks
 * and binders in [created] were made by the action, and are deleted instead. The Wishlist's own
 * cards (auto) are left alone — the app keeps those up to date by itself. Commanders come last, as
 * taking a card out can clear them.
 */
fun undoSteps(
    beforeDecks: List<Deck>,
    afterDecks: List<Deck>,
    beforeBinders: List<Binder>,
    afterBinders: List<Binder>,
    created: Set<String> = emptySet()
): List<UndoStep> {
    val steps = mutableListOf<UndoStep>()
    val commanders = mutableListOf<UndoStep>()
    for (after in afterDecks) {
        val before = beforeDecks.firstOrNull { it.id == after.id }
        if (before == null && after.id in created) {
            steps += UndoStep.DeleteDeck(after.id)
            continue
        }
        val was = before ?: Deck(id = after.id, name = after.name)
        steps += changed(was.cards, after.cards, { it.scryfallId }) { id, b, still -> UndoStep.DeckCard(after.id, id, b, still) }
        steps += changed(was.considering, after.considering, { it.scryfallId }) { id, b, still -> UndoStep.Considered(after.id, id, b, still) }
        steps += changed(was.sideboard, after.sideboard, { it.scryfallId }) { id, b, still -> UndoStep.Sideboard(after.id, id, b, still) }
        if (was.commander != after.commander || was.partnerCommander != after.partnerCommander) {
            commanders += UndoStep.Commanders(after.id, was.commander, was.partnerCommander)
        }
    }
    for (after in afterBinders) {
        val before = beforeBinders.firstOrNull { it.id == after.id }
        if (before == null && after.id in created) {
            steps += UndoStep.DeleteBinder(after.id)
            continue
        }
        val was = before?.entries.orEmpty()
        steps += changed(was, after.entries, { it.scryfallId }) { id, b, still ->
            val now = after.entries.firstOrNull { it.scryfallId == id }
            if (b?.auto == true || now?.auto == true) null else UndoStep.BinderCard(after.id, id, b, still)
        }
    }
    return steps + commanders
}

/** A step for every id whose entry differs between [before] and [after]. */
private fun <E> changed(
    before: List<E>,
    after: List<E>,
    id: (E) -> String,
    step: (id: String, before: E?, stillThere: Boolean) -> UndoStep?
): List<UndoStep> {
    val was = before.associateBy(id)
    val now = after.associateBy(id)
    return (was.keys + now.keys).mapNotNull { key ->
        if (was[key] == now[key]) null else step(key, was[key], now[key] != null)
    }
}
