package com.mtgcompanion.app.ui.detail

import com.mtgcompanion.app.ui.common.openUrl
import com.mtgcompanion.app.ui.common.rememberMoney
import com.mtgcompanion.app.ui.common.zoomSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import com.mtgcompanion.app.ui.common.gridColumnsFor
import com.mtgcompanion.app.ui.common.LocalLayoutSize
import com.mtgcompanion.app.ui.common.LayoutSize
import kotlinx.coroutines.launch
import com.mtgcompanion.app.ui.theme.Surface2
import com.mtgcompanion.app.ui.theme.OnGold
import com.mtgcompanion.app.ui.theme.NumberStyle
import com.mtgcompanion.app.ui.common.sharedArt
import com.mtgcompanion.app.ui.common.riseIn
import com.mtgcompanion.app.ui.common.popSpring
import com.mtgcompanion.app.ui.common.foilShine
import com.mtgcompanion.app.ui.common.SharedKeys
import com.mtgcompanion.app.ui.common.PillChip
import com.mtgcompanion.app.ui.common.CountUpText
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.ui.common.CardTagsRow
import com.mtgcompanion.app.ui.common.CardZoomDialog
import com.mtgcompanion.app.ui.common.SimilarCardsDialog
import com.mtgcompanion.app.ui.common.ComboDetailDialog
import com.mtgcompanion.app.ui.common.ComboSummaryRow
import com.mtgcompanion.app.ui.common.ManaCost
import com.mtgcompanion.app.ui.common.InlineManaText
import com.mtgcompanion.app.ui.common.ZoomCard
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.network.edhrec.EdhrecCardList
import com.mtgcompanion.app.network.edhrec.EdhrecCardView
import com.mtgcompanion.app.network.edhrec.inclusionPercent
import com.mtgcompanion.app.network.edhrec.scryfallImageUrl
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.spellbook.Variant
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
fun CardDetailScreen(
    viewModel: CardDetailViewModel,
    onBack: () -> Unit,
    onViewDetails: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val decks by viewModel.decks.collectAsState()
    val collections by viewModel.collections.collectAsState()
    val owned by viewModel.ownedByName.collectAsState()
    val cardSources by viewModel.cardSources.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val context = LocalContext.current
    var showDeckPicker by remember { mutableStateOf(false) }
    var showCollectionPicker by remember { mutableStateOf(false) }
    // Key of the suggested card being enlarged, if any.
    var zoomKey by remember { mutableStateOf<String?>(null) }
    // scryfallId of the enlarged "similar card", if any — its own overlay, independent of the
    // EDHREC suggestions grid above (different data source, not meant to swipe together).
    var similarZoomId by remember { mutableStateOf<String?>(null) }
    // Name of the card a nested "find similar" was triggered for, from within a zoom overlay.
    var similarSearchFor by remember { mutableStateOf<String?>(null) }
    // Card the binder/deck pickers will add — this page's card, or one of its suggestions.
    var addTarget by remember { mutableStateOf<ScryfallCard?>(null) }
    // Set when the add button on an enlarged card needs a binder-or-deck choice first.
    var chooseDestinationFor by remember { mutableStateOf<ScryfallCard?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    LaunchedEffect(state.addedToCollectionMessage, state.addedToDeckMessage) {
        val message = state.addedToCollectionMessage ?: state.addedToDeckMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        containerColor = Bg,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(state.card?.name ?: "Card", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Gold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg, scrolledContainerColor = Surface),
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        when {
            state.loading -> Column(
                modifier = Modifier.fillMaxSize().background(Bg).padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) { CircularProgressIndicator(color = Gold) }

            state.error != null -> Column(
                modifier = Modifier.fillMaxSize().background(Bg).padding(padding).padding(16.dp)
            ) { Text(state.error ?: "", color = MaterialTheme.colorScheme.error) }

            state.card != null -> {
                val card = state.card!!
                // A legendary creature has two distinct EDHREC datasets: recs for building around it
                // as a commander, vs. recs for it as an inclusion in someone else's deck.
                val showingCommanderView = card.canBeCommander && state.viewAsCommander
                val activeLists = if (showingCommanderView) state.edhrecLists else state.cardEdhrecLists
                val activeLoading = if (showingCommanderView) state.edhrecLoading else state.cardEdhrecLoading
                val sections = activeLists?.filter { it.cardviews.isNotEmpty() }.orEmpty()
                // Every tile on screen, flattened, so the overlay can swipe across sections.
                val zoomable = sections.flatMap { section ->
                    section.cardviews.take(TILES_PER_SECTION).map { view -> section.tileKey(view) to view }
                }

                // The page's own sections (header, printings, add buttons, prices) and the browsing
                // sections below them (EDHREC, combos, similar cards), shared by both layouts.
                val cardSections: androidx.compose.foundation.lazy.grid.LazyGridScope.() -> Unit = {
                    fullSpanItem { CardHeader(card) }
                    if (state.prints.size > 1) {
                        fullSpanItem {
                            PrintsSection(
                                prints = state.prints,
                                selectedId = card.id,
                                onSelect = viewModel::selectPrinting
                            )
                        }
                    }
                    fullSpanItem {
                        CollectionAndDeckActions(
                            onAddToCollection = { addTarget = card; showCollectionPicker = true },
                            onAddToDeck = { addTarget = card; showDeckPicker = true }
                        )
                    }
                    fullSpanItem { PricesSection(state, onOpenTcgplayer = {
                        card.purchaseUris?.tcgplayer?.let { openUrl(context, it) }
                    }) }

                }
                val browseSections: androidx.compose.foundation.lazy.grid.LazyGridScope.() -> Unit = {
                    if (card.canBeCommander) {
                        fullSpanItem {
                            CommanderViewToggle(
                                asCommander = state.viewAsCommander,
                                onChange = viewModel::setViewAsCommander
                            )
                        }
                    }

                    if (activeLoading) {
                        fullSpanItem {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Gold)
                            }
                        }
                    } else if (sections.isEmpty()) {
                        fullSpanItem {
                            Text(
                                "No EDHREC data for this card.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    } else {
                        sections.forEach { section -> edhrecSection(section) { zoomKey = it } }
                    }

                    fullSpanItem { SectionHeader("Combos · Commander Spellbook") }
                    fullSpanItem { CombosSection(state) }

                    // Same type + colors + a nearby mana value — not synergy, just "cards like this
                    // one" for browsing alternatives. Distinct from the EDHREC recs above.
                    fullSpanItem { SectionHeader("Similar cards") }
                    if (state.similarLoading) {
                        fullSpanItem {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Gold)
                            }
                        }
                    } else if (state.similarCards.isEmpty()) {
                        fullSpanItem {
                            Text(
                                "No similar cards found.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    } else {
                        items(state.similarCards, key = { it.id }) { similar ->
                            SimilarCardTile(similar, onClick = { similarZoomId = similar.id })
                        }
                    }
                                }

                if (LocalLayoutSize.current.isWide) {
                    // Tablet and desktop: the card and its prices stay put on the left while the
                    // suggestions scroll on the right — the web app's two-column card view.
                    val layout = LocalLayoutSize.current
                    Row(Modifier.fillMaxSize().background(Bg).padding(padding)) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(1),
                            modifier = Modifier.width(if (layout == LayoutSize.DESKTOP) 440.dp else 360.dp).fillMaxHeight(),
                            contentPadding = PaddingValues(start = layout.pagePadding - 12.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) { cardSections() }
                        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(gridColumnsFor(maxWidth - 40.dp, gridColumns)),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 12.dp, end = layout.pagePadding - 12.dp, top = 16.dp, bottom = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) { browseSections() }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        // Fixed column count from the shared grid-size setting, same as every other tab.
                        columns = GridCells.Fixed(gridColumns),
                        modifier = Modifier.fillMaxSize().background(Bg).padding(padding),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        cardSections()
                        browseSections()
                    }
                }

                similarZoomId?.let { id ->
                    CardZoomDialog(
                        cards = state.similarCards.map { similar ->
                            ZoomCard(
                                imageUrl = similar.displayImageUrl,
                                cardName = similar.name,
                                priceUsd = similar.prices?.usd?.toDoubleOrNull(),
                                onAdd = { similarZoomId = null; chooseDestinationFor = similar },
                                onSelectPrinting = { chosen -> similarZoomId = null; chooseDestinationFor = chosen },
                                onViewDetails = { similarZoomId = null; onViewDetails(similar.name) },
                                sources = cardSources[similar.id].orEmpty(),
                                backImageUrl = similar.backImageUrl,
                                tags = similar.tags,
                                onFindSimilar = { similarZoomId = null; similarSearchFor = similar.name }
                            )
                        },
                        initialIndex = state.similarCards.indexOfFirst { it.id == id }.coerceAtLeast(0)
                    ) { similarZoomId = null }
                }

                zoomKey?.let { key ->
                    CardZoomDialog(
                        cards = zoomable.map { (_, view) ->
                            val resolved = state.suggestionCards[view.name.lowercase()]
                            ZoomCard(
                                imageUrl = view.scryfallImageUrl,
                                cardName = view.name,
                                priceUsd = resolved?.prices?.usd?.toDoubleOrNull(),
                                quantity = owned[view.name.lowercase()] ?: 0,
                                // Only offer to add once we know which Scryfall printing it is.
                                onAdd = resolved?.let { card ->
                                    { zoomKey = null; chooseDestinationFor = card }
                                },
                                // Picking a printing here goes straight into the binder-or-deck
                                // choice, so choosing art and saving it is one motion, not two.
                                onSelectPrinting = { chosen -> zoomKey = null; chooseDestinationFor = chosen },
                                onViewDetails = { zoomKey = null; onViewDetails(view.name) },
                                sources = resolved?.id?.let { cardSources[it] }.orEmpty(),
                                backImageUrl = resolved?.backImageUrl,
                                tags = resolved?.tags.orEmpty(),
                                onFindSimilar = { zoomKey = null; similarSearchFor = view.name }
                            )
                        },
                        initialIndex = zoomable.indexOfFirst { (k, _) -> k == key }.coerceAtLeast(0)
                    ) { zoomKey = null }
                }
            }
        }
    }

    similarSearchFor?.let { name ->
        SimilarCardsDialog(
            cardName = name,
            onDismiss = { similarSearchFor = null },
            onAdd = { similar -> similarSearchFor = null; chooseDestinationFor = similar },
            onViewDetails = { similar -> similarSearchFor = null; onViewDetails(similar.name) }
        )
    }

    chooseDestinationFor?.let { card ->
        AddDestinationDialog(
            cardName = card.name,
            onDismiss = { chooseDestinationFor = null },
            onBinder = { chooseDestinationFor = null; addTarget = card; showCollectionPicker = true },
            onDeck = { chooseDestinationFor = null; addTarget = card; showDeckPicker = true }
        )
    }

    if (showDeckPicker) {
        val target = addTarget
        DeckPickerDialog(
            decks = decks,
            onDismiss = { showDeckPicker = false },
            onPickDeck = { deckId ->
                showDeckPicker = false
                target?.let { viewModel.addToDeck(deckId, it) }
            },
            onConsiderDeck = { deckId ->
                showDeckPicker = false
                target?.let { viewModel.considerForDeck(deckId, it) }
            },
            onCreateDeck = { name ->
                showDeckPicker = false
                target?.let { viewModel.createDeckAndAdd(name, it) }
            }
        )
    }

    if (showCollectionPicker) {
        val target = addTarget
        CollectionPickerDialog(
            collections = collections,
            onDismiss = { showCollectionPicker = false },
            onPickCollection = { collectionId ->
                showCollectionPicker = false
                target?.let { viewModel.addToCollection(collectionId, it) }
            },
            onCreateCollection = { name ->
                showCollectionPicker = false
                target?.let { viewModel.createCollectionAndAdd(name, it) }
            }
        )
    }
}

/** Binder or deck? Asked when adding straight from an enlarged suggested card. */
@Composable
private fun AddDestinationDialog(
    cardName: String,
    onDismiss: () -> Unit,
    onBinder: () -> Unit,
    onDeck: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Add $cardName to…", color = GoldLight, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                Text(
                    "Binder",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onBinder).padding(vertical = 12.dp)
                )
                Text(
                    "Deck",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onDeck).padding(vertical = 12.dp)
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } }
    )
}

@Composable
private fun CollectionPickerDialog(
    collections: List<com.mtgcompanion.app.data.Collection>,
    onDismiss: () -> Unit,
    onPickCollection: (String) -> Unit,
    onCreateCollection: (String) -> Unit
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Add to binder", color = GoldLight, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                collections.forEach { collection ->
                    Text(
                        collection.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPickCollection(collection.id) }
                            .padding(vertical = 10.dp)
                    )
                }
                if (collections.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(BorderColor))
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New binder name", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Gold,
                        unfocusedBorderColor = BorderColor,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = Gold
                    ),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (newName.isNotBlank()) onCreateCollection(newName.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
            ) { Text("Create & add", color = Bg) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        }
    )
}

/** Adds a single full-width row inside the grid. */
private fun androidx.compose.foundation.lazy.grid.LazyGridScope.fullSpanItem(
    content: @Composable () -> Unit
) = item(span = { GridItemSpan(maxLineSpan) }) { content() }

/** Identifies a tile across the grid and the zoom overlay — the same card can appear in two sections. */
private fun EdhrecCardList.tileKey(view: EdhrecCardView): String = "$tag-${view.id ?: view.name}"

/** One EDHREC section: a full-width header followed by a grid of card tiles. */
private fun androidx.compose.foundation.lazy.grid.LazyGridScope.edhrecSection(
    section: EdhrecCardList,
    onZoom: (String) -> Unit
) {
    fullSpanItem { SectionHeader(section.header ?: "") }
    items(section.cardviews.take(TILES_PER_SECTION), key = { section.tileKey(it) }) { view ->
        EdhrecTile(view, onClick = { onZoom(section.tileKey(view)) })
    }
}

@Composable
private fun EdhrecTile(view: EdhrecCardView, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = view.scryfallImageUrl,
            contentDescription = view.name,
            modifier = Modifier.zoomSource(view.scryfallImageUrl)
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(14.dp))
        )
        Text(
            view.name,
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
        val pct = view.inclusionPercent
        Text(
            if (pct != null) "$pct% of decks" else "${view.numDecks ?: 0} decks",
            style = MaterialTheme.typography.labelMedium,
            color = GoldDim
        )
    }
}

@Composable
private fun SimilarCardTile(card: ScryfallCard, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = card.displayImageUrl,
            contentDescription = card.name,
            modifier = Modifier.zoomSource(card.displayImageUrl)
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(14.dp))
        )
        Text(
            card.name,
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp, bottom = 2.dp, start = 2.dp))
}

@Composable
private fun GoldPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Surface)
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun PrintsSection(prints: List<ScryfallCard>, selectedId: String, onSelect: (ScryfallCard) -> Unit) {
    Column {
        SectionHeader("Prints · Alternate art")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            prints.forEach { print ->
                val selected = print.id == selectedId
                Column(
                    modifier = Modifier.width(90.dp).clickable { onSelect(print) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AsyncImage(
                        model = print.displayImageUrl,
                        contentDescription = print.printingLabel,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .width(90.dp)
                            .aspectRatio(0.72f)
                            .clip(RoundedCornerShape(6.dp))
                            .border(
                                BorderStroke(if (selected) 2.dp else 0.dp, if (selected) Gold else Color.Transparent),
                                RoundedCornerShape(6.dp)
                            )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        print.set?.uppercase() ?: "",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) GoldLight else TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun CardHeader(card: ScryfallCard) {
    // Which face's art/name/mana cost/type line to show — resets if the printing changes underneath.
    var showBack by remember(card.id) { mutableStateOf(false) }
    val backFace = card.cardFaces?.getOrNull(1)
    val flipped = showBack && card.backImageUrl != null
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(contentAlignment = Alignment.TopEnd) {
            TiltingCard(
                imageUrl = if (flipped) card.backImageUrl else card.displayImageUrl,
                name = card.name,
                modifier = Modifier.width(250.dp).padding(top = 8.dp, bottom = 6.dp)
            )
            if (card.backImageUrl != null) {
                IconButton(
                    onClick = { showBack = !showBack },
                    modifier = Modifier
                        .padding(top = 18.dp, end = 10.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                ) {
                    Icon(Icons.Filled.Autorenew, contentDescription = "Flip card", tint = Color.White)
                }
            }
        }
        Text("Drag the card to tilt it", style = MaterialTheme.typography.labelSmall, color = TextDim, modifier = Modifier.padding(bottom = 12.dp))
        Column(Modifier.fillMaxWidth().riseIn(1), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    ((if (flipped) backFace?.typeLine else card.typeLine) ?: ""),
                    style = MaterialTheme.typography.labelLarge,
                    color = TextMuted,
                    modifier = Modifier.weight(1f)
                )
                val manaCost = if (flipped) backFace?.manaCost else card.manaCost
                manaCost?.takeIf { it.isNotBlank() }?.let { ManaCost(it, size = 20.dp) }
            }
            Text(if (flipped) backFace?.name ?: card.name else card.name, style = MaterialTheme.typography.headlineMedium)
            if (card.tags.isNotEmpty()) {
                CardTagsRow(card.tags, modifier = Modifier.padding(top = 2.dp))
            }
            // Both faces' text, always — a flip only changes the art/name/mana cost/type line above.
            if (!card.displayOracleText.isNullOrBlank()) {
                Box(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(20.dp)).background(Surface).padding(16.dp)) {
                    InlineManaText(card.displayOracleText ?: "", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                }
            }
        }
    }
}

/**
 * The card image as a physical object: drag across it and it tilts toward your finger, with a
 * rainbow foil sheen and a glare spot that follow the tilt. Springs back flat when let go.
 */
@Composable
private fun TiltingCard(imageUrl: String?, name: String, modifier: Modifier = Modifier) {
    val rotX = remember { Animatable(0f) }
    val rotY = remember { Animatable(0f) }
    var glare by remember { mutableStateOf(Offset(0.5f, 0.25f)) }
    var dragging by remember { mutableStateOf(false) }
    val holo by animateFloatAsState(if (dragging) 1f else 0f, tween(250), label = "holo")
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(13.dp)
    fun settle() {
        dragging = false
        scope.launch { rotX.animateTo(0f, popSpring()) }
        scope.launch { rotY.animateTo(0f, popSpring()) }
    }
    Box(
        modifier
            .aspectRatio(0.716f)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { settle() },
                    onDragCancel = { settle() },
                    onDrag = { change, _ ->
                        change.consume()
                        val px = (change.position.x / size.width).coerceIn(0f, 1f)
                        val py = (change.position.y / size.height).coerceIn(0f, 1f)
                        glare = Offset(px, py)
                        scope.launch { rotY.snapTo((px - 0.5f) * 28f) }
                        scope.launch { rotX.snapTo((0.5f - py) * 24f) }
                    }
                )
            }
            .graphicsLayer {
                rotationX = rotX.value
                rotationY = rotY.value
                cameraDistance = 14f * density
            }
            .shadow(22.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().sharedArt(SharedKeys.cardArt(name), shape).foilShine()
        )
        Box(
            Modifier.matchParentSize().drawWithContent {
                drawContent()
                if (holo > 0f) {
                    val w = size.width
                    val h = size.height
                    val shift = glare.x * w
                    drawRect(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFFFF5ABE).copy(alpha = 0.28f * holo),
                                Color(0xFF5AD2FF).copy(alpha = 0.32f * holo),
                                Color(0xFFFFEB78).copy(alpha = 0.26f * holo),
                                Color.Transparent
                            ),
                            start = Offset(shift - w, 0f),
                            end = Offset(shift + w * 0.4f, h)
                        ),
                        blendMode = BlendMode.Screen
                    )
                    drawRect(
                        Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.45f * holo), Color.Transparent),
                            center = Offset(glare.x * w, glare.y * h),
                            radius = w * 0.55f
                        ),
                        blendMode = BlendMode.Screen
                    )
                }
            }
        )
    }
}

@Composable
private fun CollectionAndDeckActions(
    onAddToCollection: () -> Unit,
    onAddToDeck: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Button(
            onClick = onAddToDeck,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = OnGold),
            contentPadding = PaddingValues(vertical = 14.dp),
            modifier = Modifier.weight(1.3f)
        ) {
            Icon(Icons.Filled.Style, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add to deck", style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp))
        }
        Button(
            onClick = onAddToCollection,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Surface2, contentColor = TextPrimary),
            contentPadding = PaddingValues(vertical = 14.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text("Add to binder", style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp))
        }
    }
}

/** Legendary creatures can be viewed as a commander (build-around recs) or as a regular card (inclusion recs). */
@Composable
private fun CommanderViewToggle(asCommander: Boolean, onChange: (Boolean) -> Unit) {
    Column {
        SectionHeader("EDHREC recommendations")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillChip("As commander", asCommander, onClick = { onChange(true) })
            PillChip("As a card", !asCommander, onClick = { onChange(false) })
        }
    }
}

@Composable
private fun PricesSection(state: CardDetailUiState, onOpenTcgplayer: () -> Unit) {
    GoldPanel {
        val prices = state.card?.prices
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            // TCGplayer's prices in the chosen currency; Cardmarket's are euros already.
            val money = rememberMoney()
            prices?.usd?.toDoubleOrNull()?.let { PriceTile("TCGplayer", it) { v -> money.format(v) } }
            prices?.usdFoil?.toDoubleOrNull()?.let { PriceTile("Foil", it) { v -> money.format(v) } }
            prices?.eur?.toDoubleOrNull()?.let { PriceTile("Cardmarket", it) { v -> "€" + "%,.2f".format(v) } }
        }
        Button(
            onClick = onOpenTcgplayer,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Surface2, contentColor = TextPrimary),
            modifier = Modifier.padding(top = 14.dp)
        ) {
            Text("View on TCGplayer", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun PriceTile(label: String, value: Double, format: (Double) -> String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextMuted)
        CountUpText(value, NumberStyle(34), TextPrimary, format = format)
    }
}

@Composable
private fun CombosSection(state: CardDetailUiState) {
    when {
        state.combosLoading -> GoldPanel { CircularProgressIndicator(color = Gold) }
        state.combos.isEmpty() -> GoldPanel {
            Text("No known combos using this card.", style = MaterialTheme.typography.bodySmall)
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.combos.take(5).forEach { variant ->
                var showCombo by remember { mutableStateOf(false) }
                ComboSummaryRow(variant, onClick = { showCombo = true })
                if (showCombo) {
                    ComboDetailDialog(combo = variant, onDismiss = { showCombo = false })
                }
            }
        }
    }
}

@Composable
private fun DeckPickerDialog(
    decks: List<Deck>,
    onDismiss: () -> Unit,
    onPickDeck: (String) -> Unit,
    onConsiderDeck: (String) -> Unit,
    onCreateDeck: (String) -> Unit
) {
    var newDeckName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Add to deck", color = GoldLight, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                if (decks.isNotEmpty()) {
                    Text(
                        "Tap a deck to add the card, or CONSIDER to put it on that deck's Considering list.",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                decks.forEach { deck ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            deck.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onPickDeck(deck.id) }
                                .padding(vertical = 10.dp)
                        )
                        TextButton(onClick = { onConsiderDeck(deck.id) }) {
                            Text("Consider", color = Gold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                if (decks.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(BorderColor))
                }
                OutlinedTextField(
                    value = newDeckName,
                    onValueChange = { newDeckName = it },
                    label = { Text("New deck name", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Gold,
                        unfocusedBorderColor = BorderColor,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = Gold
                    ),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (newDeckName.isNotBlank()) onCreateDeck(newDeckName.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
            ) { Text("Create & add", color = Bg) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        }
    )
}
