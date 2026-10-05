package com.mtgcompanion.app.ui.decks

import androidx.compose.foundation.background
import com.mtgcompanion.app.ui.common.a11yPane
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mtgcompanion.app.data.CompareRow
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.VersionSummary
import com.mtgcompanion.app.data.deckCounts
import com.mtgcompanion.app.data.diffDecks
import com.mtgcompanion.app.data.versionCounts
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.TextDim
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What a deck is compared with: another deck, or one of its own saved versions. */
internal data class CompareTarget(val label: String, val counts: Map<String, Int>)

private val compareDate = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())

/**
 * "Compare with…": the user's other decks, then this deck's saved versions (newest first, the
 * current one left out since it's the deck as it is).
 */
@Composable
internal fun ComparePickerDialog(
    decks: List<Deck>,
    versions: List<VersionSummary>,
    onPick: (CompareTarget) -> Unit,
    onDismiss: () -> Unit
) {
    val earlier = versions.drop(1)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Compare with…", color = GoldLight) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                if (decks.isEmpty() && earlier.isEmpty()) {
                    Text(
                        "Nothing to compare with yet: no other decks, and no earlier versions of this one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
                if (decks.isNotEmpty()) {
                    Text("Another deck", style = MaterialTheme.typography.labelMedium, color = Gold, modifier = Modifier.padding(bottom = 4.dp))
                    decks.sortedBy { it.name.lowercase() }.forEach { other ->
                        CompareChoice(
                            title = other.name,
                            detail = "${other.mode.label} · ${other.cards.sumOf { it.quantity }} cards",
                            onClick = { onPick(CompareTarget(other.name, deckCounts(other))) }
                        )
                    }
                }
                if (earlier.isNotEmpty()) {
                    Text(
                        "An earlier version of this deck",
                        style = MaterialTheme.typography.labelMedium,
                        color = Gold,
                        modifier = Modifier.padding(top = if (decks.isNotEmpty()) 12.dp else 0.dp, bottom = 4.dp)
                    )
                    earlier.forEach { summary ->
                        val date = compareDate.format(Date(summary.version.savedAt))
                        CompareChoice(
                            title = date,
                            detail = "${summary.version.cards.values.sum()} cards" + if (summary.games > 0) " · ${summary.wins}-${summary.losses}" else "",
                            onClick = { onPick(CompareTarget("version of $date", versionCounts(summary.version))) }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } }
    )
}

@Composable
private fun CompareChoice(title: String, detail: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(detail, style = MaterialTheme.typography.labelMedium, color = TextMuted)
    }
}

/**
 * The comparison, full screen: cards only in this deck, only in the other, and in both — with each
 * side's copies where they differ. Matched by card name (DeckCompare.kt), main deck only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CompareScreen(deck: Deck, target: CompareTarget, onDismiss: () -> Unit) {
    val diff = diffDecks(deckCounts(deck), target.counts)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            modifier = Modifier.a11yPane("Compare"),
            containerColor = Bg,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Compare", style = MaterialTheme.typography.titleLarge, maxLines = 1)
                            Text(
                                "${deck.name} vs ${target.label}",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = Gold) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(Bg).padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (diff.identical) {
                    item { Text("The two lists are the same.", style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
                }
                compareSection("Only in ${deck.name}", diff.onlyHere, "only-here") { "${it.here}" }
                compareSection("Only in ${target.label}", diff.onlyThere, "only-there") { "${it.there}" }
                compareSection("In both", diff.both, "both") { row ->
                    if (row.sameCount) "${row.here}" else "${row.here} → ${row.there}"
                }
            }
        }
    }
}

private fun LazyListScope.compareSection(title: String, rows: List<CompareRow>, key: String, count: (CompareRow) -> String) {
    item(key = "$key-header") {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("${rows.size}", style = MaterialTheme.typography.labelLarge, color = TextMuted)
        }
    }
    if (rows.isEmpty()) {
        item(key = "$key-none") { Text("None.", style = MaterialTheme.typography.bodySmall, color = TextDim) }
    }
    items(rows, key = { "$key-" + it.name.lowercase() }) { row ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text(row.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(
                count(row),
                style = MaterialTheme.typography.bodyMedium,
                color = if (row.here != row.there && row.here > 0 && row.there > 0) GoldLight else TextMuted,
                fontWeight = if (row.sameCount) FontWeight.Normal else FontWeight.Bold
            )
        }
    }
}
