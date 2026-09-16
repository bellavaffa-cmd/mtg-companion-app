package com.mtgcompanion.app.ui.lifecounter

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.draggable2D
import androidx.compose.foundation.gestures.rememberDraggable2DState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Castle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.PlayerProfile
import com.mtgcompanion.app.ui.common.ManaSymbol
import com.mtgcompanion.app.ui.theme.Cinzel
import kotlin.math.abs
import kotlin.math.min

/**
 * Bright per-seat colors, chosen so dark life numbers read clearly on every one of them — the
 * bold-tile look this screen is modeled on. Order matters: saved profiles store an index into it.
 * Size must match [LifeCounterViewModel.PLAYER_COLOR_COUNT].
 */
val PlayerPalette = listOf(
    Color(0xFFF2B233), Color(0xFF4DA3F0), Color(0xFFB07CF2), Color(0xFFF2605A), Color(0xFF5CC77A),
    Color(0xFFF27AB8), Color(0xFF3CCFCF), Color(0xFFFF9A45), Color(0xFF9DAAFF), Color(0xFFCCD636)
)

fun paletteColor(index: Int): Color = PlayerPalette[Math.floorMod(index, PlayerPalette.size)]

enum class TokenKind { MONARCH, INITIATIVE }

private enum class TileFace { LIFE, COMMANDER_DAMAGE, OPTIONS }

/** Everything a tile can do to its own player, already bound to that player's id. */
class PlayerTileActions(
    val adjustLife: (Int) -> Unit,
    val setLife: (Int) -> Unit,
    val adjustCommanderDamage: (CommanderSource, Int) -> Unit,
    val adjustCounter: (PlayerCounter, Int) -> Unit,
    val adjustMana: (String, Int) -> Unit,
    val adjustTax: (slot: Int, delta: Int) -> Unit,
    val setHasPartner: (Boolean) -> Unit,
    val kill: () -> Unit,
    val revive: () -> Unit,
    val setColor: (Int) -> Unit,
    val setName: (String) -> Unit,
    val setVictoryMessage: (String) -> Unit,
    val setDefeatMessage: (String) -> Unit,
    val setBackgroundImage: (String?) -> Unit,
    val saveProfile: () -> Unit,
    val loadProfile: (PlayerProfile) -> Unit
)

/**
 * One player's tile, laid out from that player's own point of view — the caller turns it to face
 * their seat. Swipe sideways for commander damage received, up or down for counters and options.
 * [defeatMessage] is non-null while they're out, [victoryMessage] while they're the last one left.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerTile(
    player: PlayerLife,
    opponents: List<PlayerLife>,
    settings: LifeCounterSettings,
    isActiveTurn: Boolean,
    isMonarch: Boolean,
    hasInitiative: Boolean,
    defeatMessage: String?,
    victoryMessage: String?,
    profiles: List<PlayerProfile>,
    actions: PlayerTileActions,
    onTokenTap: (TokenKind) -> Unit,
    modifier: Modifier = Modifier
) {
    val color = paletteColor(player.colorIndex)
    val hasImage = player.backgroundImageUri != null
    val ink = if (hasImage || color.luminance() < 0.28f) Color.White else Color.Black
    var face by remember(player.id) { mutableStateOf(TileFace.LIFE) }
    var showKeypad by remember(player.id) { mutableStateOf(false) }
    var dragAccum by remember { mutableStateOf(Offset.Zero) }
    val draggableState = rememberDraggable2DState { delta -> dragAccum += delta }

    Box(
        modifier = modifier
            .padding(3.dp)
            .clip(RoundedCornerShape(18.dp))
            .let { if (!hasImage) it.background(color) else it.background(Color.Black) }
            .border(
                BorderStroke(if (isActiveTurn) 4.dp else 0.dp, if (isActiveTurn) Color.White else Color.Transparent),
                RoundedCornerShape(18.dp)
            )
            .draggable2D(
                state = draggableState,
                onDragStarted = { dragAccum = Offset.Zero },
                onDragStopped = {
                    val (dx, dy) = dragAccum
                    if (abs(dx) > abs(dy) && abs(dx) > 70f) {
                        face = if (face == TileFace.COMMANDER_DAMAGE) TileFace.LIFE else TileFace.COMMANDER_DAMAGE
                    } else if (abs(dy) > 70f) {
                        face = if (face == TileFace.OPTIONS) TileFace.LIFE else TileFace.OPTIONS
                    }
                    dragAccum = Offset.Zero
                }
            )
    ) {
        if (hasImage) {
            AsyncImage(
                model = player.backgroundImageUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        }

        Crossfade(targetState = face, label = "tileFace") { current ->
            when (current) {
                TileFace.LIFE -> LifeFace(
                    life = player.life,
                    ink = ink,
                    settings = settings,
                    onAdjust = actions.adjustLife,
                    onOpenKeypad = { showKeypad = true }
                )
                TileFace.COMMANDER_DAMAGE -> Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))) {
                    CommanderDamageFace(
                        player = player,
                        opponents = opponents,
                        onAdjust = actions.adjustCommanderDamage,
                        onClose = { face = TileFace.LIFE }
                    )
                }
                TileFace.OPTIONS -> Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))) {
                    OptionsFace(
                        player = player,
                        profiles = profiles,
                        autoKill = settings.autoKill,
                        actions = actions,
                        onClose = { face = TileFace.LIFE }
                    )
                }
            }
        }

        if (face == TileFace.LIFE) {
            LowLifeWarning(visible = settings.lowLifeWarning && player.life in 1..9 && defeatMessage == null)

            if (settings.playerNamesOnTile) {
                Text(
                    player.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .widthIn(max = 110.dp)
                        .clip(RoundedCornerShape(50))
                        .background(ink.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            if (settings.showCommanderDamageOnTile) {
                ReceivedCommanderDamage(
                    player = player,
                    opponents = opponents,
                    ink = ink,
                    onClick = { face = TileFace.COMMANDER_DAMAGE },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                )
            }

            if (isMonarch || hasInitiative) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 34.dp)
                ) {
                    if (isMonarch) TokenBadge(TokenKind.MONARCH, onClick = { onTokenTap(TokenKind.MONARCH) })
                    if (hasInitiative) TokenBadge(TokenKind.INITIATIVE, onClick = { onTokenTap(TokenKind.INITIATIVE) })
                }
            }

            TileCounters(
                player = player,
                settings = settings,
                ink = ink,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp, start = 8.dp, end = 8.dp)
            )

            when {
                defeatMessage != null -> Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                        Text(defeatMessage, style = MaterialTheme.typography.titleMedium, color = Color.White, textAlign = TextAlign.Center)
                        TextButton(
                            onClick = actions.revive,
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)), RoundedCornerShape(50))
                        ) { Text("REVIVE", color = Color.White, style = MaterialTheme.typography.labelMedium) }
                    }
                }
                victoryMessage != null -> Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                    Text(
                        victoryMessage,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        }
    }

    if (showKeypad) {
        LifeKeypadDialog(
            initial = player.life,
            onConfirm = { actions.setLife(it); showKeypad = false },
            onDismiss = { showKeypad = false }
        )
    }
}

/**
 * Big life total over two tap areas: side by side (left subtracts, right adds) by default, or
 * stacked (top adds, bottom subtracts). The number scales to fit the tile, whichever of its width
 * or height runs out first.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LifeFace(
    life: Int,
    ink: Color,
    settings: LifeCounterSettings,
    onAdjust: (Int) -> Unit,
    onOpenKeypad: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val hintAlpha = if (settings.minimalist) 0f else 0.35f

    @Composable
    fun TapArea(delta: Int, modifier: Modifier, hintAlignment: Alignment) {
        Box(
            modifier = modifier.combinedClickable(
                onClick = { onAdjust(delta * settings.tapAmount) },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onAdjust(delta * settings.longPressAmount)
                }
            ),
            contentAlignment = hintAlignment
        ) {
            Icon(
                if (delta > 0) Icons.Filled.Add else Icons.Filled.Remove,
                contentDescription = if (delta > 0) "Add ${settings.tapAmount} (hold for ${settings.longPressAmount})"
                else "Subtract ${settings.tapAmount} (hold for ${settings.longPressAmount})",
                tint = ink.copy(alpha = hintAlpha),
                modifier = Modifier.padding(18.dp).size(26.dp)
            )
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (settings.verticalTapAreas) {
            Column(Modifier.fillMaxSize()) {
                TapArea(+1, Modifier.weight(1f).fillMaxWidth(), Alignment.TopCenter)
                TapArea(-1, Modifier.weight(1f).fillMaxWidth(), Alignment.BottomCenter)
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                TapArea(-1, Modifier.weight(1f).fillMaxSize(), Alignment.CenterStart)
                TapArea(+1, Modifier.weight(1f).fillMaxSize(), Alignment.CenterEnd)
            }
        }

        val text = life.toString()
        val byHeight = maxHeight * 0.58f
        val byWidth = maxWidth / (text.length * 0.8f + 0.4f)
        val fontSize = with(density) { min(byHeight.toPx(), byWidth.toPx()).toSp() }
        Text(
            lifeText(text, settings.underlineSixNine),
            fontFamily = Cinzel,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            color = ink,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.Center)
                .let { if (settings.tapLifeToSet) it.clip(RoundedCornerShape(24.dp)).clickable(onClick = onOpenKeypad) else it }
                .padding(horizontal = 8.dp)
        )
    }
}

/** 6 and 9 get an underline so a number read upside down from across the table can't be misread. */
private fun lifeText(text: String, underlineSixNine: Boolean): AnnotatedString = buildAnnotatedString {
    text.forEach { ch ->
        if (underlineSixNine && (ch == '6' || ch == '9')) {
            withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(ch) }
        } else {
            append(ch)
        }
    }
}

@Composable
private fun LowLifeWarning(visible: Boolean) {
    if (!visible) return
    val transition = rememberInfiniteTransition(label = "lowLife")
    val alpha by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "lowLifeAlpha"
    )
    Box(Modifier.fillMaxSize().background(Color(0xFFD32F2F).copy(alpha = alpha)))
}

/** Per-commander damage this player has taken, as colored dots — tapping opens the full face. */
@Composable
private fun ReceivedCommanderDamage(
    player: PlayerLife,
    opponents: List<PlayerLife>,
    ink: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val taken = player.commanderDamage.filterValues { it > 0 }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(ink.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Icon(Icons.Filled.Whatshot, contentDescription = "Commander damage received", tint = ink, modifier = Modifier.size(13.dp))
        taken.entries.sortedBy { it.key.opponentId * 2 + it.key.slot }.forEach { (source, damage) ->
            val opponent = opponents.firstOrNull { it.id == source.opponentId }
            Box(Modifier.size(8.dp).clip(CircleShape).background(opponent?.let { paletteColor(it.colorIndex) } ?: ink))
            Text(
                "$damage",
                style = MaterialTheme.typography.labelMedium,
                color = if (damage >= 21) Color(0xFFB71C1C) else ink,
                fontWeight = if (damage >= 21) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun TokenBadge(kind: TokenKind, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.75f))
            .border(BorderStroke(1.5.dp, Color.White), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (kind == TokenKind.MONARCH) Icons.Filled.WorkspacePremium else Icons.Filled.Castle,
            contentDescription = if (kind == TokenKind.MONARCH) "Monarch — tap to move" else "Initiative — tap to move",
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** The counters worth showing on the tile itself, per the "counters on player tile" settings. */
@Composable
private fun TileCounters(player: PlayerLife, settings: LifeCounterSettings, ink: Color, modifier: Modifier = Modifier) {
    val shown = PlayerCounter.entries.filter { kind ->
        kind in settings.pinnedCounters ||
            (settings.countersOnTile && (player.counter(kind) > 0 || (settings.keepZeroCounters && kind in player.counters)))
    }
    val tax = player.commanderTax.sum()
    val showTax = settings.countersOnTile && tax > 0
    if (shown.isEmpty() && !showTax) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.horizontalScroll(rememberScrollState())
    ) {
        shown.forEach { kind -> CounterChip(kind.label.uppercase(), player.counter(kind), ink) }
        if (showTax) CounterChip("TAX", tax, ink)
    }
}

@Composable
private fun CounterChip(label: String, value: Int, ink: Color) {
    Text(
        "$label $value",
        style = MaterialTheme.typography.labelSmall,
        color = ink,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(ink.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    )
}

@Composable
private fun CommanderDamageFace(
    player: PlayerLife,
    opponents: List<PlayerLife>,
    onAdjust: (source: CommanderSource, delta: Int) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        FaceHeader("DAMAGE YOU'VE RECEIVED", onClose)
        Spacer(Modifier.height(6.dp))
        if (opponents.isEmpty()) {
            Text("No opponents at this table.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
        } else {
            opponents.forEach { opponent ->
                // An opponent with a partner fields two separate commanders, each with its own
                // 21-damage threshold, so each gets its own row.
                val slots = if (opponent.hasPartner) listOf(0, 1) else listOf(0)
                slots.forEach { slot ->
                    val source = CommanderSource(opponent.id, slot)
                    val damage = player.commanderDamage[source] ?: 0
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(paletteColor(opponent.colorIndex)))
                        Text(
                            if (slot == 1) "${opponent.displayName} · partner" else opponent.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onAdjust(source, -1) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Remove, contentDescription = "Remove 1 commander damage", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        Text(
                            "$damage",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (damage >= 21) Color(0xFFFFB4A8) else Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(24.dp)
                        )
                        IconButton(onClick = { onAdjust(source, 1) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Add, contentDescription = "Add 1 commander damage", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionsFace(
    player: PlayerLife,
    profiles: List<PlayerProfile>,
    autoKill: Boolean,
    actions: PlayerTileActions,
    onClose: () -> Unit
) {
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) actions.setBackgroundImage(uri.toString())
    }
    var nameText by remember(player.id) { mutableStateOf(player.name ?: "") }
    var victoryText by remember(player.id) { mutableStateOf(player.victoryMessage ?: "") }
    var defeatText by remember(player.id) { mutableStateOf(player.defeatMessage ?: "") }
    var urlText by remember(player.id) { mutableStateOf("") }
    val defeated = player.isDefeated(autoKill)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        FaceHeader("PLAYER OPTIONS", onClose)
        Spacer(Modifier.height(6.dp))
        MiniTextField(value = nameText, placeholder = "Player ${player.id}") { nameText = it; actions.setName(it) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            if (defeated) {
                OptionPill("REVIVE", onClick = actions.revive)
            } else {
                OptionPill("KILL", onClick = actions.kill, destructive = true)
            }
        }

        SectionLabel("COUNTERS")
        PlayerCounter.entries.forEach { kind ->
            CounterRow(
                if (kind.resetsEachTurn) "${kind.label} (this turn)" else kind.label,
                player.counter(kind),
                onAdjust = { actions.adjustCounter(kind, it) }
            )
        }

        SectionLabel("MANA POOL")
        ManaPoolRow(pool = player.manaPool, onAdjust = actions.adjustMana)

        SectionLabel("COMMANDER")
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Partner commander", style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.weight(1f))
            Switch(
                checked = player.hasPartner,
                onCheckedChange = actions.setHasPartner,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color.White.copy(alpha = 0.35f),
                    uncheckedThumbColor = Color.White.copy(alpha = 0.8f),
                    uncheckedTrackColor = Color.Black.copy(alpha = 0.2f),
                    uncheckedBorderColor = Color.White.copy(alpha = 0.4f)
                )
            )
        }
        CounterRow("Commander tax", player.commanderTax.getOrElse(0) { 0 }, step = 2, onAdjust = { actions.adjustTax(0, it) })
        if (player.hasPartner) {
            CounterRow("Partner tax", player.commanderTax.getOrElse(1) { 0 }, step = 2, onAdjust = { actions.adjustTax(1, it) })
        }

        SectionLabel("COLOR")
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState())
        ) {
            PlayerPalette.forEachIndexed { index, swatch ->
                val selected = index == Math.floorMod(player.colorIndex, PlayerPalette.size)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(swatch)
                        .border(BorderStroke(if (selected) 2.5.dp else 1.dp, Color.White.copy(alpha = if (selected) 1f else 0.3f)), CircleShape)
                        .clickable { actions.setColor(index) }
                )
            }
        }

        SectionLabel("BACKGROUND")
        Row(modifier = Modifier.padding(top = 2.dp)) {
            TextButton(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Text("CHOOSE PHOTO", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
            if (player.backgroundImageUri != null) {
                TextButton(onClick = { actions.setBackgroundImage(null) }) {
                    Text("CLEAR", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                MiniTextField(value = urlText, placeholder = "Or paste an image/GIF URL") { urlText = it }
            }
            Spacer(Modifier.width(6.dp))
            TextButton(onClick = { actions.setBackgroundImage(urlText.trim()) }, enabled = urlText.isNotBlank()) {
                Text("USE", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }

        SectionLabel("MY VICTORY MESSAGE")
        MiniTextField(value = victoryText, placeholder = "Use the table's messages") { victoryText = it; actions.setVictoryMessage(it) }
        SectionLabel("MY DEFEAT MESSAGE")
        MiniTextField(value = defeatText, placeholder = "Use the table's messages") { defeatText = it; actions.setDefeatMessage(it) }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Text("SAVED PROFILES", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
            TextButton(onClick = actions.saveProfile, enabled = nameText.isNotBlank()) {
                Text("SAVE", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }
        if (profiles.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                profiles.forEach { profile ->
                    OptionPill(profile.name, onClick = {
                        nameText = profile.name
                        actions.loadProfile(profile)
                    })
                }
            }
        }
    }
}

@Composable
private fun FaceHeader(title: String, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.weight(1f))
        IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Back to life total", tint = Color.White)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = Color.White.copy(alpha = 0.85f),
        modifier = Modifier.padding(top = 14.dp)
    )
}

@Composable
private fun OptionPill(label: String, onClick: () -> Unit, destructive: Boolean = false) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (destructive) Color(0xFFB71C1C) else Color.White.copy(alpha = 0.18f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    )
}

@Composable
private fun MiniTextField(value: String, placeholder: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.White,
            unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Color.White
        ),
        modifier = Modifier.fillMaxWidth().height(50.dp).padding(top = 2.dp)
    )
}

@Composable
private fun CounterRow(label: String, value: Int, step: Int = 1, onAdjust: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.weight(1f))
        IconButton(onClick = { onAdjust(-step) }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease $label", tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Text("$value", style = MaterialTheme.typography.bodyMedium, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.width(24.dp))
        IconButton(onClick = { onAdjust(step) }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.Add, contentDescription = "Increase $label", tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

/** A player's WUBRG+C mana pool: tap a symbol to add one, hold to spend one. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ManaPoolRow(pool: Map<String, Int>, onAdjust: (color: String, delta: Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
    ) {
        ManaPoolColors.forEach { color ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .combinedClickable(
                        onClick = { onAdjust(color, 1) },
                        onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onAdjust(color, -1) }
                    )
                    .padding(vertical = 6.dp)
            ) {
                ManaSymbol(color, size = 18.dp)
                Text("${pool[color] ?: 0}", style = MaterialTheme.typography.bodyMedium, color = Color.White, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
    Text(
        "Tap to add · hold to spend · empties when the turn passes",
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.6f),
        modifier = Modifier.padding(top = 4.dp)
    )
}
