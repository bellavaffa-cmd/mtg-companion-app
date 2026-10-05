package com.mtgcompanion.app.ui.social

import com.mtgcompanion.app.ui.common.EmptyPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.social.ActivityItem
import com.mtgcompanion.app.data.social.BlockedPerson
import com.mtgcompanion.app.data.social.MessagePart
import com.mtgcompanion.app.data.social.Overview
import com.mtgcompanion.app.data.social.REPORT_REASONS
import com.mtgcompanion.app.data.social.Reputation
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.data.social.Trade
import com.mtgcompanion.app.data.social.TradeMatch
import com.mtgcompanion.app.data.social.activityText
import com.mtgcompanion.app.data.social.canRate
import com.mtgcompanion.app.data.social.matchSentence
import com.mtgcompanion.app.data.social.messageParts
import com.mtgcompanion.app.data.social.positiveLine
import com.mtgcompanion.app.data.social.timeAgo
import com.mtgcompanion.app.data.social.tradesLine
import com.mtgcompanion.app.data.social.withYouLine
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.common.SectionHeader
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

// Pieces of the social screens for blocking and reporting, messages, trade reputation, activity and
// two-way trade matches. The web app's twin is src/social/MoreUi.tsx.

/** Whether the server has the social_more functions: null while asking, false when signed out. */
@Composable
fun rememberSocialMore(social: SocialRepository): Boolean? {
    val account by social.accountFlow.collectAsState()
    val available by social.more.available.collectAsState()
    LaunchedEffect(account?.userId) { if (account != null) social.more.check() }
    return if (account == null) false else available
}

/** A message's text, with [[Card Name]] as links to the card. */
@Composable
fun MessageText(body: String, onOpenCard: (String) -> Unit, style: TextStyle = MaterialTheme.typography.bodyMedium) {
    val colors = LocalAppColors.current
    val open by rememberUpdatedState(onOpenCard)
    val text = remember(body, colors.accent) {
        buildAnnotatedString {
            messageParts(body).forEach { part ->
                when (part) {
                    is MessagePart.Text -> append(part.text)
                    is MessagePart.Card -> withLink(
                        LinkAnnotation.Clickable(
                            tag = part.name,
                            styles = TextLinkStyles(SpanStyle(color = colors.accent, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline))
                        ) { open(part.name) }
                    ) { append(part.name) }
                }
            }
        }
    }
    Text(text, style = style, color = colors.textPrimary)
}

/**
 * "Block or report" for someone, from their profile, a trade, a shared item or a conversation.
 * [itemKind]/[itemId]: what a report is about, when it's one thing. [onBlocked]: after blocking.
 */
@Composable
fun BlockReportButton(
    social: SocialRepository,
    userId: String,
    name: String,
    itemKind: String? = null,
    itemId: String? = null,
    compact: Boolean = false,
    onBlocked: () -> Unit = {}
) {
    val colors = LocalAppColors.current
    val available = rememberSocialMore(social)
    var open by remember { mutableStateOf<String?>(null) } // menu, report, reported, block
    if (available != true) return
    if (compact) {
        IconButton(onClick = { open = "menu" }) { Icon(Icons.Filled.Flag, contentDescription = "Block or report $name", tint = colors.textMuted) }
    } else {
        LineButton("Block or report", { open = "menu" }, modifier = Modifier.fillMaxWidth(), icon = { Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(18.dp)) })
    }
    when (open) {
        "menu" -> AlertDialog(
            onDismissRequest = { open = null },
            containerColor = colors.surface,
            title = { Text(name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MenuRow(Icons.Filled.Flag, "Report $name", "Tell us what's wrong. They won't know it was you.") { open = "report" }
                    MenuRow(Icons.Filled.Block, "Block $name", "They can't see your things or contact you.", danger = true) { open = "block" }
                }
            },
            confirmButton = { TextButton(onClick = { open = null }) { Text("Cancel", color = colors.textMuted) } }
        )
        "report" -> ReportDialog(social, userId, name, itemKind, itemId) { sent -> open = if (sent) "reported" else null }
        "reported" -> AlertDialog(
            onDismissRequest = { open = null },
            containerColor = colors.surface,
            title = { Text("Thanks for telling us") },
            text = { Text("We'll look at your report. You can also block $name, so they can't contact you.", color = colors.textMuted) },
            confirmButton = { TextButton(onClick = { open = null }) { Text("Done", color = colors.accent) } },
            dismissButton = { TextButton(onClick = { open = "block" }) { Text("Block $name", color = colors.error) } }
        )
        "block" -> BlockDialog(social, userId, name) { blocked -> open = null; if (blocked) onBlocked() }
    }
}

@Composable
private fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String, danger: Boolean = false, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface2).clickable(onClick = onClick).padding(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = if (danger) colors.error else colors.accent)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
    }
}

@Composable
private fun BlockDialog(social: SocialRepository, userId: String, name: String, onClose: (Boolean) -> Unit) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { onClose(false) },
        containerColor = colors.surface,
        title = { Text("Block $name?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "You'll stop being friends, open trades between you are cancelled, and neither of you will see the other's decks, binders or messages. They can't ask to be friends or send you anything. Pods you're both in stay as they are. You can unblock them in Settings.",
                    color = colors.textMuted
                )
                error?.let { Notice(it, warn = true) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                error = null
                scope.launch {
                    try {
                        social.more.block(userId)
                        runCatching { social.refresh() }
                        onClose(true)
                    } catch (e: Exception) {
                        error = e.message
                        busy = false
                    }
                }
            }) { Text("Block", color = colors.error) }
        },
        dismissButton = { TextButton(onClick = { onClose(false) }) { Text("Cancel", color = colors.textMuted) } }
    )
}

@Composable
private fun ReportDialog(social: SocialRepository, userId: String, name: String, itemKind: String?, itemId: String?, onClose: (Boolean) -> Unit) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    var reason by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { onClose(false) },
        containerColor = colors.surface,
        title = { Text("Report $name") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("What's wrong?", style = MaterialTheme.typography.labelLarge)
                REPORT_REASONS.forEach { (id, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { reason = id }.padding(vertical = 6.dp, horizontal = 4.dp)
                    ) {
                        Icon(if (reason == id) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked, contentDescription = null, tint = if (reason == id) colors.accent else colors.textDim)
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(
                    note, { note = it.take(1000) },
                    label = { Text("Anything else? (optional)") },
                    placeholder = { Text("What happened") },
                    colors = socialFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (itemKind != null && itemKind != "profile") {
                    Text("The report says which ${if (itemKind == "collection") "binder" else itemKind} it's about.", style = MaterialTheme.typography.bodySmall, color = colors.textDim)
                }
                error?.let { Notice(it, warn = true) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && reason != null, onClick = {
                val r = reason ?: return@TextButton
                busy = true
                error = null
                scope.launch {
                    try {
                        social.more.report(userId, r, note, itemKind, itemId)
                        onClose(true)
                    } catch (e: Exception) {
                        error = e.message
                        busy = false
                    }
                }
            }) { Text("Send report", color = colors.accent) }
        },
        dismissButton = { TextButton(onClick = { onClose(false) }) { Text("Cancel", color = colors.textMuted) } }
    )
}

/** People the user has blocked, each with Unblock — the Settings section. */
@Composable
fun BlockedPeopleSection(social: SocialRepository) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val account by social.accountFlow.collectAsState()
    val available = rememberSocialMore(social)
    var people by remember { mutableStateOf<List<BlockedPerson>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    suspend fun load() {
        try { people = social.more.blocked(); error = null } catch (e: Exception) { error = e.message }
    }
    LaunchedEffect(available) { if (available == true) load() }
    when {
        !social.configured -> Notice("Accounts aren't set up in this build.")
        account == null -> Notice("Sign in to see who you've blocked.")
        available == false -> Notice("Not available yet.")
        error != null -> Notice(error.orEmpty(), warn = true)
        people == null -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = colors.accent) }
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("People you've blocked can't see your things, ask to be friends, or send you anything.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            val list = people.orEmpty()
            if (list.isEmpty()) Text("You haven't blocked anyone.", style = MaterialTheme.typography.bodyMedium, color = colors.textDim)
            list.forEach { p ->
                var busy by remember(p.profile.userId) { mutableStateOf(false) }
                PersonRow(p.profile, compact = true) {
                    LineButton("Unblock", {
                        busy = true
                        scope.launch {
                            try { social.more.unblock(p.profile.userId); load() } catch (e: Exception) { error = e.message } finally { busy = false }
                        }
                    }, enabled = !busy)
                }
            }
        }
    }
}

/** A friend's trading record: "Trades completed: N · since …", how many with the user, thumbs up. */
@Composable
fun ReputationLine(social: SocialRepository, userId: String) {
    val colors = LocalAppColors.current
    val available = rememberSocialMore(social)
    var rep by remember(userId) { mutableStateOf<Reputation?>(null) }
    LaunchedEffect(available, userId) { if (available == true) rep = runCatching { social.more.reputation(userId) }.getOrNull() }
    val r = rep ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(tradesLine(r.total, r.since), style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
        if (r.total > 0) Text(withYouLine(r.withYou), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        if (r.positive > 0) Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ThumbUp, contentDescription = null, tint = colors.success, modifier = Modifier.size(14.dp))
            Text(" " + positiveLine(r.positive), style = MaterialTheme.typography.bodySmall, color = colors.success)
        }
    }
}

/** Thumbs up or down for the other side of a finished trade; [rating]: what the user said before. */
@Composable
fun RateTrade(social: SocialRepository, trade: Trade, me: String, rating: Boolean?, name: String, onRated: (Boolean) -> Unit) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    if (!canRate(trade, me)) return
    fun rate(positive: Boolean) {
        busy = true
        error = null
        scope.launch {
            try { social.more.rateTrade(trade.id, positive); onRated(positive) } catch (e: Exception) { error = e.message } finally { busy = false }
        }
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (rating == null) "How did trading with $name go?" else "Thanks — you can change it.",
                style = MaterialTheme.typography.bodySmall, color = colors.textMuted, modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { rate(true) }, enabled = !busy) {
                Icon(Icons.Filled.ThumbUp, contentDescription = "Good trade", tint = if (rating == true) colors.accent else colors.textDim)
            }
            IconButton(onClick = { rate(false) }, enabled = !busy) {
                Icon(Icons.Filled.ThumbDown, contentDescription = "Bad trade", tint = if (rating == false) colors.accent else colors.textDim)
            }
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error) }
    }
}

/** Friends with matches either way, each opening a trade started from them. */
@Composable
fun TradeMatchesBlock(social: SocialRepository, overview: Overview, onOpen: (TradeMatch) -> Unit) {
    val colors = LocalAppColors.current
    val available = rememberSocialMore(social)
    var matches by remember { mutableStateOf<List<TradeMatch>>(emptyList()) }
    LaunchedEffect(available, overview) { if (available == true) matches = runCatching { social.more.tradeMatches() }.getOrDefault(emptyList()) }
    val shown = matches.filter { overview.person(it.friend) != null && (it.theyHave.isNotEmpty() || it.theyWant.isNotEmpty()) }
    if (shown.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Trade matches")
        shown.take(8).forEach { m ->
            val p = overview.person(m.friend)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).clickable { onOpen(m) }.padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Avatar(p, 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(matchSentence(p?.displayName ?: "A friend", m.theyHave.size, m.theyWant.size).orEmpty(), style = MaterialTheme.typography.titleSmall)
                    Text("Tap to start a trade with them", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                }
                Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = colors.accent)
            }
        }
    }
}

/** The Friends screen's Activity tab: what friends have shared, changed, played and put up for trade. */
@Composable
fun ActivityList(social: SocialRepository, header: @Composable () -> Unit, onOpen: (ActivityItem) -> Unit) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val available = rememberSocialMore(social)
    val items = remember { mutableStateListOf<ActivityItem>() }
    var loaded by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val now = remember { System.currentTimeMillis() }
    val page = 30
    fun load(before: Long?) {
        busy = true
        scope.launch {
            try {
                val next = social.more.activity(before, page)
                if (before == null) items.clear()
                items.addAll(next)
                more = next.size >= page
                loaded = true
                error = null
            } catch (e: Exception) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }
    LaunchedEffect(available) { if (available == true) load(null) }

    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { header() }
        when {
            available == false -> item { EmptyState(Icons.Filled.DynamicFeed, "Not available yet.") }
            error != null && !loaded -> item {
                EmptyState(Icons.Filled.CloudOff, error.orEmpty()) { LineButton("Try again", { load(null) }, enabled = !busy) }
            }
            !loaded -> item { Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = colors.accent) } }
            items.isEmpty() -> item {
                EmptyPrompt(Icons.Filled.DynamicFeed, "Nothing from friends yet. When they share a deck, record a game or put cards up for trade, it shows here.")
            }
            else -> {
                items.forEachIndexed { i, a ->
                    item(key = "a-$i-${a.kind}-${a.at}") { ActivityRow(a, now) { onOpen(a) } }
                }
                if (more) item {
                    LineButton(if (busy) "Loading…" else "Show older", { load(items.lastOrNull()?.at) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(item: ActivityItem, now: Long, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val text = activityText(item)
    val art = item.cover ?: item.cards.firstOrNull { it.second != null }?.second
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Avatar(item.actor, 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                buildAnnotatedString {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(item.actor.displayName)
                    pop()
                    append(" " + text.action)
                },
                style = MaterialTheme.typography.bodyMedium
            )
            text.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Text(timeAgo(item.at, now), style = MaterialTheme.typography.labelSmall, color = colors.textDim)
        }
        if (art != null) AsyncImage(
            model = art.toArtCropUrl(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 56.dp, height = 42.dp).clip(RoundedCornerShape(10.dp)).background(colors.surface2)
        )
    }
}
