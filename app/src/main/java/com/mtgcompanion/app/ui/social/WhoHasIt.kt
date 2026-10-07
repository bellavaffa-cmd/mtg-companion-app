package com.mtgcompanion.app.ui.social

import com.mtgcompanion.app.ui.common.rememberMoney
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.social.SharedCardHit
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.data.social.TradeCard
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.theme.LocalAppColors
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import com.mtgcompanion.app.data.DeckRepository
import com.mtgcompanion.app.data.namesDecksUse
import com.mtgcompanion.app.data.social.CardPrice
import com.mtgcompanion.app.data.social.TradeMatch
import com.mtgcompanion.app.data.social.TradeSide
import com.mtgcompanion.app.data.social.candidatesFor
import com.mtgcompanion.app.data.social.evenOut
import com.mtgcompanion.app.data.social.evenOutTitle
import com.mtgcompanion.app.data.social.fairness
import com.mtgcompanion.app.data.social.shortSide
import com.mtgcompanion.app.data.social.unpricedLine
import com.mtgcompanion.app.data.social.verdictLine
import kotlin.math.roundToInt

/**
 * "Who has it?": a deck's missing cards ([names]), each with the friends whose shared binders hold a
 * copy (who_has_cards on the server), and an Ask button that starts a trade for it.
 */
@Composable
fun WhoHasItDialog(social: SocialRepository, names: List<String>, onAsk: (owner: String) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalAppColors.current
    var hits by remember { mutableStateOf<List<SharedCardHit>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(names) {
        if (names.isEmpty()) { hits = emptyList(); return@LaunchedEffect }
        try {
            hits = social.api.whoHasCards(names.distinct().take(250))
        } catch (e: Exception) {
            error = e.message ?: "Something went wrong."
        }
    }
    val overview = social.overview.value
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("Who has it?", color = colors.accentLight) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val found = hits
                when {
                    social.userId == null -> Text("Sign in to see what your friends have.", color = colors.textMuted)
                    error != null -> Text(error!!, color = colors.error)
                    found == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(20.dp))
                        Text("Looking through your friends' binders…", color = colors.textMuted)
                    }
                    else -> {
                        val byName = found.groupBy { it.name.lowercase() }
                        val have = names.filter { byName.containsKey(it.lowercase()) }
                        val nobody = names.filterNot { byName.containsKey(it.lowercase()) }
                        if (have.isEmpty()) Text("None of your friends' shared binders have these cards.", color = colors.textMuted)
                        have.forEach { name ->
                            val cardHits = byName.getValue(name.lowercase())
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface2).padding(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    AsyncImage(
                                        model = cardHits.first().imageUrl.toArtCropUrl(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(width = 44.dp, height = 32.dp).clip(RoundedCornerShape(6.dp))
                                    )
                                    Text(name, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                                }
                                cardHits.forEach { h ->
                                    val person = overview?.people?.get(h.owner)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Avatar(person, 26.dp)
                                        Text(
                                            "${person?.displayName ?: "A friend"} has ${h.quantity + h.foilQuantity} · ${h.itemName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.textPrimary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = {
                                            social.draft = SocialRepository.TradeDraft(
                                                to = h.owner,
                                                want = listOf(TradeCard(h.scryfallId, h.name, h.imageUrl, foil = h.quantity == 0 && h.foilQuantity > 0, quantity = 1, collectionId = h.itemId))
                                            )
                                            onAsk(h.owner)
                                        }) { Text("Ask", color = colors.accent) }
                                    }
                                }
                            }
                        }
                        if (nobody.isNotEmpty()) {
                            Text("No friend has these", fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                            Text(nobody.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                        }
                        Text("Only binders your friends share with you are searched — not their decks or wishlists.", style = MaterialTheme.typography.labelSmall, color = colors.textDim)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = colors.textMuted) } }
    )
}


/** The trade matches, asked once a minute at most: every trade on the Trades screen shares one answer. */
internal object TradeMatchCache {
    private var at = 0L
    private var list: List<TradeMatch>? = null

    suspend fun get(social: SocialRepository): List<TradeMatch> {
        list?.takeIf { System.currentTimeMillis() - at < 60_000 }?.let { return it }
        val fresh = runCatching { social.more.tradeMatches() }.getOrNull() ?: return emptyList()
        list = fresh
        at = System.currentTimeMillis()
        return fresh
    }
}

/**
 * Is the trade fair? Both sides' total value at today's prices (data/social/TradeFairness.kt), the
 * difference, a balance bar, and — when [onAdd] is given and it's uneven — up to three cards from
 * [friend]'s trade matches that would even it out, one tap to add. Fair is within $2, or a tenth of
 * the bigger side. Scryfall's prices are a guide (market prices, updated daily), not an appraisal.
 * The web app's social/TradeValue.tsx.
 */
@Composable
fun TradeValue(
    get: List<TradeCard>,
    give: List<TradeCard>,
    social: SocialRepository? = null,
    /** The other person (a user id): their trade matches give the cards to even it out. */
    friend: String? = null,
    friendName: String? = null,
    addLabel: String = "Add",
    /** Puts a suggested card on the trade: on the user's ask (WANT) or offer (GIVE). */
    onAdd: ((TradeSide, TradeCard) -> Unit)? = null
) {
    if (get.isEmpty() && give.isEmpty()) return
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val deckRepository = remember { DeckRepository(context.applicationContext) }
    val decks by deckRepository.decksFlow.collectAsState(initial = emptyList())
    val decksUse = remember(decks) { namesDecksUse(decks) }
    val available = if (social != null) rememberSocialMore(social) else false
    var match by remember { mutableStateOf<TradeMatch?>(null) }
    LaunchedEffect(available, friend, onAdd != null) {
        if (available != true || social == null || friend == null || onAdd == null) return@LaunchedEffect
        match = TradeMatchCache.get(social).firstOrNull { it.friend == friend }
    }
    val extra = remember(match, decksUse) {
        candidatesFor(TradeSide.WANT, match, decksUse) + candidatesFor(TradeSide.GIVE, match, decksUse)
    }
    val ids = (get + give + extra).map { it.scryfallId }.distinct().sorted()
    var loaded by remember { mutableStateOf<Pair<List<String>, Map<String, CardPrice>>?>(null) }
    var failed by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(ids) {
        try {
            val book = CardRepository().getCardsByIds(ids).associate { it.id to CardPrice(it.prices?.usd?.toDoubleOrNull(), it.prices?.usdFoil?.toDoubleOrNull()) }
            loaded = ids to book
            failed = null
        } catch (e: Exception) {
            failed = ids
        }
    }
    val last = loaded
    // The last answer still does while it has every card (a card taken off), so the totals don't blink.
    val prices = when {
        last != null && (last.first == ids || ids.all { it in last.second }) -> last.second
        else -> null
    }
    if (prices == null) {
        Text(
            if (failed == ids) "Couldn't load prices." else "Looking up prices…",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
            modifier = Modifier.padding(top = 8.dp)
        )
        return
    }
    val f = fairness(get, give, prices)
    // Nothing priced: no verdict to give.
    if (f == null) {
        val n = (get + give).sumOf { it.quantity }
        Text(
            (if (n == 1) "This card has no price" else "None of these $n cards have a price") + ", so their value can't be compared.",
            style = MaterialTheme.typography.bodySmall, color = colors.textMuted, modifier = Modifier.padding(top = 8.dp)
        )
        return
    }
    // Worked out in US dollars, shown in the chosen currency.
    val money = rememberMoney()
    val fmt = { v: Double -> money.format(v) }
    val side = shortSide(f)
    val suggestions = if (side != null && onAdd != null) evenOut(f.diff, candidatesFor(side, match, decksUse), get + give, prices) else emptyList()
    val getPct = (f.getShare * 100).roundToInt()
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).background(colors.surface2).padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row { Text("You get", color = colors.textPrimary, modifier = Modifier.weight(1f)); Text(fmt(f.get.sum), color = colors.textPrimary, fontWeight = FontWeight.SemiBold) }
        Row { Text("You give", color = colors.textPrimary, modifier = Modifier.weight(1f)); Text(fmt(f.give.sum), color = colors.textPrimary, fontWeight = FontWeight.SemiBold) }
        // The balance bar: the user's side (accent) against theirs, the middle marked.
        Box(
            Modifier.padding(top = 8.dp).fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(colors.surface3)
                .clearAndSetSemantics { contentDescription = "You get $getPct% of the value, you give ${100 - getPct}%" }
        ) {
            Box(Modifier.fillMaxWidth(f.getShare.toFloat().coerceIn(0f, 1f)).fillMaxHeight().background(colors.accent))
            Box(Modifier.align(Alignment.Center).width(2.dp).fillMaxHeight().background(colors.textPrimary.copy(alpha = 0.55f)))
        }
        Row(Modifier.clearAndSetSemantics { }) {
            Text("You get", style = MaterialTheme.typography.labelSmall, color = colors.textDim, modifier = Modifier.weight(1f))
            Text("You give", style = MaterialTheme.typography.labelSmall, color = colors.textDim)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
            Icon(if (f.fair) Icons.Filled.Balance else Icons.Filled.Warning, contentDescription = null, tint = if (f.fair) colors.success else colors.accent, modifier = Modifier.size(18.dp))
            Text(verdictLine(f, fmt), fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
        }
        unpricedLine(f.unpriced)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = colors.textDim) }
        if (side != null && onAdd != null && suggestions.isNotEmpty()) {
            Text(evenOutTitle(side, friendName ?: "them"), style = MaterialTheme.typography.bodySmall, color = colors.textMuted, modifier = Modifier.padding(top = 8.dp))
            suggestions.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        s.card.name + if (s.card.foil) " · foil" else "",
                        color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    Text(fmt(s.price), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    TextButton(onClick = { onAdd(side, s.card) }) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                        Text(addLabel, color = colors.accent, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }
}
