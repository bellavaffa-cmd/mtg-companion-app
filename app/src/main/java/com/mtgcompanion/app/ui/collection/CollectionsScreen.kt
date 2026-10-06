package com.mtgcompanion.app.ui.collection

import com.mtgcompanion.app.ui.common.ViewModeButton
import com.mtgcompanion.app.ui.common.EmptyAction
import com.mtgcompanion.app.ui.common.a11yHeading
import com.mtgcompanion.app.ui.common.EmptyPrompt
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SearchOff
import com.mtgcompanion.app.ui.common.cardsSubject
import com.mtgcompanion.app.data.AddCandidate
import com.mtgcompanion.app.ui.common.AddItem
import com.mtgcompanion.app.ui.common.AddCheck
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.Handshake
import com.mtgcompanion.app.data.social.TradeCard
import com.mtgcompanion.app.data.offerCards
import com.mtgcompanion.app.data.isWishlist
import com.mtgcompanion.app.data.RoleTags
import com.mtgcompanion.app.data.CollectionBreakdown
import com.mtgcompanion.app.data.placesOf
import com.mtgcompanion.app.data.placeTree
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.activity.compose.BackHandler
import com.mtgcompanion.app.ui.common.AddToPicker
import com.mtgcompanion.app.ui.common.AddVerb
import com.mtgcompanion.app.ui.common.LocalAddToFeedback
import com.mtgcompanion.app.ui.common.SourceKind
import com.mtgcompanion.app.ui.common.addToMessage
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
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import com.mtgcompanion.app.ui.theme.OnGold
import com.mtgcompanion.app.ui.common.rememberMoney
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
    onOpenTag: (String) -> Unit = {},
    /** Offers these spares to a friend in a trade (picking who comes next); null without an account. */
    onOfferSpares: ((List<TradeCard>) -> Unit)? = null,
    /** Opens a set's cards, owned and missing (from the Sets page), by set code. */
    onOpenSet: (String) -> Unit = {},
    /** Opens the cards the decks use more copies of than the user owns. */
    onOpenSpreadThin: () -> Unit = {},
    /** Opens a storage place's page (from the Storage page), by id. */
    onOpenPlace: (String) -> Unit = {},
    /** Opens the scanner putting cards away into a storage place, by id. */
    onPutAway: (String) -> Unit = {},
    /** Opens the Decks tab (the Storage page's deck boxes). */
    onOpenDecks: () -> Unit = {},
    /** The Storage page's Lent out: the Loans screen. */
    onOpenLoans: () -> Unit = {},
    /** The Storage page's Sort a new pile: the scanner's sort mode. */
    onSortPile: () -> Unit = {},
    /** The Storage page's Value by place. */
    onOpenValue: () -> Unit = {},
    /** The Storage page's Space and To sell (SpaceScreen.kt, SellScreen.kt). */
    onOpenSpace: () -> Unit = {},
    onOpenSell: () -> Unit = {},
    /** Sealed product (SealedScreen.kt). */
    onOpenSealed: () -> Unit = {},
    /** Getting started with storage, and Upkeep (StorageSetupScreen.kt, UpkeepScreen.kt). */
    onSetUpStorage: () -> Unit = {},
    onOpenUpkeep: () -> Unit = {},
    /** The Storage page's Sharing storage at home (HouseholdScreen.kt). */
    onOpenHousehold: () -> Unit = {},
    /** The empty pages' "Scan cards". */
    onOpenScan: () -> Unit = {}
) {
    val tagBinders by viewModel.tagBinders.collectAsState()
    val tagging by viewModel.tagging.collectAsState()
    val tagVersion by RoleTags.version.collectAsState()
    val collections by viewModel.collections.collectAsState()
    val allCards by viewModel.allCards.collectAsState()
    val spares by viewModel.spares.collectAsState()
    val thinCount by viewModel.thinCount.collectAsState()
    // Spares only: binder cards no deck of yours plays.
    var sparesOnly by remember { mutableStateOf(false) }
    val dashboard by viewModel.dashboard.collectAsState()
    val breakdown by viewModel.breakdown.collectAsState()
    val prices by viewModel.prices.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    val importProgress by viewModel.importProgress.collectAsState()
    val unsorted by viewModel.unsorted.collectAsState()
    // Page 0 = All Cards, 1 = Binders, 2 = Storage, 3 = Sets, 4 = Shared (with accounts). Swipe or tap the tabs to switch.
    val pageCount = if (sharedPage != null) 5 else 4
    val decks by viewModel.decks.collectAsState()
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    LaunchedEffect(openShared) {
        if (openShared && sharedPage != null) {
            pagerState.scrollToPage(4)
            onSharedOpened()
        }
    }
    val binderTargets by viewModel.binderTargets.collectAsState()
    val deckTargets by viewModel.deckTargets.collectAsState()
    val addTo = LocalAddToFeedback.current
    // All cards' search: it filters the list, and Select all takes what it shows.
    var query by remember { mutableStateOf("") }
    // A card's name or one of its tags.
    // Color, type and rarity, as in Search — narrowing the same list the search field does.
    var cardFilter by remember { mutableStateOf(CollectionFilter()) }
    val cardFacts by viewModel.cardFacts.collectAsState()
    // The Advanced filters (a screen of their own, AdvancedFilterScreen.kt), on top of the panel's.
    var advFilter by remember { mutableStateOf(AdvancedFilter()) }
    var advancedOpen by remember { mutableStateOf(false) }
    val advancedFacts by viewModel.advancedFacts.collectAsState()
    val copyFacts by viewModel.copyFacts.collectAsState()
    val savedFilters by viewModel.savedFilters.collectAsState()
    val money = rememberMoney()
    val filtering = cardFilter.active || advFilter.active
    val spareIds = remember(spares) { spares.map { it.entry.scryfallId }.toSet() }
    val named = remember(allCards, query, tagVersion) {
        // "proxy" reads as a tag of its own, so a search finds the cards standing in for real ones.
        if (query.isBlank()) allCards
        else allCards.filter { card ->
            val tags = RoleTags.tagsOf(card.name).orEmpty() + if (card.proxies > 0) listOf("proxy") else emptyList()
            RoleTags.matches(card.name, tags, query)
        }
    }
    // The panel's filters and the advanced ones; a card whose data hasn't loaded is left out while either is on.
    fun passes(card: AllCardEntry, basic: CollectionFilter, adv: AdvancedFilter): Boolean =
        basic.matches(cardFacts[card.scryfallId]) &&
            advancedMatches(adv, advancedFacts[card.scryfallId], copyFacts[card.scryfallId]) { money.toUsd(it) }
    val filtered = remember(named, sparesOnly, spareIds, cardFilter, cardFacts, advFilter, advancedFacts, copyFacts, money) {
        named.filter { (!filtering || passes(it, cardFilter, advFilter)) && (!sparesOnly || it.scryfallId in spareIds) }
    }
    val clearFilters = { cardFilter = CollectionFilter(); advFilter = AdvancedFilter() }
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
                    SelectionAction("Add to…", Icons.AutoMirrored.Filled.DriveFileMove) { bulk = "add" },
                    SelectionAction("Export list", Icons.Filled.IosShare) { bulk = "export" },
                    SelectionAction("Remove from binders", Icons.Filled.Delete, destructive = true) { bulk = "remove" }
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
                title = { Text("Collection", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.a11yHeading()) },
                actions = {
                    SyncIconButton()
                    if (onShareCollection != null) {
                        IconButton(onClick = onShareCollection) {
                            Icon(Icons.Filled.GroupAdd, contentDescription = "Share my collection", tint = TextPrimary)
                        }
                    }
                    IconButton(onClick = { viewModel.resetImport(); showImport = true }) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Import list", tint = TextPrimary)
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
                title = "Import list",
                askName = true,
                progress = importProgress,
                onImport = viewModel::importBinder,
                onDismiss = { showImport = false; viewModel.resetImport() },
                places = placesOf(collections),
                // From All cards, the whole collection comes in unsorted; from Binders, as a binder.
                startInNewBinder = pagerState.currentPage == 1
            )
        }
        Column(modifier = Modifier.fillMaxSize().background(Bg).padding(padding)) {
            SegmentedTabs(
                labels = if (sharedPage != null) listOf("All cards", "Binders", "Storage", "Sets", "Shared") else listOf("All cards", "Binders", "Storage", "Sets"),
                selected = pagerState.currentPage,
                onSelect = { page -> scope.launch { pagerState.animateScrollToPage(page) } },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // While cards are picked, a swipe mustn't carry the pick off to Binders.
            HorizontalPager(state = pagerState, userScrollEnabled = !selecting, modifier = Modifier.fillMaxSize()) { page ->
                if (page == 0) {
                    AllCardsTab(
                        spares = spares.size,
                        sparesOnly = sparesOnly,
                        onSparesOnly = { sparesOnly = it },
                        onOfferSpares = onOfferSpares?.let { offer -> { offer(offerCards(spares, viewModel.prices.value)) } },
                        thinCount = thinCount,
                        onOpenSpreadThin = onOpenSpreadThin,
                        unsorted = unsorted,
                        onOpenUnsorted = { onCollectionClick(it) },
                        onImport = { viewModel.resetImport(); showImport = true },
                        onOpenScan = onOpenScan,
                        allCards = allCards,
                        query = query,
                        onQueryChange = { query = it },
                        cardFilter = cardFilter,
                        onCardFilterChange = { cardFilter = it },
                        filtersOn = cardFilter.count + advFilter.count,
                        filtering = filtering,
                        chips = filterChips(
                            cardFilter, advFilter, { id -> collections.firstOrNull { it.id == id }?.name }, { money.formatLocal(it) },
                            placeName = { id -> placesOf(collections).firstOrNull { it.id == id }?.name }
                        ),
                        onRemoveChip = { key -> val (b, a) = removeChip(cardFilter, advFilter, key); cardFilter = b; advFilter = a },
                        onClearFilters = clearFilters,
                        onOpenAdvanced = { advancedOpen = true },
                        filtered = filtered,
                        selecting = selecting,
                        pickedIds = pickedIds,
                        onToggle = ::toggle,
                        dashboard = dashboard,
                        breakdown = breakdown,
                        prices = prices,
                        viewMode = viewMode,
                        gridColumns = gridColumns,
                        onViewDetails = onViewDetails,
                        viewModel = viewModel
                    )
                } else if (page == 4 && sharedPage != null) {
                    sharedPage()
                } else if (page == 3) {
                    SetsTab(viewModel, onOpenSet, onImport = { viewModel.resetImport(); showImport = true }, onOpenScan = onOpenScan)
                } else if (page == 2) {
                    // Where the cards are kept (StorageTab.kt).
                    StorageTab(
                        collections = collections,
                        decks = decks,
                        onOpenPlace = onOpenPlace,
                        onPutAway = onPutAway,
                        onOpenDecks = onOpenDecks,
                        onChange = viewModel::changeStorage,
                        onOpenLoans = onOpenLoans,
                        onSortPile = onSortPile,
                        onOpenValue = onOpenValue,
                        onOpenSpace = onOpenSpace,
                        onOpenSell = onOpenSell,
                        onOpenSealed = onOpenSealed,
                        onSetUp = onSetUpStorage,
                        onOpenUpkeep = onOpenUpkeep,
                        onOpenHousehold = onOpenHousehold
                    )
                } else {
                    CollectionsTab(
                        // The Unsorted pile on top, then the Wishlist; both are always there.
                        unsorted = unsorted,
                        onOpenUnsorted = { onCollectionClick(it) },
                        collections = collections.filterNot { it.isUnsorted }.sortedByDescending { it.isWishlist },
                        onCollectionClick = onCollectionClick,
                        onDelete = { viewModel.deleteCollection(it) },
                        tagBinders = tagBinders,
                        tagging = tagging,
                        onOpenTag = onOpenTag,
                        onImport = { viewModel.resetImport(); showImport = true },
                        onNewBinder = { showCreateDialog = true }
                    )
                }
            }
        }
    }

    if (advancedOpen) {
        AdvancedFilterScreen(
            basic = cardFilter,
            advanced = advFilter,
            countFor = { b, a -> named.count { passes(it, b, a) && (!sparesOnly || it.scryfallId in spareIds) } },
            binders = collections.filter { it.kind == CollectionType.OWNED }.sortedBy { it.name.lowercase() }.map { it.id to it.name },
            sets = allCards.mapNotNull { c -> cardFacts[c.scryfallId]?.takeIf { it.set.isNotBlank() }?.let { it.set to it.setName.ifBlank { it.set.uppercase() } } }
                .distinctBy { it.first }.sortedBy { it.second.lowercase() },
            saved = savedFilters,
            onSave = viewModel::saveFilter,
            onRename = viewModel::renameFilter,
            onDelete = viewModel::deleteFilter,
            onApply = { b, a -> cardFilter = b; advFilter = a; advancedOpen = false },
            onDismiss = { advancedOpen = false },
            places = placeTree(placesOf(collections)).map { it.place.id to ("  ".repeat(it.depth) + it.place.name) }
        )
    }

    val pickedLabel = if (picked.size == 1) picked.first().name else "${picked.size} cards"
    val done = { bulk = null; selected = emptySet() }
    when (bulk) {
        "add" -> {
            val ids = pickedIds
            AddToPicker(
                verb = AddVerb.ADD,
                subject = pickedLabel,
                imageUrl = picked.singleOrNull()?.imageUrl,
                targets = binderTargets + deckTargets,
                // Into a binder every copy is gathered; into a deck, one of each.
                quantity = null,
                onPick = { pick ->
                    done()
                    // A binder gathers the copies from the others: they're moved, not added.
                    val verb = if (pick.target.kind == SourceKind.BINDER) AddVerb.MOVE else AddVerb.ADD
                    // One of each goes into a deck; those already in it are skipped, so aren't checked.
                    val chosen = picked
                    val check = AddCheck(pick) { deck ->
                        chosen.filter { c -> deck.cards.none { it.scryfallId == c.scryfallId } }
                            .map { AddItem(AddCandidate(it.scryfallId, it.name, 1, pick.sideboard)) }
                    }
                    addTo.perform(
                        addToMessage(verb, pickedLabel, pick.place, pick.considering),
                        check = check,
                        fewer = { kept -> addToMessage(verb, cardsSubject(kept, null), pick.place, pick.considering) }
                    ) { viewModel.sendPicked(ids, pick, this) }
                },
                onDismiss = { bulk = null }
            )
        }
        "remove" -> {
            val copies = viewModel.copiesInBinders(pickedIds)
            ConfirmDeleteDialog(
                title = if (picked.size == 1) "Remove from binders?" else "Remove ${picked.size} cards from binders?",
                message = "Removes $pickedLabel ($copies cop${if (copies == 1) "y" else "ies"}) from all your binders. Copies in decks and wishlists stay.",
                confirmLabel = "Remove from binders",
                onConfirm = { viewModel.removeFromCollection(pickedIds); done() },
                onDismiss = { bulk = null }
            )
        }
        "export" -> ExportCollectionDialog(pickedLabel, { exact -> viewModel.exportText(pickedIds, exact) }, title = "Export $pickedLabel", buildCsv = { viewModel.exportCsv(pickedIds) }) { bulk = null }
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
    unsorted: Collection?,
    onOpenUnsorted: (String) -> Unit,
    collections: List<Collection>,
    onCollectionClick: (String) -> Unit,
    onDelete: (String) -> Unit,
    tagBinders: List<TagBinder>,
    tagging: Pair<Int, Int>?,
    onOpenTag: (String) -> Unit,
    onImport: () -> Unit = {},
    onNewBinder: () -> Unit = {}
) {
    // Binder pending a delete-confirmation, if any.
    var confirmDelete by remember { mutableStateOf<Collection?>(null) }
    val listCols = adaptiveListColumns()

    // The Wishlist and the Unsorted pile are always there; empty, there's nothing to show yet.
    if (collections.all { it.isWishlist && it.entries.isEmpty() } && unsorted?.entries.isNullOrEmpty()) {
        EmptyPrompt(
            Icons.Filled.Collections,
            "No binders yet. Import your collection from another app, or make a binder for the cards you own.",
            actions = listOf(
                EmptyAction("Import your collection", Icons.AutoMirrored.Filled.PlaylistAdd, onImport),
                EmptyAction("New binder", Icons.Filled.Add, onNewBinder)
            )
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (unsorted != null) {
                item(key = "unsorted") { UnsortedRow(unsorted) { onOpenUnsorted(unsorted.id) } }
            }
            cardGrid(collections, columns = listCols, key = { it.id }) { collection ->
                CollectionRow(
                    collection = collection,
                    onClick = { onCollectionClick(collection.id) },
                    onDelete = if (collection.isWishlist) null else ({ confirmDelete = collection })
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
            confirmLabel = "Delete binder",
            onConfirm = { onDelete(collection.id); confirmDelete = null },
            onDismiss = { confirmDelete = null }
        )
    }
}

@Composable
private fun AllCardsTab(
    /** How many spare cards there are, and whether the list is showing only those. */
    spares: Int,
    sparesOnly: Boolean,
    onSparesOnly: (Boolean) -> Unit,
    /** Offers the spares in a trade (see offerCards); null when there's no account to trade from. */
    onOfferSpares: (() -> Unit)?,
    /** How many cards are spread thin (null with no decks), and opening their page. */
    thinCount: Int?,
    onOpenSpreadThin: () -> Unit,
    unsorted: Collection?,
    onOpenUnsorted: (String) -> Unit,
    onImport: () -> Unit,
    onOpenScan: () -> Unit = {},
    allCards: List<AllCardEntry>,
    // Search filters the visible card list only; the dashboard still reflects the whole collection.
    query: String,
    onQueryChange: (String) -> Unit,
    cardFilter: CollectionFilter,
    onCardFilterChange: (CollectionFilter) -> Unit,
    /** Basic and advanced filters on, for the badge; whether any is; and a chip for each. */
    filtersOn: Int,
    filtering: Boolean,
    chips: List<ActiveChip>,
    onRemoveChip: (String) -> Unit,
    onClearFilters: () -> Unit,
    onOpenAdvanced: () -> Unit,
    filtered: List<AllCardEntry>,
    selecting: Boolean,
    pickedIds: Set<String>,
    onToggle: (String) -> Unit,
    dashboard: CollectionDashboard?,
    breakdown: CollectionBreakdown?,
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
    var filterOpen by remember { mutableStateOf(false) }
    val money = rememberMoney()
    val gridCols = adaptiveGridColumns(gridColumns)
    val listCols = adaptiveListColumns()

    if (allCards.isEmpty()) {
        EmptyPrompt(
            Icons.Filled.CollectionsBookmark,
            "No cards yet. Every card in your binders and physical decks shows here.",
            actions = listOf(
                EmptyAction("Import your collection", Icons.AutoMirrored.Filled.PlaylistAdd, onImport),
                EmptyAction("Scan cards", Icons.Filled.PhotoCamera, onOpenScan)
            )
        )
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
            // Where the value sits — by set, colour, rarity, type — and the dearest cards.
            item { BreakdownPanel(breakdown, onViewDetails) }
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
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ViewModeButton(viewMode, viewModel::setViewMode)
                            IconButton(onClick = { filterOpen = !filterOpen }) {
                                BadgedBox(badge = { if (filtersOn > 0) Badge(containerColor = Gold, contentColor = OnGold) { Text("$filtersOn") } }) {
                                    Icon(
                                        Icons.Filled.FilterList,
                                        contentDescription = if (filtering) "Filters, $filtersOn on" else "Filters",
                                        tint = if (filtering || filterOpen) Gold else TextMuted
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (filterOpen) {
                item { CollectionFilterPanel(cardFilter, onCardFilterChange, onAdvanced = onOpenAdvanced, onClear = onClearFilters, anyOn = filtering) }
            } else if (filtering) {
                item { ActiveFilterChips(chips, onRemoveChip, onClearFilters) }
            }
            if (spares > 0) {
                item {
                    Text(
                        if (sparesOnly) "Showing $spares spare ${if (spares == 1) "card" else "cards"} · tap to show everything"
                        else "Spares · $spares in your binders, in none of your decks",
                        style = MaterialTheme.typography.labelMedium,
                        color = Gold,
                        modifier = Modifier.clickable { onSparesOnly(!sparesOnly) }.padding(vertical = 4.dp)
                    )
                }
                // Spares are what a trade is usually made of: offer them to a friend in one go.
                if (sparesOnly && onOfferSpares != null) {
                    item {
                        OutlinedButton(onClick = onOfferSpares, shape = RoundedCornerShape(8.dp)) {
                            Icon(Icons.Filled.Handshake, contentDescription = null, tint = Gold, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Offer in a trade", color = Gold)
                        }
                    }
                }
            }
            // Spread thin: cards the decks use more copies of than the user owns — a page of its own.
            if (thinCount != null) {
                item {
                    Text(
                        if (thinCount > 0) "Spread thin · $thinCount ${if (thinCount == 1) "card" else "cards"} your decks use more copies of than you own"
                        else "Spread thin · every deck has its own copies",
                        style = MaterialTheme.typography.labelMedium,
                        color = Gold,
                        modifier = Modifier.clickable { onOpenSpreadThin() }.padding(vertical = 4.dp)
                    )
                }
            }
            item {
                val label = if (filtering) {
                    // The cards shown and what they're worth (proxies are worth nothing).
                    val value = filtered.sumOf { (prices[it.scryfallId] ?: 0.0) * (it.total - it.proxies) }
                    "${filtered.size} ${if (filtered.size == 1) "card" else "cards"}" + if (prices.isNotEmpty()) " · ${money.format(value)}" else ""
                } else if (query.isBlank()) {
                    "${allCards.sumOf { it.total }} cards · ${allCards.size} unique (across all binders & decks)"
                } else {
                    "${filtered.size} of ${allCards.size} unique match"
                }
                Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
            }
            if (filtered.isEmpty()) {
                item {
                    EmptyPrompt(Icons.Filled.SearchOff, if (filtering) "No cards match these filters." else "No cards match \"$query\".")
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
                tags = if (c.proxies > 0) c.tags + "proxy" else c.tags,
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
                    "${card.total} total" + when {
                        card.proxies == 0 -> ""
                        card.proxies == card.total -> " · proxy"
                        else -> " · ${card.proxies} proxy"
                    },
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
private fun CollectionRow(collection: Collection, onClick: () -> Unit, onDelete: (() -> Unit)?) {
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
                    onLongClick = { if (onDelete != null) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuExpanded = true } }
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
                Text(
                    if (collection.isWishlist) "Cards you want · $total ${if (total == 1) "card" else "cards"} · not counted as owned"
                    else "$total cards · ${collection.entries.size} unique",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted
                )
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete binder", tint = TextDim)
                }
            }
        }
        if (onDelete != null) {
            CardActionMenu(
                expanded = menuExpanded,
                onDismiss = { menuExpanded = false },
                actions = listOf(CardMenuAction("Delete binder", Icons.Filled.Delete, destructive = true) { onDelete() })
            )
        }
    }
}

/** The Unsorted pile: cards owned but not in a binder or deck yet, opened to sort them. */
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
                if (total > 0) "$total card${if (total == 1) "" else "s"} not in a binder yet — tap to sort them"
                else "Empty — for cards you own that aren't in a binder or a deck",
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
    val type = CollectionType.OWNED
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
                Text(
                    "Cards you want go in your Wishlist.",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted
                )
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
