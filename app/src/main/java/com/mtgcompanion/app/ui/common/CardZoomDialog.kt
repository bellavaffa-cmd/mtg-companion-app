package com.mtgcompanion.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary

/** A place a card is held — a binder or a deck — and how many copies are there. */
enum class SourceKind { BINDER, DECK }

data class CardSource(val kind: SourceKind, val id: String, val name: String, val quantity: Int)

/**
 * Where every card is physically held, across every binder and deck — scryfallId -> the places
 * holding it. Feeds [ZoomCard.sources] for the enlarged-card "IN N PLACES" section; a card held
 * nowhere is simply absent from the map. Callers viewing one particular deck/binder typically
 * filter out that deck/binder's own [CardSource.id] before passing the list to [ZoomCard].
 */
fun buildCardSources(collections: List<Collection>, decks: List<Deck>): Map<String, List<CardSource>> {
    val bySource = HashMap<String, MutableList<CardSource>>()
    collections.forEach { collection ->
        collection.entries.forEach { entry ->
            val qty = entry.quantity + entry.foilQuantity
            if (qty > 0) {
                bySource.getOrPut(entry.scryfallId) { mutableListOf() } +=
                    CardSource(SourceKind.BINDER, collection.id, collection.name, qty)
            }
        }
    }
    decks.forEach { deck ->
        deck.cards.forEach { entry ->
            if (entry.quantity > 0) {
                bySource.getOrPut(entry.scryfallId) { mutableListOf() } +=
                    CardSource(SourceKind.DECK, deck.id, deck.name, entry.quantity)
            }
        }
    }
    return bySource
}

/**
 * One card in the enlarged-card overlay. [quantity] null hides the quantity/total row (e.g. for a
 * suggested card that isn't owned); providing [onIncrement]/[onDecrement] turns the count into an
 * editable stepper. [onAdd], for a card not yet in a deck/binder, offers to put it in one.
 * [sources], when set, lists the binders/decks the card is in. [cardName] + [onSelectPrinting]
 * together show every alternate printing of the card as a strip below the art — tapping one calls
 * [onSelectPrinting] with that printing. [backImageUrl], when set (a transform/modal-DFC/flip
 * card), adds a flip control that swaps the shown art to the other face. [tags] — printed
 * keywords plus heuristic theme tags — only ever set when the full [ScryfallCard] is on hand
 * (search results, resolved suggestions); a deck/binder entry's cached fields don't include it.
 * [onFindSimilar], when set, adds a "find similar cards" action — resolves this card by name and
 * opens its own zoom overlay of mechanically similar cards (see [SimilarCardsDialog]).
 */
data class ZoomCard(
    val imageUrl: String?,
    val cardName: String? = null,
    val priceUsd: Double? = null,
    val quantity: Int? = null,
    val onIncrement: (() -> Unit)? = null,
    val onDecrement: (() -> Unit)? = null,
    val onMove: (() -> Unit)? = null,
    val onAdd: (() -> Unit)? = null,
    val onSelectPrinting: ((ScryfallCard) -> Unit)? = null,
    val onViewDetails: (() -> Unit)? = null,
    val sources: List<CardSource> = emptyList(),
    val backImageUrl: String? = null,
    val tags: List<String> = emptyList(),
    val onFindSimilar: (() -> Unit)? = null
)

/**
 * Full-screen overlay that enlarges a card. Opens on [initialIndex] within [cards] and lets the
 * user swipe left/right to page through the rest of the list. Below each card it shows its value,
 * total value, and (when editable) a quantity stepper. Tap the card to dismiss.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CardZoomDialog(cards: List<ZoomCard>, initialIndex: Int, onDismiss: () -> Unit) {
    if (cards.isEmpty()) return
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, cards.size - 1),
        pageCount = { cards.size }
    )
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Pops the whole overlay in from a slight scale/fade rather than snapping into view instantly.
        val entrance = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            entrance.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f * entrance.value.coerceIn(0f, 1f)))
                .graphicsLayer {
                    alpha = entrance.value.coerceIn(0f, 1f)
                    val scale = 0.9f + 0.1f * entrance.value
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val card = cards[page]
                // A tapped alternate printing only swaps what's previewed here — it doesn't act on
                // its own. Resets whenever the pager lands on a different card.
                var previewed by remember(card) { mutableStateOf<ScryfallCard?>(null) }
                // Which face is showing, for a transform/modal-DFC/flip card. Resets per card too.
                var flipped by remember(card) { mutableStateOf(false) }
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = previewed?.displayImageUrl ?: (if (flipped) card.backImageUrl else card.imageUrl),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .padding(horizontal = 24.dp, vertical = 16.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .foilShine()
                        )
                        if (card.backImageUrl != null) {
                            val flipHaptic = LocalHapticFeedback.current
                            IconButton(
                                onClick = {
                                    flipHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    flipped = !flipped
                                    previewed = null
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 24.dp, end = 32.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f))
                            ) {
                                Icon(Icons.Filled.Autorenew, contentDescription = "Flip card", tint = Gold)
                            }
                        }
                    }
                    val cardName = card.cardName
                    val onSelectPrinting = card.onSelectPrinting
                    if (cardName != null && onSelectPrinting != null) {
                        AlternatePrintingsStrip(
                            cardName = cardName,
                            previewed = previewed,
                            onPreview = { previewed = it },
                            onConfirm = { chosen -> onSelectPrinting(chosen); previewed = null }
                        )
                    }
                    // Previewing an alternate printing doesn't change tags — same card, different art.
                    if (card.tags.isNotEmpty()) {
                        CardTagsRow(card.tags, modifier = Modifier.background(Surface).padding(horizontal = 24.dp, vertical = 8.dp))
                    }
                    // While a printing is previewed, show its own price instead of the original's.
                    val effectivePrice = previewed?.prices?.usd?.toDoubleOrNull() ?: card.priceUsd
                    if (effectivePrice != null || card.quantity != null ||
                        card.onAdd != null || card.onViewDetails != null || card.onFindSimilar != null
                    ) {
                        CardInfoBar(card, effectivePrice)
                    }
                    if (card.sources.isNotEmpty()) {
                        SourcesSection(card.sources)
                    }
                }
            }
        }
    }
}

@Composable
private fun CardInfoBar(card: ZoomCard, priceUsd: Double?) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            priceUsd?.let { price ->
                InfoStat("VALUE", "$" + "%,.2f".format(price))
            }
            // A total is only meaningful once you own a copy — otherwise it's just "$0.00".
            if (priceUsd != null && card.quantity != null && card.quantity > 0) {
                InfoStat("TOTAL", "$" + "%,.2f".format(priceUsd * card.quantity))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            card.onViewDetails?.let { viewDetails ->
                IconButton(onClick = viewDetails) {
                    Icon(Icons.Filled.Info, contentDescription = "View details", tint = Gold)
                }
            }
            card.onAdd?.let { add ->
                IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); add() }) {
                    Icon(Icons.Filled.AddCircle, contentDescription = "Add to binder or deck", tint = Gold)
                }
            }
            card.onMove?.let { move ->
                IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); move() }) {
                    Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Move card", tint = Gold)
                }
            }
            card.onFindSimilar?.let { findSimilar ->
                IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); findSimilar() }) {
                    Icon(Icons.Filled.Search, contentDescription = "Find similar cards", tint = Gold)
                }
            }
            card.quantity?.let { qty ->
                if (card.onIncrement != null || card.onDecrement != null) {
                    IconButton(onClick = { card.onDecrement?.invoke() }) {
                        Icon(Icons.Filled.Remove, contentDescription = "Decrease quantity", tint = Gold)
                    }
                    Text("$qty", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    IconButton(onClick = { card.onIncrement?.invoke() }) {
                        Icon(Icons.Filled.Add, contentDescription = "Increase quantity", tint = Gold)
                    }
                } else {
                    InfoStat("QTY", "$qty")
                }
            }
        }
    }
}

/**
 * Every alternate printing of [cardName], as a horizontally scrollable strip. Fetched on demand
 * (Scryfall has no bulk-by-name-list endpoint cheap enough to do this for a whole result list up
 * front) and only shown once there's more than one printing to choose between. Tapping a printing
 * just previews it in the main image above (via [onPreview]) — it takes no action on its own until
 * confirmed via the checkmark, so browsing art never accidentally triggers add/move.
 */
@Composable
private fun AlternatePrintingsStrip(
    cardName: String,
    previewed: ScryfallCard?,
    onPreview: (ScryfallCard?) -> Unit,
    onConfirm: (ScryfallCard) -> Unit
) {
    val repository = remember { CardRepository() }
    val haptic = LocalHapticFeedback.current
    var prints by remember(cardName) { mutableStateOf<List<ScryfallCard>?>(null) }
    LaunchedEffect(cardName) {
        prints = try {
            repository.getPrintings(cardName)
        } catch (e: Exception) {
            emptyList()
        }
    }

    val current = prints
    if (current == null) {
        Box(
            modifier = Modifier.fillMaxWidth().background(Surface).padding(16.dp),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator(color = Gold, modifier = Modifier.size(20.dp)) }
    } else if (current.size > 1) {
        Column(modifier = Modifier.fillMaxWidth().background(Surface)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                current.forEach { print ->
                    val isPreviewed = previewed?.id == print.id
                    AsyncImage(
                        model = print.displayImageUrl,
                        contentDescription = print.printingLabel,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .width(64.dp)
                            .aspectRatio(0.72f)
                            .clip(RoundedCornerShape(6.dp))
                            .border(
                                BorderStroke(if (isPreviewed) 2.dp else 1.dp, if (isPreviewed) Gold else BorderColor),
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { onPreview(if (isPreviewed) null else print) }
                    )
                }
            }
            AnimatedVisibility(visible = previewed != null, enter = fadeIn(), exit = fadeOut()) {
                val chosen = previewed
                if (chosen != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Use ${chosen.printingLabel}?",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onPreview(null) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel preview", tint = TextMuted)
                        }
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onConfirm(chosen)
                        }) {
                            Icon(Icons.Filled.Check, contentDescription = "Confirm this printing", tint = Gold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourcesSection(sources: List<CardSource>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface)
            .padding(horizontal = 24.dp)
            .padding(bottom = 20.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderColor))
        Text(
            "IN ${sources.size} ${if (sources.size == 1) "PLACE" else "PLACES"}",
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
        )
        sources.forEach { source ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (source.kind == SourceKind.DECK) Icons.Filled.Style else Icons.Filled.Collections,
                    contentDescription = if (source.kind == SourceKind.DECK) "Deck" else "Binder",
                    tint = Gold,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    source.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 10.dp)
                )
                Text(
                    "×${source.quantity}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GoldLight
                )
            }
        }
    }
}

@Composable
private fun InfoStat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextMuted)
        Text(value, style = MaterialTheme.typography.titleMedium, color = GoldLight)
    }
}

/**
 * The "Find similar cards" action from any card's zoom overlay: resolves [cardName] on Scryfall,
 * looks up mechanically similar cards ([CardRepository.findSimilar] — same type/colors/mana value,
 * not synergy), and shows them in their own swipeable zoom overlay. [onAdd], when given, offers to
 * add a similar card (how that's handled — straight into a known deck/binder, or a destination
 * choice — is the caller's call). [onViewDetails], given the resolved [ScryfallCard], lets the
 * user go one level deeper into a similar card's own page.
 */
@Composable
fun SimilarCardsDialog(
    cardName: String,
    onDismiss: () -> Unit,
    onAdd: ((ScryfallCard) -> Unit)? = null,
    onViewDetails: ((ScryfallCard) -> Unit)? = null
) {
    val repository = remember { CardRepository() }
    var similar by remember(cardName) { mutableStateOf<List<ScryfallCard>?>(null) }
    LaunchedEffect(cardName) {
        similar = try {
            repository.findSimilar(repository.getByFuzzyName(cardName))
        } catch (e: Exception) {
            emptyList()
        }
    }
    val result = similar
    when {
        result == null -> Dialog(onDismissRequest = onDismiss) {
            Box(
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Surface).padding(32.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = Gold) }
        }
        result.isEmpty() -> AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = Surface,
            title = { Text("No similar cards found", color = GoldLight, style = MaterialTheme.typography.titleMedium) },
            text = { Text("Couldn't find anything similar to \"$cardName\".", style = MaterialTheme.typography.bodySmall, color = TextMuted) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK", color = Gold) } }
        )
        else -> CardZoomDialog(
            cards = result.map { similarCard ->
                ZoomCard(
                    imageUrl = similarCard.displayImageUrl,
                    cardName = similarCard.name,
                    priceUsd = similarCard.prices?.usd?.toDoubleOrNull(),
                    onAdd = onAdd?.let { add -> { add(similarCard) } },
                    onViewDetails = onViewDetails?.let { view -> { view(similarCard) } },
                    backImageUrl = similarCard.backImageUrl,
                    tags = similarCard.tags
                )
            },
            initialIndex = 0,
            onDismiss = onDismiss
        )
    }
}
