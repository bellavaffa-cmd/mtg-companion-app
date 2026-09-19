package com.mtgcompanion.app.ui.collection

import com.mtgcompanion.app.data.RoleTags
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.activity.compose.BackHandler
import com.mtgcompanion.app.ui.common.MoveTargetDialog
import com.mtgcompanion.app.ui.common.ConfirmDeleteDialog
import com.mtgcompanion.app.ui.common.SyncIconButton
import com.mtgcompanion.app.ui.common.zoomSource
import com.mtgcompanion.app.ui.common.adaptiveListColumns
import com.mtgcompanion.app.ui.common.adaptiveGridColumns
import com.mtgcompanion.app.ui.common.SegmentedTabs
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.CardViewMode
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionType
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.common.CardActionMenu
import com.mtgcompanion.app.ui.common.CardMenuAction
import com.mtgcompanion.app.ui.common.CardZoomDialog
import com.mtgcompanion.app.ui.common.SimilarCardsDialog
import com.mtgcompanion.app.ui.common.ConfirmDeleteDialog
import com.mtgcompanion.app.ui.common.FlipBadge
import com.mtgcompanion.app.ui.common.ZoomCard
import com.mtgcompanion.app.ui.common.cardGrid
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
fun CollectionsScreen(
    viewModel: CollectionsViewModel,
    onCollectionClick: (String) -> Unit,
    onViewDetails: (String) -> Unit,
    onShareCollection: (() -> Unit)? = null,
    // The Shared page (what friends share), when accounts are set up; [openShared] asks to show it.
    sharedPage: (@Composable () -> Unit)? = null,
    openShared: Boolean = false,
    onSharedOpened: () -> Unit = {},
    /** Opens a tag's automatic binder. */
    onOpenTag: (String) -> Unit = {}
) {
    val tagBinders by viewModel.tagBinders.collectAsState()
    val tagging by viewModel.tagging.collectAsState()
    val tagVersion by RoleTags.version.collectAsState()
    val collections by viewModel.collections.collectAsState()
    val allCards by viewModel.allCards.collectAsState()
    val dashboard by viewModel.dashboard.collectAsState()
    val prices by viewModel.prices.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    val importProgress by viewModel.importProgress.collectAsState()
    val unsorted by viewModel.unsorted.collectAsState()
    // Page 0 = All Cards, 1 = Binders, 2 = Shared (with accounts). Swipe or tap the tabs to switch.
    val pageCount = if (sharedPage != null) 3 else 2
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    LaunchedEffect(openShared) {
        if (openShared && sharedPage != null) {
            pagerState.scrollToPage(2)
            onSharedOpened()
        }
    }
    val binderTargets by viewModel.binderTargets.collectAsState()
    val deckTargets by viewModel.deckTargets.collectAsState()
    // All cards' search: it filters the list, and Select all takes what it shows.
    var query by remember { mutableStateOf("") }
    // A card's name or one of its tags.
    val filtered = remember(allCards, query, tagVersion) {
        if (query.isBlank()) allCards else allCards.filter { RoleTags.matches(it.name, RoleTags.tagsOf(it.name).orEmpty(), query) }
    }
    // Cards picked on All cards by pressing and holding (scryfall ids), and the action open for
    // them: "binder", "deck", "export" or "remove". Cards no longer owned drop from the pick.
    var selected by remember { mutableStateOf(setOf<String>()) }
    var bulk by remember { mutableStateOf<String?>(null) }
    val picked = allCards.filter { it.scryfallId in selected }
    val pickedIds = picked.map { it.scryfallId }.toSet()
    val selecting = pickedIds.isNotEmpty()
    fun toggle(id: String) {
        selected = if (id in pickedIds) pickedIds - id else pickedIds + id
    }
    BackHandler(enabled = selecting) { selected = emptySet() }

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            if (selecting) SelectionActionBar(
                listOf(
                    SelectionAction("To binder", Icons.AutoMirrored.Filled.DriveFileMove) { bulk = "binder" },
                    SelectionAction("To deck", Icons.Filled.Style) { bulk = "deck" },
                    SelectionAction("Export", Icons.Filled.IosShare) { bulk = "export" },
                    SelectionAction("Remove", Icons.Filled.Delete, destructive = true) { bulk = "remove" }
                )
            )
        },
        topBar = {
            if (selecting) SelectionTopBar(
                count = pickedIds.size,
                total = allCards.size,
                onSelectAll = { selected = pickedIds + filtered.map { it.scryfallId } },
                onClear = { selected = emptySet() }
            ) else TopAppBar(
                title = { Text("Collection", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                actions = {
                    SyncIconButton()
                    if (onShareCollection != null) {
                        IconButton(onClick = onShareCollection) {
                            Icon(Icons.Filled.GroupAdd, contentDescription = "Share my collection", tint = TextPrimary)
                        }
                    }
                    IconButton(onClick = { viewModel.resetImport(); showImport = true }) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Import cards from another app", tint = TextPrimary)
                    }
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
        if (showImport) {
            ImportCardsDialog(
                title = "Import cards",
                askName = true,
                progress = importProgress,
                onImport = viewModel::importBinder,
                onDismiss = { showImport = false; viewModel.resetImport() },
                // From All cards, the whole collection comes in unsorted; from Binders, as a binder.
                startInNewBinder = pagerState.currentPage == 1
            )
        }
        Column(modifier = Modifier.fillMaxSize().background(Bg).padding(padding)) {
            SegmentedTabs(
                labels = if (sharedPage != null) listOf("All cards", "Binders", "Shared") else listOf("All cards", "Binders"),
                selected = pagerState.currentPage,
                onSelect = { page -> scope.launch { pagerState.animateScrollToPage(page) } },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // While cards are picked, a swipe mustn't carry the pick off to Binders.
            HorizontalPager(state = pagerState, userScrollEnabled = !selecting, modifier = Modifier.fillMaxSize()) { page ->
                if (page == 0) {
                    AllCardsTab(
                        unsorted = unsorted,
                        onOpenUnsorted = { onCollectionClick(it) },
                        onImport = { viewModel.resetImport(); showImport = true },
                        allCards = allCards,
                        query = query,
                        onQueryChange = { query = it },
                        filtered = filtered,
                        selecting = selecting,
                        pickedIds = pickedIds,
                        onToggle = ::toggle,
                        dashboard = dashboard,
                        prices = prices,
                        viewMode = viewMode,
                        gridColumns = gridColumns,
                        onViewDetails = onViewDetails,
                        viewModel = viewModel
                    )
                } else if (page == 2 && sharedPage != null) {
                    sharedPage()
                } else {
                    CollectionsTab(
                        collections = collections.filterNot { it.isUnsorted },
                        onCollectionClick = onCollectionClick,
                        onDelete = { viewModel.deleteCollection(it) },
                        tagBinders = tagBinders,
                        tagging = tagging,
                        onOpenTag = onOpenTag
                    )
                }
            }
        }
    }

    val pickedLabel = if (picked.size == 1) picked.first().name else "${picked.size} cards"
    val done = { bulk = null; selected = emptySet() }
    when (bulk) {
        "binder" -> MoveTargetDialog(
            cardName = pickedLabel,
            targets = binderTargets,
            onPick = { target -> viewModel.gatherIntoBinder(pickedIds, target.id); done() },
            onDismiss = { bulk = null },
            onNewBinder = { name -> viewModel.gatherIntoNewBinder(pickedIds, name); done() },
            title = "Move $pickedLabel into"
        )
        "deck" -> MoveTargetDialog(
            cardName = pickedLabel,
            targets = deckTargets,
            onPick = { target -> viewModel.addToDeck(pickedIds, target.id); done() },
            onDismiss = { bulk = null },
            title = "Add $pickedLabel to"
        )
        "remove" -> {
            val copies = viewModel.copiesInBinders(pickedIds)
            ConfirmDeleteDialog(
                title = if (picked.size == 1) "Remove from collection?" else "Remove ${picked.size} cards from collection?",
                message = "Removes $pickedLabel ($copies cop${if (copies == 1) "y" else "ies"}) from all your binders. Copies in decks and wishlists stay.",
                confirmLabel = "Remove",
                onConfirm = { viewModel.removeFromCollection(pickedIds); done() },
                onDismiss = { bulk = null }
            )
        }
        "export" -> ExportCollectionDialog(pickedLabel, { exact -> viewModel.exportText(pickedIds, exact) }, title = "Export $pickedLabel") { bulk = null }
    }

    if (showCreateDialog) {
        CreateCollectionDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, type ->
                showCreateDialog = false
                viewModel.createCollection(name, type) { created -> onCollectionClick(created.id) }
            }
        )
    }
}

@Composable
private fun CollectionsTab(
    collections: List<Collection>,
    onCollectionClick: (String) -> Unit,
    onDelete: (String) -> Unit,
    tagBinders: List<TagBinder>,
    tagging: Pair<Int, Int>?,
    onOpenTag: (String) -> Unit
) {
    // Binder pending a delete-confirmation, if any.
    var confirmDelete by remember { mutableStateOf<Collection?>(null) }
    val listCols = adaptiveListColumns()

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
            cardGrid(collections, columns = listCols, key = { it.id }) { collection ->
                CollectionRow(
                    collection = collection,
                    onClick = { onCollectionClick(collection.id) },
                    onDelete = { confirmDelete = collection }
                )
            }
            if (tagBinders.isNotEmpty() || tagging != null) {
                item(key = "tag-binders") { TagBindersSection(tagBinders, tagging, onOpenTag) }
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
    unsorted: Collection?,
    onOpenUnsorted: (String) -> Unit,
    onImport: () -> Unit,
    allCards: List<AllCardEntry>,
    // Search filters the visible card list only; the dashboard still reflects the whole collection.
    query: String,
    onQueryChange: (String) -> Unit,
    filtered: List<AllCardEntry>,
    selecting: Boolean,
    pickedIds: Set<String>,
    onToggle: (String) -> Unit,
    dashboard: CollectionDashboard?,
    prices: Map<String, Double>,
    viewMode: CardViewMode,
    gridColumns: Int,
    onViewDetails: (String) -> Unit,
    viewModel: CollectionsViewModel
) {
    // Tapping a card enlarges it (swipeable through the filtered list) with value/total.
    var zoomId by remember { mutableStateOf<String?>(null) }
    // Name of the card whose "find similar" overlay is open, if any.
    var similarSearchFor by remember { mutableStateOf<String?>(null) }
    val gridCols = adaptiveGridColumns(gridColumns)
    val listCols = adaptiveListColumns()

    if (allCards.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "No cards owned yet. Cards you add to any binder or deck appear here.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(onClick = onImport) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = Gold, modifier = Modifier.size(18.dp))
                Text("  Import your collection", color = TextPrimary)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (unsorted != null && unsorted.entries.isNotEmpty()) {
                item { UnsortedRow(unsorted) { onOpenUnsorted(unsorted.id) } }
            }
            item { DashboardPanel(dashboard) }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = { Text("Name or tag, e.g. ramp", color = TextMuted) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
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
                    cardGrid(filtered, columns = gridCols, key = { it.scryfallId }) { card ->
                        AllCardTile(
                            card = card,
                            selecting = selecting,
                            selected = card.scryfallId in pickedIds,
                            onClick = { if (selecting) onToggle(card.scryfallId) else zoomId = card.scryfallId },
                            onLongClick = { onToggle(card.scryfallId) }
                        )
                    }
                } else {
                    cardGrid(filtered, columns = listCols, key = { it.scryfallId }) { card ->
                        AllCardRow(
                            card = card,
                            selecting = selecting,
                            selected = card.scryfallId in pickedIds,
                            onClick = { if (selecting) onToggle(card.scryfallId) else zoomId = card.scryfallId },
                            onLongClick = { onToggle(card.scryfallId) }
                        )
                    }
                }
            }
        }
    }

    zoomId?.let { id ->
        // Owned cards across all binders/decks: show value, total, and which binders/decks hold it.
        val zoomCards = filtered.map { c ->
            ZoomCard(
                imageUrl = c.imageUrl,
                cardName = c.name,
                priceUsd = prices[c.scryfallId],
                quantity = c.total,
                sources = c.sources,
                // This entry is one printing shared by every binder/deck in its sources, so
                // re-arting it updates the printing everywhere it's held, not just one place.
                onSelectPrinting = { chosen -> viewModel.changePrintingEverywhere(c.scryfallId, chosen) },
                onViewDetails = { zoomId = null; onViewDetails(c.name) },
                backImageUrl = c.backImageUrl,
                tags = c.tags,
                // No "add" here — an All Cards entry already lives in a specific binder/deck, and
                // this tab has no destination-picker of its own to add a brand-new card into.
                onFindSimilar = { zoomId = null; similarSearchFor = c.name }
            )
        }
        CardZoomDialog(zoomCards, filtered.indexOfFirst { it.scryfallId == id }.coerceAtLeast(0)) { zoomId = null }
    }

    similarSearchFor?.let { name ->
        SimilarCardsDialog(
            cardName = name,
            onDismiss = { similarSearchFor = null },
            onViewDetails = { similar -> similarSearchFor = null; onViewDetails(similar.name) }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AllCardRow(card: AllCardEntry, selecting: Boolean, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .pressScale(interactionSource)
                .clip(RoundedCornerShape(10.dp))
                .background(Surface)
                .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Gold else BorderColor), RoundedCornerShape(10.dp))
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = androidx.compose.foundation.LocalIndication.current,
                    onClick = onClick,
                    onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() }
                )
                .padding(12.dp)
        ) {
            Box {
                AsyncImage(
                    model = card.imageUrl.toArtCropUrl(),
                    contentDescription = card.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.zoomSource(card.imageUrl).size(width = 72.dp, height = 52.dp).clip(RoundedCornerShape(10.dp))
                )
                if (card.backImageUrl != null) FlipBadge()
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(card.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Text(
                    "${card.total} total",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted
                )
            }
            if (selecting) SelectionMark(selected)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AllCardTile(card: AllCardEntry, selecting: Boolean, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    Box {
        Column(
            modifier = Modifier.fillMaxWidth().pressScale(interactionSource)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = androidx.compose.foundation.LocalIndication.current,
                    onClick = onClick,
                    onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() }
                )
        ) {
            Box {
                AsyncImage(
                    model = card.imageUrl,
                    contentDescription = card.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.zoomSource(card.imageUrl).fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(14.dp))
                        .let { if (selected) it.border(BorderStroke(3.dp, Gold), RoundedCornerShape(14.dp)) else it }
                )
                if (selecting) SelectionMark(selected, Modifier.align(Alignment.TopStart).padding(6.dp))
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
                if (card.backImageUrl != null) FlipBadge()
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CollectionRow(collection: Collection, onClick: () -> Unit, onDelete: () -> Unit) {
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
                .clip(RoundedCornerShape(10.dp))
                .background(Surface)
                .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(10.dp))
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = androidx.compose.foundation.LocalIndication.current,
                    onClick = onClick,
                    onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuExpanded = true }
                )
                .padding(12.dp)
        ) {
            Icon(
                if (collection.kind == CollectionType.WISHLIST) Icons.Filled.Star else Icons.Filled.Collections,
                contentDescription = null,
                tint = GoldDim,
                modifier = Modifier.size(40.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(collection.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                val total = collection.entries.sumOf { it.quantity + it.foilQuantity }
                val label = if (collection.kind == CollectionType.WISHLIST) "WISHLIST · " else ""
                Text(
                    "$label$total cards · ${collection.entries.size} unique",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete binder", tint = TextDim)
            }
        }
        CardActionMenu(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            actions = listOf(CardMenuAction("Delete binder", Icons.Filled.Delete, destructive = true) { onDelete() })
        )
    }
}

/** The Unsorted pile on All cards: cards owned but not in a binder yet, opened to sort them. */
@Composable
private fun UnsortedRow(unsorted: Collection, onClick: () -> Unit) {
    val total = unsorted.entries.sumOf { it.quantity + it.foilQuantity }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
            .border(BorderStroke(1.dp, GoldDim), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Icon(Icons.Filled.Inbox, contentDescription = null, tint = Gold, modifier = Modifier.size(32.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Unsorted", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text(
                "$total card${if (total == 1) "" else "s"} not in a binder yet — tap to sort them",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextDim)
    }
}

@Composable
private fun CreateCollectionDialog(onDismiss: () -> Unit, onConfirm: (String, CollectionType) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(CollectionType.OWNED) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("New binder", color = GoldLight, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Binder name", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Gold,
                        unfocusedBorderColor = BorderColor,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = Gold
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(CollectionType.OWNED to "Owned", CollectionType.WISHLIST to "Wishlist").forEach { (option, label) ->
                        val selected = type == option
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) Bg else TextPrimary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) Gold else Bg)
                                .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(50))
                                .clickable { type = option }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
                if (type == CollectionType.WISHLIST) {
                    Text(
                        "Wishlist binders track cards you want — they're excluded from your owned totals and All Cards.",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim(), type) },
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
            ) { Text("Create", color = Bg) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        }
    )
}
