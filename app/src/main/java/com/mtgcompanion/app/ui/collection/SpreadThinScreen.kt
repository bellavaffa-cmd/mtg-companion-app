package com.mtgcompanion.app.ui.collection

import android.widget.Toast
import com.mtgcompanion.app.data.picksFromThin
import com.mtgcompanion.app.ui.decks.ProxyPrintDialog
import com.mtgcompanion.app.ui.common.a11yHeading
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.BuyLine
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.Money
import com.mtgcompanion.app.data.ThinCard
import com.mtgcompanion.app.data.buyListUrl
import com.mtgcompanion.app.data.shortBuyList
import com.mtgcompanion.app.data.shortCost
import com.mtgcompanion.app.data.spreadThin
import com.mtgcompanion.app.data.thinLine
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.common.openUrl
import com.mtgcompanion.app.ui.common.rememberMoney
import com.mtgcompanion.app.ui.theme.LocalAppColors
import com.mtgcompanion.app.ui.theme.NumberStyle

/**
 * Cards spread too thin: what the user's decks between them use more copies of than they own,
 * shortest first, with the decks using each, what the missing copies cost and a list to buy them
 * from. Mirrors the web app's src/pages/SpreadThinPage.tsx.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpreadThinScreen(
    collections: List<Collection>,
    decks: List<Deck>,
    onBack: () -> Unit,
    onOpenDeck: (String) -> Unit
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val money = rememberMoney()
    val cards = remember(collections, decks) { spreadThin(collections, decks) }
    val ids = remember(cards) { cards.flatMap { it.scryfallIds }.distinct().sorted() }
    // scryfallId → non-foil price in US dollars; null until they've loaded.
    var prices by remember { mutableStateOf<Map<String, Double?>?>(null) }
    var printing by remember { mutableStateOf(false) }
    LaunchedEffect(ids) {
        if (ids.isEmpty()) { prices = emptyMap(); return@LaunchedEffect }
        prices = runCatching { CardRepository().getCardsByIds(ids).associate { it.id to it.prices?.usd?.toDoubleOrNull() } }.getOrDefault(emptyMap())
    }
    val p = prices
    val costs = cards.map { if (p == null) null else shortCost(it, p) }
    val total = costs.sumOf { it ?: 0.0 }
    val unpriced = costs.count { it == null }

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("Spread thin", style = MaterialTheme.typography.titleLarge, modifier = Modifier.a11yHeading()) },
                navigationIcon = { BackButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Cards your decks use more copies of than you own, so they're moved from deck to deck. Copies in your binders " +
                        "and in the decks you hold count as owned; any printing will do, and basic lands are left out.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted
                )
            }
            if (cards.isEmpty()) {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp)) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = colors.textDim, modifier = Modifier.size(40.dp))
                        Text(
                            "Nothing is spread thin. Every deck can have its own copy of each card.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }
                return@LazyColumn
            }
            item {
                val copies = cards.sumOf { it.short }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Figure(cards.size.toString(), if (cards.size == 1) "Card short" else "Cards short", Modifier.weight(1f))
                    Figure(copies.toString(), if (copies == 1) "Copy to buy" else "Copies to buy", Modifier.weight(1f))
                    Figure(if (p == null) "—" else money.format(total, whole = money.toLocal(total) >= 100), "To buy them", Modifier.weight(1f))
                }
                if (p != null && unpriced > 0) {
                    Text(
                        "$unpriced ${if (unpriced == 1) "card has" else "cards have"} no price, so ${if (unpriced == 1) "isn't" else "aren't"} in the total.",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textDim,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            clipboard.setText(AnnotatedString(shortBuyList(cards)))
                            Toast.makeText(context, "Buy list copied", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Copy buy list")
                    }
                    OutlinedButton(onClick = { buyListUrl(cards.map { BuyLine(it.name, it.short) })?.let { openUrl(context, it) } }) {
                        Icon(Icons.Filled.ShoppingCart, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Buy at TCGplayer", color = colors.accent)
                    }
                }
            }
            item {
                OutlinedButton(onClick = { printing = true }) {
                    Icon(Icons.Filled.Print, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Print proxies", color = colors.accent)
                }
            }
            items(cards, key = { it.name }) { card ->
                ThinRow(card, cost = if (p == null) null else shortCost(card, p), loaded = p != null, money = money, onOpenDeck = onOpenDeck)
            }
            item {
                Text(
                    "Prices are TCGplayer's market price for the cheapest printing your decks play" +
                        (if (money.isUsd) "" else ", in ${money.currency.code} at today's exchange rate") + ".",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textDim,
                    modifier = Modifier.padding(top = 6.dp, bottom = 24.dp)
                )
            }
        }
    }
    if (printing) ProxyPrintDialog(title = "Spread thin", initial = picksFromThin(cards), onDismiss = { printing = false })
}

@Composable
private fun Figure(value: String, label: String, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(value, style = NumberStyle(28), color = colors.textPrimary, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThinRow(card: ThinCard, cost: Double?, loaded: Boolean, money: Money, onOpenDeck: (String) -> Unit) {
    val colors = LocalAppColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).padding(10.dp)
    ) {
        Box(Modifier.size(width = 56.dp, height = 44.dp).clip(RoundedCornerShape(11.dp)).background(colors.surface2)) {
            AsyncImage(
                model = card.imageUrl.toArtCropUrl(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Column(Modifier.weight(1f)) {
            Text(card.name, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(thinLine(card), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 6.dp)
            ) {
                card.decks.forEach { use ->
                    Text(
                        use.deckName + if (use.copies > 1) " ×${use.copies}" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(colors.surface2)
                            .clickable { onOpenDeck(use.deckId) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${card.short} short", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.error)
            if (loaded) {
                Text(cost?.let { money.format(it) } ?: "No price", style = MaterialTheme.typography.bodySmall, color = colors.textDim)
            }
        }
    }
}
