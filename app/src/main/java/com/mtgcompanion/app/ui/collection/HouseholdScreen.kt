package com.mtgcompanion.app.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.PlaceKind
import com.mtgcompanion.app.data.StoragePlace
import com.mtgcompanion.app.data.placesOf
import com.mtgcompanion.app.data.savePlace
import com.mtgcompanion.app.data.social.DEFAULT_HOUSEHOLD_NAME
import com.mtgcompanion.app.data.social.HOUSEHOLD_UNAVAILABLE
import com.mtgcompanion.app.data.social.Household
import com.mtgcompanion.app.data.social.HouseholdCards
import com.mtgcompanion.app.data.social.HouseholdInvite
import com.mtgcompanion.app.data.social.MyHouseholds
import com.mtgcompanion.app.data.social.ShelfCopy
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.data.social.andList
import com.mtgcompanion.app.data.social.deckNote
import com.mtgcompanion.app.data.social.householdError
import com.mtgcompanion.app.data.social.householdIntro
import com.mtgcompanion.app.data.social.householdOfPlace
import com.mtgcompanion.app.data.social.invitedOf
import com.mtgcompanion.app.data.social.membersOf
import com.mtgcompanion.app.data.social.myShelfCopies
import com.mtgcompanion.app.data.social.peopleTotals
import com.mtgcompanion.app.data.social.placeContents
import com.mtgcompanion.app.data.social.placeLines
import com.mtgcompanion.app.data.social.shelfLoanLine
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.common.SectionHeader
import com.mtgcompanion.app.ui.common.a11yHeading
import com.mtgcompanion.app.ui.common.rememberMoney
import com.mtgcompanion.app.ui.social.Avatar
import com.mtgcompanion.app.ui.social.GoldButton
import com.mtgcompanion.app.ui.social.PersonRow
import com.mtgcompanion.app.ui.theme.LocalAppColors
import com.mtgcompanion.app.ui.theme.NumberStyle
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/** Where the user's households are, for a screen: signed out, asking, not on the server yet, failed, or loaded. */
sealed interface HouseholdsState {
    data object SignedOut : HouseholdsState
    data object Loading : HouseholdsState
    data object Unavailable : HouseholdsState
    data class Failed(val message: String) : HouseholdsState
    data class Ready(val data: MyHouseholds) : HouseholdsState
}

/** The user's households, loaded when the screen opens; bump [reload] to load them again. */
@Composable
fun rememberHouseholds(social: SocialRepository, reload: Int = 0): HouseholdsState {
    val account by social.accountFlow.collectAsState()
    var state by remember { mutableStateOf<HouseholdsState>(HouseholdsState.Loading) }
    LaunchedEffect(account?.userId, reload) {
        if (account == null) { state = HouseholdsState.SignedOut; return@LaunchedEffect }
        state = try {
            if (!social.household.check()) HouseholdsState.Unavailable else HouseholdsState.Ready(social.household.mine())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            HouseholdsState.Failed(householdError(e))
        }
    }
    return if (account == null) HouseholdsState.SignedOut else state
}

private fun count(n: Int): String = NumberFormat.getIntegerInstance(Locale.UK).format(n)

/**
 * Sharing storage at home, the web app's HouseholdPage (src/pages/HouseholdPage.tsx): a household of
 * friends who keep cards on the same shelf, each still owning their own (data/social/Household.kt).
 * Each person's copies and their value; each shared place with how many each person keeps there —
 * someone else's place reads "you can see, not change"; how pull lists ask them for cards; Stop
 * sharing and Invite someone. [householdId] null: the user's households and invitations (or the one
 * household, when that's all there is).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseholdScreen(
    householdId: String?,
    collections: List<Collection>,
    social: SocialRepository,
    onBack: () -> Unit,
    onOpenHousehold: (String) -> Unit,
    onOpenPlace: (String) -> Unit,
    onChange: (StorageChange) -> Unit
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    val state = rememberHouseholds(social, reload)
    var error by remember { mutableStateOf<String?>(null) }
    val ready = state as? HouseholdsState.Ready
    val h = ready?.data?.let { d ->
        if (householdId != null) d.households.firstOrNull { it.id == householdId }
        else d.households.singleOrNull()?.takeIf { d.invites.isEmpty() }
    }
    if (h != null) {
        HouseholdView(h, collections, social, onBack, onOpenPlace, onChange, onChanged = { reload++ }, onLeft = { reload++; onBack() })
        return
    }

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("Sharing storage at home", style = MaterialTheme.typography.titleLarge, modifier = Modifier.a11yHeading()) },
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
            when (state) {
                HouseholdsState.SignedOut -> item { Text("Sign in to share storage with someone you live with.", color = colors.textMuted) }
                HouseholdsState.Loading -> item { Text("Loading…", color = colors.textMuted) }
                HouseholdsState.Unavailable -> item { Text(HOUSEHOLD_UNAVAILABLE, color = colors.textMuted) }
                is HouseholdsState.Failed -> item { Text(state.message, color = colors.textMuted) }
                is HouseholdsState.Ready -> {
                    val d = state.data
                    item {
                        Text(
                            "Keep cards on the same shelf as someone you live with. Each of you still owns your own cards: they see what you keep in the places you share, and can't change it.",
                            style = MaterialTheme.typography.bodyMedium, color = colors.textMuted
                        )
                    }
                    if (householdId != null) item { Text("You're not sharing that shelf any more.", color = colors.textMuted) }
                    if (d.invites.isNotEmpty()) {
                        item { SectionHeader("Asked to share") }
                        item { HouseholdInvitesList(d.invites, social, onDone = { accepted -> reload++; if (accepted != null) onOpenHousehold(accepted) }) }
                    }
                    if (d.households.isNotEmpty()) item { SectionHeader("Your shared shelves") }
                    d.households.forEach { x ->
                        item(key = x.id) {
                            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp)) {
                                PlaceRow(Icons.Filled.Home, x.name, membersOf(x, "").joinToString(", ") { it.profile.displayName }, "›", gold = true, onClick = { onOpenHousehold(x.id) })
                            }
                        }
                    }
                    if (d.households.isEmpty()) item {
                        LoanButton("Start sharing", primary = true, modifier = Modifier.fillMaxWidth()) {
                            scope.launch {
                                error = null
                                try {
                                    val id = social.household.create(DEFAULT_HOUSEHOLD_NAME)
                                    reload++
                                    onOpenHousehold(id)
                                } catch (e: Exception) {
                                    error = householdError(e)
                                }
                            }
                        }
                    }
                    error?.let { e -> item { Text(e, color = colors.error, style = MaterialTheme.typography.bodySmall) } }
                }
            }
        }
    }
}

/** Invitations to share storage, with Decline and Accept — on the household screen and on Friends. */
@Composable
fun HouseholdInvitesList(invites: List<HouseholdInvite>, social: SocialRepository, onDone: (accepted: String?) -> Unit) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    fun answer(inv: HouseholdInvite, yes: Boolean) {
        scope.launch {
            error = null
            try {
                social.household.respond(inv.id, yes)
                onDone(if (yes) inv.id else null)
            } catch (e: Exception) {
                error = householdError(e)
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        invites.forEach { inv ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(inv.invitedBy, 44.dp)
                    Column(Modifier.weight(1f)) {
                        Text("${inv.invitedBy?.displayName ?: "Someone"} asked you to share storage at home", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                        Text("${inv.name} · each of you still owns your own cards", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    LoanButton("Decline", primary = false, modifier = Modifier.weight(1f)) { answer(inv, false) }
                    LoanButton("Accept", primary = true, modifier = Modifier.weight(1f)) { answer(inv, true) }
                }
            }
        }
        error?.let { Text(it, color = colors.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HouseholdView(
    h: Household,
    collections: List<Collection>,
    social: SocialRepository,
    onBack: () -> Unit,
    onOpenPlace: (String) -> Unit,
    onChange: (StorageChange) -> Unit,
    onChanged: () -> Unit,
    onLeft: () -> Unit
) {
    val colors = LocalAppColors.current
    val money = rememberMoney()
    val scope = rememberCoroutineScope()
    val me = social.userId.orEmpty()
    val overview by social.overview.collectAsState()
    LaunchedEffect(Unit) { if (social.overview.value == null) social.refresh() }
    var cards by remember { mutableStateOf<HouseholdCards?>(null) }
    var cardsError by remember { mutableStateOf<String?>(null) }
    var loadCards by remember { mutableIntStateOf(0) }
    LaunchedEffect(h, loadCards) {
        try { cards = social.household.cards(h.id); cardsError = null } catch (e: Exception) { cardsError = householdError(e) }
    }
    var open by remember { mutableStateOf<String?>(null) }
    var inviting by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val sharedIds = remember(h, me) { h.places.filter { me in it.users }.map { it.placeId } }
    val copies: List<ShelfCopy> = remember(collections, sharedIds, me, cards) { myShelfCopies(collections, sharedIds, me) + cards?.copies.orEmpty() }
    val ids = remember(copies) { copies.map { it.scryfallId }.distinct().sorted() }
    var prices by remember { mutableStateOf<Map<String, Pair<Double?, Double?>>?>(null) }
    LaunchedEffect(ids) {
        if (ids.isEmpty()) { prices = emptyMap(); return@LaunchedEffect }
        prices = runCatching { CardRepository().getCardsByIds(ids).associate { it.id to (it.prices?.usd?.toDoubleOrNull() to it.prices?.usdFoil?.toDoubleOrNull()) } }.getOrNull()
    }
    val price: ((ShelfCopy) -> Double?)? = prices?.let { p ->
        { c: ShelfCopy -> p[c.scryfallId]?.let { (plain, foil) -> (if (c.foil) foil ?: plain else plain ?: foil)?.takeIf { it > 0 } } }
    }
    val totals = peopleTotals(h, me, copies, price)
    val lines = placeLines(h, me, copies)
    val myPlaces = placesOf(collections)
    val unshared = myPlaces.filter { p -> h.places.none { it.placeId == p.id } }
    val inHousehold = h.members.map { it.profile.userId }.toSet()
    val friends = overview?.acceptedFriends.orEmpty().filter { it.userId !in inHousehold }
    val loans = cards?.loans.orEmpty().filter { it.lender == me || it.borrower == me }

    fun act(run: suspend () -> Unit) {
        scope.launch {
            error = null
            try { run(); onChanged(); loadCards++ } catch (e: Exception) { error = householdError(e) }
        }
    }
    // "Keep my cards here too": the place goes in the user's own storage (same id), then the household is told.
    fun join(placeId: String) = act {
        val p = h.places.firstOrNull { it.placeId == placeId } ?: return@act
        if (myPlaces.none { it.id == placeId }) onChange { savePlace(it, StoragePlace(p.placeId, p.name, PlaceKind.fromName(p.kind).name, createdAt = System.currentTimeMillis())) }
        social.household.joinPlace(h.id, placeId)
    }

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = { Text(h.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.a11yHeading(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(colors.bg).navigationBarsPadding()) {
                HorizontalDivider(color = colors.border)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp)) {
                    Button(
                        onClick = { leaving = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.surface2, contentColor = colors.textPrimary),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) { Text("Stop sharing", fontWeight = FontWeight.Bold) }
                    Button(
                        onClick = { inviting = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) { Text("Invite someone", fontWeight = FontWeight.ExtraBold) }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item { Text(householdIntro(h, me), style = MaterialTheme.typography.bodyMedium, color = colors.textMuted) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    totals.forEach { t ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(12.dp)) {
                            Text(t.label, style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                            Text(count(t.copies), style = NumberStyle(28), color = colors.textPrimary)
                            Text(
                                (if (t.copies == 1) "copy" else "copies") + (t.usd?.let { " · ${money.format(it, whole = true)}" } ?: ""),
                                style = MaterialTheme.typography.labelSmall, color = colors.textMuted
                            )
                        }
                    }
                }
            }
            cardsError?.let { e -> item { Text(e, color = colors.error, style = MaterialTheme.typography.bodySmall) } }
            lines.forEach { l ->
                item(key = "place-${l.place.placeId}") {
                    val isOpen = open == l.place.placeId
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { open = if (isOpen) null else l.place.placeId }.padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(l.place.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(l.line, style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                            }
                            Icon(if (isOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (isOpen) "Hide what's in it" else "Show what's in it", tint = colors.textMuted)
                        }
                        if (isOpen) {
                            Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                val groups = placeContents(h, me, l.place.placeId, copies)
                                if (groups.isEmpty()) Text("Nothing kept here yet.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                                groups.forEach { g ->
                                    Text(
                                        (if (g.userId == me) "Yours" else "${g.name}'s") + if (g.userId != me) " · you can see, not change" else "",
                                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = colors.textMuted, modifier = Modifier.padding(top = 6.dp)
                                    )
                                    g.copies.take(200).forEach { c ->
                                        Row(Modifier.fillMaxWidth()) {
                                            Text(c.name + if (c.foil) " · foil" else "", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("×${c.qty}", style = MaterialTheme.typography.bodyMedium, color = colors.textDim)
                                        }
                                    }
                                    if (g.copies.size > 200) Text("and ${g.copies.size - 200} more", style = MaterialTheme.typography.bodySmall, color = colors.textDim)
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                                    val wide = Modifier.fillMaxWidth()
                                    if (!l.readOnly && myPlaces.any { it.id == l.place.placeId }) LoanButton("Open", primary = false, modifier = wide) { onOpenPlace(l.place.placeId) }
                                    if (l.canJoin) LoanButton("Keep my cards here too", primary = false, modifier = wide) { join(l.place.placeId) }
                                    if (l.mine) LoanButton("Stop sharing this place", primary = false, modifier = wide) { act { social.household.unsharePlace(h.id, l.place.placeId) } }
                                    if (!l.mine && !l.readOnly) LoanButton("Take my cards off", primary = false, modifier = wide) { act { social.household.unsharePlace(h.id, l.place.placeId) } }
                                }
                            }
                        }
                    }
                }
            }
            item {
                LoanButton(if (myPlaces.isEmpty()) "Make a place in Storage first" else "Share a place", primary = false, modifier = Modifier.fillMaxWidth(), enabled = unshared.isNotEmpty()) { sharing = true }
            }
            item {
                Column(
                    Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("When you build a deck", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary, modifier = Modifier.a11yHeading())
                    Text(deckNote(h, me), style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                }
            }
            if (loans.isNotEmpty()) {
                item { SectionHeader("Borrowed from the shelf") }
                loans.forEach { l ->
                    item(key = "loan-${l.id}") {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(shelfLoanLine(h, me, l), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                            Text(l.cards.joinToString(", ") { if (it.qty > 1) "${it.name} ×${it.qty}" else it.name }, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                            LoanButton(if (l.lender == me) "Got them back" else "Gave them back", primary = false) { act { social.household.loanReturned(l.id) } }
                        }
                    }
                }
            }
            val invited = invitedOf(h)
            if (invited.isNotEmpty()) {
                item { SectionHeader("Asked, waiting for an answer") }
                invited.forEach { m ->
                    item(key = "inv-${m.profile.userId}") {
                        PersonRow(m.profile) {
                            TextButton(onClick = { act { social.household.cancelInvite(h.id, m.profile.userId) } }) { Text("Cancel", color = colors.textMuted) }
                        }
                    }
                }
            }
            error?.let { e -> item { Text(e, color = colors.error, style = MaterialTheme.typography.bodySmall) } }
        }
    }

    if (inviting) {
        AlertDialog(
            onDismissRequest = { inviting = false },
            containerColor = colors.surface,
            title = { Text("Invite someone", color = colors.accentLight, style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (friends.isEmpty()) "Add them as a friend first: invitations go to friends." else "They see what you keep in the places you share, and can’t change it.",
                        style = MaterialTheme.typography.bodySmall, color = colors.textMuted
                    )
                    friends.forEach { f ->
                        PersonRow(overview?.person(f.userId), compact = true, onClick = { inviting = false; act { social.household.invite(h.id, f.userId) } })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { inviting = false }) { Text("Close", color = colors.textMuted) } }
        )
    }
    if (sharing) {
        PlacePickerDialog("Share a place", unshared, onDismiss = { sharing = false }) { id ->
            sharing = false
            val p = myPlaces.firstOrNull { it.id == id }
            if (p != null) act { social.household.sharePlace(h.id, p) }
        }
    }
    if (leaving) {
        AlertDialog(
            onDismissRequest = { leaving = false },
            containerColor = colors.surface,
            title = { Text("Stop sharing?", color = colors.accentLight) },
            text = { Text("Your cards stay yours and where they are. The others stop seeing what you keep in the shared places, and you stop seeing theirs.", color = colors.textMuted) },
            confirmButton = {
                TextButton(onClick = {
                    leaving = false
                    scope.launch {
                        try { social.household.leave(h.id); onLeft() } catch (e: Exception) { error = householdError(e) }
                    }
                }) { Text("Stop sharing", color = colors.accent) }
            },
            dismissButton = { TextButton(onClick = { leaving = false }) { Text("Keep sharing", color = colors.textMuted) } }
        )
    }
}

/**
 * Sharing storage at home, from a place: "Shared with Alex · Shared shelf" when it's shared in a
 * household (opens it), or "Share at home" when the user has one and this place isn't in it yet.
 * Nothing when households aren't there (signed out, or the server doesn't have them yet).
 */
@Composable
fun HomeShareLine(place: StoragePlace, social: SocialRepository, onOpenHousehold: (String) -> Unit) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    val state = rememberHouseholds(social, reload)
    var error by remember { mutableStateOf<String?>(null) }
    val all = (state as? HouseholdsState.Ready)?.data?.households.orEmpty()
    if (all.isEmpty()) return
    val me = social.userId.orEmpty()
    val h = householdOfPlace(all, place.id)
    if (h != null) {
        val others = membersOf(h, me).filter { it.profile.userId != me }.map { it.profile.displayName }
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp)) {
            PlaceRow(
                Icons.Filled.Home,
                if (others.isNotEmpty()) "Shared with ${andList(others)}" else "Shared at home",
                "${h.name} · each of you still owns your own cards", "›", gold = true, onClick = { onOpenHousehold(h.id) }
            )
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        GoldButton("Share at home · ${all[0].name}", {
            scope.launch {
                error = null
                try { social.household.sharePlace(all[0].id, place); reload++ } catch (e: Exception) { error = householdError(e) }
            }
        }, icon = { Icon(Icons.Filled.Home, contentDescription = null, modifier = Modifier.size(18.dp)) })
        error?.let { Text(it, color = colors.error, style = MaterialTheme.typography.bodySmall) }
    }
}
