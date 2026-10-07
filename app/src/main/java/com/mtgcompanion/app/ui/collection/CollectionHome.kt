package com.mtgcompanion.app.ui.collection

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.HomeTileKey
import com.mtgcompanion.app.data.Money
import com.mtgcompanion.app.data.PlaceKind
import com.mtgcompanion.app.data.TourTarget
import com.mtgcompanion.app.data.UpkeepItem
import com.mtgcompanion.app.data.UpkeepKind
import com.mtgcompanion.app.data.homeNumbers
import com.mtgcompanion.app.data.homeTiles
import com.mtgcompanion.app.data.homeTodo
import com.mtgcompanion.app.data.placesOf
import com.mtgcompanion.app.data.upkeepHeadline
import com.mtgcompanion.app.ui.common.a11yHeading
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.flow.MutableStateFlow

/*
 * The Collection's home (data/CollectionHome.kt), the web app's CollectionHome.tsx: "Find a card, a
 * place or a deck", a tile for each part — All cards, Storage, Binders, Sets, Sealed and graded,
 * Loans and selling — the few things worth doing this week (from Upkeep) and Scan, Sort a pile and
 * Import. The value sits in the top bar (CollectionsScreen). The tiles open the Collection's pages.
 */

/** Asks the Collection to go back to its home: the Collection tab tapped while it's open. */
object CollectionHomeRequest {
    val requests = MutableStateFlow(0)
    fun ask() { requests.value += 1 }
}

/** Where the home's taps go. [onOpenPage]: the Collection's pages, 0 All cards … 3 Sets. */
class CollectionHomeActions(
    val onOpenPage: (Int) -> Unit,
    val onFind: () -> Unit,
    val onOpenSealed: () -> Unit,
    val onOpenLoans: () -> Unit,
    val onOpenSell: () -> Unit,
    val onOpenUpkeep: () -> Unit,
    val onPutAway: (String) -> Unit,
    val onSetUpStorage: () -> Unit,
    val onCheck: (String) -> Unit,
    val onOpenSpace: () -> Unit,
    val onOpenPullList: (String) -> Unit,
    val onScan: () -> Unit,
    val onSortPile: () -> Unit,
    val onImport: () -> Unit
)

private fun tileIcon(key: HomeTileKey): ImageVector = when (key) {
    HomeTileKey.ALL -> Icons.Filled.Style
    HomeTileKey.STORAGE -> placeIcon(PlaceKind.SHELF)
    HomeTileKey.BINDERS -> Icons.Filled.CollectionsBookmark
    HomeTileKey.SETS -> Icons.Filled.GridView
    HomeTileKey.SEALED -> Icons.Filled.Inventory2
    HomeTileKey.LOANS -> Icons.Filled.Handshake
}

@Composable
fun CollectionHomePage(
    collections: List<Collection>,
    decks: List<Deck>,
    money: Money,
    actions: CollectionHomeActions,
    tour: TourTargets,
    scroll: ScrollState
) {
    val colors = LocalAppColors.current
    val numbers = remember(collections, decks) { homeNumbers(collections, decks) }
    val tiles = homeTiles(numbers) { money.format(it, whole = true) }
    val report = rememberUpkeep(collections, decks)
    val deckNames = remember(decks) { decks.associate { it.id to it.name } }
    val todo = homeTodo(report?.items.orEmpty(), deckNames)
    val places = placesOf(collections)
    var choosing by remember { mutableStateOf(false) }
    var loansSheet by remember { mutableStateOf(false) }

    fun openTile(key: HomeTileKey) {
        when (key) {
            HomeTileKey.ALL -> actions.onOpenPage(0)
            HomeTileKey.BINDERS -> actions.onOpenPage(1)
            HomeTileKey.STORAGE -> actions.onOpenPage(2)
            HomeTileKey.SETS -> actions.onOpenPage(3)
            HomeTileKey.SEALED -> actions.onOpenSealed()
            HomeTileKey.LOANS -> loansSheet = true
        }
    }
    fun doTodo(item: UpkeepItem) {
        when (item.kind) {
            UpkeepKind.PUT_AWAY -> if (places.isEmpty()) actions.onSetUpStorage() else choosing = true
            UpkeepKind.CHECK -> item.placeId?.let(actions.onCheck)
            UpkeepKind.REMIND -> actions.onOpenLoans()
            UpkeepKind.SPLIT -> actions.onOpenSpace()
            UpkeepKind.CARRY_ON -> item.deckId?.let(actions.onOpenPullList)
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .tourTarget(tour, TourTarget.HOME_FIND)
                .height(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface)
                .clickable(role = Role.Button, onClick = actions.onFind)
                .padding(horizontal = 14.dp)
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
            Text("Find a card, a place or a deck", style = MaterialTheme.typography.bodyLarge, color = colors.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.tourTarget(tour, TourTarget.HOME_TILES)) {
            tiles.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    pair.forEach { tile ->
                        val target = when (tile.key) {
                            HomeTileKey.STORAGE -> TourTarget.HOME_STORAGE
                            HomeTileKey.SEALED -> TourTarget.HOME_SEALED
                            else -> null
                        }
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .weight(1f)
                                .then(if (target != null) Modifier.tourTarget(tour, target) else Modifier)
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.surface)
                                .clickable(role = Role.Button) { openTile(tile.key) }
                                .padding(14.dp)
                        ) {
                            Icon(tileIcon(tile.key), contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                            Text(tile.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(tile.line, style = MaterialTheme.typography.labelMedium, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.tourTarget(tour, TourTarget.HOME_TODO)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text("To do", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary, modifier = Modifier.weight(1f).a11yHeading())
                TextButton(onClick = actions.onOpenUpkeep) { Text("Upkeep", color = colors.accent, fontWeight = FontWeight.SemiBold) }
            }
            if (report != null && todo.isEmpty()) {
                TodoRow(upkeepHeadline(0), null, primary = false) {}
            }
            todo.forEachIndexed { i, t ->
                TodoRow(t.title, t.action, primary = i == 0) { doTodo(t.item) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            QuickButton("Scan", Icons.Filled.PhotoCamera, Modifier.weight(1f), actions.onScan)
            QuickButton("Sort a pile", Icons.AutoMirrored.Filled.CallSplit, Modifier.weight(1f), actions.onSortPile)
            QuickButton("Import", Icons.AutoMirrored.Filled.PlaylistAdd, Modifier.weight(1f), actions.onImport)
        }
    }

    if (choosing) {
        PlacePickerDialog("Put cards away into…", places, onDismiss = { choosing = false }) { id ->
            choosing = false
            actions.onPutAway(id)
        }
    }
    if (loansSheet) {
        AlertDialog(
            onDismissRequest = { loansSheet = false },
            title = { Text("Loans and selling") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LoanButton("Loans · ${numbers.lentOut} out", primary = false, modifier = Modifier.fillMaxWidth()) { loansSheet = false; actions.onOpenLoans() }
                    LoanButton("To sell · ${numbers.toSell}", primary = false, modifier = Modifier.fillMaxWidth()) { loansSheet = false; actions.onOpenSell() }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { loansSheet = false }) { Text("Cancel") } },
            containerColor = colors.surface
        )
    }
}

@Composable
private fun TodoRow(title: String, action: String?, primary: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            title, style = MaterialTheme.typography.bodyMedium,
            color = if (action == null) colors.textMuted else colors.textPrimary,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 12.dp, top = if (action == null) 6.dp else 0.dp, bottom = if (action == null) 6.dp else 0.dp)
        )
        if (action != null) LoanButton(action, primary = primary, modifier = Modifier.widthIn(min = 88.dp), onClick = onClick)
    }
}

@Composable
private fun QuickButton(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = colors.surface2, contentColor = colors.textPrimary),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
        modifier = modifier.height(44.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp).padding(end = 0.dp))
        Text(label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp))
    }
}
