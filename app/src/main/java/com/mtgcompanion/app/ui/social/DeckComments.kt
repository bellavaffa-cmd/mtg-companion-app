package com.mtgcompanion.app.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.social.COMMENT_MAX
import com.mtgcompanion.app.data.social.DeckComment
import com.mtgcompanion.app.data.social.DeckComments
import com.mtgcompanion.app.data.social.Profile
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.data.social.commentActions
import com.mtgcompanion.app.data.social.commentCount
import com.mtgcompanion.app.data.social.commentHeader
import com.mtgcompanion.app.data.social.commentReportId
import com.mtgcompanion.app.data.social.commentsNote
import com.mtgcompanion.app.data.social.composerPlaceholder
import com.mtgcompanion.app.data.social.forTradeLines
import com.mtgcompanion.app.data.social.offerCard
import com.mtgcompanion.app.data.social.swapReply
import com.mtgcompanion.app.data.social.threadComments
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// Comments on a shared deck: the Comments tab of SharedItemScreen. Friends the deck is shared with
// comment on it or on one of its cards and reply one level deep; the owner hides or deletes any
// comment, authors delete their own. The web app's twin is src/social/DeckComments.tsx.

/** A card the comment can be about: one of the deck's, or one of the user's for trade to suggest. */
data class PickCard(val name: String, val imageUrl: String?)

/** The comments on one deck, and the composer's draft. */
@Stable
class DeckCommentsState(
    private val social: SocialRepository,
    private val scope: CoroutineScope,
    val owner: Profile,
    val deckId: String
) {
    var data by mutableStateOf<DeckComments?>(null)
    var error by mutableStateOf<String?>(null)
    var text by mutableStateOf("")
    var card by mutableStateOf<PickCard?>(null)
    var replyTo by mutableStateOf<DeckComment?>(null)
    var busy by mutableStateOf(false)
    var picking by mutableStateOf(false)
    var confirm by mutableStateOf<DeckComment?>(null)

    val threads get() = threadComments(data?.comments.orEmpty())
    val count get() = commentCount(threads)

    fun load() {
        scope.launch {
            try {
                data = social.activity.comments(owner.userId, deckId) ?: DeckComments(isOwner = false, canComment = false, comments = emptyList())
                error = null
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong."
            }
        }
    }

    fun post() {
        val body = text.trim()
        if (body.isEmpty() || busy) return
        busy = true
        scope.launch {
            try {
                val about = if (replyTo == null) card else null
                social.activity.post(owner.userId, deckId, body, replyTo?.id, about?.name, about?.imageUrl)
                text = ""
                card = null
                replyTo = null
                data = social.activity.comments(owner.userId, deckId) ?: data
                error = null
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong."
            } finally {
                busy = false
            }
        }
    }

    fun act(action: suspend () -> Unit) {
        scope.launch {
            try {
                action()
                data = social.activity.comments(owner.userId, deckId) ?: data
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong."
            }
        }
    }

    fun delete(c: DeckComment) = act { social.activity.delete(c.id) }
    fun hide(c: DeckComment) = act { social.activity.hide(c.id, !c.hidden) }
}

@Composable
fun rememberDeckComments(social: SocialRepository, owner: Profile, deckId: String, enabled: Boolean): DeckCommentsState {
    val scope = rememberCoroutineScope()
    val state = remember(owner.userId, deckId) { DeckCommentsState(social, scope, owner, deckId) }
    LaunchedEffect(owner.userId, deckId, enabled) { if (enabled) state.load() }
    return state
}

/**
 * The comments, as items of the deck's list. [me]: the user; [deckCards]: the deck's cards
 * (commanders included); [onConsiderSwap]: the owner's "Consider a swap" (the deck's Considering);
 * [onOffer]: "Offer it in a trade" with the user's copy of the card.
 */
fun LazyListScope.deckCommentItems(
    state: DeckCommentsState,
    social: SocialRepository,
    me: String?,
    deckCards: List<PickCard>,
    collections: List<Collection>,
    onConsiderSwap: () -> Unit,
    onOffer: (com.mtgcompanion.app.data.social.TradeCard) -> Unit
) {
    val data = state.data
    when {
        data == null && state.error != null -> item { EmptyState(Icons.Filled.CloudOff, state.error.orEmpty()) { LineButton("Try again", { state.load() }) } }
        data == null -> item { Notice("Loading…") }
        else -> {
            val threads = state.threads
            if (threads.isEmpty()) item {
                EmptyState(Icons.Filled.Forum, "No comments yet." + if (data.canComment) " Say what you think, or pick a card to talk about." else "")
            }
            val names = deckCards.map { it.name }
            threads.forEach { t ->
                (listOf(t.comment) + t.replies).forEach { c ->
                    item(key = "comment-${c.id}") { CommentCard(state, social, c, me, data.canComment, names, collections, onConsiderSwap, onOffer) }
                }
            }
            item(key = "comments-note") {
                Text(commentsNote(state.owner.displayName, me == state.owner.userId), style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textMuted, modifier = Modifier.padding(vertical = 8.dp))
            }
            state.error?.let { e -> item(key = "comments-error") { Text(e, style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.error) } }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommentCard(
    state: DeckCommentsState,
    social: SocialRepository,
    c: DeckComment,
    me: String?,
    canComment: Boolean,
    deckNames: List<String>,
    collections: List<Collection>,
    onConsiderSwap: () -> Unit,
    onOffer: (com.mtgcompanion.app.data.social.TradeCard) -> Unit
) {
    val colors = LocalAppColors.current
    val owner = state.owner.userId
    val haveCard = c.cardName != null && offerCard(collections, c.cardName) != null
    val can = commentActions(c, me, owner, canComment, deckNames, haveCard)
    val header = commentHeader(c, me, owner, deckNames).split(" · ")
    Column(
        Modifier
            .padding(start = if (c.parent != null) 24.dp else 0.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (c.parent != null) colors.surface2 else colors.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colors.textPrimary)) { append(header.first()) }
                    if (header.size > 1) append(" · " + header.drop(1).joinToString(" · "))
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                modifier = Modifier.weight(1f)
            )
            if (can.hide) IconButton(onClick = { state.hide(c) }) {
                Icon(if (c.hidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, contentDescription = if (c.hidden) "Show this comment" else "Hide this comment", tint = colors.textMuted, modifier = Modifier.size(20.dp))
            }
            if (can.remove) IconButton(onClick = { state.confirm = c }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete this comment", tint = colors.textMuted, modifier = Modifier.size(20.dp))
            }
            if (can.report) BlockReportButton(social, c.author.userId, c.author.displayName, itemKind = "deck", itemId = commentReportId(c.id), compact = true, onBlocked = { state.load() })
        }
        Text(c.body, style = MaterialTheme.typography.bodyMedium, color = if (c.hidden) colors.textMuted else colors.textPrimary)
        if (can.reply || can.swap || can.offer) FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (can.reply) ActionLink("Reply") { state.replyTo = c; state.card = null }
            if (can.swap) ActionLink("Consider a swap") {
                if (me == owner) onConsiderSwap()
                else { state.replyTo = c; state.card = null; state.text = swapReply(c.cardName.orEmpty()) }
            }
            if (can.offer) ActionLink("Offer it in a trade") { c.cardName?.let { offerCard(collections, it) }?.let(onOffer) }
        }
    }
}

@Composable
private fun ActionLink(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = LocalAppColors.current.accent,
        modifier = Modifier.heightIn(min = 32.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 6.dp)
    )
}

/** The composer under the comments, with the "On a card" picker and the delete question. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommentComposer(state: DeckCommentsState, me: String?, deckCards: List<PickCard>, collections: List<Collection>) {
    val colors = LocalAppColors.current
    val data = state.data ?: return
    val isOwner = me == state.owner.userId
    if (data.canComment) Column(
        Modifier.fillMaxWidth().background(colors.bg).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val reply = state.replyTo
        val card = state.card
        if (reply != null || card != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (reply != null) Chip("Reply to ${if (reply.author.userId == me) "yourself" else reply.author.displayName}", "Stop replying") { state.replyTo = null }
            else if (card != null) Chip("On ${card.name}", "Not about ${card.name}") { state.card = null }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (reply == null) TextButton(
                onClick = { state.picking = true },
                modifier = Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(colors.surface)
            ) { Text("On a card", color = colors.accent, fontWeight = FontWeight.Bold) }
            TextField(
                value = state.text,
                onValueChange = { state.text = it.take(COMMENT_MAX) },
                placeholder = {
                    Text(
                        composerPlaceholder(state.owner.displayName, isOwner, reply?.let { if (it.author.userId == me) "yourself" else it.author.displayName }),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                },
                maxLines = 4,
                shape = RoundedCornerShape(22.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.surface, unfocusedContainerColor = colors.surface,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary
                ),
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { state.post() },
                enabled = !state.busy && state.text.isNotBlank(),
                modifier = Modifier.size(44.dp).clip(CircleShape).background(colors.accent)
            ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Post", tint = colors.onAccent) }
        }
    }
    if (state.picking) CardPickDialog(
        deckCards = deckCards,
        yours = if (isOwner) emptyList() else forTradeLines(collections).map { PickCard(it.entry.name, it.entry.imageUrl) },
        onPick = { state.card = it; state.picking = false },
        onClose = { state.picking = false }
    )
    state.confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { state.confirm = null },
            containerColor = colors.surface,
            title = { Text("Delete this comment?") },
            text = { Text(if (c.parent != null) "It goes for everyone." else "It goes for everyone, with its replies.", color = colors.textMuted) },
            confirmButton = { TextButton(onClick = { state.confirm = null; state.delete(c) }) { Text("Delete", color = colors.error) } },
            dismissButton = { TextButton(onClick = { state.confirm = null }) { Text("Cancel", color = colors.textMuted) } }
        )
    }
}

@Composable
private fun Chip(label: String, clearLabel: String, onClear: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).border(1.dp, colors.border, RoundedCornerShape(16.dp)).clickable(onClick = onClear).padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
        Icon(Icons.Filled.Close, contentDescription = clearLabel, tint = colors.textMuted, modifier = Modifier.size(16.dp))
    }
}

/** "On a card": one of the deck's cards, or — for a friend — one of their own cards for trade, to suggest. */
@Composable
private fun CardPickDialog(deckCards: List<PickCard>, yours: List<PickCard>, onPick: (PickCard) -> Unit, onClose: () -> Unit) {
    val colors = LocalAppColors.current
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    fun match(list: List<PickCard>) = list.distinctBy { it.name }.filter { q.isEmpty() || q in it.name.lowercase() }.sortedBy { it.name }.take(200)
    val inDeck = match(deckCards)
    val mine = match(yours.filter { y -> deckCards.none { it.name == y.name } })
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = colors.surface,
        title = { Text("Comment on a card") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = query, onValueChange = { query = it }, placeholder = { Text("Card name") }, singleLine = true, colors = socialFieldColors(), modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (inDeck.isNotEmpty()) item { Text("In the deck", style = MaterialTheme.typography.labelMedium, color = colors.textMuted) }
                    items(inDeck, key = { "d-${it.name}" }) { PickRow(it, onPick) }
                    if (mine.isNotEmpty()) item { Text("Suggest one of yours for trade", style = MaterialTheme.typography.labelMedium, color = colors.textMuted) }
                    items(mine, key = { "m-${it.name}" }) { PickRow(it, onPick) }
                    if (inDeck.isEmpty() && mine.isEmpty()) item { Text("No cards match “$query”.", color = colors.textMuted) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel", color = colors.textMuted) } }
    )
}

@Composable
private fun PickRow(c: PickCard, onPick: (PickCard) -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(c) }.padding(6.dp)
    ) {
        AsyncImage(
            model = c.imageUrl.toArtCropUrl(), contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 48.dp, height = 36.dp).clip(RoundedCornerShape(8.dp)).background(colors.surface2)
        )
        Text(c.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
