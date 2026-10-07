package com.mtgcompanion.app.ui.collection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.FindChipKind
import com.mtgcompanion.app.data.Finder
import com.mtgcompanion.app.data.FoundCard
import com.mtgcompanion.app.data.FoundDeck
import com.mtgcompanion.app.data.buildFindIndex
import com.mtgcompanion.app.data.chipLabel
import com.mtgcompanion.app.data.copiesLine
import com.mtgcompanion.app.ui.common.a11yHeading
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Find anything (data/FindAnything.kt), the web app's FindAnythingPage.tsx: one search over the
 * user's cards (each with its copies and every place they are), places and decks, and "Search all
 * cards" for what isn't theirs. The library is indexed once when it changes (off the main thread);
 * each keystroke filters what the last one found.
 */

@Composable
fun FindAnythingScreen(
    collections: List<Collection>,
    decks: List<Deck>,
    onBack: () -> Unit,
    onOpenCard: (String) -> Unit,
    onOpenPlace: (String) -> Unit,
    onOpenDeck: (String) -> Unit,
    /** Search, for cards that aren't the user's. */
    onSearchAll: (String) -> Unit
) {
    val colors = LocalAppColors.current
    var query by rememberSaveable { mutableStateOf("") }
    var finder by remember { mutableStateOf<Finder?>(null) }
    LaunchedEffect(collections, decks) {
        finder = withContext(Dispatchers.Default) { Finder(buildFindIndex(collections, decks)) }
    }
    val result = remember(finder, query) { finder?.find(query) }
    val typed = query.trim()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().background(colors.bg)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surface)
                    .border(2.dp, colors.accent, RoundedCornerShape(14.dp))
                    .padding(start = 14.dp, end = 4.dp)
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text("Find a card, a place or a deck", style = MaterialTheme.typography.bodyLarge, color = colors.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary, fontWeight = FontWeight.SemiBold),
                        cursorBrush = SolidColor(colors.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {}),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = "Find" }
                    )
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Clear", tint = colors.textMuted) }
                }
            }
            TextButton(onClick = onBack) { Text("Cancel", color = colors.accent, fontWeight = FontWeight.SemiBold) }
        }

        if (typed.isNotEmpty() && result != null) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (result.cards.isNotEmpty()) {
                    item(key = "h-cards") { SectionLabel("Your cards") }
                    items(result.cards, key = { "c-" + it.name }) { card -> CardResult(card) { onOpenCard(card.name) } }
                    if (result.moreCards > 0) item(key = "more") {
                        Text("And ${result.moreCards} more — type a little more of the name", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                    }
                }
                if (result.places.isNotEmpty()) {
                    item(key = "h-places") { SectionLabel("Places") }
                    items(result.places, key = { "p-" + it.id }) { p -> ResultRow(p.name, p.line) { onOpenPlace(p.id) } }
                }
                if (result.decksUsing.isNotEmpty()) {
                    item(key = "h-using") { SectionLabel("Decks using it") }
                    items(result.decksUsing, key = { "u-" + it.id }) { d: FoundDeck -> ResultRow(d.name, d.line) { onOpenDeck(d.id) } }
                }
                if (result.decks.isNotEmpty()) {
                    item(key = "h-decks") { SectionLabel("Decks") }
                    items(result.decks, key = { "d-" + it.id }) { d: FoundDeck -> ResultRow(d.name, d.line) { onOpenDeck(d.id) } }
                }
                item(key = "h-not") { SectionLabel("Not yours") }
                item(key = "search-all") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface)
                            .clickable(role = Role.Button) { onSearchAll(typed) }.padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text("Search all cards for \"$typed\"", style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.textDim)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val colors = LocalAppColors.current
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = colors.textMuted,
        letterSpacing = 0.5.sp, modifier = Modifier.padding(top = 6.dp).a11yHeading()
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardResult(card: FoundCard, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AsyncImage(
                model = card.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 30.dp, height = 42.dp).clip(RoundedCornerShape(4.dp)).background(colors.surface3)
            )
            Text(card.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.textPrimary, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(copiesLine(card), style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
        }
        if (card.chips.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                card.chips.forEach { chip ->
                    val lent = chip.kind == FindChipKind.LENT
                    Text(
                        chipLabel(chip),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (lent) colors.accentLight else colors.textPrimary,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                            .background(if (lent) colors.accent.copy(alpha = 0.16f) else colors.surface2)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultRow(name: String, line: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(line, style = MaterialTheme.typography.labelMedium, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
    }
}
