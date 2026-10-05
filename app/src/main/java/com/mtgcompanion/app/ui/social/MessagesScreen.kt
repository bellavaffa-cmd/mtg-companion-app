package com.mtgcompanion.app.ui.social

import com.mtgcompanion.app.data.usage.Usage
import com.mtgcompanion.app.data.usage.UsageAction
import com.mtgcompanion.app.ui.common.EmptyAction
import com.mtgcompanion.app.ui.common.EmptyPrompt
import androidx.compose.material.icons.filled.PersonAdd
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.PersonOff
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.social.Conversation
import com.mtgcompanion.app.data.social.DirectMessage
import com.mtgcompanion.app.data.social.MESSAGE_MAX
import com.mtgcompanion.app.data.social.Overview
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.data.social.mergeMessages
import com.mtgcompanion.app.data.social.previewLine
import com.mtgcompanion.app.data.social.timeAgo
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.common.SectionHeader
import com.mtgcompanion.app.ui.common.readableWidth
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

// Direct messages between friends: the list of conversations, and one conversation. The web app's
// twin is src/pages/MessagesPage.tsx.

/**
 * Listens for new messages while a screen is open: [onMessage] for each (the user's own from another
 * device too), [onRejoined] after a drop so the screen can reload. Runs on the screen's own thread.
 */
@Composable
private fun DirectMessagesEffect(social: SocialRepository, enabled: Boolean, onMessage: (DirectMessage) -> Unit, onRejoined: () -> Unit) {
    val account by social.accountFlow.collectAsState()
    val uid = account?.userId
    LaunchedEffect(enabled, uid) {
        if (!enabled || uid == null) return@LaunchedEffect
        // null: back after a drop.
        val incoming = Channel<DirectMessage?>(Channel.UNLIMITED)
        social.dmChannel.watch(this, uid, onMessage = { incoming.trySend(it) }, onRejoined = { incoming.trySend(null) })
        for (m in incoming) if (m == null) onRejoined() else onMessage(m)
    }
}

/** Every conversation, newest first, with unread counts; new ones arrive live. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    social: SocialRepository,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onOpenConversation: (String) -> Unit,
    /** The empty list's "Add a friend", with no friends to message yet. */
    onOpenFriends: (() -> Unit)? = null
) {
    val colors = LocalAppColors.current
    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("Messages", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { BackButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.readableWidth(680.dp)) {
                SocialGate(social, onSignIn) { overview -> ConversationList(social, overview, onOpenConversation, onOpenFriends) }
            }
        }
    }
}

/** Every conversation — on this screen and on Friends' Messages tab. */
@Composable
internal fun ConversationList(social: SocialRepository, overview: Overview, onOpen: (String) -> Unit, onOpenFriends: (() -> Unit)? = null) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val available = rememberSocialMore(social)
    val me = overview.me!!.userId
    var list by remember { mutableStateOf<List<Conversation>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    fun load() {
        scope.launch {
            try { list = social.more.conversations(); error = null; now = System.currentTimeMillis() } catch (e: Exception) { error = e.message }
        }
    }
    LaunchedEffect(available) { if (available == true) load() }
    DirectMessagesEffect(social, available == true, onMessage = { load() }, onRejoined = { load() })

    val current = list
    when {
        available == false -> EmptyState(Icons.Filled.ChatBubble, "Not available yet.")
        current == null && error != null -> EmptyState(Icons.Filled.CloudOff, error.orEmpty()) { LineButton("Try again", { load() }) }
        current == null -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = colors.accent) }
        else -> {
            val others = overview.acceptedFriends.filter { f -> current.none { it.other.userId == f.userId } }
            LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (current.isEmpty()) item {
                    if (others.isNotEmpty()) EmptyPrompt(Icons.Filled.ChatBubble, "No messages yet. Pick a friend below to start a conversation.")
                    else EmptyPrompt(
                        Icons.Filled.ChatBubble,
                        "No messages yet. Add a friend, then start a conversation with them here.",
                        actions = listOfNotNull(onOpenFriends?.let { EmptyAction("Add a friend", Icons.Filled.PersonAdd, it) })
                    )
                }
                current.forEach { c ->
                    item(key = c.id) {
                        PersonRow(
                            c.other,
                            detail = previewLine(c.lastSender, c.lastBody, me) + (c.lastAt?.let { " · " + timeAgo(it, now) } ?: ""),
                            onClick = { onOpen(c.other.userId) }
                        ) { if (c.unread > 0) CountBadge(c.unread) }
                    }
                }
                if (others.isNotEmpty()) {
                    item { SectionHeader("Message a friend") }
                    others.forEach { f ->
                        item(key = "f-${f.userId}") {
                            PersonRow(overview.person(f.userId), onClick = { onOpen(f.userId) }, avatarSize = 34.dp, compact = true) {
                                Icon(Icons.Filled.ChatBubble, contentDescription = null, tint = colors.textDim)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One conversation: older messages on request, new ones live, card names as links. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    social: SocialRepository,
    friendId: String,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onOpenCard: (String) -> Unit,
    onOpenFriend: (String) -> Unit
) {
    val colors = LocalAppColors.current
    val overview by social.overview.collectAsState()
    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = { Text(overview?.person(friendId)?.displayName ?: "Messages", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = {
                    overview?.person(friendId)?.let { p ->
                        BlockReportButton(social, friendId, p.displayName, itemKind = "profile", itemId = friendId, compact = true, onBlocked = onBack)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.readableWidth(680.dp)) {
                SocialGate(social, onSignIn) { o -> ConversationThread(social, o, friendId, onOpenCard, onOpenFriend) }
            }
        }
    }
}

private const val PAGE = 50

@Composable
private fun ConversationThread(social: SocialRepository, overview: Overview, other: String, onOpenCard: (String) -> Unit, onOpenFriend: (String) -> Unit) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val available = rememberSocialMore(social)
    val me = overview.me!!.userId
    val them = overview.person(other)
    val isFriend = overview.isFriend(other)
    var messages by remember { mutableStateOf<List<DirectMessage>?>(null) }
    var older by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // Whether a change should scroll to the newest message (not when older ones were loaded above).
    var stick by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    fun loadNewest() {
        scope.launch {
            try {
                val page = social.more.messages(other, null, PAGE)
                messages = mergeMessages(messages.orEmpty(), page)
                if (!older) older = page.size >= PAGE
                now = System.currentTimeMillis()
                runCatching { social.more.markRead(other) }
            } catch (e: Exception) {
                error = e.message
            }
        }
    }
    LaunchedEffect(available, other) { if (available == true) loadNewest() }
    DirectMessagesEffect(social, available == true, onMessage = { m ->
        if (m.sender == other || m.recipient == other) {
            stick = true
            messages = mergeMessages(messages.orEmpty(), listOf(m))
            now = System.currentTimeMillis()
            if (m.sender == other) scope.launch { runCatching { social.more.markRead(other) } }
        }
    }, onRejoined = { loadNewest() })
    LaunchedEffect(messages?.size) {
        val n = messages?.size ?: 0
        // The list is: who, maybe "Show earlier", the messages, and an end spacer.
        if (stick && n > 0) listState.scrollToItem(1 + (if (older) 1 else 0) + n)
    }

    when {
        available == false -> { EmptyState(Icons.Filled.ChatBubble, "Not available yet."); return }
        them == null -> { EmptyState(Icons.Filled.PersonOff, "You can only message friends."); return }
    }
    val person = them ?: return
    // A first message waits for the community rules (CommunityRulesHost).
    val communityRules = rememberCommunityRules()

    fun send() {
        val body = draft.trim()
        if (body.isEmpty()) return
        if (!communityRules.agreed) { communityRules.require { send() }; return }
        busy = true
        error = null
        scope.launch {
            try {
                val sent = social.more.send(other, body)
                Usage.action(UsageAction.MESSAGE_SENT)
                stick = true
                messages = mergeMessages(messages.orEmpty(), listOf(sent))
                draft = ""
                now = System.currentTimeMillis()
            } catch (e: Exception) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            item(key = "who") {
                PersonRow(person, onClick = { onOpenFriend(other) }, avatarSize = 36.dp, compact = true)
            }
            if (older) item(key = "older") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = {
                        val first = messages?.firstOrNull()?.id ?: return@TextButton
                        stick = false
                        scope.launch {
                            val page = runCatching { social.more.messages(other, first, PAGE) }.getOrDefault(emptyList())
                            messages = mergeMessages(messages.orEmpty(), page)
                            older = page.size >= PAGE
                        }
                    }) { Text("Show earlier messages", color = colors.accent) }
                }
            }
            val list = messages
            when {
                list == null -> item { Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = colors.accent) } }
                list.isEmpty() -> item {
                    Text(
                        "Say hello. Write a card's name in [[double brackets]] to link it.",
                        style = MaterialTheme.typography.bodySmall, color = colors.textDim, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(20.dp)
                    )
                }
                else -> list.forEach { m ->
                    item(key = m.id) {
                        val mine = m.sender == me
                        Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
                            Column(
                                Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(16.dp))
                                    .background(if (mine) colors.accentGlow else colors.surface)
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                MessageText(m.body, onOpenCard)
                                Text(timeAgo(m.createdAt, now), style = MaterialTheme.typography.labelSmall, color = colors.textDim)
                            }
                        }
                    }
                }
            }
            item(key = "end") { Box(Modifier.height(4.dp)) }
        }
        error?.let { Notice(it, Modifier.padding(horizontal = 16.dp), warn = true) }
        if (isFriend) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().background(colors.bg).padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(MESSAGE_MAX) },
                    placeholder = { Text("Message — [[Card Name]] links a card") },
                    colors = socialFieldColors(),
                    maxLines = 5,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { send() }, enabled = !busy && draft.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = if (draft.isNotBlank()) colors.accent else colors.textDim, modifier = Modifier.size(26.dp))
                }
            }
        } else {
            Notice("You're no longer friends, so you can't send new messages.", Modifier.padding(16.dp))
        }
    }
}

/** A row on the Friends screen opening Messages, with the unread count. */
@Composable
fun MessagesRow(unread: Int, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.accentGlow).clickable(onClick = onClick).padding(14.dp)
    ) {
        Icon(Icons.Filled.ChatBubble, contentDescription = null, tint = colors.accent)
        Text("Messages", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        if (unread > 0) CountBadge(unread)
    }
}
