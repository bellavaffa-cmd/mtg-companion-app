package com.mtgcompanion.app.ui.collection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionRepository
import com.mtgcompanion.app.data.CollectionType
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckRepository
import com.mtgcompanion.app.data.RoleTag
import com.mtgcompanion.app.data.RoleTags
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.common.CardSource
import com.mtgcompanion.app.ui.common.CardZoomDialog
import com.mtgcompanion.app.ui.common.SourceKind
import com.mtgcompanion.app.ui.common.ZoomCard
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.Surface2
import com.mtgcompanion.app.ui.theme.Surface3
import com.mtgcompanion.app.ui.theme.TextDim
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One card the user owns, however many binders its copies are spread over. */
data class OwnedCard(
    val key: String,
    val name: String,
    val scryfallId: String,
    val imageUrl: String?,
    val backImageUrl: String?,
    val copies: Int,
    /** Where the copies are, as the zoom's "also in" list shows them. */
    val where: List<CardSource>
)

/** Every card in the user's own binders (and the Unsorted pile) — not their wishlists. */
fun ownedCards(collections: List<Collection>): List<OwnedCard> {
    val byName = linkedMapOf<String, OwnedCard>()
    for (c in collections) {
        if (c.kind == CollectionType.WISHLIST) continue
        for (e in c.entries) {
            val copies = e.quantity + e.foilQuantity
            if (copies <= 0) continue
            val key = RoleTags.key(e.name)
            val card = byName[key] ?: OwnedCard(key, e.name, e.scryfallId, e.imageUrl, e.backImageUrl, 0, emptyList())
            val here = card.where.firstOrNull { it.id == c.id }
            val where = if (here != null) card.where.map { if (it.id == c.id) it.copy(quantity = it.quantity + copies) else it }
            else card.where + CardSource(SourceKind.BINDER, c.id, c.name, copies)
            byName[key] = card.copy(copies = card.copies + copies, where = where)
        }
    }
    return byName.values.sortedBy { it.name.lowercase() }
}

/**
 * Cards the user owns that do [tagId]'s job and aren't in [deck] yet — for a deck short of ramp,
 * say, the ramp already in their binders. With a commander, only cards in its colours (and none
 * until the commander's colours are known).
 */
fun ownedForTag(
    owned: List<OwnedCard>,
    deck: Deck,
    tagId: String,
    tagsOf: (String) -> List<String>? = RoleTags::tagsOf,
    identityOf: (String) -> String? = RoleTags::identityOf
): List<OwnedCard> {
    val commanders = listOfNotNull(deck.commander, deck.partnerCommander)
    val colours: Set<Char>? = if (commanders.isEmpty()) null
    else commanders.flatMap { identityOf(it.name)?.toList() ?: return emptyList() }.toSet()
    val inDeck = (deck.cards + commanders).map { RoleTags.key(it.name) }.toSet()
    return owned.filter { card ->
        card.key !in inDeck &&
            tagsOf(card.name)?.contains(tagId) == true &&
            (colours == null || identityOf(card.name)?.all { it in colours } == true)
    }
}

/** One tag's automatic binder: the tag and every owned card with it. */
data class TagBinder(val tag: RoleTag, val cards: List<OwnedCard>)

/** The owned cards with each tag, fullest first — and the lookup of any card not tagged yet. */
fun tagBindersFlow(repository: CollectionRepository, scope: kotlinx.coroutines.CoroutineScope, cardRepository: CardRepository): StateFlow<List<TagBinder>> {
    scope.launch {
        repository.collectionsFlow.map { all -> ownedCards(all).map { it.name } }.distinctUntilChanged().collectLatest { names ->
            if (names.isNotEmpty()) RoleTags.ensure(names, cardRepository)
        }
    }
    return combine(repository.collectionsFlow, RoleTags.version) { all, _ ->
        val owned = ownedCards(all)
        RoleTags.TAGS.map { tag -> TagBinder(tag, owned.filter { tag.id in RoleTags.tagsOf(it.name).orEmpty() }) }
            .filter { it.cards.isNotEmpty() }
            .sortedByDescending { it.cards.size }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
}

private const val TAGS_SHOWN = 8

/**
 * "By tag · automatic" on the Binders page: a binder per tag, gathering every card the user owns
 * that has it. They fill themselves — the copies stay in the binders they're in.
 */
@Composable
fun TagBindersSection(binders: List<TagBinder>, tagging: Pair<Int, Int>?, onOpen: (String) -> Unit) {
    var showAll by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.Label, contentDescription = null, tint = Gold, modifier = Modifier.size(16.dp))
            Text("BY TAG · AUTOMATIC", style = MaterialTheme.typography.labelMedium, color = TextMuted, modifier = Modifier.weight(1f))
            tagging?.let { (done, total) -> Text("Tagging your cards… $done of $total", style = MaterialTheme.typography.labelSmall, color = TextDim) }
        }
        if (binders.isEmpty()) {
            Text(
                if (tagging != null) "Looking up what each of your cards does…" else "None of your cards has a tag yet — they're looked up when you're online.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
        val shown = if (showAll) binders else binders.take(TAGS_SHOWN)
        shown.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { binder -> TagBinderTile(binder, Modifier.weight(1f)) { onOpen(binder.tag.id) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (binders.size > TAGS_SHOWN) {
            TextButton(onClick = { showAll = !showAll }) {
                Text(if (showAll) "Show fewer tags" else "Show all ${binders.size} tags", color = Gold)
            }
        }
    }
}

@Composable
private fun TagBinderTile(binder: TagBinder, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Surface)
            .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Box(Modifier.fillMaxWidth().height(68.dp).background(Surface3)) {
            Row(Modifier.fillMaxSize()) {
                binder.cards.take(3).forEach { card ->
                    AsyncImage(
                        model = card.imageUrl.toArtCropUrl(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).fillMaxSize()
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.padding(6.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.65f)).padding(horizontal = 7.dp, vertical = 1.dp)
            ) {
                Icon(Icons.Filled.Label, contentDescription = null, tint = Gold, modifier = Modifier.size(11.dp))
                Text("auto", style = MaterialTheme.typography.labelSmall, color = Gold)
            }
        }
        Text(binder.tag.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp))
        Text("${binder.cards.size} ${if (binder.cards.size == 1) "card" else "cards"}", style = MaterialTheme.typography.labelMedium, color = TextMuted, modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp))
    }
}

// ---- One tag's binder ----

class TagBinderViewModel(
    val tagId: String,
    collectionRepository: CollectionRepository,
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository = CardRepository()
) : ViewModel() {
    val tag: RoleTag? = RoleTags.tag(tagId)
    val binders: StateFlow<List<TagBinder>> = tagBindersFlow(collectionRepository, viewModelScope, cardRepository)
    val tagging: StateFlow<Pair<Int, Int>?> = RoleTags.progress
    val decks: StateFlow<List<Deck>> = deckRepository.decksFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Adds one copy of each of [cards] to [deckId] — into the deck, or its Considering list —
     * skipping any the deck (or, for Considering, that list) already has. [onDone] gets what happened.
     */
    fun addToDeck(cards: List<OwnedCard>, deckId: String, considering: Boolean, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val deck = decks.value.firstOrNull { it.id == deckId } ?: return@launch
            val have = (deck.cards.map { it.name } + if (considering) deck.considering.map { it.name } else emptyList()).map(RoleTags::key).toSet()
            val fresh = cards.filter { it.key !in have }.distinctBy { it.key }
            val message = try {
                // A deck entry needs the full card (type, commander-ness…), which a binder entry doesn't keep.
                val full = if (fresh.isEmpty()) emptyList() else cardRepository.getCardsByIds(fresh.map { it.scryfallId })
                full.forEach { card ->
                    if (considering) deckRepository.addToConsidering(deckId, card) else deckRepository.addCardToDeck(deckId, card)
                }
                val skipped = cards.size - full.size
                (if (full.isEmpty()) "Nothing added" else "Added ${full.size} ${if (full.size == 1) "card" else "cards"}") +
                    " to " + (if (considering) "${deck.name}'s Considering list" else deck.name) +
                    (if (skipped > 0) " · $skipped ${if (skipped == 1) "was" else "were"} already there" else "") + "."
            } catch (e: Exception) {
                "Couldn't reach Scryfall — try again when you're online."
            }
            onDone(message)
        }
    }

    class Factory(
        private val tagId: String,
        private val collectionRepository: CollectionRepository,
        private val deckRepository: DeckRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TagBinderViewModel(tagId, collectionRepository, deckRepository) as T
    }
}

/** One tag's automatic binder: every owned card with the tag, to look through and add to decks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagBinderScreen(viewModel: TagBinderViewModel, onBack: () -> Unit, onOpenTag: (String) -> Unit, onOpenDeck: (String) -> Unit) {
    val tag = viewModel.tag
    val binders by viewModel.binders.collectAsState()
    val tagging by viewModel.tagging.collectAsState()
    val cards = binders.firstOrNull { it.tag.id == viewModel.tagId }?.cards.orEmpty()
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var zoomKey by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf<List<OwnedCard>?>(null) }
    val shown = cards.filter { RoleTags.matches(it.name, RoleTags.tagsOf(it.name).orEmpty(), query) }
    val picked = cards.filter { it.key in selected }
    val selecting = picked.isNotEmpty()
    fun toggle(card: OwnedCard) { selected = if (card.key in selected) selected - card.key else selected + card.key }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text(tag?.label ?: "Tag", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Gold) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
            )
        },
        bottomBar = {
            if (selecting) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().background(Surface2).padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Filled.Close, contentDescription = "Stop selecting", tint = TextMuted) }
                    Text("${picked.size} selected", color = TextPrimary, modifier = Modifier.weight(1f))
                    Button(onClick = { adding = picked }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)) {
                        Icon(Icons.Filled.Style, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Add to deck")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    "Every card you own tagged ${tag?.label ?: viewModel.tagId}. Your copies stay in their own binders. Tap a card to see what else it does, or press and hold to pick several and add them to a deck.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            tagging?.let { (done, total) ->
                item { Text("Tagging your cards… $done of $total", style = MaterialTheme.typography.labelMedium, color = TextDim) }
            }
            if (cards.isNotEmpty()) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Name or tag, e.g. rock", color = TextMuted) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Gold) },
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = TextMuted) }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Gold, unfocusedBorderColor = BorderColor, focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary, cursorColor = Gold, focusedContainerColor = Surface, unfocusedContainerColor = Surface
                        ),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                }
                if (query.isNotBlank()) {
                    item {
                        val tagHits = shown.filterNot { it.name.contains(query.trim(), ignoreCase = true) }
                            .flatMap { RoleTags.matched(RoleTags.tagsOf(it.name).orEmpty(), query) }.distinct()
                        Text(
                            "${shown.size} ${if (shown.size == 1) "card" else "cards"}" +
                                if (tagHits.isNotEmpty()) " · tag: " + tagHits.take(2).joinToString(", ") { RoleTags.label(it) } + (if (tagHits.size > 2) "…" else "") else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMuted
                        )
                    }
                }
            }
            if (cards.isEmpty()) {
                item {
                    Text(
                        if (tagging != null) "Looking up your cards…" else "You don't own any cards tagged ${tag?.label ?: viewModel.tagId}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
            items(shown, key = { it.key }) { card ->
                OwnedCardRow(
                    card = card,
                    selecting = selecting,
                    selected = card.key in selected,
                    onClick = { if (selecting) toggle(card) else zoomKey = card.key },
                    onLongClick = { toggle(card) }
                )
            }
        }
    }

    zoomKey?.let { key ->
        val zoomCards = shown.map { card ->
            ZoomCard(
                imageUrl = card.imageUrl,
                cardName = card.name,
                sources = card.where,
                backImageUrl = card.backImageUrl,
                tags = RoleTags.tagsOf(card.name).orEmpty().map(RoleTags::label),
                onTagClick = { label ->
                    zoomKey = null
                    RoleTags.byLabel(label)?.let { if (it.id != viewModel.tagId) onOpenTag(it.id) }
                },
                onAdd = { zoomKey = null; adding = listOf(card) }
            )
        }
        CardZoomDialog(zoomCards, shown.indexOfFirst { it.key == key }.coerceAtLeast(0)) { zoomKey = null }
    }

    adding?.let { toAdd ->
        val decks by viewModel.decks.collectAsState()
        AddToDeckDialog(
            label = if (toAdd.size == 1) toAdd.first().name else "${toAdd.size} cards",
            decks = decks,
            onAdd = { deckId, considering, onResult -> viewModel.addToDeck(toAdd, deckId, considering, onResult) },
            onOpenDeck = { id -> adding = null; selected = emptySet(); onOpenDeck(id) },
            onDone = { adding = null; selected = emptySet() },
            onDismiss = { adding = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OwnedCardRow(card: OwnedCard, selecting: Boolean, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Gold else BorderColor), RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() })
            .padding(12.dp)
    ) {
        AsyncImage(
            model = card.imageUrl.toArtCropUrl(),
            contentDescription = card.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 72.dp, height = 52.dp).clip(RoundedCornerShape(10.dp))
        )
        Column(Modifier.weight(1f)) {
            Text(card.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text(
                "${card.copies} ${if (card.copies == 1) "copy" else "copies"} · " + card.where.joinToString(", ") { it.name },
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (selecting) {
            Icon(Icons.Filled.CheckCircle, contentDescription = if (selected) "Selected" else "Not selected", tint = if (selected) Gold else TextDim)
        }
    }
}

/** Picks a deck, and whether the cards go into it or its Considering list, then says what happened. */
@Composable
fun AddToDeckDialog(
    label: String,
    decks: List<Deck>,
    onAdd: (deckId: String, considering: Boolean, onResult: (String) -> Unit) -> Unit,
    onOpenDeck: (String) -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit
) {
    // The decks may still be loading when this opens: the first one is picked once they're in.
    var picked by remember { mutableStateOf<String?>(null) }
    val deckId = picked?.takeIf { id -> decks.any { it.id == id } } ?: decks.firstOrNull()?.id
    var considering by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Add $label to a deck", color = GoldLight) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    result != null -> Text(result!!, color = TextPrimary)
                    decks.isEmpty() -> Text("You have no decks yet. Make one in Decks first.", color = TextMuted)
                    else -> {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface3).padding(3.dp)) {
                            listOf(false to "Into the deck", true to "Considering").forEach { (value, text) ->
                                Text(
                                    text,
                                    color = if (considering == value) Gold else TextMuted,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(if (considering == value) Surface else Color.Transparent)
                                        .clickable { considering = value }
                                        .padding(vertical = 8.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                        Text(
                            if (considering) "Cards you might play — kept beside the deck, not counted in it." else "One copy of each, skipping any the deck already has.",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextDim
                        )
                        Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            decks.forEach { d ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Surface2)
                                        .border(BorderStroke(2.dp, if (d.id == deckId) Gold else Color.Transparent), RoundedCornerShape(12.dp))
                                        .clickable { picked = d.id }
                                        .padding(6.dp)
                                ) {
                                    AsyncImage(
                                        model = d.commander?.imageUrl.toArtCropUrl(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(width = 52.dp, height = 38.dp).clip(RoundedCornerShape(8.dp)).background(Surface3)
                                    )
                                    Text(d.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text("${d.cards.sumOf { it.quantity }} cards", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (result != null) {
                Row {
                    deckId?.let { id -> TextButton(onClick = { onOpenDeck(id) }) { Text("Open deck", color = TextMuted) } }
                    Button(onClick = onDone, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)) { Text("Done") }
                }
            } else {
                Button(
                    enabled = deckId != null && !busy,
                    onClick = {
                        val id = deckId ?: return@Button
                        busy = true
                        onAdd(id, considering) { message -> busy = false; result = message }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
                ) {
                    if (busy) CircularProgressIndicator(color = Bg, strokeWidth = 2.dp, modifier = Modifier.size(16.dp)) else Text("Add")
                }
            }
        },
        dismissButton = { if (result == null) TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } }
    )
}
