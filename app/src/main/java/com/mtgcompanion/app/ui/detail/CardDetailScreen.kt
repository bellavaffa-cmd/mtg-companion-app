package com.mtgcompanion.app.ui.detail

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
import androidx.compose.material3.LargeTopAppBar
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
import com.mtgcompanion.app.ui.common.ManaCost
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

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
            LargeTopAppBar(
                title = { Text(state.card?.name ?: "Card", color = GoldLight, style = MaterialTheme.typography.labelLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Gold)
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = Bg, scrolledContainerColor = Surface),
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

                LazyVerticalGrid(
                    // Fixed column count from the shared grid-size setting, same as every other tab.
                    columns = GridCells.Fixed(gridColumns),
                    modifier = Modifier.fillMaxSize().background(Bg).padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
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
                    fullSpanItem { SectionHeader("Similar Cards") }
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = TextMuted) } }
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
                    label = { Text("New binder name", color = GoldDim) },
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
            ) { Text("CREATE & ADD", color = Bg) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", color = TextMuted) }
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
            modifier = Modifier
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
            modifier = Modifier
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
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)) {
        Text(text.uppercase(), style = MaterialTheme.typography.titleMedium)
        Box(
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f)
                .height(1.dp)
                .background(BorderColor)
        )
    }
}

@Composable
private fun GoldPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
            .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(10.dp))
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
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Gold else BorderColor),
                                RoundedCornerShape(14.dp)
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
    // Which face's art/name/mana cost/type line to show — resets if the printing changes underneat.
    var showBack by remember(card.id) { mutableStateOf(false) }
    val backFace = card.cardFaces?.getOrNull(1)
    val flipped = showBack && card.backImageUrl != null
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(modifier = Modifier.weight(1f)) {
            AsyncImage(
                model = if (flipped) card.backImageUrl else card.displayImageUrl,
                contentDescription = card.name,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            )
            if (card.backImageUrl != null) {
                IconButton(
                    onClick = { showBack = !showBack },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                ) {
                    Icon(Icons.Filled.Autorenew, contentDescription = "Flip card", tint = Gold)
                }
            }
        }
        Column(modifier = Modifier.weight(1.4f)) {
            Text(if (flipped) backFace?.name ?: card.name else card.name, style = MaterialTheme.typography.titleLarge)
            val manaCost = if (flipped) backFace?.manaCost else card.manaCost
            manaCost?.takeIf { it.isNotBlank() }?.let {
                ManaCost(it, size = 18.dp, modifier = Modifier.padding(vertical = 6.dp))
            }
            Text(
                ((if (flipped) backFace?.typeLine else card.typeLine) ?: "").uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted
            )
            if (card.tags.isNotEmpty()) {
                CardTagsRow(card.tags, modifier = Modifier.padding(top = 8.dp))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .height(1.dp)
                    .background(Brush.horizontalGradient(listOf(BorderColor, Bg)))
            )
            // Both faces' text, always — a flip only changes the art/name/mana cost/type line above.
            Text(card.displayOracleText ?: "", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CollectionAndDeckActions(
    onAddToCollection: () -> Unit,
    onAddToDeck: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Button(
            onClick = { menuOpen = true },
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg),
            modifier = Modifier.fillMaxWidth()
        ) { Text("ADD TO…", style = MaterialTheme.typography.labelLarge, color = Bg) }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            modifier = Modifier.background(Surface)
        ) {
            DropdownMenuItem(
                text = { Text("Binder", style = MaterialTheme.typography.bodyMedium, color = TextPrimary) },
                onClick = { menuOpen = false; onAddToCollection() }
            )
            DropdownMenuItem(
                text = { Text("Deck", style = MaterialTheme.typography.bodyMedium, color = TextPrimary) },
                onClick = { menuOpen = false; onAddToDeck() }
            )
        }
    }
}

/** Legendary creatures can be viewed as a commander (build-around recs) or as a regular card (inclusion recs). */
@Composable
private fun CommanderViewToggle(asCommander: Boolean, onChange: (Boolean) -> Unit) {
    Column {
        SectionHeader("EDHREC Recommendations")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = asCommander,
                onClick = { onChange(true) },
                label = { Text("As Commander") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Gold, selectedLabelColor = Bg)
            )
            FilterChip(
                selected = !asCommander,
                onClick = { onChange(false) },
                label = { Text("As a Card") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Gold, selectedLabelColor = Bg)
            )
        }
    }
}

@Composable
private fun PricesSection(state: CardDetailUiState, onOpenTcgplayer: () -> Unit) {
    GoldPanel {
        val prices = state.card?.prices
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            prices?.usd?.let { PriceTile("USD", "$$it") }
            prices?.usdFoil?.let { PriceTile("USD Foil", "$$it") }
            prices?.eur?.let { PriceTile("EUR", "€$it") }
        }
        if (state.tcgPricesConfigured && state.tcgPrices != null) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).height(1.dp).background(BorderColor))
            Text("TCGPLAYER LIVE MARKET", style = MaterialTheme.typography.labelMedium, color = TextDim)
            state.tcgPrices.forEach { result ->
                Text(
                    "${result.subTypeName ?: "Normal"}: market $${result.marketPrice ?: "-"} " +
                        "(low $${result.lowPrice ?: "-"} / high $${result.highPrice ?: "-"})",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        Button(
            onClick = onOpenTcgplayer,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg),
            modifier = Modifier.padding(top = 14.dp)
        ) {
            Text("VIEW ON TCGPLAYER", style = MaterialTheme.typography.labelLarge, color = Bg)
        }
    }
}

@Composable
private fun PriceTile(label: String, value: String) {
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = TextDim)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = GoldLight)
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
                ComboRow(variant, onClick = { showCombo = true })
                if (showCombo) {
                    ComboDetailDialog(combo = variant, onDismiss = { showCombo = false })
                }
            }
        }
    }
}

@Composable
private fun ComboRow(variant: Variant, onClick: () -> Unit) {
    GoldPanel(modifier = Modifier.clickable(onClick = onClick)) {
        Text(
            variant.uses.joinToString(" + ") { it.card.name },
            style = MaterialTheme.typography.bodyMedium,
            color = GoldLight
        )
        Text(
            "Produces: " + variant.produces.joinToString(", ") { it.feature.name },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun DeckPickerDialog(
    decks: List<Deck>,
    onDismiss: () -> Unit,
    onPickDeck: (String) -> Unit,
    onCreateDeck: (String) -> Unit
) {
    var newDeckName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Add to deck", color = GoldLight, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                decks.forEach { deck ->
                    Text(
                        deck.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPickDeck(deck.id) }
                            .padding(vertical = 10.dp)
                    )
                }
                if (decks.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(BorderColor))
                }
                OutlinedTextField(
                    value = newDeckName,
                    onValueChange = { newDeckName = it },
                    label = { Text("New deck name", color = GoldDim) },
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
            ) { Text("CREATE & ADD", color = Bg) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", color = TextMuted) }
        }
    )
}

private fun openUrl(context: android.content.Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
