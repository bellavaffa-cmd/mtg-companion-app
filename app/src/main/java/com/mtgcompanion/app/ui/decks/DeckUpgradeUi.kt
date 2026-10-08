package com.mtgcompanion.app.ui.decks

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.UpgradeSwap
import com.mtgcompanion.app.data.bracketWarning
import com.mtgcompanion.app.data.upgradeSummary
import com.mtgcompanion.app.ui.common.elevatedCard
import com.mtgcompanion.app.ui.common.rememberMoney
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.CutColor
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary

/** "Not this one" on an upgrade swap, remembered per deck on this phone (the web keeps its own). */
object UpgradeDismissedStore {
    private const val PREFS = "deck_upgrade"

    fun load(context: Context, deckId: String): Set<String> =
        runCatching { context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet("dismissed_$deckId", null)?.toSet() }
            .getOrNull().orEmpty()

    fun save(context: Context, deckId: String, keys: Set<String>) {
        runCatching { context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet("dismissed_$deckId", keys).apply() }
    }
}

/**
 * "Upgrade with my cards", at the top of a deck's Suggestions: swaps — cut this, add that — where the
 * card coming in is one the user owns, legal here, doing the same job and better for the deck
 * (DeckUpgrade.kt). Each can be made now (the cut onto Considering, the new card onto the deck's pull
 * list), considered, or dismissed for this deck; or the ticked ones made together. The web app's
 * DeckUpgrade.tsx.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UpgradePanel(
    report: UpgradeReport?,
    usesCommander: Boolean,
    dismissedCount: Int,
    onSwap: (List<UpgradeSwap>) -> Unit,
    onConsider: (UpgradeSwap) -> Unit,
    onDismiss: (UpgradeSwap) -> Unit,
    onBringBack: () -> Unit,
    onOpen: (String) -> Unit
) {
    val money = rememberMoney()
    var showRaising by remember { mutableStateOf(false) }
    // Ticked for "Apply all checked": every swap that keeps the bracket, until the user says otherwise.
    var unticked by remember { mutableStateOf(emptySet<String>()) }
    var ticked by remember { mutableStateOf(emptySet<String>()) }
    Column(Modifier.fillMaxWidth()) {
        SectionLabel("Upgrade with my cards")
        Text(
            "Cards you already own that do the same job as one in the deck, better — in your colours, legal here" +
                (if (usesCommander) ", and keeping the bracket." else "."),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
        )
        if (report == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Gold)
                Text("Looking through your collection…", style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.padding(start = 10.dp))
            }
            return@Column
        }
        if (!report.edhrec) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(bottom = 6.dp)) {
                Icon(if (report.offline) Icons.Filled.CloudOff else Icons.Filled.Info, null, tint = TextMuted, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                Text(
                    when {
                        report.offline -> "EDHREC can't be reached — matched by role and EDHREC rank from your collection alone."
                        report.commander != null -> "EDHREC has no page for ${report.commander} — matched by role and EDHREC rank."
                        else -> "Matched by role and EDHREC rank."
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
        val keeping = report.swaps.filter { it.raisesBracketTo == null }
        val raising = report.swaps.filter { it.raisesBracketTo != null }
        if (keeping.isEmpty() && raising.isEmpty()) {
            Text("Nothing you own beats what this deck runs for the same job.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            if (dismissedCount > 0) TextButton(onClick = onBringBack) { Text("Bring back $dismissedCount dismissed", color = Gold) }
            return@Column
        }
        fun isTicked(s: UpgradeSwap) = if (s.raisesBracketTo == null) s.key !in unticked else s.key in ticked
        fun toggle(s: UpgradeSwap) {
            if (s.raisesBracketTo == null) unticked = if (s.key in unticked) unticked - s.key else unticked + s.key
            else ticked = if (s.key in ticked) ticked - s.key else ticked + s.key
        }
        if (keeping.isNotEmpty()) {
            Text(upgradeSummary(keeping) { money.format(it) }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = GoldLight, modifier = Modifier.padding(bottom = 8.dp))
        } else {
            Text("Every upgrade you own would raise the deck's bracket.", style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.padding(bottom = 8.dp))
        }
        val shown = if (showRaising) keeping + raising else keeping
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shown.forEach { s ->
                Column(
                    Modifier.fillMaxWidth()
                        .elevatedCard(shape = RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isTicked(s),
                            onCheckedChange = { toggle(s) },
                            colors = CheckboxDefaults.colors(checkedColor = Gold, checkmarkColor = Bg),
                            modifier = Modifier.semantics { contentDescription = "Include ${s.cut.name} to ${s.add.name}" }
                        )
                        FlowRow(verticalArrangement = Arrangement.Center, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                            Text(s.cut.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = CutColor, modifier = Modifier.clickable { onOpen(s.cut.name) })
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, "for", tint = TextMuted, modifier = Modifier.size(18.dp))
                            Text(s.add.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.clickable { onOpen(s.add.name) })
                        }
                    }
                    Text(s.reason, style = MaterialTheme.typography.bodySmall, color = TextPrimary, modifier = Modifier.padding(top = 4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Text(s.where, style = MaterialTheme.typography.labelMedium, color = TextMuted)
                        s.priceDelta?.let { d ->
                            Text((if (d >= 0) "+" else "−") + money.format(kotlin.math.abs(d)), style = MaterialTheme.typography.labelMedium, color = TextMuted)
                        }
                        s.raisesBracketTo?.let { b ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Warning, null, tint = CutColor, modifier = Modifier.size(14.dp))
                                Text(bracketWarning(b), style = MaterialTheme.typography.labelMedium, color = CutColor, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center, modifier = Modifier.padding(top = 6.dp)) {
                        OutlinedButton(onClick = { onSwap(listOf(s)) }) { Text("Swap now", color = Gold) }
                        OutlinedButton(onClick = { onConsider(s) }) { Text("Consider", color = TextPrimary) }
                        TextButton(onClick = { onDismiss(s) }) { Text("Not this one", color = TextMuted) }
                    }
                }
            }
        }
        val chosen = shown.filter { isTicked(it) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center, modifier = Modifier.padding(top = 10.dp)) {
            Button(
                onClick = { onSwap(chosen) },
                enabled = chosen.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
            ) { Text("Apply all checked" + if (chosen.isNotEmpty()) " (${chosen.size})" else "") }
            if (raising.isNotEmpty()) {
                TextButton(onClick = { showRaising = !showRaising }) {
                    Text((if (showRaising) "Hide" else "Show") + " ${raising.size} that would raise the bracket", color = Gold)
                }
            }
            if (dismissedCount > 0) TextButton(onClick = onBringBack) { Text("Bring back $dismissedCount dismissed", color = Gold) }
        }
        Text(
            "Swap now puts the cut card on Considering and the new one in the deck, on its pull list until you fetch it. Tag a card “keep” and it's never suggested as a cut.",
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
