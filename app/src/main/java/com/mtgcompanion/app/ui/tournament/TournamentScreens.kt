package com.mtgcompanion.app.ui.tournament

import android.content.Intent
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.social.Profile
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.data.tournament.Entrant
import com.mtgcompanion.app.data.tournament.EventFormat
import com.mtgcompanion.app.data.tournament.EventTable
import com.mtgcompanion.app.data.tournament.MAX_EVENT_PLAYERS
import com.mtgcompanion.app.data.tournament.RoundTimer
import com.mtgcompanion.app.data.tournament.TableResult
import com.mtgcompanion.app.data.tournament.Tournament
import com.mtgcompanion.app.data.tournament.PlayoffKind
import com.mtgcompanion.app.data.tournament.PlayoffMatch
import com.mtgcompanion.app.data.tournament.canCut
import com.mtgcompanion.app.data.tournament.canFinish
import com.mtgcompanion.app.data.tournament.cutLabel
import com.mtgcompanion.app.data.tournament.cutSizes
import com.mtgcompanion.app.data.tournament.matchWinner
import com.mtgcompanion.app.data.tournament.playoffChampion
import com.mtgcompanion.app.data.tournament.playoffChoices
import com.mtgcompanion.app.data.tournament.playoffEditable
import com.mtgcompanion.app.data.tournament.playoffRoundName
import com.mtgcompanion.app.data.tournament.playoffStatus
import com.mtgcompanion.app.data.tournament.seedOrder
import com.mtgcompanion.app.data.tournament.startPlayoff
import com.mtgcompanion.app.data.tournament.withPlayoffResult
import com.mtgcompanion.app.data.tournament.canPairNext
import com.mtgcompanion.app.data.tournament.clockText
import com.mtgcompanion.app.data.tournament.currentRound
import com.mtgcompanion.app.data.tournament.defaultRoundMinutes
import com.mtgcompanion.app.data.tournament.extraTurnsText
import com.mtgcompanion.app.data.tournament.formatLabel
import com.mtgcompanion.app.data.tournament.matchChoices
import com.mtgcompanion.app.data.tournament.newTournament
import com.mtgcompanion.app.data.tournament.oneDecimal
import com.mtgcompanion.app.data.tournament.pairNextRound
import com.mtgcompanion.app.data.tournament.pauseTimer
import com.mtgcompanion.app.data.tournament.percentText
import com.mtgcompanion.app.data.tournament.playerName
import com.mtgcompanion.app.data.tournament.playersProblem
import com.mtgcompanion.app.data.tournament.podResult
import com.mtgcompanion.app.data.tournament.recordText
import com.mtgcompanion.app.data.tournament.resultText
import com.mtgcompanion.app.data.tournament.standings
import com.mtgcompanion.app.data.tournament.standingsHeading
import com.mtgcompanion.app.data.tournament.standingsText
import com.mtgcompanion.app.data.tournament.startTimer
import com.mtgcompanion.app.data.tournament.statusText
import com.mtgcompanion.app.data.tournament.suggestedRounds
import com.mtgcompanion.app.data.tournament.timeLeft
import com.mtgcompanion.app.data.tournament.timerRunning
import com.mtgcompanion.app.data.tournament.withDropped
import com.mtgcompanion.app.data.tournament.withResult
import com.mtgcompanion.app.data.tournament.withTimer
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.common.ConfirmDeleteDialog
import com.mtgcompanion.app.ui.common.PillChip
import com.mtgcompanion.app.ui.common.SegmentedTabs
import com.mtgcompanion.app.ui.theme.LocalAppColors
import com.mtgcompanion.app.ui.theme.NumberStyle
import com.mtgcompanion.app.ui.social.EmptyState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.random.Random

// Small tournaments run from this phone: the events list, a new event, and an event's rounds,
// standings and players. The logic is data/tournament/Tournament.kt. The web app's
// src/tournament/EventPages.tsx.

/** "5 Oct" — the day an event was made. */
private fun day(atMillis: Long): String =
    java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(java.util.Date(atMillis))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventScaffold(title: String, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    val colors = LocalAppColors.current
    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        },
        content = content
    )
}

@Composable
private fun GoldButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text("  $label")
    }
}

@Composable
private fun LineButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Icon(icon, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(18.dp))
        Text("  $label", color = colors.textPrimary)
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textMuted)
}

@Composable
private fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = LocalAppColors.current.textMuted, modifier = modifier.padding(top = 6.dp))
}

/** The events on this phone, newest first, and a way to start one. */
@Composable
fun EventsScreen(repository: TournamentRepository, onBack: () -> Unit, onNew: () -> Unit, onOpen: (String) -> Unit) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val events by repository.events.collectAsState(initial = emptyList())
    var deleting by remember { mutableStateOf<Tournament?>(null) }
    EventScaffold("Events", onBack) { padding ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            item { Note("Run a small Swiss or Commander pod event from this phone: pairings, a round clock and standings.") }
            item { GoldButton("New event", Icons.Filled.Add, onClick = onNew) }
            if (events.isEmpty()) item { EmptyState(Icons.Filled.EmojiEvents, "Events you run show up here.") }
            items(events, key = { it.id }) { e ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.surface)
                        .clickable { onOpen(e.id) }
                        .padding(start = 14.dp, top = 6.dp, bottom = 6.dp)
                ) {
                    Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = if (e.finished) colors.textDim else colors.accent, modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(e.name, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${formatLabel(e)} · ${e.players.size} players", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(playoffStatus(e) { playerName(e, it) } ?: statusText(e), style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
                        Text(day(e.createdAt), style = MaterialTheme.typography.bodySmall, color = colors.textDim)
                    }
                    IconButton(onClick = { deleting = e }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete ${e.name}", tint = colors.textMuted)
                    }
                }
            }
        }
    }
    deleting?.let { e ->
        ConfirmDeleteDialog(
            title = "Delete event?",
            message = "${e.name}, its pairings and standings go for good.",
            onConfirm = { scope.launch { repository.delete(e.id) }; deleting = null },
            onDismiss = { deleting = null }
        )
    }
}

/** A number with − and + either side. */
@Composable
private fun Stepper(label: String, value: Int, min: Int, max: Int, step: Int = 1, unit: String = "", hint: String? = null, onChange: (Int) -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(start = 14.dp, top = 4.dp, bottom = 4.dp, end = 4.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        IconButton(onClick = { onChange(maxOf(min, value - step)) }) { Icon(Icons.Filled.Remove, contentDescription = "Fewer ${label.lowercase()}", tint = colors.textPrimary) }
        Text("$value$unit", style = NumberStyle(26), color = colors.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 56.dp))
        IconButton(onClick = { onChange(minOf(max, value + step)) }) { Icon(Icons.Filled.Add, contentDescription = "More ${label.lowercase()}", tint = colors.textPrimary) }
    }
}

@Composable
private fun eventFieldColors() = LocalAppColors.current.let { colors ->
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.accent,
        unfocusedBorderColor = colors.border,
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        cursorColor = colors.accent,
        focusedContainerColor = colors.surface,
        unfocusedContainerColor = colors.surface
    )
}

/** A new event: its name, format, players (typed, or picked from friends), rounds and round length. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewEventScreen(repository: TournamentRepository, social: SocialRepository, onBack: () -> Unit, onCreated: (String) -> Unit) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val overview by social.overview.collectAsState()
    LaunchedEffect(Unit) { if (social.overview.value == null) social.refresh() }
    var name by remember { mutableStateOf("Event ${day(System.currentTimeMillis())}") }
    var format by remember { mutableStateOf(EventFormat.SWISS) }
    var bestOf by remember { mutableStateOf(3) }
    var players by remember { mutableStateOf(listOf<Entrant>()) }
    var typed by remember { mutableStateOf("") }
    // Rounds follow the suggestion for the player count until they're changed by hand.
    var rounds by remember { mutableStateOf<Int?>(null) }
    var minutes by remember { mutableStateOf<Int?>(null) }

    val suggested = suggestedRounds(format, maxOf(players.size, 2))
    val roundCount = rounds ?: suggested
    val roundMinutes = minutes ?: defaultRoundMinutes(format)
    val taken = players.map { it.name.trim().lowercase() }.toSet()
    val friends: List<Profile> = overview?.let { o ->
        o.friends.filter { it.accepted }.mapNotNull { o.people[it.userId] }
            .filter { p -> players.none { it.userId == p.userId } }
            .sortedBy { it.displayName.lowercase() }
    }.orEmpty()
    val problem = playersProblem(players.map { it.name }) ?: if (name.isBlank()) "Give the event a name" else null

    fun add(entrant: Entrant) {
        val clean = entrant.name.trim()
        if (clean.isEmpty() || players.size >= MAX_EVENT_PLAYERS || clean.lowercase() in taken) return
        players = players + entrant.copy(name = clean)
    }
    fun addTyped() {
        add(Entrant(typed))
        typed = ""
    }
    fun create() {
        if (problem != null) return
        val t = newTournament(
            id = UUID.randomUUID().toString(), name = name, format = format, bestOf = bestOf, roundCount = roundCount,
            roundMinutes = roundMinutes, seed = Random.nextInt(Int.MAX_VALUE), createdAt = System.currentTimeMillis(), players = players
        )
        scope.launch {
            repository.save(t)
            onCreated(t.id)
        }
    }

    EventScaffold("New event", onBack) { padding ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name", color = colors.textMuted) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = eventFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item { FieldLabel("Format") }
            item {
                SegmentedTabs(
                    labels = listOf("1v1 Swiss", "Commander pods"),
                    selected = if (format == EventFormat.SWISS) 0 else 1,
                    onSelect = { format = if (it == 0) EventFormat.SWISS else EventFormat.PODS }
                )
            }
            item {
                if (format == EventFormat.SWISS) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillChip("Best of 1", bestOf == 1, { bestOf = 1 })
                        PillChip("Best of 3", bestOf == 3, { bestOf = 3 })
                    }
                } else {
                    Note("Pods of 4 (3s where the numbers don't fit). A pod win is 3 points, a draw 1 each.")
                }
            }
            item { FieldLabel("Players · ${players.size}") }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        label = { Text("Name", color = colors.textMuted) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = eventFieldColors(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addTyped() }),
                        modifier = Modifier.weight(1f)
                    )
                    val canAdd = typed.isNotBlank() && typed.trim().lowercase() !in taken && players.size < MAX_EVENT_PLAYERS
                    OutlinedButton(onClick = { addTyped() }, enabled = canAdd) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(18.dp))
                        Text("  Add", color = colors.textPrimary)
                    }
                }
            }
            if (friends.isNotEmpty()) {
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        friends.forEach { f -> PillChip("+ ${f.displayName}", false, { add(Entrant(f.displayName, f.userId)) }) }
                    }
                }
            }
            if (players.isNotEmpty()) {
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        players.forEachIndexed { i, p ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(colors.surface2).padding(start = 12.dp)
                            ) {
                                Text("${i + 1}. ${p.name}", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                                IconButton(onClick = { players = players.filterIndexed { j, _ -> j != i } }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove ${p.name}", tint = colors.textDim, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
            item {
                Stepper("Rounds", roundCount, min = 1, max = 15, hint = "Suggested for ${players.size} players: $suggested") { rounds = it }
            }
            item { Stepper("Round length", roundMinutes, min = 5, max = 180, step = 5, unit = " min") { minutes = it } }
            problem?.let { item { Note(it) } }
            item { GoldButton("Start event", Icons.Filled.EmojiEvents, enabled = problem == null) { create() } }
        }
    }
}

/** One event: the current round (clock, tables, results), the standings and the players. */
@Composable
fun EventScreen(repository: TournamentRepository, eventId: String, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val events by repository.events.collectAsState(initial = null)
    var tab by remember { mutableStateOf<Int?>(null) }
    val loaded = events ?: return
    val event = loaded.firstOrNull { it.id == eventId }
    if (event == null) {
        EventScaffold("Event", onBack) { padding ->
            Box(Modifier.padding(padding).padding(16.dp)) { Note("This event isn't on this phone any more.") }
        }
        return
    }
    val save: (Tournament) -> Unit = { t -> scope.launch { repository.save(t) } }
    // The tabs by name: the top cut (or final table) sits between the standings and the players once there is one.
    val tabs = if (event.playoff != null) listOf("round", "standings", "playoff", "players") else listOf("round", "standings", "players")
    val shownTab = tab?.let { tabs.getOrNull(it) } ?: if (event.playoff != null) "playoff" else if (event.finished) "standings" else "round"
    val shown = tabs.indexOf(shownTab)
    val labels = tabs.map { when (it) { "round" -> "Round"; "standings" -> "Standings"; "playoff" -> if (event.format == EventFormat.PODS) "Final" else "Top cut"; else -> "Players" } }
    EventScaffold(event.name, onBack) { padding ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            item { Note("${formatLabel(event)} · ${event.players.size} players · ${playoffStatus(event) { playerName(event, it) } ?: statusText(event)}") }
            item { SegmentedTabs(labels = labels, selected = shown, onSelect = { tab = it }) }
            when (shownTab) {
                "round" -> {
                    val round = currentRound(event)
                    if (round != null && !event.finished) item { RoundClock(event, save) }
                    if (round == null) item { Note("${event.players.size} players. Pair round 1 once everyone's here — seats are drawn at random.") }
                    else item { FieldLabel("Round ${round.number} of ${event.roundCount}") }
                    round?.tables?.forEachIndexed { i, table -> item { TableCard(event, table, i, save) } }
                    if (!event.finished) {
                        if (canPairNext(event)) {
                            item { GoldButton("Pair round ${event.rounds.size + 1}", Icons.Filled.Shuffle) { save(pairNextRound(event)) } }
                        }
                        if (canFinish(event)) {
                            item {
                                val finish = { save(event.copy(finished = true)); tab = tabs.indexOf("standings") }
                                if (canPairNext(event)) LineButton("Finish now", Icons.Filled.Flag, Modifier.fillMaxWidth()) { finish() }
                                else GoldButton("Finish event", Icons.Filled.Flag) { finish() }
                            }
                        }
                        if (round != null && !canFinish(event)) {
                            item { Note("Tap each table's result. Results can change until the next round is paired.") }
                        }
                    }
                }
                "standings" -> {
                    item { FieldLabel(standingsHeading(event)) }
                    val rows = standings(event)
                    rows.forEachIndexed { i, s ->
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .alpha(if (s.dropped) 0.6f else 1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(colors.surface)
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text("${i + 1}", style = NumberStyle(24), color = colors.accent, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 22.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(s.name + if (s.dropped) " · dropped" else "", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                                    val tiebreaks = if (event.format == EventFormat.SWISS) " · OMW ${percentText(s.omw)} · GW ${percentText(s.gw)} · OGW ${percentText(s.ogw)}"
                                    else " · Opp. avg ${oneDecimal(s.oppPoints)}"
                                    Text(recordText(s) + tiebreaks, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                                }
                                Text("${s.points}", style = NumberStyle(28), color = colors.textPrimary)
                                Text("pts", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                            }
                        }
                    }
                    item {
                        Note(
                            if (event.format == EventFormat.SWISS) "Match win 3, draw 1. Ties go to opponents’ match-win %, then game-win %, then opponents’ game-win % (each at least 33%)."
                            else "Pod win 3, draw 1 each. Ties go to the average points of everyone you shared a pod with."
                        )
                    }
                    if (canCut(event)) item { CutButtons(event) { size -> save(startPlayoff(event, size)); tab = 2 } }
                    item { ShareStandings(event) }
                }
                "playoff" -> {
                    val p = event.playoff!!
                    val champion = playoffChampion(event)
                    if (champion != null) item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.accent.copy(alpha = 0.16f)).padding(14.dp)
                        ) {
                            Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = colors.accent, modifier = Modifier.size(32.dp))
                            Column {
                                Text("Champion", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                                Text(playerName(event, champion), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                            }
                        }
                    }
                    // Each player's seed: where they sit in the first round, in bracket order.
                    val order = seedOrder(p.size)
                    val seedOf = p.rounds.first().flatMap { it.players }.mapIndexed { i, id -> id to order.getOrElse(i) { 0 } }.toMap()
                    p.rounds.forEachIndexed { r, round ->
                        item { FieldLabel(playoffRoundName(p, r)) }
                        round.forEachIndexed { i, match ->
                            item { PlayoffCard(event, match, r, i, if (p.kind == PlayoffKind.BRACKET) seedOf else emptyMap(), save) }
                        }
                    }
                    if (champion == null) item { Note("Tap each match's result. A result can change until the next match is played.") }
                }
                else -> {
                    item { Note("A dropped player isn't paired again; their results still count for everyone they played.") }
                    items(event.players, key = { it.id }) { p ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (p.dropped) 0.6f else 1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.surface)
                                .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                        ) {
                            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                Text(p.name, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                                if (p.dropped) Text("Dropped", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                            }
                            if (!event.finished) {
                                TextButton(onClick = { save(withDropped(event, p.id, !p.dropped)) }) {
                                    Text(if (p.dropped) "Bring back" else "Drop", color = colors.accent)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The one clock for the room: time left, large, and what to do once it's run out. */
@Composable
private fun RoundClock(event: Tournament, save: (Tournament) -> Unit) {
    val colors = LocalAppColors.current
    val round = currentRound(event) ?: return
    val running = timerRunning(round.timer)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running) {
        now = System.currentTimeMillis()
        while (running) { delay(500); now = System.currentTimeMillis() }
    }
    // Stays awake while the clock runs, so the room can see it.
    val view = LocalView.current
    DisposableEffect(view, running) {
        if (running) view.keepScreenOn = true
        onDispose { if (running) view.keepScreenOn = false }
    }
    val left = timeLeft(round.timer, event.roundMinutes, now)
    val over = left <= 0
    val set = { timer: RoundTimer -> save(withTimer(event, timer)) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(if (over) colors.accent.copy(alpha = 0.16f) else colors.surface)
            .padding(horizontal = 14.dp, vertical = 18.dp)
    ) {
        if (over) {
            Text("Extra turns", style = NumberStyle(56), color = colors.accent)
            Text("${extraTurnsText(event.format)} · ${clockText(left)} over time", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        } else {
            Text(clockText(left), style = NumberStyle(84), color = colors.textPrimary)
            Text(
                when {
                    running -> "Time left in the round"
                    round.timer.leftMs == null -> "${event.roundMinutes} minutes, not started"
                    else -> "Paused"
                },
                style = MaterialTheme.typography.bodySmall, color = colors.textMuted
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            if (running) {
                LineButton("Pause", Icons.Filled.Pause) { set(pauseTimer(round.timer, event.roundMinutes, System.currentTimeMillis())) }
            } else {
                Button(
                    onClick = { set(startTimer(round.timer, event.roundMinutes, System.currentTimeMillis())) },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Start")
                }
            }
            if (running || round.timer.leftMs != null) LineButton("Reset", Icons.Filled.Replay) { set(RoundTimer()) }
        }
    }
}

/** One table: who's sitting there and their result, tapped in; tap it again to clear it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TableCard(event: Tournament, table: EventTable, index: Int, save: (Tournament) -> Unit) {
    val colors = LocalAppColors.current
    val names = table.players.map { playerName(event, it) }
    val set = { r: TableResult -> if (!event.finished) save(withResult(event, index, if (table.result == r) null else r)) }
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).padding(14.dp)
    ) {
        if (table.players.size == 1) {
            Text(names[0], style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
            Text("Bye · " + if (event.format == EventFormat.SWISS) "2–0 win" else "counts as a win", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            return@Column
        }
        Column {
            Text(names.joinToString(if (event.format == EventFormat.SWISS) " vs " else " · "), style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
            Text("Table ${index + 1}" + (resultText(event, table)?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (event.format == EventFormat.SWISS) {
                matchChoices(event.bestOf).forEach { c -> PillChip(c.label, table.result == c.result, { set(c.result) }) }
            } else {
                table.players.forEachIndexed { i, p ->
                    val r = podResult(table.players, p)
                    PillChip(names[i], table.result == r, { set(r) })
                }
                val draw = podResult(table.players, null)
                PillChip("Draw", table.result == draw, { set(draw) })
            }
        }
        if (event.format == EventFormat.SWISS) Note("From ${names[0]}'s side")
    }
}

/** Copy the standings, or share them as plain text. */
@Composable
private fun ShareStandings(event: Tournament) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val text = standingsText(event) + (playoffChampion(event)?.let { "\n\nChampion: ${playerName(event, it)}" } ?: "")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        LineButton(if (copied) "Copied" else "Copy", Icons.Filled.ContentCopy) {
            clipboard.setText(AnnotatedString(text))
            copied = true
        }
        LineButton("Share", Icons.Filled.Share) {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).putExtra(Intent.EXTRA_SUBJECT, event.name), "Share standings"))
        }
    }
    Box(Modifier.height(8.dp))
}

/** After the Swiss: cut to a top 8 / 4 / 2 (1v1) or a final table of the top 4 (pods). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CutButtons(event: Tournament, onCut: (Int) -> Unit) {
    val sizes = cutSizes(event)
    if (sizes.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
        FieldLabel(if (event.format == EventFormat.PODS) "Final table" else "Top cut")
        Note(
            if (event.format == EventFormat.PODS) "The top players by the standings play one last game; its winner takes the event."
            else "Single elimination, seeded by the standings: 1 plays 8, 4 plays 5, 2 plays 7, 3 plays 6."
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sizes.forEach { n -> PillChip(cutLabel(event.format, n), false, { onCut(n) }) }
        }
    }
}

/** One playoff match: its players (with seeds in a bracket), the winner marked, and its result tapped in while it can change. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayoffCard(event: Tournament, match: PlayoffMatch, round: Int, index: Int, seeds: Map<String?, Int>, save: (Tournament) -> Unit) {
    val colors = LocalAppColors.current
    val p = event.playoff ?: return
    val editable = playoffEditable(p, round, index)
    val winner = matchWinner(match)
    val set = { r: TableResult -> if (editable) save(withPlayoffResult(event, round, index, if (match.result == r) null else r)) }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).padding(14.dp)
    ) {
        match.players.forEach { id ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                seeds[id]?.let { Text("$it", style = MaterialTheme.typography.labelMedium, color = colors.textDim, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 18.dp)) }
                Text(
                    id?.let { playerName(event, it) } ?: "Waiting",
                    style = MaterialTheme.typography.titleSmall,
                    color = when {
                        id == null -> colors.textDim
                        id == winner -> colors.accent
                        else -> colors.textPrimary
                    },
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                if (id != null && id == winner) Icon(Icons.Filled.EmojiEvents, contentDescription = "Won", tint = colors.accent, modifier = Modifier.size(18.dp))
            }
        }
        if (editable) {
            val ids = match.players.filterNotNull()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (p.kind == PlayoffKind.FINAL_TABLE) {
                    ids.forEach { id ->
                        val r = podResult(ids, id)
                        PillChip(playerName(event, id), match.result == r, { set(r) })
                    }
                } else {
                    playoffChoices(event.bestOf).forEach { c -> PillChip(c.label, match.result == c.result, { set(c.result) }) }
                }
            }
            if (p.kind == PlayoffKind.BRACKET) Note("From ${playerName(event, ids.first())}'s side")
        }
    }
}
