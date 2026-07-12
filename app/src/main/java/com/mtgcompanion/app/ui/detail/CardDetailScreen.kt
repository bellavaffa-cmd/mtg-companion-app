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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.ui.common.ManaCost
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
import kotlinx.coroutines.delay

private const val GRID_COLUMNS = 3
private const val TILES_PER_SECTION = 12

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(
    viewModel: CardDetailViewModel,
    onBack: () -> Unit,
    onCardClick: (String) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val decks by viewModel.decks.collectAsState()
    val collections by viewModel.collections.collectAsState()
    val context = LocalContext.current
    var showDeckPicker by remember { mutableStateOf(false) }
    var showCollectionPicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.addedToCollectionMessage, state.addedToDeckMessage) {
        if (state.addedToCollectionMessage != null || state.addedToDeckMessage != null) {
            delay(2000)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text(state.card?.name ?: "Card", color = GoldLight, style = MaterialTheme.typography.labelLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Gold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
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
                val sections = state.cardEdhrecLists?.filter { it.cardviews.isNotEmpty() }.orEmpty()

                LazyVerticalGrid(
                    columns = GridCells.Fixed(GRID_COLUMNS),
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
                            state = state,
                            onAddToCollection = { showCollectionPicker = true },
                            onAddToDeck = { showDeckPicker = true }
                        )
                    }
                    fullSpanItem { PricesSection(state, onOpenTcgplayer = {
                        card.purchaseUris?.tcgplayer?.let { openUrl(context, it) }
                    }) }

                    if (state.cardEdhrecLoading) {
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
                        sections.forEach { section -> edhrecSection(section, onCardClick) }
                    }

                    fullSpanItem { SectionHeader("Combos · Commander Spellbook") }
                    fullSpanItem { CombosSection(state) }
                }
            }
        }
    }

    if (showDeckPicker) {
        DeckPickerDialog(
            decks = decks,
            onDismiss = { showDeckPicker = false },
            onPickDeck = { deckId ->
                showDeckPicker = false
                viewModel.addToDeck(deckId)
            },
            onCreateDeck = { name ->
                showDeckPicker = false
                viewModel.createDeckAndAdd(name)
            }
        )
    }

    if (showCollectionPicker) {
        CollectionPickerDialog(
            collections = collections,
            onDismiss = { showCollectionPicker = false },
            onPickCollection = { collectionId ->
                showCollectionPicker = false
                viewModel.addToCollection(collectionId)
            },
            onCreateCollection = { name ->
                showCollectionPicker = false
                viewModel.createCollectionAndAdd(name)
            }
        )
    }
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

/** One EDHREC section: a full-width header followed by a grid of card tiles. */
private fun androidx.compose.foundation.lazy.grid.LazyGridScope.edhrecSection(
    section: EdhrecCardList,
    onCardClick: (String) -> Unit
) {
    fullSpanItem { SectionHeader(section.header ?: "") }
    items(section.cardviews.take(TILES_PER_SECTION), key = { "${section.tag}-${it.id ?: it.name}" }) { view ->
        EdhrecTile(view, onClick = { onCardClick(view.name) })
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
                .clip(RoundedCornerShape(6.dp))
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
private fun GoldPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Surface)
            .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(4.dp))
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
                                BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Gold else BorderColor),
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
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        AsyncImage(
            model = card.displayImageUrl,
            contentDescription = card.name,
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
        )
        Column(modifier = Modifier.weight(1.4f)) {
            Text(card.name, style = MaterialTheme.typography.titleLarge)
            card.manaCost?.takeIf { it.isNotBlank() }?.let {
                ManaCost(it, size = 18.dp, modifier = Modifier.padding(vertical = 6.dp))
            }
            Text(
                (card.typeLine ?: "").uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .height(1.dp)
                    .background(Brush.horizontalGradient(listOf(BorderColor, Bg)))
            )
            Text(card.displayOracleText ?: "", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CollectionAndDeckActions(
    state: CardDetailUiState,
    onAddToCollection: () -> Unit,
    onAddToDeck: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column {
        Box {
            Button(
                onClick = { menuOpen = true },
                shape = RoundedCornerShape(2.dp),
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
        (state.addedToCollectionMessage ?: state.addedToDeckMessage)?.let {
            Text(it, color = Gold, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
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
            shape = RoundedCornerShape(2.dp),
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
            state.combos.take(5).forEach { variant -> ComboRow(variant) }
        }
    }
}

@Composable
private fun ComboRow(variant: Variant) {
    GoldPanel {
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
