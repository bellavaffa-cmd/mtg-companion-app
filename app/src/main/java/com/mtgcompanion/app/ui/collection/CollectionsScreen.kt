package com.mtgcompanion.app.ui.collection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.CardViewMode
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.GridSize
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.common.CardZoomDialog
import com.mtgcompanion.app.ui.common.ConfirmDeleteDialog
import com.mtgcompanion.app.ui.common.ZoomCard
import com.mtgcompanion.app.ui.common.cardGrid
import com.mtgcompanion.app.ui.common.columns
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
fun CollectionsScreen(
    viewModel: CollectionsViewModel,
    onCollectionClick: (String) -> Unit
) {
    val collections by viewModel.collections.collectAsState()
    val allCards by viewModel.allCards.collectAsState()
    val dashboard by viewModel.dashboard.collectAsState()
    val prices by viewModel.prices.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val gridSize by viewModel.gridSize.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    // Page 0 = All Cards (left), page 1 = Binders (right). Swipe or tap the tabs to switch.
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text("COLLECTION", color = GoldLight, style = MaterialTheme.typography.labelLarge) },
                actions = {
                    if (pagerState.currentPage == 1) {
                        IconButton(onClick = { showCreateDialog = true }) {
                            Icon(Icons.Filled.Add, contentDescription = "New binder", tint = Gold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().background(Bg).padding(padding)) {
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Bg,
                contentColor = Gold
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text("ALL CARDS", style = MaterialTheme.typography.labelMedium, color = if (pagerState.currentPage == 0) Gold else TextMuted) }
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text("BINDERS", style = MaterialTheme.typography.labelMedium, color = if (pagerState.currentPage == 1) Gold else TextMuted) }
                )
            }

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                if (page == 0) {
                    AllCardsTab(
                        allCards = allCards,
                        dashboard = dashboard,
                        prices = prices,
                        viewMode = viewMode,
                        gridSize = gridSize
                    )
                } else {
                    CollectionsTab(
                        collections = collections,
                        onCollectionClick = onCollectionClick,
                        onDelete = { viewModel.deleteCollection(it) }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateCollectionDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                showCreateDialog = false
                viewModel.createCollection(name) { created -> onCollectionClick(created.id) }
            }
        )
    }
}

@Composable
private fun CollectionsTab(
    collections: List<Collection>,
    onCollectionClick: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    // Binder pending a delete-confirmation, if any.
    var confirmDelete by remember { mutableStateOf<Collection?>(null) }

    if (collections.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Text("No binders yet. Tap + to create one.", style = MaterialTheme.typography.bodySmall)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(collections, key = { it.id }) { collection ->
                CollectionRow(
                    collection = collection,
                    onClick = { onCollectionClick(collection.id) },
                    onDelete = { confirmDelete = collection }
                )
            }
        }
    }

    confirmDelete?.let { collection ->
        val total = collection.entries.sumOf { it.quantity + it.foilQuantity }
        ConfirmDeleteDialog(
            title = "Delete binder?",
            message = "\"${collection.name}\" and its $total card${if (total == 1) "" else "s"} will be " +
                "permanently deleted. This can't be undone.",
            onConfirm = { onDelete(collection.id); confirmDelete = null },
            onDismiss = { confirmDelete = null }
        )
    }
}

@Composable
private fun AllCardsTab(
    allCards: List<AllCardEntry>,
    dashboard: CollectionDashboard?,
    prices: Map<String, Double>,
    viewMode: CardViewMode,
    gridSize: GridSize
) {
    // Search filters the visible card list only; the dashboard still reflects the whole collection.
    var query by remember { mutableStateOf("") }
    val filtered = remember(allCards, query) {
        if (query.isBlank()) allCards
        else allCards.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }
    // Tapping a card enlarges it (swipeable through the filtered list) with value/total.
    var zoomId by remember { mutableStateOf<String?>(null) }

    if (allCards.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Text(
                "No cards owned yet. Cards you add to any binder or deck appear here.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { DashboardPanel(dashboard) }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search all cards", color = GoldDim) },
                    singleLine = true,
                    shape = RoundedCornerShape(2.dp),
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Gold) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Gold,
                        unfocusedBorderColor = BorderColor,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = Gold,
                        focusedContainerColor = Surface,
                        unfocusedContainerColor = Surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                val label = if (query.isBlank()) {
                    "${allCards.sumOf { it.total }} cards · ${allCards.size} unique (across all binders & decks)"
                } else {
                    "${filtered.size} of ${allCards.size} unique match"
                }
                Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
            }
            if (filtered.isEmpty()) {
                item {
                    Text("No cards match \"$query\".", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            } else {
                if (viewMode == CardViewMode.GRID) {
                    cardGrid(filtered, columns = gridSize.columns(), key = { it.scryfallId }) { card ->
                        AllCardTile(card = card, onClick = { zoomId = card.scryfallId })
                    }
                } else {
                    items(filtered, key = { it.scryfallId }) { card ->
                        AllCardRow(card = card, onClick = { zoomId = card.scryfallId })
                    }
                }
            }
        }
    }

    zoomId?.let { id ->
        // Owned cards across all binders/decks: show value, total, and which binders/decks hold it.
        val zoomCards = filtered.map { c ->
            ZoomCard(imageUrl = c.imageUrl, priceUsd = prices[c.scryfallId], quantity = c.total, sources = c.sources)
        }
        CardZoomDialog(zoomCards, filtered.indexOfFirst { it.scryfallId == id }.coerceAtLeast(0)) { zoomId = null }
    }
}

@Composable
private fun AllCardRow(card: AllCardEntry, onClick: () -> Unit) {
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
            Text(
                "${card.total} total",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun AllCardTile(card: AllCardEntry, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box {
            AsyncImage(
                model = card.imageUrl,
                contentDescription = card.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(6.dp))
            )
            Text(
                "×${card.total}",
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
        Text(
            card.name,
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun CollectionRow(collection: Collection, onClick: () -> Unit, onDelete: () -> Unit) {
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
        Icon(
            Icons.Filled.Collections,
            contentDescription = null,
            tint = GoldDim,
            modifier = Modifier.size(40.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(collection.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            val total = collection.entries.sumOf { it.quantity + it.foilQuantity }
            Text(
                "$total cards · ${collection.entries.size} unique",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete binder", tint = TextDim)
        }
    }
}

@Composable
private fun CreateCollectionDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("New binder", color = GoldLight, style = MaterialTheme.typography.titleMedium) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Binder name", color = GoldDim) },
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
