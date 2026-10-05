package com.mtgcompanion.app.ui.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.CardFacts
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.PullProgress
import com.mtgcompanion.app.data.PutBackGroupKind
import com.mtgcompanion.app.data.PutBackMode
import com.mtgcompanion.app.data.TakeApartResult
import com.mtgcompanion.app.data.cardFactsOf
import com.mtgcompanion.app.data.putBackList
import com.mtgcompanion.app.data.takeApart
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.common.SegmentedTabs
import com.mtgcompanion.app.ui.theme.LocalAppColors

/**
 * Taking a deck apart, the web app's PutBackPage (src/pages/PutBackPage.tsx): every real copy in the
 * deck with where it goes — where it came from when it was pulled, or the best place by the boxes'
 * sorting rules — grouped by place, ticked off as you go (by hand or with the scanner), then put back
 * in one go (PullList.kt takeApart).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PutBackScreen(
    deckId: String,
    collections: List<Collection>,
    decks: List<Deck>,
    onBack: () -> Unit,
    onScan: (deckId: String) -> Unit,
    /** Writes binders and decks changed together. */
    onApply: (List<Collection>, List<Deck>) -> Unit
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val progress = remember { PullProgress(context) }
    val deck = decks.firstOrNull { it.id == deckId }
    var ticked by remember { mutableStateOf(progress.ticked(PullProgress.ListKind.PUT_BACK, deckId)) }
    var mode by remember { mutableStateOf(progress.putBackMode(deckId)) }
    var confirming by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<TakeApartResult?>(null) }
    // What the sorting rules need to know of each card: its colours and type.
    var facts by remember { mutableStateOf<Map<String, CardFacts>>(emptyMap()) }
    val ids = deck?.cards?.map { it.scryfallId }?.distinct()?.sorted().orEmpty()
    LaunchedEffect(ids) {
        if (ids.isEmpty()) return@LaunchedEffect
        facts = runCatching { CardRepository().getCardsByIds(ids).associate { it.id to cardFactsOf(it) } }.getOrDefault(emptyMap())
    }
    val list = remember(deck, collections, mode, facts) { deck?.let { d -> putBackList(d, collections, mode) { facts[it.scryfallId] } } }

    fun save(next: Set<String>) {
        ticked = next
        progress.setTicked(PullProgress.ListKind.PUT_BACK, deckId, next)
    }

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        if (deck != null) Text("Take apart: ${deck.name}", style = MaterialTheme.typography.labelMedium, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Put back list", style = MaterialTheme.typography.titleLarge)
                    }
                },
                navigationIcon = { BackButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        },
        bottomBar = {
            if (deck != null && list != null && list.total > 0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().background(colors.bg).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)
                ) {
                    Button(
                        onClick = { onScan(deck.id) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.surface2, contentColor = colors.textPrimary),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Scan to tick", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
                    }
                    Button(
                        onClick = { confirming = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) { Text("Done: deck taken apart", fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    ) { padding ->
        if (deck == null || list == null) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp)) { Text("This deck isn't here any more.", color = colors.textMuted) }
            return@Scaffold
        }
        // (Not once taken apart: the dialog saying what happened is still to show.)
        if (list.total == 0 && done == null) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp)) { Text("There are no real cards in this deck to put back.", color = colors.textMuted) }
            return@Scaffold
        }
        val rows = list.groups.flatMap { it.rows }
        val put = rows.filter { it.key in ticked }.sumOf { it.qty }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text("Where should the cards go?", fontWeight = FontWeight.Bold, color = colors.textPrimary, modifier = Modifier.weight(1f))
                        Text("$put of ${list.total} put back", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    }
                    SegmentedTabs(
                        labels = listOf("Where they came from", "Best place by rule"),
                        selected = if (mode == PutBackMode.ORIGIN) 0 else 1,
                        onSelect = { i ->
                            mode = if (i == 0) PutBackMode.ORIGIN else PutBackMode.RULE
                            progress.setPutBackMode(deckId, mode)
                        }
                    )
                    Text(
                        if (mode == PutBackMode.ORIGIN) "Cards bought for this deck go where the box rules say." else "Each card goes to the first box whose sorting rule fits it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted
                    )
                }
            }
            items(list.groups, key = { it.key }) { g ->
                val of = g.rows.sumOf { it.qty }
                val got = g.rows.filter { it.key in ticked }.sumOf { it.qty }
                GroupCard(g.title, if (g.kind == PutBackGroupKind.BASIC) "$of" else listOfNotNull(g.detail.ifEmpty { null }, "$got of $of").joinToString(" · "), false) {
                    if (g.kind == PutBackGroupKind.BASIC) Text(g.detail, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    g.rows.forEach { r ->
                        val name = (if (g.kind == PutBackGroupKind.BASIC) "${r.qty} ${r.name}" else r.name + if (r.qty > 1) " ×${r.qty}" else "") + if (r.foil) " · foil" else ""
                        TickLine(name, r.key in ticked, r.hint, { save(if (r.key in ticked) ticked - r.key else ticked + r.key) })
                    }
                }
            }
        }

        if (confirming) {
            AlertDialog(
                onDismissRequest = { confirming = false },
                containerColor = colors.surface,
                title = { Text("Take ${deck.name} apart?", color = colors.accentLight) },
                text = {
                    Text(
                        "Its ${list.total} ${if (list.total == 1) "card goes" else "cards go"} back into your collection at the places on this list" +
                            (if (put < list.total) ", ticked or not" else "") + ". The list stays, as a virtual deck, so you can build it again.",
                        color = colors.textMuted
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val result = takeApart(deck, list, collections)
                        onApply(result.collections, decks.map { if (it.id == deck.id) result.deck else it })
                        // The copies' history: each back in its place, or out of the deck with none (CopyHistory.kt).
                        val at = System.currentTimeMillis()
                        val places = com.mtgcompanion.app.data.placesOf(collections)
                        com.mtgcompanion.app.data.CopyHistoryStore.record(list.groups.flatMap { it.rows }.map { r ->
                            val dest = r.dest
                            val place = dest?.let { d -> places.firstOrNull { it.id == d.placeId } }
                            com.mtgcompanion.app.data.putBackMove(
                                at, com.mtgcompanion.app.data.MoveCard(r.name, r.scryfallId), r.qty, deck.name,
                                place?.let { com.mtgcompanion.app.data.MoveSpot(it.id, listOfNotNull(it.name, dest?.section).joinToString(" › ")) }
                            )
                        })
                        progress.clearPutBack(deck.id)
                        confirming = false
                        done = result
                    }) { Text("Take apart", color = colors.accent) }
                },
                dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel", color = colors.textMuted) } }
            )
        }
        done?.let { r ->
            AlertDialog(
                onDismissRequest = { done = null; onBack() },
                containerColor = colors.surface,
                title = { Text("Taken apart", color = colors.accentLight) },
                text = {
                    Text(
                        (if (r.placed > 0) "${r.placed} ${if (r.placed == 1) "card is" else "cards are"} back in their places. " else "") +
                            (if (r.unplaced > 0) "${r.unplaced} ${if (r.unplaced == 1) "is" else "are"} in Unsorted with no place yet. " else "") +
                            "${deck.name} is a virtual deck now.",
                        color = colors.textMuted
                    )
                },
                confirmButton = { TextButton(onClick = { done = null; onBack() }) { Text("Done", color = colors.accent) } }
            )
        }
    }
}
