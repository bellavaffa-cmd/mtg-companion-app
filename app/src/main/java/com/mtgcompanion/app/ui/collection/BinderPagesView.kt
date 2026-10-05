package com.mtgcompanion.app.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.PlacedCard
import com.mtgcompanion.app.data.PocketMove
import com.mtgcompanion.app.data.StoragePlace
import com.mtgcompanion.app.data.binderPockets
import com.mtgcompanion.app.data.cardsIn
import com.mtgcompanion.app.data.factsFrom
import com.mtgcompanion.app.data.moveCopies
import com.mtgcompanion.app.data.pageCount
import com.mtgcompanion.app.data.pageGrid
import com.mtgcompanion.app.data.pageSummary
import com.mtgcompanion.app.data.placesOf
import com.mtgcompanion.app.data.pocketAt
import com.mtgcompanion.app.data.pocketIndex
import com.mtgcompanion.app.data.pocketLabel
import com.mtgcompanion.app.data.pockets
import com.mtgcompanion.app.data.printingLine
import com.mtgcompanion.app.data.relocate
import com.mtgcompanion.app.data.reorderMoves
import com.mtgcompanion.app.data.sideLabel
import com.mtgcompanion.app.data.suggestSpot
import com.mtgcompanion.app.data.swapMoves
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.common.ArtImage
import com.mtgcompanion.app.ui.theme.LocalAppColors
import com.mtgcompanion.app.ui.theme.NumberStyle
import kotlin.math.abs

/*
 * A binder one page at a time, as it sits on the shelf: the page's pockets (empty ones dashed), arrows
 * and swiping to turn the page, "Page 3" over "Front of sheet 2 · DMU 12–98". Tapping a card shows its
 * pocket, printing and finish with Move; Edit lets pockets be dragged about the page or swapped two at
 * a time (BinderPages.kt). The web app's BinderPagesView.tsx.
 */

private val PocketGap = 8.dp
private val SheetPad = 12.dp

/**
 * [onChange] null: a preview, read only — the binder as a plan would leave it, with the [marked]
 * pockets picked out.
 */
@Composable
fun BinderPagesView(
    place: StoragePlace,
    collections: List<Collection>,
    cardData: Map<String, ScryfallCard>?,
    page: Int,
    onPage: (Int) -> Unit,
    onChange: ((StorageChange) -> Unit)?,
    onOpenCard: (String) -> Unit,
    modifier: Modifier = Modifier,
    marked: Set<Int> = emptySet()
) {
    val colors = LocalAppColors.current
    val density = LocalDensity.current
    val preview = onChange == null
    val pockets = place.pockets
    val cards = remember(collections, place.id) { cardsIn(collections, place.id) }
    val inUse = remember(place, cards) { binderPockets(place, cards) }
    val byIndex = remember(inUse) { inUse.associate { it.index to it.cards } }
    val occupied = remember(inUse) { inUse.map { it.index }.toSet() }
    // A page past the last one used is shown too, empty, so cards can be moved onto it.
    val pages = pageCount(place, inUse) + if (preview) 0 else 1
    val shown = page.coerceIn(1, pages)
    val (cols, _) = pageGrid(pockets)
    val first = pocketIndex(shown, 1, pockets)
    val slots = (first until first + pockets).toList()
    val facts = factsFrom(cardData)
    val summary = pageSummary(place.rule, slots.mapNotNull { byIndex[it]?.firstOrNull()?.let(facts) })
    var selected by remember(place.id) { mutableStateOf<Int?>(null) }
    var editing by remember(place.id) { mutableStateOf(false) }
    var moving by remember { mutableStateOf<PlacedCard?>(null) }
    var gridSize by remember { mutableStateOf(IntSize.Zero) }
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragOver by remember { mutableStateOf<Int?>(null) }

    fun applyMoves(moves: List<PocketMove>) {
        if (moves.isNotEmpty()) onChange?.invoke { relocate(it, place, moves) }
    }
    fun turn(to: Int) {
        selected = null
        onPage(to.coerceIn(1, pages))
    }
    /** The pocket under a point of the grid, or null between pockets. */
    fun pocketUnder(at: Offset): Int? {
        val pad = with(density) { SheetPad.toPx() }
        val gap = with(density) { PocketGap.toPx() }
        val cellW = (gridSize.width - 2 * pad - (cols - 1) * gap) / cols
        if (cellW <= 0) return null
        val cellH = cellW * 88f / 63f
        val col = ((at.x - pad) / (cellW + gap)).toInt()
        val row = ((at.y - pad) / (cellH + gap)).toInt()
        if (at.x < pad || at.y < pad || col !in 0 until cols) return null
        val i = row * cols + col
        return if (i in 0 until pockets) first + i else null
    }

    Column(modifier.fillMaxWidth()) {
        if (!preview) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp)) {
                Text(
                    if (editing) "Tap two pockets to swap them, or drag a card to another pocket on the page." else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (editing) "Done" else "Edit",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (editing) colors.onAccent else colors.textPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (editing) colors.accent else colors.surface2)
                        .clickable { editing = !editing; selected = null }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            IconButton(onClick = { turn(shown - 1) }, enabled = shown > 1) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous page", tint = if (shown > 1) colors.textPrimary else colors.textDim)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text("Page $shown", style = NumberStyle(28), color = colors.textPrimary)
                Text("${sideLabel(shown)} · $summary", style = MaterialTheme.typography.labelMedium, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { turn(shown + 1) }, enabled = shown < pages) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Next page", tint = if (shown < pages) colors.textPrimary else colors.textDim)
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(PocketGap),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface)
                .onSizeChanged { gridSize = it }
                .pointerInput(editing, shown, pages, occupied) {
                    if (preview) return@pointerInput
                    if (!editing) {
                        // Sideways turns the page; the screen still scrolls up and down over it.
                        var dx = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { dx = 0f },
                            onHorizontalDrag = { change, amount -> dx += amount; change.consume() },
                            onDragEnd = { if (abs(dx) > 50.dp.toPx()) turn(if (dx < 0) shown + 1 else shown - 1) }
                        )
                    } else {
                        detectDragGestures(
                            onDragStart = { at -> dragFrom = pocketUnder(at)?.takeIf { it in occupied }; dragOver = dragFrom },
                            onDrag = { change, _ -> if (dragFrom != null) { change.consume(); dragOver = pocketUnder(change.position) } },
                            onDragEnd = {
                                val from = dragFrom
                                val to = dragOver
                                // Within the page: dragging is for putting a page in order.
                                if (from != null && to != null && to != from) applyMoves(reorderMoves(from, to, occupied))
                                dragFrom = null
                                dragOver = null
                                selected = null
                            },
                            onDragCancel = { dragFrom = null; dragOver = null }
                        )
                    }
                }
                .padding(SheetPad)
        ) {
            slots.chunked(cols).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(PocketGap), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { i ->
                        val here = byIndex[i].orEmpty()
                        val card = here.firstOrNull()
                        val slot = pocketAt(i, pockets).second
                        val picked = selected == i || dragOver == i
                        val shape = RoundedCornerShape(6.dp)
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(63f / 88f)
                                .clip(shape)
                                .then(
                                    when {
                                        picked -> Modifier.border(2.dp, colors.accent, shape)
                                        i in marked -> Modifier.border(2.dp, colors.success, shape)
                                        card == null -> Modifier.border(2.dp, colors.border, shape)
                                        else -> Modifier
                                    }
                                )
                                .background(if (card != null) colors.surface2 else colors.surface)
                                .then(
                                    if (preview) Modifier
                                    else Modifier.clickable(enabled = card != null || (editing && selected != null)) {
                                        if (editing) {
                                            val s = selected
                                            if (s == null) { if (i in occupied) selected = i }
                                            else { applyMoves(swapMoves(s, i, occupied)); selected = null }
                                        } else selected = if (selected == i) null else i
                                    }
                                )
                        ) {
                            if (card != null) {
                                ArtImage(card.entry.imageUrl, card.entry.name, Modifier.fillMaxSize(), contentDescription = card.entry.name)
                                val n = here.sumOf { it.line.qty }
                                if (n > 1) Text(
                                    "×$n",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textPrimary,
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp).clip(RoundedCornerShape(6.dp)).background(colors.bg.copy(alpha = 0.8f)).padding(horizontal = 4.dp)
                                )
                            } else {
                                Text("Empty", style = MaterialTheme.typography.labelSmall, color = colors.textDim, modifier = Modifier.align(Alignment.Center))
                            }
                            Text(
                                "$slot",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (picked) colors.accentLight else colors.textMuted,
                                modifier = Modifier.align(Alignment.BottomStart).padding(3.dp).clip(RoundedCornerShape(4.dp)).background(colors.bg.copy(alpha = 0.7f)).padding(horizontal = 3.dp)
                            )
                        }
                    }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        val chosenAt = selected
        val chosen = if (!editing && chosenAt != null) byIndex[chosenAt]?.firstOrNull() else null
        if (chosen != null && chosenAt != null) {
            val card = cardData?.get(chosen.entry.scryfallId)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                ArtImage(chosen.entry.imageUrl.toArtCropUrl(), chosen.entry.name, Modifier.width(36.dp).height(50.dp).clip(RoundedCornerShape(4.dp)))
                Column(Modifier.weight(1f).padding(start = 12.dp).clickable { onOpenCard(chosen.entry.name) }) {
                    Text(chosen.entry.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(pocketLabel(shown, pocketAt(chosenAt, pockets).second), printingLine(card?.set, card?.collectorNumber, chosen.line.isFoil))
                            .filter { it.isNotEmpty() }.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textMuted
                    )
                }
                Button(
                    onClick = { moving = chosen },
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.surface2, contentColor = colors.textPrimary)
                ) { Text("Move") }
            }
        }
    }

    val movingCard = moving
    val from = selected
    if (movingCard != null && from != null && onChange != null) {
        PocketMoveDialog(
            place = place,
            collections = collections,
            card = movingCard,
            from = from,
            occupied = occupied,
            nameAt = { byIndex[it]?.firstOrNull()?.entry?.name },
            onMoveInBinder = { moves, toPage -> applyMoves(moves); moving = null; selected = null; onPage(toPage) },
            onMoveToPlace = { change -> onChange(change); moving = null; selected = null },
            onDismiss = { moving = null },
            cardData = cardData
        )
    }
}

/** Move a card to another pocket of the binder (swapping with what's there) or to another place. */
@Composable
private fun PocketMoveDialog(
    place: StoragePlace,
    collections: List<Collection>,
    card: PlacedCard,
    from: Int,
    occupied: Set<Int>,
    nameAt: (Int) -> String?,
    onMoveInBinder: (List<PocketMove>, Int) -> Unit,
    onMoveToPlace: (StorageChange) -> Unit,
    onDismiss: () -> Unit,
    cardData: Map<String, ScryfallCard>?
) {
    val colors = LocalAppColors.current
    val pockets = place.pockets
    val (nowPage, nowSlot) = pocketAt(from, pockets)
    var page by remember { mutableStateOf(nowPage.toString()) }
    var slot by remember { mutableStateOf(nowSlot.toString()) }
    var picking by remember { mutableStateOf(false) }
    val p = page.toIntOrNull() ?: 0
    val s = slot.toIntOrNull() ?: 0
    val valid = p >= 1 && s in 1..pockets
    val to = if (valid) pocketIndex(p, s, pockets) else null
    val there = if (to != null && to != from) nameAt(to) else null
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.accent,
        unfocusedBorderColor = colors.border,
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        cursorColor = colors.accent
    )
    if (picking) {
        PlacePickerDialog("Move ${card.entry.name} to…", placesOf(collections).filter { it.id != place.id }, onDismiss = { picking = false }) { id ->
            val facts = factsFrom(cardData)(card)
            onMoveToPlace { cols ->
                val target = placesOf(cols).firstOrNull { it.id == id } ?: return@onMoveToPlace cols
                val spot = suggestSpot(target, facts, cols).first
                cols.map { c ->
                    if (c.id != card.collectionId) c
                    else c.copy(entries = c.entries.map { e -> if (e.scryfallId == card.entry.scryfallId) moveCopies(e, card.line, spot, card.line.qty).first else e })
                }
            }
        }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("Move ${card.entry.name}", color = colors.accentLight, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Now in ${pocketLabel(nowPage, nowSlot)}. Where to?", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = page,
                        onValueChange = { page = it.filter { c -> c.isDigit() }.take(4) },
                        label = { Text("Page", color = colors.textMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = fieldColors,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = slot,
                        onValueChange = { slot = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("Slot", color = colors.textMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = fieldColors,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (!valid) Text("A page from 1, and a slot from 1 to $pockets.", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                if (there != null) Text("$there is there now — they swap pockets.", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                TextButton(onClick = { picking = true }) { Text("Another place…", color = colors.accent) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (to == null || to == from) onDismiss() else onMoveInBinder(swapMoves(from, to, occupied), p)
                },
                enabled = valid,
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)
            ) { Text(if (there != null) "Swap" else "Move here") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textMuted) } }
    )
}

/** The binder as a list, pocket by pocket, with the cards not in a pocket yet after. */
@Composable
fun BinderList(place: StoragePlace, cards: List<PlacedCard>, onOpenCard: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val pockets = place.pockets
    val inUse = binderPockets(place, cards)
    val inPockets = inUse.flatMap { it.cards }.toSet()
    val loose = cards.filter { it !in inPockets }
    val rows = inUse.flatMap { p -> p.cards.map { c -> c to pocketAt(p.index, pockets).let { (pg, sl) -> pocketLabel(pg, sl) } } } +
        loose.map { it to "Not in a pocket yet" }
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        rows.forEach { (c, where) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onOpenCard(c.entry.name) }) {
                ArtImage(c.entry.imageUrl.toArtCropUrl(), c.entry.name, Modifier.width(30.dp).height(42.dp).clip(RoundedCornerShape(4.dp)))
                Text(
                    c.entry.name + (if (c.line.isFoil) " · foil" else "") + (if (c.line.qty > 1) " ×${c.line.qty}" else ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 10.dp)
                )
                Text(where, style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
            }
        }
    }
}
