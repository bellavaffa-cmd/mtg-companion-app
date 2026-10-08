@file:OptIn(ExperimentalLayoutApi::class)

package com.mtgcompanion.app.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtgcompanion.app.data.APART_LABELS
import com.mtgcompanion.app.data.DerivedPiles
import com.mtgcompanion.app.data.RecipeChoice
import com.mtgcompanion.app.data.RecipeSessionState
import com.mtgcompanion.app.data.RecipeVoice
import com.mtgcompanion.app.data.SmartContext
import com.mtgcompanion.app.data.alsoLine
import com.mtgcompanion.app.data.cardLine
import com.mtgcompanion.app.data.otherPile
import com.mtgcompanion.app.data.ownedLine
import com.mtgcompanion.app.data.reasonLine
import com.mtgcompanion.app.data.Money
import com.mtgcompanion.app.ui.theme.BebasNumbers
import com.mtgcompanion.app.ui.theme.LocalAppColors

/*
 * Sorting with a recipe, in the scanner (ScanViewModel's recipe mode) — the Scan and Smart mockups: the
 * pile's number big, its colour band and name (or, for a smart pile, why: "KRENKO NEEDS IT"), the card
 * and its line, Undo, Wrong card?, the last pile and how many are sorted; for a smart pile, what else
 * wants the card, Put in deck now and Send to pile N instead. Checking a pile shows each card's verdict
 * instead. The logic is data/SortRecipes.kt; the web app's RecipeScanPanel.tsx shows the same.
 */

/** A band's colour from its "#rrggbb". */
fun bandColor(hex: String): Color = runCatching { Color(0xFF000000L or hex.removePrefix("#").toLong(16)) }.getOrDefault(Color(0xFFE6B45EL))

private val Ink = Color(0xFFF1EEE6)
private val InkMuted = Color(0xFFB7C0CF)

@Composable
fun RecipeScanPanel(
    session: RecipeSessionState,
    derived: DerivedPiles,
    ctx: SmartContext,
    voice: RecipeVoice,
    money: Money,
    onUndo: () -> Unit,
    onWrong: () -> Unit,
    onSend: (RecipeChoice) -> Unit,
    onPutInDeck: () -> Unit,
    onApart: (String) -> Unit,
    onDone: () -> Unit,
    onFinishCheck: () -> Unit,
    onScanNow: () -> Unit,
    modifier: Modifier = Modifier,
    /** The newest card was put right by a correction learned before (ScanCorrections.kt). */
    learned: Boolean = false,
    onLearned: () -> Unit = {}
) {
    val colors = LocalAppColors.current
    val scans = session.scans
    val last = scans.lastOrNull()
    val before = scans.getOrNull(scans.size - 2)
    val pile = last?.let { l -> derived.piles.firstOrNull { it.number == l.pile } }
    val beforePile = before?.let { b -> derived.piles.firstOrNull { it.number == b.pile } }
    val checking = session.checking
    Column(modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Done, how it's heard, and how many are sorted.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Chip(if (checking != null) "Finish check" else "Done", onClick = if (checking != null) onFinishCheck else onDone, bold = true)
            Spacer(Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (voice.auto) colors.success else colors.textDim))
                Text(
                    listOfNotNull(if (voice.auto) "Auto" else "Tap to scan", if (voice.speak) "speaking" else null).joinToString(" · "),
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(start = 6.dp)
                )
            }
            Text(
                "${scans.size} sorted", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink,
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }

        if (checking != null) {
            val p = derived.piles.firstOrNull { it.number == checking.pile }
            val flag = checking.flagged.lastOrNull()
            Tile(p?.band ?: "#e6b45e", checking.pile.toString(), "Checking ${p?.name ?: ""}", false,
                "${checking.checked.size} of ${scans.count { it.pile == checking.pile }} found",
                flag?.let { "${it.name}: ${it.line}" } ?: "Scan the pile, card by card.") {
                if (checking.flagged.isNotEmpty()) Also("Doesn't belong:", checking.flagged.takeLast(6).reversed().map { "${it.name} — ${it.line.removePrefix("Doesn't belong — ")}" })
                if (!voice.auto) Bar { Action("Scan now", onScanNow) }
            }
            return@Column
        }
        if (last == null || pile == null) {
            Tile("#2b2e38", "?", "Show the first card", false, null,
                if (voice.auto) "Hold it still under the camera; its pile shows here, big." else "Tap Scan now for each card; its pile shows here, big.") {
                if (!voice.auto) Bar { Action("Scan now", onScanNow) }
            }
            return@Column
        }
        val reason = last.reason
        val filed = last.filed == true
        val alt = if (!filed) otherPile(session.recipe, derived, last.card, last.pile, reason, last.also, money.rate) else null
        val also = last.also.orEmpty().map { alsoLine(it) } + (if (reason != null) listOfNotNull(ownedLine(ctx, last.name, scans.dropLast(1))) else emptyList())
        Tile(pile.band, pile.number.toString(), reason?.let { reasonLine(it) } ?: pile.name, reason != null, last.name, cardLine(last.card, last.setName, reason) { money.format(it) }) {
            if (reason != null && also.isNotEmpty()) Also("Also wanted:", also)
            if (session.recipe.apart.isNotEmpty() && !filed) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 10.dp)) {
                    session.recipe.apart.forEach { k ->
                        val on = when (k) {
                            "FOIL" -> last.card.foil
                            "FOREIGN" -> !last.card.lang.isNullOrEmpty() && last.card.lang != "en"
                            else -> last.card.played
                        }
                        Text(
                            if (k == "FOIL") "Foil" else APART_LABELS.getValue(k),
                            fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = if (on) colors.onAccent else Ink,
                            modifier = Modifier.clip(RoundedCornerShape(17.dp)).background(if (on) colors.accent else Color.White.copy(alpha = 0.1f))
                                .semantics { contentDescription = "${APART_LABELS.getValue(k)}: ${if (on) "on" else "off"}" }
                                .clickable(role = Role.Switch) { onApart(k) }.heightIn(min = 34.dp).padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            Bar {
                if (reason?.kind == "DECKS" && !filed) Action("Put in deck now", onPutInDeck)
                if (alt != null) Action("Send to pile ${alt.pile} instead") { onSend(alt) }
                if (!filed) Action("Undo", onUndo)
                if (!filed) Action("Wrong card?", onWrong)
                if (learned) Text(
                    "Learned", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.onAccent,
                    modifier = Modifier.align(Alignment.CenterVertically).clip(RoundedCornerShape(12.dp)).background(colors.accent)
                        .semantics { contentDescription = "Learned from your correction: ${last.name}" }
                        .clickable(role = Role.Button, onClick = onLearned).heightIn(min = 32.dp).padding(horizontal = 10.dp, vertical = 7.dp)
                )
                if (filed) Text("Put with its deck", fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(vertical = 12.dp))
                if (!voice.auto) Action("Scan now", onScanNow)
                Spacer(Modifier.weight(1f))
                if (before != null && beforePile != null) Text("Last: ${beforePile.number} · ${beforePile.name}", fontSize = 13.sp, color = InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The big tile: a band of the pile's colour, its number, what it is, the card and its line, then [extra]. */
@Composable
private fun Tile(band: String, number: String, title: String, reason: Boolean, name: String?, line: String?, extra: @Composable () -> Unit) {
    val c = bandColor(band)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(lerp(Color(0xFF0B0D12), c, 0.14f)).border(2.dp, c, RoundedCornerShape(22.dp))
    ) {
        Box(Modifier.fillMaxWidth().height(10.dp).background(c))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 14.dp)
                // TalkBack hears each card's pile as it comes: "Pile 7, Blue, Counterspell".
                .semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = listOfNotNull("Pile $number", title, name, line).joinToString(", ")
                }
        ) {
            Text(number, style = TextStyle(fontFamily = BebasNumbers, fontSize = 120.sp, lineHeight = 102.sp), color = lerp(c, Color.White, 0.3f))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(title.uppercase(), style = TextStyle(fontFamily = BebasNumbers, fontSize = if (reason) 30.sp else 34.sp, lineHeight = if (reason) 31.sp else 35.sp, letterSpacing = 0.6.sp), color = Ink)
                if (name != null) Text(name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (line != null) Text(line, fontSize = 13.sp, color = InkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        extra()
    }
}

@Composable
private fun Also(heading: String, lines: List<String>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 12.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.3f)).padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(heading, fontSize = 13.sp, color = Color(0xFFD9C79F))
        lines.forEach { Text(it, fontSize = 13.sp, color = Ink) }
    }
}

@Composable
private fun Bar(content: @Composable FlowRowScope.() -> Unit) {
    Box(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.08f)).height(1.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp)
    ) { content() }
}

@Composable
private fun Action(label: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Text(
        label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.accent,
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick).heightIn(min = 44.dp).padding(vertical = 13.dp)
    )
}

@Composable
private fun Chip(label: String, onClick: () -> Unit, bold: Boolean = false) {
    Text(
        label, fontSize = 14.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold, color = Ink,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.55f))
            .clickable(role = Role.Button, onClick = onClick).heightIn(min = 40.dp).padding(horizontal = 14.dp, vertical = 10.dp)
    )
}
