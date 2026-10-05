package com.mtgcompanion.app.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.PlaceKind
import com.mtgcompanion.app.data.PlacedCard
import com.mtgcompanion.app.data.cardsIn
import com.mtgcompanion.app.data.childrenOf
import com.mtgcompanion.app.data.copiesWithin
import com.mtgcompanion.app.data.deletePlace
import com.mtgcompanion.app.data.pagesOf
import com.mtgcompanion.app.data.parentsOf
import com.mtgcompanion.app.data.placeAndInside
import com.mtgcompanion.app.data.placeSubtitle
import com.mtgcompanion.app.data.placesOf
import com.mtgcompanion.app.data.pockets
import com.mtgcompanion.app.data.savePlace
import com.mtgcompanion.app.data.sectionsOf
import com.mtgcompanion.app.data.storageSummary
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.common.ArtImage
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.common.StatFigure
import com.mtgcompanion.app.ui.common.rememberMoney
import com.mtgcompanion.app.ui.social.QrCode
import com.mtgcompanion.app.ui.theme.LocalAppColors
import com.mtgcompanion.app.ui.theme.NumberStyle
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * One storage place, the web app's PlacePage (src/pages/PlacePage.tsx): its copies, their value and
 * its sections — a box's sections with their cards, a binder's pages of pockets — the places inside
 * it, "Put cards away" into it with the scanner, and a label to stick on it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceScreen(
    placeId: String,
    collections: List<Collection>,
    decks: List<Deck>,
    onBack: () -> Unit,
    onOpenPlace: (String) -> Unit,
    onPutAway: (String) -> Unit,
    onOpenCard: (String) -> Unit,
    onChange: (StorageChange) -> Unit
) {
    val colors = LocalAppColors.current
    val money = rememberMoney()
    val places = placesOf(collections)
    val place = places.firstOrNull { it.id == placeId }
    val summary = remember(collections, decks) { storageSummary(collections, decks) }
    val cards = remember(collections, placeId) { cardsIn(collections, placeId) }
    // Every copy in it and in the places inside it, for its value.
    val within = remember(collections, placeId) { placeAndInside(places, placeId).flatMap { cardsIn(collections, it) } }
    val ids = remember(within) { within.map { it.entry.scryfallId }.distinct().sorted() }
    // scryfallId → (plain, foil) price in US dollars; null until they've loaded.
    var prices by remember { mutableStateOf<Map<String, Pair<Double?, Double?>>?>(null) }
    LaunchedEffect(ids) {
        if (ids.isEmpty()) { prices = emptyMap(); return@LaunchedEffect }
        prices = runCatching {
            CardRepository().getCardsByIds(ids).associate { it.id to (it.prices?.usd?.toDoubleOrNull() to it.prices?.usdFoil?.toDoubleOrNull()) }
        }.getOrDefault(emptyMap())
    }
    var open by remember { mutableStateOf(setOf<String>()) }
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        val parents = place?.let { p -> parentsOf(places, p.id).joinToString(" › ") { it.name } }.orEmpty()
                        if (parents.isNotEmpty()) Text(parents, style = MaterialTheme.typography.labelMedium, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(place?.name ?: "Place", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = {
                    if (place != null) Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreHoriz, contentDescription = "More", tint = colors.textPrimary) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = colors.surface) {
                            DropdownMenuItem(
                                text = { Text("Change place", color = colors.textPrimary) },
                                leadingIcon = { Icon(Icons.Filled.Edit, null, tint = colors.textMuted) },
                                onClick = { menu = false; editing = true }
                            )
                            DropdownMenuItem(
                                text = { Text("New place inside", color = colors.textPrimary) },
                                leadingIcon = { Icon(Icons.Filled.Add, null, tint = colors.textMuted) },
                                onClick = { menu = false; adding = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete place", color = colors.error) },
                                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = colors.error) },
                                onClick = { menu = false; deleting = true }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        }
    ) { padding ->
        if (place == null) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text("This place isn't here any more.", color = colors.textMuted)
            }
            return@Scaffold
        }
        val inside = childrenOf(places, place.id)
        val sections = sectionsOf(place, cards)
        val binder = if (place.placeKind == PlaceKind.BINDER) pagesOf(place, cards) else null
        val copies = copiesWithin(summary, places, place.id)
        val value = prices?.let { p ->
            within.sumOf { c ->
                val (plain, foil) = p[c.entry.scryfallId] ?: (null to null)
                ((if (c.line.isFoil) foil ?: plain else plain ?: foil) ?: 0.0) * c.line.qty
            }
        }
        val third = when {
            binder != null -> binder.first.size to if (binder.first.size == 1) "page" else "pages"
            place.placeKind == PlaceKind.BOX || place.sections.orEmpty().isNotEmpty() -> sections.count { it.name != null } to "sections"
            else -> inside.size to if (inside.size == 1) "place inside" else "places inside"
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    StatFigure({ Text("$copies", style = NumberStyle(28), color = colors.textPrimary) }, if (copies == 1) "copy" else "copies", Modifier.weight(1f))
                    StatFigure({ Text(value?.let { money.format(it, whole = true) } ?: "—", style = NumberStyle(28), color = colors.accent) }, "value", Modifier.weight(1f))
                    StatFigure({ Text("${third.first}", style = NumberStyle(28), color = colors.textPrimary) }, third.second, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Button(
                        onClick = { onPutAway(place.id) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text("Put cards away", fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 8.dp))
                    }
                    Button(
                        onClick = { label = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.surface2, contentColor = colors.textPrimary),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Filled.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Label", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
            place.rule?.let { rule ->
                item {
                    Text(
                        "Sorted ${rule.label.replaceFirstChar { it.lowercase() }}. New cards get a section by this rule.",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textMuted
                    )
                }
            }
            if (binder != null) item {
                Text("${place.pockets} pockets a page. New cards go in the next free pocket.", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
            }
            items(inside, key = { "inside:" + it.id }) { p ->
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surface2).padding(horizontal = 12.dp, vertical = 10.dp)) {
                    PlaceRow(placeIcon(p.placeKind), p.name, placeSubtitle(p), "${copiesWithin(summary, places, p.id)}", gold = true, onClick = { onOpenPlace(p.id) })
                }
            }
            if (binder != null) {
                items(binder.first, key = { "page:" + it.page }) { page ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(12.dp)) {
                        Text("Page ${page.page}", style = MaterialTheme.typography.labelLarge, color = colors.textMuted, modifier = Modifier.padding(bottom = 8.dp))
                        val columns = ceil(sqrt(page.slots.size.toDouble())).toInt().coerceAtLeast(1)
                        page.slots.chunked(columns).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                                row.forEach { slot ->
                                    val first = slot.firstOrNull()
                                    Box(
                                        Modifier
                                            .weight(1f)
                                            .aspectRatio(63f / 88f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(colors.surface2)
                                            .border(1.dp, colors.border, RoundedCornerShape(6.dp))
                                            .then(if (first != null) Modifier.clickable { onOpenCard(first.entry.name) } else Modifier)
                                    ) {
                                        if (first != null) {
                                            ArtImage(first.entry.imageUrl, first.entry.name, Modifier.fillMaxSize(), contentDescription = first.entry.name)
                                            val n = slot.sumOf { it.line.qty }
                                            if (n > 1) Text(
                                                "×$n",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colors.textPrimary,
                                                modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp).clip(RoundedCornerShape(6.dp)).background(colors.bg.copy(alpha = 0.8f)).padding(horizontal = 4.dp)
                                            )
                                        }
                                    }
                                }
                                // Keep the last row's pockets the same size as the others.
                                repeat(columns - row.size) { Box(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
                if (binder.second.isNotEmpty()) item {
                    CardGroup("Not in a pocket yet", binder.second.sumOf { it.line.qty }, binder.second, true, {}, onOpenCard)
                }
                if (cards.isEmpty()) item {
                    Text("Nothing here yet. Put cards away to fill it, pocket by pocket.", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                }
            } else {
                items(sections, key = { "section:" + (it.name ?: "") }) { s ->
                    val key = s.name ?: ""
                    CardGroup(
                        title = s.name ?: if (sections.size > 1) "No section" else "Cards",
                        copies = s.copies,
                        cards = s.cards,
                        isOpen = key in open || (sections.size == 1 && s.name == null),
                        onToggle = { open = if (key in open) open - key else open + key },
                        onOpenCard = onOpenCard
                    )
                }
                if (cards.isEmpty() && sections.isEmpty()) item {
                    Text("Nothing here yet. Put cards away to fill it.", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                }
            }
        }
    }

    if (place != null && editing) {
        PlaceDialog(place = place, parentId = null, places = places, onDismiss = { editing = false }) { changed ->
            onChange { savePlace(it, changed) }
            editing = false
        }
    }
    if (place != null && adding) {
        PlaceDialog(place = null, parentId = place.id, places = places, onDismiss = { adding = false }) { made ->
            onChange { savePlace(it, made) }
            adding = false
        }
    }
    if (place != null && deleting) {
        val here = cards.sumOf { it.line.qty }
        val inside = childrenOf(places, place.id)
        AlertDialog(
            onDismissRequest = { deleting = false },
            containerColor = colors.surface,
            title = { Text("Delete “${place.name}”?", color = colors.accentLight) },
            text = {
                Text(
                    (if (here > 0) "Its $here copies stay in your collection with no place" else "Its cards stay in your collection") +
                        (if (inside.isNotEmpty()) ", and the places inside it move up a level" else "") + ". Here and on your other devices.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )
            },
            confirmButton = {
                TextButton(onClick = { deleting = false; onChange { deletePlace(it, place.id) }; onBack() }) { Text("Delete place", color = colors.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel", color = colors.textMuted) } }
        )
    }
    if (place != null && label) {
        AlertDialog(
            onDismissRequest = { label = false },
            containerColor = colors.surface,
            title = { Text("Label for ${place.name}", color = colors.accentLight) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    QrCode(text = place.id, size = 200.dp, label = "QR code for ${place.name}")
                    Text(place.name, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                    Text(
                        "Print it and stick it on. The code is the place's own, so it stays right if you rename it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted
                    )
                }
            },
            confirmButton = { TextButton(onClick = { label = false }) { Text("Done", color = colors.accent) } }
        )
    }
}

/** A section of a place (or any group of its cards): its name and count, and its cards when open. */
@Composable
private fun CardGroup(
    title: String,
    copies: Int,
    cards: List<PlacedCard>,
    isOpen: Boolean,
    onToggle: () -> Unit,
    onOpenCard: (String) -> Unit
) {
    val colors = LocalAppColors.current
    val shown = isOpen && cards.isNotEmpty()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .then(if (shown) Modifier.border(1.dp, colors.accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp)) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp).clickable(onClick = onToggle)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Text("$copies", style = MaterialTheme.typography.labelLarge, color = colors.textMuted)
        }
        if (shown) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 10.dp)) {
                cards.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onOpenCard(c.entry.name) }) {
                        ArtImage(
                            c.entry.imageUrl.toArtCropUrl(),
                            c.entry.name,
                            Modifier.width(30.dp).height(42.dp).clip(RoundedCornerShape(4.dp))
                        )
                        Text(
                            c.entry.name + if (c.line.isFoil) " · foil" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(start = 10.dp)
                        )
                        Text("×${c.line.qty}", style = MaterialTheme.typography.labelLarge, color = colors.textMuted)
                    }
                }
            }
        }
    }
}
