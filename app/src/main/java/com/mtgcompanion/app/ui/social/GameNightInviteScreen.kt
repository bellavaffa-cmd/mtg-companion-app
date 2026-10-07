package com.mtgcompanion.app.ui.social

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.activeDecks
import com.mtgcompanion.app.data.newBag
import com.mtgcompanion.app.data.social.BorrowedLoan
import com.mtgcompanion.app.data.social.GameNightReminders
import com.mtgcompanion.app.data.social.MyNightDeck
import com.mtgcompanion.app.data.social.NIGHTS_UNAVAILABLE
import com.mtgcompanion.app.data.social.NIGHT_HOURS
import com.mtgcompanion.app.data.social.NOTE_MAX
import com.mtgcompanion.app.data.social.NightInvite
import com.mtgcompanion.app.data.social.Overview
import com.mtgcompanion.app.data.social.PLACE_MAX
import com.mtgcompanion.app.data.social.Profile
import com.mtgcompanion.app.data.social.RsvpAnswer
import com.mtgcompanion.app.data.social.SocialException
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.data.social.TonightPlayer
import com.mtgcompanion.app.data.social.TradeMatch
import com.mtgcompanion.app.data.social.WhoTone
import com.mtgcompanion.app.data.social.attendeesOf
import com.mtgcompanion.app.data.social.calendarTitle
import com.mtgcompanion.app.data.social.canManageNight
import com.mtgcompanion.app.data.social.deckNamed
import com.mtgcompanion.app.data.social.headerLine
import com.mtgcompanion.app.data.social.myInvite
import com.mtgcompanion.app.data.social.nightBagId
import com.mtgcompanion.app.data.social.nightDate
import com.mtgcompanion.app.data.social.nightWhen
import com.mtgcompanion.app.data.social.playersFromInvite
import com.mtgcompanion.app.data.social.whoHeader
import com.mtgcompanion.app.data.social.whoRows
import com.mtgcompanion.app.ui.collection.EventBagStore
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.common.a11yHeading
import com.mtgcompanion.app.ui.common.readableWidth
import com.mtgcompanion.app.ui.lifecounter.GameNightStore
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

// A game night invite (the Invite mockup): when and where, Going / Maybe / Can't with the deck
// you'll bring, who's coming, "Ready for the night" (Pack your bag, Trade matches tonight, cards to
// give back), Add to calendar and Make pods on the night. And planning or changing a night
// (GameNightFormScreen). The web app's twin is src/pages/GameNightInvitePage.tsx.

private val ANSWERS = listOf(RsvpAnswer.GOING to "Going", RsvpAnswer.MAYBE to "Maybe", RsvpAnswer.CANT to "Can't")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameNightInviteScreen(
    social: SocialRepository,
    nightId: String,
    decks: List<Deck>,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenBag: (String) -> Unit,
    onOpenGameNight: () -> Unit,
    onOpenLoans: () -> Unit,
    /** Trade matches tonight (TradeMatchesTonight), for the players coming. */
    tonight: @Composable (List<TonightPlayer>) -> Unit
) {
    val colors = LocalAppColors.current
    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("Game night", style = MaterialTheme.typography.titleLarge, modifier = Modifier.a11yHeading()) },
                navigationIcon = { BackButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.readableWidth(680.dp)) {
                SocialGate(social, onSignIn) { o -> Invite(social, o, nightId, decks, onEdit, onOpenBag, onOpenGameNight, onOpenLoans, tonight) }
            }
        }
    }
}

@Composable
private fun Invite(
    social: SocialRepository,
    overview: Overview,
    nightId: String,
    allDecks: List<Deck>,
    onEdit: (String) -> Unit,
    onOpenBag: (String) -> Unit,
    onOpenGameNight: () -> Unit,
    onOpenLoans: () -> Unit,
    tonight: @Composable (List<TonightPlayer>) -> Unit
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val me = overview.me!!.userId
    val available by social.nights.available.collectAsState()
    val decks = remember(allDecks) { activeDecks(allDecks).sortedBy { it.name.lowercase() } }
    var night by remember { mutableStateOf<NightInvite?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var showTrades by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    var matches by remember { mutableStateOf<List<TradeMatch>>(emptyList()) }
    var borrowed by remember { mutableStateOf<List<BorrowedLoan>>(emptyList()) }
    val bags by remember { EventBagStore.init(context); EventBagStore.bags }.collectAsState()

    LaunchedEffect(nightId, reload) {
        if (!social.nights.check()) return@LaunchedEffect
        try {
            night = social.nights.night(nightId)
            loaded = true
            error = null
            night?.let { GameNightReminders.schedule(context, listOf(it), me) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Something went wrong."
        }
    }
    LaunchedEffect(Unit) {
        if (social.more.check()) runCatching { social.more.tradeMatches() }.onSuccess { matches = it }
        runCatching { social.api.myBorrowedLoans() }.onSuccess { borrowed = it }
    }
    // A night changed elsewhere (an answer, a new time): reload it.
    LaunchedEffect(me) {
        social.dmChannel.watch(this, me, onMessage = {}, onRejoined = { reload++ }, onOther = { event, payload ->
            if (event == "game_night" && payload.optString("nightId") == nightId) reload++
        })
    }

    when {
        available == false -> { EmptyState(Icons.Filled.Event, NIGHTS_UNAVAILABLE); return }
        !loaded && error != null -> { EmptyState(Icons.Filled.CloudOff, error.orEmpty()) { LineButton("Try again", { reload++ }) }; return }
        !loaded -> { Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = colors.accent) }; return }
    }
    val n = night ?: run { EmptyState(Icons.Filled.EventBusy, "This game night isn't there any more, or you're not invited."); return }

    val mine = myInvite(n, me)
    val pod = overview.pods.firstOrNull { it.id == n.podId }
    val manage = canManageNight(n, me, pod?.owner)
    val over = n.startsAt < System.currentTimeMillis() - 12 * 3_600_000L
    val coming = attendeesOf(n, me)
    val bag = bags.firstOrNull { it.id == nightBagId(n) }
    val myDeck = deckNamed(decks, mine?.deck) { it.name }

    fun answer(a: RsvpAnswer, deck: String?) {
        busy = true
        error = null
        scope.launch {
            try {
                social.nights.rsvp(n.id, a, if (a == RsvpAnswer.CANT) null else deck)?.let {
                    night = it
                    GameNightReminders.schedule(context, listOf(it), me)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    fun pack() {
        val attendees = coming.map { it.name }
        val id = nightBagId(n)
        val existing = bag
        if (existing != null) {
            EventBagStore.save(existing.copy(attendees = attendees, deckIds = if (myDeck != null && myDeck.id !in existing.deckIds) existing.deckIds + myDeck.id else existing.deckIds))
        } else {
            EventBagStore.save(newBag(id, "Game night at ${n.place}", nightDate(n.startsAt), attendees, listOfNotNull(myDeck?.id), System.currentTimeMillis()))
        }
        onOpenBag(id)
    }

    fun makePods() {
        GameNightStore.init(context)
        val commander = myDeck?.let { listOfNotNull(it.commander?.name, it.partnerCommander?.name).joinToString(" & ").ifEmpty { null } }
        GameNightStore.update { g ->
            g.copy(players = playersFromInvite(g.players, n, me, overview.me?.displayName ?: "Me", myDeck?.let { MyNightDeck(it.id, it.name, commander) }, GameNightStore::newId))
        }
        onOpenGameNight()
    }

    fun addToCalendar() {
        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, n.startsAt)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, n.startsAt + NIGHT_HOURS * 3_600_000L)
            .putExtra(CalendarContract.Events.TITLE, calendarTitle(n))
            .putExtra(CalendarContract.Events.EVENT_LOCATION, n.place)
            .putExtra(CalendarContract.Events.DESCRIPTION, n.note.orEmpty())
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            error = "No calendar app to add it to."
        }
    }

    val tradeCounts = matches.mapNotNull { m ->
        val who = coming.firstOrNull { it.userId == m.friend } ?: return@mapNotNull null
        (m.theyHave.size + m.theyWant.size).takeIf { it > 0 }?.let { "${who.name} $it" }
    }
    val giveBack = borrowed.mapNotNull { l ->
        val lender = l.lender ?: return@mapNotNull null
        if (coming.none { it.userId == lender.userId }) return@mapNotNull null
        lender.displayName to l.cards.sumOf { it.qty }
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "head") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.accentGlow).padding(16.dp)
                ) {
                    Text(nightWhen(n.startsAt), color = colors.accent, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 32.sp)
                    Text("At ${n.place}", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    Text(headerLine(n, me), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    if (n.cancelled) Text("Called off", color = colors.error, fontWeight = FontWeight.Bold)
                    n.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary) }
                }
            }
            if (manage && !n.cancelled && !over) item(key = "manage") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LineButton("Change", { onEdit(n.id) }, icon = { Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) })
                    LineButton("Call it off", { confirmCancel = true }, icon = { Icon(Icons.Filled.EventBusy, contentDescription = null, modifier = Modifier.size(18.dp)) })
                }
            }
            if (mine != null && !n.cancelled && !over) item(key = "rsvp") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(4.dp)
                            .semantics { contentDescription = "Are you going?" }
                    ) {
                        ANSWERS.forEach { (a, label) ->
                            val on = mine.answer == a
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(10.dp))
                                    .background(if (on) colors.accent else colors.surface)
                                    .clickable(enabled = !busy, role = Role.RadioButton) { answer(a, mine.deck) }
                                    .semantics { selected = on }
                            ) {
                                Text(label, color = if (on) colors.onAccent else colors.textPrimary, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, fontSize = 14.sp)
                            }
                        }
                    }
                    val current = mine.answer
                    if ((current == RsvpAnswer.GOING || current == RsvpAnswer.MAYBE) && decks.isNotEmpty()) {
                        DeckPicker(decks, myDeck, enabled = !busy) { d -> answer(current, d?.name) }
                    }
                }
            }
            error?.let { item(key = "error") { Notice(it, warn = true) } }
            item(key = "who-h") {
                Text(whoHeader(n), style = MaterialTheme.typography.labelMedium, color = colors.textMuted, fontWeight = FontWeight.Bold)
            }
            whoRows(n, me).forEach { r ->
                item(key = "who-${r.key}") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(r.names, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
                        Text(
                            r.status,
                            style = MaterialTheme.typography.bodyMedium,
                            color = when (r.tone) { WhoTone.GOING -> colors.success; WhoTone.MAYBE -> colors.accent; WhoTone.CANT -> colors.error; WhoTone.NONE -> colors.textMuted }
                        )
                    }
                }
            }
            if (!n.cancelled) item(key = "ready") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(14.dp)
                ) {
                    Text("Ready for the night", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, modifier = Modifier.a11yHeading())
                    Text("Filled in from who's coming", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    ReadyLink("Pack your bag" + (bag?.packed?.size?.takeIf { it > 0 }?.let { " · $it packed" } ?: "")) { pack() }
                    if (coming.isNotEmpty()) {
                        ReadyLink("Trade matches tonight" + if (tradeCounts.isNotEmpty()) " · " + tradeCounts.joinToString(", ") else "") { showTrades = !showTrades }
                    }
                    giveBack.forEach { (name, count) ->
                        ReadyLink("Give back $name's " + if (count == 1) "1 borrowed card" else "$count borrowed cards", onOpenLoans)
                    }
                }
            }
            if (showTrades && !n.cancelled) item(key = "tonight") { tonight(coming.map { TonightPlayer(it.name, it.userId) }) }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().background(colors.bg).padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            LineButton("Add to calendar", { addToCalendar() }, modifier = Modifier.height(48.dp))
            if (!n.cancelled) {
                Button(
                    onClick = { makePods() },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text("Make pods on the night", fontWeight = FontWeight.ExtraBold) }
            }
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            containerColor = colors.surface,
            title = { Text("Call off this game night?") },
            text = { Text("Everyone invited is told it's off.", color = colors.textMuted) },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    scope.launch {
                        try {
                            social.nights.cancel(n.id)
                            reload++
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = e.message
                        }
                    }
                }) { Text("Call it off", color = colors.error) }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Keep it", color = colors.textMuted) } }
        )
    }
}

@Composable
private fun ReadyLink(text: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Text(
        text,
        color = colors.accent,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 4.dp)
    )
}

/** "Bringing": the user's deck for the night. */
@Composable
private fun DeckPicker(decks: List<Deck>, current: Deck?, enabled: Boolean, onPick: (Deck?) -> Unit) {
    val colors = LocalAppColors.current
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surface)
                .clickable(enabled = enabled, role = Role.Button) { open = true }.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text("Bringing", style = MaterialTheme.typography.bodySmall, color = colors.textMuted, modifier = Modifier.weight(1f))
            Text(current?.name ?: "No deck picked", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("No deck picked") }, onClick = { open = false; onPick(null) })
            decks.forEach { d -> DropdownMenuItem(text = { Text(d.name) }, onClick = { open = false; onPick(d) }) }
        }
    }
}

// ---- Planning or changing a night ----

/** Plan a game night in [podId] ([nightId] null), or change one. [onSaved]: the night's id. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameNightFormScreen(
    social: SocialRepository,
    podId: String?,
    nightId: String?,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onSaved: (String) -> Unit
) {
    val colors = LocalAppColors.current
    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = { Text(if (nightId != null) "Change game night" else "Plan a game night", style = MaterialTheme.typography.titleLarge, modifier = Modifier.a11yHeading()) },
                navigationIcon = { BackButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.readableWidth(680.dp)) {
                SocialGate(social, onSignIn) { o -> NightForm(social, o, podId, nightId, onSaved) }
            }
        }
    }
}

@Composable
private fun NightForm(social: SocialRepository, overview: Overview, podId: String?, nightId: String?, onSaved: (String) -> Unit) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val available by social.nights.available.collectAsState()
    val zone = ZoneId.systemDefault()
    val tomorrow = remember { LocalDate.now(zone).plusDays(1) }
    var loaded by remember { mutableStateOf(nightId == null) }
    var pod by remember { mutableStateOf(podId ?: overview.pods.firstOrNull()?.id.orEmpty()) }
    var date by remember { mutableStateOf(tomorrow) }
    var time by remember { mutableStateOf(LocalTime.of(19, 0)) }
    var place by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var guests by remember { mutableStateOf<List<String>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(nightId) {
        if (!social.nights.check() || nightId == null) return@LaunchedEffect
        try {
            val n = social.nights.night(nightId)
            if (n == null) {
                error = "That game night isn't there any more."
            } else {
                val at = Instant.ofEpochMilli(n.startsAt).atZone(zone)
                pod = n.podId
                date = at.toLocalDate()
                time = at.toLocalTime().withSecond(0).withNano(0)
                place = n.place
                note = n.note.orEmpty()
                guests = n.invitees.filter { !it.member }.map { it.user.userId }
                loaded = true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message
        }
    }

    when {
        available == false -> { EmptyState(Icons.Filled.Event, NIGHTS_UNAVAILABLE); return }
        overview.pods.isEmpty() -> { EmptyState(Icons.Filled.Groups, "A game night is for a pod — make one on Friends first."); return }
        !loaded -> {
            if (error != null) Notice(error.orEmpty(), Modifier.padding(16.dp), warn = true)
            else Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = colors.accent) }
            return
        }
    }
    val thePod = overview.pods.firstOrNull { it.id == pod }
    val friends: List<Profile> = overview.acceptedFriends
        .filter { thePod == null || it.userId !in thePod.members }
        .mapNotNull { overview.person(it.userId) }
        .sortedBy { it.displayName.lowercase() }
    val startsAt = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
    val ok = thePod != null && place.isNotBlank()

    fun save() {
        val p = thePod ?: return
        busy = true
        error = null
        scope.launch {
            try {
                val n = social.nights.save(nightId, p.id, startsAt, place, note, guests.filter { g -> friends.any { it.userId == g } })
                onSaved(n.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SocialException) {
                error = e.message
                busy = false
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong."
                busy = false
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        if (nightId == null && overview.pods.size > 1) {
            Text("Pod", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                overview.pods.forEach { p ->
                    FilterChip(
                        selected = p.id == pod,
                        onClick = { pod = p.id; guests = emptyList() },
                        label = { Text(p.name) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.accent, selectedLabelColor = colors.onAccent)
                    )
                }
            }
        } else {
            Text(thePod?.name.orEmpty(), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        }
        Text("When", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LineButton(com.mtgcompanion.app.data.social.nightDay(startsAt), {
                DatePickerDialog(context, { _, y, m, d -> date = LocalDate.of(y, m + 1, d) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
            })
            LineButton(com.mtgcompanion.app.data.social.nightTime(startsAt), {
                TimePickerDialog(context, { _, h, m -> time = LocalTime.of(h, m) }, time.hour, time.minute, false).show()
            })
        }
        OutlinedTextField(
            value = place,
            onValueChange = { place = it.take(PLACE_MAX) },
            label = { Text("Where") },
            placeholder = { Text("${overview.me?.displayName ?: "Priya"}'s, the game store…") },
            singleLine = true,
            colors = socialFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(NOTE_MAX) },
            label = { Text("Note (optional)") },
            placeholder = { Text("Bring your new decks") },
            maxLines = 4,
            colors = socialFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Text("Everyone in ${thePod?.name ?: "the pod"} is invited. Ask friends from outside it too:", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        if (friends.isEmpty()) {
            Text("No other friends to ask.", style = MaterialTheme.typography.bodySmall, color = colors.textDim)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                friends.forEach { f ->
                    val on = f.userId in guests
                    FilterChip(
                        selected = on,
                        onClick = { guests = if (on) guests - f.userId else guests + f.userId },
                        label = { Text(f.displayName) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.accent, selectedLabelColor = colors.onAccent)
                    )
                }
            }
        }
        error?.let { Notice(it, warn = true) }
        GoldButton(
            if (busy) "Saving…" else if (nightId != null) "Save changes" else "Invite the pod",
            { save() },
            enabled = ok && !busy,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        )
    }
}
