package com.mtgcompanion.app.ui.collection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.DeckCopyRow
import com.mtgcompanion.app.data.LocationChip
import com.mtgcompanion.app.data.LocationKind
import com.mtgcompanion.app.data.inDecksBadge
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.common.CardActionMenu
import com.mtgcompanion.app.ui.common.CardMenuAction
import com.mtgcompanion.app.ui.common.FlipBadge
import com.mtgcompanion.app.ui.common.zoomSource
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldDim
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.TextDim
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary

/*
 * A binder's deck copies (BinderViews.kt): the copies of a printing that are in decks but sorted into
 * this binder (or, in No binder, into none). They stay in their decks — the row says which ("In
 * Talrand deck", "In 2 decks"), its deck chip opens it, and its menu changes only which binder they
 * show in. The binder's steppers, selling, lending and picking act on its own copies only.
 */

/** One printing's deck copies in a binder. [inNoBinder]: shown in No binder, so there's no binder to take it out of. */
@Composable
fun DeckCopyCardRow(
    row: DeckCopyRow,
    inNoBinder: Boolean,
    onClick: () -> Unit,
    onOpenDeck: (String) -> Unit,
    onMove: () -> Unit,
    onTakeOut: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val decks = row.decks
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
            .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Box {
            AsyncImage(
                model = row.imageUrl.toArtCropUrl(),
                contentDescription = row.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.zoomSource(row.imageUrl).size(width = 72.dp, height = 52.dp).clip(RoundedCornerShape(10.dp))
            )
            if (row.backImageUrl != null) FlipBadge()
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(row.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("×${row.qty}", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                Text(
                    inDecksBadge(decks.map { it.second }),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Gold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(GoldDim.copy(alpha = 0.18f)).padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
            // The deck chip, as a card row anywhere says it: "Talrand deck ×1" — tap to open the deck.
            LocationChips(
                row.copies.groupBy { it.deckId }.map { (id, list) -> LocationChip(LocationKind.DECK, list.sumOf { it.qty }, deck = list.first().deckName, deckId = id) },
                ChipActions(onDeck = onOpenDeck)
            )
        }
        IconButton(onClick = { menu = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Card in a deck: actions", tint = TextDim)
        }
    }
    CardActionMenu(
        expanded = menu,
        onDismiss = { menu = false },
        title = row.name,
        subtitle = "In a deck — this binder only shows it",
        imageUrl = row.imageUrl,
        actions = buildList {
            decks.forEach { (id, name) ->
                add(CardMenuAction(if (decks.size == 1) "Open deck" else "Open $name", Icons.AutoMirrored.Filled.OpenInNew) { menu = false; onOpenDeck(id) })
            }
            add(CardMenuAction(if (inNoBinder) "Put in a binder…" else "Move to another binder…", Icons.AutoMirrored.Filled.DriveFileMove, description = "The card stays in its deck") { menu = false; onMove() })
            if (!inNoBinder) add(CardMenuAction("Take out of binder", Icons.Filled.RemoveCircleOutline, description = "It shows in No binder instead; it stays in its deck") { menu = false; onTakeOut() })
        }
    )
}

/** The binders to pick from, for a deck card's copies: [binders] A to Z. */
@Composable
fun BinderPickDialog(
    title: String,
    binders: List<Collection>,
    note: String? = null,
    /** "Take out of binder": into none (it shows in No binder); null to leave it out. */
    onNone: (() -> Unit)? = null,
    onPick: (Collection) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.padding(bottom = 6.dp))
                if (binders.isEmpty()) Text("No binders yet — make one on the Collection's Binders tab.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                binders.forEach { b ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onPick(b) }.padding(vertical = 10.dp, horizontal = 4.dp)
                    ) {
                        Icon(Icons.Filled.CollectionsBookmark, contentDescription = null, tint = GoldDim, modifier = Modifier.size(20.dp))
                        Text(b.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = { if (onNone != null) TextButton(onClick = onNone) { Text("Take out of binder", color = Gold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } }
    )
}
