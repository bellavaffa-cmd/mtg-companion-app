package com.mtgcompanion.app.ui.collection

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mtgcompanion.app.data.AlertHit
import com.mtgcompanion.app.data.CollectionEntry
import com.mtgcompanion.app.data.Money
import com.mtgcompanion.app.data.PriceTrack
import com.mtgcompanion.app.data.TARGET_PERCENTS
import com.mtgcompanion.app.data.TargetOptions
import com.mtgcompanion.app.data.shortPrice
import com.mtgcompanion.app.data.targetFromPercent
import com.mtgcompanion.app.data.targetLine
import com.mtgcompanion.app.data.weekDrop
import com.mtgcompanion.app.data.yearLow
import com.mtgcompanion.app.ui.common.KeepSystemBarsHidden
import com.mtgcompanion.app.ui.common.rememberMoney
import com.mtgcompanion.app.ui.theme.LocalAppColors
import java.time.LocalDate
import java.util.Locale

// The Wishlist's price targets on screen: the "Under your price" box, the target sheet ("Tell me
// when it's cheaper") and "Set targets for all…". The rules are data/WishlistTargets.kt. Mirrors the
// web app's src/collection/WishlistTargetsUi.tsx.

private fun Money.short(usd: Double): String = shortPrice(usd) { v, whole -> format(v, whole) }

/** Cards under their target now, with a way to buy them and "Got it" to put the box away until the next drop. */
@Composable
fun UnderYourPriceBox(hits: List<AlertHit>, onBuy: () -> Unit, onGotIt: () -> Unit, modifier: Modifier = Modifier) {
    if (hits.isEmpty()) return
    val app = LocalAppColors.current
    val money = rememberMoney()
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(app.accent.copy(alpha = 0.14f))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Notifications, contentDescription = null, tint = app.accent, modifier = Modifier.size(18.dp))
            Text("Under your price", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = app.textPrimary, modifier = Modifier.semantics { heading() })
        }
        hits.take(4).forEach { hit ->
            Text(
                "${hit.watch.entry.name} is ${money.format(hit.price)}, below your ${money.short(hit.watch.target)}.",
                style = MaterialTheme.typography.bodyMedium,
                color = app.textPrimary
            )
        }
        if (hits.size > 4) Text("and ${hits.size - 4} more", style = MaterialTheme.typography.bodySmall, color = app.textMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            PillButton("Buy at TCGplayer", primary = true, onClick = onBuy)
            PillButton("Got it", primary = false, onClick = onGotIt)
        }
    }
}

@Composable
private fun PillButton(label: String, primary: Boolean, modifier: Modifier = Modifier, height: Int = 36, onClick: () -> Unit) {
    val app = LocalAppColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(height.dp)
            .clip(RoundedCornerShape(if (height > 40) 14.dp else 12.dp))
            .background(if (primary) app.accent else app.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (primary) FontWeight.ExtraBold else FontWeight.SemiBold, color = if (primary) app.onAccent else app.textPrimary)
    }
}

/** The Wishlist's two buttons at the foot: "Set targets for all…" and "Buy these cards". */
@Composable
fun WishlistFoot(onSetAll: () -> Unit, onBuyAll: () -> Unit) {
    val app = LocalAppColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().background(app.bg).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp)
    ) {
        PillButton("Set targets for all…", primary = false, height = 48, modifier = Modifier.weight(1f), onClick = onSetAll)
        PillButton("Buy these cards", primary = true, height = 48, modifier = Modifier.weight(1f), onClick = onBuyAll)
    }
}

/** A wishlist row's line under the name: "Target $70 · dropped 12% this week", "$3.40 to go"… */
fun wishlistTargetLine(entry: CollectionEntry, price: Double?, track: PriceTrack?, money: Money): String {
    val target = entry.priceAlert
    val hit = target != null && price != null && price <= target
    val dropped = if (hit) weekDrop(track, entry.alertFoilOnly == true, LocalDate.now().toEpochDay()) else null
    return targetLine(target, price, dropped) { v, whole -> money.format(v, whole) }
}

/**
 * The target sheet: "Tell me when it's cheaper". [now]: the card's price now, plain and foil (US
 * dollars). Saves the target with its options, or takes it off ([onSave] with null).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TargetSheet(
    entry: CollectionEntry,
    now: Pair<Double?, Double?>?,
    track: PriceTrack?,
    onSave: (Double?, TargetOptions) -> Unit,
    onDismiss: () -> Unit
) {
    val app = LocalAppColors.current
    val context = LocalContext.current
    val money = rememberMoney()
    val decimals = money.currency.decimals
    fun local(usd: Double?): String = usd?.let { String.format(Locale.US, "%.${decimals}f", money.toLocal(it)) }.orEmpty()
    // A new target: any printing counts, as the sheet offers it; one set before keeps what it had.
    var anyPrinting by remember { mutableStateOf(if (entry.priceAlert != null) entry.alertAnyPrinting == true else entry.alertAnyPrinting ?: true) }
    var foilOnly by remember { mutableStateOf(entry.alertFoilOnly == true) }
    fun price(foil: Boolean): Double? = if (foil) now?.second else now?.first
    var text by remember { mutableStateOf(local(entry.priceAlert ?: targetFromPercent(price(foilOnly), 10))) }
    val current = price(foilOnly)
    val low = yearLow(track, foilOnly)
    val value = text.toDoubleOrNull()?.takeIf { it > 0 }?.let { kotlin.math.round(money.toUsd(it) * 10_000) / 10_000 }
    val options = TargetOptions(anyPrinting, foilOnly)
    // Notifications need the user's OK (Android 13+); asked the first time a target is set.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = app.surface,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(50)).background(app.surface3))
        }
    ) {
        KeepSystemBarsHidden()
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, bottom = 24.dp)
        ) {
            Text("Tell me when it's cheaper", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = app.textPrimary, modifier = Modifier.semantics { heading() })
            Text(
                listOfNotNull(entry.name, current?.let { "now ${money.format(it)}" }, low?.let { "lowest this year ${money.format(it)}" }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = app.textMuted
            )
            Text("ALERT ME UNDER", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = app.textMuted)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(app.bg)
                    .border(BorderStroke(1.dp, app.accent), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp)
            ) {
                val big = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold)
                if (!money.currency.after) Text(money.currency.symbol, style = big, color = app.textMuted)
                BasicTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() || it == '.' } },
                    singleLine = true,
                    textStyle = big.copy(color = app.textPrimary),
                    cursorBrush = SolidColor(app.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                if (money.currency.after) Text(money.currency.symbol, style = big, color = app.textMuted)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TARGET_PERCENTS.forEach { p ->
                    val t = targetFromPercent(current, p) ?: return@forEach
                    TargetChip("$p% off", selected = text == local(t)) { text = local(t) }
                }
                if (low != null) TargetChip("Year's low", selected = text == local(low)) { text = local(low) }
            }
            TargetCheck("Any printing counts", anyPrinting) { anyPrinting = it }
            TargetCheck("Foil only", foilOnly) { foilOnly = it }
            Text(
                "Prices are checked once a day, like your price alerts. You get one notification when it goes under, then it waits for the next drop.",
                style = MaterialTheme.typography.bodySmall,
                color = app.textMuted
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (entry.priceAlert != null) PillButton("Remove", primary = false, height = 48) { onSave(null, options) }
                else PillButton("Cancel", primary = false, height = 48, onClick = onDismiss)
                PillButton(
                    value?.let { "Alert me under ${money.short(it)}" } ?: "Alert me under…",
                    primary = true,
                    height = 48,
                    modifier = Modifier.weight(1f)
                ) {
                    val usd = value ?: return@PillButton
                    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    onSave(usd, options)
                }
            }
        }
    }
}

@Composable
private fun TargetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val app = LocalAppColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(if (selected) app.accent else app.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, color = if (selected) app.onAccent else app.textPrimary)
    }
}

@Composable
private fun TargetCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val app = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onChange(!checked) }
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange, colors = CheckboxDefaults.colors(checkedColor = app.accent, checkmarkColor = app.onAccent))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = app.textPrimary, modifier = Modifier.weight(1f))
    }
}

/** "Set targets for all…": so much off today's price, for every card without a target. [count]: how many that'd set, by percent. */
@Composable
fun SetTargetsForAllDialog(count: (Int) -> Int, onSet: (Int) -> Unit, onDismiss: () -> Unit) {
    val app = LocalAppColors.current
    val context = LocalContext.current
    var percent by remember { mutableIntStateOf(TARGET_PERCENTS.first()) }
    val n = count(percent)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = app.surface2,
        title = { Text("Set targets for all", color = app.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Tell me when each card without a target is this much under today's price:", color = app.textMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TARGET_PERCENTS.forEach { p -> TargetChip("$p% off", selected = percent == p) { percent = p } }
                }
                Text(
                    if (n == 0) "Every card with a price already has a target." else "Any printing counts. You can change each one after.",
                    style = MaterialTheme.typography.labelMedium,
                    color = app.textDim
                )
            }
        },
        confirmButton = {
            TextButton(enabled = n > 0, onClick = {
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                onSet(percent)
            }) { Text(if (n == 1) "Set 1 target" else "Set $n targets", color = app.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = app.textMuted) } }
    )
}
