package com.mtgcompanion.app.ui.lifecounter

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mtgcompanion.app.data.PlayerProfile
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.TextDim
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary

private val LIFE_PRESETS = listOf(40, 30, 20)

@Composable
internal fun LifeCounterSettingsScreen(
    settings: LifeCounterSettings,
    profiles: List<PlayerProfile>,
    onUpdate: ((LifeCounterSettings) -> LifeCounterSettings) -> Unit,
    onSetStartingLife: (twoPlayer: Boolean, life: Int) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onResetBackgrounds: () -> Unit,
    onShowTips: () -> Unit,
    onRestartGame: () -> Unit,
    onDismiss: () -> Unit
) {
    var customLifeFor by remember { mutableStateOf<Boolean?>(null) }   // twoPlayer flag while editing
    var confirmReset by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(modifier = Modifier.fillMaxSize().background(Bg)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 10.dp)) {
                Text("SETTINGS", style = MaterialTheme.typography.titleMedium, color = GoldLight, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close settings", tint = Gold) }
            }
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Section("STARTING LIFE")
                StartingLifeRow("Multiplayer", settings.multiplayerStartingLife, onPick = { onSetStartingLife(false, it) }, onCustom = { customLifeFor = false })
                StartingLifeRow("Two players", settings.twoPlayerStartingLife, onPick = { onSetStartingLife(true, it) }, onCustom = { customLifeFor = true })
                Hint("Changes apply to the next game unless nothing has happened in this one yet.")

                Section("GAMEPLAY")
                Toggle("Turn tracker", "Highlights whose turn it is, with a Next Turn button", settings.turnTrackerEnabled) { v -> onUpdate { it.copy(turnTrackerEnabled = v) } }
                Toggle("Game timer", "Total game time and time this turn", settings.gameTimerEnabled) { v -> onUpdate { it.copy(gameTimerEnabled = v) } }
                Toggle("High roll at game start", "Everyone rolls a d20 to decide who goes first", settings.highRollAtStart) { v -> onUpdate { it.copy(highRollAtStart = v) } }
                Toggle("Auto-kill", "Knock players out at 0 life, 10 poison or 21 commander damage", settings.autoKill) { v -> onUpdate { it.copy(autoKill = v) } }
                Toggle("Commander damage costs life", "Adding commander damage also subtracts it from life", settings.commanderDamageCostsLife) { v -> onUpdate { it.copy(commanderDamageCostsLife = v) } }

                Section("PLAYER TILES")
                Toggle("Counters on tile", "Poison, energy, tax and so on, shown once they're above 0", settings.countersOnTile) { v -> onUpdate { it.copy(countersOnTile = v) } }
                Toggle("Keep zero counters", "Counters stay visible after dropping back to 0", settings.keepZeroCounters, enabled = settings.countersOnTile) { v -> onUpdate { it.copy(keepZeroCounters = v) } }
                Text("Pinned counters", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.padding(top = 10.dp))
                Hint("Always shown on every tile, in every game.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState())) {
                    PlayerCounter.entries.forEach { kind ->
                        val pinned = kind in settings.pinnedCounters
                        Chip(kind.label, selected = pinned) {
                            onUpdate { s -> s.copy(pinnedCounters = if (pinned) s.pinnedCounters - kind else s.pinnedCounters + kind) }
                        }
                    }
                }
                Toggle("Commander damage on tile", "Damage received from each commander", settings.showCommanderDamageOnTile) { v -> onUpdate { it.copy(showCommanderDamageOnTile = v) } }
                Toggle("Player names on tile", null, settings.playerNamesOnTile) { v -> onUpdate { it.copy(playerNamesOnTile = v) } }
                Toggle("Shuffle player colors", "Random seat colors each new game", settings.shuffleColors) { v -> onUpdate { it.copy(shuffleColors = v) } }
                Toggle("Tap life to set it", "Tap the number to type an exact total", settings.tapLifeToSet) { v -> onUpdate { it.copy(tapLifeToSet = v) } }
                Toggle("Top/bottom tap areas", "Top adds, bottom subtracts — instead of right adds, left subtracts", settings.verticalTapAreas) { v -> onUpdate { it.copy(verticalTapAreas = v) } }
                Toggle("Minimalist", "Hide the + and − hints", settings.minimalist) { v -> onUpdate { it.copy(minimalist = v) } }
                Toggle("Underline 6 and 9", "So they can't be misread upside down", settings.underlineSixNine) { v -> onUpdate { it.copy(underlineSixNine = v) } }
                Toggle("Low life warning", "Tile flashes red below 10 life", settings.lowLifeWarning) { v -> onUpdate { it.copy(lowLifeWarning = v) } }
                AmountRow("Tap amount", settings.tapAmount) { v -> onUpdate { it.copy(tapAmount = v) } }
                AmountRow("Long-press amount", settings.longPressAmount) { v -> onUpdate { it.copy(longPressAmount = v) } }

                Section("MESSAGES")
                Toggle("Salty messages", "Pick defeat and victory messages from the lists below", settings.saltyMessages) { v -> onUpdate { it.copy(saltyMessages = v) } }
                Toggle("Cycle messages", "Rotate through the list every few seconds", settings.cycleMessages, enabled = settings.saltyMessages) { v -> onUpdate { it.copy(cycleMessages = v) } }
                MessageListEditor("Defeat", settings.defeatMessages) { list -> onUpdate { it.copy(defeatMessages = list) } }
                MessageListEditor("Commander damage defeat", settings.commanderDefeatMessages) { list -> onUpdate { it.copy(commanderDefeatMessages = list) } }
                MessageListEditor("Poison defeat", settings.poisonDefeatMessages) { list -> onUpdate { it.copy(poisonDefeatMessages = list) } }
                MessageListEditor("Victory", settings.victoryMessages) { list -> onUpdate { it.copy(victoryMessages = list) } }

                Section("SAVED PROFILES")
                if (profiles.isEmpty()) {
                    Hint("Save a player's name and color from their options (swipe up on their tile).")
                } else {
                    profiles.forEach { profile ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Box(Modifier.size(12.dp).clip(RoundedCornerShape(50)).background(paletteColor(profile.colorIndex)))
                            Text(profile.name, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(start = 10.dp))
                            TextButton(onClick = { onDeleteProfile(profile.name) }) { Text("DELETE", color = TextMuted) }
                        }
                    }
                }

                Section("RESET")
                LinkRow("Reset player backgrounds", "Clear photos and restore default seat colors", onResetBackgrounds)
                LinkRow("Show tips again", "Replay the swipe and menu tips", onShowTips)
                LinkRow("Reset all settings", "Restore every setting on this screen to its default", { confirmReset = true })

                GoldButton("RESTART GAME", modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) { onRestartGame(); onDismiss() }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    customLifeFor?.let { twoPlayer ->
        CustomLifeDialog(
            initial = if (twoPlayer) settings.twoPlayerStartingLife else settings.multiplayerStartingLife,
            onConfirm = { onSetStartingLife(twoPlayer, it); customLifeFor = null },
            onDismiss = { customLifeFor = null }
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = Bg,
            title = { Text("Reset all settings?", color = GoldLight) },
            text = { Text("Everything on this screen goes back to its default, including the message lists. Your seating and saved profiles are kept.", color = TextPrimary) },
            confirmButton = {
                GoldButton("RESET") {
                    onUpdate { LifeCounterSettings(layoutId = it.layoutId, tipsSeen = true) }
                    confirmReset = false
                }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("CANCEL", color = TextMuted) } }
        )
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(color = BorderColor, modifier = Modifier.padding(top = 20.dp))
    Text(title, style = MaterialTheme.typography.labelLarge, color = Gold, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = TextDim, modifier = Modifier.padding(top = 2.dp))
}

@Composable
private fun Toggle(title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = if (enabled) TextPrimary else TextDim)
            subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = TextDim) }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedThumbColor = Bg, checkedTrackColor = Gold, uncheckedTrackColor = Bg, uncheckedBorderColor = BorderColor)
        )
    }
}

@Composable
private fun StartingLifeRow(label: String, value: Int, onPick: (Int) -> Unit, onCustom: () -> Unit) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.padding(top = 10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
        LIFE_PRESETS.forEach { life -> Chip("$life", selected = value == life) { onPick(life) } }
        Chip(if (value in LIFE_PRESETS) "Custom" else "Custom · $value", selected = value !in LIFE_PRESETS, onClick = onCustom)
    }
}

@Composable
private fun AmountRow(label: String, value: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
        DialogNumberField(text, width = 80) { new ->
            text = new
            new.toIntOrNull()?.takeIf { it in 1..999 }?.let(onChange)
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) Bg else TextPrimary,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Gold else Bg)
            .border(BorderStroke(1.dp, if (selected) Gold else BorderColor), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun LinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextDim)
    }
}

/** A collapsible list of messages with delete buttons and an add field. */
@Composable
private fun MessageListEditor(title: String, messages: List<String>, onChange: (List<String>) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var newMessage by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 10.dp)
        ) {
            Text("$title messages", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
            Text("${messages.size} · ${if (expanded) "HIDE" else "EDIT"}", style = MaterialTheme.typography.labelMedium, color = Gold)
        }
        if (expanded) {
            messages.forEachIndexed { index, message ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(message, style = MaterialTheme.typography.bodySmall, color = TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onChange(messages.filterIndexed { i, _ -> i != index }) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove message", tint = TextDim, modifier = Modifier.size(16.dp))
                    }
                }
            }
            if (messages.isEmpty()) Hint("Empty — a plain default message is used instead.")
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)) {
                OutlinedTextField(
                    value = newMessage,
                    onValueChange = { newMessage = it },
                    placeholder = { Text("Add a message", color = TextDim) },
                    singleLine = true,
                    colors = dialogFieldColors(),
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { onChange(messages + newMessage.trim()); newMessage = "" },
                    enabled = newMessage.isNotBlank()
                ) { Text("ADD", color = Gold) }
            }
        }
    }
}

@Composable
private fun CustomLifeDialog(initial: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial.toString()) }
    val value = text.toIntOrNull()?.takeIf { it in 1..9999 }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Bg,
        title = { Text("Custom starting life", color = GoldLight) },
        text = { DialogNumberField(text, width = 120) { text = it } },
        confirmButton = { GoldButton("SET", enabled = value != null) { onConfirm(value!!) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = TextMuted) } }
    )
}
