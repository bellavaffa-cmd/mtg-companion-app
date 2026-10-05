package com.mtgcompanion.app.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.PullProgress
import com.mtgcompanion.app.data.copiesWithin
import com.mtgcompanion.app.data.parentsOf
import com.mtgcompanion.app.data.placesOf
import com.mtgcompanion.app.data.pullGroupsIn
import com.mtgcompanion.app.data.pullList
import com.mtgcompanion.app.data.storageSummary
import com.mtgcompanion.app.ui.theme.LocalAppColors

/**
 * What a scanned box label offers, the web app's PlaceLabelSheet (src/collection/PlaceLabelSheet.tsx):
 * put cards away into the place, open it, or — with a pull list open that needs something from it —
 * pull from it. A label for a place that isn't in the collection (deleted, or another account's)
 * says so. Shown over the scanner's camera.
 */
@Composable
fun PlaceLabelPanel(
    placeId: String,
    collections: List<Collection>,
    decks: List<Deck>,
    onPutAway: (String) -> Unit,
    onOpen: (String) -> Unit,
    onPull: (deckId: String, placeId: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val places = placesOf(collections)
    val place = places.firstOrNull { it.id == placeId }
    val pullDeck = remember(collections, decks, placeId) {
        val id = PullProgress(context).openPullDeck
        val deck = decks.firstOrNull { it.id == id }
        // Only when the open pull list needs something from here.
        if (deck != null && place != null && pullGroupsIn(pullList(deck, collections, decks), collections, place.id).isNotEmpty()) deck else null
    }
    val panel = modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surface).padding(18.dp)
    if (place == null) {
        Column(panel, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.QrCode2, contentDescription = null, tint = colors.textDim)
                Text("This label's place isn't in your collection", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
            }
            Text("It may have been deleted, or the label belongs to another account.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("OK", color = colors.accent) }
        }
        return
    }
    val where = parentsOf(places, place.id).joinToString(" › ") { it.name }
    val copies = copiesWithin(storageSummary(collections, decks), places, place.id)
    Column(panel, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("BOX LABEL FOUND", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = colors.textMuted, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("Close", color = colors.textMuted) }
        }
        Column {
            if (where.isNotEmpty()) Text(where.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = colors.textMuted)
            Text(place.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary)
            Text("$copies ${if (copies == 1) "copy" else "copies"}", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        Button(
            onClick = { onPutAway(place.id) },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Put cards away here", fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onOpen(place.id) },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.surface2, contentColor = colors.textPrimary),
                modifier = Modifier.weight(1f).height(44.dp)
            ) { Text("Open box") }
            if (pullDeck != null) Button(
                onClick = { onPull(pullDeck.id, place.id) },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.surface2, contentColor = colors.textPrimary),
                modifier = Modifier.weight(1f).height(44.dp)
            ) { Text("Pull from here") }
        }
        if (pullDeck != null) {
            Text(
                "Pull from here shows only the cards your open pull list (${pullDeck.name}) needs from this box.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted
            )
        }
    }
}
