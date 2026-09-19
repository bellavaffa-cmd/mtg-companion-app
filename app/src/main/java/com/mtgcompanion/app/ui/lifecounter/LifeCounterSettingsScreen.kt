package com.mtgcompanion.app.ui.lifecounter

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtgcompanion.app.data.PlayerProfile

private val LIFE_PRESETS = listOf(40, 30, 20)

@Composable
internal fun LifeCounterSettingsOverlay(
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
    var customLifeFor by remember { mutableStateOf<Boolean?>(null) }   // two-player flag while editing
    var confirmReset by remember { mutableStateOf(false) }
    var confirmRestart by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        TableOverlay(title = "Settings", onClose = onDismiss, scrim = 0.97f) {
            Box(Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                    Group("Multiplayer starting life")
                    LifeChips(settings.multiplayerStartingLife, onPick = { onSetStartingLife(false, it) }, onCustom = { customLifeFor = false })
                    Group("Two-player starting life")
                    LifeChips(settings.twoPlayerStartingLife, onPick = { onSetStartingLife(true, it) }, onCustom = { customLifeFor = true })
                    Note("Takes effect on the next game, or right away if nothing's happened yet")

                    Group("Gameplay")
                    Check("Turn tracker", "Whose turn it is gets a bigger tile and an End turn button", settings.turnTrackerEnabled) { v -> onUpdate { it.copy(turnTrackerEnabled = v) } }
                    Check("High roll at game start", null, settings.highRollAtStart) { v -> onUpdate { it.copy(highRollAtStart = v) } }
                    Check("Auto-kill", "Kill players from life, poison or commander damage", settings.autoKill) { v -> onUpdate { it.copy(autoKill = v) } }
                    Check("Commander damage", "Commander damage causes players to lose life", settings.commanderDamageCostsLife) { v -> onUpdate { it.copy(commanderDamageCostsLife = v) } }

                    Group("Counters on player card")
                    Check("Regular counters", "Poison, tax, energy and more", settings.countersOnTile) { v -> onUpdate { it.copy(countersOnTile = v) } }
                    Check("Keep zero counters", "Keep counters at 0 visible on the card", settings.keepZeroCounters, enabled = settings.countersOnTile) { v -> onUpdate { it.copy(keepZeroCounters = v) } }
                    Item("Pinned counters", "Chosen counters stay on every card, even between games")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp, bottom = 8.dp).horizontalScroll(rememberScrollState())) {
                        PlayerCounter.entries.forEach { kind ->
                            val pinned = kind in settings.pinnedCounters
                            ValueChip(kind.label, selected = pinned, onClick = {
                                onUpdate { s -> s.copy(pinnedCounters = if (pinned) s.pinnedCounters - kind else s.pinnedCounters + kind) }
                            })
                        }
                    }
                    Check("Commander damage", "Show received commander damage", settings.showCommanderDamageOnTile) { v -> onUpdate { it.copy(showCommanderDamageOnTile = v) } }

                    Group("Player cards")
                    Check("Player names on card", "Show a name on each player card", settings.playerNamesOnTile) { v -> onUpdate { it.copy(playerNamesOnTile = v) } }
                    Check("Shuffle player colors", "Player cards get random colors on start", settings.shuffleColors) { v -> onUpdate { it.copy(shuffleColors = v) } }
                    Check("Set life total overlay", "Tap a player card's life total to enter a new value", settings.tapLifeToSet) { v -> onUpdate { it.copy(tapLifeToSet = v) } }
                    Check("Vertical tap areas", "Top adds, bottom subtracts, instead of right and left", settings.verticalTapAreas) { v -> onUpdate { it.copy(verticalTapAreas = v) } }
                    Check("Minimalist mode", "Hide the + and − hints", settings.minimalist) { v -> onUpdate { it.copy(minimalist = v) } }
                    Check("Underlined 6 and 9", "So they aren't confused when read upside down", settings.underlineSixNine) { v -> onUpdate { it.copy(underlineSixNine = v) } }
                    Check("Low health warning", "Red glow when life is below 10", settings.lowLifeWarning) { v -> onUpdate { it.copy(lowLifeWarning = v) } }
                    AmountRow("Single tap value", settings.tapAmount) { v -> onUpdate { it.copy(tapAmount = v) } }
                    AmountRow("Long tap value", settings.longPressAmount) { v -> onUpdate { it.copy(longPressAmount = v) } }

                    Group("Messages")
                    Check("Salty defeat messages", "Draw defeat and victory messages from the lists below", settings.saltyMessages) { v -> onUpdate { it.copy(saltyMessages = v) } }
                    Check("Cycle messages", "Messages change over time", settings.cycleMessages, enabled = settings.saltyMessages) { v -> onUpdate { it.copy(cycleMessages = v) } }
                    MessageListEditor("Defeat messages", settings.defeatMessages) { list -> onUpdate { it.copy(defeatMessages = list) } }
                    MessageListEditor("Commander defeat messages", settings.commanderDefeatMessages) { list -> onUpdate { it.copy(commanderDefeatMessages = list) } }
                    MessageListEditor("Poison defeat messages", settings.poisonDefeatMessages) { list -> onUpdate { it.copy(poisonDefeatMessages = list) } }
                    MessageListEditor("Victory messages", settings.victoryMessages) { list -> onUpdate { it.copy(victoryMessages = list) } }

                    Group("Profiles")
                    if (profiles.isEmpty()) {
                        Note("Save a player from their card — swipe down on it")
                    } else {
                        profiles.forEach { profile ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Box(Modifier.size(20.dp).clip(CircleShape).background(paletteColor(profile.colorIndex)))
                                TableLabel(profile.name, 26.sp, modifier = Modifier.weight(1f).padding(start = 12.dp), maxLines = 1)
                                TableLabel("Delete", 22.sp, color = TableColors.Accent, modifier = Modifier.clickable { onDeleteProfile(profile.name) })
                            }
                        }
                    }

                    Group("Customize")
                    Link("Reset player backgrounds", "Restore default player background colors", onResetBackgrounds)
                    Link("Show tips again", "Replay the swipe and menu tips", onShowTips)
                    Link("Reset settings", "Put every setting here back to its default", { confirmReset = true })

                    Spacer(Modifier.height(96.dp))
                }
                PillButton(
                    "Restart game",
                    TableColors.Accent,
                    onClick = { confirmRestart = true },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp).popIn(delayMillis = 120)
                )
            }
        }

        customLifeFor?.let { twoPlayer ->
            CustomLifeOverlay(
                initial = if (twoPlayer) settings.twoPlayerStartingLife else settings.multiplayerStartingLife,
                onConfirm = { onSetStartingLife(twoPlayer, it); customLifeFor = null },
                onDismiss = { customLifeFor = null }
            )
        }
        if (confirmReset) {
            ConfirmOverlay(
                text = "Reset all settings? Seating and saved profiles are kept.",
                confirmLabel = "Reset",
                onConfirm = { onUpdate { LifeCounterSettings(layoutId = it.layoutId, tipsSeen = true) } },
                onDismiss = { confirmReset = false }
            )
        }
        if (confirmRestart) {
            ConfirmOverlay(
                text = "Are you sure you want to restart the game?",
                confirmLabel = "Restart",
                onConfirm = { onRestartGame(); onDismiss() },
                onDismiss = { confirmRestart = false }
            )
        }
    }
}

@Composable
private fun Group(title: String) {
    TableLabel(title, 34.sp, modifier = Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 8.dp))
}

@Composable
private fun Note(text: String) {
    TableLabel(text, 17.sp, color = TableColors.TextMuted, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun Item(title: String, subtitle: String?) {
    Column(Modifier.padding(vertical = 4.dp)) {
        TableLabel(title, 26.sp)
        subtitle?.let { TableLabel(it, 16.sp, color = TableColors.TextMuted) }
    }
}

@Composable
private fun Check(title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(vertical = 7.dp)
    ) {
        RoundCheck(checked = checked, modifier = Modifier.padding(end = 14.dp))
        Column(Modifier.weight(1f)) {
            TableLabel(title, 26.sp, color = if (enabled) Color.White else TableColors.Line)
            subtitle?.let { TableLabel(it, 16.sp, color = TableColors.TextMuted) }
        }
    }
}

@Composable
private fun LifeChips(value: Int, onPick: (Int) -> Unit, onCustom: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LIFE_PRESETS.forEach { life -> ValueChip("$life", selected = value == life, onClick = { onPick(life) }) }
        ValueChip(if (value in LIFE_PRESETS) "✎" else "✎ $value", selected = value !in LIFE_PRESETS, onClick = onCustom)
    }
}

@Composable
private fun AmountRow(label: String, value: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        TableLabel(label, 26.sp, modifier = Modifier.weight(1f))
        NumberField(text, width = 80) { new ->
            text = new
            new.toIntOrNull()?.takeIf { it in 1..999 }?.let(onChange)
        }
    }
}

@Composable
private fun Link(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)) {
        TableLabel(title, 26.sp)
        TableLabel(subtitle, 16.sp, color = TableColors.TextMuted)
    }
    HorizontalDivider(color = TableColors.Line.copy(alpha = 0.4f))
}

/** A collapsible list of messages with remove buttons and an add field. */
@Composable
private fun MessageListEditor(title: String, messages: List<String>, onChange: (List<String>) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var newMessage by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 8.dp)) {
            TableLabel(title, 26.sp, modifier = Modifier.weight(1f))
            TableLabel("${messages.size}  ${if (expanded) "▲" else "▼"}", 22.sp, color = TableColors.TextMuted)
        }
        if (expanded) {
            messages.forEachIndexed { index, message ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(TableColors.Surface)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    TableLabel(message, 22.sp, modifier = Modifier.weight(1f))
                    TableLabel("✕", 22.sp, color = TableColors.Accent, modifier = Modifier.clickable { onChange(messages.filterIndexed { i, _ -> i != index }) })
                }
            }
            if (messages.isEmpty()) Note("Empty — a plain message is used instead")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(TableColors.SurfaceRaised)
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    if (newMessage.isEmpty()) TableLabel("Add a message", 22.sp, color = TableColors.TextMuted)
                    BasicTextField(
                        value = newMessage,
                        onValueChange = { newMessage = it },
                        singleLine = true,
                        textStyle = tableText(22.sp),
                        cursorBrush = SolidColor(TableColors.Yellow),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                PillButton("Add", TableColors.Yellow, textColor = Color.Black, enabled = newMessage.isNotBlank(), onClick = {
                    onChange(messages + newMessage.trim())
                    newMessage = ""
                })
            }
        }
    }
}

@Composable
private fun CustomLifeOverlay(initial: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial.toString()) }
    val value = text.toIntOrNull()?.takeIf { it in 1..9999 }
    ConfirmLikeOverlay(onDismiss = onDismiss) {
        TableLabel("Custom starting life", 34.sp)
        Box(Modifier.padding(vertical = 16.dp)) { NumberField(text, width = 140) { text = it } }
        PillButton("Set", TableColors.Accent, enabled = value != null, onClick = { onConfirm(value!!) })
    }
}

@Composable
private fun ConfirmLikeOverlay(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(onClick = onDismiss)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .popIn(easing = TableMotion.Pop)
                // Swallow taps on the content itself so only a tap outside it dismisses.
                .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { }
                .padding(28.dp)
        ) { content() }
    }
}
