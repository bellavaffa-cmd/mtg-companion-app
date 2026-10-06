package com.mtgcompanion.app.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CopyHistoryStore
import com.mtgcompanion.app.data.PlaceKind
import com.mtgcompanion.app.data.Space
import com.mtgcompanion.app.data.SplitPlan
import com.mtgcompanion.app.data.StoragePlace
import com.mtgcompanion.app.data.cardsIn
import com.mtgcompanion.app.data.lastPileAdded
import com.mtgcompanion.app.data.nextBoxName
import com.mtgcompanion.app.data.placeTree
import com.mtgcompanion.app.data.placesOf
import com.mtgcompanion.app.data.planSplit
import com.mtgcompanion.app.data.pockets
import com.mtgcompanion.app.data.roomLine
import com.mtgcompanion.app.data.savePlace
import com.mtgcompanion.app.data.sizeSetting
import com.mtgcompanion.app.data.spaceLabel
import com.mtgcompanion.app.data.spaceOf
import com.mtgcompanion.app.data.splitBox
import com.mtgcompanion.app.data.splitSideLabel
import com.mtgcompanion.app.data.withSize
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.common.a11yHeading
import com.mtgcompanion.app.ui.theme.LocalAppColors
import java.util.UUID

/*
 * Box space, the web app's SpacePage (src/pages/SpacePage.tsx): every place with a size as a bar —
 * "96% full · 612 of 640", the fullest first — with "Room for about 28 more", Split into two boxes
 * (on whole sections, then the new box's label to print) and Change size; the places with no size
 * yet below. The logic is data/BoxSpace.kt. SpaceCard and SizeDialog are a place's page's too.
 */

/** The orange a nearly full place is drawn in. */
internal val FullColour = Color(0xFFFF8A4C)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceScreen(
    collections: List<Collection>,
    onBack: () -> Unit,
    onOpenPlace: (String) -> Unit,
    onChange: (StorageChange) -> Unit,
    /** The new box's label, after a split (PlaceLabelScreen). */
    onLabel: (String) -> Unit
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    remember { CopyHistoryStore.init(context) }
    val history by CopyHistoryStore.moves.collectAsState()
    val places = placesOf(collections)
    val sized = remember(collections) {
        placeTree(places).mapNotNull { n -> spaceOf(n.place, collections)?.let { n.place to it } }
            .sortedByDescending { (_, s) -> if (s.size > 0) s.used.toDouble() / s.size else 0.0 }
    }
    val unsized = places.filter { p -> sized.none { it.first.id == p.id } && p.placeKind != PlaceKind.DECK_BOX }
    var selected by remember { mutableStateOf<String?>(null) }
    var splitting by remember { mutableStateOf<String?>(null) }
    var sizing by remember { mutableStateOf<StoragePlace?>(null) }
    val open = selected ?: sized.firstOrNull { (_, s) -> s.nearlyFull }?.first?.id
    val splitPlace = splitting?.let { id -> places.firstOrNull { it.id == id } }
    val plan = splitPlace?.let { planSplit(it, cardsIn(collections, it.id)) }

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("Space", style = MaterialTheme.typography.titleLarge, modifier = Modifier.a11yHeading()) },
                navigationIcon = { BackButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        },
        bottomBar = {
            if (splitPlace != null && plan != null) {
                Box(Modifier.fillMaxWidth().background(colors.bg).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)) {
                    Button(
                        onClick = {
                            val id = UUID.randomUUID().toString()
                            val now = System.currentTimeMillis()
                            onChange { current ->
                                val place = placesOf(current).firstOrNull { it.id == splitPlace.id }
                                val fresh = place?.let { planSplit(it, cardsIn(current, it.id)) }
                                if (fresh == null) current else splitBox(current, splitPlace.id, fresh, id, now)
                            }
                            splitting = null
                            onLabel(id)
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text("Split and print new label", fontWeight = FontWeight.ExtraBold) }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (sized.isEmpty()) item {
                Text(
                    "No place has a size yet. Give a box the cards it holds, or a binder its pages, and see how full each is.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted
                )
            }
            items(sized, key = { it.first.id }) { (place, space) ->
                SpaceCard(
                    place = place,
                    space = space,
                    lastPile = lastPileAdded(history, place.id),
                    expanded = place.id == open,
                    canSplit = place.placeKind != PlaceKind.BINDER && planSplit(place, cardsIn(collections, place.id)) != null,
                    onClick = { selected = place.id; if (splitting != place.id) splitting = null },
                    onOpen = { onOpenPlace(place.id) },
                    onSplit = { selected = place.id; splitting = place.id },
                    onSize = { sizing = place }
                )
            }
            if (splitPlace != null && plan != null) item {
                SplitSection(splitPlace, plan, places)
            }
            if (unsized.isNotEmpty()) {
                item {
                    Text("NO SIZE YET", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = colors.textMuted, modifier = Modifier.padding(top = 14.dp))
                }
                items(unsized, key = { "unsized:" + it.id }) { place ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(place.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).clickable { onOpenPlace(place.id) })
                        TextButton(onClick = { sizing = place }) { Text("Set size", color = colors.accent) }
                    }
                }
            }
        }
    }

    sizing?.let { place ->
        SizeDialog(place, onDismiss = { sizing = null }) { n ->
            onChange { current -> placesOf(current).firstOrNull { it.id == place.id }?.let { savePlace(current, withSize(it, n)) } ?: current }
            sizing = null
        }
    }
}

/** One place's space: its name, "96% full · 612 of 640", the bar, and — open — the room left with Split and Change size. */
@Composable
internal fun SpaceCard(
    place: StoragePlace,
    space: Space,
    lastPile: Int?,
    expanded: Boolean,
    canSplit: Boolean,
    onClick: (() -> Unit)?,
    onOpen: (() -> Unit)?,
    onSplit: () -> Unit,
    onSize: () -> Unit
) {
    val colors = LocalAppColors.current
    val full = space.nearlyFull
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .then(if (full && expanded) Modifier.border(1.dp, FullColour.copy(alpha = 0.5f), RoundedCornerShape(14.dp)) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                place.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)
            )
            Text(
                spaceLabel(place, space),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (full) FontWeight.Bold else FontWeight.Normal,
                color = if (full) FullColour else colors.textMuted,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(colors.surface2)) {
            Box(
                Modifier
                    .fillMaxWidth((space.used.toFloat() / space.size.coerceAtLeast(1)).coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (full) FullColour else colors.accent)
            )
        }
        if (expanded) {
            Text(roomLine(space, lastPile), style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (canSplit) LoanButton("Split into two boxes", primary = true, modifier = Modifier.weight(1f), onClick = onSplit)
                LoanButton("Change size", primary = false, modifier = Modifier.weight(1f), onClick = onSize)
            }
        }
    }
}

/** "Split Red box": what stays, what goes to the new box, and that nothing inside a section moves. */
@Composable
internal fun SplitSection(place: StoragePlace, plan: SplitPlan, places: List<StoragePlace>) {
    val colors = LocalAppColors.current
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(14.dp)
    ) {
        Text("Split ${place.name}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary)
        Row(Modifier.fillMaxWidth()) {
            Text(place.name, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Text(splitSideLabel(plan.stay, plan.stayCopies), style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, modifier = Modifier.padding(start = 8.dp))
        }
        Row(Modifier.fillMaxWidth()) {
            Text("New: ${nextBoxName(places, place.name)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = colors.accentLight, modifier = Modifier.weight(1f))
            Text(splitSideLabel(plan.go, plan.goCopies), style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, modifier = Modifier.padding(start = 8.dp))
        }
        Text("Splits on whole sections, so nothing inside a ${sectionWord(place)} moves.", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
    }
}

/** "colour" for a box sorted by colour, "type" by type, else "section". */
private fun sectionWord(place: StoragePlace): String = when (place.sortRule) {
    "COLOUR" -> "colour"
    "TYPE" -> "type"
    "SET" -> "set"
    else -> "section"
}

/** Change size: a binder's pages, anything else's cards; Remove size takes it off. */
@Composable
internal fun SizeDialog(place: StoragePlace, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    val colors = LocalAppColors.current
    val binder = place.placeKind == PlaceKind.BINDER
    var text by remember { mutableStateOf(sizeSetting(place)?.toString() ?: "") }
    val n = text.toIntOrNull()?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("Size of ${place.name}", color = colors.accentLight, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text(if (binder) "Pages" else "Cards it holds", color = colors.textMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        cursorColor = colors.accent
                    )
                )
                Text(
                    if (binder) "${n?.let { "${it * place.pockets} pockets · " } ?: ""}${place.pockets} pockets a page. Count both sides of a sheet as pages."
                    else "About how many cards fit: an 800-count box holds about 800 without sleeves.",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { n?.let(onSave) }, enabled = n != null) { Text("Save", color = colors.accent) }
        },
        dismissButton = {
            Row {
                if (sizeSetting(place) != null) TextButton(onClick = { onSave(0) }) { Text("Remove size", color = colors.error) }
                TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textMuted) }
            }
        }
    )
}
