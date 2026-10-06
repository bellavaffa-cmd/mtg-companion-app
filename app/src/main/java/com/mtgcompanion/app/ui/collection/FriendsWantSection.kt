package com.mtgcompanion.app.ui.collection

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
import com.mtgcompanion.app.data.PlacedCard
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.data.social.TradeCard
import com.mtgcompanion.app.data.social.TradeMatch
import com.mtgcompanion.app.data.social.WantedHere
import com.mtgcompanion.app.data.social.friendsWantHere
import com.mtgcompanion.app.data.social.hasLine
import com.mtgcompanion.app.data.social.wantedAsTrade
import com.mtgcompanion.app.data.social.wantedWhere
import com.mtgcompanion.app.data.social.wantsLine
import com.mtgcompanion.app.ui.common.rememberMoney
import com.mtgcompanion.app.ui.social.rememberSocialMore
import com.mtgcompanion.app.ui.theme.LocalAppColors

/**
 * "Friends want these" on a binder (data/social/FriendsWant.kt): the friends whose wishlists want
 * cards in it, from the trade matches — each with how many and what they're worth, every card with its
 * pocket, what they have that the user wants, Propose a trade and, when it goes both ways, Bring to
 * game night. Nothing at all when signed out, with no friends' wants here, or before the server has
 * trade matches. The web app's FriendsWantSection in PlacePage.tsx.
 */
@Composable
fun FriendsWantSection(
    social: SocialRepository,
    cards: List<PlacedCard>,
    priceOf: (PlacedCard) -> Double?,
    /** Propose a trade: the friend, what to ask them for, what to give. */
    onPropose: (friend: String, want: List<TradeCard>, give: List<TradeCard>) -> Unit,
    onBringToGameNight: (List<WantedHere>) -> Unit
) {
    val colors = LocalAppColors.current
    val money = rememberMoney()
    val available = rememberSocialMore(social)
    val overview by social.overview.collectAsState()
    var matches by remember { mutableStateOf<List<TradeMatch>>(emptyList()) }
    LaunchedEffect(available) {
        if (available != true) return@LaunchedEffect
        if (social.overview.value == null) runCatching { social.refresh() }
        matches = runCatching { social.more.tradeMatches() }.getOrDefault(emptyList())
    }
    val people = overview ?: return
    val wants = friendsWantHere(matches, cards, priceOf).filter { people.person(it.friend) != null }
    if (wants.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Text(
            "FRIENDS WANT THESE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = colors.textMuted,
            modifier = Modifier.semantics { heading() }
        )
        wants.forEach { w ->
            val name = people.person(w.friend)?.displayName ?: "A friend"
            val both = w.theyHave.isNotEmpty()
            val priced = w.cards.any { it.price != null }
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary, modifier = Modifier.weight(1f).semantics { heading() })
                    Text(wantsLine(w.cards.size, if (priced) money.format(w.value, whole = true) else null), style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                }
                w.cards.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(c.card.entry.name, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(wantedWhere(c), style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, modifier = Modifier.padding(start = 10.dp))
                    }
                }
                hasLine(name, w.theyHave)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textMuted) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { onPropose(w.friend, w.theyHave, wantedAsTrade(w.cards)) },
                        shape = RoundedCornerShape(12.dp),
                        colors = if (both) ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)
                        else ButtonDefaults.buttonColors(containerColor = colors.surface2, contentColor = colors.textPrimary),
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) { Text("Propose a trade", fontWeight = FontWeight.Bold, maxLines = 1) }
                    if (both) Button(
                        onClick = { onBringToGameNight(w.cards) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.surface2, contentColor = colors.textPrimary),
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) { Text("Bring to game night", maxLines = 1) }
                }
            }
        }
        if (wants.any { it.theyHave.isNotEmpty() }) Text(
            "\"Bring to game night\" adds the cards to a pull list, so they're in your bag on the night.",
            style = MaterialTheme.typography.labelMedium,
            color = colors.textDim
        )
    }
}
