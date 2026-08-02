package com.mtgcompanion.app.ui.lifecounter

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.draggable2D
import androidx.compose.foundation.gestures.rememberDraggable2DState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.PlayerProfile
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.TextDim
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary
import kotlin.math.abs
import kotlin.random.Random

/**
 * Vivid, fully-colored per-seat palette (not a low-alpha tint over a dark card) — the "Lotus"-style
 * look this screen is modeled on assigns every player their own bold color rather than a shared
 * dark theme. A 10-color list covers the format's usual player-count ceiling.
 */
private val PlayerPalette = listOf(
    Color(0xFFE0A030), Color(0xFF2E86D8), Color(0xFF8B3FD6), Color(0xFFE0342F), Color(0xFF34A853),
    Color(0xFFE0409A), Color(0xFF20B2B2), Color(0xFFFF7A1A), Color(0xFF7A8CFF), Color(0xFFB8C400)
)

private enum class PanelFace { LIFE, COMMANDER_DAMAGE, COUNTERS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifeCounterScreen(viewModel: LifeCounterViewModel, onBack: () -> Unit) {
    val players by viewModel.players.collectAsState()
    val startingLife by viewModel.startingLife.collectAsState()
    val currentTurnPlayerId by viewModel.currentTurnPlayerId.collectAsState()
    val turnNumber by viewModel.turnNumber.collectAsState()
    val turnSeconds by viewModel.turnSeconds.collectAsState()
    val matchSeconds by viewModel.matchSeconds.collectAsState()
    val timerRunning by viewModel.timerRunning.collectAsState()
    val gameModeState by viewModel.gameMode.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var showDice by remember { mutableStateOf(false) }
    var showGameModes by remember { mutableStateOf(false) }
    var showGameModeDetail by remember { mutableStateOf(false) }

    // The one player left standing (everyone else defeated) is the winner — only meaningful once
    // someone has actually been knocked out, so a fresh game with nobody defeated shows nothing.
    val winnerId = if (players.size > 1 && players.count { !it.isDefeated } == 1 && players.any { it.isDefeated }) {
        players.first { !it.isDefeated }.id
    } else null

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "LIFE COUNTER", color = GoldLight, style = MaterialTheme.typography.labelLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Gold)
                    }
                },
                actions = {
                    IconButton(onClick = { showDice = true }) {
                        Icon(Icons.Filled.Casino, contentDescription = "Dice & coin", tint = Gold)
                    }
                    IconButton(onClick = { showGameModes = true }) {
                        Icon(Icons.Filled.Public, contentDescription = "Game modes", tint = Gold)
                    }
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
        val lifeFontSize = when {
            players.size <= 4 -> 56.sp
            players.size <= 6 -> 44.sp
            else -> 34.sp
        }
        Column(modifier = Modifier.fillMaxSize().background(Bg).padding(padding)) {
            TurnTimerBar(
                turnNumber = turnNumber,
                currentPlayerId = currentTurnPlayerId,
                turnSeconds = turnSeconds,
                matchSeconds = matchSeconds,
                running = timerRunning,
                onToggleTimer = viewModel::toggleTimer,
                onNextTurn = viewModel::nextTurn
            )
            if (gameModeState.mode != GameModeKind.NONE) {
                GameModeBanner(state = gameModeState, onClick = { showGameModeDetail = true })
            }
            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                rows.forEachIndexed { rowIndex, rowPlayers ->
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        rowPlayers.forEach { player ->
                            val flip = players.size == 2 && rowIndex == 0
                            PlayerPanel(
                                player = player,
                                opponents = players.filter { it.id != player.id },
                                lifeFontSize = lifeFontSize,
                                isActiveTurn = player.id == currentTurnPlayerId,
                                isWinner = player.id == winnerId,
                                profiles = profiles,
                                onIncrement = { delta -> viewModel.adjust(player.id, delta) },
                                onSetLife = { viewModel.setLife(player.id, it) },
                                onAdjustCommanderDamage = { opponentId, delta -> viewModel.adjustCommanderDamage(player.id, opponentId, delta) },
                                onAdjustPoison = { viewModel.adjustPoison(player.id, it) },
                                onAdjustExperience = { viewModel.adjustExperience(player.id, it) },
                                onAdjustTax = { viewModel.adjustCommanderTax(player.id, it) },
                                onSetColor = { viewModel.setPlayerColor(player.id, it) },
                                onSetName = { viewModel.setPlayerName(player.id, it) },
                                onSetVictoryMessage = { viewModel.setVictoryMessage(player.id, it) },
                                onSetDefeatMessage = { viewModel.setDefeatMessage(player.id, it) },
                                onSetBackgroundImage = { viewModel.setBackgroundImage(player.id, it) },
                                onSaveProfile = { viewModel.saveProfile(player.id) },
                                onLoadProfile = { viewModel.loadProfile(player.id, it) },
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

    if (showDice) {
        DiceRollerDialog(onDismiss = { showDice = false })
    }

    if (showGameModes) {
        GameModesDialog(
            players = players,
            current = gameModeState.mode,
            onStartPlanechase = viewModel::startPlanechase,
            onStartArchenemy = viewModel::startArchenemy,
            onStop = viewModel::stopGameMode,
            onDismiss = { showGameModes = false }
        )
    }

    if (showGameModeDetail && gameModeState.mode != GameModeKind.NONE) {
        GameModeDetailDialog(
            state = gameModeState,
            onPlaneswalk = viewModel::planeswalk,
            onRollDie = viewModel::rollPlanarDie,
            onRevealScheme = viewModel::revealNextScheme,
            onDismiss = { showGameModeDetail = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerPanel(
    player: PlayerLife,
    opponents: List<PlayerLife>,
    lifeFontSize: TextUnit,
    isActiveTurn: Boolean,
    isWinner: Boolean,
    profiles: List<PlayerProfile>,
    onIncrement: (Int) -> Unit,
    onSetLife: (Int) -> Unit,
    onAdjustCommanderDamage: (opponentId: Int, delta: Int) -> Unit,
    onAdjustPoison: (Int) -> Unit,
    onAdjustExperience: (Int) -> Unit,
    onAdjustTax: (Int) -> Unit,
    onSetColor: (Int) -> Unit,
    onSetName: (String) -> Unit,
    onSetVictoryMessage: (String) -> Unit,
    onSetDefeatMessage: (String) -> Unit,
    onSetBackgroundImage: (String?) -> Unit,
    onSaveProfile: () -> Unit,
    onLoadProfile: (PlayerProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    val color = PlayerPalette[player.colorIndex % PlayerPalette.size]
    val lethal = player.isDefeated
    val hasImage = player.backgroundImageUri != null
    var face by remember(player.id) { mutableStateOf(PanelFace.LIFE) }
    var showKeypad by remember(player.id) { mutableStateOf(false) }
    var dragAccum by remember { mutableStateOf(Offset.Zero) }
    val draggableState = rememberDraggable2DState { delta -> dragAccum += delta }

    Box(
        modifier = modifier
            .let { if (!hasImage) it.background(color) else it }
            .border(
                BorderStroke(
                    if (isActiveTurn) 3.dp else 2.dp,
                    when {
                        lethal -> Color.Black.copy(alpha = 0.6f)
                        isActiveTurn -> Color.White.copy(alpha = 0.9f)
                        else -> Color.Black.copy(alpha = 0f)
                    }
                )
            )
            .draggable2D(
                state = draggableState,
                onDragStarted = { dragAccum = Offset.Zero },
                onDragStopped = {
                    val (dx, dy) = dragAccum
                    if (abs(dx) > abs(dy) && abs(dx) > 70f) {
                        face = if (face == PanelFace.COMMANDER_DAMAGE) PanelFace.LIFE else PanelFace.COMMANDER_DAMAGE
                    } else if (abs(dy) > 70f) {
                        face = if (face == PanelFace.COUNTERS) PanelFace.LIFE else PanelFace.COUNTERS
                    }
                    dragAccum = Offset.Zero
                }
            )
    ) {
        if (hasImage) {
            AsyncImage(
                model = player.backgroundImageUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
        }
        Crossfade(targetState = face, label = "panelFace") { current ->
            when (current) {
                PanelFace.LIFE -> LifeFace(
                    life = player.life,
                    fontSize = lifeFontSize,
                    onIncrement = onIncrement,
                    onOpenKeypad = { showKeypad = true }
                )
                PanelFace.COMMANDER_DAMAGE -> CommanderDamageFace(
                    player = player,
                    opponents = opponents,
                    colorFor = { id -> PlayerPalette[opponents.firstOrNull { it.id == id }?.colorIndex?.rem(PlayerPalette.size) ?: 0] },
                    onAdjust = onAdjustCommanderDamage,
                    onClose = { face = PanelFace.LIFE }
                )
                PanelFace.COUNTERS -> CountersFace(
                    player = player,
                    palette = PlayerPalette,
                    profiles = profiles,
                    onAdjustPoison = onAdjustPoison,
                    onAdjustExperience = onAdjustExperience,
                    onAdjustTax = onAdjustTax,
                    onSetColor = onSetColor,
                    onSetName = onSetName,
                    onSetVictoryMessage = onSetVictoryMessage,
                    onSetDefeatMessage = onSetDefeatMessage,
                    onSetBackgroundImage = onSetBackgroundImage,
                    onSaveProfile = onSaveProfile,
                    onLoadProfile = onLoadProfile,
                    onClose = { face = PanelFace.LIFE }
                )
            }
        }
        if (face == PanelFace.LIFE) {
            Text(
                player.name ?: "P${player.id}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .widthIn(max = 92.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
            // A quick shortcut into the commander-damage face without needing to swipe, and a
            // running total so a lethal hit is visible at a glance from the life face.
            val totalCommanderDamage = player.commanderDamage.values.sum()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable { face = PanelFace.COMMANDER_DAMAGE }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Filled.Whatshot, contentDescription = "Commander damage", tint = Color.White, modifier = Modifier.size(14.dp))
                if (totalCommanderDamage > 0) {
                    Text("$totalCommanderDamage", style = MaterialTheme.typography.labelMedium, color = Color.White)
                }
            }
            when {
                lethal -> Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                    Text(
                        player.defeatMessage,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(20.dp)
                    )
                }
                isWinner -> Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                    Text(
                        player.victoryMessage,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        }
    }

    if (showKeypad) {
        LifeKeypadDialog(
            initial = player.life,
            onConfirm = { onSetLife(it); showKeypad = false },
            onDismiss = { showKeypad = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LifeFace(
    life: Int,
    fontSize: TextUnit,
    onIncrement: (Int) -> Unit,
    onOpenKeypad: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Box(Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onIncrement(1) },
                        onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onIncrement(10) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add 1 (hold for 10)", tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(28.dp))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onIncrement(-1) },
                        onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onIncrement(-10) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Subtract 1 (hold for 10)", tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(28.dp))
            }
        }
        Text(
            "$life",
            style = MaterialTheme.typography.titleLarge.copy(fontSize = fontSize, color = Color.White),
            modifier = Modifier
                .align(Alignment.Center)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onOpenKeypad)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun CommanderDamageFace(
    player: PlayerLife,
    opponents: List<PlayerLife>,
    colorFor: (Int) -> Color,
    onAdjust: (opponentId: Int, delta: Int) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("COMMANDER DAMAGE", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
            IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Back to life total", tint = Color.White)
            }
        }
        Spacer(Modifier.height(6.dp))
        if (opponents.isEmpty()) {
            Text("No opponents yet.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
        } else {
            opponents.forEach { opponent ->
                val damage = player.commanderDamage[opponent.id] ?: 0
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                ) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(colorFor(opponent.id)))
                    Text(
                        opponent.name ?: "Player ${opponent.id}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onAdjust(opponent.id, -1) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Remove, contentDescription = "Remove 1 commander damage", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                    Text(
                        "$damage",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (damage >= 21) Color(0xFFFFB4A8) else Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(22.dp)
                    )
                    IconButton(onClick = { onAdjust(opponent.id, 1) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Add, contentDescription = "Add 1 commander damage", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CountersFace(
    player: PlayerLife,
    palette: List<Color>,
    profiles: List<PlayerProfile>,
    onAdjustPoison: (Int) -> Unit,
    onAdjustExperience: (Int) -> Unit,
    onAdjustTax: (Int) -> Unit,
    onSetColor: (Int) -> Unit,
    onSetName: (String) -> Unit,
    onSetVictoryMessage: (String) -> Unit,
    onSetDefeatMessage: (String) -> Unit,
    onSetBackgroundImage: (String?) -> Unit,
    onSaveProfile: () -> Unit,
    onLoadProfile: (PlayerProfile) -> Unit,
    onClose: () -> Unit
) {
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onSetBackgroundImage(uri.toString())
    }
    var nameText by remember(player.id) { mutableStateOf(player.name ?: "") }
    var victoryText by remember(player.id) { mutableStateOf(player.victoryMessage) }
    var defeatText by remember(player.id) { mutableStateOf(player.defeatMessage) }
    var urlText by remember(player.id) { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("CUSTOMIZE", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
            IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Back to life total", tint = Color.White)
            }
        }
        Spacer(Modifier.height(6.dp))
        MiniTextField(value = nameText, placeholder = "Player ${player.id}") { nameText = it; onSetName(it) }

        Spacer(Modifier.height(12.dp))
        Text("COUNTERS", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
        CounterRow("Poison", player.poison, onAdjust = onAdjustPoison)
        CounterRow("Experience", player.experience, onAdjust = onAdjustExperience)
        CounterRow("Commander tax", player.commanderTax, step = 2, onAdjust = onAdjustTax)

        Spacer(Modifier.height(10.dp))
        Text("PLAYER COLOR", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState())
        ) {
            palette.forEachIndexed { index, swatch ->
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(swatch)
                        .border(BorderStroke(if (index == player.colorIndex) 2.dp else 1.dp, Color.White.copy(alpha = if (index == player.colorIndex) 0.9f else 0.3f)), CircleShape)
                        .clickable { onSetColor(index) }
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Text("BACKGROUND", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
        Row(modifier = Modifier.padding(top = 4.dp)) {
            TextButton(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Text("CHOOSE PHOTO", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
            if (player.backgroundImageUri != null) {
                TextButton(onClick = { onSetBackgroundImage(null) }) {
                    Text("CLEAR", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                MiniTextField(value = urlText, placeholder = "Or paste an image/GIF URL") { urlText = it }
            }
            Spacer(Modifier.width(6.dp))
            TextButton(
                onClick = { onSetBackgroundImage(urlText.trim()) },
                enabled = urlText.isNotBlank()
            ) { Text("USE", color = Color.White, style = MaterialTheme.typography.labelMedium) }
        }

        Spacer(Modifier.height(10.dp))
        Text("VICTORY MESSAGE", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
        MiniTextField(value = victoryText, placeholder = "Victory!") { victoryText = it; onSetVictoryMessage(it) }
        Spacer(Modifier.height(8.dp))
        Text("DEFEAT MESSAGE", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
        MiniTextField(value = defeatText, placeholder = "Defeated") { defeatText = it; onSetDefeatMessage(it) }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("SAVED PROFILES", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
            TextButton(onClick = onSaveProfile, enabled = nameText.isNotBlank()) {
                Text("SAVE", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }
        if (profiles.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp).horizontalScroll(rememberScrollState())
            ) {
                profiles.forEach { profile ->
                    Text(
                        profile.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.3f))
                            .clickable {
                                nameText = profile.name
                                onLoadProfile(profile)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniTextField(value: String, placeholder: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.White,
            unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Color.White
        ),
        modifier = Modifier.fillMaxWidth().height(48.dp)
    )
}

@Composable
private fun CounterRow(label: String, value: Int, step: Int = 1, onAdjust: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.weight(1f))
        IconButton(onClick = { onAdjust(-step) }, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease $label", tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Text("$value", style = MaterialTheme.typography.bodyMedium, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.width(22.dp))
        IconButton(onClick = { onAdjust(step) }, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Filled.Add, contentDescription = "Increase $label", tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

/** Tap the life number to bring up an exact-value keypad instead of tapping ±1 repeatedly. */
@Composable
private fun LifeKeypadDialog(initial: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Bg,
        title = { Text("Set life total", color = GoldLight) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text.ifBlank { "0" },
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 40.sp),
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("-", "0", "⌫")).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                        row.forEach { key ->
                            KeypadButton(key) {
                                text = when (key) {
                                    "⌫" -> text.dropLast(1)
                                    "-" -> if (text.startsWith("-")) text.removePrefix("-") else "-$text"
                                    else -> if (text == "0") key else text + key
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text.toIntOrNull() ?: initial) },
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
            ) { Text("SET", color = Bg) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", color = TextMuted) }
        }
    )
}

@Composable
private fun KeypadButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Bg)
            .border(BorderStroke(1.dp, BorderColor), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
    }
}

@Composable
private fun DiceRollerDialog(onDismiss: () -> Unit) {
    var result by remember { mutableStateOf("—") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Bg,
        title = { Text("Dice & coin", color = GoldLight) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(result, style = MaterialTheme.typography.titleLarge, color = GoldLight, modifier = Modifier.padding(bottom = 16.dp))
                listOf(4, 6, 8).let { row -> DiceRow(row) { sides -> result = "d$sides → ${Random.nextInt(1, sides + 1)}" } }
                Spacer(Modifier.height(8.dp))
                listOf(10, 12, 20).let { row -> DiceRow(row) { sides -> result = "d$sides → ${Random.nextInt(1, sides + 1)}" } }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { result = if (Random.nextBoolean()) "Heads" else "Tails" },
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
                ) { Text("FLIP COIN", color = Bg) }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)) { Text("CLOSE", color = Bg) }
        }
    )
}

@Composable
private fun DiceRow(sidesList: List<Int>, onRoll: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        sidesList.forEach { sides ->
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Bg)
                    .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(10.dp))
                    .clickable { onRoll(sides) },
                contentAlignment = Alignment.Center
            ) {
                Text("d$sides", style = MaterialTheme.typography.labelMedium, color = Gold)
            }
        }
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
                Row(
                    modifier = Modifier.padding(top = 8.dp, bottom = 18.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    (2..10).forEach { count -> ChoiceChip(label = "$count", selected = count == playerCount) { onPlayerCountChange(count) } }
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

// ---- Turn tracker + match timer ----

@Composable
private fun TurnTimerBar(
    turnNumber: Int,
    currentPlayerId: Int,
    turnSeconds: Int,
    matchSeconds: Int,
    running: Boolean,
    onToggleTimer: () -> Unit,
    onNextTurn: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().background(Bg).padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("TURN $turnNumber · PLAYER $currentPlayerId", style = MaterialTheme.typography.labelMedium, color = Gold)
            Text(
                "${formatElapsed(turnSeconds)} this turn · ${formatElapsed(matchSeconds)} total",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted
            )
        }
        IconButton(onClick = onToggleTimer, modifier = Modifier.size(32.dp)) {
            Icon(if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (running) "Pause timer" else "Resume timer", tint = Gold)
        }
        TextButton(onClick = onNextTurn) {
            Text("NEXT TURN", color = Gold, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun formatElapsed(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

// ---- Planechase / Archenemy ----

@Composable
private fun GameModesDialog(
    players: List<PlayerLife>,
    current: GameModeKind,
    onStartPlanechase: () -> Unit,
    onStartArchenemy: (archenemyPlayerId: Int) -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit
) {
    var pickingArchenemy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Bg,
        title = { Text(if (pickingArchenemy) "Who is the Archenemy?" else "Game modes", color = GoldLight) },
        text = {
            Column {
                if (!pickingArchenemy) {
                    GameModeOption("Off", selected = current == GameModeKind.NONE) { onStop(); onDismiss() }
                    GameModeOption("Planechase", selected = current == GameModeKind.PLANECHASE) { onStartPlanechase(); onDismiss() }
                    GameModeOption("Archenemy", selected = current == GameModeKind.ARCHENEMY) { pickingArchenemy = true }
                } else {
                    players.forEach { player ->
                        GameModeOption(player.name ?: "Player ${player.id}", selected = false) {
                            onStartArchenemy(player.id)
                            onDismiss()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("CLOSE", color = TextMuted) }
        }
    )
}

@Composable
private fun GameModeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)
    ) {
        Icon(
            if (selected) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = Gold
        )
        Spacer(Modifier.width(10.dp))
        Text(label, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun GameModeBanner(state: GameModeState, onClick: () -> Unit) {
    val card = if (state.mode == GameModeKind.PLANECHASE) state.currentPlane else state.currentScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        when {
            state.loading -> {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Gold)
                Text("Shuffling…", style = MaterialTheme.typography.labelMedium, color = TextMuted, modifier = Modifier.weight(1f))
            }
            card != null -> {
                AsyncImage(
                    model = card.displayImageUrl?.toArtCropUrl(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(width = 48.dp, height = 34.dp).clip(RoundedCornerShape(4.dp))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (state.mode == GameModeKind.PLANECHASE) "PLANECHASE" else "ARCHENEMY",
                        style = MaterialTheme.typography.labelMedium, color = Gold
                    )
                    Text(card.name, style = MaterialTheme.typography.bodySmall, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextDim)
            }
            else -> Text("Tap to draw", style = MaterialTheme.typography.labelMedium, color = TextMuted, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun GameModeDetailDialog(
    state: GameModeState,
    onPlaneswalk: () -> Unit,
    onRollDie: () -> PlanarDieFace,
    onRevealScheme: () -> Unit,
    onDismiss: () -> Unit
) {
    var dieResult by remember { mutableStateOf<PlanarDieFace?>(null) }
    val card = if (state.mode == GameModeKind.PLANECHASE) state.currentPlane else state.currentScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Bg,
        title = { Text(if (state.mode == GameModeKind.PLANECHASE) "Planechase" else "Archenemy", color = GoldLight) },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                if (card == null) {
                    Text("Nothing drawn yet.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                } else {
                    CardFace(card)
                    dieResult?.let {
                        Text(
                            "Planar die: ${it.name.lowercase().replaceFirstChar { c -> c.uppercase() }}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Gold,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                    if (state.mode == GameModeKind.ARCHENEMY && state.ongoingSchemes.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Text("ONGOING", style = MaterialTheme.typography.labelMedium, color = TextDim)
                        state.ongoingSchemes.forEach { scheme ->
                            Text("• ${scheme.name}", style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (state.mode == GameModeKind.PLANECHASE) {
                Row {
                    TextButton(onClick = { dieResult = onRollDie() }) { Text("ROLL DIE", color = Gold) }
                    Button(
                        onClick = onPlaneswalk,
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
                    ) { Text("PLANESWALK", color = Bg) }
                }
            } else {
                Button(
                    onClick = onRevealScheme,
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
                ) { Text("REVEAL SCHEME", color = Bg) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CLOSE", color = TextMuted) }
        }
    )
}

@Composable
private fun CardFace(card: ScryfallCard) {
    AsyncImage(
        model = card.displayImageUrl,
        contentDescription = card.name,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxWidth().aspectRatio(1.4f).clip(RoundedCornerShape(8.dp))
    )
    Text(card.name, style = MaterialTheme.typography.titleMedium, color = GoldLight, modifier = Modifier.padding(top = 10.dp))
    Text(
        card.displayOracleText ?: "No text.",
        style = MaterialTheme.typography.bodySmall,
        color = TextPrimary,
        modifier = Modifier.padding(top = 6.dp)
    )
}
