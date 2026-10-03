package com.mtgcompanion.app.ui.common

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.mtgcompanion.app.data.CollectionEntry
import com.mtgcompanion.app.data.CollectionRepository
import com.mtgcompanion.app.data.DeckCardEntry
import com.mtgcompanion.app.data.DeckRepository
import com.mtgcompanion.app.data.GameMode
import com.mtgcompanion.app.data.UNSORTED_COLLECTION_ID
import com.mtgcompanion.app.data.duplicateWarning
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one confirmation for cards going into a deck or binder, wherever that happens: a snackbar at
 * the bottom saying what was done ("Added Sol Ring to Atraxa"), with Undo.
 *
 * [perform] does the change itself, in the app's scope rather than a screen's — so it finishes, and Undo
 * still works, after the screen is left. Undo needs no per-flow code: the decks and binders are read
 * before and after the change, and whatever changed is put back (see [undoSteps]).
 */
class AddToFeedback internal constructor(
    val host: SnackbarHostState,
    private val scope: CoroutineScope,
    private val decks: DeckRepository,
    private val binders: CollectionRepository,
    // Shared by every copy (see withHost), so one change is read before and after on its own.
    private val lock: Mutex = Mutex()
) {
    /** The same confirmation, shown in [other] — for a dialog that stays open over the screen. */
    fun withHost(other: SnackbarHostState) = AddToFeedback(other, scope, decks, binders, lock)

    /**
     * Does [change], then says [message] with Undo. [change] can say more ([AddToOps.note]: a
     * format warning, "2 were already there") or something else ([AddToOps.message]). [onUndone]
     * runs after an Undo, for a screen that also has to put something back (the scanner's pile).
     */
    fun perform(message: String, onUndone: (() -> Unit)? = null, change: suspend AddToOps.() -> Unit) {
        scope.launch {
            val ops = AddToOps(decks, binders, message)
            val steps = lock.withLock {
                val beforeDecks = decks.decksFlow.first()
                val beforeBinders = binders.collectionsFlow.first()
                try {
                    ops.change()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: java.io.IOException) {
                    ops.message = "Couldn't reach Scryfall — try again when you're online."
                    ops.note = null
                } catch (e: Exception) {
                    ops.message = "Something went wrong: ${e.message ?: "try again"}."
                    ops.note = null
                }
                undoSteps(beforeDecks, decks.decksFlow.first(), beforeBinders, binders.collectionsFlow.first(), ops.created)
            }
            if (ops.takenFromPile > 0) ops.addNote("${ops.takenFromPile} taken from Unsorted.")
            val text = listOfNotNull(ops.message, ops.note).joinToString("\n")
            host.currentSnackbarData?.dismiss()
            val result = host.showSnackbar(
                text,
                actionLabel = if (steps.isEmpty()) null else "Undo",
                withDismissAction = steps.isEmpty(),
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed && steps.isNotEmpty()) {
                lock.withLock { applyUndo(steps) }
                onUndone?.invoke()
            }
        }
    }

    /** Just a message, in the same place — for an add that couldn't happen. */
    fun say(message: String) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            host.showSnackbar(message, withDismissAction = true)
        }
    }

    private suspend fun applyUndo(steps: List<UndoStep>) {
        steps.forEach { step ->
            when (step) {
                is UndoStep.DeckCard -> when {
                    step.before == null -> decks.setCardQuantity(step.deckId, step.scryfallId, 0)
                    step.stillThere -> decks.setCardQuantity(step.deckId, step.scryfallId, step.before.quantity)
                    else -> decks.addEntry(step.deckId, step.before)
                }
                is UndoStep.Considered -> {
                    if (step.stillThere) decks.removeFromConsidering(step.deckId, step.scryfallId)
                    step.before?.let { decks.addConsideringEntry(step.deckId, it) }
                }
                is UndoStep.Sideboard -> when {
                    step.before == null -> decks.setSideboardQuantity(step.deckId, step.scryfallId, 0)
                    step.stillThere -> decks.setSideboardQuantity(step.deckId, step.scryfallId, step.before.quantity)
                    else -> decks.addSideboardEntry(step.deckId, step.before)
                }
                is UndoStep.Commanders -> {
                    decks.setCommander(step.deckId, step.commander)
                    decks.setPartnerCommander(step.deckId, step.partner)
                }
                is UndoStep.BinderCard -> when {
                    step.before == null -> binders.removeEntry(step.binderId, step.scryfallId)
                    step.stillThere -> binders.setQuantity(step.binderId, step.scryfallId, step.before.quantity, step.before.foilQuantity)
                    else -> binders.addEntry(step.binderId, step.before)
                }
                is UndoStep.DeleteDeck -> decks.deleteDeck(step.deckId)
                is UndoStep.DeleteBinder -> binders.deleteCollection(step.binderId)
            }
        }
    }
}

/**
 * What a change run by [AddToFeedback.perform] can use: the repositories, a way to make the new deck or
 * binder the picker named, and the shared "add this card" step most flows use.
 */
class AddToOps internal constructor(
    val decks: DeckRepository,
    val binders: CollectionRepository,
    /** What the confirmation says; a change can replace it. */
    var message: String
) {
    /** A second line under [message] — a format warning, cards skipped. */
    var note: String? = null

    /** Copies taken out of the Unsorted pile into a deck by [addCard]; said once, at the end. */
    internal var takenFromPile = 0

    /** Decks and binders made for this change, which Undo deletes. */
    internal val created = mutableSetOf<String>()

    /** Adds a line to [note]. */
    fun addNote(line: String) {
        note = listOfNotNull(note, line).joinToString("\n")
    }

    /** Where [pick] says to go — made first, when the picker named a new deck or binder. */
    suspend fun resolve(pick: AddToPick): MoveTarget {
        if (!pick.isNew) return pick.target
        val name = pick.target.name.trim().ifBlank { if (pick.target.kind == SourceKind.DECK) "New deck" else "New binder" }
        val made = when (pick.target.kind) {
            SourceKind.DECK -> decks.createDeck(name, pick.newDeckMode ?: GameMode.DEFAULT).let { MoveTarget(SourceKind.DECK, it.id, it.name) }
            SourceKind.BINDER -> binders.createCollection(name).let { MoveTarget(SourceKind.BINDER, it.id, it.name) }
        }
        created += made.id
        return made
    }

    /**
     * Puts [pick]'s quantity of [card] (or of the printing chosen in the picker, [AddToPick.printing])
     * where [pick] says: into a deck (with a note when the format's copy limit is passed — the card
     * goes in either way), its sideboard, its Considering list, or into a binder (foil or not). A
     * deck that holds the user's own copies takes its copies out of the Unsorted pile, unless
     * [fromPile] is false (the scanner's cards are new copies in hand). The sideboard doesn't: it's
     * kept out of what a deck holds, like Considering.
     */
    suspend fun addCard(card: ScryfallCard, pick: AddToPick, fromPile: Boolean = true) {
        @Suppress("NAME_SHADOWING")
        val card = pick.printing ?: card
        val target = resolve(pick)
        val quantity = pick.quantity.coerceAtLeast(1)
        when (target.kind) {
            SourceKind.DECK -> if (pick.considering) {
                decks.addConsideringEntry(target.id, card.asDeckEntry(1))
            } else if (pick.sideboard) {
                val deck = decks.decksFlow.first().firstOrNull { it.id == target.id }
                deck?.let { duplicateWarning(it, card, quantity) }?.let { addNote(it) }
                decks.addSideboardEntry(target.id, card.asDeckEntry(quantity))
            } else {
                val deck = decks.decksFlow.first().firstOrNull { it.id == target.id }
                deck?.let { duplicateWarning(it, card, quantity) }?.let { addNote(it) }
                decks.addEntry(target.id, card.asDeckEntry(quantity))
                if (fromPile) takenFromPile += binders.takeIntoDeck(deck, card.id, card.name, quantity)
            }
            SourceKind.BINDER -> {
                val entry = CollectionEntry(
                    card.id, card.name, card.displayImageUrl,
                    quantity = if (pick.foil) 0 else quantity,
                    foilQuantity = if (pick.foil) quantity else 0,
                    backImageUrl = card.backImageUrl,
                    tags = card.tags
                )
                // The Unsorted pile is made when the first cards go into it.
                if (target.id == UNSORTED_COLLECTION_ID) binders.addUnsorted(listOf(entry)) else binders.addEntry(target.id, entry)
            }
        }
    }
}

/** [quantity] copies of this card as a deck entry, with what a deck needs to know about it. */
fun ScryfallCard.asDeckEntry(quantity: Int) = DeckCardEntry(
    id, name, displayImageUrl, quantity = quantity, canBeCommander = canBeCommander, typeLine = typeLine,
    partnerAbility = partnerAbility, backImageUrl = backImageUrl, tags = tags
)

/** Provided by the app's navigation graph, around every screen. */
val LocalAddToFeedback = staticCompositionLocalOf<AddToFeedback> { error("AddToFeedback is provided by MtgNavGraph") }

/** Makes the app's [AddToFeedback]. Its [scope] should outlive single screens. */
fun addToFeedback(host: SnackbarHostState, scope: CoroutineScope, decks: DeckRepository, binders: CollectionRepository) =
    AddToFeedback(host, scope, decks, binders)

/** Where the confirmation shows, in the app's colours. */
@Composable
fun AddToSnackbarHost(host: SnackbarHostState, modifier: Modifier = Modifier) {
    val app = LocalAppColors.current
    SnackbarHost(host, modifier) { data ->
        Snackbar(
            data,
            containerColor = app.surface3,
            contentColor = app.textPrimary,
            actionColor = app.accent,
            dismissActionContentColor = app.textMuted
        )
    }
}
