package com.mtgcompanion.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Style
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.PlaceKind
import com.mtgcompanion.app.data.WhereKind
import com.mtgcompanion.app.data.WhereLine
import com.mtgcompanion.app.data.CardFacts
import com.mtgcompanion.app.data.CopyHistoryStore
import com.mtgcompanion.app.data.MoveCard
import com.mtgcompanion.app.data.MoveSpot
import com.mtgcompanion.app.data.movedMove
import com.mtgcompanion.app.data.putAwayMove
import com.mtgcompanion.app.data.cardFactsOf
import com.mtgcompanion.app.data.moveCopies
import com.mtgcompanion.app.data.placeTree
import com.mtgcompanion.app.data.placeUnplaced
import com.mtgcompanion.app.data.placesOf
import com.mtgcompanion.app.data.suggestSpot
import com.mtgcompanion.app.data.whereItIs
import com.mtgcompanion.app.data.sellCountsByName
import com.mtgcompanion.app.data.setForSaleByName
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.ui.collection.PlacePickerDialog
import com.mtgcompanion.app.ui.collection.StorageChange
import com.mtgcompanion.app.ui.collection.placeIcon
import com.mtgcompanion.app.ui.theme.LocalAppColors

/*
 * "Where it is" on a card's page: every copy of the card, by where it's physically kept — a place
 * (and its section or pocket), a deck box, lent out (a loan each), or no place yet — with "Move a
 * copy", "Give it a place", "Lend" (LendScreen.kt) and "History" (CopyHistoryScreen.kt). The logic is whereItIs() in data/StoragePlaces.kt. Mirrors the web app's
 * src/collection/WhereItIs.tsx.
 */

@Composable
fun WhereItIsPanel(
    name: String,
    card: ScryfallCard?,
    collections: List<Collection>,
    decks: List<Deck>,
    onOpenPlace: (String) -> Unit,
    onOpenDeck: (String) -> Unit,
    onChange: (StorageChange) -> Unit,
    /** Lend copies of the card (LendScreen.kt). */
    onLend: () -> Unit = {},
    /** The card's history on this phone (CopyHistoryScreen.kt). */
    onHistory: () -> Unit = {},
    /** The Loans screen, from a line of copies lent out. */
    onOpenLoans: () -> Unit = {},
    /** Photos of a copy (CopyPhotoScreen.kt). */
    onPhotos: () -> Unit = {}
) {
    val colors = LocalAppColors.current
    val places = placesOf(collections)
    val (lines, total) = remember(collections, decks, name) { whereItIs(collections, decks, name) }
    var giving by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    var selling by remember { mutableStateOf(false) }
    if (total == 0) return
    val unplaced = lines.firstOrNull { it.kind == WhereKind.NONE }?.qty ?: 0
    val placeLines = lines.filter { it.kind == WhereKind.PLACE }
    val facts = card?.let { cardFactsOf(it) } ?: CardFacts(name)

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.surface).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Where it is", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Text("$total ${if (total == 1) "copy" else "copies"}", style = MaterialTheme.typography.labelLarge, color = colors.textMuted)
        }
        lines.forEach { line ->
            val icon: ImageVector = when (line.kind) {
                WhereKind.PLACE -> placeIcon(places.firstOrNull { it.id == line.placeId }?.placeKind ?: PlaceKind.OTHER)
                WhereKind.DECK -> Icons.Filled.Style
                WhereKind.LENT -> Icons.Filled.Handshake
                WhereKind.NONE -> Icons.Filled.ErrorOutline
            }
            val open: (() -> Unit)? = when (line.kind) {
                WhereKind.PLACE -> line.placeId?.let { id -> { onOpenPlace(id) } }
                WhereKind.DECK -> line.deckId?.let { id -> { onOpenDeck(id) } }
                WhereKind.LENT -> onOpenLoans
                else -> null
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface2)
                    .then(if (line.kind == WhereKind.NONE) Modifier.border(1.dp, colors.warning.copy(alpha = 0.45f), RoundedCornerShape(12.dp)) else Modifier)
                    .then(if (open != null) Modifier.clickable(onClick = open) else Modifier)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Icon(icon, contentDescription = null, tint = if (line.kind == WhereKind.NONE) colors.warning else colors.accent, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(line.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (line.detail.isNotEmpty()) Text(line.detail, style = MaterialTheme.typography.labelMedium, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("×${line.qty}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            }
        }
        if (places.isNotEmpty() && (placeLines.isNotEmpty() || unplaced > 0)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { moving = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent.copy(alpha = 0.16f), contentColor = colors.accentLight),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) { Text("Move a copy") }
                Button(
                    onClick = { giving = true },
                    enabled = unplaced > 0,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.surface2, contentColor = colors.textPrimary),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) { Text("Give it a place") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onLend,
                enabled = lines.any { it.kind == WhereKind.PLACE || it.kind == WhereKind.DECK || it.kind == WhereKind.NONE },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.surface2, contentColor = colors.textPrimary),
                modifier = Modifier.weight(1f).height(44.dp)
            ) { Text("Lend") }
            Button(
                onClick = onHistory,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.surface2, contentColor = colors.textPrimary),
                modifier = Modifier.weight(1f).height(44.dp)
            ) { Text("History") }
        }
        // Selling (Selling.kt) and photos of a copy (CopyPhotos.kt): binder copies only.
        val (binderCopies, toSell) = sellCountsByName(collections, name)
        if (binderCopies > 0) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { selling = true },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.surface2, contentColor = colors.textPrimary),
                modifier = Modifier.weight(1f).height(44.dp)
            ) { Text(if (toSell > 0) "To sell: $toSell" else "Sell…") }
            Button(
                onClick = onPhotos,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.surface2, contentColor = colors.textPrimary),
                modifier = Modifier.weight(1f).height(44.dp)
            ) { Text("Photos") }
        }
    }

    if (selling) {
        val (binderCopies, toSell) = sellCountsByName(collections, name)
        var count by remember { mutableStateOf(toSell.coerceAtLeast(1).coerceAtMost(binderCopies)) }
        AlertDialog(
            onDismissRequest = { selling = false },
            containerColor = colors.surface,
            title = { Text("Sell $name", color = colors.accentLight, style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Copies go on the Storage tab's To sell list, with where they are.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Copies to sell", style = MaterialTheme.typography.labelMedium, color = colors.textMuted, modifier = Modifier.weight(1f))
                        IconButton(onClick = { count = (count - 1).coerceAtLeast(0) }, enabled = count > 0) {
                            Icon(Icons.Filled.Remove, contentDescription = "One fewer", tint = colors.textPrimary)
                        }
                        Text("$count of $binderCopies", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                        IconButton(onClick = { count = (count + 1).coerceAtMost(binderCopies) }, enabled = count < binderCopies) {
                            Icon(Icons.Filled.Add, contentDescription = "One more", tint = colors.textPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { val n = count; onChange { setForSaleByName(it, name, n) }; selling = false }) { Text("Save", color = colors.accent) }
            },
            dismissButton = { TextButton(onClick = { selling = false }) { Text("Cancel", color = colors.textMuted) } }
        )
    }

    if (giving) {
        PlacePickerDialog("Give $name a place", places, onDismiss = { giving = false }) { placeId ->
            giving = false
            placesOf(collections).firstOrNull { it.id == placeId }?.let { place ->
                CopyHistoryStore.record(listOf(putAwayMove(System.currentTimeMillis(), MoveCard(name, card?.id), 1, MoveSpot(place.id, place.name), null)))
            }
            onChange { current ->
                val place = placesOf(current).firstOrNull { it.id == placeId }
                if (place == null) current else placeUnplaced(current, name, card?.id, suggestSpot(place, facts, current).first, 1).first
            }
        }
    }
    if (moving) {
        MoveCopyDialog(name, card?.id, facts, placeLines, unplaced, collections, onDismiss = { moving = false }, onChange = onChange)
    }
}

/** Moves copies from one place (or from none) to another place (or to none). */
@Composable
private fun MoveCopyDialog(
    name: String,
    preferId: String?,
    facts: CardFacts,
    lines: List<WhereLine>,
    unplaced: Int,
    collections: List<Collection>,
    onDismiss: () -> Unit,
    onChange: (StorageChange) -> Unit
) {
    val colors = LocalAppColors.current
    val places = placesOf(collections)
    // Sources: each line of places, then the copies with no place (null).
    val sources = buildList<Pair<WhereLine?, Int>> {
        lines.forEach { add(it to it.qty) }
        if (unplaced > 0) add(null to unplaced)
    }
    var from by remember { mutableStateOf(0) }
    var to by remember { mutableStateOf<String?>(null) }
    var count by remember { mutableStateOf(1) }
    val source = sources.getOrNull(from)
    val max = source?.second ?: 0
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("Move a copy", color = colors.accentLight, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("From", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                sources.forEachIndexed { i, (line, qty) ->
                    Choice(
                        text = (line?.let { it.title + if (it.detail.isNotEmpty()) " (${it.detail})" else "" } ?: "No place yet") + " — $qty",
                        selected = i == from
                    ) { from = i; count = 1 }
                }
                Text("To", style = MaterialTheme.typography.labelMedium, color = colors.textMuted, modifier = Modifier.padding(top = 8.dp))
                Choice("No place", to == null) { to = null }
                placeTree(places).forEach { node ->
                    Choice("  ".repeat(node.depth) + node.place.name, to == node.place.id) { to = node.place.id }
                }
                if (max > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Text("How many", style = MaterialTheme.typography.labelMedium, color = colors.textMuted, modifier = Modifier.weight(1f))
                        IconButton(onClick = { count = (count - 1).coerceAtLeast(1) }, enabled = count > 1) {
                            Icon(Icons.Filled.Remove, contentDescription = "One fewer", tint = colors.textPrimary)
                        }
                        Text("$count", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                        IconButton(onClick = { count = (count + 1).coerceAtMost(max) }, enabled = count < max) {
                            Icon(Icons.Filled.Add, contentDescription = "One more", tint = colors.textPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = source != null && (to != null || source.first != null),
                onClick = {
                    val target = to
                    val line = source?.first
                    val toPlace = target?.let { id -> places.firstOrNull { it.id == id } }
                    CopyHistoryStore.record(listOf(movedMove(
                        System.currentTimeMillis(), MoveCard(name, preferId), count,
                        line?.let { l -> l.placeId?.let { MoveSpot(it, l.title) } }, toPlace?.let { MoveSpot(it.id, it.name) }
                    )))
                    onChange { current ->
                        val place = target?.let { id -> placesOf(current).firstOrNull { it.id == id } }
                        val spot = place?.let { suggestSpot(it, facts, current).first }
                        if (line == null) {
                            if (spot == null) current else placeUnplaced(current, name, preferId, spot, count).first
                        } else {
                            current.map { c ->
                                if (c.id != line.collectionId) c
                                else c.copy(entries = c.entries.map { e ->
                                    val fromLine = line.line
                                    if (e.scryfallId == line.scryfallId && fromLine != null) moveCopies(e, fromLine, spot, count).first else e
                                })
                            }
                        }
                    }
                    onDismiss()
                }
            ) { Text("Move", color = colors.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textMuted) } }
    )
}

@Composable
private fun Choice(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (selected) colors.onAccent else colors.textPrimary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.accent else colors.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    )
}
