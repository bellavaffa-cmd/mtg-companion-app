package com.mtgcompanion.app.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** What a pull-to-sync ended with, shown briefly in the indicator before it tucks away. */
data class SyncPullResult(val kind: Kind, val label: String) {
    enum class Kind { OK, FAILED, SIGNED_OUT }
}

private sealed interface SyncPhase {
    data object Idle : SyncPhase
    data object Syncing : SyncPhase
    data class Done(val result: SyncPullResult) : SyncPhase
}

/** The spinner stays up at least this long, so a fast sync still reads as having happened. */
private const val MIN_SYNC_MILLIS = 900L

/**
 * Pull down past the top of any scrolling list inside [content] to sync with the account. The sync
 * icon winds up as you pull, spins while [onSync] runs, then the pill opens to say how it went.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullToSyncBox(
    enabled: Boolean,
    onSync: suspend () -> SyncPullResult,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val state = rememberPullToRefreshState()
    var phase by remember { mutableStateOf<SyncPhase>(SyncPhase.Idle) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // A tick as the pull crosses the point where letting go will sync.
    LaunchedEffect(state) {
        snapshotFlow { state.distanceFraction >= 1f }
            .distinctUntilChanged()
            .collect { armed -> if (armed && phase == SyncPhase.Idle) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }

    Box(
        modifier.pullToRefresh(
            isRefreshing = phase != SyncPhase.Idle,
            state = state,
            enabled = enabled,
            onRefresh = {
                if (phase != SyncPhase.Idle) return@pullToRefresh
                phase = SyncPhase.Syncing
                scope.launch {
                    val started = System.currentTimeMillis()
                    val result = onSync()
                    val elapsed = System.currentTimeMillis() - started
                    if (elapsed < MIN_SYNC_MILLIS) delay(MIN_SYNC_MILLIS - elapsed)
                    phase = SyncPhase.Done(result)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    delay(if (result.kind == SyncPullResult.Kind.OK) 1_200 else 2_200)
                    phase = SyncPhase.Idle
                }
            }
        )
    ) {
        content()
        SyncPullIndicator(state, phase, Modifier.align(Alignment.TopCenter))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncPullIndicator(state: PullToRefreshState, phase: SyncPhase, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    // Nothing to draw at rest; reading the fraction here keeps the list from recomposing while pulling.
    if (phase == SyncPhase.Idle && state.distanceFraction <= 0f) return

    val spin = rememberInfiniteTransition(label = "syncSpin")
    val spinAngle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "spinAngle")
    val armedTint by animateColorAsState(
        if (phase != SyncPhase.Idle || state.distanceFraction >= 1f) colors.accent else colors.textMuted,
        label = "armedTint"
    )

    Box(
        modifier
            .padding(top = 12.dp)
            .graphicsLayer {
                val f = state.distanceFraction
                // Follows the finger down, easing off past the threshold; tucks up and fades as it retracts.
                val pulled = if (f <= 1f) f else 1f + (f - 1f) * 0.35f
                translationY = (pulled - 1f) * 64.dp.toPx()
                alpha = f.coerceIn(0f, 1f)
                val scale = 0.6f + 0.4f * f.coerceIn(0f, 1f)
                scaleX = scale
                scaleY = scale
            }
            .shadow(10.dp, RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .background(colors.surface2)
            .border(1.dp, colors.border, RoundedCornerShape(50))
            .animateContentSize()
            .heightIn(min = 44.dp)
    ) {
        AnimatedContent(
            targetState = phase is SyncPhase.Done,
            transitionSpec = { (fadeIn(tween(180)) + scaleIn(initialScale = 0.8f)) togetherWith fadeOut(tween(120)) },
            label = "syncPill"
        ) { done ->
            val result = (phase as? SyncPhase.Done)?.result
            if (done && result != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(44.dp).padding(start = 12.dp, end = 16.dp)
                ) {
                    val (icon, tint) = when (result.kind) {
                        SyncPullResult.Kind.OK -> Icons.Filled.CheckCircle to colors.success
                        SyncPullResult.Kind.FAILED -> Icons.Filled.CloudOff to colors.error
                        SyncPullResult.Kind.SIGNED_OUT -> Icons.Filled.PersonOutline to colors.textMuted
                    }
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                    Text(
                        result.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(34.dp)) {
                        val stroke = 3.dp.toPx()
                        val arc = Size(size.width - stroke, size.height - stroke)
                        val topLeft = Offset(stroke / 2, stroke / 2)
                        drawArc(colors.border, 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
                        if (phase == SyncPhase.Syncing) {
                            drawArc(armedTint, spinAngle - 90f, 110f, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                        } else {
                            val sweep = 360f * state.distanceFraction.coerceIn(0f, 1f)
                            drawArc(armedTint, -90f, sweep, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                        }
                    }
                    Icon(
                        Icons.Filled.Sync,
                        contentDescription = if (phase == SyncPhase.Syncing) "Syncing" else "Pull to sync",
                        tint = armedTint,
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer {
                                rotationZ = if (phase == SyncPhase.Syncing) -spinAngle * 2f
                                else -state.distanceFraction.coerceIn(0f, 1.4f) * 300f
                            }
                    )
                }
            }
        }
    }
}
