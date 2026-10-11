package com.mtgcompanion.app.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionType
import com.mtgcompanion.app.data.LEVEL_BYS
import com.mtgcompanion.app.data.LEVEL_LABELS
import com.mtgcompanion.app.data.Misfit
import com.mtgcompanion.app.data.RuleCard
import com.mtgcompanion.app.data.SORT_BY_CHOICES
import com.mtgcompanion.app.data.SplitLevel
import com.mtgcompanion.app.data.StoragePlace
import com.mtgcompanion.app.data.Spot
import com.mtgcompanion.app.data.cardFactsOf
import com.mtgcompanion.app.data.holdsMatch
import com.mtgcompanion.app.data.holdsSummary
import com.mtgcompanion.app.data.movedInOrder
import com.mtgcompanion.app.data.placeOrderOf
import com.mtgcompanion.app.data.placeTree
import com.mtgcompanion.app.data.placesOf
import com.mtgcompanion.app.data.rulePlaces
import com.mtgcompanion.app.data.rulesLine
import com.mtgcompanion.app.data.splitLevel
import com.mtgcompanion.app.data.withPlaceOrder
import com.mtgcompanion.app.ui.common.PillChip
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlin.math.roundToInt

/*
 * The places' rules on screen (the logic is data/StorageRules.kt): the rule editor inside the place
 * dialog (what it holds, its sections, its order), the Rule order list to drag places into the order
 * they're tried, and what doesn't belong in a place. Mirrors the web app's
 * src/collection/StorageRulesUi.tsx.
 */

/** A level's options as typed: VALUE, MANA_VALUE and NUMBER their cuts, NAME its letters, SET its sets. */
fun levelOptionsText(l: SplitLevel?): String = when (l?.by) {
    "VALUE", "MANA_VALUE", "NUMBER" -> l.cuts.orEmpty().joinToString(", ") { if (it == Math.floor(it)) it.toLong().toString() else it.toString() }
    "NAME" -> l.letters.orEmpty().joinToString(", ")
    "SET" -> l.sets.orEmpty().joinToString(", ") { it.uppercase() }
    else -> ""
}

/** [l] with its options typed as [text] (see levelOptionsText). */
fun withLevelOptions(l: SplitLevel, text: String): SplitLevel {
    val items = text.split(',', ' ').map { it.trim() }.filter { it.isNotEmpty() }
    return splitLevel(when (l.by) {
        "VALUE", "MANA_VALUE", "NUMBER" -> l.copy(cuts = items.mapNotNull { it.removePrefix("$").toDoubleOrNull() })
        "NAME" -> l.copy(letters = items)
        "SET" -> l.copy(sets = items)
        else -> l
    })
}

private fun optionsLabel(by: String): String? = when (by) {
    "VALUE" -> "Price bands start at, in US dollars (highest first)"
    "MANA_VALUE" -> "Mana value sections start at"
    "NUMBER" -> "Number sections start at"
    "NAME" -> "Letter sections start at"
    "SET" -> "Sets with a section each (blank: one per set)"
    else -> null
}

/**
 * The rule editor in the place dialog: "Holds" (Everything, Choose… or a saved filter), and — for
 * anything but a binder — "Sections by" and "Then sorted by". [onSections]: the sections a level
 * makes, when it's picked or changed. The warnings below say which places' rules overlap.
 */
@Composable
fun RulesEditor(
    isBinder: Boolean,
    holds: Pair<CollectionFilter, AdvancedFilter>?,
    onHolds: (Pair<CollectionFilter, AdvancedFilter>?) -> Unit,
    level: SplitLevel?,
    onLevel: (SplitLevel?) -> Unit,
    sortKey: String,
    onSortKey: (String) -> Unit,
    saved: List<SavedFilter>,
    overlaps: List<String>,
    onRuleOrder: (() -> Unit)?,
    holdsLine: String,
    onChooseHolds: () -> Unit
) {
    val colors = LocalAppColors.current
    var holdsMenu by remember { mutableStateOf(false) }
    Text("Rules", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.textPrimary)
    Column {
        Text("Holds", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surface2)
                    .clickable { holdsMenu = true }
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Text(holdsLine, color = colors.textPrimary, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = colors.textMuted)
            }
            DropdownMenu(expanded = holdsMenu, onDismissRequest = { holdsMenu = false }, containerColor = colors.surface) {
                DropdownMenuItem(text = { Text("Everything", color = colors.textPrimary) }, onClick = { holdsMenu = false; onHolds(null) })
                DropdownMenuItem(text = { Text(if (holds != null) "Change…" else "Choose…", color = colors.textPrimary) }, onClick = { holdsMenu = false; onChooseHolds() })
                saved.forEach { s ->
                    DropdownMenuItem(text = { Text("Saved filter: ${s.name}", color = colors.textPrimary) }, onClick = { holdsMenu = false; onHolds(s.basic to s.advanced) })
                }
            }
        }
    }
    if (!isBinder) {
        PickField(
            "Sections by",
            level?.by ?: "",
            listOf("" to "None") + LEVEL_BYS.map { it to (LEVEL_LABELS[it] ?: it) }
        ) { by -> onLevel(if (by.isEmpty()) null else splitLevel(SplitLevel(by, lands = if (by == "COLOUR") true else null))) }
        val l = level
        if (l != null) {
            if (l.by == "COLOUR") PillChip("Lands get their own section", l.lands == true, { onLevel(splitLevel(l.copy(lands = if (l.lands == true) null else true))) })
            optionsLabel(l.by)?.let { label ->
                var text by remember(l.by) { mutableStateOf(levelOptionsText(l)) }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; onLevel(withLevelOptions(l, it)) },
                    label = { Text(label, color = colors.textMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent, unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary, cursorColor = colors.accent
                    )
                )
            }
        }
        PickField("Then sorted by", sortKey, listOf("" to "Not sorted") + SORT_BY_CHOICES) { onSortKey(it) }
    }
    overlaps.forEach { line ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
            Text(line, style = MaterialTheme.typography.labelMedium, color = colors.textPrimary, modifier = Modifier.padding(start = 6.dp))
        }
    }
    if (overlaps.isNotEmpty() && onRuleOrder != null) {
        Text(
            "Rule order",
            style = MaterialTheme.typography.labelLarge,
            color = colors.accentLight,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onRuleOrder).padding(vertical = 4.dp)
        )
    }
}

/**
 * "Holds › Choose…": the Cards filter panel (type, text, colours, rarity, tags) and, behind it, the
 * Advanced filters — what the place holds. [cards]: the collection's cards as the rules judge them,
 * for how many fit.
 */
@Composable
fun HoldsDialog(
    holds: Pair<CollectionFilter, AdvancedFilter>?,
    cards: List<RuleCard>,
    collections: List<Collection>,
    saved: List<SavedFilter>,
    savedFilters: SavedFilters?,
    userTagOptions: List<String>,
    onDismiss: () -> Unit,
    onDone: (Pair<CollectionFilter, AdvancedFilter>?) -> Unit
) {
    val colors = LocalAppColors.current
    var basic by remember { mutableStateOf(holds?.first ?: CollectionFilter()) }
    var advanced by remember { mutableStateOf(holds?.second ?: AdvancedFilter()) }
    var advancedOpen by remember { mutableStateOf(false) }
    val places = placesOf(collections)
    val on = basic.active || advanced.active
    val fit = if (cards.isEmpty() || !on) null else cards.count { holdsMatch(basic to advanced, it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("What it holds", color = colors.accentLight, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (on) "Holds " + holdsSummary(basic, advanced, { id -> collections.firstOrNull { it.id == id }?.name }, { id -> places.firstOrNull { it.id == id }?.name })
                    else "Holds everything",
                    style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary
                )
                if (fit != null) Text("$fit of your cards fit", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                CollectionFilterPanel(
                    filter = basic,
                    onChange = { basic = it },
                    onAdvanced = { advancedOpen = true },
                    onClear = { basic = CollectionFilter(); advanced = AdvancedFilter() },
                    anyOn = on,
                    userTagOptions = userTagOptions
                )
            }
        },
        confirmButton = { TextButton(onClick = { onDone(if (on) basic to advanced else null) }) { Text("Done", color = colors.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textMuted) } }
    )
    if (advancedOpen) {
        AdvancedFilterScreen(
            basic = basic,
            advanced = advanced,
            countFor = { b, a -> cards.count { holdsMatch(b to a, it) } },
            binders = collections.filter { it.kind == CollectionType.OWNED }.sortedBy { it.name.lowercase() }.map { it.id to it.name },
            sets = cards.mapNotNull { it.advanced?.set?.takeIf { s -> s.isNotEmpty() } }.distinct().sorted().map { it to it.uppercase() },
            saved = saved,
            onSave = { n, b, a -> savedFilters?.save(n, b, a) },
            onRename = { id, n -> savedFilters?.rename(id, n) },
            onDelete = { id -> savedFilters?.delete(id) },
            onApply = { b, a -> basic = b; advanced = a; advancedOpen = false },
            onDismiss = { advancedOpen = false },
            places = placeTree(places).map { it.place.id to ("  ".repeat(it.depth) + it.place.name) }
        )
    }
}

/**
 * Rule order: the places with rules, in the order they're tried — drag one (long-press) or use its
 * arrows to move it. "When a card fits more than one place, it goes to the first one here."
 */
@Composable
fun RuleOrderDialog(collections: List<Collection>, onChange: (StorageChange) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalAppColors.current
    val places = placesOf(collections)
    val start = rulePlaces(places, placeOrderOf(collections)).map { it.id }
    var ids by remember { mutableStateOf(start) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val rowPx = with(LocalDensity.current) { 64.dp.toPx() }
    val save = { list: List<String> -> onChange { withPlaceOrder(it, list) } }
    fun move(id: String, to: Int) {
        val next = movedInOrder(places, ids, id, to)
        ids = next
        save(next)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("Rule order", color = colors.accentLight, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("When a card fits more than one place, it goes to the first one here.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                if (ids.isEmpty()) Text("No place has a rule yet. Change a place and say what it holds.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                ids.forEachIndexed { i, id ->
                    val p = places.firstOrNull { it.id == id } ?: return@forEachIndexed
                    val dragged = dragging == id
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .zIndex(if (dragged) 1f else 0f)
                            .graphicsLayer { translationY = if (dragged) offset else 0f }
                            .fillMaxWidth()
                            .heightIn(min = 60.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (dragged) colors.surface2 else colors.surface)
                            .pointerInput(id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { dragging = id; offset = 0f },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        offset += amount.y
                                        val from = ids.indexOf(id)
                                        val to = (from + (offset / rowPx).roundToInt()).coerceIn(0, ids.lastIndex)
                                        if (from >= 0 && to != from) {
                                            ids = ids.toMutableList().apply { removeAt(from); add(to, id) }
                                            offset -= (to - from) * rowPx
                                        }
                                    },
                                    onDragEnd = { dragging = null; offset = 0f; save(ids) },
                                    onDragCancel = { dragging = null; offset = 0f }
                                )
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.DragHandle, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
                        Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = colors.accent, modifier = Modifier.padding(horizontal = 8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(rulesLine(p).ifEmpty { "Holds everything" }, style = MaterialTheme.typography.labelMedium, color = colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { move(id, i - 1) }, enabled = i > 0) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = "Move ${p.name} up", tint = if (i > 0) colors.textPrimary else colors.textDim)
                        }
                        IconButton(onClick = { move(id, i + 1) }, enabled = i < ids.lastIndex) {
                            Icon(Icons.Filled.ArrowDownward, contentDescription = "Move ${p.name} down", tint = if (i < ids.lastIndex) colors.textPrimary else colors.textDim)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = colors.accent) } }
    )
}

/**
 * What doesn't belong in a place by its rules: each line says where it belongs, with Move — and Move
 * all. [spotFor]: where in its new place a copy goes (suggestSpot).
 */
@Composable
fun MisfitsDialog(found: List<Misfit>, spotFor: (Misfit) -> Spot?, onMove: (List<Pair<Misfit, Spot?>>) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalAppColors.current
    val movable = found.filter { it.to != null || it.section != null }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("Doesn't fit the rules", color = colors.accentLight, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (found.isEmpty()) Text("Everything here fits.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                found.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(m.card.entry.name + (if (m.card.line.isFoil) " · foil" else "") + " ×${m.card.line.qty}", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                            Text(m.line, style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                        }
                        if (m.to != null || m.section != null) TextButton(onClick = { onMove(listOf(m to spotFor(m))) }) { Text("Move", color = colors.accent) }
                    }
                }
            }
        },
        confirmButton = {
            if (movable.size > 1) TextButton(onClick = { onMove(movable.map { it to spotFor(it) }) }) { Text("Move all ${movable.size}", color = colors.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = colors.textMuted) } }
    )
}

/** The collection's cards as the rules judge them, from the filter facts already worked out (StorageTab, PlaceScreen). */
fun ruleCardsOf(collections: List<Collection>, facts: FilterFacts): List<RuleCard> {
    val out = mutableListOf<RuleCard>()
    val seen = HashSet<String>()
    for (c in collections) {
        if (c.kind == CollectionType.WISHLIST) continue
        for (e in c.entries) {
            if (!seen.add(e.scryfallId)) continue
            val basic = facts.card(e.scryfallId) ?: continue
            val advanced = facts.advanced(e.scryfallId)
            out += RuleCard(
                com.mtgcompanion.app.data.CardFacts(e.name, advanced?.colors, basic.typeLine, advanced?.set, null, advanced?.identity, advanced?.cmc, basic.rarity, advanced?.usd, advanced?.usdFoil),
                basic, advanced, null, facts.roleTags(e.name), facts.userTags(e.scryfallId)
            )
        }
    }
    return out
}

/**
 * A line of copies as the rules judge it, from the filter facts already worked out: the card's
 * ([basic], [advanced]; null when its data isn't here) and the copy's own (copyFactsOfCopy).
 */
fun ruleCardOfCopy(
    c: com.mtgcompanion.app.data.PlacedCard,
    basic: CardFacts?,
    advanced: AdvancedFacts?,
    roleTags: List<String>?,
    userTags: List<String>,
    copies: Map<String, CopyFacts>,
    chains: Map<String, List<String>>
): RuleCard? {
    if (basic == null) return null
    return RuleCard(
        com.mtgcompanion.app.data.CardFacts(
            c.entry.name, advanced?.colors, basic.typeLine, advanced?.set, null, advanced?.identity, advanced?.cmc, basic.rarity,
            advanced?.usd, advanced?.usdFoil, c.line.isFoil
        ),
        basic, advanced, copyFactsOfCopy(c, copies[c.entry.scryfallId], chains), roleTags, userTags
    )
}

/** A Scryfall card's sorting facts, for a copy (foil or not). */
fun ruleFactsOf(card: com.mtgcompanion.app.network.scryfall.ScryfallCard, foil: Boolean) = cardFactsOf(card).copy(foil = foil)
