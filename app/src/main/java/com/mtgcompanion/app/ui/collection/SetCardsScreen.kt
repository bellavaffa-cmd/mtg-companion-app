package com.mtgcompanion.app.ui.collection

import com.mtgcompanion.app.ui.common.BackButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.CollectionEntry
import com.mtgcompanion.app.data.CollectionRepository
import com.mtgcompanion.app.data.DeckRepository
import com.mtgcompanion.app.data.SetInfo
import com.mtgcompanion.app.data.missingFromSet
import com.mtgcompanion.app.data.ownedPrintings
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.ui.common.PillChip
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One set's cards, owned and missing — opened from the Collection's Sets page. */
class SetCardsViewModel(
    val code: String,
    private val collectionRepository: CollectionRepository,
    deckRepository: DeckRepository,
    private val cardRepository: CardRepository = CardRepository()
) : ViewModel() {

    private val _set = MutableStateFlow<SetInfo?>(null)
    val set: StateFlow<SetInfo?> = _set.asStateFlow()

    private val _cards = MutableStateFlow<List<ScryfallCard>?>(null)
    /** The set's printings in its own order; null while loading. */
    val cards: StateFlow<List<ScryfallCard>?> = _cards.asStateFlow()

    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    /** Copies held of each printing, counted as All cards counts them. */
    val owned: StateFlow<Map<String, Int>> = combine(collectionRepository.collectionsFlow, deckRepository.decksFlow) { c, d -> ownedPrintings(c, d) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init { load() }

    fun load() {
        viewModelScope.launch {
            _failed.value = false
            _set.value = runCatching { cardRepository.getSets()[code.lowercase()] }.getOrNull()
            val found = runCatching { cardRepository.getSetCards(code) }.getOrNull()
            if (found == null) _failed.value = true else _cards.value = found
        }
    }

    /**
     * The set's printings the user doesn't own onto the Wishlist — one of each card (the Wishlist
     * keeps a card once, whatever its printing). Answers how many went on.
     */
    suspend fun addMissingToWishlist(): Int {
        val missing = missingFromSet(_cards.value.orEmpty(), owned.value.keys) { it.id }.distinctBy { it.name.lowercase() }
        if (missing.isEmpty()) return 0
        collectionRepository.addWanted(missing.map { card ->
            CollectionEntry(card.id, card.name, card.displayImageUrl, quantity = 1, backImageUrl = card.backImageUrl, tags = card.tags)
        })
        return missing.size
    }

    class Factory(
        private val code: String,
        private val collectionRepository: CollectionRepository,
        private val deckRepository: DeckRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SetCardsViewModel(code, collectionRepository, deckRepository) as T
    }
}

/**
 * A set's cards: owned ones as they are (with how many), missing ones dimmed. "Add missing to
 * Wishlist" puts what's missing on the Wishlist. A card opens its page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetCardsScreen(viewModel: SetCardsViewModel, onBack: () -> Unit, onViewDetails: (String) -> Unit) {
    val colors = LocalAppColors.current
    val set by viewModel.set.collectAsState()
    val cards by viewModel.cards.collectAsState()
    val owned by viewModel.owned.collectAsState()
    val failed by viewModel.failed.collectAsState()
    val scope = rememberCoroutineScope()
    // "all", "missing" or "owned".
    var show by rememberSaveable { mutableStateOf("all") }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val list = cards.orEmpty()
    val have = list.count { (owned[it.id] ?: 0) > 0 }
    val total = maxOf(set?.cardCount ?: 0, list.size)
    val missing = list.size - have

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(set?.name ?: viewModel.code.uppercase(), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (cards != null) Text("$have / $total cards", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                    }
                },
                navigationIcon = {
                    BackButton(onClick = onBack)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().background(colors.bg).padding(padding)) {
            when {
                failed && cards == null -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Couldn't fetch this set's cards from Scryfall.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    OutlinedButton(onClick = { viewModel.load() }) { Text("Try again", color = colors.accent) }
                }
                cards == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = colors.accent) }
                else -> {
                    val shown = when (show) {
                        "missing" -> list.filter { (owned[it.id] ?: 0) <= 0 }
                        "owned" -> list.filter { (owned[it.id] ?: 0) > 0 }
                        else -> list
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(104.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                LinearProgressIndicator(
                                    progress = { if (total == 0) 0f else (have.toFloat() / total).coerceIn(0f, 1f) },
                                    color = colors.accent,
                                    trackColor = colors.border,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    PillChip("All", show == "all", { show = "all" }, count = list.size)
                                    PillChip("Missing", show == "missing", { show = "missing" }, count = missing)
                                    PillChip("Owned", show == "owned", { show = "owned" }, count = have)
                                }
                                if (missing > 0) {
                                    Button(
                                        onClick = { scope.launch { val n = viewModel.addMissingToWishlist(); message = if (n == 1) "1 card put on your Wishlist." else "$n cards put on your Wishlist." } },
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Filled.Star, contentDescription = null)
                                        Text("  Add missing to Wishlist")
                                    }
                                }
                                message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textMuted) }
                                if (list.size < total) {
                                    Text(
                                        "Showing ${list.size} of the set's $total cards Scryfall can search.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.textDim
                                    )
                                }
                            }
                        }
                        items(shown, key = { it.id }) { card ->
                            SetCardTile(card, owned[card.id] ?: 0) { onViewDetails(card.name) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetCardTile(card: ScryfallCard, copies: Int, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Column(Modifier.clickable(onClick = onClick)) {
        Box {
            AsyncImage(
                model = card.displayImageUrl,
                contentDescription = card.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(10.dp))
                    // Missing cards are dimmed; owned ones stand out.
                    .alpha(if (copies > 0) 1f else 0.35f)
                    .let { if (copies > 0) it.border(BorderStroke(2.dp, colors.accent), RoundedCornerShape(10.dp)) else it }
            )
            if (copies > 0) {
                Text(
                    "×$copies",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.accentLight,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Text(
            listOfNotNull(card.collectorNumber?.let { "#$it" }, card.name).joinToString(" "),
            style = MaterialTheme.typography.labelSmall,
            color = if (copies > 0) colors.textPrimary else colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}
