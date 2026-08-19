package com.mtgcompanion.app.ui.decks

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.mtgcompanion.app.data.CardViewMode
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckCardEntry
import com.mtgcompanion.app.data.DeckOwnership
import com.mtgcompanion.app.data.GameMode
import com.mtgcompanion.app.data.LegalityIssue
import com.mtgcompanion.app.data.LegalityIssueKind
import com.mtgcompanion.app.data.partnersWith
import com.mtgcompanion.app.network.edhrec.EdhrecCardView
import com.mtgcompanion.app.network.edhrec.inclusionPercent
import com.mtgcompanion.app.network.edhrec.scryfallImageUrl
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.network.spellbook.Variant
import com.mtgcompanion.app.ui.common.AnimatedUsdText
import com.mtgcompanion.app.ui.common.CardActionMenu
import com.mtgcompanion.app.ui.common.CardMenuAction
import com.mtgcompanion.app.ui.common.CardZoomDialog
import com.mtgcompanion.app.ui.common.SimilarCardsDialog
import com.mtgcompanion.app.ui.common.ComboDetailDialog
import com.mtgcompanion.app.ui.common.GameModeDropdown
import com.mtgcompanion.app.ui.common.cardGrid
import com.mtgcompanion.app.ui.common.ConfirmDeleteDialog
import com.mtgcompanion.app.ui.common.elevatedCard
import com.mtgcompanion.app.ui.common.FlipBadge
import com.mtgcompanion.app.ui.common.ManaSymbol
import com.mtgcompanion.app.ui.common.MoveTargetDialog
import com.mtgcompanion.app.ui.common.ZoomCard
import com.mtgcompanion.app.ui.common.pressScale
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldDim
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
    val context = LocalContext.current
    val deck by viewModel.deck.collectAsState()
    val analysis by viewModel.analysis.collectAsState()
    val cardGroups by viewModel.cardGroups.collectAsState()
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
    // The card whose move-destination picker is open.
    var moveTarget by remember { mutableStateOf<DeckCardEntry?>(null) }
    val moveTargets by viewModel.moveTargets.collectAsState()
    val cardSources by viewModel.cardSources.collectAsState()
    // The card whose "add a copy elsewhere" picker is open (doesn't remove it from this deck).
    var copyTarget by remember { mutableStateOf<DeckCardEntry?>(null) }
    // Name of the card whose "find similar" overlay is open, if any.
    var similarSearchFor by remember { mutableStateOf<String?>(null) }
    // Tints the bar as content scrolls under it (no height change — the title row already carries
    // a commander-art thumbnail, which a full Large app bar would end up rendering twice).
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    // The card pending a remove-confirmation, if any.
    var removeCardTarget by remember { mutableStateOf<DeckCardEntry?>(null) }
    var showImport by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showGoldfish by remember { mutableStateOf(false) }
    // Progress while an import runs, then its summary ("Imported N; M couldn't be matched…").
    var importState by remember { mutableStateOf<ImportState?>(null) }

    Scaffold(
        containerColor = Bg,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        deck?.commander?.imageUrl?.let { img ->
                            AsyncImage(
                                model = img.toArtCropUrl(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(14.dp))
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
                            text = { Text("Goldfish (playtest)", color = TextPrimary) },
                            onClick = { menuOpen = false; showGoldfish = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Buy missing cards", color = TextPrimary) },
                            onClick = {
                                menuOpen = false
                                viewModel.buildMissingCardsUrl { url ->
                                    if (url == null) {
                                        Toast.makeText(context, "You already own every card in this deck.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    }
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete deck", color = Color(0xFFD3402F)) },
                            onClick = { menuOpen = false; confirmDelete = true }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg, scrolledContainerColor = Surface)
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
                        cardActions = { entry ->
                            deckCardActions(
                                entry = entry,
                                mode = currentDeck.mode,
                                isCommander = currentDeck.commander?.scryfallId == entry.scryfallId,
                                hasCommander = currentDeck.commander != null,
                                isPartnerCommander = currentDeck.partnerCommander?.scryfallId == entry.scryfallId,
                                canPartnerWithCommander = currentDeck.commander?.let { partnersWith(it, entry) } ?: false,
                                onViewDetails = onViewDetails,
                                onCopy = { copyTarget = it },
                                onMove = { moveTarget = it },
                                onRemove = { removeCardTarget = it },
                                onSetCommander = { viewModel.setCommander(it) },
                                onSetPartnerCommander = { viewModel.setPartnerCommander(it) }
                            )
                        },
                        viewModel
                    )
                    1 -> StatsTab(analysis, currentDeck, viewModel)
                    2 -> AnalysisTab(analysis, suggestions, onZoomSugg = { zoom = "sugg" to it }, viewModel)
                    else -> LegalityTab(analysis, viewModel)
                }
            }
        }

        zoom?.let { (source, key) ->
            if (source == "card") {
                val groups = when {
                    analysis.byType.isNotEmpty() -> analysis.byType
                    cardGroups.isNotEmpty() -> cardGroups
                    else -> listOf(TypeGroup("Cards", currentDeck.cards))
                }
                val flatCards = groups.flatMap { it.cards }
                val zoomCards = flatCards.map { entry ->
                    ZoomCard(
                        imageUrl = entry.imageUrl,
                        cardName = entry.name,
                        priceUsd = prices[entry.scryfallId],
                        quantity = entry.quantity,
                        onIncrement = { viewModel.setCardQuantity(entry.scryfallId, entry.quantity + 1) },
                        onDecrement = { viewModel.setCardQuantity(entry.scryfallId, (entry.quantity - 1).coerceAtLeast(1)) },
                        onSelectPrinting = { chosen -> viewModel.changePrinting(entry.scryfallId, chosen) },
                        onMove = { zoom = null; moveTarget = entry },
                        onViewDetails = { zoom = null; onViewDetails(entry.name) },
                        sources = cardSources[entry.scryfallId].orEmpty().filter { it.id != currentDeck.id },
                        backImageUrl = entry.backImageUrl,
                        tags = entry.tags,
                        onFindSimilar = { zoom = null; similarSearchFor = entry.name }
                    )
                }
                CardZoomDialog(zoomCards, flatCards.indexOfFirst { it.scryfallId == key }.coerceAtLeast(0)) { zoom = null }
            } else {
                val sug = suggestions.orEmpty()
                val zoomCards = sug.map { suggestion ->
                    ZoomCard(
                        imageUrl = suggestion.scryfallImageUrl,
                        onViewDetails = { zoom = null; onViewDetails(suggestion.name) }
                    )
                }
                CardZoomDialog(zoomCards, sug.indexOfFirst { (it.id ?: it.name) == key }.coerceAtLeast(0)) { zoom = null }
            }
        }

        similarSearchFor?.let { name ->
            SimilarCardsDialog(
                cardName = name,
                onDismiss = { similarSearchFor = null },
                onAdd = { similar ->
                    similarSearchFor = null
                    viewModel.addCard(similar) { warning -> Toast.makeText(context, warning, Toast.LENGTH_LONG).show() }
                },
                onViewDetails = { similar -> similarSearchFor = null; onViewDetails(similar.name) }
            )
        }

        moveTarget?.let { entry ->
            MoveTargetDialog(
                cardName = entry.name,
                targets = moveTargets,
                onPick = { target -> viewModel.moveCard(entry, target); moveTarget = null },
                onDismiss = { moveTarget = null }
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
                ownership = currentDeck.ownershipType,
                onOwnershipChange = { viewModel.setOwnership(it) },
                tags = currentDeck.tags,
                onTagsChange = { viewModel.setTags(it) },
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
            ExportDialog(deck = currentDeck, viewModel = viewModel, onDismiss = { showExport = false })
        }
        if (showGoldfish) {
            GoldfishDialog(deck = currentDeck, onDismiss = { showGoldfish = false })
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
/**
 * "Simple" is just "qty name" per line — the most broadly compatible format (Moxfield, Archidekt,
 * TappedOut, MTG Arena, MTGO all read it). "Exact printing" appends "(SET) collector-number" from
 * [cards], the same "(SLD) 1962" shape the deck's own decklist *importer* already parses — so it
 * round-trips through this app (or anywhere else that also understands printing-annotated lines)
 * preserving which specific art/printing each card was.
 */
private fun buildDecklist(deck: Deck, cards: Map<String, ScryfallCard> = emptyMap(), exactPrinting: Boolean = false): String = buildString {
    fun line(entry: DeckCardEntry) {
        val printing = if (exactPrinting) {
            cards[entry.scryfallId]?.let { card ->
                val set = card.set?.uppercase()
                val number = card.collectorNumber
                if (set != null && number != null) " ($set) $number" else null
            }
        } else null
        appendLine("${entry.quantity} ${entry.name}${printing ?: ""}")
    }
    deck.commander?.let { line(it) }
    deck.partnerCommander?.let { line(it) }
    val commanderIds = setOfNotNull(deck.commander?.scryfallId, deck.partnerCommander?.scryfallId)
    deck.cards
        .filterNot { it.scryfallId in commanderIds }
        .sortedBy { it.name.lowercase() }
        .forEach { line(it) }
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
private fun ExportDialog(deck: Deck, viewModel: DeckDetailViewModel, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var exact by remember { mutableStateOf(false) }
    var exactCards by remember { mutableStateOf<Map<String, ScryfallCard>?>(null) }
    var loadingExact by remember { mutableStateOf(false) }

    LaunchedEffect(exact) {
        if (exact && exactCards == null) {
            loadingExact = true
            exactCards = runCatching { viewModel.resolveCardsForExport() }.getOrDefault(emptyMap())
            loadingExact = false
        }
    }

    val decklist = if (exact) buildDecklist(deck, exactCards.orEmpty(), exactPrinting = true) else buildDecklist(deck)

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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportFormatChip("SIMPLE", selected = !exact) { exact = false }
                    ExportFormatChip("EXACT PRINTING", selected = exact) { exact = true }
                }
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Bg)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    if (exact && loadingExact) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Gold)
                    } else {
                        Text(
                            decklist.ifBlank { "This deck has no cards yet." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { clipboard.setText(AnnotatedString(decklist)) },
                enabled = decklist.isNotBlank() && !(exact && loadingExact),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
            ) { Text("Copy") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = TextMuted) }
        }
    )
}

@Composable
private fun ExportFormatChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) Bg else TextPrimary,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Gold else Bg)
            .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun DeckSettingsDialog(
    current: GameMode,
    onSelect: (GameMode) -> Unit,
    ownership: DeckOwnership,
    onOwnershipChange: (DeckOwnership) -> Unit,
    tags: List<String>,
    onTagsChange: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var tagInput by remember { mutableStateOf("") }
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
                Spacer(Modifier.height(20.dp))
                Text("Ownership", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeckOwnership.entries.forEach { option ->
                        val selected = option == ownership
                        Text(
                            option.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) Bg else TextPrimary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) Gold else Bg)
                                .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(50))
                                .clickable { onOwnershipChange(option) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
                Text(
                    ownership.description,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextDim,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Spacer(Modifier.height(20.dp))
                Text("Tags", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                Spacer(Modifier.height(8.dp))
                if (tags.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    ) {
                        tags.forEach { tag ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Bg)
                                    .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(50))
                                    .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                            ) {
                                Text(tag, style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                                IconButton(
                                    onClick = { onTagsChange(tags - tag) },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove tag \"$tag\"", tint = TextMuted, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    placeholder = { Text("e.g. Budget, Combo, Aggro", color = TextDim) },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            val trimmed = tagInput.trim()
                            if (trimmed.isNotEmpty() && trimmed !in tags) onTagsChange(tags + trimmed)
                            tagInput = ""
                        }) {
                            Icon(Icons.Filled.Add, contentDescription = "Add tag", tint = Gold)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Gold,
                        unfocusedBorderColor = BorderColor,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = Gold
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
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
private fun LegalityTab(analysis: DeckAnalysis, viewModel: DeckDetailViewModel) {
    val report = analysis.legality
    if (report == null) {
        LoadingBox()
        return
    }
    val context = LocalContext.current
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
            items(report.issues) { issue ->
                LegalityIssueRow(
                    issue,
                    onFix = if (issue.kind == LegalityIssueKind.COPY_LIMIT && issue.scryfallId != null && issue.fixQuantity != null) {
                        {
                            viewModel.setCardQuantity(issue.scryfallId, issue.fixQuantity)
                            val word = if (issue.fixQuantity == 1) "copy" else "copies"
                            Toast.makeText(context, "Reduced ${issue.card} to ${issue.fixQuantity} $word.", Toast.LENGTH_SHORT).show()
                        }
                    } else null
                )
            }
        }
    }
}

@Composable
private fun LegalityIssueRow(issue: LegalityIssue, onFix: (() -> Unit)?) {
    val haptic = LocalHapticFeedback.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .elevatedCard(shape = RoundedCornerShape(16.dp))
            .let {
                if (onFix != null) {
                    it.clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onFix() }
                } else it
            }
            .padding(12.dp)
    ) {
        Icon(
            Icons.Filled.Cancel,
            contentDescription = null,
            tint = Color(0xFFD3402F),
            modifier = Modifier.size(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            issue.card?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            }
            Text(issue.reason, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            if (onFix != null) {
                val word = if (issue.fixQuantity == 1) "COPY" else "COPIES"
                Text(
                    "TAP TO REDUCE TO ${issue.fixQuantity} $word",
                    style = MaterialTheme.typography.labelMedium,
                    color = Gold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun CardsTab(
    deck: Deck,
    analysis: DeckAnalysis,
    onZoomCard: (String) -> Unit,
    cardActions: (DeckCardEntry) -> List<CardMenuAction>,
    viewModel: DeckDetailViewModel
) {
    var query by remember { mutableStateOf("") }
    val trimmed = query.trim()
    val viewMode by viewModel.viewMode.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val cardGroups by viewModel.cardGroups.collectAsState()

    // Grouped by type instantly from cached data, refined once analysis resolves from Scryfall;
    // only falls back to one flat list for entries with no type info at all yet.
    val typeGroups = (
        when {
            analysis.byType.isNotEmpty() -> analysis.byType
            cardGroups.isNotEmpty() -> cardGroups
            else -> listOf(TypeGroup("Cards", deck.cards))
        }
        )
        .mapNotNull { group ->
            val cards = group.cards.filter { trimmed.isBlank() || it.name.contains(trimmed, ignoreCase = true) }
            if (cards.isEmpty()) null else group.copy(cards = cards)
        }

    // The commander (and partner commander, if set) render with the same full-card
    // DeckCardRow/DeckCardTile as everything else, but pulled out of their type groups into one
    // pinned section at the top of the list.
    val commanderIds = setOfNotNull(deck.commander?.scryfallId, deck.partnerCommander?.scryfallId)
    val commanderEntries = mutableListOf<DeckCardEntry>()
    val otherGroups = typeGroups.mapNotNull { group ->
        val rest = group.cards.filter { card ->
            val isCommander = card.scryfallId in commanderIds
            if (isCommander) commanderEntries += card
            !isCommander
        }
        if (rest.isEmpty()) null else group.copy(cards = rest)
    }
    val groups = if (commanderEntries.isNotEmpty()) {
        // Keep the main commander first, partner second, regardless of the order they were found in.
        val ordered = listOfNotNull(
            commanderEntries.find { it.scryfallId == deck.commander?.scryfallId },
            commanderEntries.find { it.scryfallId == deck.partnerCommander?.scryfallId }
        )
        listOf(TypeGroup("Commander", ordered)) + otherGroups
    } else otherGroups

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
                shape = RoundedCornerShape(8.dp),
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
            if (deck.cards.isEmpty()) {
                item {
                    Text(
                        "No cards yet. Add cards to this deck from a card's detail page.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                return@LazyColumn
            }
            if (groups.isEmpty()) {
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
                            isCommander = card.scryfallId == deck.commander?.scryfallId || card.scryfallId == deck.partnerCommander?.scryfallId,
                            onClick = { onZoomCard(card.scryfallId) },
                            actions = cardActions(card)
                        )
                    }
                } else {
                    items(group.cards, key = { it.scryfallId }) { card ->
                        DeckCardRow(
                            card = card,
                            isCommander = card.scryfallId == deck.commander?.scryfallId || card.scryfallId == deck.partnerCommander?.scryfallId,
                            onClick = { onZoomCard(card.scryfallId) },
                            actions = cardActions(card),
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
private fun StatsTab(analysis: DeckAnalysis, deck: Deck, viewModel: DeckDetailViewModel) {
    if (analysis.loading) {
        LoadingBox()
        return
    }
    var showLogResult by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Panel {
                val wins = deck.gameResults.count { it.result == "WIN" }
                val losses = deck.gameResults.count { it.result == "LOSS" }
                val draws = deck.gameResults.count { it.result == "DRAW" }
                val total = deck.gameResults.size
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    SectionLabel("MATCH RECORD")
                    TextButton(onClick = { showLogResult = true }) { Text("LOG RESULT", color = Gold, style = MaterialTheme.typography.labelMedium) }
                }
                if (total == 0) {
                    Text(
                        "No games logged yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("$wins-$losses" + if (draws > 0) "-$draws" else "", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${(wins * 100 / total)}% win rate over $total game${if (total == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GoldLight,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        deck.gameResults.sortedByDescending { it.playedAt }.take(5).forEach { game ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    game.result,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = when (game.result) {
                                        "WIN" -> Gold
                                        "LOSS" -> Color(0xFFD3402F)
                                        else -> TextMuted
                                    }
                                )
                                Text(
                                    game.opponent ?: "—",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { viewModel.removeGameResult(game.id) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove this result", tint = TextDim, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
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
                AnimatedUsdText(analysis.totalUsd, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
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
        if (analysis.colorPipCounts.isNotEmpty()) {
            item {
                Panel {
                    SectionLabel("MANA SYMBOLS")
                    val totalPips = analysis.colorPipCounts.sumOf { it.second }
                    Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        analysis.colorPipCounts.forEach { (color, count) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ManaSymbol(color, size = 16.dp)
                                Text(
                                    "$count",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    modifier = Modifier.width(28.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(BorderColor)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(count.toFloat() / totalPips)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Gold)
                                    )
                                }
                                Text(
                                    "${count * 100 / totalPips}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextMuted,
                                    modifier = Modifier.width(38.dp)
                                )
                            }
                        }
                    }
                    Text(
                        "$totalPips colored mana symbols across every card's cast cost — how much of each color this deck actually demands, not just how many lands produce it.",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextDim,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }
        }
        if (analysis.landCount > 0) {
            item {
                Panel {
                    SectionLabel("MANA BASE")
                    Text(
                        "${analysis.landCount} lands · ${analysis.deckSize} cards in library",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                    if (analysis.colorSourceCounts.isEmpty()) {
                        Text(
                            "No color-producing lands detected in this deck's cached data.",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextDim
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            analysis.colorSourceCounts.forEach { (color, sources) ->
                                val openingHand = probabilityAtLeastOne(analysis.deckSize, sources, 7)
                                val byTurn3 = probabilityAtLeastOne(analysis.deckSize, sources, 10)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    ManaSymbol(color, size = 16.dp)
                                    Text(
                                        "$sources sources",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary,
                                        modifier = Modifier.width(78.dp)
                                    )
                                    Text(
                                        "${(openingHand * 100).toInt()}% opening hand · ${(byTurn3 * 100).toInt()}% by turn 3",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextMuted
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        "Hypergeometric odds of drawing at least one source, on the draw.",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextDim,
                        modifier = Modifier.padding(top = 8.dp)
                    )
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

    if (showLogResult) {
        LogGameResultDialog(
            onConfirm = { result, opponent -> viewModel.logGameResult(result, opponent); showLogResult = false },
            onDismiss = { showLogResult = false }
        )
    }
}

@Composable
private fun LogGameResultDialog(onConfirm: (String, String?) -> Unit, onDismiss: () -> Unit) {
    var result by remember { mutableStateOf("WIN") }
    var opponent by remember { mutableStateOf("") }
    AlertDialog(
        containerColor = Surface,
        onDismissRequest = onDismiss,
        title = { Text("Log game result", color = GoldLight) },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("WIN", "LOSS", "DRAW").forEach { option ->
                        val selected = result == option
                        Text(
                            option,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) Bg else TextPrimary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) Gold else Bg)
                                .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(50))
                                .clickable { result = option }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = opponent,
                    onValueChange = { opponent = it },
                    label = { Text("Opponent (optional)", color = GoldDim) },
                    singleLine = true,
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
                onClick = { onConfirm(result, opponent.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
            ) { Text("LOG", color = Bg) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", color = TextMuted) }
        }
    )
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
            items(analysis.combos.take(10), key = { it.id }) { combo ->
                var showCombo by remember { mutableStateOf(false) }
                ComboRow(combo, onClick = { showCombo = true })
                if (showCombo) {
                    ComboDetailDialog(combo = combo, onDismiss = { showCombo = false })
                }
            }
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
            .elevatedCard()
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
                // The bar lives in whatever space is left after both text labels, so its height is
                // always a fraction of that leftover — never a fixed dp value. A hardcoded bar
                // height could add up with the labels to more than the Row's fixed height, pushing
                // the tallest bars' bucket labels out of the chart entirely (out of line with the
                // rest); sizing by fraction of the remaining space makes overflow impossible and
                // keeps every bucket label bottom-aligned at the same height.
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .fillMaxHeight(0.06f + 0.94f * count / max)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(if (count > 0) Gold else BorderColor)
                    )
                }
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
        Box(modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(8.dp)).background(Bg)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(count.toFloat() / max)
                    .height(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Gold)
            )
        }
        Text("$count", style = MaterialTheme.typography.bodySmall, color = GoldLight)
    }
}

@Composable
private fun ComboRow(combo: Variant, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .elevatedCard(shape = RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        // Commander Spellbook hands us each card's real Scryfall art directly, so a quick glance
        // at the combo shows the actual cards instead of just their names.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            combo.uses.forEach { usage ->
                AsyncImage(
                    model = usage.card.imageUriFrontArtCrop,
                    contentDescription = usage.card.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(width = 56.dp, height = 40.dp).clip(RoundedCornerShape(8.dp))
                )
            }
        }
        Text(
            combo.uses.joinToString(" + ") { it.card.name },
            style = MaterialTheme.typography.bodyMedium,
            color = GoldLight,
            modifier = Modifier.padding(top = 8.dp)
        )
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
            .elevatedCard(shape = RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        AsyncImage(
            model = view.scryfallImageUrl.toArtCropUrl(),
            contentDescription = view.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 72.dp, height = 52.dp).clip(RoundedCornerShape(10.dp))
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
            modifier = Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(14.dp))
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeckCardRow(
    card: DeckCardEntry,
    isCommander: Boolean,
    onClick: () -> Unit,
    actions: List<CardMenuAction>,
    onToggleCommander: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .pressScale(interactionSource)
                .elevatedCard(shape = RoundedCornerShape(16.dp))
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = androidx.compose.foundation.LocalIndication.current,
                    onClick = onClick,
                    onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuExpanded = true }
                )
                .padding(12.dp)
        ) {
            Box {
                AsyncImage(
                    model = card.imageUrl.toArtCropUrl(),
                    contentDescription = card.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(width = 72.dp, height = 52.dp).clip(RoundedCornerShape(10.dp))
                )
                if (card.backImageUrl != null) FlipBadge()
            }
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
        CardActionMenu(expanded = menuExpanded, onDismiss = { menuExpanded = false }, actions = actions)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeckCardTile(card: DeckCardEntry, isCommander: Boolean = false, onClick: () -> Unit, actions: List<CardMenuAction>) {
    var menuExpanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    Box {
        Column(
            modifier = Modifier.fillMaxWidth().pressScale(interactionSource).combinedClickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick,
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuExpanded = true }
            )
        ) {
            Box {
                AsyncImage(
                    model = card.imageUrl,
                    contentDescription = card.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(14.dp))
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
                // The commander star already claims this corner — don't stack both badges there.
                if (card.backImageUrl != null && !isCommander) FlipBadge()
                if (isCommander) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "Commander",
                        tint = Gold,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(4.dp)
                            .size(14.dp)
                    )
                }
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
        CardActionMenu(expanded = menuExpanded, onDismiss = { menuExpanded = false }, actions = actions)
    }
}

private fun deckCardActions(
    entry: DeckCardEntry,
    mode: GameMode,
    isCommander: Boolean,
    hasCommander: Boolean,
    isPartnerCommander: Boolean,
    canPartnerWithCommander: Boolean,
    onViewDetails: (String) -> Unit,
    onCopy: (DeckCardEntry) -> Unit,
    onMove: (DeckCardEntry) -> Unit,
    onRemove: (DeckCardEntry) -> Unit,
    onSetCommander: (DeckCardEntry?) -> Unit,
    onSetPartnerCommander: (DeckCardEntry?) -> Unit
): List<CardMenuAction> {
    val actions = mutableListOf<CardMenuAction>()
    if (entry.canBeCommander && mode == GameMode.COMMANDER) {
        actions += if (isCommander) {
            CardMenuAction("Remove as commander", Icons.Filled.Star) { onSetCommander(null) }
        } else {
            CardMenuAction("Set as commander", Icons.Outlined.Star) { onSetCommander(entry) }
        }
    }
    // Only offered once a main commander exists, for a card that isn't it, and that actually has a
    // valid Partner pairing with it (plain "Partner"+"Partner", or a matching "Partner with <name>").
    if (mode == GameMode.COMMANDER && hasCommander && !isCommander && (isPartnerCommander || canPartnerWithCommander)) {
        actions += if (isPartnerCommander) {
            CardMenuAction("Remove as partner commander", Icons.Filled.Star) { onSetPartnerCommander(null) }
        } else {
            CardMenuAction("Set as partner commander", Icons.Outlined.Star) { onSetPartnerCommander(entry) }
        }
    }
    actions += CardMenuAction("Add to another binder/deck", Icons.Filled.Add) { onCopy(entry) }
    actions += CardMenuAction("Move", Icons.AutoMirrored.Filled.DriveFileMove) { onMove(entry) }
    actions += CardMenuAction("Remove from deck", Icons.Filled.Close, destructive = true) { onRemove(entry) }
    actions += CardMenuAction("View details (EDHREC)", Icons.Filled.Info) { onViewDetails(entry.name) }
    return actions
}
