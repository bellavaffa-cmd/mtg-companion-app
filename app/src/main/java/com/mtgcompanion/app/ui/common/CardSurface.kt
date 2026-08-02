package com.mtgcompanion.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.Surface2

/**
 * The app-wide "elevated" card look: a soft top-to-bottom gradient (instead of a flat fill) and a
 * faint hairline border, standing in for real elevation/shadow without needing a shadow API that
 * behaves differently per-theme. Replaces the old flat-background + 1dp-border pattern used on
 * every tile/row/panel. [borderColor] lets a few emphasized surfaces (e.g. "continue this deck")
 * keep their accent-tinted border instead of the default hairline.
 */
@Composable
fun Modifier.elevatedCard(shape: Shape = RoundedCornerShape(14.dp), borderColor: Color = BorderColor): Modifier = this
    .clip(shape)
    .background(Brush.verticalGradient(listOf(Surface, Surface2)))
    .border(BorderStroke(1.dp, borderColor), shape)
