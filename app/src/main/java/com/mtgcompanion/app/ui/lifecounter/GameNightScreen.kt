package com.mtgcompanion.app.ui.lifecounter

import androidx.compose.foundation.background
import com.mtgcompanion.app.ui.common.a11yHeading
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.social.ShareKind
import com.mtgcompanion.app.data.social.SharedSummary
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.common.PillChip
import com.mtgcompanion.app.ui.common.SectionHeader
import com.mtgcompanion.app.ui.common.SegmentedTabs
import com.mtgcompanion.app.ui.theme.LocalAppColors

/**
 * Game night: who's here and what they're playing, fair pods by power bracket (not last night's
 * pairings, where that can be helped), and each pod's game started on the life counter with its
 * players seated — the user's game saving to the deck they picked. Reached from the Play tab. The
 * web app's src/lifecounter/GameNightPage.tsx.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GameNightScreen(viewModel: GameNightViewModel, onBack: () -> Unit, onOpenLifeCounter: () -> Unit, onOpenPack: (() -> Unit)? = null) {
    val colors = LocalAppColors.current
    val saved by viewModel.nights.collectAsState()
    val decks by viewModel.decks.collectAsState()
    val overview by viewModel.overview.collectAsState()
    val tableGames by viewModel.tableGames.collectAsState()
    val suggesting by viewModel.suggesting.collectAsState()
    val night = saved.current
    val previousPairs = remember(saved.previous) { pairingsOf(saved.previous) }
    var guest by remember { mutableStateOf("") }

    val me = night.players.firstOrNull { it.kind == NightPlayerKind.ME }
    val friends = overview?.let { o ->
        o.acceptedFriends.mapNotNull { o.person(it.userId) }
            .filter { f -> night.players.none { it.userId == f.userId } }
            .sortedBy { it.displayName.lowercase() }
    }.orEmpty()
    val waiting = unseated(night)

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("Game night", style = MaterialTheme.typography.titleLarge, modifier = Modifier.a11yHeading()) },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = {
                    if (onOpenPack != null) {
                        androidx.compose.material3.TextButton(onClick = onOpenPack) {
                            Icon(Icons.Filled.Backpack, contentDescription = null, tint = colors.accentLight)
                            Text("Pack your bag", color = colors.accentLight, modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        }
    ) { padding ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
        ) {
            item {
                Text(
                    "Who's here and what they're playing, split into fair pods by power bracket. Start a pod's game on the life counter; your result saves to your deck.",
                    style = MaterialTheme.typography.bodySmall, color = colors.textMuted
                )
            }
            item {
                SegmentedTabs(
                    labels = listOf("Commander", "1v1"),
                    selected = if (night.format == NightFormat.DUEL) 1 else 0,
                    onSelect = { viewModel.setFormat(if (it == 1) NightFormat.DUEL else NightFormat.COMMANDER) }
                )
            }
            item {
                SectionHeader(
                    "Who's here · ${night.players.size}",
                    modifier = Modifier.padding(top = 6.dp),
                    action = if (night.pods.isNotEmpty()) "New night" else null,
                    onAction = { viewModel.startNewNight() }
                )
            }
            items(night.players, key = { it.id }) { p ->
                PlayerCard(
                    player = p,
                    decks = decks,
                    sharedDecks = p.userId?.let { id -> overview?.sharedWithMe?.filter { it.kind == ShareKind.DECK && it.owner == id } }.orEmpty(),
                    suggesting = suggesting,
                    onPickDeck = { viewModel.pickDeck(p.id, it) },
                    onSuggest = { viewModel.suggest(p.id) },
                    onChange = { transform -> viewModel.setPlayer(p.id, transform) },
                    onRemove = { viewModel.removePlayer(p.id) }
                )
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (me == null) PillChip("+ Me", selected = false, onClick = { viewModel.addMe() })
                    friends.forEach { f -> PillChip("+ ${f.displayName.ifBlank { f.username }}", selected = false, onClick = { viewModel.addFriend(f) }) }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NightTextField(guest, { guest = it }, "Add a guest by name", Modifier.weight(1f))
                    OutlinedButton(onClick = { viewModel.addGuest(guest); guest = "" }, enabled = guest.isNotBlank()) { Text("Add") }
                }
            }
            item {
                SectionHeader(
                    "Pods",
                    modifier = Modifier.padding(top = 10.dp),
                    action = if (night.pods.isNotEmpty()) "Reshuffle" else null,
                    onAction = { viewModel.makePods() }
                )
            }
            if (night.pods.isEmpty()) {
                item {
                    Text(
                        (if (night.format == NightFormat.DUEL) "Pairs" else "Pods of 3–4") +
                            ", players close in power together, and not the same tables as last night where that can be helped.",
                        style = MaterialTheme.typography.bodySmall, color = colors.textMuted
                    )
                }
                item {
                    Button(
                        onClick = { viewModel.makePods() },
                        enabled = night.players.size >= 2,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)
                    ) {
                        Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Make pods", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            } else {
                itemsIndexed(night.pods, key = { _, pod -> pod.id }) { i, pod ->
                    val keys = pod.playerIds.mapNotNull { id -> night.players.firstOrNull { it.id == id } }.map { playerKey(it) }
                    PodCard(
                        pod = pod,
                        index = i,
                        night = night,
                        repeats = repeatsIn(keys, previousPairs),
                        result = podWinner(pod, night.players, tableGames),
                        onMove = { playerId, to -> viewModel.move(playerId, to) },
                        onStart = { viewModel.start(pod); onOpenLifeCounter() },
                        onWinner = { viewModel.tapWinner(pod, it) }
                    )
                }
                if (waiting.isNotEmpty()) {
                    item {
                        Card {
                            Text("Not in a pod yet", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                            waiting.forEach { p ->
                                SeatRow(p.name, null, podCount = night.pods.size, current = -1) { to -> viewModel.move(p.id, to) }
                            }
                        }
                    }
                }
            }
            item { Box(Modifier.height(24.dp)) }
        }
    }
}

private fun kindLabel(kind: NightPlayerKind) = when (kind) {
    NightPlayerKind.ME -> "You"
    NightPlayerKind.FRIEND -> "Friend"
    NightPlayerKind.GUEST -> "Guest"
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp)
    ) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerCard(
    player: NightPlayer,
    decks: List<Deck>,
    sharedDecks: List<SharedSummary>,
    suggesting: Boolean,
    onPickDeck: (Deck?) -> Unit,
    onSuggest: () -> Unit,
    onChange: ((NightPlayer) -> NightPlayer) -> Unit,
    onRemove: () -> Unit
) {
    val colors = LocalAppColors.current
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(player.name, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(kindLabel(player.kind).uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.textDim)
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = "Remove ${player.name}", tint = colors.textMuted) }
        }
        if (player.kind == NightPlayerKind.ME) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val sorted = decks.sortedBy { it.name.lowercase() }
                Picker(
                    shown = player.deck ?: "No deck",
                    options = listOf<Pair<String?, String>>(null to "No deck") + sorted.map { it.id to it.name },
                    onPick = { id -> onPickDeck(decks.firstOrNull { it.id == id }) },
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = onSuggest, enabled = !suggesting && decks.isNotEmpty()) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(if (suggesting) "Looking…" else "Suggest", modifier = Modifier.padding(start = 6.dp))
                }
            }
            player.commander?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textMuted) }
        } else {
            if (sharedDecks.isNotEmpty()) {
                Picker(
                    shown = if (player.sharedDeckId != null) player.deck ?: "Untitled deck" else "Not one they share",
                    options = listOf<Pair<String?, String>>(null to "Not one they share") + sharedDecks.map { it.itemId to (it.name ?: "Untitled deck") },
                    onPick = { id ->
                        val shared = sharedDecks.firstOrNull { it.itemId == id }
                        onChange { it.copy(sharedDeckId = shared?.itemId, deck = shared?.name) }
                    }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (player.sharedDeckId == null) {
                    NightTextField(player.deck.orEmpty(), { v -> onChange { it.copy(deck = v.ifEmpty { null }) } }, "Deck", Modifier.weight(1f))
                }
                NightTextField(player.commander.orEmpty(), { v -> onChange { it.copy(commander = v.ifEmpty { null }) } }, "Commander", Modifier.weight(1f))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            Text("Bracket", style = MaterialTheme.typography.bodySmall, color = colors.textMuted, modifier = Modifier.align(Alignment.CenterVertically).padding(end = 4.dp))
            listOf<Int?>(null, 1, 2, 3, 4, 5).forEach { b ->
                PillChip(b?.toString() ?: "?", selected = player.bracket == b, onClick = { onChange { it.copy(bracket = b) } })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PodCard(
    pod: NightPod,
    index: Int,
    night: GameNight,
    repeats: Int,
    result: PodResult?,
    onMove: (playerId: String, to: Int) -> Unit,
    onStart: () -> Unit,
    onWinner: (String) -> Unit
) {
    val colors = LocalAppColors.current
    val seated = pod.playerIds.mapNotNull { id -> night.players.firstOrNull { it.id == id } }
    val winner = result?.winnerId?.let { id -> seated.firstOrNull { it.id == id } }
    val me = seated.firstOrNull { it.kind == NightPlayerKind.ME }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pod ${index + 1}", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
            Text(
                "bracket ${podPower(pod, night.players)}" + if (repeats > 0) " · ${if (repeats == 1) "1 pairing" else "$repeats pairings"} from last time" else "",
                style = MaterialTheme.typography.bodySmall, color = colors.textMuted
            )
        }
        seated.forEach { p ->
            val detail = listOfNotNull(p.commander ?: p.deck, p.bracket?.let { "bracket $it" }).joinToString(" · ").ifEmpty { "No deck yet" }
            SeatRow(p.name, detail, podCount = night.pods.size, current = index) { to -> onMove(p.id, to) }
        }
        if (result?.fromTable == true) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = if (winner != null) colors.accent else colors.textDim, modifier = Modifier.size(18.dp))
                Text(winner?.let { "${it.name} won" } ?: "Nobody left standing", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
            }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    winner?.let { "${it.name} won" } ?: if (pod.startedAt != null) "Who won?" else "Winner",
                    style = MaterialTheme.typography.bodySmall, color = colors.textMuted,
                    modifier = Modifier.align(Alignment.CenterVertically).padding(end = 4.dp)
                )
                seated.forEach { p -> PillChip(p.name, selected = pod.winnerId == p.id, onClick = { onWinner(p.id) }) }
            }
        }
        if (me?.deckId != null) Text("Your game saves to ${me.deck}", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        Button(onClick = onStart, colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(if (pod.startedAt != null) "Start again" else "Start", modifier = Modifier.padding(start = 6.dp))
        }
    }
}

/** A player in a pod (or waiting for one), and where to move them: another pod, or a new one. */
@Composable
private fun SeatRow(name: String, detail: String?, podCount: Int, current: Int, onMove: (Int) -> Unit) {
    val colors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Picker(
            shown = if (current < 0) "Seat in…" else "Pod ${current + 1}",
            options = List(podCount) { i -> i.toString() to "Pod ${i + 1}" } + (podCount.toString() to "New pod"),
            onPick = { it?.toIntOrNull()?.let(onMove) },
            compact = true
        )
    }
}

/** A dropdown of [options] (id to label), showing [shown]. */
@Composable
private fun Picker(shown: String, options: List<Pair<String?, String>>, onPick: (String?) -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    val colors = LocalAppColors.current
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = (if (compact) Modifier else Modifier.fillMaxWidth())
                .clip(if (compact) RoundedCornerShape(50) else RoundedCornerShape(14.dp))
                .background(colors.surface2)
                .clickable { open = true }
                .padding(start = 14.dp, end = 8.dp, top = if (compact) 6.dp else 12.dp, bottom = if (compact) 6.dp else 12.dp)
        ) {
            Text(shown, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = if (compact) Modifier else Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = colors.accent)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(colors.surface)) {
            options.forEach { (id, label) ->
                DropdownMenuItem(
                    text = { Text(label, color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium) },
                    onClick = { open = false; onPick(id) }
                )
            }
        }
    }
}

@Composable
private fun NightTextField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = colors.textDim) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = colors.border,
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            cursorColor = colors.accent,
            focusedContainerColor = colors.surface2,
            unfocusedContainerColor = colors.surface2
        ),
        modifier = modifier
    )
}
