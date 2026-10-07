package com.mtgcompanion.app.ui.lifecounter

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.LEAGUE_PRESETS
import com.mtgcompanion.app.data.LEAGUE_UNAVAILABLE
import com.mtgcompanion.app.data.LeagueStanding
import com.mtgcompanion.app.data.MAX_RULE_POINTS
import com.mtgcompanion.app.data.PRESET_CUSTOM
import com.mtgcompanion.app.data.PodGame
import com.mtgcompanion.app.data.Season
import com.mtgcompanion.app.data.SeasonStatus
import com.mtgcompanion.app.data.canManageSeason
import com.mtgcompanion.app.data.championLine
import com.mtgcompanion.app.data.championNames
import com.mtgcompanion.app.data.nextSeasonName
import com.mtgcompanion.app.data.rulesSummary
import com.mtgcompanion.app.data.runningSeason
import com.mtgcompanion.app.data.seasonDays
import com.mtgcompanion.app.data.seasonEndedAt
import com.mtgcompanion.app.data.seasonProblem
import com.mtgcompanion.app.data.seasonStatus
import com.mtgcompanion.app.data.seasonTable
import com.mtgcompanion.app.data.shortDay
import com.mtgcompanion.app.data.social.Overview
import com.mtgcompanion.app.data.social.Pod
import com.mtgcompanion.app.data.social.Profile
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.ui.common.PillChip
import com.mtgcompanion.app.ui.social.GoldButton
import com.mtgcompanion.app.ui.social.LineButton
import com.mtgcompanion.app.ui.social.Notice
import com.mtgcompanion.app.ui.social.socialFieldColors
import com.mtgcompanion.app.ui.theme.LocalAppColors
import com.mtgcompanion.app.ui.theme.NumberStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate

// League mode on a pod's Playgroup page: the running season's table (points, games, wins, win rate,
// streak), its points per game night, its champion once it's over, and the past seasons with their
// final tables. The scoring is data/League.kt. Mirrors the web app's src/pages/LeagueView.tsx.

/** The league's part of a pod's page. [seasons]: null while loading; [onChanged] loads them again. */
@Composable
internal fun LeagueSection(
    social: SocialRepository,
    overview: Overview,
    pod: Pod,
    me: Profile,
    games: List<PodGame>,
    seasons: List<Season>?,
    unavailable: Boolean,
    loadError: String?,
    onChanged: () -> Unit
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Season?>(null) }
    var creating by remember { mutableStateOf(false) }
    var ending by remember { mutableStateOf<Season?>(null) }
    var error by remember(pod.id) { mutableStateOf<String?>(null) }
    var openPast by remember(pod.id) { mutableStateOf<String?>(null) }
    var showNights by remember(pod.id) { mutableStateOf(false) }
    // Seasons this phone already tried to close, so a refusal doesn't repeat forever.
    val autoEnded = remember(pod.id) { mutableSetOf<String>() }
    fun nameOf(userId: String?, name: String): String = userId?.let { overview.person(it)?.displayName } ?: name

    if (unavailable) {
        LeaguePanel {
            LeagueTitle("League")
            Text("$LEAGUE_UNAVAILABLE.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        return
    }
    if (seasons == null) {
        if (loadError != null) Notice(loadError, warn = true)
        return
    }

    val today = LocalDate.now().toString()
    val running = runningSeason(seasons)
    val status = running?.let { seasonStatus(it, games, today) }
    val table = remember(running, games) { running?.let { seasonTable(it, games) } }
    val canManage = running != null && canManageSeason(running, me.userId, pod.owner)
    fun resolved(list: List<LeagueStanding>) = list.map { it.copy(name = nameOf(it.userId, it.name)) }

    fun end(season: Season, endedAt: Long) {
        val t = seasonTable(season, games)
        val standings = resolved(t.standings)
        scope.launch {
            try {
                social.api.endPodSeason(season.id, endedAt, championNames(standings.filter { it.rank == 1 }), standings)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong."
            }
            onChanged()
        }
    }

    // A season over by its own rules (its last day or last game night has passed) is closed by
    // whoever may close it, the first time they look: its table is kept as it ended.
    LaunchedEffect(running?.id, status, canManage) {
        if (running != null && status == SeasonStatus.OVER && canManage && autoEnded.add(running.id)) {
            end(running, seasonEndedAt(running, seasonTable(running, games), System.currentTimeMillis()))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        error?.let { Notice(it, warn = true) }
        if (running == null || table == null || status == null) {
            LeaguePanel {
                LeagueTitle("League")
                Text(
                    "Run a season: points for wins, one table everyone in the pod sees, and a champion at the end.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                GoldButton("Start ${nextSeasonName(seasons)}", { creating = true }, icon = { Icon(Icons.Filled.EmojiEvents, contentDescription = null, modifier = Modifier.size(18.dp)) })
            }
        } else {
            LeaguePanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(running.name, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            when (status) {
                                SeasonStatus.UPCOMING -> "Starts ${shortDay(running.startsOn)}"
                                SeasonStatus.OVER -> "Over · ${plural(table.nights.size, "game night")}"
                                else -> "Running · ${plural(table.nights.size, "game night")}" +
                                    (running.maxNights?.let { " of $it" } ?: "")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (status == SeasonStatus.RUNNING) colors.accent else colors.textMuted
                        )
                    }
                    if (canManage) {
                        TextButton(onClick = { editing = running }) { Text("Edit", color = colors.accent) }
                    }
                }
                Text(seasonDays(running) + " · " + rulesSummary(running.rules), style = MaterialTheme.typography.labelMedium, color = colors.textDim, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                if (status == SeasonStatus.OVER) {
                    championLine(running.name, championNames(resolved(table.champions)))?.let { ChampionLine(it) }
                }
                if (table.standings.isEmpty()) {
                    Text(
                        if (status == SeasonStatus.UPCOMING) "Games recorded from ${shortDay(running.startsOn)} count."
                        else "No games in this season yet. Record a game and it counts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted
                    )
                } else {
                    StandingsTable(table.standings, { id, n -> nameOf(id, n) }, me.userId)
                    if (table.perNight.isNotEmpty()) {
                        Text(
                            if (showNights) "Hide points per game night" else "Points per game night",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.accent,
                            modifier = Modifier.clickable { showNights = !showNights }.padding(top = 10.dp, bottom = 4.dp)
                        )
                        if (showNights) {
                            table.perNight.forEach { n ->
                                Column(Modifier.padding(vertical = 4.dp)) {
                                    Text("${shortDay(n.night)} · ${plural(n.games, "game")}", style = MaterialTheme.typography.bodySmall, color = colors.textDim)
                                    Text(
                                        n.scores.joinToString(" · ") { "${nameOf(it.userId, it.name)} ${it.points}" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.textPrimary
                                    )
                                }
                            }
                        }
                    }
                }
                if (canManage) {
                    Row(Modifier.padding(top = 10.dp)) {
                        LineButton("End season", { ending = running })
                    }
                }
            }
        }

        val past = seasons.filter { it.endedAt != null }
        if (past.isNotEmpty()) {
            LeaguePanel {
                LeagueTitle("Past seasons")
                past.forEach { s ->
                    val open = openPast == s.id
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { openPast = if (open) null else s.id }.padding(vertical = 6.dp)
                    ) {
                        Text(s.name, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                        Text(
                            championLine(s.name, s.champion) ?: "No games were played.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (s.champion != null) colors.accent else colors.textMuted
                        )
                        Text(seasonDays(s), style = MaterialTheme.typography.labelMedium, color = colors.textDim)
                    }
                    if (open) {
                        val final = s.standings ?: seasonTable(s, games).standings
                        if (final.isNotEmpty()) StandingsTable(final, { id, n -> nameOf(id, n) }, me.userId)
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        SeasonDialog(
            social = social,
            pod = pod,
            season = editing,
            suggestedName = nextSeasonName(seasons),
            onSaved = { creating = false; editing = null; onChanged() },
            onDismiss = { creating = false; editing = null }
        )
    }
    ending?.let { s ->
        val t = seasonTable(s, games)
        val champion = championNames(resolved(t.champions))
        AlertDialog(
            containerColor = colors.surface,
            onDismissRequest = { ending = null },
            title = { Text("End ${s.name}?", color = colors.accentLight) },
            text = {
                Text(
                    "The table is kept as it is now" + (champion?.let { ", and $it ${if (" & " in it) "share the title" else "is champion"}." } ?: ".") +
                        " Games recorded after this don't count for it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary
                )
            },
            confirmButton = { GoldButton("End season", { ending = null; end(s, System.currentTimeMillis()) }) },
            dismissButton = { TextButton(onClick = { ending = null }) { Text("Cancel", color = colors.textMuted) } }
        )
    }
}

private fun plural(n: Int, one: String, many: String = "${one}s") = "$n ${if (n == 1) one else many}"

@Composable
private fun LeaguePanel(content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.surface).padding(16.dp), content = content)
}

@Composable
private fun LeagueTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = LocalAppColors.current.textPrimary, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun ChampionLine(text: String) {
    val colors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp).padding(end = 4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = colors.accent)
    }
}

/** The table: rank, player, points, games, wins, win rate and streak. */
@Composable
private fun StandingsTable(rows: List<LeagueStanding>, nameOf: (String?, String) -> String, me: String) {
    val colors = LocalAppColors.current
    @Composable
    fun Cell(text: String, width: Int, bold: Boolean = false, color: androidx.compose.ui.graphics.Color = colors.textMuted) {
        Text(
            text,
            style = if (bold) NumberStyle(17) else MaterialTheme.typography.bodySmall,
            color = color,
            maxLines = 1,
            modifier = Modifier.width(width.dp)
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
        Cell("", 20)
        Text("Player", style = MaterialTheme.typography.labelMedium, color = colors.textDim, modifier = Modifier.weight(1f))
        Cell("Pts", 34, color = colors.textDim)
        Cell("G", 26, color = colors.textDim)
        Cell("W", 26, color = colors.textDim)
        Cell("Win %", 42, color = colors.textDim)
        Cell("Streak", 44, color = colors.textDim)
    }
    rows.forEach { s ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Cell("${s.rank}", 20, color = colors.textDim)
            Text(
                nameOf(s.userId, s.name) + if (s.userId == me) " (you)" else "",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (s.rank == 1) FontWeight.Bold else FontWeight.Normal,
                color = if (s.rank == 1) colors.accent else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Cell("${s.points}", 34, bold = true, color = colors.textPrimary)
            Cell("${s.games}", 26)
            Cell("${s.wins}", 26)
            Cell("${s.winRate}%", 42)
            Cell(s.streak.ifEmpty { "–" }, 44, color = if (s.streak.startsWith("W")) colors.accent else colors.textMuted)
        }
    }
}

/** How a season ends: on a date, after a number of game nights, or when someone ends it. */
private enum class EndBy { DATE, NIGHTS, NONE }

/** Starting a season, or changing the running one ([season]). */
@Composable
private fun SeasonDialog(
    social: SocialRepository,
    pod: Pod,
    season: Season?,
    suggestedName: String,
    onSaved: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(season?.name ?: suggestedName) }
    var startsOn by remember { mutableStateOf(season?.startsOn ?: LocalDate.now().toString()) }
    var endBy by remember {
        mutableStateOf(
            when {
                season == null -> EndBy.NIGHTS
                season.endsOn != null -> EndBy.DATE
                season.maxNights != null -> EndBy.NIGHTS
                else -> EndBy.NONE
            }
        )
    }
    var endsOn by remember { mutableStateOf(season?.endsOn ?: LocalDate.now().plusMonths(3).toString()) }
    var nights by remember { mutableStateOf((season?.maxNights ?: 8).toString()) }
    var rules by remember { mutableStateOf(season?.rules ?: LEAGUE_PRESETS.first().rules) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun pickDay(current: String, onPick: (String) -> Unit) {
        val d = runCatching { LocalDate.parse(current) }.getOrNull() ?: LocalDate.now()
        DatePickerDialog(context, { _, y, m, day -> onPick(LocalDate.of(y, m + 1, day).toString()) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
    }

    fun save() {
        val end = endsOn.takeIf { endBy == EndBy.DATE }
        val max = if (endBy == EndBy.NIGHTS) nights.toIntOrNull() ?: 0 else null
        val problem = seasonProblem(name, startsOn, end, max)
        if (problem != null) { error = problem; return }
        busy = true
        error = null
        scope.launch {
            try {
                if (season == null) social.api.createPodSeason(pod.id, name, startsOn, end, max, rules)
                else social.api.updatePodSeason(season.id, name, startsOn, end, max, rules)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong."
                busy = false
                return@launch
            }
            onSaved()
        }
    }

    AlertDialog(
        containerColor = colors.surface,
        onDismissRequest = onDismiss,
        title = { Text(if (season == null) "Start a season" else "Change ${season.name}", color = colors.accentLight) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                LeagueLabel("Name")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    singleLine = true,
                    colors = socialFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                LeagueLabel("Starts", top = 14)
                PillChip(shortDay(startsOn), selected = true, onClick = { pickDay(startsOn) { startsOn = it } })
                LeagueLabel("Ends", top = 14)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    PillChip("After game nights", selected = endBy == EndBy.NIGHTS, onClick = { endBy = EndBy.NIGHTS })
                    PillChip("On a date", selected = endBy == EndBy.DATE, onClick = { endBy = EndBy.DATE })
                    PillChip("When we end it", selected = endBy == EndBy.NONE, onClick = { endBy = EndBy.NONE })
                }
                when (endBy) {
                    EndBy.DATE -> Row(Modifier.padding(top = 8.dp)) {
                        PillChip(shortDay(endsOn), selected = true, onClick = { pickDay(endsOn) { endsOn = it } })
                    }
                    EndBy.NIGHTS -> OutlinedTextField(
                        value = nights,
                        onValueChange = { v -> nights = v.filter { it.isDigit() }.take(3) },
                        label = { Text("Game nights") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = socialFieldColors(),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    EndBy.NONE -> Text(
                        "It runs until whoever started it, or the pod's owner, ends it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                LeagueLabel("Points", top = 14)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    LEAGUE_PRESETS.forEach { p -> PillChip(p.label, selected = rules.preset == p.id, onClick = { rules = p.rules }) }
                    PillChip("Custom", selected = rules.preset == PRESET_CUSTOM, onClick = { rules = rules.copy(preset = PRESET_CUSTOM) })
                }
                Spacer(Modifier.height(6.dp))
                RuleStepper("A win", rules.win) { rules = rules.copy(preset = PRESET_CUSTOM, win = it) }
                RuleStepper("Second place", rules.second) { rules = rules.copy(preset = PRESET_CUSTOM, second = it) }
                RuleStepper("A draw", rules.draw) { rules = rules.copy(preset = PRESET_CUSTOM, draw = it) }
                RuleStepper("Playing a game", rules.played) { rules = rules.copy(preset = PRESET_CUSTOM, played = it) }
                RuleStepper("First blood", rules.firstBlood) { rules = rules.copy(preset = PRESET_CUSTOM, firstBlood = it) }
                RuleStepper("Winning with a new deck", rules.newDeckWin) { rules = rules.copy(preset = PRESET_CUSTOM, newDeckWin = it) }
                Text(
                    "Second place and first blood count when they're picked as a game is recorded. A new deck is one its player hasn't played in this pod before.",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textDim,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Text(
                    "Every pod game played on the season's days counts. Everyone in the pod sees the same table.",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textDim,
                    modifier = Modifier.padding(top = 6.dp)
                )
                error?.let { Notice(it, warn = true, modifier = Modifier.padding(top = 10.dp)) }
            }
        },
        confirmButton = {
            GoldButton(if (busy) "Saving…" else if (season == null) "Start" else "Save", { save() }, enabled = !busy)
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textMuted) }
        }
    )
}

@Composable
private fun LeagueLabel(text: String, top: Int = 0) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = LocalAppColors.current.textMuted, modifier = Modifier.padding(top = top.dp, bottom = 6.dp))
}

@Composable
private fun RuleStepper(label: String, value: Int, onChange: (Int) -> Unit) {
    val colors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange((value - 1).coerceAtLeast(0)) }, enabled = value > 0) {
            Icon(Icons.Filled.Remove, contentDescription = "Fewer points for $label", tint = if (value > 0) colors.accent else colors.textDim)
        }
        Text("$value", style = NumberStyle(17), color = colors.textPrimary, modifier = Modifier.width(22.dp))
        IconButton(onClick = { onChange((value + 1).coerceAtMost(MAX_RULE_POINTS)) }, enabled = value < MAX_RULE_POINTS) {
            Icon(Icons.Filled.Add, contentDescription = "More points for $label", tint = if (value < MAX_RULE_POINTS) colors.accent else colors.textDim)
        }
    }
}
