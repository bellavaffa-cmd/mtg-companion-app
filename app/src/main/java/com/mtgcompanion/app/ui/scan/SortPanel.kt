package com.mtgcompanion.app.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.sp
import com.mtgcompanion.app.data.BY_RULE
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.MAX_PILES
import com.mtgcompanion.app.data.MIN_PILES
import com.mtgcompanion.app.data.PILE_COLOURS
import com.mtgcompanion.app.data.PileKind
import com.mtgcompanion.app.data.PileRule
import com.mtgcompanion.app.data.Prices
import com.mtgcompanion.app.data.SortSession
import com.mtgcompanion.app.data.defaultPiles
import com.mtgcompanion.app.data.pileDestination
import com.mtgcompanion.app.data.pileGoesTo
import com.mtgcompanion.app.data.pileTallies
import com.mtgcompanion.app.data.pileTitle
import com.mtgcompanion.app.data.placeTree
import com.mtgcompanion.app.data.placesOf
import com.mtgcompanion.app.ui.theme.LocalAppColors

/*
 * Sorting a new pile, in the scanner (ScanViewModel's sort mode): the big coloured tile for the card
 * just scanned — its pile's number, where that pile goes, the card, its rarity and price — the piles
 * so far, the piles' rules (Piles…) and "Done: file every pile". The logic is data/SortPiles.kt; the
 * web app's SortPilePanel.tsx shows the same.
 */

private val KIND_LABELS = mapOf(
    PileKind.VALUE to "Rares and mythics worth over…",
    PileKind.PRICE to "Any card worth over…",
    PileKind.SPARES to "Spares: more than … owned",
    PileKind.WANTED to "Wanted by a deck",
    PileKind.BULK to "Bulk: everything else"
)

private val RARITY = mapOf("common" to "Common", "uncommon" to "Uncommon", "rare" to "Rare", "mythic" to "Mythic", "special" to "Special", "bonus" to "Bonus")

private fun pileColour(i: Int) = Color(PILE_COLOURS[i % PILE_COLOURS.size])

@Composable
fun SortPanel(session: SortSession, collections: List<Collection>, onChange: (SortSession) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val money by Prices.money.collectAsState()
    var editing by remember { mutableStateOf(false) }
    val last = session.scans.lastOrNull()
    val tallies = pileTallies(session)
    val fmt = { usd: Double -> money.format(usd, whole = usd >= 10) }
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(colors.bg.copy(alpha = 0.94f))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                session.source, { onChange(session.copy(source = it)) },
                placeholder = { Text("Where they're from (Booster box, Duskmourn)") },
                singleLine = true, modifier = Modifier.weight(1f)
            )
            Text(
                if (session.newCards) "New cards" else "Already mine",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (session.newCards) colors.onAccent else colors.textPrimary,
                modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(if (session.newCards) colors.accent else colors.surface2)
                    .clickable { onChange(session.copy(newCards = !session.newCards)) }.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
        // The big tile: the card just scanned, in its pile's colour.
        val rule = last?.let { session.rules.getOrNull(it.pile) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(if (last != null && rule != null) pileColour(last.pile) else colors.surface2).padding(18.dp)
        ) {
            val ink = if (last != null && rule != null) Color(0xFF1E0D02) else colors.textPrimary
            Text(if (last == null) "?" else if (rule != null) "${last.pile + 1}" else "–", fontSize = 56.sp, fontWeight = FontWeight.Bold, color = ink)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (last == null) {
                    Text("Scan the first card", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = ink)
                    Text("Each card shows its pile, big.", style = MaterialTheme.typography.labelMedium, color = ink)
                } else {
                    Text(
                        if (rule != null) "Pile ${last.pile + 1} · ${pileDestination(rule, last.facts, collections).label}" else "No pile fits · Unsorted",
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(last.name, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(last.rarity?.let { RARITY[it] }, last.usd?.let { money.format(it) } ?: "No price").joinToString(" · ") +
                            if (last.why.isNotEmpty() && last.why != "Bulk") " · ${last.why}" else "",
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        // The piles so far, two a row.
        session.rules.indices.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                pair.forEach { i ->
                    val r = session.rules[i]
                    val t = tallies[i]
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(colors.surface)
                            .then(if (last?.pile == i) Modifier.border(2.dp, pileColour(i), RoundedCornerShape(14.dp)) else Modifier)
                            .padding(12.dp)
                    ) {
                        Text("Pile ${i + 1} · ${pileTitle(r, fmt)}", style = MaterialTheme.typography.labelSmall, color = pileColour(i), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(pileGoesTo(r, collections), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${t.cards} ${if (t.cards == 1) "card" else "cards"}" +
                                (if (t.usd > 0 && (r.pileKind == PileKind.VALUE || r.pileKind == PileKind.PRICE)) " · ${money.format(t.usd, whole = true)}" else "") +
                                (if (t.decks.isNotEmpty()) " · ${t.decks.joinToString(", ")}" else ""),
                            style = MaterialTheme.typography.labelSmall, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("${session.scans.size} ${if (session.scans.size == 1) "card" else "cards"} sorted", style = MaterialTheme.typography.labelMedium, color = colors.textMuted, modifier = Modifier.weight(1f))
            if (last != null) TextButton(onClick = { onChange(session.copy(scans = session.scans.dropLast(1))) }) { Text("Undo last", color = colors.accent) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { editing = true },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.surface2, contentColor = colors.textPrimary),
                modifier = Modifier.height(48.dp)
            ) { Text("Piles…", fontWeight = FontWeight.Bold) }
            Button(
                onClick = onDone,
                enabled = session.scans.isNotEmpty(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                modifier = Modifier.weight(1f).height(48.dp)
            ) { Text("Done: file every pile", fontWeight = FontWeight.ExtraBold) }
        }
    }
    if (editing) {
        PilesDialog(session.rules, collections, onDismiss = { editing = false }) { rules ->
            editing = false
            // Cards scanned so far stay in their piles; a pile that's gone leaves its cards with none.
            onChange(session.copy(rules = rules, scans = session.scans.map { if (it.pile >= rules.size) it.copy(pile = -1) else it }))
        }
    }
}

/** The piles' rules, in order: what goes in each and where it goes. */
@Composable
private fun PilesDialog(rules: List<PileRule>, collections: List<Collection>, onDismiss: () -> Unit, onSave: (List<PileRule>) -> Unit) {
    val colors = LocalAppColors.current
    val money by Prices.money.collectAsState()
    var list by remember { mutableStateOf(rules) }
    val places = placeTree(placesOf(collections))
    fun change(i: Int, r: PileRule) { list = list.mapIndexed { j, x -> if (j == i) r else x } }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("Piles", color = colors.accentLight, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Each card goes in the first pile whose rule fits it; bulk takes the rest.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                list.forEachIndexed { i, r ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surface2).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(14.dp).clip(CircleShape).background(pileColour(i)))
                            Text("Pile ${i + 1}", fontWeight = FontWeight.Bold, color = colors.textPrimary, modifier = Modifier.weight(1f).padding(start = 8.dp))
                            IconButton(onClick = { list = list.toMutableList().also { l -> val x = l[i]; l[i] = l[i - 1]; l[i - 1] = x } }, enabled = i > 0) { Icon(Icons.Filled.ArrowUpward, contentDescription = "Earlier", tint = colors.textPrimary) }
                            IconButton(onClick = { list = list.toMutableList().also { l -> val x = l[i]; l[i] = l[i + 1]; l[i + 1] = x } }, enabled = i < list.size - 1) { Icon(Icons.Filled.ArrowDownward, contentDescription = "Later", tint = colors.textPrimary) }
                            IconButton(onClick = { list = list.filterIndexed { j, _ -> j != i } }, enabled = list.size > MIN_PILES) { Icon(Icons.Filled.Delete, contentDescription = "Remove pile", tint = colors.textPrimary) }
                        }
                        Text("What goes in it", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                        PileKind.entries.forEach { k ->
                            Choice(KIND_LABELS.getValue(k), r.pileKind == k) {
                                change(i, PileRule(k.name, over = if (k == PileKind.VALUE || k == PileKind.PRICE) r.over ?: 2.0 else null, keep = if (k == PileKind.SPARES) r.keep ?: 4 else null, to = r.to))
                            }
                        }
                        if (r.pileKind == PileKind.VALUE || r.pileKind == PileKind.PRICE) {
                            var text by remember(i, r.kind) { mutableStateOf(String.format(java.util.Locale.US, "%.2f", money.toLocal(r.over ?: 0.0)).trimEnd('0').trimEnd('.')) }
                            OutlinedTextField(text, { v -> text = v; v.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }?.let { change(i, r.copy(over = money.toUsd(it))) } }, label = { Text("Worth over, in ${money.currency.code}") }, singleLine = true)
                        }
                        if (r.pileKind == PileKind.SPARES) {
                            var text by remember(i, r.kind) { mutableStateOf((r.keep ?: 4).toString()) }
                            OutlinedTextField(text, { v -> text = v; v.toIntOrNull()?.takeIf { it >= 0 }?.let { change(i, r.copy(keep = it)) } }, label = { Text("Copies to keep") }, singleLine = true)
                        }
                        Text("Where it goes", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                        Choice("No place (Unsorted)", r.to == null) { change(i, r.copy(to = null)) }
                        Choice("The box whose rule fits", r.to == BY_RULE) { change(i, r.copy(to = BY_RULE)) }
                        places.forEach { n -> Choice("  ".repeat(n.depth) + n.place.name, r.to == n.place.id) { change(i, r.copy(to = n.place.id)) } }
                    }
                }
                if (list.size < MAX_PILES) TextButton(onClick = { list = list + PileRule(PileKind.PRICE.name, over = 5.0) }) { Text("Add a pile", color = colors.accent) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(list) }) { Text("Save", color = colors.accent) } },
        dismissButton = { TextButton(onClick = { list = defaultPiles(collections) }) { Text("Start again", color = colors.textMuted) } }
    )
}

@Composable
private fun Choice(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (selected) colors.onAccent else colors.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (selected) colors.accent else colors.surface).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp)
    )
}
