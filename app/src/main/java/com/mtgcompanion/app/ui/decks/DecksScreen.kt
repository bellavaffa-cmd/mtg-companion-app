package com.mtgcompanion.app.ui.decks

import com.mtgcompanion.app.ui.theme.OnGold
import com.mtgcompanion.app.ui.theme.NumberStyle
import com.mtgcompanion.app.ui.theme.LocalAppColors
import com.mtgcompanion.app.ui.common.sharedArt
import com.mtgcompanion.app.ui.common.riseIn
import com.mtgcompanion.app.ui.common.rememberEntranceWindow
import com.mtgcompanion.app.ui.common.pressScale
import com.mtgcompanion.app.ui.common.SharedKeys
import com.mtgcompanion.app.ui.common.SearchPill
import com.mtgcompanion.app.ui.common.PillChip
import com.mtgcompanion.app.ui.common.ManaPips
import com.mtgcompanion.app.ui.common.IdentityStrip
import com.mtgcompanion.app.ui.common.ArtImage
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckOwnership
import com.mtgcompanion.app.data.GameMode
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.common.GameModeDropdown
import com.mtgcompanion.app.ui.common.ManaSymbol
import com.mtgcompanion.app.ui.common.elevatedCard
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldDim
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecksScreen(viewModel: DecksViewModel, onDeckClick: (String) -> Unit, onBrowsePrecons: () -> Unit) {
    val decks by viewModel.decks.collectAsState()
    val commanderColors by viewModel.commanderColors.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var ownership by rememberSaveable { mutableStateOf<String?>(null) }
    val app = LocalAppColors.current
    val entering = rememberEntranceWindow()

    val shown = decks.filter { deck ->
        (ownership == null || deck.ownershipType.name == ownership) &&
            (query.isBlank() || deck.name.contains(query.trim(), ignoreCase = true) || deck.commander?.name?.contains(query.trim(), ignoreCase = true) == true)
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().background(Bg),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 18.dp, bottom = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.riseIn(0)) {
                    Text("Decks", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f).padding(start = 4.dp))
                    Box(
                        Modifier.size(42.dp).clip(CircleShape).background(app.surface).clickable(onClick = onBrowsePrecons),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Inventory2, contentDescription = "Browse precons", tint = app.textPrimary, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(42.dp).clip(CircleShape).background(app.accent).clickable { showCreateDialog = true },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Add, contentDescription = "New deck", tint = app.onAccent) }
                }
                if (decks.isNotEmpty()) {
                    SearchPill(query = query, onQueryChange = { query = it }, placeholder = "Search decks or commanders", modifier = Modifier.riseIn(1))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState()).riseIn(2)) {
                        PillChip("All", ownership == null, onClick = { ownership = null }, count = decks.size)
                        DeckOwnership.entries.forEach { type ->
                            val n = decks.count { it.ownershipType == type }
                            if (n > 0) PillChip(type.label, ownership == type.name, onClick = { ownership = if (ownership == type.name) null else type.name }, count = n)
                        }
                    }
                }
            }
        }

        if (decks.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                Text(
                    "No decks yet. Start from an official precon, or tap + to build one from scratch.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(4.dp)
                )
            }
        } else if (shown.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "nomatch") {
                Text("No decks match.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(4.dp))
            }
        }

        itemsIndexed(shown, key = { _, deck -> deck.id }) { index, deck ->
            // Colourless commander -> a single "C" pip; unknown (not fetched yet) -> none.
            val colors = commanderColors[deck.id]?.ifEmpty { listOf("C") }.orEmpty()
            DeckTile(
                deck = deck,
                colors = colors,
                onClick = { onDeckClick(deck.id) },
                modifier = Modifier.animateItem().riseIn(index + 3, entering)
            )
        }

        // Fills an odd row, and is the obvious next step when the list is short.
        if (query.isBlank() && ownership == null) {
            item(key = "precons") {
                PreconTile(onClick = onBrowsePrecons, modifier = Modifier.riseIn(shown.size + 3, entering))
            }
        }
    }

    if (showCreateDialog) {
        CreateDeckDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, mode ->
                showCreateDialog = false
                viewModel.createDeck(name, mode) { deck -> onDeckClick(deck.id) }
            }
        )
    }
}

@Composable
private fun DeckTile(deck: Deck, colors: List<String>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .pressScale(interaction)
            .fillMaxWidth()
            .aspectRatio(0.74f)
            .clip(RoundedCornerShape(22.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        ArtImage(
            model = deck.commander?.imageUrl.toArtCropUrl(),
            seed = deck.name,
            colors = colors,
            contentDescription = deck.commander?.name,
            modifier = Modifier.fillMaxSize().sharedArt(SharedKeys.deckArt(deck.id), RoundedCornerShape(22.dp))
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.3f to Color.Transparent, 0.62f to Color.Black.copy(alpha = 0.35f), 1f to Color.Black.copy(alpha = 0.95f)))
        )
        // Ownership badge, top-left: hidden for Physical decks (the default) so it only draws
        // attention when a deck is not counted toward the collection.
        if (deck.ownershipType != DeckOwnership.PHYSICAL) {
            Text(
                deck.ownershipType.label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 14.dp)
        ) {
            if (deck.tags.isNotEmpty()) {
                Text(
                    deck.tags.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalAppColors.current.accentLight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
            Text(
                deck.name,
                style = MaterialTheme.typography.titleSmall.copy(lineHeight = 18.sp),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                deck.commander?.name ?: "No commander set",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                if (colors.isNotEmpty()) ManaPips(colors, size = 15.dp)
                Spacer(Modifier.weight(1f))
                Text("${deck.cards.sumOf { it.quantity }}", style = NumberStyle(19), color = Color.White)
                Text(" cards", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
            }
        }
        IdentityStrip(colors, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), thickness = 3.dp)
    }
}

@Composable
private fun PreconTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val app = LocalAppColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.74f)
            .clip(RoundedCornerShape(22.dp))
            .background(app.surface)
            .clickable(onClick = onClick)
            .padding(18.dp)
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(app.surface3), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Inventory2, contentDescription = null, tint = app.textPrimary)
        }
        Spacer(Modifier.height(12.dp))
        Text("Start from a precon", style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        Text("Import any official Commander deck", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun CreateDeckDialog(onDismiss: () -> Unit, onConfirm: (String, GameMode) -> Unit) {
    var name by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(GameMode.DEFAULT) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("New deck", color = GoldLight, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Deck name", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Gold,
                        unfocusedBorderColor = BorderColor,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = Gold
                    )
                )
                GameModeDropdown(selected = mode, onSelect = { mode = it })
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim(), mode) },
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = OnGold)
            ) { Text("Create", color = OnGold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        }
    )
}
