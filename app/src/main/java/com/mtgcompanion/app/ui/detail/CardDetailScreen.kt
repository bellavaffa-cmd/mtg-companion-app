package com.mtgcompanion.app.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.spellbook.Variant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(
    viewModel: CardDetailViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.card?.name ?: "Card") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.loading -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) { CircularProgressIndicator() }

            state.error != null -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
            ) { Text(state.error ?: "", color = MaterialTheme.colorScheme.error) }

            state.card != null -> {
                val card = state.card!!
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { CardHeader(card) }
                    item { PricesSection(state, onOpenTcgplayer = {
                        card.purchaseUris?.tcgplayer?.let { openUrl(context, it) }
                    }) }
                    if (card.canBeCommander) {
                        item { EdhrecSection(state) }
                    }
                    item { CombosSection(state) }
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
            modifier = Modifier.weight(1f)
        )
        Column(modifier = Modifier.weight(1.4f)) {
            Text(card.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            card.manaCost?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(card.typeLine ?: "", style = MaterialTheme.typography.bodyMedium)
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Text(card.displayOracleText ?: "", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PricesSection(state: CardDetailUiState, onOpenTcgplayer: () -> Unit) {
    Column {
        Text("Prices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        val prices = state.card?.prices
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(top = 8.dp)) {
            prices?.usd?.let { PriceTile("USD", "$$it") }
            prices?.usdFoil?.let { PriceTile("USD Foil", "$$it") }
            prices?.eur?.let { PriceTile("EUR", "€$it") }
        }
        if (state.tcgPricesConfigured && state.tcgPrices != null) {
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Text("TCGPlayer live market", style = MaterialTheme.typography.labelLarge)
            state.tcgPrices.forEach { result ->
                Text(
                    "${result.subTypeName ?: "Normal"}: market $${result.marketPrice ?: "-"} " +
                        "(low $${result.lowPrice ?: "-"} / high $${result.highPrice ?: "-"})",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            Text(
                "Add a TCGPlayer API key in Settings for live marketplace pricing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Button(onClick = onOpenTcgplayer, modifier = Modifier.padding(top = 8.dp)) {
            Text("View on TCGPlayer")
        }
    }
}

@Composable
private fun PriceTile(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EdhrecSection(state: CardDetailUiState) {
    Column {
        Text("EDHREC recommendations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        when {
            state.edhrecLoading -> CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
            state.edhrecLists == null -> Text(
                "No EDHREC data found for this commander.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            else -> {
                val topCards = state.edhrecLists.firstOrNull { it.tag == "topcards" }
                    ?: state.edhrecLists.firstOrNull()
                topCards?.cardviews?.take(10)?.forEach { view ->
                    Text(
                        "${view.name} — played in ${view.numDecks ?: 0} decks",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CombosSection(state: CardDetailUiState) {
    Column {
        Text("Combos (Commander Spellbook)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        when {
            state.combosLoading -> CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
            state.combos.isEmpty() -> Text(
                "No known combos using this card.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            else -> state.combos.take(5).forEach { variant -> ComboRow(variant) }
        }
    }
}

@Composable
private fun ComboRow(variant: Variant) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            variant.uses.joinToString(" + ") { it.card.name },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Produces: " + variant.produces.joinToString(", ") { it.feature.name },
            style = MaterialTheme.typography.bodySmall
        )
        Divider(modifier = Modifier.padding(top = 8.dp))
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
