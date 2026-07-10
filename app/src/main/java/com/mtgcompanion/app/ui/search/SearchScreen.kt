package com.mtgcompanion.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgcompanion.app.network.scryfall.ScryfallCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onCardClick: (ScryfallCard) -> Unit,
    onSettingsClick: () -> Unit
) {
    val query by viewModel.query.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MTG Companion") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::onQueryChange,
                    label = { Text("Search cards (Scryfall syntax)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = viewModel::search) {
                    Icon(Icons.Filled.Search, contentDescription = "Search")
                }
            }

            when (val state = uiState) {
                is SearchUiState.Idle -> Text(
                    "Try a card name, or Scryfall syntax like \"is:commander c:g\".",
                    modifier = Modifier.padding(top = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                is SearchUiState.Loading -> Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) { CircularProgressIndicator() }
                is SearchUiState.Error -> Text(
                    state.message,
                    modifier = Modifier.padding(top = 24.dp),
                    color = MaterialTheme.colorScheme.error
                )
                is SearchUiState.Success -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    items(state.cards, key = { it.id }) { card ->
                        CardResultRow(card = card, onClick = { onCardClick(card) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CardResultRow(card: ScryfallCard, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(card.name) },
        supportingContent = {
            Text(
                card.typeLine ?: "",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            AsyncImage(
                model = card.displayImageUrl,
                contentDescription = card.name,
                modifier = Modifier.size(width = 40.dp, height = 56.dp)
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
}
