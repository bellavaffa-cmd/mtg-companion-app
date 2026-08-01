package com.mtgcompanion.app.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.ui.theme.Surface

/** Spring-based scale-down-on-press feedback. Pass the same [interactionSource] given to clickable/combinedClickable. */
@Composable
fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "pressScale"
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Card-shaped shimmering placeholder shown while content is still loading. */
@Composable
fun ShimmerPlaceholder(modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(4.dp)) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerProgress"
    )
    Box(
        modifier
            .clip(shape)
            .background(Surface)
            .drawWithCache {
                val w = size.width
                val brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent),
                    start = Offset(progress * w - w, 0f),
                    end = Offset(progress * w, size.height)
                )
                onDrawWithContent {
                    drawContent()
                    drawRect(brush)
                }
            }
    )
}

/** A dollar amount that eases toward [value] instead of snapping, e.g. "$142.50" rolling up/down. */
@Composable
fun AnimatedUsdText(value: Double, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(
        targetValue = value.toFloat(),
        animationSpec = tween(650),
        label = "usdCounter"
    )
    androidx.compose.material3.Text(
        "$" + "%,.2f".format(animated),
        style = style,
        color = color,
        modifier = modifier
    )
}

/** A diagonal light sweep, like a holo-foil card catching the light. Chain onto an image's own modifiers, after any `.clip(...)`. */
@Composable
fun Modifier.foilShine(): Modifier {
    val transition = rememberInfiniteTransition(label = "foilShine")
    val progress by transition.animateFloat(
        initialValue = -0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "foilShineProgress"
    )
    return this.drawWithCache {
        val w = size.width
        val brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                Color.White.copy(alpha = 0.05f),
                Color.White.copy(alpha = 0.22f),
                Color.White.copy(alpha = 0.05f),
                Color.Transparent
            ),
            start = Offset(progress * w - w * 0.6f, 0f),
            end = Offset(progress * w + w * 0.6f, size.height)
        )
        onDrawWithContent {
            drawContent()
            drawRect(brush)
        }
    }
}
