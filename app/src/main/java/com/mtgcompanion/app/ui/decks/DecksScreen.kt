package com.mtgcompanion.app.ui.decks

import com.mtgcompanion.app.ui.common.SyncIconButton
import com.mtgcompanion.app.ui.common.a11yHeading
import com.mtgcompanion.app.ui.common.EmptyAction
import com.mtgcompanion.app.ui.common.EmptyPrompt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.foundation.layout.widthIn
import com.mtgcompanion.app.ui.common.LocalLayoutSize
import com.mtgcompanion.app.ui.common.LayoutSize
import com.mtgcompanion.app.ui.theme.OnGold
import com.mtgcompanion.app.ui.theme.NumberStyle
import com.mtgcompanion.app.ui.theme.LocalAppColors
import com.mtgcompanion.app.ui.common.sharedArt
import com.mtgcompanion.app.ui.common.riseIn
import com.mtgcompanion.app.ui.common.rememberEntranceWindow
import com.mtgcompanion.app.ui.common.pressScale
import com.mtgcompanion.app.ui.common.SharedKeys
import com.mtgcompanion.app.ui.common.SearchPill
import com.mtgcompanion.app.ui.common.PillChip
import com.mtgcompanion.app.ui.common.ManaPips
import com.mtgcompanion.app.ui.common.IdentityStrip
import com.mtgcompanion.app.ui.common.ArtImage
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.deckSections
import com.mtgcompanion.app.data.folderNames
import com.mtgcompanion.app.data.renamedFolder
import com.mtgcompanion.app.data.tidyFolder
import com.mtgcompanion.app.data.withFolder
import com.mtgcompanion.app.data.withoutFolder
import com.mtgcompanion.app.data.isArchived
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Checkbox
import com.mtgcompanion.app.data.DeckOwnership
import com.mtgcompanion.app.data.GameMode
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.common.GameModeDropdown
import com.mtgcompanion.app.ui.common.ManaSymbol
import com.mtgcompanion.app.ui.common.elevatedCard
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldDim
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecksScreen(
    viewModel: DecksViewModel,
    onDeckClick: (String) -> Unit,
    onBrowsePrecons: () -> Unit,
    onNewDeck: () -> Unit,
    /** The empty list's "Paste a list": a first deck from a pasted list (ui/onboarding/WelcomeScreen.kt). */
    onPasteList: (() -> Unit)? = null
) {
    val decks by viewModel.decks.collectAsState()
    val commanderColors by viewModel.commanderColors.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var ownership by rememberSaveable { mutableStateOf<String?>(null) }
    val app = LocalAppColors.current
    val entering = rememberEntranceWindow()

    val shown = decks.filter { deck ->
        (ownership == null || deck.ownershipType.name == ownership) &&
            (query.isBlank() || deck.name.contains(query.trim(), ignoreCase = true) || deck.commander?.name?.contains(query.trim(), ignoreCase = true) == true)
    }
    // Folders and the Archived section (DeckFolders.kt); folded ones show only their heading.
    val sections = deckSections(shown)
    val filed = sections.folders.isNotEmpty() || sections.archived.isNotEmpty()
    var folded by remember { mutableStateOf(setOf("archived")) }
    var makingFolder by remember { mutableStateOf(false) }
    var editingFolder by remember { mutableStateOf<String?>(null) }
    if (makingFolder) {
        NewFolderDialog(
            decks = decks.filterNot { it.isArchived },
            taken = folderNames(decks),
            onDone = { name, ids ->
                viewModel.changeDecks { all -> all.map { if (it.id in ids) it.withFolder(name) else it } }
                makingFolder = false
            },
            onDismiss = { makingFolder = false }
        )
    }
    editingFolder?.let { name ->
        EditFolderDialog(
            name = name,
            onRename = { to -> viewModel.changeDecks { renamedFolder(it, name, to) }; editingFolder = null },
            onDelete = { viewModel.changeDecks { withoutFolder(it, name) }; editingFolder = null },
            onDismiss = { editingFolder = null }
        )
    }

    val layout = LocalLayoutSize.current
    LazyVerticalGrid(
        // Two tiles across on a phone, three on a tablet, as many ~200dp tiles as fit on wider screens.
        columns = when (layout) {
            LayoutSize.PHONE -> GridCells.Fixed(2)
            LayoutSize.TABLET -> GridCells.Fixed(3)
            LayoutSize.DESKTOP -> GridCells.Adaptive(200.dp)
        },
        modifier = Modifier.fillMaxSize().background(Bg),
        contentPadding = PaddingValues(start = layout.pagePadding, end = layout.pagePadding, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(if (layout.isWide) 16.dp else 10.dp),
        verticalArrangement = Arrangement.spacedBy(if (layout.isWide) 16.dp else 10.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 18.dp, bottom = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.riseIn(0)) {
                    Text("Decks", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.a11yHeading().weight(1f).padding(start = 4.dp))
                    SyncIconButton(filled = true)
                    Spacer(Modifier.width(8.dp))
                    if (decks.isNotEmpty()) {
                        Box(
                            Modifier.size(42.dp).clip(CircleShape).background(app.surface).clickable { makingFolder = true },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Filled.CreateNewFolder, contentDescription = "New folder", tint = app.textPrimary, modifier = Modifier.size(20.dp)) }
                        Spacer(Modifier.width(8.dp))
                    }
                    Box(
                        Modifier.size(42.dp).clip(CircleShape).background(app.surface).clickable(onClick = onBrowsePrecons),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Inventory2, contentDescription = "Browse precons", tint = app.textPrimary, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(42.dp).clip(CircleShape).background(app.accent).clickable(onClick = onNewDeck),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Add, contentDescription = "New deck", tint = app.onAccent) }
                }
                if (decks.isNotEmpty()) {
                    SearchPill(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = "Search decks or commanders",
                        modifier = Modifier.then(if (layout.isWide) Modifier.widthIn(max = 520.dp) else Modifier).riseIn(1)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState()).riseIn(2)) {
                        PillChip("All", ownership == null, onClick = { ownership = null }, count = decks.size)
                        DeckOwnership.entries.forEach { type ->
                            val n = decks.count { it.ownershipType == type }
                            if (n > 0) PillChip(type.label, ownership == type.name, onClick = { ownership = if (ownership == type.name) null else type.name }, count = n)
                        }
                    }
                }
            }
        }

        if (decks.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                EmptyPrompt(
                    Icons.Filled.Style,
                    "No decks yet. Paste a list from anywhere, or start from an official precon.",
                    actions = listOf(
                        EmptyAction("Paste a list", Icons.Filled.Edit, onPasteList ?: onNewDeck),
                        EmptyAction("Browse precons", Icons.Filled.Inventory2, onBrowsePrecons)
                    )
                )
            }
        } else if (shown.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "nomatch") {
                EmptyPrompt(
                    Icons.Filled.SearchOff,
                    "No decks match. Try another name, or show all decks.",
                    actions = listOf(EmptyAction("Show all", onClick = { query = ""; ownership = null }))
                )
            }
        }

        // The decks: all together, or folder by folder with the archived ones apart.
        val groups: List<Triple<String?, String, List<Deck>>> = if (!filed) listOf(Triple(null, "", shown)) else {
            sections.folders.map { Triple("folder:" + it.name.lowercase(), it.name, it.decks) } +
                (if (sections.loose.isNotEmpty()) listOf(Triple("loose", "Not in a folder", sections.loose)) else emptyList()) +
                (if (sections.archived.isNotEmpty()) listOf(Triple("archived", "Archived", sections.archived)) else emptyList())
        }
        groups.forEach { (key, title, list) ->
            if (key != null) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "h-$key") {
                    FolderHeader(
                        title = title,
                        count = list.size,
                        archived = key == "archived",
                        open = key !in folded,
                        onToggle = { folded = if (key in folded) folded - key else folded + key },
                        onEdit = if (key.startsWith("folder:")) ({ editingFolder = title }) else null
                    )
                }
            }
            if (key == null || key !in folded) {
                itemsIndexed(list, key = { _, deck -> deck.id }) { index, deck ->
                    // Colourless commander -> a single "C" pip; unknown (not fetched yet) -> none.
                    val colors = commanderColors[deck.id]?.ifEmpty { listOf("C") }.orEmpty()
                    DeckTile(
                        deck = deck,
                        colors = colors,
                        onClick = { onDeckClick(deck.id) },
                        modifier = Modifier.animateItem().riseIn(index + 3, entering)
                    )
                }
            }
        }

        // The two ways to start a deck: the obvious next step when the list is short.
        if (query.isBlank() && ownership == null && !filed) {
            item(key = "scratch") {
                StartTile(
                    icon = Icons.Filled.Add,
                    title = "Start from scratch",
                    subtitle = "Pick a format and a commander",
                    onClick = onNewDeck,
                    modifier = Modifier.riseIn(shown.size + 3, entering)
                )
            }
            item(key = "precons") {
                StartTile(
                    icon = Icons.Filled.Inventory2,
                    title = "Start from a precon",
                    subtitle = "Import any official Commander deck",
                    onClick = onBrowsePrecons,
                    modifier = Modifier.riseIn(shown.size + 4, entering)
                )
            }
        }
    }
}

@Composable
private fun DeckTile(deck: Deck, colors: List<String>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .pressScale(interaction)
            .fillMaxWidth()
            .aspectRatio(0.74f)
            .clip(RoundedCornerShape(22.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        ArtImage(
            model = deck.commander?.imageUrl.toArtCropUrl(),
            seed = deck.name,
            colors = colors,
            contentDescription = deck.commander?.name,
            modifier = Modifier.fillMaxSize().sharedArt(SharedKeys.deckArt(deck.id), RoundedCornerShape(22.dp))
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.3f to Color.Transparent, 0.62f to Color.Black.copy(alpha = 0.35f), 1f to Color.Black.copy(alpha = 0.95f)))
        )
        // Ownership badge, top-left: hidden for Physical decks (the default) so it only draws
        // attention when a deck is not counted toward the collection.
        if (deck.sample == true || deck.ownershipType != DeckOwnership.PHYSICAL) {
            Text(
                if (deck.sample == true) "Sample" else deck.ownershipType.label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 14.dp)
        ) {
            if (deck.tags.isNotEmpty()) {
                Text(
                    deck.tags.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalAppColors.current.accentLight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
            Text(
                deck.name,
                style = MaterialTheme.typography.titleSmall.copy(lineHeight = 18.sp),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                deck.commander?.name ?: "No commander set",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                if (colors.isNotEmpty()) ManaPips(colors, size = 15.dp)
                Spacer(Modifier.weight(1f))
                Text("${deck.cards.sumOf { it.quantity }}", style = NumberStyle(19), color = Color.White)
                Text(" cards", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
            }
        }
        IdentityStrip(colors, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), thickness = 3.dp)
    }
}

/** A tile beside the decks that starts a new one — from scratch or from a precon. */
@Composable
private fun StartTile(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val app = LocalAppColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.74f)
            .clip(RoundedCornerShape(22.dp))
            .background(app.surface)
            .clickable(onClick = onClick)
            .padding(18.dp)
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(app.surface3), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = app.textPrimary)
        }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
}

/** A folder's heading on the decks list (or Archived's): tap to fold it, ⋯ to rename or delete it. */
@Composable
private fun FolderHeader(title: String, count: Int, archived: Boolean, open: Boolean, onToggle: () -> Unit, onEdit: (() -> Unit)?) {
    val app = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable(onClick = onToggle).padding(vertical = 6.dp, horizontal = 4.dp)
        ) {
            Icon(if (archived) Icons.Filled.Archive else Icons.Filled.Folder, contentDescription = null, tint = app.textMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(8.dp))
            Text("$count", style = MaterialTheme.typography.labelLarge, color = app.textMuted)
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (open) "Fold $title" else "Open $title", tint = app.textMuted)
        }
        if (onEdit != null) {
            IconButton(onClick = onEdit) { Icon(Icons.Filled.MoreHoriz, contentDescription = "Rename or delete $title", tint = app.textMuted) }
        }
    }
}

/** A new folder: its name, and the decks that go in it (a folder is there while a deck is in it). */
@Composable
private fun NewFolderDialog(decks: List<Deck>, taken: List<String>, onDone: (String, Set<String>) -> Unit, onDismiss: () -> Unit) {
    val app = LocalAppColors.current
    var name by remember { mutableStateOf("") }
    var ids by remember { mutableStateOf(emptySet<String>()) }
    val exists = taken.any { it.equals(tidyFolder(name), ignoreCase = true) }
    AlertDialog(
        containerColor = app.surface,
        onDismissRequest = onDismiss,
        title = { Text("New folder", color = app.accentLight) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    placeholder = { Text("Name, e.g. Modern", color = app.textDim) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = app.accent, unfocusedBorderColor = app.border, focusedTextColor = app.textPrimary, unfocusedTextColor = app.textPrimary, cursorColor = app.accent),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    if (exists) "There is a folder with this name — the decks you pick join it." else "Pick the decks that go in it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = app.textMuted,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                )
                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    decks.forEach { d ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { ids = if (d.id in ids) ids - d.id else ids + d.id }
                        ) {
                            Checkbox(checked = d.id in ids, onCheckedChange = { ids = if (it) ids + d.id else ids - d.id })
                            Text(d.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            d.folder?.takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = app.textMuted) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onDone(tidyFolder(name), ids) },
                enabled = tidyFolder(name).isNotEmpty() && ids.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = app.accent, contentColor = app.onAccent)
            ) { Text("Make folder") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = app.textMuted) } }
    )
}

/** Renaming a folder, or deleting it — its decks stay, out of any folder. */
@Composable
private fun EditFolderDialog(name: String, onRename: (String) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val app = LocalAppColors.current
    var to by remember { mutableStateOf(name) }
    AlertDialog(
        containerColor = app.surface,
        onDismissRequest = onDismiss,
        title = { Text(name, color = app.accentLight) },
        text = {
            Column {
                OutlinedTextField(
                    value = to,
                    onValueChange = { to = it.take(60) },
                    singleLine = true,
                    label = { Text("Folder name") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = app.accent, unfocusedBorderColor = app.border, focusedTextColor = app.textPrimary, unfocusedTextColor = app.textPrimary, cursorColor = app.accent),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Deleting the folder keeps its decks — they go back to the main list.",
                    style = MaterialTheme.typography.bodySmall,
                    color = app.textMuted,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onRename(to) },
                enabled = tidyFolder(to).isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = app.accent, contentColor = app.onAccent)
            ) { Text("Rename") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Delete folder", color = app.error) }
                TextButton(onClick = onDismiss) { Text("Cancel", color = app.textMuted) }
            }
        }
    )
}
