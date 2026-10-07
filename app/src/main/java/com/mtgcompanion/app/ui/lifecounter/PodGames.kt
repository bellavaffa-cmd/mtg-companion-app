package com.mtgcompanion.app.ui.lifecounter

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.CommanderRecord
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.GameMode
import com.mtgcompanion.app.data.GameResult
import com.mtgcompanion.app.data.PLAYGROUP_MIN_GAMES
import com.mtgcompanion.app.data.PodGame
import com.mtgcompanion.app.data.PodPlayer
import com.mtgcompanion.app.data.LeagueRules
import com.mtgcompanion.app.data.Season
import com.mtgcompanion.app.data.runningSeason
import com.mtgcompanion.app.data.social.SocialException
import com.mtgcompanion.app.data.canDeletePodGame
import com.mtgcompanion.app.data.deckResultOf
import com.mtgcompanion.app.data.podGameProblem
import com.mtgcompanion.app.data.podStats
import com.mtgcompanion.app.data.record
import com.mtgcompanion.app.data.social.Overview
import com.mtgcompanion.app.data.social.Pod
import com.mtgcompanion.app.data.social.Profile
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.ui.common.ConfirmDeleteDialog
import com.mtgcompanion.app.ui.common.PillChip
import com.mtgcompanion.app.ui.social.GoldButton
import com.mtgcompanion.app.ui.social.LineButton
import com.mtgcompanion.app.ui.social.Notice
import com.mtgcompanion.app.ui.social.ShareSwitch
import com.mtgcompanion.app.ui.social.socialFieldColors
import com.mtgcompanion.app.ui.theme.LocalAppColors
import com.mtgcompanion.app.ui.theme.NumberStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// A pod's shared games on the Playgroup screen: the group's table, commanders, nemeses and latest
// games, and recording a new one. Mirrors the web app's src/pages/PodGames.tsx.

/** How many commanders each list shows. */
private const val COMMANDERS_SHOWN = 5

private fun plural(n: Int, one: String, many: String = "${one}s") = "$n ${if (n == 1) one else many}"
private fun recordLine(wins: Int, losses: Int, draws: Int) = "$wins–$losses" + if (draws > 0) "–$draws" else ""
private fun formatLabel(format: String): String =
    GameMode.entries.firstOrNull { it.name == format }?.label ?: format.lowercase().replaceFirstChar { it.uppercase() }
private fun commanderOf(deck: Deck): String = listOfNotNull(deck.commander?.name, deck.partnerCommander?.name).joinToString(" & ")

@Composable
fun PodView(
    social: SocialRepository,
    overview: Overview,
    pod: Pod,
    me: Profile,
    decks: List<Deck>,
    onAddGameResult: (String, GameResult) -> Unit,
    onOpenPodChat: ((String) -> Unit)? = null,
    onPlanGameNight: ((String) -> Unit)? = null,
    onOpenGameNight: ((String) -> Unit)? = null
) {
    val colors = LocalAppColors.current
    var games by remember(pod.id) { mutableStateOf<List<PodGame>?>(null) }
    var error by remember(pod.id) { mutableStateOf<String?>(null) }
    var loading by remember(pod.id) { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var recording by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<PodGame?>(null) }
    val scope = rememberCoroutineScope()
    // The pod's league seasons (LeagueView.kt): null while loading.
    var seasons by remember(pod.id) { mutableStateOf<List<Season>?>(null) }
    var leagueUnavailable by remember(pod.id) { mutableStateOf(false) }
    var leagueError by remember(pod.id) { mutableStateOf<String?>(null) }
    var leagueReload by remember { mutableIntStateOf(0) }

    LaunchedEffect(pod.id, reload, leagueReload) {
        try {
            seasons = social.api.podSeasons(pod.id)
            leagueError = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: SocialException) {
            if (e.code == "unavailable") leagueUnavailable = true else leagueError = e.message
        } catch (e: Exception) {
            leagueError = e.message ?: "Something went wrong."
        }
    }

    // A different pod (or a reload) cancels the last load, so an old answer never lands.
    LaunchedEffect(pod.id, reload) {
        loading = true
        error = null
        try {
            games = social.api.podGames(pod.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Something went wrong."
        }
        loading = false
    }

    val stats = remember(games) { games?.let { podStats(it) } }
    fun nameOf(userId: String?, name: String): String = userId?.let { overview.person(it)?.displayName } ?: name

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(pod.name, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        plural(pod.members.size, "person", "people") + (stats?.let { " · " + plural(it.games, "game") } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted
                    )
                }
                GoldButton("Record a game", { recording = true }, icon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) })
            }
        }
        // For now the way into the pod's chat and game nights; the lead wires them into Friends and Play.
        if (onOpenPodChat != null && onPlanGameNight != null && onOpenGameNight != null) item {
            PodNightAndChat(social, pod.id, onOpenPodChat, onPlanGameNight, onOpenGameNight)
        }
        val current = games
        if (current != null) item {
            LeagueSection(social, overview, pod, me, current, seasons, leagueUnavailable, leagueError) { leagueReload++ }
        }
        when {
            current == null && error != null -> item {
                PodEmpty(Icons.Filled.CloudOff, error.orEmpty()) { LineButton("Try again", { reload++ }, enabled = !loading) }
            }
            current == null -> item {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.accent)
                }
            }
            stats == null || stats.games == 0 -> item {
                PodEmpty(Icons.Filled.Groups, "No games recorded in this pod yet. Record one after you play — everyone in the pod sees the same games.")
            }
            else -> {
                error?.let { item { Notice(it, warn = true) } }
                item {
                    Panel {
                        PanelTitle("Players")
                        stats.players.forEach { p ->
                            StatRow(
                                nameOf(p.userId, p.name) + if (p.userId == me.userId) " (you)" else "",
                                "${p.winRate}% · ${plural(p.games, "game")}",
                                recordLine(p.wins, p.losses, p.draws),
                                recordColor(p.wins, p.losses)
                            )
                        }
                        val length = listOfNotNull(stats.averageMinutes?.let { "$it min" }, stats.averageTurns?.let { "$it turns" })
                        if (length.isNotEmpty()) {
                            Text("A game takes about ${length.joinToString(" · ")}", style = MaterialTheme.typography.bodySmall, color = colors.textMuted, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
                if (stats.nemeses.isNotEmpty()) item {
                    Panel {
                        PanelTitle("Nemeses")
                        stats.nemeses.forEach { (player, nemesis) ->
                            StatRow("${nameOf(player.userId, player.name)} → ${nemesis.name}", plural(nemesis.games, "game"), nemesis.record(), colors.error)
                        }
                        Text(
                            "Who each player does worst against, out of those they've played $PLAYGROUP_MIN_GAMES or more times.",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textDim,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
                if (stats.mostPlayed.isNotEmpty()) item {
                    Panel {
                        PanelTitle("Commanders")
                        SubTitle("Most played")
                        stats.mostPlayed.take(COMMANDERS_SHOWN).forEach { CommanderRow(it) }
                        SubTitle("Best win rate")
                        stats.best.take(COMMANDERS_SHOWN).forEach { CommanderRow(it) }
                        Text(
                            "Commanders played $PLAYGROUP_MIN_GAMES or more times.",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textDim,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
                item {
                    Panel {
                        PanelTitle("Latest games")
                        stats.latest.forEach { g ->
                            GameRow(g, { id, n -> nameOf(id, n) }, if (canDeletePodGame(g, me.userId, pod.owner)) ({ deleting = g }) else null)
                        }
                    }
                }
            }
        }
    }

    if (recording) {
        RecordGameDialog(
            social = social,
            overview = overview,
            pod = pod,
            me = me,
            decks = decks,
            league = seasons?.let { runningSeason(it) }?.rules,
            onRecorded = { game, deckId ->
                recording = false
                if (deckId != null && game != null) onAddGameResult(deckId, game)
                reload++
            },
            onDismiss = { recording = false }
        )
    }
    deleting?.let { g ->
        ConfirmDeleteDialog(
            title = "Delete this game?",
            message = "It comes out of the pod's games for everyone. A deck's own record keeps it.",
            onConfirm = {
                deleting = null
                scope.launch {
                    try {
                        social.api.deletePodGame(g.id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = e.message ?: "Something went wrong."
                    }
                    reload++
                }
            },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun PodEmpty(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, action: (@Composable () -> Unit)? = null) {
    val colors = LocalAppColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 8.dp)) {
        Icon(icon, contentDescription = null, tint = colors.textDim, modifier = Modifier.size(40.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, modifier = Modifier.padding(top = 10.dp, bottom = 12.dp))
        action?.invoke()
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.surface).padding(16.dp), content = content)
}

@Composable
private fun PanelTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = LocalAppColors.current.textPrimary, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun SubTitle(title: String) {
    Text(title, style = MaterialTheme.typography.labelMedium, color = LocalAppColors.current.textMuted, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
}

/** Gold for a winning record, red for a losing one. */
@Composable
private fun recordColor(wins: Int, losses: Int): Color {
    val colors = LocalAppColors.current
    return when {
        wins > losses -> colors.accent
        wins < losses -> colors.error
        else -> colors.textMuted
    }
}

@Composable
private fun StatRow(name: String, detail: String, figure: String, figureColor: Color) {
    val colors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(name, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.textDim)
        Text(figure, style = NumberStyle(17), color = figureColor)
    }
}

@Composable
private fun CommanderRow(c: CommanderRecord) {
    val colors = LocalAppColors.current
    StatRow(c.name, "${c.winRate}% · ${plural(c.games, "game")}", plural(c.wins, "win"), if (c.wins * 2 > c.games) colors.accent else colors.textMuted)
}

@Composable
private fun GameRow(game: PodGame, nameOf: (String?, String) -> String, onDelete: (() -> Unit)?) {
    val colors = LocalAppColors.current
    val draw = game.players.all { it.result == "DRAW" }
    val facts = listOfNotNull(
        SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(game.playedAt)),
        formatLabel(game.format).ifEmpty { null },
        game.turns?.takeIf { it > 0 }?.let { plural(it, "turn") },
        game.minutes?.takeIf { it > 0 }?.let { "$it min" },
        if (draw) "Draw" else null
    )
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = colors.textDim)
            game.players.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    if (p.result == "WIN") {
                        Icon(Icons.Filled.EmojiEvents, contentDescription = "Winner", tint = colors.accent, modifier = Modifier.size(15.dp).padding(end = 3.dp))
                    }
                    Text(
                        nameOf(p.userId, p.name) + (p.commander?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (p.result == "WIN") FontWeight.Bold else FontWeight.Normal,
                        color = if (p.result == "WIN") colors.accent else colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete this game", tint = colors.textDim) }
        }
    }
}

/** One seat in the form. [deckId]: the user's own deck, for their own seat. */
private data class Seat(
    val id: String = UUID.randomUUID().toString(),
    val userId: String?,
    val name: String,
    val commander: String = "",
    val deck: String = "",
    val deckId: String? = null
)

private const val DRAW = "draw"

/**
 * Recording a game: who played (pod members, and guests by name), what each played, who won.
 * [onRecorded] gets the game as a result for the user's own deck when they asked for that.
 */
@Composable
private fun RecordGameDialog(
    social: SocialRepository,
    overview: Overview,
    pod: Pod,
    me: Profile,
    decks: List<Deck>,
    /** The running season's rules: when they give points for second place or first blood, those can be picked. */
    league: LeagueRules?,
    onRecorded: (GameResult?, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    fun memberName(id: String) = if (id == me.userId) me.displayName else overview.person(id)?.displayName ?: "Someone"
    var seats by remember { mutableStateOf(listOf(Seat(userId = me.userId, name = me.displayName))) }
    var guest by remember { mutableStateOf("") }
    // A seat's id, or DRAW.
    var winner by remember { mutableStateOf("") }
    // Seat ids, or "" for nobody.
    var second by remember { mutableStateOf("") }
    var firstBlood by remember { mutableStateOf("") }
    var format by remember { mutableStateOf(GameMode.COMMANDER) }
    var turns by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }
    var toDeck by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Kept for retries, so a game that did reach the server isn't stored twice.
    val clientId = remember { UUID.randomUUID().toString() }
    val playedAt = remember { System.currentTimeMillis() }

    val myDecks = remember(decks) { decks.sortedBy { it.name.lowercase() } }
    val members = listOf(me.userId) + pod.members.filter { it != me.userId }
    val mySeat = seats.firstOrNull { it.userId == me.userId }
    fun update(id: String, change: (Seat) -> Seat) { seats = seats.map { if (it.id == id) change(it) else it } }
    fun toggleMember(userId: String) {
        val seat = seats.firstOrNull { it.userId == userId }
        if (seat != null) {
            seats = seats.filter { it.id != seat.id }
            if (winner == seat.id) winner = ""
            if (second == seat.id) second = ""
            if (firstBlood == seat.id) firstBlood = ""
        } else {
            seats = seats + Seat(userId = userId, name = memberName(userId))
        }
    }
    fun addGuest() {
        val name = guest.trim()
        if (name.isEmpty()) return
        seats = seats + Seat(userId = null, name = name)
        guest = ""
    }
    fun count(text: String): Int? = text.toIntOrNull()?.takeIf { it > 0 }

    fun save() {
        val players = seats.map { s ->
            PodPlayer(
                userId = s.userId,
                name = s.name.trim(),
                commander = s.commander.trim().ifEmpty { null },
                deck = s.deck.trim().ifEmpty { null },
                result = if (winner == DRAW) "DRAW" else if (winner == s.id) "WIN" else "LOSS",
                place = when {
                    winner == DRAW -> null
                    winner == s.id -> if (second.isNotEmpty()) 1 else null
                    second == s.id -> 2
                    else -> null
                },
                firstBlood = firstBlood == s.id
            )
        }
        val problem = if (winner.isEmpty()) "Pick who won, or Draw." else podGameProblem(players)
        if (problem != null) { error = problem; return }
        val t = count(turns)
        val m = count(minutes)
        busy = true
        error = null
        scope.launch {
            try {
                social.api.recordPodGame(pod.id, clientId, playedAt, format.name, t, m, players)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Offline or refused: the form stays filled, to try again.
                error = e.message ?: "Something went wrong."
                busy = false
                return@launch
            }
            val deckId = mySeat?.deckId?.takeIf { toDeck }
            onRecorded(deckId?.let { deckResultOf(playedAt, t, m, players, me.userId, clientId) }, deckId)
        }
    }

    AlertDialog(
        containerColor = colors.surface,
        onDismissRequest = onDismiss,
        title = { Text("Record a game", color = colors.accentLight) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FieldLabel("Who played")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    members.forEach { id ->
                        PillChip(
                            label = if (id == me.userId) "${memberName(id)} (you)" else memberName(id),
                            selected = seats.any { it.userId == id },
                            onClick = { toggleMember(id) }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = guest,
                        onValueChange = { guest = it.take(40) },
                        placeholder = { Text("A guest's name") },
                        singleLine = true,
                        colors = socialFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { addGuest() }, enabled = guest.isNotBlank()) { Text("Add guest", color = if (guest.isNotBlank()) colors.accent else colors.textDim) }
                }

                seats.forEach { s ->
                    Column(
                        Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface2).padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                s.name + when { s.userId == me.userId -> " (you)"; s.userId == null -> " · guest"; else -> "" },
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                if (s.userId != null) toggleMember(s.userId) else {
                                    seats = seats.filter { it.id != s.id }
                                    if (winner == s.id) winner = ""
                                    if (second == s.id) second = ""
                                    if (firstBlood == s.id) firstBlood = ""
                                }
                            }) { Icon(Icons.Filled.Close, contentDescription = "Remove ${s.name}", tint = colors.textDim) }
                        }
                        if (s.userId == me.userId) {
                            Dropdown(
                                label = "Your deck",
                                options = listOf("" to "No deck") + myDecks.map { it.id to it.name },
                                selected = s.deckId ?: "",
                                onSelect = { id ->
                                    val deck = decks.firstOrNull { it.id == id }
                                    update(s.id) {
                                        it.copy(
                                            deckId = deck?.id,
                                            deck = deck?.name ?: "",
                                            commander = if (deck != null && it.commander.isBlank()) commanderOf(deck) else it.commander
                                        )
                                    }
                                }
                            )
                        } else {
                            OutlinedTextField(
                                value = s.deck,
                                onValueChange = { v -> update(s.id) { it.copy(deck = v.take(80)) } },
                                placeholder = { Text("Deck (optional)") },
                                singleLine = true,
                                colors = socialFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        OutlinedTextField(
                            value = s.commander,
                            onValueChange = { v -> update(s.id) { it.copy(commander = v.take(120)) } },
                            placeholder = { Text("Commander (optional)") },
                            singleLine = true,
                            colors = socialFieldColors(),
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Dropdown(
                    label = "Who won",
                    options = seats.map { it.id to it.name } + (DRAW to "Draw"),
                    selected = winner,
                    placeholder = "Pick the winner",
                    onSelect = { winner = it; if (second == it) second = "" }
                )
                // Only asked for while the pod's running season gives points for them.
                if (league != null && league.second > 0 && winner.isNotEmpty() && winner != DRAW) {
                    Spacer(Modifier.height(14.dp))
                    Dropdown(
                        label = "Second place (optional)",
                        options = listOf("" to "Not recorded") + seats.filter { it.id != winner }.map { it.id to it.name },
                        selected = second,
                        onSelect = { second = it }
                    )
                }
                if (league != null && league.firstBlood > 0) {
                    Spacer(Modifier.height(14.dp))
                    Dropdown(
                        label = "First blood (optional)",
                        options = listOf("" to "Not recorded") + seats.map { it.id to it.name },
                        selected = firstBlood,
                        onSelect = { firstBlood = it }
                    )
                }
                Spacer(Modifier.height(14.dp))
                Dropdown(
                    label = "Format",
                    options = GameMode.entries.map { it.name to it.label },
                    selected = format.name,
                    onSelect = { name -> format = GameMode.entries.firstOrNull { it.name == name } ?: GameMode.COMMANDER }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 14.dp)) {
                    OutlinedTextField(
                        value = turns,
                        onValueChange = { v -> turns = v.filter { it.isDigit() }.take(4) },
                        label = { Text("Turns (optional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = socialFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { v -> minutes = v.filter { it.isDigit() }.take(5) },
                        label = { Text("Minutes (optional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = socialFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                }
                val myDeck = mySeat?.takeIf { it.deckId != null }?.deck
                if (myDeck != null) {
                    Spacer(Modifier.height(8.dp))
                    ShareSwitch("Add to my deck's record too", "$myDeck — so it counts in your own stats", toDeck) { toDeck = it }
                }
                error?.let { Notice(it, warn = true, modifier = Modifier.padding(top = 10.dp)) }
            }
        },
        confirmButton = {
            GoldButton(if (busy) "Saving…" else "Record", { save() }, enabled = !busy)
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textMuted) }
        }
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = LocalAppColors.current.textMuted, modifier = Modifier.padding(bottom = 6.dp))
}

/** A labelled dropdown of [options] (value to label). */
@Composable
private fun Dropdown(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, placeholder: String = "") {
    val colors = LocalAppColors.current
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        FieldLabel(label)
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surface)
                    .border(BorderStroke(1.dp, colors.border), RoundedCornerShape(8.dp))
                    .clickable { open = true }
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                val chosen = options.firstOrNull { it.first == selected }?.second
                Text(
                    chosen ?: placeholder,
                    color = if (chosen != null) colors.textPrimary else colors.textDim,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = label, tint = colors.accent)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(colors.surface)) {
                options.forEach { (value, text) ->
                    DropdownMenuItem(
                        text = { Text(text, color = if (value == selected) colors.accent else colors.textPrimary, style = MaterialTheme.typography.bodyMedium) },
                        onClick = { onSelect(value); open = false }
                    )
                }
            }
        }
    }
}

/**
 * On a pod's page: its next game night, Plan a game night and Pod chat. A temporary way in until
 * Friends' Chats tab and Play show them. Nothing before the server has invites.
 */
@Composable
private fun PodNightAndChat(social: SocialRepository, podId: String, onOpenChat: (String) -> Unit, onPlan: (String) -> Unit, onOpenNight: (String) -> Unit) {
    val available by social.nights.available.collectAsState()
    LaunchedEffect(Unit) { social.nights.check() }
    if (available != true) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        com.mtgcompanion.app.ui.social.NextGameNightCard(social, onOpenNight, podId = podId)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LineButton("Pod chat", { onOpenChat(podId) })
            LineButton("Plan a game night", { onPlan(podId) })
        }
    }
}
