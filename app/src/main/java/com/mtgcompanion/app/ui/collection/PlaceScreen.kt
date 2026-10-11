package com.mtgcompanion.app.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.gestures.scrollBy
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.copiesByPlace
import com.mtgcompanion.app.data.walkPullList
import androidx.compose.foundation.horizontalScroll
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.data.social.TradeCard
import com.mtgcompanion.app.data.social.WantedHere
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CopyHistoryStore
import com.mtgcompanion.app.data.movesOfPlace
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.PlaceKind
import com.mtgcompanion.app.data.CheckSessions
import com.mtgcompanion.app.data.PlacedCard
import com.mtgcompanion.app.data.PocketMove
import com.mtgcompanion.app.data.binderPockets
import com.mtgcompanion.app.data.closeGapsMoves
import com.mtgcompanion.app.data.fitSteps
import com.mtgcompanion.app.data.lastCheckedLabel
import com.mtgcompanion.app.data.looseCopies
import com.mtgcompanion.app.data.relocate
import com.mtgcompanion.app.data.undoMoves
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.data.cardsIn
import com.mtgcompanion.app.data.childrenOf
import com.mtgcompanion.app.data.copiesWithin
import com.mtgcompanion.app.data.deletePlace
import com.mtgcompanion.app.data.pagesOf
import com.mtgcompanion.app.data.parentsOf
import com.mtgcompanion.app.data.placeAndInside
import com.mtgcompanion.app.data.placeSubtitle
import com.mtgcompanion.app.data.placesOf
import com.mtgcompanion.app.data.pockets
import com.mtgcompanion.app.data.savePlace
import com.mtgcompanion.app.data.sectionsOf
import com.mtgcompanion.app.data.storageSummary
import com.mtgcompanion.app.data.lastPileAdded
import com.mtgcompanion.app.data.planSplit
import com.mtgcompanion.app.data.spaceOf
import com.mtgcompanion.app.data.splitBox
import com.mtgcompanion.app.data.withSize
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.common.ArtImage
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.common.StatFigure
import com.mtgcompanion.app.ui.common.rememberMoney
import com.mtgcompanion.app.ui.theme.LocalAppColors
import com.mtgcompanion.app.ui.theme.NumberStyle

/**
 * One storage place, the web app's PlacePage (src/pages/PlacePage.tsx): its copies, their value and
 * its sections — a box's sections with their cards, a binder one page at a time or as a list
 * (BinderPagesView.kt) — the places inside it, "Put cards away" into it with the scanner, Check (scan
 * everything in it, see PlaceCheck.kt) and when it was last checked, and a label to stick on it
 * (PlaceLabelScreen). A binder has Close the gaps and Add cards in order (BinderFitScreen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceScreen(
    placeId: String,
    collections: List<Collection>,
    decks: List<Deck>,
    onBack: () -> Unit,
    onOpenPlace: (String) -> Unit,
    onPutAway: (String) -> Unit,
    onOpenCard: (String) -> Unit,
    onChange: (StorageChange) -> Unit,
    /** The place's label to print (PlaceLabelScreen). */
    onLabel: (String) -> Unit,
    /** The scanner checking the place (PlaceCheck.kt); the check to run is in CheckSessions. */
    onCheck: (String) -> Unit = {},
    /** A binder's Add cards in order (BinderFitScreen). */
    onFit: (String) -> Unit = {},
    /** The page a binder opens at. */
    startPage: Int = 1,
    /** The box section it opens at, open and scrolled to (a card row's place chip). */
    startSection: String? = null,
    /** Lend cards from the place (LendScreen.kt). */
    onLend: (String) -> Unit = {},
    /** Scan a whole binder page (PageScanScreen): the binder and the page shown. */
    onScanPage: (String, Int) -> Unit = { _, _ -> },
    /** For "Friends want these" on a binder; null leaves it out. */
    social: SocialRepository? = null,
    /** Propose a trade with a friend, started with both sides. */
    onProposeTrade: (friend: String, want: List<TradeCard>, give: List<TradeCard>) -> Unit = { _, _, _ -> },
    /** Bring to game night: the cards onto the game night deck's pull list. */
    onBringToGameNight: (List<WantedHere>) -> Unit = {},
    /** Sharing storage at home (HouseholdScreen.kt): the household this place is shared in. */
    onOpenHousehold: (String) -> Unit = {},
    /** The Advanced filters' saved filters (the Cards tab's); null: none to save to. */
    savedFilters: SavedFilters? = null,
    /** Walk this list (WalkListScreen): the list to walk is in WalkSession. */
    onWalk: () -> Unit = {},
    /** Opens a place at a box's section or a binder's page (a result row's place chip). */
    onOpenPlaceAt: (String, String?, Int?) -> Unit = { id, _, _ -> onOpenPlace(id) }
) {
    val colors = LocalAppColors.current
    val historyContext = androidx.compose.ui.platform.LocalContext.current
    remember { CopyHistoryStore.init(historyContext) }
    val history by CopyHistoryStore.moves.collectAsState()
    val money = rememberMoney()
    val places = placesOf(collections)
    val place = places.firstOrNull { it.id == placeId }
    val summary = remember(collections, decks) { storageSummary(collections, decks) }
    // The user's own tags, offered in the filter panel's Tags row as on the Cards tab.
    val userTagOptions = remember(collections, decks) { com.mtgcompanion.app.data.allUserTags(decks, collections).take(12) }
    val cards = remember(collections, placeId) { cardsIn(collections, placeId) }
    // Every copy in it and in the places inside it, for its value.
    val within = remember(collections, placeId) { placeAndInside(places, placeId).flatMap { cardsIn(collections, it) } }
    val ids = remember(within) { within.map { it.entry.scryfallId }.distinct().sorted() }
    // Scryfall's data for the cards here — their prices, and the sets and numbers a binder's order
    // and its pages' summaries use; null until it's loaded.
    var cardData by remember { mutableStateOf<Map<String, ScryfallCard>?>(null) }
    LaunchedEffect(ids) {
        if (ids.isEmpty()) { cardData = emptyMap(); return@LaunchedEffect }
        cardData = runCatching { CardRepository().getCardsByIds(ids).associateBy { it.id } }.getOrDefault(emptyMap())
    }
    // scryfallId → (plain, foil) price in US dollars; null until they've loaded.
    val prices = cardData?.mapValues { (_, c) -> c.prices?.usd?.toDoubleOrNull() to c.prices?.usdFoil?.toDoubleOrNull() }
    val usdOf: ((PlacedCard) -> Double?)? = prices?.let { p -> { c: PlacedCard -> p[c.entry.scryfallId]?.let { (plain, foil) -> placedCopyUsd(c, plain, foil) } } }
    // Search and filter what's here with the Cards tab's filters (PlaceFilter.kt), kept for this place
    // while the app runs.
    val keptFilters by PlaceFilterStore.filters.collectAsState()
    val filter = filterIn(keptFilters, placeId)
    val setFilter: (CardsFilter) -> Unit = { PlaceFilterStore.set(placeId, it) }
    val filtering = filter.active
    var filterOpen by remember { mutableStateOf(false) }
    var advancedOpen by remember { mutableStateOf(false) }
    var showAllRows by remember { mutableStateOf(false) }
    val savedFlow = remember(savedFilters) { savedFilters?.list ?: kotlinx.coroutines.flow.flowOf(emptyList()) }
    val saved by savedFlow.collectAsState(initial = emptyList())
    // The collection's copies (In a deck, Copies) only once an Advanced filter needs them.
    val needCopies = filter.advanced.active || advancedOpen
    val copyFacts = remember(collections, decks, needCopies) { if (needCopies) copyFactsOf(collections, decks) else emptyMap() }
    val facts = remember(cardData, copyFacts, collections, decks, money) {
        storageFilterFacts(
            cardData?.mapValues { CardFacts.of(it.value) }.orEmpty(),
            cardData?.mapValues { advancedFactsOf(it.value) }.orEmpty(),
            copyFacts, collections, decks
        ) { money.toUsd(it) }
    }
    val matchHere = remember(cards, filter, facts) { filterCopies(cards, filter, facts) }
    val matchInside = remember(within, placeId, filter, facts) { filterCopies(within.filter { it.line.placeId != placeId }, filter, facts) }
    val matching = matchHere + matchInside
    var listView by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable(placeId) { mutableIntStateOf(startPage) }
    var choosingCheck by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf<List<PocketMove>?>(null) }
    var closed by remember { mutableStateOf<List<PocketMove>?>(null) }
    var open by remember { mutableStateOf(if (startSection != null) setOf(startSection) else setOf<String>()) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var sizing by remember { mutableStateOf(false) }
    var splitting by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        val parents = place?.let { p -> parentsOf(places, p.id).joinToString(" › ") { it.name } }.orEmpty()
                        if (parents.isNotEmpty()) Text(parents, style = MaterialTheme.typography.labelMedium, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(place?.name ?: "Place", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = {
                    if (place != null) Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreHoriz, contentDescription = "More", tint = colors.textPrimary) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = colors.surface) {
                            DropdownMenuItem(
                                text = { Text("Change place", color = colors.textPrimary) },
                                leadingIcon = { Icon(Icons.Filled.Edit, null, tint = colors.textMuted) },
                                onClick = { menu = false; editing = true }
                            )
                            DropdownMenuItem(
                                text = { Text("New place inside", color = colors.textPrimary) },
                                leadingIcon = { Icon(Icons.Filled.Add, null, tint = colors.textMuted) },
                                onClick = { menu = false; adding = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Change size", color = colors.textPrimary) },
                                leadingIcon = { Icon(Icons.Filled.Inventory2, null, tint = colors.textMuted) },
                                onClick = { menu = false; sizing = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Lend cards from here", color = colors.textPrimary) },
                                leadingIcon = { Icon(Icons.Filled.Handshake, null, tint = colors.textMuted) },
                                onClick = { menu = false; onLend(place.id) }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete place", color = colors.error) },
                                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = colors.error) },
                                onClick = { menu = false; deleting = true }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        }
    ) { padding ->
        if (place == null) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text("This place isn't here any more.", color = colors.textMuted)
            }
            return@Scaffold
        }
        val inside = childrenOf(places, place.id)
        val allSections = sectionsOf(place, cards)
        // While filtering, a box shows only its sections with a match, each open.
        val sections = if (filtering) sectionsOf(place, matchHere).filter { it.cards.isNotEmpty() } else allSections
        val binder = if (place.placeKind == PlaceKind.BINDER) pagesOf(place, cards) else null
        val hits = if (filtering && binder != null) matchingPockets(place, cards, matchHere) else null
        val hitPages = if (filtering && binder != null) matchingPages(place, cards, matchHere) else emptyList()
        val copies = copiesWithin(summary, places, place.id)
        val value = usdOf?.let { p -> within.sumOf { c -> (p(c) ?: 0.0) * c.line.qty } }
        val walk = {
            val q = filter.query.trim()
            WalkSession.start(place.name + if (q.isNotEmpty()) " · “$q”" else "", walkPullList(matching, places))
            onWalk()
        }
        val third = when {
            binder != null -> binder.first.size to if (binder.first.size == 1) "page" else "pages"
            place.placeKind == PlaceKind.BOX || place.sections.orEmpty().isNotEmpty() -> allSections.count { it.name != null } to "sections"
            else -> inside.size to if (inside.size == 1) "place inside" else "places inside"
        }
        // Opened at a section (a card row's chip): scrolled down to it once the list is laid out.
        val sectionKey = startSection?.let { "section:$it" }
        LaunchedEffect(sectionKey, binder == null) {
            if (sectionKey == null || binder != null) return@LaunchedEffect
            androidx.compose.runtime.snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
            repeat(40) {
                val hit = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == sectionKey }
                if (hit != null) { listState.animateScrollToItem(hit.index); return@LaunchedEffect }
                if (!listState.canScrollForward) return@LaunchedEffect
                listState.scrollBy(listState.layoutInfo.viewportSize.height * 0.8f)
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    StatFigure({ Text("$copies", style = NumberStyle(28), color = colors.textPrimary) }, if (copies == 1) "copy" else "copies", Modifier.weight(1f))
                    StatFigure({ Text(value?.let { money.format(it, whole = true) } ?: "—", style = NumberStyle(28), color = colors.accent) }, "value", Modifier.weight(1f))
                    StatFigure({ Text("${third.first}", style = NumberStyle(28), color = colors.textPrimary) }, third.second, Modifier.weight(1f))
                }
            }
            // How full it is, when it has a size (BoxSpace.kt).
            spaceOf(place, collections)?.let { space ->
                item {
                    SpaceCard(
                        place = place,
                        space = space,
                        lastPile = lastPileAdded(history, place.id),
                        expanded = true,
                        canSplit = place.placeKind != PlaceKind.BINDER && planSplit(place, cards) != null,
                        onClick = null,
                        onOpen = null,
                        onSplit = { splitting = true },
                        onSize = { sizing = true }
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Button(
                        onClick = { onPutAway(place.id) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text("Put cards away", fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 8.dp))
                    }
                    Button(
                        onClick = {
                            val named = place.sections.orEmpty().isNotEmpty() || cards.any { it.line.section != null }
                            if (named || CheckSessions.load(place.id) != null) choosingCheck = true
                            else { CheckSessions.save(CheckSessions.Session(place.id, null, emptyList())); onCheck(place.id) }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.surface2, contentColor = colors.textPrimary),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Check", modifier = Modifier.padding(start = 6.dp))
                    }
                    Button(
                        onClick = { onLabel(place.id) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.surface2, contentColor = colors.textPrimary),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Filled.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Label", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
            item(key = "search") {
                PlaceSearchBar(
                    query = filter.query,
                    onQueryChange = { setFilter(filter.copy(query = it)) },
                    hint = "Search ${place.name}, e.g. goblin",
                    filtersOn = filter.count,
                    filterOpen = filterOpen,
                    onFilter = { filterOpen = !filterOpen },
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            item(key = "filters") { CardsFilterArea(filter, setFilter, filterOpen, { advancedOpen = true }, collections, userTagOptions) }
            if (filtering) item(key = "totals") {
                FilterTotalsLine(totalsLine(filterTotals(within, matching, usdOf)) { money.format(it, whole = true) }, matching.isNotEmpty(), walk)
            }
            // Sharing storage at home: who else sees it, or Share at home (nothing before the server has households).
            if (social != null) item { HomeShareLine(place, social, onOpenHousehold) }
            if (binder == null) place.rule?.let { rule ->
                item {
                    Text(
                        "Sorted ${rule.label.replaceFirstChar { it.lowercase() }}. New cards get a section by this rule.",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textMuted
                    )
                }
            }
            if (binder != null) item {
                val rule = place.rule
                Text(
                    "${place.pockets} pockets a page" + if (rule != null) ", in order ${rule.label.replaceFirstChar { it.lowercase() }}. Add cards in order says where new cards go."
                    else ". New cards go in the next free pocket.",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted
                )
            }
            place.lastChecked?.let { at ->
                item { Text("Last checked: ${lastCheckedLabel(at, System.currentTimeMillis())}", style = MaterialTheme.typography.labelMedium, color = colors.textMuted) }
            }
            if (filtering) copyGroupItems(copiesByPlace(matchInside, places), places, showAllRows, { showAllRows = true }, onOpenCard, onOpenPlaceAt, "inside-match:")
            else items(inside, key = { "inside:" + it.id }) { p ->
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surface2).padding(horizontal = 12.dp, vertical = 10.dp)) {
                    PlaceRow(placeIcon(p.placeKind), p.name, placeSubtitle(p), "${copiesWithin(summary, places, p.id)}", gold = true, onClick = { onOpenPlace(p.id) })
                }
            }
            if (binder != null) {
                val pocketsInUse = binderPockets(place, cards)
                val waiting = looseCopies(place, cards).size
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        ViewChip("Pages", !listView) { listView = false }
                        ViewChip("List", listView) { listView = true }
                        Box(Modifier.weight(1f))
                        val pageWord = if (binder.first.size == 1) "page" else "pages"
                        val of = if (filtering) "${matchHere.sumOf { it.line.qty }} of " else ""
                        Text("$of${cards.sumOf { it.line.qty }} cards · ${binder.first.size} $pageWord", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                    }
                }
                // Which pages have a match: tap one to turn to it.
                if (filtering && !listView) item(key = "match-pages") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        if (hitPages.isEmpty()) Text("No pocket here matches.", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                        else {
                            Text(if (hitPages.size == 1) "Matches on page" else "Matches on pages", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                            hitPages.forEach { p -> ViewChip("$p", p == page) { page = p } }
                        }
                    }
                }
                item {
                    if (listView) BinderList(place, if (filtering) matchHere else cards, onOpenCard)
                    else BinderPagesView(place, collections, cardData, page, { page = it }, onChange, onOpenCard, hits = hits)
                }
                if (!listView) item {
                    Button(
                        onClick = { onScanPage(place.id, page.coerceIn(1, binder.first.size + 1)) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.surface2, contentColor = colors.textPrimary),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Scan this page", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                val loose = if (filtering) binder.second.filter { it in matchHere } else binder.second
                if (!listView && loose.isNotEmpty()) item {
                    CardGroup("Not in a pocket yet", loose.sumOf { it.line.qty }, loose, true, {}, onOpenCard)
                }
                if (cards.isEmpty()) item {
                    Text("Nothing here yet. Put cards away to fill it, pocket by pocket.", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                }
                closed?.let { moves ->
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surface2).padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("Closed the gaps — ${moves.size} ${if (moves.size == 1) "card" else "cards"} moved", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
                            TextButton(onClick = { onChange { relocate(it, place, undoMoves(moves)) }; closed = null }) { Text("Undo", color = colors.accent) }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Button(
                            onClick = { closing = closeGapsMoves(pocketsInUse.map { it.index }) },
                            enabled = pocketsInUse.isNotEmpty(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.surface2, contentColor = colors.textPrimary),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) { Text("Close the gaps", fontWeight = FontWeight.Bold) }
                        Button(
                            onClick = { onFit(place.id) },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) { Text("Add cards in order" + if (waiting > 0) " ($waiting)" else "", fontWeight = FontWeight.ExtraBold, maxLines = 1) }
                    }
                }
            } else {
                items(sections, key = { "section:" + (it.name ?: "") }) { s ->
                    val key = s.name ?: ""
                    CardGroup(
                        title = s.name ?: if (sections.size > 1) "No section" else "Cards",
                        copies = s.copies,
                        cards = s.cards,
                        isOpen = filtering || key in open || (sections.size == 1 && s.name == null),
                        onToggle = { open = if (key in open) open - key else open + key },
                        onOpenCard = onOpenCard
                    )
                }
                if (filtering && matching.isEmpty()) item {
                    Text("No cards here match.", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                }
                if (!filtering && cards.isEmpty() && sections.isEmpty() && inside.isEmpty()) item {
                    Text("Nothing here yet. Put cards away to fill it.", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                }
            }
            // Friends whose wishlists want cards in this binder (FriendsWant.kt).
            if (binder != null && social != null && cards.isNotEmpty()) item {
                FriendsWantSection(
                    social = social,
                    cards = cards,
                    priceOf = { c -> prices?.get(c.entry.scryfallId)?.let { (plain, foil) -> if (c.line.isFoil) foil ?: plain else plain ?: foil } },
                    onPropose = onProposeTrade,
                    onBringToGameNight = onBringToGameNight
                )
            }
            // What came and went lately, on this phone (CopyHistory.kt).
            val recent = movesOfPlace(history, placeAndInside(places, placeId), 10)
            if (recent.isNotEmpty()) {
                item {
                    Text("RECENT MOVES", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = colors.textMuted, modifier = Modifier.padding(top = 18.dp))
                }
                item { MoveList(recent, named = true) }
            }
        }
    }

    if (place != null && advancedOpen) {
        CardsAdvancedFilters(
            filter = filter,
            onChange = setFilter,
            countFor = { f -> filterCopies(within, f, facts).sumOf { it.line.qty } },
            collections = collections,
            sets = cardData.orEmpty().values.mapNotNull { c -> c.set?.takeIf { it.isNotBlank() }?.let { it.lowercase() to (c.setName ?: it.uppercase()) } }
                .distinctBy { it.first }.sortedBy { it.second.lowercase() },
            saved = saved,
            savedFilters = savedFilters,
            onDismiss = { advancedOpen = false }
        )
    }
    if (place != null && choosingCheck) {
        val going = CheckSessions.load(place.id)
        val what = when (place.placeKind) { PlaceKind.BINDER -> "binder"; PlaceKind.BOX -> "box"; else -> "place" }
        val names = sectionsOf(place, cards).mapNotNull { it.name }
        val start = { section: String? ->
            choosingCheck = false
            CheckSessions.save(CheckSessions.Session(place.id, section, emptyList()))
            onCheck(place.id)
        }
        AlertDialog(
            onDismissRequest = { choosingCheck = false },
            containerColor = colors.surface,
            title = { Text("Check ${place.name}", color = colors.accentLight) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Scan everything in it, then see what's missing and what's extra.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    if (going != null) TextButton(onClick = { choosingCheck = false; onCheck(place.id) }) {
                        Text("Carry on checking · ${going.section ?: "whole $what"} · ${going.scans.size} scanned", color = colors.accent)
                    }
                    TextButton(onClick = { start(null) }) { Text("Whole $what", color = colors.textPrimary) }
                    names.forEach { name -> TextButton(onClick = { start(name) }) { Text(name, color = colors.textPrimary) } }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosingCheck = false }) { Text("Cancel", color = colors.textMuted) } }
        )
    }
    val closingMoves = closing
    if (place != null && closingMoves != null) {
        val pocketsInUse = binderPockets(place, cards)
        AlertDialog(
            onDismissRequest = { closing = null },
            containerColor = colors.surface,
            title = { Text("Close the gaps?", color = colors.accentLight) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    if (closingMoves.isEmpty()) {
                        Text("There are no empty pockets between the cards.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    } else {
                        Text(
                            "${closingMoves.size} ${if (closingMoves.size == 1) "card moves" else "cards move"} back to fill the empty pockets, in the same order. You can undo it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )
                        fitSteps(
                            com.mtgcompanion.app.data.FitPlan(closingMoves, emptyList()),
                            place.pockets,
                            { i -> pocketsInUse.firstOrNull { it.index == i }?.cards?.firstOrNull()?.entry?.name ?: "the card" },
                            { "" to "" }
                        ).forEachIndexed { i, st ->
                            Text("${i + 1}. ${st.title}" + if (st.detail.isNotEmpty()) " — ${st.detail}" else "", style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                if (closingMoves.isNotEmpty()) TextButton(onClick = {
                    onChange { relocate(it, place, closingMoves) }
                    closed = closingMoves
                    closing = null
                }) { Text("Close the gaps", color = colors.accent) }
            },
            dismissButton = { TextButton(onClick = { closing = null }) { Text("Cancel", color = colors.textMuted) } }
        )
    }
    if (place != null && editing) {
        PlaceDialog(place = place, parentId = null, places = places, onDismiss = { editing = false }) { changed ->
            onChange { savePlace(it, changed) }
            editing = false
        }
    }
    if (place != null && adding) {
        PlaceDialog(place = null, parentId = place.id, places = places, onDismiss = { adding = false }) { made ->
            onChange { savePlace(it, made) }
            adding = false
        }
    }
    if (place != null && sizing) {
        SizeDialog(place, onDismiss = { sizing = false }) { n ->
            onChange { current -> placesOf(current).firstOrNull { it.id == place.id }?.let { savePlace(current, withSize(it, n)) } ?: current }
            sizing = false
        }
    }
    val plan = if (place != null && splitting) planSplit(place, cards) else null
    if (place != null && plan != null) {
        AlertDialog(
            onDismissRequest = { splitting = false },
            containerColor = colors.surface,
            text = { SplitSection(place, plan, places) },
            confirmButton = {
                TextButton(onClick = {
                    val id = java.util.UUID.randomUUID().toString()
                    val now = System.currentTimeMillis()
                    onChange { current ->
                        val p = placesOf(current).firstOrNull { it.id == place.id }
                        val fresh = p?.let { planSplit(it, cardsIn(current, it.id)) }
                        if (fresh == null) current else splitBox(current, place.id, fresh, id, now)
                    }
                    splitting = false
                    onLabel(id)
                }) { Text("Split and print new label", color = colors.accent) }
            },
            dismissButton = { TextButton(onClick = { splitting = false }) { Text("Cancel", color = colors.textMuted) } }
        )
    }
    if (place != null && deleting) {
        val here = cards.sumOf { it.line.qty }
        val inside = childrenOf(places, place.id)
        AlertDialog(
            onDismissRequest = { deleting = false },
            containerColor = colors.surface,
            title = { Text("Delete “${place.name}”?", color = colors.accentLight) },
            text = {
                Text(
                    (if (here > 0) "Its $here copies stay in your collection with no place" else "Its cards stay in your collection") +
                        (if (inside.isNotEmpty()) ", and the places inside it move up a level" else "") + ". Here and on your other devices.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )
            },
            confirmButton = {
                TextButton(onClick = { deleting = false; onChange { deletePlace(it, place.id) }; onBack() }) { Text("Delete place", color = colors.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel", color = colors.textMuted) } }
        )
    }
}

/** A section of a place (or any group of its cards): its name and count, and its cards when open. */
@Composable
private fun CardGroup(
    title: String,
    copies: Int,
    cards: List<PlacedCard>,
    isOpen: Boolean,
    onToggle: () -> Unit,
    onOpenCard: (String) -> Unit
) {
    val colors = LocalAppColors.current
    val shown = isOpen && cards.isNotEmpty()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .then(if (shown) Modifier.border(1.dp, colors.accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp)) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp).clickable(onClick = onToggle)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Text("$copies", style = MaterialTheme.typography.labelLarge, color = colors.textMuted)
        }
        if (shown) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 10.dp)) {
                cards.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onOpenCard(c.entry.name) }) {
                        ArtImage(
                            c.entry.imageUrl.toArtCropUrl(),
                            c.entry.name,
                            Modifier.width(30.dp).height(42.dp).clip(RoundedCornerShape(4.dp))
                        )
                        Text(
                            c.entry.name + if (c.line.isFoil) " · foil" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(start = 10.dp)
                        )
                        Text("×${c.line.qty}", style = MaterialTheme.typography.labelLarge, color = colors.textMuted)
                    }
                }
            }
        }
    }
}

/** A chip that's on or off: "Pages" / "List". */
@Composable
private fun ViewChip(label: String, on: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
        color = if (on) colors.onAccent else colors.textMuted,
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) colors.accent else colors.surface2).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

