package com.mtgcompanion.app.ui.decks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.COMMON_CATEGORIES
import com.mtgcompanion.app.data.COMPANIONS
import com.mtgcompanion.app.data.CompanionInfo
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckCardEntry
import com.mtgcompanion.app.data.DeckGrouping
import com.mtgcompanion.app.data.MAX_DESCRIPTION
import com.mtgcompanion.app.data.Money
import com.mtgcompanion.app.data.PrimerBlock
import com.mtgcompanion.app.data.PrimerSpan
import com.mtgcompanion.app.data.ValuePoint
import com.mtgcompanion.app.data.categoryCounts
import com.mtgcompanion.app.data.deckCategoryNames
import com.mtgcompanion.app.data.monthChange
import com.mtgcompanion.app.data.parsePrimer
import com.mtgcompanion.app.data.targetOf
import com.mtgcompanion.app.data.tidyCategory
import com.mtgcompanion.app.data.tidyFolder
import com.mtgcompanion.app.ui.common.PillChip
import com.mtgcompanion.app.ui.theme.LocalAppColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// The deck screen's primer, categories, companion, folder and value history: the "About" panel (and
// the primer as shared decks show it), a card's categories, a category's target, the companion
// picker, the folder picker and the value panel on Stats. The logic is in data/Primer.kt,
// DeckCategories.kt, Companion.kt, DeckFolders.kt and DeckValueHistory.kt; the web app's
// src/components/DeckExtras.tsx shows the same.

// ---- The primer ----

/** One block's spans as text: bold, italic, and links — a [[card]] opens it, a web link opens the browser. */
@Composable
private fun spansText(spans: List<PrimerSpan>, onCard: (String) -> Unit): AnnotatedString {
    val link = LocalAppColors.current.accentLight
    val linkStyles = TextLinkStyles(SpanStyle(color = link, textDecoration = TextDecoration.Underline))
    return buildAnnotatedString {
        spans.forEach { s ->
            val style = SpanStyle(
                fontWeight = if (s.bold) FontWeight.Bold else null,
                fontStyle = if (s.italic) FontStyle.Italic else null
            )
            withStyle(style) {
                when {
                    s.card != null -> {
                        val name = s.card
                        withLink(LinkAnnotation.Clickable("card", linkStyles) { onCard(name) }) { append(s.text) }
                    }
                    s.url != null -> withLink(LinkAnnotation.Url(s.url, linkStyles)) { append(s.text) }
                    else -> append(s.text)
                }
            }
        }
    }
}

/** A primer drawn as headings, lists and paragraphs — text only; [[cards]] open the card. */
@Composable
fun PrimerText(text: String, onCard: (String) -> Unit, modifier: Modifier = Modifier) {
    val blocks = remember(text) { parsePrimer(text) }
    val colors = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        blocks.forEach { b ->
            when (b) {
                is PrimerBlock.Heading -> Text(
                    spansText(b.spans, onCard),
                    style = when (b.level) {
                        1 -> MaterialTheme.typography.titleMedium
                        2 -> MaterialTheme.typography.titleSmall
                        else -> MaterialTheme.typography.labelLarge
                    },
                    color = if (b.level >= 3) colors.textMuted else colors.textPrimary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                is PrimerBlock.Paragraph -> Text(spansText(b.spans, onCard), style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                is PrimerBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    b.items.forEachIndexed { i, item ->
                        Row {
                            Text(if (b.ordered) "${i + 1}." else "•", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, modifier = Modifier.width(22.dp))
                            Text(spansText(item, onCard), style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                        }
                    }
                }
            }
        }
    }
}

/** The deck's "About": its primer, written and edited here. */
@Composable
fun AboutPanel(deck: Deck, onSave: (String) -> Unit, onCard: (String) -> Unit) {
    val colors = LocalAppColors.current
    var editing by remember { mutableStateOf<String?>(null) }
    val text = deck.description.orEmpty()
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("About", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { editing = text }) { Text(if (text.isEmpty()) "Write" else "Edit", color = colors.accent) }
        }
        if (text.isEmpty()) {
            Text(
                "No primer yet. Say how the deck plays, what to keep in an opening hand, and its key cards — [[Card name]] links a card.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted
            )
        } else {
            PrimerText(text, onCard)
        }
    }
    editing?.let { draft ->
        AlertDialog(
            containerColor = colors.surface,
            onDismissRequest = { editing = null },
            title = { Text("About this deck", color = colors.accentLight) },
            text = {
                Column {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { editing = it.take(MAX_DESCRIPTION) },
                        placeholder = { Text("## How it plays\nRamp early, then **[[Craterhoof Behemoth]]**.\n\n- Keep hands with two lands", color = colors.textDim) },
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 420.dp)
                    )
                    Text(
                        "# Heading · **bold** · *italic* · - list · [[Card name]] · [words](https://…) — ${draft.length} / $MAX_DESCRIPTION",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                Button(onClick = { onSave(draft); editing = null }, colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel", color = colors.textMuted) } }
        )
    }
}

@Composable
private fun fieldColors() = LocalAppColors.current.let { c ->
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = c.accent,
        unfocusedBorderColor = c.border,
        focusedTextColor = c.textPrimary,
        unfocusedTextColor = c.textPrimary,
        cursorColor = c.accent
    )
}

// ---- Categories ----

/** The chips over the Cards list: how it's grouped, and "Suggest categories" when grouping by them. */
@Composable
fun GroupByRow(selected: DeckGrouping, onSelect: (DeckGrouping) -> Unit, onSuggest: (() -> Unit)?, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.horizontalScroll(rememberScrollState())
    ) {
        Text("Group by", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
        DeckGrouping.entries.forEach { g -> PillChip(g.label, selected == g, onClick = { onSelect(g) }) }
        if (onSuggest != null) {
            TextButton(onClick = onSuggest) { Text("Suggest categories", color = colors.accent) }
        }
    }
}

/** Picking a card's categories: the deck's own and the common ones as chips, and a new one typed in. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardCategoriesDialog(deck: Deck, entry: DeckCardEntry, onSave: (List<String>) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalAppColors.current
    var chosen by remember { mutableStateOf(entry.categories.orEmpty()) }
    var typed by remember { mutableStateOf("") }
    fun has(name: String) = chosen.any { it.equals(name, ignoreCase = true) }
    val offered = (deckCategoryNames(deck) + COMMON_CATEGORIES + chosen).distinctBy { it.lowercase() }
    fun add() {
        val name = tidyCategory(typed)
        if (name.isNotEmpty() && !has(name)) chosen = chosen + name
        typed = ""
    }
    AlertDialog(
        containerColor = colors.surface,
        onDismissRequest = onDismiss,
        title = { Text("Categories for ${entry.name}", color = colors.accentLight) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("A card can be in several. Group the list by Category to see them.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    offered.forEach { name ->
                        PillChip(if (has(name)) "✓ $name" else name, has(name), onClick = {
                            chosen = if (has(name)) chosen.filterNot { it.equals(name, ignoreCase = true) } else chosen + name
                        })
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it.take(60) },
                    placeholder = { Text("New category, e.g. Win cons", color = colors.textDim) },
                    singleLine = true,
                    trailingIcon = { TextButton(onClick = { add() }, enabled = tidyCategory(typed).isNotEmpty()) { Text("Add", color = colors.accent) } },
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(chosen) }, colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textMuted) } }
    )
}

/** One category's target, name and removal. */
@Composable
fun CategoryDialog(deck: Deck, name: String, onSave: (target: Int?, rename: String) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    val colors = LocalAppColors.current
    var target by remember { mutableStateOf(targetOf(deck, name)?.toString().orEmpty()) }
    var rename by remember { mutableStateOf(name) }
    val count = categoryCounts(deck)[name] ?: 0
    AlertDialog(
        containerColor = colors.surface,
        onDismissRequest = onDismiss,
        title = { Text(name, color = colors.accentLight) },
        text = {
            Column {
                Text(
                    "$count ${if (count == 1) "card" else "cards"} in it. Remove takes it off every card; the cards stay.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = rename, onValueChange = { rename = it.take(60) }, label = { Text("Name") }, singleLine = true,
                    colors = fieldColors(), modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = target,
                    onValueChange = { v -> target = v.filter { it.isDigit() }.take(3) },
                    label = { Text("Target (how many you want)") },
                    placeholder = { Text("None", color = colors.textDim) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(target.toIntOrNull()?.takeIf { it > 0 }, rename) },
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onRemove) { Text("Remove", color = colors.error) }
                TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textMuted) }
            }
        }
    )
}

// ---- Companion ----

/**
 * The deck's companion: one of the ten, kept in the sideboard (outside the 100 in Commander). Picking
 * one adds it there when it isn't yet; Remove takes the mark off.
 */
@Composable
fun CompanionDialog(current: CompanionInfo?, sided: Boolean, error: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalAppColors.current
    AlertDialog(
        containerColor = colors.surface,
        onDismissRequest = onDismiss,
        title = { Text("Companion", color = colors.accentLight) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "A companion starts the game outside the deck if the deck meets its condition — " +
                        if (sided) "it takes one of the sideboard's 15." else "in Commander it sits outside the 100.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error) }
                COMPANIONS.forEach { c ->
                    val on = current?.name == c.name
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (on) colors.accentGlow else colors.surface2)
                            .clickable { onPick(c.name) }
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Text(c.name, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                        Text(c.rule, style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = colors.textMuted) } },
        dismissButton = {
            if (current != null) TextButton(onClick = { onPick(null) }) { Text("Remove", color = colors.error) }
        }
    )
}

// ---- Folders ----

/** Filing a deck: an existing folder, a new one, or none. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FolderDialog(deckName: String, folders: List<String>, current: String?, onMove: (String?) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalAppColors.current
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        containerColor = colors.surface,
        onDismissRequest = onDismiss,
        title = { Text("File “$deckName”", color = colors.accentLight) },
        text = {
            Column {
                if (folders.isNotEmpty() || current != null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        folders.forEach { f -> PillChip(f, current.equals(f, ignoreCase = true), onClick = { onMove(f) }) }
                        if (current != null) PillChip("No folder", false, onClick = { onMove(null) })
                    }
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it.take(60) },
                    placeholder = { Text("New folder, e.g. Modern", color = colors.textDim) },
                    singleLine = true,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onMove(typed) },
                enabled = tidyFolder(typed).isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)
            ) { Text("Move") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textMuted) } }
    )
}

// ---- Value over time ----

/** "+$12 this month", "−$3 this month"; null under two points this month. */
fun monthChangeText(points: List<ValuePoint>, money: Money): String? {
    val change = monthChange(points) ?: return null
    val sign = if (change.usd < 0) "−" else "+"
    return sign + money.format(kotlin.math.abs(change.usd), kotlin.math.abs(money.toLocal(change.usd)) >= 100) + " this month"
}

private val DAY = DateTimeFormatter.ofPattern("d MMM", Locale.UK)

/** The deck's value over the last three months, noted once a day on this device, and how it moved this month. */
@Composable
fun DeckValuePanel(all: List<ValuePoint>, money: Money) {
    val colors = LocalAppColors.current
    val points = all.takeLast(91)
    val change = monthChangeText(all, money)
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Value over time", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            change?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = if (it.startsWith("−")) colors.error else colors.success) }
        }
        Spacer(Modifier.height(10.dp))
        if (points.size < 2) {
            Text(
                "Noted once a day on this device while the app is open — the chart fills in from tomorrow.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted
            )
        } else {
            val days = points.map { LocalDate.parse(it.date).toEpochDay() }
            val span = (days.last() - days.first()).coerceAtLeast(1).toFloat()
            val lo = points.minOf { it.usd }
            val hi = points.maxOf { it.usd }
            val pad = (if (hi - lo > 0) hi - lo else maxOf(hi * 0.1, 1.0)) * 0.1
            val bottom = lo - pad
            val top = hi + pad
            val line = colors.accent
            Canvas(Modifier.fillMaxWidth().height(64.dp)) {
                fun x(i: Int) = (days[i] - days.first()) / span * size.width
                fun y(v: Double) = (size.height * (1 - (v - bottom) / (top - bottom))).toFloat()
                val path = Path()
                points.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(i), y(p.usd)) else path.lineTo(x(i), y(p.usd)) }
                drawPath(path, line, style = Stroke(width = 2.dp.toPx()))
                drawCircle(line, radius = 3.dp.toPx(), center = Offset(x(points.size - 1), y(points.last().usd)))
            }
        }
        points.lastOrNull()?.let { last ->
            val day = runCatching { LocalDate.parse(last.date).format(DAY) }.getOrDefault(last.date)
            Text(
                "${money.format(last.usd, true)} on $day · kept on this device only",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
