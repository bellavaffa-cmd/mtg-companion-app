package com.mtgcompanion.app.ui.lifecounter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
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
import com.mtgcompanion.app.data.SeatMemory
import com.mtgcompanion.app.ui.common.SectionHeader
import com.mtgcompanion.app.ui.theme.LocalAppColors

/**
 * The Play tab: start a life counter game on this phone, join someone else's table with your phone
 * as the remote for your seat (or go back to the seat you're in), run a small event, and the games
 * played here.
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
    onOpenEvents: (() -> Unit)? = null
) {
    val colors = LocalAppColors.current
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
    ) {
        item {
            Text("Play", style = MaterialTheme.typography.headlineMedium, color = colors.textPrimary, modifier = Modifier.padding(top = 18.dp, bottom = 4.dp))
        }
        item { StartGameCard(onStartGame) }
        remoteSeat?.let { seat ->
            item {
                PlayRow(Icons.Filled.EventSeat, "Back to seat ${seat.seat}", "You're still at a table — open your remote", highlight = true) {
                    onOpenRemote(seat.matchId, seat.seat)
                }
            }
        }
        item { PlayRow(Icons.Filled.QrCodeScanner, "Join a table", "Scan a seat's QR code: your phone becomes your remote") { onJoinTable() } }
        item { PlayRow(Icons.Filled.Groups, "Playgroup", "Your record across every deck: who you play, your nemesis, your best decks") { onOpenPlaygroup() } }
        onOpenEvents?.let { open ->
            item { PlayRow(Icons.Filled.EmojiEvents, "Events", "Run a Swiss or Commander pod event: pairings, round clock, standings") { open() } }
        }
        item { PlayRow(Icons.Filled.MenuBook, "Rules", "Look up a rule or a card's rulings") { onOpenRules() } }
        item { SectionHeader("Recent games", modifier = Modifier.padding(top = 10.dp)) }
        if (games.isEmpty()) {
            item { Text("Games played on this phone's life counter show up here.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted) }
        }
        items(games.take(20), key = { it.id }) { game -> RecentGameRow(game) }
        item { Box(Modifier.height(24.dp)) }
    }
}

@Composable
private fun StartGameCard(onClick: () -> Unit) {
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
        Text("Life counter", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        Text("Start a game on this phone · up to 8 players, turn timer, deck tokens", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
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

@Composable
private fun RecentGameRow(game: TableGame) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(12.dp)
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
        }
    }
}

/** "3 Oct" — the day a game ended. */
private fun clockDate(atMillis: Long): String =
    java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(java.util.Date(atMillis))
