package com.mtgcompanion.app.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionType
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.PlacedCard
import com.mtgcompanion.app.data.CopyGroup
import com.mtgcompanion.app.data.RoleTags
import com.mtgcompanion.app.data.StoragePlace
import com.mtgcompanion.app.data.namesDecksUse
import com.mtgcompanion.app.data.placeChains
import com.mtgcompanion.app.data.placeTree
import com.mtgcompanion.app.data.placesOf
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.common.ArtImage
import com.mtgcompanion.app.ui.common.PillChip
import com.mtgcompanion.app.ui.common.rememberMoney
import com.mtgcompanion.app.ui.theme.LocalAppColors

/*
 * The Cards tab's filters, for what's in storage (PlaceFilter.kt): a search field with the filter
 * button, the filter panel and Spares only while it's open, the chips for what's on (each removable,
 * and one Clear) once it's shut, the Advanced filters screen with its saved filters, and the results
 * grouped by place › section with each row's place chip — on a place's page and the Storage tab.
 * The web app's src/collection/CardsFilterControls.tsx.
 */

/** The filter button: "Filters, 2 on", gold while any is on or the panel is open. */
@Composable
fun FilterIconButton(filtersOn: Int, open: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    IconButton(onClick = onClick) {
        BadgedBox(badge = { if (filtersOn > 0) Badge(containerColor = colors.accent, contentColor = colors.onAccent) { Text("$filtersOn") } }) {
            Icon(
                Icons.Filled.FilterList,
                contentDescription = if (filtersOn > 0) "Filters, $filtersOn on" else "Filters",
                tint = if (filtersOn > 0 || open) colors.accent else colors.textMuted
            )
        }
    }
}

/** A place's search field, with the filter button at its end. */
@Composable
fun PlaceSearchBar(query: String, onQueryChange: (String) -> Unit, hint: String, filtersOn: Int, filterOpen: Boolean, onFilter: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(hint, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = colors.accent) },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = colors.textMuted) }
                FilterIconButton(filtersOn, filterOpen, onFilter)
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = colors.border,
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            cursorColor = colors.accent,
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surface
        ),
        modifier = modifier.fillMaxWidth().semantics { contentDescription = hint }
    )
}

/**
 * Under the search: while [open], the filter panel and Spares only; once shut, a chip for each thing
 * on and Clear. [onAdvanced] opens the Advanced filters (CardsAdvancedFilters, kept outside a list so
 * scrolling can't close it).
 */
@Composable
fun CardsFilterArea(filter: CardsFilter, onChange: (CardsFilter) -> Unit, open: Boolean, onAdvanced: () -> Unit, collections: List<Collection>) {
    val money = rememberMoney()
    val clear = { onChange(CardsFilter()) }
    if (open) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CollectionFilterPanel(filter.basic, { onChange(filter.copy(basic = it)) }, onAdvanced = onAdvanced, onClear = clear, anyOn = filter.active)
            PillChip("Spares only", filter.sparesOnly, { onChange(filter.copy(sparesOnly = !filter.sparesOnly)) })
        }
    } else if (filter.active) {
        val places = placesOf(collections)
        ActiveFilterChips(
            cardsFilterChips(
                filter,
                { id -> collections.firstOrNull { it.id == id }?.name },
                { money.formatLocal(it) },
                placeName = { id -> places.firstOrNull { it.id == id }?.name }
            ),
            onRemove = { key -> onChange(removeCardsFilterChip(filter, key)) },
            onClear = clear,
            clearLabel = "Clear"
        )
    }
}

/** The Advanced filters over what's in storage: "Show N cards" counts copies; the saved filters are the Cards tab's. */
@Composable
fun CardsAdvancedFilters(
    filter: CardsFilter,
    onChange: (CardsFilter) -> Unit,
    countFor: (CardsFilter) -> Int,
    collections: List<Collection>,
    /** Set code to name, of the cards in question. */
    sets: List<Pair<String, String>>,
    saved: List<SavedFilter>,
    savedFilters: SavedFilters?,
    onDismiss: () -> Unit
) {
    AdvancedFilterScreen(
        basic = filter.basic,
        advanced = filter.advanced,
        countFor = { b, a -> countFor(filter.copy(basic = b, advanced = a)) },
        binders = collections.filter { it.kind == CollectionType.OWNED }.sortedBy { it.name.lowercase() }.map { it.id to it.name },
        sets = sets,
        saved = saved,
        onSave = { n, b, a -> savedFilters?.save(n, b, a) },
        onRename = { id, n -> savedFilters?.rename(id, n) },
        onDelete = { id -> savedFilters?.delete(id) },
        onApply = { b, a -> onChange(filter.copy(basic = b, advanced = a)); onDismiss() },
        onDismiss = onDismiss,
        places = placeTree(placesOf(collections)).map { it.place.id to ("  ".repeat(it.depth) + it.place.name) }
    )
}

/**
 * What judging copies in storage needs (FilterFacts), from the cards' facts already worked out (the
 * Cards tab's, or a place's own lookup), the collection, and the search's tags as Cards has them.
 */
fun storageFilterFacts(
    card: Map<String, CardFacts>,
    advanced: Map<String, AdvancedFacts>,
    copies: Map<String, CopyFacts>,
    collections: List<Collection>,
    decks: List<Deck>,
    toUsd: (Double) -> Double
): FilterFacts = FilterFacts(
    card = { card[it] },
    advanced = { advanced[it] },
    copies = copies,
    chains = placeChains(placesOf(collections)),
    usedNames = namesDecksUse(decks),
    toUsd = toUsd,
    searchMatches = { c, q -> RoleTags.matches(c.entry.name, RoleTags.tagsOf(c.entry.name).orEmpty(), q) }
)

/** "14 of 312 cards · $118" and Walk this list, over a filter's results. */
@Composable
fun FilterTotalsLine(line: String, canWalk: Boolean, onWalk: () -> Unit) {
    val colors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(line, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.textPrimary, modifier = Modifier.weight(1f))
        if (canWalk) Button(
            onClick = onWalk,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.surface2, contentColor = colors.textPrimary),
            modifier = Modifier.height(40.dp)
        ) {
            Icon(Icons.Filled.Checklist, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Walk this list", modifier = Modifier.padding(start = 6.dp))
        }
    }
}

/** Rows shown before "Show all": a big collection can match thousands. */
const val SHOWN_COPY_ROWS = 200

/**
 * Copies found in storage, grouped by place › section (copiesByPlace): each row the card, its finish
 * and how many, and its place chip ("Red box › Red ×2") — tap the chip to open the place there. At
 * most [SHOWN_COPY_ROWS] rows until [showAll].
 */
fun LazyListScope.copyGroupItems(
    groups: List<CopyGroup>,
    places: List<StoragePlace>,
    showAll: Boolean,
    onShowAll: () -> Unit,
    onOpenCard: (String) -> Unit,
    onOpenPlaceAt: (String, String?, Int?) -> Unit,
    keyPrefix: String
) {
    val byId = places.associateBy { it.id }
    var left = if (showAll) Int.MAX_VALUE else SHOWN_COPY_ROWS
    val shown = mutableListOf<Pair<CopyGroup, List<PlacedCard>>>()
    for (g in groups) {
        if (left <= 0) break
        val rows = g.copies.take(left)
        left -= rows.size
        shown += g to rows
    }
    items(shown, key = { keyPrefix + it.first.key }) { (g, rows) ->
        val colors = LocalAppColors.current
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(g.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (g.detail.isNotEmpty()) Text(g.detail, style = MaterialTheme.typography.labelMedium, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("${g.copies.sumOf { it.line.qty }}", style = MaterialTheme.typography.labelLarge, color = colors.textMuted)
            }
            rows.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onOpenCard(c.entry.name) }) {
                    ArtImage(c.entry.imageUrl.toArtCropUrl(), c.entry.name, Modifier.width(30.dp).height(42.dp).clip(RoundedCornerShape(4.dp)))
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(c.entry.name + if (c.line.isFoil) " · foil" else "", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        LocationChips(listOf(copyChip(c, byId[c.line.placeId])), ChipActions(onPlace = onOpenPlaceAt))
                    }
                }
            }
        }
    }
    val total = groups.sumOf { it.copies.size }
    if (!showAll && total > SHOWN_COPY_ROWS) item(key = keyPrefix + "all") {
        TextButton(onClick = onShowAll) { Text("Show all $total rows", color = LocalAppColors.current.accent) }
    }
}
