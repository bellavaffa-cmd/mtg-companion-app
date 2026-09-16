package com.mtgcompanion.app.ui.lifecounter

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Castle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.TextDim
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A full-screen multiplayer life counter meant to lie flat in the middle of the table: every tile
 * faces its player's seat, and everything that isn't a life total lives behind the centre button.
 */
@Composable
fun LifeCounterScreen(viewModel: LifeCounterViewModel, onBack: () -> Unit) {
    val ready by viewModel.ready.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val players by viewModel.players.collectAsState()
    val gameNumber by viewModel.gameNumber.collectAsState()
    val currentTurnPlayerId by viewModel.currentTurnPlayerId.collectAsState()
    val turnNumber by viewModel.turnNumber.collectAsState()
    val turnSeconds by viewModel.turnSeconds.collectAsState()
    val matchSeconds by viewModel.matchSeconds.collectAsState()
    val timerRunning by viewModel.timerRunning.collectAsState()
    val gameModeState by viewModel.gameMode.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val monarchPlayerId by viewModel.monarchPlayerId.collectAsState()
    val initiativePlayerId by viewModel.initiativePlayerId.collectAsState()
    val dayNight by viewModel.dayNight.collectAsState()
    val history by viewModel.history.collectAsState()
    val highRollRequested by viewModel.highRollRequested.collectAsState()

    var menuOpen by remember { mutableStateOf(false) }
    var showDice by remember { mutableStateOf(false) }
    var showHighRoll by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showLayouts by remember { mutableStateOf(false) }
    var showCardSearch by remember { mutableStateOf(false) }
    var showGameModeDetail by remember { mutableStateOf(false) }
    var pickingArchenemy by remember { mutableStateOf(false) }
    var confirmRestart by remember { mutableStateOf(false) }
    var replayTips by remember { mutableStateOf(false) }
    var floatingToken by remember { mutableStateOf<TokenKind?>(null) }
    var tokenStart by remember { mutableStateOf<Offset?>(null) }

    val seatBounds = remember { mutableStateMapOf<Int, Rect>() }
    var tableBounds by remember { mutableStateOf(Rect.Zero) }

    BackHandler(enabled = menuOpen) { menuOpen = false }

    LaunchedEffect(highRollRequested) {
        if (highRollRequested) {
            showHighRoll = true
            viewModel.consumeHighRollRequest()
        }
    }

    // Advances every few seconds while "cycle messages" is on, so defeat/victory messages rotate.
    var messageTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(settings.saltyMessages && settings.cycleMessages) {
        if (settings.saltyMessages && settings.cycleMessages) {
            while (true) {
                delay(4_000)
                messageTick++
            }
        }
    }

    val layout = TableLayouts.byId(settings.layoutId)
    val winnerId = winnerIdOf(players, settings.autoKill)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize()) {
            if (gameModeState.mode != GameModeKind.NONE) {
                GameModeBanner(state = gameModeState, onClick = { showGameModeDetail = true })
            }
            if (settings.turnTrackerEnabled || settings.gameTimerEnabled) {
                TurnStrip(
                    showTurns = settings.turnTrackerEnabled,
                    showTimer = settings.gameTimerEnabled,
                    turnNumber = turnNumber,
                    currentPlayerName = players.firstOrNull { it.id == currentTurnPlayerId }?.displayName ?: "Player $currentTurnPlayerId",
                    turnSeconds = turnSeconds,
                    matchSeconds = matchSeconds,
                    running = timerRunning,
                    onToggleTimer = viewModel::toggleTimer,
                    onNextTurn = viewModel::nextTurn
                )
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned { tableBounds = it.boundsInRoot() }
            ) {
                if (ready) {
                    Column(Modifier.fillMaxSize().padding(3.dp)) {
                        layout.rows.forEach { row ->
                            Row(Modifier.weight(1f).fillMaxWidth()) {
                                row.cells.forEach { cell ->
                                    val player = cell.seat?.let { seat -> players.firstOrNull { it.id == seat } }
                                    if (player == null) {
                                        EmptySeat(Modifier.weight(1f).fillMaxSize())
                                    } else {
                                        val reason = player.lossReason(settings.autoKill)
                                        PlayerTile(
                                            player = player,
                                            opponents = players.filter { it.id != player.id },
                                            settings = settings,
                                            isActiveTurn = settings.turnTrackerEnabled && player.id == currentTurnPlayerId,
                                            isMonarch = player.id == monarchPlayerId,
                                            hasInitiative = player.id == initiativePlayerId,
                                            defeatMessage = reason?.let { defeatMessageFor(player, it, settings, gameNumber, messageTick) },
                                            victoryMessage = if (player.id == winnerId) victoryMessageFor(player, settings, gameNumber, messageTick) else null,
                                            profiles = profiles,
                                            actions = viewModel.actionsFor(player.id),
                                            onTokenTap = { kind ->
                                                tokenStart = seatBounds[player.id]?.center
                                                floatingToken = kind
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxSize()
                                                .onGloballyPositioned { seatBounds[player.id] = it.boundsInRoot() }
                                                .faceSeat(cell.facing)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                dayNight?.let { state ->
                    DayNightPill(
                        state = state,
                        onToggle = viewModel::toggleDayNight,
                        onStop = viewModel::stopDayNight,
                        modifier = Modifier.align(Alignment.Center).offset(y = (-58).dp)
                    )
                }

                if (menuOpen) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { menuOpen = false }
                    )
                    RadialMenu(
                        items = listOf(
                            MenuItem("Restart", Icons.Filled.Replay) { confirmRestart = true },
                            MenuItem("High roll", Icons.Filled.Casino) { showHighRoll = true },
                            MenuItem("Seating", Icons.Filled.TableRestaurant) { showLayouts = true },
                            MenuItem("Settings", Icons.Filled.Settings) { showSettings = true },
                            MenuItem("Tips", Icons.AutoMirrored.Filled.Help) { replayTips = true },
                            MenuItem("Exit", Icons.AutoMirrored.Filled.ArrowBack, onBack)
                        ),
                        onPicked = { menuOpen = false },
                        modifier = Modifier.align(Alignment.Center)
                    )
                    ToolBar(
                        items = listOf(
                            MenuItem("Dice", Icons.Filled.Casino) { showDice = true },
                            MenuItem("History", Icons.Filled.History) { showHistory = true },
                            MenuItem("Card search", Icons.Filled.Search) { showCardSearch = true },
                            MenuItem("Monarch", Icons.Filled.WorkspacePremium) {
                                tokenStart = monarchPlayerId?.let { seatBounds[it]?.center }
                                floatingToken = TokenKind.MONARCH
                            },
                            MenuItem("Initiative", Icons.Filled.Castle) {
                                tokenStart = initiativePlayerId?.let { seatBounds[it]?.center }
                                floatingToken = TokenKind.INITIATIVE
                            },
                            MenuItem("Day/Night", Icons.Filled.LightMode) { viewModel.startDayNight() },
                            MenuItem("Planechase", Icons.Filled.Public) {
                                if (gameModeState.mode != GameModeKind.PLANECHASE) viewModel.startPlanechase()
                                showGameModeDetail = true
                            },
                            MenuItem("Archenemy", Icons.Filled.Shield) {
                                if (gameModeState.mode == GameModeKind.ARCHENEMY) showGameModeDetail = true else pickingArchenemy = true
                            },
                            MenuItem("Bounty", Icons.Filled.Flag) {
                                if (gameModeState.mode != GameModeKind.BOUNTY) viewModel.startBounty()
                                showGameModeDetail = true
                            }
                        ),
                        onPicked = { menuOpen = false },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }

                CenterMenuButton(open = menuOpen, onClick = { menuOpen = !menuOpen }, modifier = Modifier.align(Alignment.Center))
            }
        }

        floatingToken?.let { kind ->
            TokenPlacementOverlay(
                kind = kind,
                start = tokenStart ?: tableBounds.center,
                // Positions are cached by player id, so a smaller seating can leave stale entries
                // for seats that no longer exist — only ever drop onto a current player.
                seatBounds = seatBounds.filterKeys { id -> players.any { it.id == id } },
                holderName = players.firstOrNull { it.id == if (kind == TokenKind.MONARCH) monarchPlayerId else initiativePlayerId }?.displayName,
                onDrop = { seat ->
                    if (kind == TokenKind.MONARCH) viewModel.setMonarch(seat) else viewModel.setInitiative(seat)
                    floatingToken = null
                },
                onRemove = {
                    if (kind == TokenKind.MONARCH) viewModel.setMonarch(null) else viewModel.setInitiative(null)
                    floatingToken = null
                },
                onDone = { floatingToken = null }
            )
        }

        if (ready && (!settings.tipsSeen || replayTips)) {
            TipsOverlay(
                settings = settings,
                onFinished = {
                    viewModel.markTipsSeen()
                    replayTips = false
                }
            )
        }
    }

    if (showDice) {
        DiceRollerDialog(players = players, startWithHighRoll = false, onHighRoll = viewModel::rollHighRoll, onSetFirstPlayer = viewModel::setFirstPlayer, onDismiss = { showDice = false })
    }
    if (showHighRoll) {
        DiceRollerDialog(players = players, startWithHighRoll = true, onHighRoll = viewModel::rollHighRoll, onSetFirstPlayer = viewModel::setFirstPlayer, onDismiss = { showHighRoll = false })
    }
    if (showHistory) {
        GameHistoryDialog(entries = history, players = players, onDismiss = { showHistory = false })
    }
    if (showLayouts) {
        LayoutPickerDialog(currentLayoutId = settings.layoutId, onSelect = viewModel::selectLayout, onDismiss = { showLayouts = false })
    }
    if (showCardSearch) {
        CardSearchDialog(onSearch = viewModel::searchCards, onDismiss = { showCardSearch = false })
    }
    if (showSettings) {
        LifeCounterSettingsScreen(
            settings = settings,
            profiles = profiles,
            onUpdate = viewModel::updateSettings,
            onSetStartingLife = viewModel::setStartingLife,
            onDeleteProfile = viewModel::deleteProfile,
            onResetBackgrounds = viewModel::resetPlayerBackgrounds,
            onShowTips = { showSettings = false; replayTips = true },
            onRestartGame = viewModel::newGame,
            onDismiss = { showSettings = false }
        )
    }
    if (pickingArchenemy) {
        ArchenemyPickerDialog(
            players = players,
            onPick = { id -> viewModel.startArchenemy(id); showGameModeDetail = true },
            onDismiss = { pickingArchenemy = false }
        )
    }
    if (showGameModeDetail && gameModeState.mode != GameModeKind.NONE) {
        GameModeDetailDialog(
            state = gameModeState,
            onPlaneswalk = viewModel::planeswalk,
            onRollDie = viewModel::rollPlanarDie,
            onRevealScheme = viewModel::revealNextScheme,
            onRevealBounty = viewModel::revealNextBounty,
            onStop = viewModel::stopGameMode,
            onDismiss = { showGameModeDetail = false }
        )
    }
    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            containerColor = Bg,
            title = { Text("Restart the game?", color = GoldLight) },
            text = { Text("Everyone goes back to starting life, and counters and history are cleared.", color = TextPrimary) },
            confirmButton = { GoldButton("RESTART") { viewModel.newGame(); confirmRestart = false } },
            dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text("CANCEL", color = TextMuted) } }
        )
    }
}

private fun LifeCounterViewModel.actionsFor(id: Int) = PlayerTileActions(
    adjustLife = { adjust(id, it) },
    setLife = { setLife(id, it) },
    adjustCommanderDamage = { source, delta -> adjustCommanderDamage(id, source, delta) },
    adjustCounter = { kind, delta -> adjustCounter(id, kind, delta) },
    adjustMana = { color, delta -> adjustMana(id, color, delta) },
    adjustTax = { slot, delta -> adjustCommanderTax(id, slot, delta) },
    setHasPartner = { setHasPartner(id, it) },
    kill = { kill(id) },
    revive = { revive(id) },
    setColor = { setPlayerColor(id, it) },
    setName = { setPlayerName(id, it) },
    setVictoryMessage = { setVictoryMessage(id, it) },
    setDefeatMessage = { setDefeatMessage(id, it) },
    setBackgroundImage = { setBackgroundImage(id, it) },
    saveProfile = { saveProfile(id) },
    loadProfile = { loadProfile(id, it) }
)

// ---- Defeat & victory messages ----

/**
 * A player's own message wins; otherwise one is drawn from the settings list matching why they
 * lost. The pick is keyed on player and game, so it holds steady for the whole game (or rotates on
 * [tick] when cycling) instead of changing on every recomposition.
 */
private fun defeatMessageFor(player: PlayerLife, reason: LossReason, settings: LifeCounterSettings, gameNumber: Int, tick: Int): String {
    player.defeatMessage?.let { return it }
    if (!settings.saltyMessages) return "Defeated"
    val list = when (reason) {
        LossReason.COMMANDER_DAMAGE -> settings.commanderDefeatMessages
        LossReason.POISON -> settings.poisonDefeatMessages
        LossReason.LIFE, LossReason.KILLED -> settings.defeatMessages
    }
    return pickMessage(list, "Defeated", player.id, gameNumber, tick)
}

private fun victoryMessageFor(player: PlayerLife, settings: LifeCounterSettings, gameNumber: Int, tick: Int): String {
    player.victoryMessage?.let { return it }
    if (!settings.saltyMessages) return "Victory!"
    return pickMessage(settings.victoryMessages, "Victory!", player.id, gameNumber, tick)
}

private fun pickMessage(list: List<String>, fallback: String, playerId: Int, gameNumber: Int, tick: Int): String =
    if (list.isEmpty()) fallback else list[Math.floorMod(playerId * 31 + gameNumber * 17 + tick, list.size)]

// ---- Table chrome ----

@Composable
private fun EmptySeat(modifier: Modifier) {
    Box(modifier.padding(3.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF141414)))
}

private class MenuItem(val label: String, val icon: ImageVector, val onClick: () -> Unit)

@Composable
private fun CenterMenuButton(open: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(58.dp)
            .clip(CircleShape)
            .background(Bg)
            .border(BorderStroke(2.dp, Gold), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (open) Icons.Filled.Close else Icons.Filled.GridView,
            contentDescription = if (open) "Close menu" else "Open menu",
            tint = Gold,
            modifier = Modifier.size(26.dp)
        )
    }
}

/** Game-level actions arranged in a ring around the centre button. */
@Composable
private fun RadialMenu(items: List<MenuItem>, onPicked: () -> Unit, modifier: Modifier = Modifier) {
    val radius = 108.dp
    Box(modifier) {
        items.forEachIndexed { index, item ->
            // Start at the top and go clockwise.
            val angle = Math.toRadians(-90.0 + index * 360.0 / items.size)
            MenuPill(
                item = item,
                onPicked = onPicked,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = radius * cos(angle).toFloat(), y = radius * sin(angle).toFloat())
            )
        }
    }
}

@Composable
private fun MenuPill(item: MenuItem, onPicked: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Bg)
            .border(BorderStroke(1.dp, Gold.copy(alpha = 0.7f)), RoundedCornerShape(50))
            .clickable { onPicked(); item.onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(item.icon, contentDescription = null, tint = Gold, modifier = Modifier.size(16.dp))
        Text(item.label.uppercase(), style = MaterialTheme.typography.labelMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
    }
}

/** Table tools and game modes, along the bottom while the menu is open. */
@Composable
private fun ToolBar(items: List<MenuItem>, onPicked: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(Bg)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        items.forEach { item -> MenuPill(item = item, onPicked = onPicked) }
    }
}

@Composable
private fun TurnStrip(
    showTurns: Boolean,
    showTimer: Boolean,
    turnNumber: Int,
    currentPlayerName: String,
    turnSeconds: Int,
    matchSeconds: Int,
    running: Boolean,
    onToggleTimer: () -> Unit,
    onNextTurn: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().background(Bg).padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (showTurns) {
                Text("TURN $turnNumber · ${currentPlayerName.uppercase()}", style = MaterialTheme.typography.labelMedium, color = Gold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (showTimer) {
                Text("${formatElapsed(turnSeconds)} this turn · ${formatElapsed(matchSeconds)} total", style = MaterialTheme.typography.labelMedium, color = TextMuted)
            }
        }
        if (showTimer) {
            IconButton(onClick = onToggleTimer, modifier = Modifier.size(32.dp)) {
                Icon(if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (running) "Pause timer" else "Resume timer", tint = Gold)
            }
        }
        if (showTurns) {
            TextButton(onClick = onNextTurn) { Text("NEXT TURN", color = Gold, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
private fun DayNightPill(state: DayNight, onToggle: () -> Unit, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val isDay = state == DayNight.DAY
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (isDay) Color(0xFFFFE08A) else Color(0xFF1F2A5C))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)), RoundedCornerShape(50))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.clickable(onClick = onToggle).padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
        ) {
            Icon(
                if (isDay) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                contentDescription = null,
                tint = if (isDay) Color(0xFF6B4A00) else Color.White,
                modifier = Modifier.size(16.dp)
            )
            Text(if (isDay) "DAY" else "NIGHT", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (isDay) Color(0xFF6B4A00) else Color.White)
        }
        IconButton(onClick = onStop, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Stop tracking day and night", tint = if (isDay) Color(0xFF6B4A00) else Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun GameModeBanner(state: GameModeState, onClick: () -> Unit) {
    val card = when (state.mode) {
        GameModeKind.PLANECHASE -> state.currentPlane
        GameModeKind.ARCHENEMY -> state.currentScheme
        GameModeKind.BOUNTY -> state.currentBounty
        GameModeKind.NONE -> null
    }
    val label = when (state.mode) {
        GameModeKind.PLANECHASE -> "PLANECHASE"
        GameModeKind.ARCHENEMY -> "ARCHENEMY"
        GameModeKind.BOUNTY -> "BOUNTY"
        GameModeKind.NONE -> ""
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().background(Bg).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        if (state.loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Gold)
        } else if (card != null) {
            val image = if (state.mode == GameModeKind.BOUNTY) card.cardFaces?.firstOrNull()?.imageUris?.normal else card.displayImageUrl
            AsyncImage(
                model = image?.toArtCropUrl(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 44.dp, height = 30.dp).clip(RoundedCornerShape(8.dp))
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Gold)
            Text(
                when {
                    state.loading -> "Shuffling…"
                    card != null -> card.cardFaces?.firstOrNull()?.name?.takeIf { state.mode == GameModeKind.BOUNTY } ?: card.name
                    else -> "Tap to draw"
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextDim)
    }
}

// ---- Monarch / Initiative token ----

/**
 * The Monarch or Initiative as a token dragged onto whoever holds it. Dropping it on a tile hands
 * it to that player; dropping it anywhere else leaves it floating so it can be picked up again.
 */
@Composable
private fun TokenPlacementOverlay(
    kind: TokenKind,
    start: Offset,
    seatBounds: Map<Int, Rect>,
    holderName: String?,
    onDrop: (Int) -> Unit,
    onRemove: () -> Unit,
    onDone: () -> Unit
) {
    val density = LocalDensity.current
    val tokenSize = 64.dp
    val halfPx = with(density) { (tokenSize / 2).toPx() }
    var origin by remember { mutableStateOf<Offset?>(null) }
    // Token centre, in this overlay's own coordinates.
    var position by remember(kind) { mutableStateOf<Offset?>(null) }
    val title = if (kind == TokenKind.MONARCH) "MONARCH" else "INITIATIVE"

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .onGloballyPositioned { coords ->
                val o = coords.positionInRoot()
                origin = o
                if (position == null) position = start - o
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp).widthIn(max = 320.dp)
        ) {
            Text("DRAG THE $title TO A PLAYER", style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(
                holderName?.let { "Currently: $it" } ?: "Nobody holds it yet",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 2.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                TextButton(onClick = onRemove) { Text("REMOVE FROM GAME", color = Color(0xFFFF8A80)) }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onDone) { Text("DONE", color = Gold) }
            }
        }

        val current = position
        val o = origin
        if (current != null && o != null) {
            Box(
                modifier = Modifier
                    .offset { IntOffset((current.x - halfPx).roundToInt(), (current.y - halfPx).roundToInt()) }
                    .size(tokenSize)
                    .clip(CircleShape)
                    .background(Bg)
                    .border(BorderStroke(3.dp, Gold), CircleShape)
                    .pointerInput(kind) {
                        detectDragGestures(
                            onDragEnd = {
                                val point = (position ?: return@detectDragGestures) + o
                                seatBounds.entries.firstOrNull { it.value.contains(point) }?.key?.let(onDrop)
                            }
                        ) { change, amount ->
                            change.consume()
                            position = (position ?: Offset.Zero) + amount
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (kind == TokenKind.MONARCH) Icons.Filled.WorkspacePremium else Icons.Filled.Castle,
                    contentDescription = title,
                    tint = Gold,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
    }
}

// ---- First-run tips ----

private class Tip(val title: String, val body: String)

/** A short walkthrough of the gestures, shown once and replayable from the menu. */
@Composable
private fun TipsOverlay(settings: LifeCounterSettings, onFinished: () -> Unit) {
    val tapHow = if (settings.verticalTapAreas) "the top half of a tile to gain life and the bottom half to lose it"
    else "the right half of a tile to gain life and the left half to lose it"
    val tips = listOf(
        Tip("CHANGE LIFE", "Tap $tapHow. Hold to change it by ${settings.longPressAmount}."),
        Tip("COMMANDER DAMAGE", "Swipe sideways on your tile to track damage from each opponent's commander."),
        Tip("YOUR OPTIONS", "Swipe up or down on your tile for counters, mana, partner, color, background and more."),
        Tip("EVERYTHING ELSE", "Tap the button in the middle for dice, history, seating, settings, the Monarch and game modes.")
    )
    var index by remember { mutableIntStateOf(0) }
    val tip = tips[index]
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(28.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Bg)
                .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
            Text("${index + 1} / ${tips.size}", style = MaterialTheme.typography.labelMedium, color = TextDim)
            Text(tip.title, style = MaterialTheme.typography.titleMedium, color = GoldLight, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            Text(tip.body, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
            Row(modifier = Modifier.padding(top = 18.dp)) {
                TextButton(onClick = onFinished) { Text("SKIP", color = TextMuted) }
                Spacer(Modifier.width(8.dp))
                GoldButton(if (index == tips.lastIndex) "GOT IT" else "NEXT") {
                    if (index == tips.lastIndex) onFinished() else index++
                }
            }
        }
    }
}
