package com.mtgcompanion.app.ui.settings

import androidx.compose.ui.platform.LocalContext
import com.mtgcompanion.app.ui.scan.AutoCameraSetting

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.Prices
import com.mtgcompanion.app.data.ScanCue
import com.mtgcompanion.app.data.ScanSoundMode
import com.mtgcompanion.app.data.ScanSoundSettings
import com.mtgcompanion.app.data.ScanTier
import com.mtgcompanion.app.data.SettingsRepository
import com.mtgcompanion.app.data.parseThreshold
import com.mtgcompanion.app.data.thresholdText
import com.mtgcompanion.app.ui.scan.ScanFeedback
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Play all's buttons, in the order Play all plays them. The web app's ScannerSection.tsx. */
private val PREVIEWS = listOf(
    "Common" to ScanCue(ScanTier.COMMON),
    "Uncommon" to ScanCue(ScanTier.UNCOMMON),
    "Rare" to ScanCue(ScanTier.RARE),
    "Mythic" to ScanCue(ScanTier.MYTHIC),
    "Value" to ScanCue(ScanTier.VALUE),
    "Foil" to ScanCue(ScanTier.COMMON, foil = true)
)

private fun modeNote(mode: ScanSoundMode) = when (mode) {
    ScanSoundMode.RARITY -> "Common: a soft tick. Uncommon: a two-note blip. Rare: a chime. Mythic: a rising flourish. A foil adds a sparkle."
    ScanSoundMode.VALUE -> "A jackpot sting for a card worth the amount below or more; a soft tick for the rest."
    ScanSoundMode.BOTH -> "A jackpot sting for a card worth the amount below or more; the rest sound by rarity."
}

/** Settings → Scanner: the sound and buzz when the scanner recognises a card (ScanFeedback.kt). */
@Composable
internal fun ScannerSection(settingsRepository: SettingsRepository) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val s by settingsRepository.scanSound.collectAsState(initial = ScanSoundSettings())
    val money by Prices.money.collectAsState()
    fun change(next: ScanSoundSettings) = scope.launch { settingsRepository.setScanSound(next) }

    Text(
        "A sound and a buzz when the scanner recognises a card — by its rarity, or a special sound for a valuable one.",
        style = MaterialTheme.typography.bodySmall,
        color = colors.textMuted
    )
    SwitchRow("Scan sounds", "A sound for each card recognised: a tick for a common, up to a flourish for a mythic.", s.on) { change(s.copy(on = it)) }

    var volume by remember(s.volume) { mutableFloatStateOf(s.volume.toFloat()) }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Volume", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Text("${volume.roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = colors.accentLight)
        }
        Slider(
            value = volume,
            onValueChange = { volume = (it / 5).roundToInt() * 5f },
            onValueChangeFinished = {
                val v = volume.roundToInt()
                change(s.copy(volume = v))
                ScanFeedback.preview(ScanCue(ScanTier.RARE), v, vibrate = false)
            },
            valueRange = 0f..100f,
            enabled = s.on,
            colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent, inactiveTrackColor = colors.border),
            modifier = Modifier.semantics { contentDescription = "Scan sound volume, in percent" }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Sound by", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScanSoundMode.entries.forEach { m ->
                    FilterChip(
                        selected = s.mode == m,
                        onClick = { change(s.copy(mode = m)) },
                        label = { Text(m.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.accent,
                            selectedLabelColor = colors.onAccent,
                            labelColor = colors.textMuted,
                            containerColor = colors.surface
                        )
                    )
                }
            }
        }
        Text(modeNote(s.mode), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
    }

    if (s.mode != ScanSoundMode.RARITY) {
        var text by remember(s.threshold) { mutableStateOf(thresholdText(s.threshold)) }
        fun commit() {
            val v = parseThreshold(text)
            if (v != null) change(s.copy(threshold = v)) else text = thresholdText(s.threshold)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Text("Worth at least", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Text(money.currency.symbol.trim(), style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                modifier = Modifier
                    .width(110.dp)
                    .onFocusChanged { if (!it.isFocused) commit() }
                    .semantics { contentDescription = "Value threshold, in ${money.currency.name}s" }
            )
        }
        Text(
            "The printing's price, foil or not, in ${money.currency.code} — ${money.formatLocal(s.threshold)} or more gets the sting.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted
        )
    }

    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    // Kept on this phone only (ScanZoomControl.kt), like the zoom itself.
    val context = LocalContext.current
    val autoCamera by remember { AutoCameraSetting.flow(context) }.collectAsState()
    SwitchRow(
        "Auto zoom and focus",
        "The camera zooms until the card fills the outline, and focuses again on it when it goes soft. Pinching or − / + takes the zoom over until you tap the zoom.",
        autoCamera
    ) { AutoCameraSetting.set(context, it) }
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    SwitchRow("Vibrate", "A buzz for each card, stronger for rarer ones.", s.vibrate) { change(s.copy(vibrate = it)) }
    SwitchRow("Play in silent mode", "Off: the sounds stay quiet while the phone is on silent or vibrate.", s.silent) { change(s.copy(silent = it)) }
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))

    var playingAll by remember { mutableStateOf(0) }
    LaunchedEffect(playingAll) {
        if (playingAll == 0) return@LaunchedEffect
        for ((_, cue) in PREVIEWS) {
            ScanFeedback.preview(cue, s.volume, s.vibrate)
            delay(650)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("Play all", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { playingAll++ }) { Text("Play all", color = colors.accent) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        PREVIEWS.forEach { (label, cue) ->
            FilterChip(
                selected = false,
                onClick = { ScanFeedback.preview(cue, s.volume, s.vibrate) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(labelColor = colors.textPrimary, containerColor = colors.surface)
            )
        }
    }
}

@Composable
private fun SwitchRow(title: String, note: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().toggleable(value = on, role = Role.Switch, onValueChange = onChange).padding(vertical = 8.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            Text(note, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        Switch(
            checked = on,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.onAccent)
        )
    }
}
