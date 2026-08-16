package com.mtgcompanion.app.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.NewsItem
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.common.AnimatedUsdText
import com.mtgcompanion.app.ui.common.elevatedCard
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.TextDim
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class)
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
    val matchSummary by viewModel.matchSummary.collectAsState()
    val cardOfDay by viewModel.cardOfDay.collectAsState()
    val alert by viewModel.alert.collectAsState()
    val news by viewModel.news.collectAsState()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        containerColor = Bg,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("MTG COMPANION", style = MaterialTheme.typography.labelLarge, color = GoldLight) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Gold)
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = Bg, scrolledContainerColor = Surface),
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().background(Bg).padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Search cards, track your collection, and build decks — all in one place.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )

            alert?.let { message ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .elevatedCard(borderColor = Gold.copy(alpha = 0.5f))
                        .padding(12.dp)
                ) {
                    Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = Gold, modifier = Modifier.size(20.dp))
                    Text(message, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("DECKS", "$deckCount", Modifier.weight(1f))
                StatCard("BINDERS", "$binderCount", Modifier.weight(1f))
                ValueStatCard(collectionValue, Modifier.weight(1f))
            }

            if (matchSummary.total > 0) {
                MatchSummaryCard(matchSummary)
            }

            lastOpenedDeck?.let { deck ->
                ContinueDeckTile(deck, onClick = { onOpenDeck(deck.id) })
            }

            cardOfDay?.let { card ->
                CardOfDayTile(card, onClick = { onViewCard(card.name) })
            }

            HomeTile(Icons.Filled.Search, "Search", "Find any card on Scryfall", onOpenSearch)
            HomeTile(Icons.Filled.Collections, "Collection", "Your owned cards and binders", onOpenCollection)
            HomeTile(Icons.Filled.Style, "Decks", "Build, analyze, and check legality", onOpenDecks, iconSize = 34.dp)
            HomeTile(Icons.Filled.CameraAlt, "Scan", "Add cards with your camera", onOpenScan)
            HomeTile(Icons.Filled.MenuBook, "Rules", "Keyword glossary and card rulings", onOpenRules)
            HomeTile(Icons.Filled.Favorite, "Life Counter", "Track life totals at the table", onOpenLifeCounter)

            if (news.isNotEmpty()) {
                NewsSection(news, onOpenArticle = { url ->
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                })
            }
        }
    }
}

@Composable
private fun NewsSection(items: List<NewsItem>, onOpenArticle: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .elevatedCard()
            .padding(vertical = 4.dp)
    ) {
        Text(
            "LATEST MTG NEWS",
            style = MaterialTheme.typography.labelMedium,
            color = TextDim,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
        )
        items.take(6).forEachIndexed { index, item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenArticle(item.link) }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(item.source, style = MaterialTheme.typography.labelMedium, color = TextMuted)
                }
                Icon(Icons.Filled.OpenInNew, contentDescription = "Open article", tint = TextDim, modifier = Modifier.size(16.dp))
            }
            if (index < items.take(6).size - 1) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(BorderColor))
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .elevatedCard()
            .padding(16.dp)
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = GoldLight)
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextMuted)
    }
}

@Composable
private fun ValueStatCard(value: Double?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .elevatedCard()
            .padding(16.dp)
    ) {
        if (value != null) {
            AnimatedUsdText(value, style = MaterialTheme.typography.titleLarge, color = GoldLight)
        } else {
            Text("—", style = MaterialTheme.typography.titleLarge, color = GoldLight)
        }
        Text("VALUE", style = MaterialTheme.typography.labelMedium, color = TextMuted)
    }
}

@Composable
private fun MatchSummaryCard(summary: MatchSummary) {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .elevatedCard()
            .padding(16.dp)
    ) {
        Text(
            "${summary.wins}-${summary.losses}" + if (summary.draws > 0) "-${summary.draws}" else "",
            style = MaterialTheme.typography.titleMedium,
            color = GoldLight
        )
        Text(
            "record across all decks (${(summary.wins * 100 / summary.total)}% win rate)",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
    }
}

@Composable
private fun ContinueDeckTile(deck: Deck, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .elevatedCard(borderColor = Gold.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        AsyncImage(
            model = deck.commander?.imageUrl.toArtCropUrl(),
            contentDescription = deck.commander?.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 64.dp, height = 46.dp).clip(RoundedCornerShape(10.dp))
        )
        Column(modifier = Modifier.weight(1f)) {
            Text("CONTINUE", style = MaterialTheme.typography.labelMedium, color = TextDim)
            Text(deck.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextDim)
    }
}

@Composable
private fun CardOfDayTile(card: CardOfDay, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .elevatedCard()
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        AsyncImage(
            model = card.imageUrl,
            contentDescription = card.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.width(44.dp).aspectRatio(0.72f).clip(RoundedCornerShape(10.dp))
        )
        Column(modifier = Modifier.weight(1f)) {
            Text("CARD OF THE DAY", style = MaterialTheme.typography.labelMedium, color = TextDim)
            Text(card.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextDim)
    }
}

@Composable
private fun HomeTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    iconSize: Dp = 26.dp
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .elevatedCard()
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Gold, modifier = Modifier.size(iconSize))
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        Box(modifier = Modifier.padding(start = 8.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextDim)
        }
    }
}
