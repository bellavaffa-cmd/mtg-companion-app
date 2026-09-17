package com.mtgcompanion.app.ui.lifecounter

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtgcompanion.app.R
import kotlinx.coroutines.launch

/**
 * The life counter's own visual language, modeled on the Lotus life counter: black table, vivid
 * wide-gamut seat colors, tall condensed all-caps type, and springy pop-in motion. Timings and
 * easing curves below match the ones Lotus's web version uses; artwork, logo and digit glyphs are
 * this app's own.
 */

/** Bebas Neue (SIL Open Font License — see assets/licenses/BebasNeue-OFL.txt). Caps only. */
val BebasNeue = FontFamily(Font(R.font.bebas_neue))

private fun p3(r: Float, g: Float, b: Float) = Color(r, g, b, 1f, ColorSpaces.DisplayP3)

object TableColors {
    val Background = Color.Black
    /** What a tile's slot shows once the tile slides away. */
    val Well = Color(0xFF34323E)
    val Surface = Color(0xFF1F1E27)
    val SurfaceRaised = Color(0xFF2F2E39)
    val Line = Color(0xFF76798B)
    val TextMuted = Color(0xFF9898A4)
    val BarBackground = Color(0xFFECEEFB)
    val Accent = p3(1f, 0f, 0.373f)
    val Blue = p3(0.271f, 0.314f, 1f)
    val Yellow = p3(1f, 0.776f, 0f)
    val Gold = p3(1f, 0.839f, 0f)
    val CriticalRed = p3(0.92f, 0f, 0.17f)

    // Radial menu buttons.
    val MenuRestart = p3(1f, 0.9f, 0.55f)
    val MenuHighRoll = p3(0f, 0.8f, 1f)
    val MenuSettings = p3(0.62f, 0.55f, 1f)
    val MenuSeating = p3(0.07f, 0.9f, 0.49f)
    val MenuTips = p3(1f, 0.25f, 0.38f)
    val MenuExit = p3(1f, 0.957f, 0.8f)

    // Menu button gradient.
    val MenuGradient = listOf(p3(0.961f, 0.886f, 1f), p3(0.925f, 0.765f, 1f), p3(0.741f, 0.765f, 1f), p3(0.682f, 0.925f, 1f), p3(0.961f, 0.886f, 1f))
}

/** A seat color and whether text on it needs to be white rather than black to stay readable. */
data class SeatColor(val color: Color, val whiteText: Boolean = false)

/**
 * Order matters twice over: saved profiles store an index into this list, and the first
 * [LifeCounterViewModel.PLAYER_COLOR_COUNT] entries are the default seat colors, in seat order.
 * With the default four-player table (seats 2/3 on top, 1/4 below) that reads yellow and red
 * across the top, lilac and blue along the bottom.
 */
val PlayerPalette = listOf(
    SeatColor(p3(0.922f, 0.612f, 1f)),                 // lilac
    SeatColor(p3(1f, 0.776f, 0f)),                     // yellow
    SeatColor(p3(0.957f, 0.173f, 0.314f)),             // red
    SeatColor(p3(0.271f, 0.314f, 1f)),                 // blue
    SeatColor(p3(0f, 0.8f, 0.46f)),                    // green
    SeatColor(p3(1f, 0.38f, 0f)),                      // orange
    SeatColor(p3(0.682f, 0.925f, 1f)),                 // baby blue
    SeatColor(p3(0.42f, 0.118f, 1f), whiteText = true), // purple
    SeatColor(p3(1f, 0f, 0.902f)),                     // pink
    SeatColor(p3(0.784f, 1f, 0.302f)),                 // bright green
    SeatColor(p3(1f, 0.957f, 0.8f)),                   // sand
    SeatColor(p3(0f, 0.71f, 0.482f)),                  // sea green
    SeatColor(p3(0f, 0.459f, 1f), whiteText = true),   // light blue
    SeatColor(p3(0.325f, 0.412f, 0.784f), whiteText = true), // sand blue
    SeatColor(p3(0.561f, 0f, 0.263f), whiteText = true), // dark red
    SeatColor(p3(0.349f, 0.365f, 0.467f), whiteText = true), // grey
    SeatColor(p3(0.616f, 0.635f, 0.773f)),             // light grey
    SeatColor(p3(0.91f, 1f, 0.545f)),                  // baby green
    SeatColor(p3(1f, 0.839f, 0f))                      // gold
)

fun seatColor(index: Int): SeatColor = PlayerPalette[Math.floorMod(index, PlayerPalette.size)]

fun paletteColor(index: Int): Color = seatColor(index).color

object TableMotion {
    const val FAST = 300
    const val MENU_CHIPS = 500
    /** Springy pop with a visible overshoot — buttons appearing. */
    val PopOvershoot = CubicBezierEasing(0.49f, 0.2f, 0.19f, 1.48f)
    /** A gentler pop — result text appearing. */
    val Pop = CubicBezierEasing(0.5f, 0.3f, 0.2f, 1.4f)
    /** Panels sliding up from the bottom. */
    val SlideIn = CubicBezierEasing(0.36f, 0.62f, 0.48f, 1f)
}

fun tableText(size: TextUnit, color: Color = Color.White) =
    TextStyle(fontFamily = BebasNeue, fontSize = size, color = color, lineHeight = size * 0.95f)

/** Caps condensed label in the table's typeface. */
@Composable
fun TableLabel(
    text: String,
    size: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        text.uppercase(),
        style = tableText(size, color),
        textAlign = align,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/** Scales from 0 to full size with an overshoot the first time it appears. */
@Composable
fun Modifier.popIn(delayMillis: Int = 0, easing: CubicBezierEasing = TableMotion.PopOvershoot): Modifier {
    val scale = remember { Animatable(0f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, tween(TableMotion.FAST, delayMillis, easing)) }
    return graphicsLayer { scaleX = scale.value; scaleY = scale.value; alpha = scale.value.coerceIn(0f, 1f) }
}

/** A rounded pill button: bold color, caps label, a brief squash when pressed. */
@Composable
fun PillButton(
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White,
    enabled: Boolean = true,
    textSize: TextUnit = 24.sp,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 9.dp)
) {
    val press = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer { scaleX = press.value; scaleY = press.value; alpha = if (enabled) 1f else 0.35f }
            .clip(RoundedCornerShape(50))
            .background(color)
            .clickable(enabled = enabled) {
                scope.launch {
                    press.animateTo(0.9f, tween(90))
                    press.animateTo(1f, tween(140, easing = TableMotion.PopOvershoot))
                }
                onClick()
            }
            .padding(contentPadding)
    ) {
        TableLabel(label, textSize, color = textColor, maxLines = 1)
    }
}

/** A round on/off check — white ring when off, filled blue with a check when on. */
@Composable
fun RoundCheck(checked: Boolean, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(if (checked) TableColors.Blue else Color.Transparent)
            .border(BorderStroke(2.5.dp, if (checked) TableColors.Blue else Color.White), CircleShape)
    ) {
        if (checked) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

/** A square-ish value chip: yellow with black text when selected, white outline otherwise. */
@Composable
fun ValueChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) TableColors.Yellow else Color.Transparent)
            .border(BorderStroke(2.dp, if (selected) TableColors.Yellow else Color.White), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        TableLabel(label, 30.sp, color = if (selected) Color.Black else Color.White, maxLines = 1)
    }
}

@Composable
fun CloseCircle(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .popIn(easing = TableMotion.PopOvershoot)
            .size(40.dp)
            .clip(CircleShape)
            .background(TableColors.Accent)
            .clickable(onClick = onClick)
    ) {
        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

/**
 * A full-screen overlay drawn over the table itself rather than in a separate dialog window, so it
 * fades in over the tiles the way the table's own panels do. Back closes it.
 */
@Composable
fun TableOverlay(
    title: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    scrim: Float = 0.92f,
    content: @Composable () -> Unit
) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    BackHandler(onBack = onClose)
    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn(tween(TableMotion.FAST)),
        exit = fadeOut(tween(TableMotion.FAST))
    ) {
        Box(
            modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrim))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                ) {
                    Box(Modifier.weight(1f)) {
                        if (title != null) TableLabel(title, 46.sp)
                    }
                    CloseCircle(onClick = onClose)
                }
                Box(Modifier.weight(1f).fillMaxWidth()) { content() }
            }
        }
    }
}
