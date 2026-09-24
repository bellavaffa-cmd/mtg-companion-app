package com.mtgcompanion.app.ui.lifecounter

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MobileOff
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.DisposableEffect
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.social.GiphyPickerDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// The remote's own dark look, the same as the web app's (src/lifecounter/remote.css).
private val RmBg = Color(0xFF0B0B0D)
private val RmSurface = Color(0xFF1B1C21)
private val RmRaised = Color(0xFF2A2C33)
private val RmSheet = Color(0xFF16181E)
private val RmText = Color(0xFFF1EEE6)
private val RmMuted = Color(0xFF9A978E)
private val RmGold = Color(0xFFE6B45E)
private val RmYellow = Color(0xFFFFC600)
private val RmAlertBg = Color(0xFF4A1B0C)
private val RmAlertText = Color(0xFFF5C4B3)
private val RmGreen = Color(0xFF2BD98F)

/** No word from the table for this long (it sends the game at least every 25 s): it's gone. */
private const val SILENT_MS = 60_000L
private const val FEEDBACK_HOLD_MS = 1_500L

private enum class RemoteSheet { DAMAGE, COUNTERS, BACKGROUND, MORE, DECK, SHOW, NOTES }

/** "#rrggbb", or the short "#rgb" the table uses for ink (which Android's parser doesn't read). */
private fun hexColor(hex: String): Color {
    val full = if (Regex("#[0-9a-fA-F]{3}").matches(hex)) "#" + hex.drop(1).map { "$it$it" }.joinToString("") else hex
    return runCatching { Color(android.graphics.Color.parseColor(full)) }.getOrDefault(Color.Gray)
}

/**
 * A player's phone as the remote for their seat at someone's life counter: their life, undo, End
 * turn, counters, commander damage taken and dealt, everyone's life, the tile's background, showing
 * a card on the table, private notes and a big-number mode. Stays awake while open.
 */
@Composable
fun RemoteScreen(viewModel: RemoteViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val live by viewModel.live.collectAsState()
    val heardAt by viewModel.heardAt.collectAsState()
    val error by viewModel.error.collectAsState()
    val gone by viewModel.gone.collectAsState()
    val decks by viewModel.decks.collectAsState()
    val deckId by viewModel.deckId.collectAsState()
    val deck = decks.firstOrNull { it.id == deckId }
    var sheet by remember { mutableStateOf<RemoteSheet?>(null) }
    var big by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(5_000); now = System.currentTimeMillis() } }

    // Stays awake while it's in front, like the table.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    BackHandler(enabled = sheet != null) { sheet = null }

    val mine = state?.players?.firstOrNull { it.seat == viewModel.seat }
    // Remember the seat while it's ours, so Home can offer the way back to this remote.
    val seatIsMine = mine != null && (mine.userId == null || mine.userId == viewModel.userId)
    LaunchedEffect(seatIsMine) { if (seatIsMine) viewModel.rememberSeat() }
    val seatIsTheirs = mine?.userId != null && mine.userId != viewModel.userId
    LaunchedEffect(gone, seatIsTheirs) { if (gone || seatIsTheirs) viewModel.forgetSeat() }
    val silent = state != null && now - heardAt > SILENT_MS

    Box(Modifier.fillMaxSize().background(RmBg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
            // Header: connection, seat, turn; Big; More.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp)) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(if (live && !silent) RmGreen else Color(0xFF6B6B74)))
                Text(
                    buildString {
                        append("Seat ${viewModel.seat}")
                        state?.turn?.let { append(" · turn ${it.number}") }
                        if (state != null && (!live || silent)) append(if (silent) " · table disconnected" else " · reconnecting…")
                    },
                    color = if (state != null && (!live || silent)) Color(0xFFFFB45E) else RmMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                if (mine != null && state?.remotes == true) {
                    Chip(if (big) "Exit big" else "Big", if (big) Icons.Filled.CloseFullscreen else Icons.Filled.OpenInFull) { big = !big }
                }
                Chip(null, Icons.Filled.MoreHoriz) { sheet = RemoteSheet.MORE }
            }
            error?.let {
                Text(it, color = RmAlertText, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(12.dp)).background(RmAlertBg).padding(10.dp))
            }
            val s = state
            when {
                viewModel.userId == null -> Notice(Icons.Filled.Lock, "Sign in to use your remote", null)
                gone -> Notice(Icons.Filled.EventSeat, "You've left this table", "The seat was freed, or the table ended.") { RmButton("Done", onClick = onBack) }
                s == null || mine == null -> Notice(Icons.Filled.Sync, if (live) "Waiting for the table…" else "Connecting to the table…", "Keep the life counter open on the table's phone.")
                mine.userId != null && mine.userId != viewModel.userId -> Notice(Icons.Filled.EventSeat, "Someone else is in seat ${viewModel.seat} now", null) { RmButton("Done", onClick = onBack) }
                !s.remotes -> Notice(Icons.Filled.MobileOff, "Remotes are off", "The table's owner is keeping this game on the table's phone.")
                else -> Remote(s, mine, big, deck, viewModel, onSheet = { sheet = it })
            }
        }

        val s = state
        if (s?.over != null && mine != null) GameOver(viewModel, s, deck, onPickDeck = { sheet = RemoteSheet.DECK })
        when (sheet) {
            RemoteSheet.DAMAGE -> if (s != null && mine != null) RmSheetBox("Commander damage", { sheet = null }) {
                val others = s.players.filter { it.seat != mine.seat }
                Label("You dealt")
                others.forEach { o ->
                    (if (mine.partner) listOf(0, 1) else listOf(0)).forEach { slot ->
                        Stepper(o.name + partnerSuffix(mine.partner, slot), o.damageFrom(mine.seat, slot), dot = hexColor(o.color)) { viewModel.send(RemoteActions.dealtDamage(o.seat, slot, it)) }
                    }
                }
                Label("You took")
                others.forEach { o ->
                    (if (o.partner) listOf(0, 1) else listOf(0)).forEach { slot ->
                        Stepper(o.name + partnerSuffix(o.partner, slot), mine.damageFrom(o.seat, slot), dot = hexColor(o.color)) { viewModel.send(RemoteActions.commanderDamage(o.seat, slot, it)) }
                    }
                }
            }
            RemoteSheet.COUNTERS -> if (mine != null) RmSheetBox("Counters", { sheet = null }) {
                Stepper("Poison", mine.poison) { viewModel.send(RemoteActions.counter("poison", it)) }
                PlayerCounter.entries.filter { it != PlayerCounter.POISON }.forEach { kind ->
                    Stepper(kind.label, mine.counters[kind.wire()] ?: 0) { viewModel.send(RemoteActions.counter(kind.wire(), it)) }
                }
                Text("Storm goes back to 0 when the turn passes.", color = RmMuted, fontSize = 13.sp)
            }
            RemoteSheet.BACKGROUND -> if (mine != null) BackgroundSheet(viewModel, mine, deck, onPickDeck = { sheet = RemoteSheet.DECK }, onClose = { sheet = null })
            RemoteSheet.DECK -> RmSheetBox("Your deck", { sheet = null }) {
                if (decks.isEmpty()) Text("You have no decks yet.", color = RmMuted)
                decks.forEach { d ->
                    Option(d.name, d.commander?.name ?: "No commander", selected = d.id == deckId, swatchUrl = d.commander?.imageUrl.toArtCropUrl()) {
                        viewModel.chooseDeck(d)
                        viewModel.logAfterPicking()
                        sheet = null
                    }
                }
                if (deckId != null) RmButton("Not playing one of my decks", outlined = true) { viewModel.chooseDeck(null); sheet = null }
            }
            RemoteSheet.MORE -> RmSheetBox("More", { sheet = null }) {
                Option("Deck", deck?.name ?: "Pick the deck you're playing", icon = Icons.Filled.Style) { sheet = RemoteSheet.DECK }
                if (s?.remotes == true && mine != null) {
                    Option("Show a card on the table", "Everyone sees it big until they tap it away", icon = Icons.Filled.Visibility) { sheet = RemoteSheet.SHOW }
                }
                Option("Notes", "Only you see these", icon = Icons.Filled.Lock) { sheet = RemoteSheet.NOTES }
                Option("Leave this seat", "Your name comes off the table", icon = Icons.AutoMirrored.Filled.Logout) {
                    viewModel.leaveSeat()
                    onBack()
                }
            }
            RemoteSheet.SHOW -> ShowCardSheet(viewModel, onClose = { sheet = null })
            RemoteSheet.NOTES -> RmSheetBox("Notes", { sheet = null }) {
                var text by remember { mutableStateOf(viewModel.notes) }
                Text("Only you see these, on this phone.", color = RmMuted, fontSize = 13.sp)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it; viewModel.notes = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(RmGold),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp).clip(RoundedCornerShape(12.dp)).background(RmSurface).padding(12.dp)
                )
            }
            null -> Unit
        }
    }
}

private fun partnerSuffix(partner: Boolean, slot: Int) = if (!partner) "" else if (slot == 0) " · commander" else " · partner"

@Composable
private fun Remote(s: RemoteState, mine: RemoteSeat, big: Boolean, deck: Deck?, viewModel: RemoteViewModel, onSheet: (RemoteSheet) -> Unit) {
    var tally by remember { mutableIntStateOf(0) }
    var taps by remember { mutableIntStateOf(0) }
    LaunchedEffect(tally, taps) { if (tally != 0) { delay(FEEDBACK_HOLD_MS); tally = 0 } }
    val view = LocalView.current
    val change = { delta: Int ->
        viewModel.send(RemoteActions.life(delta))
        taps++
        tally = if (tally == 0 || (tally > 0) == (delta > 0)) tally + delta else delta
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
    val myTurn = s.turn?.seat == mine.seat
    val worst = mine.commanderDamage.maxOfOrNull { it.amount } ?: 0
    val alert = when {
        mine.out != null -> null
        mine.poison >= 8 -> "Poison ${mine.poison} — ${10 - mine.poison} more and you're out"
        worst >= 18 -> "Commander damage $worst — ${21 - worst} more and you're out"
        else -> null
    }
    val tallyText = if (tally == 0) null else (if (tally > 0) "+" else "−") + kotlin.math.abs(tally)

    val buttons: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            HoldCircle("−", if (big) 96.dp else 72.dp, s.longPress) { change(-it) }
            if (!big) {
                SmallCircle(Icons.AutoMirrored.Filled.Undo, "Undo my last change", enabled = mine.canUndo) { viewModel.send(RemoteActions.undo()) }
                if (myTurn && mine.out == null) SmallCircle(Icons.Filled.Check, "End turn", gold = true) { viewModel.send(RemoteActions.endTurn()) }
            }
            HoldCircle("+", if (big) 96.dp else 72.dp, s.longPress) { change(it) }
        }
    }

    if (big) {
        SeatCard(mine, Modifier.fillMaxSize()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text("${mine.life}", style = TextStyle(fontFamily = BebasNeue, fontSize = 220.sp, color = ink(mine)))
                Text(tallyText ?: "", style = TextStyle(fontFamily = BebasNeue, fontSize = 28.sp, color = ink(mine)), modifier = Modifier.height(34.dp))
                Spacer(Modifier.height(24.dp))
                buttons()
            }
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SeatCard(mine, Modifier.fillMaxWidth().then(if (myTurn) Modifier.border(5.dp, RmYellow, RoundedCornerShape(24.dp)) else Modifier)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(16.dp).alpha(if (mine.out != null) 0.6f else 1f)) {
                Text(mine.name + (deck?.let { " · ${it.name}" } ?: ""), color = ink(mine), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${mine.life}", style = TextStyle(fontFamily = BebasNeue, fontSize = 130.sp, color = ink(mine)))
                Text(
                    tallyText ?: if (mine.out != null) "Out of the game" else if (myTurn) "Your turn" else "",
                    style = TextStyle(fontFamily = BebasNeue, fontSize = 26.sp, color = ink(mine).copy(alpha = 0.85f)),
                    modifier = Modifier.height(32.dp)
                )
                buttons()
            }
        }
        alert?.let {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(RmAlertBg).padding(12.dp)) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = RmAlertText, modifier = Modifier.size(20.dp))
                Text(it, color = RmAlertText, fontSize = 15.sp)
            }
        }
        s.shownCard?.takeIf { it.seat == mine.seat }?.let { card ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF1D2433)).padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)) {
                Text("Showing ${card.name} on the table", color = RmText, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Chip("Hide", null) { viewModel.send(RemoteActions.hideCard()) }
            }
        }
        Label("Everyone")
        val others = s.players.filter { it.seat != mine.seat }
        others.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { o ->
                    SeatCard(o, Modifier.weight(1f).then(if (s.turn?.seat == o.seat) Modifier.border(3.dp, RmYellow, RoundedCornerShape(14.dp)) else Modifier), corner = 14.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 6.dp).alpha(if (o.out != null) 0.5f else 1f)) {
                            Text(o.name, color = ink(o), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${o.life}", style = TextStyle(fontFamily = BebasNeue, fontSize = 38.sp, color = ink(o)))
                            if (o.poison > 0) Text("☠ ${o.poison}", color = ink(o), fontSize = 12.sp)
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            BarButton("Damage", Icons.Filled.Warning, Modifier.weight(1f)) { onSheet(RemoteSheet.DAMAGE) }
            BarButton("Counters", Icons.Filled.WaterDrop, Modifier.weight(1f)) { onSheet(RemoteSheet.COUNTERS) }
            BarButton("Background", Icons.Filled.Image, Modifier.weight(1f)) { onSheet(RemoteSheet.BACKGROUND) }
            BarButton("More", Icons.Filled.MoreHoriz, Modifier.weight(1f)) { onSheet(RemoteSheet.MORE) }
        }
    }
}

private fun ink(p: RemoteSeat): Color = if (p.background != null) Color.White else hexColor(p.ink)

/** A seat's colour, or the picture its player chose (darkened so white numbers read on it). */
@Composable
private fun SeatCard(p: RemoteSeat, modifier: Modifier, corner: Dp = 24.dp, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.clip(RoundedCornerShape(corner)).background(hexColor(p.color))) {
        if (p.background != null) {
            AsyncImage(model = p.background, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.5f)))))
        }
        content()
    }
}

/** Tap for ±1; hold for ±[longPress], repeating while held. */
@Composable
private fun HoldCircle(label: String, size: Dp, longPress: Int, onStep: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.72f))
            .pointerInput(longPress) {
                detectTapGestures(onPress = {
                    var held = false
                    val repeat: Job = scope.launch {
                        delay(450)
                        while (true) {
                            held = true
                            onStep(longPress)
                            delay(600)
                        }
                    }
                    val released = tryAwaitRelease()
                    repeat.cancel()
                    if (released && !held) onStep(1)
                })
            }
    ) {
        Text(label, style = TextStyle(fontFamily = BebasNeue, fontSize = (size.value * 0.55f).sp, color = Color.White))
    }
}

@Composable
private fun SmallCircle(icon: ImageVector, description: String, enabled: Boolean = true, gold: Boolean = false, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(54.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(CircleShape)
            .background(if (gold) RmYellow else Color.Black.copy(alpha = 0.72f))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Icon(icon, contentDescription = description, tint = if (gold) Color.Black else Color.White, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun Chip(text: String?, icon: ImageVector?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF1F2027)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        icon?.let { Icon(it, contentDescription = text ?: "More", tint = RmText, modifier = Modifier.size(18.dp)) }
        text?.let { Text(it, color = RmText, fontSize = 14.sp) }
    }
}

@Composable
private fun BarButton(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(RmSurface).clickable(onClick = onClick).padding(vertical = 10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = RmGold, modifier = Modifier.size(24.dp))
        Text(label, color = RmText, fontSize = 13.sp)
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = RmMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
}

@Composable
private fun ColumnScope.Notice(icon: ImageVector, title: String, text: String?, action: (@Composable () -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp)
    ) {
        Icon(icon, contentDescription = null, tint = RmGold, modifier = Modifier.size(44.dp))
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        text?.let { Text(it, color = RmMuted, fontSize = 14.sp) }
        action?.invoke()
    }
}

@Composable
private fun RmButton(text: String, outlined: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .then(if (outlined) Modifier.border(1.dp, Color(0xFF3A3B44), RoundedCornerShape(50)) else Modifier.background(RmGold))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp)
    ) {
        Text(text, color = if (outlined) RmText else Color(0xFF1C1405), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Stepper(label: String, value: Int, dot: Color? = null, onChange: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF1F2027)).padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        dot?.let { Box(Modifier.size(10.dp).clip(CircleShape).background(it)) }
        Text(label, color = RmText, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        StepButton("−", "$label minus one") { onChange(-1) }
        Text("$value", style = TextStyle(fontFamily = BebasNeue, fontSize = 26.sp, color = Color.White), modifier = Modifier.width(34.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        StepButton("+", "$label plus one") { onChange(1) }
    }
}

@Composable
private fun StepButton(text: String, description: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(RmRaised).clickable(onClickLabel = description, onClick = onClick)
    ) {
        Text(text, color = Color.White, fontSize = 22.sp)
    }
}

@Composable
private fun Option(title: String, subtitle: String?, selected: Boolean = false, icon: ImageVector? = null, swatchUrl: String? = null, swatchColor: Color? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1F2027))
            .then(if (selected) Modifier.border(2.dp, RmGold, RoundedCornerShape(14.dp)) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = RmGold)
        if (swatchUrl != null || swatchColor != null) {
            Box(Modifier.size(width = 48.dp, height = 34.dp).clip(RoundedCornerShape(8.dp)).background(swatchColor ?: Color(0xFF34323E))) {
                swatchUrl?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
            subtitle?.let { Text(it, color = RmMuted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** A sheet from the bottom, over a dimmed remote. */
@Composable
private fun RmSheetBox(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(RmSheet)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Chip("Done", null, onClose)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.verticalScroll(rememberScrollState()), content = content)
        }
    }
}

/** Tile background: the commander of the deck being played, the profile picture, the seat colour, or a GIF / photo. */
@Composable
private fun BackgroundSheet(viewModel: RemoteViewModel, mine: RemoteSeat, deck: Deck?, onPickDeck: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val busy by viewModel.busy.collectAsState()
    val customPref by viewModel.customUrl.collectAsState()
    var giphy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.choosePhoto(context, uri)
    }
    val art = deck?.commander?.imageUrl.toArtCropUrl()
    val avatar = viewModel.avatarUrl
    val current = mine.background
    val currentKind = when {
        current == null -> TileBackground.COLOUR
        current == art -> TileBackground.COMMANDER
        current == avatar -> TileBackground.PROFILE
        else -> TileBackground.CUSTOM
    }
    val custom = if (currentKind == TileBackground.CUSTOM) current else customPref
    RmSheetBox("Tile background", onClose) {
        Option(
            "Commander art",
            if (deck == null) "Pick the deck you're playing" else deck.commander?.let { "${it.name}, from ${deck.name}" } ?: "${deck.name} has no commander",
            selected = currentKind == TileBackground.COMMANDER, swatchUrl = art, swatchColor = Color(0xFF34323E)
        ) { if (art != null) { viewModel.chooseBackground(TileBackground.COMMANDER); onClose() } else onPickDeck() }
        Option("Profile picture", if (avatar != null) "Your photo or GIF" else "You have no profile picture", selected = currentKind == TileBackground.PROFILE, swatchUrl = avatar, swatchColor = Color(0xFF34323E), enabled = avatar != null) {
            viewModel.chooseBackground(TileBackground.PROFILE); onClose()
        }
        Option("Seat colour", "Plain colour", selected = currentKind == TileBackground.COLOUR, swatchColor = hexColor(mine.color)) {
            viewModel.chooseBackground(TileBackground.COLOUR); onClose()
        }
        Option("A GIF or a photo", if (busy) "Uploading…" else "Search Giphy, or pick from your phone", selected = currentKind == TileBackground.CUSTOM, swatchUrl = custom, swatchColor = RmGreen, enabled = !busy) {
            if (custom != null) { viewModel.chooseBackground(TileBackground.CUSTOM, custom = custom); onClose() } else giphy = true
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            RmButton("Search Giphy", outlined = true, modifier = Modifier.weight(1f)) { giphy = true }
            RmButton("Pick a photo", outlined = true, modifier = Modifier.weight(1f)) {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
        if (busy) CircularProgressIndicator(color = RmGold, modifier = Modifier.size(22.dp).align(Alignment.CenterHorizontally))
    }
    if (giphy) {
        GiphyPickerDialog(
            social = viewModel.socialRepository,
            onPicked = { link -> viewModel.chooseGiphy(link); giphy = false; onClose() },
            onDismiss = { giphy = false }
        )
    }
}

/** Search a card by name and show it big on the table. */
@Composable
private fun ShowCardSheet(viewModel: RemoteViewModel, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var names by remember { mutableStateOf<List<String>>(emptyList()) }
    val busy by viewModel.busy.collectAsState()
    LaunchedEffect(query) {
        delay(250)
        names = runCatching { viewModel.cardNames(query) }.getOrDefault(emptyList())
    }
    RmSheetBox("Show a card on the table", onClose) {
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
            cursorBrush = SolidColor(RmGold),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) Text("Card name", color = RmMuted, fontSize = 16.sp)
                    inner()
                }
            },
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF1F2027)).padding(12.dp)
        )
        names.forEach { n ->
            Option(n, null, enabled = !busy) { viewModel.showCard(n); onClose() }
        }
    }
}

/** The result, once the table has one winner (or nobody left), and the game saved to the chosen deck. */
@Composable
private fun BoxScope.GameOver(viewModel: RemoteViewModel, s: RemoteState, deck: Deck?, onPickDeck: () -> Unit) {
    val over = s.over ?: return
    var closed by remember(s.gameId) { mutableStateOf(false) }
    if (closed) return
    val logged by viewModel.logged.collectAsState()
    val winner = s.players.firstOrNull { it.seat == over.winner }
    val standings = s.players.sortedWith(compareBy<RemoteSeat> { it.seat != over.winner }.thenBy { it.out != null }.thenByDescending { it.life })
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.matchParentSize().background(RmBg).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}.padding(horizontal = 18.dp, vertical = 24.dp)
    ) {
        Text("Game over · ${over.minutes} min · ${over.turns} ${if (over.turns == 1) "turn" else "turns"}", color = RmMuted, fontSize = 13.sp)
        Text(
            when {
                winner == null -> "Nobody is left standing"
                winner.seat == viewModel.seat -> "You win!"
                else -> "${winner.name} wins"
            },
            color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold
        )
        standings.forEachIndexed { i, p ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(RmSurface).padding(horizontal = 12.dp, vertical = 10.dp)) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(hexColor(p.color)))
                Text("${i + 1}. ${p.name}${if (p.seat == viewModel.seat) " (you)" else ""}", color = RmText, modifier = Modifier.weight(1f))
                Text("${p.life}", style = TextStyle(fontFamily = BebasNeue, fontSize = 24.sp, color = Color.White))
            }
        }
        if (deck != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF1B2A22)).padding(12.dp)) {
                Icon(Icons.Filled.BarChart, contentDescription = null, tint = Color(0xFF5DCAA5))
                Text(logged ?: "Saving to ${deck.name}…", color = RmText)
            }
        } else {
            RmButton("Pick your deck to save this result", outlined = true, onClick = onPickDeck)
        }
        Spacer(Modifier.weight(1f))
        RmButton("Done", modifier = Modifier.fillMaxWidth()) { closed = true }
    }
}
