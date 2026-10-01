package com.mtgcompanion.app.ui.collection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.ui.common.ManaSymbol
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.TextDim
import com.mtgcompanion.app.ui.theme.TextPrimary
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldDim
import com.mtgcompanion.app.ui.theme.OnGold
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.TextMuted

/** What the All cards filter needs to know about a card, taken from its Scryfall data. */
data class CardFacts(
    /** Color identity as WUBRG letters; empty for a colorless card. */
    val colors: Set<Char>,
    val typeLine: String,
    val rarity: String,
    /** The card's rules text, every face of it. */
    val text: String = ""
) {
    companion object {
        fun of(card: ScryfallCard) = CardFacts(
            colors = (card.colorIdentity ?: card.colors).orEmpty().mapNotNull { it.firstOrNull()?.uppercaseChar() }.toSet(),
            typeLine = card.typeLine.orEmpty(),
            rarity = card.rarity.orEmpty().lowercase(),
            text = card.displayOracleText.orEmpty()
        )
    }
}

/** The All cards filter: Search's type, text, color and rarity filters, over the cards you own. */
data class CollectionFilter(
    /** Words that must all be in the type line, e.g. "legendary creature". */
    val type: String = "",
    /** A phrase that must be in the rules text, e.g. "draw a card". */
    val text: String = "",
    val colors: Set<Char> = emptySet(),
    val rarities: Set<String> = emptySet()
) {
    val active: Boolean get() = type.isNotBlank() || text.isNotBlank() || colors.isNotEmpty() || rarities.isNotEmpty()
    val count: Int get() = (if (type.isNotBlank()) 1 else 0) + (if (text.isNotBlank()) 1 else 0) + colors.size + rarities.size

    /**
     * A card passes when its type line has every word typed, its rules text has the phrase typed,
     * it has every chosen color and is any of the chosen rarities. A card whose data hasn't loaded
     * ([facts] null) can't be judged, so it's left out while a filter is on.
     */
    fun matches(facts: CardFacts?): Boolean {
        if (!active) return true
        if (facts == null) return false
        if (type.split(' ').any { it.isNotBlank() && !facts.typeLine.contains(it, ignoreCase = true) }) return false
        if (text.isNotBlank() && !facts.text.contains(text.trim(), ignoreCase = true)) return false
        if (!facts.colors.containsAll(colors)) return false
        if (rarities.isNotEmpty() && facts.rarity !in rarities) return false
        return true
    }
}

val COLLECTION_FILTER_RARITIES = listOf("common", "uncommon", "rare", "mythic")

private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item

/** The filter's controls, shown under All cards' search field while it's opened. */
@Composable
fun CollectionFilterPanel(filter: CollectionFilter, onChange: (CollectionFilter) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Surface)
            .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        if (filter.active) {
            Text(
                "Clear filters",
                style = MaterialTheme.typography.labelMedium,
                color = Gold,
                modifier = Modifier.clickable { onChange(CollectionFilter()) }.padding(vertical = 4.dp)
            )
        }
        // Named as in Search.
        FilterText("Type", filter.type, "e.g. legendary creature") { onChange(filter.copy(type = it)) }
        FilterText("Text", filter.text, "e.g. draw a card") { onChange(filter.copy(text = it)) }
        Text("Colors (at least)", style = MaterialTheme.typography.labelMedium, color = GoldDim)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            "WUBRG".forEach { color ->
                val isSelected = color in filter.colors
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) Gold.copy(alpha = 0.22f) else Surface)
                        .border(BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) Gold else BorderColor), CircleShape)
                        .clickable { onChange(filter.copy(colors = filter.colors.toggle(color))) },
                    contentAlignment = Alignment.Center
                ) {
                    ManaSymbol(color.toString(), size = 24.dp)
                }
            }
        }
        Text("Rarity", style = MaterialTheme.typography.labelMedium, color = GoldDim)
        FilterChips(COLLECTION_FILTER_RARITIES, filter.rarities) { onChange(filter.copy(rarities = filter.rarities.toggle(it))) }
    }
}

@Composable
private fun FilterText(label: String, value: String, placeholder: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = TextMuted) },
        placeholder = { Text(placeholder, color = TextDim, style = MaterialTheme.typography.bodySmall) },
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Gold,
            unfocusedBorderColor = BorderColor,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = Gold,
            focusedContainerColor = Bg,
            unfocusedContainerColor = Bg
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun FilterChips(options: List<String>, selected: Set<String>, onToggle: (String) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState())
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option in selected,
                onClick = { onToggle(option) },
                label = { Text(option.replaceFirstChar { it.uppercase() }) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Gold,
                    selectedLabelColor = OnGold,
                    labelColor = TextMuted,
                    containerColor = Surface
                )
            )
        }
    }
}
