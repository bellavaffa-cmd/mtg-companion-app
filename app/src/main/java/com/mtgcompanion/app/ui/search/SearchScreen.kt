package com.mtgcompanion.app.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldDim
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.TextDim
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onCardClick: (ScryfallCard) -> Unit,
    onSettingsClick: () -> Unit,
    onScanClick: () -> Unit
) {
    val query by viewModel.query.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val filters by viewModel.filters.collectAsState()
    var showFilters by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        containerColor = Bg,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            "MTG COMPANION",
                            style = MaterialTheme.typography.labelLarge,
                            color = GoldLight
                        )
                    },
                    actions = {
                        IconButton(onClick = onScanClick) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = "Scan a card", tint = Gold)
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Gold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
                )
                GoldDivider()
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().background(Bg).padding(padding).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::onQueryChange,
                    label = { Text("Search cards", color = GoldDim) },
                    placeholder = { Text("Try \"is:commander c:g\"", color = TextDim) },
                    singleLine = true,
                    shape = RoundedCornerShape(2.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Gold,
                        unfocusedBorderColor = BorderColor,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = Gold,
                        focusedContainerColor = Surface,
                        unfocusedContainerColor = Surface
                    ),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = viewModel::search) {
                    Icon(Icons.Filled.Search, contentDescription = "Search", tint = Gold)
                }
                IconButton(onClick = { showFilters = true }) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Icon(Icons.Filled.Tune, contentDescription = "Filters", tint = if (filters.isActive) Gold else TextMuted)
                        if (filters.isActive) {
                            Box(
                                Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(Gold)
                            )
                        }
                    }
                }
            }

            when (val state = uiState) {
                is SearchUiState.Idle -> Text(
                    "Search a card name, or use Scryfall syntax like “is:commander c:g”.",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(top = 28.dp)
                )
                is SearchUiState.Loading -> Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) { CircularProgressIndicator(color = Gold) }
                is SearchUiState.Error -> Text(
                    state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 24.dp)
                )
                is SearchUiState.Success -> if (state.cards.isEmpty()) {
                    Text(
                        "No cards match.",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(top = 28.dp)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(top = 20.dp)
                    ) {
                        items(state.cards, key = { it.id }) { card ->
                            CardResultRow(card = card, onClick = { onCardClick(card) })
                        }
                    }
                }
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(
            onDismissRequest = { showFilters = false },
            sheetState = sheetState,
            containerColor = Surface
        ) {
            SearchFiltersSheet(
                initial = filters,
                onApply = { viewModel.onFiltersChange(it); showFilters = false },
                onClear = { viewModel.onFiltersChange(SearchFilters()); showFilters = false }
            )
        }
    }
}

@Composable
private fun SearchFiltersSheet(
    initial: SearchFilters,
    onApply: (SearchFilters) -> Unit,
    onClear: () -> Unit
) {
    var draft by remember { mutableStateOf(initial) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("FILTERS", style = MaterialTheme.typography.labelLarge, color = GoldLight)

        FilterField("Type line", draft.typeLine, "e.g. legendary creature") { draft = draft.copy(typeLine = it) }
        FilterField("Oracle text", draft.oracle, "e.g. draw a card") { draft = draft.copy(oracle = it) }
        FilterField("Mana cost", draft.manaCost, "e.g. {2}{U}{U}") { draft = draft.copy(manaCost = it) }
        FilterField("Sets", draft.sets, "set codes, e.g. MH3, LTR") { draft = draft.copy(sets = it) }

        FilterLabel("Rarity")
        ChipRow(
            options = listOf("common", "uncommon", "rare", "mythic"),
            selected = draft.rarities,
            onToggle = { draft = draft.copy(rarities = draft.rarities.toggle(it)) }
        )

        FilterLabel("Price (USD)")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField("Min", draft.priceMin, Modifier.weight(1f)) { draft = draft.copy(priceMin = it) }
            NumberField("Max", draft.priceMax, Modifier.weight(1f)) { draft = draft.copy(priceMax = it) }
        }

        FilterLabel("Power")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField("Min", draft.powerMin, Modifier.weight(1f)) { draft = draft.copy(powerMin = it) }
            NumberField("Max", draft.powerMax, Modifier.weight(1f)) { draft = draft.copy(powerMax = it) }
        }

        FilterLabel("Toughness")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField("Min", draft.toughnessMin, Modifier.weight(1f)) { draft = draft.copy(toughnessMin = it) }
            NumberField("Max", draft.toughnessMax, Modifier.weight(1f)) { draft = draft.copy(toughnessMax = it) }
        }

        FilterLabel("Finishes")
        ChipRow(
            options = listOf("nonfoil", "foil", "etched"),
            selected = draft.finishes,
            onToggle = { draft = draft.copy(finishes = draft.finishes.toggle(it)) }
        )

        FilterField("Artist", draft.artist, "e.g. Rebecca Guay") { draft = draft.copy(artist = it) }

        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = { draft = SearchFilters(); onClear() }, modifier = Modifier.weight(1f)) {
                Text("Clear all", color = TextMuted)
            }
            Button(
                onClick = { onApply(draft) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
            ) { Text("Apply") }
        }
    }
}

private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item

@Composable
private fun FilterLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = GoldDim)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChipRow(options: List<String>, selected: Set<String>, onToggle: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option in selected,
                onClick = { onToggle(option) },
                label = { Text(option.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Gold,
                    selectedLabelColor = Bg,
                    labelColor = TextMuted,
                    containerColor = Bg
                )
            )
        }
    }
}

@Composable
private fun FilterField(label: String, value: String, placeholder: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = GoldDim) },
        placeholder = { Text(placeholder, color = TextDim, style = MaterialTheme.typography.bodySmall) },
        singleLine = true,
        shape = RoundedCornerShape(2.dp),
        colors = filterFieldColors(),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun NumberField(label: String, value: String, modifier: Modifier, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = GoldDim) },
        singleLine = true,
        shape = RoundedCornerShape(2.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = filterFieldColors(),
        modifier = modifier
    )
}

@Composable
private fun filterFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Gold,
    unfocusedBorderColor = BorderColor,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = Gold,
    focusedContainerColor = Bg,
    unfocusedContainerColor = Bg
)

@Composable
private fun GoldDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(BorderColor, Gold.copy(alpha = 0.5f), BorderColor)
                )
            )
    )
}

@Composable
private fun CardResultRow(card: ScryfallCard, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Surface)
            .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        AsyncImage(
            model = card.displayImageUrl,
            contentDescription = card.name,
            modifier = Modifier.size(width = 48.dp, height = 67.dp).clip(RoundedCornerShape(3.dp))
        )
        Column {
            Text(card.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text(
                (card.typeLine ?: "").uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
