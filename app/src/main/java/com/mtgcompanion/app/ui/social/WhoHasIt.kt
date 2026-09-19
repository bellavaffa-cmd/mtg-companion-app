package com.mtgcompanion.app.ui.social

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
import java.util.Locale

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

private fun usd(v: Double) = String.format(Locale.US, "$%.2f", v)

/**
 * Both sides' total value, and whether that's roughly even: within $2, or 10% of the bigger side.
 * Scryfall's prices are a guide (market prices, updated daily), not an appraisal.
 */
@Composable
fun TradeValue(get: List<TradeCard>, give: List<TradeCard>) {
    if (get.isEmpty() && give.isEmpty()) return
    val colors = LocalAppColors.current
    val ids = (get + give).map { it.scryfallId }.distinct().sorted()
    var prices by remember { mutableStateOf<Map<String, Pair<Double?, Double?>>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(ids) {
        failed = false
        try {
            prices = CardRepository().getCardsByIds(ids).associate { it.id to (it.prices?.usd?.toDoubleOrNull() to it.prices?.usdFoil?.toDoubleOrNull()) }
        } catch (e: Exception) {
            failed = true
        }
    }
    val p = prices
    if (p == null) {
        if (failed) Text("Couldn't load prices.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        return
    }
    fun total(cards: List<TradeCard>): Pair<Double, Int> {
        var sum = 0.0
        var unpriced = 0
        for (c in cards) {
            val (normal, foil) = p[c.scryfallId] ?: (null to null)
            val each = if (c.foil) foil ?: normal else normal ?: foil
            if (each == null) unpriced += c.quantity else sum += each * c.quantity
        }
        return sum to unpriced
    }
    val (mine, u1) = total(get)
    val (theirs, u2) = total(give)
    // Nothing priced: no verdict to give.
    if (mine == 0.0 && theirs == 0.0) {
        Text("There are no prices for these cards, so their value can't be compared.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted, modifier = Modifier.padding(top = 8.dp))
        return
    }
    val diff = mine - theirs
    val fair = kotlin.math.abs(diff) <= maxOf(2.0, 0.1 * maxOf(mine, theirs))
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).background(colors.surface2).padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row { Text("You get", color = colors.textPrimary, modifier = Modifier.weight(1f)); Text(usd(mine), color = colors.textPrimary) }
        Row { Text("You give", color = colors.textPrimary, modifier = Modifier.weight(1f)); Text(usd(theirs), color = colors.textPrimary) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
            Icon(if (fair) Icons.Filled.Balance else Icons.Filled.Warning, contentDescription = null, tint = if (fair) Color(0xFF5DCAA5) else colors.accent, modifier = Modifier.size(18.dp))
            Text(
                when {
                    fair && kotlin.math.abs(diff) < 0.005 -> "Even — a fair trade"
                    fair -> "Within ${usd(kotlin.math.abs(diff))} — a fair trade"
                    diff > 0 -> "You get ${usd(diff)} more"
                    else -> "You give ${usd(-diff)} more"
                },
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
        }
        if (u1 + u2 > 0) {
            Text("${u1 + u2} ${if (u1 + u2 == 1) "card has no price and is" else "cards have no price and are"} left out.", style = MaterialTheme.typography.labelSmall, color = colors.textDim)
        }
    }
}
