package com.mtgcompanion.app.ui.rules

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.rules.Keyword
import com.mtgcompanion.app.network.scryfall.ScryfallRuling
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
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
fun RulesScreen(viewModel: RulesViewModel) {
    val mode by viewModel.mode.collectAsState()
    val query by viewModel.query.collectAsState()
    val keywords by viewModel.keywords.collectAsState()
    val rulings by viewModel.rulings.collectAsState()

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text("RULES", style = MaterialTheme.typography.labelLarge, color = GoldLight) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().background(Bg).padding(padding).padding(20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip("Keywords", mode == RulesMode.KEYWORDS) { viewModel.setMode(RulesMode.KEYWORDS) }
                ModeChip("Card rulings", mode == RulesMode.RULINGS) { viewModel.setMode(RulesMode.RULINGS) }
            }

            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                placeholder = {
                    Text(
                        if (mode == RulesMode.KEYWORDS) "Search keywords (e.g. trample)" else "Card name for rulings",
                        color = TextDim
                    )
                },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextMuted) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear", tint = TextMuted)
                        }
                    }
                },
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
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
            )

            if (mode == RulesMode.KEYWORDS) {
                KeywordsList(keywords)
            } else {
                RulingsBody(rulings)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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

@Composable
private fun KeywordsList(keywords: List<Keyword>) {
    if (keywords.isEmpty()) {
        Text(
            "No keywords match. Try the Card rulings tab for card-specific questions.",
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            modifier = Modifier.padding(top = 20.dp)
        )
        return
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize().padding(top = 14.dp)
    ) {
        items(keywords, key = { it.name }) { keyword -> KeywordCard(keyword) }
    }
}

@Composable
private fun KeywordCard(keyword: Keyword) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Surface)
            .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(6.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(keyword.name, style = MaterialTheme.typography.titleMedium, color = GoldLight, modifier = Modifier.weight(1f))
            Text(keyword.category.uppercase(), style = MaterialTheme.typography.labelMedium, color = TextDim)
        }
        Text(
            keyword.text,
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun RulingsBody(state: RulingsState) {
    when (state) {
        is RulingsState.Idle -> Text(
            "Enter a card name to see its official rulings from Scryfall.",
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            modifier = Modifier.padding(top = 20.dp)
        )
        is RulingsState.Loading -> Column(
            modifier = Modifier.fillMaxWidth().padding(top = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) { CircularProgressIndicator(color = Gold) }
        is RulingsState.Error -> Text(
            state.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 20.dp)
        )
        is RulingsState.Loaded -> LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize().padding(top = 14.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = state.card.displayImageUrl.toArtCropUrl(),
                        contentDescription = state.card.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(width = 72.dp, height = 52.dp).clip(RoundedCornerShape(4.dp))
                    )
                    Text(
                        state.card.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = GoldLight,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }
            if (state.rulings.isEmpty()) {
                item {
                    Text(
                        "No official rulings for this card.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            } else {
                items(state.rulings) { ruling -> RulingCard(ruling) }
            }
        }
    }
}

@Composable
private fun RulingCard(ruling: ScryfallRuling) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Surface)
            .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(6.dp))
            .padding(14.dp)
    ) {
        Text(ruling.comment, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
        val meta = listOfNotNull(
            ruling.source?.let { if (it == "wotc") "Wizards of the Coast" else it.replaceFirstChar { c -> c.uppercase() } },
            ruling.publishedAt
        ).joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(meta, style = MaterialTheme.typography.labelMedium, color = TextDim, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
