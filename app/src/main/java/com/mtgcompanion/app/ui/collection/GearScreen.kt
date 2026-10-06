package com.mtgcompanion.app.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckOwnership
import com.mtgcompanion.app.data.GearItem
import com.mtgcompanion.app.data.GearKind
import com.mtgcompanion.app.data.PlaceKind
import com.mtgcompanion.app.data.deckNeeds
import com.mtgcompanion.app.data.deckNeedsLine
import com.mtgcompanion.app.data.deleteGear
import com.mtgcompanion.app.data.gearOf
import com.mtgcompanion.app.data.gearRows
import com.mtgcompanion.app.data.placesOf
import com.mtgcompanion.app.data.saveGear
import com.mtgcompanion.app.data.tokensToBring
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.common.a11yHeading
import com.mtgcompanion.app.ui.theme.LocalAppColors
import java.util.UUID

/*
 * Gear (data/Gear.kt), the web app's GearPage (src/pages/GearPage.tsx) and the Gear mockup: sleeves
 * with how many are left and which decks and binders use them ("running low" when a deck using them
 * needs more), inner sleeves, deck boxes and what each holds, tokens by name and where they're kept,
 * dice, playmats and the rest; "This deck needs" for a deck; "+ Add gear". The gear syncs on the
 * Unsorted pile.
 */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GearScreen(
    collections: List<Collection>,
    decks: List<Deck>,
    onBack: () -> Unit,
    onChange: (StorageChange) -> Unit
) {
    val colors = LocalAppColors.current
    val gear = gearOf(collections)
    val rows = remember(gear, decks, collections) { gearRows(gear, decks, collections) }
    var editing by remember { mutableStateOf<GearItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<String?>(null) }
    val shown = decks.filter { it.archived != true && it.sample != true && it.ownershipType != DeckOwnership.VIRTUAL }
    var deckId by remember { mutableStateOf<String?>(null) }
    val deck = shown.firstOrNull { it.id == deckId } ?: shown.firstOrNull()

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("Gear", style = MaterialTheme.typography.titleLarge, modifier = Modifier.a11yHeading()) },
                navigationIcon = { BackButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().background(colors.bg).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp)) {
                LoanButton("+ Add gear", primary = true, modifier = Modifier.fillMaxWidth()) { adding = true }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (rows.isEmpty()) {
                item {
                    Text(
                        "Sleeves, deck boxes, tokens, dice and playmats: say what you have, and decks say what they still need.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.textMuted
                    )
                }
            }
            items(rows, key = { it.key }) { row ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface)
                        .clickable {
                            if (row.items.size == 1 && row.key == row.items[0].id) editing = row.items[0]
                            else open = if (open == row.key) null else row.key
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(row.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.textPrimary, modifier = Modifier.weight(1f))
                        Text(row.value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (row.warn) colors.cut else colors.textPrimary)
                    }
                    Text(row.line, style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                    if (open == row.key) {
                        row.items.forEach { g ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surface2).clickable { editing = g }
                                    .heightIn(min = 40.dp).padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text(g.name, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
                                if (g.gearKind != GearKind.DECK_BOX) Text("×${g.count}", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                            }
                        }
                    }
                }
            }
            if (deck != null) {
                item(key = "needs") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(14.dp)
                    ) {
                        Text("This deck needs", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary, modifier = Modifier.a11yHeading())
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            shown.forEach { d -> PickChip(d.name, d.id == deck.id) { deckId = d.id } }
                        }
                        Text(rememberDeckNeedsLine(deck, collections, decks), style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                    }
                }
            }
        }
    }

    if (adding || editing != null) {
        GearDialog(
            item = editing, collections = collections, decks = decks,
            onDismiss = { adding = false; editing = null },
            onSave = { g -> onChange { saveGear(it, g) }; adding = false; editing = null },
            onDelete = { id -> onChange { deleteGear(it, id) }; adding = false; editing = null }
        )
    }
}

/** "Krenko goblins: 100 sleeves, a deck box and Goblin tokens. You have them all." — the deck's tokens looked up once. */
@Composable
fun rememberDeckNeedsLine(deck: Deck, collections: List<Collection>, decks: List<Deck>): String {
    var tokens by remember(deck.id) { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(deck.id, deck.cards.size) {
        val ids = (listOfNotNull(deck.commander, deck.partnerCommander) + deck.cards).map { it.scryfallId }
        tokens = runCatching { tokensToBring(deck, CardRepository().getCardsByIds(ids).associateBy { it.id }).map { it.name } }.getOrDefault(emptyList())
    }
    return deckNeedsLine(deck, deckNeeds(deck, gearOf(collections), tokens, decks, collections))
}

/** Add a piece of gear, or change one: what it is, its name, how many, what it's on or holds, where it's kept. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GearDialog(
    item: GearItem?,
    collections: List<Collection>,
    decks: List<Deck>,
    onDismiss: () -> Unit,
    onSave: (GearItem) -> Unit,
    onDelete: (String) -> Unit
) {
    val colors = LocalAppColors.current
    val places = placesOf(collections)
    var kind by remember { mutableStateOf(item?.gearKind ?: GearKind.SLEEVES) }
    var name by remember { mutableStateOf(item?.name ?: "") }
    var count by remember { mutableStateOf((item?.count ?: 100).toString()) }
    var usedBy by remember { mutableStateOf(item?.usedBy.orEmpty()) }
    var holds by remember { mutableStateOf(item?.holds) }
    var placeId by remember { mutableStateOf(item?.placeId) }
    val live = decks.filter { it.archived != true && it.sample != true }
    val binders = places.filter { it.placeKind == PlaceKind.BINDER }
    val sleeves = kind == GearKind.SLEEVES || kind == GearKind.INNER_SLEEVES
    val label = name.trim().ifEmpty { if (kind == GearKind.DECK_BOX) "" else kind.label }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.accent, unfocusedBorderColor = colors.border,
        focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary, cursorColor = colors.accent
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(if (item == null) "Add gear" else "Change gear", color = colors.accentLight, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("What it is", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GearKind.entries.forEach { k -> PickChip(k.label, kind == k) { kind = k } }
                }
                OutlinedTextField(
                    name, { name = it },
                    label = { Text(if (kind == GearKind.TOKENS) "Token" else if (kind == GearKind.DECK_BOX) "Deck box" else "Name", color = colors.textMuted) },
                    placeholder = { Text(if (kind == GearKind.TOKENS) "Goblin" else if (kind == GearKind.DECK_BOX) "Red" else if (kind == GearKind.SLEEVES) "Black matte sleeves" else kind.label, color = colors.textDim) },
                    singleLine = true, colors = fieldColors
                )
                if (kind != GearKind.DECK_BOX) {
                    OutlinedTextField(
                        count, { v -> count = v.filter { it.isDigit() } },
                        label = { Text(if (sleeves) "How many left" else "How many", color = colors.textMuted) },
                        singleLine = true, colors = fieldColors, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                if (sleeves) {
                    Text(if (kind == GearKind.INNER_SLEEVES) "Double-sleeving" else "On", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        live.forEach { d -> PickChip(d.name, d.id in usedBy) { usedBy = if (d.id in usedBy) usedBy - d.id else usedBy + d.id } }
                        binders.forEach { p -> PickChip(p.name, p.id in usedBy) { usedBy = if (p.id in usedBy) usedBy - p.id else usedBy + p.id } }
                    }
                }
                if (kind == GearKind.DECK_BOX) {
                    Text("Holds", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PickChip("Nothing — it's empty", holds == null) { holds = null }
                        live.forEach { d -> PickChip(d.name, holds == d.id) { holds = d.id } }
                    }
                }
                if (!sleeves && kind != GearKind.DECK_BOX && places.isNotEmpty()) {
                    Text("Kept in", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PickChip("Not said", placeId == null) { placeId = null }
                        places.forEach { p -> PickChip(p.name, placeId == p.id) { placeId = p.id } }
                    }
                }
                if (item != null) {
                    Text(
                        "Delete", color = colors.error, style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onDelete(item.id) }.padding(vertical = 6.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = label.isNotEmpty(), onClick = {
                onSave(
                    GearItem(
                        id = item?.id ?: UUID.randomUUID().toString(),
                        kind = kind.name,
                        name = label,
                        count = if (kind == GearKind.DECK_BOX) 1 else (count.toIntOrNull() ?: 0),
                        usedBy = if (sleeves) usedBy.ifEmpty { null } else null,
                        holds = if (kind == GearKind.DECK_BOX) holds else null,
                        placeId = if (!sleeves && kind != GearKind.DECK_BOX) placeId else null,
                        note = item?.note,
                        createdAt = item?.createdAt ?: System.currentTimeMillis()
                    )
                )
            }) { Text(if (item == null) "Add" else "Save", color = colors.accentLight) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textMuted) } }
    )
}
