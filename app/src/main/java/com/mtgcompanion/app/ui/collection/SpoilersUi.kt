package com.mtgcompanion.app.ui.collection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.DeckMatch
import com.mtgcompanion.app.data.NewSetsStore
import com.mtgcompanion.app.data.PackCard
import com.mtgcompanion.app.data.SetCard
import com.mtgcompanion.app.data.SetInfo
import com.mtgcompanion.app.data.openingPacks
import com.mtgcompanion.app.data.releaseCountdown
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.common.EmptyAction
import com.mtgcompanion.app.ui.common.EmptyPrompt
import com.mtgcompanion.app.ui.common.a11yHeading
import com.mtgcompanion.app.ui.common.rememberMoney
import com.mtgcompanion.app.ui.theme.LocalAppColors

// Spoiler season (data/Spoilers.kt): a set's revealed cards as a gallery — each to want before
// release, with the decks it could go in (a tap puts it on that deck's Considering list) — and the
// Opening packs list for prerelease night. Part of New sets (NewSetsScreen.kt). The web app's
// collection/SpoilersUi.tsx.

/** The price once it's out, or the countdown before. */
@Composable
private fun priceOrCountdown(card: SetCard, set: SetInfo, today: String): String {
    val money = rememberMoney()
    val out = (card.releasedAt ?: set.releasedAt).orEmpty() <= today
    val usd = card.usd?.toDoubleOrNull()
    return when {
        out && usd != null -> money.format(usd)
        out -> "No price yet"
        else -> releaseCountdown(card.releasedAt ?: set.releasedAt, today) ?: "No price yet"
    }
}

/**
 * One revealed card: its image (a tap opens it), the price or the countdown, how many are wanted
 * ([want], changed with [onWant]), and the decks it fits ([fits]) — [considering]: the deck ids
 * already considering it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpoilerTile(
    card: SetCard,
    set: SetInfo,
    today: String,
    want: Int,
    fits: List<DeckMatch>,
    considering: Set<String>,
    onOpen: () -> Unit,
    onWant: (Int) -> Unit,
    onConsider: (DeckMatch) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(6.dp)
    ) {
        AsyncImage(
            model = card.imageUrl,
            contentDescription = card.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().aspectRatio(0.716f).clip(RoundedCornerShape(10.dp)).background(colors.surface3)
                .clickable(role = Role.Button, onClick = onOpen)
        )
        Text(card.name, style = MaterialTheme.typography.labelLarge, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(priceOrCountdown(card, set, today), style = MaterialTheme.typography.labelMedium, color = colors.textMuted, maxLines = 1)
        if (want <= 0) {
            OutlinedButton(onClick = { onWant(1) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(4.dp))
                Text("Want")
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { onWant(want - 1) }) { Icon(Icons.Filled.Remove, contentDescription = "Want one fewer", tint = colors.accent) }
                Text("Want $want", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = colors.accent, modifier = Modifier.weight(1f))
                IconButton(onClick = { onWant(want + 1) }) { Icon(Icons.Filled.Add, contentDescription = "Want one more", tint = colors.accent) }
            }
        }
        if (fits.isNotEmpty()) {
            Text("Fits", style = MaterialTheme.typography.labelSmall, color = colors.textDim)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                fits.forEach { m -> DeckChip(m, m.deckId in considering) { onConsider(m) } }
            }
        }
    }
}

/** A deck the card fits: a tap puts the card on its Considering list (a tick once it's there). */
@Composable
private fun DeckChip(match: DeckMatch, on: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.clip(RoundedCornerShape(50))
            .border(BorderStroke(1.dp, if (on) colors.accent else colors.textDim), RoundedCornerShape(50))
            .clickable(enabled = !on, role = Role.Button, onClickLabel = "Consider for ${match.deckName}", onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(if (on) Icons.Filled.Check else Icons.Filled.Add, contentDescription = null, tint = colors.accent, modifier = Modifier.size(12.dp))
        Text(match.deckName, style = MaterialTheme.typography.labelSmall, color = if (on) colors.accent else colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Opening packs: the wanted cards from the set, to tick off as they come out of the packs — each
 * tick puts a copy in the Unsorted pile and wants one fewer ([onPulled], with foil or not).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpeningPacksScreen(
    code: String,
    collections: List<Collection>,
    onBack: () -> Unit,
    onOpenCard: (String) -> Unit,
    onPulled: (PackCard, Boolean) -> Unit
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    var set by remember { mutableStateOf<SetInfo?>(null) }
    var cards by remember { mutableStateOf<List<SetCard>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    val pulled = remember { mutableStateListOf<String>() }
    LaunchedEffect(code, attempt) {
        failed = false
        try {
            set = NewSetsStore.releaseSets(context).firstOrNull { it.code == code.lowercase() }
            cards = NewSetsStore.setCards(code.lowercase())
        } catch (e: Exception) {
            failed = true
        }
    }
    val list = remember(collections, cards) { cards?.let { openingPacks(collections, it) } }

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("Opening packs", style = MaterialTheme.typography.titleLarge, modifier = Modifier.a11yHeading()) },
                navigationIcon = { BackButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        }
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            when {
                failed && cards == null -> EmptyPrompt(
                    Icons.Filled.CloudOff, "Couldn't reach Scryfall for this set's cards.",
                    actions = listOf(EmptyAction("Try again", Icons.Filled.Refresh) { attempt++ })
                )
                list == null -> Text("Looking through the set's cards…", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                else -> {
                    Text(
                        "${set?.name ?: code.uppercase()}: the cards you want from it. Tick one off as it comes out of a pack — it goes into your Unsorted pile and you want one fewer.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.textMuted
                    )
                    if (list.isEmpty()) EmptyPrompt(Icons.Filled.Inventory2, "Nothing wanted from this set yet. Mark cards Want on the set's page.")
                    list.forEach { p -> PackRow(p, onOpen = { onOpenCard(p.card.name) }) { foil -> onPulled(p, foil); pulled += p.card.name } }
                    if (pulled.isNotEmpty()) {
                        Text("Pulled so far", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.textPrimary, modifier = Modifier.padding(top = 12.dp).a11yHeading())
                        Text(
                            pulled.groupingBy { it }.eachCount().entries.joinToString(" · ") { (n, c) -> if (c > 1) "$n ×$c" else n },
                            style = MaterialTheme.typography.bodyMedium, color = colors.textMuted
                        )
                    }
                }
            }
            Box(Modifier.padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun PackRow(p: PackCard, onOpen: () -> Unit, onPulled: (Boolean) -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(8.dp)
    ) {
        AsyncImage(
            model = p.card.imageUrl.toArtCropUrl(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 52.dp, height = 38.dp).clip(RoundedCornerShape(8.dp)).background(colors.surface3).clickable(onClick = onOpen)
        )
        Column(Modifier.weight(1f)) {
            Text(p.entry.name, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${p.entry.quantity} wanted", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
        }
        TextButton(onClick = { onPulled(true) }) { Text("Foil") }
        OutlinedButton(onClick = { onPulled(false) }) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(4.dp))
            Text("Pulled")
        }
    }
}
