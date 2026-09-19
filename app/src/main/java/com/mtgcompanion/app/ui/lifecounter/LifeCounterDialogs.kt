package com.mtgcompanion.app.ui.lifecounter

import com.mtgcompanion.app.data.social.SocialApi
import com.mtgcompanion.app.ui.social.QrCode
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.isOffline
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.ui.common.InlineManaText
import kotlinx.coroutines.launch
import kotlin.random.Random

// ---- Confirm ----

/** A centered question with a cancel and a confirm, the way the table asks before anything destructive. */
@Composable
internal fun ConfirmOverlay(text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp).popIn(easing = TableMotion.Pop)) {
            TableLabel(text, 34.sp, align = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), modifier = Modifier.padding(top = 18.dp)) {
                TableLabel("Cancel", 30.sp, color = TableColors.Blue, modifier = Modifier.clickable(onClick = onDismiss))
                TableLabel(confirmLabel, 30.sp, color = TableColors.Accent, modifier = Modifier.clickable { onConfirm(); onDismiss() })
            }
        }
    }
}

// ---- Exact life keypad ----

/** Tap the life number to type an exact total instead of tapping ±1 repeatedly. */
@Composable
internal fun LifeKeypadOverlay(playerName: String, initial: Int, seat: SeatColor, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial.toString()) }
    TableOverlay(title = playerName, onClose = onDismiss) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 20.dp)
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(seat.color)
            ) {
                TableLabel(text.ifBlank { "0" }, 96.sp, color = if (seat.whiteText) Color.White else Color.Black)
            }
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("−", "0", "⌫")).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(bottom = 14.dp)) {
                    row.forEach { key ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(TableColors.SurfaceRaised)
                                .clickable {
                                    text = when (key) {
                                        "⌫" -> text.dropLast(1)
                                        "−" -> if (text.startsWith("-")) text.removePrefix("-") else "-$text"
                                        else -> if (text == "0") key else (text + key).take(4)
                                    }
                                }
                        ) { TableLabel(key, 36.sp) }
                    }
                }
            }
            PillButton("Set life", TableColors.Accent, onClick = { onConfirm(text.toIntOrNull() ?: initial); onDismiss() }, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

// ---- Dice, coin, high roll ----

private const val MAX_CUSTOM_DICE = 20
private const val MAX_CUSTOM_SIDES = 1000

@Composable
internal fun DiceOverlay(
    // Rolls for who goes first, shown on the table itself (this screen closes for it).
    onHighRoll: () -> Unit,
    onDismiss: () -> Unit
) {
    var result by remember { mutableStateOf<String?>(null) }
    var rollId by remember { mutableIntStateOf(0) }
    var diceCount by remember { mutableStateOf("1") }
    var diceSides by remember { mutableStateOf("20") }
    val count = diceCount.toIntOrNull()?.takeIf { it in 1..MAX_CUSTOM_DICE }
    val sides = diceSides.toIntOrNull()?.takeIf { it in 2..MAX_CUSTOM_SIDES }
    fun show(text: String) { result = text; rollId++ }

    TableOverlay(title = "Dice", onClose = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
        ) {
            run {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(130.dp)) {
                    result?.let { RollResult(it, rollId) } ?: TableLabel("Tap a die", 40.sp, color = TableColors.TextMuted)
                }
                listOf(listOf(4, 6, 8), listOf(10, 12, 20)).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                        row.forEach { s ->
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(84.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(TableColors.SurfaceRaised)
                                    .clickable { show(rollDice(1, s)) }
                            ) { TableLabel("D$s", 36.sp) }
                        }
                    }
                }
                PillButton("Flip a coin", TableColors.Yellow, textColor = Color.Black, onClick = { show(if (Random.nextBoolean()) "Heads" else "Tails") })

                SectionTitle("Custom dice")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField(diceCount, width = 70) { diceCount = it }
                    TableLabel("D", 36.sp)
                    NumberField(diceSides, width = 90) { diceSides = it }
                    PillButton("Roll", TableColors.Accent, enabled = count != null && sides != null, onClick = { show(rollDice(count!!, sides!!)) })
                }
                if (count == null || sides == null) {
                    TableLabel("Up to $MAX_CUSTOM_DICE dice, 2–$MAX_CUSTOM_SIDES sides", 18.sp, color = TableColors.TextMuted, modifier = Modifier.padding(top = 6.dp))
                }
                SectionTitle("Who goes first")
            }

            PillButton("Roll for everyone", TableColors.MenuHighRoll, textColor = Color.Black, onClick = { onHighRoll(); onDismiss() })
            Spacer(Modifier.height(28.dp))
        }
    }
}

/** A roll result spinning into place. Re-keyed on every roll so the same value still animates. */
@Composable
private fun RollResult(text: String, rollId: Int) {
    val progress = remember(rollId) { Animatable(0f) }
    LaunchedEffect(rollId) { progress.animateTo(1f, tween(450, easing = TableMotion.Pop)) }
    TableLabel(
        text,
        if (text.length > 14) 40.sp else 72.sp,
        color = TableColors.Yellow,
        align = TextAlign.Center,
        modifier = Modifier.graphicsLayer {
            val p = progress.value
            scaleX = p; scaleY = p
            alpha = p.coerceIn(0f, 1f)
            rotationZ = (1f - p) * 360f
        }
    )
}

/** "D20 · 14" for one die; "3D6 · 2 5 1 = 8" for several. */
private fun rollDice(count: Int, sides: Int): String {
    val rolls = List(count) { Random.nextInt(1, sides + 1) }
    return if (count == 1) "${rolls.first()}" else "${rolls.joinToString("  ")} = ${rolls.sum()}"
}

@Composable
internal fun NumberField(value: String, width: Int, onValueChange: (String) -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(width.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(TableColors.SurfaceRaised)
            .padding(vertical = 8.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = { new -> if (new.length <= 4 && new.all { it.isDigit() }) onValueChange(new) },
            singleLine = true,
            textStyle = tableText(30.sp).copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            cursorBrush = SolidColor(TableColors.Yellow),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    TableLabel(text, 30.sp, color = TableColors.TextMuted, modifier = modifier.fillMaxWidth().padding(top = 26.dp, bottom = 10.dp))
}

// ---- A seat's QR code ----

/**
 * The QR code a player scans with their own phone to sit at [seat] with their profile. Closes by
 * itself once they've joined (the screen watches the table while it's up).
 */
@Composable
internal fun SeatCodeOverlay(seat: Int, playerName: String, code: String?, error: String?, onDismiss: () -> Unit) {
    TableOverlay(title = "Seat $seat · $playerName", onClose = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)
        ) {
            when {
                error != null -> TableLabel(error, 22.sp, color = TableColors.TextMuted, align = TextAlign.Center)
                code == null -> TableLabel("Opening the table…", 22.sp, color = TableColors.TextMuted)
                else -> {
                    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White).padding(10.dp)) {
                        QrCode(SocialApi.seatLink(code, seat), 260.dp, "QR code to sit at seat $seat")
                    }
                    TableLabel(
                        "Scan with the MTG Companion app (Friends → Scan QR code) or any phone camera. Waiting for them to join…",
                        20.sp,
                        color = TableColors.TextMuted,
                        align = TextAlign.Center
                    )
                }
            }
        }
    }
}

// ---- Game history ----

/** Newest first, since the last few changes are what a table usually wants to check. */
@Composable
internal fun GameHistoryOverlay(entries: List<HistoryEntry>, players: List<PlayerLife>, onDismiss: () -> Unit) {
    val nameOf = { id: Int -> players.firstOrNull { it.id == id }?.displayName ?: "Player $id" }
    TableOverlay(title = "History", onClose = onDismiss) {
        if (entries.isEmpty()) {
            TableLabel("Nothing has happened yet this game", 26.sp, color = TableColors.TextMuted, modifier = Modifier.padding(20.dp))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(entries.asReversed(), key = { it.id }) { entry ->
                    val player = entry.playerId?.let { id -> players.firstOrNull { it.id == id } }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(TableColors.Surface)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(player?.let { paletteColor(it.colorIndex) } ?: TableColors.Line))
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            InlineManaText(describeHistoryEntry(entry, nameOf).uppercase(), style = tableText(21.sp), color = Color.White)
                        }
                        TableLabel("T${entry.turn} · ${clockTime(entry.atMillis)}", 17.sp, color = TableColors.TextMuted)
                    }
                }
            }
        }
    }
}

/** Mana appears as `{W}` etc., which InlineManaText renders as the real symbol. */
private fun describeHistoryEntry(entry: HistoryEntry, nameOf: (Int) -> String): String {
    val who = entry.playerId?.let(nameOf) ?: "The table"
    val from = entry.from
    val to = entry.to
    val change = if (from != null && to != null) {
        val delta = to - from
        "  $from → $to (${if (delta > 0) "+" else ""}$delta)"
    } else ""
    return when (val event = entry.event) {
        HistoryEvent.Life -> "$who · life$change"
        is HistoryEvent.CommanderDamage -> {
            val partner = if (event.source.slot == 1) "'s partner" else ""
            "$who · damage from ${nameOf(event.source.opponentId)}$partner$change"
        }
        is HistoryEvent.Counter -> "$who · ${event.kind.label}$change"
        is HistoryEvent.Mana -> "$who · {${event.color}} pool$change"
        is HistoryEvent.CommanderTax -> "$who · ${if (event.slot == 1) "partner tax" else "commander tax"}$change"
        HistoryEvent.BecameMonarch -> "$who became the Monarch"
        HistoryEvent.TookInitiative -> "$who took the Initiative"
        HistoryEvent.Killed -> "$who was knocked out"
        HistoryEvent.Revived -> "$who was revived"
        HistoryEvent.TurnStarted -> "$who's turn"
        HistoryEvent.WonHighRoll -> "$who goes first"
        is HistoryEvent.BecameDayOrNight -> if (event.state == DayNight.DAY) "It became day" else "It became night"
    }
}

/** The time of day an entry happened, e.g. 21:04. */
internal fun clockTime(atMillis: Long): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(atMillis))

// ---- Seating layouts ----

/** Every seating arrangement, grouped by player count. Picking one starts a new game after confirming. */
@Composable
internal fun SeatingOverlay(currentLayoutId: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    var pending by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize()) {
        TableOverlay(title = "Seating", onClose = onDismiss) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                TableLabel("Each tile faces whoever sits at that edge of the phone", 20.sp, color = TableColors.TextMuted)
                TableLayouts.all.groupBy { it.playerCount }.forEach { (count, layouts) ->
                    SectionTitle(if (count == 1) "1 player" else "$count players")
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        layouts.forEachIndexed { index, layout ->
                            LayoutThumbnail(
                                layout = layout,
                                selected = layout.id == currentLayoutId,
                                onClick = { if (layout.id != currentLayoutId) pending = layout.id },
                                modifier = Modifier.popIn(delayMillis = 30 * index)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
        pending?.let { layoutId ->
            ConfirmOverlay(
                text = "Start a new game with this seating?",
                confirmLabel = "New game",
                onConfirm = { onSelect(layoutId); onDismiss() },
                onDismiss = { pending = null }
            )
        }
    }
}

@Composable
private fun LayoutThumbnail(layout: TableLayout, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .size(width = 70.dp, height = 116.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) TableColors.Accent else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = layout.description
                this.selected = selected
            }
            .padding(5.dp)
    ) {
        layout.rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f).fillMaxWidth()) {
                row.cells.forEach { cell ->
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    cell.seat == null -> Color.White.copy(alpha = 0.18f)
                                    selected -> Color.White.copy(alpha = 0.9f)
                                    else -> Color.White
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
internal fun ArchenemyPickerOverlay(players: List<PlayerLife>, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    TableOverlay(title = "Who's the Archenemy?", onClose = onDismiss) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            players.forEachIndexed { index, player ->
                val seat = seatColor(player.colorIndex)
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .popIn(delayMillis = 40 * index)
                        .clip(RoundedCornerShape(18.dp))
                        .background(seat.color)
                        .clickable { onPick(player.id); onDismiss() }
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    TableLabel(player.displayName, 34.sp, color = if (seat.whiteText) Color.White else Color.Black)
                }
            }
        }
    }
}

@Composable
internal fun GameModeOverlay(
    state: GameModeState,
    onPlaneswalk: () -> Unit,
    onRollDie: () -> PlanarDieFace,
    onRevealScheme: () -> Unit,
    onRevealBounty: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit
) {
    var dieResult by remember { mutableStateOf<PlanarDieFace?>(null) }
    var dieRoll by remember { mutableIntStateOf(0) }
    var showRules by remember { mutableStateOf(false) }
    val title = when (state.mode) {
        GameModeKind.PLANECHASE -> "Planechase"
        GameModeKind.ARCHENEMY -> "Archenemy"
        GameModeKind.BOUNTY -> "Bounty"
        GameModeKind.NONE -> ""
    }
    TableOverlay(title = title, onClose = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
        ) {
            when {
                state.loading -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 40.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = TableColors.Yellow)
                    TableLabel("Shuffling", 30.sp, color = TableColors.TextMuted, modifier = Modifier.padding(start = 12.dp))
                }
                state.mode == GameModeKind.PLANECHASE -> {
                    state.currentPlane?.let { ModeCard(it.displayImageUrl, it.name, it.displayOracleText, landscape = true) }
                        ?: TableLabel("Couldn't load planes — check your connection", 24.sp, color = TableColors.TextMuted)
                    dieResult?.let { face ->
                        RollResult(face.name, dieRoll)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 14.dp)) {
                        PillButton("Roll planar die", TableColors.SurfaceRaised, onClick = { dieResult = onRollDie(); dieRoll++ })
                        PillButton("Planeswalk", TableColors.Accent, onClick = onPlaneswalk)
                    }
                }
                state.mode == GameModeKind.ARCHENEMY -> {
                    state.currentScheme?.let { ModeCard(it.displayImageUrl, it.name, it.displayOracleText, landscape = true) }
                        ?: TableLabel(
                            if (state.schemeDeck.isEmpty()) "Couldn't load schemes — check your connection" else "Reveal the first scheme to begin",
                            24.sp, color = TableColors.TextMuted
                        )
                    if (state.ongoingSchemes.isNotEmpty()) {
                        SectionTitle("Ongoing")
                        state.ongoingSchemes.forEach { TableLabel(it.name, 22.sp, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) }
                    }
                    PillButton("Reveal scheme", TableColors.Accent, enabled = state.schemeDeck.isNotEmpty(), onClick = onRevealScheme, modifier = Modifier.padding(top = 14.dp))
                }
                state.mode == GameModeKind.BOUNTY -> {
                    val bounty = state.currentBounty
                    val front = bounty?.cardFaces?.firstOrNull()
                    if (bounty == null) {
                        TableLabel(
                            if (state.bountyDeck.isEmpty()) "Couldn't load bounty cards — check your connection"
                            else "Reveal the first bounty as the starting player's third turn begins",
                            24.sp, color = TableColors.TextMuted, align = TextAlign.Center
                        )
                    } else {
                        ModeCard(front?.imageUris?.normal ?: bounty.displayImageUrl, front?.name ?: bounty.name, front?.oracleText, landscape = false)
                    }
                    PillButton(
                        if (bounty == null) "Reveal bounty" else "Claimed · next bounty",
                        TableColors.Accent,
                        enabled = state.bountyDeck.isNotEmpty(),
                        onClick = onRevealBounty,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                    state.bountyRules?.let { rules ->
                        TableLabel(if (showRules) "Hide rules" else "How bounty works", 24.sp, color = TableColors.Yellow,
                            modifier = Modifier.padding(top = 14.dp).clickable { showRules = !showRules })
                        if (showRules) InlineManaText(rules, style = tableText(20.sp), color = TableColors.TextMuted, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            TableLabel("End $title", 26.sp, color = TableColors.Accent, modifier = Modifier.padding(top = 26.dp, bottom = 30.dp).clickable { onStop(); onDismiss() })
        }
    }
}

@Composable
private fun ModeCard(imageUrl: String?, name: String, text: String?, landscape: Boolean) {
    AsyncImage(
        model = imageUrl,
        contentDescription = name,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxWidth().aspectRatio(if (landscape) 1.4f else 0.72f).popIn(easing = TableMotion.Pop).clip(RoundedCornerShape(18.dp))
    )
    TableLabel(name, 34.sp, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
    text?.let { InlineManaText(it, style = tableText(20.sp), color = TableColors.TextMuted, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) }
}

// ---- Card search ----

private val SEARCH_FORMATS = listOf("commander", "standard", "pioneer", "modern", "legacy", "vintage", "pauper")

/** Price and format legality for any card, without leaving the game. */
@Composable
internal fun CardSearchOverlay(onSearch: suspend (String) -> List<ScryfallCard>, onDismiss: () -> Unit) {
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
                if (results.isEmpty()) message = "No cards found"
            } catch (e: Exception) {
                results = emptyList()
                message = if (isOffline(e)) "You're offline — card search needs internet" else "Search failed"
            } finally {
                searching = false
            }
        }
    }

    TableOverlay(title = "Card search", onClose = { if (selected != null) selected = null else onDismiss() }) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            val card = selected
            if (card == null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TableColors.SurfaceRaised).padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) TableLabel("Card name", 26.sp, color = TableColors.TextMuted)
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = tableText(26.sp),
                            cursorBrush = SolidColor(TableColors.Yellow),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searching) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = TableColors.Yellow)
                    else TableLabel("Go", 26.sp, color = TableColors.Yellow, modifier = Modifier.clickable { runSearch() })
                }
                message?.let { TableLabel(it, 22.sp, color = TableColors.TextMuted, modifier = Modifier.padding(top = 12.dp)) }
                LazyColumn(modifier = Modifier.padding(top = 10.dp)) {
                    items(results, key = { it.id }) { result ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(TableColors.Surface)
                                .clickable { selected = result }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                TableLabel(result.name, 24.sp, maxLines = 1)
                                result.typeLine?.let { TableLabel(it, 16.sp, color = TableColors.TextMuted, maxLines = 1) }
                            }
                            TableLabel(result.prices?.usd?.let { "$$it" } ?: "—", 24.sp, color = TableColors.Yellow)
                        }
                    }
                }
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    AsyncImage(
                        model = card.displayImageUrl,
                        contentDescription = card.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().aspectRatio(0.72f).popIn(easing = TableMotion.Pop).clip(RoundedCornerShape(18.dp))
                    )
                    TableLabel(card.name, 36.sp, modifier = Modifier.padding(top = 12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        TableLabel("Price ${card.prices?.usd?.let { "$$it" } ?: "—"}", 26.sp, color = TableColors.Yellow)
                        card.prices?.usdFoil?.let { TableLabel("Foil $$it", 26.sp, color = TableColors.TextMuted) }
                    }
                    SectionTitle("Legality")
                    SEARCH_FORMATS.forEach { format ->
                        val status = card.legalities?.get(format) ?: "not_legal"
                        val legal = status == "legal" || status == "restricted"
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                            TableLabel(format, 24.sp, modifier = Modifier.weight(1f))
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (legal) TableColors.MenuSeating else TableColors.SurfaceRaised)
                                    .padding(horizontal = 12.dp, vertical = 2.dp)
                            ) {
                                TableLabel(status.replace('_', ' '), 20.sp, color = if (legal) Color.Black else TableColors.TextMuted)
                            }
                        }
                    }
                    card.displayOracleText?.let {
                        InlineManaText(it, style = tableText(20.sp), color = TableColors.TextMuted, modifier = Modifier.padding(top = 12.dp, bottom = 24.dp))
                    }
                }
            }
        }
    }
}
