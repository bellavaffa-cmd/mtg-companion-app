package com.mtgcompanion.app.ui.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.social.ShareKind
import com.mtgcompanion.app.data.social.SharedSummary
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.ui.common.SectionHeader
import com.mtgcompanion.app.ui.common.readableWidth
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

/** One friend: what they've shared with the user, trading with them, and unfriending. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendScreen(
    social: SocialRepository,
    friendId: String,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onOpenShared: (SharedSummary) -> Unit,
    onProposeTrade: (String) -> Unit
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    var confirmRemove by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("Friend", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.accent) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.readableWidth(680.dp)) {
                SocialGate(social, onSignIn) { overview ->
                    val friend = overview.person(friendId)
                    if (friend == null || !overview.isFriend(friendId)) {
                        EmptyState(Icons.Filled.PersonOff, "You're not friends with this person.")
                        return@SocialGate
                    }
                    val shared = overview.sharedWithMe.filter { it.owner == friendId }
                    val binders = shared.count { it.kind == ShareKind.COLLECTION }
                    val pods = overview.pods.filter { friendId in it.members }
                    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                                Avatar(friend, 112.dp)
                                Text(friend.displayName, style = MaterialTheme.typography.headlineSmall)
                                Text(friend.handle, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                                if (pods.isNotEmpty()) Text("In ${pods.joinToString { it.name }}", style = MaterialTheme.typography.bodySmall, color = colors.textDim)
                            }
                        }
                        item {
                            GoldButton(
                                "Propose a trade",
                                { social.draft = SocialRepository.TradeDraft(to = friendId); onProposeTrade(friendId) },
                                enabled = binders > 0,
                                modifier = Modifier.fillMaxWidth(),
                                icon = { Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                            if (binders == 0) Text(
                                "Trading needs a binder they've shared with you.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textDim,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            )
                        }
                        item { SectionHeader("Shared with you") }
                        if (shared.isEmpty()) item { Notice("${friend.displayName} hasn't shared any decks or binders with you yet.") }
                        shared.forEach { s -> item(key = "${s.kind}:${s.itemId}") { SharedRow(s, null) { onOpenShared(s) } } }
                        item {
                            LineButton(
                                "Remove friend",
                                { confirmRemove = true },
                                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                                icon = { Icon(Icons.Filled.PersonRemove, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                        }
                    }
                    if (confirmRemove) {
                        AlertDialog(
                            onDismissRequest = { confirmRemove = false },
                            containerColor = colors.surface,
                            title = { Text("Remove ${friend.displayName}?") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("You'll stop seeing what they share with all their friends, and they'll stop seeing yours. Pods you're both in stay as they are.", color = colors.textMuted)
                                    error?.let { Notice(it, warn = true) }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    scope.launch {
                                        try {
                                            social.api.removeFriend(friendId)
                                            social.refresh()
                                            confirmRemove = false
                                            onBack()
                                        } catch (e: Exception) {
                                            error = e.message
                                        }
                                    }
                                }) { Text("Remove", color = colors.error) }
                            },
                            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel", color = colors.textMuted) } }
                        )
                    }
                }
            }
        }
    }
}
