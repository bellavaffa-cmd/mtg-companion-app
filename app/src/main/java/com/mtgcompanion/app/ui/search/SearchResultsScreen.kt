package com.mtgcompanion.app.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.CardViewMode
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.common.CardZoomDialog
import com.mtgcompanion.app.ui.common.MoveTargetDialog
import com.mtgcompanion.app.ui.common.ZoomCard
import com.mtgcompanion.app.ui.common.cardGrid
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary

/** Results of the search built on SearchScreen — its own page, reached by pressing Search there. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultsScreen(
    viewModel: SearchViewModel,
    onCardClick: (ScryfallCard) -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val addTargets by viewModel.addTargets.collectAsState()
    // The card whose "Add to…" binder/deck picker is open.
    var addTarget by remember { mutableStateOf<ScryfallCard?>(null) }
    // Index (within the current results) of the card enlarged by a tap, if any.
    var zoomIndex by remember { mutableStateOf<Int?>(null) }
    val resultCards = (uiState as? SearchUiState.Success)?.cards.orEmpty()
    val listState = rememberLazyListState()

    // Scryfall paginates at 175 cards per page — fetch the next page once the user scrolls near
    // the bottom, so a broad query doesn't silently stop at the first page.
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo }
            .collect { layoutInfo ->
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@collect
                if (layoutInfo.totalItemsCount > 0 && lastVisible >= layoutInfo.totalItemsCount - 4) {
                    viewModel.loadMore()
                }
            }
    }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text("RESULTS", color = GoldLight, style = MaterialTheme.typography.labelLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Gold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize().background(Bg).padding(padding).padding(20.dp)
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
                                onAddToTarget = { addTarget = card },
                                onViewDetails = { onCardClick(card) }
                            )
                        }
                    } else {
                        items(state.cards, key = { it.id }) { card ->
                            CardResultRow(
                                card = card,
                                onClick = { zoomIndex = resultCards.indexOfFirst { it.id == card.id } },
                                onAddToTarget = { addTarget = card },
                                onViewDetails = { onCardClick(card) }
                            )
                        }
                    }
                    if (state.loadingMore) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) { CircularProgressIndicator(color = Gold) }
                        }
                    }
                }
            }
        }
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
                    cardName = card.name,
                    priceUsd = card.prices?.usd?.toDoubleOrNull(),
                    onAdd = { zoomIndex = null; addTarget = card },
                    // Picking a printing here goes straight into the normal add flow, so choosing
                    // art and saving it to a binder/deck is one motion instead of two pickers.
                    onSelectPrinting = { chosen -> zoomIndex = null; addTarget = chosen },
                    onViewDetails = { zoomIndex = null; onCardClick(card) }
                )
            },
            initialIndex = index,
            onDismiss = { zoomIndex = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CardResultRow(
    card: ScryfallCard,
    onClick: () -> Unit,
    onAddToTarget: () -> Unit,
    onViewDetails: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Surface)
                .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(4.dp))
                .combinedClickable(onClick = onClick, onLongClick = { menuExpanded = true })
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
        CardActionMenu(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            onAddToTarget = onAddToTarget,
            onViewDetails = onViewDetails
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CardResultTile(
    card: ScryfallCard,
    onClick: () -> Unit,
    onAddToTarget: () -> Unit,
    onViewDetails: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { menuExpanded = true })
        ) {
            AsyncImage(
                model = card.displayImageUrl,
                contentDescription = card.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(6.dp))
            )
        }
        CardActionMenu(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            onAddToTarget = onAddToTarget,
            onViewDetails = onViewDetails
        )
    }
}

/** Long-press quick-action menu, anchored directly to the card it was pressed on. */
@Composable
private fun CardActionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAddToTarget: () -> Unit,
    onViewDetails: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, modifier = Modifier.background(Surface)) {
        DropdownMenuItem(
            text = { Text("Add to binder/deck", color = TextPrimary, style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, tint = Gold) },
            onClick = { onDismiss(); onAddToTarget() }
        )
        DropdownMenuItem(
            text = { Text("View details (EDHREC)", color = TextPrimary, style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null, tint = Gold) },
            onClick = { onDismiss(); onViewDetails() }
        )
    }
}
