package com.mtgcompanion.app.ui.collection

import com.mtgcompanion.app.data.usage.Usage
import com.mtgcompanion.app.data.usage.UsageAction
import com.mtgcompanion.app.ui.common.EmptyAction
import com.mtgcompanion.app.ui.common.EmptyPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.DEFAULT_POCKETS
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.PlaceKind
import com.mtgcompanion.app.data.SortRule
import com.mtgcompanion.app.data.StoragePlace
import com.mtgcompanion.app.data.canMoveInto
import com.mtgcompanion.app.data.cardsByPlace
import com.mtgcompanion.app.data.childrenOf
import com.mtgcompanion.app.data.copiesByPlace
import com.mtgcompanion.app.data.walkPullList
import com.mtgcompanion.app.data.copiesWithin
import com.mtgcompanion.app.data.gearOf
import com.mtgcompanion.app.data.gearSummary
import com.mtgcompanion.app.data.placeAndInside
import com.mtgcompanion.app.data.placeSubtitle
import com.mtgcompanion.app.data.placeTree
import com.mtgcompanion.app.data.placesOf
import com.mtgcompanion.app.data.savePlace
import com.mtgcompanion.app.data.PlaceRules
import com.mtgcompanion.app.data.RuleCard
import com.mtgcompanion.app.data.holdsMap
import com.mtgcompanion.app.data.holdsSummary
import com.mtgcompanion.app.data.overlaps
import com.mtgcompanion.app.data.placeOrderOf
import com.mtgcompanion.app.data.placeRules
import com.mtgcompanion.app.data.ruleSections
import com.mtgcompanion.app.data.withRules
import com.mtgcompanion.app.data.storageSummary
import com.mtgcompanion.app.ui.theme.LocalAppColors
import com.mtgcompanion.app.ui.common.rememberMoney
import com.mtgcompanion.app.data.sealedOf
import com.mtgcompanion.app.data.sealedTotalUsd
import com.mtgcompanion.app.data.bindersByPlace
import com.mtgcompanion.app.data.bindersIn
import com.mtgcompanion.app.data.gradedOf
import com.mtgcompanion.app.data.inBindersLine
import com.mtgcompanion.app.ui.common.a11yHeading
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.IconButton
import androidx.compose.ui.semantics.Role
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

/*
 * The Collection's Storage tab: Put cards away (and the other storage tools under More), the places
 * themselves as a tree with their copies and the binders using them, then deck boxes, sealed, graded,
 * lent out and gear, and how much of the collection has a place — and the dialogs to make, change and
 * pick a place. The logic is in data/StoragePlaces.kt. Mirrors the web app's
 * src/collection/StorageTab.tsx.
 */

/** The icon for each kind of place. */
fun placeIcon(kind: PlaceKind): ImageVector = when (kind) {
    PlaceKind.BOX -> Icons.Filled.Inventory2
    PlaceKind.BINDER -> Icons.Filled.MenuBook
    PlaceKind.DECK_BOX -> Icons.Filled.Style
    PlaceKind.SHELF -> Icons.Filled.Layers
    PlaceKind.OTHER -> Icons.Filled.Folder
}

private fun count(n: Int): String = NumberFormat.getIntegerInstance(Locale.UK).format(n)

/** A change to the binders: the storage places, or where copies are kept. */
typealias StorageChange = (List<Collection>) -> List<Collection>

@Composable
fun StorageTab(
    collections: List<Collection>,
    decks: List<Deck>,
    onOpenPlace: (String) -> Unit,
    onPutAway: (String) -> Unit,
    onOpenDecks: () -> Unit,
    onChange: (StorageChange) -> Unit,
    /** The header's search: narrows the places to those it names. */
    query: String = "",
    /** The Loans screen (LoansScreen.kt). */
    onOpenLoans: () -> Unit = {},
    /** Sort a pile's recipes (SortRecipesScreen.kt). */
    onSortPile: () -> Unit = {},
    /** Value by place (ValueByPlaceScreen.kt). */
    onOpenValue: () -> Unit = {},
    /** How full each place is (SpaceScreen.kt). */
    onOpenSpace: () -> Unit = {},
    /** The To sell list (SellScreen.kt). */
    onOpenSell: () -> Unit = {},
    /** Getting started with storage (StorageSetupScreen.kt). */
    onSetUp: () -> Unit = {},
    /** Upkeep: what's worth doing this week (UpkeepScreen.kt). */
    onOpenUpkeep: () -> Unit = {},
    /** Sharing storage at home (HouseholdScreen.kt). */
    onOpenHousehold: () -> Unit = {},
    /** Sealed product (SealedScreen.kt). */
    onOpenSealed: () -> Unit = {},
    /** Every graded copy (GradedListScreen.kt). */
    onOpenGraded: () -> Unit = {},
    /** Gear: sleeves, deck boxes, tokens (GearScreen.kt). */
    onOpenGear: () -> Unit = {},
    /** Settings › Data and speed: the backup (DataAndSpeedSection.kt). */
    onOpenDataAndSpeed: () -> Unit = {},
    /** Search everywhere (Find anything) with the header's search. */
    onSearchEverywhere: () -> Unit = {},
    /** The Storage tab's own filters (the header's filter button): with the header's search, they also find the cards in storage. */
    filter: CardsFilter = CardsFilter(),
    onFilterChange: ((CardsFilter) -> Unit)? = null,
    filterOpen: Boolean = false,
    /** Opens the Advanced filters for the Storage tab's filters. */
    onAdvanced: () -> Unit = {},
    /** What the filters judge the cards by (the Cards tab's, from the view model). */
    facts: FilterFacts = FilterFacts(),
    /** scryfallId → its (plain, foil) price in US dollars; empty until loaded. */
    prices: Map<String, Pair<Double?, Double?>> = emptyMap(),
    onOpenCard: (String) -> Unit = {},
    onOpenPlaceAt: (String, String?, Int?) -> Unit = { id, _, _ -> onOpenPlace(id) },
    /** Walk this list (WalkListScreen): the list to walk is in WalkSession. */
    onWalk: () -> Unit = {},
    /** The saved filters, for a place's "Holds" (StorageRules.kt). */
    saved: List<SavedFilter> = emptyList(),
    savedFilters: SavedFilters? = null
) {
    val colors = LocalAppColors.current
    val places = placesOf(collections)
    // The user's own tags, offered in the filter panel's Tags row as on the Cards tab.
    val userTagOptions = remember(collections, decks) { com.mtgcompanion.app.data.allUserTags(decks, collections).take(12) }
    val summary = remember(collections, decks) { storageSummary(collections, decks) }
    val byPlace = remember(collections) { bindersByPlace(collections) }
    var editing by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    var ruleOrder by remember { mutableStateOf(false) }
    // The collection's cards as the places' rules judge them, for the rule editor's overlap warnings.
    val ruleCards = remember(collections, facts) { ruleCardsOf(collections, facts) }
    val share = if (summary.total > 0) summary.placed.toFloat() / summary.total else 0f
    val upkeep = rememberUpkeep(collections, decks)
    val money = rememberMoney()
    val q = query.trim()
    // The header's search and the filters, over every copy in storage: "Cards in storage matching …".
    val searching = filter.copy(query = query)
    val matchingOn = searching.active
    val stored = remember(collections) {
        val known = placesOf(collections).map { it.id }.toSet()
        cardsByPlace(collections).values.flatten().filter { it.line.placeId in known }
    }
    val matching = remember(stored, searching, facts) { if (matchingOn) filterCopies(stored, searching, facts) else emptyList() }
    var showAllRows by remember { mutableStateOf(false) }
    val putAway = { if (places.isEmpty()) onSetUp() else choosing = true }
    // A place's line: what it is, and the binders with copies in it ("in 2 binders").
    fun detail(p: StoragePlace, withSubtitle: Boolean): String? =
        listOf(if (withSubtitle) placeSubtitle(p) else "", inBindersLine(bindersIn(byPlace, places, p.id))).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Put cards away, and the rest of storage's tools behind More.
        item(key = "actions") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = putAway,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text(
                        if (summary.unplaced > 0) "Put cards away · ${count(summary.unplaced)}" else "Put cards away",
                        fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Box {
                    IconButton(onClick = { more = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More storage tools", tint = colors.textPrimary)
                    }
                    DropdownMenu(expanded = more, onDismissRequest = { more = false }, containerColor = colors.surface) {
                        val n = upkeep?.items?.size ?: 0
                        listOf(
                            (if (n > 0) "Upkeep · $n" else "Upkeep") to onOpenUpkeep,
                            "Set up storage" to onSetUp,
                            "Value by place" to onOpenValue,
                            "Space" to onOpenSpace,
                            "To sell" to onOpenSell,
                            "Sort a new pile" to onSortPile,
                            "Rule order" to { ruleOrder = true },
                            "Data and speed" to onOpenDataAndSpeed,
                            "Sharing storage at home" to onOpenHousehold
                        ).forEach { (label, open) ->
                            DropdownMenuItem(text = { Text(label, color = colors.textPrimary) }, onClick = { more = false; open() })
                        }
                    }
                }
            }
        }
        if (onFilterChange != null) item(key = "filters") {
            CardsFilterArea(filter, { onFilterChange(it.copy(query = "")) }, filterOpen, onAdvanced, collections, userTagOptions)
        }
        if (matchingOn && places.isNotEmpty()) {
            item(key = "matches-head") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Text(storageMatchingTitle(searching), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, modifier = Modifier.a11yHeading())
                    val usdOf: ((com.mtgcompanion.app.data.PlacedCard) -> Double?)? =
                        if (prices.isEmpty()) null else { c: com.mtgcompanion.app.data.PlacedCard -> prices[c.entry.scryfallId]?.let { (plain, foil) -> placedCopyUsd(c, plain, foil) } }
                    FilterTotalsLine(totalsLine(filterTotals(stored, matching, usdOf)) { money.format(it, whole = true) }, matching.isNotEmpty()) {
                        WalkSession.start(storageMatchingTitle(searching).replace("Cards in storage matching ", "Storage · "), walkPullList(matching, places))
                        onWalk()
                    }
                }
            }
            if (matching.isEmpty()) item(key = "matches-none") {
                Text("No cards in your places match.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            } else copyGroupItems(copiesByPlace(matching, places), places, showAllRows, { showAllRows = true }, onOpenCard, onOpenPlaceAt, "match:")
        }
        item(key = "places-head") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Your places", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, modifier = Modifier.weight(1f).a11yHeading())
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.accent.copy(alpha = 0.16f))
                        .clickable(role = Role.Button) { editing = true }
                        .heightIn(min = 36.dp)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = colors.accentLight, modifier = Modifier.size(18.dp))
                    Text("New place", style = MaterialTheme.typography.labelLarge, color = colors.accentLight, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
        if (places.isEmpty()) {
            item {
                EmptyPrompt(
                    Icons.Filled.Inventory2,
                    "No places yet. Say roughly what you keep your cards in — binders, boxes, a shelf — and the app makes the places, their labels, and keeps track of where each copy is.",
                    actions = listOf(
                        EmptyAction("Get started", Icons.Filled.Inventory2, onSetUp),
                        EmptyAction("New place", Icons.Filled.Add) { editing = true }
                    )
                )
            }
        }
        if (q.isNotEmpty()) {
            // Searching: every place whose name has it, flat, with where it sits.
            val found = placeTree(places).filter { it.place.name.contains(q, ignoreCase = true) }
            if (found.isEmpty() && places.isNotEmpty()) item { Text("No places match \"$q\".", style = MaterialTheme.typography.bodySmall, color = colors.textMuted) }
            items(found, key = { "found:" + it.place.id }) { node ->
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp)) {
                    PlaceRow(placeIcon(node.place.placeKind), node.place.name, detail(node.place, true), count(copiesWithin(summary, places, node.place.id)), gold = node.depth > 0, onClick = { onOpenPlace(node.place.id) })
                }
            }
            item(key = "everywhere") { SearchEverywhereRow(q, onSearchEverywhere) }
        } else items(childrenOf(places, null), key = { it.id }) { top ->
            val within = placeAndInside(places, top.id)
            val inside = placeTree(places).filter { it.place.id != top.id && it.place.id in within }
            val kids = childrenOf(places, top.id).size
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                PlaceRow(
                    icon = placeIcon(top.placeKind),
                    name = top.name,
                    detail = detail(top, kids == 0),
                    trailing = if (kids > 0) "$kids ${if (kids == 1) "place" else "places"}" else count(copiesWithin(summary, places, top.id)),
                    gold = false,
                    onClick = { onOpenPlace(top.id) }
                )
                inside.forEach { node ->
                    Box(
                        Modifier
                            .padding(start = (24 * node.depth).dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surface2)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        PlaceRow(
                            icon = placeIcon(node.place.placeKind),
                            name = node.place.name,
                            detail = detail(node.place, true),
                            trailing = count(copiesWithin(summary, places, node.place.id)),
                            gold = true,
                            onClick = { onOpenPlace(node.place.id) }
                        )
                    }
                }
            }
        }
        if (q.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp)) {
                    PlaceRow(Icons.Filled.Style, "Deck boxes", "Your physical decks, kept up to date", count(summary.inDecks), gold = false, onClick = onOpenDecks)
                }
            }
            item {
                val sealed = sealedOf(collections)
                val boxes = sealed.sumOf { it.count }
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp)) {
                    PlaceRow(
                        Icons.Filled.Inventory2, "Sealed",
                        if (sealed.isNotEmpty()) "${count(boxes)} sealed · ${money.format(sealedTotalUsd(sealed), whole = true)}" else "Booster boxes, bundles and precons",
                        count(boxes), gold = false, onClick = onOpenSealed
                    )
                }
            }
            item {
                val graded = gradedOf(collections)
                val worth = graded.sumOf { it.valueUsd ?: 0.0 }
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp)) {
                    PlaceRow(
                        Icons.Filled.Verified, "Graded",
                        if (graded.isNotEmpty()) "${count(graded.size)} ${if (graded.size == 1) "slab" else "slabs"}" + (if (worth > 0) " · ${money.format(worth, whole = true)}" else "")
                        else "Slabs from PSA, BGS, CGC — mark one from a card's page",
                        count(graded.size), gold = false, onClick = onOpenGraded
                    )
                }
            }
            item {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp)) {
                    PlaceRow(Icons.Filled.Handshake, "Lent out", "Your loans, and what friends lent you", count(summary.lent), gold = false, onClick = onOpenLoans)
                }
            }
            item {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp)) {
                    PlaceRow(Icons.Filled.Backpack, "Gear", gearSummary(gearOf(collections), decks), "›", gold = false, onClick = onOpenGear)
                }
            }
            // How much of the collection has a place.
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(14.dp)
                ) {
                    Text(
                        "${count(summary.placed)} of ${count(summary.total)} ${if (summary.total == 1) "copy has" else "copies have"} a place",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary
                    )
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(colors.surface2)) {
                        Box(Modifier.fillMaxWidth(share).height(8.dp).clip(RoundedCornerShape(4.dp)).background(colors.accent))
                    }
                    if (summary.unplaced > 0) {
                        Text(
                            "${count(summary.unplaced)} without a place · Put them away",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.accentLight,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(role = Role.Button) { putAway() }
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }

    if (editing) {
        PlaceDialog(
            place = null, parentId = null, places = places, onDismiss = { editing = false },
            order = placeOrderOf(collections), ruleCards = ruleCards, collections = collections, saved = saved, savedFilters = savedFilters,
            userTagOptions = userTagOptions, onRuleOrder = { ruleOrder = true }
        ) { place ->
            onChange { savePlace(it, place) }
            editing = false
        }
    }
    if (ruleOrder) RuleOrderDialog(collections, onChange) { ruleOrder = false }
    if (choosing) {
        PlacePickerDialog("Put cards away into…", places, onDismiss = { choosing = false }) { id ->
            choosing = false
            onPutAway(id)
        }
    }
}

/** One place in a list: its icon, name, a line about it and a count. */
@Composable
fun PlaceRow(icon: ImageVector, name: String, detail: String?, trailing: String, gold: Boolean, onClick: (() -> Unit)?) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Icon(icon, contentDescription = null, tint = if (gold) colors.accent else colors.textMuted, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!detail.isNullOrEmpty()) Text(detail, style = MaterialTheme.typography.labelMedium, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(trailing, style = MaterialTheme.typography.labelLarge, color = colors.textMuted, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Pick a place: every place, in tree order. */
@Composable
fun PlacePickerDialog(title: String, places: List<StoragePlace>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val colors = LocalAppColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(title, color = colors.accentLight, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (places.isEmpty()) Text("No places yet — make one on the Collection's Storage tab.", color = colors.textMuted)
                placeTree(places).forEach { node ->
                    Box(Modifier.padding(start = (20 * node.depth).dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).padding(vertical = 4.dp)) {
                        PlaceRow(placeIcon(node.place.placeKind), node.place.name, placeSubtitle(node.place), "", gold = true, onClick = { onPick(node.place.id) })
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textMuted) } }
    )
}

/** A dropdown picking one of [options] (value to label), shown as a field. */
@Composable
internal fun PickField(label: String, value: String, options: List<Pair<String, String>>, onPick: (String) -> Unit) {
    val colors = LocalAppColors.current
    var open by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surface2)
                    .clickable { open = true }
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Text(options.firstOrNull { it.first == value }?.second ?: "", color = colors.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = colors.textMuted)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.surface) {
                options.forEach { (v, text) ->
                    DropdownMenuItem(text = { Text(text, color = colors.textPrimary) }, onClick = { onPick(v); open = false })
                }
            }
        }
    }
}

/**
 * Make a place ([place] null), or change one: its name, what it is, where it sits and how it's organised
 * — and its rules (StorageRules.kt): what it holds, its sections and its order. [order]: the rule order;
 * [ruleCards]: the collection's cards as the rules judge them, for the overlap warnings; [onRuleOrder]
 * opens Rule order.
 */
@Composable
fun PlaceDialog(
    place: StoragePlace?,
    parentId: String?,
    places: List<StoragePlace>,
    onDismiss: () -> Unit,
    order: List<String> = emptyList(),
    ruleCards: List<RuleCard> = emptyList(),
    collections: List<Collection> = emptyList(),
    saved: List<SavedFilter> = emptyList(),
    savedFilters: SavedFilters? = null,
    userTagOptions: List<String> = emptyList(),
    onRuleOrder: (() -> Unit)? = null,
    onSave: (StoragePlace) -> Unit
) {
    val colors = LocalAppColors.current
    var name by remember { mutableStateOf(place?.name ?: "") }
    var kind by remember { mutableStateOf(place?.placeKind ?: PlaceKind.BOX) }
    var parent by remember { mutableStateOf(place?.parentId ?: parentId ?: "") }
    var note by remember { mutableStateOf(place?.note ?: "") }
    var pockets by remember { mutableStateOf((place?.pocketsPerPage ?: DEFAULT_POCKETS).toString()) }
    // A binder's order (BinderPages.kt).
    var rule by remember { mutableStateOf(place?.sortRule ?: "") }
    var sections by remember { mutableStateOf(place?.sections.orEmpty().joinToString(", ")) }
    // The rules (StorageRules.kt), as the place goes by them now — an older app's sorting rule read as what it meant.
    val startRules = remember { place?.let { placeRules(it) } ?: PlaceRules() }
    var holds by remember { mutableStateOf(startRules.holds) }
    var level by remember { mutableStateOf(startRules.sectionsBy) }
    var sortKey by remember { mutableStateOf(startRules.sortBy.joinToString(",")) }
    var choosingHolds by remember { mutableStateOf(false) }
    val parents = placeTree(places).filter { place == null || canMoveInto(places, place.id, it.place.id) }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.accent,
        unfocusedBorderColor = colors.border,
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        cursorColor = colors.accent
    )
    val isBinder = kind == PlaceKind.BINDER
    val sortBy = sortKey.split(",").filter { it.isNotEmpty() }
    fun built(): StoragePlace {
        val list = sections.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val pocketCount = pockets.toIntOrNull()?.takeIf { it > 0 && it != DEFAULT_POCKETS }
        val base = StoragePlace(
            id = place?.id ?: "new",
            name = name.trim(),
            kind = kind.name,
            parentId = parent.ifEmpty { null },
            note = note.trim().ifEmpty { null },
            sections = if (!isBinder && list.isNotEmpty()) list else null,
            pocketsPerPage = if (isBinder) pocketCount else null,
            // A binder's order (BinderPages.kt); anything else's is written from its rules (withRules).
            sortRule = if (isBinder) rule.ifEmpty { null } else place?.sortRule,
            createdAt = place?.createdAt ?: System.currentTimeMillis(),
            // When it was last checked (PlaceCheck.kt) isn't changed here, nor its size (Change size, BoxSpace.kt).
            lastChecked = place?.lastChecked,
            capacity = place?.capacity,
            pages = place?.pages,
            holds = place?.holds,
            sectionsBy = place?.sectionsBy,
            sortBy = place?.sortBy
        )
        return withRules(base, holds?.let { (b, a) -> holdsMap(b, a) }, if (isBinder) null else level, if (isBinder) emptyList() else sortBy)
    }
    val save = {
        if (name.isNotBlank()) {
            if (place == null) Usage.action(UsageAction.PLACE_CREATED)
            val made = built()
            onSave(if (place == null) made.copy(id = UUID.randomUUID().toString()) else made)
        }
    }
    val placeName = { id: String -> places.firstOrNull { it.id == id }?.name }
    val binderName = { id: String -> collections.firstOrNull { it.id == id }?.name }
    val overlapLines = remember(holds, level, sortKey, kind, ruleCards, order) {
        if (holds == null || ruleCards.isEmpty()) emptyList() else overlaps(built(), places, order, ruleCards).map { it.line }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(if (place == null) "New place" else "Change place", color = colors.accentLight, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name", color = colors.textMuted) },
                    placeholder = { Text("Red box", color = colors.textDim) },
                    singleLine = true,
                    colors = fieldColors
                )
                Text("What it is", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlaceKind.entries.forEach { k ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (k == kind) colors.textPrimary else colors.surface2)
                                .clickable { kind = k }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(placeIcon(k), contentDescription = null, tint = if (k == kind) colors.bg else colors.textMuted, modifier = Modifier.size(16.dp))
                            Text(k.label, style = MaterialTheme.typography.labelLarge, color = if (k == kind) colors.bg else colors.textMuted, modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
                PickField(
                    "Inside",
                    parent,
                    listOf("" to "Nothing — it stands on its own") + parents.map { it.place.id to ("  ".repeat(it.depth) + it.place.name) }
                ) { parent = it }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note", color = colors.textMuted) },
                    placeholder = { Text("Bulk, trade fodder…", color = colors.textDim) },
                    singleLine = true,
                    colors = fieldColors
                )
                if (kind == PlaceKind.BINDER) {
                    OutlinedTextField(
                        value = pockets,
                        onValueChange = { pockets = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("Pockets per page", color = colors.textMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = fieldColors
                    )
                    PickField("In order", rule, listOf("" to "No order — new cards go in the next free pocket") + SortRule.entries.map { it.name to it.label }) { rule = it }
                    Text(
                        "With an order, Add cards in order says where new cards go and what to shift.",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textMuted
                    )
                }
                RulesEditor(
                    isBinder = isBinder,
                    holds = holds,
                    onHolds = { holds = it },
                    level = level,
                    onLevel = { l -> level = l; sections = ruleSections(l).joinToString(", ") },
                    sortKey = sortKey,
                    onSortKey = { sortKey = it },
                    saved = saved,
                    overlaps = overlapLines,
                    onRuleOrder = onRuleOrder,
                    holdsLine = holds?.let { (b, a) -> "Holds " + holdsSummary(b, a, binderName, placeName) } ?: "Everything",
                    onChooseHolds = { choosingHolds = true }
                )
                if (!isBinder && (level != null || kind == PlaceKind.BOX || sections.isNotBlank())) {
                    OutlinedTextField(
                        value = sections,
                        onValueChange = { sections = it },
                        label = { Text("Sections", color = colors.textMuted) },
                        placeholder = { Text("White, Blue, Black…", color = colors.textDim) },
                        colors = fieldColors
                    )
                    Text(
                        "In order, with commas between. Cards put away go in the section the rule gives them; rename them as you like.",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textMuted
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = save, enabled = name.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)) {
                Text(if (place == null) "Make place" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textMuted) } }
    )
    if (choosingHolds) {
        HoldsDialog(holds, ruleCards, collections, saved, savedFilters, userTagOptions, onDismiss = { choosingHolds = false }) {
            holds = it
            choosingHolds = false
        }
    }
}
