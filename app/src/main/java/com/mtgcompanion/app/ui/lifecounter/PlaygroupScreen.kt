package com.mtgcompanion.app.ui.lifecounter

import com.mtgcompanion.app.ui.common.EmptyAction
import com.mtgcompanion.app.ui.common.EmptyPrompt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Style
import com.mtgcompanion.app.data.mulliganSummary
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.vector.ImageVector
import com.mtgcompanion.app.data.GameResult
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.ui.common.PillChip
import com.mtgcompanion.app.ui.social.GoldButton
import com.mtgcompanion.app.ui.social.LineButton
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckRecord
import com.mtgcompanion.app.data.Matchup
import com.mtgcompanion.app.data.PLAYGROUP_MIN_GAMES
import com.mtgcompanion.app.data.playgroupStats
import com.mtgcompanion.app.data.record
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.theme.LocalAppColors
import com.mtgcompanion.app.ui.theme.NumberStyle

/** How many people and commanders show before "Show all". */
private const val SHOWN = 8

private fun plural(n: Int, one: String, many: String = "${one}s") = "$n ${if (n == 1) one else many}"
private fun recordLine(wins: Int, losses: Int, draws: Int) = "$wins–$losses" + if (draws > 0) "–$draws" else ""

/**
 * The playgroup: every deck's games together — the user's record, who they play most and how they
 * do against them, their nemesis, which decks win most, and their streaks. Above it, a switch to
 * each pod's shared games (PodGames.kt). Mirrors the web app's src/pages/PlaygroupPage.tsx.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaygroupScreen(
    decks: List<Deck>,
    social: SocialRepository,
    onBack: () -> Unit,
    onOpenDeck: (String) -> Unit,
    onSignIn: () -> Unit,
    onOpenFriends: () -> Unit,
    onAddGameResult: (String, GameResult) -> Unit,
    /** The empty page's buttons: the life counter, and the Decks tab. */
    onStartGame: () -> Unit = {},
    onOpenDecks: () -> Unit = {}
) {
    val colors = LocalAppColors.current
    val account by social.accountFlow.collectAsState()
    val overview by social.overview.collectAsState()
    val socialError by social.error.collectAsState()
    val socialLoading by social.loading.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(account?.userId) { if (account != null && social.overview.value == null) social.refresh() }
    // "Just me" (null), a pod's id, or PODS while there are none to list.
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    val me = if (account != null) overview?.me else null
    val pods = if (me != null) overview?.pods.orEmpty() else emptyList()
    val pod = pods.firstOrNull { it.id == chosen }

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("Playgroup", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { BackButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                PillChip("Just me", selected = chosen == null, onClick = { chosen = null })
                pods.forEach { p -> PillChip(p.name, selected = pod?.id == p.id, onClick = { chosen = p.id }) }
                if (pods.isEmpty() && social.configured) PillChip("Pods", selected = chosen != null, onClick = { chosen = PODS })
            }
            val current = overview
            when {
                pod != null && me != null && current != null ->
                    PodView(social, current, pod, me, decks, onAddGameResult)
                chosen == null -> JustMe(decks, onOpenDeck, onStartGame, onOpenDecks)
                !social.configured -> PodsState(Icons.Filled.CloudOff, "Accounts aren't set up in this build.")
                account == null -> PodsState(Icons.Filled.Groups, "Sign in to see your pods' games.") { GoldButton("Sign in", onSignIn) }
                current == null -> if (socialError != null) {
                    PodsState(Icons.Filled.CloudOff, socialError.orEmpty()) {
                        LineButton("Try again", { scope.launch { social.refresh() } }, enabled = !socialLoading)
                    }
                } else {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = colors.accent) }
                }
                me == null -> PodsState(Icons.Filled.Groups, "Make your profile on Friends first — then your pods' games show here.") { GoldButton("Make your profile", onOpenFriends) }
                else -> PodsState(
                    Icons.Filled.Groups,
                    (if (chosen == PODS) "You're not in a pod yet." else "You're not in that pod any more.") +
                        " A pod is a group of friends — make one on Friends, and everyone in it can record games here."
                ) { LineButton("Friends", onOpenFriends) }
            }
        }
    }
}

/** The switcher's entry for pods while there are none to list (signed out, loading, no pods). */
private const val PODS = "pods"

@Composable
private fun PodsState(icon: ImageVector, text: String, action: (@Composable () -> Unit)? = null) {
    val colors = LocalAppColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp)) {
        Icon(icon, contentDescription = null, tint = colors.textDim, modifier = Modifier.size(40.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, modifier = Modifier.padding(top = 10.dp, bottom = 12.dp))
        action?.invoke()
    }
}

/** The user's own view: every one of their decks' games together. */
@Composable
private fun JustMe(decks: List<Deck>, onOpenDeck: (String) -> Unit, onStartGame: () -> Unit, onOpenDecks: () -> Unit) {
    val colors = LocalAppColors.current
    val stats = remember(decks) { playgroupStats(decks) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (stats.games == 0) {
            item {
                EmptyPrompt(
                    Icons.Filled.Groups,
                    "No games yet. Play at the life counter, or log a result on a deck's Stats, and every game shows here.",
                    actions = listOf(
                        EmptyAction("Start a game", Icons.Filled.Favorite, onStartGame),
                        EmptyAction("Your decks", Icons.Filled.Style, onOpenDecks)
                    )
                )
            }
            return@LazyColumn
        }
        item {
            Panel {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(recordLine(stats.wins, stats.losses, stats.draws), style = NumberStyle(40), color = colors.textPrimary)
                    Text(
                        "${stats.winRate}% win rate over ${plural(stats.games, "game")}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.accentLight,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                val streaks = listOfNotNull(
                    stats.streak?.let { (result, count) -> "$count ${when (result) { "WIN" -> "wins"; "LOSS" -> "losses"; else -> "draws" }} in a row now" },
                    stats.longestWinStreak.takeIf { it > 1 }?.let { "Longest win streak $it" }
                )
                Text(
                    streaks.joinToString(" · ").ifEmpty { "Across ${plural(decks.count { it.gameResults.isNotEmpty() }, "deck")}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    modifier = Modifier.padding(top = 8.dp)
                )
                val length = listOfNotNull(stats.averageMinutes?.let { "$it min" }, stats.averageTurns?.let { "$it turns" })
                if (length.isNotEmpty()) {
                    Text("A game takes about ${length.joinToString(" · ")}", style = MaterialTheme.typography.bodySmall, color = colors.textMuted, modifier = Modifier.padding(top = 4.dp))
                }
                mulliganSummary(stats.mulligans)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        if (stats.nemesis != null || stats.nemesisCommander != null) {
            item {
                Panel {
                    PanelTitle("Nemesis")
                    stats.nemesis?.let { NemesisRow("Player", it) }
                    stats.nemesisCommander?.let { NemesisRow("Commander", it) }
                    Text(
                        "Who you do worst against, out of those you've played $PLAYGROUP_MIN_GAMES or more times.",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textDim,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
        if (stats.ranked.isNotEmpty() || stats.unranked.isNotEmpty()) {
            item {
                Panel {
                    PanelTitle("Decks")
                    stats.ranked.forEachIndexed { i, r -> DeckRow(i + 1, r) { onOpenDeck(r.deckId) } }
                    if (stats.unranked.isNotEmpty()) {
                        Text(
                            "Fewer than $PLAYGROUP_MIN_GAMES games",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textMuted,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                        stats.unranked.forEach { r -> DeckRow(null, r) { onOpenDeck(r.deckId) } }
                    }
                }
            }
        }
        if (stats.opponents.isNotEmpty()) item { MatchupPanel("Against", stats.opponents) }
        if (stats.commanders.isNotEmpty()) item { MatchupPanel("Commanders faced", stats.commanders) }
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.surface).padding(16.dp), content = content)
}

@Composable
private fun PanelTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = LocalAppColors.current.textPrimary, modifier = Modifier.padding(bottom = 8.dp))
}

/** Gold for a winning record, red for a losing one. */
@Composable
private fun recordColor(wins: Int, losses: Int): Color {
    val colors = LocalAppColors.current
    return when {
        wins > losses -> colors.accent
        wins < losses -> colors.error
        else -> colors.textMuted
    }
}

@Composable
private fun NemesisRow(label: String, m: Matchup) {
    val colors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.textDim, modifier = Modifier.width(78.dp))
        Text(m.name, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(plural(m.games, "game"), style = MaterialTheme.typography.bodySmall, color = colors.textDim)
        Text(m.record(), style = NumberStyle(17), color = colors.error)
    }
}

@Composable
private fun MatchupPanel(title: String, rows: List<Matchup>) {
    val colors = LocalAppColors.current
    var all by remember { mutableStateOf(false) }
    Panel {
        PanelTitle(title)
        (if (all) rows else rows.take(SHOWN)).forEach { m ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(m.name, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(plural(m.games, "game"), style = MaterialTheme.typography.bodySmall, color = colors.textDim)
                Text(m.record(), style = NumberStyle(17), color = recordColor(m.wins, m.losses))
            }
        }
        if (rows.size > SHOWN) {
            Text(
                if (all) "Show fewer" else "Show all ${rows.size}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.accent,
                modifier = Modifier.clickable { all = !all }.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun DeckRow(rank: Int?, r: DeckRecord, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 5.dp)
    ) {
        if (rank != null) Text("$rank", style = MaterialTheme.typography.bodySmall, color = colors.textDim, modifier = Modifier.width(18.dp))
        Text(r.name, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text((if (rank != null) "${r.winRate}% · " else "") + plural(r.games, "game"), style = MaterialTheme.typography.bodySmall, color = colors.textDim)
        Text(recordLine(r.wins, r.losses, r.draws), style = NumberStyle(17), color = recordColor(r.wins, r.losses))
    }
}
