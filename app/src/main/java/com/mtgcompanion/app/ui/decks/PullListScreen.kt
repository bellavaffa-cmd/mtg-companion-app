package com.mtgcompanion.app.ui.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.MovePulledResult
import com.mtgcompanion.app.data.PullGroupKind
import com.mtgcompanion.app.data.PullProgress
import com.mtgcompanion.app.data.PullRow
import com.mtgcompanion.app.data.PullSource
import com.mtgcompanion.app.data.holdsCards
import com.mtgcompanion.app.data.markMissingAsProxies
import com.mtgcompanion.app.data.movePulled
import com.mtgcompanion.app.data.placesOf
import com.mtgcompanion.app.data.pullBuyList
import com.mtgcompanion.app.data.pullGroupsIn
import com.mtgcompanion.app.data.pullList
import com.mtgcompanion.app.data.pullRowsAZ
import com.mtgcompanion.app.data.pulledCopies
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.common.PillChip
import com.mtgcompanion.app.ui.common.rememberMoney
import com.mtgcompanion.app.ui.theme.LocalAppColors

/**
 * A deck's pull list, the web app's PullListPage (src/pages/PullListPage.tsx): every copy the deck
 * still needs, grouped as you'd walk round the shelves to fetch them (PullList.kt), ticked off as you
 * go — by hand or with the scanner — then moved into the deck box in one go. The ticks are kept on
 * the phone (PullProgress.kt). [placeFilter]: show only what's in one box, for "Pull from here" on
 * its label.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullListScreen(
    deckId: String,
    collections: List<Collection>,
    decks: List<Deck>,
    placeFilter: String?,
    onBack: () -> Unit,
    onScan: (deckId: String) -> Unit,
    /** Writes binders and decks changed together. */
    onApply: (List<Collection>, List<Deck>) -> Unit
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val money = rememberMoney()
    val clipboard = LocalClipboardManager.current
    val progress = remember { PullProgress(context) }
    val deck = decks.firstOrNull { it.id == deckId }
    val list = remember(deck, collections, decks) { deck?.let { pullList(it, collections, decks) } }
    var ticked by remember { mutableStateOf(progress.ticked(PullProgress.ListKind.PULL, deckId)) }
    var byPlace by remember { mutableStateOf(true) }
    var hidePulled by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(placeFilter) }
    var asking by remember { mutableStateOf<PullRow?>(null) }
    var moving by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<MovePulledResult?>(null) }
    var copied by remember { mutableStateOf(false) }
    val missingRows = list?.groups?.filter { it.kind == PullGroupKind.MISSING }?.flatMap { it.rows }.orEmpty()
    var cost by remember { mutableStateOf<Double?>(null) }
    val missingIds = missingRows.map { it.scryfallId }.distinct().sorted()
    LaunchedEffect(missingIds) {
        if (missingIds.isEmpty()) { cost = null; return@LaunchedEffect }
        cost = runCatching {
            val prices = CardRepository().getCardsByIds(missingIds).associate { it.id to it.prices?.usd?.toDoubleOrNull() }
            missingRows.sumOf { (prices[it.scryfallId] ?: 0.0) * it.qty }
        }.getOrNull()
    }
    // This is the open pull list now: a scanned box label offers "Pull from here".
    LaunchedEffect(deck?.id) { if (deck != null) progress.openPullDeck = deck.id }

    fun save(next: Set<String>) {
        ticked = next
        progress.setTicked(PullProgress.ListKind.PULL, deckId, next)
    }
    fun toggle(row: PullRow) {
        when {
            row.key in ticked -> save(ticked - row.key)
            // The owner's choice: a card only another deck has is asked about every time.
            row.source is PullSource.InDeck -> asking = row
            else -> save(ticked + row.key)
        }
    }

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        if (deck != null) Text("Build: ${deck.name}", style = MaterialTheme.typography.labelMedium, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Pull list", style = MaterialTheme.typography.titleLarge)
                    }
                },
                navigationIcon = { BackButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        },
        bottomBar = {
            if (list != null && deck != null && (list.total > 0 || list.toBuy > 0)) {
                val pulled = pulledCopies(list.groups.flatMap { it.rows }, ticked)
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
                        onClick = { moving = true },
                        enabled = pulled > 0,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("Move pulled into deck box", fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    ) { padding ->
        if (deck == null || list == null) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp)) { Text("This deck isn't here any more.", color = colors.textMuted) }
            return@Scaffold
        }
        // (Not once moved: the dialog saying what happened is still to show.)
        if (list.total == 0 && list.toBuy == 0 && done == null) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp)) { Text("Nothing to pull: every card is in the deck box.", color = colors.textMuted) }
            return@Scaffold
        }
        val allRows = list.groups.flatMap { it.rows }
        val pulled = pulledCopies(allRows, ticked)
        val filterPlace = filter?.let { id -> placesOf(collections).firstOrNull { it.id == id } }
        val groups = if (filterPlace != null) pullGroupsIn(list, collections, filterPlace.id) else list.groups
        fun shown(rows: List<PullRow>) = if (hidePulled) rows.filter { it.key !in ticked } else rows

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text("$pulled of ${list.total} pulled", fontWeight = FontWeight.Bold, color = colors.textPrimary, modifier = Modifier.weight(1f))
                        Text(
                            "${list.places} ${if (list.places == 1) "place" else "places"}" + if (list.toBuy > 0) " · ${list.toBuy} to buy" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )
                    }
                    LinearProgressIndicator(
                        progress = { if (list.total > 0) pulled.toFloat() / list.total else 0f },
                        color = colors.accent,
                        trackColor = colors.surface2,
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PillChip("By place", byPlace, { byPlace = true })
                    PillChip("A–Z", !byPlace, { byPlace = false })
                    PillChip("Hide pulled", hidePulled, { hidePulled = !hidePulled })
                }
            }
            if (filterPlace != null) item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(colors.accent).clickable { filter = null }.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Only ${filterPlace.name}", color = colors.onAccent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Icon(Icons.Filled.Close, contentDescription = "Show every place", tint = colors.onAccent, modifier = Modifier.padding(start = 4.dp).size(16.dp))
                }
            }
            if (!byPlace && filterPlace == null) {
                item {
                    GroupCard(null, null, false) {
                        shown(pullRowsAZ(list)).forEach { r -> PullRowLine(r, r.key in ticked, r.where, { toggle(r) }) }
                    }
                }
            } else {
                items(groups.filter { it.kind != PullGroupKind.MISSING && shown(it.rows).isNotEmpty() }, key = { it.key }) { g ->
                    val rows = if (byPlace) g.rows else g.rows.sortedBy { it.name.lowercase() }
                    val of = g.rows.sumOf { it.qty }
                    val got = pulledCopies(g.rows, ticked)
                    GroupCard(g.title, listOfNotNull(g.detail.takeIf { g.kind == PullGroupKind.PLACE && it.isNotEmpty() }, "$got of $of").joinToString(" · "), g.kind == PullGroupKind.DECK) {
                        shown(rows).forEach { r -> PullRowLine(r, r.key in ticked, r.hint, { toggle(r) }, ask = r.source is PullSource.InDeck) }
                    }
                }
                if (filterPlace != null && groups.isEmpty()) item {
                    Text("Nothing on this list is kept in ${filterPlace.name}.", color = colors.textMuted)
                }
            }
            if (filterPlace == null && missingRows.isNotEmpty()) item {
                GroupCard("Not owned", "${list.toBuy}" + (cost?.takeIf { it > 0 }?.let { " · ${money.format(it)}" } ?: ""), false) {
                    Text(missingRows.joinToString(", ") { if (it.qty > 1) "${it.name} ×${it.qty}" else it.name }, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Button(
                            onClick = { clipboard.setText(AnnotatedString(pullBuyList(list))); copied = true },
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.surface2, contentColor = colors.textPrimary)
                        ) { Text(if (copied) "Copied" else "Copy buy list") }
                        if (!deck.holdsCards) Button(
                            onClick = { onApply(collections, decks.map { if (it.id == deck.id) markMissingAsProxies(it, list) else it }) },
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.surface2, contentColor = colors.textPrimary)
                        ) { Text("Mark as proxies") }
                    }
                }
            }
        }

        asking?.let { row ->
            val from = (row.source as? PullSource.InDeck)?.let { s -> decks.firstOrNull { it.id == s.deckId }?.name } ?: "another deck"
            AlertDialog(
                onDismissRequest = { asking = null },
                containerColor = colors.surface,
                title = { Text("Take ${row.name} from $from?", color = colors.accentLight) },
                text = { Text("$from will be a card short: it shows there as a proxy until a copy goes back.", color = colors.textMuted) },
                confirmButton = { TextButton(onClick = { save(ticked + row.key); asking = null }) { Text("Take it", color = colors.accent) } },
                dismissButton = { TextButton(onClick = { asking = null }) { Text("Leave it there", color = colors.textMuted) } }
            )
        }
        if (moving) {
            AlertDialog(
                onDismissRequest = { moving = false },
                containerColor = colors.surface,
                title = { Text("Move pulled into deck box?", color = colors.accentLight) },
                text = {
                    Text(
                        "The ${if (pulled == 1) "card leaves its" else "cards leave their"} places and ${if (pulled == 1) "counts" else "count"} as in ${deck.name}." +
                            if (!deck.holdsCards) " ${deck.name} becomes a physical deck; the cards not pulled yet count as its proxies." else "",
                        color = colors.textMuted
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val result = movePulled(deck, list, ticked, collections, decks)
                        if (result.moved > 0) onApply(result.collections, result.decks)
                        save(emptySet())
                        progress.openPullDeck = null
                        moving = false
                        done = result
                    }) { Text("Move $pulled ${if (pulled == 1) "card" else "cards"}", color = colors.accent) }
                },
                dismissButton = { TextButton(onClick = { moving = false }) { Text("Cancel", color = colors.textMuted) } }
            )
        }
        done?.let { r ->
            AlertDialog(
                onDismissRequest = { done = null; onBack() },
                containerColor = colors.surface,
                title = { Text("In the deck box", color = colors.accentLight) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${r.moved} ${if (r.moved == 1) "card is" else "cards are"} in ${deck.name} now." +
                                when {
                                    r.nowPhysical -> " It's a physical deck" + (if (r.proxies > 0) ", with ${r.proxies} ${if (r.proxies == 1) "proxy" else "proxies"} for the cards still to pull" else "") + "."
                                    r.proxies > 0 -> " ${r.proxies} ${if (r.proxies == 1) "proxy is" else "proxies are"} left to swap."
                                    else -> ""
                                },
                            color = colors.textMuted
                        )
                        r.taken.forEach { t ->
                            Text("${t.name} came out of ${t.deck}: it shows there as a proxy until a copy goes back.", color = colors.textMuted)
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { done = null; onBack() }) { Text("Done", color = colors.accent) } }
            )
        }
    }
}

/** One group of a pull or put-back list: its heading, the line beside it, and its rows. */
@Composable
internal fun GroupCard(title: String?, detail: String?, ask: Boolean, content: @Composable () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .then(if (ask) Modifier.border(1.dp, colors.warning.copy(alpha = 0.45f), RoundedCornerShape(14.dp)) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (title != null) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary, modifier = Modifier.weight(1f))
            if (detail != null) Text(detail, style = MaterialTheme.typography.labelMedium, color = colors.textMuted, textAlign = TextAlign.End)
        }
        content()
    }
}

/** A row with a tick box: the card, and where to find it (or where it goes). */
@Composable
internal fun PullRowLine(row: PullRow, ticked: Boolean, hint: String?, onToggle: () -> Unit, ask: Boolean = false) =
    TickLine(row.name + if (row.qty > 1) " ×${row.qty}" else "", ticked, hint, onToggle, ask)

@Composable
internal fun TickLine(name: String, ticked: Boolean, hint: String?, onToggle: () -> Unit, ask: Boolean = false) {
    val colors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
        Checkbox(
            checked = ticked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(checkedColor = colors.accent, checkmarkColor = colors.onAccent, uncheckedColor = colors.textMuted)
        )
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (ticked) colors.textDim else colors.textPrimary,
            textDecoration = if (ticked) TextDecoration.LineThrough else null,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (hint != null) Text(
            hint,
            style = MaterialTheme.typography.bodySmall,
            color = if (ticked) colors.textDim else if (ask) colors.warning else colors.textMuted,
            textDecoration = if (ticked) TextDecoration.LineThrough else null,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
