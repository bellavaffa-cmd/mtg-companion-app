package com.mtgcompanion.app.ui.collection

import com.mtgcompanion.app.data.usage.Usage
import com.mtgcompanion.app.data.usage.UsageAction
import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CopyHistoryStore
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.LendPick
import com.mtgcompanion.app.data.LendSource
import com.mtgcompanion.app.data.Loan
import com.mtgcompanion.app.data.MoveCard
import com.mtgcompanion.app.data.dayOf
import com.mtgcompanion.app.data.lend
import com.mtgcompanion.app.data.lendSources
import com.mtgcompanion.app.data.lentMove
import com.mtgcompanion.app.data.loanDue
import com.mtgcompanion.app.data.loansOf
import com.mtgcompanion.app.data.placesOf
import com.mtgcompanion.app.data.social.LoanServer
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.lifecounter.GameNightStore
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

/**
 * Lend cards, the web app's LendPage (src/pages/LendPage.tsx): what to lend — one card's copies
 * ([cardName], from its Where it is) or any of the cards in a place ([placeId], ticked) — to whom (a
 * friend by account, or anyone by name), back by when (no date, the next game night, or a day) and a
 * note. Copies from a place come off it while they're out. The logic is data/Loans.kt.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LendScreen(
    cardName: String?,
    placeId: String?,
    collections: List<Collection>,
    decks: List<Deck>,
    social: SocialRepository,
    onBack: () -> Unit,
    onChange: (StorageChange) -> Unit,
    onLent: () -> Unit
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Picked once: lending changes them.
    val sources = remember { lendSources(collections, decks, name = if (placeId == null) cardName.orEmpty() else null, placeId = placeId) }
    var picked by remember { mutableStateOf(if (cardName != null && placeId == null && sources.isNotEmpty()) mapOf(sources[0].key to 1) else emptyMap()) }
    val overview by social.overview.collectAsState()
    val account by social.accountFlow.collectAsState()
    LaunchedEffect(account?.userId) { if (account != null && overview == null) runCatching { social.refresh() } }
    val friends = overview?.friends.orEmpty().filter { it.accepted }
        .map { it.userId to (overview?.people?.get(it.userId)?.displayName ?: "Friend") }
        .sortedBy { it.second.lowercase() }
    var friendId by remember { mutableStateOf<String?>(null) }
    var someone by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val nightsExist = remember { GameNightStore.init(context); GameNightStore.gameNights().first }
    var backBy by remember { mutableStateOf("none") }
    var date by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    val count = picked.values.sum()
    val to = when {
        friendId != null -> friends.firstOrNull { it.first == friendId }?.second.orEmpty()
        someone -> name.trim()
        else -> ""
    }
    val place = placeId?.let { id -> placesOf(collections).firstOrNull { it.id == id } }
    val fromDeck = sources.firstOrNull { it.deckId != null && (picked[it.key] ?: 0) > 0 }

    fun set(s: LendSource, n: Int) {
        val v = n.coerceIn(0, s.qty)
        picked = if (v > 0) picked + (s.key to v) else picked - s.key
    }

    fun go() {
        if (count == 0 || to.isEmpty()) return
        val now = System.currentTimeMillis()
        val loan = Loan(
            id = UUID.randomUUID().toString(), to = to, friendId = friendId, lentAt = now,
            backBy = if (backBy == "date") date else null,
            gameNight = if (backBy == "night") true else null,
            note = note.trim().ifEmpty { null }
        )
        val picks = sources.filter { (picked[it.key] ?: 0) > 0 }.map { LendPick(it, picked.getValue(it.key)) }
        val after = lend(collections, picks, loan)
        onChange { current -> lend(current, picks, loan) }
        val made = loansOf(after).firstOrNull { it.id == loan.id }
        if (made != null) {
            Usage.action(UsageAction.LOAN_CREATED)
            val due = loanDue(made, dayOf(now), emptyList()).label
            CopyHistoryStore.record(picks.map { p ->
                lentMove(now, MoveCard(p.source.name, p.source.scryfallId), p.qty, to, p.source.from, p.source.line?.placeId, if (due == "No date") null else due)
            })
            if (made.friendId != null) scope.launch { LoanServer.send(social.api, made) }
        }
        onLent()
    }

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        val eyebrow = place?.let { "From ${it.name}" } ?: cardName
                        if (eyebrow != null) Text(eyebrow, style = MaterialTheme.typography.labelMedium, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (count > 0) "Lend $count ${if (count == 1) "card" else "cards"}" else "Lend cards", style = MaterialTheme.typography.titleLarge)
                    }
                },
                navigationIcon = { BackButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().background(colors.bg).navigationBarsPadding().padding(16.dp)) {
                LoanButton(
                    if (to.isNotEmpty()) "Lend to $to" else "Lend",
                    primary = true,
                    enabled = count > 0 && to.isNotEmpty() && (backBy != "date" || date != null),
                    modifier = Modifier.weight(1f)
                ) { go() }
            }
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (sources.isEmpty()) item { Text("Nothing here to lend — copies already lent out aren’t offered.", color = colors.textMuted) }
            items(sources, key = { it.key }) { s ->
                val n = picked[s.key] ?: 0
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surface).clickable { set(s, if (n > 0) 0 else 1) }.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Checkbox(checked = n > 0, onCheckedChange = { set(s, if (it) 1 else 0) }, colors = CheckboxDefaults.colors(checkedColor = colors.accent))
                    Column(Modifier.weight(1f)) {
                        Text(s.name + if (s.foil) " · foil" else "", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("from ${s.from}", style = MaterialTheme.typography.labelMedium, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (s.qty > 1) {
                        IconButton(onClick = { set(s, n - 1) }, enabled = n > 0) { Icon(Icons.Filled.Remove, contentDescription = "One fewer", tint = colors.textPrimary) }
                        Text("$n/${s.qty}", color = colors.textPrimary, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { set(s, n + 1) }, enabled = n < s.qty) { Icon(Icons.Filled.Add, contentDescription = "One more", tint = colors.textPrimary) }
                    }
                }
            }
            item { FieldLabel("To") }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    friends.forEach { (id, n) -> LoanPill("$n (friend)", friendId == id) { friendId = id; someone = false } }
                    LoanPill("Someone else…", someone) { someone = true; friendId = null }
                }
            }
            if (someone) item {
                OutlinedTextField(name, { name = it }, label = { Text("Their name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            if (account == null && friends.isEmpty()) item { Text("Sign in to lend to friends by account.", style = MaterialTheme.typography.labelSmall, color = colors.textMuted) }
            item { FieldLabel("Back by") }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LoanPill("No date", backBy == "none") { backBy = "none" }
                    if (nightsExist) LoanPill("Next game night", backBy == "night") { backBy = "night" }
                    LoanPill(if (backBy == "date" && date != null) "By ${com.mtgcompanion.app.data.shortDay(date!!)}" else "Pick a date", backBy == "date") {
                        val start = LocalDate.now().plusDays(7)
                        DatePickerDialog(context, { _, y, m, d -> date = LocalDate.of(y, m + 1, d).toString(); backBy = "date" }, start.year, start.monthValue - 1, start.dayOfMonth).show()
                    }
                }
            }
            item { FieldLabel("Note") }
            item {
                OutlinedTextField(note, { if (it.length <= 200) note = it }, placeholder = { Text("e.g. for the Saturday event") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            if ((friendId != null || fromDeck != null) && to.isNotEmpty()) item {
                val me = overview?.me?.displayName ?: "you"
                Text(
                    (if (friendId != null) "$to gets a note in Friends: “Borrowed from $me: $count ${if (count == 1) "card" else "cards"}”. " else "") +
                        (if (fromDeck != null) "The ${fromDeck.from} will show ${fromDeck.name} as lent out until it's back." else ""),
                    style = MaterialTheme.typography.bodySmall, color = colors.textMuted,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = LocalAppColors.current.textMuted, modifier = Modifier.padding(top = 8.dp))
}

@Composable
internal fun LoanPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        color = if (selected) colors.onAccent else colors.textMuted,
        modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(if (selected) colors.accent else colors.surface2).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp)
    )
}
