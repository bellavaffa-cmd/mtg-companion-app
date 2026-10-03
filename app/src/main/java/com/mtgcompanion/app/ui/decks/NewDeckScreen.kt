package com.mtgcompanion.app.ui.decks

import com.mtgcompanion.app.ui.common.BackButton
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.CommanderSort
import com.mtgcompanion.app.data.GameMode
import com.mtgcompanion.app.data.IDENTITY_CHIPS
import com.mtgcompanion.app.data.identityHint
import com.mtgcompanion.app.data.landingTab
import com.mtgcompanion.app.data.SecondCommanderKind
import com.mtgcompanion.app.data.formatBlurb
import com.mtgcompanion.app.data.pickerCards
import com.mtgcompanion.app.data.secondCommanderOptions
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.common.CardZoomDialog
import com.mtgcompanion.app.ui.common.FlipBadge
import com.mtgcompanion.app.ui.common.ManaSymbol
import com.mtgcompanion.app.ui.common.PillChip
import com.mtgcompanion.app.ui.common.SearchPill
import com.mtgcompanion.app.ui.common.ZoomAction
import com.mtgcompanion.app.ui.common.ZoomCard
import com.mtgcompanion.app.ui.common.adaptiveGridColumns
import com.mtgcompanion.app.ui.common.cardGrid
import com.mtgcompanion.app.ui.common.elevatedCard
import com.mtgcompanion.app.ui.common.zoomSource
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.LocalAppColors
import com.mtgcompanion.app.ui.theme.OnGold
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary

/**
 * A deck from nothing: pick a format; for Commander and Brawl, pick the commander from every legal
 * one (and a second commander when the first allows one); name it. [onCreated] gets the new deck
 * and the tab to open it on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewDeckScreen(viewModel: NewDeckViewModel, onBack: () -> Unit, onCreated: (deckId: String, tab: String?) -> Unit) {
    val state by viewModel.state.collectAsState()
    val goBack: () -> Unit = { if (!viewModel.back()) onBack() }
    BackHandler(enabled = state.step != NewDeckStep.FORMAT) { goBack() }

    val title = when (state.step) {
        NewDeckStep.FORMAT -> "New deck"
        NewDeckStep.COMMANDER -> "Choose a commander"
        NewDeckStep.SECOND -> state.secondKind?.action ?: "Add a partner"
        NewDeckStep.NAME -> "Name your deck"
    }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    BackButton(onClick = goBack)
                },
                actions = {
                    if (state.step == NewDeckStep.SECOND) {
                        TextButton(onClick = { viewModel.pickSecond(null) }) { Text("Skip", color = Gold) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().background(Bg).padding(padding)) {
            when (state.step) {
                NewDeckStep.FORMAT -> FormatStep(onPick = viewModel::pickFormat)
                NewDeckStep.COMMANDER, NewDeckStep.SECOND -> PickerStep(viewModel, state)
                NewDeckStep.NAME -> NameStep(
                    state = state,
                    onName = viewModel::setName,
                    onCreate = {
                        val tab = state.mode?.let { landingTab(it) }
                        viewModel.create { deckId -> onCreated(deckId, tab) }
                    }
                )
            }
        }
    }
}

@Composable
private fun FormatStep(onPick: (GameMode) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text("Pick a format. Commander and Brawl decks choose their commander next.", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        GameMode.entries.forEach { mode ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .elevatedCard()
                    .clickable { onPick(mode) }
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Text(mode.label, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(formatBlurb(mode), style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** The commander grid (step 2) or the second-commander grid (step 3), with its search, colours and sort. */
@Composable
private fun PickerStep(viewModel: NewDeckViewModel, state: NewDeckState) {
    val catalogFlow by viewModel.catalog.collectAsState()
    val flow = catalogFlow ?: return
    val catalog by flow.collectAsState()
    val second = state.step == NewDeckStep.SECOND
    val main = state.commander
    val base = remember(catalog.cards, second, main) {
        if (second && main != null) secondCommanderOptions(main, catalog.cards) else catalog.cards
    }
    val shown = remember(base, state.filter) {
        pickerCards(base, state.filter.query, state.filter.colours, state.filter.sort)
    }
    var zoomIndex by remember { mutableStateOf<Int?>(null) }
    val columns = adaptiveGridColumns(3)
    val app = LocalAppColors.current
    val pick: (ScryfallCard) -> Unit = { card ->
        zoomIndex = null
        if (second) viewModel.pickSecond(card) else viewModel.pickCommander(card)
    }
    val noun = when {
        !second -> "commanders"
        state.secondKind == SecondCommanderKind.BACKGROUND -> "Backgrounds"
        else -> "commanders that pair with it"
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "controls") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                if (second && main != null) {
                    Text(
                        "${main.name} can lead with ${secondArticle(state.secondKind)} as a second commander.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
                SearchPill(
                    query = state.filter.query,
                    onQueryChange = { viewModel.setFilter(state.filter.copy(query = it)) },
                    placeholder = "Name, type or rules text"
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    IDENTITY_CHIPS.forEach { code ->
                        val selected = code in state.filter.colours
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (selected) app.accent else app.surface)
                                .border(1.dp, if (selected) app.accent else BorderColor, CircleShape)
                                .clickable {
                                    val colours = if (selected) state.filter.colours - code else state.filter.colours + code
                                    viewModel.setFilter(state.filter.copy(colours = colours))
                                }
                        ) { ManaSymbol(code, size = 24.dp) }
                    }
                }
                Text(identityHint(state.filter.colours.isNotEmpty()), style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CommanderSort.entries.forEach { sort ->
                        PillChip(sort.label, state.filter.sort == sort, onClick = { viewModel.setFilter(state.filter.copy(sort = sort)) })
                    }
                    Spacer(Modifier.weight(1f))
                    Text("${shown.size} $noun", style = MaterialTheme.typography.labelMedium, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        cardGrid(shown, columns = columns, key = { it.id }) { card ->
            PickerTile(card, onClick = { zoomIndex = shown.indexOfFirst { it.id == card.id }.takeIf { it >= 0 } })
        }

        item(key = "footer") {
            when {
                catalog.error != null -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                ) {
                    Text(catalog.error.orEmpty(), style = MaterialTheme.typography.bodySmall, color = TextMuted, textAlign = TextAlign.Center)
                    OutlinedButton(onClick = viewModel::retry, modifier = Modifier.padding(top = 8.dp)) { Text("Try again", color = Gold) }
                }
                catalog.loading -> Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                ) {
                    CircularProgressIndicator(color = Gold, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Text(
                        if (catalog.cards.isEmpty()) "Loading…" else "Loading more…",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
                shown.isEmpty() -> Text(
                    "No $noun match.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                )
                else -> Spacer(Modifier.height(1.dp))
            }
        }
    }

    zoomIndex?.let { index ->
        CardZoomDialog(
            cards = shown.map { card ->
                ZoomCard(
                    imageUrl = card.displayImageUrl,
                    cardName = card.name,
                    priceUsd = card.prices?.usd?.toDoubleOrNull(),
                    backImageUrl = card.backImageUrl,
                    tags = card.tags,
                    primaryAction = ZoomAction(if (second) "Add ${card.name}" else "Build with ${card.name}") { pick(card) }
                )
            },
            initialIndex = index,
            onDismiss = { zoomIndex = null }
        )
    }
}

/** "a partner", "a Background"… for the second-commander step's intro line. */
private fun secondArticle(kind: SecondCommanderKind?): String = when (kind) {
    SecondCommanderKind.BACKGROUND -> "a Background"
    SecondCommanderKind.DOCTOR -> "a Doctor"
    SecondCommanderKind.COMPANION -> "a Doctor's companion"
    else -> "a partner"
}

@Composable
private fun PickerTile(card: ScryfallCard, onClick: () -> Unit) {
    Box {
        AsyncImage(
            model = card.displayImageUrl,
            contentDescription = card.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .zoomSource(card.displayImageUrl)
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
        )
        if (card.backImageUrl != null) FlipBadge()
    }
}

@Composable
private fun NameStep(state: NewDeckState, onName: (String) -> Unit, onCreate: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        val commanders = listOfNotNull(state.commander, state.partner)
        if (commanders.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                commanders.forEach { card ->
                    AsyncImage(
                        model = card.displayImageUrl,
                        contentDescription = card.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.width(150.dp).aspectRatio(0.72f).clip(RoundedCornerShape(12.dp))
                    )
                }
            }
        }
        Text(
            state.mode?.let { "${it.label} deck" }.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
        OutlinedTextField(
            value = state.name,
            onValueChange = onName,
            label = { Text("Deck name", color = TextMuted) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Gold,
                unfocusedBorderColor = BorderColor,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = Gold,
                focusedContainerColor = Surface,
                unfocusedContainerColor = Surface
            ),
            modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp)
        )
        Button(
            onClick = onCreate,
            enabled = !state.creating && state.name.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = OnGold),
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (state.creating) "Creating…" else "Create deck") }
    }
}
