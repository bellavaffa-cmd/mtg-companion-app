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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.PlaceKind
import com.mtgcompanion.app.data.SetupCounts
import com.mtgcompanion.app.data.applySetup
import com.mtgcompanion.app.data.copiesWithin
import com.mtgcompanion.app.data.deckBoxCount
import com.mtgcompanion.app.data.nextBoxRule
import com.mtgcompanion.app.data.nextPockets
import com.mtgcompanion.app.data.placedPercent
import com.mtgcompanion.app.data.placesOf
import com.mtgcompanion.app.data.setupDrafts
import com.mtgcompanion.app.data.setupPlaces
import com.mtgcompanion.app.data.storageSummary
import com.mtgcompanion.app.data.usage.Usage
import com.mtgcompanion.app.data.usage.UsageAction
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.common.a11yHeading
import com.mtgcompanion.app.ui.theme.LocalAppColors
import java.util.UUID

/*
 * Getting started with storage (data/StorageSetup.kt), the web app's StorageSetupPage
 * (src/pages/StorageSetupPage.tsx): what the cards are kept in, the places' names, then their labels
 * and putting cards away one box at a time, with the share of copies that have a place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageSetupScreen(
    collections: List<Collection>,
    decks: List<Deck>,
    onBack: () -> Unit,
    onChange: (StorageChange) -> Unit,
    /** A new place's label to print (and All labels from there). */
    onLabel: (String) -> Unit,
    /** The scanner putting cards away into a place. */
    onPutAway: (String) -> Unit
) {
    val colors = LocalAppColors.current
    var step by remember { mutableStateOf(1) }
    var counts by remember { mutableStateOf(SetupCounts()) }
    val names = remember { mutableStateMapOf<String, String>() }
    var made by remember { mutableStateOf<List<String>>(emptyList()) }
    val places = placesOf(collections)
    val drafts = remember(counts, step) { setupDrafts(counts, if (step == 3) emptyList() else places) }
    val summary = remember(collections, decks) { storageSummary(collections, decks) }

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("Set up storage", style = MaterialTheme.typography.titleLarge, modifier = Modifier.a11yHeading()) },
                navigationIcon = { BackButton(onClick = { if (step == 2) step = 1 else onBack() }) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        },
        bottomBar = {
            Column(
                Modifier.fillMaxWidth().background(colors.bg).navigationBarsPadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (step) {
                    1 -> {
                        Text(
                            "Next: name them, then print labels. Then put cards away one box at a time; the progress bar shows how far you are.",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textMuted
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LoanButton("Skip", primary = false, modifier = Modifier.weight(1f), onClick = onBack)
                            LoanButton("Next: name them", primary = true, enabled = counts.total > 0, modifier = Modifier.weight(2f)) { step = 2 }
                        }
                    }
                    2 -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LoanButton("Back", primary = false, modifier = Modifier.weight(1f)) { step = 1 }
                        LoanButton("Make ${drafts.size} ${if (drafts.size == 1) "place" else "places"}", primary = true, modifier = Modifier.weight(2f)) {
                            val newPlaces = setupPlaces(counts, drafts, names.toMap(), System.currentTimeMillis()) { UUID.randomUUID().toString() }
                            onChange { applySetup(it, newPlaces) }
                            newPlaces.forEach { _ -> Usage.action(UsageAction.PLACE_CREATED) }
                            made = newPlaces.map { it.id }
                            step = 3
                        }
                    }
                    else -> LoanButton("Done", primary = true, modifier = Modifier.fillMaxWidth(), onClick = onBack)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("STEP $step OF 3", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = colors.accentLight)
            }
            when (step) {
                1 -> {
                    item {
                        Text("What do you keep your cards in?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary)
                        Text("Rough numbers are fine. You can add, rename and move places later.", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
                    }
                    item {
                        CountRow(placeIcon(PlaceKind.BINDER), "Binders", "${counts.pockets} per page", counts.binders,
                            onDetail = { counts = counts.copy(pockets = nextPockets(counts.pockets)) }) { counts = counts.copy(binders = it) }
                    }
                    item {
                        CountRow(placeIcon(PlaceKind.BOX), "Bulk boxes", counts.boxRule?.short ?: "not sorted", counts.boxes,
                            onDetail = { counts = counts.copy(boxRule = nextBoxRule(counts.boxRule)) }) { counts = counts.copy(boxes = it) }
                    }
                    item {
                        val n = deckBoxCount(decks)
                        SetupRow(Icons.Filled.Style, "Deck boxes", if (n > 0) "one per physical deck · $n now" else "one per physical deck", null) {
                            Text("Automatic", style = MaterialTheme.typography.labelLarge, color = colors.textMuted)
                        }
                    }
                    item {
                        CountRow(placeIcon(PlaceKind.SHELF), "A shelf or cupboard", "to group the rest", counts.shelves, onDetail = null) { counts = counts.copy(shelves = it) }
                    }
                }
                2 -> {
                    item {
                        Text("Name them", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary)
                        Text(
                            "Call each what's written on it, or will be. Binders and boxes go inside the shelf.",
                            style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                        )
                    }
                    items(drafts, key = { it.key }) { d ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(placeIcon(d.kind), contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
                            OutlinedTextField(
                                value = names[d.key] ?: d.name,
                                onValueChange = { names[d.key] = it.take(60) },
                                singleLine = true,
                                label = { Text(d.kind.label, color = colors.textMuted) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.accent, unfocusedBorderColor = colors.border,
                                    focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary, cursorColor = colors.accent
                                ),
                                modifier = Modifier.weight(1f).padding(start = 10.dp)
                            )
                        }
                    }
                }
                else -> {
                    item {
                        Text("Labels, then put cards away", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary)
                        Text(
                            "Print a label for each place and stick it on. Then put cards away one box at a time: scan each card as it goes in.",
                            style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                        )
                    }
                    item {
                        val share = if (summary.total > 0) summary.placed.toFloat() / summary.total else 0f
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(14.dp)
                        ) {
                            Text("${placedPercent(summary)}% of copies have a place", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(colors.surface2)) {
                                Box(Modifier.fillMaxWidth(share).height(8.dp).clip(RoundedCornerShape(4.dp)).background(colors.accent))
                            }
                            Text("${summary.placed} of ${summary.total} · ${summary.unplaced} to go", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                        }
                    }
                    item {
                        LoanButton("Print labels", primary = false, enabled = made.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                            made.firstOrNull { id -> places.any { it.id == id && it.placeKind != PlaceKind.SHELF } }?.let(onLabel) ?: made.firstOrNull()?.let(onLabel)
                        }
                    }
                    val toFill = places.filter { it.id in made && it.placeKind != PlaceKind.SHELF }
                    items(toFill, key = { it.id }) { p ->
                        SetupRow(placeIcon(p.placeKind), p.name, "${copiesWithin(summary, places, p.id)} copies", onClick = null) {
                            LoanButton("Put away", primary = false) { onPutAway(p.id) }
                        }
                    }
                }
            }
        }
    }
}

/** One row of the setup: an icon, a name with a line under it, and whatever goes on the right. */
@Composable
private fun SetupRow(icon: ImageVector, title: String, detail: String, onClick: (() -> Unit)?, trailing: @Composable () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                detail,
                style = MaterialTheme.typography.labelMedium,
                color = if (onClick != null) colors.accentLight else colors.textMuted,
                modifier = if (onClick != null) Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(vertical = 2.dp) else Modifier
            )
        }
        trailing()
    }
}

/** A row with − and + either side of its number; tapping [detail] (when [onDetail]) changes it. */
@Composable
private fun CountRow(icon: ImageVector, title: String, detail: String, value: Int, onDetail: (() -> Unit)?, onValue: (Int) -> Unit) {
    val colors = LocalAppColors.current
    SetupRow(icon, title, detail, onDetail) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StepButton(Icons.Filled.Remove, "Fewer $title", enabled = value > 0) { onValue(value - 1) }
            Text("$value", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary)
            StepButton(Icons.Filled.Add, "More $title", enabled = value < 50) { onValue(value + 1) }
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(36.dp).clip(CircleShape).background(colors.surface2).clickable(enabled = enabled, onClick = onClick)
    ) {
        Icon(icon, contentDescription = label, tint = if (enabled) colors.textPrimary else colors.textDim, modifier = Modifier.size(18.dp))
    }
}
