package com.mtgcompanion.app.ui.social

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionEntry
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckCardEntry
import com.mtgcompanion.app.data.DeckRepository
import com.mtgcompanion.app.data.localMoshi
import com.mtgcompanion.app.data.social.ShareKind
import com.mtgcompanion.app.data.social.SharedItem
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.data.social.TradeCard
import com.mtgcompanion.app.data.social.cardTotal
import com.mtgcompanion.app.data.social.withQuantity
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.common.CardZoomDialog
import com.mtgcompanion.app.ui.common.SectionHeader
import com.mtgcompanion.app.ui.common.ZoomCard
import com.mtgcompanion.app.ui.common.readableWidth
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

private sealed interface Loaded {
    data object Loading : Loaded
    data object Missing : Loaded
    data class Failed(val message: String) : Loaded
    data class Ok(val item: SharedItem) : Loaded
}

/** Where a shared item comes from: a friend (owner + id) or a share link. */
sealed interface SharedSource {
    data class FromFriend(val owner: String, val kind: ShareKind, val itemId: String) : SharedSource
    data class FromLink(val token: String) : SharedSource
}

/**
 * A deck or binder someone shared, view only. A friend's binder can start a trade; a shared deck
 * can be copied into the user's own decks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedItemScreen(
    social: SocialRepository,
    deckRepository: DeckRepository,
    source: SharedSource,
    onBack: () -> Unit,
    onOpenDeck: (String) -> Unit,
    onProposeTrade: (friendId: String) -> Unit
) {
    val colors = LocalAppColors.current
    val account by social.accountFlow.collectAsState()
    var loaded by remember { mutableStateOf<Loaded>(Loaded.Loading) }
    LaunchedEffect(source, account?.userId) {
        loaded = Loaded.Loading
        loaded = try {
            val item = when (source) {
                is SharedSource.FromFriend -> social.api.sharedItem(source.owner, source.kind, source.itemId)
                is SharedSource.FromLink -> social.api.sharedByLink(source.token)
            }
            if (item == null) Loaded.Missing else Loaded.Ok(item)
        } catch (e: Exception) {
            Loaded.Failed(e.message ?: "Something went wrong.")
        }
    }
    val title = (loaded as? Loaded.Ok)?.item?.let { runCatching { org.json.JSONObject(it.data).optString("name") }.getOrNull() } ?: "Shared"

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.accent) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.readableWidth(760.dp)) {
                when (val l = loaded) {
                    Loaded.Loading -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = colors.accent) }
                    Loaded.Missing -> EmptyState(
                        Icons.Filled.LinkOff,
                        if (source is SharedSource.FromLink) "This link no longer works — it may have been turned off, or the deck or binder deleted."
                        else "This isn't shared with you any more."
                    )
                    is Loaded.Failed -> EmptyState(Icons.Filled.CloudOff, l.message)
                    is Loaded.Ok -> if (l.item.kind == ShareKind.DECK) {
                        SharedDeck(l.item, deckRepository, canCopy = account != null, onOpenDeck = onOpenDeck)
                    } else {
                        SharedBinder(
                            social = social,
                            item = l.item,
                            ownerId = (source as? SharedSource.FromFriend)?.owner,
                            onProposeTrade = onProposeTrade
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OwnerLine(item: SharedItem) {
    val colors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Avatar(item.owner, 28.dp)
        Text("Shared by ${item.owner.displayName} ${item.owner.handle}", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
    }
}

private val TYPE_ORDER = listOf("Creature", "Planeswalker", "Battle", "Instant", "Sorcery", "Artifact", "Enchantment", "Land")
private val TYPE_PLURALS = mapOf("Creature" to "Creatures", "Planeswalker" to "Planeswalkers", "Battle" to "Battles", "Instant" to "Instants", "Sorcery" to "Sorceries", "Artifact" to "Artifacts", "Enchantment" to "Enchantments", "Land" to "Lands")

private fun primaryType(typeLine: String?): String {
    val front = typeLine.orEmpty().substringBefore(" // ")
    return TYPE_ORDER.firstOrNull { front.contains(it) } ?: "Other"
}

@Composable
private fun SharedDeck(item: SharedItem, deckRepository: DeckRepository, canCopy: Boolean, onOpenDeck: (String) -> Unit) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val deck = remember(item.data) { runCatching { localMoshi.adapter(Deck::class.java).fromJson(item.data) }.getOrNull() }
    var copied by remember { mutableStateOf<String?>(null) }
    var zoom by remember { mutableStateOf<DeckCardEntry?>(null) }
    if (deck == null) {
        EmptyState(Icons.Filled.CloudOff, "This deck couldn't be read. Updating the app may help.")
        return
    }
    val groups = deck.cards.sortedBy { it.name }.groupBy { primaryType(it.typeLine) }.toList()
        .sortedBy { (type, _) -> TYPE_ORDER.indexOf(type).let { if (it == -1) 99 else it } }
    val commanders = listOfNotNull(deck.commander, deck.partnerCommander)

    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(24.dp)).background(colors.surface)) {
                deck.commander?.imageUrl?.let { AsyncImage(model = it.toArtCropUrl(), contentDescription = null, contentScale = ContentScale.Crop, alpha = 0.5f, modifier = Modifier.fillMaxSize()) }
                Column(Modifier.align(Alignment.BottomStart).padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${deck.mode.label} deck".uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                    Text(deck.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    OwnerLine(item)
                }
            }
        }
        item {
            Text("${deck.cards.sumOf { it.quantity }} cards · ${deck.cards.size} unique", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        if (canCopy) item {
            val id = copied
            if (id != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.accentGlow).clickable { onOpenDeck(id) }.padding(14.dp)
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = colors.accent)
                    Text("Copied to your decks — open your copy", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            } else {
                LineButton("Copy to my decks", {
                    scope.launch { copied = deckRepository.createDeckWithCards(deck.name, deck.mode, deck.cards, deck.commander, deck.partnerCommander).id }
                }, icon = { Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) })
            }
        }
        if (commanders.isNotEmpty()) {
            item { SectionHeader(if (commanders.size > 1) "Commanders" else "Commander") }
            commanders.forEach { c -> item(key = "cmd-${c.scryfallId}") { ReadOnlyCardRow(c.name, c.imageUrl, c.quantity, 0) { zoom = c } } }
        }
        groups.forEach { (type, cards) ->
            item(key = "h-$type") { SectionHeader("${TYPE_PLURALS[type] ?: type} · ${cards.sumOf { it.quantity }}") }
            cards.forEach { c -> item(key = "c-${c.scryfallId}") { ReadOnlyCardRow(c.name, c.imageUrl, c.quantity, 0) { zoom = c } } }
        }
        if (deck.cards.isEmpty()) item { Notice("This deck has no cards yet.") }
    }
    zoom?.let { c ->
        CardZoomDialog(listOf(ZoomCard(imageUrl = c.imageUrl, cardName = c.name, quantity = c.quantity, backImageUrl = c.backImageUrl)), 0) { zoom = null }
    }
}

@Composable
fun ReadOnlyCardRow(name: String, imageUrl: String?, quantity: Int, foil: Int, detail: String? = null, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).clickable(onClick = onClick).padding(8.dp)
    ) {
        AsyncImage(model = imageUrl.toArtCropUrl(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(width = 56.dp, height = 44.dp).clip(RoundedCornerShape(11.dp)).background(colors.surface2))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (foil > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = colors.accent, modifier = Modifier.size(14.dp))
                Text("$foil foil", style = MaterialTheme.typography.labelMedium, color = colors.accent)
            }
            if (detail != null) Text(detail, style = MaterialTheme.typography.labelSmall, color = colors.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (quantity > 0) Text("$quantity×", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 8.dp))
    }
}

@Composable
private fun SharedBinder(social: SocialRepository, item: SharedItem, ownerId: String?, onProposeTrade: (String) -> Unit) {
    val colors = LocalAppColors.current
    val overview by social.overview.collectAsState()
    val collection = remember(item.data) { runCatching { localMoshi.adapter(Collection::class.java).fromJson(item.data) }.getOrNull() }
    var trading by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<List<TradeCard>>(emptyList()) }
    var zoom by remember { mutableStateOf<CollectionEntry?>(null) }
    LaunchedEffect(Unit) { if (social.overview.value == null) social.refresh() }
    if (collection == null) {
        EmptyState(Icons.Filled.CloudOff, "This binder couldn't be read. Updating the app may help.")
        return
    }
    val isFriend = ownerId != null && overview?.isFriend(ownerId) == true
    val entries = collection.entries.sortedBy { it.name }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (collection.type == "WISHLIST") "WISHLIST" else "BINDER", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                    Text(collection.name, style = MaterialTheme.typography.headlineSmall)
                    OwnerLine(item)
                    Text(
                        "${collection.entries.sumOf { it.quantity }} cards · ${collection.entries.sumOf { it.foilQuantity }} foils · ${collection.entries.size} unique",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted
                    )
                }
            }
            if (isFriend && collection.type != "WISHLIST" && entries.isNotEmpty()) item {
                if (trading) LineButton("Stop picking", { trading = false; picked = emptyList() })
                else GoldButton("Ask to trade for cards", { trading = true }, icon = { Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp)) })
            }
            if (entries.isEmpty()) item { Notice("This binder is empty.") }
            if (trading) {
                binderPicker(collection.id, entries, picked) { picked = it }
            } else {
                entries.forEach { e ->
                    item(key = e.scryfallId) { ReadOnlyCardRow(e.name, e.imageUrl, e.quantity, e.foilQuantity) { zoom = e } }
                }
            }
        }
        if (trading && picked.isNotEmpty() && ownerId != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surface2).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
            ) {
                Text("${picked.cardTotal()} ${if (picked.cardTotal() == 1) "card" else "cards"} picked", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                GoldButton("Next", {
                    social.draft = SocialRepository.TradeDraft(to = ownerId, want = picked)
                    onProposeTrade(ownerId)
                }, icon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp)) })
            }
        }
    }
    zoom?.let { e ->
        CardZoomDialog(listOf(ZoomCard(imageUrl = e.imageUrl, cardName = e.name, quantity = e.quantity + e.foilQuantity, backImageUrl = e.backImageUrl)), 0) { zoom = null }
    }
}

/** A binder's cards, each with a regular and a foil stepper — up to what the binder holds. */
@OptIn(ExperimentalLayoutApi::class)
fun LazyListScope.binderPicker(collectionId: String, entries: List<CollectionEntry>, picked: List<TradeCard>, onChange: (List<TradeCard>) -> Unit) {
    entries.filter { it.quantity + it.foilQuantity > 0 }.forEach { e ->
        item(key = "pick-$collectionId-${e.scryfallId}") {
            val colors = LocalAppColors.current
            fun count(foil: Boolean) = picked.firstOrNull { it.key == TradeCard(e.scryfallId, e.name, foil = foil, collectionId = collectionId).key }?.quantity ?: 0
            fun set(foil: Boolean, n: Int) = onChange(picked.withQuantity(TradeCard(e.scryfallId, e.name, e.imageUrl, foil, 0, collectionId), n))
            val on = count(false) + count(true) > 0
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface)
                    .let { if (on) it.border(BorderStroke(1.5.dp, colors.accent), RoundedCornerShape(18.dp)) else it }
                    .padding(8.dp)
            ) {
                AsyncImage(model = e.imageUrl.toArtCropUrl(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(width = 56.dp, height = 44.dp).clip(RoundedCornerShape(11.dp)).background(colors.surface2))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(e.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // Wraps when both don't fit side by side (a phone held upright).
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (e.quantity > 0) MiniStepper("Regular", e.quantity, count(false), e.name, foil = false) { set(false, it) }
                        if (e.foilQuantity > 0) MiniStepper("Foil", e.foilQuantity, count(true), e.name, foil = true) { set(true, it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStepper(label: String, max: Int, value: Int, name: String, foil: Boolean, onChange: (Int) -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (value > 0) colors.accentGlow else colors.bg).padding(start = 10.dp)
    ) {
        if (foil) Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = colors.accent, modifier = Modifier.size(13.dp))
        Text(" $label ", style = MaterialTheme.typography.labelMedium)
        Text("of $max", style = MaterialTheme.typography.labelSmall, color = colors.textDim)
        val kind = if (foil) "foil " else ""
        IconButton(onClick = { onChange(value - 1) }, enabled = value > 0, modifier = Modifier.size(34.dp).semantics { contentDescription = "One fewer $kind$name" }) {
            Text("−", style = MaterialTheme.typography.titleMedium, color = if (value > 0) colors.textPrimary else colors.textDim)
        }
        Text("$value", style = MaterialTheme.typography.titleSmall)
        IconButton(onClick = { onChange(value + 1) }, enabled = value < max, modifier = Modifier.size(34.dp).semantics { contentDescription = "One more $kind$name" }) {
            Text("+", style = MaterialTheme.typography.titleMedium, color = if (value < max) colors.textPrimary else colors.textDim)
        }
        Spacer(Modifier.size(2.dp))
    }
}
