package com.mtgcompanion.app.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.CheckKind
import com.mtgcompanion.app.data.CheckLine
import com.mtgcompanion.app.data.CheckResult
import com.mtgcompanion.app.ui.theme.LocalAppColors

/*
 * The scanner's check mode (PlaceCheck.kt): at the top what's being checked, with "Whole box" when it's
 * one section; at the bottom how far it's got ("61 of 97 scanned"), what the last card was — green
 * "belongs here", amber "should be in Blue" / "listed in Krenko deck" — the cards that don't belong,
 * Undo last and Finish check. The web app's ScanPage.tsx shows the same.
 */

private val Belongs = Color(0xFF5BCB8F)
private val Amber = Color(0xFFF3B64A)

/** "Checking: Red box › Red", with the button that checks the whole place instead. */
@Composable
fun CheckTarget(text: String, wholeLabel: String?, onWhole: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(colors.accent).padding(start = 14.dp, end = 6.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = colors.onAccent, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (wholeLabel != null) Text(
            wholeLabel,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(colors.bg.copy(alpha = 0.85f)).clickable(onClick = onWhole).padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun CheckPanel(
    result: CheckResult,
    scanned: Int,
    onUndoLast: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(colors.bg.copy(alpha = 0.94f))
            .padding(16.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(Modifier.fillMaxWidth()) {
                Text("${result.here} of ${result.expected} scanned", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.textPrimary, modifier = Modifier.weight(1f))
                Text(if (result.here >= result.expected) "All found" else "Keep going", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
            }
            val done = if (result.expected > 0) (result.here.toFloat() / result.expected).coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(colors.surface2)) {
                if (done > 0f) Box(Modifier.fillMaxWidth(done).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(Belongs))
            }
        }
        result.lines.lastOrNull()?.let { CheckRow(it) }
        val off = result.extra.asReversed().take(4)
        if (off.isNotEmpty()) {
            Text("Found something that doesn't belong", style = MaterialTheme.typography.labelMedium, color = colors.textMuted, modifier = Modifier.padding(top = 4.dp))
            off.forEach { CheckRow(it) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("$scanned ${if (scanned == 1) "card" else "cards"} scanned", style = MaterialTheme.typography.labelMedium, color = colors.textMuted, modifier = Modifier.weight(1f))
            if (scanned > 0) Text(
                "Undo last",
                style = MaterialTheme.typography.labelLarge,
                color = colors.accent,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onUndoLast).padding(8.dp)
            )
        }
        Button(
            onClick = onFinish,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { Text("Finish check", fontWeight = FontWeight.ExtraBold) }
    }
}

/** A scan and what it was: green when it belongs here, amber otherwise. */
@Composable
private fun CheckRow(line: CheckLine) {
    val colors = LocalAppColors.current
    val here = line.kind == CheckKind.HERE
    val tint = if (here) Belongs else Amber
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, tint.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(line.scan.name, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(line.label, style = MaterialTheme.typography.bodyMedium, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
    }
}
