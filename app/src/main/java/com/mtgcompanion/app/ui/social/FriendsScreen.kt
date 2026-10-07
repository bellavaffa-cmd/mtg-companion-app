package com.mtgcompanion.app.ui.social

import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.common.a11yHeading
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.mtgcompanion.app.ui.common.SegmentedTabs
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.CollectionRepository
import com.mtgcompanion.app.data.social.ActivityTarget
import com.mtgcompanion.app.data.social.FriendsTab
import com.mtgcompanion.app.data.social.FriendsWaiting
import com.mtgcompanion.app.data.social.friendsTabCounts
import com.mtgcompanion.app.data.social.friendsTabFor
import com.mtgcompanion.app.data.social.friendsTabs
import com.mtgcompanion.app.data.social.Overview
import com.mtgcompanion.app.data.social.TradeMatch
import com.mtgcompanion.app.data.social.Pod
import com.mtgcompanion.app.data.social.Profile
import com.mtgcompanion.app.data.social.ShareKind
import com.mtgcompanion.app.data.social.SharedSummary
import com.mtgcompanion.app.data.social.SocialApi
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.common.SectionHeader
import com.mtgcompanion.app.ui.collection.HouseholdInvitesList
import com.mtgcompanion.app.ui.collection.HouseholdsState
import com.mtgcompanion.app.ui.collection.rememberHouseholds
import com.mtgcompanion.app.ui.common.readableWidth
import com.mtgcompanion.app.ui.theme.LocalAppColors
import androidx.compose.material.icons.filled.PersonAdd
import com.mtgcompanion.app.data.loanPeople
import com.mtgcompanion.app.data.loansOf
import com.mtgcompanion.app.data.social.FriendAction
import com.mtgcompanion.app.data.social.friendContext
import com.mtgcompanion.app.data.social.friendsTabLabel
import com.mtgcompanion.app.data.social.lentTo
import com.mtgcompanion.app.ui.common.rememberMoney
import kotlinx.coroutines.launch

/** Where the Friends tab's conversations, activity, cards for trade, trade matches and quick actions lead. */
data class FriendsMoreActions(
    val onOpenConversation: (friendId: String) -> Unit = {},
    val onOpenForTrade: () -> Unit = {},
    val onOpenActivity: (ActivityTarget) -> Unit = {},
    val onOpenMatch: (TradeMatch) -> Unit = {},
    /** Sharing storage at home: a household, by id, once an invitation is accepted (HouseholdScreen.kt). */
    val onOpenHousehold: (householdId: String) -> Unit = {},
    /** What the user lent: the Loans screen on Lent (a friend's "Loan" quick action). */
    val onOpenLent: () -> Unit = {},
    /** A game night's invite, by id, from the next game night card (NextGameNightCard.kt). */
    val onOpenNight: (nightId: String) -> Unit = {},
    /** A pod's chat, by pod id: from Chats, and a pod tapped on People (PodChatScreen.kt). */
    val onOpenPodChat: (podId: String) -> Unit = {},
    /** Plan a game night for a pod, by pod id (GameNightFormScreen). */
    val onPlanGameNight: (podId: String) -> Unit = {}
)

/**
 * Friends, a tab of the bottom bar, in four tabs (FriendsTabs.kt): People — the next game night,
 * your pods with their league, friends each with a line of context and a quick action, requests and
 * the way to what's shared; Chats (direct messages, pod chats above them); Trades — the trade inbox:
 * Your turn, Waiting on them, What friends want from you and Done; and Activity. Chats and Activity
 * need the server's social_more functions. Adding a friend, scanning a QR code and the user's own
 * profile open from the top bar. A tapped notification picks the tab (SocialRepository.openFriendsTab).
 * The web app's twin is src/pages/FriendsPage.tsx.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    social: SocialRepository,
    collectionRepository: CollectionRepository,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onScanQr: () -> Unit,
    onOpenFriend: (String) -> Unit,
    onOpenShared: (SharedSummary) -> Unit,
    onOpenSharedCollection: (String) -> Unit = {},
    onOpenSharedTab: () -> Unit = {},
    /** Loans: what friends have lent the user, and what the user lent (LoansScreen.kt). */
    onOpenLoans: () -> Unit = {},
    /** A counter-offer to a trade: the composer, for that friend. */
    onCounterTrade: (friendId: String) -> Unit = {},
    more: FriendsMoreActions = FriendsMoreActions()
) {
    val colors = LocalAppColors.current
    // The user's own profile, over the tabs; Back closes it. Friends is a tab of the bar, so it has
    // no back button of its own (Back goes Home, as from the other tabs).
    var profile by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = profile) { profile = false }
    val signedIn = social.accountFlow.collectAsState().value != null
    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = { Text(if (profile) "Your profile" else "Friends", style = MaterialTheme.typography.titleLarge, modifier = Modifier.a11yHeading()) },
                navigationIcon = { if (profile) BackButton(onClick = { profile = false }) },
                actions = {
                    if (!profile && signedIn) IconButton(onClick = { adding = true }) { Icon(Icons.Filled.PersonAdd, contentDescription = "Add a friend", tint = colors.accent) }
                    IconButton(onClick = onScanQr) { Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan a QR code", tint = colors.textPrimary) }
                    if (!profile) IconButton(onClick = { profile = true }) { Icon(Icons.Filled.AccountCircle, contentDescription = "Your profile and QR code", tint = colors.textPrimary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.readableWidth(680.dp)) {
                SocialGate(social, onSignIn) { overview ->
                    if (profile) ProfileTab(social, overview.me!!)
                    else FriendsContent(social, collectionRepository, overview, onOpenFriend, onOpenSharedTab, onOpenLoans, onCounterTrade, more)
                }
            }
        }
    }
    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            containerColor = colors.surface,
            text = { AddFriend(social, onShowQr = { adding = false; profile = true }) },
            confirmButton = { TextButton(onClick = { adding = false }) { Text("Close", color = colors.accent) } }
        )
    }
}

@Composable
private fun FriendsContent(
    social: SocialRepository,
    collectionRepository: CollectionRepository,
    overview: Overview,
    onOpenFriend: (String) -> Unit,
    onOpenSharedTab: () -> Unit,
    onOpenLoans: () -> Unit,
    onCounterTrade: (String) -> Unit,
    more: FriendsMoreActions
) {
    val me = overview.me!!
    val withMore = rememberSocialMore(social)
    // The tab asked for (a FriendsTab key); shown only among the tabs there are.
    var asked by rememberSaveable { mutableStateOf(FriendsTab.PEOPLE.key) }
    val pending by social.openFriendsTab.collectAsState()
    LaunchedEffect(pending) {
        pending?.let { asked = it; social.openFriendsTab.value = null }
    }
    val tabs = friendsTabs(withMore)
    val tab = friendsTabFor(asked, withMore)

    val dmUnread by social.unread.collectAsState()
    val podUnread by social.podUnread.collectAsState()
    val unread = dmUnread + podUnread
    LaunchedEffect(withMore, overview, tab) { if (withMore == true) runCatching { social.more.unread() }.onSuccess { social.setUnread(it) } }
    // Pod chats' unread count goes in "Chats · N" and the badge too (the Chats tab keeps it fresh while open).
    LaunchedEffect(overview, tab) {
        if (social.nights.check()) runCatching { social.nights.unread() }.onSuccess { social.setPodUnread(it) }
    }
    val requests = overview.friends.count { !it.accepted && it.incoming }
    val inbox = overview.trades.count { com.mtgcompanion.app.data.social.waitingOnMe(it, me.userId) }
    // The counts go in the labels: "Chats · 3", "Trades · 2".
    val counts = friendsTabCounts(tabs, FriendsWaiting(requests = requests, unread = unread, trades = inbox))

    Column(Modifier.fillMaxSize()) {
        SegmentedTabs(
            labels = tabs.mapIndexed { i, t -> friendsTabLabel(t, counts[i] ?: 0) },
            selected = tabs.indexOf(tab).coerceAtLeast(0),
            onSelect = { asked = tabs[it].key },
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 6.dp)
        )
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (tab) {
                FriendsTab.PEOPLE -> PeopleTab(social, collectionRepository, overview, onOpenFriend, onOpenSharedTab, more)
                FriendsTab.MESSAGES -> ConversationList(social, overview, more.onOpenConversation, onOpenPodChat = more.onOpenPodChat)
                FriendsTab.TRADES -> TradesTab(social, collectionRepository, overview, withMore == true, onOpenLoans, onCounterTrade, more)
                // Friends' feed with its new kinds (ActivityFeed.kt): each item opens where it's about.
                FriendsTab.ACTIVITY -> ActivityList(social, header = {}, onOpen = more.onOpenActivity)
            }
        }
    }
}

/**
 * People: the next game night, Your pods, friend requests, friends — each with one line of context
 * and a quick action (FriendsHub.kt's friendContext) — requests the user sent, and what's shared.
 */
@Composable
private fun PeopleTab(
    social: SocialRepository,
    collectionRepository: CollectionRepository,
    overview: Overview,
    onOpenFriend: (String) -> Unit,
    onOpenSharedTab: () -> Unit,
    more: FriendsMoreActions
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val me = overview.me!!
    var podDialog by remember { mutableStateOf<Pod?>(null) }
    var newPod by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var homesReload by remember { mutableIntStateOf(0) }
    val homes = rememberHouseholds(social, homesReload)
    val incoming = overview.friends.filter { !it.accepted && it.incoming }
    val outgoing = overview.friends.filter { !it.accepted && !it.incoming }
    val friends = overview.acceptedFriends.sortedBy { overview.person(it.userId)?.displayName?.lowercase() }

    // Each friend's line: what they want of the user's, what they have on loan, a shelf shared at home.
    val collections by collectionRepository.collectionsFlow.collectAsState(initial = emptyList())
    val wants = rememberWantsFromYou(social, overview, collections)
    val loans = remember(collections) { loansOf(collections) }
    val today = remember { java.time.LocalDate.now().toString() }
    val dues = remember(loans, today) { loanPeople(loans, today, emptyList()) }
    val households = (homes as? HouseholdsState.Ready)?.data?.households.orEmpty()
    val leagues = rememberPodLeagues(social, overview.pods, me.userId)
    val money = rememberMoney()
    // Pod chat and game nights (supabase/migrations/20261006070000_game_nights_chat.sql).
    val podChat = social.nights.available.collectAsState().value == true
    LaunchedEffect(Unit) { social.nights.check() }

    fun run(action: suspend () -> Unit) {
        scope.launch {
            error = null
            try { action(); social.refresh() } catch (e: Exception) { error = e.message }
        }
    }

    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // The next game night (NextGameNightCard.kt): nothing until there's one to show.
        item(key = "next-night") { NextGameNightCard(social, more.onOpenNight) }
        error?.let { item { Notice(it, warn = true) } }

        if (incoming.isNotEmpty()) {
            item { SectionHeader("Friend requests") }
            incoming.forEach { f ->
                item(key = "in-${f.userId}") {
                    PersonRow(overview.person(f.userId)) {
                        TextButton(onClick = { run { social.api.respondFriend(f.userId, false) } }) { Text("Decline", color = colors.textMuted) }
                        GoldButton("Accept", { run { social.api.respondFriend(f.userId, true) } })
                    }
                }
            }
        }

        // Invitations to share storage at home (HouseholdScreen.kt); nothing before the server has households.
        val homeInvites = (homes as? HouseholdsState.Ready)?.data?.invites.orEmpty()
        if (homeInvites.isNotEmpty()) {
            item { SectionHeader("Sharing storage at home") }
            item(key = "home-invites") { HouseholdInvitesList(homeInvites, social, onDone = { accepted -> homesReload++; if (accepted != null) more.onOpenHousehold(accepted) }) }
        }

        item { SectionHeader("Your pods", action = "New pod", onAction = { newPod = true }) }
        if (overview.pods.isEmpty()) {
            item { Notice("A pod is a group of friends — your playgroup. Share a deck with a whole pod at once.") }
        } else {
            // A pod opens its chat once the server has pod chat (before that, its members to edit as
            // before); each card also offers Plan a game night, and Members.
            item(key = "pods") {
                PodsRow(
                    overview.pods, leagues,
                    onOpen = { pod -> if (podChat) more.onOpenPodChat(pod.id) else podDialog = pod },
                    onPlan = if (podChat) ({ pod -> more.onPlanGameNight(pod.id) }) else null,
                    onEdit = if (podChat) ({ pod -> podDialog = pod }) else null
                )
            }
        }

        item { SectionHeader(if (friends.isEmpty()) "Friends" else "Friends · ${friends.size}") }
        if (friends.isEmpty()) {
            item { Notice("No friends yet. Add someone by their username, or let them scan your QR code.") }
        }
        friends.forEach { f ->
            item(key = "f-${f.userId}") {
                val want = wants.firstOrNull { it.friend == f.userId }
                val home = households.firstOrNull { h -> h.members.any { it.isMember && it.profile.userId == f.userId } }
                val context = friendContext(
                    wants = want?.cards ?: 0,
                    wantsValue = want?.value?.let { money.format(it, whole = true) },
                    lent = lentTo(loans, f.userId),
                    lentDue = dues.firstOrNull { it.friendId == f.userId }?.label,
                    sharesHome = home != null
                )
                val shares = overview.sharedWithMe.count { it.owner == f.userId }
                PersonRow(
                    overview.person(f.userId),
                    detail = context?.line ?: if (shares > 0) "Shares $shares with you" else overview.person(f.userId)?.handle,
                    onClick = { onOpenFriend(f.userId) }
                ) {
                    if (context == null) Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.textDim)
                    else TextButton(onClick = {
                        when (context.action) {
                            FriendAction.TRADE -> want?.let { more.onOpenMatch(it.match) }
                            FriendAction.LOAN -> more.onOpenLent()
                            FriendAction.HOME -> home?.let { more.onOpenHousehold(it.id) }
                        }
                    }) { Text(context.action.label, color = if (context.action == FriendAction.TRADE) colors.accent else colors.textMuted) }
                }
            }
        }

        if (outgoing.isNotEmpty()) {
            item { SectionHeader("Waiting for an answer") }
            outgoing.forEach { f ->
                item(key = "out-${f.userId}") {
                    PersonRow(overview.person(f.userId)) {
                        TextButton(onClick = { run { social.api.removeFriend(f.userId) } }) { Text("Cancel", color = colors.textMuted) }
                    }
                }
            }
        }

        // What friends share lives on Collection's Shared page; this is the way there.
        val sharers = overview.sharedWithMe.map { it.owner }.distinct()
        item { SectionHeader("Shared with you") }
        item(key = "shared-link") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).clickable(onClick = onOpenSharedTab).padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Box(Modifier.size(width = 62.dp, height = 34.dp)) {
                    sharers.take(3).forEachIndexed { i, m -> Avatar(overview.person(m), 32.dp, Modifier.offset(x = (i * 15).dp)) }
                    if (sharers.isEmpty()) Icon(Icons.Filled.Collections, contentDescription = null, tint = colors.accent, modifier = Modifier.align(Alignment.Center))
                }
                Column(Modifier.weight(1f)) {
                    Text("See what friends share", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (sharers.isEmpty()) "Nothing shared with you yet" else "${sharers.size} ${if (sharers.size == 1) "friend shares" else "friends share"} with you — in Collection",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted
                    )
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.textDim)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (newPod || podDialog != null) {
        PodDialog(social, overview, podDialog) { newPod = false; podDialog = null }
    }
}

/**
 * Trades: the trade inbox (TradesScreen.kt's TradeInboxList) — Your turn, Waiting on them, What
 * friends want from you and Done — then the way to the user's cards for trade and to loans.
 */
@Composable
private fun TradesTab(
    social: SocialRepository,
    collectionRepository: CollectionRepository,
    overview: Overview,
    withMore: Boolean,
    onOpenLoans: () -> Unit,
    onCounterTrade: (String) -> Unit,
    more: FriendsMoreActions
) {
    // What friends have lent the user (supabase/migrations/20261006010000_loans.sql) — nothing if the server can't say.
    var borrowed by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { runCatching { social.api.myBorrowedLoans() }.onSuccess { l -> borrowed = l.sumOf { b -> b.cards.sumOf { it.qty } } } }
    val collections by collectionRepository.collectionsFlow.collectAsState(initial = emptyList())
    val wants = rememberWantsFromYou(social, overview, collections)
    TradeInboxList(
        social, collectionRepository, overview, onCounterTrade, more.onOpenConversation,
        wants = {
            if (wants.isNotEmpty()) {
                item(key = "wants-h") { SectionHeader("What friends want from you") }
                item(key = "wants") { WantsFromYouCard(overview, wants, more.onOpenMatch) }
            }
        },
        footer = {
            item(key = "links-h") { Spacer(Modifier.height(4.dp)) }
            if (withMore) item(key = "for-trade") { LinkRow(Icons.Filled.Sell, "Your cards for trade", more.onOpenForTrade) }
            item(key = "loans") {
                LinkRow(
                    Icons.Filled.Handshake,
                    if (borrowed > 0) "Borrowed / Lent · $borrowed ${if (borrowed == 1) "card" else "cards"} borrowed" else "Borrowed / Lent",
                    onOpenLoans
                )
            }
        }
    )
}

/** A row that leads somewhere else: an icon, its name and a chevron. */
@Composable
private fun LinkRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).clickable(onClick = onClick).padding(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = colors.accent)
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.textDim)
    }
}

/** The user's own profile: how others see them, their QR code, editing it, and notifications. */
@Composable
private fun ProfileTab(social: SocialRepository, me: Profile) {
    val colors = LocalAppColors.current
    var editing by rememberSaveable { mutableStateOf(false) }
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        val card = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.surface)
        if (editing) {
            Column(card.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Edit profile", style = MaterialTheme.typography.titleSmall)
                ProfileEditor(social) { editing = false }
            }
        } else {
            Column(card.padding(vertical = 24.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Avatar(me, 112.dp)
                Text(me.displayName, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.a11yHeading().padding(top = 8.dp))
                Text(me.handle, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                LineButton("Edit profile", { editing = true }, modifier = Modifier.padding(top = 8.dp), icon = { Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) })
            }
        }
        Column(card.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Add me as a friend", style = MaterialTheme.typography.titleSmall)
            QrCode(SocialApi.friendLink(me.username), 220.dp, "QR code to add ${me.handle}")
            Text(
                "Friends scan this with the app's scanner or their phone's camera — or add ${me.handle}.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                textAlign = TextAlign.Center
            )
        }
        NotificationsSection(social)
        Spacer(Modifier.height(16.dp))
    }
}

/** A friend's collection as a whole — every binder they share with the user — opened as one. */
@Composable
fun WholeCollectionRow(name: String, binders: List<SharedSummary>, whole: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.surface)
            .border(BorderStroke(1.dp, colors.accentDim), RoundedCornerShape(22.dp)).clickable(onClick = onClick).padding(10.dp)
    ) {
        Box(Modifier.size(width = 60.dp, height = 46.dp).clip(RoundedCornerShape(12.dp)).background(colors.surface2), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.CollectionsBookmark, contentDescription = null, tint = colors.accent)
        }
        Column(Modifier.weight(1f)) {
            Text(if (whole) "$name's collection" else "Everything $name shares", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(if (whole) "Whole collection" else "All shared binders", "${binders.size} ${if (binders.size == 1) "binder" else "binders"}", "${binders.sumOf { it.cards }} cards").joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted
            )
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.textDim)
    }
}

@Composable
fun SharedRow(item: SharedSummary, owner: Profile?, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.surface).clickable(onClick = onClick).padding(10.dp)
    ) {
        Box(Modifier.size(width = 60.dp, height = 46.dp).clip(RoundedCornerShape(12.dp)).background(colors.surface2), contentAlignment = Alignment.Center) {
            if (item.cover != null) {
                AsyncImage(model = item.cover.toArtCropUrl(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(if (item.kind == ShareKind.DECK) Icons.Filled.Style else Icons.Filled.Collections, contentDescription = null, tint = colors.textDim)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(item.name ?: "Untitled", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val what = if (item.kind == ShareKind.DECK) "Deck" else "Binder"
            Text(listOfNotNull(what, "${item.cards} cards", owner?.displayName).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.textDim)
    }
}

@Composable
private fun AddFriend(social: SocialRepository, onShowQr: () -> Unit) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    fun add() {
        val name = username.trim().removePrefix("@").lowercase()
        if (name.isEmpty() || busy) return
        busy = true
        message = null
        scope.launch {
            message = try {
                val result = social.api.requestFriend(name)
                username = ""
                social.refresh()
                true to when (result) {
                    "accepted" -> "You and @$name are now friends."
                    "already" -> "You've already asked @$name."
                    else -> "Asked @$name — they'll see your request."
                }
            } catch (e: Exception) {
                false to (e.message ?: "Something went wrong.")
            } finally {
                busy = false
            }
        }
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Add a friend", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it.filterNot(Char::isWhitespace).take(21) },
                placeholder = { Text("their username") },
                prefix = { Text("@", color = colors.textDim) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { add() }),
                colors = socialFieldColors(),
                modifier = Modifier.weight(1f)
            )
            GoldButton("Add", ::add, enabled = !busy && username.isNotBlank())
        }
        message?.let { (ok, text) -> Text(text, style = MaterialTheme.typography.bodySmall, color = if (ok) colors.textMuted else colors.error) }
        TextButton(onClick = onShowQr) { Text("Show my QR code", color = colors.accent) }
    }
}

@Composable
private fun PodDialog(social: SocialRepository, overview: Overview, pod: Pod?, onClose: () -> Unit) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val me = overview.me!!
    val mine = pod == null || pod.owner == me.userId
    var name by remember { mutableStateOf(pod?.name ?: "") }
    var members by remember { mutableStateOf(pod?.members.orEmpty().filter { it != me.userId }) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Anyone already in the pod stays listed even if no longer a friend.
    val choices = (overview.acceptedFriends.map { it.userId } + members).distinct()

    fun run(action: suspend () -> Unit) {
        busy = true
        error = null
        scope.launch {
            try { action(); social.refresh(); onClose() } catch (e: Exception) { error = e.message } finally { busy = false }
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = colors.surface,
        title = { Text(if (pod == null) "New pod" else if (mine) "Edit pod" else pod.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mine) {
                    OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text("Name") }, placeholder = { Text("e.g. Friday night Commander") }, singleLine = true, colors = socialFieldColors(), modifier = Modifier.fillMaxWidth())
                    Text("Who's in it", style = MaterialTheme.typography.labelLarge, color = colors.textMuted)
                    if (choices.isEmpty()) Text("Add some friends first.", style = MaterialTheme.typography.bodySmall, color = colors.textDim)
                    Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        choices.forEach { id ->
                            val on = id in members
                            PersonRow(overview.person(id), compact = true, avatarSize = 32.dp, onClick = { members = if (on) members - id else members + id }) {
                                Icon(if (on) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, contentDescription = if (on) "In the pod" else "Not in the pod", tint = if (on) colors.accent else colors.textDim)
                            }
                        }
                    }
                    if (confirmDelete) Notice("Deleting the pod removes it for everyone in it, and stops sharing with it.", warn = true)
                } else {
                    Text("Made by ${overview.person(pod!!.owner)?.displayName ?: "someone"}.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    pod.members.forEach { PersonRow(overview.person(it), compact = true, avatarSize = 32.dp) }
                }
                error?.let { Notice(it, warn = true) }
            }
        },
        confirmButton = {
            if (mine) GoldButton(if (pod == null) "Create pod" else "Save", { run { social.api.savePod(pod?.id, name.trim(), members) } }, enabled = !busy && name.isNotBlank())
            else TextButton(onClick = onClose) { Text("Close", color = colors.accent) }
        },
        dismissButton = {
            when {
                pod != null && mine && !confirmDelete -> TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = colors.error) }
                pod != null && mine -> TextButton(onClick = { run { social.api.leavePod(pod.id) } }, enabled = !busy) { Text("Delete for everyone", color = colors.error) }
                pod != null -> TextButton(onClick = { run { social.api.leavePod(pod.id) } }, enabled = !busy) { Text("Leave pod", color = colors.error) }
                else -> TextButton(onClick = onClose) { Text("Cancel", color = colors.textMuted) }
            }
        }
    )
}
