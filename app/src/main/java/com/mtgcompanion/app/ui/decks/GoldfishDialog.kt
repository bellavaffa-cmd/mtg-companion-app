package com.mtgcompanion.app.ui.decks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.RestartAlt
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.mtgcompanion.app.data.PlayCard
import com.mtgcompanion.app.data.PlaytestState
import com.mtgcompanion.app.data.createToken
import com.mtgcompanion.app.data.draw
import com.mtgcompanion.app.data.keep
import com.mtgcompanion.app.data.mulligan
import com.mtgcompanion.app.data.newGame
import com.mtgcompanion.app.data.nextTurn
import com.mtgcompanion.app.data.play
import com.mtgcompanion.app.data.playCards
import com.mtgcompanion.app.data.putOnBottom
import com.mtgcompanion.app.data.reset
import com.mtgcompanion.app.data.toGraveyard
import com.mtgcompanion.app.data.toHand
import com.mtgcompanion.app.data.toggleTap
import com.mtgcompanion.app.data.withFreeMulligan
import com.mtgcompanion.app.data.withOnThePlay
import com.mtgcompanion.app.data.SIM_KEEP_LANDS
import com.mtgcompanion.app.data.handStats
import com.mtgcompanion.app.data.oddsPercent
import com.mtgcompanion.app.data.simLibrary
import androidx.compose.ui.text.font.FontWeight
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckCardEntry
import com.mtgcompanion.app.ui.common.CardZoomDialog
import com.mtgcompanion.app.ui.common.ZoomCard
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.TextDim
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary

// The game's rules live in data/Playtest.kt; this is only how it looks. [shuffledLibrary] stays for the
// web app's matching test.

/** One physical copy in the simulated library — [instanceId] distinguishes multiple copies of the same card. */
internal data class LibraryCard(val instanceId: String, val entry: DeckCardEntry)

/**
 * Every copy of the deck's cards, shuffled — less one copy of each commander, which starts in the
 * command zone (a deck's commanders are in its card list too). The web app's goldfish.ts is the same.
 */
internal fun shuffledLibrary(deck: Deck, random: kotlin.random.Random = kotlin.random.Random.Default): List<LibraryCard> {
    val commanders = listOfNotNull(deck.commander, deck.partnerCommander).groupingBy { it.scryfallId }.eachCount()
    return deck.cards.flatMap { entry ->
        (0 until entry.quantity - (commanders[entry.scryfallId] ?: 0)).map { i -> LibraryCard("${entry.scryfallId}#$i", entry) }
    }.shuffled(random)
}

/**
 * Playtesting a deck, full screen: shuffle and draw seven, mulligan the London way (a free first
 * mulligan for Commander and Brawl, on by default), choose to be on the play or the draw, then play
 * turns — Next turn untaps everything and draws. Tap a hand card to put it onto the battlefield
 * (land or spell alike), tap a permanent to tap or untap it; press and hold a card for more (To
 * graveyard, Back to hand, Look). The deck's [tokens] can be made on the battlefield. Nothing is
 * kept: it starts over every time it's opened, or with Reset. Hand stats (the chart button) shows how
 * 10,000 shuffled hands go (HandStatsPanel, HandSim.kt).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GoldfishDialog(
    deck: Deck,
    tokens: List<TokenArt> = emptyList(),
    /** The deck's card data, for Hand stats' mana values and land types. */
    cardsById: Map<String, ScryfallCard> = emptyMap(),
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val start = remember(deck.id) { playCards(deck) }
        if (start.first.isEmpty()) {
            Scaffold(containerColor = Bg, topBar = { PlaytestTopBar(null, onReset = {}, onDismiss = onDismiss) }) { padding ->
                Column(modifier = Modifier.fillMaxSize().background(Bg).padding(padding).padding(20.dp)) {
                    Text("Add cards to this deck before playtesting.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
            return@Dialog
        }

        var game by remember { mutableStateOf(newGame(start.first, start.second, freeMulligan = deck.mode.usesCommander)) }
        var looking by remember { mutableStateOf<PlayCard?>(null) }
        var showStats by remember { mutableStateOf(false) }

        Scaffold(
            containerColor = Bg,
            topBar = { PlaytestTopBar(game, onReset = { game = game.reset() }, onDismiss = onDismiss, showStats = showStats, onStats = { showStats = !showStats }) },
            bottomBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().background(Bg).padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (game.choosingHand) {
                        OutlinedButton(onClick = { game = game.mulligan() }, modifier = Modifier.weight(1f)) {
                            Text("Mulligan", color = Gold)
                        }
                        Button(
                            onClick = { game = game.keep() },
                            enabled = game.canKeep,
                            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg),
                            modifier = Modifier.weight(1f)
                        ) { Text("Keep", color = Bg) }
                    } else {
                        OutlinedButton(onClick = { game = game.draw() }, enabled = game.library.isNotEmpty(), modifier = Modifier.weight(1f)) {
                            Text("Draw", color = Gold)
                        }
                        Button(
                            onClick = { game = game.nextTurn() },
                            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg),
                            modifier = Modifier.weight(1f)
                        ) { Text("Next turn", color = Bg) }
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Bg)
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 20.dp)
            ) {
                if (showStats) {
                    ZoneLabel("Hand stats")
                    HandStatsPanel(deck, cardsById, Modifier.padding(horizontal = 20.dp))
                }
                if (game.choosingHand) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterPill("On the play", game.onThePlay) { game = game.withOnThePlay(true) }
                            FilterPill("On the draw", !game.onThePlay) { game = game.withOnThePlay(false) }
                            if (deck.mode.usesCommander) {
                                FilterPill("Free first mulligan", game.freeMulligan) { game = game.withFreeMulligan(!game.freeMulligan) }
                            }
                        }
                        Text(
                            when {
                                game.toBottom > 0 -> "Tap ${game.toBottom} card${if (game.toBottom == 1) "" else "s"} in your hand to put on the bottom."
                                game.mulligans > 0 -> "Mulligans: ${game.mulligans}. Keep this hand, or mulligan again."
                                else -> "Keep this hand, or mulligan: shuffle and draw seven, then put one card on the bottom for each mulligan."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (game.toBottom > 0) GoldLight else TextMuted
                        )
                    }
                }

                if (game.commandZone.isNotEmpty()) {
                    ZoneLabel("Command zone")
                    CardStrip {
                        game.commandZone.forEach { card ->
                            PlayCardView(
                                card = card,
                                width = 96.dp,
                                onClick = { if (!game.choosingHand) game = game.play(card.id) },
                                menu = listOf("Look" to { looking = card })
                            )
                        }
                    }
                }

                ZoneLabel("Hand (${game.hand.size})")
                if (game.hand.isEmpty()) {
                    Text("No cards in hand.", style = MaterialTheme.typography.bodySmall, color = TextDim, modifier = Modifier.padding(horizontal = 20.dp))
                } else {
                    CardStrip {
                        game.hand.forEach { card ->
                            PlayCardView(
                                card = card,
                                width = 110.dp,
                                onClick = {
                                    game = when {
                                        game.toBottom > 0 -> game.putOnBottom(card.id)
                                        game.choosingHand -> game
                                        else -> game.play(card.id)
                                    }
                                },
                                menu = if (game.choosingHand) {
                                    listOf("Look" to { looking = card })
                                } else {
                                    listOf(
                                        "Play" to { game = game.play(card.id) },
                                        "To graveyard" to { game = game.toGraveyard(card.id) },
                                        "Look" to { looking = card }
                                    )
                                }
                            )
                        }
                    }
                }

                if (!game.choosingHand) {
                    ZoneLabel("Battlefield (${game.battlefield.size})")
                    if (game.battlefield.isEmpty()) {
                        Text(
                            "Tap a card in your hand to play it. Tap a permanent to tap or untap it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextDim,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    } else {
                        // Lands in their own row under everything else, as on a table.
                        val (lands, others) = game.battlefield.partition { it.card.typeLine?.contains("Land") == true }
                        listOf(others, lands).filter { it.isNotEmpty() }.forEach { row ->
                            FlowRow(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                row.forEach { permanent ->
                                    val card = permanent.card
                                    PlayCardView(
                                        card = card,
                                        width = 72.dp,
                                        tapped = permanent.tapped,
                                        onClick = { game = game.toggleTap(card.id) },
                                        menu = listOf(
                                            (if (permanent.tapped) "Untap" else "Tap") to { game = game.toggleTap(card.id) },
                                            "To graveyard" to { game = game.toGraveyard(card.id) },
                                            (if (card.isToken) "Remove token" else "Back to hand") to { game = game.toHand(card.id) },
                                            "Look" to { looking = card }
                                        )
                                    )
                                }
                            }
                        }
                    }

                    if (tokens.isNotEmpty()) {
                        ZoneLabel("Make a token")
                        FlowRow(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tokens.forEach { art ->
                                FilterPill("+ ${art.token.name}", false) {
                                    game = game.createToken(art.token.name, art.imageUrl, art.token.typeLine)
                                }
                            }
                        }
                    }
                }

                ZoneLabel("Graveyard (${game.graveyard.size})")
                if (game.graveyard.isEmpty()) {
                    Text("Empty.", style = MaterialTheme.typography.bodySmall, color = TextDim, modifier = Modifier.padding(horizontal = 20.dp))
                } else {
                    CardStrip {
                        game.graveyard.asReversed().forEach { card ->
                            PlayCardView(card = card, width = 64.dp, onClick = { looking = card }, menu = emptyList())
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        looking?.let { card ->
            CardZoomDialog(listOf(ZoomCard(imageUrl = card.imageUrl, cardName = card.name, backImageUrl = card.backImageUrl)), 0) { looking = null }
        }
    }
}

@Composable
private fun ZoneLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = TextMuted,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun CardStrip(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}

/**
 * One card in the game: its picture (its name until the picture loads, or for a token without
 * one), turned sideways when [tapped]. Tap for [onClick]; press and hold for [menu].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayCardView(
    card: PlayCard,
    width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    menu: List<Pair<String, () -> Unit>>,
    tapped: Boolean = false
) {
    var open by remember { mutableStateOf(false) }
    val height = width / 0.72f
    // A tapped card turns sideways, so its slot is as wide as the card is tall.
    Box(Modifier.size(width = if (tapped) height else width, height = height), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .width(width)
                .aspectRatio(0.72f)
                .graphicsLayer { rotationZ = if (tapped) 90f else 0f }
                .clip(RoundedCornerShape(8.dp))
                .background(Surface)
                .combinedClickable(onClick = onClick, onLongClick = { if (menu.isNotEmpty()) open = true }),
            contentAlignment = Alignment.Center
        ) {
            Text(
                card.name,
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(4.dp)
            )
            AsyncImage(
                model = card.imageUrl,
                contentDescription = card.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(Surface)) {
            menu.forEach { (label, action) ->
                DropdownMenuItem(text = { Text(label, color = TextPrimary) }, onClick = { open = false; action() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaytestTopBar(
    game: PlaytestState?,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    showStats: Boolean = false,
    onStats: () -> Unit = {}
) {
    TopAppBar(
        title = {
            Column {
                Text("Playtest", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (game != null) {
                    Text(
                        listOf(
                            if (game.choosingHand) "Opening hand" else "Turn ${game.turn}",
                            "Library ${game.library.size}",
                            "Graveyard ${game.graveyard.size}"
                        ).joinToString(" · "),
                        color = TextPrimary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Gold)
            }
        },
        actions = {
            if (game != null) {
                IconButton(onClick = onStats) {
                    Icon(Icons.Filled.BarChart, contentDescription = if (showStats) "Hide hand stats" else "Hand stats", tint = if (showStats) GoldLight else Gold)
                }
                IconButton(onClick = onReset) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = "Reset", tint = Gold)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
    )
}

/**
 * Hand stats in the playtest: 10,000 shuffles of the deck, counted (HandSim.kt) — lands in the
 * opener, a land drop each turn on the play and on the draw, a two-drop on turn 2, and how often a
 * 2–5 land keep rule mulligans. The web app's HandStatsPanel.tsx.
 */
@Composable
private fun HandStatsPanel(deck: Deck, cardsById: Map<String, ScryfallCard>, modifier: Modifier = Modifier) {
    val stats = remember(deck, cardsById) {
        handStats(simLibrary(deck, { cardsById[it.scryfallId]?.typeLine }, { cardsById[it.scryfallId]?.cmc }))
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (stats == null) {
            Text("The deck needs at least 11 cards in its library for hand stats.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            return@Column
        }
        StatRow("2–4 lands in your opening seven", oddsPercent(stats.twoToFourLands))
        StatRow("Lands in your opening seven, on average", String.format(Locale.US, "%.1f", stats.averageLands))
        StatRow("Mulligan, keeping ${SIM_KEEP_LANDS.first}–${SIM_KEEP_LANDS.last} lands", oddsPercent(stats.mulliganRate))
        StatRow("Land drop every turn", "Play", "Draw", header = true)
        stats.landDrops.forEach { (turn, d) -> StatRow("Turn $turn", oddsPercent(d.onThePlay), oddsPercent(d.onTheDraw)) }
        if (stats.twoDrops > 0) {
            StatRow("A two-drop to cast on turn 2", oddsPercent(stats.twoDropOnTurn2.onThePlay), oddsPercent(stats.twoDropOnTurn2.onTheDraw))
        } else {
            Text(
                "No two-drops in the deck" + if (cardsById.isEmpty()) " yet — card data is still loading." else ".",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
        Text(
            "From ${String.format(Locale.US, "%,d", stats.hands)} shuffles of the ${stats.library} cards in the library (${stats.lands} lands" +
                (if (deck.commander != null) "; the commander starts in the command zone" else "") +
                "), seven-card hands without mulligans. Colours aren't checked: any land counts, and a two-drop is any card with mana value 2, so it's a rough guide.",
            style = MaterialTheme.typography.labelSmall,
            color = TextDim,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun StatRow(label: String, value: String, second: String? = null, header: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = if (header) TextMuted else TextPrimary, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (header) FontWeight.Normal else FontWeight.Bold,
            color = if (header) TextMuted else GoldLight,
            textAlign = TextAlign.End,
            modifier = Modifier.width(52.dp)
        )
        if (second != null) {
            Text(
                second,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (header) FontWeight.Normal else FontWeight.Bold,
                color = if (header) TextMuted else GoldLight,
                textAlign = TextAlign.End,
                modifier = Modifier.width(52.dp)
            )
        }
    }
}
