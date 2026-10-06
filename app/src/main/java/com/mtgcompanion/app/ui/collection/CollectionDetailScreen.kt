package com.mtgcompanion.app.ui.collection

import com.mtgcompanion.app.ui.common.ViewModeButton
import com.mtgcompanion.app.ui.common.EmptyAction
import com.mtgcompanion.app.ui.common.a11yHeading
import com.mtgcompanion.app.ui.common.EmptyPrompt
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SearchOff
import com.mtgcompanion.app.ui.common.cardsSubject
import com.mtgcompanion.app.ui.common.toAddItem
import com.mtgcompanion.app.ui.common.AddCheck
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.common.openUrl
import com.mtgcompanion.app.ui.common.CopyBadge
import com.mtgcompanion.app.ui.common.CopyDetailsButton
import com.mtgcompanion.app.data.copyBadges
import com.mtgcompanion.app.data.buyListUrl
import com.mtgcompanion.app.data.BuyLine
import com.mtgcompanion.app.data.decksConsidering
import com.mtgcompanion.app.data.isWishlist
import com.mtgcompanion.app.ui.common.rememberMoney
import com.mtgcompanion.app.data.RoleTags
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationAdd
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import com.mtgcompanion.app.data.PriceAlerts
import com.mtgcompanion.app.data.gotItKept
import com.mtgcompanion.app.data.targetCount
import com.mtgcompanion.app.data.underYourPrice
import com.mtgcompanion.app.data.wishlistTotal
import com.mtgcompanion.app.data.withGotIt
import com.mtgcompanion.app.data.buyCardUrl
import androidx.compose.runtime.LaunchedEffect
import com.mtgcompanion.app.data.CollectionType
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Sell
import com.mtgcompanion.app.ui.common.CardActionMenu
import com.mtgcompanion.app.ui.common.CardMenuAction
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
import com.mtgcompanion.app.ui.common.AddToPick
import com.mtgcompanion.app.ui.common.AddToPicker
import com.mtgcompanion.app.ui.common.AddVerb
import com.mtgcompanion.app.ui.common.LocalAddToFeedback
import com.mtgcompanion.app.ui.common.addToMessage
import com.mtgcompanion.app.ui.common.asTarget
import com.mtgcompanion.app.ui.common.quantityLimits
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
    onShare: (() -> Unit)? = null,
    /** The empty binder's and Wishlist's buttons. */
    onOpenSearch: (() -> Unit)? = null,
    onOpenScan: (() -> Unit)? = null
) {
    val collection by viewModel.collection.collectAsState()
    val entries by viewModel.entries.collectAsState()
    val query by viewModel.query.collectAsState()
    val dashboard by viewModel.dashboard.collectAsState()
    // A wishlist card with a target shows the price its target is checked against.
    val prices by viewModel.shownPrices.collectAsState()
    val pricePairs by viewModel.pricePairs.collectAsState()
    val underTarget by viewModel.underTarget.collectAsState()
    val priceTracks by viewModel.priceTracks.collectAsState()
    val cardTags by viewModel.cardTags.collectAsState()
    val knownUserTags by viewModel.knownUserTags.collectAsState()
    val userTagsByCard by viewModel.userTagsByCard.collectAsState()
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
    var settingAll by remember { mutableStateOf(false) }
    // "Got it" on the Under your price box, kept on this device: card -> its price then. A card that
    // goes back over its target is forgotten, so the next time under shows again.
    val appContext = LocalContext.current
    var gotIt by remember { mutableStateOf(PriceAlerts.gotIt(appContext)) }
    LaunchedEffect(underTarget) {
        val hits = underTarget ?: return@LaunchedEffect
        val kept = gotItKept(gotIt, hits)
        if (kept != gotIt) { gotIt = kept; PriceAlerts.saveGotIt(appContext, kept) }
    }
    val under = underTarget?.let { underYourPrice(it, gotIt) }.orEmpty()
    var confirmDeleteBinder by remember { mutableStateOf(false) }
    val decks by viewModel.decks.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
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
    val addTo = LocalAddToFeedback.current

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            if (selecting) SelectionActionBar(
                listOf(
                    SelectionAction("Move to…", Icons.AutoMirrored.Filled.DriveFileMove) { bulk = "move" },
                    SelectionAction("Copy to…", Icons.Filled.ContentCopy) { bulk = "copy" },
                    SelectionAction("Export list", Icons.Filled.IosShare) { bulk = "export" },
                    SelectionAction("Remove from binder", Icons.Filled.Delete, destructive = true) { bulk = "remove" }
                )
            ) else if (isWishlist && collection?.entries?.isNotEmpty() == true) WishlistFoot(
                onSetAll = { settingAll = true },
                onBuyAll = { collection?.let { c -> buyListUrl(c.entries.map { BuyLine(it.name, it.quantity) })?.let { openUrl(context, it) } } }
            )
        },
        topBar = {
            if (selecting) SelectionTopBar(
                count = pickedIds.size,
                total = entries.size,
                onSelectAll = { selected = pickedIds + entries.map { it.scryfallId } },
                onClear = { selected = emptySet() }
            ) else TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(collection?.name ?: "Binder", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).a11yHeading())
                        // What the Wishlist would cost today.
                        val worth = if (isWishlist) wishlistTotal(collection?.entries.orEmpty(), prices) else null
                        if (worth != null) Text(rememberMoney().format(worth, whole = true), style = MaterialTheme.typography.titleLarge, color = Gold, maxLines = 1)
                    }
                },
                navigationIcon = {
                    BackButton(onClick = onBack)
                },
                actions = {
                    SyncIconButton()
                    ViewModeButton(viewMode, viewModel::setViewMode)
                    if (onShare != null) {
                        IconButton(onClick = onShare) {
                            Icon(Icons.Filled.GroupAdd, contentDescription = "Share with friends", tint = TextPrimary)
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Binder actions", tint = TextPrimary)
                        }
                        // The binder's own actions, in a sheet like a card's: each with a line on what
                        // it does, the destructive one last.
                        val c = collection
                        if (c != null) {
                            val binderActions = buildList {
                                add(CardMenuAction("Import list", Icons.AutoMirrored.Filled.PlaylistAdd, description = "A list from Moxfield, ManaBox, Archidekt…") { viewModel.resetImport(); listDialog = "import" })
                                add(CardMenuAction("Export list", Icons.Filled.IosShare, description = "For other apps, as text or CSV") { listDialog = "export" })
                                // The Wishlist is a shopping list: buy the lot in one basket.
                                if (c.isWishlist && c.entries.isNotEmpty()) add(
                                    CardMenuAction("Buy these cards", Icons.Filled.Sell, description = "All of them at TCGplayer, in one basket") {
                                        buyListUrl(c.entries.map { BuyLine(it.name, it.quantity) })?.let { openUrl(context, it) }
                                    }
                                )
                                // The Wishlist is always there.
                                if (!c.isWishlist) add(
                                    CardMenuAction(
                                        if (c.isUnsorted) "Remove all cards" else "Delete binder",
                                        Icons.Filled.Delete,
                                        destructive = true
                                    ) { confirmDeleteBinder = true }
                                )
                            }
                            val cardCount = c.entries.sumOf { it.quantity + it.foilQuantity }
                            CardActionMenu(
                                expanded = menuOpen,
                                onDismiss = { menuOpen = false },
                                actions = binderActions,
                                title = c.name,
                                subtitle = "$cardCount card${if (cardCount == 1) "" else "s"}"
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
            "export" -> ExportCollectionDialog(collection?.name ?: "binder", viewModel::exportText, buildCsv = { viewModel.exportCsv() }) { listDialog = null }
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
            if (isWishlist && under.isNotEmpty()) {
                UnderYourPriceBox(
                    hits = under,
                    onBuy = {
                        val url = if (under.size == 1) buyCardUrl(under.first().watch.entry.name)
                        else buyListUrl(under.map { BuyLine(it.watch.entry.name, maxOf(1, it.watch.entry.quantity + it.watch.entry.foilQuantity)) })
                        url?.let { openUrl(context, it) }
                    },
                    onGotIt = { gotIt = withGotIt(gotIt, under).also { PriceAlerts.saveGotIt(appContext, it) } },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                )
            }
            if (collection?.isWishlist == true) {
                Text(
                    "Cards you want. They don't count as owned. Cards your decks are considering that you don't own are added here by themselves, until you own them — take one off and it stays off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp)
                )
            }
            val notWanted = collection?.notWanted.orEmpty()
            if (collection?.isWishlist == true && notWanted.isNotEmpty()) {
                var showNotWanted by remember { mutableStateOf(false) }
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Text(
                        "${notWanted.size} ${if (notWanted.size == 1) "card" else "cards"} you said no to" +
                            if (showNotWanted) "" else " · tap to show",
                        style = MaterialTheme.typography.labelMedium,
                        color = Gold,
                        modifier = Modifier.clickable { showNotWanted = !showNotWanted }
                    )
                    if (showNotWanted) {
                        notWanted.forEach { name ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                                Text(
                                    name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                TextButton(onClick = { viewModel.wantAgain(name) }) { Text("Want it", color = Gold) }
                            }
                        }
                    }
                }
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
                collection?.entries.isNullOrEmpty() -> when {
                    collection?.isUnsorted == true -> EmptyPrompt(Icons.Filled.Inbox, "All sorted — every card is in a binder.")
                    isWishlist -> EmptyPrompt(
                        Icons.Filled.Star,
                        "Your wishlist is empty. Add cards you want from any card's page; cards your decks are considering show up here too.",
                        actions = listOfNotNull(onOpenSearch?.let { EmptyAction("Search cards", Icons.Filled.Search, it) })
                    )
                    else -> EmptyPrompt(
                        Icons.Filled.CollectionsBookmark,
                        "No cards yet. Add cards from a card's page, or scan them in.",
                        actions = listOfNotNull(onOpenScan?.let { EmptyAction("Scan cards", Icons.Filled.PhotoCamera, it) })
                    )
                }
                entries.isEmpty() -> EmptyPrompt(Icons.Filled.SearchOff, "No cards match \"$query\".")
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        if (isWishlist) Text(
                            targetCount(collection?.entries.orEmpty()).uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) else Text(
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
                                priceTrack = if (isWishlist) priceTracks?.get(entry.scryfallId) else null,
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
                onFindSimilar = { zoomId = null; similarSearchFor = entry.name },
                userTags = userTagsByCard[entry.scryfallId].orEmpty(),
                knownUserTags = knownUserTags,
                onUserTags = { next -> viewModel.setUserTags(entry.scryfallId, next) },
                // Condition, language and a "rises above" alert belong to owned copies; a wishlist
                // card still has its price history.
                copyDetails = {
                    CopyDetailsButton(
                        entry = entry,
                        onCopyDetails = if (isWishlist) null else ({ condition, language -> viewModel.setCopyDetails(entry, condition, language) }),
                        onAlertAbove = if (isWishlist) null else ({ usd -> viewModel.setPriceAlertAbove(entry, usd) }),
                        price = prices[entry.scryfallId]
                    )
                }
            )
        }
        CardZoomDialog(zoomCards, entries.indexOfFirst { it.scryfallId == id }.coerceAtLeast(0)) { zoomId = null }
    }

    similarSearchFor?.let { name ->
        SimilarCardsDialog(
            cardName = name,
            onDismiss = { similarSearchFor = null },
            onAdd = { similar ->
                similarSearchFor = null
                collection?.let { here ->
                    addTo.perform(addToMessage(AddVerb.ADD, similar.name, here.name)) { addCard(similar, AddToPick(here.asTarget())) }
                }
            },
            onViewDetails = { similar -> similarSearchFor = null; onViewDetails(similar.name) }
        )
    }

    moveTarget?.let { entry ->
        AddToPicker(
            verb = AddVerb.MOVE,
            subject = entry.name,
            imageUrl = entry.imageUrl,
            targets = moveTargets,
            quantity = quantityLimits(AddVerb.MOVE, entry.quantity + entry.foilQuantity),
            onPick = { pick ->
                moveTarget = null
                // Onto a Considering list the copies stay here: it's added, not moved.
                val verb = if (pick.considering) AddVerb.ADD else AddVerb.MOVE
                val check = AddCheck(pick, listOf(entry.toAddItem(pick.quantity, pick.sideboard)))
                addTo.perform(addToMessage(verb, entry.name, pick.place, pick.considering, pick.quantity), check = check) {
                    viewModel.sendEntry(entry, pick, keep = false, ops = this)
                }
            },
            onDismiss = { moveTarget = null }
        )
    }

    alertTarget?.let { entry ->
        TargetSheet(
            entry = entry,
            now = pricePairs[entry.scryfallId],
            track = priceTracks?.get(entry.scryfallId),
            onSave = { usd, options -> viewModel.setPriceAlert(entry, usd, options); alertTarget = null },
            onDismiss = { alertTarget = null }
        )
    }

    if (settingAll) {
        SetTargetsForAllDialog(
            count = viewModel::targetsForAllCount,
            onSet = { percent -> viewModel.setTargetsForAll(percent); settingAll = false },
            onDismiss = { settingAll = false }
        )
    }

    removeTarget?.let { entry ->
        val qty = entry.quantity + entry.foilQuantity
        ConfirmDeleteDialog(
            title = "Remove from binder?",
            message = "Remove ${entry.name} ($qty cop${if (qty == 1) "y" else "ies"}) from this binder?",
            confirmLabel = "Remove from binder",
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
            confirmLabel = "Delete binder",
            onConfirm = { confirmDeleteBinder = false; viewModel.deleteCollection(onBack) },
            onDismiss = { confirmDeleteBinder = false }
        )
    }

    val pickedLabel = if (picked.size == 1) picked.first().name else "${picked.size} cards"
    val done = { bulk = null; selected = emptySet() }
    when (bulk) {
        "move", "copy" -> {
            val keep = bulk == "copy"
            val verb = if (keep) AddVerb.COPY else AddVerb.MOVE
            val ids = pickedIds
            AddToPicker(
                verb = verb,
                subject = pickedLabel,
                imageUrl = picked.singleOrNull()?.imageUrl,
                targets = moveTargets,
                // Every copy of each picked card goes.
                quantity = null,
                onPick = { pick ->
                    done()
                    val said = if (pick.considering) AddVerb.ADD else verb
                    val check = AddCheck(pick, picked.map { it.toAddItem(sideboard = pick.sideboard) })
                    addTo.perform(
                        addToMessage(said, pickedLabel, pick.place, pick.considering),
                        check = check,
                        fewer = { kept -> addToMessage(said, cardsSubject(kept, null), pick.place, pick.considering) }
                    ) {
                        viewModel.sendEntries(ids, pick, keep = keep, ops = this)
                    }
                },
                onDismiss = { bulk = null }
            )
        }
        "remove" -> {
            val copies = picked.sumOf { it.quantity + it.foilQuantity }
            ConfirmDeleteDialog(
                title = if (picked.size == 1) "Remove from binder?" else "Remove ${picked.size} cards from binder?",
                message = "Remove $pickedLabel ($copies cop${if (copies == 1) "y" else "ies"}) from this binder?",
                confirmLabel = "Remove from binder",
                onConfirm = { viewModel.removeEntries(pickedIds); done() },
                onDismiss = { bulk = null }
            )
        }
        "export" -> ExportCollectionDialog(pickedLabel, { exact -> viewModel.exportText(exact, pickedIds) }, title = "Export $pickedLabel", buildCsv = { viewModel.exportCsv(pickedIds) }) { bulk = null }
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
    /** Wishlists: the card's price history, for the week's drop. */
    priceTrack: com.mtgcompanion.app.data.PriceTrack? = null,
    /** Wishlists: opens this card's target sheet; the row then says how far off its target it is. */
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Normal: ${entry.quantity}" + if (entry.foilQuantity > 0) " · Foil: ${entry.foilQuantity}" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted
                    )
                    // The copies' condition and language, only when the user has said.
                    copyBadges(entry).forEach { CopyBadge(it) }
                    if (entry.priceAlertAbove != null) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = "Price alert set", tint = Gold, modifier = Modifier.size(14.dp))
                    }
                }
                considering?.let {
                    Text("Considering in $it", style = MaterialTheme.typography.labelMedium, color = GoldDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // Wishlists: the target and how far off it is ("Target $15 · $3.40 to go"), and the
                // price now; a tap opens the target sheet.
                onPriceAlert?.let { open ->
                    val alert = entry.priceAlert
                    val hit = price != null && alert != null && price <= alert
                    val money = rememberMoney()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = open).padding(vertical = 2.dp)
                    ) {
                        Icon(
                            if (alert != null) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationAdd,
                            contentDescription = null,
                            tint = if (alert != null) Gold else TextDim,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            wishlistTargetLine(entry, price, priceTrack, money),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (hit) Gold else TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (price != null) Text(
                            money.format(price),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (hit) Gold else TextPrimary,
                            maxLines = 1
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
                val badges = copyBadges(entry)
                if (badges.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)) {
                        badges.forEach { CopyBadge(it) }
                    }
                }
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
