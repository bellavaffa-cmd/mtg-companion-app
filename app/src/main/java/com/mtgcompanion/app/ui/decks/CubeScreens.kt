package com.mtgcompanion.app.ui.decks

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mtgcompanion.app.data.CUBE_DEFAULT_SIZE
import com.mtgcompanion.app.data.CUBE_GROUPS
import com.mtgcompanion.app.data.CUBE_GROUP_LABELS
import com.mtgcompanion.app.data.CUBE_SIZES
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.CubeBalance
import com.mtgcompanion.app.data.CubeCard
import com.mtgcompanion.app.data.CubeCardState
import com.mtgcompanion.app.data.CubeCardStatus
import com.mtgcompanion.app.data.CubeCount
import com.mtgcompanion.app.data.CubeFilter
import com.mtgcompanion.app.data.CubeLine
import com.mtgcompanion.app.data.CubePacks
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckCardEntry
import com.mtgcompanion.app.data.DeckRepository
import com.mtgcompanion.app.data.PullGroupKind
import com.mtgcompanion.app.data.PullSource
import com.mtgcompanion.app.data.cubeBalance
import com.mtgcompanion.app.data.cubeBoxOf
import com.mtgcompanion.app.data.cubeCardOf
import com.mtgcompanion.app.data.cubeFill
import com.mtgcompanion.app.data.cubeFilterMatches
import com.mtgcompanion.app.data.cubeGroupOf
import com.mtgcompanion.app.data.cubeListText
import com.mtgcompanion.app.data.cubePacks
import com.mtgcompanion.app.data.cubePool
import com.mtgcompanion.app.data.cubePullList
import com.mtgcompanion.app.data.cubeSettings
import com.mtgcompanion.app.data.cubeStatus
import com.mtgcompanion.app.data.isCube
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.common.ConfirmDeleteDialog
import com.mtgcompanion.app.ui.common.PillChip
import com.mtgcompanion.app.ui.common.SectionHeader
import com.mtgcompanion.app.ui.common.SegmentedTabs
import com.mtgcompanion.app.ui.common.a11yHeading
import com.mtgcompanion.app.ui.social.EmptyState
import com.mtgcompanion.app.ui.social.Notice
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/*
 * Cubes (data/Cube.kt): the list of cubes, and one cube — its cards (built from the collection, with
 * cards not owned flagged), its balance, its box (a storage place, filled from the pull list) and its
 * draft (packs dealt from it into a draft or sealed deck). The web app's CubesPage and CubePage
 * (src/pages/CubePage.tsx) show the same.
 */

private fun cardsWord(n: Int) = if (n == 1) "1 card" else "$n cards"

/** The cubes, A–Z, and "New cube". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CubesScreen(deckRepository: DeckRepository, onBack: () -> Unit, onOpenCube: (String) -> Unit) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val all by deckRepository.decksFlow.collectAsState(initial = emptyList())
    val cubes = all.filter { it.isCube }.sortedBy { it.name.lowercase() }
    var making by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("Cubes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.a11yHeading()) },
                navigationIcon = { BackButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth().background(colors.bg).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp)) {
                Button(
                    onClick = { making = true },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("+ New cube", fontWeight = FontWeight.ExtraBold) }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (cubes.isEmpty()) item {
                EmptyState(Icons.Filled.GridView, "No cubes yet. Make one from your own cards — 360, 540, 720 or any size — or paste a list from CubeCobra.")
            }
            items(cubes, key = { it.id }) { c ->
                val s = c.cubeSettings
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).clickable { onOpenCube(c.id) }.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${c.cards.sumOf { it.quantity }} / ${s.size} cards" + if (s.singleton) " · singleton" else "",
                        style = MaterialTheme.typography.bodySmall, color = colors.textMuted
                    )
                }
            }
        }
    }
    if (making) {
        CubeSettingsDialog(
            title = "New cube",
            name = "",
            size = CUBE_DEFAULT_SIZE,
            singleton = true,
            confirm = "Make it",
            onDismiss = { making = false },
            onDone = { name, size, singleton ->
                making = false
                scope.launch { onOpenCube(createCube(deckRepository, name, size, singleton)) }
            }
        )
    }
}

/** Name, size (360, 540, 720 or custom) and singleton — for a new cube, or to change one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CubeSettingsDialog(
    title: String,
    name: String?,
    size: Int,
    singleton: Boolean,
    confirm: String,
    onDismiss: () -> Unit,
    onDone: (name: String, size: Int, singleton: Boolean) -> Unit
) {
    val colors = LocalAppColors.current
    var typed by remember { mutableStateOf(name.orEmpty()) }
    var chosen by remember { mutableStateOf(size) }
    var custom by remember { mutableStateOf(if (size in CUBE_SIZES) "" else size.toString()) }
    var single by remember { mutableStateOf(singleton) }
    val customSize = custom.toIntOrNull()
    val finalSize = if (chosen > 0) chosen else customSize ?: 0
    AlertDialog(
        containerColor = colors.surface,
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (name != null) OutlinedTextField(typed, { typed = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Size", style = MaterialTheme.typography.labelLarge, color = colors.textMuted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CUBE_SIZES.forEach { s -> PillChip("$s", chosen == s, onClick = { chosen = s }) }
                    PillChip("Custom", chosen == 0, onClick = { chosen = 0 })
                }
                if (chosen == 0) OutlinedTextField(
                    custom, { custom = it.filter(Char::isDigit).take(4) }, label = { Text("Cards (40 to 1,500)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Singleton", style = MaterialTheme.typography.bodyLarge)
                        Text("One copy of each card", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    }
                    Switch(single, { single = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onDone(typed, finalSize, single) },
                enabled = finalSize >= 40,
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)
            ) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textMuted) } }
    )
}

private const val TAB_CARDS = 0
private const val TAB_BALANCE = 1
private const val TAB_BOX = 2
private const val TAB_DRAFT = 3

/** One cube: Cards, Balance, Box and Draft. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CubeScreen(
    viewModel: CubeViewModel,
    onBack: () -> Unit,
    /** Share with friends — the deck sharing (ShareDialog); null when the account can't share. */
    onShare: (() -> Unit)?,
    onOpenDeck: (String) -> Unit,
    onOpenPlace: (String) -> Unit,
    onViewCard: (String) -> Unit
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val cubeOrNull by viewModel.cube.collectAsState()
    val collections by viewModel.collections.collectAsState()
    val decks by viewModel.allDecks.collectAsState()
    val cards by viewModel.cards.collectAsState()
    val owned by viewModel.owned.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val importState by viewModel.importState.collectAsState()
    var tab by rememberSaveable { mutableStateOf(TAB_CARDS) }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var addingOwned by remember { mutableStateOf(false) }
    var filling by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    val cube = cubeOrNull
    if (cube == null || !cube.isCube) {
        Scaffold(containerColor = colors.bg, topBar = { TopAppBar(title = { Text("Cube") }, navigationIcon = { BackButton(onClick = onBack) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)) }) { p ->
            Box(Modifier.padding(p)) { EmptyState(Icons.Filled.GridView, "This cube isn't here any more.") }
        }
        return
    }
    val settings = cube.cubeSettings
    val lines = remember(cube.cards, cards) { cube.cards.mapNotNull { e -> cards[e.scryfallId]?.let { CubeLine(cubeCardOf(it), e.quantity) } } }
    val balance = remember(lines, settings) { cubeBalance(lines, settings.size, settings.singleton) }
    val status = remember(cube, collections) { cubeStatus(cube, collections).associateBy { it.scryfallId } }
    val total = cube.cards.sumOf { it.quantity }
    val unknown = cube.cards.count { it.scryfallId !in cards }
    fun added(n: Int, skipped: Int) {
        message = "Added ${cardsWord(n)}" + if (skipped > 0) " — ${cardsWord(skipped)} already in the cube left out" else ""
    }

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(cube.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.a11yHeading())
                        Text("$total / ${settings.size} cards" + if (settings.singleton) " · singleton" else "", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                    }
                },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = {
                    if (onShare != null) IconButton(onClick = onShare) { Icon(Icons.Filled.Share, contentDescription = "Share with friends", tint = colors.textPrimary) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = colors.textPrimary) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                            DropdownMenuItem(text = { Text("Size and singleton") }, onClick = { menu = false; editing = true })
                            DropdownMenuItem(text = { Text("Import a list") }, onClick = { menu = false; importing = true })
                            DropdownMenuItem(text = { Text("Copy the list") }, onClick = {
                                menu = false
                                clipboard.setText(AnnotatedString(cubeListText(cube.cards.map { it.name to it.quantity })))
                                message = "List copied — a card a line, ready for CubeCobra"
                            })
                            DropdownMenuItem(text = { Text("Send the list…") }, onClick = {
                                menu = false
                                val text = cubeListText(cube.cards.map { it.name to it.quantity })
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, cube.name).putExtra(Intent.EXTRA_TEXT, text), cube.name))
                            })
                            DropdownMenuItem(text = { Text("Delete cube", color = colors.error) }, onClick = { menu = false; deleting = true })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "tabs") {
                SegmentedTabs(listOf("Cards", "Balance", "Box", "Draft"), tab, { tab = it }, Modifier.fillMaxWidth())
            }
            message?.let { m -> item(key = "msg") { Notice(m, Modifier.clickable { message = null }) } }
            if (loading && unknown > 0) item(key = "loading") {
                Text("Looking up ${cardsWord(unknown)} on Scryfall…", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
            }
            when (tab) {
                TAB_CARDS -> cardsTab(
                    cube, cards, status, query, { query = it },
                    onAddOwned = { addingOwned = true }, onFill = { filling = true }, onSearch = { searching = true }, onImport = { importing = true },
                    onRemove = { viewModel.remove(it) }, onProxy = { id, on -> viewModel.setProxy(id, on) }, onView = onViewCard
                )
                TAB_BALANCE -> balanceTab(balance, unknown)
                TAB_BOX -> boxTab(cube, collections, decks, status.values.toList(), viewModel, onOpenPlace) { message = it }
                TAB_DRAFT -> item(key = "draft") { DraftPanel(cube, onStart = { ids, name, note -> viewModel.startLimited(ids, name, note, onOpenDeck) }, onSave = viewModel::setPacks, onCopy = { text -> clipboard.setText(AnnotatedString(text)); message = "Pack lists copied" }) }
            }
        }
    }

    if (renaming) {
        var name by remember { mutableStateOf(cube.name) }
        AlertDialog(
            containerColor = colors.surface,
            onDismissRequest = { renaming = false },
            title = { Text("Rename cube") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { Button(onClick = { viewModel.rename(name); renaming = false }, enabled = name.isNotBlank()) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel", color = colors.textMuted) } }
        )
    }
    if (editing) {
        CubeSettingsDialog("Size and singleton", null, settings.size, settings.singleton, "Save", { editing = false }) { _, size, single ->
            viewModel.setSettings(size, single)
            editing = false
        }
    }
    if (deleting) {
        ConfirmDeleteDialog(
            title = "Delete ${cube.name}?",
            message = "The cube's list goes. The cards stay in your collection — the ones in the cube box stay there.",
            onConfirm = { deleting = false; viewModel.delete(onBack) },
            onDismiss = { deleting = false }
        )
    }
    if (addingOwned) {
        AddFromCollectionDialog(cube, owned, cards, onDismiss = { addingOwned = false }) { picked ->
            addingOwned = false
            viewModel.add(picked, ::added)
        }
    }
    if (filling) {
        val ownedFacts = remember(owned, cards) { owned.mapNotNull { e -> cards[e.scryfallId]?.let { cubeCardOf(it) } } }
        val suggestions = remember(lines, ownedFacts, settings) { cubeFill(lines, ownedFacts, settings.size) }
        FillDialog(suggestions, unknown, onDismiss = { filling = false }) { picked ->
            filling = false
            val byId = owned.associateBy { it.scryfallId }
            viewModel.add(picked.mapNotNull { byId[it.id]?.copy(quantity = 1) }, ::added)
        }
    }
    if (searching) {
        SearchCardsDialog(cube, onDismiss = { searching = false }) { card -> viewModel.addCard(card, ::added) }
    }
    if (importing) {
        ImportCubeDialog(importState, onImport = viewModel::importList, onDismiss = { importing = false; viewModel.clearImport() })
    }
}

private fun stateLine(s: CubeCardStatus?): String = when (s?.state) {
    CubeCardState.IN_BOX -> "In the cube box"
    CubeCardState.OWNED -> "Owned · ${s?.where.orEmpty()}".trimEnd(' ', '·')
    CubeCardState.PROXY -> "Proxy"
    CubeCardState.NOT_OWNED -> "Not owned"
    null -> ""
}

@OptIn(ExperimentalLayoutApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.cardsTab(
    cube: Deck,
    cards: Map<String, ScryfallCard>,
    status: Map<String, CubeCardStatus>,
    query: String,
    onQuery: (String) -> Unit,
    onAddOwned: () -> Unit,
    onFill: () -> Unit,
    onSearch: () -> Unit,
    onImport: () -> Unit,
    onRemove: (String) -> Unit,
    onProxy: (String, Boolean) -> Unit,
    onView: (String) -> Unit
) {
    item(key = "add") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onAddOwned) { Text("Add from collection") }
            OutlinedButton(onClick = onFill) { Text("Fill from collection") }
            OutlinedButton(onClick = onSearch) { Text("Any card") }
            OutlinedButton(onClick = onImport) { Text("Import a list") }
        }
    }
    if (cube.cards.isEmpty()) {
        item(key = "empty") { EmptyState(Icons.Filled.GridView, "No cards yet. Add them from your collection, let it fill by colour, or import a list.") }
        return
    }
    if (cube.cards.size > 10) item(key = "q") {
        OutlinedTextField(query, onQuery, placeholder = { Text("Find in the cube") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
    val shown = cube.cards.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    val groups = shown.groupBy { e -> cards[e.scryfallId]?.let { cubeGroupOf(cubeCardOf(it)) } ?: "?" }
    (CUBE_GROUPS + "?").forEach { g ->
        val inGroup = groups[g].orEmpty().sortedBy { it.name.lowercase() }
        if (inGroup.isEmpty()) return@forEach
        item(key = "h-$g") { SectionHeader("${CUBE_GROUP_LABELS[g] ?: "Looking up"} · ${inGroup.sumOf { it.quantity }}") }
        items(inGroup, key = { "c-${it.scryfallId}" }) { e -> CubeCardRow(e, status[e.scryfallId], onRemove, onProxy, onView) }
    }
}

@Composable
private fun CubeCardRow(e: DeckCardEntry, s: CubeCardStatus?, onRemove: (String) -> Unit, onProxy: (String, Boolean) -> Unit, onView: (String) -> Unit) {
    val colors = LocalAppColors.current
    val st = s?.state
    val tint = when (st) {
        CubeCardState.IN_BOX -> colors.success
        CubeCardState.PROXY -> colors.accent
        CubeCardState.NOT_OWNED -> colors.warning
        else -> colors.textMuted
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).clickable { onView(e.name) }.padding(start = 14.dp, top = 6.dp, bottom = 6.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text((if (e.quantity > 1) "${e.quantity}× " else "") + e.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stateLine(s), style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (st == CubeCardState.NOT_OWNED || st == CubeCardState.PROXY) {
            TextButton(onClick = { onProxy(e.scryfallId, st == CubeCardState.NOT_OWNED) }) {
                Text(if (st == CubeCardState.NOT_OWNED) "Mark proxy" else "Not a proxy", color = colors.accent)
            }
        }
        IconButton(onClick = { onRemove(e.scryfallId) }) { Icon(Icons.Filled.Close, contentDescription = "Take ${e.name} out of the cube", tint = colors.textMuted) }
    }
}

// ---- Balance ----

@Composable
private fun CountBar(c: CubeCount) {
    val colors = LocalAppColors.current
    val target = c.target
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(c.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(if (target != null) "${c.count} / $target" else "${c.count}", style = MaterialTheme.typography.labelLarge, color = colors.textMuted)
        }
        if (target != null && target > 0) {
            LinearProgressIndicator(
                progress = { (c.count.toFloat() / target).coerceIn(0f, 1f) },
                color = if (c.count > target) colors.warning else colors.accent,
                trackColor = colors.surface2,
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.balanceTab(b: CubeBalance, unknown: Int) {
    if (unknown > 0) item(key = "unk") { Notice("${cardsWord(unknown)} not looked up yet — they're left out of the balance until they are.") }
    if (b.warnings.isNotEmpty()) item(key = "warn") { Notice(b.warnings.joinToString("\n"), warn = true) }
    else item(key = "ok") { Notice("Balanced for ${b.size} cards.") }
    item(key = "h-col") { SectionHeader("Colours") }
    items(b.groups, key = { "g-${it.key}" }) { CountBar(it) }
    item(key = "h-curve") { SectionHeader("Curve · average ${"%.2f".format(java.util.Locale.UK, b.averageMv)}") }
    item(key = "curve") {
        val colors = LocalAppColors.current
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            b.curve.forEach { c ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(colors.surface).padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(c.label, style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                    Text("${c.count}", style = MaterialTheme.typography.titleMedium)
                    Text("of ${c.target}", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                }
            }
        }
    }
    item(key = "h-types") { SectionHeader("Types") }
    items(b.types, key = { "t-${it.key}" }) { CountBar(it) }
    item(key = "h-roles") { SectionHeader("Roles") }
    items(b.roles, key = { "r-${it.key}" }) { CountBar(it) }
    item(key = "roles-note") {
        Text(
            "Roles are read from each card's rules text: removal, fixing (lands and spells that make or find more than one colour), card draw and counterspells.",
            style = MaterialTheme.typography.labelSmall, color = LocalAppColors.current.textMuted
        )
    }
}

// ---- The cube box ----

private fun androidx.compose.foundation.lazy.LazyListScope.boxTab(
    cube: Deck,
    collections: List<com.mtgcompanion.app.data.Collection>,
    decks: List<Deck>,
    status: List<CubeCardStatus>,
    viewModel: CubeViewModel,
    onOpenPlace: (String) -> Unit,
    say: (String) -> Unit
) {
    val box = cubeBoxOf(cube, collections)
    val inBox = status.sumOf { it.inBox }
    val proxies = status.filter { it.state == CubeCardState.PROXY }.sumOf { it.qty }
    val notOwned = status.filter { it.state == CubeCardState.NOT_OWNED }.sumOf { it.qty - it.inBox }
    item(key = "box-head") {
        val colors = LocalAppColors.current
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (box == null) {
                Text(
                    if (cube.cube?.boxPlaceId != null) "The cube box isn't one of your storage places any more (deleted, or the collection was reset). Make it again to keep track of the cards in it."
                    else "Keep the cube's cards together: the cube box is a storage place, so every owned card in it says where it is. The pull list fetches the rest from wherever they are now.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = { viewModel.makeBox() }, colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)) {
                    Icon(Icons.Filled.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp)); Text("  Make the cube box")
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(box.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onOpenPlace(box.id) }) { Text("Open", color = colors.accent) }
                }
            }
            Text(
                listOf("$inBox in the box", "${status.filter { it.state == CubeCardState.OWNED }.sumOf { it.qty - it.inBox }} to pull", "$proxies proxies", "$notOwned not owned").joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = colors.textMuted
            )
        }
    }
    if (box == null) return
    item(key = "pull") { CubePullPanel(cube, collections, decks, viewModel, say) }
}

@Composable
private fun CubePullPanel(cube: Deck, collections: List<com.mtgcompanion.app.data.Collection>, decks: List<Deck>, viewModel: CubeViewModel, say: (String) -> Unit) {
    val colors = LocalAppColors.current
    val list = remember(cube, collections, decks) { cubePullList(cube, collections, decks) }
    var ticked by remember(list) { mutableStateOf(setOf<String>()) }
    val movable = list.groups.filter { it.kind == PullGroupKind.PLACE || it.kind == PullGroupKind.LOOSE }.flatMap { it.rows }
    val chosen = movable.filter { it.key in ticked }.sumOf { it.qty }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (list.groups.isEmpty()) {
            Notice("Every owned card is in the box.")
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pull list", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).a11yHeading())
            if (movable.isNotEmpty()) TextButton(onClick = { ticked = if (ticked.size == movable.size) emptySet() else movable.map { it.key }.toSet() }) {
                Text(if (ticked.size == movable.size) "Untick all" else "Tick all", color = colors.accent)
            }
        }
        list.groups.forEach { g ->
            Text(g.title, style = MaterialTheme.typography.labelLarge, color = colors.accentLight, modifier = Modifier.padding(top = 6.dp))
            if (g.detail.isNotEmpty()) Text(g.detail, style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
            g.rows.forEach { r ->
                val canMove = r.source is PullSource.Place || r.source is PullSource.Loose
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                    if (canMove) Checkbox(r.key in ticked, { on -> ticked = if (on) ticked + r.key else ticked - r.key })
                    else Box(Modifier.width(48.dp))
                    Column(Modifier.weight(1f)) {
                        Text((if (r.qty > 1) "${r.qty}× " else "") + r.name, style = MaterialTheme.typography.bodyMedium)
                        r.hint?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = colors.textMuted) }
                        if (r.source is PullSource.InDeck) Text("Stays in that deck — swap a copy in when you have one", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                    }
                }
            }
        }
        Button(
            onClick = { viewModel.moveIntoBox(list, ticked) { n -> ticked = emptySet(); say("Moved ${if (n == 1) "1 copy" else "$n copies"} into the cube box") } },
            enabled = chosen > 0,
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (chosen > 0) "Move $chosen into the cube box" else "Tick what you've pulled") }
    }
}

// ---- Draft ----

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    val colors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange(value - 1) }, enabled = value > range.first, modifier = Modifier.minimumInteractiveComponentSize()) {
            Icon(Icons.Filled.Remove, contentDescription = "Fewer: $label", tint = colors.textMuted)
        }
        Text("$value", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(36.dp))
        IconButton(onClick = { onChange(value + 1) }, enabled = value < range.last, modifier = Modifier.minimumInteractiveComponentSize()) {
            Icon(Icons.Filled.Add, contentDescription = "More: $label", tint = colors.textMuted)
        }
    }
}

/** The pack lists as text: "Seat 1 — pack 1: A, B, C". */
private fun packText(name: String, dealt: CubePacks, names: Map<String, String>): String =
    buildString {
        appendLine(name)
        dealt.seats.forEachIndexed { s, packs ->
            packs.forEachIndexed { p, pack -> appendLine("Seat ${s + 1} — pack ${p + 1}: " + pack.joinToString(", ") { names[it] ?: it }) }
        }
    }.trim()

@Composable
private fun DraftPanel(cube: Deck, onStart: (ids: List<String>, name: String, note: String) -> Unit, onSave: (Int, Int, Int) -> Unit, onCopy: (String) -> Unit) {
    val colors = LocalAppColors.current
    val (s0, p0, n0) = cube.cubeSettings.packsOr()
    var seats by rememberSaveable { mutableStateOf(s0) }
    var packs by rememberSaveable { mutableStateOf(p0) }
    var size by rememberSaveable { mutableStateOf(n0) }
    var seed by rememberSaveable { mutableStateOf<Int?>(null) }
    val pool = remember(cube.cards) { cubePool(cube) }
    val names = remember(cube.cards) { cube.cards.associate { it.scryfallId to it.name } }
    val dealt = seed?.let { remember(pool, seats, packs, size, it) { cubePacks(pool, seats, packs, size, it) } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Deal packs from the cube for your pod: each seat gets its packs, to pull from the cube box. Then start a draft or sealed deck — your pool goes in, and the deck's draft and sealed tools take it from there.",
            style = MaterialTheme.typography.bodyMedium
        )
        Stepper("Seats (pod size)", seats, 2..16) { seats = it }
        Stepper("Packs a seat", packs, 1..6) { packs = it }
        Stepper("Cards a pack", size, 5..20) { size = it }
        Text("${cardsWord(pool.size)} in the cube · ${seats * packs * size} needed", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
        Button(
            onClick = { seed = Random.nextInt(); onSave(seats, packs, size) },
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (dealt == null) "Deal packs" else "Shuffle and deal again") }
        if (dealt != null && dealt.short > 0) Notice("The cube is ${cardsWord(dealt.short)} short of ${dealt.needed} — fewer seats, packs or cards a pack, or add cards.", warn = true)
        if (dealt != null && dealt.short == 0) {
            OutlinedButton(onClick = {
                onStart(dealt.seats.flatten().flatten(), "${cube.name} draft", "Drafted from the cube ${cube.name}, $seats seats × $packs packs of $size. The pool holds every card dealt: move your picks into the main deck.")
            }, modifier = Modifier.fillMaxWidth()) { Text("Start a draft deck") }
            OutlinedButton(onClick = { onCopy(packText(cube.name, dealt, names)) }, modifier = Modifier.fillMaxWidth()) { Text("Copy pack lists") }
            dealt.seats.forEachIndexed { s, seatPacks ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Seat ${s + 1}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            onStart(seatPacks.flatten(), "${cube.name} sealed — seat ${s + 1}", "Sealed from the cube ${cube.name}: seat ${s + 1}'s $packs packs of $size.")
                        }) { Text("Sealed deck", color = colors.accent) }
                    }
                    seatPacks.forEachIndexed { p, pack ->
                        Text("Pack ${p + 1}: " + pack.joinToString(", ") { names[it] ?: it }, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    }
                }
            }
        }
    }
}

// ---- Adding cards ----

private val RARITIES = listOf("common", "uncommon", "rare", "mythic")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddFromCollectionDialog(cube: Deck, owned: List<DeckCardEntry>, cards: Map<String, ScryfallCard>, onDismiss: () -> Unit, onAdd: (List<DeckCardEntry>) -> Unit) {
    val colors = LocalAppColors.current
    var filter by remember { mutableStateOf(CubeFilter()) }
    var minText by remember { mutableStateOf("") }
    var maxText by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(setOf<String>()) }
    val inCube = remember(cube.cards) { cube.cards.map { it.name.lowercase() }.toSet() }
    val f = filter.copy(minUsd = minText.toDoubleOrNull(), maxUsd = maxText.toDoubleOrNull())
    val facts = remember(owned, cards) { owned.mapNotNull { e -> cards[e.scryfallId]?.let { e.scryfallId to cubeCardOf(it) } }.toMap() }
    val shown = remember(owned, facts, f, inCube) {
        owned.filter { it.name.lowercase() !in inCube }
            .filter { e ->
                // A card not looked up yet only shows while nothing but its name is asked.
                facts[e.scryfallId]?.let { cubeFilterMatches(f, it) }
                    ?: (f.copy(query = "") == CubeFilter() && (f.query.isBlank() || e.name.contains(f.query.trim(), ignoreCase = true)))
            }
            .distinctBy { it.name.lowercase() }
            .sortedBy { it.name.lowercase() }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(colors.bg).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Add from collection", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).a11yHeading())
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            }
            OutlinedTextField(filter.query, { filter = filter.copy(query = it) }, placeholder = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CUBE_GROUPS.forEach { g ->
                    FilterChip(g in filter.groups, { filter = filter.copy(groups = if (g in filter.groups) filter.groups - g else filter.groups + g) }, label = { Text(CUBE_GROUP_LABELS.getValue(g)) })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RARITIES.forEach { r ->
                    FilterChip(r in filter.rarities, { filter = filter.copy(rarities = if (r in filter.rarities) filter.rarities - r else filter.rarities + r) }, label = { Text(r.replaceFirstChar { it.uppercase() }) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(filter.type, { filter = filter.copy(type = it) }, placeholder = { Text("Type") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(filter.set, { filter = filter.copy(set = it) }, placeholder = { Text("Set code") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(minText, { minText = it }, placeholder = { Text("Min $") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                OutlinedTextField(maxText, { maxText = it }, placeholder = { Text("Max $") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${shown.size} of your cards", style = MaterialTheme.typography.labelMedium, color = colors.textMuted, modifier = Modifier.weight(1f))
                TextButton(onClick = { picked = if (picked.size == shown.size) emptySet() else shown.map { it.scryfallId }.toSet() }) {
                    Text(if (shown.isNotEmpty() && picked.size == shown.size) "Untick all" else "Tick all", color = colors.accent)
                }
            }
            LazyColumn(Modifier.weight(1f)) {
                items(shown, key = { it.scryfallId }) { e ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { picked = if (e.scryfallId in picked) picked - e.scryfallId else picked + e.scryfallId }) {
                        Checkbox(e.scryfallId in picked, { on -> picked = if (on) picked + e.scryfallId else picked - e.scryfallId })
                        Text(e.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        cards[e.scryfallId]?.prices?.usd?.let { Text("$$it", style = MaterialTheme.typography.labelSmall, color = colors.textMuted) }
                    }
                }
            }
            Button(
                onClick = { onAdd(shown.filter { it.scryfallId in picked }.map { it.copy(quantity = 1) }) },
                enabled = picked.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Add ${cardsWord(shown.count { it.scryfallId in picked })}") }
        }
    }
}

@Composable
private fun FillDialog(suggestions: List<CubeCard>, unknown: Int, onDismiss: () -> Unit, onAdd: (List<CubeCard>) -> Unit) {
    val colors = LocalAppColors.current
    var off by remember(suggestions) { mutableStateOf(setOf<String>()) }
    val chosen = suggestions.filter { it.id !in off }
    AlertDialog(
        containerColor = colors.surface,
        onDismissRequest = onDismiss,
        title = { Text("Fill from collection") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (suggestions.isEmpty()) "Nothing to suggest: the cube is full, its colours are at their targets, or none of your cards fits." + if (unknown > 0) " Some cards are still being looked up." else ""
                    else "Your cards for the colours that are short, a colour at a time, the rarest and dearest first. Untick any you'd rather not.",
                    style = MaterialTheme.typography.bodyMedium
                )
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(suggestions, key = { it.id }) { c ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { off = if (c.id in off) off - c.id else off + c.id }) {
                            Checkbox(c.id !in off, { on -> off = if (on) off - c.id else off + c.id })
                            Text(c.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(CUBE_GROUP_LABELS[cubeGroupOf(c)].orEmpty(), style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(chosen) }, enabled = chosen.isNotEmpty(), colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)) {
                Text("Add ${cardsWord(chosen.size)}")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textMuted) } }
    )
}

/** Any card, from Scryfall: one not owned goes in flagged "Not owned", to be bought or proxied. */
@Composable
private fun SearchCardsDialog(cube: Deck, onDismiss: () -> Unit, onAdd: (ScryfallCard) -> Unit) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ScryfallCard>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val inCube = cube.cards.map { it.name.lowercase() }.toSet()
    fun search() {
        if (query.isBlank()) return
        busy = true
        error = null
        scope.launch {
            val page = runCatching { withContext(Dispatchers.IO) { CardRepository().search(query.trim()) } }.getOrNull()
            if (page == null) error = "Couldn't search — check your connection." else results = page.cards.take(60)
            busy = false
        }
    }
    AlertDialog(
        containerColor = colors.surface,
        onDismissRequest = onDismiss,
        title = { Text("Add any card") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(query, { query = it }, placeholder = { Text("Name or Scryfall search") }, singleLine = true, modifier = Modifier.weight(1f))
                    TextButton(onClick = { search() }, enabled = !busy) { Text("Search", color = colors.accent) }
                }
                Text("Cards you don't own go in as “Not owned” — mark them as proxies, or buy them.", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                error?.let { Text(it, color = colors.error, style = MaterialTheme.typography.labelMedium) }
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(results, key = { it.id }) { c ->
                        val there = c.name.lowercase() in inCube
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(listOfNotNull(c.typeLine, c.set?.uppercase()).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (there) Text("In the cube", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                            else IconButton(onClick = { onAdd(c) }) { Icon(Icons.Filled.Add, contentDescription = "Add ${c.name}", tint = colors.accent) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = colors.accent) } }
    )
}

/** "Import a list": pasted, or from a file — CubeCobra's plain text or CSV, or a "2 Name" list. */
@Composable
private fun ImportCubeDialog(state: CubeImportState, onImport: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var fileError by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val read = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().takeIf { b -> b.size <= 5_000_000 }?.toString(Charsets.UTF_8) } }.getOrNull()
            }
            if (read == null) fileError = "That file couldn't be read (5 MB at most)." else { fileError = null; text = read }
        }
    }
    AlertDialog(
        containerColor = colors.surface,
        onDismissRequest = { if (!state.running) onDismiss() },
        title = { Text("Import a cube list") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    state.running -> {
                        Text("Finding ${state.done} of ${state.total} cards…", style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(progress = { if (state.total == 0) 0f else state.done.toFloat() / state.total }, modifier = Modifier.fillMaxWidth())
                    }
                    state.added != null -> {
                        Text(
                            "Added ${cardsWord(state.added)}." + (if (state.skipped > 0) " ${cardsWord(state.skipped)} already in the cube left out." else ""),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (state.missing.isNotEmpty()) Text("Not found: " + state.missing.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = colors.warning)
                    }
                    else -> {
                        Text("Paste CubeCobra's plain text (a card a line), its CSV export, or a list like “2 Lightning Bolt”.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                        OutlinedTextField(text, { text = it }, placeholder = { Text("Sol Ring\nLightning Bolt\n…") }, modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 260.dp))
                        TextButton(onClick = { picker.launch(arrayOf("text/*", "application/csv", "application/octet-stream")) }) { Text("Load a file", color = colors.accent) }
                        fileError?.let { Text(it, color = colors.error, style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
        },
        confirmButton = {
            if (state.added != null) Button(onClick = onDismiss) { Text("Done") }
            else Button(onClick = { onImport(text) }, enabled = text.isNotBlank() && !state.running, colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)) { Text("Import") }
        },
        dismissButton = { if (!state.running && state.added == null) TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textMuted) } }
    )
}
