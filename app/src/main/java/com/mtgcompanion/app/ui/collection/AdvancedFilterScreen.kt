package com.mtgcompanion.app.ui.collection

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mtgcompanion.app.data.CARD_CONDITIONS
import com.mtgcompanion.app.data.CARD_LANGUAGES
import com.mtgcompanion.app.data.languageName
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.common.ManaSymbol
import com.mtgcompanion.app.ui.common.openUrl
import com.mtgcompanion.app.ui.common.rememberMoney
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldDim
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.OnGold
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.Surface2
import com.mtgcompanion.app.ui.theme.TextDim
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary
import kotlinx.coroutines.delay

// All cards → Filters → Advanced filters: a screen of its own over the collection, with the same
// search as Scryfall syntax (read-only, to copy or open on Scryfall), the Scryfall-style fields, the
// Your copies fields, "Save as…" and a live "Show N cards". The filters here are a draft until Show
// is pressed; Back leaves them as they were. Mirrors the web app's
// src/collection/AdvancedFilterPage.tsx; the rules are in AdvancedFilter.kt.

private val COLOR_NAMES = mapOf("W" to "White", "U" to "Blue", "B" to "Black", "R" to "Red", "G" to "Green", "C" to "Colourless")

private fun <T> List<T>.toggle(item: T): List<T> = if (item in this) this - item else this + item

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdvancedFilterScreen(
    basic: CollectionFilter,
    advanced: AdvancedFilter,
    /** How many cards the list would show with these filters. */
    countFor: (CollectionFilter, AdvancedFilter) -> Int,
    /** The owned binders, id to name, for "Binder". */
    binders: List<Pair<String, String>>,
    /** The sets of the cards owned, code to name, for "Find a set". */
    sets: List<Pair<String, String>>,
    saved: List<SavedFilter>,
    onSave: (String, CollectionFilter, AdvancedFilter) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onApply: (CollectionFilter, AdvancedFilter) -> Unit,
    onDismiss: () -> Unit
) {
    var b by remember { mutableStateOf(basic) }
    var a by remember { mutableStateOf(advanced) }
    val money = rememberMoney()
    val query = scryfallQuery(b, a) { money.toUsd(it) }
    val count = remember(b, a) { countFor(b, a) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }
    // Saving (id null) or renaming a saved filter: the name being typed.
    var naming by remember { mutableStateOf<Pair<String?, String>?>(null) }
    var setQuery by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            containerColor = Bg,
            topBar = {
                TopAppBar(
                    title = { Text("Advanced filters", style = MaterialTheme.typography.titleLarge, maxLines = 1) },
                    navigationIcon = { BackButton(onClick = onDismiss) },
                    actions = {
                        TextButton(onClick = { b = CollectionFilter(); a = AdvancedFilter() }) { Text("Clear all", color = TextMuted) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
                )
            },
            bottomBar = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().background(Surface).padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    OutlinedButton(onClick = { naming = null to "" }, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, BorderColor)) {
                        Text("Save as…", color = TextPrimary)
                    }
                    Button(
                        onClick = { onApply(b, a) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = OnGold),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Show $count ${if (count == 1) "card" else "cards"}", fontWeight = FontWeight.Bold)
                    }
                }
            }
        ) { padding ->
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize().background(Bg).padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)
            ) {
                if (saved.isNotEmpty()) {
                    Section("Saved filters") {
                        saved.forEach { s ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    s.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Surface2)
                                        .clickable { b = s.basic; a = s.advanced }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                )
                                IconButton(onClick = { naming = s.id to s.name }) { Icon(Icons.Filled.Edit, contentDescription = "Rename ${s.name}", tint = TextMuted) }
                                IconButton(onClick = { onDelete(s.id) }) { Icon(Icons.Filled.Delete, contentDescription = "Delete ${s.name}", tint = TextMuted) }
                            }
                        }
                    }
                }

                // The same search on Scryfall, read-only.
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface).padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text("SAME SEARCH ON SCRYFALL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextMuted)
                    Text(
                        query.ifEmpty { "No Scryfall filters yet" },
                        color = if (query.isEmpty()) TextDim else GoldLight,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Surface2).padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                    if (query.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallAction(if (copied) "Copied" else "Copy", Icons.Filled.ContentCopy) {
                                clipboard.setText(AnnotatedString(query))
                                copied = true
                            }
                            SmallAction("Open on Scryfall", Icons.Filled.OpenInNew) {
                                openUrl(context, "https://scryfall.com/search?q=" + Uri.encode(query))
                            }
                        }
                    }
                    Text("Updates as you choose filters below. Your copies filters only apply here, not on Scryfall.", style = MaterialTheme.typography.bodySmall, color = TextDim)
                }

                Section("Colours") {
                    Seg(listOf("color" to "Card colour", "identity" to "Commander identity"), a.colorTarget) { a = a.copy(colorTarget = it) }
                    Seg(COLOR_MODES.map { it to (COLOR_MODE_LABELS[it] ?: it) }, a.colorMode) { a = a.copy(colorMode = it) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ADVANCED_COLORS.forEach { c ->
                            val on = c in a.colors
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .border(BorderStroke(2.dp, if (on) Gold else androidx.compose.ui.graphics.Color.Transparent), CircleShape)
                                    .clickable {
                                        a = a.copy(colors = if (c == "C") (if (on) emptyList() else listOf("C")) else (a.colors - "C").toggle(c))
                                    }
                                    .alpha(if (on) 1f else 0.45f)
                            ) {
                                ManaSymbol(c, size = 28.dp, modifier = Modifier)
                            }
                        }
                        ToggleChip("Multicolour", a.multicolor) { a = a.copy(multicolor = !a.multicolor) }
                    }
                    // Screen readers: which colours are picked.
                    if (a.colors.isNotEmpty()) {
                        Text(a.colors.joinToString(", ") { COLOR_NAMES[it] ?: it }, style = MaterialTheme.typography.labelSmall, color = TextDim)
                    }
                }

                Section("Mana") {
                    NumberRow("Mana value", a.mvOp, a.mv, { a = a.copy(mvOp = it) }) { a = a.copy(mv = it) }
                    Field("Mana cost", a.manaCost, "e.g. {2}{U}{U}") { a = a.copy(manaCost = it) }
                }

                Section("Stats") {
                    NumberRow("Power", a.powerOp, a.power, { a = a.copy(powerOp = it) }) { a = a.copy(power = it) }
                    NumberRow("Toughness", a.toughnessOp, a.toughness, { a = a.copy(toughnessOp = it) }) { a = a.copy(toughness = it) }
                    NumberRow("Loyalty", a.loyaltyOp, a.loyalty, { a = a.copy(loyaltyOp = it) }) { a = a.copy(loyalty = it) }
                }

                Section("Format") {
                    Picker(listOf("" to "Any format") + FILTER_FORMATS, a.format, "Format") { a = a.copy(format = it) }
                    Seg(LEGALITIES.map { it to (LEGALITY_LABELS[it] ?: it) }, a.legality) { a = a.copy(legality = it) }
                }

                Section("Sets") {
                    Field("Find a set", setQuery, "e.g. Modern Horizons 3") { setQuery = it }
                    val q = setQuery.trim().lowercase()
                    val hits = if (q.isEmpty()) emptyList() else sets.filter { (code, name) -> code !in a.sets && (name.lowercase().contains(q) || code == q) }.take(8)
                    if (hits.isNotEmpty()) {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Surface2)) {
                            hits.forEach { (code, name) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { a = a.copy(sets = a.sets + code); setQuery = "" }.padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Text(name, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text(code.uppercase(), color = TextDim, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    } else if (q.isNotEmpty()) {
                        Text("No set of yours matches.", style = MaterialTheme.typography.bodySmall, color = TextDim)
                    }
                    if (a.sets.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            a.sets.forEach { code ->
                                val name = sets.firstOrNull { it.first == code }?.second ?: code.uppercase()
                                FilterChip(
                                    selected = true,
                                    onClick = { a = a.copy(sets = a.sets - code) },
                                    label = { Text(name) },
                                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove $name", modifier = Modifier.size(16.dp)) },
                                    colors = chipColors()
                                )
                            }
                        }
                    }
                }

                Section("Card is") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CARD_IS.forEach { isWhat -> ToggleChip(CARD_IS_LABELS[isWhat] ?: isWhat, isWhat in a.cardIs) { a = a.copy(cardIs = a.cardIs.toggle(isWhat)) } }
                    }
                    Field("Keywords", a.keywords, "e.g. flying, deathtouch") { a = a.copy(keywords = it) }
                }

                Section("Price") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { Field("Lowest price", a.priceMin, "${money.currency.symbol.trim()} min", number = true) { a = a.copy(priceMin = it) } }
                        Text("to", color = TextDim)
                        Box(Modifier.weight(1f)) { Field("Highest price", a.priceMax, "${money.currency.symbol.trim()} max", number = true) { a = a.copy(priceMax = it) } }
                    }
                    Text("Per copy, in your currency.", style = MaterialTheme.typography.bodySmall, color = TextDim)
                }

                Section("Art and words") {
                    Field("Artist", a.artist, "e.g. Rebecca Guay") { a = a.copy(artist = it) }
                    Field("Flavour text", a.flavor, "any words") { a = a.copy(flavor = it) }
                }

                Section("Your copies", badge = "Not on Scryfall") {
                    Label("Finish")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FINISHES.forEach { f -> ToggleChip(FINISH_LABELS[f] ?: f, f in a.finishes) { a = a.copy(finishes = a.finishes.toggle(f)) } }
                    }
                    Label("Condition")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CARD_CONDITIONS.forEach { c -> ToggleChip(CONDITION_LABELS[c] ?: c, c in a.conditions) { a = a.copy(conditions = a.conditions.toggle(c)) } }
                    }
                    Label("Language")
                    Picker(listOf("" to "Any") + CARD_LANGUAGES.map { it to languageName(it) }, a.language, "Language") { a = a.copy(language = it) }
                    Label("Binder")
                    Picker(listOf("" to "Any binder") + binders, a.binder, "Binder") { a = a.copy(binder = it) }
                    Label("In a deck")
                    Seg(IN_DECK_OPTIONS.map { it to (IN_DECK_LABELS[it] ?: it) }, a.inDeck) { a = a.copy(inDeck = it) }
                    NumberRow("Copies", a.copiesOp, a.copies, { a = a.copy(copiesOp = it) }) { a = a.copy(copies = it) }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        naming?.let { (id, name) ->
            AlertDialog(
                onDismissRequest = { naming = null },
                containerColor = Surface,
                title = { Text(if (id == null) "Save filters as" else "Rename filter", color = GoldLight) },
                text = {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { naming = id to it },
                        placeholder = { Text("e.g. Cheap blue creatures", color = TextDim) },
                        singleLine = true,
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = name.isNotBlank(),
                        onClick = {
                            if (id == null) onSave(name, b, a) else onRename(id, name)
                            naming = null
                        }
                    ) { Text(if (id == null) "Save" else "Rename", color = if (name.isNotBlank()) Gold else TextDim) }
                },
                dismissButton = { TextButton(onClick = { naming = null }) { Text("Cancel", color = TextMuted) } }
            )
        }
    }
}

/** The filters on, a chip each — tap one to take it off — and Clear all. Shown under the search. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActiveFilterChips(chips: List<ActiveChip>, onRemove: (String) -> Unit, onClear: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        chips.forEach { chip ->
            FilterChip(
                selected = true,
                onClick = { onRemove(chip.key) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(chip.label)
                        chip.symbols.forEach { ManaSymbol(it, size = 15.dp) }
                    }
                },
                trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove: ${chip.text}", modifier = Modifier.size(16.dp)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Surface2,
                    selectedLabelColor = TextPrimary,
                    selectedTrailingIconColor = TextMuted
                )
            )
        }
        TextButton(onClick = onClear) { Text("Clear all", color = Gold) }
    }
}

@Composable
private fun Section(title: String, badge: String? = null, content: @Composable () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = TextPrimary, modifier = Modifier.weight(1f))
            if (badge != null) {
                Text(
                    badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Surface2).padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        content()
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = GoldDim)
}

@Composable
private fun SmallAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(Surface2).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
    }
}

/** A row of equal buttons, one picked: Card colour / Commander identity, Legal / Banned / Restricted. */
@Composable
private fun Seg(options: List<Pair<String, String>>, value: String, onPick: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEach { (key, label) ->
            val on = key == value
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) Gold else Surface2)
                    .clickable { onPick(key) }
                    .padding(horizontal = 4.dp)
            ) {
                Text(
                    label,
                    color = if (on) OnGold else TextMuted,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = Gold,
    selectedLabelColor = OnGold,
    selectedTrailingIconColor = OnGold,
    labelColor = TextMuted,
    containerColor = Surface2
)

@Composable
private fun ToggleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) }, colors = chipColors())
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Gold,
    unfocusedBorderColor = BorderColor,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = Gold,
    focusedContainerColor = Bg,
    unfocusedContainerColor = Bg
)

@Composable
private fun Field(label: String, value: String, placeholder: String, number: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, color = TextMuted) },
        placeholder = { Text(placeholder, color = TextDim, style = MaterialTheme.typography.bodySmall) },
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth()
    )
}

/** A label, how to compare (= < ≤ > ≥ ≠) and the number: Mana value ≤ 3. */
@Composable
private fun NumberRow(label: String, op: String, value: String, onOp: (String) -> Unit, onValue: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextMuted, modifier = Modifier.width(96.dp))
        Box {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(width = 56.dp, height = 48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Surface2)
                    .clickable { open = true }
            ) {
                Text(OP_SYMBOLS[op] ?: op, color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(Surface)) {
                COMPARE_OPS.forEach { o ->
                    DropdownMenuItem(
                        text = { Text(OP_SYMBOLS[o] ?: o, color = if (o == op) Gold else TextPrimary, fontWeight = FontWeight.Bold) },
                        onClick = { onOp(o); open = false }
                    )
                }
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            placeholder = { Text("any", color = TextDim) },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = fieldColors(),
            modifier = Modifier.width(96.dp)
        )
    }
}

/** A drop-down of [options] (key to label), showing the one picked. */
@Composable
private fun Picker(options: List<Pair<String, String>>, value: String, what: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Bg)
                .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(10.dp))
                .clickable { open = true }
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            Text(options.firstOrNull { it.first == value }?.second ?: value, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose $what", tint = Gold)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(Surface)) {
            options.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { Text(label, color = if (key == value) Gold else TextPrimary) },
                    onClick = { onPick(key); open = false }
                )
            }
        }
    }
}
