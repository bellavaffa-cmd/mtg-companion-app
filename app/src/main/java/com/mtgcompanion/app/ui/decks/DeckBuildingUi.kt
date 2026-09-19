package com.mtgcompanion.app.ui.decks

import com.mtgcompanion.app.data.RoleTags
import com.mtgcompanion.app.data.DeckRole
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import com.mtgcompanion.app.ui.theme.Surface3
import com.mtgcompanion.app.ui.theme.NumberStyle
import com.mtgcompanion.app.ui.theme.LocalAppColors
import com.mtgcompanion.app.ui.common.StatusBadge
import com.mtgcompanion.app.ui.common.PillChip
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckCardEntry
import com.mtgcompanion.app.data.DeckRepository
import com.mtgcompanion.app.data.MissingCard
import com.mtgcompanion.app.data.NearMissCombo
import com.mtgcompanion.app.data.RoleStatus
import com.mtgcompanion.app.data.VersionSummary
import com.mtgcompanion.app.data.cardNameKeys
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.network.spellbook.Variant
import com.mtgcompanion.app.ui.common.ComboDetailDialog
import com.mtgcompanion.app.ui.common.ComboSummaryRow
import com.mtgcompanion.app.ui.common.InlineManaText
import com.mtgcompanion.app.ui.common.elevatedCard
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.Surface2
import com.mtgcompanion.app.ui.collection.OwnedCard
import com.mtgcompanion.app.data.GameResult
import com.mtgcompanion.app.data.HandOdds
import com.mtgcompanion.app.data.KEEPABLE_LANDS
import com.mtgcompanion.app.data.oddsPercent
import com.mtgcompanion.app.data.Matchup
import com.mtgcompanion.app.data.gameStats
import com.mtgcompanion.app.data.record
import com.mtgcompanion.app.ui.theme.TextDim
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Cut candidates are flagged in this color everywhere they appear. */
internal val CutColor = Color(0xFFFF8A4C)
private val ShortColor = Color(0xFFE56B5D)

// ---- Cards tab: filter + badges ----

enum class CardFilter(val label: String) { ALL("All"), CUT("Cut candidates"), COMBO("Combo pieces") }

@Composable
internal fun CardFilterChips(selected: CardFilter, cutCount: Int, comboCount: Int, onSelect: (CardFilter) -> Unit, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.horizontalScroll(rememberScrollState())) {
        CardFilter.entries.forEach { filter ->
            val count = when (filter) {
                CardFilter.ALL -> null
                CardFilter.CUT -> cutCount
                CardFilter.COMBO -> comboCount
            }
            PillChip(filter.label, selected == filter, onClick = { onSelect(filter) }, count = count)
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) Bg else TextPrimary,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Gold else Surface)
            .border(BorderStroke(1.dp, if (selected) Gold else BorderColor), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

/** Cut candidate, combo piece, or one card away from a combo — each at a glance. */
@Composable
internal fun DeckCardBadges(replaceable: Boolean, comboPiece: Boolean, nearMiss: Boolean, modifier: Modifier = Modifier) {
    if (!replaceable && !comboPiece && !nearMiss) return
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
        if (replaceable) Badge("CUT", Icons.Filled.SwapHoriz, fill = CutColor)
        if (comboPiece) Badge("COMBO", Icons.Filled.Bolt, fill = Gold)
        if (nearMiss && !comboPiece) Badge("+1 COMBO", Icons.Filled.Bolt, fill = null)
    }
}

@Composable
private fun Badge(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, fill: Color?) {
    val app = LocalAppColors.current
    if (fill == null) {
        StatusBadge(label, fill = app.accent, ink = app.onAccent, icon = icon, outlined = true)
    } else {
        StatusBadge(label, fill = fill, ink = if (fill == app.accent) app.onAccent else Color(0xFF1E0D02), icon = icon)
    }
}

// ---- Considering tab ----

@Composable
internal fun ConsideringTab(
    deck: Deck,
    analysis: DeckAnalysis,
    prices: Map<String, Double>,
    onZoom: (String) -> Unit,
    onAddToDeck: (DeckCardEntry) -> Unit,
    onSwapIn: (DeckCardEntry) -> Unit,
    onRemove: (DeckCardEntry) -> Unit
) {
    val cutCount = deck.cards.count { it.replaceable }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Panel {
                SectionLabel("Considering (${deck.considering.size})")
                Text(
                    "Cards you think might work but haven't committed to. They don't count toward this deck's size, curve, price, legality or combos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (cutCount > 0) {
                    Text(
                        "$cutCount cut candidate${if (cutCount == 1) "" else "s"} in the deck — SWAP IN trades one out for a card here.",
                        style = MaterialTheme.typography.labelMedium,
                        color = CutColor,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
        if (deck.considering.isEmpty()) {
            item {
                Text(
                    "Nothing here yet. Add cards from a card's page (Add to deck → Consider), from the REC tab, or move a card out of the deck from its long-press menu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
        items(deck.considering, key = { it.scryfallId }) { entry ->
            val completesCombo = cardNameKeys(entry.name).any { it in analysis.comboCompleters }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .elevatedCard(shape = RoundedCornerShape(16.dp))
                    .clickable { onZoom(entry.scryfallId) }
                    .padding(12.dp)
            ) {
                AsyncImage(
                    model = entry.imageUrl.toArtCropUrl(),
                    contentDescription = entry.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(width = 64.dp, height = 46.dp).clip(RoundedCornerShape(10.dp))
                )
                Column(Modifier.weight(1f)) {
                    Text(entry.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        prices[entry.scryfallId]?.let { Text("$%.2f".format(it), style = MaterialTheme.typography.labelMedium, color = TextMuted) }
                        if (completesCombo) Badge("Completes a combo", Icons.Filled.Bolt, fill = Gold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { onAddToDeck(entry) }) { Text("Add", color = Gold, style = MaterialTheme.typography.labelMedium) }
                        TextButton(onClick = { onSwapIn(entry) }, enabled = deck.cards.isNotEmpty()) {
                            Text("Swap in", color = if (deck.cards.isNotEmpty()) CutColor else TextDim, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                IconButton(onClick = { onRemove(entry) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Stop considering ${entry.name}", tint = TextDim)
                }
            }
        }
    }
}

/**
 * Picks the other half of a swap. Cut candidates are listed first when choosing what to take out,
 * since that's what they're for.
 */
@Composable
internal fun SwapPickerDialog(
    title: String,
    message: String,
    options: List<DeckCardEntry>,
    onPick: (DeckCardEntry) -> Unit,
    onDismiss: () -> Unit
) {
    val ordered = options.sortedWith(compareByDescending<DeckCardEntry> { it.replaceable }.thenBy { it.name.lowercase() })
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text(title, color = GoldLight) },
        text = {
            Column {
                Text(message, style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.padding(bottom = 8.dp))
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(ordered, key = { it.scryfallId }) { entry ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth().clickable { onPick(entry) }.padding(vertical = 7.dp)
                        ) {
                            AsyncImage(
                                model = entry.imageUrl.toArtCropUrl(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(width = 48.dp, height = 34.dp).clip(RoundedCornerShape(8.dp))
                            )
                            Text(entry.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (entry.replaceable) Badge("CUT", Icons.Filled.SwapHoriz, fill = CutColor)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } }
    )
}

/** Shown before flagging a combo piece as a cut candidate — cutting it would break those combos. */
@Composable
internal fun ComboPieceWarningDialog(cardName: String, combos: List<Variant>, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("$cardName is a combo piece", color = GoldLight) },
        text = {
            Column {
                Text(
                    "Cutting it would break ${if (combos.size == 1) "this combo" else "these ${combos.size} combos"}:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                combos.take(5).forEach { combo ->
                    Text(
                        "• " + combo.uses.joinToString(" + ") { it.card.name },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (combos.size > 5) Text("…and ${combos.size - 5} more", style = MaterialTheme.typography.labelMedium, color = TextDim, modifier = Modifier.padding(top = 4.dp))
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = CutColor, contentColor = Bg)) { Text("Mark anyway", color = Bg) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } }
    )
}

// ---- Stats tab: roles, mana advice, versions ----

private val LossColor = Color(0xFFD3402F)

private fun resultColor(result: String, win: Color, other: Color): Color = when (result) {
    "WIN" -> win
    "LOSS" -> LossColor
    else -> other
}

/**
 * The deck's games: its record and recent form, how long its games run, and how it does against
 * each commander and each person it has faced — then the latest games, each removable.
 */
@Composable
internal fun MatchRecordPanel(results: List<GameResult>, onLog: () -> Unit, onRemove: (String) -> Unit) {
    val stats = remember(results) { gameStats(results) }
    var showAll by remember { mutableStateOf(false) }
    Panel {
        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            SectionLabel("Match record")
            TextButton(onClick = onLog) { Text("Log result", color = Gold, style = MaterialTheme.typography.labelMedium) }
        }
        if (stats.games == 0) {
            Text(
                "No games logged yet. Games you play with your phone as a remote at a life counter table are saved here by themselves.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                modifier = Modifier.padding(top = 4.dp)
            )
            return@Panel
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${stats.wins}–${stats.losses}" + if (stats.draws > 0) "–${stats.draws}" else "", style = NumberStyle(40), color = TextPrimary)
            Text(
                "${stats.winRate}% win rate over ${stats.games} game${if (stats.games == 1) "" else "s"}",
                style = MaterialTheme.typography.bodyMedium,
                color = GoldLight,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        // Recent form, newest on the left.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
            stats.recent.forEach { r ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(resultColor(r, Gold, Surface3))
                ) {
                    Text(r.take(1), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (r == "DRAW") TextMuted else Bg)
                }
            }
            stats.streak?.let { (result, n) ->
                val word = when (result) { "WIN" -> "wins"; "LOSS" -> "losses"; else -> "draws" }
                Text("$n $word in a row", style = MaterialTheme.typography.labelMedium, color = TextMuted, modifier = Modifier.padding(start = 6.dp))
            }
        }
        val length = listOfNotNull(stats.averageMinutes?.let { "$it min" }, stats.averageTurns?.let { "$it turns" })
        if (length.isNotEmpty()) {
            Text("A game takes about ${length.joinToString(" · ")}", style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.padding(top = 8.dp))
        }
        if (stats.commanders.isNotEmpty()) MatchupList("Commanders faced", stats.commanders)
        if (stats.opponents.isNotEmpty()) MatchupList("Against", stats.opponents)

        Text("Latest games", style = MaterialTheme.typography.labelMedium, color = TextMuted, modifier = Modifier.padding(top = 14.dp))
        val newest = results.sortedByDescending { it.playedAt }
        Column(modifier = Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            (if (showAll) newest else newest.take(5)).forEach { game ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(game.result, style = MaterialTheme.typography.labelMedium, color = resultColor(game.result, Gold, TextMuted), modifier = Modifier.width(40.dp))
                    Text(
                        listOfNotNull(
                            game.opponent?.let { "vs $it" },
                            game.commanders.takeIf { it.isNotEmpty() }?.joinToString(", "),
                            game.turns?.let { "$it turns" }
                        ).joinToString(" · ").ifEmpty { "—" },
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onRemove(game.id) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove this result", tint = TextDim, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        if (newest.size > 5) {
            TextButton(onClick = { showAll = !showAll }) {
                Text(if (showAll) "Show fewer" else "Show all ${newest.size}", color = Gold, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * How the opening hand tends to look, worked out exactly from the list: a keepable seven, how many
 * lands it holds, ramp in it, and hitting land drops.
 */
@Composable
internal fun HandOddsPanel(odds: HandOdds) {
    Panel {
        SectionLabel("Opening hand")
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            Text(oddsPercent(odds.keepable), style = NumberStyle(40), color = TextPrimary)
            Column(Modifier.padding(bottom = 4.dp)) {
                Text("chance of a keepable seven (${KEEPABLE_LANDS.first}–${KEEPABLE_LANDS.last} lands)", style = MaterialTheme.typography.bodyMedium, color = GoldLight)
                Text(
                    "${oddsPercent(odds.keepableWithMulligan)} within one mulligan" + if (odds.drawsOnTurnOne) " — the first is free in Commander" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted
                )
            }
        }
        // Lands in the opening seven: 0 to 7.
        val most = odds.landSpread.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(92.dp)) {
            odds.landSpread.forEachIndexed { lands, p ->
                val keep = lands in KEEPABLE_LANDS
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom, modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Text(if (p >= 0.005) "${Math.round(p * 100)}" else "", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight(((p / most) * 0.7).toFloat().coerceAtLeast(0.02f))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(if (keep) Gold else Surface3)
                    )
                    Text("$lands", style = MaterialTheme.typography.labelSmall, color = if (keep) TextPrimary else TextDim)
                }
            }
        }
        Text("Lands in the opening seven, as a % of hands", style = MaterialTheme.typography.labelSmall, color = TextDim, modifier = Modifier.padding(top = 4.dp))
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (odds.ramp > 0) OddsRow("A ramp card in your opening hand", odds.rampInHand)
            odds.landDrops.forEach { (turn, p) -> OddsRow("$turn lands by turn $turn", p) }
            if (odds.ramp > 0) OddsRow("2+ lands and a ramp card by turn 2", odds.landsAndRampByTurn2)
        }
        Text(
            "From the ${odds.library} cards in the library: ${odds.lands} lands, ${odds.ramp} ramp. " +
                if (odds.drawsOnTurnOne) "Multiplayer Commander draws on turn 1." else "On the play, with no draw on turn 1.",
            style = MaterialTheme.typography.labelMedium,
            color = TextDim,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

@Composable
private fun OddsRow(label: String, p: Double) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
        Text(oddsPercent(p), style = NumberStyle(18), color = Gold)
    }
}

/** One matchup a line: the name, how many games, and the record. */
@Composable
private fun MatchupList(title: String, rows: List<Matchup>) {
    Text(title, style = MaterialTheme.typography.labelMedium, color = TextMuted, modifier = Modifier.padding(top = 14.dp))
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { m ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(m.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("${m.games} ${if (m.games == 1) "game" else "games"}", style = MaterialTheme.typography.labelMedium, color = TextDim, modifier = Modifier.padding(end = 12.dp))
                Text(
                    m.record(),
                    style = NumberStyle(17),
                    color = when {
                        m.wins > m.losses -> Gold
                        m.wins < m.losses -> LossColor
                        else -> TextMuted
                    }
                )
            }
        }
    }
}

/**
 * The cards the user owns that do [label]'s job and aren't in the deck: each can go into the deck,
 * or onto its Considering list ([considering]: the names already there).
 */
@Composable
internal fun OwnedForRoleDialog(
    label: String,
    cards: List<OwnedCard>,
    considering: Set<String>,
    onAdd: (OwnedCard, considering: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("$label you own", color = GoldLight) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("In your binders, not in this deck yet.", style = MaterialTheme.typography.labelMedium, color = TextDim)
                if (cards.isEmpty()) Text("All added.", color = TextMuted)
                LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(cards, key = { it.key }) { card ->
                        val consideringIt = card.key in considering
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface2).padding(start = 6.dp, top = 6.dp, end = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                AsyncImage(
                                    model = card.imageUrl.toArtCropUrl(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(width = 52.dp, height = 38.dp).clip(RoundedCornerShape(8.dp)).background(Surface3)
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(card.name, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        if (consideringIt) "On Considering" else card.where.joinToString(", ") { it.name },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            // The buttons get their own line, so a long name isn't cut short.
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                if (!consideringIt) {
                                    TextButton(onClick = { onAdd(card, true) }) { Text("Consider", color = TextMuted, style = MaterialTheme.typography.labelMedium) }
                                }
                                TextButton(onClick = { onAdd(card, false) }) { Text("Add to deck", color = Gold, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = Gold) } }
    )
}

@Composable
internal fun RolesPanel(
    report: RoleReport?,
    onTag: ((String) -> Unit)? = null,
    owned: Map<String, List<OwnedCard>> = emptyMap(),
    onOwned: ((DeckRole) -> Unit)? = null
) {
    Panel {
        SectionLabel("Deck roles")
        if (report == null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Gold)
                Text("Checking what each card does…", style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.padding(start = 10.dp))
            }
            return@Panel
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
            report.counts.forEach { count ->
                var expanded by remember(count.role) { mutableStateOf(false) }
                val color = when (count.status) {
                    RoleStatus.SHORT -> ShortColor
                    RoleStatus.ON_TARGET -> Gold
                    RoleStatus.OVER -> GoldLight
                    RoleStatus.NO_TARGET -> Gold
                }
                Column(Modifier.fillMaxWidth().clickable(enabled = count.cards.isNotEmpty()) { expanded = !expanded }) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(count.role.label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
                        Text("${count.count}", style = NumberStyle(20), color = color)
                        if (count.min != null) {
                            Text(
                                " / ${count.min}–${count.max}",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextMuted
                            )
                        }
                    }
                    if (count.max != null) {
                        val fill = remember(count.role) { Animatable(0f) }
                        LaunchedEffect(count.count, count.max) { fill.animateTo((count.count.toFloat() / count.max).coerceIn(0f, 1f), tween(850, easing = FastOutSlowInEasing)) }
                        Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Surface3)) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fill.value)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(color)
                            )
                        }
                    }
                    when (count.status) {
                        RoleStatus.SHORT -> Text("${count.min!! - count.count} short of the usual minimum", style = MaterialTheme.typography.labelMedium, color = ShortColor, modifier = Modifier.padding(top = 2.dp))
                        RoleStatus.OVER -> Text("Above the usual range", style = MaterialTheme.typography.labelMedium, color = TextMuted, modifier = Modifier.padding(top = 2.dp))
                        else -> Unit
                    }
                    // The cards already in the user's binders that would fill the gap.
                    val have = owned[count.role.otag].orEmpty()
                    if (have.isNotEmpty() && onOwned != null && count.status != RoleStatus.OVER) {
                        Text(
                            "You own ${have.size} more · add from your binders",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (count.status == RoleStatus.SHORT) Gold else GoldLight,
                            modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).clickable { onOwned(count.role) }.padding(vertical = 2.dp)
                        )
                    }
                    if (expanded) {
                        Text(count.cards.joinToString(", "), style = MaterialTheme.typography.labelMedium, color = TextMuted, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        // Every other job the deck's cards do, from their tags; tap one to see those cards.
        val core = DeckRole.TAGGED.mapNotNull { it.otag }.toSet()
        val more = report.tags.filter { (id, _) -> id !in core }
        if (more.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SectionLabel("What the cards do")
            val most = more.maxOf { it.second }.coerceAtLeast(1)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                more.forEach { (id, n) ->
                    Column(Modifier.fillMaxWidth().clickable(enabled = onTag != null) { onTag?.invoke(RoleTags.label(id)) }) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(RoleTags.label(id), style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
                            Text("$n", style = NumberStyle(18), color = Gold)
                        }
                        Box(Modifier.fillMaxWidth().padding(top = 5.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Surface3)) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(n.toFloat() / most).clip(RoundedCornerShape(3.dp)).background(Gold.copy(alpha = 0.8f)))
                        }
                    }
                }
            }
        }
        Text(
            if (report.fromTagger) "Roles from Scryfall's community card tagger — tap a role to see its cards. Targets are common Commander guidelines, not rules."
            else "Offline — roles estimated from card text, which misses some cards. Reconnect for tagger data.",
            style = MaterialTheme.typography.labelMedium,
            color = TextDim,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

@Composable
internal fun ManaAdviceList(advice: List<String>) {
    if (advice.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 10.dp)) {
        advice.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = CutColor, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                InlineManaText(line, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
            }
        }
    }
}

private val versionDate = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())

@Composable
internal fun VersionHistoryPanel(history: List<VersionSummary>, onOpen: (VersionSummary) -> Unit) {
    Panel {
        SectionLabel("Version history")
        if (history.isEmpty()) {
            Text(
                "Each time you change this deck's list, the new list is saved here as a version — with what changed and how it did in games you log.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                modifier = Modifier.padding(top = 4.dp)
            )
            return@Panel
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 6.dp)) {
            history.take(12).forEachIndexed { index, summary ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(summary) }.padding(vertical = 7.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (index == 0) "Current · ${versionDate.format(Date(summary.version.savedAt))}" else versionDate.format(Date(summary.version.savedAt)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Text(
                            when {
                                summary.isBaseline || summary.version.id.startsWith(DeckRepository.BASELINE_PREFIX) -> "Starting list · ${summary.version.cards.values.sum()} cards"
                                else -> changeSummary(summary)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMuted
                        )
                    }
                    if (summary.games > 0) {
                        Text(
                            "${summary.wins}-${summary.losses}${if (summary.draws > 0) "-${summary.draws}" else ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GoldLight,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        Text(
            "Record = games logged while that version was the current list.",
            style = MaterialTheme.typography.labelMedium,
            color = TextDim,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

private fun changeSummary(summary: VersionSummary): String {
    val added = summary.added.sumOf { it.second }
    val removed = summary.removed.sumOf { it.second }
    return when {
        added == 0 && removed == 0 -> "Commander changed"
        else -> listOfNotNull(if (added > 0) "+$added" else null, if (removed > 0) "−$removed" else null).joinToString("  ")
    }
}

@Composable
internal fun VersionDetailDialog(summary: VersionSummary, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text(versionDate.format(Date(summary.version.savedAt)), color = GoldLight) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                if (summary.games > 0) {
                    Text(
                        "Record on this version: ${summary.wins}-${summary.losses}${if (summary.draws > 0) "-${summary.draws}" else ""} (${summary.wins * 100 / summary.games}% wins)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GoldLight,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
                if (summary.isBaseline) {
                    Text("The earliest saved list:", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    summary.version.cards.entries.sortedBy { it.key }.forEach { (name, qty) ->
                        Text("$qty  $name", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                    }
                } else {
                    if (summary.added.isNotEmpty()) {
                        Text("Added", style = MaterialTheme.typography.labelMedium, color = Gold)
                        summary.added.forEach { (name, qty) -> Text("+$qty  $name", style = MaterialTheme.typography.bodySmall, color = TextPrimary) }
                    }
                    if (summary.removed.isNotEmpty()) {
                        Text("Removed", style = MaterialTheme.typography.labelMedium, color = ShortColor, modifier = Modifier.padding(top = 10.dp))
                        summary.removed.forEach { (name, qty) -> Text("−$qty  $name", style = MaterialTheme.typography.bodySmall, color = TextPrimary) }
                    }
                    if (summary.added.isEmpty() && summary.removed.isEmpty()) {
                        Text("Only the commander changed.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
                Text("Commander: ${summary.version.commanders.joinToString(" + ").ifEmpty { "none" }}", style = MaterialTheme.typography.labelMedium, color = TextMuted, modifier = Modifier.padding(top = 10.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Gold) } }
    )
}

// ---- REC tab: near-miss combos, budget swaps ----

internal fun LazyListScope.nearMissSection(nearMisses: List<NearMissCombo>, available: Boolean, onConsider: (String) -> Unit) {
    item { SectionLabel("One card away (${nearMisses.size})") }
    when {
        !available -> item { Text("Couldn't reach Commander Spellbook — check your connection.", style = MaterialTheme.typography.bodySmall, color = TextMuted) }
        nearMisses.isEmpty() -> item { Text("No combos are a single card away.", style = MaterialTheme.typography.bodySmall, color = TextMuted) }
        else -> items(nearMisses.take(15), key = { "near-" + it.combo.id }) { near ->
            var showCombo by remember { mutableStateOf(false) }
            Column {
                ComboSummaryRow(near.combo, onClick = { showCombo = true })
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 2.dp)) {
                    Text(
                        "Missing: ${near.missing.joinToString()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = GoldLight,
                        modifier = Modifier.weight(1f)
                    )
                    near.missing.firstOrNull()?.let { name ->
                        TextButton(onClick = { onConsider(name) }) { Text("Consider", color = Gold, style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
            if (showCombo) ComboDetailDialog(combo = near.combo, onDismiss = { showCombo = false })
        }
    }
}

private val BUDGET_THRESHOLDS = listOf(2.0, 5.0, 10.0, 20.0)

internal fun LazyListScope.budgetSwapsSection(
    state: BudgetSwapState,
    onFind: (Double) -> Unit,
    onConsider: (ScryfallCard) -> Unit,
    onMarkCut: (DeckCardEntry) -> Unit,
    onViewDetails: (String) -> Unit
) {
    item {
        Column {
            SectionLabel("Budget swaps")
            Text(
                "Cheaper cards that do the same job, in your colors and legal in this format. Pick a price above which to look:",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
            )
            val selected = (state as? BudgetSwapState.Done)?.threshold
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BUDGET_THRESHOLDS.forEach { threshold ->
                    FilterPill("$${threshold.toInt()}+", selected == threshold) { onFind(threshold) }
                }
            }
        }
    }
    when (state) {
        BudgetSwapState.Idle -> Unit
        BudgetSwapState.Loading -> item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Gold)
                Text("Looking for cheaper alternatives…", style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.padding(start = 10.dp))
            }
        }
        is BudgetSwapState.Failed -> item { Text(state.message, style = MaterialTheme.typography.bodySmall, color = TextMuted) }
        is BudgetSwapState.Done -> {
            if (state.swaps.isEmpty()) {
                item { Text("No cards in this deck cost $${state.threshold.toInt()} or more.", style = MaterialTheme.typography.bodySmall, color = TextMuted) }
            }
            items(state.swaps, key = { "swap-" + it.entry.scryfallId }) { swap ->
                Column(Modifier.fillMaxWidth().elevatedCard(shape = RoundedCornerShape(16.dp)).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(swap.entry.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "$%.2f".format(swap.priceUsd) + (swap.role?.let { " · ${it.label.lowercase()}" } ?: ""),
                                style = MaterialTheme.typography.labelMedium,
                                color = TextMuted
                            )
                        }
                        if (!swap.entry.replaceable) {
                            TextButton(onClick = { onMarkCut(swap.entry) }) { Text("Mark cut", color = CutColor, style = MaterialTheme.typography.labelMedium) }
                        } else {
                            Text("CUT", style = MaterialTheme.typography.labelMedium, color = CutColor, modifier = Modifier.padding(end = 8.dp))
                        }
                    }
                    if (swap.alternatives.isEmpty()) {
                        Text("No cheaper alternatives found.", style = MaterialTheme.typography.labelMedium, color = TextDim, modifier = Modifier.padding(top = 6.dp))
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState())) {
                            swap.alternatives.forEach { alt ->
                                Column(Modifier.width(96.dp)) {
                                    AsyncImage(
                                        model = alt.displayImageUrl,
                                        contentDescription = alt.name,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(8.dp)).clickable { onViewDetails(alt.name) }
                                    )
                                    Text(alt.prices?.usd?.let { "$$it" } ?: "—", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                                    Text(
                                        "Consider",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Gold,
                                        modifier = Modifier.clickable { onConsider(alt) }.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- Missing cards ----

@Composable
internal fun MissingCardsDialog(
    missing: List<MissingCard>,
    physicalDeck: Boolean,
    wishlists: List<Collection>,
    onAddToWishlist: (wishlistId: String?, newName: String?) -> Unit,
    onBuy: () -> Unit,
    onDismiss: () -> Unit,
    /** Friends who own them (signed in only). */
    onWhoHasIt: (() -> Unit)? = null
) {
    var pickingWishlist by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text(if (pickingWishlist) "Add to which wishlist?" else "Cards you don't own", color = GoldLight) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                if (!pickingWishlist) {
                    if (missing.isEmpty() && physicalDeck) {
                        // Every deck starts out Physical, which counts its own cards as owned — so
                        // "you own everything" would be true by definition, not a real check.
                        Text(
                            "This deck is set to Physical, so its own cards count as owned and nothing shows as missing. " +
                                "To see what you'd still need to buy, set it to Virtual or Prototype in Deck settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    } else if (missing.isEmpty()) {
                        Text("You own every card in this deck — counting your owned binders and Physical decks.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    } else {
                        Text(
                            "${missing.sumOf { it.need }} copies across ${missing.size} cards aren't in your owned binders or Physical decks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        missing.forEach { card ->
                            Text("${card.need}  ${card.entry.name}", style = MaterialTheme.typography.bodySmall, color = TextPrimary, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                } else {
                    wishlists.forEach { wishlist ->
                        Text(
                            wishlist.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            modifier = Modifier.fillMaxWidth().clickable { onAddToWishlist(wishlist.id, null) }.padding(vertical = 10.dp)
                        )
                    }
                    if (wishlists.isNotEmpty()) Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("New wishlist name", color = TextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Gold, unfocusedBorderColor = BorderColor, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Gold)
                    )
                }
            }
        },
        confirmButton = {
            if (missing.isNotEmpty()) {
                if (pickingWishlist) {
                    Button(onClick = { onAddToWishlist(null, newName) }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)) {
                        Text("Create & add", color = Bg)
                    }
                } else {
                    Row {
                        onWhoHasIt?.let { TextButton(onClick = it) { Text("Who has it?", color = Gold) } }
                        TextButton(onClick = onBuy) { Text("Buy", color = Gold) }
                        Button(onClick = { pickingWishlist = true }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)) {
                            Text("Add to wishlist", color = Bg)
                        }
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = TextMuted) } }
    )
}
