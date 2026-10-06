package com.mtgcompanion.app.ui.collection

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.BagSection
import com.mtgcompanion.app.data.BorrowedFrom
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.WantedBy
import com.mtgcompanion.app.data.BagInput
import com.mtgcompanion.app.data.allTicked
import com.mtgcompanion.app.data.bagHeading
import com.mtgcompanion.app.data.bagLines
import com.mtgcompanion.app.data.cardsIn
import com.mtgcompanion.app.data.countersNeeded
import com.mtgcompanion.app.data.dayOf
import com.mtgcompanion.app.data.dayLabel
import com.mtgcompanion.app.data.gearOf
import com.mtgcompanion.app.data.homeSummary
import com.mtgcompanion.app.data.isTicked
import com.mtgcompanion.app.data.namesFrom
import com.mtgcompanion.app.data.newBag
import com.mtgcompanion.app.data.placesOf
import com.mtgcompanion.app.data.setComingHome
import com.mtgcompanion.app.data.shownLines
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.data.social.TradeMatch
import com.mtgcompanion.app.data.social.friendsWantHere
import com.mtgcompanion.app.data.tickAll
import com.mtgcompanion.app.data.toggleTick
import com.mtgcompanion.app.data.tokensToBring
import com.mtgcompanion.app.data.tournament.Tournament
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.common.a11yHeading
import com.mtgcompanion.app.ui.lifecounter.GameNight
import com.mtgcompanion.app.ui.lifecounter.NightPlayerKind
import com.mtgcompanion.app.ui.social.rememberSocialMore
import com.mtgcompanion.app.ui.theme.LocalAppColors
import java.util.UUID

/*
 * Pack your bag (data/EventBag.kt), the web app's PackPage (src/pages/PackPage.tsx): the bags being
 * packed on this phone, new ones — for tonight's game night, an event, or a quick "Pack for…" with a
 * name and a day — and one bag's checklist (the EventBag mockup). The ticks stay on this phone
 * (EventBagStore.kt).
 */

/** What a new bag starts with: from tonight's game night, an event, or nothing ("Pack for…"). */
private data class BagDraft(val name: String, val day: String, val who: String, val deckIds: List<String>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackListScreen(
    decks: List<Deck>,
    night: GameNight,
    events: List<Tournament>,
    onBack: () -> Unit,
    onOpenBag: (String) -> Unit
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    remember { EventBagStore.init(context) }
    val bags by EventBagStore.bags.collectAsState()
    var draft by remember { mutableStateOf<BagDraft?>(null) }
    val today = dayOf(System.currentTimeMillis())

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("Pack your bag", style = MaterialTheme.typography.titleLarge, modifier = Modifier.a11yHeading()) },
                navigationIcon = { BackButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (bags.isEmpty()) {
                item {
                    Text(
                        "Pack for a game night or an event: the decks, their tokens and counters, dice and playmat, the cards friends want and what to give back.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.textMuted
                    )
                }
            }
            items(bags, key = { it.id }) { b ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).clickable { onOpenBag(b.id) }.padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Icon(if (b.comingHome) Icons.Filled.Home else Icons.Filled.Backpack, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(b.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        Text(dayLabel(b.day, today) + if (b.comingHome) " · Coming home" else "", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                    }
                    Text("›", color = colors.textMuted)
                }
            }
            if (night.players.size > 1) {
                item {
                    LoanButton("Tonight's game night", primary = false, modifier = Modifier.fillMaxWidth()) {
                        draft = BagDraft(
                            "Game night", today,
                            night.players.filter { it.kind != NightPlayerKind.ME }.joinToString(", ") { it.name },
                            night.players.filter { it.kind == NightPlayerKind.ME }.mapNotNull { it.deckId }
                        )
                    }
                }
            }
            items(events.filter { !it.finished }, key = { "event:${it.id}" }) { e ->
                LoanButton(e.name, primary = false, modifier = Modifier.fillMaxWidth()) {
                    draft = BagDraft(e.name, dayOf(e.createdAt), e.players.joinToString(", ") { it.name }, emptyList())
                }
            }
            item {
                LoanButton("Pack for…", primary = true, modifier = Modifier.fillMaxWidth()) { draft = BagDraft("", today, "", emptyList()) }
            }
        }
    }

    draft?.let { d ->
        BagDialog(d, decks, title = "Pack for…", onDismiss = { draft = null }) { done ->
            val bag = newBag(UUID.randomUUID().toString(), done.name, done.day, namesFrom(done.who), done.deckIds, System.currentTimeMillis())
            EventBagStore.save(bag)
            draft = null
            onOpenBag(bag.id)
        }
    }
}

/** Name, day, who's coming and which decks: making a bag, or changing one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BagDialog(draft: BagDraft, decks: List<Deck>, title: String, onDismiss: () -> Unit, onSave: (BagDraft) -> Unit) {
    val colors = LocalAppColors.current
    var name by remember { mutableStateOf(draft.name) }
    var day by remember { mutableStateOf(draft.day) }
    var who by remember { mutableStateOf(draft.who) }
    var chosen by remember { mutableStateOf(draft.deckIds) }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.accent, unfocusedBorderColor = colors.border,
        focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary, cursorColor = colors.accent
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(title, color = colors.accentLight, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name", color = colors.textMuted) }, placeholder = { Text("Game night at Priya's", color = colors.textDim) }, singleLine = true, colors = fieldColors)
                OutlinedTextField(day, { day = it }, label = { Text("Day (YYYY-MM-DD)", color = colors.textMuted) }, singleLine = true, colors = fieldColors)
                OutlinedTextField(who, { who = it }, label = { Text("Who's coming", color = colors.textMuted) }, placeholder = { Text("Priya, Sam", color = colors.textDim) }, singleLine = true, colors = fieldColors)
                Text("With commas between. Friends' wants and what you borrowed from them go in the bag.", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                Text("Decks", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    decks.filter { it.archived != true && it.sample != true }.forEach { d ->
                        PickChip(d.name, d.id in chosen) { chosen = if (d.id in chosen) chosen - d.id else chosen + d.id }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(BagDraft(name, day.trim(), who, chosen)) }) { Text("Pack your bag", color = colors.accentLight) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textMuted) } }
    )
}

/** A chip that's on or off. */
@Composable
internal fun PickChip(label: String, on: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (on) colors.onAccent else colors.textPrimary,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (on) colors.accent else colors.surface2)
            .clickable(onClick = onClick)
            .heightIn(min = 32.dp)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

/** One bag's checklist: Decks, Tokens and extras, For trades; Coming home; All packed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackScreen(
    bagId: String,
    collections: List<Collection>,
    decks: List<Deck>,
    social: SocialRepository,
    onBack: () -> Unit
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    remember { EventBagStore.init(context) }
    val bags by EventBagStore.bags.collectAsState()
    val bag = bags.firstOrNull { it.id == bagId }
    val chosen = remember(bag?.deckIds, decks) { bag?.deckIds.orEmpty().mapNotNull { id -> decks.firstOrNull { it.id == id } } }
    var cards by remember { mutableStateOf<Map<String, ScryfallCard>>(emptyMap()) }
    val ids = remember(chosen) { chosen.flatMap { d -> (listOfNotNull(d.commander, d.partnerCommander) + d.cards).map { it.scryfallId } }.distinct() }
    LaunchedEffect(ids) {
        if (ids.isEmpty()) return@LaunchedEffect
        cards = runCatching { CardRepository().getCardsByIds(ids).associateBy { it.id } }.getOrDefault(emptyMap())
    }
    val available = rememberSocialMore(social)
    val account by social.accountFlow.collectAsState()
    val overview by social.overview.collectAsState()
    var matches by remember { mutableStateOf<List<TradeMatch>>(emptyList()) }
    var borrowed by remember { mutableStateOf<List<BorrowedFrom>>(emptyList()) }
    LaunchedEffect(available) {
        if (available != true) return@LaunchedEffect
        if (social.overview.value == null) runCatching { social.refresh() }
        matches = runCatching { social.more.tradeMatches() }.getOrDefault(emptyList())
    }
    LaunchedEffect(account?.userId) {
        if (account == null) return@LaunchedEffect
        runCatching { social.api.myBorrowedLoans() }.onSuccess { loans ->
            borrowed = loans.mapNotNull { l -> l.lender?.displayName?.let { it to l.cards.sumOf { c -> c.qty } } }
                .groupBy({ it.first }, { it.second }).map { (from, n) -> BorrowedFrom(from, n.sum()) }
        }
    }
    var editing by remember { mutableStateOf(false) }

    val lines = remember(bag, chosen, cards, collections, matches, overview, borrowed) {
        if (bag == null) emptyList() else {
            val placed = placesOf(collections).flatMap { cardsIn(collections, it.id) }
            val wants = friendsWantHere(matches, placed) { null }
                .mapNotNull { w -> overview?.person(w.friend)?.displayName?.let { WantedBy(it, w.cards.map { c -> c.card }) } }
            bagLines(BagInput(
                decks = chosen, collections = collections, gear = gearOf(collections),
                tokens = chosen.associate { it.id to tokensToBring(it, cards) },
                counters = chosen.associate { it.id to countersNeeded(it, cards) },
                wants = wants, borrowed = borrowed, attendees = bag.attendees
            ))
        }
    }

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        if (bag != null) Text(bagHeading(bag, dayOf(System.currentTimeMillis())), style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                        Text(if (bag?.comingHome == true) "Coming home" else "Pack your bag", style = MaterialTheme.typography.titleLarge, modifier = Modifier.a11yHeading())
                    }
                },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = { if (bag != null) TextButton(onClick = { editing = true }) { Text("Change", color = colors.accentLight) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        },
        bottomBar = {
            if (bag != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().background(colors.bg).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp)) {
                    LoanButton(if (bag.comingHome) "Packing" else "Coming home", primary = false) { EventBagStore.save(setComingHome(bag, !bag.comingHome)) }
                    LoanButton(if (bag.comingHome) "All home" else "All packed", primary = true, modifier = Modifier.weight(1f), enabled = !allTicked(bag, lines)) {
                        EventBagStore.save(tickAll(bag, lines))
                    }
                }
            }
        }
    ) { padding ->
        if (bag == null) {
            Text("This bag isn't on this phone any more.", color = colors.textMuted, modifier = Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        val shown = shownLines(bag, lines)
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (bag.comingHome) item { Text(homeSummary(bag, lines), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.textPrimary) }
            if (chosen.isEmpty() && !bag.comingHome) item { Text("No decks chosen yet — Change to pick the decks you're bringing.", color = colors.textMuted) }
            for (section in BagSection.entries) {
                val inSection = shown.filter { it.section == section }
                if (inSection.isEmpty()) continue
                item(key = section.name) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text(section.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary, modifier = Modifier.a11yHeading())
                        inSection.forEach { line ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { EventBagStore.save(toggleTick(bag, line.key)) }
                            ) {
                                Checkbox(
                                    checked = isTicked(bag, line.key),
                                    onCheckedChange = { EventBagStore.save(toggleTick(bag, line.key)) },
                                    colors = CheckboxDefaults.colors(checkedColor = colors.accent, checkmarkColor = colors.onAccent)
                                )
                                Text(line.title, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
                                if (line.detail.isNotEmpty()) Text(
                                    line.detail, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End,
                                    color = if (line.warn) colors.warning else colors.textMuted, modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
            if (bag.comingHome && shown.isEmpty()) item { Text("Nothing was ticked as packed, so there's nothing to check off.", color = colors.textMuted) }
            item {
                Text(
                    "Delete this bag", color = colors.accentLight, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { EventBagStore.delete(bag.id); onBack() }.padding(vertical = 8.dp)
                )
            }
        }
    }

    if (editing && bag != null) {
        BagDialog(BagDraft(bag.name, bag.day, bag.attendees.joinToString(", "), bag.deckIds), decks, title = "Change bag", onDismiss = { editing = false }) { d ->
            EventBagStore.save(bag.copy(name = d.name.trim().ifEmpty { bag.name }, day = d.day.ifEmpty { bag.day }, attendees = namesFrom(d.who), deckIds = d.deckIds))
            editing = false
        }
    }
}
