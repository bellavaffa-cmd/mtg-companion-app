package com.mtgcompanion.app.ui.scan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.PrintingSearchFacts
import com.mtgcompanion.app.data.ScanRow
import com.mtgcompanion.app.data.searchPrintings
import com.mtgcompanion.app.data.setCodeQuery
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.TextDim
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary
import kotlinx.coroutines.delay

/** Waits this long after the last key before asking Scryfall for a set the loaded printings don't have. */
private const val SET_LOOKUP_MS = 350L

private fun factsOf(card: ScryfallCard) = PrintingSearchFacts(card.set, card.setName, card.collectorNumber, card.releasedAt)

/** A printing's set code and number, as its tile heads it: "FIN · 306". */
private val ScryfallCard.printingCode: String
    get() = listOfNotNull(set?.uppercase(), collectorNumber).joinToString(" · ")

/** [this] then whatever of [more] isn't in it already, by id. */
private fun List<ScryfallCard>.plusNew(more: List<ScryfallCard>): List<ScryfallCard> {
    if (more.isEmpty()) return this
    val ids = mapTo(HashSet()) { it.id }
    return this + more.filter { it.id !in ids }
}

/**
 * Which printing is in your hand. The camera reads a card's name easily; the tiny set code that
 * says *which* printing often can't be read at all, and then the card comes in as its usual
 * printing. This shows every printing there is, so the right art is a tap away — and a search
 * field over them narrows them by set name, set code, collector number or year (data/PrintingSearch).
 * The field doesn't take focus by itself, so the keyboard stays down and the art in view until it's
 * tapped.
 *
 * [load] fetches the printings, telling its callback each page as it comes (the printings so far
 * and how many there are); [loadSet] fetches one set's printings of the card, for a set code typed
 * that the printings loaded so far don't have.
 */
@Composable
internal fun ArtPickerDialog(
    row: ScanRow,
    load: suspend (onPage: (List<ScryfallCard>, Int?) -> Unit) -> List<ScryfallCard>,
    onPick: (ScryfallCard) -> Unit,
    onDismiss: () -> Unit,
    /** Given where a scan is being fixed: "It's a different card" — search for the card it really is. */
    onDifferent: (() -> Unit)? = null,
    /** Whether [row]'s card is ringed as the one it is now (not for a different card just searched for). */
    ringCurrent: Boolean = true,
    loadSet: (suspend (String) -> List<ScryfallCard>)? = null
) {
    var printings by remember(row.id) { mutableStateOf<List<ScryfallCard>?>(null) }
    var total by remember(row.id) { mutableStateOf<Int?>(null) }
    var loading by remember(row.id) { mutableStateOf(true) }
    var query by remember(row.id) { mutableStateOf("") }
    val asked = remember(row.id) { mutableSetOf<String>() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(row.id) {
        val found = load { soFar, all ->
            total = all
            // Printings fetched by set (below) while the pages were coming stay in.
            printings = soFar.plusNew(printings.orEmpty())
        }
        printings = found.plusNew(printings.orEmpty())
        loading = false
    }
    val found = printings
    val shown = remember(found, query) { found?.let { searchPrintings(it, query, ::factsOf) }.orEmpty() }
    val searching = query.isNotBlank()
    val count = found?.size ?: 0
    val of = maxOf(count, total ?: 0)

    // A card with so many printings they aren't all in yet (still loading, or more than the pages the
    // app follows): a set code with no match among them is asked of Scryfall itself, `!"name" set:code`.
    val known = total
    val incomplete = loading || (known != null && count < known)
    LaunchedEffect(query, shown.isEmpty(), incomplete) {
        if (loadSet == null || shown.isNotEmpty() || !incomplete) return@LaunchedEffect
        val code = setCodeQuery(query) ?: return@LaunchedEffect
        if (code.set in asked) return@LaunchedEffect
        delay(SET_LOOKUP_MS)
        asked += code.set
        val inSet = loadSet(code.set)
        if (inSet.isNotEmpty()) printings = printings.orEmpty().plusNew(inSet)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text(row.card.name, color = GoldLight) },
        text = {
            when {
                found == null -> Text("Looking up printings…", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                count <= 1 && !loading -> Text("Only one printing of this card.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                count <= 1 -> Text("Looking up printings…", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Set name, code or number", color = TextDim) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextMuted) },
                        trailingIcon = if (query.isEmpty()) null else ({
                            IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = TextMuted) }
                        }),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Search
                        ),
                        // Search on the keyboard: the one printing left, picked; otherwise the keyboard
                        // goes down to show the ones that match.
                        keyboardActions = KeyboardActions(onSearch = {
                            if (searching && shown.size == 1) onPick(shown[0]) else keyboard?.hide()
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Gold,
                            unfocusedBorderColor = BorderColor,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = Gold
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        (if (searching) "${shown.size} of $of printings" else "$of printings") + if (loading) " · loading more…" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                    if (searching && shown.isEmpty()) {
                        Column(modifier = Modifier.padding(vertical = 12.dp)) {
                            Text(
                                "No printing of ${row.card.name} in “${query.trim()}”" + if (loading) " yet." else ".",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                            if (onDifferent != null) {
                                TextButton(onClick = onDifferent) { Text("It's a different card", color = Gold) }
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(96.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.height(380.dp)
                        ) {
                            items(shown, key = { it.id }) { card ->
                                Column(modifier = Modifier.clickable { onPick(card) }) {
                                    val picked = ringCurrent && card.id == row.card.id
                                    AsyncImage(
                                        model = card.displayImageUrl,
                                        contentDescription = listOfNotNull(card.printingCode.ifEmpty { null }, card.setName).joinToString(" · "),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(0.72f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .border(
                                                BorderStroke(if (picked) 2.dp else 1.dp, if (picked) Gold else BorderColor),
                                                RoundedCornerShape(10.dp)
                                            )
                                    )
                                    Text(
                                        card.printingCode.ifEmpty { card.printingLabel },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    card.setName?.let {
                                        Text(it, style = MaterialTheme.typography.labelSmall, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Gold) } },
        dismissButton = onDifferent?.let { different -> { TextButton(onClick = different) { Text("It's a different card", color = Gold) } } }
    )
}
