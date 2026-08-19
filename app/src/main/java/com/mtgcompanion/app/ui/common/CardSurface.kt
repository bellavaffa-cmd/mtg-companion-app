package com.mtgcompanion.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.Surface2
import com.mtgcompanion.app.ui.theme.Surface3
import com.mtgcompanion.app.ui.theme.TextPrimary

/**
 * The app-wide "elevated" card look: a real soft shadow for genuine depth, a top-to-bottom
 * gradient (instead of a flat fill), and a faint hairline border. Replaces the old flat-background
 * + 1dp-border pattern used on every tile/row/panel. [borderColor] lets a few emphasized surfaces
 * (e.g. "continue this deck") keep their accent-tinted border instead of the default hairline.
 */
@Composable
fun Modifier.elevatedCard(shape: Shape = RoundedCornerShape(20.dp), borderColor: Color = BorderColor): Modifier = this
    .shadow(elevation = 6.dp, shape = shape, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.5f))
    .clip(shape)
    .background(Brush.verticalGradient(listOf(Surface, Surface2)))
    .border(BorderStroke(1.dp, borderColor), shape)

/**
 * A small top-start corner badge marking a card as having a second side (transform/modal-DFC/flip)
 * — a hint to tap in and flip it, before committing to a zoom. Mirrors the existing top-end ×qty
 * badge styling used across deck/binder/all-cards tiles, just on the opposite corner.
 */
@Composable
fun BoxScope.FlipBadge() {
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(6.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(3.dp)
    ) {
        Icon(
            Icons.Filled.Autorenew,
            contentDescription = "Has a second side",
            tint = GoldLight,
            modifier = Modifier.size(12.dp)
        )
    }
}

/**
 * A horizontally-scrollable row of small pill chips — printed keywords (Flying, Lifelink, ...)
 * plus a few heuristic theme tags (Lifegain, Removal, ...) from [ScryfallCard.tags]. No-op for an
 * empty list, so callers can render it unconditionally.
 */
@Composable
fun CardTagsRow(tags: List<String>, modifier: Modifier = Modifier) {
    if (tags.isEmpty()) return
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(tags) { tag ->
            Text(
                tag,
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Surface3)
                    .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}
