package com.mtgcompanion.app.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.cardsIn
import com.mtgcompanion.app.data.namesDecksUse
import com.mtgcompanion.app.data.placesOf
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.data.social.TonightCard
import com.mtgcompanion.app.data.social.TonightPlayer
import com.mtgcompanion.app.data.social.TradeCard
import com.mtgcompanion.app.data.social.TradeMatch
import com.mtgcompanion.app.data.social.addFriendLine
import com.mtgcompanion.app.data.social.tonightLine
import com.mtgcompanion.app.data.social.tradeMatchesTonight
import com.mtgcompanion.app.ui.theme.LocalAppColors

/**
 * "Trade matches tonight" (data/social/TradeTonight.kt), on Game night and in Pack your bag: for
 * each friend at the table, the cards on their wishlist the user has spare or for trade (and where
 * each is), and theirs for trade the user wants — with Propose a trade (both sides filled in) and
 * Bring them (onto the "Bring to game night" pull list, as Friends want these does). Guests are named
 * with "Add … as a friend to see what they want". Nothing when signed out, or before the server has
 * trade matches. The web app's social/TradeMatchesTonight.tsx.
 */
@Composable
fun TradeMatchesTonight(
    social: SocialRepository,
    players: List<TonightPlayer>,
    collections: List<Collection>,
    decks: List<Deck>,
    /** Propose a trade: the friend, what to ask them for, what to give. */
    onPropose: (friend: String, want: List<TradeCard>, give: List<TradeCard>) -> Unit,
    /** Puts the cards on the "Bring to game night" deck, then says which deck that is. */
    onBring: (cards: List<TonightCard>, done: (deckId: String) -> Unit) -> Unit,
    onOpenPullList: (deckId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    val available = rememberSocialMore(social)
    val overview by social.overview.collectAsState()
    var matches by remember { mutableStateOf<List<TradeMatch>?>(null) }
    var brought by remember { mutableStateOf<Pair<String, String>?>(null) } // friend to deck id
    LaunchedEffect(available) {
        if (available != true) return@LaunchedEffect
        if (social.overview.value == null) runCatching { social.refresh() }
        matches = runCatching { social.more.tradeMatches() }.getOrDefault(emptyList())
    }
    val people = overview
    val found = matches
    if (available != true || players.isEmpty() || people == null || found == null) return
    val tonight = remember(people, found, players, collections, decks) {
        val places = placesOf(collections)
        val placed = places.flatMap { cardsIn(collections, it.id) }
        tradeMatchesTonight(
            players, people.acceptedFriends.map { it.userId }.toSet(), found, namesDecksUse(decks), placed, places.associate { it.id to it.name }
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            "TRADE MATCHES TONIGHT",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = colors.textMuted,
            modifier = Modifier.semantics { heading() }
        )
        tonight.matches.forEach { m ->
            val done = brought?.takeIf { it.first == m.friend }?.second
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(m.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
                Text(tonightLine(m), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                m.theyWant.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(c.card.name, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(c.where, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, modifier = Modifier.padding(start = 10.dp))
                    }
                }
                if (m.theyHave.isNotEmpty()) {
                    Text("For trade, that you want: " + m.theyHave.joinToString(", ") { it.name }, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { onPropose(m.friend, m.theyHave, m.theyWant.map { it.card }) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) { Text("Propose a trade", fontWeight = FontWeight.Bold, maxLines = 1) }
                    if (m.theyWant.isNotEmpty()) Button(
                        onClick = {
                            if (done != null) onOpenPullList(done)
                            else onBring(m.theyWant) { deckId -> brought = m.friend to deckId }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.surface2, contentColor = colors.textPrimary),
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) { Text(if (done != null) "Open pull list" else "Bring them", maxLines = 1) }
                }
                if (done != null) Text("On the \"Bring to game night\" pull list.", style = MaterialTheme.typography.labelMedium, color = colors.textDim)
            }
        }
        if (tonight.nothing.isNotEmpty()) {
            Text("Nothing to trade with ${tonight.nothing.joinToString(", ")} tonight.", style = MaterialTheme.typography.labelMedium, color = colors.textDim)
        }
        tonight.notFriends.forEach { n ->
            Text(addFriendLine(n) + ".", style = MaterialTheme.typography.labelMedium, color = colors.textDim)
        }
    }
}
