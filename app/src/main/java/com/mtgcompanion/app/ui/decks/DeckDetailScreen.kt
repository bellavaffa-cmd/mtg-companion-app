package com.mtgcompanion.app.ui.decks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckCardEntry
import com.mtgcompanion.app.network.edhrec.EdhrecCardView
import com.mtgcompanion.app.network.edhrec.inclusionPercent
import com.mtgcompanion.app.network.edhrec.scryfallImageUrl
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.network.spellbook.Variant
import com.mtgcompanion.app.ui.common.ManaSymbol
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.TextDim
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckDetailScreen(
    viewModel: DeckDetailViewModel,
    onBack: () -> Unit,
    onCardClick: (String) -> Unit = {}
) {
    val deck by viewModel.deck.collectAsState()
    val analysis by viewModel.analysis.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text(deck?.name ?: "Deck", color = GoldLight, style = MaterialTheme.typography.labelLarge) },
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
                            text = { Text("Import decklist", color = TextPrimary) },
                            onClick = { menuOpen = false; showImport = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Export decklist", color = TextPrimary) },
                            onClick = { menuOpen = false; showExport = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete deck", color = Color(0xFFD3402F)) },
                            onClick = { menuOpen = false; viewModel.deleteDeck(onBack) }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
            )
        }
    ) { padding ->
        val currentDeck = deck ?: return@Scaffold

        Column(modifier = Modifier.fillMaxSize().background(Bg).padding(padding)) {
            TabRow(selectedTabIndex = selectedTab, containerColor = Bg, contentColor = Gold) {
                listOf("CARDS", "STATS", "ANALYSIS").forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selectedTab == index) Gold else TextMuted
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> CardsTab(currentDeck, analysis, onCardClick, viewModel)
                1 -> StatsTab(analysis)
                else -> AnalysisTab(analysis, suggestions, onCardClick)
            }
        }

        if (showImport) {
            ImportDialog(
                onDismiss = { showImport = false },
                onImport = { text -> viewModel.importDecklist(text) { _, _ -> }; showImport = false }
            )
        }
        if (showExport) {
            ExportDialog(decklist = buildDecklist(currentDeck), onDismiss = { showExport = false })
        }
    }
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
private fun CardsTab(
    deck: Deck,
    analysis: DeckAnalysis,
    onCardClick: (String) -> Unit,
    viewModel: DeckDetailViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (deck.commander != null) {
            item { CommanderSection(deck, onClick = { deck.commander?.let { onCardClick(it.name) } }) }
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

        // Grouped by type once analysis has loaded; otherwise a flat list so cards show immediately.
        val groups = if (analysis.byType.isNotEmpty()) analysis.byType
        else listOf(TypeGroup("Cards", deck.cards))

        groups.forEach { group ->
            item {
                Text(
                    "${group.type.uppercase()} (${group.cards.sumOf { it.quantity }})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                )
            }
            items(group.cards, key = { it.scryfallId }) { card ->
                DeckCardRow(
                    card = card,
                    isCommander = deck.commander?.scryfallId == card.scryfallId,
                    onClick = { onCardClick(card.name) },
                    onToggleCommander = {
                        viewModel.setCommander(if (deck.commander?.scryfallId == card.scryfallId) null else card)
                    },
                    onRemove = { viewModel.removeCard(card.scryfallId) }
                )
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
    onCardClick: (String) -> Unit
) {
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
            else -> items(sug, key = { it.id ?: it.name }) { view -> SuggestionRow(view, onClick = { onCardClick(view.name) }) }
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
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Surface)
            .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        AsyncImage(
            model = view.scryfallImageUrl,
            contentDescription = view.name,
            modifier = Modifier.size(width = 40.dp, height = 56.dp).clip(RoundedCornerShape(3.dp))
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

@Composable
private fun DeckCardRow(
    card: DeckCardEntry,
    isCommander: Boolean,
    onClick: () -> Unit,
    onToggleCommander: () -> Unit,
    onRemove: () -> Unit
) {
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
            model = card.imageUrl.toArtCropUrl(),
            contentDescription = card.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 72.dp, height = 52.dp).clip(RoundedCornerShape(4.dp))
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(card.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text("Qty ${card.quantity}", style = MaterialTheme.typography.labelMedium, color = TextMuted)
        }
        if (card.canBeCommander) {
            IconButton(onClick = onToggleCommander) {
                Icon(
                    if (isCommander) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = "Set as commander",
                    tint = Gold
                )
            }
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = "Remove from deck", tint = TextDim)
        }
    }
}
