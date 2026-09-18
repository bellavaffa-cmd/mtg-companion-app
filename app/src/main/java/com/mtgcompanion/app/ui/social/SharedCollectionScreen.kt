package com.mtgcompanion.app.ui.social

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionType
import com.mtgcompanion.app.data.localMoshi
import com.mtgcompanion.app.data.social.SharedCollection
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.ui.common.CardZoomDialog
import com.mtgcompanion.app.ui.common.SectionHeader
import com.mtgcompanion.app.ui.common.ZoomCard
import com.mtgcompanion.app.ui.common.readableWidth
import com.mtgcompanion.app.ui.theme.LocalAppColors

private sealed interface LoadedCollection {
    data object Loading : LoadedCollection
    data object Missing : LoadedCollection
    data class Failed(val message: String) : LoadedCollection
    data class Ok(val shared: SharedCollection, val binders: List<Collection>) : LoadedCollection
}

/** One card across a friend's binders: copies in all of them, and which binders. */
private data class OwnedCard(
    val scryfallId: String,
    val name: String,
    val imageUrl: String?,
    val backImageUrl: String?,
    val quantity: Int,
    val foilQuantity: Int,
    val binders: List<String>
)

/** How many cards the page lists before asking for a search. */
private const val LIST_LIMIT = 300

/**
 * A friend's collection as a whole: every card across the binders they share with the user — all
 * of them when they share their whole collection — searchable, with the binders themselves below.
 * Wishlists are listed but their cards aren't counted as owned.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedCollectionScreen(
    social: SocialRepository,
    owner: String,
    onBack: () -> Unit,
    onOpenBinder: (String) -> Unit,
    onProposeTrade: (String) -> Unit
) {
    val colors = LocalAppColors.current
    val account by social.accountFlow.collectAsState()
    var loaded by remember { mutableStateOf<LoadedCollection>(LoadedCollection.Loading) }
    var query by remember { mutableStateOf("") }
    var zoom by remember { mutableStateOf<OwnedCard?>(null) }
    LaunchedEffect(owner, account?.userId) {
        loaded = LoadedCollection.Loading
        loaded = try {
            val shared = social.api.sharedCollection(owner)
            if (shared == null) LoadedCollection.Missing
            else {
                val adapter = localMoshi.adapter(Collection::class.java)
                LoadedCollection.Ok(shared, shared.binders.mapNotNull { runCatching { adapter.fromJson(it) }.getOrNull() })
            }
        } catch (e: Exception) {
            LoadedCollection.Failed(e.message ?: "Something went wrong.")
        }
    }
    val name = (loaded as? LoadedCollection.Ok)?.shared?.owner?.displayName

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = { Text(if (name != null) "$name's collection" else "Collection", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.accent) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.readableWidth(760.dp)) {
                when (val l = loaded) {
                    LoadedCollection.Loading -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = colors.accent) }
                    LoadedCollection.Missing -> EmptyState(Icons.Filled.LinkOff, "Nothing of theirs is shared with you any more.")
                    is LoadedCollection.Failed -> EmptyState(Icons.Filled.CloudOff, l.message)
                    is LoadedCollection.Ok -> {
                        val cards = remember(l) {
                            val byCard = LinkedHashMap<String, OwnedCard>()
                            l.binders.filter { it.kind != CollectionType.WISHLIST }.forEach { b ->
                                b.entries.forEach { e ->
                                    val c = byCard[e.scryfallId]
                                    byCard[e.scryfallId] = if (c == null) OwnedCard(e.scryfallId, e.name, e.imageUrl, e.backImageUrl, e.quantity, e.foilQuantity, listOf(b.name))
                                    else c.copy(quantity = c.quantity + e.quantity, foilQuantity = c.foilQuantity + e.foilQuantity, binders = c.binders + b.name)
                                }
                            }
                            byCard.values.sortedBy { it.name.lowercase() }
                        }
                        val shown = if (query.isBlank()) cards else cards.filter { it.name.contains(query.trim(), ignoreCase = true) }
                        val total = cards.sumOf { it.quantity + it.foilQuantity }
                        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (l.shared.whole) "WHOLE COLLECTION" else "EVERYTHING SHARED WITH YOU", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                                    Text("${l.shared.owner.displayName}'s collection", style = MaterialTheme.typography.headlineSmall)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Avatar(l.shared.owner, 28.dp)
                                        Text("Shared by ${l.shared.owner.displayName} ${l.shared.owner.handle}", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                                    }
                                    Text(
                                        "$total cards · ${cards.size} unique · ${l.binders.size} ${if (l.binders.size == 1) "binder" else "binders"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textMuted
                                    )
                                }
                            }
                            if (account != null && total > 0) item {
                                GoldButton("Propose a trade", { social.draft = SocialRepository.TradeDraft(to = owner); onProposeTrade(owner) },
                                    icon = { Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp)) })
                            }
                            item {
                                OutlinedTextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    label = { Text("Search ${cards.size} cards") },
                                    singleLine = true,
                                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = colors.accent) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            shown.take(LIST_LIMIT).forEach { c ->
                                item(key = "c-${c.scryfallId}") {
                                    ReadOnlyCardRow(c.name, c.imageUrl, c.quantity + c.foilQuantity, c.foilQuantity, detail = c.binders.joinToString()) { zoom = c }
                                }
                            }
                            if (shown.isEmpty()) item { Notice(if (query.isBlank()) "No cards yet." else "No cards match “$query”.") }
                            if (shown.size > LIST_LIMIT) item {
                                Text("Showing $LIST_LIMIT of ${shown.size} — search to find a card.", style = MaterialTheme.typography.bodySmall, color = colors.textDim)
                            }
                            item { SectionHeader("Binders · ${l.binders.size}") }
                            l.binders.forEach { b ->
                                item(key = "b-${b.id}") {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.surface).clickable { onOpenBinder(b.id) }.padding(14.dp)
                                    ) {
                                        Icon(if (b.kind == CollectionType.WISHLIST) Icons.Filled.Star else Icons.Filled.Collections, contentDescription = null, tint = colors.textDim)
                                        Column(Modifier.weight(1f)) {
                                            Text(b.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            val n = b.entries.sumOf { it.quantity + it.foilQuantity }
                                            Text(listOfNotNull(if (b.kind == CollectionType.WISHLIST) "Wishlist" else null, "$n ${if (n == 1) "card" else "cards"}").joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                                        }
                                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.textDim)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    zoom?.let { c ->
        CardZoomDialog(listOf(ZoomCard(imageUrl = c.imageUrl, cardName = c.name, quantity = c.quantity + c.foilQuantity, backImageUrl = c.backImageUrl)), 0) { zoom = null }
    }
}
