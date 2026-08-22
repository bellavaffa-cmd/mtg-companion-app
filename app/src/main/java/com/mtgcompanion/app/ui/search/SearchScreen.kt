package com.mtgcompanion.app.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.spellbook.Variant
import com.mtgcompanion.app.ui.common.ComboDetailDialog
import com.mtgcompanion.app.ui.common.ComboSummaryRow
import com.mtgcompanion.app.ui.common.ManaSymbol
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldDim
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.TextDim
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary

/**
 * The Search tab's front page: build a query and every filter here, then press Search to see
 * results on their own page (SearchResultsScreen). The random/shuffle button is the one exception
 * that bypasses filters entirely, jumping straight to a random card's detail page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onCardClick: (ScryfallCard) -> Unit,
    onOpenResults: () -> Unit
) {
    val mode by viewModel.mode.collectAsState()
    val query by viewModel.query.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val sortBy by viewModel.sortBy.collectAsState()
    val sortDirection by viewModel.sortDirection.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()

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
                        if (mode == SearchMode.CARDS) {
                            TextButton(onClick = { viewModel.onFiltersChange(SearchFilters()) }) {
                                Text("Clear all", color = TextMuted)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
                )
                GoldDivider()
            }
        },
        bottomBar = {
            if (mode == SearchMode.CARDS) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Bg)
                        .border(BorderStroke(1.dp, BorderColor))
                        .padding(16.dp)
                ) {
                    Button(
                        onClick = { viewModel.search(); onOpenResults() },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = Bg, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("SEARCH", style = MaterialTheme.typography.labelLarge, color = Bg)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Bg)
                .padding(padding)
                .let { if (mode == SearchMode.CARDS) it.verticalScroll(rememberScrollState()) else it }
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip("Cards", mode == SearchMode.CARDS) { viewModel.setMode(SearchMode.CARDS) }
                ModeChip("Combos", mode == SearchMode.COMBOS) { viewModel.setMode(SearchMode.COMBOS) }
            }

            if (mode == SearchMode.COMBOS) {
                val comboCardQuery by viewModel.comboCardQuery.collectAsState()
                val comboResultQuery by viewModel.comboResultQuery.collectAsState()
                val comboColors by viewModel.comboColors.collectAsState()
                val combos by viewModel.combos.collectAsState()
                ComboSearchBody(
                    cardQuery = comboCardQuery,
                    resultQuery = comboResultQuery,
                    colors = comboColors,
                    state = combos,
                    onCardQueryChange = viewModel::onComboCardQueryChange,
                    onResultQueryChange = viewModel::onComboResultQueryChange,
                    onToggleColor = viewModel::toggleComboColor
                )
            } else {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = viewModel::onQueryChange,
                            label = { Text("Search cards", color = GoldDim) },
                            placeholder = { Text("Try \"is:commander c:g\"", color = TextDim) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
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
                        IconButton(onClick = { viewModel.randomCard(onCardClick) }) {
                            Icon(Icons.Filled.Shuffle, contentDescription = "Random card", tint = TextMuted)
                        }
                    }
                    if (suggestions.isNotEmpty()) {
                        SuggestionsDropdown(
                            suggestions,
                            onPick = { name -> viewModel.pickSuggestion(name); onOpenResults() }
                        )
                    }
                }

                SortSection(
                    sortBy = sortBy,
                    sortDirection = sortDirection,
                    onSortChange = viewModel::onSortChange,
                    onSortDirectionChange = viewModel::onSortDirectionChange
                )

                FilterField("Type line", filters.typeLine, "e.g. legendary creature") { viewModel.onFiltersChange(filters.copy(typeLine = it)) }
                FilterField("Oracle text", filters.oracle, "e.g. draw a card") { viewModel.onFiltersChange(filters.copy(oracle = it)) }
                FilterField("Sets", filters.sets, "set codes, e.g. MH3, LTR") { viewModel.onFiltersChange(filters.copy(sets = it)) }

                Column {
                    FilterLabel("Colors")
                    ManaColorPicker(
                        selected = filters.colors,
                        onToggle = { color -> viewModel.onFiltersChange(filters.copy(colors = filters.colors.toggle(color))) }
                    )
                }

                Column {
                    FilterLabel("Commander (color identity)")
                    ManaColorPicker(
                        selected = filters.colorIdentity,
                        onToggle = { color -> viewModel.onFiltersChange(filters.copy(colorIdentity = filters.colorIdentity.toggle(color))) }
                    )
                }

                RarityDropdown(
                    selected = filters.rarities,
                    onToggle = { viewModel.onFiltersChange(filters.copy(rarities = filters.rarities.toggle(it))) }
                )

                Column {
                    FilterLabel("Price (USD)")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                        NumberField("Min", filters.priceMin, Modifier.weight(1f)) { viewModel.onFiltersChange(filters.copy(priceMin = it)) }
                        NumberField("Max", filters.priceMax, Modifier.weight(1f)) { viewModel.onFiltersChange(filters.copy(priceMax = it)) }
                    }
                }

                Column {
                    FilterLabel("Power")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                        NumberField("Min", filters.powerMin, Modifier.weight(1f)) { viewModel.onFiltersChange(filters.copy(powerMin = it)) }
                        NumberField("Max", filters.powerMax, Modifier.weight(1f)) { viewModel.onFiltersChange(filters.copy(powerMax = it)) }
                    }
                }

                Column {
                    FilterLabel("Toughness")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                        NumberField("Min", filters.toughnessMin, Modifier.weight(1f)) { viewModel.onFiltersChange(filters.copy(toughnessMin = it)) }
                        NumberField("Max", filters.toughnessMax, Modifier.weight(1f)) { viewModel.onFiltersChange(filters.copy(toughnessMax = it)) }
                    }
                }

                Column {
                    FilterLabel("Finishes")
                    Row(modifier = Modifier.padding(top = 8.dp)) {
                        ChipRow(
                            options = listOf("nonfoil", "foil", "etched"),
                            selected = filters.finishes,
                            onToggle = { viewModel.onFiltersChange(filters.copy(finishes = filters.finishes.toggle(it))) }
                        )
                    }
                }

                FilterField("Artist", filters.artist, "e.g. Rebecca Guay") { viewModel.onFiltersChange(filters.copy(artist = it)) }

                Spacer(Modifier.size(12.dp))
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Gold,
            selectedLabelColor = Bg,
            labelColor = TextMuted,
            containerColor = Bg
        )
    )
}

/** Combo search: card name + what it produces are free text (Commander Spellbook does substring
 * matching on both); Commander (color identity) is a WUBRG multi-select meaning "combos that fit
 * within these colors" — the same "what can my commander's identity support" filter Card search
 * already has, applied to the combo's own color identity instead of a card's. The three combine
 * into one query string in the ViewModel. */
@Composable
private fun ComboSearchBody(
    cardQuery: String,
    resultQuery: String,
    colors: Set<Char>,
    state: ComboSearchState,
    onCardQueryChange: (String) -> Unit,
    onResultQueryChange: (String) -> Unit,
    onToggleColor: (Char) -> Unit
) {
    var showCombo by remember { mutableStateOf<Variant?>(null) }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Gold,
        unfocusedBorderColor = BorderColor,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        cursorColor = Gold,
        focusedContainerColor = Surface,
        unfocusedContainerColor = Surface
    )
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = cardQuery,
            onValueChange = onCardQueryChange,
            placeholder = { Text("Card used in the combo", color = TextDim) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextMuted) },
            shape = RoundedCornerShape(8.dp),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
        )
        OutlinedTextField(
            value = resultQuery,
            onValueChange = onResultQueryChange,
            placeholder = { Text("Produces (e.g. infinite mana, extra turns)", color = TextDim) },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        )
        FilterLabel("Commander (color identity)")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
            WUBRG.forEach { color ->
                val isSelected = color in colors
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) Gold.copy(alpha = 0.22f) else Surface)
                        .border(
                            BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) Gold else BorderColor),
                            CircleShape
                        )
                        .clickable { onToggleColor(color) },
                    contentAlignment = Alignment.Center
                ) {
                    ManaSymbol(color.toString(), size = 22.dp)
                }
            }
        }

        when (state) {
            ComboSearchState.Idle -> Text(
                "Search by card, effect, or commander color identity to find combos.",
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(top = 20.dp)
            )
            ComboSearchState.Loading -> Box(
                modifier = Modifier.fillMaxWidth().padding(top = 30.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = Gold) }
            is ComboSearchState.Error -> Text(
                state.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 20.dp)
            )
            is ComboSearchState.Loaded -> if (state.combos.isEmpty()) {
                Text(
                    "No combos match.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 20.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize().padding(top = 14.dp)
                ) {
                    items(state.combos, key = { it.id }) { combo ->
                        ComboSummaryRow(combo, onClick = { showCombo = combo })
                    }
                }
            }
        }
    }
    showCombo?.let { combo ->
        ComboDetailDialog(combo = combo, onDismiss = { showCombo = null })
    }
}

/** Name suggestions below the search bar, from Scryfall's autocomplete endpoint. */
@Composable
private fun SuggestionsDropdown(suggestions: List<String>, onPick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
            .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(10.dp))
    ) {
        suggestions.forEach { name ->
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(name) }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            )
        }
    }
}

/** Sort field + direction, as a single tap-to-open dropdown row alongside the other filters. */
@Composable
private fun SortSection(
    sortBy: SortOption,
    sortDirection: SortDirection,
    onSortChange: (SortOption) -> Unit,
    onSortDirectionChange: (SortDirection) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val summary = if (sortBy == SortOption.RELEVANCE) sortBy.label else "${sortBy.label} · ${sortDirection.label}"
    Column {
        FilterLabel("Sort")
        Box(modifier = Modifier.padding(top = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Bg)
                    .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(8.dp))
                    .clickable { open = true }
                    .padding(horizontal = 14.dp, vertical = 14.dp)
            ) {
                Text(summary, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose sort", tint = Gold)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(Surface)) {
                Text(
                    "DIRECTION",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                SortDirection.entries.forEach { direction ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                direction.label,
                                color = if (direction == sortDirection) Gold else TextPrimary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        onClick = { onSortDirectionChange(direction) }
                    )
                }
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(BorderColor))
                Text(
                    "SORT BY",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                SortOption.entries.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                option.label,
                                color = if (option == sortBy) Gold else TextPrimary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        onClick = { onSortChange(option); open = false }
                    )
                }
            }
        }
    }
}

/** Five clickable mana-color icons, multi-selectable — shared by the Colors and Commander filters. */
@Composable
private fun ManaColorPicker(selected: Set<Char>, onToggle: (Char) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
        WUBRG.forEach { color ->
            val isSelected = color in selected
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Gold.copy(alpha = 0.22f) else Surface)
                    .border(
                        BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) Gold else BorderColor),
                        CircleShape
                    )
                    .clickable { onToggle(color) },
                contentAlignment = Alignment.Center
            ) {
                ManaSymbol(color.toString(), size = 26.dp)
            }
        }
    }
}

/** Rarity as a multi-select dropdown — tapping toggles a rarity without closing the menu. */
@Composable
private fun RarityDropdown(selected: Set<String>, onToggle: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val options = listOf("common", "uncommon", "rare", "mythic")
    val summary = if (selected.isEmpty()) {
        "Any rarity"
    } else {
        options.filter { it in selected }.joinToString(", ") { it.replaceFirstChar(Char::uppercase) }
    }
    Column {
        FilterLabel("Rarity")
        Box(modifier = Modifier.padding(top = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Bg)
                    .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(8.dp))
                    .clickable { open = true }
                    .padding(horizontal = 14.dp, vertical = 14.dp)
            ) {
                Text(summary, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose rarity", tint = Gold)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(Surface)) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = option in selected,
                                    onCheckedChange = { onToggle(option) },
                                    colors = CheckboxDefaults.colors(checkedColor = Gold, uncheckedColor = TextMuted, checkmarkColor = Bg)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(option.replaceFirstChar(Char::uppercase), color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                            }
                        },
                        onClick = { onToggle(option) }
                    )
                }
            }
        }
    }
}

private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item

@Composable
private fun FilterLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = GoldDim)
}

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
        shape = RoundedCornerShape(8.dp),
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
        shape = RoundedCornerShape(8.dp),
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
