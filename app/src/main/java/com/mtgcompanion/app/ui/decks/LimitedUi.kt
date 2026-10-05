package com.mtgcompanion.app.ui.decks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.BASIC_LAND_FOR
import com.mtgcompanion.app.data.DeckCardEntry
import com.mtgcompanion.app.data.basicLandSplit
import com.mtgcompanion.app.data.basicsWanted
import com.mtgcompanion.app.data.mainDeckPips
import com.mtgcompanion.app.data.strongestPairs
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.ui.common.ManaPips
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.NumberStyle
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary

// The draft and sealed parts of the deck screen (data/Limited.kt): the pool's strongest colour
// pairs, and "Add basic lands". The web app's components/LimitedPanels.tsx shows the same.

private val COLOURS = listOf("W", "U", "B", "R", "G")

/** One line over the pool: the three colour pairs with the most playable cards. */
@Composable
internal fun PoolPairsHint(pool: List<DeckCardEntry>, cards: Map<String, ScryfallCard>, modifier: Modifier = Modifier) {
    val pairs = strongestPairs(pool, cards)
    if (pairs.isEmpty()) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        Text("Strongest pairs", style = MaterialTheme.typography.labelMedium, color = TextMuted)
        pairs.forEach { pair ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ManaPips(pair.colours.map { it.toString() }, size = 16.dp)
                Text("${pair.count}", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
            }
        }
    }
}

/**
 * "Add basic lands": 17 lands for 40 cards (less the lands already in), split by the coloured mana
 * symbols in the main deck. Every count can be changed before they go into the main deck.
 */
@Composable
internal fun BasicLandsDialog(
    mainDeck: List<DeckCardEntry>,
    cards: Map<String, ScryfallCard>,
    onAdd: (Map<String, Int>) -> Unit,
    onDismiss: () -> Unit
) {
    var counts by remember { mutableStateOf(basicLandSplit(mainDeckPips(mainDeck, cards), basicsWanted(mainDeck, cards))) }
    val suggested = remember { counts.isNotEmpty() }
    val total = COLOURS.sumOf { counts[it] ?: 0 }
    fun step(colour: String, by: Int) {
        counts = counts + (colour to ((counts[colour] ?: 0) + by).coerceAtLeast(0))
    }
    AlertDialog(
        containerColor = Surface,
        onDismissRequest = onDismiss,
        title = { Text("Add basic lands", color = GoldLight) },
        text = {
            Column {
                Text(
                    if (suggested) "Split by the coloured mana symbols in your main deck — 17 lands for 40 cards. Change any count before adding them."
                    else "No coloured mana symbols in the main deck yet. Move your picks in from the pool first, or choose the lands yourself.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                COLOURS.forEach { colour ->
                    val land = BASIC_LAND_FOR.getValue(colour)
                    val count = counts[colour] ?: 0
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        ManaPips(listOf(colour), size = 18.dp)
                        Text(land, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, modifier = Modifier.padding(start = 10.dp).weight(1f))
                        IconButton(onClick = { step(colour, -1) }, enabled = count > 0, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Remove, contentDescription = "One $land fewer", tint = TextMuted, modifier = Modifier.size(18.dp))
                        }
                        Text("$count", style = NumberStyle(22), color = TextPrimary, textAlign = TextAlign.Center, modifier = Modifier.width(32.dp))
                        IconButton(onClick = { step(colour, 1) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Add, contentDescription = "One $land more", tint = TextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(counts) },
                enabled = total > 0,
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
            ) { Text("Add $total ${if (total == 1) "land" else "lands"}", color = Bg) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        }
    )
}
