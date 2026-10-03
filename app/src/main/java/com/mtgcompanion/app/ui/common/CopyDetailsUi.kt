package com.mtgcompanion.app.ui.common

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mtgcompanion.app.data.CARD_CONDITIONS
import com.mtgcompanion.app.data.CardPriceHistory
import com.mtgcompanion.app.data.CollectionEntry
import com.mtgcompanion.app.data.CARD_LANGUAGES
import com.mtgcompanion.app.data.Money
import com.mtgcompanion.app.data.PriceKind
import com.mtgcompanion.app.data.PriceTrack
import com.mtgcompanion.app.data.conditionName
import com.mtgcompanion.app.data.kindsIn
import com.mtgcompanion.app.data.languageName
import com.mtgcompanion.app.data.priceMove
import com.mtgcompanion.app.data.priceSeries
import com.mtgcompanion.app.ui.theme.LocalAppColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** A small label on a binder row or a trade line: a copy's condition ("LP") or language ("JA"). */
@Composable
fun CopyBadge(text: String, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = colors.accentLight,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.surface2)
            .padding(horizontal = 5.dp, vertical = 1.dp)
    )
}

/** A price as its kind is shown: dollars in the chosen currency, Cardmarket's in euros. */
private fun formatPrice(money: Money, kind: PriceKind, value: Double): String =
    if (kind == PriceKind.EUR) "€" + String.format(Locale.US, "%,.2f", value) else money.format(value)

private fun dayLabel(day: Long, withYear: Boolean): String =
    LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern(if (withYear) "d MMM yyyy" else "d MMM", Locale.getDefault()))

/**
 * A card's price over time as this phone has noted it (see CardPriceHistory): the range, how much it
 * moved, and a line. Before there's a history it says how one fills in. [preferFoil]: start on the
 * foil price, for copies that are all foil.
 */
@Composable
fun PriceHistoryPanel(scryfallId: String, modifier: Modifier = Modifier, preferFoil: Boolean = false) {
    val colors = LocalAppColors.current
    val money = rememberMoney()
    val tracks by CardPriceHistory.tracks.collectAsState()
    LaunchedEffect(Unit) { CardPriceHistory.load() }
    val track: PriceTrack? = tracks?.get(scryfallId)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Price history (on this phone)", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
        if (tracks == null) {
            Text("Loading…", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            return@Column
        }
        val kinds = track?.let { kindsIn(it) }.orEmpty()
        if (track == null || kinds.isEmpty()) {
            Text(
                "Nothing yet. This phone notes the card's price each day it checks your cards' prices, so the chart fills in day by day.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted
            )
            return@Column
        }
        var picked by remember(scryfallId) { mutableStateOf(if (preferFoil && PriceKind.USD_FOIL in kinds) PriceKind.USD_FOIL else kinds.first()) }
        val kind = if (picked in kinds) picked else kinds.first()
        if (kinds.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                kinds.forEach { k -> PillChip(k.label, k == kind, { picked = k }) }
            }
        }
        val series = priceSeries(track, kind)
        val move = priceMove(series)
        if (move == null || series.size < 2 || move.fromDay == move.toDay) {
            val price = series.lastOrNull()?.second
            Text(
                (price?.let { "${formatPrice(money, kind, it)} on ${dayLabel(track.lastDay, false)}. " } ?: "") +
                    "One day noted so far — the chart fills in day by day.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted
            )
            return@Column
        }
        val withYear = LocalDate.ofEpochDay(move.fromDay).year != LocalDate.ofEpochDay(move.toDay).year
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "${dayLabel(move.fromDay, withYear)} – ${dayLabel(move.toDay, withYear)}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted,
                modifier = Modifier.weight(1f)
            )
            val up = move.change >= 0
            Text(
                formatPrice(money, kind, move.from) + " → " + formatPrice(money, kind, move.to) +
                    (move.percent?.let { " (" + (if (up) "+" else "−") + String.format(Locale.US, "%.1f", abs(it)) + "%)" } ?: ""),
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    abs(move.change) < 0.005 -> colors.textMuted
                    up -> colors.success
                    else -> colors.error
                },
                maxLines = 1
            )
        }
        PriceStepChart(series, line = colors.accent, grid = colors.border, modifier = Modifier.fillMaxWidth().height(110.dp))
    }
}

/** Prices as steps — each holds until the next one noted — over a soft fill, spaced by day. */
@Composable
private fun PriceStepChart(series: List<Pair<Long, Double>>, line: Color, grid: Color, modifier: Modifier) {
    Canvas(modifier) {
        val firstDay = series.first().first
        val span = (series.last().first - firstDay).coerceAtLeast(1).toFloat()
        val lo = series.minOf { it.second }
        val hi = series.maxOf { it.second }
        val range = (hi - lo).takeIf { it > 0 } ?: maxOf(hi * 0.1, 0.5)
        val bottom = lo - range * 0.12
        val top = hi + range * 0.12
        fun x(day: Long) = (day - firstDay) / span * size.width
        fun y(v: Double) = (size.height * (1 - (v - bottom) / (top - bottom))).toFloat()
        for (g in 0..2) {
            val gy = size.height * g / 2f
            drawLine(grid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
        }
        val path = Path().apply {
            series.forEachIndexed { i, (day, v) ->
                if (i == 0) moveTo(x(day), y(v))
                else {
                    lineTo(x(day), y(series[i - 1].second))
                    lineTo(x(day), y(v))
                }
            }
        }
        val fill = Path().apply {
            addPath(path)
            lineTo(x(series.last().first), size.height)
            lineTo(x(firstDay), size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.25f), line.copy(alpha = 0f))))
        drawPath(path, line, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(line, radius = 4.dp.toPx(), center = Offset(x(series.last().first), y(series.last().second)))
    }
}

/**
 * A binder card's own details in its enlarged view: one row saying what's set, opening the editor —
 * condition and language ([onCopyDetails]; null on a wishlist, whose cards aren't owned), the "tell
 * me when it rises above" alert ([onAlertAbove]; owned binders only) and the price history.
 */
@Composable
fun CopyDetailsButton(
    entry: CollectionEntry,
    onCopyDetails: ((condition: String?, language: String?) -> Unit)?,
    onAlertAbove: ((Double?) -> Unit)?,
    /** Today's price, to start the alert from. */
    price: Double?,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    val money = rememberMoney()
    var open by remember { mutableStateOf(false) }
    val said = listOfNotNull(
        entry.condition?.let(::conditionName),
        entry.language?.let(::languageName),
        entry.priceAlertAbove?.let { "alert over ${money.format(it)}" }
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable { open = true }
            .padding(horizontal = 24.dp, vertical = 10.dp)
    ) {
        Icon(Icons.Filled.Tune, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
        Text(
            said.joinToString(" · ").ifEmpty {
                if (onCopyDetails != null) "Condition, language, price alert & history" else "Price history"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (said.isEmpty()) colors.textMuted else colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 10.dp)
        )
        Text(if (onCopyDetails != null) "Edit" else "Show", style = MaterialTheme.typography.labelMedium, color = colors.accent)
    }
    if (open) CopyDetailsDialog(entry, onCopyDetails, onAlertAbove, price) { open = false }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CopyDetailsDialog(
    entry: CollectionEntry,
    onCopyDetails: ((String?, String?) -> Unit)?,
    onAlertAbove: ((Double?) -> Unit)?,
    price: Double?,
    onDismiss: () -> Unit
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val money = rememberMoney()
    val decimals = money.currency.decimals
    // Shown at once, saved as they're tapped; the alert is saved with Done.
    var condition by remember { mutableStateOf(entry.condition) }
    var language by remember { mutableStateOf(entry.language) }
    var alertText by remember {
        mutableStateOf(entry.priceAlertAbove?.let { String.format(Locale.US, "%.${decimals}f", money.toLocal(it)) }.orEmpty())
    }
    val alertUsd = alertText.toDoubleOrNull()?.takeIf { it > 0 }?.let { money.toUsd(it) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun saveAlert() {
        val save = onAlertAbove ?: return
        val next = alertUsd?.let { kotlin.math.round(it * 10_000) / 10_000 }
        val had = entry.priceAlertAbove
        if (next == had || (next != null && had != null && abs(next - had) < 0.0001)) return
        if (next != null && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        save(next)
    }
    AlertDialog(
        onDismissRequest = { saveAlert(); onDismiss() },
        containerColor = colors.surface,
        title = { Text(entry.name, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (onCopyDetails != null) {
                    Text("Condition · every copy here", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PillChip("Not set", condition == null, { condition = null; onCopyDetails(null, language) })
                        CARD_CONDITIONS.forEach { c ->
                            PillChip(c, condition == c, { condition = c; onCopyDetails(c, language) })
                        }
                    }
                    condition?.let { Text(conditionName(it), style = MaterialTheme.typography.labelSmall, color = colors.textDim) }
                    Text("Language", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PillChip("Not set", language == null, { language = null; onCopyDetails(condition, null) })
                        CARD_LANGUAGES.forEach { l ->
                            PillChip(l.uppercase(), language == l, { language = l; onCopyDetails(condition, l) })
                        }
                    }
                    language?.let { Text(languageName(it), style = MaterialTheme.typography.labelSmall, color = colors.textDim) }
                }
                if (onAlertAbove != null) {
                    Text(
                        (price?.let { "It's ${money.format(it)} now. " } ?: "") + "Tell me when it rises to (${money.currency.code}):",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textMuted
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = alertText,
                            onValueChange = { v -> alertText = v.filter { it.isDigit() || it == '.' } },
                            singleLine = true,
                            placeholder = { Text("No alert", color = colors.textDim) },
                            prefix = if (money.currency.after) null else ({ Text(money.currency.symbol, color = colors.textMuted) }),
                            suffix = if (money.currency.after) ({ Text(money.currency.symbol, color = colors.textMuted) }) else null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        if (alertText.isNotEmpty()) {
                            TextButton(onClick = { alertText = "" }) { Text("Clear", color = colors.textMuted) }
                        }
                    }
                    Text(
                        "Checked a few times a day; you'll get a notification. Non-foil price, or the foil price when every copy here is foil.",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textDim
                    )
                }
                PriceHistoryPanel(
                    entry.scryfallId,
                    modifier = Modifier.padding(top = 4.dp),
                    preferFoil = entry.quantity <= 0 && entry.foilQuantity > 0
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { saveAlert(); onDismiss() }) { Text("Done", color = colors.accent) }
        }
    )
}
