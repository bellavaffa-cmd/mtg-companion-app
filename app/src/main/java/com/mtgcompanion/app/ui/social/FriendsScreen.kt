package com.mtgcompanion.app.ui.social

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RadioButtonUnchecked
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.social.Overview
import com.mtgcompanion.app.data.social.Pod
import com.mtgcompanion.app.data.social.Profile
import com.mtgcompanion.app.data.social.ShareKind
import com.mtgcompanion.app.data.social.SharedSummary
import com.mtgcompanion.app.data.social.SocialApi
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.common.SectionHeader
import com.mtgcompanion.app.ui.common.readableWidth
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

/**
 * Friends: the user's profile, adding friends by username or QR code, requests, pods, what friends
 * have shared, and trades.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    social: SocialRepository,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onScanQr: () -> Unit,
    onOpenFriend: (String) -> Unit,
    onOpenShared: (SharedSummary) -> Unit,
    onOpenTrades: () -> Unit
) {
    val colors = LocalAppColors.current
    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("Friends", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.accent) } },
                actions = { IconButton(onClick = onScanQr) { Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan a QR code", tint = colors.textPrimary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.readableWidth(680.dp)) {
                SocialGate(social, onSignIn) { overview ->
                    FriendsContent(social, overview, onOpenFriend, onOpenShared, onOpenTrades)
                }
            }
        }
    }
}

@Composable
private fun FriendsContent(
    social: SocialRepository,
    overview: Overview,
    onOpenFriend: (String) -> Unit,
    onOpenShared: (SharedSummary) -> Unit,
    onOpenTrades: () -> Unit
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val me = overview.me!!
    var editing by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    var podDialog by remember { mutableStateOf<Pod?>(null) }
    var newPod by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val inbox = overview.trades.count { com.mtgcompanion.app.data.social.waitingOnMe(it, me.userId) }

    val incoming = overview.friends.filter { !it.accepted && it.incoming }
    val outgoing = overview.friends.filter { !it.accepted && !it.incoming }
    val friends = overview.acceptedFriends.sortedBy { overview.person(it.userId)?.displayName?.lowercase() }

    fun run(action: suspend () -> Unit) {
        scope.launch {
            error = null
            try { action(); social.refresh() } catch (e: Exception) { error = e.message }
        }
    }

    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.surface).padding(16.dp)
            ) {
                Avatar(me, 64.dp)
                Column(Modifier.weight(1f)) {
                    Text(me.displayName, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(me.handle, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                }
                IconButton(onClick = { showQr = true }) { Icon(Icons.Filled.QrCode2, contentDescription = "Show my QR code", tint = colors.textPrimary) }
                IconButton(onClick = { editing = true }) { Icon(Icons.Filled.Edit, contentDescription = "Edit profile", tint = colors.textPrimary) }
            }
        }
        item { AddFriend(social) }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.accentGlow).clickable(onClick = onOpenTrades).padding(14.dp)
            ) {
                Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = colors.accent)
                Text("Trades", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (inbox > 0) CountBadge(inbox)
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.textDim)
            }
        }
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

        item { SectionHeader(if (friends.isEmpty()) "Friends" else "Friends · ${friends.size}") }
        if (friends.isEmpty()) {
            item { Notice("No friends yet. Add someone by their username, or let them scan your QR code.") }
        }
        friends.forEach { f ->
            item(key = "f-${f.userId}") {
                val shares = overview.sharedWithMe.count { it.owner == f.userId }
                PersonRow(overview.person(f.userId), detail = if (shares > 0) "shares $shares" else null, onClick = { onOpenFriend(f.userId) }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.textDim)
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

        item { SectionHeader("Pods", action = "New pod", onAction = { newPod = true }) }
        if (overview.pods.isEmpty()) {
            item { Notice("A pod is a group of friends — your playgroup. Share a deck with a whole pod at once.") }
        }
        overview.pods.forEach { pod ->
            item(key = "pod-${pod.id}") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).clickable { podDialog = pod }.padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Box(Modifier.size(width = 62.dp, height = 34.dp)) {
                        pod.members.take(3).forEachIndexed { i, m -> Avatar(overview.person(m), 32.dp, Modifier.offset(x = (i * 15).dp)) }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(pod.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val owner = if (pod.owner == me.userId) "" else " · ${overview.person(pod.owner)?.displayName ?: ""}'s pod"
                        Text("${pod.members.size} ${if (pod.members.size == 1) "person" else "people"}$owner", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.textDim)
                }
            }
        }

        item { SectionHeader(if (overview.sharedWithMe.isEmpty()) "Shared with you" else "Shared with you · ${overview.sharedWithMe.size}") }
        if (overview.sharedWithMe.isEmpty()) {
            item { Notice("Decks and binders friends share with you show up here.") }
        }
        overview.sharedWithMe.forEach { s ->
            item(key = "sh-${s.owner}-${s.kind}-${s.itemId}") { SharedRow(s, overview.person(s.owner)) { onOpenShared(s) } }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            containerColor = colors.surface,
            title = { Text("Edit profile") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { ProfileEditor(social) { editing = false } } },
            confirmButton = {}
        )
    }
    if (showQr) {
        AlertDialog(
            onDismissRequest = { showQr = false },
            containerColor = colors.surface,
            title = { Text("Add me as a friend") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    QrCode(SocialApi.friendLink(me.username), 240.dp, "QR code to add ${me.handle}")
                    Text(me.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(me.handle, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    Text("Scan it with this app's Scan QR code, or any phone camera.", style = MaterialTheme.typography.bodySmall, color = colors.textDim, textAlign = TextAlign.Center)
                }
            },
            confirmButton = { TextButton(onClick = { showQr = false }) { Text("Done", color = colors.accent) } }
        )
    }
    if (newPod || podDialog != null) {
        PodDialog(social, overview, podDialog) { newPod = false; podDialog = null }
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
private fun AddFriend(social: SocialRepository) {
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
