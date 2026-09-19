package com.mtgcompanion.app.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.UNSORTED_COLLECTION_ID
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionRepository
import com.mtgcompanion.app.data.localMoshi
import com.mtgcompanion.app.data.social.Overview
import com.mtgcompanion.app.data.social.WantedCard
import com.mtgcompanion.app.data.social.cardsTheyWant
import com.mtgcompanion.app.data.social.hitsAsTrade
import kotlinx.coroutines.flow.first
import com.mtgcompanion.app.data.social.Profile
import com.mtgcompanion.app.data.social.ShareKind
import com.mtgcompanion.app.data.social.SharedCardHit
import com.mtgcompanion.app.data.social.SharedSeen
import com.mtgcompanion.app.data.social.SharedSummary
import com.mtgcompanion.app.data.social.SocialApi
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.common.ArtImage
import com.mtgcompanion.app.ui.common.LayoutSize
import com.mtgcompanion.app.ui.common.LocalLayoutSize
import com.mtgcompanion.app.ui.common.SearchPill
import com.mtgcompanion.app.ui.common.SectionHeader
import com.mtgcompanion.app.ui.common.pressScale
import com.mtgcompanion.app.ui.theme.LocalAppColors
import com.mtgcompanion.app.ui.theme.NumberStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Everything one friend shares with the user, for their tile and their page. */
private class FriendShares(val owner: String, val profile: Profile?, val items: List<SharedSummary>, val wholeCollection: Boolean) {
    val binders = items.filter { it.kind == ShareKind.COLLECTION && !it.isWishlist }
        .sortedWith(compareByDescending<SharedSummary> { it.itemId == UNSORTED_COLLECTION_ID }.thenBy { it.name.orEmpty().lowercase() })
    val wishlists = items.filter { it.kind == ShareKind.COLLECTION && it.isWishlist }.sortedBy { it.name.orEmpty().lowercase() }
    val decks = items.filter { it.kind == ShareKind.DECK }.sortedBy { it.name.orEmpty().lowercase() }
    val cards = binders.sumOf { it.cards } + decks.sumOf { it.cards }
    val latest = items.maxOfOrNull { it.editedMs } ?: 0L
    /** Art for a friend without a picture: a commander's, else a binder's first card. */
    val cover = decks.firstNotNullOfOrNull { it.cover } ?: binders.firstNotNullOfOrNull { it.cover }
    val name get() = profile?.displayName ?: "A friend"
}

private fun friendShares(overview: Overview): List<FriendShares> =
    overview.sharedWithMe.groupBy { it.owner }
        .map { (owner, items) -> FriendShares(owner, overview.person(owner), items, overview.wholeCollectionFrom(owner)) }
        .sortedWith(compareByDescending<FriendShares> { it.latest }.thenBy { it.name.lowercase() })

private fun plural(n: Int, one: String) = "$n ${if (n == 1) one else one + "s"}"

/** The user's cards on [owner]'s shared wishlists — empty if they share none, or can't be reached. */
private suspend fun loadTheyWant(social: SocialRepository, collectionRepository: CollectionRepository, owner: String): List<WantedCard> =
    runCatching {
        val shared = social.api.sharedCollection(owner) ?: return@runCatching emptyList()
        val adapter = localMoshi.adapter(Collection::class.java)
        cardsTheyWant(collectionRepository.collectionsFlow.first(), shared.binders.mapNotNull { runCatching { adapter.fromJson(it) }.getOrNull() })
    }.getOrDefault(emptyList())


/**
 * The Collection tab's Shared page: a tile per friend who shares anything with the user, a
 * "who has a card?" search across all of it, and — on each tile — how many cards on the user's
 * wishlists that friend has.
 */
@Composable
fun SharedFriendsPage(
    social: SocialRepository,
    collectionRepository: CollectionRepository,
    onSignIn: () -> Unit,
    onOpenFriend: (owner: String) -> Unit,
    onOpenItem: (owner: String, kind: ShareKind, itemId: String) -> Unit,
    onAddFriend: () -> Unit
) {
    SocialGate(social, onSignIn) { overview ->
        val colors = LocalAppColors.current
        val context = LocalContext.current
        val layout = LocalLayoutSize.current
        val friends = remember(overview) { friendShares(overview) }
        var query by remember { mutableStateOf("") }
        var hits by remember { mutableStateOf<List<SharedCardHit>?>(null) }
        var searchError by remember { mutableStateOf<String?>(null) }
        var matches by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
        var theyWant by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
        LaunchedEffect(overview) {
            matches = runCatching { social.api.wishlistMatches() }.getOrNull().orEmpty().groupBy { it.owner }.mapValues { it.value.size }
        }
        // And the other way: how many of the user's cards are on each friend's shared wishlists.
        LaunchedEffect(friends) {
            val counts = mutableMapOf<String, Int>()
            friends.filter { it.wishlists.isNotEmpty() }.forEach { f ->
                counts[f.owner] = loadTheyWant(social, collectionRepository, f.owner).size
                theyWant = counts.toMap()
            }
        }
        LaunchedEffect(query) {
            hits = null
            searchError = null
            val q = query.trim()
            if (q.length < 2) return@LaunchedEffect
            delay(350)
            try {
                hits = social.api.searchSharedCards(q)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                searchError = e.message ?: "Something went wrong."
            }
        }
        val searching = query.trim().length >= 2

        LazyVerticalGrid(
            columns = when (layout) {
                LayoutSize.PHONE -> GridCells.Fixed(2)
                LayoutSize.TABLET -> GridCells.Fixed(3)
                LayoutSize.DESKTOP -> GridCells.Adaptive(200.dp)
            },
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (friends.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "search") {
                    SearchPill(query = query, onQueryChange = { query = it }, placeholder = "Who has a card? e.g. Sol Ring")
                }
            }
            if (searching) {
                searchResults(hits, searchError, query.trim(), overview, onOpenItem)
            } else {
                if (friends.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)) {
                            Text("Nothing shared with you yet", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "When friends share their collection, binders or decks with you, they show up here — and you can search them all for a card you need.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted
                            )
                        }
                    }
                }
                friends.forEach { f ->
                    item(key = "friend-${f.owner}") {
                        FriendTile(
                            f,
                            updated = f.latest > SharedSeen.lastSeen(context, f.owner),
                            wanted = matches[f.owner] ?: 0,
                            theyWant = theyWant[f.owner] ?: 0,
                            onClick = { onOpenFriend(f.owner) }
                        )
                    }
                }
                item(key = "add-friend") { AddFriendTile(onAddFriend) }
            }
        }
    }
}

private fun LazyGridScope.searchResults(
    hits: List<SharedCardHit>?,
    error: String?,
    query: String,
    overview: Overview,
    onOpenItem: (String, ShareKind, String) -> Unit
) {
    val full: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
    when {
        error != null -> item(span = full, key = "search-error") { Notice(error, warn = true) }
        hits == null -> item(span = full, key = "searching") {
            Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = LocalAppColors.current.accent)
            }
        }
        hits.isEmpty() -> item(span = full, key = "no-hits") { Notice("None of your friends' shared cards is called “$query”.") }
        else -> {
            hits.groupBy { it.name }.forEach { (name, copies) ->
                item(span = full, key = "hit-h-$name") {
                    Text(
                        "$name · ${copies.sumOf { it.quantity + it.foilQuantity }.let { n -> "$n ${if (n == 1) "copy" else "copies"}" }}",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                    )
                }
                copies.forEach { hit ->
                    item(span = full, key = "hit-${hit.owner}-${hit.kind}-${hit.itemId}-${hit.scryfallId}") {
                        HitRow(hit, overview.person(hit.owner)) { onOpenItem(hit.owner, hit.kind, hit.itemId) }
                    }
                }
            }
            if (hits.size >= 200) item(span = full, key = "hit-more") {
                Text("Showing the first 200 — type more of the name to narrow it down.", style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textDim)
            }
        }
    }
}

/** One friend's copies of a found card, and where they are. */
@Composable
private fun HitRow(hit: SharedCardHit, owner: Profile?, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).clickable(onClick = onClick).padding(8.dp)
    ) {
        Box {
            AsyncImage(
                model = hit.imageUrl.toArtCropUrl(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 56.dp, height = 44.dp).clip(RoundedCornerShape(11.dp)).background(colors.surface2)
            )
            Avatar(owner, 24.dp, Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 6.dp).border(2.dp, colors.surface, CircleShape))
        }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(owner?.displayName ?: "A friend", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(if (hit.kind == ShareKind.DECK) "Deck" else "Binder", hit.itemName, if (hit.foilQuantity > 0) "${hit.foilQuantity} foil" else null).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text("${hit.quantity + hit.foilQuantity}×", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 4.dp))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.textDim)
    }
}

/** A friend, the way the Decks tab shows a deck: their picture full-size, what they share over it. */
@Composable
private fun FriendTile(f: FriendShares, updated: Boolean, wanted: Int, theyWant: Int, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val interaction = remember { MutableInteractionSource() }
    val avatar = SocialApi.avatarUrl(f.profile?.avatarPath)
    Box(
        Modifier
            .pressScale(interaction)
            .fillMaxWidth()
            .aspectRatio(0.74f)
            .clip(RoundedCornerShape(22.dp))
            .background(colors.surface)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        if (avatar != null) {
            AsyncImage(model = avatar, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            // No picture: their deck's (or binder's) art, dimmed, behind their initial.
            ArtImage(model = f.cover.toArtCropUrl(), seed = f.owner, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
            Text(
                (f.name.trim().firstOrNull() ?: '?').uppercase(),
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 64.sp,
                modifier = Modifier.align(Alignment.Center).padding(bottom = 48.dp)
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 0.62f to Color.Black.copy(alpha = 0.4f), 1f to Color.Black.copy(alpha = 0.95f))))
        val badge = when {
            f.wholeCollection -> "Whole collection"
            f.binders.isEmpty() && f.wishlists.isEmpty() -> "Decks only"
            else -> null
        }
        if (badge != null) {
            Text(
                badge,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 9.dp, vertical = 4.dp)
            )
        }
        if (updated) {
            Box(Modifier.align(Alignment.TopEnd).padding(12.dp).size(11.dp).clip(CircleShape).background(colors.accent))
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 14.dp)) {
            Text(f.name, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            f.profile?.let { Text(it.handle, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f), maxLines = 1) }
            val what = listOfNotNull(
                if (f.binders.isNotEmpty()) plural(f.binders.size, "binder") else null,
                if (f.decks.isNotEmpty()) plural(f.decks.size, "deck") else null,
                if (f.wishlists.isNotEmpty()) plural(f.wishlists.size, "wishlist") else null
            ).joinToString(" · ")
            Text(what, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            if (wanted > 0) {
                Text("$wanted on your wishlist", style = MaterialTheme.typography.labelMedium, color = colors.accentLight, maxLines = 1)
            }
            if (theyWant > 0) {
                Text("Wants $theyWant of yours", style = MaterialTheme.typography.labelMedium, color = colors.accentLight, maxLines = 1)
            }
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp)) {
                Text("%,d".format(f.cards), style = NumberStyle(19), color = Color.White)
                Text(if (f.cards == 1) " card" else " cards", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

/** The last tile: add someone, so there's more to see here. */
@Composable
private fun AddFriendTile(onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().aspectRatio(0.74f).clip(RoundedCornerShape(22.dp)).background(colors.surface).clickable(onClick = onClick).padding(18.dp)
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(colors.surface3), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = colors.accent)
        }
        Spacer(Modifier.height(12.dp))
        Text("Add a friend", style = MaterialTheme.typography.titleSmall)
        Text("See what they share", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
    }
}

/**
 * One friend's shared things as folders: everything at once, then each binder and wishlist, then
 * their decks the way the Decks tab shows them — and, first, any cards they have that the user wants.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendSharedScreen(
    social: SocialRepository,
    owner: String,
    onBack: () -> Unit,
    collectionRepository: CollectionRepository,
    onSignIn: () -> Unit,
    onOpenCollection: (owner: String) -> Unit,
    onOpenItem: (owner: String, kind: ShareKind, itemId: String) -> Unit,
    onProposeTrade: (owner: String) -> Unit
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = {
                    val name = social.overview.value?.person(owner)?.displayName
                    Text(if (name != null) "$name's shared" else "Shared", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.accent) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            SocialGate(social, onSignIn) { overview ->
                val f = remember(overview) { friendShares(overview).firstOrNull { it.owner == owner } }
                if (f == null) {
                    EmptyState(Icons.Filled.PersonOff, "Nothing of theirs is shared with you any more.")
                    return@SocialGate
                }
                LaunchedEffect(f.latest) { SharedSeen.markSeen(context, owner, f.latest) }
                var wanted by remember { mutableStateOf<List<SharedCardHit>>(emptyList()) }
                var theyWant by remember { mutableStateOf<List<WantedCard>>(emptyList()) }
                LaunchedEffect(overview) {
                    wanted = runCatching { social.api.wishlistMatches() }.getOrNull().orEmpty().filter { it.owner == owner }
                }
                LaunchedEffect(overview) { theyWant = loadTheyWant(social, collectionRepository, owner) }
                // Six columns: binders take two (three a row), decks three (two a row), the rest all six.
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(span = { GridItemSpan(6) }, key = "who") {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                            Avatar(f.profile, 64.dp)
                            Column(Modifier.weight(1f)) {
                                Text(f.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(listOfNotNull(f.profile?.handle, "${"%,d".format(f.cards)} cards").joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                            }
                        }
                    }
                    if (f.binders.isNotEmpty() || theyWant.isNotEmpty()) item(span = { GridItemSpan(6) }, key = "trade") {
                        // With matches either way, the trade starts from them: their cards the user
                        // wishes for, and the user's cards on their wishlists.
                        val ask = hitsAsTrade(wanted)
                        val offer = theyWant.map { it.card }
                        val matched = ask.isNotEmpty() || offer.isNotEmpty()
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            GoldButton(
                                if (matched) "Suggest a trade" else "Propose a trade",
                                { social.draft = SocialRepository.TradeDraft(to = owner, want = ask, give = offer); onProposeTrade(owner) },
                                modifier = Modifier.fillMaxWidth(),
                                icon = { Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                            if (matched) {
                                Text(
                                    "Starts with " + listOfNotNull(
                                        if (ask.isNotEmpty()) "${plural(ask.size, "card")} of theirs on your wishlist" else null,
                                        if (offer.isNotEmpty()) "${plural(offer.size, "card")} of yours on theirs" else null
                                    ).joinToString(" and ") + ". Change anything before you send it.",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.textDim
                                )
                            }
                        }
                    }
                    if (wanted.isNotEmpty()) {
                        item(span = { GridItemSpan(6) }, key = "wanted-h") { SectionHeader("On your wishlist · ${wanted.size}") }
                        wanted.take(6).forEach { hit ->
                            item(span = { GridItemSpan(6) }, key = "wanted-${hit.itemId}-${hit.scryfallId}") {
                                ReadOnlyCardRow(hit.name, hit.imageUrl, hit.quantity + hit.foilQuantity, hit.foilQuantity, detail = "In ${hit.itemName}") {
                                    onOpenItem(owner, ShareKind.COLLECTION, hit.itemId)
                                }
                            }
                        }
                        if (wanted.size > 6) item(span = { GridItemSpan(6) }, key = "wanted-more") {
                            Text("…and ${wanted.size - 6} more in their binders.", style = MaterialTheme.typography.bodySmall, color = colors.textDim)
                        }
                    }
                    if (theyWant.isNotEmpty()) {
                        item(span = { GridItemSpan(6) }, key = "theywant-h") { SectionHeader("On their wishlist · ${theyWant.size}") }
                        theyWant.take(6).forEach { card ->
                            item(span = { GridItemSpan(6) }, key = "theywant-${card.name}") {
                                ReadOnlyCardRow(card.name, card.imageUrl, card.copies, 0, detail = "You have ${card.copies} · wanted in ${card.wishlist}") {}
                            }
                        }
                        if (theyWant.size > 6) item(span = { GridItemSpan(6) }, key = "theywant-more") {
                            Text("…and ${theyWant.size - 6} more of yours.", style = MaterialTheme.typography.bodySmall, color = colors.textDim)
                        }
                    }
                    if (f.binders.isNotEmpty()) {
                        item(span = { GridItemSpan(6) }, key = "all-h") { SectionHeader("All cards") }
                        item(span = { GridItemSpan(6) }, key = "all") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.accentGlow)
                                    .border(1.dp, colors.accentDim, RoundedCornerShape(18.dp)).clickable { onOpenCollection(owner) }.padding(14.dp)
                            ) {
                                Icon(Icons.Filled.CollectionsBookmark, contentDescription = null, tint = colors.accent, modifier = Modifier.size(28.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Everything ${f.name} shares", style = MaterialTheme.typography.titleSmall)
                                    Text("${"%,d".format(f.binders.sumOf { it.cards })} cards · searchable", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                                }
                                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.textDim)
                            }
                        }
                    }
                    val folders = f.binders + f.wishlists
                    if (folders.isNotEmpty()) {
                        item(span = { GridItemSpan(6) }, key = "binders-h") { SectionHeader("Binders · ${folders.size}") }
                        folders.forEach { b ->
                            item(span = { GridItemSpan(2) }, key = "b-${b.itemId}") { FolderTile(b) { onOpenItem(owner, ShareKind.COLLECTION, b.itemId) } }
                        }
                    }
                    if (f.decks.isNotEmpty()) {
                        item(span = { GridItemSpan(6) }, key = "decks-h") { SectionHeader("Decks · ${f.decks.size}") }
                        f.decks.forEach { d ->
                            item(span = { GridItemSpan(3) }, key = "d-${d.itemId}") { SharedDeckTile(d) { onOpenItem(owner, ShareKind.DECK, d.itemId) } }
                        }
                    }
                }
            }
        }
    }
}

/** A binder as a folder: a tab on top, an icon, its name and card count. */
@Composable
private fun FolderTile(item: SharedSummary, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(Modifier.padding(start = 12.dp).width(40.dp).height(8.dp).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).background(colors.surface))
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)).background(colors.surface).padding(12.dp)
        ) {
            Icon(
                when {
                    item.isWishlist -> Icons.Filled.Star
                    item.itemId == UNSORTED_COLLECTION_ID -> Icons.Filled.Inbox
                    else -> Icons.Filled.Folder
                },
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(24.dp)
            )
            Text(item.name ?: "Binder", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            Text(
                (if (item.isWishlist) "Wishlist · " else "") + plural(item.cards, "card"),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
                maxLines = 1
            )
        }
    }
}

/** A shared deck, like a Decks-tab tile: its commander's art with the name over it. */
@Composable
private fun SharedDeckTile(item: SharedSummary, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.pressScale(interaction).fillMaxWidth().aspectRatio(0.9f).clip(RoundedCornerShape(20.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        ArtImage(model = item.cover.toArtCropUrl(), seed = item.name ?: item.itemId, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.3f to Color.Transparent, 0.62f to Color.Black.copy(alpha = 0.35f), 1f to Color.Black.copy(alpha = 0.95f))))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
            Text(item.name ?: "Deck", style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
                Text("${item.cards}", style = NumberStyle(17), color = Color.White)
                Text(if (item.cards == 1) " card" else " cards", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}
