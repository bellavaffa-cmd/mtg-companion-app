package com.mtgcompanion.app.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionRepository
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.data.social.TradeCard
import com.mtgcompanion.app.data.social.forTradeLines
import com.mtgcompanion.app.data.social.forTradeOf
import com.mtgcompanion.app.data.social.forTradePicks
import com.mtgcompanion.app.data.social.setForTrade
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.common.SectionHeader
import com.mtgcompanion.app.ui.common.readableWidth
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

// The user's cards for trade: copies in their own binders they offer to friends. A binder card's
// forTrade count, so it syncs with the binder; friends see the list on the user's profile and in
// trade matches. The web app's twin is src/pages/ForTradePage.tsx.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForTradeScreen(social: SocialRepository, collectionRepository: CollectionRepository, onBack: () -> Unit, onSignIn: () -> Unit) {
    val colors = LocalAppColors.current
    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("Cards for trade", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { BackButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.readableWidth(680.dp)) {
                SocialGate(social, onSignIn) { ForTradeList(social, collectionRepository) }
            }
        }
    }
}

@Composable
private fun ForTradeList(social: SocialRepository, collectionRepository: CollectionRepository) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val available = rememberSocialMore(social)
    val collections by collectionRepository.collectionsFlow.collectAsState(initial = emptyList())
    var picking by remember { mutableStateOf(false) }
    // Counts when the picker opened, so closing it can tell friends' feeds what's newly for trade.
    var before by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    val lines = forTradeLines(collections)
    val owned = collections.filter { it.type != "WISHLIST" }

    fun set(collectionId: String, scryfallId: String, n: Int) {
        scope.launch { collectionRepository.changeStorage { setForTrade(it, collectionId, scryfallId, n) } }
    }
    /** The picker changed one binder's picks: each card's new count is its picks added up. */
    fun picked(binder: Collection, next: List<TradeCard>) {
        val totals = next.groupBy { it.scryfallId }.mapValues { (_, cards) -> cards.sumOf { it.quantity } }
        scope.launch {
            collectionRepository.changeStorage { cols ->
                val current = cols.firstOrNull { it.id == binder.id } ?: return@changeStorage cols
                current.entries.fold(cols) { acc, e ->
                    val want = totals[e.scryfallId] ?: 0
                    if (want == forTradeOf(e)) acc else setForTrade(acc, binder.id, e.scryfallId, want)
                }
            }
        }
    }
    fun closePicker() {
        picking = false
        val added = forTradeLines(collections).filter { it.count > (before["${it.collectionId}:${it.entry.scryfallId}"] ?: 0) }
        if (available == true) social.noteForTradeInBackground(added.map { it.entry.name to it.entry.imageUrl })
    }

    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(
                "Mark copies you'd trade away. All your friends can see this list — even from binders you don't share — and it shows in trade matches." +
                    if (available == false) " Friends will see it once this is ready on the server." else "",
                style = MaterialTheme.typography.bodyMedium, color = colors.textMuted
            )
        }
        item {
            GoldButton(
                "Choose cards from your binders",
                {
                    before = lines.associate { "${it.collectionId}:${it.entry.scryfallId}" to it.count }
                    picking = true
                },
                enabled = owned.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                icon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }
        val total = lines.sumOf { it.count }
        item { SectionHeader(if (total > 0) "For trade · $total" else "For trade") }
        if (lines.isEmpty()) item { Notice("Nothing marked for trade yet.") }
        lines.forEach { l ->
            item(key = "${l.collectionId}:${l.entry.scryfallId}") {
                val max = l.entry.quantity + l.entry.foilQuantity
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).padding(8.dp)
                ) {
                    AsyncImage(
                        model = l.entry.imageUrl.toArtCropUrl(), contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(width = 56.dp, height = 44.dp).clip(RoundedCornerShape(11.dp)).background(colors.surface2)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(l.entry.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${l.binder} · ${l.count} of $max", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    }
                    IconButton(onClick = { set(l.collectionId, l.entry.scryfallId, l.count - 1) }) {
                        Text("−", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    }
                    Text("${l.count}", style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = { set(l.collectionId, l.entry.scryfallId, l.count + 1) }, enabled = l.count < max) {
                        Text("+", style = MaterialTheme.typography.titleMedium, color = if (l.count < max) colors.textPrimary else colors.textDim)
                    }
                }
            }
        }
    }

    if (picking) {
        Dialog(onDismissRequest = { closePicker() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(Modifier.fillMaxSize().background(colors.bg)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Your binders", style = MaterialTheme.typography.titleLarge)
                        Text("How many of each you'd trade", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    }
                    GoldButton("Done", { closePicker() })
                }
                LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    owned.forEach { b ->
                        item(key = "h-${b.id}") { Text(b.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp)) }
                        if (b.entries.isEmpty()) item(key = "e-${b.id}") { Text("This binder is empty.", style = MaterialTheme.typography.bodySmall, color = colors.textDim) }
                        binderPicker(b.id, b.entries.sortedBy { it.name }, forTradePicks(b)) { next -> picked(b, next) }
                    }
                }
            }
        }
    }
}
