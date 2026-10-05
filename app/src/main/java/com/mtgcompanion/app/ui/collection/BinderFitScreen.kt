package com.mtgcompanion.app.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.mtgcompanion.app.data.FitMode
import com.mtgcompanion.app.data.PlaceKind
import com.mtgcompanion.app.data.SortRule
import com.mtgcompanion.app.data.applyFit
import com.mtgcompanion.app.data.cardsIn
import com.mtgcompanion.app.data.factsFrom
import com.mtgcompanion.app.data.fitLooseCards
import com.mtgcompanion.app.data.fitSteps
import com.mtgcompanion.app.data.placesOf
import com.mtgcompanion.app.data.pocketAt
import com.mtgcompanion.app.data.pockets
import com.mtgcompanion.app.data.printingLine
import com.mtgcompanion.app.data.savePlace
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.theme.LocalAppColors

/**
 * Add cards in order, the web app's BinderFitPage (src/pages/BinderFitPage.tsx): the binder's cards
 * that aren't in a pocket yet — put away into it with the scanner, say — fitted in by its order. "Keep
 * the order" shifts cards along to make room, "Fill gaps, no shifting" only uses empty pockets; the
 * steps say what to do by hand, from the last card backwards, and Show on pages shows the binder as
 * it'll be. Done moves every copy to its new pocket. The rules are BinderPages.kt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BinderFitScreen(
    placeId: String,
    collections: List<Collection>,
    onBack: () -> Unit,
    onChange: (StorageChange) -> Unit,
    /** The scanner putting cards away into the binder. */
    onScanIn: (String) -> Unit,
    /** After Done: the binder, at the page the first new card went in. */
    onDone: (placeId: String, page: Int) -> Unit
) {
    val colors = LocalAppColors.current
    val place = placesOf(collections).firstOrNull { it.id == placeId }
    val cards = remember(collections, placeId) { cardsIn(collections, placeId) }
    val ids = remember(cards) { cards.map { it.entry.scryfallId }.distinct().sorted() }
    var cardData by remember { mutableStateOf<Map<String, ScryfallCard>?>(null) }
    LaunchedEffect(ids) {
        if (ids.isEmpty()) { cardData = emptyMap(); return@LaunchedEffect }
        cardData = runCatching { CardRepository().getCardsByIds(ids).associateBy { it.id } }.getOrDefault(emptyMap())
    }
    var mode by remember { mutableStateOf(FitMode.KEEP) }
    var onPages by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf<Int?>(null) }
    val keep = remember(collections, place, cardData) { place?.let { fitLooseCards(collections, it, factsFrom(cardData), FitMode.KEEP) } }
    val gaps = remember(collections, place, cardData) { place?.let { fitLooseCards(collections, it, factsFrom(cardData), FitMode.GAPS) } }
    val items = keep?.items.orEmpty()

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        if (place != null) Text(place.name, style = MaterialTheme.typography.labelMedium, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (items.isNotEmpty()) "Adding ${items.size} ${if (items.size == 1) "card" else "cards"}" else "Add cards in order",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                navigationIcon = { BackButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        }
    ) { padding ->
        if (place == null || place.placeKind != PlaceKind.BINDER || keep == null || gaps == null) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp)) { Text("This binder isn't here any more.", color = colors.textMuted) }
            return@Scaffold
        }
        val rule = place.rule
        val shifting = keep.plan.moves.size
        val chosen = if (shifting == 0 || mode == FitMode.KEEP) keep else gaps
        val pockets = place.pockets
        val loading = cardData == null && items.isNotEmpty() && rule != SortRule.NAME
        val firstPage = chosen.plan.puts.minOfOrNull { pocketAt(it.to, pockets).first } ?: 1
        val ready = rule != null && items.isNotEmpty() && !loading
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (rule == null) item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("What order is this binder in?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        SortRule.entries.forEach { r ->
                            Text(
                                r.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.textPrimary,
                                modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(colors.surface2)
                                    .clickable { onChange { savePlace(it, place.copy(sortRule = r.name)) } }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
                if (rule != null && items.isEmpty()) item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
                        Text(
                            "No cards waiting. Scan cards into this binder, then fit them in by its order — ${rule.label.lowercase()}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted
                        )
                        Button(
                            onClick = { onScanIn(place.id) },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)
                        ) { Text("Scan cards in", fontWeight = FontWeight.Bold) }
                    }
                }
                if (rule != null && items.isNotEmpty() && loading) item {
                    Text("Getting the cards' sets and numbers…", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                }
                if (ready) {
                    item {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (shifting > 0) {
                                Text(
                                    "Shift $shifting ${if (shifting == 1) "card" else "cards"} along, or use the gaps?",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FitModeButton("Keep the order", mode == FitMode.KEEP, Modifier.weight(1f)) { mode = FitMode.KEEP }
                                    FitModeButton("Fill gaps, no shifting", mode == FitMode.GAPS, Modifier.weight(1f)) { mode = FitMode.GAPS }
                                }
                            } else {
                                Text("Nothing needs to move", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                Text("Each card has an empty pocket where it goes.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                            }
                        }
                    }
                    if (onPages) item {
                        BinderPagesView(
                            place = place,
                            collections = applyFit(collections, place, chosen.plan, chosen.items),
                            cardData = cardData,
                            page = page ?: firstPage,
                            onPage = { page = it },
                            onChange = null,
                            onOpenCard = {},
                            marked = chosen.plan.puts.map { it.to }.toSet()
                        )
                    } else {
                        item { Text("Steps", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary, modifier = Modifier.padding(top = 6.dp)) }
                        val steps = fitSteps(
                            chosen.plan,
                            pockets,
                            { i -> chosen.pockets.firstOrNull { it.index == i }?.cards?.firstOrNull()?.entry?.name ?: "the card" },
                            { i ->
                                val c = chosen.items[i]
                                val card = cardData?.get(c.entry.scryfallId)
                                c.entry.name to printingLine(card?.set, card?.collectorNumber, c.line.isFoil)
                            }
                        )
                        itemsIndexed(steps) { i, st ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Box(Modifier.size(26.dp).clip(RoundedCornerShape(13.dp)).background(colors.surface2), contentAlignment = Alignment.Center) {
                                    Text("${i + 1}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary)
                                }
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(st.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                    if (st.detail.isNotEmpty()) Text(st.detail, style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                                }
                            }
                        }
                    }
                }
            }
            if (ready) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().background(colors.bg).padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Button(
                        onClick = { onPages = !onPages },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.surface2, contentColor = colors.textPrimary),
                        modifier = Modifier.height(48.dp)
                    ) { Text(if (onPages) "Show steps" else "Show on pages", fontWeight = FontWeight.Bold) }
                    Button(
                        onClick = {
                            val plan = chosen.plan
                            val fitted = chosen.items
                            onChange { applyFit(it, place, plan, fitted) }
                            onDone(place.id, firstPage)
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) { Text("Done", fontWeight = FontWeight.ExtraBold) }
                }
            }
        }
    }
}

@Composable
private fun FitModeButton(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Box(
        modifier.height(36.dp).clip(RoundedCornerShape(10.dp)).background(if (on) colors.accent else colors.surface2).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) colors.onAccent else colors.textMuted, maxLines = 1)
    }
}
