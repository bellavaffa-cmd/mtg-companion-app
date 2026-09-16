package com.mtgcompanion.app.ui.lifecounter

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.isOffline
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.ui.common.InlineManaText
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.TextDim
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary
import kotlinx.coroutines.launch
import kotlin.random.Random

// ---- Exact life keypad ----

/** Tap the life number to bring up an exact-value keypad instead of tapping ±1 repeatedly. */
@Composable
internal fun LifeKeypadDialog(initial: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Bg,
        title = { Text("Set life total", color = GoldLight) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text.ifBlank { "0" },
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 40.sp),
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("-", "0", "⌫")).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                        row.forEach { key ->
                            KeypadButton(key) {
                                text = when (key) {
                                    "⌫" -> text.dropLast(1)
                                    "-" -> if (text.startsWith("-")) text.removePrefix("-") else "-$text"
                                    else -> if (text == "0") key else text + key
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { GoldButton("SET") { onConfirm(text.toIntOrNull() ?: initial) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = TextMuted) } }
    )
}

@Composable
private fun KeypadButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Bg)
            .border(BorderStroke(1.dp, BorderColor), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
    }
}

@Composable
internal fun GoldButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg),
        modifier = modifier
    ) { Text(label, color = Bg, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

// ---- Dice, coin, high roll ----

private const val MAX_CUSTOM_DICE = 20
private const val MAX_CUSTOM_SIDES = 1000

@Composable
internal fun DiceRollerDialog(
    players: List<PlayerLife>,
    startWithHighRoll: Boolean,
    onHighRoll: () -> HighRollResult,
    onSetFirstPlayer: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var result by remember { mutableStateOf("—") }
    var diceCount by remember { mutableStateOf("1") }
    var diceSides by remember { mutableStateOf("20") }
    var highRoll by remember { mutableStateOf(if (startWithHighRoll) onHighRoll() else null) }
    val count = diceCount.toIntOrNull()?.takeIf { it in 1..MAX_CUSTOM_DICE }
    val sides = diceSides.toIntOrNull()?.takeIf { it in 2..MAX_CUSTOM_SIDES }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Bg,
        title = { Text(if (startWithHighRoll) "Who goes first?" else "Dice & coin", color = GoldLight) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState())
            ) {
                if (!startWithHighRoll) {
                    Text(result, style = MaterialTheme.typography.titleLarge, color = GoldLight, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 16.dp))
                    DiceRow(listOf(4, 6, 8)) { s -> result = rollDice(1, s) }
                    Spacer(Modifier.height(8.dp))
                    DiceRow(listOf(10, 12, 20)) { s -> result = rollDice(1, s) }
                    Spacer(Modifier.height(14.dp))
                    GoldButton("FLIP COIN") { result = if (Random.nextBoolean()) "Heads" else "Tails" }

                    Spacer(Modifier.height(18.dp))
                    DialogSectionLabel("CUSTOM DICE")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                        DialogNumberField(diceCount, width = 60) { diceCount = it }
                        Text("d", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        DialogNumberField(diceSides, width = 76) { diceSides = it }
                        GoldButton("ROLL", enabled = count != null && sides != null) { result = rollDice(count!!, sides!!) }
                    }
                    if (count == null || sides == null) {
                        Text("Up to $MAX_CUSTOM_DICE dice, 2–$MAX_CUSTOM_SIDES sides", style = MaterialTheme.typography.labelSmall, color = TextDim, modifier = Modifier.padding(top = 4.dp))
                    }
                    Spacer(Modifier.height(18.dp))
                    DialogSectionLabel("WHO GOES FIRST")
                }
                TextButton(onClick = { highRoll = onHighRoll() }) {
                    Text(if (highRoll == null) "HIGH ROLL (D20 EACH)" else "ROLL AGAIN", color = Gold)
                }
                highRoll?.let { roll ->
                    players.forEach { player ->
                        val rolls = roll.rolls[player.id].orEmpty()
                        val isWinner = player.id == roll.winnerId
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                        ) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(paletteColor(player.colorIndex)))
                            Text(
                                player.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isWinner) GoldLight else TextPrimary,
                                fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            // More than one roll means they tied for highest and rolled off.
                            Text(
                                rolls.joinToString(" → "),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isWinner) GoldLight else TextMuted,
                                fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                    players.firstOrNull { it.id == roll.winnerId }?.let { winner ->
                        GoldButton("START WITH ${winner.displayName.uppercase()}", modifier = Modifier.padding(top = 8.dp)) {
                            onSetFirstPlayer(winner.id)
                            onDismiss()
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CLOSE", color = TextMuted) } }
    )
}

/** "d20 → 14" for one die; "3d6 → 2, 5, 1 = 8" for several. */
private fun rollDice(count: Int, sides: Int): String {
    val rolls = List(count) { Random.nextInt(1, sides + 1) }
    return if (count == 1) "d$sides → ${rolls.first()}" else "${count}d$sides → ${rolls.joinToString(", ")} = ${rolls.sum()}"
}

@Composable
private fun DiceRow(sidesList: List<Int>, onRoll: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        sidesList.forEach { sides ->
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Bg)
                    .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(18.dp))
                    .clickable { onRoll(sides) },
                contentAlignment = Alignment.Center
            ) {
                Text("d$sides", style = MaterialTheme.typography.labelMedium, color = Gold)
            }
        }
    }
}

@Composable
internal fun DialogNumberField(value: String, width: Int, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> if (new.length <= 4 && new.all { it.isDigit() }) onValueChange(new) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = dialogFieldColors(),
        modifier = Modifier.width(width.dp)
    )
}

@Composable
internal fun dialogFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Gold,
    unfocusedBorderColor = BorderColor,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = Gold
)

@Composable
internal fun DialogSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = TextMuted, modifier = modifier.fillMaxWidth())
}

// ---- Game history ----

/** Newest first, since the last few changes are what a table usually wants to check. */
@Composable
internal fun GameHistoryDialog(entries: List<HistoryEntry>, players: List<PlayerLife>, onDismiss: () -> Unit) {
    val nameOf = { id: Int -> players.firstOrNull { it.id == id }?.displayName ?: "Player $id" }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Bg,
        title = { Text("Game history", color = GoldLight) },
        text = {
            if (entries.isEmpty()) {
                Text("Nothing has happened yet this game.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 460.dp)) {
                    items(entries.asReversed(), key = { it.id }) { entry ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                            Text(
                                "T${entry.turn} · ${formatElapsed(entry.matchSeconds)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextDim,
                                modifier = Modifier.width(72.dp)
                            )
                            InlineManaText(
                                describeHistoryEntry(entry, nameOf),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CLOSE", color = Gold) } }
    )
}

/** Mana appears as `{W}` etc., which InlineManaText renders as the real symbol. */
private fun describeHistoryEntry(entry: HistoryEntry, nameOf: (Int) -> String): String {
    val who = entry.playerId?.let(nameOf) ?: "The table"
    val from = entry.from
    val to = entry.to
    val change = if (from != null && to != null) {
        val delta = to - from
        " $from → $to (${if (delta > 0) "+" else ""}$delta)"
    } else ""
    return when (val event = entry.event) {
        HistoryEvent.Life -> "$who · life$change"
        is HistoryEvent.CommanderDamage -> {
            val partner = if (event.source.slot == 1) "'s partner" else ""
            "$who · commander damage from ${nameOf(event.source.opponentId)}$partner$change"
        }
        is HistoryEvent.Counter -> "$who · ${event.kind.label.lowercase()}$change"
        is HistoryEvent.Mana -> "$who · {${event.color}} in pool$change"
        is HistoryEvent.CommanderTax -> "$who · ${if (event.slot == 1) "partner tax" else "commander tax"}$change"
        HistoryEvent.BecameMonarch -> "$who became the Monarch"
        HistoryEvent.TookInitiative -> "$who took the Initiative"
        HistoryEvent.Killed -> "$who was knocked out"
        HistoryEvent.Revived -> "$who was revived$change"
        HistoryEvent.TurnStarted -> "$who's turn begins"
        HistoryEvent.WonHighRoll -> "$who won the high roll and goes first"
        is HistoryEvent.BecameDayOrNight -> if (event.state == DayNight.DAY) "It became day" else "It became night"
    }
}

internal fun formatElapsed(totalSeconds: Int): String = "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)

// ---- Seating layouts ----

/** Every seating arrangement, grouped by player count. Picking one starts a new game after confirming. */
@Composable
internal fun LayoutPickerDialog(currentLayoutId: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    var pending by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Bg)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("SEATING", style = MaterialTheme.typography.titleMedium, color = GoldLight, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("CLOSE", color = TextMuted) }
            }
            Text(
                "Each tile turns to face the player sitting at that edge of the phone.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                TableLayouts.all.groupBy { it.playerCount }.forEach { (count, layouts) ->
                    DialogSectionLabel(if (count == 1) "1 PLAYER" else "$count PLAYERS", Modifier.padding(top = 12.dp, bottom = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        layouts.forEach { layout ->
                            LayoutPreview(
                                layout = layout,
                                selected = layout.id == currentLayoutId,
                                onClick = { if (layout.id != currentLayoutId) pending = layout.id }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    pending?.let { layoutId ->
        AlertDialog(
            onDismissRequest = { pending = null },
            containerColor = Bg,
            title = { Text("Start a new game?", color = GoldLight) },
            text = { Text("Changing the seating resets everyone's life and counters.", color = TextPrimary) },
            confirmButton = { GoldButton("NEW GAME") { onSelect(layoutId); pending = null; onDismiss() } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("CANCEL", color = TextMuted) } }
        )
    }
}

@Composable
private fun LayoutPreview(layout: TableLayout, selected: Boolean, onClick: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .size(width = 62.dp, height = 104.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Gold.copy(alpha = 0.25f) else Color.Black)
            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Gold else BorderColor), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(5.dp)
    ) {
        layout.rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f).fillMaxWidth()) {
                row.cells.forEach { cell ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when {
                                    cell.seat == null -> Color.White.copy(alpha = 0.12f)
                                    selected -> Gold
                                    else -> Color.White.copy(alpha = 0.85f)
                                }
                            )
                    )
                }
            }
        }
    }
}

// ---- Game modes ----

@Composable
internal fun ArchenemyPickerDialog(players: List<PlayerLife>, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Bg,
        title = { Text("Who is the Archenemy?", color = GoldLight) },
        text = {
            Column {
                players.forEach { player ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(player.id); onDismiss() }.padding(vertical = 10.dp)
                    ) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(paletteColor(player.colorIndex)))
                        Spacer(Modifier.width(10.dp))
                        Text(player.displayName, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = TextMuted) } }
    )
}

@Composable
internal fun GameModeDetailDialog(
    state: GameModeState,
    onPlaneswalk: () -> Unit,
    onRollDie: () -> PlanarDieFace,
    onRevealScheme: () -> Unit,
    onRevealBounty: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit
) {
    var dieResult by remember { mutableStateOf<PlanarDieFace?>(null) }
    var showRules by remember { mutableStateOf(false) }
    val title = when (state.mode) {
        GameModeKind.PLANECHASE -> "Planechase"
        GameModeKind.ARCHENEMY -> "Archenemy"
        GameModeKind.BOUNTY -> "Bounty"
        GameModeKind.NONE -> ""
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Bg,
        title = { Text(title, color = GoldLight) },
        text = {
            Column(modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                when {
                    state.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Gold)
                        Text("Shuffling…", color = TextMuted, modifier = Modifier.padding(start = 10.dp))
                    }
                    state.mode == GameModeKind.PLANECHASE -> {
                        state.currentPlane?.let { CardFace(it) } ?: Text("Couldn't load planes — check your connection.", color = TextMuted)
                        dieResult?.let {
                            Text(
                                "Planar die: ${it.name.lowercase().replaceFirstChar { c -> c.uppercase() }}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Gold,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        }
                    }
                    state.mode == GameModeKind.ARCHENEMY -> {
                        state.currentScheme?.let { CardFace(it) }
                            ?: Text(if (state.schemeDeck.isEmpty()) "Couldn't load schemes — check your connection." else "Reveal the first scheme to begin.", color = TextMuted)
                        if (state.ongoingSchemes.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            Text("ONGOING", style = MaterialTheme.typography.labelMedium, color = TextDim)
                            state.ongoingSchemes.forEach { scheme ->
                                Text("• ${scheme.name}", style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                    state.mode == GameModeKind.BOUNTY -> {
                        val bounty = state.currentBounty
                        if (bounty == null) {
                            Text(
                                if (state.bountyDeck.isEmpty()) "Couldn't load bounty cards — check your connection."
                                else "Reveal the first bounty as the starting player's third turn begins.",
                                color = TextMuted
                            )
                        } else {
                            BountyFace(bounty)
                        }
                        state.bountyRules?.let { rules ->
                            TextButton(onClick = { showRules = !showRules }, modifier = Modifier.padding(top = 6.dp)) {
                                Text(if (showRules) "HIDE RULES" else "HOW BOUNTY WORKS", color = Gold)
                            }
                            if (showRules) {
                                InlineManaText(rules, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            }
                        }
                    }
                }
                TextButton(onClick = { onStop(); onDismiss() }, modifier = Modifier.padding(top = 10.dp)) {
                    Text("END ${title.uppercase()}", color = Color(0xFFFF8A80))
                }
            }
        },
        confirmButton = {
            when (state.mode) {
                GameModeKind.PLANECHASE -> Row {
                    TextButton(onClick = { dieResult = onRollDie() }) { Text("ROLL DIE", color = Gold) }
                    GoldButton("PLANESWALK", onClick = onPlaneswalk)
                }
                GameModeKind.ARCHENEMY -> GoldButton("REVEAL SCHEME", enabled = state.schemeDeck.isNotEmpty(), onClick = onRevealScheme)
                GameModeKind.BOUNTY -> GoldButton(
                    if (state.currentBounty == null) "REVEAL BOUNTY" else "CLAIMED · NEXT",
                    enabled = state.bountyDeck.isNotEmpty(),
                    onClick = onRevealBounty
                )
                GameModeKind.NONE -> Unit
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CLOSE", color = TextMuted) } }
    )
}

@Composable
private fun CardFace(card: ScryfallCard) {
    AsyncImage(
        model = card.displayImageUrl,
        contentDescription = card.name,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxWidth().aspectRatio(1.4f).clip(RoundedCornerShape(16.dp))
    )
    Text(card.name, style = MaterialTheme.typography.titleMedium, color = GoldLight, modifier = Modifier.padding(top = 10.dp))
    InlineManaText(card.displayOracleText ?: "No text.", style = MaterialTheme.typography.bodySmall, color = TextPrimary, modifier = Modifier.padding(top = 6.dp))
}

/** Only the bounty (front) face — the back is the shared rules, shown separately on request. */
@Composable
private fun BountyFace(card: ScryfallCard) {
    val front = card.cardFaces?.firstOrNull()
    AsyncImage(
        model = front?.imageUris?.normal ?: card.displayImageUrl,
        contentDescription = front?.name ?: card.name,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(16.dp))
    )
    Text(front?.name ?: card.name, style = MaterialTheme.typography.titleMedium, color = GoldLight, modifier = Modifier.padding(top = 10.dp))
    front?.oracleText?.let {
        InlineManaText(it, style = MaterialTheme.typography.bodySmall, color = TextPrimary, modifier = Modifier.padding(top = 6.dp))
    }
}

// ---- Card search ----

private val SEARCH_FORMATS = listOf("commander", "standard", "pioneer", "modern", "legacy", "vintage", "pauper")

/** Price and format legality for any card, without leaving the game. */
@Composable
internal fun CardSearchDialog(onSearch: suspend (String) -> List<ScryfallCard>, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ScryfallCard>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<ScryfallCard?>(null) }

    fun runSearch() {
        if (query.isBlank()) return
        searching = true
        message = null
        selected = null
        scope.launch {
            try {
                results = onSearch(query.trim())
                if (results.isEmpty()) message = "No cards found."
            } catch (e: Exception) {
                results = emptyList()
                message = if (isOffline(e)) "You're offline — card search needs an internet connection." else "Search failed."
            } finally {
                searching = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(modifier = Modifier.fillMaxSize().background(Bg).padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (selected == null) "CARD SEARCH" else "CARD",
                    style = MaterialTheme.typography.titleMedium,
                    color = GoldLight,
                    modifier = Modifier.weight(1f)
                )
                if (selected != null) {
                    TextButton(onClick = { selected = null }) { Text("RESULTS", color = Gold) }
                }
                TextButton(onClick = onDismiss) { Text("CLOSE", color = TextMuted) }
            }

            val card = selected
            if (card == null) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Card name", color = TextDim) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                    colors = dialogFieldColors(),
                    trailingIcon = {
                        if (searching) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Gold)
                        else TextButton(onClick = ::runSearch) { Text("GO", color = Gold) }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                message?.let { Text(it, color = TextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp)) }
                LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                    items(results, key = { it.id }) { result ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { selected = result }.padding(vertical = 10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(result.name, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                result.typeLine?.let { Text(it, color = TextDim, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            }
                            Text(result.prices?.usd?.let { "$$it" } ?: "—", color = Gold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    AsyncImage(
                        model = card.displayImageUrl,
                        contentDescription = card.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(16.dp))
                    )
                    Text(card.name, style = MaterialTheme.typography.titleMedium, color = GoldLight, modifier = Modifier.padding(top = 10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 6.dp)) {
                        Text("Price ${card.prices?.usd?.let { "$$it" } ?: "—"}", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                        card.prices?.usdFoil?.let { Text("Foil $$it", color = TextMuted, style = MaterialTheme.typography.bodyMedium) }
                    }
                    DialogSectionLabel("LEGALITY", Modifier.padding(top = 14.dp, bottom = 6.dp))
                    SEARCH_FORMATS.forEach { format ->
                        val status = card.legalities?.get(format) ?: "not_legal"
                        val legal = status == "legal" || status == "restricted"
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(format.replaceFirstChar { it.uppercase() }, color = TextPrimary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text(
                                status.replace('_', ' ').uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (legal) Color(0xFF7CD992) else Color(0xFFFF8A80)
                            )
                        }
                    }
                    card.displayOracleText?.let {
                        InlineManaText(it, style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
        }
    }
}
