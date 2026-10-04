package com.mtgcompanion.app.ui.common

import com.mtgcompanion.app.data.CollectionEntry
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.AddCandidate
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckCardEntry
import com.mtgcompanion.app.data.DeckRepository
import com.mtgcompanion.app.data.GameMode
import com.mtgcompanion.app.data.addCheckLines
import com.mtgcompanion.app.data.addCheckOffersAllowedOnly
import com.mtgcompanion.app.data.addCheckTitle
import com.mtgcompanion.app.data.cardNameKey
import com.mtgcompanion.app.data.checkAdd
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.OnGold
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

// Before cards go into a deck's main deck or sideboard, they're checked against its format, its
// commander's colours and the copy limit (data/AddCheck.kt). When one fails, this asks first.

/** A card about to go into a deck, with the card itself when the caller has it (else it's looked up). */
data class AddItem(val candidate: AddCandidate, val card: ScryfallCard? = null)

/** [quantity] copies of this card, about to go into a deck's main deck or [sideboard]. */
fun ScryfallCard.toAddItem(quantity: Int = 1, sideboard: Boolean = false) =
    AddItem(AddCandidate(id, name, quantity.coerceAtLeast(1), sideboard, typeLine), this)

/** [quantity] copies of this entry's card, about to go into a deck's main deck or [sideboard]. */
fun DeckCardEntry.toAddItem(quantity: Int = this.quantity, sideboard: Boolean = false) =
    AddItem(AddCandidate(scryfallId, name, quantity.coerceAtLeast(1), sideboard, typeLine))

/** [quantity] copies of a binder's card, about to go into a deck's main deck or [sideboard]. */
fun CollectionEntry.toAddItem(quantity: Int = this.quantity + foilQuantity, sideboard: Boolean = false) =
    AddItem(AddCandidate(scryfallId, name, quantity.coerceAtLeast(1), sideboard))

/**
 * What to check before an add: the cards ([items], worked out from the deck as it is then) going
 * where [into] says. Nothing is checked when that's a binder or a Considering list.
 */
class AddCheck(val into: AddToPick, val items: suspend (Deck) -> List<AddItem>) {
    constructor(into: AddToPick, items: List<AddItem>) : this(into, { _ -> items })
}

/** The check for putting [pick]'s copies of [card] (or of the printing chosen in the picker) where it says. */
fun checkFor(card: ScryfallCard, pick: AddToPick) =
    AddCheck(pick, listOf((pick.printing ?: card).toAddItem(pick.quantity, pick.sideboard)))

/**
 * What a check decided: the cards (by cardNameKey) to [leaveOut], and how many of the checked
 * cards still go in ([kept]).
 */
data class AddCheckOutcome(val leaveOut: Set<String>, val kept: Int)

/** How the user answered. */
enum class AddCheckAnswer { ALL, ALLOWED_ONLY, CANCEL }

/** The question on screen: what the dialog says, and where its answer goes. */
class AddCheckQuestion internal constructor(
    val title: String,
    val lines: List<String>,
    val single: Boolean,
    val offersAllowedOnly: Boolean,
    internal val answer: CompletableDeferred<AddCheckAnswer>
)

/**
 * Runs the check and, when a card fails it, puts the question up ([AddCheckDialogHost] shows it)
 * and waits for the answer. One per app, shared by every [AddToFeedback].
 */
class AddCheckGate internal constructor(
    private val decks: DeckRepository,
    private val cards: CardRepository = CardRepository()
) {
    /** The question being asked, if any. */
    var question by mutableStateOf<AddCheckQuestion?>(null)
        private set

    // Cards looked up for a check, kept for the app's run: a deck's commanders are asked about on every add.
    private val known = mutableMapOf<String, ScryfallCard>()

    /**
     * Which cards to leave out: none when every card passes or the user says add them all; null
     * when the user cancels.
     */
    suspend fun run(check: AddCheck): AddCheckOutcome? {
        val none = AddCheckOutcome(emptySet(), 0)
        val pick = check.into
        if (pick.target.kind != SourceKind.DECK || pick.considering) return none
        val deck = if (pick.isNew) {
            Deck(id = "", name = pick.target.name, gameMode = (pick.newDeckMode ?: GameMode.DEFAULT).name)
        } else {
            decks.decksFlow.first().firstOrNull { it.id == pick.target.id } ?: return none
        }
        val items = check.items(deck)
        if (items.isEmpty()) return none

        // What's needed to check: each card's legalities and colour identity, and the commanders'.
        val given = items.mapNotNull { it.card?.takeIf { c -> c.legalities != null && c.colorIdentity != null } }
        given.forEach { known[it.id] = it }
        val wanted = (items.map { it.candidate.scryfallId } +
            listOfNotNull(deck.commander, deck.partnerCommander).map { it.scryfallId }).distinct()
            .filter { it !in known }
        if (wanted.isNotEmpty()) {
            // Offline, the lookup gives nothing: the format and colours go unchecked, the copy limit still is.
            val found = withTimeoutOrNull(LOOKUP_TIMEOUT_MILLIS) { cards.getCardsByIds(wanted) }.orEmpty()
            found.forEach { known[it.id] = it }
        }
        val results = checkAdd(deck, items.map { it.candidate }, known)
        if (results.all { it.allowed }) return AddCheckOutcome(emptySet(), results.size)

        val asked = AddCheckQuestion(
            title = addCheckTitle(results, deck.name),
            lines = addCheckLines(results),
            single = results.size == 1,
            offersAllowedOnly = addCheckOffersAllowedOnly(results),
            answer = CompletableDeferred()
        )
        question = asked
        val answer = try {
            asked.answer.await()
        } finally {
            if (question === asked) question = null
        }
        return when (answer) {
            AddCheckAnswer.ALL -> AddCheckOutcome(emptySet(), results.size)
            AddCheckAnswer.ALLOWED_ONLY -> AddCheckOutcome(
                results.filterNot { it.allowed }.map { cardNameKey(it.name) }.toSet(),
                results.count { it.allowed }
            )
            AddCheckAnswer.CANCEL -> null
        }
    }

    /** The user's answer to the question on screen. */
    fun answer(answer: AddCheckAnswer) {
        val asked = question ?: return
        question = null
        asked.answer.complete(answer)
    }

    private companion object {
        const val LOOKUP_TIMEOUT_MILLIS = 6_000L
    }
}

/** Shows [gate]'s question, when there is one. Placed once, around every screen (MtgNavGraph). */
@Composable
fun AddCheckDialogHost(gate: AddCheckGate) {
    val asked = gate.question ?: return
    AlertDialog(
        containerColor = Surface,
        onDismissRequest = { gate.answer(AddCheckAnswer.CANCEL) },
        title = { Text(asked.title, color = GoldLight) },
        text = {
            Column(
                Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                asked.lines.forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { gate.answer(AddCheckAnswer.ALL) },
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = OnGold)
            ) { Text(if (asked.single) "Add anyway" else "Add all") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { gate.answer(AddCheckAnswer.CANCEL) }) { Text("Cancel", color = TextMuted) }
                if (asked.offersAllowedOnly) {
                    TextButton(onClick = { gate.answer(AddCheckAnswer.ALLOWED_ONLY) }) { Text("Add only allowed", color = Gold) }
                }
            }
        }
    )
}
