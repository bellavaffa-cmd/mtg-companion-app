package com.mtgcompanion.app.ui.lifecounter

import com.mtgcompanion.app.data.social.Giphy
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Castle
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.PlayerProfile
import com.mtgcompanion.app.ui.common.ManaSymbol
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.material3.Text

enum class TokenKind { MONARCH, INITIATIVE }

/** Which card a tile has slid away to reveal. The tile keeps a thin strip of itself visible. */
private enum class Reveal { NONE, OPTIONS, APPEARANCE, DAMAGE_START, DAMAGE_END }

private val TileCorner = 24.dp
private val TileShape = RoundedCornerShape(TileCorner)
/** How much of a slid-away tile stays showing, to tap (or drag) it back. */
private val StripSize = 34.dp
private val CardGap = 12.dp

/** Everything a tile can do to its own player, already bound to that player's id. */
class PlayerTileActions(
    val adjustLife: (Int) -> Unit,
    val openKeypad: () -> Unit,
    val adjustCommanderDamage: (CommanderSource, Int) -> Unit,
    val adjustCounter: (PlayerCounter, Int) -> Unit,
    val adjustMana: (String, Int) -> Unit,
    val adjustTax: (slot: Int, delta: Int) -> Unit,
    val setHasPartner: (Boolean) -> Unit,
    val kill: () -> Unit,
    val revive: () -> Unit,
    val setColor: (Int) -> Unit,
    val setName: (String) -> Unit,
    val setVictoryMessage: (String) -> Unit,
    val setDefeatMessage: (String) -> Unit,
    val setBackgroundImage: (String?) -> Unit,
    val saveProfile: () -> Unit,
    val loadProfile: (PlayerProfile) -> Unit,
    /** Shows this seat's QR code, for a player to join with their profile; null when accounts aren't set up. */
    val linkSeat: (() -> Unit)? = null,
    /** Frees the seat from the profile sitting there. */
    val unlinkSeat: () -> Unit = {}
)

/**
 * One player's seat, laid out from that player's own point of view — the caller turns it to face
 * them. The colored tile follows a drag and, past a threshold, slides away to uncover a card in
 * the slot beneath it: swipe up for options, down for appearance, sideways for commander damage
 * received. Tap or drag the strip that's left showing to slide it back.
 */
@Composable
fun PlayerTile(
    player: PlayerLife,
    opponents: List<PlayerLife>,
    settings: LifeCounterSettings,
    isActiveTurn: Boolean,
    isMonarch: Boolean,
    hasInitiative: Boolean,
    defeatMessage: String?,
    victoryMessage: String?,
    profiles: List<PlayerProfile>,
    actions: PlayerTileActions,
    onTokenTap: (TokenKind) -> Unit,
    modifier: Modifier = Modifier
) {
    val seat = seatColor(player.colorIndex)
    val hasImage = player.backgroundImageUri != null
    val ink = if (hasImage || seat.whiteText) Color.White else Color.Black
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val stripPx = with(density) { StripSize.toPx() }
    val ringPx = with(density) { 5.dp.toPx() }
    val cornerPx = with(density) { TileCorner.toPx() }

    val tileOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var reveal by remember(player.id) { mutableStateOf(Reveal.NONE) }
    // What's drawn in the slot underneath — held until a closing slide finishes, so the card doesn't
    // vanish before the tile has covered it again.
    var shownReveal by remember(player.id) { mutableStateOf(Reveal.NONE) }
    var slotSize by remember { mutableStateOf(IntSize.Zero) }

    fun destination(target: Reveal): Offset {
        val w = slotSize.width.toFloat()
        val h = slotSize.height.toFloat()
        return when (target) {
            Reveal.NONE -> Offset.Zero
            Reveal.OPTIONS -> Offset(0f, -(h - stripPx))
            Reveal.APPEARANCE -> Offset(0f, h - stripPx)
            Reveal.DAMAGE_START -> Offset(-(w - stripPx), 0f)
            Reveal.DAMAGE_END -> Offset(w - stripPx, 0f)
        }
    }

    fun settle(target: Reveal) {
        reveal = target
        if (target != Reveal.NONE) shownReveal = target
        scope.launch {
            tileOffset.animateTo(destination(target), tween(TableMotion.FAST, easing = FastOutSlowInEasing))
            if (target == Reveal.NONE) shownReveal = Reveal.NONE
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .clip(TileShape)
            .background(TableColors.Well)
            .onSizeChanged { slotSize = it }
    ) {
        when (shownReveal) {
            Reveal.OPTIONS -> OptionsCard(
                player = player,
                autoKill = settings.autoKill,
                actions = actions,
                modifier = Modifier.padding(top = StripSize + CardGap, start = CardGap, end = CardGap, bottom = CardGap)
            )
            Reveal.APPEARANCE -> AppearanceCard(
                player = player,
                profiles = profiles,
                actions = actions,
                modifier = Modifier.padding(bottom = StripSize + CardGap, start = CardGap, end = CardGap, top = CardGap)
            )
            Reveal.DAMAGE_START, Reveal.DAMAGE_END -> CommanderDamageCard(
                player = player,
                opponents = opponents,
                onAdjust = actions.adjustCommanderDamage,
                modifier = Modifier.padding(
                    start = if (shownReveal == Reveal.DAMAGE_START) CardGap else StripSize + CardGap,
                    end = if (shownReveal == Reveal.DAMAGE_START) StripSize + CardGap else CardGap,
                    top = CardGap,
                    bottom = CardGap
                )
            )
            Reveal.NONE -> Unit
        }

        Box(
            Modifier
                .fillMaxSize()
                .offset { tileOffset.value.let { IntOffset(it.x.roundToInt(), it.y.roundToInt()) } }
                // A dark ring hugging the tile, only ever visible along its edge as it slides.
                .drawBehind {
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.3f),
                        topLeft = Offset(-ringPx, -ringPx),
                        size = Size(size.width + ringPx * 2, size.height + ringPx * 2),
                        cornerRadius = CornerRadius(cornerPx + ringPx)
                    )
                }
                .clip(TileShape)
                .background(if (hasImage) Color.Black else seat.color)
                .pointerInput(player.id) {
                    var horizontal: Boolean? = null
                    detectDragGestures(
                        onDragStart = {
                            horizontal = when (reveal) {
                                Reveal.NONE -> null
                                Reveal.OPTIONS, Reveal.APPEARANCE -> false
                                Reveal.DAMAGE_START, Reveal.DAMAGE_END -> true
                            }
                        },
                        onDragEnd = {
                            val o = tileOffset.value
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val target = when (horizontal) {
                                true -> {
                                    val fraction = abs(o.x) / w
                                    val side = if (o.x < 0) Reveal.DAMAGE_START else Reveal.DAMAGE_END
                                    if (reveal == Reveal.NONE) (if (fraction > OPEN_THRESHOLD) side else Reveal.NONE)
                                    else (if (fraction > 1f - OPEN_THRESHOLD) reveal else Reveal.NONE)
                                }
                                false -> {
                                    val fraction = abs(o.y) / h
                                    val side = if (o.y < 0) Reveal.OPTIONS else Reveal.APPEARANCE
                                    if (reveal == Reveal.NONE) (if (fraction > OPEN_THRESHOLD) side else Reveal.NONE)
                                    else (if (fraction > 1f - OPEN_THRESHOLD) reveal else Reveal.NONE)
                                }
                                null -> reveal
                            }
                            settle(target)
                        },
                        onDragCancel = { settle(reveal) }
                    ) { change, amount ->
                        change.consume()
                        val isHorizontal = horizontal ?: (abs(amount.x) > abs(amount.y)).also { horizontal = it }
                        val o = tileOffset.value
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        // An open tile only moves back the way it came, never across to the other side.
                        val next = if (isHorizontal) {
                            val x = o.x + amount.x
                            Offset(
                                when (reveal) {
                                    Reveal.DAMAGE_START -> x.coerceIn(-w, 0f)
                                    Reveal.DAMAGE_END -> x.coerceIn(0f, w)
                                    else -> x.coerceIn(-w, w)
                                },
                                0f
                            )
                        } else {
                            val y = o.y + amount.y
                            Offset(
                                0f,
                                when (reveal) {
                                    Reveal.OPTIONS -> y.coerceIn(-h, 0f)
                                    Reveal.APPEARANCE -> y.coerceIn(0f, h)
                                    else -> y.coerceIn(-h, h)
                                }
                            )
                        }
                        if (reveal == Reveal.NONE) {
                            shownReveal = when {
                                next.x < 0f -> Reveal.DAMAGE_START
                                next.x > 0f -> Reveal.DAMAGE_END
                                next.y < 0f -> Reveal.OPTIONS
                                next.y > 0f -> Reveal.APPEARANCE
                                else -> Reveal.NONE
                            }
                        }
                        scope.launch { tileOffset.snapTo(next) }
                    }
                }
        ) {
            if (hasImage) {
                AsyncImage(
                    model = player.backgroundImageUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.2f), Color.Black.copy(alpha = 0.4f)))))
            }

            val alive = defeatMessage == null
            CriticalLifeGlow(visible = settings.lowLifeWarning && alive && player.life in 1..9)

            LifeFace(
                player = player,
                ink = ink,
                settings = settings,
                // Still tappable when out, so a player knocked to 0 by a mis-tap can tap straight back up.
                interactive = reveal == Reveal.NONE,
                showNumber = (reveal == Reveal.NONE || tileOffset.isRunning) && alive,
                isMonarch = isMonarch,
                hasInitiative = hasInitiative,
                actions = actions
            )

            SwipeHandles(ink = ink, reveal = reveal)

            AnimatedVisibility(visible = reveal == Reveal.NONE, enter = fadeIn(tween(TableMotion.FAST)), exit = fadeOut(tween(150))) {
                Box(Modifier.fillMaxSize()) {
                    TileTopRow(
                        player = player,
                        opponents = opponents,
                        settings = settings,
                        ink = ink,
                        isMonarch = isMonarch,
                        hasInitiative = hasInitiative,
                        onTokenTap = onTokenTap,
                        onOpenDamage = { settle(Reveal.DAMAGE_END) },
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = CardGap + 10.dp)
                    )
                    TileCounters(
                        player = player,
                        settings = settings,
                        ink = ink,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = CardGap + 10.dp, start = 16.dp, end = 16.dp)
                    )
                }
            }

            if (isActiveTurn) {
                Box(Modifier.fillMaxSize().border(BorderStroke(5.dp, TableColors.Gold), TileShape))
            }

            AnimatedVisibility(visible = !alive, enter = fadeIn(tween(TableMotion.FAST)), exit = fadeOut(tween(TableMotion.FAST))) {
                OutcomeOverlay(defeatMessage ?: "", Color.Black.copy(alpha = 0.6f))
            }
            AnimatedVisibility(visible = alive && victoryMessage != null, enter = fadeIn(tween(TableMotion.FAST)), exit = fadeOut(tween(TableMotion.FAST))) {
                OutcomeOverlay(victoryMessage ?: "", Color.Black.copy(alpha = 0.3f))
            }

            if (reveal != Reveal.NONE) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { settle(Reveal.NONE) }
                )
            }
        }
    }
}

private const val OPEN_THRESHOLD = 0.22f

// ---- Life face ----

/**
 * The life total over two tap areas. Each area shows a faint + or − at its outer edge, which turns
 * into the running change (+3, −7…) while taps are coming in and fades back shortly after. A big
 * single change shakes the number; gaining the Monarch or Initiative makes it pulse.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LifeFace(
    player: PlayerLife,
    ink: Color,
    settings: LifeCounterSettings,
    interactive: Boolean,
    showNumber: Boolean,
    isMonarch: Boolean,
    hasInitiative: Boolean,
    actions: PlayerTileActions
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var pendingDelta by remember { mutableIntStateOf(0) }
    var lastChangeAt by remember { mutableLongStateOf(0L) }
    val shake = remember { Animatable(0f) }
    val pulse = remember { Animatable(1f) }

    LaunchedEffect(lastChangeAt) {
        if (lastChangeAt != 0L) {
            delay(FEEDBACK_HOLD_MILLIS)
            pendingDelta = 0
        }
    }
    LaunchedEffect(isMonarch) {
        if (isMonarch) pulse.animateTo(1f, keyframes { durationMillis = 700; 1.2f at 210; 1f at 700 })
    }
    LaunchedEffect(hasInitiative) {
        if (hasInitiative) {
            pulse.animateTo(1f, keyframes { durationMillis = 700; 0.7f at 140; 1.2f at 280; 0.9f at 420; 1.05f at 560; 1f at 700 })
        }
    }

    fun change(delta: Int) {
        actions.adjustLife(delta)
        pendingDelta += delta
        lastChangeAt = System.currentTimeMillis()
        if (abs(delta) >= settings.longPressAmount) {
            scope.launch {
                shake.snapTo(0f)
                shake.animateTo(1f, tween(500, easing = LinearEasing))
                shake.snapTo(0f)
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = maxHeight
        val w = maxWidth
        val feedbackSize = with(LocalDensity.current) { (min(h.value * 0.24f, w.value * 0.2f)).dp.toSp() }

        @Composable
        fun TapArea(sign: Int, areaModifier: Modifier, alignment: Alignment, edgePadding: Modifier) {
            val active = pendingDelta != 0 && (pendingDelta > 0) == (sign > 0)
            val label = when {
                active && pendingDelta > 0 -> "+$pendingDelta"
                active -> "−${abs(pendingDelta)}"
                sign > 0 -> "+"
                else -> "−"
            }
            val alpha by animateFloatAsState(
                when {
                    active -> 1f
                    settings.minimalist -> 0f
                    else -> 0.3f
                },
                tween(TableMotion.FAST),
                label = "feedbackAlpha"
            )
            Box(
                contentAlignment = alignment,
                modifier = areaModifier.combinedClickable(
                    enabled = interactive,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { change(sign * settings.tapAmount) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        change(sign * settings.longPressAmount)
                    }
                )
            ) {
                Text(label, style = tableText(feedbackSize, ink), modifier = edgePadding.graphicsLayer { this.alpha = alpha })
            }
        }

        if (settings.verticalTapAreas) {
            Column(Modifier.fillMaxSize()) {
                TapArea(+1, Modifier.weight(1f).fillMaxWidth(), Alignment.TopCenter, Modifier.padding(top = h * 0.06f))
                TapArea(-1, Modifier.weight(1f).fillMaxWidth(), Alignment.BottomCenter, Modifier.padding(bottom = h * 0.06f))
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                TapArea(-1, Modifier.weight(1f).fillMaxHeight(), Alignment.CenterStart, Modifier.padding(start = w * 0.06f))
                TapArea(+1, Modifier.weight(1f).fillMaxHeight(), Alignment.CenterEnd, Modifier.padding(end = w * 0.06f))
            }
        }

        val numberAlpha by animateFloatAsState(if (showNumber) 1f else 0f, tween(TableMotion.FAST), label = "lifeAlpha")
        val text = player.life.toString()
        // Bebas Neue digits are about 0.4em wide and its caps about 0.7em tall.
        val bySize = min(h.value * 0.62f, (w.value * 0.62f) / (text.length * 0.42f))
        val lifeSize = with(LocalDensity.current) { bySize.dp.toSp() }
        Text(
            lifeText(text, settings.underlineSixNine),
            style = tableText(lifeSize, ink),
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    alpha = numberAlpha
                    val p = shake.value
                    if (p > 0f) {
                        val wave = sin(p * PI.toFloat() * 10f)
                        translationX = wave * 2.dp.toPx()
                        translationY = sin(p * PI.toFloat() * 7f + 1f) * 2.dp.toPx()
                        rotationZ = wave
                    }
                    scaleX = pulse.value
                    scaleY = pulse.value
                }
                .let {
                    if (settings.tapLifeToSet && interactive) {
                        it.clip(RoundedCornerShape(20.dp)).clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = actions.openKeypad
                        )
                    } else it
                }
        )

        if (settings.playerNamesOnTile && player.name != null) {
            val nameSize = with(LocalDensity.current) { (h.value / 8.5f).coerceIn(13f, 30f).dp.toSp() }
            Text(
                player.name.uppercase(),
                style = tableText(nameSize, ink),
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.78f)
                    .graphicsLayer { translationY = (h / 4.2f).toPx(); alpha = numberAlpha }
            )
        }
    }
}

private const val FEEDBACK_HOLD_MILLIS = 1_600L

/** 6 and 9 get an underline so a number read upside down from across the table can't be misread. */
private fun lifeText(text: String, underlineSixNine: Boolean): AnnotatedString = buildAnnotatedString {
    text.forEach { ch ->
        if (underlineSixNine && (ch == '6' || ch == '9')) {
            withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(ch) }
        } else {
            append(ch)
        }
    }
}

/** A red glow creeping in from the tile's edges, slowly blinking, while life is below 10. */
@Composable
private fun CriticalLifeGlow(visible: Boolean) {
    if (!visible) return
    val transition = rememberInfiniteTransition(label = "critical")
    val alpha by transition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(1250, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "criticalAlpha"
    )
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        0.45f to Color.Transparent,
                        1f to TableColors.CriticalRed,
                        center = center,
                        radius = size.maxDimension * 0.62f
                    )
                )
            }
    )
}

/**
 * Small handle bars at the top and bottom edge hinting that the tile slides. On an open tile the
 * handle left on its visible strip pulses, pointing the way back.
 */
@Composable
private fun SwipeHandles(ink: Color, reveal: Reveal) {
    val base = if (ink == Color.White) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.3f)
    val transition = rememberInfiniteTransition(label = "handles")
    val pulse by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2500), RepeatMode.Reverse),
        label = "handlePulse"
    )
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = CardGap)
                .fillMaxWidth(0.3f)
                .height(CardGap / 2)
                .graphicsLayer { alpha = if (reveal == Reveal.APPEARANCE) pulse else 1f }
                .clip(RoundedCornerShape(50))
                .background(base)
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = CardGap)
                .fillMaxWidth(0.3f)
                .height(CardGap / 2)
                .graphicsLayer { alpha = if (reveal == Reveal.OPTIONS) pulse else 1f }
                .clip(RoundedCornerShape(50))
                .background(base)
        )
    }
}

/** Monarch/Initiative tokens and commander damage received, just under the top handle. */
@Composable
private fun TileTopRow(
    player: PlayerLife,
    opponents: List<PlayerLife>,
    settings: LifeCounterSettings,
    ink: Color,
    isMonarch: Boolean,
    hasInitiative: Boolean,
    onTokenTap: (TokenKind) -> Unit,
    onOpenDamage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val taken = player.commanderDamage.filterValues { it > 0 }
    val showDamage = settings.showCommanderDamageOnTile && taken.isNotEmpty()
    if (!isMonarch && !hasInitiative && !showDamage) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        if (isMonarch) TokenBadge(TokenKind.MONARCH, onClick = { onTokenTap(TokenKind.MONARCH) })
        if (hasInitiative) TokenBadge(TokenKind.INITIATIVE, onClick = { onTokenTap(TokenKind.INITIATIVE) })
        if (showDamage) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(ink.copy(alpha = 0.14f))
                    .clickable(onClick = onOpenDamage)
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            ) {
                Icon(Icons.Filled.Whatshot, contentDescription = "Commander damage received", tint = ink, modifier = Modifier.size(13.dp))
                taken.entries.sortedBy { it.key.opponentId * 2 + it.key.slot }.forEach { (source, damage) ->
                    val opponent = opponents.firstOrNull { it.id == source.opponentId }
                    Box(Modifier.size(9.dp).clip(CircleShape).background(opponent?.let { paletteColor(it.colorIndex) } ?: ink))
                    Text("$damage", style = tableText(17.sp, ink))
                }
            }
        }
    }
}

@Composable
private fun TokenBadge(kind: TokenKind, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .popIn()
            .size(32.dp)
            .clip(CircleShape)
            .background(if (kind == TokenKind.MONARCH) TableColors.Gold else Color.Black)
            .border(BorderStroke(2.dp, Color.White), CircleShape)
            .clickable(onClick = onClick)
    ) {
        Icon(
            if (kind == TokenKind.MONARCH) Icons.Filled.WorkspacePremium else Icons.Filled.Castle,
            contentDescription = if (kind == TokenKind.MONARCH) "Monarch — tap to move" else "Initiative — tap to move",
            tint = if (kind == TokenKind.MONARCH) Color.Black else Color.White,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** The counters worth showing on the tile itself, per the "counters on player tile" settings. */
@Composable
private fun TileCounters(player: PlayerLife, settings: LifeCounterSettings, ink: Color, modifier: Modifier = Modifier) {
    val shown = PlayerCounter.entries.filter { kind ->
        kind in settings.pinnedCounters ||
            (settings.countersOnTile && (player.counter(kind) > 0 || (settings.keepZeroCounters && kind in player.counters)))
    }
    val tax = player.commanderTax.sum()
    val showTax = settings.countersOnTile && tax > 0
    if (shown.isEmpty() && !showTax) return
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = modifier.horizontalScroll(rememberScrollState())) {
        shown.forEach { kind -> CounterChip(kind.label, player.counter(kind), ink) }
        if (showTax) CounterChip("Tax", tax, ink)
    }
}

@Composable
private fun CounterChip(label: String, value: Int, ink: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(ink.copy(alpha = 0.14f))
            .padding(horizontal = 9.dp, vertical = 2.dp)
    ) {
        TableLabel(label, 15.sp, color = ink.copy(alpha = 0.75f), maxLines = 1)
        TableLabel("$value", 19.sp, color = ink, maxLines = 1)
    }
}

@Composable
private fun OutcomeOverlay(message: String, scrim: Color) {
    BoxWithConstraints(Modifier.fillMaxSize().background(scrim), contentAlignment = Alignment.Center) {
        val size = with(LocalDensity.current) { (min(maxHeight.value * 0.2f, maxWidth.value * 0.14f)).dp.toSp() }
        TableLabel(
            message,
            size,
            color = Color.White,
            align = TextAlign.Center,
            modifier = Modifier.padding(CardGap).popIn(easing = TableMotion.Pop)
        )
    }
}

// ---- Cards under the tile ----

/** Swipe up: game options for this player. */
@Composable
private fun OptionsCard(player: PlayerLife, autoKill: Boolean, actions: PlayerTileActions, modifier: Modifier = Modifier) {
    var name by remember(player.id) { mutableStateOf(player.name ?: "") }
    val defeated = player.isDefeated(autoKill)
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OptionTile(
                label = if (defeated) "Revive" else "Kill",
                color = if (defeated) TableColors.MenuSeating else TableColors.Accent,
                textColor = if (defeated) Color.Black else Color.White,
                onClick = if (defeated) actions.revive else actions.kill,
                modifier = Modifier.weight(1f)
            )
            OptionTile(
                label = if (player.hasPartner) "Partners ✓" else "Partners",
                color = if (player.hasPartner) TableColors.Yellow else TableColors.SurfaceRaised,
                textColor = if (player.hasPartner) Color.Black else Color.White,
                onClick = { actions.setHasPartner(!player.hasPartner) },
                modifier = Modifier.weight(1f)
            )
        }

        val linked = player.linked
        if (linked != null) {
            CardSection("Playing")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${linked.displayName} · @${linked.username}", style = tableText(18.sp, Color.White), maxLines = 1, modifier = Modifier.weight(1f))
                OptionTile("Free seat", TableColors.SurfaceRaised, Color.White, onClick = actions.unlinkSeat)
            }
        } else {
            CardSection("Name")
            CardTextField(value = name, placeholder = "Player ${player.id}") { name = it; actions.setName(it) }
            actions.linkSeat?.let { link ->
                OptionTile("Join with a profile (QR code)", TableColors.Yellow, Color.Black, onClick = link, modifier = Modifier.fillMaxWidth())
            }
        }

        CardSection("Counters")
        PlayerCounter.entries.forEach { kind ->
            StepperRow(if (kind.resetsEachTurn) "${kind.label} · this turn" else kind.label, player.counter(kind)) { actions.adjustCounter(kind, it) }
        }
        StepperRow("Commander tax", player.commanderTax.getOrElse(0) { 0 }, step = 2) { actions.adjustTax(0, it) }
        if (player.hasPartner) {
            StepperRow("Partner tax", player.commanderTax.getOrElse(1) { 0 }, step = 2) { actions.adjustTax(1, it) }
        }

        CardSection("Mana pool")
        ManaPoolRow(pool = player.manaPool, onAdjust = actions.adjustMana)
        Spacer(Modifier.height(4.dp))
    }
}

/** Swipe down: how this player's tile looks, and their saved profile. */
@Composable
private fun AppearanceCard(player: PlayerLife, profiles: List<PlayerProfile>, actions: PlayerTileActions, modifier: Modifier = Modifier) {
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) actions.setBackgroundImage(uri.toString())
    }
    var url by remember(player.id) { mutableStateOf("") }
    var victory by remember(player.id) { mutableStateOf(player.victoryMessage ?: "") }
    var defeat by remember(player.id) { mutableStateOf(player.defeatMessage ?: "") }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
    ) {
        CardSection("Color")
        ColorGrid(selected = Math.floorMod(player.colorIndex, PlayerPalette.size), onPick = actions.setColor)

        CardSection("Background")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionTile("Choose photo", TableColors.SurfaceRaised, Color.White, onClick = {
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }, modifier = Modifier.weight(1f))
            if (player.backgroundImageUri != null) {
                OptionTile("Clear", TableColors.SurfaceRaised, Color.White, onClick = { actions.setBackgroundImage(null) }, modifier = Modifier.weight(1f))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { CardTextField(value = url, placeholder = "Or paste an image / GIF URL") { url = it } }
            OptionTile("Use", if (url.isBlank()) TableColors.SurfaceRaised else TableColors.Yellow, if (url.isBlank()) TableColors.TextMuted else Color.Black,
                onClick = { if (url.isNotBlank()) actions.setBackgroundImage(Giphy.directUrl(url)) })
        }

        CardSection("My victory message")
        CardTextField(value = victory, placeholder = "Use the table's messages") { victory = it; actions.setVictoryMessage(it) }
        CardSection("My defeat message")
        CardTextField(value = defeat, placeholder = "Use the table's messages") { defeat = it; actions.setDefeatMessage(it) }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { CardSection("Profiles") }
            OptionTile("Save", if (player.name != null) TableColors.Yellow else TableColors.SurfaceRaised, if (player.name != null) Color.Black else TableColors.TextMuted,
                onClick = { if (player.name != null) actions.saveProfile() })
        }
        if (profiles.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                profiles.forEach { profile ->
                    OptionTile(profile.name, paletteColor(profile.colorIndex), if (seatColor(profile.colorIndex).whiteText) Color.White else Color.Black,
                        onClick = { actions.loadProfile(profile) })
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

/**
 * Swipe sideways: commander damage this player has received, one tile per opposing commander in
 * that opponent's color. Left half removes a point, right half adds one — the same gesture as life.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommanderDamageCard(
    player: PlayerLife,
    opponents: List<PlayerLife>,
    onAdjust: (CommanderSource, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val sources = opponents.flatMap { opponent ->
        (if (opponent.hasPartner) listOf(0, 1) else listOf(0)).map { slot -> opponent to CommanderSource(opponent.id, slot) }
    }
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TableLabel("Commander damage received", 22.sp, color = Color.White, maxLines = 1)
        if (sources.isEmpty()) {
            TableLabel("No opponents at this table", 20.sp, color = TableColors.TextMuted)
            return@Column
        }
        val columns = if (sources.size <= 2) sources.size else 2
        sources.chunked(columns).forEach { rowSources ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f).fillMaxWidth()) {
                rowSources.forEach { (opponent, source) ->
                    val damage = player.commanderDamage[source] ?: 0
                    val seat = seatColor(opponent.colorIndex)
                    val ink = if (seat.whiteText) Color.White else Color.Black
                    BoxWithConstraints(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(16.dp))
                            .background(seat.color)
                    ) {
                        Row(Modifier.fillMaxSize()) {
                            listOf(-1, 1).forEach { delta ->
                                Box(
                                    contentAlignment = if (delta < 0) Alignment.CenterStart else Alignment.CenterEnd,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .combinedClickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { onAdjust(source, delta) },
                                            onLongClick = { onAdjust(source, delta * 5) }
                                        )
                                ) {
                                    TableLabel(if (delta < 0) "−" else "+", 22.sp, color = ink.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 8.dp))
                                }
                            }
                        }
                        val numberSize = with(LocalDensity.current) { min(maxHeight.value * 0.55f, maxWidth.value * 0.42f).dp.toSp() }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.align(Alignment.Center)) {
                            Text(
                                "$damage",
                                style = tableText(numberSize, if (damage >= 21) TableColors.CriticalRed else ink),
                                textDecoration = if (damage >= 21) TextDecoration.LineThrough else null
                            )
                            TableLabel(
                                if (source.slot == 1) "${opponent.displayName} · partner" else opponent.displayName,
                                14.sp,
                                color = ink.copy(alpha = 0.7f),
                                maxLines = 1
                            )
                        }
                    }
                }
                if (rowSources.size < columns) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ---- Card building blocks ----

@Composable
private fun CardSection(title: String) {
    TableLabel(title, 22.sp, color = TableColors.TextMuted, maxLines = 1)
}

@Composable
private fun OptionTile(label: String, color: Color, textColor: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        TableLabel(label, 24.sp, color = textColor, maxLines = 1)
    }
}

@Composable
private fun CardTextField(value: String, placeholder: String, onValueChange: (String) -> Unit) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TableColors.SurfaceRaised)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        if (value.isEmpty()) TableLabel(placeholder, 20.sp, color = TableColors.TextMuted, maxLines = 1)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = tableText(22.sp, Color.White),
            cursorBrush = SolidColor(TableColors.Yellow),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StepperRow(label: String, value: Int, step: Int = 1, onAdjust: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        TableLabel(label, 22.sp, modifier = Modifier.weight(1f), maxLines = 1)
        RoundStep("−") { onAdjust(-step) }
        TableLabel("$value", 28.sp, align = TextAlign.Center, modifier = Modifier.width(44.dp))
        RoundStep("+") { onAdjust(step) }
    }
}

@Composable
private fun RoundStep(symbol: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(36.dp).clip(CircleShape).background(TableColors.SurfaceRaised).clickable(onClick = onClick)
    ) {
        TableLabel(symbol, 26.sp)
    }
}

@Composable
private fun ColorGrid(selected: Int, onPick: (Int) -> Unit) {
    val itemSize: Dp = 34.dp
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PlayerPalette.indices.chunked(7).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { index ->
                    val isSelected = index == selected
                    Box(
                        Modifier
                            .size(itemSize)
                            .clip(CircleShape)
                            .background(PlayerPalette[index].color)
                            .border(BorderStroke(if (isSelected) 3.dp else 0.dp, Color.White), CircleShape)
                            .clickable { onPick(index) }
                    )
                }
            }
        }
    }
}

/** A player's WUBRG+C mana pool: tap a symbol to add one, hold to spend one. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ManaPoolRow(pool: Map<String, Int>, onAdjust: (color: String, delta: Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
            ManaPoolColors.forEach { color ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(TableColors.SurfaceRaised)
                        .combinedClickable(
                            onClick = { onAdjust(color, 1) },
                            onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onAdjust(color, -1) }
                        )
                        .padding(vertical = 7.dp)
                ) {
                    ManaSymbol(color, size = 20.dp)
                    TableLabel("${pool[color] ?: 0}", 24.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
        TableLabel("Tap to add · hold to spend · empties each turn", 15.sp, color = TableColors.TextMuted, modifier = Modifier.padding(top = 4.dp))
    }
}
