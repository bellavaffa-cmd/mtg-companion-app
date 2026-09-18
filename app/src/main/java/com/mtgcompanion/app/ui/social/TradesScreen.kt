package com.mtgcompanion.app.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionEntry
import com.mtgcompanion.app.data.CollectionRepository
import com.mtgcompanion.app.data.localMoshi
import com.mtgcompanion.app.data.social.CollectionChange
import com.mtgcompanion.app.data.social.Overview
import com.mtgcompanion.app.data.social.ShareKind
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.data.social.Trade
import com.mtgcompanion.app.data.social.TradeCard
import com.mtgcompanion.app.data.social.TradeStatus
import com.mtgcompanion.app.data.social.awaitingMyUpdate
import com.mtgcompanion.app.data.social.cardTotal
import com.mtgcompanion.app.data.social.tradeChanges
import com.mtgcompanion.app.data.social.tradeSides
import com.mtgcompanion.app.data.social.waitingOnMe
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.common.PillChip
import com.mtgcompanion.app.ui.common.SectionHeader
import com.mtgcompanion.app.ui.common.readableWidth
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

private enum class TradeFilter { WAITING, SENT, DONE }

/** Trades with friends: the ones waiting on the user, the ones they sent, and finished ones. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradesScreen(
    social: SocialRepository,
    collectionRepository: CollectionRepository,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onCounter: (friendId: String) -> Unit
) {
    val colors = LocalAppColors.current
    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("Trades", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.accent) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.readableWidth(760.dp)) {
                SocialGate(social, onSignIn) { overview -> TradeList(social, collectionRepository, overview, onCounter) }
            }
        }
    }
}

@Composable
private fun TradeList(social: SocialRepository, collectionRepository: CollectionRepository, overview: Overview, onCounter: (String) -> Unit) {
    val me = overview.me!!.userId
    val waiting = overview.trades.filter { waitingOnMe(it, me) }
    val sent = overview.trades.filter { it.status == TradeStatus.OPEN && it.fromUser == me }
    val done = overview.trades.filter { it !in waiting && it !in sent }
    var filter by remember { mutableStateOf(if (waiting.isNotEmpty() || sent.isEmpty()) TradeFilter.WAITING else TradeFilter.SENT) }
    val shown = when (filter) { TradeFilter.WAITING -> waiting; TradeFilter.SENT -> sent; TradeFilter.DONE -> done }

    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                PillChip("Waiting on you", filter == TradeFilter.WAITING, { filter = TradeFilter.WAITING }, count = waiting.size)
                PillChip("Sent", filter == TradeFilter.SENT, { filter = TradeFilter.SENT }, count = sent.size)
                PillChip("Done", filter == TradeFilter.DONE, { filter = TradeFilter.DONE }, count = done.size)
            }
        }
        if (shown.isEmpty()) item {
            EmptyState(
                Icons.Filled.SwapHoriz,
                when (filter) {
                    TradeFilter.WAITING -> "Nothing needs your answer."
                    TradeFilter.SENT -> "No trade requests waiting for an answer."
                    TradeFilter.DONE -> "No finished trades yet."
                } + if (filter != TradeFilter.DONE && overview.acceptedFriends.isNotEmpty()) " To start one, open a friend's shared binder." else ""
            )
        }
        shown.forEach { t -> item(key = t.id) { TradeCardView(social, collectionRepository, overview, t, onCounter) } }
    }
}

private val STATUS = mapOf(
    TradeStatus.OPEN to "Waiting for an answer",
    TradeStatus.ACCEPTED to "Accepted",
    TradeStatus.DECLINED to "Declined",
    TradeStatus.CANCELLED to "Cancelled",
    TradeStatus.COUNTERED to "Countered"
)

@Composable
private fun TradeCardView(social: SocialRepository, collectionRepository: CollectionRepository, overview: Overview, trade: Trade, onCounter: (String) -> Unit) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val me = overview.me!!.userId
    val sides = tradeSides(trade, me)
    val them = overview.person(sides.other)
    val theirName = them?.displayName ?: "Someone"
    val incoming = trade.toUser == me
    var answering by remember { mutableStateOf<Boolean?>(null) }
    var updating by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun run(action: suspend () -> Unit) {
        busy = true
        error = null
        scope.launch {
            try { action(); social.refresh() } catch (e: Exception) { error = e.message } finally { busy = false }
        }
    }

    val status = when {
        trade.status == TradeStatus.OPEN && incoming -> "$theirName asks you"
        trade.status == TradeStatus.OPEN -> "Waiting for $theirName"
        trade.status == TradeStatus.ACCEPTED && awaitingMyUpdate(trade, me) -> "Accepted — update your binders"
        trade.status == TradeStatus.ACCEPTED && (if (trade.fromUser == me) trade.toApplied else trade.fromApplied) -> "Done"
        trade.status == TradeStatus.ACCEPTED -> "Accepted — $theirName is updating their binders"
        else -> STATUS.getValue(trade.status)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.surface).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Avatar(them, 38.dp)
            Column(Modifier.weight(1f)) {
                Text(theirName, style = MaterialTheme.typography.titleSmall)
                Text(status, style = MaterialTheme.typography.bodySmall, color = if (trade.status == TradeStatus.OPEN || trade.status == TradeStatus.ACCEPTED) colors.accent else colors.textMuted)
            }
            Text(trade.updatedAt.take(10), style = MaterialTheme.typography.labelSmall, color = colors.textDim)
        }
        TradeSideList("You give", sides.give)
        TradeSideList("You get", sides.get)
        trade.message?.let { TradeMessage(if (trade.fromUser == me) "You" else theirName, it) }
        trade.reply?.let { TradeMessage(if (trade.toUser == me) "You" else theirName, it) }
        error?.let { Notice(it, warn = true) }
        if (trade.status == TradeStatus.OPEN && incoming) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { answering = false }, enabled = !busy) { Text("Decline", color = colors.textMuted) }
                TextButton(onClick = {
                    // A counter starts from their trade turned around: what they offered is what the user asks for.
                    social.draft = SocialRepository.TradeDraft(to = sides.other, replyTo = trade.id, want = trade.give, give = trade.want)
                    onCounter(sides.other)
                }, enabled = !busy) { Text("Counter", color = colors.textPrimary) }
                GoldButton("Accept", { answering = true }, enabled = !busy)
            }
        }
        if (trade.status == TradeStatus.OPEN && !incoming) {
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { run { social.api.respondTrade(trade.id, "cancel") } }, enabled = !busy) { Text("Cancel request", color = colors.textMuted) }
            }
        }
        if (awaitingMyUpdate(trade, me)) {
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                GoldButton("Update my binders", { updating = true }, enabled = !busy, icon = { Icon(Icons.Filled.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp)) })
            }
        }
    }

    answering?.let { accept ->
        var reply by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { answering = null },
            containerColor = colors.surface,
            title = { Text(if (accept) "Accept $theirName's trade?" else "Decline $theirName's trade?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (accept) Text("Once you've swapped the cards, each of you taps Update my binders.", color = colors.textMuted)
                    OutlinedTextField(reply, { reply = it.take(500) }, label = { Text("Message (optional)") }, colors = socialFieldColors(), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = { answering = null; run { social.api.respondTrade(trade.id, if (accept) "accept" else "decline", reply.trim()) } }) {
                    Text(if (accept) "Accept" else "Decline", color = if (accept) colors.accent else colors.error)
                }
            },
            dismissButton = { TextButton(onClick = { answering = null }) { Text("Cancel", color = colors.textMuted) } }
        )
    }
    if (updating) UpdateBindersDialog(social, collectionRepository, trade, me, theirName) { updating = false }
}

@Composable
private fun TradeSideList(title: String, cards: List<TradeCard>) {
    val colors = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            (if (cards.isEmpty()) title else "$title · ${cards.cardTotal()}").uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = colors.textDim
        )
        TradeCardList(cards, empty = "Nothing")
    }
}

@Composable
fun TradeCardList(cards: List<TradeCard>, empty: String, onRemove: ((TradeCard) -> Unit)? = null) {
    val colors = LocalAppColors.current
    if (cards.isEmpty()) {
        Text(empty, style = MaterialTheme.typography.bodySmall, color = colors.textDim)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        cards.forEach { c ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AsyncImage(model = c.imageUrl.toArtCropUrl(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(width = 40.dp, height = 30.dp).clip(RoundedCornerShape(8.dp)).background(colors.surface2))
                Text("${c.quantity}×", style = MaterialTheme.typography.titleSmall)
                Text(c.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (c.foil) Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = colors.accent, modifier = Modifier.size(14.dp))
                    Text("Foil", style = MaterialTheme.typography.labelMedium, color = colors.accent)
                }
                if (onRemove != null) IconButton(onClick = { onRemove(c) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove ${c.name}", tint = colors.textMuted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun TradeMessage(who: String, text: String) {
    val colors = LocalAppColors.current
    Text(
        "$who: $text",
        style = MaterialTheme.typography.bodySmall,
        color = colors.textPrimary,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface2).padding(horizontal = 12.dp, vertical = 10.dp)
    )
}

/**
 * Applies the user's side of an accepted trade to their own binders: the cards they give come out
 * of the binders they were in, the cards they get go into the binder they pick.
 */
@Composable
private fun UpdateBindersDialog(social: SocialRepository, collectionRepository: CollectionRepository, trade: Trade, me: String, theirName: String, onClose: () -> Unit) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val collections by collectionRepository.collectionsFlow.collectAsState(initial = emptyList())
    val owned = collections.filter { it.type != "WISHLIST" }
    val sides = tradeSides(trade, me)
    val givenFrom = sides.give.mapNotNull { it.collectionId }.firstOrNull { id -> owned.any { it.id == id } }
    var into by remember { mutableStateOf<String?>(null) }
    val target = into ?: givenFrom ?: owned.firstOrNull()?.id
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var short by remember { mutableStateOf<List<CollectionChange>?>(null) }

    val missing = short
    if (missing != null) {
        AlertDialog(
            onDismissRequest = onClose,
            containerColor = colors.surface,
            title = { Text("Binders updated") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Some cards you gave weren't in your binder any more (or not as many), so they were left as they were:", color = colors.textMuted)
                    missing.forEach { Text("• ${it.card.name}") }
                }
            },
            confirmButton = { TextButton(onClick = onClose) { Text("OK", color = colors.accent) } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = colors.surface,
        title = { Text("Update my binders") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (sides.give.isNotEmpty()) Text("${sides.give.cardTotal()} ${if (sides.give.cardTotal() == 1) "card goes" else "cards go"} to $theirName and come out of your binders.", color = colors.textMuted)
                if (sides.get.isNotEmpty()) {
                    if (owned.isEmpty()) Notice("Make a binder first for the cards you get.", warn = true)
                    else {
                        Text("Put the ${sides.get.cardTotal()} ${if (sides.get.cardTotal() == 1) "card" else "cards"} you get into", style = MaterialTheme.typography.labelLarge)
                        owned.forEach { b ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { into = b.id }.padding(vertical = 8.dp, horizontal = 4.dp)
                            ) {
                                Icon(if (b.id == target) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked, contentDescription = null, tint = if (b.id == target) colors.accent else colors.textDim)
                                Text(b.name)
                            }
                        }
                    }
                }
                error?.let { Notice(it, warn = true) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && (sides.get.isEmpty() || target != null),
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            // Marked first: if that fails (offline), nothing has changed and the user can simply try again.
                            social.api.markTradeApplied(trade.id)
                            val left = collectionRepository.applyTrade(tradeChanges(trade, me, target ?: "", givenFrom))
                            social.refresh()
                            if (left.isEmpty()) onClose() else short = left
                        } catch (e: Exception) {
                            error = e.message
                        } finally {
                            busy = false
                        }
                    }
                }
            ) { Text("Update binders", color = colors.accent) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel", color = colors.textMuted) } }
    )
}

/**
 * Proposes a trade to a friend: cards from their shared binders, and optionally cards from the
 * user's own binders in return. A counter-offer starts from their trade turned around; sending it
 * closes theirs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeComposerScreen(
    social: SocialRepository,
    collectionRepository: CollectionRepository,
    friendId: String,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onSent: () -> Unit
) {
    val colors = LocalAppColors.current
    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("Propose a trade", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.accent) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.readableWidth(680.dp)) {
                SocialGate(social, onSignIn) { overview -> Composer(social, collectionRepository, overview, friendId, onSent) }
            }
        }
    }
}

@Composable
private fun Composer(social: SocialRepository, collectionRepository: CollectionRepository, overview: Overview, friendId: String, onSent: () -> Unit) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val friend = overview.person(friendId)
    if (friend == null || !overview.isFriend(friendId)) {
        EmptyState(Icons.Filled.PersonOff, "You can only trade with friends.")
        return
    }
    // The draft lives in the repository, so it survives the screen turning.
    var draft by remember { mutableStateOf(social.draft?.takeIf { it.to == friendId } ?: SocialRepository.TradeDraft(to = friendId)) }
    fun update(next: SocialRepository.TradeDraft) { draft = next; social.draft = next }
    val replying = draft.replyTo?.let { id -> overview.trades.firstOrNull { it.id == id && it.status == TradeStatus.OPEN } }
    var picking by remember { mutableStateOf<Boolean?>(null) } // true: their binders, false: mine
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { PersonRow(friend, detail = if (replying != null) "Counter-offer" else null) }
        item { SectionHeader(if (draft.want.isEmpty()) "You ask for" else "You ask for · ${draft.want.cardTotal()}", action = "Pick cards", onAction = { picking = true }) }
        item { TradeCardList(draft.want, "Nothing yet — pick from ${friend.displayName}'s shared binders.") { c -> update(draft.copy(want = draft.want.filterNot { it.key == c.key })) } }
        item { SectionHeader(if (draft.give.isEmpty()) "You offer" else "You offer · ${draft.give.cardTotal()}", action = "Pick cards", onAction = { picking = false }) }
        item { TradeCardList(draft.give, "Nothing — or pick cards from your binders to offer.") { c -> update(draft.copy(give = draft.give.filterNot { it.key == c.key })) } }
        item {
            OutlinedTextField(
                value = draft.message,
                onValueChange = { update(draft.copy(message = it.take(500))) },
                label = { Text("Message (optional)") },
                placeholder = { Text("e.g. Can bring them on Friday") },
                colors = socialFieldColors(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
        error?.let { item { Notice(it, warn = true) } }
        item {
            GoldButton(
                if (busy) "Sending…" else if (replying != null) "Send counter-offer" else "Send trade request",
                {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            social.api.proposeTrade(friendId, draft.want, draft.give, draft.message.trim(), replying?.id)
                            social.draft = null
                            social.refresh()
                            onSent()
                        } catch (e: Exception) {
                            error = e.message
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy && (draft.want.isNotEmpty() || draft.give.isNotEmpty()),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                icon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Text(
                "Nothing moves until you both agree — then each of you updates your own binders.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textDim,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
        }
    }

    picking?.let { theirs ->
        val shared = overview.sharedWithMe.filter { it.owner == friendId && it.kind == ShareKind.COLLECTION }
        var binders by remember { mutableStateOf<List<Collection>?>(null) }
        val myBinders by collectionRepository.collectionsFlow.collectAsState(initial = emptyList())
        LaunchedEffect(theirs) {
            if (theirs) binders = shared.mapNotNull { s ->
                runCatching { social.api.sharedItem(friendId, ShareKind.COLLECTION, s.itemId) }.getOrNull()
                    ?.let { runCatching { localMoshi.adapter(Collection::class.java).fromJson(it.data) }.getOrNull() }
            }
        }
        Dialog(onDismissRequest = { picking = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(Modifier.fillMaxSize().background(colors.bg)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(if (theirs) "${friend.displayName}'s binders" else "Your binders", style = MaterialTheme.typography.titleLarge)
                        Text(if (theirs) "Pick what you'd like" else "Pick what to offer", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    }
                    GoldButton("Done", { picking = null })
                }
                val list = if (theirs) binders else myBinders.filter { it.type != "WISHLIST" }
                when {
                    theirs && shared.isEmpty() -> Notice("${friend.displayName} hasn't shared a binder with you.", Modifier.padding(16.dp))
                    list == null -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = colors.accent) }
                    list.isEmpty() -> Notice("You have no binders yet.", Modifier.padding(16.dp))
                    else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        list.forEach { b ->
                            item(key = "h-${b.id}") { Text(b.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp)) }
                            if (b.entries.isEmpty()) item(key = "e-${b.id}") { Text("This binder is empty.", style = MaterialTheme.typography.bodySmall, color = colors.textDim) }
                            binderPicker(b.id, b.entries.sortedBy { it.name }, if (theirs) draft.want else draft.give) { next ->
                                update(if (theirs) draft.copy(want = next) else draft.copy(give = next))
                            }
                        }
                    }
                }
            }
        }
    }
}
