package com.mtgcompanion.app.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.ScannerMode
import com.mtgcompanion.app.data.SortRecipe
import com.mtgcompanion.app.ui.common.a11yHeading
import com.mtgcompanion.app.ui.theme.LocalAppColors

/*
 * The scanner's mode switch and what each mode asks before it starts (ScannerModes.kt): the segmented
 * control at the top of the camera, the one-time tip pointing at Sort a pile, Sort a pile's Start, and
 * the place Put away and Check need. The web app's ScannerModeBar.tsx.
 */

/**
 * Add cards · Sort a pile · Put away · Check, over the camera. TalkBack reads it as tabs, "Sort a
 * pile, tab, 2 of 4, selected". Add cards says how many cards wait in its list while another mode is on.
 */
@Composable
fun ScannerModeSwitch(mode: ScannerMode?, addCount: Int, onPick: (ScannerMode) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.bg.copy(alpha = 0.78f))
            .padding(3.dp)
            .selectableGroup()
    ) {
        ScannerMode.entries.forEach { m ->
            val on = m == mode
            val label = if (m == ScannerMode.ADD && addCount > 0 && !on) "${m.label} · $addCount" else m.label
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(if (on) colors.accent else androidx.compose.ui.graphics.Color.Transparent)
                    .selectable(selected = on, role = Role.Tab, onClick = { onPick(m) })
                    .semantics {
                        if (m == ScannerMode.ADD && addCount > 0) contentDescription = "${m.label}, $addCount in the list"
                    }
                    .padding(horizontal = 4.dp, vertical = 6.dp)
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = if (on) colors.onAccent else colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** "New: sort a pile while you scan", under the switch, its arrow at Sort a pile. Shown once; × or a tap on Sort a pile puts it away for good. */
@Composable
fun SortModeTip(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Sort a pile is the second of four segments: its middle is 3/8 of the way across.
        val arrowX = maxWidth * 3 / 8 - 7.dp
        Column {
            Box(
                Modifier
                    .offset(x = arrowX, y = 7.dp)
                    .size(14.dp)
                    .rotate(45f)
                    .background(colors.accentLight)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.accentLight)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .padding(start = 14.dp, top = 4.dp, bottom = 4.dp)
            ) {
                Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                    Text("New: sort a pile while you scan", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = colors.onAccent)
                    Text("Tap Sort a pile and each card is told its pile as you scan it.", style = MaterialTheme.typography.labelMedium, color = colors.onAccent)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Dismiss tip", tint = colors.onAccent)
                }
            }
        }
    }
}

/** The bottom sheet's frame the prompts share: over the camera, like the modes' own panels. */
@Composable
private fun PromptPanel(title: String, text: String, modifier: Modifier, content: @Composable () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(colors.bg.copy(alpha = 0.94f))
            .padding(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary, modifier = Modifier.a11yHeading())
        Text(text, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        content()
    }
}

@Composable
private fun PromptButton(label: String, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (primary) colors.accent else colors.surface2)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (primary) FontWeight.ExtraBold else FontWeight.Bold, color = if (primary) colors.onAccent else colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Sort a pile, before a sort is under way: the recipe used last — "By card type · last used" — with
 * Start, Lay out piles (its piles to put out on the table, their signs, how they're heard) and Change
 * recipe; with no recipe used yet, Choose a recipe.
 */
@Composable
fun SortStartPanel(last: SortRecipe?, line: String?, onStart: () -> Unit, onLayout: () -> Unit, onChange: () -> Unit, modifier: Modifier = Modifier) {
    if (last == null) {
        PromptPanel(
            "Sort a pile",
            "Scan a pile and each card is told which pile it goes on — by colour, type, set, value, or what your decks and binders need.",
            modifier
        ) {
            PromptButton("Choose a recipe", primary = true, modifier = Modifier.fillMaxWidth(), onClick = onChange)
        }
        return
    }
    PromptPanel("${last.name} · last used", line ?: "Sort this pile the way you sorted the last one.", modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            PromptButton("Start", primary = true, modifier = Modifier.weight(1f), onClick = onStart)
            PromptButton("Change recipe", primary = false, modifier = Modifier.weight(1f), onClick = onChange)
        }
        PromptButton("Lay out piles first", primary = false, modifier = Modifier.fillMaxWidth(), onClick = onLayout)
    }
}

/** Put away and Check before their place is picked: Pick a place, and for Put away "Let the app choose" when boxes have rules. */
@Composable
fun PlacePromptPanel(title: String, text: String, onPick: () -> Unit, onAuto: (() -> Unit)?, modifier: Modifier = Modifier) {
    PromptPanel(title, text, modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            PromptButton("Pick a place", primary = true, modifier = Modifier.weight(1f), onClick = onPick)
            if (onAuto != null) PromptButton("Let the app choose", primary = false, modifier = Modifier.weight(1f), onClick = onAuto)
        }
    }
}
