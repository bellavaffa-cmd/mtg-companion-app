package com.mtgcompanion.app.ui.collection

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtgcompanion.app.data.TourAction
import com.mtgcompanion.app.data.TourStep
import com.mtgcompanion.app.data.TourTarget
import com.mtgcompanion.app.data.TOUR_ID
import com.mtgcompanion.app.data.parseTourSeen
import com.mtgcompanion.app.data.tourEyebrow
import com.mtgcompanion.app.data.tourNextLabel
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.flow.MutableStateFlow

/*
 * The What's new tour (data/WhatsNew.kt), the web app's WhatsNewTour.tsx: each step lights up a part
 * of the Collection's home — marked with Modifier.tourTarget — with a card under or over it: "New · 2
 * of 5", the words, Skip tour, the step's call to action and Next.
 */

/** Whether this phone has seen the What's new tour, and Settings asking for it again. */
object WhatsNewStore {
    private const val PREFS = "whats_new"
    private const val KEY = "seen"

    /** Settings › What's new tour: the Collection's home shows the tour once it opens. */
    val replay = MutableStateFlow(false)

    fun seen(context: Context): String? = parseTourSeen(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))

    fun markSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, TOUR_ID).apply()
    }
}

/** Where each part of the screen the tour can light up is, in window coordinates. */
class TourTargets {
    val bounds = mutableStateMapOf<TourTarget, Rect>()
}

/** Marks this as the part of the screen the tour's [target] step lights up. */
fun Modifier.tourTarget(targets: TourTargets, target: TourTarget): Modifier =
    onGloballyPositioned { targets.bounds[target] = it.boundsInWindow() }

/**
 * The tour over the screen: [steps] in turn, each lighting up its target. [onStep] is told which one
 * shows, so the screen can scroll it into view.
 */
@Composable
fun WhatsNewTour(
    steps: List<TourStep>,
    targets: TourTargets,
    onAction: (TourAction) -> Unit,
    onClose: () -> Unit,
    onStep: (TourTarget) -> Unit = {}
) {
    val colors = LocalAppColors.current
    val density = LocalDensity.current
    var index by remember { mutableIntStateOf(0) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val step = steps.getOrNull(index) ?: return
    androidx.compose.runtime.LaunchedEffect(step.target) { onStep(step.target) }
    BackHandler { onClose() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInWindow() }
            // The screen under the tour doesn't take taps while it's up.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
    ) {
        val pad = with(density) { 6.dp.toPx() }
        val spot = targets.bounds[step.target]?.let {
            Rect(it.left - origin.x - pad, it.top - origin.y - pad, it.right - origin.x + pad, it.bottom - origin.y + pad)
        }
        val heightPx = with(density) { maxHeight.toPx() }
        val scrim = Color(0xFF050608).copy(alpha = 0.62f)
        val gold = colors.accent
        Canvas(Modifier.fillMaxSize()) {
            val radius = CornerRadius(16.dp.toPx())
            if (spot == null) {
                drawRect(scrim)
            } else {
                val path = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(Offset.Zero, size))
                    addRoundRect(RoundRect(spot, radius))
                }
                drawPath(path, scrim)
                drawRoundRect(gold, topLeft = spot.topLeft, size = spot.size, cornerRadius = radius, style = Stroke(3.dp.toPx()))
            }
        }
        // Under the lit part when there's room, else over it.
        val below = spot == null || spot.bottom + with(density) { 260.dp.toPx() } < heightPx
        val cardModifier = when {
            spot == null -> Modifier.align(Alignment.Center)
            below -> Modifier.align(Alignment.TopCenter).padding(top = with(density) { spot.bottom.toDp() } + 14.dp)
            else -> Modifier.align(Alignment.BottomCenter).padding(bottom = with(density) { (heightPx - spot.top).toDp() } + 14.dp)
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = cardModifier
                .padding(horizontal = 16.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surface2)
                .padding(18.dp)
                .semantics { paneTitle = step.title }
        ) {
            Text(
                tourEyebrow(index, steps.size).uppercase(),
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = colors.accent, letterSpacing = 0.7.sp
            )
            Text(step.title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
            Text(step.body, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                TextButton(onClick = onClose) { Text("Skip tour", color = colors.textMuted, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.weight(1f))
                step.cta?.let { cta ->
                    LoanButton(cta.label, primary = false, modifier = Modifier.padding(end = 8.dp)) { onClose(); onAction(cta.action) }
                }
                LoanButton(tourNextLabel(index, steps.size), primary = true) {
                    if (index >= steps.size - 1) onClose() else index += 1
                }
            }
        }
    }
}
