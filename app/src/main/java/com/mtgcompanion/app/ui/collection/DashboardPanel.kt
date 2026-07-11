package com.mtgcompanion.app.ui.collection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.ui.common.ManaSymbol
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary

private val typeChartColors = mapOf(
    "Creature" to Color(0xFF4E9A57),
    "Instant" to Color(0xFF3B82C4),
    "Sorcery" to Color(0xFF7E5AC4),
    "Artifact" to Color(0xFF9AA0A6),
    "Enchantment" to Color(0xFFE0A84E),
    "Planeswalker" to Color(0xFFD3402F),
    "Land" to Color(0xFF8A6D3B),
    "Battle" to Color(0xFFB5651D),
    "Other" to Color(0xFF6B6473)
)

private fun typeColor(type: String): Color = typeChartColors[type] ?: Color(0xFF6B6473)

/** Compact totals panel (value, mana colours, card-type pie) shared by the All Cards tab and each binder. */
@Composable
fun DashboardPanel(dashboard: CollectionDashboard?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Surface)
            .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(6.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (dashboard == null) {
            Text("Calculating totals…", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            return@Column
        }

        // Total value + mana colours on one row.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("$" + "%,.2f".format(dashboard.totalUsd), style = MaterialTheme.typography.titleLarge)
                Text(
                    "${dashboard.pricedCount} cards",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                dashboard.colorCounts.forEach { (color, count) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        ManaSymbol(color, size = 14.dp)
                        Text("$count", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                    }
                }
            }
        }

        // Card types: compact pie + legend.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TypePieChart(dashboard.typeCounts, modifier = Modifier.size(76.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
                dashboard.typeCounts.forEach { (type, count) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(50))
                                .background(typeColor(type))
                        )
                        Text(type, style = MaterialTheme.typography.labelMedium, color = TextPrimary, modifier = Modifier.weight(1f))
                        Text("$count", style = MaterialTheme.typography.labelMedium, color = GoldLight)
                    }
                }
            }
        }
    }
}

@Composable
private fun TypePieChart(typeCounts: List<Pair<String, Int>>, modifier: Modifier = Modifier) {
    val total = typeCounts.sumOf { it.second }.coerceAtLeast(1).toFloat()
    Canvas(modifier = modifier) {
        var startAngle = -90f
        typeCounts.forEach { (type, count) ->
            val sweep = count / total * 360f
            drawArc(color = typeColor(type), startAngle = startAngle, sweepAngle = sweep, useCenter = true)
            // Thin separator between slices for definition.
            drawArc(color = Bg, startAngle = startAngle, sweepAngle = sweep, useCenter = true, style = Stroke(width = 2f))
            startAngle += sweep
        }
    }
}
