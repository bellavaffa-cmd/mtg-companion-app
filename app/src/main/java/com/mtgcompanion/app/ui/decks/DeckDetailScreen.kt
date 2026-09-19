package com.mtgcompanion.app.ui.decks

import com.mtgcompanion.app.data.RoleTags
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import com.mtgcompanion.app.data.partnersWith
import com.mtgcompanion.app.data.VersionSummary
import com.mtgcompanion.app.data.cardNameKeys
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
import com.mtgcompanion.app.ui.common.MoveTargetDialog
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

/** Tab order. The Considering tab sits right beside Cards, since the two are worked together. */
private val DECK_TABS = listOf("Cards", "Considering", "Stats", "Suggestions", "Legality")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DeckDetailScreen(
    viewModel: DeckDetailViewModel,
    onBack: () -> Unit,
    onViewDetails: (String) -> Unit,
    onShare: (() -> Unit)? = null,
    /** "Who has it?" for the cards the user doesn't own (signed in only). */
    onWhoHasIt: ((names: List<String>) -> Unit)? = null
) {
    val context = LocalContext.current
    val deck by viewModel.deck.collectAsState()
    val analysis by viewModel.analysis.collectAsState()
    val cardGroups by viewModel.cardGroups.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val prices by viewModel.prices.collectAsState()
    val layout = LocalLayoutSize.current
    // On a desktop-width window Stats sits in a panel beside the cards, so it isn't a tab there.
    val tabs = if (layout == LayoutSize.DESKTOP) DECK_TABS.filter { it != "Stats" } else DECK_TABS
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val missing by viewModel.missing.collectAsState()
    val cardTags by viewModel.cardTags.collectAsState()
    val wishlists by viewModel.wishlists.collectAsState()
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
    // The art header shrinks from a full hero to a slim bar as any tab's list scrolls, and grows
    // back when you pull down at the top — driven by the same nested-scroll state a collapsing
    // Material app bar uses, so it works across every page of the pager.
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    // The card pending a remove-confirmation, if any.
    var removeCardTarget by remember { mutableStateOf<DeckCardEntry?>(null) }
    var showImport by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showGoldfish by remember { mutableStateOf(false) }
    // Progress while an import runs, then its summary ("Imported N; M couldn't be matched…").
    var importState by remember { mutableStateOf<ImportState?>(null) }

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
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        modifier = Modifier.background(Surface2)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Deck settings", color = TextPrimary) },
                            onClick = { menuOpen = false; showSettings = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Import decklist", color = TextPrimary) },
                            onClick = { menuOpen = false; showImport = true }
                        )
                        if (onShare != null) {
                            DropdownMenuItem(
                                text = { Text("Share with friends", color = TextPrimary) },
                                onClick = { menuOpen = false; onShare() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Export decklist", color = TextPrimary) },
                            onClick = { menuOpen = false; showExport = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Goldfish (playtest)", color = TextPrimary) },
                            onClick = { menuOpen = false; showGoldfish = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Cards I don't own", color = TextPrimary) },
                            onClick = { menuOpen = false; showMissing = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete deck", color = LocalAppColors.current.error) },
                            onClick = { menuOpen = false; confirmDelete = true }
                        )
                    }
                }
            )
        }
    ) { padding ->
        val currentDeck = deck ?: return@Scaffold

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
                                canPartnerWithCommander = currentDeck.commander?.let { partnersWith(it, entry) } ?: false,
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
                                onMoveToConsidering = { viewModel.moveToConsidering(it.scryfallId); toast("Moved ${it.name} to Considering.") },
                                onSwap = { swapOut = it }
                            )
                        },
                        viewModel
                    )
                    "Considering" -> ConsideringTab(
                        deck = currentDeck,
                        analysis = analysis,
                        prices = prices,
                        onZoom = { zoom = "consider" to it },
                        onAddToDeck = { viewModel.addConsideredToDeck(it.scryfallId); toast("Added ${it.name} to the deck.") },
                        onSwapIn = { swapIn = it },
                        onRemove = { viewModel.removeFromConsidering(it.scryfallId) }
                    )
                    "Stats" -> StatsTab(analysis, currentDeck, viewModel, onTag = searchTag)
                    "Suggestions" -> AnalysisTab(
                        analysis, suggestions, onZoomSugg = { zoom = "sugg" to it }, viewModel,
                        onConsiderName = { name -> viewModel.considerByName(name, toast) },
                        onConsiderCard = { card -> viewModel.consider(card); toast("Added ${card.name} to Considering.") },
                        onMarkCut = { entry -> viewModel.setReplaceable(entry.scryfallId, true) },
                        onViewDetails = onViewDetails
                    )
                    else -> LegalityTab(analysis, viewModel)
                }
            }
        }
        if (layout == LayoutSize.DESKTOP) {
            // Stats beside the cards, the way the web app's deck page shows them.
            Box(Modifier.width(360.dp).fillMaxHeight()) {
                StatsTab(analysis, currentDeck, viewModel, onTag = searchTag)
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
                        onIncrement = { viewModel.setCardQuantity(entry.scryfallId, entry.quantity + 1) },
                        onDecrement = { viewModel.setCardQuantity(entry.scryfallId, (entry.quantity - 1).coerceAtLeast(1)) },
                        onSelectPrinting = { chosen -> viewModel.changePrinting(entry.scryfallId, chosen) },
                        onMove = { zoom = null; moveTarget = entry },
                        onViewDetails = { zoom = null; onViewDetails(entry.name) },
                        sources = cardSources[entry.scryfallId].orEmpty().filter { it.id != currentDeck.id },
                        backImageUrl = entry.backImageUrl,
                        tags = cardTags[entry.name].orEmpty().map(RoleTags::label),
                        onFindSimilar = { zoom = null; similarSearchFor = entry.name },
                        onTagClick = searchTag
                    )
                }
                CardZoomDialog(zoomCards, flatCards.indexOfFirst { it.scryfallId == key }.coerceAtLeast(0)) { zoom = null }
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
                        onTagClick = searchTag
                    )
                }
                CardZoomDialog(zoomCards, considering.indexOfFirst { it.scryfallId == key }.coerceAtLeast(0)) { zoom = null }
            } else {
                val sug = suggestions.orEmpty()
                val zoomCards = sug.map { suggestion ->
                    ZoomCard(
                        imageUrl = suggestion.scryfallImageUrl,
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
                    viewModel.addCard(similar) { warning -> Toast.makeText(context, warning, Toast.LENGTH_LONG).show() }
                },
                onViewDetails = { similar -> similarSearchFor = null; onViewDetails(similar.name) }
            )
        }

        moveTarget?.let { entry ->
            MoveTargetDialog(
                cardName = entry.name,
                targets = moveTargets,
                onPick = { target -> viewModel.moveCard(entry, target); moveTarget = null },
                onDismiss = { moveTarget = null }
            )
        }

        copyTarget?.let { entry ->
            MoveTargetDialog(
                cardName = entry.name,
                targets = moveTargets,
                onPick = { target -> viewModel.copyCard(entry, target); copyTarget = null },
                onDismiss = { copyTarget = null }
            )
        }

        removeCardTarget?.let { entry ->
            ConfirmDeleteDialog(
                title = "Remove card?",
                message = "Remove ${entry.name} (${entry.quantity} cop${if (entry.quantity == 1) "y" else "ies"}) from this deck?",
                confirmLabel = "Remove",
                onConfirm = { viewModel.removeCard(entry.scryfallId); removeCardTarget = null },
                onDismiss = { removeCardTarget = null }
            )
        }

        if (showSettings) {
            DeckSettingsDialog(
                current = currentDeck.mode,
                onSelect = { viewModel.setGameMode(it) },
                ownership = currentDeck.ownershipType,
                onOwnershipChange = { viewModel.setOwnership(it) },
                tags = currentDeck.tags,
                onTagsChange = { viewModel.setTags(it) },
                onDismiss = { showSettings = false }
            )
        }
        if (showImport) {
            ImportDialog(
                onDismiss = { showImport = false },
                onImport = { text ->
                    showImport = false
                    importState = ImportState()
                    viewModel.importDecklist(
                        text = text,
                        onProgress = { done, total ->
                            importState = ImportState(done = done, total = total)
                        },
                        onResult = { added, failed ->
                            importState = ImportState(summary = importSummary(added, failed))
                        }
                    )
                }
            )
        }
        importState?.let { state ->
            ImportResultDialog(state = state, onDismiss = { importState = null })
        }
        if (confirmDelete) {
            DeleteDeckDialog(
                deckName = currentDeck.name,
                cardCount = currentDeck.cards.sumOf { it.quantity },
                onConfirm = { confirmDelete = false; viewModel.deleteDeck(onBack) },
                onDismiss = { confirmDelete = false }
            )
        }
        if (showExport) {
            ExportDialog(deck = currentDeck, viewModel = viewModel, onDismiss = { showExport = false })
        }
        if (showGoldfish) {
            GoldfishDialog(deck = currentDeck, onDismiss = { showGoldfish = false })
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
                        viewModel.swap(outgoing.scryfallId, incoming.scryfallId)
                        toast("Swapped in ${incoming.name} for ${outgoing.name}.")
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
                    viewModel.swap(outgoing.scryfallId, incoming.scryfallId)
                    toast("Swapped in ${incoming.name} for ${outgoing.name}.")
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
                wishlists = wishlists,
                onAddToWishlist = { wishlistId, newName ->
                    viewModel.addMissingToWishlist(wishlistId, newName) { message -> toast(message) }
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

private fun importSummary(added: Int, failed: List<String>): String = buildString {
    append("Imported $added card${if (added == 1) "" else "s"}.")
    if (failed.isNotEmpty()) {
        append("\n\n${failed.size} line${if (failed.size == 1) "" else "s"} couldn't be matched:\n")
        append(failed.take(25).joinToString("\n") { "• $it" })
        if (failed.size > 25) append("\n…and ${failed.size - 25} more")
    }
}

/** Deleting a deck throws away its whole card list and can't be undone, so make it deliberate. */
@Composable
private fun DeleteDeckDialog(
    deckName: String,
    cardCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ConfirmDeleteDialog(
        title = "Delete deck?",
        message = "\"$deckName\" and its $cardCount card${if (cardCount == 1) "" else "s"} will be " +
            "permanently deleted. This can't be undone.",
        onConfirm = onConfirm,
        onDismiss = onDismiss
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
            Text(if (summary == null) "Importing decklist…" else "Import complete", color = GoldLight)
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

/** Builds a plain-text decklist ("1 Card Name" per line), commander first. */
/**
 * "Simple" is just "qty name" per line — the most broadly compatible format (Moxfield, Archidekt,
 * TappedOut, MTG Arena, MTGO all read it). "Exact printing" appends "(SET) collector-number" from
 * [cards], the same "(SLD) 1962" shape the deck's own decklist *importer* already parses — so it
 * round-trips through this app (or anywhere else that also understands printing-annotated lines)
 * preserving which specific art/printing each card was.
 */
private fun buildDecklist(deck: Deck, cards: Map<String, ScryfallCard> = emptyMap(), exactPrinting: Boolean = false): String = buildString {
    fun line(entry: DeckCardEntry) {
        val printing = if (exactPrinting) {
            cards[entry.scryfallId]?.let { card ->
                val set = card.set?.uppercase()
                val number = card.collectorNumber
                if (set != null && number != null) " ($set) $number" else null
            }
        } else null
        appendLine("${entry.quantity} ${entry.name}${printing ?: ""}")
    }
    deck.commander?.let { line(it) }
    deck.partnerCommander?.let { line(it) }
    val commanderIds = setOfNotNull(deck.commander?.scryfallId, deck.partnerCommander?.scryfallId)
    deck.cards
        .filterNot { it.scryfallId in commanderIds }
        .sortedBy { it.name.lowercase() }
        .forEach { line(it) }
}

@Composable
private fun ImportDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        containerColor = Surface,
        onDismissRequest = onDismiss,
        title = { Text("Import decklist", color = GoldLight) },
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

@Composable
private fun ExportDialog(deck: Deck, viewModel: DeckDetailViewModel, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var exact by remember { mutableStateOf(false) }
    var exactCards by remember { mutableStateOf<Map<String, ScryfallCard>?>(null) }
    var loadingExact by remember { mutableStateOf(false) }

    LaunchedEffect(exact) {
        if (exact && exactCards == null) {
            loadingExact = true
            exactCards = runCatching { viewModel.resolveCardsForExport() }.getOrDefault(emptyMap())
            loadingExact = false
        }
    }

    val decklist = if (exact) buildDecklist(deck, exactCards.orEmpty(), exactPrinting = true) else buildDecklist(deck)

    AlertDialog(
        containerColor = Surface,
        onDismissRequest = onDismiss,
        title = { Text("Export decklist", color = GoldLight) },
        text = {
            Column {
                Text(
                    "Copy this decklist to share or back up your deck.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportFormatChip("Simple", selected = !exact) { exact = false }
                    ExportFormatChip("Exact printing", selected = exact) { exact = true }
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
                    if (exact && loadingExact) {
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
                enabled = decklist.isNotBlank() && !(exact && loadingExact),
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
                    "The game mode sets the legality rules checked in the Legal tab.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                Spacer(Modifier.height(14.dp))
                GameModeDropdown(selected = current, onSelect = onSelect)
                Spacer(Modifier.height(20.dp))
                Text("Ownership", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeckOwnership.entries.forEach { option ->
                        val selected = option == ownership
                        Text(
                            option.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) Bg else TextPrimary,
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

@Composable
private fun LegalityTab(analysis: DeckAnalysis, viewModel: DeckDetailViewModel) {
    val report = analysis.legality
    if (report == null) {
        LoadingBox()
        return
    }
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val color = if (report.legal) Gold else Color(0xFFD3402F)
                    Icon(
                        if (report.legal) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                        contentDescription = null,
                        tint = color
                    )
                    Column {
                        Text(
                            if (report.legal) "Legal for ${report.mode.label}" else "Not legal for ${report.mode.label}",
                            style = MaterialTheme.typography.titleMedium,
                            color = color
                        )
                        Text(
                            "${report.totalCards} cards" +
                                if (report.mode.exactSize) " · needs ${report.mode.deckSize}" else " · min ${report.mode.deckSize}",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMuted
                        )
                    }
                }
            }
        }
        if (report.legal) {
            item {
                Text(
                    "No rule violations found for ${report.mode.label}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        } else {
            item {
                Text(
                    "${report.issues.size} issue${if (report.issues.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(report.issues) { issue ->
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
            tint = Color(0xFFD3402F),
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
    viewModel: DeckDetailViewModel
) {
    val query by viewModel.cardQuery.collectAsState()
    val trimmed = query.trim()
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

    Column(modifier = Modifier.fillMaxSize()) {
        if (deck.cards.isNotEmpty() || deck.commander != null) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setCardQuery,
                placeholder = { Text("Name or tag, e.g. ramp", color = TextDim) },
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp)
            )
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
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
        val listCols = listColumnsFor(maxWidth - 40.dp)
        val gridCols = gridColumnsFor(maxWidth - 40.dp, gridColumns)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (deck.cards.isEmpty()) {
                item {
                    Text(
                        "No cards yet. Add cards to this deck from a card's detail page.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                return@LazyColumn
            }
            if (groups.isEmpty()) {
                item {
                    Text(
                        when {
                            trimmed.isNotBlank() -> "No cards match \"$trimmed\"."
                            filter == CardFilter.CUT -> "No cut candidates. Long-press a card and choose Mark as cut candidate."
                            filter == CardFilter.COMBO -> "No combo pieces detected in this deck."
                            else -> "No cards."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
                return@LazyColumn
            }

            groups.forEach { group ->
                item {
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp, start = 2.dp, end = 4.dp)) {
                        Text(group.type, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text("${group.cards.sumOf { it.quantity }}", style = NumberStyle(20), color = TextMuted)
                    }
                }
                if (viewMode == CardViewMode.GRID) {
                    cardGrid(group.cards, columns = gridCols, key = { it.scryfallId }) { card ->
                        DeckCardTile(
                            card = card,
                            isCommander = card.scryfallId == deck.commander?.scryfallId || card.scryfallId == deck.partnerCommander?.scryfallId,
                            onClick = { onZoomCard(card.scryfallId) },
                            actions = cardActions(card),
                            comboPiece = isComboPiece(card),
                            nearMiss = isNearMiss(card)
                        )
                    }
                } else {
                    cardGrid(group.cards, columns = listCols, key = { it.scryfallId }) { card ->
                        DeckCardRow(
                            card = card,
                            isCommander = card.scryfallId == deck.commander?.scryfallId || card.scryfallId == deck.partnerCommander?.scryfallId,
                            onClick = { onZoomCard(card.scryfallId) },
                            actions = cardActions(card),
                            onToggleCommander = {
                                viewModel.setCommander(if (deck.commander?.scryfallId == card.scryfallId) null else card)
                            },
                            onIncrement = { viewModel.setCardQuantity(card.scryfallId, card.quantity + 1) },
                            onDecrement = { viewModel.setCardQuantity(card.scryfallId, card.quantity - 1) },
                            comboPiece = isComboPiece(card),
                            nearMiss = isNearMiss(card)
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun StatsTab(analysis: DeckAnalysis, deck: Deck, viewModel: DeckDetailViewModel, onTag: (String) -> Unit) {
    if (analysis.loading) {
        LoadingBox()
        return
    }
    var showLogResult by remember { mutableStateOf(false) }
    val roles by viewModel.roles.collectAsState()
    val ownedGaps by viewModel.ownedGaps.collectAsState()
    val handOdds by viewModel.handOdds.collectAsState()
    var ownedFor by remember { mutableStateOf<DeckRole?>(null) }
    val context = LocalContext.current
    val versionHistory by viewModel.versionHistory.collectAsState()
    var openVersion by remember { mutableStateOf<VersionSummary?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { MatchRecordPanel(deck.gameResults, onLog = { showLogResult = true }, onRemove = { viewModel.removeGameResult(it) }) }
        item { VersionHistoryPanel(versionHistory, onOpen = { openVersion = it }) }
        item {
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
        item {
            Panel {
                SectionLabel("Total value (USD)")
                CountUpText(analysis.totalUsd, NumberStyle(46), TextPrimary, format = { "$" + "%,.2f".format(it) }, modifier = Modifier.padding(top = 4.dp))
            }
        }
        item {
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
        item { RolesPanel(roles, onTag, ownedGaps, onOwned = { ownedFor = it }) }
        handOdds?.let { odds -> item { HandOddsPanel(odds) } }
        item {
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
        if (analysis.colorPipCounts.isNotEmpty()) {
            item {
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
        if (analysis.landCount > 0) {
            item {
                Panel {
                    SectionLabel("Mana base")
                    Text(
                        "${analysis.landCount} lands · ${analysis.deckSize} cards in library",
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
                                val openingHand = probabilityAtLeastOne(analysis.deckSize, sources, 7)
                                val byTurn3 = probabilityAtLeastOne(analysis.deckSize, sources, 10)
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
        item {
            Panel {
                SectionLabel("Card types")
                val maxType = analysis.typeCounts.maxOfOrNull { it.second } ?: 1
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    analysis.typeCounts.forEach { (type, count) -> StatBar(type, count, maxType) }
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
            onAdd = { card, considering -> viewModel.addOwned(card, considering) { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } },
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
            state = budgetSwaps,
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
            sug.isEmpty() -> item { Text("No suggestions found.", style = MaterialTheme.typography.bodySmall, color = TextMuted) }
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

@Composable
internal fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
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
        TextButton(onClick = onConsider) { Text("Consider", color = Gold, style = MaterialTheme.typography.labelMedium) }
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
                "Consider",
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
    onToggleCommander: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    comboPiece: Boolean = false,
    nearMiss: Boolean = false
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
                DeckCardBadges(card.replaceable, comboPiece, nearMiss, modifier = Modifier.padding(top = 3.dp))
            }
            if (card.canBeCommander) {
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
    nearMiss: Boolean = false
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    Box {
        Column(
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
                Text(
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
                modifier = Modifier.padding(top = 4.dp)
            )
            DeckCardBadges(card.replaceable, comboPiece, nearMiss, modifier = Modifier.padding(top = 2.dp))
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
    onViewDetails: (String) -> Unit,
    onCopy: (DeckCardEntry) -> Unit,
    onMove: (DeckCardEntry) -> Unit,
    onRemove: (DeckCardEntry) -> Unit,
    onSetCommander: (DeckCardEntry?) -> Unit,
    onSetPartnerCommander: (DeckCardEntry?) -> Unit,
    hasConsidering: Boolean,
    onToggleReplaceable: (DeckCardEntry) -> Unit,
    onMoveToConsidering: (DeckCardEntry) -> Unit,
    onSwap: (DeckCardEntry) -> Unit
): List<CardMenuAction> {
    val actions = mutableListOf<CardMenuAction>()
    if (!isCommander && !isPartnerCommander) {
        actions += if (entry.replaceable) {
            CardMenuAction("Not a cut candidate", Icons.Filled.SwapHoriz) { onToggleReplaceable(entry) }
        } else {
            CardMenuAction("Mark as cut candidate", Icons.Filled.SwapHoriz) { onToggleReplaceable(entry) }
        }
        if (hasConsidering) {
            actions += CardMenuAction("Swap with a considered card", Icons.Filled.SwapHoriz) { onSwap(entry) }
        }
        actions += CardMenuAction("Move to Considering", Icons.AutoMirrored.Filled.DriveFileMove) { onMoveToConsidering(entry) }
    }
    if (entry.canBeCommander && mode == GameMode.COMMANDER) {
        actions += if (isCommander) {
            CardMenuAction("Remove as commander", Icons.Filled.Star) { onSetCommander(null) }
        } else {
            CardMenuAction("Set as commander", Icons.Outlined.Star) { onSetCommander(entry) }
        }
    }
    // Only offered once a main commander exists, for a card that isn't it, and that actually has a
    // valid Partner pairing with it (plain "Partner"+"Partner", or a matching "Partner with <name>").
    if (mode == GameMode.COMMANDER && hasCommander && !isCommander && (isPartnerCommander || canPartnerWithCommander)) {
        actions += if (isPartnerCommander) {
            CardMenuAction("Remove as partner commander", Icons.Filled.Star) { onSetPartnerCommander(null) }
        } else {
            CardMenuAction("Set as partner commander", Icons.Outlined.Star) { onSetPartnerCommander(entry) }
        }
    }
    actions += CardMenuAction("Add to another binder/deck", Icons.Filled.Add) { onCopy(entry) }
    actions += CardMenuAction("Move", Icons.AutoMirrored.Filled.DriveFileMove) { onMove(entry) }
    actions += CardMenuAction("Remove from deck", Icons.Filled.Close, destructive = true) { onRemove(entry) }
    actions += CardMenuAction("View details (EDHREC)", Icons.Filled.Info) { onViewDetails(entry.name) }
    return actions
}

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
            HeroButton(Icons.Filled.ArrowBack, "Back", onBack)
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
                        CountUpText(analysis.totalUsd, NumberStyle(24), app.textPrimary, format = { "$" + "%,.2f".format(it) })
                    }
                    if (!analysis.loading && analysis.bracket > 0) {
                        Text(
                            "Bracket ${analysis.bracket}",
                            style = MaterialTheme.typography.labelSmall,
                            color = app.accent,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(app.accentGlow).padding(horizontal = 10.dp, vertical = 4.dp)
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
