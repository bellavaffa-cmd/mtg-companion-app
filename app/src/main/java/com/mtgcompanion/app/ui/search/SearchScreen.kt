package com.mtgcompanion.app.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.CardViewMode
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.common.CardActionSheet
import com.mtgcompanion.app.ui.common.CardMenuAction
import com.mtgcompanion.app.ui.common.CardZoomDialog
import com.mtgcompanion.app.ui.common.MoveTargetDialog
import com.mtgcompanion.app.ui.common.ZoomCard
import com.mtgcompanion.app.ui.common.cardGrid
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldDim
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.TextDim
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onCardClick: (ScryfallCard) -> Unit,
    onOpenFilters: () -> Unit
) {
    val query by viewModel.query.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val addTargets by viewModel.addTargets.collectAsState()
    // The card whose long-press quick-action menu is open.
    var menuTarget by remember { mutableStateOf<ScryfallCard?>(null) }
    // The card whose "Add to…" binder/deck picker is open.
    var addTarget by remember { mutableStateOf<ScryfallCard?>(null) }
    // Index (within the current results) of the card enlarged by a tap, if any.
    var zoomIndex by remember { mutableStateOf<Int?>(null) }
    val resultCards = (uiState as? SearchUiState.Success)?.cards.orEmpty()

    Scaffold(
        containerColor = Bg,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            "MTG COMPANION",
                            style = MaterialTheme.typography.labelLarge,
                            color = GoldLight
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
                )
                GoldDivider()
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().background(Bg).padding(padding).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::onQueryChange,
                    label = { Text("Search cards", color = GoldDim) },
                    placeholder = { Text("Try \"is:commander c:g\"", color = TextDim) },
                    singleLine = true,
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
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { viewModel.randomCard(onCardClick) }) {
                    Icon(Icons.Filled.Shuffle, contentDescription = "Random card", tint = TextMuted)
                }
                IconButton(onClick = onOpenFilters) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Icon(Icons.Filled.Tune, contentDescription = "Filters", tint = if (filters.isActive) Gold else TextMuted)
                        if (filters.isActive) {
                            Box(
                                Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(Gold)
                            )
                        }
                    }
                }
            }

            if (suggestions.isNotEmpty()) {
                SuggestionsDropdown(suggestions, onPick = viewModel::pickSuggestion)
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                when (val state = uiState) {
                    is SearchUiState.Idle -> item {
                        Text(
                            "Search a card name, or use Scryfall syntax like “is:commander c:g”.",
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                    is SearchUiState.Loading -> item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) { CircularProgressIndicator(color = Gold) }
                    }
                    is SearchUiState.Error -> item {
                        Text(
                            state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                    is SearchUiState.OfflineNoDatabase -> item {
                        Text(
                            "You're offline. Download the card database in Settings to search cards without a connection.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                    is SearchUiState.Success -> {
                        if (state.offline) {
                            item {
                                Text(
                                    "Offline — showing results from your downloaded card database.",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Gold,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                )
                            }
                        }
                        if (state.cards.isEmpty()) {
                            item {
                                Text(
                                    "No cards match.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontStyle = FontStyle.Italic,
                                    modifier = Modifier.padding(top = 16.dp)
                                )
                            }
                        } else if (viewMode == CardViewMode.GRID) {
                            cardGrid(state.cards, columns = gridColumns, key = { it.id }) { card ->
                                CardResultTile(
                                    card = card,
                                    onClick = { zoomIndex = resultCards.indexOfFirst { it.id == card.id } },
                                    onLongClick = { menuTarget = card }
                                )
                            }
                        } else {
                            items(state.cards, key = { it.id }) { card ->
                                CardResultRow(
                                    card = card,
                                    onClick = { zoomIndex = resultCards.indexOfFirst { it.id == card.id } },
                                    onLongClick = { menuTarget = card }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    menuTarget?.let { card ->
        CardActionSheet(
            cardName = card.name,
            actions = listOf(
                CardMenuAction("Add to binder/deck", Icons.Filled.Add) { addTarget = card },
                CardMenuAction("View details (EDHREC)", Icons.Filled.Info) { onCardClick(card) }
            ),
            onDismiss = { menuTarget = null }
        )
    }

    addTarget?.let { card ->
        MoveTargetDialog(
            cardName = card.name,
            targets = addTargets,
            onPick = { target -> viewModel.addToTarget(card, target); addTarget = null },
            onDismiss = { addTarget = null }
        )
    }

    zoomIndex?.let { index ->
        CardZoomDialog(
            cards = resultCards.map { card ->
                ZoomCard(
                    imageUrl = card.displayImageUrl,
                    priceUsd = card.prices?.usd?.toDoubleOrNull(),
                    onAdd = { zoomIndex = null; addTarget = card }
                )
            },
            initialIndex = index,
            onDismiss = { zoomIndex = null }
        )
    }
}

/** Name suggestions below the search bar, from Scryfall's autocomplete endpoint. */
@Composable
private fun SuggestionsDropdown(suggestions: List<String>, onPick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Surface)
            .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(4.dp))
    ) {
        suggestions.forEach { name ->
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(name) }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun GoldDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(BorderColor, Gold.copy(alpha = 0.5f), BorderColor)
                )
            )
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CardResultRow(card: ScryfallCard, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Surface)
            .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(4.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(12.dp)
    ) {
        AsyncImage(
            model = card.displayImageUrl.toArtCropUrl(),
            contentDescription = card.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 72.dp, height = 52.dp).clip(RoundedCornerShape(4.dp))
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(card.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text(
                (card.typeLine ?: "").uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        card.prices?.usd?.let { usd ->
            Text("$$usd", style = MaterialTheme.typography.bodyMedium, color = GoldLight)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CardResultTile(card: ScryfallCard, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Box {
            AsyncImage(
                model = card.displayImageUrl,
                contentDescription = card.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(6.dp))
            )
            card.prices?.usd?.let { usd ->
                Text(
                    "$$usd",
                    style = MaterialTheme.typography.labelMedium,
                    color = GoldLight,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
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
}
