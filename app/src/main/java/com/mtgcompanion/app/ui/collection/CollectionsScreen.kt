package com.mtgcompanion.app.ui.collection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.AddCandidate
import com.mtgcompanion.app.data.LayoutGroup
import com.mtgcompanion.app.data.CardGroupBy
import com.mtgcompanion.app.data.CardViewMode
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionBreakdown
import com.mtgcompanion.app.data.CollectionTab
import com.mtgcompanion.app.data.CollectionType
import com.mtgcompanion.app.data.CopyHistoryStore
import com.mtgcompanion.app.data.LocationChip
import com.mtgcompanion.app.data.LocationKind
import com.mtgcompanion.app.data.MoveCard
import com.mtgcompanion.app.data.MoveSpot
import com.mtgcompanion.app.data.NO_PLACE_TITLE
import com.mtgcompanion.app.data.QuickFilter
import com.mtgcompanion.app.data.QuickSets
import com.mtgcompanion.app.data.RoleTags
import com.mtgcompanion.app.data.TourAction
import com.mtgcompanion.app.data.TourTarget
import com.mtgcompanion.app.data.binderKeys
import com.mtgcompanion.app.data.groupLayout
import com.mtgcompanion.app.data.isWishlist
import com.mtgcompanion.app.data.keptInPlacesLine
import com.mtgcompanion.app.data.needsAttentionLabel
import com.mtgcompanion.app.data.offerCards
import com.mtgcompanion.app.data.passesQuick
import com.mtgcompanion.app.data.placeKeys
import com.mtgcompanion.app.data.placeTree
import com.mtgcompanion.app.data.placeUnplaced
import com.mtgcompanion.app.data.placesOf
import com.mtgcompanion.app.data.placesUsed
import com.mtgcompanion.app.data.putAwayMove
import com.mtgcompanion.app.data.quickCount
import com.mtgcompanion.app.data.quickSets
import com.mtgcompanion.app.data.setGroupDetail
import com.mtgcompanion.app.data.setKeys
import com.mtgcompanion.app.data.shouldShowTour
import com.mtgcompanion.app.data.startTab
import com.mtgcompanion.app.data.suggestSpot
import com.mtgcompanion.app.data.tourSteps
import com.mtgcompanion.app.data.social.TradeCard
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.common.AddCheck
import com.mtgcompanion.app.ui.common.AddItem
import com.mtgcompanion.app.ui.common.AddToPicker
import com.mtgcompanion.app.ui.common.AddVerb
import com.mtgcompanion.app.ui.common.CardActionMenu
import com.mtgcompanion.app.ui.common.CardMenuAction
import com.mtgcompanion.app.ui.common.CardZoomDialog
import com.mtgcompanion.app.ui.common.ConfirmDeleteDialog
import com.mtgcompanion.app.ui.common.EmptyAction
import com.mtgcompanion.app.ui.common.EmptyPrompt
import com.mtgcompanion.app.ui.common.FlipBadge
import com.mtgcompanion.app.ui.common.LocalAddToFeedback
import com.mtgcompanion.app.ui.common.PillChip
import com.mtgcompanion.app.ui.common.SegmentedTabs
import com.mtgcompanion.app.ui.common.SimilarCardsDialog
import com.mtgcompanion.app.ui.common.SourceKind
import com.mtgcompanion.app.ui.common.SyncIconButton
import com.mtgcompanion.app.ui.common.ViewModeButton
import com.mtgcompanion.app.ui.common.ZoomCard
import com.mtgcompanion.app.ui.common.a11yHeading
import com.mtgcompanion.app.ui.common.adaptiveGridColumns
import com.mtgcompanion.app.ui.common.adaptiveListColumns
import com.mtgcompanion.app.ui.common.addToMessage
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.common.cardGrid
import com.mtgcompanion.app.ui.common.cardsSubject
import com.mtgcompanion.app.ui.common.pressScale
import com.mtgcompanion.app.ui.common.rememberMoney
import com.mtgcompanion.app.ui.common.zoomSource
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldDim
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.LocalAppColors
import com.mtgcompanion.app.ui.theme.NumberStyle
import com.mtgcompanion.app.ui.theme.OnGold
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.TextDim
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary
import kotlinx.coroutines.launch

/*
 * The Collection: one header — its value (tap: over time), sync, one search over the tab you're on, the
 * filters (Cards) and "Needs attention · N" when Upkeep has something — and four tabs, always there:
 * Cards (every card owned, grouped and quick-filtered, each saying where its copies are), Binders (the
 * Unsorted pile, binders, tag binders, goals), Storage (places, sealed, graded, loans, gear) and
 * Wishlist. It opens on the tab used last; old routes to the home's tiles and tabs map onto these
 * (CollectionLayout.kt). Mirrors the web app's src/pages/CollectionsPage.tsx.
 */

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
    /** The Wishlist tab: the Wishlist binder's page without its own header, searched by the header's [String]. */
    wishlistPage: (@Composable (String) -> Unit)? = null,
    /** Opens a tag's automatic binder. */
    onOpenTag: (String) -> Unit = {},
    /** Offers these spares to a friend in a trade (picking who comes next); null without an account. */
    onOfferSpares: ((List<TradeCard>) -> Unit)? = null,
    /** Opens a set's cards, owned and missing (a set group's header on Cards), by set code. */
    onOpenSet: (String) -> Unit = {},
    /** Opens the cards the decks use more copies of than the user owns. */
    onOpenSpreadThin: () -> Unit = {},
    /** Opens a storage place's page (from the Storage tab), by id. */
    onOpenPlace: (String) -> Unit = {},
    /** Opens a place at a box's section or a binder's page (a card row's place chip). */
    onOpenPlaceAt: (String, String?, Int?) -> Unit = { id, _, _ -> onOpenPlace(id) },
    /** Opens a deck (a card row's deck chip). */
    onOpenDeck: (String) -> Unit = {},
    /** Opens the scanner putting cards away into a storage place, by id. */
    onPutAway: (String) -> Unit = {},
    /** Opens the Decks tab (the Storage tab's deck boxes). */
    onOpenDecks: () -> Unit = {},
    /** Lent out: the Loans screen. */
    onOpenLoans: () -> Unit = {},
    /** Sort a new pile: the scanner in Sort a pile (Start with the recipe used last, or pick one). */
    onSortPile: () -> Unit = {},
    /** Value by place. */
    onOpenValue: () -> Unit = {},
    /** Space and To sell (SpaceScreen.kt, SellScreen.kt). */
    onOpenSpace: () -> Unit = {},
    onOpenSell: () -> Unit = {},
    /** Sealed product (SealedScreen.kt), and every graded copy (GradedListScreen.kt). */
    onOpenSealed: () -> Unit = {},
    onOpenGraded: () -> Unit = {},
    /** Getting started with storage, and Upkeep — Needs attention's list (StorageSetupScreen.kt, UpkeepScreen.kt). */
    onSetUpStorage: () -> Unit = {},
    onOpenUpkeep: () -> Unit = {},
    /** Sharing storage at home (HouseholdScreen.kt). */
    onOpenHousehold: () -> Unit = {},
    /** Gear (GearScreen.kt). */
    onOpenGear: () -> Unit = {},
    /** Settings › Data and speed (the backup). */
    onOpenDataAndSpeed: () -> Unit = {},
    /** The empty pages' "Scan cards". */
    onOpenScan: () -> Unit = {},
    /** Search everywhere: Find anything (FindAnythingScreen.kt) with what's typed. */
    onFind: (String) -> Unit = {},
    /** The value in the header: the collection's value over time (ValueHistoryScreen.kt). */
    onOpenValueHistory: () -> Unit = {},
    /** The Binders tab's Goals (GoalsScreen.kt): all goals, one, and New goal. */
    onOpenGoals: () -> Unit = {},
    onOpenGoal: (String) -> Unit = {},
    onNewGoal: () -> Unit = {}
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val tagBinders by viewModel.tagBinders.collectAsState()
    val tagging by viewModel.tagging.collectAsState()
    val tagVersion by RoleTags.version.collectAsState()
    val collections by viewModel.collections.collectAsState()
    val decks by viewModel.decks.collectAsState()
    val allCards by viewModel.allCards.collectAsState()
    val spares by viewModel.spares.collectAsState()
    val thinCount by viewModel.thinCount.collectAsState()
    val chipIndex by viewModel.locationIndex.collectAsState()
    // Spares only: binder cards no deck of yours plays.
    var sparesOnly by remember { mutableStateOf(false) }
    val dashboard by viewModel.dashboard.collectAsState()
    val ownedValue by viewModel.ownedValue.collectAsState()
    val breakdown by viewModel.breakdown.collectAsState()
    val prices by viewModel.prices.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    val importProgress by viewModel.importProgress.collectAsState()
    val unsorted by viewModel.unsorted.collectAsState()
    val scope = rememberCoroutineScope()
    val money = rememberMoney()

    // The four tabs. A route asking for one (an old tile or tab) wins; else the one used last.
    val asked by CollectionTabRequest.route.collectAsState()
    val pagerState = rememberPagerState(initialPage = remember { startTab(asked, CollectionTabStore.last(context)).ordinal }) { CollectionTab.entries.size }
    val tab = CollectionTab.entries[pagerState.currentPage]
    LaunchedEffect(pagerState.currentPage) { CollectionTabStore.save(context, CollectionTab.entries[pagerState.currentPage].key) }
    var groupKey by rememberSaveable { mutableStateOf(CardGroupBy.NONE.key) }
    val groupBy = CardGroupBy.fromKey(groupKey)
    var quickKey by rememberSaveable { mutableStateOf<String?>(null) }
    val quick = QuickFilter.fromKey(quickKey)
    var showShared by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(asked) {
        val route = asked ?: return@LaunchedEffect
        pagerState.scrollToPage(route.tab.ordinal)
        route.group?.let { groupKey = it.key }
        route.quick?.let { quickKey = it.key }
        if (route.shared && sharedPage != null) showShared = true
        CollectionTabRequest.route.value = null
    }
    LaunchedEffect(openShared) {
        if (openShared && sharedPage != null) {
            showShared = true
            onSharedOpened()
        }
    }
    BackHandler(enabled = showShared) { showShared = false }

    // The What's new tour (WhatsNew.kt): once on this phone, or again from Settings.
    val tourTargets = remember { TourTargets() }
    var touring by remember { mutableStateOf(false) }
    val replayTour by WhatsNewStore.replay.collectAsState()
    LaunchedEffect(replayTour, allCards.size, decks.size) {
        if (replayTour || shouldShowTour(WhatsNewStore.seen(context), cards = allCards.size, decks = decks.size)) {
            WhatsNewStore.replay.value = false
            WhatsNewStore.markSeen(context)
            touring = true
        }
    }

    val binderTargets by viewModel.binderTargets.collectAsState()
    val deckTargets by viewModel.deckTargets.collectAsState()
    val addTo = LocalAddToFeedback.current
    // The header's one search: it narrows the tab you're on.
    var query by rememberSaveable { mutableStateOf("") }
    // Color, type and rarity, as in Search — narrowing the same list the search field does.
    var cardFilter by remember { mutableStateOf(CollectionFilter()) }
    var filterOpen by remember { mutableStateOf(false) }
    val cardFacts by viewModel.cardFacts.collectAsState()
    // The Advanced filters (a screen of their own, AdvancedFilterScreen.kt), on top of the panel's.
    var advFilter by remember { mutableStateOf(AdvancedFilter()) }
    var advancedOpen by remember { mutableStateOf(false) }
    val advancedFacts by viewModel.advancedFacts.collectAsState()
    val copyFacts by viewModel.copyFacts.collectAsState()
    val savedFilters by viewModel.savedFilters.collectAsState()
    val filtering = cardFilter.active || advFilter.active
    val spareIds = remember(spares) { spares.map { it.entry.scryfallId }.toSet() }
    val quickSets = remember(collections, chipIndex) { quickSets(collections, chipIndex) }
    // The user's own tags on each printing, and the ones offered as filter chips (most used first).
    val userTagIndex = remember(collections, decks) { com.mtgcompanion.app.data.userTagsById(decks, collections) }
    val userTagOptions = remember(collections, decks) { com.mtgcompanion.app.data.allUserTags(decks, collections).take(12) }
    val named = remember(allCards, query, tagVersion, userTagIndex) {
        // "proxy" reads as a tag of its own, so a search finds the cards standing in for real ones;
        // the user's own tags are found too.
        if (query.isBlank()) allCards
        else allCards.filter { card ->
            val tags = RoleTags.tagsOf(card.name).orEmpty() + if (card.proxies > 0) listOf("proxy") else emptyList()
            RoleTags.matches(card.name, tags, query) ||
                userTagIndex[card.scryfallId].orEmpty().any { it.contains(query.trim(), ignoreCase = true) }
        }
    }
    // The panel's filters and the advanced ones; a card whose data hasn't loaded is left out while either is on.
    fun passes(card: AllCardEntry, basic: CollectionFilter, adv: AdvancedFilter): Boolean =
        basic.matches(cardFacts[card.scryfallId]) &&
            basic.matchesTags(RoleTags.tagsOf(card.name), userTagIndex[card.scryfallId].orEmpty()) &&
            advancedMatches(adv, advancedFacts[card.scryfallId], copyFacts[card.scryfallId]) { money.toUsd(it) }
    val unquick = remember(named, sparesOnly, spareIds, cardFilter, cardFacts, advFilter, advancedFacts, copyFacts, money, tagVersion, userTagIndex) {
        named.filter { (!filtering || passes(it, cardFilter, advFilter)) && (!sparesOnly || it.scryfallId in spareIds) }
    }
    val filtered = remember(unquick, quick, quickSets) { unquick.filter { passesQuick(quick, it.scryfallId, it.name, quickSets) } }
    val clearFilters = { cardFilter = CollectionFilter(); advFilter = AdvancedFilter() }
    // Cards picked on Cards by pressing and holding (scryfall ids), and the action open for them:
    // "add", "export" or "remove". Cards no longer owned drop from the pick.
    var selected by remember { mutableStateOf(setOf<String>()) }
    var bulk by remember { mutableStateOf<String?>(null) }
    val picked = allCards.filter { it.scryfallId in selected }
    val pickedIds = picked.map { it.scryfallId }.toSet()
    val selecting = pickedIds.isNotEmpty()
    fun toggle(id: String) {
        selected = if (id in pickedIds) pickedIds - id else pickedIds + id
    }
    BackHandler(enabled = selecting) { selected = emptySet() }

    // Needs attention: Upkeep's list, when it has something.
    val upkeep = rememberUpkeep(collections, decks)
    val attention = needsAttentionLabel(upkeep?.items?.size ?: 0)
    var menuOpen by remember { mutableStateOf(false) }
    // A card row's "No place yet · Put away": the card whose copies are getting a place.
    var puttingAway by remember { mutableStateOf<AllCardEntry?>(null) }
    val places = remember(collections) { placesOf(collections) }

    Box(Modifier.fillMaxSize()) {

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
                title = { Text("Collection", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.a11yHeading()) },
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.tourTarget(tourTargets, TourTarget.HEADER)) {
                        ownedValue?.let { usd ->
                            TextButton(onClick = onOpenValueHistory, modifier = Modifier.semantics { contentDescription = "Collection value ${money.format(usd, whole = true)}: see it over time" }) {
                                Text(money.format(usd, whole = true), style = NumberStyle(26), color = Gold)
                            }
                        }
                        SyncIconButton()
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = TextPrimary)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = colors.surface) {
                            DropdownMenuItem(
                                text = { Text("Import list", color = colors.textPrimary) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = colors.textMuted) },
                                onClick = { menuOpen = false; viewModel.resetImport(); showImport = true }
                            )
                            DropdownMenuItem(
                                text = { Text("New binder", color = colors.textPrimary) },
                                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, tint = colors.textMuted) },
                                onClick = { menuOpen = false; showCreateDialog = true }
                            )
                            if (onShareCollection != null) DropdownMenuItem(
                                text = { Text("Share my collection", color = colors.textPrimary) },
                                leadingIcon = { Icon(Icons.Filled.GroupAdd, contentDescription = null, tint = colors.textMuted) },
                                onClick = { menuOpen = false; onShareCollection() }
                            )
                            if (sharedPage != null) DropdownMenuItem(
                                text = { Text("Shared with me", color = colors.textPrimary) },
                                leadingIcon = { Icon(Icons.Filled.Handshake, contentDescription = null, tint = colors.textMuted) },
                                onClick = { menuOpen = false; showShared = true }
                            )
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
                places = places,
                // From Cards, the whole collection comes in unsorted; from Binders, as a binder.
                startInNewBinder = tab == CollectionTab.BINDERS
            )
        }
        Column(modifier = Modifier.fillMaxSize().background(Bg).padding(padding)) {
            CollectionSearchBar(
                query = query,
                onQueryChange = { query = it },
                tab = tab,
                filtersOn = cardFilter.count + advFilter.count,
                filterOpen = filterOpen,
                onFilter = if (tab == CollectionTab.CARDS) ({ filterOpen = !filterOpen }) else null,
                viewMode = if (tab == CollectionTab.CARDS) viewMode else null,
                onViewMode = viewModel::setViewMode,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp).tourTarget(tourTargets, TourTarget.SEARCH)
            )
            if (attention != null) {
                NeedsAttentionChip(attention, onOpenUpkeep, Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
            }
            SegmentedTabs(
                labels = CollectionTab.entries.map { it.label },
                selected = pagerState.currentPage,
                onSelect = { page -> scope.launch { pagerState.animateScrollToPage(page) } },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).tourTarget(tourTargets, TourTarget.TABS),
                tabModifier = { i ->
                    when (CollectionTab.entries[i]) {
                        CollectionTab.STORAGE -> Modifier.tourTarget(tourTargets, TourTarget.STORAGE)
                        CollectionTab.CARDS -> Modifier.tourTarget(tourTargets, TourTarget.CARDS)
                        else -> Modifier
                    }
                }
            )

            // While cards are picked, a swipe mustn't carry the pick off to Binders.
            HorizontalPager(state = pagerState, userScrollEnabled = !selecting, modifier = Modifier.fillMaxSize()) { page ->
                when (CollectionTab.entries[page]) {
                    CollectionTab.CARDS -> CardsTab(
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
                        cardFilter = cardFilter,
                        onCardFilterChange = { cardFilter = it },
                        filterOpen = filterOpen,
                        filtering = filtering,
                        chips = filterChips(
                            cardFilter, advFilter, { id -> collections.firstOrNull { it.id == id }?.name }, { money.formatLocal(it) },
                            placeName = { id -> places.firstOrNull { it.id == id }?.name }
                        ),
                        onRemoveChip = { key -> val (b, a) = removeChip(cardFilter, advFilter, key); cardFilter = b; advFilter = a },
                        onClearFilters = clearFilters,
                        onOpenAdvanced = { advancedOpen = true },
                        userTagOptions = userTagOptions,
                        unquick = unquick,
                        filtered = filtered,
                        groupBy = groupBy,
                        onGroupBy = { groupKey = it.key },
                        quick = quick,
                        onQuick = { quickKey = if (it == quick) null else it?.key },
                        quickSets = quickSets,
                        onManageQuick = { q ->
                            when (q) {
                                QuickFilter.LENT_OUT -> onOpenLoans()
                                QuickFilter.FOR_SALE -> onOpenSell()
                                QuickFilter.GRADED -> onOpenGraded()
                                QuickFilter.NO_PLACE -> if (places.isEmpty()) onSetUpStorage() else onOpenUpkeep()
                            }
                        },
                        chipIndex = chipIndex,
                        chipActions = { card ->
                            ChipActions(
                                onPlace = onOpenPlaceAt,
                                onDeck = onOpenDeck,
                                onLent = onOpenLoans,
                                onPutAway = { if (places.isEmpty()) onSetUpStorage() else puttingAway = card },
                                onMore = { onViewDetails(card.name) }
                            )
                        },
                        cardFacts = cardFacts,
                        selecting = selecting,
                        pickedIds = pickedIds,
                        onToggle = ::toggle,
                        dashboard = dashboard,
                        breakdown = breakdown,
                        prices = prices,
                        viewMode = viewMode,
                        gridColumns = gridColumns,
                        onViewDetails = onViewDetails,
                        onSearchEverywhere = { onFind(query) },
                        onOpenSet = onOpenSet,
                        viewModel = viewModel
                    )
                    CollectionTab.BINDERS -> BindersTab(
                        unsorted = unsorted,
                        onOpenUnsorted = { onCollectionClick(it) },
                        collections = collections,
                        decks = decks,
                        query = query,
                        onCollectionClick = onCollectionClick,
                        onDelete = { viewModel.deleteCollection(it) },
                        tagBinders = tagBinders,
                        tagging = tagging,
                        onOpenTag = onOpenTag,
                        onImport = { viewModel.resetImport(); showImport = true },
                        onNewBinder = { showCreateDialog = true },
                        onOpenGoals = onOpenGoals,
                        onOpenGoal = onOpenGoal,
                        onNewGoal = onNewGoal,
                        onSearchEverywhere = { onFind(query) }
                    )
                    // Where the cards are kept (StorageTab.kt).
                    CollectionTab.STORAGE -> StorageTab(
                        collections = collections,
                        decks = decks,
                        query = query,
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
                        onOpenGraded = onOpenGraded,
                        onSetUp = onSetUpStorage,
                        onOpenUpkeep = onOpenUpkeep,
                        onOpenHousehold = onOpenHousehold,
                        onOpenGear = onOpenGear,
                        onOpenDataAndSpeed = onOpenDataAndSpeed,
                        onSearchEverywhere = { onFind(query) }
                    )
                    CollectionTab.WISHLIST -> if (wishlistPage != null) wishlistPage(query) else EmptyPrompt(Icons.Filled.Star, "Your wishlist is loading…")
                }
            }
        }
    }

    // What friends share (with accounts): from the header's menu, or a link asking for it.
    if (showShared && sharedPage != null) {
        Scaffold(
            containerColor = Bg,
            topBar = {
                TopAppBar(
                    title = { Text("Shared with me", style = MaterialTheme.typography.titleLarge, modifier = Modifier.a11yHeading()) },
                    navigationIcon = { BackButton(onClick = { showShared = false }) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
                )
            }
        ) { padding -> Box(Modifier.fillMaxSize().background(Bg).padding(padding)) { sharedPage() } }
    }

    if (touring) {
        WhatsNewTour(
            steps = remember(collections) { tourSteps(placesOf(collections).isNotEmpty()) },
            targets = tourTargets,
            onAction = { action -> if (action == TourAction.FIND) onFind(query) else onSetUpStorage() },
            onClose = { touring = false },
            onStep = { target ->
                // The step's tab in view under the tab row.
                when (target) {
                    TourTarget.STORAGE -> scope.launch { pagerState.animateScrollToPage(CollectionTab.STORAGE.ordinal) }
                    TourTarget.CARDS -> scope.launch { pagerState.animateScrollToPage(CollectionTab.CARDS.ordinal) }
                    else -> {}
                }
            }
        )
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
            places = placeTree(places).map { it.place.id to ("  ".repeat(it.depth) + it.place.name) }
        )
    }

    puttingAway?.let { card ->
        val none = chipIndex[card.scryfallId].orEmpty().firstOrNull { it.kind == LocationKind.NONE }?.qty ?: 1
        PlacePickerDialog("Give ${card.name} a place", places, onDismiss = { puttingAway = null }) { placeId ->
            puttingAway = null
            val facts = viewModel.placeFacts(card.scryfallId, card.name)
            places.firstOrNull { it.id == placeId }?.let { place ->
                CopyHistoryStore.record(listOf(putAwayMove(System.currentTimeMillis(), MoveCard(card.name, card.scryfallId), none, MoveSpot(place.id, place.name), null)))
            }
            viewModel.changeStorage { current ->
                val place = placesOf(current).firstOrNull { it.id == placeId }
                if (place == null) current else placeUnplaced(current, card.name, card.scryfallId, suggestSpot(place, facts, current).first, none).first
            }
        }
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

/** The header's one search, over the tab you're on; on Cards also the view and the filters. */
@Composable
private fun CollectionSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    tab: CollectionTab,
    filtersOn: Int,
    filterOpen: Boolean,
    onFilter: (() -> Unit)?,
    viewMode: CardViewMode?,
    onViewMode: (CardViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val hint = when (tab) {
        CollectionTab.CARDS -> "Search your cards, e.g. ramp"
        CollectionTab.BINDERS -> "Search binders and goals"
        CollectionTab.STORAGE -> "Search your places"
        CollectionTab.WISHLIST -> "Search your wishlist"
    }
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(hint, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
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
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = TextMuted)
                    }
                }
                if (viewMode != null) ViewModeButton(viewMode, onViewMode)
                if (onFilter != null) {
                    IconButton(onClick = onFilter) {
                        BadgedBox(badge = { if (filtersOn > 0) Badge(containerColor = Gold, contentColor = OnGold) { Text("$filtersOn") } }) {
                            Icon(
                                Icons.Filled.FilterList,
                                contentDescription = if (filtersOn > 0) "Filters, $filtersOn on" else "Filters",
                                tint = if (filtersOn > 0 || filterOpen) Gold else TextMuted
                            )
                        }
                    }
                }
            }
        },
        modifier = modifier.fillMaxWidth().semantics { contentDescription = hint }
    )
}

/** "Needs attention · 3": opens Upkeep's list. Not shown when there's nothing. */
@Composable
private fun NeedsAttentionChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.warning.copy(alpha = 0.14f))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$label: open Upkeep" }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = colors.warning, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = colors.warning)
    }
}

/** "Search everywhere for "ram"": Find anything — your cards, places and decks at once. */
@Composable
internal fun SearchEverywhereRow(query: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Icon(Icons.Filled.TravelExplore, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text("Search everywhere for “${query.trim()}”", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Your cards, places and decks", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.textDim)
    }
}

/** A group's header on Cards: "Dominaria  41/92 · 12 cards". */
@Composable
private fun GroupHeader(title: String, detail: String?, count: Int, onClick: (() -> Unit)? = null) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = 40.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).a11yHeading())
        if (detail != null) Text(detail, style = MaterialTheme.typography.labelLarge, color = colors.accent, modifier = Modifier.padding(start = 8.dp).semantics { contentDescription = "$detail of the set" })
        Spacer(Modifier.weight(1f))
        Text("$count ${if (count == 1) "card" else "cards"}", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
        if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.textDim)
    }
}

@Composable
private fun CardsTab(
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
    // The header's search filters the list; the dashboard still reflects the whole collection.
    query: String,
    cardFilter: CollectionFilter,
    onCardFilterChange: (CollectionFilter) -> Unit,
    filterOpen: Boolean,
    filtering: Boolean,
    chips: List<ActiveChip>,
    onRemoveChip: (String) -> Unit,
    onClearFilters: () -> Unit,
    onOpenAdvanced: () -> Unit,
    /** The user's own tags, offered in the filter panel's Tags row. */
    userTagOptions: List<String> = emptyList(),
    /** The cards before the quick filter (for its counts), and after. */
    unquick: List<AllCardEntry>,
    filtered: List<AllCardEntry>,
    groupBy: CardGroupBy,
    onGroupBy: (CardGroupBy) -> Unit,
    quick: QuickFilter?,
    onQuick: (QuickFilter?) -> Unit,
    quickSets: QuickSets,
    onManageQuick: (QuickFilter) -> Unit,
    chipIndex: Map<String, List<LocationChip>>,
    chipActions: (AllCardEntry) -> ChipActions,
    cardFacts: Map<String, CardFacts>,
    selecting: Boolean,
    pickedIds: Set<String>,
    onToggle: (String) -> Unit,
    dashboard: CollectionDashboard?,
    breakdown: CollectionBreakdown?,
    prices: Map<String, Double>,
    viewMode: CardViewMode,
    gridColumns: Int,
    onViewDetails: (String) -> Unit,
    onSearchEverywhere: () -> Unit,
    onOpenSet: (String) -> Unit,
    viewModel: CollectionsViewModel
) {
    // Tapping a card enlarges it (swipeable through the filtered list) with value/total.
    var zoomId by remember { mutableStateOf<String?>(null) }
    // Name of the card whose "find similar" overlay is open, if any.
    var similarSearchFor by remember { mutableStateOf<String?>(null) }
    val money = rememberMoney()
    val gridCols = adaptiveGridColumns(gridColumns)
    val listCols = adaptiveListColumns()
    val collections by viewModel.collections.collectAsState()

    // Group by set needs Scryfall's sets for each set's size: "41/92".
    LaunchedEffect(groupBy) { if (groupBy == CardGroupBy.SET) viewModel.loadSets() }
    val setProgress by viewModel.setProgress.collectAsState()
    val bySet = remember(setProgress) { setProgress.orEmpty().associateBy { it.set.code.lowercase() } }
    val groups: List<LayoutGroup<AllCardEntry>>? = remember(filtered, groupBy, cardFacts, chipIndex, bySet) {
        when (groupBy) {
            CardGroupBy.NONE -> null
            CardGroupBy.SET -> groupLayout(filtered, { c -> setKeys(cardFacts[c.scryfallId]?.let { it.set to it.setName }) }, "Set not known yet") { code ->
                bySet[code]?.let { setGroupDetail(it.owned, it.total) }
            }
            CardGroupBy.BINDER -> groupLayout(filtered, { c ->
                binderKeys(c.sources.map { Triple(if (it.kind == SourceKind.DECK) "deck" else "binder", it.id, it.name) })
            }, "No binder")
            CardGroupBy.PLACE -> groupLayout(filtered, { c -> placeKeys(chipIndex[c.scryfallId].orEmpty()) }, NO_PLACE_TITLE)
        }
    }

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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (filterOpen) {
                item { CollectionFilterPanel(cardFilter, onCardFilterChange, onAdvanced = onOpenAdvanced, onClear = onClearFilters, anyOn = filtering, userTagOptions = userTagOptions) }
            } else if (filtering) {
                item { ActiveFilterChips(chips, onRemoveChip, onClearFilters) }
            }
            // Quick filters: one at a time; each has its full screen behind "Manage…".
            item(key = "quick") {
                val ids = remember(unquick) { unquick.map { it.scryfallId to it.name } }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    QuickFilter.entries.forEach { q ->
                        PillChip(q.label, q == quick, { onQuick(q) }, count = quickCount(q, ids, quickSets))
                    }
                }
            }
            if (quick != null) item(key = "manage") {
                TextButton(onClick = { onManageQuick(quick) }) { Text("Manage… · ${quick.manage}", color = Gold) }
            }
            item(key = "group") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    Text("Group by", style = MaterialTheme.typography.labelLarge, color = TextMuted, modifier = Modifier.padding(end = 2.dp))
                    CardGroupBy.entries.forEach { g -> PillChip(g.label, g == groupBy, { onGroupBy(g) }) }
                }
            }
            if (query.isBlank() && quick == null && unsorted != null && unsorted.entries.isNotEmpty()) {
                item { UnsortedRow(unsorted) { onOpenUnsorted(unsorted.id) } }
            }
            if (query.isBlank() && quick == null && groupBy == CardGroupBy.NONE) {
                item { DashboardPanel(dashboard) }
                // Where the value sits — by set, colour, rarity, type — and the dearest cards.
                item { BreakdownPanel(breakdown, onViewDetails) }
            }
            if (spares > 0 && quick == null) {
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
            if (thinCount != null && quick == null) {
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
                val label = if (filtering || quick != null) {
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
                    EmptyPrompt(
                        Icons.Filled.SearchOff,
                        when {
                            quick != null && query.isBlank() && !filtering -> "Nothing ${quick.label.lowercase()}."
                            filtering || quick != null -> "No cards match these filters."
                            else -> "No cards match \"$query\"."
                        }
                    )
                }
            } else {
                val rows: LazyListScope.(List<AllCardEntry>, String) -> Unit = { list, prefix ->
                    if (viewMode == CardViewMode.GRID) {
                        cardGrid(list, columns = gridCols, key = { prefix + it.scryfallId }) { card ->
                            AllCardTile(
                                card = card,
                                selecting = selecting,
                                selected = card.scryfallId in pickedIds,
                                onClick = { if (selecting) onToggle(card.scryfallId) else zoomId = card.scryfallId },
                                onLongClick = { onToggle(card.scryfallId) }
                            )
                        }
                    } else {
                        cardGrid(list, columns = listCols, key = { prefix + it.scryfallId }) { card ->
                            AllCardRow(
                                card = card,
                                chips = chipIndex[card.scryfallId].orEmpty(),
                                chipActions = chipActions(card),
                                selecting = selecting,
                                selected = card.scryfallId in pickedIds,
                                onClick = { if (selecting) onToggle(card.scryfallId) else zoomId = card.scryfallId },
                                onLongClick = { onToggle(card.scryfallId) }
                            )
                        }
                    }
                }
                if (groups == null) this.rows(filtered, "")
                else groups.forEach { g ->
                    item(key = "group:" + g.key) {
                        // A set's header opens the set: its cards, owned and missing.
                        GroupHeader(g.title, g.detail, g.items.size, onClick = if (groupBy == CardGroupBy.SET && g.key.isNotEmpty()) ({ onOpenSet(g.key) }) else null)
                    }
                    this.rows(g.items, g.key + "|")
                }
            }
            if (query.isNotBlank()) item(key = "everywhere") { SearchEverywhereRow(query, onSearchEverywhere) }
        }
    }

    zoomId?.let { id ->
        // Owned cards across all binders/decks: show value, total, and which binders/decks hold it,
        // with where each binder's copies are kept.
        val places = remember(collections) { placesOf(collections) }
        val zoomCards = filtered.map { c ->
            ZoomCard(
                imageUrl = c.imageUrl,
                cardName = c.name,
                priceUsd = prices[c.scryfallId],
                quantity = c.total,
                sources = keptSources(c, collections, places),
                // This entry is one printing shared by every binder/deck in its sources, so
                // re-arting it updates the printing everywhere it's held, not just one place.
                onSelectPrinting = { chosen -> viewModel.changePrintingEverywhere(c.scryfallId, chosen) },
                onViewDetails = { zoomId = null; onViewDetails(c.name) },
                backImageUrl = c.backImageUrl,
                tags = if (c.proxies > 0) c.tags + "proxy" else c.tags,
                // No "add" here — a Cards entry already lives in a specific binder/deck, and
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

/** A Cards entry's binders and decks for its zoom, each binder with where its copies are kept ("Red box ×2"). */
private fun keptSources(card: AllCardEntry, collections: List<Collection>, places: List<com.mtgcompanion.app.data.StoragePlace>) =
    card.sources.map { s ->
        if (s.kind != SourceKind.BINDER || s.kept.isNotEmpty()) s
        else {
            val entry = collections.firstOrNull { it.id == s.id }?.entries?.firstOrNull { it.scryfallId == card.scryfallId }
            if (entry == null) s else s.copy(kept = com.mtgcompanion.app.data.keptLabel(entry, places))
        }
    }

@Composable
private fun BindersTab(
    unsorted: Collection?,
    onOpenUnsorted: (String) -> Unit,
    collections: List<Collection>,
    decks: List<com.mtgcompanion.app.data.Deck>,
    query: String,
    onCollectionClick: (String) -> Unit,
    onDelete: (String) -> Unit,
    tagBinders: List<TagBinder>,
    tagging: Pair<Int, Int>?,
    onOpenTag: (String) -> Unit,
    onImport: () -> Unit,
    onNewBinder: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenGoal: (String) -> Unit,
    onNewGoal: () -> Unit,
    onSearchEverywhere: () -> Unit
) {
    // Binder pending a delete-confirmation, if any.
    var confirmDelete by remember { mutableStateOf<Collection?>(null) }
    val listCols = adaptiveListColumns()
    val money = rememberMoney()
    val known = remember(collections) { placesOf(collections).map { it.id }.toSet() }
    val q = query.trim()
    // The binders: not the Unsorted pile (on top), not the Wishlist (a tab of its own).
    val binders = collections.filter { !it.isUnsorted && !it.isWishlist && it.kind != CollectionType.WISHLIST }
        .filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) }
    val empty = collections.none { !it.isUnsorted && !it.isWishlist && it.kind != CollectionType.WISHLIST } && unsorted?.entries.isNullOrEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (unsorted != null && q.isEmpty()) {
            item(key = "unsorted") { UnsortedRow(unsorted) { onOpenUnsorted(unsorted.id) } }
        }
        item(key = "binders-head") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text("Binders", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = TextPrimary, modifier = Modifier.weight(1f).a11yHeading())
                TextButton(onClick = onNewBinder) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Gold, modifier = Modifier.size(18.dp))
                    Text("New binder", color = Gold, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
        if (empty) {
            item {
                EmptyPrompt(
                    Icons.Filled.Collections,
                    "No binders yet. Import your collection from another app, or make a binder for the cards you own.",
                    actions = listOf(
                        EmptyAction("Import your collection", Icons.AutoMirrored.Filled.PlaylistAdd, onImport),
                        EmptyAction("New binder", Icons.Filled.Add, onNewBinder)
                    )
                )
            }
        } else if (binders.isEmpty() && q.isNotEmpty()) {
            item { Text("No binders match \"$q\".", style = MaterialTheme.typography.bodySmall, color = TextMuted) }
        }
        cardGrid(binders, columns = listCols, key = { it.id }) { collection ->
            val total = collection.entries.sumOf { it.quantity + it.foilQuantity }
            CollectionRow(
                collection = collection,
                kept = keptInPlacesLine(placesUsed(collection, known), total),
                onClick = { onCollectionClick(collection.id) },
                onDelete = { confirmDelete = collection }
            )
        }
        if (q.isEmpty() && (tagBinders.isNotEmpty() || tagging != null)) {
            item(key = "tag-binders") { TagBindersSection(tagBinders, tagging, onOpenTag) }
        }
        item(key = "goals") { GoalsSection(collections, decks, money, q, onOpenGoals, onOpenGoal, onNewGoal) }
        if (q.isNotEmpty()) item(key = "everywhere") { SearchEverywhereRow(q, onSearchEverywhere) }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AllCardRow(
    card: AllCardEntry,
    chips: List<LocationChip>,
    chipActions: ChipActions,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
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
                // Where its copies are: "Blue box › Instants ×2", "Talrand deck ×1", "No place yet · Put away".
                if (!selecting) LocationChips(chips, chipActions)
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
private fun CollectionRow(collection: Collection, kept: String, onClick: () -> Unit, onDelete: (() -> Unit)?) {
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
                    else "$total cards · ${collection.entries.size} unique" + if (kept.isNotEmpty()) " · $kept" else "",
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
