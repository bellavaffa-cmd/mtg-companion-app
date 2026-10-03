package com.mtgcompanion.app.ui.collection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.SetProgress
import com.mtgcompanion.app.data.SetSort
import com.mtgcompanion.app.data.sortedSets
import com.mtgcompanion.app.ui.common.PillChip
import com.mtgcompanion.app.ui.theme.LocalAppColors

/**
 * The Collection's Sets page: every set the user owns a card from, how much of it they have (owned
 * printings / the set's size, from Scryfall), sorted by how complete, by name or newest first. A set
 * opens its cards, owned and missing.
 */
@Composable
fun SetsTab(viewModel: CollectionsViewModel, onOpenSet: (String) -> Unit) {
    val colors = LocalAppColors.current
    LaunchedEffect(Unit) { viewModel.loadSets() }
    val progress by viewModel.setProgress.collectAsState()
    val failed by viewModel.setsFailed.collectAsState()
    val allCards by viewModel.allCards.collectAsState()
    var sortName by rememberSaveable { mutableStateOf(SetSort.PERCENT.name) }
    val sort = SetSort.entries.firstOrNull { it.name == sortName } ?: SetSort.PERCENT
    var query by remember { mutableStateOf("") }

    val list = progress
    when {
        allCards.isEmpty() -> Message("Cards you own show here, set by set, with how much of each set you have.")
        failed && list == null -> Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Couldn't fetch the sets from Scryfall. Check the connection and try again.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            OutlinedButton(onClick = { viewModel.loadSets() }) { Text("Try again", color = colors.accent) }
        }
        list == null -> Message("Looking up sets…")
        list.isEmpty() -> Message("Looking up which sets your cards are from…")
        else -> {
            val shown = sortedSets(
                if (query.isBlank()) list else list.filter { it.set.name.contains(query.trim(), ignoreCase = true) || it.set.code.equals(query.trim(), ignoreCase = true) },
                sort
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "search") {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Set name or code", color = colors.textMuted) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = colors.accent) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            cursorColor = colors.accent,
                            focusedContainerColor = colors.surface,
                            unfocusedContainerColor = colors.surface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item(key = "sort") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        SetSort.entries.forEach { s -> PillChip(s.label, s == sort, { sortName = s.name }) }
                    }
                }
                item(key = "count") {
                    val complete = list.count { it.complete }
                    Text(
                        "${list.size} ${if (list.size == 1) "set" else "sets"}" + if (complete > 0) " · $complete complete" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted
                    )
                }
                items(shown, key = { it.set.code }) { p -> SetRow(p) { onOpenSet(p.set.code) } }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textMuted, modifier = Modifier.padding(20.dp))
}

@Composable
private fun SetRow(p: SetProgress, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface)
            .border(BorderStroke(1.dp, if (p.complete) colors.accent else colors.border), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            if (p.set.iconSvgUri != null) {
                AsyncImage(
                    model = p.set.iconSvgUri,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(if (p.complete) colors.accent else colors.textPrimary),
                    modifier = Modifier.size(26.dp)
                )
            } else {
                Text(p.set.code.uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.textMuted, maxLines = 1)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.set.name, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(
                    if (p.total > 0) "${p.percent}%" else "—",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (p.complete) colors.accent else colors.textPrimary
                )
            }
            LinearProgressIndicator(
                progress = { p.fraction },
                color = colors.accent,
                trackColor = colors.border,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                listOfNotNull(
                    if (p.total > 0) "${p.owned} / ${p.total} cards" else "${p.owned} cards · size unknown",
                    p.set.code.uppercase(),
                    p.set.releasedAt?.take(4)
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
                maxLines = 1
            )
        }
    }
}
