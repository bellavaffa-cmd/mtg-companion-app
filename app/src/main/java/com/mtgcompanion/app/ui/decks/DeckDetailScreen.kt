package com.mtgcompanion.app.ui.decks

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
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
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckCardEntry
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
fun DeckDetailScreen(
    viewModel: DeckDetailViewModel,
    onBack: () -> Unit,
    onCardClick: (String) -> Unit = {}
) {
    val deck by viewModel.deck.collectAsState()

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text(deck?.name ?: "Deck", color = GoldLight, style = MaterialTheme.typography.labelLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Gold)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.deleteDeck(onBack) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete deck", tint = TextDim)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
            )
        }
    ) { padding ->
        val currentDeck = deck ?: return@Scaffold

        LazyColumn(
            modifier = Modifier.fillMaxSize().background(Bg).padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { CommanderSection(currentDeck, onClick = { currentDeck.commander?.let { onCardClick(it.name) } }) }
            item {
                Text(
                    "CARDS (${currentDeck.cards.sumOf { it.quantity }})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
            }
            if (currentDeck.cards.isEmpty()) {
                item {
                    Text(
                        "No cards yet. Add cards to this deck from a card's detail page.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            items(currentDeck.cards, key = { it.scryfallId }) { card ->
                DeckCardRow(
                    card = card,
                    isCommander = currentDeck.commander?.scryfallId == card.scryfallId,
                    onClick = { onCardClick(card.name) },
                    onToggleCommander = {
                        viewModel.setCommander(if (currentDeck.commander?.scryfallId == card.scryfallId) null else card)
                    },
                    onRemove = { viewModel.removeCard(card.scryfallId) }
                )
            }
        }
    }
}

@Composable
private fun CommanderSection(deck: Deck, onClick: () -> Unit) {
    val commander = deck.commander ?: return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Surface)
            .border(BorderStroke(1.dp, Gold.copy(alpha = 0.5f)), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        AsyncImage(
            model = commander.imageUrl,
            contentDescription = commander.name,
            modifier = Modifier.size(width = 56.dp, height = 78.dp).clip(RoundedCornerShape(3.dp))
        )
        Column {
            Text("COMMANDER", style = MaterialTheme.typography.labelMedium, color = TextDim)
            Text(commander.name, style = MaterialTheme.typography.bodyMedium, color = GoldLight)
        }
    }
}

@Composable
private fun DeckCardRow(
    card: DeckCardEntry,
    isCommander: Boolean,
    onClick: () -> Unit,
    onToggleCommander: () -> Unit,
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
            model = card.imageUrl,
            contentDescription = card.name,
            modifier = Modifier.size(width = 48.dp, height = 67.dp).clip(RoundedCornerShape(3.dp))
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(card.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text("Qty ${card.quantity}", style = MaterialTheme.typography.labelMedium, color = TextMuted)
        }
        if (card.canBeCommander) {
            IconButton(onClick = onToggleCommander) {
                Icon(
                    if (isCommander) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = "Set as commander",
                    tint = Gold
                )
            }
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = "Remove from deck", tint = TextDim)
        }
    }
}
