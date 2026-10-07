package com.mtgcompanion.app.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.runningSeason
import com.mtgcompanion.app.data.seasonTable
import com.mtgcompanion.app.data.social.CardPrice
import com.mtgcompanion.app.data.social.Overview
import com.mtgcompanion.app.data.social.Pod
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.data.social.TradeMatch
import com.mtgcompanion.app.data.social.WantFromYou
import com.mtgcompanion.app.data.social.friendsWantFromYou
import com.mtgcompanion.app.data.social.peopleLine
import com.mtgcompanion.app.data.social.podLine
import com.mtgcompanion.app.data.social.wantFromYouLine
import com.mtgcompanion.app.ui.common.rememberMoney
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.CancellationException

// Pieces of the Friends tab (FriendsHub.kt has their logic): two-way trade matches shared by People
// and Trades, what friends want from the user across all binders, and the Your pods row with each
// pod's running league season. The web app's twin is src/social/FriendsHubUi.tsx.

/** Two-way trade matches with friends (asked once a minute at most); empty without social_more. */
@Composable
fun rememberTradeMatches(social: SocialRepository, overview: Overview): List<TradeMatch> {
    val available = rememberSocialMore(social)
    var matches by remember { mutableStateOf<List<TradeMatch>>(emptyList()) }
    LaunchedEffect(available, overview) { if (available == true) matches = TradeMatchCache.get(social) }
    return matches.filter { overview.person(it.friend) != null }
}

/** What friends want from the user across all binders, priced at today's prices once they're in. */
@Composable
fun rememberWantsFromYou(social: SocialRepository, overview: Overview, collections: List<Collection>): List<WantFromYou> {
    val matches = rememberTradeMatches(social, overview)
    val ids = remember(matches) { matches.flatMap { m -> m.theyWant.map { it.scryfallId } }.distinct().sorted() }
    var prices by remember { mutableStateOf<Map<String, CardPrice>>(emptyMap()) }
    LaunchedEffect(ids) {
        if (ids.isEmpty()) return@LaunchedEffect
        try {
            prices = CardRepository().getCardsByIds(ids).associate { it.id to CardPrice(it.prices?.usd?.toDoubleOrNull(), it.prices?.usdFoil?.toDoubleOrNull()) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // No prices: the lines go without a value.
        }
    }
    val names = remember(collections) { collections.associate { it.id to it.name } }
    return remember(matches, prices, names) { friendsWantFromYou(matches, { names[it] }, prices) }
}

/** A pod's running league season and the user's place in it; [season] null when none is running. */
data class PodLeague(val season: String?, val rank: Int?)

/** Each pod's league (League.kt), by pod id; a pod whose seasons can't be loaded is left out. */
@Composable
fun rememberPodLeagues(social: SocialRepository, pods: List<Pod>, me: String): Map<String, PodLeague> {
    var leagues by remember { mutableStateOf<Map<String, PodLeague>>(emptyMap()) }
    LaunchedEffect(pods.map { it.id }, me) {
        val out = mutableMapOf<String, PodLeague>()
        for (pod in pods) {
            try {
                val season = runningSeason(social.api.podSeasons(pod.id))
                out[pod.id] = if (season == null) PodLeague(null, null)
                else PodLeague(season.name, seasonTable(season, social.api.podGames(pod.id)).standings.firstOrNull { it.userId == me }?.rank)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Leagues not on the server yet, or offline: the pod shows its people only.
            }
        }
        leagues = out
    }
    return leagues
}

/** Your pods, side by side: each pod's name and "5 people · Season 2 · you're 2nd". */
@Composable
fun PodsRow(pods: List<Pod>, leagues: Map<String, PodLeague>, onOpen: (Pod) -> Unit) {
    val colors = LocalAppColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        pods.forEach { pod ->
            val league = leagues[pod.id]
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.widthIn(min = 150.dp, max = 240.dp).clip(RoundedCornerShape(16.dp)).background(colors.surface)
                    .clickable(role = Role.Button) { onOpen(pod) }.padding(12.dp)
            ) {
                Text(pod.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (league == null) peopleLine(pod.members.size) else podLine(pod.members.size, league.season, league.rank),
                    style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** What friends want from you: a line per friend (cards, where, value) and Make offers. */
@Composable
fun WantsFromYouCard(overview: Overview, wants: List<WantFromYou>, onOpen: (TradeMatch) -> Unit) {
    val colors = LocalAppColors.current
    val money = rememberMoney()
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        wants.take(6).forEach { w ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { onOpen(w.match) }.padding(vertical = 8.dp)
            ) {
                Text(wantFromYouLine(overview.person(w.friend)?.displayName ?: "A friend", w.cards), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                w.where?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)) }
                w.value?.let { Text(money.format(it, whole = true), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold) }
            }
        }
        // Make offers starts with the friend who wants the most; each line starts its own.
        Text(
            "Make offers",
            style = MaterialTheme.typography.labelLarge,
            color = colors.accent,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { wants.firstOrNull()?.let { onOpen(it.match) } }.padding(vertical = 12.dp, horizontal = 2.dp)
        )
    }
}
