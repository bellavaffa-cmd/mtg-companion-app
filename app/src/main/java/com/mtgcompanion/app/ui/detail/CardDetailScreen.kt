package com.mtgcompanion.app.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.Deck
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(
    viewModel: CardDetailViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val decks by viewModel.decks.collectAsState()
    val context = LocalContext.current
    var showDeckPicker by remember { mutableStateOf(false) }

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
                LazyColumn(
                    modifier = Modifier.fillMaxSize().background(Bg).padding(padding),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    item { CardHeader(card) }
                    item {
                        CollectionAndDeckActions(
                            state = state,
                            onAddToCollection = viewModel::addToCollection,
                            onAddToDeck = { showDeckPicker = true }
                        )
                    }
                    item {
                        Column {
                            SectionLabel("Prices")
                            PricesSection(state, onOpenTcgplayer = {
                                card.purchaseUris?.tcgplayer?.let { openUrl(context, it) }
                            })
                        }
                    }
                    if (card.canBeCommander) {
                        item {
                            Column {
                                SectionLabel("EDHREC Recommendations")
                                EdhrecSection(state)
                            }
                        }
                    }
                    item {
                        Column {
                            SectionLabel("EDHREC · Top Commanders")
                            EdhrecCommandersSection(state)
                        }
                    }
                    item {
                        Column {
                            SectionLabel("EDHREC · Often Played With")
                            EdhrecSynergySection(state)
                        }
                    }
                    item {
                        Column {
                            SectionLabel("Combos · Commander Spellbook")
                            CombosSection(state)
                        }
                    }
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
}

@Composable
private fun CollectionAndDeckActions(
    state: CardDetailUiState,
    onAddToCollection: () -> Unit,
    onAddToDeck: () -> Unit
) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onAddToCollection,
                shape = RoundedCornerShape(2.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
            ) { Text("ADD TO COLLECTION", style = MaterialTheme.typography.labelLarge, color = Bg) }
            OutlinedButton(
                onClick = onAddToDeck,
                shape = RoundedCornerShape(2.dp),
                border = BorderStroke(1.dp, BorderColor),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldLight)
            ) { Text("ADD TO DECK", style = MaterialTheme.typography.labelLarge) }
        }
        (state.addedToCollectionMessage ?: state.addedToDeckMessage)?.let {
            Text(it, color = Gold, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
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

@Composable
private fun SectionLabel(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 14.dp)) {
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
private fun CardHeader(card: ScryfallCard) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        AsyncImage(
            model = card.displayImageUrl,
            contentDescription = card.name,
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
        )
        Column(modifier = Modifier.weight(1.4f)) {
            Text(card.name, style = MaterialTheme.typography.titleLarge)
            card.manaCost?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted, modifier = Modifier.padding(top = 6.dp))
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
        } else {
            Text(
                "Add a TCGPlayer API key in Settings for live marketplace pricing.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp)
            )
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
private fun EdhrecSection(state: CardDetailUiState) {
    GoldPanel {
        when {
            state.edhrecLoading -> CircularProgressIndicator(color = Gold)
            state.edhrecLists == null -> Text(
                "No EDHREC data found for this commander.",
                style = MaterialTheme.typography.bodySmall
            )
            else -> {
                val topCards = state.edhrecLists.firstOrNull { it.tag == "topcards" }
                    ?: state.edhrecLists.firstOrNull()
                topCards?.cardviews?.take(10)?.forEachIndexed { index, view ->
                    if (index > 0) {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(BorderColor))
                    }
                    Text(
                        "${view.name} — played in ${view.numDecks ?: 0} decks",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun EdhrecCommandersSection(state: CardDetailUiState) {
    GoldPanel {
        when {
            state.cardEdhrecLoading -> CircularProgressIndicator(color = Gold)
            state.cardEdhrecLists == null -> Text(
                "No EDHREC data found for this card.",
                style = MaterialTheme.typography.bodySmall
            )
            else -> {
                val commanders = state.cardEdhrecLists.firstOrNull { it.tag == "topcommanders" }
                    ?: state.cardEdhrecLists.firstOrNull { it.header?.contains("Commander", ignoreCase = true) == true }
                val views = commanders?.cardviews?.take(10).orEmpty()
                if (views.isEmpty()) {
                    Text("No commander data for this card.", style = MaterialTheme.typography.bodySmall)
                } else {
                    views.forEachIndexed { index, view ->
                        if (index > 0) {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(BorderColor))
                        }
                        Text(
                            "${view.name} — runs it in ${view.numDecks ?: 0} decks",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EdhrecSynergySection(state: CardDetailUiState) {
    GoldPanel {
        when {
            state.cardEdhrecLoading -> CircularProgressIndicator(color = Gold)
            state.cardEdhrecLists == null -> Text(
                "No EDHREC data found for this card.",
                style = MaterialTheme.typography.bodySmall
            )
            else -> {
                // Prefer high-synergy ("lift") cards; fall back to raw top cards.
                val synergy = state.cardEdhrecLists.firstOrNull { it.tag == "highliftcards" }
                    ?: state.cardEdhrecLists.firstOrNull { it.tag == "topcards" }
                val views = synergy?.cardviews?.take(10).orEmpty()
                if (views.isEmpty()) {
                    Text("No synergy data for this card.", style = MaterialTheme.typography.bodySmall)
                } else {
                    views.forEachIndexed { index, view ->
                        if (index > 0) {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(BorderColor))
                        }
                        Text(
                            "${view.name} — in ${view.numDecks ?: 0} decks",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
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

private fun openUrl(context: android.content.Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
