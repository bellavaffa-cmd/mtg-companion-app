package com.mtgcompanion.app.ui.lifecounter

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary

// One hue per seat, cycling through Magic's five colors of mana — same palette used for the
// accent-theme picker — so a 6th player just repeats the first hue at lower alpha.
private val PlayerColors = listOf(
    Color(0xFFC4893A), // White
    Color(0xFF3E7FC9), // Blue
    Color(0xFF8C4FC7), // Black (violet stand-in — true black is unreadable on a dark board)
    Color(0xFFC43A4E), // Red
    Color(0xFF3AA860), // Green
    Color(0xFFE0A84E)  // 6th seat
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifeCounterScreen(viewModel: LifeCounterViewModel, onBack: () -> Unit) {
    val players by viewModel.players.collectAsState()
    val startingLife by viewModel.startingLife.collectAsState()
    var showSettings by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text("LIFE COUNTER", color = GoldLight, style = MaterialTheme.typography.labelLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Gold)
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Groups, contentDescription = "Players & starting life", tint = Gold)
                    }
                    IconButton(onClick = { viewModel.resetAll() }) {
                        Icon(Icons.Filled.RestartAlt, contentDescription = "Reset", tint = Gold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
            )
        }
    ) { padding ->
        // 2 players stack top/bottom with the top seat flipped 180° so the phone can sit on the
        // table between them; 3+ wrap into a 2-column grid of face-up panels instead.
        val columns = if (players.size <= 2) 1 else 2
        val rows = players.chunked(columns)
        Column(modifier = Modifier.fillMaxSize().background(Bg).padding(padding)) {
            rows.forEachIndexed { rowIndex, rowPlayers ->
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    rowPlayers.forEach { player ->
                        val flip = players.size == 2 && rowIndex == 0
                        PlayerPanel(
                            player = player,
                            color = PlayerColors[(player.id - 1) % PlayerColors.size],
                            onIncrement = { viewModel.adjust(player.id, 1) },
                            onDecrement = { viewModel.adjust(player.id, -1) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .let { if (flip) it.graphicsLayer { rotationZ = 180f } else it }
                        )
                    }
                }
            }
        }
    }

    if (showSettings) {
        LifeCounterSettingsDialog(
            playerCount = players.size,
            startingLife = startingLife,
            onPlayerCountChange = viewModel::setPlayerCount,
            onStartingLifeChange = viewModel::setStartingLife,
            onDismiss = { showSettings = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerPanel(
    player: PlayerLife,
    color: Color,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.14f))
            .border(BorderStroke(1.dp, BorderColor))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onIncrement,
                        onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); repeat(5) { onIncrement() } }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add 1 (hold for 5)", tint = color.copy(alpha = 0.45f), modifier = Modifier.size(28.dp))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onDecrement,
                        onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); repeat(5) { onDecrement() } }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Subtract 1 (hold for 5)", tint = color.copy(alpha = 0.45f), modifier = Modifier.size(28.dp))
            }
        }
        Text(
            "${player.life}",
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 56.sp, color = TextPrimary),
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun LifeCounterSettingsDialog(
    playerCount: Int,
    startingLife: Int,
    onPlayerCountChange: (Int) -> Unit,
    onStartingLifeChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Bg,
        title = { Text("Players & life", color = GoldLight) },
        text = {
            Column {
                Text("PLAYERS", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                Row(modifier = Modifier.padding(top = 8.dp, bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (2..6).forEach { count -> ChoiceChip(label = "$count", selected = count == playerCount) { onPlayerCountChange(count) } }
                }
                Text("STARTING LIFE", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(20, 30, 40).forEach { life -> ChoiceChip(label = "$life", selected = life == startingLife) { onStartingLifeChange(life) } }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
            ) { Text("DONE", color = Bg) }
        }
    )
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) Bg else TextPrimary,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Gold else Bg)
            .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}
