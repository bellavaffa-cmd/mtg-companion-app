package com.mtgcompanion.app.ui.collection

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.Money
import com.mtgcompanion.app.data.Prices
import com.mtgcompanion.app.data.ValueHistory
import com.mtgcompanion.app.data.ValuePoint
import com.mtgcompanion.app.data.ValueRange
import com.mtgcompanion.app.data.changeOf
import com.mtgcompanion.app.data.pointsIn
import com.mtgcompanion.app.ui.decks.FilterPill
import com.mtgcompanion.app.ui.theme.LocalAppColors
import com.mtgcompanion.app.ui.theme.NumberStyle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
private fun day(date: String) = runCatching { LocalDate.parse(date).format(DAY) }.getOrDefault(date)

/** "+₱1,234 (+5.2%)" / "−$3 (−0.4%)". */
private fun signed(money: Money, usd: Double, percent: Double?): String {
    val sign = if (usd < 0) "−" else "+"
    return sign + money.format(abs(usd), whole = abs(money.toLocal(usd)) >= 100) +
        (percent?.let { " ($sign${String.format(Locale.US, "%.1f", abs(it))}%)" } ?: "")
}

/**
 * The collection's value over time: a line of the daily values Home has noted, over the last month,
 * three months, year or all of it — and how much it moved. Touch the line for a day's value.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValueHistoryScreen(onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val money by Prices.money.collectAsState()
    val all by ValueHistory.points.collectAsState()
    var range by remember { mutableStateOf(ValueRange.QUARTER) }
    var picked by remember { mutableStateOf<ValuePoint?>(null) }
    val points = remember(all, range) { pointsIn(all, range) }
    val change = changeOf(points)

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("Collection value", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.accent) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            val shown = picked ?: all.lastOrNull()
            if (shown == null) {
                Text("—", style = NumberStyle(44), color = colors.textDim)
                Text(
                    "Your binders' value is noted once a day, when Home works it out. The first one appears once their prices have loaded.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                    modifier = Modifier.padding(top = 8.dp)
                )
                return@Column
            }
            Text(money.format(shown.usd), style = NumberStyle(44), color = colors.textPrimary)
            Text(
                if (picked != null) "${day(shown.date)} · ${shown.cards} cards"
                else change?.let { "${signed(money, it.usd, it.percent)} since ${day(it.from.date)}" } ?: "${shown.cards} cards · noted ${day(shown.date)}",
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    picked != null || change == null -> colors.textMuted
                    change.usd > 0 -> colors.accent
                    change.usd < 0 -> Color(0xFFD3402F)
                    else -> colors.textMuted
                },
                modifier = Modifier.padding(top = 2.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 18.dp)) {
                ValueRange.entries.forEach { r -> FilterPill(r.label, range == r) { range = r; picked = null } }
            }

            if (points.size < 2) {
                Text(
                    "A value is noted once a day. Come back tomorrow to see it change.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                    modifier = Modifier.padding(top = 24.dp)
                )
            } else {
                ValueChart(points, colors.accent, colors.surface3, onPick = { picked = it }, modifier = Modifier.padding(top = 18.dp).fillMaxWidth().height(220.dp))
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text(day(points.first().date), style = MaterialTheme.typography.labelSmall, color = colors.textDim, modifier = Modifier.weight(1f))
                    Text(day(points.last().date), style = MaterialTheme.typography.labelSmall, color = colors.textDim)
                }
                val low = points.minBy { it.usd }
                val high = points.maxBy { it.usd }
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Figure("Low", money.format(low.usd), day(low.date), Modifier.weight(1f))
                    Figure("High", money.format(high.usd), day(high.date), Modifier.weight(1f))
                }
            }
            Text(
                "The value of your binders (not wishlists) at TCGplayer's market prices" +
                    (if (money.isUsd) "" else ", in ${money.currency.code} at today's exchange rate") +
                    ". It's noted on this device, once a day.",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textDim,
                modifier = Modifier.padding(top = 20.dp, bottom = 24.dp)
            )
        }
    }
}

@Composable
private fun Figure(label: String, value: String, detail: String, modifier: Modifier) {
    val colors = LocalAppColors.current
    Column(modifier.background(colors.surface, androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).padding(14.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
        Text(value, style = NumberStyle(22), color = colors.textPrimary)
        Text(detail, style = MaterialTheme.typography.labelSmall, color = colors.textDim)
    }
}

/** The values as a line over a soft fill, spaced by date. Touching or dragging picks the nearest day. */
@Composable
private fun ValueChart(points: List<ValuePoint>, line: Color, grid: Color, onPick: (ValuePoint?) -> Unit, modifier: Modifier) {
    val days = remember(points) { points.map { LocalDate.parse(it.date).toEpochDay() } }
    val firstDay = days.first()
    val daySpan = (days.last() - firstDay).coerceAtLeast(1)
    fun xAt(i: Int, width: Float) = (days[i] - firstDay).toFloat() / daySpan * width
    var touchX by remember(points) { mutableStateOf<Float?>(null) }
    var width by remember { mutableStateOf(1f) }
    val pickedIndex = touchX?.let { tx -> points.indices.minBy { abs(xAt(it, width) - tx) } }
    LaunchedEffect(pickedIndex) { onPick(pickedIndex?.let { points[it] }) }
    Box(modifier) {
        Canvas(
            Modifier.fillMaxSize()
                .onSizeChanged { width = it.width.toFloat() }
                .pointerInput(points) {
                    detectTapGestures(onPress = { touchX = it.x; tryAwaitRelease(); touchX = null })
                }
                .pointerInput(points) {
                    // Sideways only, so the page still scrolls up and down over the chart.
                    detectHorizontalDragGestures(
                        onDragStart = { touchX = it.x },
                        onHorizontalDrag = { change, _ -> touchX = change.position.x },
                        onDragEnd = { touchX = null },
                        onDragCancel = { touchX = null }
                    )
                }
        ) {
            val lo = points.minOf { it.usd }
            val hi = points.maxOf { it.usd }
            val span = (hi - lo).takeIf { it > 0 } ?: maxOf(hi * 0.1, 1.0)
            val bottom = (lo - span * 0.1)
            val top = (hi + span * 0.1)
            fun x(i: Int) = xAt(i, size.width)
            fun y(v: Double) = (size.height * (1 - (v - bottom) / (top - bottom))).toFloat()

            for (g in 0..3) {
                val gy = size.height * g / 3f
                drawLine(grid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
            }
            val path = Path().apply {
                points.forEachIndexed { i, p -> if (i == 0) moveTo(x(i), y(p.usd)) else lineTo(x(i), y(p.usd)) }
            }
            val fill = Path().apply {
                addPath(path)
                lineTo(x(points.lastIndex), size.height)
                lineTo(x(0), size.height)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.28f), line.copy(alpha = 0f))))
            drawPath(path, line, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

            pickedIndex?.let { i ->
                drawLine(line.copy(alpha = 0.5f), Offset(x(i), 0f), Offset(x(i), size.height), strokeWidth = 1.5f)
                drawCircle(line, radius = 6.dp.toPx(), center = Offset(x(i), y(points[i].usd)))
            }
        }
    }
}
