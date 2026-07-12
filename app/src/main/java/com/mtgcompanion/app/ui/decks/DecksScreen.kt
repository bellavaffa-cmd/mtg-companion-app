package com.mtgcompanion.app.ui.decks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.common.ManaSymbol
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
fun DecksScreen(viewModel: DecksViewModel, onDeckClick: (String) -> Unit) {
    val decks by viewModel.decks.collectAsState()
    val commanderColors by viewModel.commanderColors.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text("DECKS", color = GoldLight, style = MaterialTheme.typography.labelLarge) },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "New deck", tint = Gold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
            )
        }
    ) { padding ->
        if (decks.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().background(Bg).padding(padding).padding(20.dp)) {
                Text(
                    "No decks yet. Tap + to build your first deck.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            LazyVerticalGrid(
                // Adaptive: as many ~160dp columns as fit — 2 on a phone, more on wider screens.
                columns = GridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier.fillMaxSize().background(Bg).padding(padding),
                contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(decks, key = { it.id }) { deck ->
                    // Colourless commander -> a single "C" pip; unknown (not fetched yet) -> none.
                    val colors = commanderColors[deck.id]?.ifEmpty { listOf("C") }.orEmpty()
                    DeckTile(deck = deck, colors = colors, onClick = { onDeckClick(deck.id) })
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateDeckDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                showCreateDialog = false
                viewModel.createDeck(name) { deck -> onDeckClick(deck.id) }
            }
        )
    }
}

@Composable
private fun DeckTile(deck: Deck, colors: List<String>, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(Surface)
            .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = deck.commander?.imageUrl.toArtCropUrl(),
            contentDescription = deck.commander?.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Commander colour identity pips, top-right over the art.
        if (colors.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 5.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                colors.forEach { ManaSymbol(it, size = 16.dp) }
            }
        }
        // Dark scrim over the lower half so the overlaid name stays legible on bright art.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.4f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.9f)
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                deck.name,
                style = MaterialTheme.typography.titleSmall,
                color = GoldLight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                (deck.commander?.name ?: "No commander set") + " · ${deck.cards.sumOf { it.quantity }} cards",
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CreateDeckDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("New deck", color = GoldLight, style = MaterialTheme.typography.titleMedium) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Deck name", color = GoldDim) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Gold,
                    unfocusedBorderColor = BorderColor,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Gold
                )
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
            ) { Text("CREATE", color = Bg) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", color = TextMuted) }
        }
    )
}
