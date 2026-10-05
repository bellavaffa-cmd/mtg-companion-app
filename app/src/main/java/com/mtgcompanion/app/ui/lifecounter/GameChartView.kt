package com.mtgcompanion.app.ui.lifecounter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

// A finished game's life chart (drawn on a Canvas) and its recap, by round or by time. Shown from the
// table's games and the Play tab's recent games, so its ink comes from whoever shows it: the table's
// black overlay, or the app's light or dark theme. Lines are in the seats' colours. The web app's
// src/lifecounter/GameChart.tsx; the logic is LifeChart.kt.

/** A seat as the chart shows it: its name and seat colour. */
data class ChartSeat(val seat: Int, val name: String, val colorIndex: Int)

/** A kept game's seats for its chart (seat order's colours for games kept before colours were). */
fun chartSeats(game: TableGame): List<ChartSeat> = game.players.map { ChartSeat(it.seat, it.name, it.colorIndex ?: (it.seat - 1)) }

/** About four tidy steps from [min] to [max]. */
private fun ticks(min: Double, max: Double): List<Double> {
    val span = maxOf(1.0, max - min)
    val raw = span / 4
    val pow = 10.0.pow(floor(log10(raw)))
    val step = listOf(1.0, 2.0, 5.0, 10.0).map { it * pow }.firstOrNull { it >= raw } ?: raw
    val out = mutableListOf<Double>()
    var v = ceil(min / step) * step
    while (v <= max + 1e-9) {
        out += (v * 1000).roundToLong() / 1000.0
        v += step
    }
    return out
}

@Composable
fun GameChartView(
    log: GameLog,
    seats: List<ChartSeat>,
    ink: Color,
    muted: Color,
    line: Color,
    modifier: Modifier = Modifier
) {
    var byTurn by remember { mutableStateOf(true) }
    val chart = lifeChart(log, byTurn)
    fun nameOf(seat: Int) = seats.firstOrNull { it.seat == seat }?.name ?: "Player $seat"
    fun colorOf(seat: Int) = seatColor(seats.firstOrNull { it.seat == seat }?.colorIndex ?: (seat - 1)).color
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = muted, fontSize = 10.sp)
    val summary = "Life totals ${if (byTurn) "by round" else "over time"}. " +
        chart.series.joinToString("; ") { s -> "${nameOf(s.seat)}: ${s.points.first().life} to ${s.points.last().life}" }
    val recap = recapLines(gameRecap(log), ::nameOf)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChartSwitch("By round", byTurn, ink, muted, line) { byTurn = true }
            ChartSwitch("By time", !byTurn, ink, muted, line) { byTurn = false }
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(190.dp)
                .semantics { contentDescription = summary }
        ) {
            val left = 34.dp.toPx()
            val right = size.width - 10.dp.toPx()
            val top = 10.dp.toPx()
            val bottom = size.height - 24.dp.toPx()
            val xMax = maxOf(1L, chart.xMax).toFloat()
            val ySpan = maxOf(1, chart.yMax - chart.yMin).toFloat()
            fun x(v: Long) = left + v / xMax * (right - left)
            fun y(v: Int) = top + (chart.yMax - v) / ySpan * (bottom - top)

            for (t in ticks(chart.yMin.toDouble(), chart.yMax.toDouble())) {
                val yy = top + ((chart.yMax - t) / ySpan).toFloat() * (bottom - top)
                drawLine(
                    if (t == 0.0) muted else line, Offset(left, yy), Offset(right, yy), strokeWidth = 1.dp.toPx(),
                    pathEffect = if (t == 0.0) PathEffect.dashPathEffect(floatArrayOf(6f, 6f)) else null
                )
                val text = measurer.measure(t.roundToLong().toString(), labelStyle)
                drawText(text, topLeft = Offset(left - 4.dp.toPx() - text.size.width, yy - text.size.height / 2f))
            }
            val xTicks = if (byTurn) ticks(0.0, chart.xMax.toDouble()).filter { it == floor(it) }.map { it.toLong() }
            else ticks(0.0, chart.xMax / 60_000.0).map { (it * 60_000).roundToLong() }
            for (t in xTicks) {
                val label = if (byTurn) (if (t == 0L) "Start" else t.toString()) else "${t / 60_000}m"
                val text = measurer.measure(label, labelStyle)
                drawText(text, topLeft = Offset(x(t) - text.size.width / 2f, bottom + 6.dp.toPx()))
            }
            for (s in chart.series) {
                val color = colorOf(s.seat)
                val path = Path()
                s.points.forEachIndexed { i, p ->
                    when {
                        i == 0 -> path.moveTo(x(p.x), y(p.life))
                        byTurn -> path.lineTo(x(p.x), y(p.life))
                        // By time, life holds until the next change: a step, not a slope.
                        else -> {
                            path.lineTo(x(p.x), y(s.points[i - 1].life))
                            path.lineTo(x(p.x), y(p.life))
                        }
                    }
                }
                drawPath(path, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                if (byTurn) s.points.forEach { p -> drawCircle(color, radius = 2.6.dp.toPx(), center = Offset(x(p.x), y(p.life))) }
                if (log.outs.any { it.seat == s.seat }) {
                    val last = s.points.last()
                    val mark = measurer.measure("✕", TextStyle(color = ink, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                    drawText(mark, topLeft = Offset(x(last.x) - mark.size.width / 2f, y(last.life) - mark.size.height - 2.dp.toPx()))
                }
            }
        }
        // The legend, and what the chart says, for anyone who can't see it.
        chart.series.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(colorOf(s.seat)))
                Text(nameOf(s.seat), color = ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("${s.points.last().life}", color = muted, fontSize = 14.sp)
            }
        }
        recap.forEach { Text("• $it", color = ink.copy(alpha = 0.85f), fontSize = 13.sp) }
        if (!byTurn) Text("${durationText(log.endMs)} on the game clock; time paused doesn't count.", color = muted, fontSize = 12.sp)
    }
}

@Composable
private fun ChartSwitch(label: String, on: Boolean, ink: Color, muted: Color, line: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .border(1.5.dp, if (on) ink else line, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .semantics { role = Role.Tab; selected = on }
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(label, color = if (on) ink else muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
