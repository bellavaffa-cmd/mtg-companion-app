package com.mtgcompanion.app.ui.lifecounter

import androidx.compose.foundation.background
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
 * do against them, their nemesis, which decks win most, and their streaks. Mirrors the web app's
 * src/pages/PlaygroupPage.tsx.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaygroupScreen(decks: List<Deck>, onBack: () -> Unit, onOpenDeck: (String) -> Unit) {
    val colors = LocalAppColors.current
    val stats = remember(decks) { playgroupStats(decks) }

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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (stats.games == 0) {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 8.dp)) {
                        Icon(Icons.Filled.Groups, contentDescription = null, tint = colors.textDim, modifier = Modifier.size(40.dp))
                        Text(
                            "No games recorded yet. Log a result on a deck's Stats, or play with your phone as a remote at a life counter table — every deck's games come together here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
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
