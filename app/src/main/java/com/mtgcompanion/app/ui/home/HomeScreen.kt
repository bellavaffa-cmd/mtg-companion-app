package com.mtgcompanion.app.ui.home

import com.mtgcompanion.app.ui.common.SyncIconButton
import com.mtgcompanion.app.ui.common.LocalLayoutSize
import com.mtgcompanion.app.ui.common.LayoutSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.NewsItem
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.common.ArtImage
import com.mtgcompanion.app.ui.common.CountUpText
import com.mtgcompanion.app.ui.common.IdentityStrip
import com.mtgcompanion.app.ui.common.SectionHeader
import com.mtgcompanion.app.ui.common.SharedKeys
import com.mtgcompanion.app.ui.common.StatFigure
import com.mtgcompanion.app.ui.common.foilShine
import com.mtgcompanion.app.ui.common.pressScale
import com.mtgcompanion.app.ui.common.riseIn
import com.mtgcompanion.app.ui.common.sharedArt
import com.mtgcompanion.app.ui.theme.BebasNumbers
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.EyebrowStyle
import com.mtgcompanion.app.ui.theme.LocalAppColors
import com.mtgcompanion.app.ui.theme.NumberStyle
import java.time.LocalTime

/**
 * Home as a dashboard: jump back into the last deck, glance at your numbers, flick through your
 * decks, and today's card. Navigation lives in the bottom bar, so nothing here repeats it.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenSearch: () -> Unit,
    onOpenCollection: () -> Unit,
    onOpenDecks: () -> Unit,
    onOpenScan: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenLifeCounter: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDeck: (String) -> Unit,
    onViewCard: (String) -> Unit
) {
    val deckCount by viewModel.deckCount.collectAsState()
    val binderCount by viewModel.binderCount.collectAsState()
    val collectionValue by viewModel.collectionValue.collectAsState()
    val lastOpenedDeck by viewModel.lastOpenedDeck.collectAsState()
    val decks by viewModel.decks.collectAsState()
    val deckColors by viewModel.deckColors.collectAsState()
    val matchSummary by viewModel.matchSummary.collectAsState()
    val cardOfDay by viewModel.cardOfDay.collectAsState()
    val alert by viewModel.alert.collectAsState()
    val news by viewModel.news.collectAsState()
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val greeting = remember {
        when (LocalTime.now().hour) {
            in 0..4 -> "Late night"
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    val layout = LocalLayoutSize.current
    val wide = layout.isWide
    val pad = layout.pagePadding
    val continueDeck = lastOpenedDeck ?: decks.firstOrNull()
    val statSize = if (layout == LayoutSize.DESKTOP) 48 else 32

    val statDecks: @Composable (Modifier) -> Unit = { m ->
        StatFigure(value = { CountUpText(deckCount.toDouble(), NumberStyle(statSize), colors.textPrimary) }, label = "Decks", modifier = m.clickable(onClick = onOpenDecks))
    }
    val statBinders: @Composable (Modifier) -> Unit = { m ->
        StatFigure(value = { CountUpText(binderCount.toDouble(), NumberStyle(statSize), colors.textPrimary) }, label = "Binders", modifier = m.clickable(onClick = onOpenCollection))
    }
    val statValue: @Composable (Modifier) -> Unit = { m ->
        StatFigure(
            value = {
                val v = collectionValue
                if (v != null) CountUpText(v, NumberStyle(statSize), colors.textPrimary, format = { "$" + "%,.0f".format(it) })
                else Text("—", style = NumberStyle(statSize), color = colors.textDim)
            },
            label = "Collection value",
            modifier = m.clickable(onClick = onOpenCollection)
        )
    }
    val statRecord: @Composable (Modifier) -> Unit = { m ->
        StatFigure(
            value = {
                if (matchSummary.total > 0) {
                    Text("${matchSummary.wins}–${matchSummary.losses}" + if (matchSummary.draws > 0) "–${matchSummary.draws}" else "", style = NumberStyle(statSize), color = colors.textPrimary)
                } else {
                    Text("—", style = NumberStyle(statSize), color = colors.textDim)
                }
            },
            label = if (matchSummary.total > 0) "Match record · ${matchSummary.wins * 100 / matchSummary.total}% wins" else "Match record",
            modifier = m
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState())
            .padding(bottom = if (wide) 40.dp else 24.dp),
        verticalArrangement = Arrangement.spacedBy(if (wide) 18.dp else 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = if (wide) pad else 20.dp, end = if (wide) pad else 12.dp, top = if (wide) 26.dp else 18.dp).riseIn(0)
        ) {
            Column(Modifier.weight(1f)) {
                Text(greeting.uppercase(), style = EyebrowStyle, color = colors.textMuted)
                Text(
                    if (layout == LayoutSize.DESKTOP) "Welcome back" else "MTG Companion",
                    style = if (wide) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall
                )
            }
            SyncIconButton(filled = true)
            // Wide layouts reach Settings from the rail or sidebar.
            if (!wide) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(colors.surface).clickable(onClick = onOpenSettings),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = colors.textPrimary, modifier = Modifier.size(21.dp))
                }
            }
        }

        alert?.let { message ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .padding(horizontal = pad)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.warning.copy(alpha = 0.14f))
                    .clickable(onClick = onOpenSettings)
                    .padding(14.dp)
                    .riseIn(1)
            ) {
                Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = colors.warning, modifier = Modifier.size(20.dp))
                Text(message, style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
            }
        }

        if (layout == LayoutSize.DESKTOP) {
            // The hero beside a 2×2 block of figures.
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(horizontal = pad).fillMaxWidth().height(320.dp).riseIn(1)
            ) {
                if (continueDeck != null) {
                    ContinueHero(
                        deck = continueDeck,
                        colors = deckColors[continueDeck.id].orEmpty(),
                        isLast = lastOpenedDeck != null,
                        onClick = { onOpenDeck(continueDeck.id) },
                        modifier = Modifier.weight(2f).fillMaxHeight()
                    )
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        statDecks(Modifier.weight(1f).fillMaxHeight())
                        statBinders(Modifier.weight(1f).fillMaxHeight())
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        statValue(Modifier.weight(1f).fillMaxHeight())
                        statRecord(Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        } else {
            continueDeck?.let { deck ->
                ContinueHero(
                    deck = deck,
                    colors = deckColors[deck.id].orEmpty(),
                    isLast = lastOpenedDeck != null,
                    onClick = { onOpenDeck(deck.id) },
                    modifier = Modifier.padding(horizontal = pad).height(if (wide) 280.dp else 232.dp).riseIn(1)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(if (wide) 12.dp else 8.dp), modifier = Modifier.padding(horizontal = pad).riseIn(2)) {
                statDecks(Modifier.weight(1f))
                statBinders(Modifier.weight(1f))
                statValue(Modifier.weight(1.25f))
                if (wide) statRecord(Modifier.weight(1.25f))
            }
            if (!wide && matchSummary.total > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).padding(14.dp).riseIn(3)
                ) {
                    Text(
                        "${matchSummary.wins}–${matchSummary.losses}" + if (matchSummary.draws > 0) "–${matchSummary.draws}" else "",
                        style = NumberStyle(30),
                        color = colors.textPrimary
                    )
                    Column(Modifier.weight(1f)) {
                        Text("Match record", style = MaterialTheme.typography.titleSmall)
                        Text("${matchSummary.wins * 100 / matchSummary.total}% win rate across all decks", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (decks.isNotEmpty()) {
            SectionHeader(
                "Your decks",
                action = "See all",
                onAction = onOpenDecks,
                modifier = Modifier.padding(start = if (wide) pad else 20.dp, end = if (wide) pad - 10.dp else 10.dp, top = 12.dp).riseIn(3)
            )
            if (wide) {
                // A grid of tiles filling the width instead of a sideways-scrolling row.
                BoxWithConstraints(Modifier.padding(horizontal = pad).fillMaxWidth().riseIn(4)) {
                    val columns = if (layout == LayoutSize.TABLET) 3 else (maxWidth / 180.dp).toInt().coerceIn(4, 8)
                    val rows = if (layout == LayoutSize.TABLET) 2 else 1
                    val shown: List<Deck?> = (decks + listOf<Deck?>(null)).take(columns * rows)
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        shown.chunked(columns).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { deck ->
                                    val tileModifier = Modifier.weight(1f).aspectRatio(0.8f)
                                    if (deck != null) {
                                        MiniDeckTile(deck, deckColors[deck.id].orEmpty(), onClick = { onOpenDeck(deck.id) }, shareArt = deck.id != continueDeck?.id, modifier = tileModifier)
                                    } else {
                                        NewDeckTile(onClick = onOpenDecks, modifier = tileModifier)
                                    }
                                }
                                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.riseIn(4)
                ) {
                    items(decks, key = { it.id }) { deck ->
                        MiniDeckTile(deck, deckColors[deck.id].orEmpty(), onClick = { onOpenDeck(deck.id) }, shareArt = deck.id != continueDeck?.id)
                    }
                    item(key = "new") { NewDeckTile(onClick = onOpenDecks) }
                }
            }
        }

        if (wide) {
            // Card of the day beside the table tools.
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
                modifier = Modifier.padding(horizontal = pad).fillMaxWidth().riseIn(5)
            ) {
                Column(Modifier.weight(7f)) {
                    cardOfDay?.let { card ->
                        SectionHeader("Card of the day", modifier = Modifier.padding(bottom = 10.dp))
                        CardOfDayTile(card, onClick = { onViewCard(card.name) })
                    }
                }
                Column(Modifier.weight(5f)) {
                    SectionHeader("At the table", modifier = Modifier.padding(bottom = 10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LifeCounterTile(onClick = onOpenLifeCounter, modifier = Modifier.weight(1f))
                        RulesTile(onClick = onOpenRules, modifier = Modifier.weight(1f))
                    }
                }
            }
        } else {
            cardOfDay?.let { card ->
                SectionHeader("Card of the day", modifier = Modifier.padding(start = 20.dp, end = 10.dp, top = 12.dp).riseIn(5))
                CardOfDayTile(card, onClick = { onViewCard(card.name) }, modifier = Modifier.padding(horizontal = 16.dp).riseIn(5))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).riseIn(6)) {
                LifeCounterTile(onClick = onOpenLifeCounter, modifier = Modifier.weight(1f))
                RulesTile(onClick = onOpenRules, modifier = Modifier.weight(1f))
            }
        }

        if (news.isNotEmpty()) {
            SectionHeader("Latest news", modifier = Modifier.padding(start = if (wide) pad else 20.dp, end = 10.dp, top = 12.dp).riseIn(7))
            NewsList(news, modifier = Modifier.padding(horizontal = pad).riseIn(7), onOpenArticle = { url ->
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            })
        }
    }
}

@Composable
private fun ContinueHero(deck: Deck, colors: List<String>, isLast: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val app = LocalAppColors.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(interaction)
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        ArtImage(
            model = deck.commander?.imageUrl.toArtCropUrl(),
            seed = deck.name,
            colors = colors,
            contentDescription = deck.commander?.name,
            modifier = Modifier.fillMaxSize().sharedArt(SharedKeys.deckArt(deck.id), RoundedCornerShape(26.dp))
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.45f to Color.Black.copy(alpha = 0.25f), 1f to Color.Black.copy(alpha = 0.92f))))
        Column(Modifier.align(Alignment.BottomStart).padding(start = 18.dp, end = 76.dp, bottom = 18.dp)) {
            Text((if (isLast) "Continue building" else "Your deck").uppercase(), style = EyebrowStyle, color = Color.White.copy(alpha = 0.75f))
            Text(deck.name, style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(deck.commander?.name, "${deck.cards.sumOf { it.quantity }} cards", deck.considering.size.takeIf { it > 0 }?.let { "$it considering" }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            IdentityStrip(colors, modifier = Modifier.width(96.dp))
        }
        Box(
            Modifier.align(Alignment.BottomEnd).padding(16.dp).size(46.dp).clip(CircleShape).background(app.accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = app.onAccent)
        }
    }
}

@Composable
private fun MiniDeckTile(deck: Deck, colors: List<String>, onClick: () -> Unit, shareArt: Boolean, modifier: Modifier = Modifier.size(width = 136.dp, height = 172.dp)) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(interaction)
            .clip(RoundedCornerShape(20.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        ArtImage(
            model = deck.commander?.imageUrl.toArtCropUrl(),
            seed = deck.name,
            colors = colors,
            modifier = Modifier.fillMaxSize().let { if (shareArt) it.sharedArt(SharedKeys.deckArt(deck.id), RoundedCornerShape(20.dp)) else it }
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.92f))))
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(deck.name, style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, lineHeight = 17.sp), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            IdentityStrip(colors, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun NewDeckTile(onClick: () -> Unit, modifier: Modifier = Modifier.size(width = 136.dp, height = 172.dp)) {
    val app = LocalAppColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(app.surface)
            .clickable(onClick = onClick)
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(app.surface3), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = app.textPrimary)
        }
        Spacer(Modifier.height(8.dp))
        Text("New deck", style = MaterialTheme.typography.labelLarge, color = app.textMuted)
    }
}

@Composable
private fun CardOfDayTile(card: CardOfDay, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val app = LocalAppColors.current
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .pressScale(interaction)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(app.surface)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(14.dp)
    ) {
        AsyncImage(
            model = card.imageUrl,
            contentDescription = card.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .width(104.dp)
                .aspectRatio(0.716f)
                .sharedArt(SharedKeys.cardArt(card.name), RoundedCornerShape(7.dp))
                .clip(RoundedCornerShape(7.dp))
                .foilShine()
        )
        Column(Modifier.weight(1f)) {
            Text("TODAY'S PICK", style = EyebrowStyle, color = app.accent)
            Text(card.name, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Prices, printings and rulings", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = app.textDim)
            }
        }
    }
}

/** The life counter's own colour blocks, so the shortcut looks like the thing it opens. */
@Composable
private fun LifeCounterTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val app = LocalAppColors.current
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .pressScale(interaction)
            .clip(RoundedCornerShape(22.dp))
            .background(app.surface)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val seats = listOf(Color(0xFFFFC400), Color(0xFFFF1F4B), Color(0xFFF58FF7), Color(0xFF4A5BFF))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(0, 2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(row, row + 1).forEach { i ->
                        Box(
                            Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(9.dp)).background(seats[i]),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("40", fontFamily = BebasNumbers, fontSize = 19.sp, color = Color(0xFF0A0A0A), modifier = Modifier.graphicsLayer { rotationZ = if (row == 0) 180f else 0f })
                        }
                    }
                }
            }
        }
        Column {
            Text("Life counter", style = MaterialTheme.typography.titleSmall)
            Text("Up to 8 players", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RulesTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val app = LocalAppColors.current
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .pressScale(interaction)
            .clip(RoundedCornerShape(22.dp))
            .background(app.surface)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(12.dp)).background(app.accentGlow),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.MenuBook, contentDescription = null, tint = app.accent, modifier = Modifier.size(34.dp))
        }
        Column {
            Text("Rules", style = MaterialTheme.typography.titleSmall)
            Text("Keywords and rulings", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun NewsList(items: List<NewsItem>, modifier: Modifier = Modifier, onOpenArticle: (String) -> Unit) {
    val app = LocalAppColors.current
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(app.surface)) {
        val shown = items.take(5)
        shown.forEachIndexed { index, item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().clickable { onOpenArticle(item.link) }.padding(horizontal = 16.dp, vertical = 13.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(item.source, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 2.dp))
                }
                Icon(Icons.Filled.OpenInNew, contentDescription = "Open article", tint = app.textDim, modifier = Modifier.size(16.dp))
            }
            if (index < shown.size - 1) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(app.border))
            }
        }
    }
}
