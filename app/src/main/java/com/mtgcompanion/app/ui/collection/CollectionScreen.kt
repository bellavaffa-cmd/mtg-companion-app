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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.CollectionEntry
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.TextDim
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    viewModel: CollectionViewModel,
    onCardClick: (String) -> Unit = {}
) {
    val entries by viewModel.entries.collectAsState()

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text("COLLECTION", color = GoldLight, style = MaterialTheme.typography.labelLarge) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
            )
        }
    ) { padding ->
        if (entries.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().background(Bg).padding(padding).padding(20.dp)) {
                Text(
                    "No cards yet. Add cards to your collection from a card's detail page.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().background(Bg).padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "${entries.sumOf { it.quantity + it.foilQuantity }} cards, ${entries.size} unique",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(entries, key = { it.scryfallId }) { entry ->
                CollectionRow(
                    entry = entry,
                    onClick = { onCardClick(entry.name) },
                    onQuantityChange = { qty, foil -> viewModel.setQuantity(entry, qty, foil) },
                    onRemove = { viewModel.remove(entry) }
                )
            }
        }
    }
}

@Composable
private fun CollectionRow(
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
                Icon(Icons.Filled.Close, contentDescription = "Remove from collection", tint = TextDim)
            }
        }
    }
}
