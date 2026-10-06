package com.mtgcompanion.app.ui.collection

import com.mtgcompanion.app.data.usage.Usage
import com.mtgcompanion.app.data.usage.UsageAction
import com.mtgcompanion.app.ui.common.EmptyAction
import com.mtgcompanion.app.ui.common.EmptyPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.DEFAULT_POCKETS
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.PlaceKind
import com.mtgcompanion.app.data.SortRule
import com.mtgcompanion.app.data.StoragePlace
import com.mtgcompanion.app.data.canMoveInto
import com.mtgcompanion.app.data.childrenOf
import com.mtgcompanion.app.data.copiesWithin
import com.mtgcompanion.app.data.defaultSections
import com.mtgcompanion.app.data.gearOf
import com.mtgcompanion.app.data.gearSummary
import com.mtgcompanion.app.data.placeAndInside
import com.mtgcompanion.app.data.placeSubtitle
import com.mtgcompanion.app.data.placeTree
import com.mtgcompanion.app.data.placesOf
import com.mtgcompanion.app.data.savePlace
import com.mtgcompanion.app.data.storageSummary
import com.mtgcompanion.app.ui.theme.LocalAppColors
import com.mtgcompanion.app.ui.common.rememberMoney
import com.mtgcompanion.app.data.sealedOf
import com.mtgcompanion.app.data.sealedTotalUsd
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

/*
 * The Collection's Storage tab: how much of the collection has a place, the places themselves as a
 * tree with their copies, the deck boxes and the copies lent out — and the dialogs to make, change
 * and pick a place. The logic is in data/StoragePlaces.kt. Mirrors the web app's
 * src/collection/StorageTab.tsx.
 */

/** The icon for each kind of place. */
fun placeIcon(kind: PlaceKind): ImageVector = when (kind) {
    PlaceKind.BOX -> Icons.Filled.Inventory2
    PlaceKind.BINDER -> Icons.Filled.MenuBook
    PlaceKind.DECK_BOX -> Icons.Filled.Style
    PlaceKind.SHELF -> Icons.Filled.Layers
    PlaceKind.OTHER -> Icons.Filled.Folder
}

private fun count(n: Int): String = NumberFormat.getIntegerInstance(Locale.UK).format(n)

/** A change to the binders: the storage places, or where copies are kept. */
typealias StorageChange = (List<Collection>) -> List<Collection>

@Composable
fun StorageTab(
    collections: List<Collection>,
    decks: List<Deck>,
    onOpenPlace: (String) -> Unit,
    onPutAway: (String) -> Unit,
    onOpenDecks: () -> Unit,
    onChange: (StorageChange) -> Unit,
    /** The Loans screen (LoansScreen.kt). */
    onOpenLoans: () -> Unit = {},
    /** The scanner sorting a new pile into piles (SortPanel.kt). */
    onSortPile: () -> Unit = {},
    /** Value by place (ValueByPlaceScreen.kt). */
    onOpenValue: () -> Unit = {},
    /** How full each place is (SpaceScreen.kt). */
    onOpenSpace: () -> Unit = {},
    /** The To sell list (SellScreen.kt). */
    onOpenSell: () -> Unit = {},
    /** Getting started with storage (StorageSetupScreen.kt). */
    onSetUp: () -> Unit = {},
    /** Upkeep: what's worth doing this week (UpkeepScreen.kt). */
    onOpenUpkeep: () -> Unit = {},
    /** Sharing storage at home (HouseholdScreen.kt). */
    onOpenHousehold: () -> Unit = {},
    /** Sealed product (SealedScreen.kt). */
    onOpenSealed: () -> Unit = {},
    /** Gear: sleeves, deck boxes, tokens (GearScreen.kt). */
    onOpenGear: () -> Unit = {}
) {
    val colors = LocalAppColors.current
    val places = placesOf(collections)
    val summary = remember(collections, decks) { storageSummary(collections, decks) }
    var editing by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf(false) }
    val share = if (summary.total > 0) summary.placed.toFloat() / summary.total else 0f
    val upkeep = rememberUpkeep(collections, decks)
    val money = rememberMoney()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(14.dp)
            ) {
                Text(
                    "${count(summary.placed)} of ${count(summary.total)} ${if (summary.total == 1) "copy has" else "copies have"} a place",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary
                )
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(colors.surface2)) {
                    Box(Modifier.fillMaxWidth(share).height(8.dp).clip(RoundedCornerShape(4.dp)).background(colors.accent))
                }
                if (summary.unplaced > 0) {
                    Text(
                        "${count(summary.unplaced)} without a place · Put them away",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.accentLight,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { if (places.isEmpty()) editing = true else choosing = true }
                            .padding(vertical = 4.dp)
                    )
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Your places", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.accent.copy(alpha = 0.16f))
                        .clickable { editing = true }
                        .heightIn(min = 36.dp)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = colors.accentLight, modifier = Modifier.size(18.dp))
                    Text("New place", style = MaterialTheme.typography.labelLarge, color = colors.accentLight, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
        if (places.isEmpty()) {
            item {
                EmptyPrompt(
                    Icons.Filled.Inventory2,
                    "No places yet. Say roughly what you keep your cards in — binders, boxes, a shelf — and the app makes the places, their labels, and keeps track of where each copy is.",
                    actions = listOf(
                        EmptyAction("Get started", Icons.Filled.Inventory2, onSetUp),
                        EmptyAction("New place", Icons.Filled.Add) { editing = true }
                    )
                )
            }
        }
        items(childrenOf(places, null), key = { it.id }) { top ->
            val within = placeAndInside(places, top.id)
            val inside = placeTree(places).filter { it.place.id != top.id && it.place.id in within }
            val kids = childrenOf(places, top.id).size
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                PlaceRow(
                    icon = placeIcon(top.placeKind),
                    name = top.name,
                    detail = if (kids == 0) placeSubtitle(top) else null,
                    trailing = if (kids > 0) "$kids ${if (kids == 1) "place" else "places"}" else count(copiesWithin(summary, places, top.id)),
                    gold = false,
                    onClick = { onOpenPlace(top.id) }
                )
                inside.forEach { node ->
                    Box(
                        Modifier
                            .padding(start = (24 * node.depth).dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surface2)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        PlaceRow(
                            icon = placeIcon(node.place.placeKind),
                            name = node.place.name,
                            detail = placeSubtitle(node.place),
                            trailing = count(copiesWithin(summary, places, node.place.id)),
                            gold = true,
                            onClick = { onOpenPlace(node.place.id) }
                        )
                    }
                }
            }
        }
        item {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp)) {
                PlaceRow(Icons.Filled.Style, "Deck boxes", "Your physical decks, kept up to date", count(summary.inDecks), gold = false, onClick = onOpenDecks)
            }
        }
        item {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp)) {
                PlaceRow(Icons.Filled.Handshake, "Lent out", "Your loans, and what friends lent you", count(summary.lent), gold = false, onClick = onOpenLoans)
            }
        }
        item {
            val sealed = sealedOf(collections)
            val boxes = sealed.sumOf { it.count }
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp)) {
                PlaceRow(
                    Icons.Filled.Inventory2, "Sealed",
                    if (sealed.isNotEmpty()) "${count(boxes)} sealed · ${money.format(sealedTotalUsd(sealed), whole = true)}" else "Booster boxes, bundles and precons",
                    count(boxes), gold = false, onClick = onOpenSealed
                )
            }
        }
        item {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp)) {
                PlaceRow(Icons.Filled.Backpack, "Gear", gearSummary(gearOf(collections), decks), "›", gold = false, onClick = onOpenGear)
            }
        }
        item {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp)) {
                PlaceRow(Icons.Filled.Home, "Sharing storage at home", "Keep cards on the same shelf as someone you live with", "›", gold = false, onClick = onOpenHousehold)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                val n = upkeep?.items?.size ?: 0
                LoanButton(if (n > 0) "Upkeep · $n" else "Upkeep", primary = n > 0, modifier = Modifier.weight(1f), onClick = onOpenUpkeep)
                LoanButton("Set up storage", primary = false, modifier = Modifier.weight(1f), onClick = onSetUp)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                LoanButton("Sort a new pile", primary = false, modifier = Modifier.weight(1f), onClick = onSortPile)
                LoanButton("Value by place", primary = false, modifier = Modifier.weight(1f), onClick = onOpenValue)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                LoanButton("Space", primary = false, modifier = Modifier.weight(1f), onClick = onOpenSpace)
                LoanButton("To sell", primary = false, modifier = Modifier.weight(1f), onClick = onOpenSell)
            }
        }
    }

    if (editing) {
        PlaceDialog(place = null, parentId = null, places = places, onDismiss = { editing = false }) { place ->
            onChange { savePlace(it, place) }
            editing = false
        }
    }
    if (choosing) {
        PlacePickerDialog("Put cards away into…", places, onDismiss = { choosing = false }) { id ->
            choosing = false
            onPutAway(id)
        }
    }
}

/** One place in a list: its icon, name, a line about it and a count. */
@Composable
fun PlaceRow(icon: ImageVector, name: String, detail: String?, trailing: String, gold: Boolean, onClick: (() -> Unit)?) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Icon(icon, contentDescription = null, tint = if (gold) colors.accent else colors.textMuted, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!detail.isNullOrEmpty()) Text(detail, style = MaterialTheme.typography.labelMedium, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(trailing, style = MaterialTheme.typography.labelLarge, color = colors.textMuted, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Pick a place: every place, in tree order. */
@Composable
fun PlacePickerDialog(title: String, places: List<StoragePlace>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val colors = LocalAppColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(title, color = colors.accentLight, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (places.isEmpty()) Text("No places yet — make one on the Collection's Storage tab.", color = colors.textMuted)
                placeTree(places).forEach { node ->
                    Box(Modifier.padding(start = (20 * node.depth).dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).padding(vertical = 4.dp)) {
                        PlaceRow(placeIcon(node.place.placeKind), node.place.name, placeSubtitle(node.place), "", gold = true, onClick = { onPick(node.place.id) })
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textMuted) } }
    )
}

/** A dropdown picking one of [options] (value to label), shown as a field. */
@Composable
internal fun PickField(label: String, value: String, options: List<Pair<String, String>>, onPick: (String) -> Unit) {
    val colors = LocalAppColors.current
    var open by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surface2)
                    .clickable { open = true }
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Text(options.firstOrNull { it.first == value }?.second ?: "", color = colors.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = colors.textMuted)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.surface) {
                options.forEach { (v, text) ->
                    DropdownMenuItem(text = { Text(text, color = colors.textPrimary) }, onClick = { onPick(v); open = false })
                }
            }
        }
    }
}

/** Make a place ([place] null), or change one: its name, what it is, where it sits and how it's organised. */
@Composable
fun PlaceDialog(place: StoragePlace?, parentId: String?, places: List<StoragePlace>, onDismiss: () -> Unit, onSave: (StoragePlace) -> Unit) {
    val colors = LocalAppColors.current
    var name by remember { mutableStateOf(place?.name ?: "") }
    var kind by remember { mutableStateOf(place?.placeKind ?: PlaceKind.BOX) }
    var parent by remember { mutableStateOf(place?.parentId ?: parentId ?: "") }
    var note by remember { mutableStateOf(place?.note ?: "") }
    var pockets by remember { mutableStateOf((place?.pocketsPerPage ?: DEFAULT_POCKETS).toString()) }
    var rule by remember { mutableStateOf(place?.sortRule ?: "") }
    var sections by remember { mutableStateOf(place?.sections.orEmpty().joinToString(", ")) }
    val parents = placeTree(places).filter { place == null || canMoveInto(places, place.id, it.place.id) }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.accent,
        unfocusedBorderColor = colors.border,
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        cursorColor = colors.accent
    )
    val save = {
        if (name.isNotBlank()) {
            val list = sections.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val pocketCount = pockets.toIntOrNull()?.takeIf { it > 0 && it != DEFAULT_POCKETS }
            if (place == null) Usage.action(UsageAction.PLACE_CREATED)
            onSave(StoragePlace(
                id = place?.id ?: UUID.randomUUID().toString(),
                name = name.trim(),
                kind = kind.name,
                parentId = parent.ifEmpty { null },
                note = note.trim().ifEmpty { null },
                sections = if (kind == PlaceKind.BOX && list.isNotEmpty()) list else null,
                pocketsPerPage = if (kind == PlaceKind.BINDER) pocketCount else null,
                // A box's sorting rule, or a binder's order (BinderPages.kt).
                sortRule = if (kind == PlaceKind.BOX || kind == PlaceKind.BINDER) rule.ifEmpty { null } else null,
                createdAt = place?.createdAt ?: System.currentTimeMillis(),
                // When it was last checked (PlaceCheck.kt) isn't changed here, nor its size (Change size, BoxSpace.kt).
                lastChecked = place?.lastChecked,
                capacity = place?.capacity,
                pages = place?.pages
            ))
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(if (place == null) "New place" else "Change place", color = colors.accentLight, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name", color = colors.textMuted) },
                    placeholder = { Text("Red box", color = colors.textDim) },
                    singleLine = true,
                    colors = fieldColors
                )
                Text("What it is", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlaceKind.entries.forEach { k ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (k == kind) colors.textPrimary else colors.surface2)
                                .clickable { kind = k }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(placeIcon(k), contentDescription = null, tint = if (k == kind) colors.bg else colors.textMuted, modifier = Modifier.size(16.dp))
                            Text(k.label, style = MaterialTheme.typography.labelLarge, color = if (k == kind) colors.bg else colors.textMuted, modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
                PickField(
                    "Inside",
                    parent,
                    listOf("" to "Nothing — it stands on its own") + parents.map { it.place.id to ("  ".repeat(it.depth) + it.place.name) }
                ) { parent = it }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note", color = colors.textMuted) },
                    placeholder = { Text("Bulk, trade fodder…", color = colors.textDim) },
                    singleLine = true,
                    colors = fieldColors
                )
                if (kind == PlaceKind.BINDER) {
                    OutlinedTextField(
                        value = pockets,
                        onValueChange = { pockets = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("Pockets per page", color = colors.textMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = fieldColors
                    )
                    PickField("In order", rule, listOf("" to "No order — new cards go in the next free pocket") + SortRule.entries.map { it.name to it.label }) { rule = it }
                    Text(
                        "With an order, Add cards in order says where new cards go and what to shift.",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textMuted
                    )
                }
                if (kind == PlaceKind.BOX) {
                    PickField("Sorted", rule, listOf("" to "Not sorted") + SortRule.entries.map { it.name to it.label }) { picked ->
                        rule = picked
                        if (sections.isBlank() && picked.isNotEmpty()) sections = defaultSections(SortRule.fromName(picked)).joinToString(", ")
                    }
                    OutlinedTextField(
                        value = sections,
                        onValueChange = { sections = it },
                        label = { Text("Sections", color = colors.textMuted) },
                        placeholder = { Text("White, Blue, Black…", color = colors.textDim) },
                        colors = fieldColors
                    )
                    Text(
                        "In order, with commas between. Cards put away go in the section the rule gives them.",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textMuted
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = save, enabled = name.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)) {
                Text(if (place == null) "Make place" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textMuted) } }
    )
}
