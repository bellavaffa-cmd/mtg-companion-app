package com.mtgcompanion.app.ui.decks

import com.mtgcompanion.app.ui.common.AddToPick
import com.mtgcompanion.app.data.AddCandidate
import com.mtgcompanion.app.ui.common.toAddItem
import com.mtgcompanion.app.ui.common.checkFor
import com.mtgcompanion.app.ui.common.AddItem
import com.mtgcompanion.app.ui.common.AddCheck
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.lazy.LazyRow
import com.mtgcompanion.app.data.madeByLabel
import com.mtgcompanion.app.data.holdsOwnCopies
import com.mtgcompanion.app.data.holdsCards
import com.mtgcompanion.app.data.pullNeeds
import com.mtgcompanion.app.data.proxyCopies
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Unarchive
import com.mtgcompanion.app.data.realCopiesOf
import com.mtgcompanion.app.data.ProxyHeldElsewhere
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.mtgcompanion.app.ui.social.GoldButton
import com.mtgcompanion.app.data.ProxySwap
import com.mtgcompanion.app.ui.common.rememberMoney
import com.mtgcompanion.app.data.RoleTags
import com.mtgcompanion.app.data.CardGroup
import com.mtgcompanion.app.data.isLandType
import com.mtgcompanion.app.data.DeckRole
import com.mtgcompanion.app.ui.common.zoomSource
import androidx.compose.foundation.layout.BoxWithConstraints
import com.mtgcompanion.app.ui.common.gridColumnsFor
import com.mtgcompanion.app.ui.common.listColumnsFor
import com.mtgcompanion.app.ui.common.LocalLayoutSize
import com.mtgcompanion.app.ui.common.LayoutSize
import com.mtgcompanion.app.ui.theme.Surface3
import com.mtgcompanion.app.ui.theme.Surface2
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import com.mtgcompanion.app.ui.theme.NumberStyle
import com.mtgcompanion.app.ui.theme.LocalAppColors
import com.mtgcompanion.app.ui.common.sharedArt
import com.mtgcompanion.app.ui.common.popSpring
import com.mtgcompanion.app.ui.common.SharedKeys
import com.mtgcompanion.app.ui.common.SegmentedTabs
import com.mtgcompanion.app.ui.common.ManaPips
import com.mtgcompanion.app.ui.common.IdentityStrip
import com.mtgcompanion.app.ui.common.CountUpText
import com.mtgcompanion.app.ui.common.ArtImage
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Pets
import com.mtgcompanion.app.data.DeckGrouping
import com.mtgcompanion.app.data.GroupingFacts
import com.mtgcompanion.app.data.categoryCounts
import com.mtgcompanion.app.data.companionEntry
import com.mtgcompanion.app.data.companionNamed
import com.mtgcompanion.app.data.folderNames
import com.mtgcompanion.app.data.folderOf
import com.mtgcompanion.app.data.groupCards
import com.mtgcompanion.app.data.isArchived
import com.mtgcompanion.app.data.removedCategory
import com.mtgcompanion.app.data.renamedCategory
import com.mtgcompanion.app.data.tidyCategory
import com.mtgcompanion.app.data.tidyDescription
import com.mtgcompanion.app.data.withArchived
import com.mtgcompanion.app.data.withCardCategories
import com.mtgcompanion.app.data.withCategoryTarget
import com.mtgcompanion.app.data.withCompanion
import com.mtgcompanion.app.data.withFolder
import com.mtgcompanion.app.data.withSuggestedCategories
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import com.mtgcompanion.app.data.StatsPanels
import com.mtgcompanion.app.data.oddsPercent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import com.mtgcompanion.app.data.LegalityReport
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.mtgcompanion.app.data.CardViewMode
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckCardEntry
import com.mtgcompanion.app.data.DeckOwnership
import com.mtgcompanion.app.data.GameMode
import com.mtgcompanion.app.data.LegalityIssue
import com.mtgcompanion.app.data.LegalityIssueKind
import com.mtgcompanion.app.data.SecondCommanderKind
import com.mtgcompanion.app.data.canPair
import com.mtgcompanion.app.data.pairCard
import com.mtgcompanion.app.data.secondCommanderKind
import com.mtgcompanion.app.data.withPairingFrom
import com.mtgcompanion.app.data.canLead
import com.mtgcompanion.app.data.VersionSummary
import com.mtgcompanion.app.data.cardNameKeys
import com.mtgcompanion.app.data.DeckExportFormat
import com.mtgcompanion.app.data.deckExportText
import com.mtgcompanion.app.data.isOwnedName
import com.mtgcompanion.app.data.sideboardCount
import com.mtgcompanion.app.data.sideboardName
import com.mtgcompanion.app.data.poolCopies
import com.mtgcompanion.app.data.poolGroups
import com.mtgcompanion.app.ui.common.SourceKind
import com.mtgcompanion.app.ui.common.cardsSubject
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Landscape
import com.mtgcompanion.app.network.edhrec.EdhrecCardView
import com.mtgcompanion.app.network.edhrec.inclusionPercent
import com.mtgcompanion.app.network.edhrec.scryfallImageUrl
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.network.spellbook.Variant
import com.mtgcompanion.app.ui.common.AnimatedUsdText
import com.mtgcompanion.app.ui.common.CardActionMenu
import com.mtgcompanion.app.ui.common.CardMenuAction
import com.mtgcompanion.app.ui.common.CardZoomDialog
import com.mtgcompanion.app.ui.common.SimilarCardsDialog
import com.mtgcompanion.app.ui.common.ComboDetailDialog
import com.mtgcompanion.app.ui.common.ComboSummaryRow
import com.mtgcompanion.app.ui.common.GameModeDropdown
import com.mtgcompanion.app.ui.common.cardGrid
import com.mtgcompanion.app.ui.common.ConfirmDeleteDialog
import com.mtgcompanion.app.ui.common.elevatedCard
import com.mtgcompanion.app.ui.common.FlipBadge
import com.mtgcompanion.app.ui.common.ManaSymbol
import com.mtgcompanion.app.ui.common.AddToPicker
import com.mtgcompanion.app.ui.common.AddVerb
import com.mtgcompanion.app.ui.common.LocalAddToFeedback
import com.mtgcompanion.app.ui.common.addToMessage
import com.mtgcompanion.app.ui.common.asTarget
import com.mtgcompanion.app.ui.common.quantityLimits
import com.mtgcompanion.app.ui.common.ZoomCard
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
import com.mtgcompanion.app.ui.badge.BadgeSheetDialog

/**
 * Tab order. The Considering tab sits right beside Cards, since the two are worked together.
 * Legality has no tab of its own: its badge heads Stats (as on the web), and opens the list there.
 */
private val DECK_TABS = listOf("Cards", "Considering", "Stats", "Suggestions")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DeckDetailScreen(
    viewModel: DeckDetailViewModel,
    onBack: () -> Unit,
    onViewDetails: (String) -> Unit,
    onShare: (() -> Unit)? = null,
    /** "Who has it?" for the cards the user doesn't own (signed in only). */
    onWhoHasIt: ((names: List<String>) -> Unit)? = null,
    /** Opens another of the user's decks — where a proxy's real copy is. */
    onOpenDeck: ((String) -> Unit)? = null,
    /** Puts one of this deck's tokens onto an NFC e-paper badge. */
    onOpenBadge: (() -> Unit)? = null,
    /** The deck's pull list: building it from storage (PullListScreen). */
    onPullList: (() -> Unit)? = null,
    /** The deck's put-back list: taking it apart (PutBackScreen). */
    onTakeApart: (() -> Unit)? = null,
    /** The tab to open on ("Suggestions" for a deck just made with its commander); null for Cards. */
    initialTab: String? = null
) {
    val context = LocalContext.current
    val deck by viewModel.deck.collectAsState()
    val analysis by viewModel.analysis.collectAsState()
    val cardGroups by viewModel.cardGroups.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val prices by viewModel.prices.collectAsState()
    val knownUserTags by viewModel.knownUserTags.collectAsState()
    val userTagsByCard by viewModel.userTagsByCard.collectAsState()
    val layout = LocalLayoutSize.current
    // On a desktop-width window Stats sits in a panel beside the cards, so it isn't a tab there.
    val tabs = if (layout == LayoutSize.DESKTOP) DECK_TABS.filter { it != "Stats" } else DECK_TABS
    // Links made when Legality was a tab of its own open on Stats, where it lives now.
    val startTab = if (initialTab == "Legality") "Stats" else initialTab
    val pagerState = rememberPagerState(initialPage = (startTab?.let { tabs.indexOf(it) } ?: 0).coerceAtLeast(0), pageCount = { tabs.size })
    val missing by viewModel.missing.collectAsState()
    val cardTags by viewModel.cardTags.collectAsState()
    // Swap flows: a cut candidate choosing its replacement, or a considered card choosing what it replaces.
    var swapOut by remember { mutableStateOf<DeckCardEntry?>(null) }
    var swapIn by remember { mutableStateOf<DeckCardEntry?>(null) }
    // A combo piece the user asked to mark as a cut candidate, pending their confirmation.
    var comboWarningFor by remember { mutableStateOf<DeckCardEntry?>(null) }
    var showMissing by remember { mutableStateOf(false) }
    val toast = { message: String -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // A physical deck becoming one that holds no real cards: asked what happens to the cards it has.
    var leavingPhysical by remember { mutableStateOf<DeckOwnership?>(null) }
    // Tapping a card enlarges it (swipeable), showing value/total and a quantity stepper.
    // Holds (source, key): source "card" -> deck card by scryfallId, "sugg" -> suggestion by id/name.
    var zoom by remember { mutableStateOf<Pair<String, String>?>(null) }
    // A tag tapped in a card's zoom or in Stats: the Cards tab, searched for it.
    val searchTag: (String) -> Unit = { label ->
        zoom = null
        viewModel.setCardQuery(label)
        val cardsPage = tabs.indexOf("Cards")
        if (cardsPage >= 0) scope.launch { pagerState.animateScrollToPage(cardsPage) }
    }
    // The card whose move-destination picker is open.
    var moveTarget by remember { mutableStateOf<DeckCardEntry?>(null) }
    val moveTargets by viewModel.moveTargets.collectAsState()
    val cardSources by viewModel.cardSources.collectAsState()
    // The card whose "add a copy elsewhere" picker is open (doesn't remove it from this deck).
    var copyTarget by remember { mutableStateOf<DeckCardEntry?>(null) }
    // Name of the card whose "find similar" overlay is open, if any.
    var similarSearchFor by remember { mutableStateOf<String?>(null) }
    // A suggested card (by name, and the card itself when it's known) whose "into the deck or
    // Considering?" picker is open.
    var addSuggestion by remember { mutableStateOf<Pair<String, ScryfallCard?>?>(null) }
    val addTo = LocalAddToFeedback.current
    // The art header shrinks from a full hero to a slim bar as any tab's list scrolls, and grows
    // back when you pull down at the top — driven by the same nested-scroll state a collapsing
    // Material app bar uses, so it works across every page of the pager.
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    // The card pending a remove-confirmation, if any.
    var removeCardTarget by remember { mutableStateOf<DeckCardEntry?>(null) }
    var showImport by remember { mutableStateOf(false) }
    // The pasted list, kept so Cancel on the check before importing goes back to it.
    var importText by remember { mutableStateOf("") }
    var showExport by remember { mutableStateOf(false) }
    var showGoldfish by remember { mutableStateOf(false) }
    // "Compare with…": first the picker (a deck or a saved version), then the comparison itself.
    var comparePicking by remember { mutableStateOf(false) }
    var compareWith by remember { mutableStateOf<CompareTarget?>(null) }
    // A sideboard card whose remove-confirmation is up.
    var removeSideboardTarget by remember { mutableStateOf<DeckCardEntry?>(null) }
    // Draft and sealed: the basic lands being chosen, and the binder the pool is being copied to.
    var addingBasics by remember { mutableStateOf(false) }
    var poolToBinder by remember { mutableStateOf(false) }
    // Progress while an import runs, then its summary ("Imported N; M couldn't be matched…").
    var importState by remember { mutableStateOf<ImportState?>(null) }
    // A card whose categories are being picked, a category being set up, the companion picker and
    // the folder picker (DeckExtrasUi.kt).
    var categoriesFor by remember { mutableStateOf<DeckCardEntry?>(null) }
    var categoryOpen by remember { mutableStateOf<String?>(null) }
    var companionPicking by remember { mutableStateOf(false) }
    var companionError by remember { mutableStateOf<String?>(null) }
    var filing by remember { mutableStateOf(false) }
    val allDecks by viewModel.otherDecks.collectAsState()

    Scaffold(
        containerColor = Bg,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            val density = LocalDensity.current
            val expanded = if (layout.isWide) 320.dp else 284.dp
            val collapsed = 64.dp
            val limit = with(density) { (collapsed - expanded).toPx() }
            SideEffect {
                if (scrollBehavior.state.heightOffsetLimit != limit) scrollBehavior.state.heightOffsetLimit = limit
            }
            val height = expanded + with(density) { scrollBehavior.state.heightOffset.toDp() }
            DeckHero(
                deck = deck,
                analysis = analysis,
                height = height,
                collapsedFraction = scrollBehavior.state.collapsedFraction,
                onBack = onBack,
                onMenu = { menuOpen = true },
                menu = {
                    // The deck's own actions, in a sheet like a card's: each with a line on what it does,
                    // Delete last in the danger style.
                    val d = deck
                    if (d != null) {
                        val deckActions = buildList {
                            if (onShare != null) add(CardMenuAction("Share with friends", Icons.Filled.Group, description = "View only — friends, pods or a link") { onShare() })
                            add(CardMenuAction("Playtest", Icons.Filled.Casino, description = "Mulligan, play or draw, then turns") { showGoldfish = true })
                            add(CardMenuAction("Compare with…", Icons.Filled.Layers, description = "Another deck or a saved version") { comparePicking = true })
                            // Building it from storage, and taking it apart again (PullList.kt).
                            if (onPullList != null) {
                                if (!d.holdsCards) add(CardMenuAction("Build this deck", Icons.Filled.Inventory2, description = "A pull list: its cards, place by place") { onPullList() })
                                else if (pullNeeds(d).isNotEmpty()) add(CardMenuAction("Pull list", Icons.Filled.Inventory2, description = "Fetch the cards it still needs from storage") { onPullList() })
                            }
                            if (onTakeApart != null && d.holdsCards && d.cards.any { it.quantity - proxyCopies(d, it) > 0 }) {
                                add(CardMenuAction("Take apart", Icons.Filled.Unarchive, description = "A list to put its cards back where they go") { onTakeApart() })
                            }
                            add(CardMenuAction("Cards I don't own", Icons.Filled.Sell, description = "Buy them, wishlist them, or ask friends") { showMissing = true })
                            if (d.mode.limited) {
                                add(CardMenuAction("Add basic lands", Icons.Filled.Landscape, description = "17 for 40 cards, by the colours you play") { addingBasics = true })
                                add(CardMenuAction("Add pool to a binder", Icons.Filled.Collections, description = "Every card here, deck and pool, copied into a binder") { poolToBinder = true })
                            }
                            add(CardMenuAction("Import list", Icons.AutoMirrored.Filled.PlaylistAdd, description = "Paste a decklist") { showImport = true })
                            add(CardMenuAction("Export list", Icons.Filled.IosShare, description = "Simple, exact printing, Arena or MTGO") { showExport = true })
                            add(CardMenuAction("Deck settings", Icons.Filled.Tune, description = "Format, ownership and tags") { showSettings = true })
                            if (!d.mode.limited) {
                                add(CardMenuAction("Companion", Icons.Filled.Pets, description = companionNamed(d.companion)?.name ?: "One of the ten, outside the deck") { companionError = null; companionPicking = true })
                            }
                            add(CardMenuAction("Move to folder…", Icons.Filled.Folder, description = folderOf(d)?.let { "In $it" } ?: "File it on your decks list") { filing = true })
                            if (d.isArchived) {
                                add(CardMenuAction("Back on the decks list", Icons.Filled.Unarchive, description = "Out of Archived, and offered in pickers again") {
                                    viewModel.changeDeck { it.withArchived(false) }
                                    toast("Back on your decks list.")
                                })
                            } else {
                                add(CardMenuAction("Archive", Icons.Filled.Archive, description = "Kept, but hidden from the list and from pickers") {
                                    viewModel.changeDeck { it.withArchived(true) }
                                    toast("Archived. Find it under Archived on your decks list.")
                                })
                            }
                            add(CardMenuAction("Delete deck", Icons.Filled.Delete, destructive = true) { confirmDelete = true })
                        }
                        val cardCount = d.cards.sumOf { it.quantity }
                        CardActionMenu(
                            expanded = menuOpen,
                            onDismiss = { menuOpen = false },
                            actions = deckActions,
                            title = d.name,
                            subtitle = "$cardCount card${if (cardCount == 1) "" else "s"} · ${d.ownershipType.label}",
                            imageUrl = d.commander?.imageUrl.toArtCropUrl()
                        )
                    }
                }
            )
        }
    ) { padding ->
        val currentDeck = deck ?: return@Scaffold
        // A considered card swapped in for one in the deck: checked first, like any card going in.
        val swapChecked = { outgoing: DeckCardEntry, incoming: DeckCardEntry ->
            addTo.perform(
                "Swapped in ${incoming.name} for ${outgoing.name}",
                check = AddCheck(AddToPick(currentDeck.asTarget()), listOf(incoming.toAddItem()))
            ) { viewModel.swap(outgoing.scryfallId, incoming.scryfallId) }
        }

        Row(modifier = Modifier.fillMaxSize().background(Bg).padding(padding)) {
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            SegmentedTabs(
                labels = tabs,
                selected = pagerState.currentPage.coerceAtMost(tabs.size - 1),
                onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                counts = mapOf(tabs.indexOf("Considering") to currentDeck.considering.size),
                modifier = Modifier.padding(horizontal = if (layout.isWide) layout.pagePadding - 4.dp else 16.dp, vertical = 8.dp)
            )

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (tabs.getOrNull(page)) {
                    "Cards" -> CardsTab(
                        currentDeck,
                        analysis,
                        onZoomCard = { zoom = "card" to it },
                        cardActions = { entry ->
                            deckCardActions(
                                entry = entry,
                                mode = currentDeck.mode,
                                isCommander = currentDeck.commander?.scryfallId == entry.scryfallId,
                                hasCommander = currentDeck.commander != null,
                                isPartnerCommander = currentDeck.partnerCommander?.scryfallId == entry.scryfallId,
                                canPartnerWithCommander = currentDeck.commander?.let { commander ->
                                    canPair(
                                        commander.withPairingFrom(analysis.pairingAbilities),
                                        entry.withPairingFrom(analysis.pairingAbilities)
                                    )
                                } ?: false,
                                secondCommanderNoun = currentDeck.commander
                                    ?.let { secondCommanderKind(it.withPairingFrom(analysis.pairingAbilities).pairCard) }
                                    ?.noun ?: SecondCommanderKind.PARTNER.noun,
                                onViewDetails = onViewDetails,
                                onCopy = { copyTarget = it },
                                onMove = { moveTarget = it },
                                onRemove = { removeCardTarget = it },
                                onSetCommander = { viewModel.setCommander(it) },
                                onSetPartnerCommander = { viewModel.setPartnerCommander(it) },
                                hasConsidering = currentDeck.considering.isNotEmpty(),
                                onToggleReplaceable = { card ->
                                    val combos = cardNameKeys(card.name).flatMap { analysis.comboPieces[it].orEmpty() }.distinctBy { it.id }
                                    when {
                                        card.replaceable -> viewModel.setReplaceable(card.scryfallId, false)
                                        combos.isNotEmpty() -> comboWarningFor = card
                                        else -> viewModel.setReplaceable(card.scryfallId, true)
                                    }
                                },
                                onMoveToConsidering = { entry -> addTo.perform(addToMessage(AddVerb.MOVE, entry.name, currentDeck.name, considering = true)) { viewModel.moveToConsidering(entry.scryfallId) } },
                                onSwap = { swapOut = it },
                                onCategories = { categoriesFor = it },
                                hasSideboard = currentDeck.mode.hasSideboard,
                                sideboardName = sideboardName(currentDeck.mode),
                                onMoveToSideboard = { entry ->
                                    addTo.perform(
                                        addToMessage(AddVerb.MOVE, entry.name, currentDeck.name, quantity = entry.quantity, sideboard = true, pool = currentDeck.mode.limited),
                                        // Asked first when the sideboard would go past its 15 cards.
                                        check = AddCheck.forMove(AddToPick(currentDeck.asTarget()), listOf(entry.toAddItem(entry.quantity, sideboard = true)))
                                    ) { viewModel.moveToSideboard(entry.scryfallId) }
                                }
                            )
                        },
                        viewModel,
                        onZoomSideboard = { zoom = "side" to it },
                        sideboardActions = { entry ->
                            sideboardCardActions(
                                entry = entry,
                                sideboardName = sideboardName(currentDeck.mode),
                                onMoveToMain = {
                                    addTo.perform(addToMessage(AddVerb.MOVE, entry.name, currentDeck.name, quantity = entry.quantity)) { viewModel.moveToMain(entry.scryfallId) }
                                },
                                onRemove = { removeSideboardTarget = entry },
                                onViewDetails = onViewDetails,
                                isCompanion = companionEntry(currentDeck)?.scryfallId == entry.scryfallId,
                                companionRule = companionNamed(entry.name)?.rule,
                                onCompanion = { on -> viewModel.changeDeck { it.withCompanion(if (on) entry.name else null) } }
                            )
                        },
                        onRemoveLastSideboardCopy = { removeSideboardTarget = it },
                        onCategory = { categoryOpen = it }
                    )
                    "Considering" -> ConsideringTab(
                        deck = currentDeck,
                        analysis = analysis,
                        prices = prices,
                        onZoom = { zoom = "consider" to it },
                        onAddToDeck = { entry ->
                            addTo.perform(
                                addToMessage(AddVerb.MOVE, entry.name, currentDeck.name),
                                check = AddCheck(AddToPick(currentDeck.asTarget()), listOf(entry.toAddItem()))
                            ) { viewModel.addConsideredToDeck(entry.scryfallId) }
                        },
                        onSwapIn = { swapIn = it },
                        onRemove = { viewModel.removeFromConsidering(it.scryfallId) }
                    )
                    "Stats" -> StatsTab(analysis, currentDeck, viewModel, onTag = searchTag, onOpenDeck = onOpenDeck, onOpenBadge = onOpenBadge, onCard = onViewDetails)
                    "Suggestions" -> AnalysisTab(
                        analysis, suggestions, onZoomSugg = { zoom = "sugg" to it }, viewModel,
                        onConsiderName = { name -> addSuggestion = name to null },
                        onConsiderCard = { card -> addSuggestion = card.name to card },
                        onMarkCut = { entry -> viewModel.setReplaceable(entry.scryfallId, true) },
                        onViewDetails = onViewDetails
                    )
                    else -> Unit
                }
            }
        }
        if (layout == LayoutSize.DESKTOP) {
            // Stats beside the cards, the way the web app's deck page shows them.
            Box(Modifier.width(360.dp).fillMaxHeight()) {
                StatsTab(analysis, currentDeck, viewModel, onTag = searchTag, onOpenDeck = onOpenDeck, onOpenBadge = onOpenBadge, onCard = onViewDetails)
            }
        }
        }

        zoom?.let { (source, key) ->
            if (source == "card") {
                val groups = when {
                    analysis.byType.isNotEmpty() -> analysis.byType
                    cardGroups.isNotEmpty() -> cardGroups
                    else -> listOf(TypeGroup("Cards", currentDeck.cards))
                }
                val flatCards = groups.flatMap { it.cards }
                val zoomCards = flatCards.map { entry ->
                    ZoomCard(
                        imageUrl = entry.imageUrl,
                        cardName = entry.name,
                        priceUsd = prices[entry.scryfallId],
                        quantity = entry.quantity,
                        onIncrement = { addTo.oneMore(currentDeck, entry) { viewModel.setCardQuantity(entry.scryfallId, entry.quantity + 1) } },
                        onDecrement = { viewModel.setCardQuantity(entry.scryfallId, (entry.quantity - 1).coerceAtLeast(1)) },
                        onSelectPrinting = { chosen -> viewModel.changePrinting(entry.scryfallId, chosen) },
                        onMove = { zoom = null; moveTarget = entry },
                        onViewDetails = { zoom = null; onViewDetails(entry.name) },
                        sources = cardSources[entry.scryfallId].orEmpty().filter { it.id != currentDeck.id },
                        backImageUrl = entry.backImageUrl,
                        tags = cardTags[entry.name].orEmpty().map(RoleTags::label),
                        onFindSimilar = { zoom = null; similarSearchFor = entry.name },
                        onTagClick = searchTag,
                        userTags = userTagsByCard[entry.scryfallId].orEmpty(),
                        knownUserTags = knownUserTags,
                        onUserTags = { next -> viewModel.setUserTags(entry.scryfallId, next) }
                    )
                }
                CardZoomDialog(zoomCards, flatCards.indexOfFirst { it.scryfallId == key }.coerceAtLeast(0)) { zoom = null }
            } else if (source == "side") {
                // A pool swipes in the order it's shown: by colour.
                val side = if (currentDeck.mode.limited && !analysis.loading) poolGroups(currentDeck.sideboard, analysis.cardsById).flatMap { it.cards }
                else currentDeck.sideboard.sortedBy { it.name.lowercase() }
                val zoomCards = side.map { entry ->
                    ZoomCard(
                        imageUrl = entry.imageUrl,
                        cardName = entry.name,
                        priceUsd = prices[entry.scryfallId],
                        quantity = entry.quantity,
                        onIncrement = { addTo.oneMore(currentDeck, entry, sideboard = true) { viewModel.setSideboardQuantity(entry.scryfallId, entry.quantity + 1) } },
                        onDecrement = { viewModel.setSideboardQuantity(entry.scryfallId, (entry.quantity - 1).coerceAtLeast(1)) },
                        onViewDetails = { zoom = null; onViewDetails(entry.name) },
                        backImageUrl = entry.backImageUrl,
                        tags = cardTags[entry.name].orEmpty().map(RoleTags::label),
                        onFindSimilar = { zoom = null; similarSearchFor = entry.name },
                        onTagClick = searchTag
                    )
                }
                CardZoomDialog(zoomCards, side.indexOfFirst { it.scryfallId == key }.coerceAtLeast(0)) { zoom = null }
            } else if (source == "consider") {
                val considering = currentDeck.considering
                val zoomCards = considering.map { entry ->
                    ZoomCard(
                        imageUrl = entry.imageUrl,
                        cardName = entry.name,
                        priceUsd = prices[entry.scryfallId],
                        onViewDetails = { zoom = null; onViewDetails(entry.name) },
                        sources = cardSources[entry.scryfallId].orEmpty(),
                        backImageUrl = entry.backImageUrl,
                        tags = cardTags[entry.name].orEmpty().map(RoleTags::label),
                        onFindSimilar = { zoom = null; similarSearchFor = entry.name },
                        onTagClick = searchTag,
                        userTags = userTagsByCard[entry.scryfallId].orEmpty(),
                        knownUserTags = knownUserTags,
                        onUserTags = { next -> viewModel.setUserTags(entry.scryfallId, next) }
                    )
                }
                CardZoomDialog(zoomCards, considering.indexOfFirst { it.scryfallId == key }.coerceAtLeast(0)) { zoom = null }
            } else {
                val sug = suggestions.orEmpty()
                val zoomCards = sug.map { suggestion ->
                    ZoomCard(
                        imageUrl = suggestion.scryfallImageUrl,
                        cardName = suggestion.name,
                        onAdd = { addSuggestion = suggestion.name to null },
                        onViewDetails = { zoom = null; onViewDetails(suggestion.name) }
                    )
                }
                CardZoomDialog(zoomCards, sug.indexOfFirst { (it.id ?: it.name) == key }.coerceAtLeast(0)) { zoom = null }
            }
        }

        similarSearchFor?.let { name ->
            SimilarCardsDialog(
                cardName = name,
                onDismiss = { similarSearchFor = null },
                onAdd = { similar ->
                    similarSearchFor = null
                    addTo.perform(addToMessage(AddVerb.ADD, similar.name, currentDeck.name), check = checkFor(similar, AddToPick(currentDeck.asTarget()))) { viewModel.addCard(similar, this) }
                },
                onViewDetails = { similar -> similarSearchFor = null; onViewDetails(similar.name) }
            )
        }

        // Move or copy a card to another deck or binder: the same picker as everywhere else.
        listOfNotNull(moveTarget?.let { it to AddVerb.MOVE }, copyTarget?.let { it to AddVerb.COPY }).firstOrNull()?.let { (entry, verb) ->
            val close = { moveTarget = null; copyTarget = null }
            AddToPicker(
                verb = verb,
                subject = entry.name,
                imageUrl = entry.imageUrl,
                targets = moveTargets,
                quantity = quantityLimits(verb, entry.quantity),
                offerSideboard = true,
                onPick = { pick ->
                    close()
                    addTo.perform(addToMessage(verb, entry.name, pick), check = AddCheck(pick, listOf(entry.toAddItem(pick.quantity, pick.sideboard)))) {
                        viewModel.sendCard(entry, pick, keep = verb == AddVerb.COPY, ops = this)
                    }
                },
                onDismiss = close
            )
        }

        addSuggestion?.let { (name, card) ->
            // Into this deck or onto its Considering list — Considering first, as it's a suggestion.
            AddToPicker(
                verb = AddVerb.ADD,
                subject = name,
                targets = listOf(currentDeck.asTarget()),
                canMakeBinder = false,
                canMakeDeck = false,
                considering = true,
                quantity = null,
                offerSideboard = true,
                printing = card,
                onPick = { pick ->
                    addSuggestion = null
                    // A suggestion known only by name is looked up for the check (and then added as that card).
                    val check = if (card != null) checkFor(card, pick)
                    else AddCheck(pick) { _ -> listOfNotNull(viewModel.findByName(name)?.toAddItem(pick.quantity, pick.sideboard)) }
                    addTo.perform(addToMessage(AddVerb.ADD, name, pick), check = check) {
                        if (card != null) addCard(card, pick) else viewModel.addByName(name, pick, this)
                    }
                },
                onDismiss = { addSuggestion = null }
            )
        }

        removeCardTarget?.let { entry ->
            ConfirmDeleteDialog(
                title = "Remove from deck?",
                message = "Remove ${entry.name} (${entry.quantity} cop${if (entry.quantity == 1) "y" else "ies"}) from this deck?",
                confirmLabel = "Remove from deck",
                onConfirm = { viewModel.removeCard(entry.scryfallId); removeCardTarget = null },
                onDismiss = { removeCardTarget = null }
            )
        }

        removeSideboardTarget?.let { entry ->
            val side = sideboardName(currentDeck.mode).lowercase()
            ConfirmDeleteDialog(
                title = "Remove from $side?",
                message = "Take ${entry.name} (${entry.quantity} cop${if (entry.quantity == 1) "y" else "ies"}) out of this deck's $side?",
                confirmLabel = "Remove from $side",
                onConfirm = { viewModel.setSideboardQuantity(entry.scryfallId, 0); removeSideboardTarget = null },
                onDismiss = { removeSideboardTarget = null }
            )
        }

        if (addingBasics) {
            BasicLandsDialog(
                mainDeck = currentDeck.cards,
                cards = analysis.cardsById,
                onAdd = { counts ->
                    addingBasics = false
                    val total = counts.values.sum()
                    // New copies from the land station, into the main deck — not ones from the Unsorted pile.
                    addTo.perform("Added $total basic ${if (total == 1) "land" else "lands"} to ${currentDeck.name}") {
                        viewModel.addBasicLands(counts, this)
                    }
                },
                onDismiss = { addingBasics = false }
            )
        }
        if (poolToBinder) {
            val copies = poolCopies(currentDeck).sumOf { it.quantity }
            AddToPicker(
                verb = AddVerb.COPY,
                subject = cardsSubject(copies, null),
                targets = moveTargets.filter { it.kind == SourceKind.BINDER },
                canMakeDeck = false,
                considering = null,
                quantity = null,
                onPick = { pick ->
                    poolToBinder = false
                    addTo.perform(addToMessage(AddVerb.COPY, cardsSubject(copies, null), pick, quantity = 1)) {
                        viewModel.copyPoolToBinder(pick, this)
                    }
                },
                onDismiss = { poolToBinder = false }
            )
        }

        if (showSettings) {
            DeckSettingsDialog(
                current = currentDeck.mode,
                onSelect = { viewModel.setGameMode(it) },
                ownership = currentDeck.ownershipType,
                onOwnershipChange = { o ->
                    if (currentDeck.holdsOwnCopies && o != DeckOwnership.PHYSICAL && realCopiesOf(currentDeck).isNotEmpty()) leavingPhysical = o
                    else viewModel.setOwnership(o)
                },
                tags = currentDeck.tags,
                onTagsChange = { viewModel.setTags(it) },
                onDismiss = { showSettings = false }
            )
        }
        if (showImport) {
            ImportDialog(
                initial = importText,
                onDismiss = { showImport = false; importText = "" },
                onImport = { text ->
                    showImport = false
                    importText = text
                    importState = ImportState()
                    viewModel.importDecklist(
                        text = text,
                        onProgress = { done, total ->
                            importState = ImportState(done = done, total = total)
                        },
                        check = { items -> addTo.gate.run(AddCheck(AddToPick(currentDeck.asTarget()), items)) },
                        onCancelled = {
                            importState = null
                            showImport = true
                        },
                        onResult = { added, considering, sideboard, failed ->
                            importText = ""
                            importState = ImportState(summary = importSummary(added, considering, sideboard, failed))
                        }
                    )
                }
            )
        }
        importState?.let { state ->
            ImportResultDialog(state = state, onDismiss = { importState = null })
        }
        leavingPhysical?.let { target ->
            val real = realCopiesOf(currentDeck).sumOf { it.quantity }
            AlertDialog(
                containerColor = Surface,
                onDismissRequest = { leavingPhysical = null },
                title = { Text("Make it ${target.label.lowercase()}?", color = GoldLight) },
                text = {
                    Text(
                        "\"${currentDeck.name}\" holds $real of your cards, and a ${target.label.lowercase()} deck doesn't count " +
                            "its cards as yours. Keep them and they go to Unsorted; or remove them from your collection.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                },
                confirmButton = {
                    Column(horizontalAlignment = Alignment.End) {
                        Button(
                            onClick = { viewModel.setOwnership(target, keepCards = true); leavingPhysical = null },
                            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
                        ) { Text("Keep cards", color = Bg) }
                        TextButton(onClick = { viewModel.setOwnership(target, keepCards = false); leavingPhysical = null }) {
                            Text("Remove the cards", color = LocalAppColors.current.error)
                        }
                        TextButton(onClick = { leavingPhysical = null }) { Text("Cancel", color = TextMuted) }
                    }
                }
            )
        }
        if (confirmDelete) {
            DeleteDeckDialog(
                deckName = currentDeck.name,
                cardCount = currentDeck.cards.sumOf { it.quantity },
                realCopies = realCopiesOf(currentDeck).sumOf { it.quantity },
                onDelete = { keepCards -> confirmDelete = false; viewModel.deleteDeck(keepCards, onBack) },
                onDismiss = { confirmDelete = false }
            )
        }
        categoriesFor?.let { entry ->
            CardCategoriesDialog(
                deck = currentDeck,
                entry = currentDeck.cards.firstOrNull { it.scryfallId == entry.scryfallId } ?: entry,
                onSave = { chosen -> viewModel.changeDeck { it.withCardCategories(entry.scryfallId, chosen) }; categoriesFor = null },
                onDismiss = { categoriesFor = null }
            )
        }
        categoryOpen?.let { name ->
            CategoryDialog(
                deck = currentDeck,
                name = name,
                onSave = { target, rename ->
                    viewModel.changeDeck { d ->
                        val next = d.withCategoryTarget(name, target)
                        if (tidyCategory(rename).isNotEmpty() && tidyCategory(rename) != name) next.renamedCategory(name, rename) else next
                    }
                    categoryOpen = null
                },
                onRemove = { viewModel.changeDeck { it.removedCategory(name) }; categoryOpen = null },
                onDismiss = { categoryOpen = null }
            )
        }
        if (companionPicking) {
            CompanionDialog(
                current = companionNamed(currentDeck.companion),
                sided = currentDeck.mode.hasSideboard,
                error = companionError,
                onPick = { name ->
                    scope.launch {
                        if (viewModel.chooseCompanion(name)) companionPicking = false
                        else companionError = "Couldn't reach Scryfall — try again when you're online."
                    }
                },
                onDismiss = { companionPicking = false }
            )
        }
        if (filing) {
            FolderDialog(
                deckName = currentDeck.name,
                folders = folderNames(allDecks + currentDeck),
                current = folderOf(currentDeck),
                onMove = { folder -> viewModel.changeDeck { it.withFolder(folder) }; filing = false },
                onDismiss = { filing = false }
            )
        }
        if (showExport) {
            ExportDialog(deck = currentDeck, viewModel = viewModel, onDismiss = { showExport = false })
        }
        if (showGoldfish) {
            val tokens by viewModel.tokens.collectAsState()
            GoldfishDialog(deck = currentDeck, tokens = tokens, onDismiss = { showGoldfish = false })
        }
        if (comparePicking) {
            val others by viewModel.otherDecks.collectAsState()
            val history by viewModel.versionHistory.collectAsState()
            ComparePickerDialog(
                decks = others,
                versions = history,
                onPick = { comparePicking = false; compareWith = it },
                onDismiss = { comparePicking = false }
            )
        }
        compareWith?.let { target ->
            CompareScreen(deck = currentDeck, target = target, onDismiss = { compareWith = null })
        }

        swapOut?.let { outgoing ->
            if (currentDeck.considering.isEmpty()) {
                SwapPickerDialog(
                    title = "Nothing to swap in yet",
                    message = "Add cards to this deck's Considering list first — from a card's page, the REC tab, or budget swaps.",
                    options = emptyList(),
                    onPick = {},
                    onDismiss = { swapOut = null }
                )
            } else {
                SwapPickerDialog(
                    title = "Replace ${outgoing.name} with…",
                    message = "${outgoing.name} moves to Considering, so you can swap it back later.",
                    options = currentDeck.considering,
                    onPick = { incoming ->
                        swapChecked(outgoing, incoming)
                        swapOut = null
                    },
                    onDismiss = { swapOut = null }
                )
            }
        }

        swapIn?.let { incoming ->
            val commanderIds = setOfNotNull(currentDeck.commander?.scryfallId, currentDeck.partnerCommander?.scryfallId)
            SwapPickerDialog(
                title = "Swap ${incoming.name} in for…",
                message = "The card you pick moves to Considering. Cut candidates are listed first.",
                options = currentDeck.cards.filterNot { it.scryfallId in commanderIds },
                onPick = { outgoing ->
                    swapChecked(outgoing, incoming)
                    swapIn = null
                },
                onDismiss = { swapIn = null }
            )
        }

        comboWarningFor?.let { card ->
            ComboPieceWarningDialog(
                cardName = card.name,
                combos = cardNameKeys(card.name).flatMap { analysis.comboPieces[it].orEmpty() }.distinctBy { it.id },
                onConfirm = { viewModel.setReplaceable(card.scryfallId, true); comboWarningFor = null },
                onDismiss = { comboWarningFor = null }
            )
        }

        if (showMissing) {
            MissingCardsDialog(
                missing = missing,
                physicalDeck = deck?.ownershipType == DeckOwnership.PHYSICAL,
                onAddToWishlist = {
                    viewModel.addMissingToWishlist { message -> toast(message) }
                    showMissing = false
                },
                onBuy = {
                    viewModel.buildMissingCardsUrl { url ->
                        if (url != null) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                    showMissing = false
                },
                onDismiss = { showMissing = false },
                onWhoHasIt = onWhoHasIt?.let { open -> { showMissing = false; open(missing.map { it.entry.name }) } }
            )
        }
    }
}

private fun importSummary(added: Int, considering: Int, sideboard: Int, failed: List<String>): String = buildString {
    append("Imported $added card${if (added == 1) "" else "s"}.")
    if (sideboard > 0) append("\n\n$sideboard card${if (sideboard == 1) "" else "s"} went to the sideboard.")
    if (considering > 0) append("\n\n$considering sideboard/maybeboard card${if (considering == 1) "" else "s"} went to Considering.")
    if (failed.isNotEmpty()) {
        append("\n\n${failed.size} line${if (failed.size == 1) "" else "s"} couldn't be matched:\n")
        append(failed.take(25).joinToString("\n") { "• $it" })
        if (failed.size > 25) append("\n…and ${failed.size - 25} more")
    }
}

/**
 * Deleting a deck can't be undone, so make it deliberate. A physical deck holds real cards
 * ([realCopies] of them, proxies aside): they can go back to the Unsorted pile rather than out of the
 * collection with the deck, and keeping them is the first choice. A deck with no real cards has
 * nothing to keep.
 */
@Composable
private fun DeleteDeckDialog(
    deckName: String,
    cardCount: Int,
    realCopies: Int,
    onDelete: (keepCards: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    if (realCopies == 0) {
        ConfirmDeleteDialog(
            title = "Delete deck?",
            message = "\"$deckName\" and its $cardCount card${if (cardCount == 1) "" else "s"} will be " +
                "permanently deleted. This can't be undone.",
            confirmLabel = "Delete deck",
            onConfirm = { onDelete(false) },
            onDismiss = onDismiss
        )
        return
    }
    AlertDialog(
        containerColor = Surface,
        onDismissRequest = onDismiss,
        title = { Text("Delete deck?", color = GoldLight) },
        text = {
            Text(
                "\"$deckName\" holds $realCopies of your cards. Keep them and they go to Unsorted; " +
                    "or delete them with the deck, out of your collection. Deleting the deck can't be undone.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                Button(
                    onClick = { onDelete(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
                ) { Text("Delete deck, keep cards", color = Bg) }
                TextButton(onClick = { onDelete(false) }) {
                    Text("Delete deck and cards", color = LocalAppColors.current.error)
                }
                TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
            }
        }
    )
}

/** Import progress, or the final [summary] once it finishes. */
private data class ImportState(
    val done: Int = 0,
    val total: Int = 0,
    val summary: String? = null
)

@Composable
private fun ImportResultDialog(state: ImportState, onDismiss: () -> Unit) {
    val summary = state.summary
    AlertDialog(
        containerColor = Surface,
        onDismissRequest = { if (summary != null) onDismiss() },
        title = {
            Text(if (summary == null) "Importing list…" else "Import complete", color = GoldLight)
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (summary == null) {
                    if (state.total > 0) {
                        LinearProgressIndicator(
                            progress = { state.done.toFloat() / state.total },
                            color = Gold,
                            trackColor = BorderColor,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "${state.done} of ${state.total} cards",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    } else {
                        LinearProgressIndicator(
                            color = Gold,
                            trackColor = BorderColor,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("Reading list…", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                } else {
                    Text(summary, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                }
            }
        },
        confirmButton = {
            if (summary != null) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
                ) { Text("OK", color = Bg) }
            }
        }
    )
}

@Composable
private fun ImportDialog(onDismiss: () -> Unit, onImport: (String) -> Unit, initial: String = "") {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        containerColor = Surface,
        onDismissRequest = onDismiss,
        title = { Text("Import list", color = GoldLight) },
        text = {
            Column {
                Text(
                    "Paste a decklist — one card per line, e.g. \"1 Sol Ring\". Cards are matched on Scryfall and added to this deck.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    placeholder = { Text("1 Sol Ring\n1 Arcane Signet\n…", color = TextDim) },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Gold,
                        unfocusedBorderColor = BorderColor,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = Gold
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(text) },
                enabled = text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
            ) { Text("Import") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        }
    )
}

/**
 * Export list: the deck as text in one of four shapes (DeckExport.kt) — Simple ("1 Sol Ring", what
 * nearly everything reads), Exact printing (with "(SET) number", so the art survives), Arena and
 * MTGO. The printings are looked up the first time a format needs them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExportDialog(deck: Deck, viewModel: DeckDetailViewModel, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var format by remember { mutableStateOf(DeckExportFormat.SIMPLE) }
    var exactCards by remember { mutableStateOf<Map<String, ScryfallCard>?>(null) }
    var loadingExact by remember { mutableStateOf(false) }

    LaunchedEffect(format) {
        if (format.needsPrintings && exactCards == null) {
            loadingExact = true
            exactCards = runCatching { viewModel.resolveCardsForExport() }.getOrDefault(emptyMap())
            loadingExact = false
        }
    }

    val printings = exactCards.orEmpty().mapNotNull { (id, card) ->
        val set = card.set
        val number = card.collectorNumber
        if (set != null && number != null) id to (set to number) else null
    }.toMap()
    val loading = format.needsPrintings && loadingExact
    val decklist = deckExportText(deck, format, if (format.needsPrintings) printings else emptyMap())

    AlertDialog(
        containerColor = Surface,
        onDismissRequest = onDismiss,
        title = { Text("Export list", color = GoldLight) },
        text = {
            Column {
                Text(
                    when (format) {
                        DeckExportFormat.SIMPLE -> "Copy this decklist to share or back up your deck."
                        DeckExportFormat.EXACT -> "Each card with its set and collector number, so the same art comes back."
                        DeckExportFormat.ARENA -> "For MTG Arena's Import: Commander, Deck and Sideboard sections."
                        DeckExportFormat.MTGO -> "For Magic Online: no set codes, the sideboard after a blank line."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeckExportFormat.entries.forEach { f ->
                        ExportFormatChip(f.label, selected = format == f) { format = f }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Bg)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Gold)
                    } else {
                        Text(
                            decklist.ifBlank { "This deck has no cards yet." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { clipboard.setText(AnnotatedString(decklist)) },
                enabled = decklist.isNotBlank() && !loading,
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
            ) { Text("Copy") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = TextMuted) }
        }
    )
}

@Composable
private fun ExportFormatChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) Bg else TextPrimary,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Gold else Bg)
            .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DeckSettingsDialog(
    current: GameMode,
    onSelect: (GameMode) -> Unit,
    ownership: DeckOwnership,
    onOwnershipChange: (DeckOwnership) -> Unit,
    tags: List<String>,
    onTagsChange: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var tagInput by remember { mutableStateOf("") }
    AlertDialog(
        containerColor = Surface,
        onDismissRequest = onDismiss,
        title = { Text("Deck settings", color = GoldLight) },
        text = {
            Column {
                Text(
                    "The game mode sets the legality rules checked in Stats.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                Spacer(Modifier.height(14.dp))
                GameModeDropdown(selected = current, onSelect = onSelect)
                Spacer(Modifier.height(20.dp))
                Text("Ownership", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                Spacer(Modifier.height(8.dp))
                // Four of these don't fit a phone's width: they wrap onto a second line rather
                // than squeezing "Prototype" into a column of letters.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DeckOwnership.entries.forEach { option ->
                        val selected = option == ownership
                        Text(
                            option.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) Bg else TextPrimary,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) Gold else Bg)
                                .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(50))
                                .clickable { onOwnershipChange(option) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
                Text(
                    ownership.description,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextDim,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Spacer(Modifier.height(20.dp))
                Text("Tags", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                Spacer(Modifier.height(8.dp))
                if (tags.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    ) {
                        tags.forEach { tag ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Bg)
                                    .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(50))
                                    .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                            ) {
                                Text(tag, style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                                IconButton(
                                    onClick = { onTagsChange(tags - tag) },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove tag \"$tag\"", tint = TextMuted, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    placeholder = { Text("e.g. Budget, Combo, Aggro", color = TextDim) },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            val trimmed = tagInput.trim()
                            if (trimmed.isNotEmpty() && trimmed !in tags) onTagsChange(tags + trimmed)
                            tagInput = ""
                        }) {
                            Icon(Icons.Filled.Add, contentDescription = "Add tag", tint = Gold)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Gold,
                        unfocusedBorderColor = BorderColor,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = Gold
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
            ) { Text("Done") }
        }
    )
}

/**
 * The deck's legality at a glance — "Legal" with a tick, or how many problems there are — which
 * opens the full list, with its one-tap fixes, underneath. Heads the Stats tab.
 */
@Composable
private fun LegalitySection(report: LegalityReport, viewModel: DeckDetailViewModel) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column {
        LegalityBadge(report, expanded = open, onClick = { open = !open })
        AnimatedVisibility(visible = open) {
            Column(Modifier.padding(top = 12.dp)) { LegalityDetails(report, viewModel) }
        }
    }
}

@Composable
private fun LegalityBadge(report: LegalityReport, expanded: Boolean, onClick: () -> Unit) {
    val app = LocalAppColors.current
    val color = if (report.legal) app.success else app.error
    val problems = report.issues.size
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(if (report.legal) Icons.Filled.CheckCircle else Icons.Filled.Cancel, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(
            when {
                report.legal -> "Legal"
                problems > 0 -> "$problems problem${if (problems == 1) "" else "s"}"
                else -> "Not legal"
            },
            style = MaterialTheme.typography.labelLarge,
            color = color
        )
        Icon(
            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (expanded) "Hide legality" else "Show legality",
            tint = color,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** What the badge opens: the format, the count against its size rule, then each problem. */
@Composable
private fun LegalityDetails(report: LegalityReport, viewModel: DeckDetailViewModel) {
    val context = LocalContext.current
    val app = LocalAppColors.current
    Text(
        if (report.legal) "Legal for ${report.mode.label}" else "Not legal for ${report.mode.label}",
        style = MaterialTheme.typography.titleSmall,
        color = if (report.legal) app.success else app.error
    )
    Text(
        "${report.totalCards} cards" +
            if (report.mode.exactSize) " · needs ${report.mode.deckSize}" else " · min ${report.mode.deckSize}",
        style = MaterialTheme.typography.labelMedium,
        color = TextMuted
    )
    if (report.legal) {
        Text(
            "This deck follows ${report.mode.label}'s building rules.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.padding(top = 8.dp)
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
        report.issues.forEach { issue ->
            LegalityIssueRow(
                issue,
                onFix = if (issue.kind == LegalityIssueKind.COPY_LIMIT && issue.scryfallId != null && issue.fixQuantity != null) {
                    {
                        viewModel.setCardQuantity(issue.scryfallId, issue.fixQuantity)
                        val word = if (issue.fixQuantity == 1) "copy" else "copies"
                        Toast.makeText(context, "Reduced ${issue.card} to ${issue.fixQuantity} $word.", Toast.LENGTH_SHORT).show()
                    }
                } else null
            )
        }
    }
}

@Composable
private fun LegalityIssueRow(issue: LegalityIssue, onFix: (() -> Unit)?) {
    val haptic = LocalHapticFeedback.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .elevatedCard(shape = RoundedCornerShape(16.dp))
            .let {
                if (onFix != null) {
                    it.clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onFix() }
                } else it
            }
            .padding(12.dp)
    ) {
        Icon(
            Icons.Filled.Cancel,
            contentDescription = null,
            tint = LocalAppColors.current.error,
            modifier = Modifier.size(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            issue.card?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            }
            Text(issue.reason, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            if (onFix != null) {
                val word = if (issue.fixQuantity == 1) "copy" else "copies"
                Text(
                    "Tap to reduce to ${issue.fixQuantity} $word",
                    style = MaterialTheme.typography.labelMedium,
                    color = Gold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun CardsTab(
    deck: Deck,
    analysis: DeckAnalysis,
    onZoomCard: (String) -> Unit,
    cardActions: (DeckCardEntry) -> List<CardMenuAction>,
    viewModel: DeckDetailViewModel,
    onZoomSideboard: (String) -> Unit = {},
    sideboardActions: (DeckCardEntry) -> List<CardMenuAction> = { emptyList() },
    /** The − on a sideboard card's last copy: asked about first, as in the main deck. */
    onRemoveLastSideboardCopy: (DeckCardEntry) -> Unit = {},
    /** A category's heading tapped: its target, name and removal. */
    onCategory: (String) -> Unit = {}
) {
    val query by viewModel.cardQuery.collectAsState()
    val grouping by viewModel.grouping.collectAsState()
    val trimmed = query.trim()
    val addTo = LocalAddToFeedback.current
    val cardTags by viewModel.cardTags.collectAsState()
    val tagging by viewModel.tagging.collectAsState()
    fun tagsOf(card: DeckCardEntry) = cardTags[card.name].orEmpty()
    var filter by remember { mutableStateOf(CardFilter.ALL) }
    val viewMode by viewModel.viewMode.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val cardGroups by viewModel.cardGroups.collectAsState()
    fun isComboPiece(card: DeckCardEntry) = cardNameKeys(card.name).any { it in analysis.comboPieces }
    fun isNearMiss(card: DeckCardEntry) = cardNameKeys(card.name).any { it in analysis.nearMissPieces }
    val cutCount = deck.cards.count { it.replaceable }
    val comboCount = deck.cards.count { isComboPiece(it) }
    // Copies lent out from the deck (Loans.kt): still listed, marked lent out.
    val lentOut by viewModel.lentOut.collectAsState()
    fun lentOf(card: DeckCardEntry) = lentOut[card.name.trim().lowercase()] ?: 0

    // Grouped by type instantly from cached data, refined once analysis resolves from Scryfall;
    // only falls back to one flat list for entries with no type info at all yet.
    //
    // The analysis is a snapshot that lags the deck by its network round trips (combos, Scryfall),
    // so it only decides grouping, and only while it covers exactly the deck's cards. The entries
    // drawn always come from the live deck, so a cut flag or quantity change shows immediately.
    val liveById = deck.cards.associateBy { it.scryfallId }
    val analysisCurrent = analysis.byType.isNotEmpty() &&
        analysis.byType.flatMap { g -> g.cards.map { it.scryfallId } }.toSet() == liveById.keys
    val typeGroups = (
        when {
            analysisCurrent -> analysis.byType
            cardGroups.isNotEmpty() -> cardGroups
            else -> listOf(TypeGroup("Cards", deck.cards))
        }
        )
        .map { group -> group.copy(cards = group.cards.mapNotNull { liveById[it.scryfallId] }) }
        .mapNotNull { group ->
            val cards = group.cards.filter { card ->
                RoleTags.matches(card.name, tagsOf(card), trimmed) &&
                    when (filter) {
                        CardFilter.ALL -> true
                        CardFilter.CUT -> card.replaceable
                        CardFilter.COMBO -> isComboPiece(card)
                    }
            }
            if (cards.isEmpty()) null else group.copy(cards = cards)
        }

    // The commander (and partner commander, if set) render with the same full-card
    // DeckCardRow/DeckCardTile as everything else, but pulled out of their type groups into one
    // pinned section at the top of the list.
    val commanderIds = setOfNotNull(deck.commander?.scryfallId, deck.partnerCommander?.scryfallId)
    val commanderEntries = mutableListOf<DeckCardEntry>()
    val otherGroups = typeGroups.mapNotNull { group ->
        val rest = group.cards.filter { card ->
            val isCommander = card.scryfallId in commanderIds
            if (isCommander) commanderEntries += card
            !isCommander
        }
        if (rest.isEmpty()) null else group.copy(cards = rest)
    }
    val groups = if (commanderEntries.isNotEmpty()) {
        // Keep the main commander first, partner second, regardless of the order they were found in.
        val ordered = listOfNotNull(
            commanderEntries.find { it.scryfallId == deck.commander?.scryfallId },
            commanderEntries.find { it.scryfallId == deck.partnerCommander?.scryfallId }
        )
        listOf(TypeGroup("Commander", ordered)) + otherGroups
    } else otherGroups

    // Grouped another way than by type (DeckCategories.kt): the commanders stay pinned on top.
    val listedCards = groups.flatMap { it.cards }.filter { it.scryfallId !in commanderIds }
    fun factsOf(e: DeckCardEntry): GroupingFacts? {
        val roles = tagsOf(e).map(RoleTags::label)
        val card = analysis.cardsById[e.scryfallId]
        if (card == null) return if (grouping == DeckGrouping.ROLE) GroupingFacts(null, null, false, roles) else null
        return GroupingFacts(card.cmc, card.colors ?: card.cardFaces?.firstOrNull()?.colors, isLandType(card.typeLine ?: e.typeLine), roles)
    }
    val searching = trimmed.isNotEmpty() || filter != CardFilter.ALL
    val categoryTotals = if (grouping == DeckGrouping.CATEGORY && !searching) categoryCounts(deck) else emptyMap()
    val shownGroups: List<CardGroup> = if (grouping == DeckGrouping.TYPE) {
        groups.map { g -> CardGroup("type:" + g.type, g.type, g.cards, g.cards.sumOf { it.quantity }) }
    } else {
        groups.filter { it.type == "Commander" }.map { g -> CardGroup("type:Commander", g.type, g.cards, g.cards.sumOf { it.quantity }) } +
            groupCards(listedCards, grouping, ::factsOf, if (searching) emptyMap() else deck.categoryTargets.orEmpty())
    }
    val toastContext = LocalContext.current
    val suggestCategories: (() -> Unit)? = if (grouping == DeckGrouping.CATEGORY && deck.cards.any { it.categories.isNullOrEmpty() }) {
        {
            val filled = deck.withSuggestedCategories { name -> cardTags[name].orEmpty() }.second
            if (filled == 0) {
                Toast.makeText(toastContext, if (tagging != null) "Still finding what the cards do — try again in a moment." else "No role tags to suggest from.", Toast.LENGTH_SHORT).show()
            } else {
                viewModel.changeDeck { it.withSuggestedCategories { name -> cardTags[name].orEmpty() }.first }
                Toast.makeText(toastContext, "Filled in categories for $filled ${if (filled == 1) "card" else "cards"}.", Toast.LENGTH_SHORT).show()
            }
        }
    } else null

    // Taking the last copy out removes the card, which is easy to do by accident on a small − button:
    // it's asked about first. The card being asked about, while the question is up.
    var removing by remember { mutableStateOf<DeckCardEntry?>(null) }
    val fewer: (DeckCardEntry) -> Unit = { card ->
        if (card.quantity <= 1) removing = card else viewModel.setCardQuantity(card.scryfallId, card.quantity - 1)
    }
    removing?.let { card ->
        ConfirmDeleteDialog(
            title = "Remove ${card.name}?",
            message = "That was the last copy in this deck. Removing it takes the card out of the deck.",
            confirmLabel = "Remove from deck",
            onConfirm = { viewModel.setCardQuantity(card.scryfallId, 0); removing = null },
            onDismiss = { removing = null }
        )
    }

    // Cards the deck doesn't have that match the search, offered under the deck's own matches.
    val addResults by viewModel.addResults.collectAsState()
    val context = LocalContext.current
    val owned = deck.cards.map { it.name }.toSet()
    val addable = if (trimmed.length < 3) emptyList() else addResults.filter { it.name !in owned }
    // A Limited deck's search fills its pool: the main deck is built from there.
    val limited = deck.mode.limited
    val addSection: LazyListScope.() -> Unit = {
        if (addable.isNotEmpty()) {
            item(key = "add-header") {
                Text(if (limited) "Add to the pool" else "Add to this deck", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 2.dp, start = 2.dp))
            }
            items(addable, key = { "add-" + it.id }) { card ->
                AddToDeckRow(card) {
                    if (limited) {
                        val pick = AddToPick(deck.asTarget(), sideboard = true)
                        addTo.perform(addToMessage(AddVerb.ADD, card.name, pick), check = checkFor(card, pick)) { addCard(card, pick) }
                    } else {
                        addTo.perform(addToMessage(AddVerb.ADD, card.name, deck.name), check = checkFor(card, AddToPick(deck.asTarget()))) { viewModel.addCard(card, this) }
                    }
                }
            }
        }
    }

    // The sideboard, after the main deck's groups: the same rows, with their own − and +. Shown for a
    // format with a sideboard, and for any deck that still has cards there (one switched to Commander).
    val showSideboard = deck.mode.hasSideboard || deck.sideboard.isNotEmpty()
    val sideboardShown = deck.sideboard
        .filter { card -> filter == CardFilter.ALL && RoleTags.matches(card.name, tagsOf(card), trimmed) }
        .sortedBy { it.name.lowercase() }
    // A Limited deck's sideboard is its pool: shown by colour once its cards are known (Limited.kt).
    val poolByColour = if (limited && !analysis.loading) poolGroups(sideboardShown, analysis.cardsById) else null
    val sideboardSection: LazyListScope.(columns: Int, grid: Boolean) -> Unit = { columns, grid ->
        if (showSideboard && (sideboardShown.isNotEmpty() || (trimmed.isEmpty() && filter == CardFilter.ALL))) {
            item(key = "sideboard-header") {
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp, start = 2.dp, end = 4.dp)) {
                    // In Commander the companion waits here, outside the 100.
                    val sideTitle = if (!deck.mode.hasSideboard && companionEntry(deck) != null && deck.sideboard.size == 1) "Companion" else sideboardName(deck.mode)
                    Text("$sideTitle (${deck.sideboardCount})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    val limit = deck.mode.sideboardLimit
                    if (deck.mode.hasSideboard && limit != null) Text("up to $limit", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                }
            }
            if (sideboardShown.isEmpty()) {
                item(key = "sideboard-empty") {
                    Text(
                        if (limited) "No cards in the pool yet. Type a card's name above to add it, or scan the pool in."
                        else "No sideboard cards yet. Long-press a card and choose Move to sideboard, or pick Sideboard when adding one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
            val sideFewer: (DeckCardEntry) -> Unit = { card ->
                if (card.quantity <= 1) onRemoveLastSideboardCopy(card) else viewModel.setSideboardQuantity(card.scryfallId, card.quantity - 1)
            }
            val sideCard: @Composable (DeckCardEntry) -> Unit = { card ->
                if (grid) {
                    DeckCardTile(
                        card = card,
                        onClick = { onZoomSideboard(card.scryfallId) },
                        actions = sideboardActions(card),
                        onIncrement = { addTo.oneMore(deck, card, sideboard = true) { viewModel.setSideboardQuantity(card.scryfallId, card.quantity + 1) } },
                        onDecrement = { sideFewer(card) }
                    )
                } else {
                    DeckCardRow(
                        card = card,
                        isCommander = false,
                        onClick = { onZoomSideboard(card.scryfallId) },
                        actions = sideboardActions(card),
                        canLead = false,
                        onToggleCommander = {},
                        onIncrement = { addTo.oneMore(deck, card, sideboard = true) { viewModel.setSideboardQuantity(card.scryfallId, card.quantity + 1) } },
                        onDecrement = { sideFewer(card) }
                    )
                }
            }
            if (poolByColour != null && sideboardShown.isNotEmpty()) {
                // The pool by colour, with the pairs it supports best.
                item(key = "pool-pairs") {
                    PoolPairsHint(deck.sideboard, analysis.cardsById, Modifier.padding(start = 2.dp, bottom = 2.dp))
                }
                poolByColour.forEach { group ->
                    item(key = "pool-" + group.key) {
                        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 2.dp, end = 4.dp)) {
                            Text(group.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text("${group.count}", style = NumberStyle(18), color = TextMuted)
                        }
                    }
                    cardGrid(group.cards, columns = columns, key = { "sb-" + it.scryfallId }, itemContent = sideCard)
                }
            } else {
                cardGrid(sideboardShown, columns = columns, key = { "sb-" + it.scryfallId }, itemContent = sideCard)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        run {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setCardQuery,
                // One line only: a placeholder that wraps makes the whole field twice as tall.
                placeholder = { Text("Find or add a card", color = TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextMuted) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setCardQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = TextMuted)
                        }
                    }
                },
                shape = RoundedCornerShape(8.dp),
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
            // List or grid, here where the cards are rather than only in Settings.
            val grid = viewMode == CardViewMode.GRID
            IconButton(onClick = { viewModel.setViewMode(if (grid) CardViewMode.LIST else CardViewMode.GRID) }) {
                Icon(
                    if (grid) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                    contentDescription = if (grid) "Show as a list" else "Show as a grid",
                    tint = Gold
                )
            }
            }
            // A search that found cards by their tag says which, since tags only show in the zoom.
            if (trimmed.isNotEmpty()) {
                val shownCount = groups.sumOf { it.cards.size }
                val tagHits = groups.flatMap { g -> g.cards.filterNot { it.name.contains(trimmed, ignoreCase = true) } }
                    .flatMap { RoleTags.matched(tagsOf(it), trimmed) }.distinct()
                Text(
                    buildString {
                        append("$shownCount ${if (shownCount == 1) "card" else "cards"}")
                        if (tagHits.isNotEmpty()) append(" · tag: " + tagHits.take(2).joinToString(", ") { RoleTags.label(it) } + if (tagHits.size > 2) "…" else "")
                        if (tagging != null) append(" · finding tags…")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                    modifier = Modifier.padding(start = 22.dp, end = 20.dp, top = 6.dp)
                )
            }
            if (cutCount > 0 || comboCount > 0 || filter != CardFilter.ALL) {
                CardFilterChips(
                    selected = filter,
                    cutCount = cutCount,
                    comboCount = comboCount,
                    onSelect = { filter = it },
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp)
                )
            }
            if (deck.cards.isNotEmpty()) {
                GroupByRow(grouping, viewModel::setGrouping, suggestCategories, Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp))
            }
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
        val listCols = listColumnsFor(maxWidth - 40.dp)
        val gridCols = gridColumnsFor(maxWidth - 40.dp, gridColumns)
        val gridMode = viewMode == CardViewMode.GRID
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (deck.cards.isEmpty()) {
                item {
                    Text(
                        "No cards yet. Type a card's name above to add it.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                sideboardSection(if (gridMode) gridCols else listCols, gridMode)
                addSection()
                return@LazyColumn
            }
            if (groups.isEmpty()) {
                item {
                    Text(
                        when {
                            trimmed.isNotBlank() -> "No cards in this deck match \"$trimmed\"."
                            filter == CardFilter.CUT -> "No cut candidates. Long-press a card and choose Mark as cut candidate."
                            filter == CardFilter.COMBO -> "No combo pieces detected in this deck."
                            else -> "No cards."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
                sideboardSection(if (gridMode) gridCols else listCols, gridMode)
                addSection()
                return@LazyColumn
            }

            shownGroups.forEach { group ->
                // A category's heading opens its target and name; its count is the whole deck's against the target.
                val category = if (grouping == DeckGrouping.CATEGORY && group.key.startsWith("cat:") && group.key != "cat:") group.label else null
                item(key = "h-" + group.key) {
                    val count = category?.let { categoryTotals[it] } ?: group.count
                    val app = LocalAppColors.current
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (category != null) Modifier.clickable { onCategory(category) } else Modifier)
                            .padding(top = 12.dp, bottom = 2.dp, start = 2.dp, end = 4.dp)
                    ) {
                        Text(group.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        val target = group.target
                        Text(
                            if (target != null) "$count/$target" else "$count",
                            style = NumberStyle(20),
                            color = when {
                                target == null -> TextMuted
                                count < target -> app.warning
                                count > target -> app.cut
                                else -> app.success
                            }
                        )
                    }
                }
                if (group.cards.isEmpty()) {
                    item(key = "e-" + group.key) {
                        Text("None yet — long-press a card and choose Categories.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
                if (viewMode == CardViewMode.GRID) {
                    cardGrid(group.cards, columns = gridCols, key = { group.key + "|" + it.scryfallId }) { card ->
                        DeckCardTile(
                            card = card,
                            isCommander = card.scryfallId == deck.commander?.scryfallId || card.scryfallId == deck.partnerCommander?.scryfallId,
                            onClick = { onZoomCard(card.scryfallId) },
                            actions = cardActions(card),
                            comboPiece = isComboPiece(card),
                            nearMiss = isNearMiss(card),
                            onIncrement = { addTo.oneMore(deck, card) { viewModel.setCardQuantity(card.scryfallId, card.quantity + 1) } },
                            onDecrement = { fewer(card) },
                            lent = lentOf(card)
                        )
                    }
                } else {
                    cardGrid(group.cards, columns = listCols, key = { group.key + "|" + it.scryfallId }) { card ->
                        DeckCardRow(
                            card = card,
                            isCommander = card.scryfallId == deck.commander?.scryfallId || card.scryfallId == deck.partnerCommander?.scryfallId,
                            onClick = { onZoomCard(card.scryfallId) },
                            actions = cardActions(card),
                            canLead = card.canLead(deck.mode),
                            onToggleCommander = {
                                viewModel.setCommander(if (deck.commander?.scryfallId == card.scryfallId) null else card)
                            },
                            onIncrement = { addTo.oneMore(deck, card) { viewModel.setCardQuantity(card.scryfallId, card.quantity + 1) } },
                            onDecrement = { fewer(card) },
                            comboPiece = isComboPiece(card),
                            nearMiss = isNearMiss(card),
                            lent = lentOf(card)
                        )
                    }
                }
            }
            sideboardSection(if (gridMode) gridCols else listCols, gridMode)
            addSection()
        }
        }
    }
}

/** A card the deck doesn't have, found by the deck's search: its picture, name, type, and a + to add it. */
@Composable
private fun AddToDeckRow(card: ScryfallCard, onAdd: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Surface)
            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp)
    ) {
        AsyncImage(
            model = card.displayImageUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.width(40.dp).aspectRatio(0.72f).clip(RoundedCornerShape(4.dp))
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(card.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            card.typeLine?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = onAdd) {
            Icon(Icons.Filled.AddCircle, contentDescription = "Add ${card.name} to this deck", tint = Gold)
        }
    }
}

@Composable
private fun StatsTab(
    analysis: DeckAnalysis,
    deck: Deck,
    viewModel: DeckDetailViewModel,
    onTag: (String) -> Unit,
    onOpenDeck: ((String) -> Unit)? = null,
    onOpenBadge: (() -> Unit)? = null,
    /** A [[card]] in the primer tapped. */
    onCard: (String) -> Unit = {}
) {
    if (analysis.loading) {
        LoadingBox()
        return
    }
    val valueHistory by viewModel.valueHistory.collectAsState()
    var showLogResult by remember { mutableStateOf(false) }
    val roles by viewModel.roles.collectAsState()
    val ownedGaps by viewModel.ownedGaps.collectAsState()
    val handOdds by viewModel.handOdds.collectAsState()
    var ownedFor by remember { mutableStateOf<DeckRole?>(null) }
    val context = LocalContext.current
    val versionHistory by viewModel.versionHistory.collectAsState()
    var openVersion by remember { mutableStateOf<VersionSummary?>(null) }
    val proxies by viewModel.proxies.collectAsState()
    val proxiesElsewhere by viewModel.proxiesElsewhere.collectAsState()
    val tokens by viewModel.tokens.collectAsState()
    // Every panel folds away; which are open is remembered across decks (StatsPanels).
    val panelState by viewModel.statsPanels.collectAsState()
    val isOpen: (String) -> Boolean = { id -> StatsPanels.isOpen(id, panelState) }
    val toggle: (String) -> Unit = { id -> viewModel.setStatsPanelOpen(id, !StatsPanels.isOpen(id, panelState)) }
    val money = rememberMoney()
    // Tapping a token opens the same sheet the remote uses, on that token.
    var badgeToken by remember { mutableStateOf<String?>(null) }
    badgeToken?.let { id ->
        BadgeSheetDialog(deck = deck, initialTokenId = id, onDismiss = { badgeToken = null })
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // The primer, and the deck's value over time (DeckExtrasUi.kt).
        item(key = "about") {
            AboutPanel(
                deck,
                onSave = { text ->
                    val next = tidyDescription(text)
                    if (next != deck.description.orEmpty()) viewModel.changeDeck { d -> if (next.isNotEmpty() || d.description != null) d.copy(description = next) else d }
                },
                onCard = onCard
            )
        }
        item(key = "value") { DeckValuePanel(valueHistory, money) }
        item(key = "summary") {
            val cards = deck.cards.sumOf { it.quantity }
            CollapsibleStat("Summary", isOpen("summary"), { toggle("summary") }, summary = "$cards cards · ${money.format(analysis.totalUsd)}") {
                StatsSummary(analysis, cards, money.format(analysis.totalUsd), viewModel)
            }
        }
        val (proxiesLeft, swaps) = proxies
        if (proxiesLeft > 0 || deck.ownershipType == DeckOwnership.PROXY) {
            item(key = "proxies") {
                CollapsibleStat("Proxies", isOpen("proxies"), { toggle("proxies") }, summary = "$proxiesLeft left") {
                ProxiesPanel(
                    proxiesLeft = proxiesLeft,
                    swaps = swaps,
                    elsewhere = proxiesElsewhere,
                    onOpenDeck = onOpenDeck,
                    onSwapIn = { viewModel.swapInProxy(it) },
                    onMarkPhysical = { viewModel.setOwnership(DeckOwnership.PHYSICAL) }
                )
                }
            }
        }
        item(key = "match") {
            val games = deck.gameResults.size
            CollapsibleStat("Match record", isOpen("match"), { toggle("match") }, summary = if (games == 0) "No games yet" else "$games game${if (games == 1) "" else "s"}") {
                MatchRecordPanel(deck.gameResults, onLog = { showLogResult = true }, onRemove = { viewModel.removeGameResult(it) })
            }
        }
        if (tokens.isNotEmpty()) {
            item(key = "tokens") {
                CollapsibleStat("Tokens to bring", isOpen("tokens"), { toggle("tokens") }, summary = if (tokens.size == 1) "1 kind" else "${tokens.size} kinds") {
                    TokensPanel(tokens, onOpenBadge, onTokenClick = { badgeToken = it })
                }
            }
        }
        item(key = "versions") {
            CollapsibleStat("Version history", isOpen("versions"), { toggle("versions") }, summary = if (versionHistory.isEmpty()) null else "${versionHistory.size} saved") {
                VersionHistoryPanel(versionHistory, onOpen = { openVersion = it })
            }
        }
        // A Commander bracket says nothing about a draft or sealed deck.
        if (!deck.mode.limited) item(key = "bracket") {
            CollapsibleStat("Commander bracket", isOpen("bracket"), { toggle("bracket") }, summary = analysis.bracketName.ifBlank { null }) {
            Panel {
                SectionLabel("Commander bracket")
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Text("${analysis.bracket}", style = NumberStyle(46), color = TextPrimary)
                    Column(Modifier.padding(bottom = 6.dp)) {
                        Text("Bracket", style = MaterialTheme.typography.labelMedium)
                        Text(analysis.bracketName, style = MaterialTheme.typography.titleMedium, color = GoldLight)
                    }
                }
                Text(analysis.bracketReason, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                if (analysis.gameChangers.isNotEmpty()) {
                    Text(
                        "Game Changers: ${analysis.gameChangers.joinToString(", ")}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                Text(
                    "Estimated from Game Changers and combos — not an official rating.",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextDim,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            }
        }
        item(key = "value") {
            CollapsibleStat("Total value", isOpen("value"), { toggle("value") }, summary = money.format(analysis.totalUsd)) {
            Panel {
                SectionLabel("Total value")
                CountUpText(analysis.totalUsd, NumberStyle(46), TextPrimary, format = { money.format(it) }, modifier = Modifier.padding(top = 4.dp))
            }
            }
        }
        item(key = "curve") {
            CollapsibleStat("Mana curve", isOpen("curve"), { toggle("curve") }, summary = "avg ${"%.2f".format(analysis.avgManaValue)}") {
            Panel {
                SectionLabel("Mana curve")
                ManaCurveChart(analysis.manaCurve)
                Text(
                    "Average mana value: ${"%.2f".format(analysis.avgManaValue)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
            }
        }
        item(key = "roles") {
            CollapsibleStat("Deck roles", isOpen("roles"), { toggle("roles") }) {
                RolesPanel(roles, onTag, ownedGaps, onOwned = { ownedFor = it })
            }
        }
        handOdds?.let { odds ->
            item(key = "hand") {
                CollapsibleStat("Opening hand", isOpen("hand"), { toggle("hand") }, summary = "${oddsPercent(odds.keepable)} keepable") {
                    HandOddsPanel(odds)
                }
            }
        }
        item(key = "colors") {
            CollapsibleStat("Colors", isOpen("colors"), { toggle("colors") }) {
            Panel {
                SectionLabel("Colors")
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 6.dp)) {
                    analysis.colorCounts.forEach { (color, count) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            ManaSymbol(color, size = 16.dp)
                            Text("$count", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        }
                    }
                }
            }
            }
        }
        if (analysis.colorPipCounts.isNotEmpty()) {
            item(key = "pips") {
                CollapsibleStat("Mana symbols", isOpen("pips"), { toggle("pips") }) {
                Panel {
                    SectionLabel("Mana symbols")
                    val totalPips = analysis.colorPipCounts.sumOf { it.second }
                    Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        analysis.colorPipCounts.forEach { (color, count) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ManaSymbol(color, size = 16.dp)
                                Text(
                                    "$count",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    modifier = Modifier.width(28.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(BorderColor)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(count.toFloat() / totalPips)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Gold)
                                    )
                                }
                                Text(
                                    "${count * 100 / totalPips}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextMuted,
                                    modifier = Modifier.width(38.dp)
                                )
                            }
                        }
                    }
                    Text(
                        "$totalPips colored mana symbols across every card's cast cost — how much of each color this deck actually demands, not just how many lands produce it.",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextDim,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
                }
            }
        }
        if (analysis.landCount > 0) {
            item(key = "manabase") {
                CollapsibleStat("Mana base", isOpen("manabase"), { toggle("manabase") }, summary = "${analysis.landCount} lands") {
                Panel {
                    SectionLabel("Mana base")
                    Text(
                        "${analysis.landCount} lands · ${analysis.librarySize} cards in library",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                    if (analysis.colorSourceCounts.isEmpty()) {
                        Text(
                            "No color-producing lands detected in this deck's cached data.",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextDim
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            analysis.colorSourceCounts.forEach { (color, sources) ->
                                val openingHand = probabilityAtLeastOne(analysis.librarySize, sources, 7)
                                val byTurn3 = probabilityAtLeastOne(analysis.librarySize, sources, 10)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    ManaSymbol(color, size = 16.dp)
                                    Text(
                                        "$sources sources",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary,
                                        modifier = Modifier.width(78.dp)
                                    )
                                    Text(
                                        "${(openingHand * 100).toInt()}% opening hand · ${(byTurn3 * 100).toInt()}% by turn 3",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextMuted
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        "Hypergeometric odds of drawing at least one source, on the draw.",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextDim,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    ManaAdviceList(analysis.manaAdvice)
                    if (analysis.manaAdvice.isNotEmpty()) {
                        Text(
                            "Sources count lands only — mana rocks and creatures that tap for mana aren't included.",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextDim,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
                }
            }
        }
        item(key = "types") {
            CollapsibleStat("Card types", isOpen("types"), { toggle("types") }) {
            Panel {
                SectionLabel("Card types")
                val maxType = analysis.typeCounts.maxOfOrNull { it.second } ?: 1
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    analysis.typeCounts.forEach { (type, count) -> StatBar(type, count, maxType) }
                }
            }
            }
        }
    }

    if (showLogResult) {
        LogGameResultDialog(
            suggest = { viewModel.suggestNames(it) },
            onConfirm = { result, opponent, commanders -> viewModel.logGameResult(result, opponent, commanders); showLogResult = false },
            onDismiss = { showLogResult = false }
        )
    }
    openVersion?.let { VersionDetailDialog(it, onDismiss = { openVersion = null }) }
    ownedFor?.let { role ->
        OwnedForRoleDialog(
            label = role.label,
            cards = ownedGaps[role.otag].orEmpty(),
            considering = deck.considering.map { RoleTags.key(it.name) }.toSet(),
            onAdd = { card, considering, feedback ->
                val check = AddCheck(AddToPick(deck.asTarget(), considering = considering), listOf(AddItem(AddCandidate(card.scryfallId, card.name))))
                feedback.perform(addToMessage(AddVerb.ADD, card.name, deck.name, considering), check = check) { viewModel.addOwned(card, considering, this) }
            },
            onDismiss = { ownedFor = null }
        )
    }
}

@Composable
private fun LogGameResultDialog(suggest: suspend (String) -> List<String>, onConfirm: (String, String?, List<String>) -> Unit, onDismiss: () -> Unit) {
    var result by remember { mutableStateOf("WIN") }
    var opponent by remember { mutableStateOf("") }
    // Commander names have commas in them ("Krenko, Mob Boss"), so they're picked one at a time.
    var commanders by remember { mutableStateOf<List<String>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) { suggestions = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(250)
        suggestions = suggest(q).take(5)
    }
    fun withCommander(list: List<String>, name: String): List<String> {
        val n = name.trim()
        return if (n.isEmpty() || list.any { it.equals(n, ignoreCase = true) }) list else list + n
    }
    fun add(name: String) {
        commanders = withCommander(commanders, name)
        query = ""
        suggestions = emptyList()
    }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Gold,
        unfocusedBorderColor = BorderColor,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        cursorColor = Gold
    )
    AlertDialog(
        containerColor = Surface,
        onDismissRequest = onDismiss,
        title = { Text("Log game result", color = GoldLight) },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("WIN", "LOSS", "DRAW").forEach { option ->
                        val selected = result == option
                        Text(
                            option,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) Bg else TextPrimary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) Gold else Bg)
                                .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(50))
                                .clickable { result = option }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = opponent,
                    onValueChange = { opponent = it },
                    label = { Text("Opponents (optional)", color = TextMuted) },
                    placeholder = { Text("e.g. Bob, Carol", color = TextDim) },
                    singleLine = true,
                    colors = fieldColors
                )
                Spacer(Modifier.height(8.dp))
                commanders.forEach { c ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp).clip(RoundedCornerShape(50)).background(Surface2).padding(start = 12.dp)
                    ) {
                        Text(c, style = MaterialTheme.typography.labelMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        IconButton(onClick = { commanders = commanders - c }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove $c", tint = TextDim, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(if (commanders.isEmpty()) "Their commanders (optional)" else "Add another commander", color = TextMuted) },
                    placeholder = { Text("e.g. Atraxa", color = TextDim) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (query.isNotBlank()) add(suggestions.firstOrNull() ?: query) }),
                    colors = fieldColors
                )
                if (suggestions.isNotEmpty()) {
                    Column(Modifier.padding(top = 4.dp).clip(RoundedCornerShape(12.dp)).background(Surface2)) {
                        suggestions.forEach { name ->
                            Text(
                                name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                modifier = Modifier.fillMaxWidth().clickable { add(name) }.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                // Whatever's still typed in counts too.
                onClick = { onConfirm(result, opponent.trim(), withCommander(commanders, query)) },
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
            ) { Text("Log", color = Bg) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        }
    )
}

@Composable
private fun AnalysisTab(
    analysis: DeckAnalysis,
    suggestions: List<EdhrecCardView>?,
    onZoomSugg: (String) -> Unit,
    viewModel: DeckDetailViewModel,
    onConsiderName: (String) -> Unit,
    onConsiderCard: (ScryfallCard) -> Unit,
    onMarkCut: (DeckCardEntry) -> Unit,
    onViewDetails: (String) -> Unit
) {
    if (analysis.loading) {
        LoadingBox()
        return
    }
    val viewMode by viewModel.recViewMode.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val budgetSwaps by viewModel.budgetSwaps.collectAsState()
    val ownedOnly by viewModel.ownedOnly.collectAsState()
    val ownedKeys by viewModel.ownedKeys.collectAsState()
    // "Only cards I own" narrows the budget swaps' alternatives the same way as the suggestions.
    val swaps = budgetSwaps.let { state ->
        if (state is BudgetSwapState.Done) {
            state.copy(swaps = state.swaps.map { swap ->
                swap.copy(alternatives = swap.alternatives.filter { !ownedOnly || isOwnedName(it.name, ownedKeys) }.take(ALTERNATIVES_PER_SWAP))
            })
        } else state
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "owned-only") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { viewModel.setOwnedOnly(!ownedOnly) }.padding(vertical = 2.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Only cards I own", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    Text("Suggestions and budget swaps you have in your binders", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                }
                Switch(
                    checked = ownedOnly,
                    onCheckedChange = { viewModel.setOwnedOnly(it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Gold, checkedThumbColor = Bg)
                )
            }
        }
        item { SectionLabel("Combos (${analysis.combos.size})") }
        if (!analysis.combosAvailable) {
            item { Text("Couldn't reach Commander Spellbook — check your connection.", style = MaterialTheme.typography.bodySmall, color = TextMuted) }
        } else if (analysis.combos.isEmpty()) {
            item { Text("No complete combos detected in this deck.", style = MaterialTheme.typography.bodySmall, color = TextMuted) }
        } else {
            items(analysis.combos.take(10), key = { it.id }) { combo ->
                var showCombo by remember { mutableStateOf(false) }
                ComboSummaryRow(combo, onClick = { showCombo = true })
                if (showCombo) {
                    ComboDetailDialog(combo = combo, onDismiss = { showCombo = false })
                }
            }
        }
        nearMissSection(analysis.nearMisses, analysis.combosAvailable, onConsider = onConsiderName)
        budgetSwapsSection(
            state = swaps,
            onFind = viewModel::findBudgetSwaps,
            onConsider = onConsiderCard,
            onMarkCut = onMarkCut,
            onViewDetails = onViewDetails
        )
        item { SectionLabel("EDHREC suggestions") }
        val sug = suggestions
        when {
            sug == null -> item {
                Text(
                    "Set a commander to see suggestions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            sug.isEmpty() -> item {
                Text(
                    if (ownedOnly) "None of this commander's suggestions are in your binders." else "No suggestions found.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            viewMode == CardViewMode.GRID -> {
                cardGrid(sug, columns = gridColumns, key = { it.id ?: it.name }) { view ->
                    SuggestionTile(view, onClick = { onZoomSugg(view.id ?: view.name) }, onConsider = { onConsiderName(view.name) })
                }
            }
            else -> items(sug, key = { it.id ?: it.name }) { view ->
                SuggestionRow(view, onClick = { onZoomSugg(view.id ?: view.name) }, onConsider = { onConsiderName(view.name) })
            }
        }
    }
}

// ---- shared bits ----

@Composable
internal fun LoadingBox() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Gold)
    }
}

@Composable
internal fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .elevatedCard(shape = RoundedCornerShape(22.dp))
            .padding(16.dp),
        content = content
    )
}

/**
 * A panel's title. Inside an open [CollapsibleStat] whose title it matches, it carries the chevron
 * that folds the panel away; anywhere else it's just the title.
 */
@Composable
internal fun SectionLabel(text: String) {
    val fold = LocalPanelFold.current
    if (fold == null || fold.title != text) {
        Text(text, style = MaterialTheme.typography.titleMedium)
        return
    }
    PanelTitle(text, open = true, onClick = fold.onToggle)
}

/** The title of a [CollapsibleStat], set while it's open so the panel's own [SectionLabel] can fold it. */
private class PanelFold(val title: String, val onToggle: () -> Unit)

private val LocalPanelFold = compositionLocalOf<PanelFold?> { null }

@Composable
private fun PanelTitle(text: String, open: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
        Icon(
            if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (open) "Hide $text" else "Show $text",
            tint = TextMuted,
            modifier = Modifier.padding(start = 4.dp).size(20.dp)
        )
    }
}

/**
 * A Stats panel that folds away to a single line — its [title], a chevron and a short [summary] —
 * and opens to the full [content] (a [Panel] whose [SectionLabel] matches [title]).
 */
@Composable
private fun CollapsibleStat(title: String, open: Boolean, onToggle: () -> Unit, summary: String? = null, content: @Composable () -> Unit) {
    if (open) {
        CompositionLocalProvider(LocalPanelFold provides PanelFold(title, onToggle)) { content() }
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .elevatedCard(shape = RoundedCornerShape(22.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        PanelTitle(title, open = false, onClick = onToggle)
        Spacer(Modifier.weight(1f))
        if (summary != null) {
            Text(summary, style = MaterialTheme.typography.labelMedium, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

/** The top of Stats: the deck's key figures in a strip, then its legality badge. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsSummary(analysis: DeckAnalysis, cards: Int, value: String, viewModel: DeckDetailViewModel) {
    Panel {
        SectionLabel("Summary")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 10.dp)
        ) {
            SummaryFigure("$cards", if (cards == 1) "card" else "cards")
            SummaryFigure(value, "total value")
            SummaryFigure("%.2f".format(analysis.avgManaValue), "avg mana value")
            if (analysis.bracket > 0) SummaryFigure("${analysis.bracket}", "bracket")
        }
        analysis.legality?.let { report ->
            Spacer(Modifier.height(12.dp))
            LegalitySection(report, viewModel)
        }
    }
}

@Composable
private fun SummaryFigure(value: String, label: String) {
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Surface2)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(value, style = NumberStyle(22), color = TextPrimary, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextMuted, maxLines = 1)
    }
}

@Composable
private fun ManaCurveChart(curve: List<Pair<String, Int>>) {
    val max = curve.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, popSpring()) }
    Row(
        modifier = Modifier.fillMaxWidth().height(130.dp).padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        curve.forEach { (bucket, count) ->
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("$count", style = NumberStyle(17), color = TextPrimary)
                // The bar lives in whatever space is left after both text labels, so its height is
                // always a fraction of that leftover — never a fixed dp value. A hardcoded bar
                // height could add up with the labels to more than the Row's fixed height, pushing
                // the tallest bars' bucket labels out of the chart entirely (out of line with the
                // rest); sizing by fraction of the remaining space makes overflow impossible and
                // keeps every bucket label bottom-aligned at the same height.
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.78f)
                            .fillMaxHeight(0.06f + 0.94f * count / max)
                            .graphicsLayer { scaleY = grow.value; transformOrigin = TransformOrigin(0.5f, 1f) }
                            .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                            .background(if (count > 0) Brush.verticalGradient(listOf(GoldLight, GoldDim)) else Brush.verticalGradient(listOf(Surface3, Surface3)))
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(bucket, style = MaterialTheme.typography.labelMedium, color = TextMuted)
            }
        }
    }
}

@Composable
private fun StatBar(label: String, count: Int, max: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextPrimary, modifier = Modifier.fillMaxWidth(0.28f))
        val fill = remember { Animatable(0f) }
        LaunchedEffect(count, max) { fill.animateTo(count.toFloat() / max, tween(800, easing = FastOutSlowInEasing)) }
        Box(modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(8.dp)).background(Bg)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fill.value)
                    .height(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Gold)
            )
        }
        Text("$count", style = NumberStyle(18), color = TextPrimary)
    }
}


@Composable
private fun SuggestionRow(view: EdhrecCardView, onClick: () -> Unit, onConsider: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .elevatedCard(shape = RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        AsyncImage(
            model = view.scryfallImageUrl.toArtCropUrl(),
            contentDescription = view.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.zoomSource(view.scryfallImageUrl).size(width = 72.dp, height = 52.dp).clip(RoundedCornerShape(10.dp))
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(view.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            val pct = view.inclusionPercent
            Text(
                if (pct != null) "$pct% of decks" else "${view.numDecks ?: 0} decks",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted
            )
        }
        TextButton(onClick = onConsider) { Text("Add…", color = Gold, style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
private fun SuggestionTile(view: EdhrecCardView, onClick: () -> Unit, onConsider: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        AsyncImage(
            model = view.scryfallImageUrl,
            contentDescription = view.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.zoomSource(view.scryfallImageUrl).fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(14.dp))
        )
        Text(
            view.name,
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
        val pct = view.inclusionPercent
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (pct != null) "$pct%" else "${view.numDecks ?: 0}",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
                modifier = Modifier.weight(1f)
            )
            Text(
                "Add…",
                style = MaterialTheme.typography.labelMedium,
                color = Gold,
                modifier = Modifier.clickable(onClick = onConsider).padding(vertical = 2.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeckCardRow(
    card: DeckCardEntry,
    isCommander: Boolean,
    onClick: () -> Unit,
    actions: List<CardMenuAction>,
    /** Whether the card can lead this deck's format — shows the commander star. */
    canLead: Boolean,
    onToggleCommander: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    comboPiece: Boolean = false,
    nearMiss: Boolean = false,
    /** Copies lent out from the deck (Loans.kt). */
    lent: Int = 0
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .pressScale(interactionSource)
                .clip(RoundedCornerShape(18.dp))
                .background(if (menuExpanded) Surface2 else Surface)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = androidx.compose.foundation.LocalIndication.current,
                    onClick = onClick,
                    onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuExpanded = true }
                )
                .padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
        ) {
            Box {
                ArtImage(
                    model = card.imageUrl.toArtCropUrl(),
                    seed = card.name,
                    contentDescription = card.name,
                    modifier = Modifier.zoomSource(card.imageUrl).size(width = 60.dp, height = 46.dp).clip(RoundedCornerShape(11.dp))
                )
                if (card.backImageUrl != null) FlipBadge()
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    card.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                card.typeLine?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DeckCardBadges(card.replaceable, comboPiece, nearMiss, modifier = Modifier.padding(top = 3.dp), lent = lent, quantity = card.quantity)
            }
            if (canLead) {
                IconButton(onClick = onToggleCommander, modifier = Modifier.size(30.dp)) {
                    Icon(
                        if (isCommander) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = "Set as commander",
                        tint = Gold,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            // Compact quantity stepper on the right: − removes a copy (removes the card at 0), + adds one.
            QuantityStepper(card.quantity, onDecrement = onDecrement, onIncrement = onIncrement)
        }
        CardActionMenu(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            actions = actions,
            title = card.name,
            subtitle = card.typeLine,
            imageUrl = card.imageUrl.toArtCropUrl()
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeckCardTile(
    card: DeckCardEntry,
    isCommander: Boolean = false,
    onClick: () -> Unit,
    actions: List<CardMenuAction>,
    comboPiece: Boolean = false,
    nearMiss: Boolean = false,
    /** With these, the tile carries its own − and +, so copies change without opening the card. */
    onIncrement: (() -> Unit)? = null,
    onDecrement: (() -> Unit)? = null,
    /** Copies lent out from the deck (Loans.kt). */
    lent: Int = 0
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val hasStepper = onIncrement != null && onDecrement != null
    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().pressScale(interactionSource).combinedClickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick,
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuExpanded = true }
            )
        ) {
            Box {
                AsyncImage(
                    model = card.imageUrl,
                    contentDescription = card.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.zoomSource(card.imageUrl).fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(14.dp))
                )
                // The count sits on the art only when there's no stepper below saying it.
                if (!hasStepper) Text(
                    "×${card.quantity}",
                    style = MaterialTheme.typography.labelMedium,
                    color = GoldLight,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                // The commander star already claims this corner — don't stack both badges there.
                if (card.backImageUrl != null && !isCommander) FlipBadge()
                if (isCommander) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "Commander",
                        tint = Gold,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(4.dp)
                            .size(14.dp)
                    )
                }
            }
            Text(
                card.name,
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
            DeckCardBadges(card.replaceable, comboPiece, nearMiss, modifier = Modifier.fillMaxWidth().padding(top = 2.dp), lent = lent, quantity = card.quantity)
            if (onIncrement != null && onDecrement != null) {
                Box(Modifier.padding(top = 4.dp)) { QuantityStepper(card.quantity, onDecrement = onDecrement, onIncrement = onIncrement) }
            }
        }
        CardActionMenu(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            actions = actions,
            title = card.name,
            subtitle = card.typeLine,
            imageUrl = card.imageUrl.toArtCropUrl()
        )
    }
}

private fun deckCardActions(
    entry: DeckCardEntry,
    mode: GameMode,
    isCommander: Boolean,
    hasCommander: Boolean,
    isPartnerCommander: Boolean,
    canPartnerWithCommander: Boolean,
    /** What the second commander is called: "partner commander", "Background", "Doctor"… */
    secondCommanderNoun: String,
    onViewDetails: (String) -> Unit,
    onCopy: (DeckCardEntry) -> Unit,
    onMove: (DeckCardEntry) -> Unit,
    onRemove: (DeckCardEntry) -> Unit,
    onSetCommander: (DeckCardEntry?) -> Unit,
    onSetPartnerCommander: (DeckCardEntry?) -> Unit,
    hasConsidering: Boolean,
    onToggleReplaceable: (DeckCardEntry) -> Unit,
    onMoveToConsidering: (DeckCardEntry) -> Unit,
    onSwap: (DeckCardEntry) -> Unit,
    onCategories: (DeckCardEntry) -> Unit = {},
    hasSideboard: Boolean = false,
    /** "Sideboard", or "Pool" for a Limited deck. */
    sideboardName: String = "Sideboard",
    onMoveToSideboard: (DeckCardEntry) -> Unit = {}
): List<CardMenuAction> {
    val actions = mutableListOf<CardMenuAction>()
    // Commander: who leads the deck. The same rule as the row's star — any format with a commander,
    // by that format's own test.
    val commander = "Commander"
    if (entry.canLead(mode)) {
        actions += if (isCommander) {
            CardMenuAction("Remove as commander", Icons.Filled.Star, description = "Stays in the deck", section = commander) { onSetCommander(null) }
        } else {
            CardMenuAction("Set as commander", Icons.Outlined.Star, section = commander) { onSetCommander(entry) }
        }
    }
    // Only offered once a main commander exists, for a card that isn't it, and that can actually
    // pair with it (Partner, Partner with, Friends forever, a Background, a Doctor — CommanderPairing.kt).
    if (mode.usesCommander && hasCommander && !isCommander && (isPartnerCommander || canPartnerWithCommander)) {
        actions += if (isPartnerCommander) {
            CardMenuAction("Remove as $secondCommanderNoun", Icons.Filled.Star, description = "Stays in the deck", section = commander) { onSetPartnerCommander(null) }
        } else {
            CardMenuAction("Set as $secondCommanderNoun", Icons.Outlined.Star, section = commander) { onSetPartnerCommander(entry) }
        }
    }
    // In this deck: cutting, swapping and moving it within the deck's own lists.
    val inDeck = "In this deck"
    if (!isCommander && !isPartnerCommander) {
        actions += if (entry.replaceable) {
            CardMenuAction("Not a cut candidate", Icons.Filled.SwapHoriz, section = inDeck) { onToggleReplaceable(entry) }
        } else {
            CardMenuAction("Mark as cut candidate", Icons.Filled.SwapHoriz, description = "Stays in, flagged as first to go", section = inDeck) { onToggleReplaceable(entry) }
        }
        if (hasConsidering) {
            actions += CardMenuAction("Swap with a considered card", Icons.Filled.SwapHoriz, section = inDeck) { onSwap(entry) }
        }
        actions += CardMenuAction("Move to Considering", Icons.AutoMirrored.Filled.DriveFileMove, description = "Out of the deck, still on your list", section = inDeck) { onMoveToConsidering(entry) }
        if (hasSideboard) {
            actions += CardMenuAction("Move to ${sideboardName.lowercase()}", Icons.AutoMirrored.Filled.DriveFileMove, section = inDeck) { onMoveToSideboard(entry) }
        }
    }
    // The user's own groups for it in this deck (DeckCategories.kt).
    actions += CardMenuAction(
        "Categories…", Icons.Filled.Category,
        description = entry.categories?.joinToString(", ") ?: "Your own groups: Ramp, Removal, Win cons…",
        section = inDeck
    ) { onCategories(entry) }
    // Elsewhere: other decks and binders, and the card's own page.
    val elsewhere = "Elsewhere"
    actions += CardMenuAction("Move to…", Icons.AutoMirrored.Filled.DriveFileMove, description = "Out of this deck, into another or a binder", section = elsewhere) { onMove(entry) }
    actions += CardMenuAction("Copy to…", Icons.Filled.ContentCopy, description = "Stays here, and goes there too", section = elsewhere) { onCopy(entry) }
    actions += CardMenuAction("View details (EDHREC)", Icons.Filled.Info, section = elsewhere) { onViewDetails(entry.name) }
    // Last, set apart, in the danger style.
    actions += CardMenuAction("Remove from deck", Icons.Filled.Close, destructive = true) { onRemove(entry) }
    return actions
}

/** What a sideboard card's long-press offers: back into the main deck, its details, and off the sideboard last. */
private fun sideboardCardActions(
    entry: DeckCardEntry,
    /** "Sideboard", or "Pool" for a Limited deck. */
    sideboardName: String = "Sideboard",
    onMoveToMain: () -> Unit,
    onRemove: () -> Unit,
    onViewDetails: (String) -> Unit,
    /** It's the deck's companion (Companion.kt). */
    isCompanion: Boolean = false,
    /** Its companion condition, when it's one of the ten. */
    companionRule: String? = null,
    onCompanion: (Boolean) -> Unit = {}
): List<CardMenuAction> = listOfNotNull(
    when {
        isCompanion -> CardMenuAction("Not the companion", Icons.Filled.Pets, description = "Stays in the sideboard") { onCompanion(false) }
        companionRule != null -> CardMenuAction("Make it the companion", Icons.Filled.Pets, description = companionRule) { onCompanion(true) }
        else -> null
    },
    CardMenuAction("Move to main deck", Icons.AutoMirrored.Filled.DriveFileMove) { onMoveToMain() },
    CardMenuAction("View details (EDHREC)", Icons.Filled.Info) { onViewDetails(entry.name) },
    CardMenuAction("Remove from ${sideboardName.lowercase()}", Icons.Filled.Close, destructive = true) { onRemove() }
)

/**
 * The deck page's header: commander art filling the top, deck name and key figures over its lower
 * edge, collapsing to a slim bar with just the name as the content scrolls.
 */
@Composable
private fun DeckHero(
    deck: Deck?,
    analysis: DeckAnalysis,
    height: Dp,
    collapsedFraction: Float,
    onBack: () -> Unit,
    onMenu: () -> Unit,
    menu: @Composable () -> Unit
) {
    val app = LocalAppColors.current
    val identity = analysis.colorCounts.map { it.first }.filter { it in listOf("W", "U", "B", "R", "G") }
    val expandedAlpha = (1f - collapsedFraction * 1.8f).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(Bg)
            .clipToBounds()
    ) {
        if (deck != null) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - collapsedFraction * 0.9f; translationY = -collapsedFraction * 60f }) {
                ArtImage(
                    model = deck.commander?.imageUrl.toArtCropUrl(),
                    seed = deck.name,
                    colors = identity,
                    contentDescription = deck.commander?.name,
                    modifier = Modifier.fillMaxSize().sharedArt(SharedKeys.deckArt(deck.id))
                )
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Bg.copy(alpha = 0.45f),
                    0.3f to Bg.copy(alpha = 0.05f),
                    0.7f to Bg.copy(alpha = 0.78f),
                    1f to Bg
                )
            )
        )
        Box(Modifier.fillMaxSize().background(Bg.copy(alpha = collapsedFraction)))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp)
        ) {
            HeroButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            Text(
                deck?.name ?: "",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp).graphicsLayer { alpha = ((collapsedFraction - 0.6f) * 2.5f).coerceIn(0f, 1f) }
            )
            Box {
                HeroButton(Icons.Filled.MoreVert, "Deck menu", onMenu)
                menu()
            }
        }

        if (deck != null && expandedAlpha > 0f) {
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 10.dp)
                    .graphicsLayer { alpha = expandedAlpha },
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                deck.commander?.let { commander ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (identity.isNotEmpty()) ManaPips(identity, size = 16.dp)
                        Text(commander.name, style = MaterialTheme.typography.labelLarge, color = app.textPrimary.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text(deck.name, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 2.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        CountUpText(deck.cards.sumOf { it.quantity }.toDouble(), NumberStyle(24), app.textPrimary)
                        Text(" cards", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 3.dp))
                    }
                    if (!analysis.loading && analysis.totalUsd > 0) {
                        val money = rememberMoney()
                        CountUpText(analysis.totalUsd, NumberStyle(24), app.textPrimary, format = { money.format(it) })
                    }
                    if (!analysis.loading && analysis.bracket > 0) {
                        Text(
                            "Bracket ${analysis.bracket}",
                            style = MaterialTheme.typography.labelSmall,
                            color = app.accent,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(app.accentGlow).padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    // Whether the deck is legal for its game mode; the badge at the top of Stats says why not.
                    analysis.legality?.takeIf { !analysis.loading }?.let { report ->
                        val illegal = app.error
                        Text(
                            if (report.legal) "Legal" else "Not legal",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (report.legal) app.accent else illegal,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (report.legal) app.accentGlow else illegal.copy(alpha = 0.18f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                if (identity.isNotEmpty()) IdentityStrip(identity, modifier = Modifier.width(110.dp).padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun HeroButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val app = LocalAppColors.current
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(app.bg.copy(alpha = 0.55f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = app.textPrimary, modifier = Modifier.size(22.dp))
    }
}

/** − n + in a small pill; the number bumps when it changes. */
@Composable
private fun QuantityStepper(quantity: Int, onDecrement: () -> Unit, onIncrement: () -> Unit) {
    val app = LocalAppColors.current
    val bump = remember { Animatable(1f) }
    var last by remember { mutableIntStateOf(quantity) }
    LaunchedEffect(quantity) {
        if (quantity != last) {
            last = quantity
            bump.snapTo(1.35f)
            bump.animateTo(1f, popSpring())
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(app.bg)
    ) {
        Box(Modifier.size(width = 32.dp, height = 36.dp).clickable(onClick = onDecrement), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Remove, contentDescription = "Remove a copy", tint = app.textMuted, modifier = Modifier.size(16.dp))
        }
        Text("$quantity", style = NumberStyle(20), color = app.textPrimary, modifier = Modifier.graphicsLayer { scaleX = bump.value; scaleY = bump.value })
        Box(Modifier.size(width = 32.dp, height = 36.dp).clickable(onClick = onIncrement), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Add, contentDescription = "Add a copy", tint = app.textMuted, modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * A deck built with proxies: how many are left, and the ones sitting spare in a binder, each a tap
 * away from being the real card (see Proxies.kt). Below them, the ones owned for real but only in
 * another deck — named, with the deck a tap away, but never moved: taking one out would leave that
 * deck a card short, so which deck gets it is the user's call.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProxiesPanel(
    proxiesLeft: Int,
    swaps: List<ProxySwap>,
    elsewhere: List<ProxyHeldElsewhere>,
    onOpenDeck: ((String) -> Unit)?,
    onSwapIn: (String) -> Unit,
    onMarkPhysical: () -> Unit
) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SectionLabel("Proxies") }
            Text("$proxiesLeft left", style = MaterialTheme.typography.labelMedium, color = TextMuted)
        }
        Spacer(Modifier.height(8.dp))
        when {
            proxiesLeft == 0 -> {
                Text(
                    "Every card in here is the real thing now.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                Spacer(Modifier.height(8.dp))
                GoldButton("Mark it Physical", onMarkPhysical)
            }
            swaps.isEmpty() -> Text(
                "None of these are sitting spare in your binders yet — the Wishlist is where to note the ones to buy.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            else -> {
                Text(
                    "You already own ${if (swaps.size == 1) "one of these" else "${swaps.size} of these"} for real. Swapping one in takes the copy out of your binder and stops counting it as a proxy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                Spacer(Modifier.height(8.dp))
                swaps.forEach { swap ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        AsyncImage(
                            model = swap.entry.imageUrl.toArtCropUrl(),
                            contentDescription = swap.entry.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(width = 56.dp, height = 40.dp).clip(RoundedCornerShape(8.dp))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(swap.entry.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${swap.spare} spare in your binders", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                        }
                        TextButton(onClick = { onSwapIn(swap.entry.scryfallId) }) { Text("Swap in", color = Gold) }
                    }
                }
            }
        }
        if (proxiesLeft > 0 && elsewhere.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                "In your other decks. Nothing here moves on its own — taking one out leaves that deck a card short, so it's your call which deck gets it.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            Spacer(Modifier.height(8.dp))
            elsewhere.forEach { held ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    AsyncImage(
                        model = held.entry.imageUrl.toArtCropUrl(),
                        contentDescription = held.entry.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(width = 56.dp, height = 40.dp).clip(RoundedCornerShape(8.dp))
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(held.entry.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Real copy in", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                            held.decks.forEachIndexed { i, (other, copies) ->
                                val label = other.name + (if (copies > 1) " ($copies)" else "") + if (i < held.decks.lastIndex) "," else ""
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (onOpenDeck != null) Gold else TextMuted,
                                    modifier = if (onOpenDeck != null) Modifier.clickable { onOpenDeck(other.id) } else Modifier
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


/**
 * What to put in the box besides the deck: the tokens its cards make, with the card that asks for
 * each (see DeckTokens.kt). A token whose picture hasn't arrived still shows its name.
 */
@Composable
private fun TokensPanel(tokens: List<TokenArt>, onOpenBadge: (() -> Unit)? = null, onTokenClick: ((String) -> Unit)? = null) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            SectionLabel("Tokens to bring")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (tokens.size == 1) "1 kind" else "${tokens.size} kinds",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted
                )
                if (onOpenBadge != null) {
                    // An NFC e-paper badge can stand in for the cardboard — see ui/badge.
                    Text(
                        "Put on badge",
                        style = MaterialTheme.typography.labelMedium,
                        color = Gold,
                        modifier = Modifier.clickable(onClick = onOpenBadge)
                    )
                }
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 10.dp)
        ) {
            items(tokens, key = { it.token.name + (it.token.typeLine ?: "") }) { each ->
                Column(
                    modifier = Modifier
                        .width(104.dp)
                        .then(if (onTokenClick != null) Modifier.clickable { onTokenClick(each.token.id) } else Modifier)
                ) {
                    Box {
                        AsyncImage(
                            model = each.imageUrl,
                            contentDescription = each.token.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.72f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Surface3)
                        )
                        if (each.token.madeBy.size > 1) {
                            Text(
                                "×${each.token.madeBy.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Bg,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Gold)
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        each.token.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Text(
                        madeByLabel(each.token),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Text(
            "Read off the cards themselves. A number is how many cards in the deck make that token — not how many you need.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}
