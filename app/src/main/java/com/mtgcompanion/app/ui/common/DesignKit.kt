package com.mtgcompanion.app.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mtgcompanion.app.ui.theme.BebasNumbers
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.LocalAppColors
import com.mtgcompanion.app.ui.theme.ManaColors
import com.mtgcompanion.app.ui.theme.Manrope
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.TextMuted
import kotlinx.coroutines.delay
import kotlin.math.abs

// Shared building blocks for the refreshed look: identity strips, pips, fallback art, count-ups,
// staggered entrances, pill chips and a sliding segmented control.

/** The spring every "pop" in the app uses — a small overshoot that settles quickly. */
fun <T> popSpring() = spring<T>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)

/**
 * Fades and lifts content in on first composition, delayed by [index] so a column of items arrives
 * one after another. [enabled] lets a lazy list switch it off once the first screenful has played,
 * so items scrolled into view later don't re-animate.
 */
fun Modifier.riseIn(index: Int = 0, enabled: Boolean = true): Modifier = composed {
    if (!enabled) return@composed this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index.coerceAtMost(10) * 45L))
        progress.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
    }
    val density = LocalDensity.current
    this.graphicsLayer {
        alpha = progress.value
        translationY = with(density) { 16.dp.toPx() } * (1f - progress.value)
    }
}

/** Remembers a flag that is true for the first moments of a screen — pass it to [riseIn] in lazy lists. */
@Composable
fun rememberEntranceWindow(millis: Long = 900): Boolean {
    var open by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(millis); open = false }
    return open
}

/** Scales a badge or chip in from nothing with [popSpring] the first time it appears. */
fun Modifier.popIn(): Modifier = composed {
    val scale = remember { Animatable(0.3f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, popSpring()) }
    this.graphicsLayer { scaleX = scale.value; scaleY = scale.value; alpha = ((scale.value - 0.3f) / 0.7f).coerceIn(0f, 1f) }
}

/**
 * A number that counts up from zero on first show, then eases between later values — deck value,
 * card counts, prices. [format] turns the animated value into text.
 */
@Composable
fun CountUpText(
    value: Double,
    style: TextStyle,
    color: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
    format: (Double) -> String = { "%,.0f".format(it) }
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(value) { anim.animateTo(value.toFloat(), tween(if (anim.value == 0f) 900 else 500, easing = FastOutSlowInEasing)) }
    Text(format(anim.value.toDouble()), style = style, color = color, modifier = modifier, maxLines = 1)
}

/** A thin glowing bar in a deck's colour identity — the at-a-glance "what colours is this" cue. */
@Composable
fun IdentityStrip(colors: List<String>, modifier: Modifier = Modifier, thickness: Dp = 3.dp) {
    val cs = colors.ifEmpty { listOf("C") }.map { ManaColors.of(it) }
    val brush = if (cs.size == 1) Brush.horizontalGradient(listOf(cs[0], cs[0])) else Brush.horizontalGradient(cs)
    Box(modifier.height(thickness + 6.dp)) {
        // Soft glow underneath, then the crisp bar.
        Box(Modifier.fillMaxWidth().height(thickness + 6.dp).graphicsLayer { alpha = 0.35f }.clip(RoundedCornerShape(50)).background(brush))
        Box(Modifier.align(Alignment.Center).fillMaxWidth().height(thickness).clip(RoundedCornerShape(50)).background(brush))
    }
}

/** Colour-identity pips drawn locally (no network), letters in the numbers face. */
@Composable
fun ManaPips(colors: List<String>, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = modifier) {
        colors.ifEmpty { listOf("C") }.forEach { c ->
            val code = c.uppercase().take(1)
            Box(
                Modifier.size(size).clip(CircleShape).background(ManaColors.of(code)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    code,
                    fontFamily = BebasNumbers,
                    fontSize = (size.value * 0.68f).sp,
                    color = if (code == "U" || code == "B") Color(0xFFF4F1EA) else Color(0xFF15161B),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }
    }
}

private fun seedFloat(seed: Int, salt: Int): Float {
    var x = seed * 374761393 + salt * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    return (abs(x xor (x ushr 16)) % 10000) / 10000f
}

/**
 * Soft mana-coloured light blooms over a dark wash — shown behind every art image, so a card whose
 * image hasn't loaded (or can't) still gets a distinct, on-theme placeholder instead of a blank box.
 */
fun Modifier.fallbackArt(seed: String, colors: List<String> = emptyList()): Modifier = drawBehind {
    val h = seed.hashCode()
    val palette = colors.ifEmpty {
        // Unknown colours: pick two stable hues from the name so different cards still differ.
        val all = listOf("W", "U", "B", "R", "G")
        listOf(all[abs(h) % 5], all[abs(h / 7) % 5])
    }.map { ManaColors.of(it) }
    val base0 = palette[0]
    val base1 = palette[1 % palette.size]
    drawRect(Brush.linearGradient(listOf(lerpDark(base0, 0.55f), lerpDark(base1, 0.8f)), start = Offset.Zero, end = Offset(size.width, size.height)))
    for (i in 0 until 4) {
        val c = palette[i % palette.size]
        val center = Offset(seedFloat(h, i * 2 + 1) * size.width, seedFloat(h, i * 2 + 2) * size.height)
        val radius = (0.35f + seedFloat(h, i + 11) * 0.45f) * maxOf(size.width, size.height)
        drawRect(Brush.radialGradient(listOf(c.copy(alpha = 0.85f), c.copy(alpha = 0f)), center = center, radius = radius))
    }
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f))))
}

private fun lerpDark(c: Color, t: Float) = Color(c.red * (1 - t) + 0.03f * t, c.green * (1 - t) + 0.03f * t, c.blue * (1 - t) + 0.045f * t, 1f)

/** An art image with [fallbackArt] underneath, so it never shows as an empty box while loading or offline. */
@Composable
fun ArtImage(
    model: Any?,
    seed: String,
    modifier: Modifier = Modifier,
    colors: List<String> = emptyList(),
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop
) {
    Box(modifier.fallbackArt(seed, colors)) {
        if (model != null) {
            AsyncImage(model = model, contentDescription = contentDescription, contentScale = contentScale, modifier = Modifier.fillMaxSize())
        }
    }
}

/** A rounded filter/choice chip: filled when selected, with an optional count in the numbers face. */
@Composable
fun PillChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, count: Int? = null) {
    val colors = LocalAppColors.current
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .pressScale(interaction)
            .clip(RoundedCornerShape(50))
            .background(if (selected) colors.textPrimary else colors.surface)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp), color = if (selected) colors.bg else colors.textMuted, maxLines = 1)
        if (count != null) {
            Text(
                "$count",
                fontFamily = BebasNumbers,
                fontSize = 16.sp,
                color = (if (selected) colors.bg else colors.textMuted).copy(alpha = 0.75f),
                modifier = Modifier.padding(start = 6.dp, top = 1.dp)
            )
        }
    }
}

/** A small status badge ("CUT", "COMBO") that pops in the first time it's shown. */
@Composable
fun StatusBadge(label: String, fill: Color, ink: Color, icon: ImageVector? = null, outlined: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .popIn()
            .clip(RoundedCornerShape(50))
            .background(if (outlined) Color.Transparent else fill)
            .then(if (outlined) Modifier.background(fill.copy(alpha = 0.14f)) else Modifier)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = if (outlined) fill else ink, modifier = Modifier.size(11.dp))
        Text(label, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 10.5.sp, letterSpacing = 0.4.sp, color = if (outlined) fill else ink)
    }
}

/** Section title with an optional trailing text action ("See all"). */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (action != null && onAction != null) {
            Text(action, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp), color = Gold, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(6.dp))
        }
    }
}

/**
 * A pill-shaped tab control whose highlight slides (with a little spring) to the selected tab.
 * Scrolls horizontally when the labels don't fit, e.g. the deck page's five sections.
 */
@Composable
fun SegmentedTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, counts: Map<Int, Int> = emptyMap()) {
    val colors = LocalAppColors.current
    val density = LocalDensity.current
    val xs = remember(labels.size) { mutableStateListOf(*Array(labels.size) { 0f }) }
    val ws = remember(labels.size) { mutableStateListOf(*Array(labels.size) { 0f }) }
    val indX by animateDpAsState(with(density) { xs.getOrElse(selected) { 0f }.toDp() }, popSpring(), label = "tabX")
    val indW by animateDpAsState(with(density) { ws.getOrElse(selected) { 0f }.toDp() }, popSpring(), label = "tabW")
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .horizontalScroll(rememberScrollState())
            .padding(4.dp)
    ) {
        if (ws.getOrElse(selected) { 0f } > 0f) {
            Box(Modifier.offset(x = indX).width(indW).height(38.dp).clip(RoundedCornerShape(12.dp)).background(colors.surface3))
        }
        Row {
            labels.forEachIndexed { i, label ->
                val isSel = i == selected
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .height(38.dp)
                        .onGloballyPositioned { xs[i] = it.positionInParentX(); ws[i] = it.size.width.toFloat() }
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(i) }
                        .padding(horizontal = 14.dp)
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.5.sp), color = if (isSel) colors.textPrimary else colors.textMuted, maxLines = 1)
                    counts[i]?.takeIf { it > 0 }?.let { n ->
                        Box(
                            Modifier.padding(start = 6.dp).clip(RoundedCornerShape(50)).background(colors.accent).padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$n", fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = colors.onAccent)
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.layout.LayoutCoordinates.positionInParentX(): Float =
    parentLayoutCoordinates?.localPositionOf(this, Offset.Zero)?.x ?: 0f

/** A bold figure over a small caption — the stat tiles on Home and the deck page. */
@Composable
fun StatFigure(value: @Composable () -> Unit, label: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(Surface).padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        value()
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A rounded search field: magnifier, free text, and a clear button once there's something to clear. */
@Composable
fun SearchPill(query: String, onQueryChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val app = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(app.surface)
            .padding(start = 16.dp, end = 6.dp)
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = app.textMuted, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = app.textDim, maxLines = 1)
            }
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = app.textPrimary, fontWeight = FontWeight.Medium),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(app.accent),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotEmpty()) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).clickable { onQueryChange("") },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = app.textMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * Keeps a dialog or bottom sheet's own window fullscreen. They open in a separate window that
 * doesn't inherit the activity's hidden system bars, so without this the status bar pops back in
 * for as long as the sheet is up.
 */
@Composable
fun KeepSystemBarsHidden() {
    val view = androidx.compose.ui.platform.LocalView.current
    LaunchedEffect(view) {
        var parent: android.view.ViewParent? = view.parent
        var window: android.view.Window? = null
        while (parent != null && window == null) {
            window = (parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
            parent = parent.parent
        }
        window?.let { w ->
            androidx.core.view.WindowCompat.getInsetsController(w, w.decorView).apply {
                systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}
