package com.mtgcompanion.app.ui.lifecounter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/**
 * Picks the commander a seat is playing, for a player without a phone of their own: their name
 * goes into everyone's game records, and its art behind a bare tile.
 */
@Composable
internal fun CommanderPickOverlay(
    playerName: String,
    current: String?,
    onSearch: suspend (String) -> List<ScryfallCard>,
    onPick: (ScryfallCard?) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ScryfallCard>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(400)
        searching = true
        results = onSearch(q).take(20)
        searching = false
    }
    TableOverlay(title = "$playerName's commander", onClose = onDismiss) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TableColors.SurfaceRaised).padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) TableLabel("Commander name", 26.sp, color = TableColors.TextMuted)
                    BasicTextField(value = query, onValueChange = { query = it }, singleLine = true, textStyle = tableText(26.sp), cursorBrush = SolidColor(TableColors.Yellow), modifier = Modifier.fillMaxWidth())
                }
                if (searching) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = TableColors.Yellow)
            }
            if (current != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                    TableLabel("Now: $current", 20.sp, color = TableColors.TextMuted, maxLines = 1, modifier = Modifier.weight(1f))
                    TableLabel("Clear", 22.sp, color = TableColors.Yellow, modifier = Modifier.clickable { onPick(null) }.padding(8.dp))
                }
            }
            LazyColumn(modifier = Modifier.padding(top = 10.dp)) {
                items(results, key = { it.id }) { card ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(14.dp)).background(TableColors.Surface)
                            .clickable { onPick(card) }.padding(8.dp)
                    ) {
                        AsyncImage(
                            model = (card.imageUris?.artCrop ?: card.cardFaces?.firstOrNull()?.imageUris?.artCrop),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(width = 64.dp, height = 46.dp).clip(RoundedCornerShape(10.dp)).background(TableColors.SurfaceRaised)
                        )
                        Column(Modifier.weight(1f)) {
                            TableLabel(card.name, 22.sp, maxLines = 1)
                            card.typeLine?.let { TableLabel(it, 15.sp, color = TableColors.TextMuted, maxLines = 1) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * "This is me": marks a seat as the table owner's, for playing without a phone of their own, and
 * picks the deck their games there are saved to. A seat someone joins from a phone saves its games
 * from that phone instead.
 */
@Composable
internal fun MeSeatOverlay(
    seat: Int,
    decks: List<Deck>,
    currentDeckId: String?,
    isMe: Boolean,
    onPick: (deckId: String) -> Unit,
    onNotMe: () -> Unit,
    onDismiss: () -> Unit
) {
    TableOverlay(title = "Your seat · deck", onClose = onDismiss) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            TableLabel(
                "Each game at this seat is saved to the deck you pick. If you join the seat from your phone, the phone saves it instead.",
                18.sp,
                color = TableColors.TextMuted,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            if (isMe) {
                TableLabel("Not me", 22.sp, color = TableColors.Yellow, modifier = Modifier.clickable(onClick = onNotMe).padding(vertical = 8.dp))
            }
            if (decks.isEmpty()) TableLabel("You have no decks yet.", 22.sp, color = TableColors.TextMuted, modifier = Modifier.padding(top = 10.dp))
            LazyColumn {
                items(decks, key = { it.id }) { deck ->
                    val picked = isMe && deck.id == currentDeckId
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (picked) TableColors.Yellow else TableColors.Surface)
                            .clickable { onPick(deck.id) }.padding(8.dp)
                    ) {
                        AsyncImage(
                            model = deck.commander?.imageUrl.toArtCropUrl(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(width = 64.dp, height = 46.dp).clip(RoundedCornerShape(10.dp)).background(TableColors.SurfaceRaised)
                        )
                        Column(Modifier.weight(1f)) {
                            TableLabel(deck.name, 22.sp, color = if (picked) Color.Black else Color.White, maxLines = 1)
                            deck.commander?.let { TableLabel(it.name, 15.sp, color = if (picked) Color.Black else TableColors.TextMuted, maxLines = 1) }
                        }
                    }
                }
            }
        }
    }
}

/** The games played at this table, newest first: who won, how long it took, and who played what. */
@Composable
internal fun TableGamesOverlay(games: List<TableGame>, onDelete: (String) -> Unit, onClear: () -> Unit, onDismiss: () -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }
    TableOverlay(title = "Games at this table", onClose = onDismiss) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            if (games.isEmpty()) {
                TableLabel("Finished games show up here.", 24.sp, color = TableColors.TextMuted, modifier = Modifier.padding(20.dp))
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                val wins = games.groupingBy { it.winner?.name }.eachCount().filterKeys { it != null }
                val top = wins.maxByOrNull { it.value }
                TableLabel(
                    "${games.size} ${if (games.size == 1) "game" else "games"}" + (top?.let { " · most wins: ${it.key} (${it.value})" } ?: ""),
                    18.sp,
                    color = TableColors.TextMuted,
                    modifier = Modifier.weight(1f)
                )
                TableLabel(if (confirmClear) "Clear all?" else "Clear", 20.sp, color = TableColors.Yellow, modifier = Modifier.clickable {
                    if (confirmClear) { onClear(); confirmClear = false } else confirmClear = true
                }.padding(8.dp))
            }
            LazyColumn {
                items(games, key = { it.id }) { game ->
                    Column(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(14.dp)).background(TableColors.Surface).padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TableLabel(game.winner?.let { "${it.name} won" } ?: "No winner", 24.sp, color = TableColors.Yellow, maxLines = 1, modifier = Modifier.weight(1f))
                            TableLabel("✕", 20.sp, color = TableColors.TextMuted, modifier = Modifier.clickable { onDelete(game.id) }.padding(6.dp))
                        }
                        TableLabel(
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(game.endedAt)) +
                                " · ${game.minutes} min · ${game.turns} ${if (game.turns == 1) "turn" else "turns"}",
                            16.sp,
                            color = TableColors.TextMuted
                        )
                        game.players.forEach { p ->
                            TableLabel(
                                (if (p.seat == game.winnerSeat) "★ " else "   ") + p.name + (p.commander?.let { " · $it" } ?: "") + (if (p.me) " · you" else ""),
                                17.sp,
                                color = if (p.out == null) Color.White else TableColors.TextMuted,
                                maxLines = 1,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
