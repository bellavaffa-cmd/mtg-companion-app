package com.mtgcompanion.app.ui.social

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.RoleTags
import com.mtgcompanion.app.ui.theme.LocalAppColors

// One search over someone else's cards by name or by what they do (a tag's label: "ramp",
// "removal"…), as the user's own decks and binders have. The web app's is
// src/tags/useNameTagSearch.tsx.

/** Looks up what [names] do (once), and re-reads as tags arrive. Returns whether some are still being looked up. */
@Composable
internal fun rememberCardTags(names: List<String>): Boolean {
    LaunchedEffect(names) { if (names.isNotEmpty()) RoleTags.ensure(names, CardRepository()) }
    RoleTags.version.collectAsState().value
    return RoleTags.progress.collectAsState().value != null
}

/** The cards [query] finds by name or tag; all of them for a blank query. */
internal fun <T> List<T>.byNameOrTag(query: String, name: (T) -> String): List<T> =
    if (query.isBlank()) this else filter { RoleTags.matches(name(it), RoleTags.tagsOf(name(it)).orEmpty(), query) }

/** A card's tags as labels, for the card zoom. */
internal fun tagLabelsOf(name: String): List<String> = RoleTags.tagsOf(name).orEmpty().map(RoleTags::label)

/** The search box, with "12 cards · tag: Mana ramp" under it while there's a query. */
@Composable
internal fun NameTagSearch(query: String, onQueryChange: (String) -> Unit, shownNames: List<String>, tagging: Boolean) {
    val colors = LocalAppColors.current
    Column {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Name or tag, e.g. ramp") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = colors.accent) },
            trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = colors.textDim) } }) else null,
            modifier = Modifier.fillMaxWidth()
        )
        if (query.isNotBlank()) {
            // The tags that found cards whose names didn't.
            val tagHits = shownNames.filterNot { it.contains(query.trim(), ignoreCase = true) }
                .flatMap { RoleTags.matched(RoleTags.tagsOf(it).orEmpty(), query) }.distinct()
            Text(
                "${shownNames.size} ${if (shownNames.size == 1) "card" else "cards"}" +
                    (if (tagHits.isNotEmpty()) " · tag: " + tagHits.take(2).joinToString(", ") { RoleTags.label(it) } + if (tagHits.size > 2) "…" else "" else "") +
                    if (tagging) " · finding tags…" else "",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )
        }
    }
}
