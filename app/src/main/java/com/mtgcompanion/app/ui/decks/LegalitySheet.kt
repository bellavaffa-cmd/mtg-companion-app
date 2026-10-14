package com.mtgcompanion.app.ui.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mtgcompanion.app.data.LEGALITY_CHECKING
import com.mtgcompanion.app.data.LegalityIssue
import com.mtgcompanion.app.data.LegalityIssueKind
import com.mtgcompanion.app.data.LegalityReport
import com.mtgcompanion.app.data.deckSizeFix
import com.mtgcompanion.app.data.deckSizeFixLabel
import com.mtgcompanion.app.data.groupLegalityIssues
import com.mtgcompanion.app.data.legalitySummary
import com.mtgcompanion.app.data.uncheckedNote
import com.mtgcompanion.app.ui.common.KeepSystemBarsHidden
import com.mtgcompanion.app.ui.common.LocalLayoutSize
import com.mtgcompanion.app.ui.common.a11yHeading
import com.mtgcompanion.app.ui.common.elevatedCard
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.LocalAppColors
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary

// The deck header's legality pill — "Legal", "Not legal · 3 banned", "Checking…" — and the sheet it
// opens: every problem grouped under a plain heading, each card openable, with the fixes there are.
// The words come from data/LegalitySummary.kt (the web app's DeckLegality.tsx says the same).

/** The header pill. Tapping it opens [LegalitySheet]; while [loading] it says "Checking…". */
@Composable
internal fun LegalityPill(report: LegalityReport?, loading: Boolean, onClick: () -> Unit) {
    val app = LocalAppColors.current
    val text = legalitySummary(report, loading)
    val checking = text == LEGALITY_CHECKING
    val legal = !checking && report?.legal == true
    val color = when {
        checking -> TextMuted
        legal -> app.accent
        else -> app.error
    }
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (legal) app.accentGlow else color.copy(alpha = 0.18f))
            .clickable(enabled = report != null, role = Role.Button, onClickLabel = "Why", onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/** What a problem row's card can have done to it from the sheet. Null actions aren't offered. */
internal class LegalityActions(
    /** Open the issue's card (its zoom). */
    val onOpen: ((LegalityIssue) -> Unit)? = null,
    /** A copy-limit issue: cut its row to [LegalityIssue.fixQuantity]. */
    val onReduce: ((LegalityIssue) -> Unit)? = null,
    /** A banned / off-colour main-deck card: out of the deck, into Considering. */
    val onMoveToConsidering: ((LegalityIssue) -> Unit)? = null,
    /** A banned / off-colour card (main deck or sideboard): take it out. */
    val onRemove: ((LegalityIssue) -> Unit)? = null,
    /** The deck's size is off: true, cards to add (Suggestions); false, cards to cut (Cards). */
    val onSizeFix: ((add: Boolean) -> Unit)? = null,
    /** Whether a card can go to Considering (not a commander, not in the sideboard). */
    val canConsider: (LegalityIssue) -> Boolean = { !it.sideboard }
)

/** The sheet of problems, from the header pill: a bottom sheet on a phone, a dialog on a wide screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LegalitySheet(
    report: LegalityReport,
    actions: LegalityActions,
    onSeeStats: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val app = LocalAppColors.current
    if (LocalLayoutSize.current.isWide) {
        Dialog(onDismissRequest = onDismiss) {
            KeepSystemBarsHidden()
            Column(
                Modifier
                    .width(480.dp)
                    .heightIn(max = 680.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(app.surface)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) { LegalityProblems(report, actions, onSeeStats, onDismiss) }
        }
        return
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = app.surface,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 40.dp, height = 5.dp).clip(RoundedCornerShape(50)).background(app.surface3))
        }
    ) {
        KeepSystemBarsHidden()
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            LegalityProblems(report, actions, onSeeStats, onDismiss)
        }
    }
}

@Composable
private fun LegalityProblems(report: LegalityReport, actions: LegalityActions, onSeeStats: (() -> Unit)?, onDismiss: () -> Unit) {
    val app = LocalAppColors.current
    val summary = legalitySummary(report)
    val checking = summary == LEGALITY_CHECKING
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            when {
                checking -> Icons.Filled.HourglassEmpty
                report.legal -> Icons.Filled.CheckCircle
                else -> Icons.Filled.Cancel
            },
            contentDescription = null,
            tint = if (checking) TextMuted else if (report.legal) app.success else app.error,
            modifier = Modifier.size(22.dp)
        )
        Text(
            when {
                checking -> "Checking ${report.mode.label} legality…"
                report.legal -> "Legal for ${report.mode.label}"
                else -> "Why it isn't legal for ${report.mode.label}"
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.a11yHeading()
        )
    }
    Text(
        "${report.totalCards} cards" + if (report.mode.exactSize) " · needs ${report.mode.deckSize}" else " · min ${report.mode.deckSize}",
        style = MaterialTheme.typography.labelMedium,
        color = TextMuted,
        modifier = Modifier.padding(top = 4.dp)
    )
    uncheckedNote(report)?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.padding(top = 8.dp))
    }
    if (report.issues.isEmpty() && !checking) {
        Text("This deck follows ${report.mode.label}'s building rules.", style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.padding(top = 8.dp))
    }
    LegalityGroups(report, actions)
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
        if (onSeeStats != null) TextButton(onClick = onSeeStats) { Text("See in Stats", color = Gold) }
        TextButton(onClick = onDismiss) { Text("Done", color = TextPrimary) }
    }
}

/** Every problem under its heading, most important first. Shared by the sheet and the Stats tab. */
@Composable
internal fun LegalityGroups(report: LegalityReport, actions: LegalityActions) {
    val groups = groupLegalityIssues(report)
    if (groups.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
        groups.forEach { group ->
            Text(
                group.heading,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                modifier = Modifier.padding(top = 6.dp).a11yHeading()
            )
            group.issues.forEach { issue -> LegalityProblemRow(report, issue, actions) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LegalityProblemRow(report: LegalityReport, issue: LegalityIssue, actions: LegalityActions) {
    val app = LocalAppColors.current
    val open = actions.onOpen?.takeIf { issue.scryfallId != null && issue.card != null }
    Column(
        Modifier
            .fillMaxWidth()
            .elevatedCard(shape = RoundedCornerShape(16.dp))
            .let { if (open != null) it.clickable(onClickLabel = "Open card") { open(issue) } else it }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.Cancel, contentDescription = null, tint = app.error, modifier = Modifier.size(18.dp))
            Column(Modifier.weight(1f)) {
                issue.card?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextPrimary) }
                Text(issue.reason, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            if (open != null) Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
        }
        val fixes = buildList<Pair<String, () -> Unit>> {
            when (issue.kind) {
                LegalityIssueKind.COPY_LIMIT -> {
                    val reduce = actions.onReduce
                    if (reduce != null && issue.scryfallId != null && issue.fixQuantity != null) {
                        add("Reduce to ${issue.fixQuantity} ${if (issue.fixQuantity == 1) "copy" else "copies"}" to { reduce(issue) })
                    }
                }
                LegalityIssueKind.LEGALITY, LegalityIssueKind.COLOR_IDENTITY -> if (issue.scryfallId != null) {
                    val consider = actions.onMoveToConsidering
                    if (consider != null && actions.canConsider(issue)) add("Move to Considering" to { consider(issue) })
                    actions.onRemove?.let { remove -> add((if (issue.sideboard) "Remove from sideboard" else "Remove") to { remove(issue) }) }
                }
                LegalityIssueKind.DECK_SIZE -> if (!issue.sideboard) {
                    val label = deckSizeFixLabel(report)
                    val fix = actions.onSizeFix
                    if (label != null && fix != null) {
                        val adding = deckSizeFix(report) > 0
                        add((if (adding) "$label · see suggestions" else "$label · open the cards") to { fix(adding) })
                    }
                }
                else -> Unit
            }
        }
        if (fixes.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 28.dp, top = 6.dp)) {
                fixes.forEach { (label, run) ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = Gold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(role = Role.Button, onClick = run)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
