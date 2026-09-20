package com.mtgcompanion.app.ui.collection

import com.mtgcompanion.app.data.decksConsidering
import com.mtgcompanion.app.data.isWishlist
import com.mtgcompanion.app.ui.common.rememberMoney
import com.mtgcompanion.app.data.RoleTags
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import com.mtgcompanion.app.data.PriceAlerts
import com.mtgcompanion.app.data.CollectionType
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.mtgcompanion.app.ui.theme.Surface2
import com.mtgcompanion.app.ui.theme.LocalAppColors
import com.mtgcompanion.app.ui.common.SyncIconButton
import com.mtgcompanion.app.ui.common.zoomSource
import com.mtgcompanion.app.ui.common.adaptiveListColumns
import com.mtgcompanion.app.ui.common.adaptiveGridColumns
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.CardViewMode
import com.mtgcompanion.app.data.CollectionEntry
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import androidx.activity.compose.BackHandler
import com.mtgcompanion.app.ui.common.CardZoomDialog
import com.mtgcompanion.app.ui.common.SimilarCardsDialog
import com.mtgcompanion.app.ui.common.ConfirmDeleteDialog
import com.mtgcompanion.app.ui.common.FlipBadge
import com.mtgcompanion.app.ui.common.MoveTargetDialog
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailScreen(
    viewModel: CollectionDetailViewModel,
    onBack: () -> Unit,
    onViewDetails: (String) -> Unit,
    onShare: (() -> Unit)? = null
) {
    val collection by viewModel.collection.collectAsState()
    val entries by viewModel.entries.collectAsState()
    val query by viewModel.query.collectAsState()
    val dashboard by viewModel.dashboard.collectAsState()
    val prices by viewModel.prices.collectAsState()
    val cardTags by viewModel.cardTags.collectAsState()
    val tagging by viewModel.tagging.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val gridCols = adaptiveGridColumns(gridColumns)
    val listCols = adaptiveListColumns()
    val moveTargets by viewModel.moveTargets.collectAsState()
    val cardSources by viewModel.cardSources.collectAsState()
    // The card whose move-destination picker is open.
    var moveTarget by remember { mutableStateOf<CollectionEntry?>(null) }
    // Tapping a card enlarges it (swipeable), showing value/total and a quantity stepper.
    var zoomId by remember { mutableStateOf<String?>(null) }
    // The card pending a remove-confirmation, if any.
    var removeTarget by remember { mutableStateOf<CollectionEntry?>(null) }
    var alertTarget by remember { mutableStateOf<CollectionEntry?>(null) }
    val isWishlist = collection?.kind == CollectionType.WISHLIST
    var confirmDeleteBinder by remember { mutableStateOf(false) }
    val decks by viewModel.decks.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    var listDialog by remember { mutableStateOf<String?>(null) } // "import" or "export"
    val importProgress by viewModel.importProgress.collectAsState()
    // Cards picked by pressing and holding (scryfall ids), and the action open for them:
    // "move", "copy", "remove" or "export".
    var selected by remember { mutableStateOf(setOf<String>()) }
    var bulk by remember { mutableStateOf<String?>(null) }
    // Cards removed or moved out drop from the pick.
    val picked = collection?.entries.orEmpty().filter { it.scryfallId in selected }
    val pickedIds = picked.map { it.scryfallId }.toSet()
    val selecting = pickedIds.isNotEmpty()
    fun toggle(entry: CollectionEntry) {
        selected = if (entry.scryfallId in pickedIds) pickedIds - entry.scryfallId else pickedIds + entry.scryfallId
    }
    BackHandler(enabled = selecting) { selected = emptySet() }
    // Name of the card whose "find similar" overlay is open, if any.
    var similarSearchFor by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            if (selecting) SelectionActionBar(
                listOf(
                    SelectionAction("Move", Icons.AutoMirrored.Filled.DriveFileMove) { bulk = "move" },
                    SelectionAction("Copy to", Icons.Filled.ContentCopy) { bulk = "copy" },
                    SelectionAction("Export", Icons.Filled.IosShare) { bulk = "export" },
                    SelectionAction("Remove", Icons.Filled.Delete, destructive = true) { bulk = "remove" }
                )
            )
        },
        topBar = {
            if (selecting) SelectionTopBar(
                count = pickedIds.size,
                total = entries.size,
                onSelectAll = { selected = pickedIds + entries.map { it.scryfallId } },
                onClear = { selected = emptySet() }
            ) else TopAppBar(
                title = { Text(collection?.name ?: "Binder", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Gold)
                    }
                },
                actions = {
                    SyncIconButton()
                    if (onShare != null) {
                        IconButton(onClick = onShare) {
                            Icon(Icons.Filled.GroupAdd, contentDescription = "Share with friends", tint = TextPrimary)
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Binder actions", tint = TextPrimary)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, modifier = Modifier.background(Surface2)) {
                            DropdownMenuItem(text = { Text("Import cards", color = TextPrimary) }, onClick = { menuOpen = false; viewModel.resetImport(); listDialog = "import" })
                            DropdownMenuItem(text = { Text("Export as text", color = TextPrimary) }, onClick = { menuOpen = false; listDialog = "export" })
                            // The Wishlist is always there.
                            if (collection?.isWishlist != true) DropdownMenuItem(
                                text = { Text(if (collection?.isUnsorted == true) "Remove all cards" else "Delete binder", color = LocalAppColors.current.error) },
                                onClick = { menuOpen = false; confirmDeleteBinder = true }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
            )
        }
    ) { padding ->
        when (listDialog) {
            "import" -> ImportCardsDialog(
                title = "Import into ${collection?.name ?: "binder"}",
                askName = false,
                progress = importProgress,
                onImport = { _, text -> viewModel.importCards(text) },
                onDismiss = { listDialog = null; viewModel.resetImport() }
            )
            "export" -> ExportCollectionDialog(collection?.name ?: "binder", viewModel::exportText) { listDialog = null }
        }
        Column(modifier = Modifier.fillMaxSize().background(Bg).padding(padding)) {
            if (collection?.entries?.isNotEmpty() == true) {
                Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
                    DashboardPanel(dashboard)
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                label = { Text("Name or tag, e.g. ramp", color = TextMuted) },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Gold) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = TextMuted)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Gold,
                    unfocusedBorderColor = BorderColor,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Gold,
                    focusedContainerColor = Surface,
                    unfocusedContainerColor = Surface
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
            )
            // A search that found cards by their tag says which, since tags only show in the zoom.
            if (query.isNotBlank()) {
                val tagHits = entries.filterNot { it.name.contains(query.trim(), ignoreCase = true) }
                    .flatMap { RoleTags.matched(cardTags[it.name].orEmpty(), query) }.distinct()
                Text(
                    buildString {
                        append("${entries.size} ${if (entries.size == 1) "card" else "cards"}")
                        if (tagHits.isNotEmpty()) append(" · tag: " + tagHits.take(2).joinToString(", ") { RoleTags.label(it) } + if (tagHits.size > 2) "…" else "")
                        if (tagging != null) append(" · finding tags…")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                    modifier = Modifier.padding(start = 22.dp, end = 20.dp, bottom = 6.dp)
                )
            }

            val total = collection?.entries?.sumOf { it.quantity + it.foilQuantity } ?: 0
            if (collection?.isWishlist == true) {
                Text(
                    "Cards you want. They don't count as owned. Cards your decks are considering that you don't own are added here by themselves, until you own them — take one off and it stays off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp)
                )
            }
            if (collection?.isUnsorted == true && collection?.entries?.isNotEmpty() == true) {
                Text(
                    "Cards you own that aren't in a binder yet. Long-press a card and choose Move to put it in a binder — or a new one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp)
                )
            }
            when {
                collection?.entries.isNullOrEmpty() -> Text(
                    if (collection?.isUnsorted == true) "All sorted — every card is in a binder." else "No cards yet. Add cards from a card's detail page or the scanner.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                entries.isEmpty() -> Text(
                    "No cards match \"$query\".",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(
                            "$total cards · ${collection?.entries?.size ?: 0} unique",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    if (viewMode == CardViewMode.GRID) {
                        cardGrid(entries, columns = gridCols, key = { it.scryfallId }) { entry ->
                            CollectionCardTile(
                                entry = entry,
                                selecting = selecting,
                                selected = entry.scryfallId in pickedIds,
                                onClick = { if (selecting) toggle(entry) else zoomId = entry.scryfallId },
                                onLongClick = { toggle(entry) }
                            )
                        }
                    } else {
                        cardGrid(entries, columns = listCols, key = { it.scryfallId }) { entry ->
                            CollectionCardRow(
                                entry = entry,
                                selecting = selecting,
                                selected = entry.scryfallId in pickedIds,
                                onClick = { if (selecting) toggle(entry) else zoomId = entry.scryfallId },
                                onLongClick = { toggle(entry) },
                                onQuantityChange = { qty, foil -> viewModel.setQuantity(entry, qty, foil) },
                                onRemove = { removeTarget = entry },
                                // A wishlist shows each card's price now, and can watch for it to drop.
                                price = if (isWishlist) prices[entry.scryfallId] else null,
                                onPriceAlert = if (isWishlist) ({ alertTarget = entry }) else null,
                                considering = if (entry.auto) decksConsidering(decks, entry.name).joinToString(", ").ifEmpty { null } else null
                            )
                        }
                    }
                }
            }
        }
    }

    zoomId?.let { id ->
        val zoomCards = entries.map { entry ->
            ZoomCard(
                imageUrl = entry.imageUrl,
                cardName = entry.name,
                priceUsd = prices[entry.scryfallId],
                quantity = entry.quantity,
                onIncrement = { viewModel.setQuantity(entry, entry.quantity + 1, entry.foilQuantity) },
                onDecrement = { viewModel.setQuantity(entry, (entry.quantity - 1).coerceAtLeast(0), entry.foilQuantity) },
                onSelectPrinting = { chosen -> viewModel.changePrinting(entry.scryfallId, chosen) },
                onMove = { zoomId = null; moveTarget = entry },
                onViewDetails = { zoomId = null; onViewDetails(entry.name) },
                sources = cardSources[entry.scryfallId].orEmpty().filter { it.id != collection?.id },
                backImageUrl = entry.backImageUrl,
                tags = cardTags[entry.name].orEmpty().map(RoleTags::label),
                onTagClick = { label -> zoomId = null; viewModel.onQueryChange(label) },
                onFindSimilar = { zoomId = null; similarSearchFor = entry.name }
            )
        }
        CardZoomDialog(zoomCards, entries.indexOfFirst { it.scryfallId == id }.coerceAtLeast(0)) { zoomId = null }
    }

    similarSearchFor?.let { name ->
        SimilarCardsDialog(
            cardName = name,
            onDismiss = { similarSearchFor = null },
            onAdd = { similar -> similarSearchFor = null; viewModel.addCard(similar) },
            onViewDetails = { similar -> similarSearchFor = null; onViewDetails(similar.name) }
        )
    }

    moveTarget?.let { entry ->
        MoveTargetDialog(
            cardName = entry.name,
            targets = moveTargets,
            onPick = { target -> viewModel.moveEntry(entry, target); moveTarget = null },
            onDismiss = { moveTarget = null },
            onNewBinder = { name -> viewModel.moveToNewBinder(entry, name, keepHere = false); moveTarget = null }
        )
    }

    alertTarget?.let { entry ->
        PriceAlertDialog(
            entry = entry,
            price = prices[entry.scryfallId],
            onSave = { usd -> viewModel.setPriceAlert(entry, usd); alertTarget = null },
            onDismiss = { alertTarget = null }
        )
    }

    removeTarget?.let { entry ->
        val qty = entry.quantity + entry.foilQuantity
        ConfirmDeleteDialog(
            title = "Remove card?",
            message = "Remove ${entry.name} ($qty cop${if (qty == 1) "y" else "ies"}) from this binder?",
            confirmLabel = "Remove",
            onConfirm = { viewModel.remove(entry); removeTarget = null },
            onDismiss = { removeTarget = null }
        )
    }

    if (confirmDeleteBinder && collection?.isUnsorted == true) {
        val total = collection?.entries.orEmpty().sumOf { it.quantity + it.foilQuantity }
        ConfirmDeleteDialog(
            title = "Remove all unsorted cards?",
            message = "All $total unsorted card${if (total == 1) "" else "s"} will be removed from your collection, here and on your other devices. Cards in binders stay.",
            confirmLabel = "Remove all",
            onConfirm = { confirmDeleteBinder = false; viewModel.clearAll(onBack) },
            onDismiss = { confirmDeleteBinder = false }
        )
    } else if (confirmDeleteBinder) {
        val name = collection?.name ?: "this binder"
        val total = collection?.entries?.sumOf { it.quantity + it.foilQuantity } ?: 0
        ConfirmDeleteDialog(
            title = "Delete binder?",
            message = "\"$name\" and its $total card${if (total == 1) "" else "s"} will be permanently deleted. This can't be undone.",
            onConfirm = { confirmDeleteBinder = false; viewModel.deleteCollection(onBack) },
            onDismiss = { confirmDeleteBinder = false }
        )
    }

    val pickedLabel = if (picked.size == 1) picked.first().name else "${picked.size} cards"
    val done = { bulk = null; selected = emptySet() }
    when (bulk) {
        "move", "copy" -> {
            val keep = bulk == "copy"
            MoveTargetDialog(
                cardName = pickedLabel,
                targets = moveTargets,
                onPick = { target -> viewModel.moveEntries(pickedIds, target, keepHere = keep); done() },
                onDismiss = { bulk = null },
                onNewBinder = { name -> viewModel.moveEntriesToNewBinder(pickedIds, name, keepHere = keep); done() },
                title = if (keep) "Copy $pickedLabel to" else null
            )
        }
        "remove" -> {
            val copies = picked.sumOf { it.quantity + it.foilQuantity }
            ConfirmDeleteDialog(
                title = if (picked.size == 1) "Remove card?" else "Remove ${picked.size} cards?",
                message = "Remove $pickedLabel ($copies cop${if (copies == 1) "y" else "ies"}) from this binder?",
                confirmLabel = "Remove",
                onConfirm = { viewModel.removeEntries(pickedIds); done() },
                onDismiss = { bulk = null }
            )
        }
        "export" -> ExportCollectionDialog(pickedLabel, { exact -> viewModel.exportText(exact, pickedIds) }, title = "Export $pickedLabel") { bulk = null }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CollectionCardRow(
    entry: CollectionEntry,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onQuantityChange: (Int, Int) -> Unit,
    onRemove: () -> Unit,
    /** Wishlists: today's price (null: none, or not a wishlist). */
    price: Double? = null,
    /** Wishlists: opens this card's price alert. */
    onPriceAlert: (() -> Unit)? = null,
    /** The Wishlist: the decks considering a card it has because of them. */
    considering: String? = null
) {
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
                    model = entry.imageUrl.toArtCropUrl(),
                    contentDescription = entry.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.zoomSource(entry.imageUrl).size(width = 72.dp, height = 52.dp).clip(RoundedCornerShape(10.dp))
                )
                if (entry.backImageUrl != null) FlipBadge()
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Text(
                    "Normal: ${entry.quantity}" + if (entry.foilQuantity > 0) " · Foil: ${entry.foilQuantity}" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted
                )
                considering?.let {
                    Text("Considering in $it", style = MaterialTheme.typography.labelMedium, color = GoldDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // Wishlists: today's price and the alert, which a tap sets.
                onPriceAlert?.let { open ->
                    val alert = entry.priceAlert
                    val hit = price != null && alert != null && price <= alert
                    val money = rememberMoney()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = open).padding(vertical = 2.dp)
                    ) {
                        Icon(
                            if (alert != null) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationAdd,
                            contentDescription = if (alert != null) "Price alert at ${money.format(alert)}" else "Set a price alert",
                            tint = if (alert != null) Gold else TextDim,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            listOfNotNull(price?.let { money.format(it) }, alert?.let { "≤ " + money.format(it).removeSuffix(".00") }).joinToString(" · ").ifEmpty { "Set alert" },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (hit) Gold else TextMuted,
                            fontWeight = if (hit) FontWeight.SemiBold else null,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (selecting) SelectionMark(selected, Modifier.padding(end = 8.dp)) else Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onQuantityChange((entry.quantity - 1).coerceAtLeast(0), entry.foilQuantity) }) {
                    Icon(Icons.Filled.Remove, contentDescription = "Decrease quantity", tint = Gold)
                }
                Text("${entry.quantity}", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                IconButton(onClick = { onQuantityChange(entry.quantity + 1, entry.foilQuantity) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Increase quantity", tint = Gold)
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Filled.Close,
                        // On the Wishlist, taking off a card it added by itself means "not interested".
                        contentDescription = if (considering != null) "Not interested" else "Remove from binder",
                        tint = TextDim
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CollectionCardTile(entry: CollectionEntry, selecting: Boolean, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val totalQty = entry.quantity + entry.foilQuantity
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    Box {
        Column(
            modifier = Modifier.fillMaxWidth().pressScale(interactionSource).combinedClickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick,
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() }
            )
        ) {
            Box {
                AsyncImage(
                    model = entry.imageUrl,
                    contentDescription = entry.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.zoomSource(entry.imageUrl).fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(14.dp))
                        .let { if (selected) it.border(BorderStroke(3.dp, Gold), RoundedCornerShape(14.dp)) else it }
                )
                if (selecting) SelectionMark(selected, Modifier.align(Alignment.TopStart).padding(6.dp))
                Text(
                    "×$totalQty",
                    style = MaterialTheme.typography.labelMedium,
                    color = GoldLight,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                if (entry.backImageUrl != null) FlipBadge()
            }
            Text(
                entry.name,
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/** Sets (or turns off) the price a wishlist card should drop to before the user is told. */
@Composable
private fun PriceAlertDialog(entry: CollectionEntry, price: Double?, onSave: (Double?) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // Typed in the currency prices show in; kept in US dollars, like the prices it's checked against.
    val money = rememberMoney()
    val decimals = money.currency.decimals
    var text by remember {
        mutableStateOf(
            entry.priceAlert?.let { String.format(java.util.Locale.US, "%.${decimals}f", money.toLocal(it)) }
                ?: price?.let { String.format(java.util.Locale.US, "%.${decimals}f", money.toLocal(it) * 0.9) }.orEmpty()
        )
    }
    // Notifications need the user's OK (Android 13+); asked the first time an alert is set.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val value = text.toDoubleOrNull()?.takeIf { it > 0 }?.let { money.toUsd(it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface2,
        title = { Text("Price alert · ${entry.name}", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    (price?.let { "It's ${money.format(it)} now. " } ?: "") + "Tell me when it's this much or less (${money.currency.code}, non-foil):",
                    color = TextMuted
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() || it == '.' } },
                    singleLine = true,
                    prefix = if (money.currency.after) null else ({ Text(money.currency.symbol, color = TextMuted) }),
                    suffix = if (money.currency.after) ({ Text(money.currency.symbol, color = TextMuted) }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Checked a few times a day; you'll get a notification.", style = MaterialTheme.typography.labelMedium, color = TextDim)
            }
        },
        confirmButton = {
            TextButton(enabled = value != null, onClick = {
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                onSave(value?.let { kotlin.math.round(it * 10_000) / 10_000 })
            }) { Text("Save", color = Gold) }
        },
        dismissButton = {
            TextButton(onClick = { if (entry.priceAlert != null) onSave(null) else onDismiss() }) {
                Text(if (entry.priceAlert != null) "Turn off" else "Cancel", color = TextMuted)
            }
        }
    )
}
