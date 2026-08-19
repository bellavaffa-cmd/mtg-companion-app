package com.mtgcompanion.app.ui.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckCardEntry
import com.mtgcompanion.app.ui.common.CardZoomDialog
import com.mtgcompanion.app.ui.common.ZoomCard
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.TextDim
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary

/** One physical copy in the simulated library — [instanceId] distinguishes multiple copies of the same card. */
private data class LibraryCard(val instanceId: String, val entry: DeckCardEntry)

private fun shuffledLibrary(deck: Deck): List<LibraryCard> =
    deck.cards.flatMap { entry -> (0 until entry.quantity).map { i -> LibraryCard("${entry.scryfallId}#$i", entry) } }
        .shuffled()

/**
 * Solo playtesting: shuffles this deck's cards (the commander stays in the command zone, same as a
 * real game, since it isn't part of [Deck.cards]), draws an opening hand, and lets you draw one card
 * at a time to see how the deck's mana/curve plays out — no persistence, resets every time it's opened.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoldfishDialog(deck: Deck, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        if (deck.cards.isEmpty()) {
            Scaffold(containerColor = Bg, topBar = { GoldfishTopBar(0, onDismiss) }) { padding ->
                Column(modifier = Modifier.fillMaxSize().background(Bg).padding(padding).padding(20.dp)) {
                    Text("Add cards to this deck before playtesting.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
            return@Dialog
        }

        var library by remember { mutableStateOf(shuffledLibrary(deck)) }
        var hand by remember { mutableStateOf(library.take(7)) }
        var remaining by remember { mutableStateOf(library.drop(7)) }
        var zoomIndex by remember { mutableStateOf<Int?>(null) }

        fun newHand() {
            library = shuffledLibrary(deck)
            hand = library.take(7)
            remaining = library.drop(7)
        }

        Scaffold(
            containerColor = Bg,
            topBar = { GoldfishTopBar(remaining.size, onDismiss) },
            bottomBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().background(Bg).padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(onClick = { newHand() }, modifier = Modifier.weight(1f)) {
                        Text("NEW HAND", color = Gold)
                    }
                    Button(
                        onClick = { remaining.firstOrNull()?.let { card -> hand = hand + card; remaining = remaining.drop(1) } },
                        enabled = remaining.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg),
                        modifier = Modifier.weight(1f)
                    ) { Text("DRAW", color = Bg) }
                }
            }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().background(Bg).padding(padding)) {
                Text(
                    "HAND (${hand.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
                )
                if (hand.isEmpty()) {
                    Text(
                        "No cards drawn yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDim,
                        modifier = Modifier.padding(20.dp)
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        hand.forEachIndexed { index, card ->
                            AsyncImage(
                                model = card.entry.imageUrl,
                                contentDescription = card.entry.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .width(110.dp)
                                    .aspectRatio(0.72f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { zoomIndex = index }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }

        zoomIndex?.let { index ->
            val zoomCards = hand.map { card ->
                ZoomCard(imageUrl = card.entry.imageUrl, cardName = card.entry.name, backImageUrl = card.entry.backImageUrl)
            }
            CardZoomDialog(zoomCards, index) { zoomIndex = null }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoldfishTopBar(libraryCount: Int, onDismiss: () -> Unit) {
    TopAppBar(
        title = {
            Column {
                Text("GOLDFISH", color = GoldLight, style = MaterialTheme.typography.labelLarge)
                Text("Library: $libraryCount", color = TextPrimary, style = MaterialTheme.typography.labelMedium)
            }
        },
        navigationIcon = {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Gold)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
    )
}
