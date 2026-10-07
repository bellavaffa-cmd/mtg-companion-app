package com.mtgcompanion.app.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.seasonTable
import com.mtgcompanion.app.data.social.ACTIVITY_PREF_ROWS
import com.mtgcompanion.app.data.social.ACTIVITY_PRIVACY_NOTE
import com.mtgcompanion.app.data.social.ActivityPrefs
import com.mtgcompanion.app.data.social.ActivityTarget
import com.mtgcompanion.app.data.social.FeedActionKind
import com.mtgcompanion.app.data.social.FeedItem
import com.mtgcompanion.app.data.social.LeagueRow
import com.mtgcompanion.app.data.social.LeagueSnapshot
import com.mtgcompanion.app.data.social.Profile
import com.mtgcompanion.app.data.social.SellingCard
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.data.social.activityTarget
import com.mtgcompanion.app.data.social.feedDeck
import com.mtgcompanion.app.data.social.feedLine
import com.mtgcompanion.app.data.social.isOn
import com.mtgcompanion.app.data.social.sellingAsk
import com.mtgcompanion.app.data.social.timeAgo
import com.mtgcompanion.app.data.social.withPref
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.common.EmptyPrompt
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

// The Friends screen's Activity tab: what friends have shared, built, put up for trade or for sale,
// league news from the user's pods, and comments on their decks — and Settings › Privacy's switches
// for what it shows of the user. The web app's twin is src/social/ActivityFeed.tsx and
// src/social/ActivityPrivacy.tsx.

/** Whether the server has the activity/comments functions: null while asking, false when signed out. */
@Composable
fun rememberActivityComments(social: SocialRepository): Boolean? {
    val account by social.accountFlow.collectAsState()
    val available by social.activity.available.collectAsState()
    LaunchedEffect(account?.userId) { if (account != null) social.activity.check() }
    return if (account == null) false else available
}

@Composable
internal fun ActivityFeed(social: SocialRepository, header: @Composable () -> Unit, onOpen: (ActivityTarget) -> Unit) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val available = rememberSocialMore(social)
    val richer = rememberActivityComments(social)
    val items = remember { mutableStateListOf<FeedItem>() }
    var loaded by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Running seasons' tables, by season id (null: couldn't be worked out).
    val tables = remember { mutableStateMapOf<String, LeagueSnapshot?>() }
    var selling by remember { mutableStateOf<Profile?>(null) }
    val now = remember { System.currentTimeMillis() }
    val page = 30
    fun load(before: Long?) {
        busy = true
        scope.launch {
            try {
                val next = social.activity.feed(before, page)
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
    LaunchedEffect(available, richer) { if (available == true && richer != null) load(null) }

    // League news names the leader: the table comes from the pod's games, as on the league screen.
    LaunchedEffect(items.size) {
        val running = items.filter { it.kind == "league" && !it.ended && it.seasonId != null && it.podId != null && it.seasonId !in tables }
        for ((pod, news) in running.groupBy { it.podId!! }) {
            val found = runCatching {
                val seasons = social.api.podSeasons(pod)
                val games = social.api.podGames(pod)
                news.associate { n ->
                    n.seasonId!! to seasons.firstOrNull { it.id == n.seasonId }?.let { season ->
                        val t = seasonTable(season, games)
                        LeagueSnapshot(t.standings.map { LeagueRow(it.userId, it.name, it.points, it.rank) }, t.nights.size)
                    }
                }
            }.getOrElse { news.associate { it.seasonId!! to null } }
            tables.putAll(found)
        }
    }

    fun act(item: FeedItem, kind: FeedActionKind) {
        when (kind) {
            FeedActionKind.COMMENTS -> feedDeck(item)?.let { onOpen(ActivityTarget.SharedItem(it.owner, "deck", it.deckId, comments = true)) }
            FeedActionKind.SELLING -> selling = item.actor
            FeedActionKind.ASK -> {
                val owner = item.actor?.userId ?: return
                scope.launch {
                    val want = social.activity.askFor(owner, item.wanted)
                    social.draft = SocialRepository.TradeDraft(to = owner, want = want)
                    onOpen(ActivityTarget.Trade(owner))
                }
            }
        }
    }

    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    item(key = "a-$i-${a.kind}-${a.at}") {
                        FeedRow(
                            a, now, a.seasonId?.let { tables[it] },
                            onOpen = { if (a.kind == "selling") selling = a.actor else activityTarget(a)?.let(onOpen) },
                            onAction = { act(a, it) }
                        )
                    }
                }
                if (more) item {
                    LineButton(if (busy) "Loading…" else "Show older", { load(items.lastOrNull()?.at) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        if (richer == true && loaded) item {
            Text(
                buildAnnotatedString {
                    append("Only friends see your activity. Choose what's shared in ")
                    withStyle(SpanStyle(color = colors.accent, fontWeight = FontWeight.Bold)) { append("Settings › Privacy") }
                    append(".")
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                modifier = Modifier.fillMaxWidth().clickable { onOpen(ActivityTarget.Privacy) }.padding(vertical = 12.dp)
            )
        }
    }
    selling?.let { owner -> SellingDialog(social, owner, onClose = { selling = null }, onAsk = { onOpen(ActivityTarget.Trade(owner.userId)) }) }
}

@Composable
private fun FeedRow(item: FeedItem, now: Long, table: LeagueSnapshot?, onOpen: () -> Unit, onAction: (FeedActionKind) -> Unit) {
    val colors = LocalAppColors.current
    val line = feedLine(item, table)
    val time = timeAgo(item.at, now)
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).clickable(onClick = onOpen).padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        if (item.actor != null) Avatar(item.actor, 36.dp)
        else Box(Modifier.size(36.dp).clip(CircleShape).background(colors.accentGlow), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                buildAnnotatedString {
                    line.parts.forEach { p -> if (p.bold) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(p.text) } else append(p.text) }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary
            )
            Text(line.sub?.let { "$it · $time" } ?: time, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            line.action?.let { action ->
                Text(
                    action.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent,
                    modifier = Modifier.heightIn(min = 32.dp).clip(RoundedCornerShape(8.dp)).clickable { onAction(action.kind) }.padding(vertical = 6.dp)
                )
            }
        }
    }
}

/** "See them": a friend's To sell list, the cards on the user's wishlists first. */
@Composable
private fun SellingDialog(social: SocialRepository, owner: Profile, onClose: () -> Unit, onAsk: () -> Unit) {
    val colors = LocalAppColors.current
    var list by remember { mutableStateOf<List<SellingCard>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(owner.userId) {
        try {
            val l = social.activity.sellingList(owner.userId)
            if (l == null) error = "They're not showing their To sell list any more." else list = l
        } catch (e: Exception) {
            error = e.message ?: "Something went wrong."
        }
    }
    val wanted = list?.let(::sellingAsk).orEmpty()
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = colors.surface,
        title = { Text("${owner.displayName} is selling") },
        text = {
            when {
                error != null -> Text(error.orEmpty(), color = colors.textMuted)
                list == null -> Text("Loading…", color = colors.textMuted)
                list!!.isEmpty() -> Text("Nothing on it right now.", color = colors.textMuted)
                else -> LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(list!!, key = { "${it.itemId}:${it.scryfallId}" }) { c ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AsyncImage(
                                model = c.imageUrl.toArtCropUrl(), contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.size(width = 48.dp, height = 36.dp).clip(RoundedCornerShape(8.dp)).background(colors.surface2)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${c.forSale} to sell" + (c.itemName?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                            }
                            if (c.wanted) Text("On your wishlist", style = MaterialTheme.typography.labelSmall, color = colors.accent)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (list != null) TextButton(onClick = {
                social.draft = SocialRepository.TradeDraft(to = owner.userId, want = wanted)
                onClose()
                onAsk()
            }) { Text(if (wanted.isNotEmpty()) "Ask ${owner.displayName} for them" else "Propose a trade", color = colors.accent) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Close", color = colors.textMuted) } }
    )
}

/** Settings › Privacy: what friends' Activity shows of the user, once signed in and the server has it. */
@Composable
fun ActivityPrivacySection(social: SocialRepository) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val available = rememberActivityComments(social)
    var prefs by remember { mutableStateOf<ActivityPrefs?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(available) {
        if (available == true) try { prefs = social.activity.prefs() } catch (e: Exception) { error = e.message }
    }
    if (available != true) return
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Friends' activity", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        Text(ACTIVITY_PRIVACY_NOTE, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        val current = prefs
        if (current == null && error == null) Text("Loading…", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        if (current != null) ACTIVITY_PREF_ROWS.forEach { row ->
            val on = current.isOn(row.key)
            fun flip() {
                val next = current.withPref(row.key, !on)
                prefs = next
                error = null
                scope.launch {
                    try { social.activity.setPrefs(next) } catch (e: Exception) { prefs = current; error = e.message }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().clickable { flip() }.padding(vertical = 8.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(row.title, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                    Text(row.detail, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                }
                Switch(
                    checked = on,
                    onCheckedChange = { flip() },
                    colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.onAccent)
                )
            }
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error) }
    }
}
