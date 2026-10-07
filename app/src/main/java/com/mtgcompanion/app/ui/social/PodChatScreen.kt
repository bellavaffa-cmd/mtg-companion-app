package com.mtgcompanion.app.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.activeDecks
import com.mtgcompanion.app.data.seasonTable
import com.mtgcompanion.app.data.social.CHAT_UNAVAILABLE
import com.mtgcompanion.app.data.social.ChatItem
import com.mtgcompanion.app.data.social.NightInvite
import com.mtgcompanion.app.data.social.Overview
import com.mtgcompanion.app.data.social.POD_MESSAGE_MAX
import com.mtgcompanion.app.data.social.Pod
import com.mtgcompanion.app.data.social.PodChat
import com.mtgcompanion.app.data.social.PodMessage
import com.mtgcompanion.app.data.social.PodMessageRef
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.data.social.chatCardLine
import com.mtgcompanion.app.data.social.chatItems
import com.mtgcompanion.app.data.social.leagueLabel
import com.mtgcompanion.app.data.social.membersLine
import com.mtgcompanion.app.data.social.mergePodMessages
import com.mtgcompanion.app.data.social.myAnswer
import com.mtgcompanion.app.data.social.nightDay
import com.mtgcompanion.app.data.social.nightTime
import com.mtgcompanion.app.data.social.parsePodMessage
import com.mtgcompanion.app.data.social.podPreview
import com.mtgcompanion.app.data.social.tableLine
import com.mtgcompanion.app.data.social.timeAgo
import com.mtgcompanion.app.data.social.upcomingNights
import com.mtgcompanion.app.data.usage.Usage
import com.mtgcompanion.app.data.usage.UsageAction
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.common.a11yHeading
import com.mtgcompanion.app.ui.common.readableWidth
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.json.JSONObject

// A pod's group chat (the Chats mockup): messages with who sent them and when, league results and
// game night invites inline (tap Going? to answer), sharing a game night, a deck or a card, and block
// or report from a sender's name. rememberPodChats and PodChatRow give the Chats
// list. The web app's twin is src/pages/PodChatPage.tsx.

/** What arrives on the user's channel for pod chat: a message, a night changed, or back after a drop. */
private sealed interface PodLive {
    data class Message(val message: PodMessage) : PodLive
    data class Night(val nightId: String) : PodLive
    data object Rejoined : PodLive
}

/** Listens for pod chat messages and game night changes while a screen is open, on the screen's own thread. */
@Composable
internal fun PodLiveEffect(social: SocialRepository, enabled: Boolean, onEvent: (PodLiveEvent) -> Unit) {
    val account by social.accountFlow.collectAsState()
    val uid = account?.userId
    LaunchedEffect(enabled, uid) {
        if (!enabled || uid == null) return@LaunchedEffect
        val incoming = Channel<PodLive>(Channel.UNLIMITED)
        social.dmChannel.watch(this, uid, onMessage = {}, onRejoined = { incoming.trySend(PodLive.Rejoined) }, onOther = { event, payload: JSONObject ->
            when (event) {
                "pod_message" -> parsePodMessage(payload)?.let { incoming.trySend(PodLive.Message(it)) }
                "game_night" -> payload.optString("nightId").takeIf { it.isNotEmpty() }?.let { incoming.trySend(PodLive.Night(it)) }
            }
        })
        for (e in incoming) when (e) {
            is PodLive.Message -> onEvent(PodLiveEvent(message = e.message))
            is PodLive.Night -> onEvent(PodLiveEvent(nightId = e.nightId))
            PodLive.Rejoined -> onEvent(PodLiveEvent(rejoined = true))
        }
    }
}

/** One live event: a new chat message, a night that changed, or back after a drop (reload). */
internal data class PodLiveEvent(val message: PodMessage? = null, val nightId: String? = null, val rejoined: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodChatScreen(
    social: SocialRepository,
    podId: String,
    decks: List<Deck>,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onOpenCard: (String) -> Unit,
    onOpenNight: (String) -> Unit,
    onPlanNight: (String) -> Unit,
    /** The pod's games and league (Playgroup). */
    onOpenPod: (String) -> Unit,
    onOpenSharedDeck: (owner: String, itemId: String) -> Unit
) {
    val colors = LocalAppColors.current
    val overview by social.overview.collectAsState()
    val pod = overview?.pods?.firstOrNull { it.id == podId }
    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(pod?.name ?: "Pod chat", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.a11yHeading())
                        val o = overview
                        if (pod != null && o?.me != null) {
                            val others = pod.members.filter { it != o.me.userId }.mapNotNull { o.person(it)?.displayName }
                            Text(membersLine(others), style = MaterialTheme.typography.labelMedium, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = {
                    if (pod != null) IconButton(onClick = { onOpenPod(pod.id) }) {
                        Icon(Icons.Filled.EmojiEvents, contentDescription = "Pod and league", tint = colors.accent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.readableWidth(680.dp)) {
                SocialGate(social, onSignIn) { o ->
                    val p = o.pods.firstOrNull { it.id == podId }
                    if (p == null) EmptyState(Icons.Filled.Groups, "You're not in that pod any more.")
                    else Chat(social, o, p, decks, onOpenCard, onOpenNight, onPlanNight, onOpenSharedDeck)
                }
            }
        }
    }
}

private const val PAGE = 50

@Composable
private fun Chat(
    social: SocialRepository,
    overview: Overview,
    pod: Pod,
    allDecks: List<Deck>,
    onOpenCard: (String) -> Unit,
    onOpenNight: (String) -> Unit,
    onPlanNight: (String) -> Unit,
    onOpenSharedDeck: (String, String) -> Unit
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val available by social.nights.available.collectAsState()
    val me = overview.me!!.userId
    var messages by remember { mutableStateOf<List<PodMessage>?>(null) }
    var nights by remember { mutableStateOf<List<NightInvite>>(emptyList()) }
    var older by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var stick by remember { mutableStateOf(true) }
    var sharing by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf<PodMessage?>(null) }
    var nightsTick by remember { mutableIntStateOf(0) }
    var table by remember { mutableStateOf<Pair<String, String?>?>(null) }
    val listState = rememberLazyListState()
    fun nameOf(id: String?): String = when (id) {
        null -> "Manabind"
        me -> "You"
        else -> overview.person(id)?.displayName ?: "Someone"
    }

    fun loadNewest() {
        scope.launch {
            try {
                val page = social.nights.messages(pod.id, null, PAGE)
                messages = mergePodMessages(messages.orEmpty(), page)
                if (!older) older = page.size >= PAGE
                now = System.currentTimeMillis()
                runCatching { social.nights.markRead(pod.id) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message
            }
        }
    }
    LaunchedEffect(Unit) { social.nights.check() }
    LaunchedEffect(available, pod.id) { if (available == true) loadNewest() }
    LaunchedEffect(available, pod.id, nightsTick) {
        if (available == true) runCatching { social.nights.nights(pod.id) }.onSuccess { nights = it }
    }
    PodLiveEffect(social, available == true) { e ->
        val m = e.message
        when {
            m != null && m.podId == pod.id -> {
                stick = true
                messages = mergePodMessages(messages.orEmpty(), listOf(m))
                now = System.currentTimeMillis()
                if (m.sender != me) scope.launch { runCatching { social.nights.markRead(pod.id) } }
            }
            e.nightId != null -> nightsTick++
            e.rejoined -> { loadNewest(); nightsTick++ }
        }
    }
    val items = remember(messages, now) { chatItems(messages.orEmpty(), me, now) }
    LaunchedEffect(items.size) {
        if (stick && items.isNotEmpty()) listState.scrollToItem((if (older) 1 else 0) + items.size)
    }
    // The table under the newest league result, worked out from the pod's games as the league page does.
    val lastLeague = remember(messages) { messages.orEmpty().lastOrNull { it.kind == "league" && it.ref?.seasonId != null } }
    LaunchedEffect(lastLeague?.id) {
        val seasonId = lastLeague?.ref?.seasonId ?: return@LaunchedEffect
        runCatching {
            val season = social.api.podSeasons(pod.id).firstOrNull { it.id == seasonId } ?: return@runCatching
            val games = social.api.podGames(pod.id)
            table = seasonId to tableLine(seasonTable(season, games).standings.map { Triple(it.userId, it.name, it.points) }, me)
        }
    }

    if (available == false) { EmptyState(Icons.Filled.Forum, CHAT_UNAVAILABLE); return }
    // A first message waits for the community rules (CommunityRulesHost).
    val communityRules = rememberCommunityRules()

    fun sent(m: PodMessage?) {
        if (m == null) return
        Usage.action(UsageAction.MESSAGE_SENT)
        stick = true
        messages = mergePodMessages(messages.orEmpty(), listOf(m))
        now = System.currentTimeMillis()
    }
    fun send() {
        val body = draft.trim()
        if (body.isEmpty()) return
        if (!communityRules.agreed) { communityRules.require { send() }; return }
        busy = true
        error = null
        scope.launch {
            try {
                sent(social.nights.send(pod.id, body))
                draft = ""
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }
    fun share(ref: PodMessageRef) {
        sharing = false
        if (!communityRules.agreed) { communityRules.require { share(ref) }; return }
        busy = true
        error = null
        scope.launch {
            try {
                sent(social.nights.share(pod.id, ref))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    val nightById = nights.associateBy { it.id }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (older) item(key = "older") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = {
                        val first = messages?.firstOrNull()?.id ?: return@TextButton
                        stick = false
                        scope.launch {
                            val page = runCatching { social.nights.messages(pod.id, first, PAGE) }.getOrDefault(emptyList())
                            messages = mergePodMessages(messages.orEmpty(), page)
                            older = page.size >= PAGE
                        }
                    }) { Text("Show earlier messages", color = colors.accent) }
                }
            }
            val list = messages
            when {
                list == null && error != null -> item { Notice(error.orEmpty(), warn = true) }
                list == null -> item { Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = colors.accent) } }
                list.isEmpty() -> item {
                    Text(
                        "Say hello to ${pod.name}. Write a card's name in [[double brackets]] to link it.",
                        style = MaterialTheme.typography.bodySmall, color = colors.textDim, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(20.dp)
                    )
                }
                else -> items.forEach { entry ->
                    item(key = entry.key) {
                        when (val it = entry) {
                            is ChatItem.Day -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(it.label, style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                            }
                            is ChatItem.Message -> {
                                val m = it.message
                                when {
                                    m.kind == "league" -> LeagueCard(m, if (m.id == lastLeague?.id && table?.first == m.ref?.seasonId) table?.second else null)
                                    m.kind == "system" -> Text(m.body, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                    m.kind == "night" || m.ref?.type == "night" -> NightChatCard(
                                        m, m.ref?.nightId?.let { id -> nightById[id] }, me,
                                        by = if (it.showName) nameOf(m.sender) else null, onOpen = onOpenNight
                                    )
                                    else -> Bubble(m, it.mine, if (it.showName) nameOf(m.sender) else null, now, onOpenCard, onOpenSharedDeck) { about = m }
                                }
                            }
                        }
                    }
                }
            }
            item(key = "end") { Box(Modifier.height(4.dp)) }
        }
        if (messages != null) error?.let { Notice(it, Modifier.padding(horizontal = 16.dp), warn = true) }
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().background(colors.bg).padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            IconButton(onClick = { sharing = true }, enabled = !busy) {
                Icon(Icons.Filled.Add, contentDescription = "Share a card, deck or game night", tint = colors.accent)
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(POD_MESSAGE_MAX) },
                placeholder = { Text("Message ${pod.name}") },
                colors = socialFieldColors(),
                maxLines = 5,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { send() }, enabled = !busy && draft.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = if (draft.isNotBlank()) colors.accent else colors.textDim, modifier = Modifier.size(26.dp))
            }
        }
    }

    if (sharing) ShareSheet(upcomingNights(nights, System.currentTimeMillis()), remember(allDecks) { activeDecks(allDecks).sortedBy { it.name.lowercase() } }, onShare = { share(it) }, onPlan = { sharing = false; onPlanNight(pod.id) }) { sharing = false }
    val aboutMessage = about
    val aboutSender = aboutMessage?.sender
    if (aboutMessage != null && aboutSender != null && aboutSender != me) {
        AlertDialog(
            onDismissRequest = { about = null },
            containerColor = colors.surface,
            title = { Text(nameOf(aboutSender)) },
            text = {
                BlockReportButton(social, aboutSender, nameOf(aboutSender), itemKind = "message", itemId = "pod:${aboutMessage.id}", onBlocked = {
                    about = null
                    messages = null
                    loadNewest()
                })
            },
            confirmButton = { TextButton(onClick = { about = null }) { Text("Close", color = colors.textMuted) } }
        )
    }
}

@Composable
private fun Bubble(
    m: PodMessage,
    mine: Boolean,
    name: String?,
    now: Long,
    onOpenCard: (String) -> Unit,
    onOpenSharedDeck: (String, String) -> Unit,
    onAbout: () -> Unit
) {
    val colors = LocalAppColors.current
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start, verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.widthIn(max = 320.dp)) {
            name?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = colors.textMuted, modifier = Modifier.clickable(role = Role.Button, onClick = onAbout))
            }
            Column(
                Modifier.clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (mine) 16.dp else 4.dp, bottomEnd = if (mine) 4.dp else 16.dp))
                    .background(if (mine) colors.accent else colors.surface2)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                val ref = m.ref
                if (m.kind == "share" && ref != null) {
                    Text(if (ref.type == "deck") "DECK" else "CARD", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = if (mine) colors.onAccent else colors.accent)
                    val open: (() -> Unit)? = when {
                        ref.type == "card" && ref.name != null -> { { onOpenCard(ref.name) } }
                        ref.type == "deck" && ref.ownerId != null && ref.itemId != null -> { { onOpenSharedDeck(ref.ownerId, ref.itemId) } }
                        else -> null
                    }
                    Text(
                        ref.name.orEmpty(),
                        fontWeight = FontWeight.Bold,
                        color = if (mine) colors.onAccent else colors.textPrimary,
                        modifier = if (open != null) Modifier.clickable(role = Role.Button, onClick = open) else Modifier
                    )
                    if (m.body.isNotEmpty()) MessageText(m.body, onOpenCard)
                } else if (mine) {
                    Text(m.body, color = colors.onAccent, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                } else {
                    MessageText(m.body, onOpenCard)
                }
            }
            Text(timeAgo(m.createdAt, now), style = MaterialTheme.typography.labelSmall, color = colors.textDim)
        }
    }
}

/** A league result: "LEAGUE · SEASON 2", "Priya won with Atraxa. Sam took first blood." and the table. */
@Composable
private fun LeagueCard(m: PodMessage, table: String?) {
    val colors = LocalAppColors.current
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface)
            .border(1.dp, colors.accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(leagueLabel(m.ref), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = colors.accent)
        Text(m.body, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
        table?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textMuted) }
    }
}

/** A game night in the chat: "Game night · Fri 10 Oct, 7pm" / "Priya's · 4 going, Sam maybe" and Going?. */
@Composable
private fun NightChatCard(m: PodMessage, night: NightInvite?, me: String, by: String?, onOpen: (String) -> Unit) {
    val colors = LocalAppColors.current
    val at = night?.startsAt ?: m.ref?.startsAt ?: m.createdAt
    val id = night?.id ?: m.ref?.nightId
    val ask = night != null && !night.cancelled && myAnswer(night, me) == null
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.accentGlow).padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            by?.let { Text("$it shared", style = MaterialTheme.typography.labelSmall, color = colors.textMuted) }
            Text("Game night · ${nightDay(at)}, ${nightTime(at)}", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
            Text(night?.let { chatCardLine(it, me) } ?: m.ref?.place.orEmpty(), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        if (id != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.height(36.dp).clip(RoundedCornerShape(12.dp)).background(colors.accent)
                    .clickable(role = Role.Button) { onOpen(id) }.padding(horizontal = 14.dp)
            ) {
                Text(if (ask) "Going?" else "Open", color = colors.onAccent, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            }
        }
    }
}

/** The + button: share a game night of this pod's, one of the user's decks, or a card by name. */
@Composable
private fun ShareSheet(nights: List<NightInvite>, decks: List<Deck>, onShare: (PodMessageRef) -> Unit, onPlan: () -> Unit, onDismiss: () -> Unit) {
    val colors = LocalAppColors.current
    var card by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("Share in the chat") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("A game night", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                nights.forEach { n ->
                    ShareRow("Game night · ${nightDay(n.startsAt)}, ${nightTime(n.startsAt)}") { onShare(PodMessageRef(type = "night", nightId = n.id)) }
                }
                ShareRow("Plan a game night", onPlan)
                if (decks.isNotEmpty()) Text("A deck", style = MaterialTheme.typography.labelMedium, color = colors.textMuted, modifier = Modifier.padding(top = 8.dp))
                decks.take(30).forEach { d -> ShareRow(d.name) { onShare(PodMessageRef(type = "deck", itemId = d.id, name = d.name)) } }
                Text("A card", style = MaterialTheme.typography.labelMedium, color = colors.textMuted, modifier = Modifier.padding(top = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = card, onValueChange = { card = it.take(150) }, placeholder = { Text("Card name") }, singleLine = true, colors = socialFieldColors(), modifier = Modifier.weight(1f))
                    TextButton(onClick = { onShare(PodMessageRef(type = "card", name = card.trim())) }, enabled = card.isNotBlank()) { Text("Share", color = colors.accent) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textMuted) } }
    )
}

@Composable
private fun ShareRow(text: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textPrimary,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surface2).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    )
}

/**
 * Every pod's chat for the Chats list (MessagesScreen.kt's ConversationList, merged with the direct
 * messages by mergeChatRows): null until loaded, and empty until the server has pod chat. Reloads as
 * messages arrive, and tells the Friends badge how many wait unread (SocialRepository.podUnread).
 */
@Composable
fun rememberPodChats(social: SocialRepository): List<PodChat>? {
    val scope = rememberCoroutineScope()
    val account by social.accountFlow.collectAsState()
    val available by social.nights.available.collectAsState()
    val me = account?.userId
    var chats by remember(me) { mutableStateOf<List<PodChat>?>(null) }
    fun load() {
        scope.launch {
            runCatching { social.nights.chats() }.onSuccess { c -> chats = c; social.setPodUnread(c.sumOf { it.unread }) }
        }
    }
    LaunchedEffect(me) {
        if (me == null) return@LaunchedEffect
        if (social.nights.check()) load() else chats = emptyList()
    }
    PodLiveEffect(social, available == true) { e -> if (e.message != null || e.rejoined) load() }
    return if (available == false) emptyList() else chats
}

/** One pod's chat as a row of the Chats list: name, last message and when, unread count. */
@Composable
fun PodChatRow(chat: PodChat, me: String, now: Long, onOpen: (String) -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface)
            .clickable(role = Role.Button) { onOpen(chat.podId) }.padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(colors.accentGlow), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Groups, contentDescription = null, tint = colors.accent)
        }
        Column(Modifier.weight(1f)) {
            Text(chat.name, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                podPreview(chat.last, me) + (chat.last?.let { " · " + timeAgo(it.createdAt, now) } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (chat.unread > 0) CountBadge(chat.unread)
    }
}
