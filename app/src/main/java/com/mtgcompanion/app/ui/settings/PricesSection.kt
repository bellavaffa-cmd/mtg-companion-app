package com.mtgcompanion.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.Currencies
import com.mtgcompanion.app.data.Prices
import com.mtgcompanion.app.data.SettingsRepository
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Settings → Prices: the currency prices show in. Prices stay US dollars underneath; this only
 * changes how they read, at the European Central Bank's rate of the day.
 */
@Composable
internal fun PricesSection(settingsRepository: SettingsRepository) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val chosen by Prices.chosen.collectAsState()
    val money by Prices.money.collectAsState()
    val ratesDate by Prices.ratesDate.collectAsState()
    val loading by Prices.loading.collectAsState()
    var picking by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val currency = Currencies.of(chosen)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).clickable { picking = true }.padding(14.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text("Currency", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
            Text("${currency.name} (${currency.code})", style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
        }
        Text(money.format(10.0), style = MaterialTheme.typography.bodyMedium, color = colors.accent, modifier = Modifier.padding(end = 6.dp))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.textDim)
    }
    val date = ratesDate?.let { runCatching { LocalDate.parse(it).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())) }.getOrNull() }
    Text(
        when {
            currency.code == "USD" -> "Prices are TCGplayer's market prices, in US dollars."
            money.currency.code != currency.code && loading -> "Getting today's exchange rate…"
            money.currency.code != currency.code ->
                "Couldn't get the exchange rate yet, so prices show in US dollars for now. They'll switch once the app is online."
            else -> "TCGplayer's US dollar prices, converted at the European Central Bank's rate" + (date?.let { " of $it" } ?: "") + " ($10 = ${money.format(10.0)})."
        },
        style = MaterialTheme.typography.bodySmall,
        color = colors.textMuted
    )
    if (currency.code != "USD" && money.currency.code != currency.code && !loading) {
        TextButton(onClick = { scope.launch { failed = !Prices.refresh(force = true) } }) {
            Text(if (failed) "Still offline — try again" else "Try again", color = colors.accent)
        }
    }

    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            containerColor = colors.surface,
            title = { Text("Show prices in", color = colors.accentLight) },
            text = {
                LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(Currencies.ALL, key = { it.code }) { c ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                                picking = false
                                scope.launch { settingsRepository.setCurrency(c.code) }
                            }.padding(vertical = 2.dp)
                        ) {
                            RadioButton(
                                selected = c.code == chosen,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = colors.accent, unselectedColor = colors.textDim)
                            )
                            Text(c.name, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f).padding(start = 8.dp))
                            Text(c.symbol.trim(), style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, modifier = Modifier.width(44.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = false }) { Text("Close", color = colors.accent) } }
        )
    }
}
