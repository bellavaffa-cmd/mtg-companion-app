package com.mtgcompanion.app.ui.lifecounter

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Castle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.material3.Text

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
    var showSeating by remember { mutableStateOf(false) }
    var showCardSearch by remember { mutableStateOf(false) }
    var showGameMode by remember { mutableStateOf(false) }
    var pickingArchenemy by remember { mutableStateOf(false) }
    var confirmRestart by remember { mutableStateOf(false) }
    var replayTips by remember { mutableStateOf(false) }
    var keypadPlayerId by remember { mutableStateOf<Int?>(null) }
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

    Box(Modifier.fillMaxSize().background(TableColors.Background)) {
        Column(Modifier.fillMaxSize()) {
            if (gameModeState.mode != GameModeKind.NONE) {
                GameModeBanner(state = gameModeState, onClick = { showGameMode = true })
            }
            if (settings.turnTrackerEnabled || settings.gameTimerEnabled) {
                TurnStrip(
                    showTurns = settings.turnTrackerEnabled,
                    showTimer = settings.gameTimerEnabled,
                    turnNumber = turnNumber,
                    currentPlayer = players.firstOrNull { it.id == currentTurnPlayerId },
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
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(TableGap)) {
                        layout.rows.forEach { row ->
                            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TableGap)) {
                                row.cells.forEach { cell ->
                                    val player = cell.seat?.let { seat -> players.firstOrNull { it.id == seat } }
                                    if (player == null) {
                                        Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(24.dp)).background(TableColors.Surface))
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
                                            actions = viewModel.actionsFor(player.id, openKeypad = { keypadPlayerId = player.id }),
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
                        modifier = Modifier.align(Alignment.Center).offset(y = (-64).dp)
                    )
                }

                val scrim by animateFloatAsState(if (menuOpen) 0.6f else 0f, tween(TableMotion.FAST), label = "menuScrim")
                if (scrim > 0f) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = scrim))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { menuOpen = false }
                    )
                }
                if (menuOpen) {
                    RadialMenu(
                        items = listOf(
                            RadialItem("Restart", TableColors.MenuRestart, -145f) { confirmRestart = true },
                            RadialItem("Exit", TableColors.MenuExit, -90f, onBack),
                            RadialItem("High roll", TableColors.MenuHighRoll, -35f) { showHighRoll = true },
                            RadialItem("Settings", TableColors.MenuSettings, 25f) { showSettings = true },
                            RadialItem("Tips", TableColors.MenuTips, 95f) { replayTips = true },
                            RadialItem("Seating", TableColors.MenuSeating, 165f) { showSeating = true }
                        ),
                        onPicked = { menuOpen = false },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = menuOpen,
                    enter = slideInVertically(tween(TableMotion.FAST, easing = TableMotion.SlideIn)) { it },
                    exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(200)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    ToolBar(
                        items = listOf(
                            ToolItem("Dice", Icons.Filled.Casino) { showDice = true },
                            ToolItem("History", Icons.Filled.History) { showHistory = true },
                            ToolItem("Card search", Icons.Filled.Search) { showCardSearch = true },
                            ToolItem("Monarch", Icons.Filled.WorkspacePremium) {
                                tokenStart = monarchPlayerId?.let { seatBounds[it]?.center }
                                floatingToken = TokenKind.MONARCH
                            },
                            ToolItem("Initiative", Icons.Filled.Castle) {
                                tokenStart = initiativePlayerId?.let { seatBounds[it]?.center }
                                floatingToken = TokenKind.INITIATIVE
                            },
                            ToolItem("Day/Night", Icons.Filled.LightMode) { viewModel.startDayNight() },
                            ToolItem("Planechase", Icons.Filled.Public) {
                                if (gameModeState.mode != GameModeKind.PLANECHASE) viewModel.startPlanechase()
                                showGameMode = true
                            },
                            ToolItem("Archenemy", Icons.Filled.Shield) {
                                if (gameModeState.mode == GameModeKind.ARCHENEMY) showGameMode = true else pickingArchenemy = true
                            },
                            ToolItem("Bounty", Icons.Filled.Flag) {
                                if (gameModeState.mode != GameModeKind.BOUNTY) viewModel.startBounty()
                                showGameMode = true
                            }
                        ),
                        onPicked = { menuOpen = false }
                    )
                }

                MenuButton(open = menuOpen, onClick = { menuOpen = !menuOpen }, modifier = Modifier.align(Alignment.Center))
            }
        }

        floatingToken?.let { kind ->
            TokenPlacementOverlay(
                kind = kind,
                start = tokenStart ?: tableBounds.center,
                // Positions are cached by player id, so a smaller seating can leave stale entries
                // for seats that no longer exist — only ever drop onto a current player.
                seatBounds = seatBounds.filterKeys { id -> players.any { it.id == id } },
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

        keypadPlayerId?.let { id ->
            players.firstOrNull { it.id == id }?.let { player ->
                LifeKeypadOverlay(
                    playerName = player.displayName,
                    initial = player.life,
                    seat = seatColor(player.colorIndex),
                    onConfirm = { viewModel.setLife(id, it) },
                    onDismiss = { keypadPlayerId = null }
                )
            }
        }
        if (showDice) {
            DiceOverlay(players, startWithHighRoll = false, onHighRoll = viewModel::rollHighRoll, onSetFirstPlayer = viewModel::setFirstPlayer, onDismiss = { showDice = false })
        }
        if (showHighRoll) {
            DiceOverlay(players, startWithHighRoll = true, onHighRoll = viewModel::rollHighRoll, onSetFirstPlayer = viewModel::setFirstPlayer, onDismiss = { showHighRoll = false })
        }
        if (showHistory) {
            GameHistoryOverlay(entries = history, players = players, onDismiss = { showHistory = false })
        }
        if (showSeating) {
            SeatingOverlay(currentLayoutId = settings.layoutId, onSelect = viewModel::selectLayout, onDismiss = { showSeating = false })
        }
        if (showCardSearch) {
            CardSearchOverlay(onSearch = viewModel::searchCards, onDismiss = { showCardSearch = false })
        }
        if (showSettings) {
            LifeCounterSettingsOverlay(
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
            ArchenemyPickerOverlay(
                players = players,
                onPick = { id -> viewModel.startArchenemy(id); showGameMode = true },
                onDismiss = { pickingArchenemy = false }
            )
        }
        if (showGameMode && gameModeState.mode != GameModeKind.NONE) {
            GameModeOverlay(
                state = gameModeState,
                onPlaneswalk = viewModel::planeswalk,
                onRollDie = viewModel::rollPlanarDie,
                onRevealScheme = viewModel::revealNextScheme,
                onRevealBounty = viewModel::revealNextBounty,
                onStop = viewModel::stopGameMode,
                onDismiss = { showGameMode = false }
            )
        }
        if (confirmRestart) {
            ConfirmOverlay(
                text = "Are you sure you want to restart the game?",
                confirmLabel = "Restart",
                onConfirm = viewModel::newGame,
                onDismiss = { confirmRestart = false }
            )
        }
    }
}

private val TableGap = 10.dp

private fun LifeCounterViewModel.actionsFor(id: Int, openKeypad: () -> Unit) = PlayerTileActions(
    adjustLife = { adjust(id, it) },
    openKeypad = openKeypad,
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

// ---- Centre menu button ----

/**
 * A round button with a pastel ring and three bars. Opening it turns the ring 60°, fills it in, and
 * folds the bars into a dark X.
 */
@Composable
private fun MenuButton(open: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(if (open) 1f else 0f, tween(TableMotion.FAST, easing = FastOutSlowInEasing), label = "menuMorph")
    val barColor by animateColorAsState(if (open) Color.Black else Color.White, tween(TableMotion.FAST), label = "menuBars")
    val density = LocalDensity.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.3f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    ) {
        Box(
            Modifier
                .size(68.dp)
                .graphicsLayer { rotationZ = 60f * progress }
                .clip(CircleShape)
                .background(Brush.sweepGradient(TableColors.MenuGradient))
        )
        // The dark centre shrinks away as the menu opens, leaving the ring's gradient as a solid fill.
        Box(
            Modifier
                .size(56.dp)
                .graphicsLayer { scaleX = 1f - progress; scaleY = 1f - progress }
                .clip(CircleShape)
                .background(TableColors.Surface)
        )
        val gapPx = with(density) { 8.dp.toPx() }
        listOf(-1, 0, 1).forEach { position ->
            Box(
                Modifier
                    .size(width = 26.dp, height = 4.5.dp)
                    .graphicsLayer {
                        when (position) {
                            0 -> { alpha = 1f - progress; rotationZ = -90f * progress }
                            else -> {
                                translationY = position * gapPx * (1f - progress)
                                rotationZ = position * 45f * progress
                            }
                        }
                    }
                    .clip(RoundedCornerShape(50))
                    .background(barColor)
            )
        }
    }
}

private class RadialItem(val label: String, val color: Color, val angle: Float, val onClick: () -> Unit)

/**
 * Game-level actions scattered around the centre button, each pill tilted and swinging into place:
 * from a squashed −40° to a settled −20°.
 */
@Composable
private fun RadialMenu(items: List<RadialItem>, onPicked: () -> Unit, modifier: Modifier = Modifier) {
    val radius = 112.dp
    Box(modifier) {
        items.forEachIndexed { index, item ->
            val swing = remember { Animatable(0f) }
            LaunchedEffect(Unit) { swing.animateTo(1f, tween(TableMotion.FAST, delayMillis = 25 * index, easing = TableMotion.Pop)) }
            val radians = Math.toRadians(item.angle.toDouble())
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = radius * cos(radians).toFloat(), y = radius * sin(radians).toFloat())
                    .graphicsLayer {
                        val p = swing.value
                        rotationZ = -40f + 20f * p
                        scaleX = 0.6f + 0.4f * p
                        scaleY = 0.7f + 0.3f * p
                        alpha = p.coerceIn(0f, 1f)
                    }
                    .clip(RoundedCornerShape(50))
                    .background(item.color)
                    .clickable { onPicked(); item.onClick() }
                    .padding(horizontal = 18.dp, vertical = 5.dp)
            ) {
                TableLabel(item.label, 30.sp, color = Color.Black, maxLines = 1)
            }
        }
    }
}

private class ToolItem(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/** CSS ease-out, which Lotus's chip entrance runs its keyframes on. */
private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

/** (progress, rise as a fraction of the chip's height, rotation°) — each chip springs up and wobbles to rest. */
private val ChipKeys = listOf(
    Triple(0f, 1f, 60f),
    Triple(0.5f, -0.05f, -10f),
    Triple(0.7f, 0.025f, 5f),
    Triple(0.85f, -0.0125f, -2.5f),
    Triple(1f, 0f, 0f)
)

private fun chipPose(p: Float): Pair<Float, Float> {
    val next = ChipKeys.indexOfFirst { it.first >= p }.coerceAtLeast(1)
    val (p0, y0, r0) = ChipKeys[next - 1]
    val (p1, y1, r1) = ChipKeys[next]
    val t = if (p1 == p0) 1f else ((p - p0) / (p1 - p0)).coerceIn(0f, 1f)
    return (y0 + (y1 - y0) * t) to (r0 + (r1 - r0) * t)
}

/** Table tools and game modes, along the bottom while the menu is open. */
@Composable
private fun ToolBar(items: List<ToolItem>, onPicked: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(TableColors.BarBackground)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        items.forEach { item ->
            val entrance = remember { Animatable(0f) }
            LaunchedEffect(Unit) { entrance.animateTo(1f, tween(TableMotion.MENU_CHIPS, easing = EaseOut)) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier
                    .graphicsLayer {
                        val (rise, rotation) = chipPose(entrance.value)
                        translationY = rise * size.height
                        rotationZ = rotation
                    }
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black)
                    .clickable { onPicked(); item.onClick() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(item.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                TableLabel(item.label, 24.sp, maxLines = 1)
            }
        }
    }
}

// ---- Table strips ----

@Composable
private fun TurnStrip(
    showTurns: Boolean,
    showTimer: Boolean,
    turnNumber: Int,
    currentPlayer: PlayerLife?,
    turnSeconds: Int,
    matchSeconds: Int,
    running: Boolean,
    onToggleTimer: () -> Unit,
    onNextTurn: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        if (showTurns) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(currentPlayer?.let { paletteColor(it.colorIndex) } ?: TableColors.Line))
            TableLabel("Turn $turnNumber · ${currentPlayer?.displayName ?: ""}", 26.sp, maxLines = 1, modifier = Modifier.weight(1f))
        }
        if (showTimer) {
            TableLabel("${formatElapsed(turnSeconds)} / ${formatElapsed(matchSeconds)}", 26.sp, color = TableColors.TextMuted, modifier = if (showTurns) Modifier else Modifier.weight(1f))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(34.dp).clip(CircleShape).background(TableColors.SurfaceRaised).clickable(onClick = onToggleTimer)
            ) {
                Icon(if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (running) "Pause timer" else "Resume timer", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        if (showTurns) {
            PillButton("Next turn", TableColors.Yellow, textColor = Color.Black, onClick = onNextTurn, textSize = 20.sp)
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
        GameModeKind.PLANECHASE -> "Planechase"
        GameModeKind.ARCHENEMY -> "Archenemy"
        GameModeKind.BOUNTY -> "Bounty"
        GameModeKind.NONE -> ""
    }
    val bountyFront = if (state.mode == GameModeKind.BOUNTY) card?.cardFaces?.firstOrNull() else null
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        when {
            state.loading -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = TableColors.Yellow)
            card != null -> AsyncImage(
                model = (bountyFront?.imageUris?.normal ?: card.displayImageUrl)?.toArtCropUrl(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 48.dp, height = 32.dp).clip(RoundedCornerShape(8.dp))
            )
        }
        TableLabel(label, 26.sp, color = TableColors.Yellow)
        TableLabel(
            when {
                state.loading -> "Shuffling"
                card != null -> bountyFront?.name ?: card.name
                else -> "Tap to draw"
            },
            26.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DayNightPill(state: DayNight, onToggle: () -> Unit, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val isDay = state == DayNight.DAY
    val background by animateColorAsState(if (isDay) Color(0xFFFFD45C) else Color(0xFF26306E), tween(500), label = "dayNightBg")
    val ink = if (isDay) Color.Black else Color.White
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .popIn()
            .clip(RoundedCornerShape(50))
            .background(background)
            .border(BorderStroke(2.dp, Color.White), RoundedCornerShape(50))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.clickable(onClick = onToggle).padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Icon(if (isDay) Icons.Filled.LightMode else Icons.Filled.DarkMode, contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
            TableLabel(if (isDay) "Day" else "Night", 26.sp, color = ink)
        }
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp).clickable(onClick = onStop)) {
            Icon(Icons.Filled.Close, contentDescription = "Stop tracking day and night", tint = ink, modifier = Modifier.size(16.dp))
        }
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
    onDrop: (Int) -> Unit,
    onRemove: () -> Unit,
    onDone: () -> Unit
) {
    val density = LocalDensity.current
    val tokenSize = 72.dp
    val halfPx = with(density) { (tokenSize / 2).toPx() }
    var origin by remember { mutableStateOf<Offset?>(null) }
    // Token centre, in this overlay's own coordinates.
    var position by remember(kind) { mutableStateOf<Offset?>(null) }
    var dragging by remember { mutableStateOf(false) }
    val attention = rememberInfiniteTransition(label = "tokenAttention")
    val bob by attention.animateFloat(1f, 1.15f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "tokenBob")
    BackHandler(onBack = onDone)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .onGloballyPositioned { coords ->
                val o = coords.positionInRoot()
                origin = o
                if (position == null) position = start - o
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(30.dp),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp)
        ) {
            TableLabel("Remove", 30.sp, color = TableColors.Accent, modifier = Modifier.clickable(onClick = onRemove))
            TableLabel("Done", 30.sp, modifier = Modifier.clickable(onClick = onDone))
        }

        val current = position
        val o = origin
        if (current != null && o != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .offset { IntOffset((current.x - halfPx).roundToInt(), (current.y - halfPx).roundToInt()) }
                    .pointerInput(kind) {
                        detectDragGestures(
                            onDragStart = { dragging = true },
                            onDragEnd = {
                                dragging = false
                                val point = (position ?: return@detectDragGestures) + o
                                seatBounds.entries.firstOrNull { it.value.contains(point) }?.key?.let(onDrop)
                            },
                            onDragCancel = { dragging = false }
                        ) { change, amount ->
                            change.consume()
                            position = (position ?: Offset.Zero) + amount
                        }
                    }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .popIn()
                        .graphicsLayer { val s = if (dragging) 1.1f else bob; scaleX = s; scaleY = s }
                        .size(tokenSize)
                        .clip(CircleShape)
                        .background(if (kind == TokenKind.MONARCH) TableColors.Gold else Color.Black)
                        .border(BorderStroke(3.dp, Color.White), CircleShape)
                ) {
                    Icon(
                        if (kind == TokenKind.MONARCH) Icons.Filled.WorkspacePremium else Icons.Filled.Castle,
                        contentDescription = if (kind == TokenKind.MONARCH) "Monarch" else "Initiative",
                        tint = if (kind == TokenKind.MONARCH) Color.Black else Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
                AnimatedVisibility(visible = !dragging, enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
                    Box(
                        Modifier
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                    ) {
                        TableLabel("Drag me", 22.sp)
                    }
                }
            }
        }
    }
}

// ---- First-run tips ----

private enum class TipDemo { SIDEWAYS, VERTICAL, TAP, MENU }

private class Tip(val title: String, val body: String, val highlights: List<String>, val demo: TipDemo, val button: String, val buttonColor: Color)

/**
 * A short walkthrough of the gestures over a sample tile that acts each one out. Shown once;
 * replayable from the menu.
 */
@Composable
private fun TipsOverlay(settings: LifeCounterSettings, onFinished: () -> Unit) {
    val tapHow = if (settings.verticalTapAreas) "Tap the top or bottom half" else "Tap the left or right half"
    val tips = listOf(
        Tip("Commander damage", "Swipe left or right on your card for damage from each commander", listOf("LEFT", "RIGHT"), TipDemo.SIDEWAYS, "Got it", TableColors.SurfaceRaised),
        Tip("Player options", "Swipe up for counters and kill, down for colors and background", listOf("UP", "DOWN"), TipDemo.VERTICAL, "Alright", TableColors.Accent),
        Tip("Change life", "$tapHow · hold for ${settings.longPressAmount}", listOf("HOLD"), TipDemo.TAP, "Nice", TableColors.Accent),
        Tip("Everything else", "Tap the button in the middle for dice, seating, settings and more", listOf("MIDDLE"), TipDemo.MENU, "Let's play", TableColors.Accent)
    )
    var index by remember { mutableIntStateOf(0) }
    val tip = tips[index]
    BackHandler(onBack = onFinished)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 28.dp)) {
            // Re-keyed per tip so every step animates in fresh.
            androidx.compose.runtime.key(index) {
                TipSample(tip.demo)
                TableLabel(tip.title, 52.sp, align = TextAlign.Center, modifier = Modifier.padding(top = 26.dp).popIn(easing = TableMotion.Pop))
                Text(
                    buildAnnotatedString {
                        tip.body.uppercase().split(" ").forEachIndexed { i, word ->
                            if (i > 0) append(" ")
                            if (tip.highlights.any { word.trim(',', '·').startsWith(it) }) {
                                withStyle(SpanStyle(color = TableColors.Yellow)) { append(word) }
                            } else append(word)
                        }
                    },
                    style = tableText(30.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
                PillButton(
                    tip.button,
                    tip.buttonColor,
                    onClick = { if (index == tips.lastIndex) onFinished() else index++ },
                    modifier = Modifier.padding(top = 30.dp).width(200.dp).popIn(delayMillis = 150),
                    textSize = 28.sp
                )
            }
            TableLabel("Skip", 22.sp, color = TableColors.TextMuted, modifier = Modifier.padding(top = 18.dp).clickable(onClick = onFinished))
        }
    }
}

/** A sample tile acting out the tip's gesture on a loop. */
@Composable
private fun TipSample(demo: TipDemo) {
    val loop = rememberInfiniteTransition(label = "tipDemo")
    val motion by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "tipMotion")
    Box(
        Modifier
            .size(width = 280.dp, height = 168.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(TableColors.Well)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    when (demo) {
                        TipDemo.SIDEWAYS -> translationX = -size.width * 0.45f * motion
                        TipDemo.VERTICAL -> translationY = -size.height * 0.55f * motion
                        TipDemo.TAP, TipDemo.MENU -> Unit
                    }
                }
                .clip(RoundedCornerShape(24.dp))
                .background(TableColors.Blue)
        ) {
            val life = if (demo == TipDemo.TAP) 40 + (motion * 5).roundToInt() else 40
            TableLabel("$life", 110.sp, color = Color.Black)
            Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                TableLabel("−", 44.sp, color = Color.Black.copy(alpha = 0.3f), modifier = Modifier.weight(1f))
                TableLabel(if (demo == TipDemo.TAP && motion > 0.05f) "+${(motion * 5).roundToInt()}" else "+", 44.sp, color = Color.Black.copy(alpha = if (demo == TipDemo.TAP) 1f else 0.3f))
            }
        }
        if (demo == TipDemo.MENU) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .graphicsLayer { val s = 1f + 0.3f * motion; scaleX = s; scaleY = s }
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Brush.sweepGradient(TableColors.MenuGradient))
            )
        }
    }
}
