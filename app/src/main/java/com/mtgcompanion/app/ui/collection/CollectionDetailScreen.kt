package com.mtgcompanion.app.ui.collection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.CollectionEntry
import com.mtgcompanion.app.ui.common.AlternateArtDialog
import com.mtgcompanion.app.ui.common.CardZoomDialog
import com.mtgcompanion.app.ui.common.ZoomCard
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
fun CollectionDetailScreen(
    viewModel: CollectionDetailViewModel,
    onBack: () -> Unit
) {
    val collection by viewModel.collection.collectAsState()
    val entries by viewModel.entries.collectAsState()
    val query by viewModel.query.collectAsState()
    val dashboard by viewModel.dashboard.collectAsState()
    val prices by viewModel.prices.collectAsState()
    // Tapping a card enlarges it (swipeable), showing value/total and a quantity stepper.
    var zoomId by remember { mutableStateOf<String?>(null) }
    // Alternate-art target while the printing picker is open: (current scryfallId, card name).
    var artTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text(collection?.name ?: "Binder", color = GoldLight, style = MaterialTheme.typography.labelLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Gold)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.deleteCollection(onBack) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete binder", tint = TextDim)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().background(Bg).padding(padding)) {
            if (collection?.entries?.isNotEmpty() == true) {
                Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
                    DashboardPanel(dashboard)
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                label = { Text("Search this binder", color = GoldDim) },
                singleLine = true,
                shape = RoundedCornerShape(2.dp),
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
            )

            val total = collection?.entries?.sumOf { it.quantity + it.foilQuantity } ?: 0
            when {
                collection?.entries.isNullOrEmpty() -> Text(
                    "No cards yet. Add cards from a card's detail page or the scanner.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                entries.isEmpty() -> Text(
                    "No cards match \"$query\".",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(
                            "$total cards · ${collection?.entries?.size ?: 0} unique",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    items(entries, key = { it.scryfallId }) { entry ->
                        CollectionCardRow(
                            entry = entry,
                            onClick = { zoomId = entry.scryfallId },
                            onQuantityChange = { qty, foil -> viewModel.setQuantity(entry, qty, foil) },
                            onRemove = { viewModel.remove(entry) }
                        )
                    }
                }
            }
        }
    }

    zoomId?.let { id ->
        val zoomCards = entries.map { entry ->
            ZoomCard(
                imageUrl = entry.imageUrl,
                priceUsd = prices[entry.scryfallId],
                quantity = entry.quantity,
                onIncrement = { viewModel.setQuantity(entry, entry.quantity + 1, entry.foilQuantity) },
                onDecrement = { viewModel.setQuantity(entry, (entry.quantity - 1).coerceAtLeast(0), entry.foilQuantity) },
                onChangeArt = { artTarget = entry.scryfallId to entry.name }
            )
        }
        CardZoomDialog(zoomCards, entries.indexOfFirst { it.scryfallId == id }.coerceAtLeast(0)) { zoomId = null }
    }

    artTarget?.let { (id, name) ->
        AlternateArtDialog(name, onSelect = { viewModel.changePrinting(id, it) }, onDismiss = { artTarget = null })
    }
}

@Composable
private fun CollectionCardRow(
    entry: CollectionEntry,
    onClick: () -> Unit,
    onQuantityChange: (Int, Int) -> Unit,
    onRemove: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Surface)
            .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        AsyncImage(
            model = entry.imageUrl,
            contentDescription = entry.name,
            modifier = Modifier.size(width = 48.dp, height = 67.dp).clip(RoundedCornerShape(3.dp))
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text(
                "Normal: ${entry.quantity}" + if (entry.foilQuantity > 0) " · Foil: ${entry.foilQuantity}" else "",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onQuantityChange((entry.quantity - 1).coerceAtLeast(0), entry.foilQuantity) }) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease quantity", tint = Gold)
            }
            Text("${entry.quantity}", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            IconButton(onClick = { onQuantityChange(entry.quantity + 1, entry.foilQuantity) }) {
                Icon(Icons.Filled.Add, contentDescription = "Increase quantity", tint = Gold)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Remove from binder", tint = TextDim)
            }
        }
    }
}
