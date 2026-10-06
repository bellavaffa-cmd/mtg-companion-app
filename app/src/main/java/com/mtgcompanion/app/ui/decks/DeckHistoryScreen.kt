package com.mtgcompanion.app.ui.decks

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckCardEntry
import com.mtgcompanion.app.data.HistoryDevice
import com.mtgcompanion.app.data.HistoryItem
import com.mtgcompanion.app.data.HistoryLine
import com.mtgcompanion.app.data.cardCount
import com.mtgcompanion.app.data.dayText
import com.mtgcompanion.app.data.entryTitle
import com.mtgcompanion.app.data.historyItems
import com.mtgcompanion.app.data.historyOf
import com.mtgcompanion.app.data.linesText
import com.mtgcompanion.app.data.listStateOf
import com.mtgcompanion.app.data.recordLine
import com.mtgcompanion.app.data.sourceText
import com.mtgcompanion.app.data.stateAt
import com.mtgcompanion.app.data.versionDiff
import com.mtgcompanion.app.data.wholeList
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.scryfall.ScryfallIdentifier
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.common.SegmentedTabs
import com.mtgcompanion.app.ui.collection.fetchPrices
import com.mtgcompanion.app.ui.common.rememberMoney
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

private val InColor = Color(0xFF7FC59A)
private val OutColor = Color(0xFFE08A7A)

/**
 * A deck's history, the web app's DeckHistoryPage (src/pages/DeckHistoryPage.tsx): each change to its
 * list, newest first — when, on which device, the cards in and out, the value before and after — with
 * the versions saved by name and the games played on each, and "Save this version…" (DeckHistory.kt).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckHistoryScreen(
    deckId: String,
    decks: List<Deck>,
    onBack: () -> Unit,
    onOpen: (entryId: String) -> Unit,
    onSaveVersion: (name: String, note: String) -> Unit
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val money = rememberMoney()
    val deck = decks.firstOrNull { it.id == deckId }
    val entries = remember(deck) { deck?.let { historyItems(it) }.orEmpty() }
    var naming by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val dev = remember { HistoryDevice.id(context) }

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("History", style = MaterialTheme.typography.titleLarge)
                        if (deck != null) {
                            val total = cardCount(listStateOf(deck))
                            Text("${deck.name} · $total ${if (total == 1) "card" else "cards"}", style = MaterialTheme.typography.labelMedium, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                navigationIcon = { BackButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        },
        bottomBar = {
            if (deck != null) {
                Row(Modifier.fillMaxWidth().background(colors.bg).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp)) {
                    Button(
                        onClick = { naming = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) { Text("Save this version…", fontWeight = FontWeight.ExtraBold) }
                }
            }
        }
    ) { padding ->
        if (deck == null) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp)) { Text("This deck isn't here any more.", color = colors.textMuted) }
            return@Scaffold
        }
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text("No changes yet. Each change to the list shows here, and you can save a version by name.", color = colors.textMuted)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(entries, key = { it.entry.id }) { item ->
                    HistoryCard(item, now, sourceText(item.entry, "android", dev), deck.commander != null, { money.format(it, whole = true) }) { onOpen(item.entry.id) }
                }
            }
        }
        if (naming) {
            var name by remember { mutableStateOf("") }
            var note by remember { mutableStateOf("") }
            val fields = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accent, unfocusedBorderColor = colors.border,
                focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary, cursorColor = colors.accent
            )
            AlertDialog(
                onDismissRequest = { naming = false },
                containerColor = colors.surface,
                title = { Text("Save this version", color = colors.accentLight) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = name, onValueChange = { name = it.take(60) }, singleLine = true,
                            label = { Text("Name") }, placeholder = { Text("Before game night", color = colors.textDim) },
                            colors = fields, modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = note, onValueChange = { note = it.take(500) }, minLines = 2,
                            label = { Text("Note (optional)") }, placeholder = { Text("What you changed and why", color = colors.textDim) },
                            colors = fields, modifier = Modifier.fillMaxWidth()
                        )
                        Text("The list as it is now, kept for good. The games you play with it count for it.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    }
                },
                confirmButton = {
                    TextButton(enabled = name.isNotBlank(), onClick = { onSaveVersion(name, note); naming = false }) { Text("Save", color = if (name.isNotBlank()) colors.accent else colors.textDim) }
                },
                dismissButton = { TextButton(onClick = { naming = false }) { Text("Cancel", color = colors.textMuted) } }
            )
        }
    }
}

@Composable
private fun HistoryCard(item: HistoryItem, now: Long, source: String, usesCommander: Boolean, format: (Double) -> String, onOpen: () -> Unit) {
    val colors = LocalAppColors.current
    val e = item.entry
    val record = recordLine(item)
    val small = MaterialTheme.typography.bodySmall
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(entryTitle(e, now), fontWeight = FontWeight.ExtraBold, color = colors.textPrimary, modifier = Modifier.weight(1f))
            if (source.isNotEmpty()) Text(source, style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
        }
        when (e.kind) {
            "named" -> {
                e.note?.let { Text(it, style = small, color = colors.textPrimary) }
                Text("Saved by you." + if (record.isNotEmpty()) " $record" else "", style = small, color = colors.textMuted)
            }
            "import", "start" -> Text(
                (if (e.kind == "import") "Imported" else "Starting list") + " · ${item.cards} ${if (item.cards == 1) "card" else "cards"}" + if (record.isNotEmpty()) ". $record" else "",
                style = small, color = colors.textMuted
            )
            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (e.kind == "restore" && e.to != null) Text("Went back to the list from ${dayText(e.to, now)}", style = small, color = colors.textMuted)
                    ChangeLine("+", item.added, InColor)
                    ChangeLine("−", item.removed, OutColor)
                    val commanders = item.commanders
                    if (commanders != null) Text("Commander: ${commanders.joinToString(" & ").ifEmpty { "none" }}", style = small, color = colors.textMuted)
                    else if (usesCommander && !item.latest) Text("Commander unchanged", style = small, color = colors.textMuted)
                }
                val v0 = e.v0
                val v1 = e.v1
                if (v0 != null && v1 != null && Math.round(v0) != Math.round(v1)) Text("Value ${format(v0)} → ${format(v1)}", style = small, color = colors.textMuted)
                if (record.isNotEmpty()) Text(record, style = small, color = colors.textMuted)
            }
        }
        if (!item.latest) {
            Text(
                "See the deck as it was", style = small, fontWeight = FontWeight.Bold, color = colors.accent,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onOpen).padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun ChangeLine(sign: String, lines: List<HistoryLine>, color: Color) {
    if (lines.isEmpty()) return
    val (lead, rest) = linesText(sign, lines)
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color)) { append(lead) }
            if (rest.isNotEmpty()) append(" $rest")
        },
        style = MaterialTheme.typography.bodySmall,
        color = LocalAppColors.current.textPrimary
    )
}

/** What going back did, to say so. */
data class RestoreOutcome(val incoming: Int, val out: Int, val missing: Int, val physical: Boolean)

/**
 * One earlier list, read only — the web app's DeckVersionPage: what was in it then and isn't now, and
 * what's been added since (or the whole list), with "Copy as new deck" and "Go back to this". Going
 * back keeps today's list in the history; a physical deck gets its pull list for the cards coming
 * back, and the ones going out land on the Unsorted pile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckVersionScreen(
    deckId: String,
    entryId: String,
    decks: List<Deck>,
    collections: List<Collection>,
    onBack: () -> Unit,
    /** A new deck from the list then: its name, cards and commanders. */
    onCopy: (name: String, cards: List<DeckCardEntry>, commander: DeckCardEntry?, partner: DeckCardEntry?) -> Unit,
    /** Takes the deck back to the list then; [known] gives a printing for a card the deck no longer has. */
    onRestore: suspend (known: (String) -> DeckCardEntry?) -> RestoreOutcome?,
    onPullList: () -> Unit,
    onDone: () -> Unit
) {
    val colors = LocalAppColors.current
    val money = rememberMoney()
    val scope = rememberCoroutineScope()
    val deck = decks.firstOrNull { it.id == deckId }
    val history = remember(deck) { deck?.let { historyOf(it) }.orEmpty() }
    val entry = history.firstOrNull { it.id == entryId }
    val then = remember(history, entryId) { stateAt(history, entryId) }
    val nowState = remember(deck) { deck?.let { listStateOf(it) } }
    var whole by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<RestoreOutcome?>(null) }
    var busy by remember { mutableStateOf(false) }
    // The cards from then that the deck hasn't now: their prices, and printings to put back.
    val goneNames = remember(then, nowState) { then?.cards?.keys?.filter { (nowState?.cards?.get(it) ?: 0) == 0 }?.sorted().orEmpty() }
    var fetched by remember { mutableStateOf<Map<String, ScryfallCard>>(emptyMap()) }
    val ids = deck?.cards?.map { it.scryfallId }?.distinct()?.sorted().orEmpty()
    var prices by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    LaunchedEffect(goneNames) {
        if (goneNames.isEmpty()) return@LaunchedEffect
        val repo = CardRepository()
        val out = HashMap<String, ScryfallCard>()
        for (chunk in goneNames.chunked(75)) {
            runCatching { repo.getCollection(chunk.map { ScryfallIdentifier(name = it) }).data }.getOrNull()?.forEach { c ->
                out[chunk.firstOrNull { it == c.name || c.name.startsWith("$it // ") } ?: c.name] = c
            }
        }
        fetched = out
    }
    LaunchedEffect(ids) {
        if (ids.isEmpty()) return@LaunchedEffect
        prices = runCatching { fetchPrices(CardRepository(), ids) }.getOrDefault(emptyMap())
    }

    val day = entry?.let { dayText(it.at, System.currentTimeMillis()) }.orEmpty()
    fun known(name: String): DeckCardEntry? {
        val d = deck ?: return null
        (d.sideboard + d.considering).firstOrNull { it.name == name }?.let { return it }
        fetched[name]?.let { c -> return DeckCardEntry(c.id, c.name, c.displayImageUrl, 1, c.canBeCommander, c.typeLine, c.partnerAbility, c.backImageUrl, c.tags) }
        for (col in collections) {
            col.entries.firstOrNull { it.name == name }?.let { e -> return DeckCardEntry(e.scryfallId, e.name, e.imageUrl, 1, backImageUrl = e.backImageUrl, tags = e.tags) }
        }
        return null
    }
    fun priceOf(name: String): String? {
        val inDeck = deck?.cards?.firstOrNull { it.name == name }
        val usd = inDeck?.let { prices[it.scryfallId] } ?: fetched[name]?.prices?.usd?.toDoubleOrNull()
        return usd?.let { money.format(it) }
    }

    val diff = if (then != null && nowState != null) versionDiff(then, nowState) else emptyList<HistoryLine>() to emptyList<HistoryLine>()
    val same = diff.first.isEmpty() && diff.second.isEmpty()

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (entry != null) "As it was on $day" else "An earlier version", style = MaterialTheme.typography.titleLarge)
                        if (deck != null) Text("${deck.name} · read only", style = MaterialTheme.typography.labelMedium, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = { BackButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        },
        bottomBar = {
            if (deck != null && then != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().background(colors.bg).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp)
                ) {
                    Button(
                        onClick = {
                            val plan = com.mtgcompanion.app.data.restoreList(
                                deck.copy(cards = emptyList(), commander = null, partnerCommander = null, ownership = com.mtgcompanion.app.data.DeckOwnership.VIRTUAL.name),
                                then
                            ) { name -> deck.cards.firstOrNull { it.name == name } ?: known(name) }
                            val cards = plan.deck.cards.map { it.copy(proxyQuantity = null, replaceable = false) }
                            val commander = plan.deck.commander?.let { c -> cards.firstOrNull { it.scryfallId == c.scryfallId } }
                            val partner = plan.deck.partnerCommander?.let { c -> cards.firstOrNull { it.scryfallId == c.scryfallId } }
                            onCopy("${deck.name} ($day)", cards, commander, partner)
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.surface2, contentColor = colors.textPrimary),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) { Text("Copy as new deck", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    Button(
                        enabled = !same && !busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                done = onRestore(::known)
                                busy = false
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) { Text("Go back to this", fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    ) { padding ->
        if (deck == null || entry == null || then == null) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp)) { Text("This version isn't in the deck's history any more.", color = colors.textMuted) }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { SegmentedTabs(labels = listOf("Differences", "Whole list"), selected = if (whole) 1 else 0, onSelect = { whole = it == 1 }) }
            if (whole) {
                item { HistorySectionLabel("WHOLE LIST · ${cardCount(then)}") }
                item {
                    LinesCard(wholeList(then).map { l -> Triple(null, if (l.q > 1) "${l.q} ${l.n}" else l.n, if (l.n in then.commanders) "Commander" else null) })
                }
            } else if (same) {
                item { Text("The same list as now.", color = colors.textMuted) }
            } else {
                if (diff.first.isNotEmpty()) {
                    item { HistorySectionLabel("IN IT THEN, NOT NOW · ${diff.first.sumOf { it.q }}") }
                    item { LinesCard(diff.first.map { l -> Triple("−", if (l.q > 1) "${l.q} ${l.n}" else l.n, priceOf(l.n)) }) }
                }
                if (diff.second.isNotEmpty()) {
                    item { HistorySectionLabel("ADDED SINCE · ${diff.second.sumOf { it.q }}") }
                    item { LinesCard(diff.second.map { l -> Triple("+", if (l.q > 1) "${l.q} ${l.n}" else l.n, priceOf(l.n)) }) }
                }
            }
            item {
                Text(
                    "Going back keeps today's list in the history too, so you can always return to it. Physical decks get a pull list for the cards that change.",
                    style = MaterialTheme.typography.bodySmall, color = colors.textMuted, modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        done?.let { r ->
            val pull = r.physical && r.incoming > 0
            val text = listOfNotNull(
                if (pull) "Its pull list has the ${r.incoming} ${if (r.incoming == 1) "card" else "cards"} coming back." else null,
                if (r.out > 0) "The ${r.out} ${if (r.out == 1) "card" else "cards"} taken out ${if (r.out == 1) "is" else "are"} on the Unsorted pile." else null,
                if (r.missing > 0) "${r.missing} ${if (r.missing == 1) "card" else "cards"} couldn't be found, so ${if (r.missing == 1) "it's" else "they're"} left out." else null,
                "Today's list is in the history, so you can go back to it."
            ).joinToString(" ")
            AlertDialog(
                onDismissRequest = { done = null; onDone() },
                containerColor = colors.surface,
                title = { Text("Back to the list from $day", color = colors.accentLight) },
                text = { Text(text, color = colors.textMuted) },
                confirmButton = {
                    if (pull) TextButton(onClick = { done = null; onPullList() }) { Text("Pull list", color = colors.accent) }
                    else TextButton(onClick = { done = null; onDone() }) { Text("Done", color = colors.accent) }
                },
                dismissButton = { if (pull) TextButton(onClick = { done = null; onDone() }) { Text("Later", color = colors.textMuted) } }
            )
        }
    }
}

@Composable
private fun HistorySectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = LocalAppColors.current.textMuted, modifier = Modifier.padding(top = 6.dp))
}

/** Rows of a sign, a card and what goes on the right (its price, or "Commander"). */
@Composable
private fun LinesCard(rows: List<Triple<String?, String, String?>>) {
    val colors = LocalAppColors.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        rows.forEach { (sign, name, right) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (sign != null) Text(sign, fontWeight = FontWeight.Bold, color = if (sign == "+") InColor else OutColor)
                Text(name, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
                if (right != null) Text(right, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
        }
    }
}
