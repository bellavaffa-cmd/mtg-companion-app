package com.mtgcompanion.app.ui.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.CollectionRepository
import com.mtgcompanion.app.data.CollectionType
import com.mtgcompanion.app.data.DeckRepository
import com.mtgcompanion.app.data.social.Overview
import com.mtgcompanion.app.data.social.Share
import com.mtgcompanion.app.data.social.ShareKind
import com.mtgcompanion.app.data.social.SocialException
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.data.supabase.SupabaseSync
import com.mtgcompanion.app.ui.common.SectionHeader
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

/**
 * Why one deck or binder is already visible to [friendId] other than one by one — shared with all
 * friends, or with a pod they're in — or null when it isn't.
 */
private fun sharedBroadly(overview: Overview, share: Share?, friendId: String): String? {
    if (share == null) return null
    if (share.allFriends) return "Shared with all your friends"
    val pod = overview.pods.firstOrNull { it.id in share.podIds && friendId in it.members }
    return pod?.let { "Shared with ${it.name}" }
}

private fun count(n: Int, one: String) = "$n ${if (n == 1) one else one + "s"}"

/**
 * On a friend's page: what the user shares with them — their whole collection, all their decks,
 * or binders and decks one by one. Each switch saves straight away.
 */
@Composable
fun ShareWithFriendSection(
    social: SocialRepository,
    sync: SupabaseSync,
    collectionRepository: CollectionRepository,
    deckRepository: DeckRepository,
    overview: Overview,
    friendId: String,
    friendName: String
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val collections by collectionRepository.collectionsFlow.collectAsState(initial = emptyList())
    val decks by deckRepository.decksFlow.collectAsState(initial = emptyList())
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // The switch being saved and where it's going: shown flipped straight away, as saving (a sync
    // first, then the change) takes a moment.
    var pending by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    fun shown(key: String, saved: Boolean) = pending?.takeIf { it.first == key }?.second ?: saved

    fun run(key: String, on: Boolean, action: suspend () -> Unit) {
        busy = true
        pending = key to on
        error = null
        scope.launch {
            try {
                // New binders and decks have to reach the server before they can be shared.
                sync.refresh()
                action()
                social.refresh()
            } catch (e: SocialException) {
                error = if (e.code == "no_such_item") "That hasn't synced yet — check your connection and try again." else e.message
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong."
            } finally {
                busy = false
                pending = null
            }
        }
    }

    @Composable
    fun ItemSwitch(kind: ShareKind, id: String, name: String, detail: String) {
        val share = overview.myShares.firstOrNull { it.kind == kind && it.itemId == id }
        val broad = sharedBroadly(overview, share, friendId)
        ShareSwitch(name, broad ?: detail, shown("$kind:$id", broad != null || share?.friendIds?.contains(friendId) == true), enabled = broad == null && !busy) { on ->
            run("$kind:$id", on) { social.api.setItemFriendShare(kind, id, friendId, on) }
        }
    }

    val wholeCollection = overview.sharesAll(ShareKind.COLLECTION, friendId)
    val allDecks = overview.sharesAll(ShareKind.DECK, friendId)
    val collectionToAll = overview.sharesAll(ShareKind.COLLECTION, null)
    val decksToAll = overview.sharesAll(ShareKind.DECK, null)
    // Binders or decks shared one by one sit under their "everything" switch, set off by a rule.
    val group = Modifier.fillMaxWidth().padding(start = 12.dp)
        .drawBehind { drawLine(colors.border, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 2.dp.toPx()) }
        .padding(start = 14.dp)

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SectionHeader("What you share with $friendName")
        Text("They can look, not change anything. It stays up to date as you edit.", style = MaterialTheme.typography.bodySmall, color = colors.textDim)
        ShareSwitch(
            "My whole collection",
            if (collectionToAll) "Shared with all your friends" else "Every binder and wishlist — including ones you make later",
            shown("all:collection", wholeCollection || collectionToAll),
            enabled = !collectionToAll && !busy
        ) { on -> run("all:collection", on) { social.api.setShareAll(ShareKind.COLLECTION, friendId, on) } }
        if (!wholeCollection && !collectionToAll && collections.isNotEmpty()) {
            Column(group) {
                collections.sortedWith(compareByDescending<com.mtgcompanion.app.data.Collection> { it.isUnsorted }.thenBy { it.name.lowercase() }).forEach { c ->
                    val what = when {
                        c.isUnsorted -> "Not in a binder"
                        c.kind == CollectionType.WISHLIST -> "Wishlist"
                        else -> "Binder"
                    }
                    ItemSwitch(ShareKind.COLLECTION, c.id, c.name, "$what · ${count(c.entries.sumOf { it.quantity + it.foilQuantity }, "card")}")
                }
            }
        }
        ShareSwitch(
            "All my decks",
            if (decksToAll) "Shared with all your friends" else "Every deck — including ones you make later",
            shown("all:deck", allDecks || decksToAll),
            enabled = !decksToAll && !busy
        ) { on -> run("all:deck", on) { social.api.setShareAll(ShareKind.DECK, friendId, on) } }
        if (!allDecks && !decksToAll && decks.isNotEmpty()) {
            Column(group) {
                decks.sortedBy { it.name.lowercase() }.forEach { d ->
                    ItemSwitch(ShareKind.DECK, d.id, d.name, "Deck · ${count(d.cards.sumOf { it.quantity }, "card")}")
                }
            }
        }
        error?.let { Notice(it, warn = true) }
    }
}

/** From the Collection tab: the user's whole collection, shared with all friends or chosen ones. */
@Composable
fun ShareCollectionDialog(social: SocialRepository, sync: SupabaseSync, onOpenFriends: () -> Unit, onClose: () -> Unit) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val account by social.accountFlow.collectAsState()
    val overview by social.overview.collectAsState()
    val loadError by social.error.collectAsState()
    LaunchedEffect(account?.userId) { if (account != null) social.refresh() }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // The friend (or "all") being saved, shown flipped straight away.
    var pending by remember { mutableStateOf<Pair<String?, Boolean>?>(null) }
    val o = overview

    if (account == null || o == null || o.me == null) {
        AlertDialog(
            onDismissRequest = onClose,
            containerColor = colors.surface,
            title = { Text("Share my collection") },
            text = {
                Text(
                    when {
                        account == null -> "Sign in to share your collection with friends."
                        o == null -> loadError ?: "Loading…"
                        else -> "Make your profile first — friends see your name on what you share."
                    },
                    color = colors.textMuted
                )
            },
            confirmButton = {
                if (account != null && o != null) TextButton(onClick = { onClose(); onOpenFriends() }) { Text("Make my profile", color = colors.accent) }
                else TextButton(onClick = onClose) { Text("Close", color = colors.accent) }
            }
        )
        return
    }

    fun set(viewer: String?, on: Boolean) {
        busy = true
        pending = viewer to on
        error = null
        scope.launch {
            try {
                sync.refresh()
                social.api.setShareAll(ShareKind.COLLECTION, viewer, on)
                social.refresh()
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong."
            } finally {
                busy = false
                pending = null
            }
        }
    }

    fun shown(viewer: String?) = pending?.takeIf { it.first == viewer }?.second ?: o.sharesAll(ShareKind.COLLECTION, viewer)
    val toAll = shown(null)
    val friends = o.acceptedFriends.mapNotNull { o.person(it.userId) }.sortedBy { it.displayName.lowercase() }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = colors.surface,
        title = { Text("Share my collection") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Every binder and wishlist — including ones you make later. Friends can look, not change anything. To share only some binders, open a friend in Friends.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )
                ShareSwitch(
                    "All my friends",
                    if (friends.isEmpty()) "You have no friends added yet" else "${count(friends.size, "friend")}, and anyone you add later",
                    toAll,
                    enabled = !busy
                ) { set(null, it) }
                if (!toAll) friends.forEach { p ->
                    ShareSwitch(p.displayName, p.handle, shown(p.userId), enabled = !busy) { set(p.userId, it) }
                }
                error?.let { Notice(it, warn = true) }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close", color = colors.accent) } }
    )
}
