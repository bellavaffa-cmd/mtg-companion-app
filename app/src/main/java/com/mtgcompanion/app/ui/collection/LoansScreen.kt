package com.mtgcompanion.app.ui.collection

import com.mtgcompanion.app.ui.common.EmptyAction
import com.mtgcompanion.app.ui.common.a11yHeading
import com.mtgcompanion.app.ui.common.EmptyPrompt
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Inventory2
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CopyHistoryStore
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.LoanPerson
import com.mtgcompanion.app.data.MoveCard
import com.mtgcompanion.app.data.NightDay
import com.mtgcompanion.app.data.dayOf
import com.mtgcompanion.app.data.loanCardFrom
import com.mtgcompanion.app.data.loanDue
import com.mtgcompanion.app.data.loanPeople
import com.mtgcompanion.app.data.loansFromTags
import com.mtgcompanion.app.data.loansOf
import com.mtgcompanion.app.data.reminderText
import com.mtgcompanion.app.data.returnCards
import com.mtgcompanion.app.data.returnedMove
import com.mtgcompanion.app.data.shortDay
import com.mtgcompanion.app.data.stillOut
import com.mtgcompanion.app.data.taggedLentCopies
import com.mtgcompanion.app.data.Loan
import com.mtgcompanion.app.data.social.BorrowedLoan
import com.mtgcompanion.app.data.social.LoanServer
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.common.SegmentedTabs
import com.mtgcompanion.app.ui.lifecounter.GameNightStore
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Loans, the web app's LoansPage (src/pages/LoansPage.tsx): the cards lent out, a group per person —
 * overdue first, each card with where it goes back to — with Got them back (every card back where it
 * came from), Some back… and Remind (a friend gets a notification; anyone else, a message to send).
 * Borrowed: what friends have lent the user. And, while copies are still marked with "lent" tags, a
 * one-time Turn them into loans. The logic is data/Loans.kt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoansScreen(
    collections: List<Collection>,
    decks: List<Deck>,
    social: SocialRepository,
    startBorrowed: Boolean,
    onBack: () -> Unit,
    onChange: (StorageChange) -> Unit,
    /** A printing's tags set, everywhere it's held (turning "lent" tags into loans). */
    onRetag: (scryfallId: String, tags: List<String>) -> Unit,
    onLendFromPlace: () -> Unit
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val account by social.accountFlow.collectAsState()
    var borrowedTab by remember { mutableStateOf(startBorrowed) }
    val loans = loansOf(collections)
    val today = dayOf(System.currentTimeMillis())
    val nights = remember {
        GameNightStore.init(context)
        GameNightStore.gameNights().second.map { NightDay(it, dayOf(it)) }
    }
    val people = remember(loans, today) { loanPeople(loans, today, nights) }
    val tagged = remember(collections) { taggedLentCopies(collections) }
    var borrowed by remember { mutableStateOf<List<BorrowedLoan>?>(null) }
    var borrowedFailed by remember { mutableStateOf(false) }
    var some by remember { mutableStateOf<LoanPerson?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    val lentCount = people.sumOf { it.copies }
    val borrowedCount = borrowed?.sumOf { l -> l.cards.sumOf { it.qty } } ?: 0

    // The friend side, best effort: send what's out (in case a send was missed), and fetch what's borrowed.
    LaunchedEffect(account?.userId) {
        if (account == null) return@LaunchedEffect
        LoanServer.sendAll(social.api, loansOf(collections), System.currentTimeMillis())
        runCatching { social.api.myBorrowedLoans() }
            .onSuccess { borrowed = it; borrowedFailed = false }
            .onFailure { borrowedFailed = true }
    }

    fun gotBack(person: LoanPerson, counts: Map<String, List<Int>>?) {
        val now = System.currentTimeMillis()
        var after = collections
        for (loan in person.loans) after = returnCards(after, loan.id, now, counts?.get(loan.id))
        val moves = person.loans.flatMap { loan ->
            loan.cards.mapIndexedNotNull { i, card ->
                val n = minOf(stillOut(card), counts?.get(loan.id)?.getOrNull(i) ?: if (counts == null) stillOut(card) else 0)
                if (n > 0) returnedMove(now, MoveCard(card.name, card.scryfallId), n, person.name, loanCardFrom(card, after, decks), card.placeId) else null
            }
        }
        CopyHistoryStore.record(moves)
        onChange { current ->
            var out = current
            for (loan in person.loans) out = returnCards(out, loan.id, now, counts?.get(loan.id))
            out
        }
        val ids = person.loans.map { it.id }.toSet()
        scope.launch { loansOf(after).filter { it.id in ids }.forEach { LoanServer.send(social.api, it) } }
        some = null
        note = if (counts != null) "Got those back — each card is where it came from." else "Got everything back from ${person.name} — each card is where it came from."
    }

    fun remind(person: LoanPerson) {
        scope.launch {
            if (person.friendId != null) {
                when (LoanServer.remind(social.api, person.loans)) {
                    LoanServer.Remind.SENT -> { note = "Reminded ${person.name}."; return@launch }
                    LoanServer.Remind.ALREADY -> { note = "${person.name} was reminded in the last 12 hours."; return@launch }
                    LoanServer.Remind.FAILED -> Unit
                }
            }
            val text = reminderText(person.name, person.loans)
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Remind ${person.name}"))
        }
    }

    fun convert() {
        val now = System.currentTimeMillis()
        var n = 0
        val id = { "${now.toString(36)}-${++n}-${UUID.randomUUID().toString().take(6)}" }
        val out = loansFromTags(collections, now, id)
        // The same loans as computed here, so their ids hold.
        onChange { current -> if (out.loans.isEmpty()) current else com.mtgcompanion.app.data.withLoans(current, loansOf(current) + out.loans) }
        out.retag.forEach { (scryfallId, tags) -> onRetag(scryfallId, tags) }
        note = "Made ${out.loans.size} ${if (out.loans.size == 1) "loan" else "loans"} from your “lent” tags."
    }

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("Loans", style = MaterialTheme.typography.titleLarge, modifier = Modifier.a11yHeading()) },
                navigationIcon = { BackButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                SegmentedTabs(
                    listOf("Lent out · $lentCount", "Borrowed · $borrowedCount"),
                    selected = if (borrowedTab) 1 else 0,
                    onSelect = { borrowedTab = it == 1 },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            note?.let { n -> item { Text(n, style = MaterialTheme.typography.bodyMedium, color = colors.accentLight) } }
            if (!borrowedTab) {
                if (tagged > 0) item {
                    LoanCardBox(overdue = false) {
                        Text("$tagged ${if (tagged == 1) "copy is" else "copies are"} tagged “lent”", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        Text("Turn the tags into loans, with who has them, and the tags come off.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                        LoanButton("Turn “lent” tags into loans", primary = true, modifier = Modifier.fillMaxWidth()) { convert() }
                    }
                }
                if (people.isEmpty()) item {
                    EmptyPrompt(
                        Icons.Filled.Handshake,
                        "Nothing lent out. Open a card or one of your places to lend from it.",
                        actions = listOf(EmptyAction("Lend from a place", Icons.Filled.Inventory2, onLendFromPlace))
                    )
                }
                items(people, key = { it.key }) { p ->
                    PersonCard(p, collections, decks, today, onBack = { gotBack(p, null) }, onSome = { some = p }, onRemind = { remind(p) })
                }
                if (people.isNotEmpty()) item {
                    Text("“Got them back” puts each card where it came from.", style = MaterialTheme.typography.labelSmall, color = colors.textDim)
                }
                item { LoanButton("Lend from a place", primary = false, modifier = Modifier.fillMaxWidth()) { onLendFromPlace() } }
            } else {
                when {
                    account == null -> item { Text("Sign in to see what friends have lent you.", color = colors.textMuted) }
                    borrowedFailed -> item { Text("Couldn’t load what friends have lent you. Try again later.", color = colors.textMuted) }
                    borrowed == null -> item { Text("Loading…", color = colors.textMuted) }
                    borrowed!!.isEmpty() -> item { EmptyPrompt(Icons.Filled.Handshake, "Nothing borrowed. When a friend lends you cards in Manabind, they show here.") }
                    else -> items(borrowed!!, key = { it.id }) { l -> BorrowedCard(l, today) }
                }
            }
        }
    }

    some?.let { p ->
        SomeBackDialog(p, collections, decks, onDismiss = { some = null }, onDone = { counts -> gotBack(p, counts) })
    }
}

@Composable
private fun LoanCardBox(overdue: Boolean, content: @Composable () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .then(if (overdue) Modifier.border(1.dp, colors.cut.copy(alpha = 0.5f), RoundedCornerShape(14.dp)) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) { content() }
}

@Composable
internal fun LoanButton(text: String, primary: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = if (primary) ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)
        else ButtonDefaults.buttonColors(containerColor = colors.surface2, contentColor = colors.textPrimary),
        modifier = modifier.height(44.dp)
    ) { Text(text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun PersonCard(person: LoanPerson, collections: List<Collection>, decks: List<Deck>, today: String, onBack: () -> Unit, onSome: () -> Unit, onRemind: () -> Unit) {
    val colors = LocalAppColors.current
    LoanCardBox(overdue = person.overdue > 0) {
        Row(Modifier.fillMaxWidth()) {
            Text(person.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Text(person.label, style = MaterialTheme.typography.labelMedium, fontWeight = if (person.overdue > 0) FontWeight.Bold else FontWeight.Normal, color = if (person.overdue > 0) colors.cut else colors.textMuted)
        }
        val notes = person.loans.mapNotNull { it.note }.distinct()
        Text(
            "${person.copies} ${if (person.copies == 1) "card" else "cards"}" + if (notes.isNotEmpty()) " · “${notes.joinToString("”, “")}”" else "",
            style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary
        )
        person.loans.forEach { loan ->
            loan.cards.filter { stillOut(it) > 0 }.forEach { c ->
                Row(Modifier.fillMaxWidth()) {
                    Text((if (stillOut(c) > 1) "${stillOut(c)}× " else "") + c.name + if (c.isFoil) " · foil" else "", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // Where it goes back to can be long: it shares the line with the card's name rather than taking it.
                    Text("→ ${loanCardFrom(c, collections, decks)}", style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).padding(start = 8.dp))
                }
            }
        }
        val dates = person.loans.mapNotNull { it.backBy }.distinct().joinToString(" · ") { "Back by ${shortDay(it, today.take(4).toIntOrNull())}" }
        // The dates, when there's more to say than the one already beside the name.
        if (dates.isNotEmpty() && person.overdue == 0 && dates != person.label) {
            Text(dates, style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            LoanButton("Got them back", primary = true, modifier = Modifier.weight(1f), onClick = onBack)
            if (person.copies > 1) LoanButton("Some back…", primary = false, modifier = Modifier.weight(1f), onClick = onSome)
        }
        LoanButton("Remind ${person.name}", primary = false, modifier = Modifier.fillMaxWidth(), onClick = onRemind)
    }
}

@Composable
private fun BorrowedCard(loan: BorrowedLoan, today: String) {
    val colors = LocalAppColors.current
    val due = loanDue(
        Loan(loan.clientId, "", lentAt = loan.lentAt, cards = loan.cards.map { com.mtgcompanion.app.data.LoanCard(it.name, it.printingId, it.qty) }, backBy = loan.backBy, gameNight = if (loan.gameNight) true else null),
        today, emptyList()
    )
    LoanCardBox(overdue = due.overdue > 0) {
        Row(Modifier.fillMaxWidth()) {
            Text("Borrowed from ${loan.lender?.displayName ?: "a friend"}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Text(due.label, style = MaterialTheme.typography.labelMedium, color = if (due.overdue > 0) colors.cut else colors.textMuted)
        }
        val n = loan.cards.sumOf { it.qty }
        Text("$n ${if (n == 1) "card" else "cards"}" + (loan.note?.let { " · “$it”" } ?: ""), style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
        loan.cards.forEach { c -> Text((if (c.qty > 1) "${c.qty}× " else "") + c.name, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary) }
    }
}

/** Some of a person's cards back: how many of each. */
@Composable
private fun SomeBackDialog(person: LoanPerson, collections: List<Collection>, decks: List<Deck>, onDismiss: () -> Unit, onDone: (Map<String, List<Int>>) -> Unit) {
    val colors = LocalAppColors.current
    var counts by remember { mutableStateOf(person.loans.associate { l -> l.id to l.cards.map { 0 } }) }
    fun set(loan: Loan, i: Int, n: Int) {
        val v = n.coerceIn(0, stillOut(loan.cards[i]))
        counts = counts + (loan.id to counts.getValue(loan.id).mapIndexed { j, x -> if (j == i) v else x })
    }
    val total = counts.values.sumOf { it.sum() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("What did ${person.name} give back?", color = colors.accentLight, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                person.loans.forEach { loan ->
                    loan.cards.forEachIndexed { i, c ->
                        if (stillOut(c) <= 0) return@forEachIndexed
                        val n = counts[loan.id]?.getOrNull(i) ?: 0
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(c.name + if (c.isFoil) " · foil" else "", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("→ ${loanCardFrom(c, collections, decks)}", style = MaterialTheme.typography.labelSmall, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { set(loan, i, n - 1) }, enabled = n > 0) { Icon(Icons.Filled.Remove, contentDescription = "One fewer ${c.name}", tint = colors.textPrimary) }
                            Text("$n/${stillOut(c)}", color = colors.textPrimary, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { set(loan, i, n + 1) }, enabled = n < stillOut(c)) { Icon(Icons.Filled.Add, contentDescription = "One more ${c.name}", tint = colors.textPrimary) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = total > 0, onClick = { onDone(counts) }) { Text("Got $total back", color = colors.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textMuted) } }
    )
}
