package com.mtgcompanion.app.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.CollectionRepository
import com.mtgcompanion.app.data.social.BAG_SOURCE_NAME
import com.mtgcompanion.app.data.social.CardPrice
import com.mtgcompanion.app.data.social.NightCard
import com.mtgcompanion.app.data.social.NightList
import com.mtgcompanion.app.data.social.NightSource
import com.mtgcompanion.app.data.social.SocialArea
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.data.social.TableState
import com.mtgcompanion.app.data.social.Trade
import com.mtgcompanion.app.data.social.TradeCard
import com.mtgcompanion.app.data.social.TradeNight
import com.mtgcompanion.app.data.social.WANT_DECK
import com.mtgcompanion.app.data.social.WANT_WISHLIST
import com.mtgcompanion.app.data.social.bagNamesOf
import com.mtgcompanion.app.data.social.bringCards
import com.mtgcompanion.app.data.social.defaultSources
import com.mtgcompanion.app.data.social.nightDecksUse
import com.mtgcompanion.app.data.social.nightWantsOf
import com.mtgcompanion.app.data.social.priceIdsNeeded
import com.mtgcompanion.app.data.social.suggestedTrades
import com.mtgcompanion.app.data.social.nightTableLine
import com.mtgcompanion.app.data.social.theyWantFromYou
import com.mtgcompanion.app.data.social.tradeTable
import com.mtgcompanion.app.data.social.wantedHere
import com.mtgcompanion.app.ui.common.a11yHeading
import com.mtgcompanion.app.ui.common.rememberMoney
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * "Trades" on a game night (data/social/TradeNights.kt): for someone who answered Going — Bring for
 * trades (binders or the event bag, shared with the others going only when they say so), Wanted here,
 * They want from you, Suggested trades (a fair bundle per person; Propose opens the trade composer
 * filled in, tied to the night) and the Trade table (the night's trades; "Swapped" updates the
 * binders the usual way, UpdateBindersDialog). Nothing until the server has trade nights
 * (20261008100000_trade_nights.sql). The web app's social/TradeNightSection.tsx.
 */
@Composable
fun TradeNightSection(
    social: SocialRepository,
    collectionRepository: CollectionRepository,
    nightId: String,
    me: String,
    going: Boolean,
    collections: List<Collection>,
    decks: List<Deck>,
    /** Propose a trade: who, what to ask for, what to give, their name and what they bring (to pick from). */
    onPropose: (to: String, want: List<TradeCard>, give: List<TradeCard>, name: String, theirCards: List<NightCard>) -> Unit,
    onOpenTrades: () -> Unit
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val available by social.tradeNights.available.collectAsState()
    val overview by social.overview.collectAsState()
    val changes = social.changes.collectAsState().value
    val nightChanges = changes[SocialArea.NIGHTS] ?: 0
    val tradeChanges = changes[SocialArea.TRADES] ?: 0
    var data by remember { mutableStateOf<TradeNight?>(null) }
    var picked by remember { mutableStateOf<List<NightSource>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var updating by remember { mutableStateOf<Trade?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(nightId, nightChanges, tradeChanges, going, reload) {
        if (!social.tradeNights.check()) return@LaunchedEffect
        try {
            data = social.tradeNights.night(nightId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message
        }
    }

    val binders = remember(collections) { collections.filter { it.type != "WISHLIST" } }
    val bagNames = remember(decks) { bagNamesOf(decks) }
    val decksUse = remember(decks) { nightDecksUse(decks) }
    val wants = remember(collections, decks) { nightWantsOf(collections, decks) }
    val defaults = remember(collections) { defaultSources(collections) }
    val d = data
    // What's picked: the user's choice here, else what they shared, else their trade binder.
    val sources = picked ?: d?.mine?.sources ?: defaults
    val myCards = remember(collections, sources, bagNames, decksUse) { bringCards(collections, sources, bagNames, decksUse) }
    val meList = remember(me, myCards, wants) { NightList(me, "You", myCards, wants) }
    val others = remember(d) { d?.others.orEmpty().map { it.asList() } }
    val ids = remember(meList, others) { priceIdsNeeded(meList, others) }
    var prices by remember { mutableStateOf<Pair<List<String>, Map<String, CardPrice>>?>(null) }
    var pricesFailed by remember { mutableStateOf(false) }
    LaunchedEffect(ids) {
        if (ids.isEmpty()) { prices = ids to emptyMap(); return@LaunchedEffect }
        try {
            prices = ids to CardRepository().getCardsByIds(ids).associate { it.id to CardPrice(it.prices?.usd?.toDoubleOrNull(), it.prices?.usdFoil?.toDoubleOrNull()) }
            pricesFailed = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            pricesFailed = true
        }
    }
    val book = prices?.takeIf { p -> ids.all { it in p.second } || p.first == ids }?.second
    val suggestions = remember(meList, others, book) { book?.let { suggestedTrades(meList, others, it) }.orEmpty() }
    val money = rememberMoney()

    if (available != true || d == null) return
    val table = tradeTable(d.trades, me)
    if (!d.going && table.isEmpty()) return
    val mine = d.mine
    val here = wantedHere(wants, others)
    val theyWant = theyWantFromYou(myCards, others)
    fun nameOf(id: String) = d.others.firstOrNull { it.user.userId == id }?.user?.displayName ?: overview?.person(id)?.displayName ?: "Someone"
    fun names(cards: List<String>) = cards.joinToString(", ")
    fun sameSource(a: NightSource, b: NightSource) = if (a.isBag) b.isBag else !b.isBag && a.id == b.id
    fun toggle(s: NightSource) {
        picked = if (sources.any { sameSource(it, s) }) sources.filterNot { sameSource(it, s) } else sources + s
    }
    val changed = mine != null && (sources.map { it.id }.toSet() != mine.sources.map { it.id }.toSet() || mine.cards.size != myCards.size)

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(14.dp)
    ) {
        Text("Trades", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, modifier = Modifier.a11yHeading())
        if (d.going && d.open) {
            TnLabel("Bring for trades")
            Text(
                if (mine != null) "Shared with the people going · ${mine.cards.size} ${if (mine.cards.size == 1) "line" else "lines"}"
                else "Pick what to show the others going. Nothing is shared until you do.",
                style = MaterialTheme.typography.bodySmall, color = colors.textMuted
            )
            binders.forEach { b ->
                val s = NightSource.binder(b.id, b.name)
                TnSourceRow(b.name, sources.any { sameSource(it, s) }) { toggle(s) }
            }
            if (bagNames.isNotEmpty()) {
                val s = NightSource.bag()
                TnSourceRow("$BAG_SOURCE_NAME · ${bagNames.size} ${if (bagNames.size == 1) "card" else "cards"} to bring", sources.any { sameSource(it, s) }) { toggle(s) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                GoldButton(
                    if (mine != null) "Update what I bring" else "Share ${myCards.size} ${if (myCards.size == 1) "line" else "lines"} with the table",
                    {
                        busy = true
                        error = null
                        scope.launch {
                            try {
                                social.tradeNights.share(nightId, sources, myCards, wants)?.let { data = it }
                                picked = null
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                error = e.message
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy && myCards.size + wants.size > 0 && (mine == null || changed || picked != null),
                    modifier = Modifier.weight(1f)
                )
                if (mine != null) LineButton("Stop sharing", {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            social.tradeNights.stop(nightId)
                            picked = null
                            reload++
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = e.message
                        } finally {
                            busy = false
                        }
                    }
                }, enabled = !busy)
            }
            TnNote("Only the people going to this night see it, and only until the night is over. Your wants (Wishlist, cards your decks are missing, goals) go with it.")
            error?.let { Notice(it, warn = true) }

            TnLabel("Wanted here")
            if (here.isEmpty()) TnNote(if (others.isEmpty()) "Nobody going has shared their cards yet." else "Nothing you want is coming tonight.")
            here.forEach { w ->
                TnCardLine(w.name, w.from.joinToString(", ") { it.name } + " · " + when {
                    w.weight >= WANT_WISHLIST -> "Wishlist"
                    w.weight >= WANT_DECK -> "A deck needs it"
                    else -> "Goal"
                })
            }

            TnLabel("They want from you")
            if (theyWant.isEmpty()) TnNote("Nobody going wants what you're bringing yet.")
            theyWant.forEach { t -> TnCardLine(t.name, names(t.cards.map { it.card.name })) }

            TnLabel("Suggested trades")
            when {
                book == null && pricesFailed -> TnNote("Couldn't load prices.")
                book == null -> TnNote("Looking up prices…")
                suggestions.isEmpty() -> TnNote("No fair trade to suggest yet — fair means within \$2 or a tenth either way.")
            }
            suggestions.forEach { s ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface2).padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary, modifier = Modifier.weight(1f))
                        Text("${money.format(s.getValue)} ⇄ ${money.format(s.giveValue)}", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    }
                    Text("You get: " + names(s.get.map { it.name }), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    Text("You give: " + names(s.give.map { it.name }), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    GoldButton("Propose this trade", {
                        onPropose(s.userId, s.get, s.give, s.name, d.others.firstOrNull { it.user.userId == s.userId }?.cards.orEmpty())
                    }, modifier = Modifier.fillMaxWidth().height(40.dp))
                }
            }
        }
        if (d.going && !d.open) TnNote("Lists are closed — the night is over or called off.")

        if (table.isNotEmpty()) {
            TnLabel("Trade table · ${nightTableLine(table)}")
            table.forEach { r ->
                val mineGive = if (r.trade.fromUser == me) r.trade.give else r.trade.want
                val mineGet = if (r.trade.fromUser == me) r.trade.want else r.trade.give
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surface2).padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    val done = r.state == TableState.DONE
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(if (done) colors.accent else colors.surface3)
                    ) { if (done) Icon(Icons.Filled.Check, contentDescription = "Done", tint = colors.onAccent, modifier = Modifier.size(16.dp)) }
                    Column(Modifier.weight(1f)) {
                        Text(
                            nameOf(r.other), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = colors.textPrimary,
                            textDecoration = if (done) TextDecoration.LineThrough else null
                        )
                        Text(
                            "You give ${names(mineGive.map { it.name }).ifEmpty { "nothing" }} · you get ${names(mineGet.map { it.name }).ifEmpty { "nothing" }}",
                            style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 3, overflow = TextOverflow.Ellipsis
                        )
                    }
                    when (r.state) {
                        TableState.AGREED -> GoldButton("Swapped", { updating = r.trade })
                        TableState.WAITING -> LineButton("Waiting", onOpenTrades)
                        TableState.DONE -> Text("Done", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    }
                }
            }
        }
    }

    updating?.let { t ->
        UpdateBindersDialog(social, collectionRepository, t, me, nameOf(if (t.fromUser == me) t.toUser else t.fromUser)) {
            updating = null
            reload++
        }
    }
}

@Composable
private fun TnLabel(text: String) {
    val colors = LocalAppColors.current
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = colors.textMuted, modifier = Modifier.padding(top = 6.dp).a11yHeading())
}

@Composable
private fun TnNote(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = LocalAppColors.current.textDim)
}

@Composable
private fun TnCardLine(name: String, detail: String) {
    val colors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(name, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp).weight(1f, fill = false))
    }
}

@Composable
private fun TnSourceRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(role = Role.Checkbox, onClick = onToggle).padding(vertical = 2.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = colors.accent))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.padding(start = 6.dp))
    }
}
