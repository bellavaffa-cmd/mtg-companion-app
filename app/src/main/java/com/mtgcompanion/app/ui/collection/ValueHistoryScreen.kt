package com.mtgcompanion.app.ui.collection

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.BinderValue
import com.mtgcompanion.app.data.CardPriceHistory
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.Money
import com.mtgcompanion.app.data.Prices
import com.mtgcompanion.app.data.SeriesPoint
import com.mtgcompanion.app.data.SeriesRange
import com.mtgcompanion.app.data.ValueMover
import com.mtgcompanion.app.data.binderValues
import com.mtgcompanion.app.data.holdingsOf
import com.mtgcompanion.app.data.likeForLike
import com.mtgcompanion.app.data.trendSummary
import com.mtgcompanion.app.data.valueMovers
import com.mtgcompanion.app.data.valueSeries
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.common.EmptyAction
import com.mtgcompanion.app.ui.common.EmptyPrompt
import com.mtgcompanion.app.ui.common.a11yHeading
import com.mtgcompanion.app.ui.decks.FilterPill
import com.mtgcompanion.app.ui.theme.LocalAppColors
import com.mtgcompanion.app.ui.theme.NumberStyle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
private val SHORT_DAY = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
/** An epoch day as "12 Sep 2026" (or "12 Sep" with [short]). */
private fun day(epochDay: Long, short: Boolean = false) = LocalDate.ofEpochDay(epochDay).format(if (short) SHORT_DAY else DAY)

/** "+₱1,234 (+5.2%)" / "−$3 (−0.4%)". */
private fun signed(money: Money, usd: Double, percent: Double?): String {
    val sign = if (usd < 0) "−" else "+"
    return sign + money.format(abs(usd), whole = abs(money.toLocal(usd)) >= 100) +
        (percent?.let { " ($sign${String.format(Locale.US, "%.1f", abs(it))}%)" } ?: "")
}

/**
 * The collection's value over time, worked out from each card's own price history (ValueSeries.kt):
 * the cards owned now at the prices this phone saved, by day, week or month over the last month, six
 * months, year or all of it — where the history starts, never before. Touch the line for a point's
 * value. Below it, the cards that rose and fell most over the same stretch, and each binder's value.
 * The web app's ValueHistoryPage.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValueHistoryScreen(
    onBack: () -> Unit,
    /** The binders; null while they're read. */
    collections: List<Collection>? = null,
    /** False while the binders hold no cards: there's no value to show yet. */
    hasCards: Boolean = true,
    /** The empty page's "Bring in your cards" (the welcome flow's collection step). */
    onBringCards: (() -> Unit)? = null,
    /** A riser's or faller's page, by name. */
    onOpenCard: (String) -> Unit = {},
    /** A binder, by id. */
    onOpenBinder: (String) -> Unit = {}
) {
    val colors = LocalAppColors.current
    val money by Prices.money.collectAsState()
    val tracks by CardPriceHistory.tracks.collectAsState()
    LaunchedEffect(Unit) { runCatching { CardPriceHistory.load() } }
    var range by remember { mutableStateOf(SeriesRange.HALF_YEAR) }
    var picked by remember { mutableStateOf<SeriesPoint?>(null) }
    val holdings = remember(collections) { holdingsOf(collections.orEmpty()) }
    val series = remember(tracks, holdings, range) { tracks?.let { valueSeries(it, holdings, range, LocalDate.now().toEpochDay()) } }
    val points = series?.points.orEmpty()
    val first = points.firstOrNull()
    val last = points.lastOrNull()
    val change = remember(tracks, holdings, first, last) {
        val t = tracks
        if (t != null && first != null && last != null && first.day != last.day) likeForLike(t, holdings, first.day, last.day) else null
    }
    val movers = remember(tracks, holdings, first, last) {
        val t = tracks
        if (t != null && first != null && last != null) valueMovers(t, holdings, first.day, last.day) else null
    }
    val binders = remember(tracks, holdings, first, last) {
        val t = tracks
        if (t != null && first != null && last != null) binderValues(t, holdings, first.day, last.day) else emptyList()
    }
    val summary = if (points.isEmpty()) "" else trendSummary(points, { money.format(it) }, { day(it) })

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("Collection value", style = MaterialTheme.typography.titleLarge, modifier = Modifier.a11yHeading()) },
                navigationIcon = { BackButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            val shown = picked ?: last
            if (series == null && !hasCards) {
                EmptyPrompt(
                    Icons.Filled.BarChart,
                    "No value yet. Once your binders have cards, their prices are saved here once a day.",
                    actions = listOfNotNull(onBringCards?.let { EmptyAction("Bring in your cards", Icons.AutoMirrored.Filled.PlaylistAdd, it) })
                )
                return@Column
            }
            if (series == null || shown == null || first == null || last == null) {
                Text("—", style = NumberStyle(44), color = colors.textDim)
                Text(
                    if (tracks == null || collections == null) "Reading the prices saved on this phone…"
                    else "Your cards' prices are saved once a day, when Home works out their value. The chart starts once the first day's prices are saved.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                    modifier = Modifier.padding(top = 8.dp)
                )
                return@Column
            }
            Text(money.format(shown.usd), style = NumberStyle(44), color = colors.textPrimary)
            Text(
                when {
                    picked != null -> "${day(shown.day)} · ${shown.priced} of ${shown.copies} cards priced"
                    change != null -> "${signed(money, change.change, change.percent)} since ${day(first.day)}"
                    else -> "${shown.priced} cards · saved ${day(shown.day)}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    picked != null || change == null -> colors.textMuted
                    change.change > 0 -> colors.accent
                    change.change < 0 -> colors.error
                    else -> colors.textMuted
                },
                modifier = Modifier.padding(top = 2.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 18.dp)) {
                SeriesRange.entries.forEach { r -> FilterPill(r.label, range == r) { range = r; picked = null } }
            }

            if (points.size < 2) {
                Text(
                    "Prices are saved once a day. Come back tomorrow to see the value change.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                    modifier = Modifier.padding(top = 24.dp)
                )
            } else {
                ValueChart(
                    points, colors.accent, colors.surface3, onPick = { picked = it },
                    modifier = Modifier.padding(top = 18.dp).fillMaxWidth().height(220.dp).semantics { contentDescription = summary }
                )
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text(day(first.day), style = MaterialTheme.typography.labelSmall, color = colors.textDim, modifier = Modifier.weight(1f))
                    Text(series.bucket.label, style = MaterialTheme.typography.labelSmall, color = colors.textDim, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text(day(last.day), style = MaterialTheme.typography.labelSmall, color = colors.textDim, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
                val low = points.minBy { it.usd }
                val high = points.maxBy { it.usd }
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Figure("Low", money.format(low.usd), day(low.day), Modifier.weight(1f))
                    Figure("High", money.format(high.usd), day(high.day), Modifier.weight(1f))
                }
            }
            Text(
                buildString {
                    append("Value history starts ${day(series.historyStart, short = true)}, when this device began saving prices.")
                    if (series.lateCards > 0) append(" ${series.lateCards} ${if (series.lateCards == 1) "card counts" else "cards count"} from the day ${if (series.lateCards == 1) "its" else "their"} price was first saved.")
                    if (series.unpriced > 0) append(" ${series.unpriced} ${if (series.unpriced == 1) "copy has" else "copies have"} no saved price yet and ${if (series.unpriced == 1) "isn't" else "aren't"} counted.")
                },
                style = MaterialTheme.typography.labelMedium,
                color = colors.textDim,
                modifier = Modifier.padding(top = 14.dp)
            )
            movers?.let { m ->
                Text("Risers and fallers", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, modifier = Modifier.padding(top = 28.dp).a11yHeading())
                Text("Since ${day(first.day)}", style = MaterialTheme.typography.labelMedium, color = colors.textDim, modifier = Modifier.padding(top = 4.dp))
                if (m.risers.isEmpty() && m.fallers.isEmpty()) {
                    Text("None of your cards changed price.", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, modifier = Modifier.padding(top = 8.dp))
                }
                if (m.risers.isNotEmpty()) MoverList("Up", m.risers, money, colors.accent, onOpenCard)
                if (m.fallers.isNotEmpty()) MoverList("Down", m.fallers, money, colors.error, onOpenCard)
            }
            if (binders.size > 1) {
                Text("By binder", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, modifier = Modifier.padding(top = 28.dp, bottom = 8.dp).a11yHeading())
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    binders.forEach { b -> BinderRow(b, money, first.day) { onOpenBinder(b.id) } }
                }
            }
            Text(
                "The cards in your binders now (not wishlists) at TCGplayer's market prices on each day" +
                    (if (money.isUsd) "" else ", in ${money.currency.code} at today's exchange rate") +
                    ". Prices are saved on this device, once a day; nothing is filled in for days before that.",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textDim,
                modifier = Modifier.padding(top = 20.dp, bottom = 24.dp)
            )
        }
    }
}

@Composable
private fun MoverList(title: String, movers: List<ValueMover>, money: Money, tint: Color, onOpen: (String) -> Unit) {
    val colors = LocalAppColors.current
    Text(title, style = MaterialTheme.typography.labelMedium, color = colors.textMuted, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        movers.forEach { m ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface)
                    .clickable(role = Role.Button) { onOpen(m.name) }.padding(8.dp)
            ) {
                AsyncImage(
                    model = m.imageUrl.toArtCropUrl(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(width = 52.dp, height = 38.dp).clip(RoundedCornerShape(8.dp)).background(colors.surface3)
                )
                Column(Modifier.weight(1f)) {
                    Text(m.name, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${money.format(m.from)} → ${money.format(m.to)}" +
                            (m.percent?.let { " (${if (it >= 0) "+" else "−"}${String.format(Locale.US, "%.0f", abs(it))}%)" } ?: "") +
                            if (m.copies > 1) " · ×${m.copies}" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted,
                        maxLines = 1
                    )
                }
                Text((if (m.change >= 0) "+" else "−") + money.format(abs(m.change)), style = NumberStyle(17), color = tint)
            }
        }
    }
}

@Composable
private fun BinderRow(b: BinderValue, money: Money, since: Long, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(b.name, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (b.change == 0.0) "No change" else "${signed(money, b.change, null)} since ${day(since, short = true)}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted
            )
        }
        Text(money.format(b.usd, whole = b.usd >= 100), style = NumberStyle(17), color = colors.textPrimary)
    }
}

@Composable
private fun Figure(label: String, value: String, detail: String, modifier: Modifier) {
    val colors = LocalAppColors.current
    Column(modifier.background(colors.surface, RoundedCornerShape(16.dp)).padding(14.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
        Text(value, style = NumberStyle(22), color = colors.textPrimary)
        Text(detail, style = MaterialTheme.typography.labelSmall, color = colors.textDim)
    }
}

/** The values as a line over a soft fill, spaced by date. Touching or dragging picks the nearest point. */
@Composable
private fun ValueChart(points: List<SeriesPoint>, line: Color, grid: Color, onPick: (SeriesPoint?) -> Unit, modifier: Modifier) {
    val firstDay = points.first().day
    val daySpan = (points.last().day - firstDay).coerceAtLeast(1)
    fun xAt(i: Int, width: Float) = (points[i].day - firstDay).toFloat() / daySpan * width
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
