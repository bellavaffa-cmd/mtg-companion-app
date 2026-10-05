package com.mtgcompanion.app.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.CheckKind
import com.mtgcompanion.app.data.CheckScope
import com.mtgcompanion.app.data.CheckSessions
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.PlaceKind
import com.mtgcompanion.app.data.lastCheckedLabel
import com.mtgcompanion.app.data.listedWhere
import com.mtgcompanion.app.data.markChecked
import com.mtgcompanion.app.data.markNoPlace
import com.mtgcompanion.app.data.placePath
import com.mtgcompanion.app.data.placesOf
import com.mtgcompanion.app.data.reconcile
import com.mtgcompanion.app.data.recordHere
import com.mtgcompanion.app.data.removeMissing
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.theme.LocalAppColors
import com.mtgcompanion.app.ui.theme.NumberStyle

/** How many missing cards show before "and 3 more". */
private const val SHOWN_MISSING = 5
private val MissingTint = Color(0xFFFF8A4C)

/**
 * A check's results, the web app's CheckResultsPage (src/pages/CheckResultsPage.tsx): how many are
 * where they should be, which are missing (listed here, not scanned) and which are extra (scanned
 * here, listed somewhere else or not at all), with what to do about each — Find it, Mark No place yet,
 * Remove from collection; Record them here, I'll put them back. Missing cards stay where they're
 * listed until one of those is tapped. Save results notes when the place was checked. The rules are
 * PlaceCheck.kt; the scans come from the scanner's check mode (CheckSessions).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckResultsScreen(
    placeId: String,
    collections: List<Collection>,
    decks: List<Deck>,
    onBack: () -> Unit,
    onChange: (StorageChange) -> Unit,
    /** Binders and decks changed together (Record them here, taking cards out of decks). */
    onApply: (List<Collection>, List<Deck>) -> Unit,
    /** Find it: the card's page, with Where it is. */
    onFindIt: (String) -> Unit,
    /** After Save results. */
    onSaved: (String) -> Unit
) {
    val colors = LocalAppColors.current
    val session = remember(placeId) { CheckSessions.load(placeId) }
    val places = placesOf(collections)
    val place = places.firstOrNull { it.id == placeId }
    val scope = CheckScope(placeId, session?.section)
    val result = remember(collections, decks, session) { reconcile(collections, decks, scope, session?.scans.orEmpty()) }
    var allMissing by remember { mutableStateOf(false) }
    var puttingBack by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var askDecks by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        if (place != null) Text(
                            listOfNotNull(placePath(places, place.id), session?.section).joinToString(" › "),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text("Check results", style = MaterialTheme.typography.titleLarge)
                    }
                },
                navigationIcon = { BackButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        }
    ) { padding ->
        if (place == null || session == null) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text(if (place != null) "No check going on here. Start one from the place's page." else "This place isn't here any more.", color = colors.textMuted)
            }
            return@Scaffold
        }
        val what = when (place.placeKind) { PlaceKind.BINDER -> "binder"; PlaceKind.BOX -> "box"; else -> "place" }
        val inDecks = result.extra.filter { it.kind == CheckKind.DECK }
        fun record(takeFromDecks: Boolean) {
            val out = recordHere(collections, decks, scope, result.extra, takeFromDecks)
            onApply(out.collections, out.decks)
            askDecks = false
            note = "${out.recorded} recorded here" + (if (out.leftInDecks > 0) " · ${out.leftInDecks} left in ${if (out.leftInDecks == 1) "its deck" else "their decks"}" else "") + "."
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        CheckStat("${result.here}", "where they should be", colors.success, Modifier.weight(1f))
                        CheckStat("${result.missingCount}", "missing", MissingTint, Modifier.weight(1f))
                        CheckStat("${if (puttingBack) 0 else result.extra.size}", "extra", colors.warning, Modifier.weight(1f))
                    }
                }
                if (result.foilIgnored && result.expected > 0) item {
                    Text("The scanner can't tell foil from plain, so foil and plain copies were counted together.", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                }
                if (result.missing.isNotEmpty()) item {
                    CheckGroup("Missing · not scanned") {
                        val shown = if (allMissing) result.missing else result.missing.take(SHOWN_MISSING)
                        shown.forEach { m ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text("${m.name}${if (m.line.isFoil) " · foil" else ""} ×${m.qty}", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
                                val where = listedWhere(m)
                                if (where.isNotEmpty()) Text(where, style = MaterialTheme.typography.labelMedium, color = colors.textMuted, modifier = Modifier.padding(horizontal = 8.dp))
                                CheckChip("Find it") { onFindIt(m.name) }
                            }
                        }
                        if (!allMissing && result.missing.size > SHOWN_MISSING) {
                            Text(
                                "and ${result.missing.size - SHOWN_MISSING} more",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.accent,
                                modifier = Modifier.clickable { allMissing = true }.padding(vertical = 4.dp)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            CheckButton("Mark No place yet", Modifier.weight(1f)) {
                                val count = result.missingCount
                                onChange { markNoPlace(it, result.missing) }
                                note = "$count marked No place yet."
                            }
                            CheckButton("Remove from collection", Modifier.weight(1f)) { removing = true }
                        }
                    }
                }
                if (result.extra.isNotEmpty() && !puttingBack) item {
                    CheckGroup("Extra · found here") {
                        result.extra.forEach { l ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(l.scan.name, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
                                Text(l.label, style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            CheckButton("Record them here", Modifier.weight(1f)) { if (inDecks.isNotEmpty()) askDecks = true else record(false) }
                            CheckButton("I'll put them back", Modifier.weight(1f)) { puttingBack = true }
                        }
                    }
                }
                if (puttingBack && result.extra.isNotEmpty()) item {
                    Text(
                        "${result.extra.size} to put back where ${if (result.extra.size == 1) "it's" else "they're"} listed.",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textMuted
                    )
                }
                if (result.missing.isEmpty() && result.extra.isEmpty()) item {
                    Text("Everything listed here was scanned, and nothing else.", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                }
                note?.let { n -> item { Text(n, style = MaterialTheme.typography.labelMedium, color = colors.accentLight) } }
                item {
                    Text(
                        "Save results to note today as when this $what was last checked" +
                            (place.lastChecked?.let { " (last time: ${lastCheckedLabel(it, System.currentTimeMillis())})" } ?: "") + ". Its page shows it.",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textDim
                    )
                }
            }
            Button(
                onClick = {
                    onChange { markChecked(it, place.id, System.currentTimeMillis()) }
                    // Each card found where it should be, in its history (CopyHistory.kt).
                    val at = System.currentTimeMillis()
                    val spot = com.mtgcompanion.app.data.MoveSpot(place.id, listOfNotNull(place.name, session?.section).joinToString(" › "))
                    com.mtgcompanion.app.data.CopyHistoryStore.record(result.lines.filter { it.kind == com.mtgcompanion.app.data.CheckKind.HERE }.map {
                        com.mtgcompanion.app.data.checkedMove(at, com.mtgcompanion.app.data.MoveCard(it.scan.name, it.scan.scryfallId), spot, "where it should be")
                    })
                    CheckSessions.clear()
                    onSaved(place.id)
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(48.dp)
            ) { Text("Save results", fontWeight = FontWeight.ExtraBold) }
        }

        if (removing) {
            val count = result.missingCount
            AlertDialog(
                onDismissRequest = { removing = false },
                containerColor = colors.surface,
                title = { Text("Remove $count ${if (count == 1) "copy" else "copies"}?", color = colors.accentLight) },
                text = { Text("The missing cards come out of your collection, here and on your other devices.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted) },
                confirmButton = {
                    TextButton(onClick = {
                        onChange { removeMissing(it, result.missing) }
                        removing = false
                        note = "$count removed from your collection."
                    }) { Text("Remove", color = colors.error) }
                },
                dismissButton = { TextButton(onClick = { removing = false }) { Text("Cancel", color = colors.textMuted) } }
            )
        }
        if (askDecks) {
            AlertDialog(
                onDismissRequest = { askDecks = false },
                containerColor = colors.surface,
                title = { Text("${inDecks.size} ${if (inDecks.size == 1) "is" else "are"} in a deck", color = colors.accentLight) },
                text = {
                    Text(
                        inDecks.joinToString(", ") { "${it.scan.name} (${it.label.removePrefix("listed in ")})" } +
                            ". Take ${if (inDecks.size == 1) "it" else "them"} out of the deck and record ${if (inDecks.size == 1) "it" else "them"} here?",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted
                    )
                },
                confirmButton = { TextButton(onClick = { record(true) }) { Text("Take out of decks", color = colors.accent) } },
                dismissButton = { TextButton(onClick = { record(false) }) { Text("Leave in decks", color = colors.textMuted) } }
            )
        }
    }
}

@Composable
private fun CheckStat(value: String, label: String, tint: Color, modifier: Modifier) {
    val colors = LocalAppColors.current
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(12.dp)) {
        Text(value, style = NumberStyle(28), color = tint)
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.textMuted, maxLines = 2)
    }
}

@Composable
private fun CheckGroup(title: String, content: @Composable () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary)
        content()
    }
}

@Composable
private fun CheckChip(label: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = colors.textPrimary,
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(colors.surface2).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

@Composable
private fun CheckButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Box(
        modifier.height(40.dp).clip(RoundedCornerShape(10.dp)).background(colors.surface2).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp))
    }
}
