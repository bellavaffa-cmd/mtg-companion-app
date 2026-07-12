package com.mtgcompanion.app.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary

/** A place a card is held — a binder or a deck — and how many copies are there. */
enum class SourceKind { BINDER, DECK }

data class CardSource(val kind: SourceKind, val name: String, val quantity: Int)

/**
 * One card in the enlarged-card overlay. [quantity] null hides the quantity/total row (e.g. for a
 * suggested card that isn't owned); providing [onIncrement]/[onDecrement] turns the count into an
 * editable stepper. [sources], when set, lists the binders/decks the card is in.
 */
data class ZoomCard(
    val imageUrl: String?,
    val priceUsd: Double? = null,
    val quantity: Int? = null,
    val onIncrement: (() -> Unit)? = null,
    val onDecrement: (() -> Unit)? = null,
    val onChangeArt: (() -> Unit)? = null,
    val onMove: (() -> Unit)? = null,
    val sources: List<CardSource> = emptyList()
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
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f))) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val card = cards[page]
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
                            model = card.imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .padding(horizontal = 24.dp, vertical = 16.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                    }
                    if (card.priceUsd != null || card.quantity != null || card.onChangeArt != null) {
                        CardInfoBar(card)
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
private fun CardInfoBar(card: ZoomCard) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            card.priceUsd?.let { price ->
                InfoStat("VALUE", "$" + "%,.2f".format(price))
            }
            if (card.priceUsd != null && card.quantity != null) {
                InfoStat("TOTAL", "$" + "%,.2f".format(card.priceUsd * card.quantity))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            card.onMove?.let { move ->
                IconButton(onClick = move) {
                    Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Move card", tint = Gold)
                }
            }
            card.onChangeArt?.let { changeArt ->
                IconButton(onClick = changeArt) {
                    Icon(Icons.Filled.Image, contentDescription = "Change art", tint = Gold)
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
