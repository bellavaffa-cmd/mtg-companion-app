package com.mtgcompanion.app.ui.common

import androidx.compose.foundation.clickable
import com.mtgcompanion.app.ui.theme.Gold
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
 * The app-wide card surface: a flat tonal fill one step up from the background, no outline and no
 * shadow — surfaces separate by tone, which keeps busy screens calm and lets card art carry the
 * colour. [borderColor] is only for the rare surface that genuinely needs an accent edge.
 */
@Composable
fun Modifier.elevatedCard(shape: Shape = RoundedCornerShape(20.dp), borderColor: Color = Color.Unspecified): Modifier = this
    .clip(shape)
    .background(Surface)
    .then(if (borderColor != Color.Unspecified) Modifier.border(BorderStroke(1.dp, borderColor), shape) else Modifier)

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
fun CardTagsRow(tags: List<String>, modifier: Modifier = Modifier, onClick: ((String) -> Unit)? = null) {
    if (tags.isEmpty()) return
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(tags) { tag ->
            Text(
                tag,
                style = MaterialTheme.typography.labelMedium,
                color = if (onClick != null) Gold else TextPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Surface3)
                    .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(50))
                    .then(if (onClick != null) Modifier.clickable { onClick(tag) } else Modifier)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}
