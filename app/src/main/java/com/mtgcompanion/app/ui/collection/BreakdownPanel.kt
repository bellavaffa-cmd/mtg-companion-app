package com.mtgcompanion.app.ui.collection

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.CollectionBreakdown
import com.mtgcompanion.app.data.Slice
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.common.PillChip
import com.mtgcompanion.app.ui.common.rememberMoney
import com.mtgcompanion.app.ui.theme.LocalAppColors
import com.mtgcompanion.app.ui.theme.ManaColors

/** The colour buckets' own colours (see colorBucket). */
private fun colorOf(label: String): Color? = when (label) {
    "White" -> ManaColors.W
    "Blue" -> ManaColors.U
    "Black" -> ManaColors.B
    "Red" -> ManaColors.R
    "Green" -> ManaColors.G
    "Multicolor" -> Color(0xFFD4AF37)
    "Colorless" -> ManaColors.C
    else -> null
}

/**
 * The dashboard's Breakdown, folded away until asked for: the collection's value by set (the top
 * eight and the rest), by colour, by rarity and by type, and the ten most valuable cards. Tapping a
 * card opens its page.
 */
@Composable
fun BreakdownPanel(breakdown: CollectionBreakdown?, onViewCard: (String) -> Unit) {
    val colors = LocalAppColors.current
    val money = rememberMoney()
    var open by rememberSaveable { mutableStateOf(false) }
    var view by rememberSaveable { mutableStateOf("Sets") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(BorderStroke(1.dp, colors.border), RoundedCornerShape(14.dp))
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text("Breakdown", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Text(
                if (breakdown == null) "Working it out…" else "Value by set, colour, rarity, type",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted
            )
            Icon(
                if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (open) "Hide breakdown" else "Show breakdown",
                tint = colors.textMuted
            )
        }
        if (!open || breakdown == null) return@Column
        Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf("Sets", "Colours", "Rarity", "Types", "Most valuable").forEach { v -> PillChip(v, view == v, { view = v }) }
            }
            when (view) {
                "Sets" -> SliceBars(breakdown.bySet, breakdown.totalUsd) { null }
                "Colours" -> SliceBars(breakdown.byColor, breakdown.totalUsd) { colorOf(it) }
                "Rarity" -> SliceBars(breakdown.byRarity, breakdown.totalUsd) { null }
                "Types" -> SliceBars(breakdown.byType, breakdown.totalUsd) { null }
                else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (breakdown.mostValuable.isEmpty()) {
                        Text("No prices for your cards yet.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    }
                    breakdown.mostValuable.forEachIndexed { i, card ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onViewCard(card.name) }.padding(vertical = 2.dp)
                        ) {
                            Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = colors.textDim, modifier = Modifier.width(18.dp))
                            AsyncImage(
                                model = card.imageUrl.toArtCropUrl(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(width = 44.dp, height = 32.dp).clip(RoundedCornerShape(6.dp)).background(colors.surface2)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(card.name, style = MaterialTheme.typography.bodySmall, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(card.setName.ifBlank { null }, if (card.copies > 1) "×${card.copies} at ${money.format(card.usd ?: 0.0)}" else null).joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(money.format(card.value), style = MaterialTheme.typography.labelLarge, color = colors.accentLight)
                        }
                    }
                }
            }
        }
    }
}

/** One bar per slice, its length its share of [total], with its value and copies. */
@Composable
private fun SliceBars(slices: List<Slice>, total: Double, tint: (String) -> Color?) {
    val colors = LocalAppColors.current
    val money = rememberMoney()
    val most = slices.maxOfOrNull { it.usd }?.takeIf { it > 0 } ?: 1.0
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        slices.forEach { s ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.label, style = MaterialTheme.typography.labelMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    val share = if (total > 0) " · ${Math.round(s.usd / total * 100)}%" else ""
                    Text("${money.format(s.usd)}$share · ${s.copies} cards", style = MaterialTheme.typography.labelSmall, color = colors.textMuted, maxLines = 1)
                }
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(colors.surface2)) {
                    Box(
                        Modifier
                            .fillMaxWidth((s.usd / most).toFloat().coerceIn(0.02f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(tint(s.label) ?: colors.accent)
                    )
                }
            }
        }
    }
}
