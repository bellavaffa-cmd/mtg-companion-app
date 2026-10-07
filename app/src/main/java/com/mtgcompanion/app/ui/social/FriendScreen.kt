package com.mtgcompanion.app.ui.social

import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.common.a11yHeading
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.social.ForTradeCard
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
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
import com.mtgcompanion.app.data.CollectionRepository
import com.mtgcompanion.app.data.DeckRepository
import com.mtgcompanion.app.data.social.ShareKind
import com.mtgcompanion.app.data.supabase.SupabaseSync
import com.mtgcompanion.app.data.social.SharedSummary
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.data.social.SocialArea
import com.mtgcompanion.app.data.social.withoutFriend
import com.mtgcompanion.app.ui.common.SectionHeader
import com.mtgcompanion.app.ui.common.readableWidth
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

/** One friend: what they've shared with the user, trading with them, and unfriending. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendScreen(
    social: SocialRepository,
    sync: SupabaseSync,
    collectionRepository: CollectionRepository,
    deckRepository: DeckRepository,
    friendId: String,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onOpenShared: (SharedSummary) -> Unit,
    onOpenSharedCollection: (String) -> Unit,
    onProposeTrade: (String) -> Unit,
    onMessage: (String) -> Unit = {}
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    var confirmRemove by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val withMore = rememberSocialMore(social) == true
    // What they've marked for trade (they show it to all friends).
    var forTrade by remember(friendId) { mutableStateOf<List<ForTradeCard>>(emptyList()) }
    LaunchedEffect(withMore, friendId) {
        if (withMore) forTrade = runCatching { social.more.forTradeList(friendId) }.getOrNull().orEmpty()
    }
    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("Friend", style = MaterialTheme.typography.titleLarge, modifier = Modifier.a11yHeading()) },
                navigationIcon = { BackButton(onClick = onBack) },
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
                    val wholeCollection = overview.wholeCollectionFrom(friendId)
                    val pods = overview.pods.filter { friendId in it.members }
                    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                                Avatar(friend, 112.dp)
                                Text(friend.displayName, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.a11yHeading())
                                Text(friend.handle, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                                if (pods.isNotEmpty()) Text("In ${pods.joinToString { it.name }}", style = MaterialTheme.typography.bodySmall, color = colors.textDim)
                                ReputationLine(social, friendId)
                            }
                        }
                        if (withMore) item {
                            LineButton(
                                "Message",
                                { onMessage(friendId) },
                                modifier = Modifier.fillMaxWidth(),
                                icon = { Icon(Icons.Filled.ChatBubble, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                        }
                        item {
                            GoldButton(
                                "Propose a trade",
                                { social.draft = SocialRepository.TradeDraft(to = friendId); onProposeTrade(friendId) },
                                enabled = binders > 0 || forTrade.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth(),
                                icon = { Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                            if (binders == 0 && forTrade.isEmpty()) Text(
                                "Trading needs a binder they've shared with you, or cards they've marked for trade.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textDim,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            )
                        }
                        if (forTrade.isNotEmpty()) {
                            item { SectionHeader("For trade · ${forTrade.sumOf { it.forTrade }}") }
                            forTrade.take(12).forEach { c ->
                                item(key = "ft-${c.itemId}-${c.scryfallId}") {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface)
                                            .clickable {
                                                social.draft = SocialRepository.TradeDraft(to = friendId, want = listOf(c.asTrade()))
                                                onProposeTrade(friendId)
                                            }
                                            .padding(8.dp)
                                    ) {
                                        AsyncImage(
                                            model = c.imageUrl.toArtCropUrl(), contentDescription = null, contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(width = 56.dp, height = 44.dp).clip(RoundedCornerShape(11.dp)).background(colors.surface2)
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(c.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(listOfNotNull(c.itemName ?: "Binder", c.condition).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                                        }
                                        Text("${c.forTrade}×", style = MaterialTheme.typography.titleSmall, color = colors.accent)
                                    }
                                }
                            }
                            if (forTrade.size > 12) item { Text("…and ${forTrade.size - 12} more — pick them when you propose a trade.", style = MaterialTheme.typography.bodySmall, color = colors.textDim) }
                        }
                        item { SectionHeader("Shared with you") }
                        if (shared.isEmpty()) item { Notice("${friend.displayName} hasn't shared any decks or binders with you yet.") }
                        if (wholeCollection || binders > 1) item(key = "whole") {
                            WholeCollectionRow(friend.displayName, shared.filter { it.kind == ShareKind.COLLECTION }, wholeCollection) { onOpenSharedCollection(friendId) }
                        }
                        shared.forEach { s -> item(key = "${s.kind}:${s.itemId}") { SharedRow(s, null) { onOpenShared(s) } } }
                        item(key = "share-with") {
                            ShareWithFriendSection(social, sync, collectionRepository, deckRepository, overview, friendId, friend.displayName)
                        }
                        item {
                            LineButton(
                                "Remove friend",
                                { confirmRemove = true },
                                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                                icon = { Icon(Icons.Filled.PersonRemove, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                        }
                        item(key = "block") { BlockReportButton(social, friendId, friend.displayName, itemKind = "profile", itemId = friendId, onBlocked = onBack) }
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
                                            social.mutate(SocialArea.FRIENDS, optimistic = { it.withoutFriend(friendId) }) { social.api.removeFriend(friendId) }
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
