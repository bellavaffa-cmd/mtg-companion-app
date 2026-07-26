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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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

/** Full-screen filter builder for card search — every field lives on its own page, reached from the Tune icon. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchFiltersScreen(
    filters: SearchFilters,
    onChange: (SearchFilters) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text("FILTERS", color = GoldLight, style = MaterialTheme.typography.labelLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Gold)
                    }
                },
                actions = { TextButton(onClick = onClear) { Text("Clear all", color = TextMuted) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Bg)
                    .border(BorderStroke(1.dp, BorderColor))
                    .padding(16.dp)
            ) {
                Button(
                    onClick = onSearch,
                    shape = RoundedCornerShape(2.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = Bg, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("SEARCH", style = MaterialTheme.typography.labelLarge, color = Bg)
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            FilterField("Type line", filters.typeLine, "e.g. legendary creature") { onChange(filters.copy(typeLine = it)) }
            FilterField("Oracle text", filters.oracle, "e.g. draw a card") { onChange(filters.copy(oracle = it)) }
            FilterField("Sets", filters.sets, "set codes, e.g. MH3, LTR") { onChange(filters.copy(sets = it)) }

            Column {
                FilterLabel("Colors")
                ManaColorPicker(
                    selected = filters.colors,
                    onToggle = { color -> onChange(filters.copy(colors = filters.colors.toggle(color))) }
                )
            }

            Column {
                FilterLabel("Commander (color identity)")
                ManaColorPicker(
                    selected = filters.colorIdentity,
                    onToggle = { color -> onChange(filters.copy(colorIdentity = filters.colorIdentity.toggle(color))) }
                )
            }

            RarityDropdown(
                selected = filters.rarities,
                onToggle = { onChange(filters.copy(rarities = filters.rarities.toggle(it))) }
            )

            Column {
                FilterLabel("Price (USD)")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                    NumberField("Min", filters.priceMin, Modifier.weight(1f)) { onChange(filters.copy(priceMin = it)) }
                    NumberField("Max", filters.priceMax, Modifier.weight(1f)) { onChange(filters.copy(priceMax = it)) }
                }
            }

            Column {
                FilterLabel("Power")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                    NumberField("Min", filters.powerMin, Modifier.weight(1f)) { onChange(filters.copy(powerMin = it)) }
                    NumberField("Max", filters.powerMax, Modifier.weight(1f)) { onChange(filters.copy(powerMax = it)) }
                }
            }

            Column {
                FilterLabel("Toughness")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                    NumberField("Min", filters.toughnessMin, Modifier.weight(1f)) { onChange(filters.copy(toughnessMin = it)) }
                    NumberField("Max", filters.toughnessMax, Modifier.weight(1f)) { onChange(filters.copy(toughnessMax = it)) }
                }
            }

            Column {
                FilterLabel("Finishes")
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    ChipRow(
                        options = listOf("nonfoil", "foil", "etched"),
                        selected = filters.finishes,
                        onToggle = { onChange(filters.copy(finishes = filters.finishes.toggle(it))) }
                    )
                }
            }

            FilterField("Artist", filters.artist, "e.g. Rebecca Guay") { onChange(filters.copy(artist = it)) }

            Spacer(Modifier.size(12.dp))
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
                    .clip(RoundedCornerShape(2.dp))
                    .background(Bg)
                    .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(2.dp))
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
