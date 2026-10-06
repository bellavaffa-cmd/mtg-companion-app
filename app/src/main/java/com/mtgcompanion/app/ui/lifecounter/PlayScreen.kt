package com.mtgcompanion.app.ui.lifecounter

import androidx.compose.foundation.background
import com.mtgcompanion.app.ui.common.a11yHeading
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.SeatMemory
import com.mtgcompanion.app.data.playgroupStats
import com.mtgcompanion.app.data.tournament.Tournament
import com.mtgcompanion.app.data.tournament.playoffChampion
import com.mtgcompanion.app.ui.common.SectionHeader
import com.mtgcompanion.app.ui.theme.LocalAppColors

/** What the Play tab's Your group tiles say about Game night, Playgroup and Events. */
data class PlayGroupStatus(val gameNight: String, val playgroup: String, val events: String)

/** Each of Your group's status lines (PlayHub.kt), from what's kept on this phone. */
fun playGroupStatus(night: GameNight, decks: List<Deck>, events: List<Tournament>, now: Long = System.currentTimeMillis()): PlayGroupStatus {
    val stats = playgroupStats(decks)
    val running = events.count { !it.finished || (it.playoff != null && playoffChampion(it) == null) }
    return PlayGroupStatus(
        gameNight = gameNightStatus(night.players.size, night.pods.size, now - night.createdAt > NIGHT_STALE_MS),
        playgroup = playgroupStatus(stats.games, stats.nemesis?.name),
        events = eventsStatus(running, events.size)
    )
}

/**
 * The Play tab, in three parts. Play now: start a game on this phone (the table it starts with, and
 * who played last), join someone else's table with your phone as the remote for your seat, or go
 * back to the seat you're in. Your group: Game night, Playgroup and Events, each with a line on where
 * it stands. Recent games, each opening its life chart. Rules sits in the header (it has its own
 * place in the wide layouts' rail too). The web app's twin is src/lifecounter/PlayPage.tsx; the
 * status lines are PlayHub.kt (playHub.ts).
 */
@Composable
fun PlayScreen(
    games: List<TableGame>,
    remoteSeat: SeatMemory?,
    onStartGame: () -> Unit,
    onJoinTable: () -> Unit,
    onOpenRemote: (matchId: String, seat: Int) -> Unit,
    onOpenRules: () -> Unit,
    /** Every deck's games together. */
    onOpenPlaygroup: () -> Unit = {},
    onOpenEvents: (() -> Unit)? = null,
    onOpenGameNight: () -> Unit = {},
    /** Pack your bag for a game night or an event (PackScreen.kt). */
    onOpenPack: (() -> Unit)? = null,
    settings: LifeCounterSettings = LifeCounterSettings(),
    status: PlayGroupStatus? = null
) {
    val colors = LocalAppColors.current
    var allGames by rememberSaveable { mutableStateOf(false) }
    val players = TableLayouts.byId(settings.layoutId).playerCount
    val startLine = startGameLine(players, settings.startingLifeFor(players)) +
        (games.firstOrNull()?.let { g -> lastPlayersLine(g.players.map { it.name }) }?.let { " · $it" } ?: "")
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 4.dp)) {
                Text("Play", style = MaterialTheme.typography.headlineMedium, color = colors.textPrimary, modifier = Modifier.a11yHeading().weight(1f))
                IconButton(onClick = onOpenRules) { Icon(Icons.Filled.MenuBook, contentDescription = "Rules", tint = colors.textPrimary) }
            }
        }
        item { SectionHeader("Play now") }
        item { StartGameCard(startLine, onStartGame) }
        remoteSeat?.let { seat ->
            item {
                PlayRow(Icons.Filled.EventSeat, "Back to seat ${seat.seat}", "You're still at a table — open your remote", highlight = true) {
                    onOpenRemote(seat.matchId, seat.seat)
                }
            }
        }
        item { PlayRow(Icons.Filled.QrCodeScanner, "Join a table", "Scan a seat's QR code: your phone becomes your remote") { onJoinTable() } }

        item { SectionHeader("Your group", modifier = Modifier.padding(top = 10.dp)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                PlayTile(Icons.Filled.Groups, "Game night", status?.gameNight ?: "Fair pods by power", onOpenGameNight)
                PlayTile(Icons.Filled.Leaderboard, "Playgroup", status?.playgroup ?: "Your record", onOpenPlaygroup)
                onOpenEvents?.let { open -> PlayTile(Icons.Filled.EmojiEvents, "Events", status?.events ?: "Swiss or Commander pods", open) }
            }
        }
        onOpenPack?.let { open ->
            item { PlayRow(Icons.Filled.Backpack, "Pack your bag", "For a game night or an event: decks, tokens, trades and what to give back") { open() } }
        }

        item {
            SectionHeader(
                "Recent games",
                modifier = Modifier.padding(top = 10.dp),
                action = if (games.size > RECENT_SHOWN) (if (allGames) "Fewer" else "All games") else null,
                onAction = { allGames = !allGames }
            )
        }
        if (games.isEmpty()) {
            item { Text("Games played on this phone's life counter show up here.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted) }
        }
        items(if (allGames) games else games.take(RECENT_SHOWN), key = { it.id }) { game -> RecentGameRow(game) }
        item { Box(Modifier.height(24.dp)) }
    }
}

@Composable
private fun StartGameCard(line: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        // The life counter's own colour blocks, so the button looks like what it opens.
        val seats = listOf(Color(0xFFFFC400), Color(0xFFFF1F4B), Color(0xFFF58FF7), Color(0xFF4A5BFF))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            seats.forEach { c -> Box(Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(9.dp)).background(c)) }
        }
        Text("Start a game", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        Text(line, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
    }
}

@Composable
private fun PlayRow(icon: ImageVector, title: String, subtitle: String, highlight: Boolean = false, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (highlight) colors.accent.copy(alpha = 0.14f) else colors.surface)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
    }
}

/** One of Your group's three: an icon, its name and a line on where it stands. */
@Composable
private fun RowScope.PlayTile(icon: ImageVector, title: String, status: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(status, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RecentGameRow(game: TableGame) {
    val colors = LocalAppColors.current
    var chart by remember { mutableStateOf(false) }
    val title = game.winner?.let { "${it.name} won" } ?: "Nobody left standing"
    if (chart && game.log != null) {
        AlertDialog(
            onDismissRequest = { chart = false },
            confirmButton = { TextButton(onClick = { chart = false }) { Text("Close") } },
            title = { Text(title) },
            text = {
                game.log?.let { log ->
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        GameChartView(log, chartSeats(game), ink = colors.textPrimary, muted = colors.textMuted, line = colors.border)
                    }
                }
            }
        )
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface)
            .then(if (game.log != null) Modifier.clickable(onClickLabel = "Life chart and recap") { chart = true } else Modifier)
            .padding(12.dp)
    ) {
        Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = if (game.winner != null) colors.accent else colors.textDim, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(game.winner?.let { "${it.name} won" } ?: "Nobody left standing", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
            Text(
                game.players.joinToString(" · ") { p -> p.name + (p.commander?.let { " ($it)" } ?: "") },
                style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${game.minutes} min", style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
            Text(clockDate(game.endedAt), style = MaterialTheme.typography.bodySmall, color = colors.textDim)
            if (game.log != null) Text("Life chart", style = MaterialTheme.typography.bodySmall, color = colors.accent)
        }
    }
}

/** "3 Oct" — the day a game ended. */
private fun clockDate(atMillis: Long): String =
    java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(java.util.Date(atMillis))
