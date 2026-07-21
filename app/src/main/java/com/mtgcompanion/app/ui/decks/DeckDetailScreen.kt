package com.mtgcompanion.app.ui.decks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.mtgcompanion.app.data.CardViewMode
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckCardEntry
import com.mtgcompanion.app.data.GameMode
import com.mtgcompanion.app.data.LegalityIssue
import com.mtgcompanion.app.network.edhrec.EdhrecCardView
import com.mtgcompanion.app.network.edhrec.inclusionPercent
import com.mtgcompanion.app.network.edhrec.scryfallImageUrl
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.network.spellbook.Variant
import com.mtgcompanion.app.ui.common.AlternateArtDialog
import com.mtgcompanion.app.ui.common.CardActionSheet
import com.mtgcompanion.app.ui.common.CardMenuAction
import com.mtgcompanion.app.ui.common.CardZoomDialog
import com.mtgcompanion.app.ui.common.GameModeDropdown
import com.mtgcompanion.app.ui.common.cardGrid
import com.mtgcompanion.app.ui.common.ConfirmDeleteDialog
import com.mtgcompanion.app.ui.common.ManaSymbol
import com.mtgcompanion.app.ui.common.MoveTargetDialog
import com.mtgcompanion.app.ui.common.ZoomCard
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.TextDim
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DeckDetailScreen(
    viewModel: DeckDetailViewModel,
    onBack: () -> Unit,
    onViewDetails: (String) -> Unit
) {
    val deck by viewModel.deck.collectAsState()
    val analysis by viewModel.analysis.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val prices by viewModel.prices.collectAsState()
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Tapping a card enlarges it (swipeable), showing value/total and a quantity stepper.
    // Holds (source, key): source "card" -> deck card by scryfallId, "sugg" -> suggestion by id/name.
    var zoom by remember { mutableStateOf<Pair<String, String>?>(null) }
    // Alternate-art target while the printing picker is open: (current scryfallId, card name).
    var artTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    // The card whose move-destination picker is open.
    var moveTarget by remember { mutableStateOf<DeckCardEntry?>(null) }
    val moveTargets by viewModel.moveTargets.collectAsState()
    // The card whose long-press quick-action menu is open.
    var cardMenuTarget by remember { mutableStateOf<DeckCardEntry?>(null) }
    // The card whose "add a copy elsewhere" picker is open (doesn't remove it from this deck).
    var copyTarget by remember { mutableStateOf<DeckCardEntry?>(null) }
    // The card pending a remove-confirmation, if any.
    var removeCardTarget by remember { mutableStateOf<DeckCardEntry?>(null) }
    var showImport by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    // Progress while an import runs, then its summary ("Imported N; M couldn't be matched…").
    var importState by remember { mutableStateOf<ImportState?>(null) }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        deck?.commander?.imageUrl?.let { img ->
                            AsyncImage(
                                model = img.toArtCropUrl(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp))
                            )
                        }
                        Text(deck?.name ?: "Deck", color = GoldLight, style = MaterialTheme.typography.labelLarge)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Gold)
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Deck menu", tint = Gold)
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        modifier = Modifier.background(Surface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Deck settings", color = TextPrimary) },
                            onClick = { menuOpen = false; showSettings = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Import decklist", color = TextPrimary) },
                            onClick = { menuOpen = false; showImport = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Export decklist", color = TextPrimary) },
                            onClick = { menuOpen = false; showExport = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete deck", color = Color(0xFFD3402F)) },
                            onClick = { menuOpen = false; confirmDelete = true }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
            )
        }
    ) { padding ->
        val currentDeck = deck ?: return@Scaffold

        Column(modifier = Modifier.fillMaxSize().background(Bg).padding(padding)) {
            TabRow(selectedTabIndex = pagerState.currentPage, containerColor = Bg, contentColor = Gold) {
                listOf("CARDS", "STATS", "REC", "LEGAL").forEachIndexed { index, label ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (pagerState.currentPage == index) Gold else TextMuted
                            )
                        }
                    )
                }
            }

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> CardsTab(
                        currentDeck,
                        analysis,
                        onZoomCard = { zoom = "card" to it },
                        onLongPressCard = { cardMenuTarget = it },
                        viewModel
                    )
                    1 -> StatsTab(analysis)
                    2 -> AnalysisTab(analysis, suggestions, onZoomSugg = { zoom = "sugg" to it }, viewModel)
                    else -> LegalityTab(analysis)
                }
            }
        }

        zoom?.let { (source, key) ->
            if (source == "card") {
                val groups = if (analysis.byType.isNotEmpty()) analysis.byType
                else listOf(TypeGroup("Cards", currentDeck.cards))
                val flatCards = groups.flatMap { it.cards }
                val zoomCards = flatCards.map { entry ->
                    ZoomCard(
                        imageUrl = entry.imageUrl,
                        priceUsd = prices[entry.scryfallId],
                        quantity = entry.quantity,
                        onIncrement = { viewModel.setCardQuantity(entry.scryfallId, entry.quantity + 1) },
                        onDecrement = { viewModel.setCardQuantity(entry.scryfallId, (entry.quantity - 1).coerceAtLeast(1)) },
                        onChangeArt = { artTarget = entry.scryfallId to entry.name },
                        onMove = { zoom = null; moveTarget = entry }
                    )
                }
                CardZoomDialog(zoomCards, flatCards.indexOfFirst { it.scryfallId == key }.coerceAtLeast(0)) { zoom = null }
            } else {
                val sug = suggestions.orEmpty()
                val zoomCards = sug.map { ZoomCard(imageUrl = it.scryfallImageUrl) }
                CardZoomDialog(zoomCards, sug.indexOfFirst { (it.id ?: it.name) == key }.coerceAtLeast(0)) { zoom = null }
            }
        }

        artTarget?.let { (id, name) ->
            AlternateArtDialog(name, onSelect = { viewModel.changePrinting(id, it) }, onDismiss = { artTarget = null })
        }

        moveTarget?.let { entry ->
            MoveTargetDialog(
                cardName = entry.name,
                targets = moveTargets,
                onPick = { target -> viewModel.moveCard(entry, target); moveTarget = null },
                onDismiss = { moveTarget = null }
            )
        }

        cardMenuTarget?.let { entry ->
            CardActionSheet(
                cardName = entry.name,
                actions = listOf(
                    CardMenuAction("Add to another binder/deck", Icons.Filled.Add) { copyTarget = entry },
                    CardMenuAction("Move", Icons.AutoMirrored.Filled.DriveFileMove) { moveTarget = entry },
                    CardMenuAction("Remove from deck", Icons.Filled.Close, destructive = true) { removeCardTarget = entry },
                    CardMenuAction("View details (EDHREC)", Icons.Filled.Info) { onViewDetails(entry.name) }
                ),
                onDismiss = { cardMenuTarget = null }
            )
        }

        copyTarget?.let { entry ->
            MoveTargetDialog(
                cardName = entry.name,
                targets = moveTargets,
                onPick = { target -> viewModel.copyCard(entry, target); copyTarget = null },
                onDismiss = { copyTarget = null }
            )
        }

        removeCardTarget?.let { entry ->
            ConfirmDeleteDialog(
                title = "Remove card?",
                message = "Remove ${entry.name} (${entry.quantity} cop${if (entry.quantity == 1) "y" else "ies"}) from this deck?",
                confirmLabel = "REMOVE",
                onConfirm = { viewModel.removeCard(entry.scryfallId); removeCardTarget = null },
                onDismiss = { removeCardTarget = null }
            )
        }

        if (showSettings) {
            DeckSettingsDialog(
                current = currentDeck.mode,
                onSelect = { viewModel.setGameMode(it) },
                onDismiss = { showSettings = false }
            )
        }
        if (showImport) {
            ImportDialog(
                onDismiss = { showImport = false },
                onImport = { text ->
                    showImport = false
                    importState = ImportState()
                    viewModel.importDecklist(
                        text = text,
                        onProgress = { done, total ->
                            importState = ImportState(done = done, total = total)
                        },
                        onResult = { added, failed ->
                            importState = ImportState(summary = importSummary(added, failed))
                        }
                    )
                }
            )
        }
        importState?.let { state ->
            ImportResultDialog(state = state, onDismiss = { importState = null })
        }
        if (confirmDelete) {
            DeleteDeckDialog(
                deckName = currentDeck.name,
                cardCount = currentDeck.cards.sumOf { it.quantity },
                onConfirm = { confirmDelete = false; viewModel.deleteDeck(onBack) },
                onDismiss = { confirmDelete = false }
            )
        }
        if (showExport) {
            ExportDialog(decklist = buildDecklist(currentDeck), onDismiss = { showExport = false })
        }
    }
}

private fun importSummary(added: Int, failed: List<String>): String = buildString {
    append("Imported $added card${if (added == 1) "" else "s"}.")
    if (failed.isNotEmpty()) {
        append("\n\n${failed.size} line${if (failed.size == 1) "" else "s"} couldn't be matched:\n")
        append(failed.take(25).joinToString("\n") { "• $it" })
        if (failed.size > 25) append("\n…and ${failed.size - 25} more")
    }
}

/** Deleting a deck throws away its whole card list and can't be undone, so make it deliberate. */
@Composable
private fun DeleteDeckDialog(
    deckName: String,
    cardCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ConfirmDeleteDialog(
        title = "Delete deck?",
        message = "\"$deckName\" and its $cardCount card${if (cardCount == 1) "" else "s"} will be " +
            "permanently deleted. This can't be undone.",
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

/** Import progress, or the final [summary] once it finishes. */
private data class ImportState(
    val done: Int = 0,
    val total: Int = 0,
    val summary: String? = null
)

@Composable
private fun ImportResultDialog(state: ImportState, onDismiss: () -> Unit) {
    val summary = state.summary
    AlertDialog(
        containerColor = Surface,
        onDismissRequest = { if (summary != null) onDismiss() },
        title = {
            Text(if (summary == null) "Importing decklist…" else "Import complete", color = GoldLight)
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (summary == null) {
                    if (state.total > 0) {
                        LinearProgressIndicator(
                            progress = { state.done.toFloat() / state.total },
                            color = Gold,
                            trackColor = BorderColor,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "${state.done} of ${state.total} cards",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    } else {
                        LinearProgressIndicator(
                            color = Gold,
                            trackColor = BorderColor,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("Reading list…", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                } else {
                    Text(summary, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                }
            }
        },
        confirmButton = {
            if (summary != null) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
                ) { Text("OK", color = Bg) }
            }
        }
    )
}

/** Builds a plain-text decklist ("1 Card Name" per line), commander first. */
private fun buildDecklist(deck: Deck): String = buildString {
    deck.commander?.let { appendLine("${it.quantity} ${it.name}") }
    deck.cards
        .filter { it.scryfallId != deck.commander?.scryfallId }
        .sortedBy { it.name.lowercase() }
        .forEach { appendLine("${it.quantity} ${it.name}") }
}

@Composable
private fun ImportDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        containerColor = Surface,
        onDismissRequest = onDismiss,
        title = { Text("Import decklist", color = GoldLight) },
        text = {
            Column {
                Text(
                    "Paste a decklist — one card per line, e.g. \"1 Sol Ring\". Cards are matched on Scryfall and added to this deck.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    placeholder = { Text("1 Sol Ring\n1 Arcane Signet\n…", color = TextDim) },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Gold,
                        unfocusedBorderColor = BorderColor,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = Gold
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(text) },
                enabled = text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
            ) { Text("Import") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        }
    )
}

@Composable
private fun ExportDialog(decklist: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        containerColor = Surface,
        onDismissRequest = onDismiss,
        title = { Text("Export decklist", color = GoldLight) },
        text = {
            Column {
                Text(
                    "Copy this decklist to share or back up your deck.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Bg)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    Text(
                        decklist.ifBlank { "This deck has no cards yet." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { clipboard.setText(AnnotatedString(decklist)) },
                enabled = decklist.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
            ) { Text("Copy") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = TextMuted) }
        }
    )
}

@Composable
private fun DeckSettingsDialog(
    current: GameMode,
    onSelect: (GameMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        containerColor = Surface,
        onDismissRequest = onDismiss,
        title = { Text("Deck settings", color = GoldLight) },
        text = {
            Column {
                Text(
                    "The game mode sets the legality rules checked in the Legal tab.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                Spacer(Modifier.height(14.dp))
                GameModeDropdown(selected = current, onSelect = onSelect)
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
            ) { Text("Done") }
        }
    )
}

@Composable
private fun LegalityTab(analysis: DeckAnalysis) {
    val report = analysis.legality
    if (report == null) {
        LoadingBox()
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val color = if (report.legal) Gold else Color(0xFFD3402F)
                    Icon(
                        if (report.legal) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                        contentDescription = null,
                        tint = color
                    )
                    Column {
                        Text(
                            if (report.legal) "Legal for ${report.mode.label}" else "Not legal for ${report.mode.label}",
                            style = MaterialTheme.typography.titleMedium,
                            color = color
                        )
                        Text(
                            "${report.totalCards} cards" +
                                if (report.mode.exactSize) " · needs ${report.mode.deckSize}" else " · min ${report.mode.deckSize}",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMuted
                        )
                    }
                }
            }
        }
        if (report.legal) {
            item {
                Text(
                    "No rule violations found for ${report.mode.label}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        } else {
            item {
                Text(
                    "${report.issues.size} issue${if (report.issues.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(report.issues) { issue -> LegalityIssueRow(issue) }
        }
    }
}

@Composable
private fun LegalityIssueRow(issue: LegalityIssue) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Surface)
            .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(4.dp))
            .padding(12.dp)
    ) {
        Icon(
            Icons.Filled.Cancel,
            contentDescription = null,
            tint = Color(0xFFD3402F),
            modifier = Modifier.size(18.dp)
        )
        Column {
            issue.card?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            }
            Text(issue.reason, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
    }
}

@Composable
private fun CardsTab(
    deck: Deck,
    analysis: DeckAnalysis,
    onZoomCard: (String) -> Unit,
    onLongPressCard: (DeckCardEntry) -> Unit,
    viewModel: DeckDetailViewModel
) {
    var query by remember { mutableStateOf("") }
    val trimmed = query.trim()
    val viewMode by viewModel.viewMode.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()

    // Grouped by type once analysis has loaded; otherwise a flat list so cards show immediately.
    // The commander is shown in its own pinned section, so exclude it from the list to avoid a duplicate.
    val commanderId = deck.commander?.scryfallId
    val groups = (if (analysis.byType.isNotEmpty()) analysis.byType else listOf(TypeGroup("Cards", deck.cards)))
        .mapNotNull { group ->
            val cards = group.cards.filterNot { it.scryfallId == commanderId }
                .filter { trimmed.isBlank() || it.name.contains(trimmed, ignoreCase = true) }
            if (cards.isEmpty()) null else group.copy(cards = cards)
        }
    val commanderMatches = deck.commander?.name?.contains(trimmed, ignoreCase = true) == true

    Column(modifier = Modifier.fillMaxSize()) {
        if (deck.cards.isNotEmpty() || deck.commander != null) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search this deck", color = TextDim) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextMuted) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = TextMuted)
                        }
                    }
                },
                shape = RoundedCornerShape(2.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Gold,
                    unfocusedBorderColor = BorderColor,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Gold,
                    focusedContainerColor = Surface,
                    unfocusedContainerColor = Surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (deck.commander != null && (trimmed.isBlank() || commanderMatches)) {
                item { CommanderSection(deck, onClick = { deck.commander?.let { onZoomCard(it.scryfallId) } }) }
            }
            if (deck.cards.isEmpty()) {
                item {
                    Text(
                        "No cards yet. Add cards to this deck from a card's detail page.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                return@LazyColumn
            }
            if (groups.isEmpty() && !(deck.commander != null && commanderMatches)) {
                item {
                    Text(
                        "No cards match \"$trimmed\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
                return@LazyColumn
            }

            groups.forEach { group ->
                item {
                    Text(
                        "${group.type.uppercase()} (${group.cards.sumOf { it.quantity }})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                    )
                }
                if (viewMode == CardViewMode.GRID) {
                    cardGrid(group.cards, columns = gridColumns, key = { it.scryfallId }) { card ->
                        DeckCardTile(
                            card = card,
                            onClick = { onZoomCard(card.scryfallId) },
                            onLongClick = { onLongPressCard(card) }
                        )
                    }
                } else {
                    items(group.cards, key = { it.scryfallId }) { card ->
                        DeckCardRow(
                            card = card,
                            isCommander = deck.commander?.scryfallId == card.scryfallId,
                            onClick = { onZoomCard(card.scryfallId) },
                            onLongClick = { onLongPressCard(card) },
                            onToggleCommander = {
                                viewModel.setCommander(if (deck.commander?.scryfallId == card.scryfallId) null else card)
                            },
                            onIncrement = { viewModel.setCardQuantity(card.scryfallId, card.quantity + 1) },
                            onDecrement = { viewModel.setCardQuantity(card.scryfallId, card.quantity - 1) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsTab(analysis: DeckAnalysis) {
    if (analysis.loading) {
        LoadingBox()
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Panel {
                SectionLabel("COMMANDER BRACKET")
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Bracket ${analysis.bracket}", style = MaterialTheme.typography.titleLarge)
                    Text(analysis.bracketName, style = MaterialTheme.typography.bodyMedium, color = GoldLight, modifier = Modifier.padding(bottom = 4.dp))
                }
                Text(analysis.bracketReason, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                if (analysis.gameChangers.isNotEmpty()) {
                    Text(
                        "Game Changers: ${analysis.gameChangers.joinToString(", ")}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                Text(
                    "Estimated from Game Changers and combos — not an official rating.",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextDim,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        item {
            Panel {
                SectionLabel("TOTAL VALUE (USD)")
                Text("$" + "%,.2f".format(analysis.totalUsd), style = MaterialTheme.typography.titleLarge)
            }
        }
        item {
            Panel {
                SectionLabel("MANA CURVE")
                ManaCurveChart(analysis.manaCurve)
                Text(
                    "Average mana value: ${"%.2f".format(analysis.avgManaValue)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
        item {
            Panel {
                SectionLabel("COLORS")
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 6.dp)) {
                    analysis.colorCounts.forEach { (color, count) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            ManaSymbol(color, size = 16.dp)
                            Text("$count", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        }
                    }
                }
            }
        }
        item {
            Panel {
                SectionLabel("CARD TYPES")
                val maxType = analysis.typeCounts.maxOfOrNull { it.second } ?: 1
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    analysis.typeCounts.forEach { (type, count) -> StatBar(type, count, maxType) }
                }
            }
        }
    }
}

@Composable
private fun AnalysisTab(
    analysis: DeckAnalysis,
    suggestions: List<EdhrecCardView>?,
    onZoomSugg: (String) -> Unit,
    viewModel: DeckDetailViewModel
) {
    if (analysis.loading) {
        LoadingBox()
        return
    }
    val viewMode by viewModel.recViewMode.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { SectionLabel("COMBOS (${analysis.combos.size})") }
        if (analysis.combos.isEmpty()) {
            item { Text("No complete combos detected in this deck.", style = MaterialTheme.typography.bodySmall, color = TextMuted) }
        } else {
            items(analysis.combos.take(10), key = { it.id }) { combo -> ComboRow(combo) }
        }
        item { SectionLabel("EDHREC SUGGESTIONS") }
        val sug = suggestions
        when {
            sug == null -> item {
                Text(
                    "Set a commander to see suggestions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            sug.isEmpty() -> item { Text("No suggestions found.", style = MaterialTheme.typography.bodySmall, color = TextMuted) }
            viewMode == CardViewMode.GRID -> {
                cardGrid(sug, columns = gridColumns, key = { it.id ?: it.name }) { view ->
                    SuggestionTile(view, onClick = { onZoomSugg(view.id ?: view.name) })
                }
            }
            else -> items(sug, key = { it.id ?: it.name }) { view ->
                SuggestionRow(view, onClick = { onZoomSugg(view.id ?: view.name) })
            }
        }
    }
}

// ---- shared bits ----

@Composable
private fun LoadingBox() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Gold)
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Surface)
            .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(6.dp))
            .padding(14.dp),
        content = content
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun ManaCurveChart(curve: List<Pair<String, Int>>) {
    val max = curve.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    Row(
        modifier = Modifier.fillMaxWidth().height(130.dp).padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        curve.forEach { (bucket, count) ->
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("$count", style = MaterialTheme.typography.labelMedium, color = GoldLight)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height((6 + 92 * count / max).dp)
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(if (count > 0) Gold else BorderColor)
                )
                Spacer(Modifier.height(4.dp))
                Text(bucket, style = MaterialTheme.typography.labelMedium, color = TextMuted)
            }
        }
    }
}

@Composable
private fun StatBar(label: String, count: Int, max: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextPrimary, modifier = Modifier.fillMaxWidth(0.28f))
        Box(modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(2.dp)).background(Bg)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(count.toFloat() / max)
                    .height(8.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Gold)
            )
        }
        Text("$count", style = MaterialTheme.typography.bodySmall, color = GoldLight)
    }
}

@Composable
private fun ComboRow(combo: Variant) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Surface)
            .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(4.dp))
            .padding(12.dp)
    ) {
        Text(combo.uses.joinToString(" + ") { it.card.name }, style = MaterialTheme.typography.bodyMedium, color = GoldLight)
        Text(
            "Produces: " + combo.produces.joinToString(", ") { it.feature.name },
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun SuggestionRow(view: EdhrecCardView, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Surface)
            .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        AsyncImage(
            model = view.scryfallImageUrl.toArtCropUrl(),
            contentDescription = view.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 72.dp, height = 52.dp).clip(RoundedCornerShape(4.dp))
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(view.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            val pct = view.inclusionPercent
            Text(
                if (pct != null) "$pct% of decks" else "${view.numDecks ?: 0} decks",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun SuggestionTile(view: EdhrecCardView, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        AsyncImage(
            model = view.scryfallImageUrl,
            contentDescription = view.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(6.dp))
        )
        Text(
            view.name,
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
        val pct = view.inclusionPercent
        Text(
            if (pct != null) "$pct%" else "${view.numDecks ?: 0}",
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted
        )
    }
}

@Composable
private fun CommanderSection(deck: Deck, onClick: () -> Unit) {
    val commander = deck.commander ?: return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Surface)
            .border(BorderStroke(1.dp, Gold.copy(alpha = 0.5f)), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        AsyncImage(
            model = commander.imageUrl.toArtCropUrl(),
            contentDescription = commander.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 84.dp, height = 60.dp).clip(RoundedCornerShape(4.dp))
        )
        Column {
            Text("COMMANDER", style = MaterialTheme.typography.labelMedium, color = TextDim)
            Text(commander.name, style = MaterialTheme.typography.bodyMedium, color = GoldLight)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeckCardRow(
    card: DeckCardEntry,
    isCommander: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleCommander: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Surface)
            .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(4.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(12.dp)
    ) {
        AsyncImage(
            model = card.imageUrl.toArtCropUrl(),
            contentDescription = card.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 72.dp, height = 52.dp).clip(RoundedCornerShape(4.dp))
        )
        Text(
            card.name,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (card.canBeCommander) {
            IconButton(onClick = onToggleCommander, modifier = Modifier.size(30.dp)) {
                Icon(
                    if (isCommander) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = "Set as commander",
                    tint = Gold,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        // Compact quantity stepper on the right: − removes a copy (removes the card at 0), + adds one.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(onClick = onDecrement, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Filled.Remove, contentDescription = "Remove a copy", tint = Gold, modifier = Modifier.size(18.dp))
            }
            Text("${card.quantity}", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            IconButton(onClick = onIncrement, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Filled.Add, contentDescription = "Add a copy", tint = Gold, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeckCardTile(card: DeckCardEntry, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Box {
            AsyncImage(
                model = card.imageUrl,
                contentDescription = card.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(6.dp))
            )
            Text(
                "×${card.quantity}",
                style = MaterialTheme.typography.labelMedium,
                color = GoldLight,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Text(
            card.name,
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
