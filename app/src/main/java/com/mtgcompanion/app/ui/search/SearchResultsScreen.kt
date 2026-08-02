package com.mtgcompanion.app.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.CardViewMode
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.common.CardActionMenu
import com.mtgcompanion.app.ui.common.CardMenuAction
import com.mtgcompanion.app.ui.common.CardZoomDialog
import com.mtgcompanion.app.ui.common.MoveTargetDialog
import com.mtgcompanion.app.ui.common.ShimmerPlaceholder
import com.mtgcompanion.app.ui.common.ZoomCard
import com.mtgcompanion.app.ui.common.cardGrid
import com.mtgcompanion.app.ui.common.elevatedCard
import com.mtgcompanion.app.ui.common.foilShine
import com.mtgcompanion.app.ui.common.pressScale
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldLight
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
                    EmptyState(
                        icon = Icons.Filled.SearchOff,
                        title = "Search a card name, or use Scryfall syntax like “is:commander c:g”."
                    )
                }
                is SearchUiState.Loading -> {
                    items(6) { i ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                        ) {
                            ShimmerPlaceholder(Modifier.size(width = 72.dp, height = 52.dp))
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                ShimmerPlaceholder(Modifier.fillMaxWidth(if (i % 2 == 0) 0.7f else 0.5f).height(14.dp))
                                ShimmerPlaceholder(Modifier.fillMaxWidth(0.35f).height(10.dp))
                            }
                        }
                    }
                }
                is SearchUiState.Error -> item {
                    EmptyState(
                        icon = Icons.Filled.ErrorOutline,
                        title = state.message,
                        actionLabel = "RETRY",
                        onAction = { viewModel.search() }
                    )
                }
                is SearchUiState.OfflineNoDatabase -> item {
                    EmptyState(
                        icon = Icons.Filled.CloudOff,
                        title = "You're offline. Download the card database in Settings to search cards without a connection."
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
                            EmptyState(icon = Icons.Filled.SearchOff, title = "No cards match.")
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
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .pressScale(interactionSource)
                .elevatedCard()
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = androidx.compose.foundation.LocalIndication.current,
                    onClick = onClick,
                    onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuExpanded = true }
                )
                .padding(12.dp)
        ) {
            AsyncImage(
                model = card.displayImageUrl.toArtCropUrl(),
                contentDescription = card.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 72.dp, height = 52.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .let { if (card.isRareOrMythic) it.foilShine() else it }
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
        CardActionMenu(expanded = menuExpanded, onDismiss = { menuExpanded = false }, actions = resultCardActions(onAddToTarget, onViewDetails))
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
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pressScale(interactionSource)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = androidx.compose.foundation.LocalIndication.current,
                    onClick = onClick,
                    onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuExpanded = true }
                )
        ) {
            AsyncImage(
                model = card.displayImageUrl,
                contentDescription = card.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(6.dp))
                    .let { if (card.isRareOrMythic) it.foilShine() else it }
            )
        }
        CardActionMenu(expanded = menuExpanded, onDismiss = { menuExpanded = false }, actions = resultCardActions(onAddToTarget, onViewDetails))
    }
}

private val ScryfallCard.isRareOrMythic: Boolean
    get() = rarity == "rare" || rarity == "mythic"

private fun resultCardActions(onAddToTarget: () -> Unit, onViewDetails: () -> Unit) = listOf(
    CardMenuAction("Add to binder/deck", Icons.Filled.Add, onClick = onAddToTarget),
    CardMenuAction("View details (EDHREC)", Icons.Filled.Info, onClick = onViewDetails)
)

/** A friendlier stand-in for a bare line of text — used for empty results, errors, and offline notices. */
@Composable
private fun EmptyState(
    icon: ImageVector,
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onAction,
                border = BorderStroke(1.dp, BorderColor),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Gold)
            ) { Text(actionLabel) }
        }
    }
}
