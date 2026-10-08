@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.mtgcompanion.app.ui.collection

import android.print.PrintAttributes
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtgcompanion.app.data.APART_KINDS
import com.mtgcompanion.app.data.APART_LABELS
import com.mtgcompanion.app.data.BY_RULE
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CopyHistoryStore
import com.mtgcompanion.app.data.LEVEL_BYS
import com.mtgcompanion.app.data.LEVEL_LABELS
import com.mtgcompanion.app.data.MAX_LEVELS
import com.mtgcompanion.app.data.MoveCard
import com.mtgcompanion.app.data.MoveSpot
import com.mtgcompanion.app.data.PileChecking
import com.mtgcompanion.app.data.PlaceKind
import com.mtgcompanion.app.data.Prices
import com.mtgcompanion.app.data.RecipePile
import com.mtgcompanion.app.data.RecipeSessionState
import com.mtgcompanion.app.data.RecipeSessionStore
import com.mtgcompanion.app.data.SMART_KINDS
import com.mtgcompanion.app.data.SMART_LABELS
import com.mtgcompanion.app.data.SortRecipe
import com.mtgcompanion.app.data.SortRule
import com.mtgcompanion.app.data.SplitLevel
import com.mtgcompanion.app.data.addedMove
import com.mtgcompanion.app.data.capWarning
import com.mtgcompanion.app.data.cardsIn
import com.mtgcompanion.app.data.deleteRecipe
import com.mtgcompanion.app.data.derivePiles
import com.mtgcompanion.app.data.fileRecipe
import com.mtgcompanion.app.data.binderFiledInto
import com.mtgcompanion.app.data.isTemplate
import com.mtgcompanion.app.data.levelLine
import com.mtgcompanion.app.data.newRecipe
import com.mtgcompanion.app.data.pileGoesTo
import com.mtgcompanion.app.data.pileSignsHtml
import com.mtgcompanion.app.data.placeTree
import com.mtgcompanion.app.data.placesOf
import com.mtgcompanion.app.data.putAwayMove
import com.mtgcompanion.app.data.recipeLine
import com.mtgcompanion.app.data.recipeTemplates
import com.mtgcompanion.app.data.goalsOf
import com.mtgcompanion.app.data.recipesOf
import com.mtgcompanion.app.data.saveRecipe
import com.mtgcompanion.app.data.social.TradeCard
import com.mtgcompanion.app.data.sortRecipe
import com.mtgcompanion.app.data.splitLevel
import com.mtgcompanion.app.data.startRecipeSession
import com.mtgcompanion.app.data.summarize
import com.mtgcompanion.app.data.withGoTo
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.common.a11yHeading
import com.mtgcompanion.app.ui.scan.bandColor
import com.mtgcompanion.app.ui.theme.BebasNumbers
import com.mtgcompanion.app.ui.theme.LocalAppColors
import java.util.UUID

/*
 * Sorting a pile with a recipe — the scansort mockups: pick a recipe (yours, or start from one), build
 * your own, lay out the piles (print their signs, how you'll hear them), and, once the scanner's Done,
 * what went where. The logic is data/SortRecipes.kt; the scanning is ScanViewModel's recipe mode. The
 * web app's SortRecipesPage.tsx shows the same.
 */

private sealed interface RecipeStep {
    data object Pick : RecipeStep
    data class Edit(val start: SortRecipe?) : RecipeStep
    data class Layout(val recipe: SortRecipe) : RecipeStep
    data object Summary : RecipeStep
}

@Composable
fun SortRecipesScreen(
    collections: List<Collection>,
    startOnSummary: Boolean,
    onBack: () -> Unit,
    onChange: ((List<Collection>) -> List<Collection>) -> Unit,
    /** Start (or carry on) scanning with the sort kept in RecipeSessionStore. */
    onScan: () -> Unit,
    /** "Keep, spares, decks, bulk": the first sorter, as it was. */
    onClassic: () -> Unit,
    onOffer: (friendId: String, cards: List<TradeCard>) -> Unit,
    onFit: (placeId: String) -> Unit
) {
    val context = LocalContext.current
    val store = remember { RecipeSessionStore(context) }
    var step by remember { mutableStateOf<RecipeStep>(if (startOnSummary) RecipeStep.Summary else RecipeStep.Pick) }
    // Back from the scanner: the sort kept on the phone is read again.
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumes by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumes++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    BackHandler(enabled = step !is RecipeStep.Pick && !(startOnSummary && step is RecipeStep.Summary)) { step = RecipeStep.Pick }
    // The sets the binders kept in order by set collect, for Binder by set.
    var setData by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val setIds = remember(collections) {
        placesOf(collections).filter { it.placeKind == PlaceKind.BINDER && it.sortRule == SortRule.SET.name }
            .flatMap { p -> cardsIn(collections, p.id).map { it.entry.scryfallId } }
    }
    LaunchedEffect(setIds) {
        val ids = setIds.distinct()
        if (ids.isNotEmpty()) setData = runCatching { CardRepository().getCardsByIds(ids).associate { it.id to (it.set ?: "") } }.getOrDefault(emptyMap())
    }
    val sets = setIds.mapNotNull { setData[it]?.takeIf { s -> s.isNotEmpty() } }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
    // With a goal under way, "What my collection needs" pulls out what the goals need too.
    val templates = recipeTemplates(sets, goals = goalsOf(collections).any { it.completedAt == null })
    when (val s = step) {
        RecipeStep.Pick -> RecipePicker(collections, templates, store, resumes, onBack, onClassic, onScan,
            onSummary = { step = RecipeStep.Summary }, onEdit = { step = RecipeStep.Edit(null) }, onLayout = { step = RecipeStep.Layout(it) })
        is RecipeStep.Edit -> RecipeEditor(s.start, onBack = { step = RecipeStep.Pick }, onChange = onChange) { saved -> step = RecipeStep.Layout(saved) }
        is RecipeStep.Layout -> RecipeLayout(s.recipe, collections, store, resumes, onBack = { step = RecipeStep.Pick }, onChange = onChange,
            onEdit = { step = RecipeStep.Edit(s.recipe) }, onSummary = { step = RecipeStep.Summary }, onScan = onScan)
        RecipeStep.Summary -> RecipeSummaryView(collections, store, resumes, onBack = { if (startOnSummary) onBack() else step = RecipeStep.Pick },
            onChange = onChange, onScan = onScan, onOffer = onOffer, onFit = onFit, onDone = { step = RecipeStep.Pick })
    }
}

@Composable
private fun Frame(title: String, subtitle: String?, onBack: () -> Unit, action: (@Composable () -> Unit)? = null, bottom: @Composable RowScope.() -> Unit, content: @Composable () -> Unit) {
    val colors = LocalAppColors.current
    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.a11yHeading())
                        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = { action?.invoke() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        },
        bottomBar = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().background(colors.bg).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp)
            ) { bottom() }
        }
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp)
        ) { content() }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp, color = LocalAppColors.current.textMuted, modifier = Modifier.padding(top = 10.dp).a11yHeading())
}

@Composable
private fun Big(label: String, primary: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) colors.accent else colors.surface2,
            contentColor = if (primary) colors.onAccent else colors.textPrimary
        ),
        modifier = modifier.height(48.dp)
    ) { Text(label, fontWeight = if (primary) FontWeight.ExtraBold else FontWeight.Bold) }
}

@Composable
private fun RecipeRow(title: String, sub: String, mine: Boolean = false, tag: String? = null, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (mine) Color(0xFF2A2210) else colors.surface)
            .then(if (mine) Modifier.border(1.dp, colors.accent.copy(alpha = 0.5f), RoundedCornerShape(16.dp)) else Modifier)
            .clickable(role = Role.Button, onClick = onClick).padding(14.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            Text(sub, fontSize = 13.sp, color = if (mine) Color(0xFFD9C79F) else colors.textMuted)
        }
        if (tag != null) Text(tag, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.accent)
    }
}

private fun money() = Prices.money.value
private fun fmtLocal(n: Double): String = money().formatLocal(n, whole = n == Math.floor(n))

/** The templates' lines, as the mockup words them. */
private fun templateLine(r: SortRecipe): String = when (r.id) {
    "tpl-colour" -> "W · U · B · R · G · Multi · Colourless · Lands"
    "tpl-set" -> "One pile per set, then collector number"
    "tpl-value" -> levelLine(r.levels[0], ::fmtLocal)
    "tpl-needs" -> "Decks need it · friends want it · binder gaps · trade · bulk"
    else -> recipeLine(r, ::fmtLocal)
}

@Composable
private fun RecipePicker(
    collections: List<Collection>, templates: List<SortRecipe>, store: RecipeSessionStore, resumes: Int, onBack: () -> Unit, onClassic: () -> Unit, onScan: () -> Unit,
    onSummary: () -> Unit, onEdit: () -> Unit, onLayout: (SortRecipe) -> Unit
) {
    val colors = LocalAppColors.current
    val last = store.lastRecipeId()
    val mine = recipesOf(collections).sortedBy { if (it.id == last) 0 else 1 }
    val session = remember(resumes) { store.session() }
    Frame("Sort a pile", "How should this pile split?", onBack, bottom = { Big("Make your own recipe", false, Modifier.weight(1f), onClick = onEdit) }) {
        if (session != null && session.scans.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.accent.copy(alpha = 0.16f)).padding(12.dp)) {
                Text("${session.scans.size} ${if (session.scans.size == 1) "card" else "cards"} sorted with ${session.recipe.name}, not filed yet", color = colors.textPrimary, fontSize = 14.sp)
                Row {
                    TextButton(onClick = onSummary) { Text("See them", color = colors.accent, fontWeight = FontWeight.Bold) }
                    TextButton(onClick = onScan) { Text("Keep going", color = colors.accent, fontWeight = FontWeight.Bold) }
                }
            }
        }
        if (mine.isNotEmpty()) {
            Heading("Your recipes")
            mine.forEach { r -> RecipeRow(r.name, recipeLine(r, ::fmtLocal), mine = true, tag = if (r.id == last) "Last used" else null) { onLayout(r) } }
        }
        Heading("Start from")
        templates.forEach { r -> RecipeRow(r.name, templateLine(r)) { onLayout(r) } }
        // The first sorter, as it was: up to six piles by rules.
        RecipeRow("Keep, spares, decks, bulk", "Rares worth keeping · spares · wanted by a deck · bulk by your boxes' rules", onClick = onClassic)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text("+", color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(end = 10.dp))
            Text("Smart piles go first in every recipe: deck needs, friends' wants, binder gaps.", fontSize = 13.sp, color = colors.textMuted)
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { onChange(!checked) }.heightIn(min = 44.dp)
    ) {
        Checkbox(checked, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = colors.accent, checkmarkColor = colors.onAccent))
        Text(label, fontSize = 14.sp, color = colors.textPrimary, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun Pill(label: String, on: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Text(
        label, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
        color = if (on) colors.onAccent else colors.textPrimary,
        modifier = Modifier.clip(RoundedCornerShape(17.dp)).background(if (on) colors.accent else colors.surface2)
            .semantics { contentDescription = "$label: ${if (on) "on" else "off"}" }
            .clickable(role = Role.Switch, onClick = onClick).heightIn(min = 34.dp).padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun RecipeEditor(start: SortRecipe?, onBack: () -> Unit, onChange: ((List<Collection>) -> List<Collection>) -> Unit, onSaved: (SortRecipe) -> Unit) {
    val colors = LocalAppColors.current
    val saved = start != null && !start.isTemplate
    var draft by remember {
        mutableStateOf(
            start?.let { sortRecipe(it).let { r -> if (r.isTemplate) r.copy(id = UUID.randomUUID().toString(), createdAt = System.currentTimeMillis()) else r } }
                ?: newRecipe(UUID.randomUUID().toString(), System.currentTimeMillis())
        )
    }
    var open by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val derived = derivePiles(draft, ::fmtLocal)
    Frame(
        if (saved) "Change recipe" else "New recipe", null, onBack,
        action = { if (saved) TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = colors.error) } },
        bottom = {
            Text("${derived.piles.size} ${if (derived.piles.size == 1) "pile" else "piles"}", fontSize = 13.sp, color = colors.textMuted, modifier = Modifier.weight(1f))
            Big("Save and lay out", true) {
                val r = sortRecipe(draft)
                onChange { saveRecipe(it, r) }
                onSaved(r)
            }
        }
    ) {
        Heading("Name")
        OutlinedTextField(draft.name, { draft = draft.copy(name = it) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Heading("First, pull out")
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 8.dp, vertical = 4.dp)) {
            SMART_KINDS.forEach { k ->
                CheckRow(SMART_LABELS.getValue(k), k in draft.pullOut) { on -> draft = draft.copy(pullOut = SMART_KINDS.filter { x -> if (x == k) on else x in draft.pullOut }) }
            }
        }
        Heading("Then split the rest by")
        draft.levels.forEachIndexed { i, l ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(if (i == 0) colors.accent else colors.surface2), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (i == 0) colors.onAccent else colors.textPrimary)
                    }
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(LEVEL_LABELS.getValue(l.by), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        Text(levelLine(l, ::fmtLocal), fontSize = 12.sp, color = colors.textMuted)
                    }
                    TextButton(onClick = { open = if (open == i) null else i }) { Text(if (open == i) "Done" else "Change", color = colors.accent, fontWeight = FontWeight.Bold) }
                }
                if (open == i) LevelEditor(l, money().currency.code, onChange = { next -> draft = draft.copy(levels = draft.levels.mapIndexed { j, x -> if (j == i) next else x }) }) {
                    draft = draft.copy(levels = draft.levels.filterIndexed { j, _ -> j != i })
                    open = null
                }
            }
        }
        if (draft.levels.size < MAX_LEVELS) {
            Text(
                "+ Add a level (set, mana value, rarity, type, A–Z…)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textMuted,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, colors.surface3, RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button) {
                        draft = draft.copy(levels = draft.levels + splitLevel(SplitLevel(if (draft.levels.any { it.by == "COLOUR" }) "RARITY" else "COLOUR")))
                        open = draft.levels.size - 1
                    }.heightIn(min = 44.dp).padding(14.dp)
            )
        }
        Heading("Also keep apart")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            APART_KINDS.forEach { k ->
                Pill(APART_LABELS.getValue(k), k in draft.apart) { draft = draft.copy(apart = APART_KINDS.filter { x -> if (x == k) x !in draft.apart else x in draft.apart }) }
            }
        }
        capWarning(derived)?.let { Warning(it) }
    }
    if (confirmDelete && start != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = colors.surface,
            title = { Text("Delete ${start.name}?", color = colors.accentLight) },
            text = { Text("The recipe goes from every device. Cards already sorted stay where they are.", color = colors.textMuted) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onChange { deleteRecipe(it, start.id) }; onBack() }) { Text("Delete", color = colors.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep it", color = colors.textMuted) } }
        )
    }
}

@Composable
private fun Warning(text: String) {
    val colors = LocalAppColors.current
    Text(
        text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.warning,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.warning.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

private fun numbers(text: String): List<Double> = text.split(Regex("[;\\s]+|,(?!\\d)")).mapNotNull { it.replace(',', '.').toDoubleOrNull() }
private fun textOf(l: SplitLevel): String = when {
    l.cuts != null -> l.cuts.joinToString(", ") { if (it == Math.floor(it)) it.toLong().toString() else it.toString() }
    l.letters != null -> l.letters.joinToString(", ")
    l.sets != null -> l.sets.joinToString(", ") { it.uppercase() }
    else -> ""
}

/** One level's settings: what it splits by, and its bands, ranges or sets. */
@Composable
private fun LevelEditor(level: SplitLevel, currency: String, onChange: (SplitLevel) -> Unit, onRemove: () -> Unit) {
    val colors = LocalAppColors.current
    var text by remember(level.by) { mutableStateOf(textOf(level)) }
    val hint = when (level.by) {
        "VALUE" -> "Band edges in $currency, like 20, 5, 1"
        "MANA_VALUE" -> "Where each pile starts, like 0, 2, 3, 4, 5"
        "NUMBER" -> "Where each pile starts, like 1, 100, 200"
        "NAME" -> "Where each range starts, like A, F, L, R"
        "SET" -> "Set codes, like DSK, BLB"
        else -> null
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LEVEL_BYS.forEach { b -> Pill(LEVEL_LABELS.getValue(b), level.by == b) { val next = splitLevel(SplitLevel(b)); text = textOf(next); onChange(next) } }
        }
        if (hint != null) OutlinedTextField(
            text,
            { t ->
                text = t
                onChange(splitLevel(when (level.by) {
                    "NAME" -> level.copy(letters = t.split(Regex("[,;\\s]+")))
                    "SET" -> level.copy(sets = t.split(Regex("[,;\\s]+")))
                    else -> level.copy(cuts = numbers(t))
                }))
            },
            label = { Text(hint) }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        if (level.by == "VALUE") CheckRow("Only the top bands apart; the rest go on to the next level", level.restOn == true) { onChange(splitLevel(level.copy(restOn = it))) }
        if (level.by == "COLOUR") CheckRow("Lands in a pile of their own", level.lands == true) { onChange(splitLevel(level.copy(lands = it))) }
        TextButton(onClick = onRemove) { Text("Remove this level", color = colors.accent) }
    }
}

@Composable
private fun RecipeLayout(
    start: SortRecipe, collections: List<Collection>, store: RecipeSessionStore, resumes: Int, onBack: () -> Unit, onChange: ((List<Collection>) -> List<Collection>) -> Unit,
    onEdit: () -> Unit, onSummary: () -> Unit, onScan: () -> Unit
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    var draft by remember(start) { mutableStateOf(sortRecipe(start)) }
    var voice by remember { mutableStateOf(store.voice()) }
    var placing by remember { mutableStateOf<RecipePile?>(null) }
    var printing by remember { mutableStateOf(false) }
    var replacing by remember { mutableStateOf(false) }
    val derived = derivePiles(draft, ::fmtLocal)
    val places = placesOf(collections)
    fun goesTo(p: RecipePile): String = when (p.key) {
        "S:DECKS" -> "For their decks"
        "S:BINDER" -> "Into its binder"
        else -> pileGoesTo(draft, p).let { to -> if (to == BY_RULE) "Box whose rule fits" else if (to.isEmpty()) "Unsorted" else places.firstOrNull { it.id == to }?.name ?: "No place" }
    }
    val begin = {
        if (!draft.isTemplate && sortRecipe(start) != draft) onChange { saveRecipe(it, draft) }
        if (!draft.isTemplate) store.setLastRecipeId(draft.id)
        store.saveSession(startRecipeSession(draft))
        onScan()
    }
    val session = remember(resumes) { store.session() }
    Frame(
        "Lay out ${derived.piles.size} ${if (derived.piles.size == 1) "pile" else "piles"}", "${draft.name} · left to right on the table", onBack,
        action = { TextButton(onClick = onEdit) { Text("Change", color = colors.accent, fontWeight = FontWeight.Bold) } },
        bottom = {
            Big("Print pile signs", false) { printing = true }
            Big("Start scanning", true, Modifier.weight(1f)) { if (session != null && session.scans.isNotEmpty()) replacing = true else begin() }
        }
    ) {
        capWarning(derived)?.let { Warning(it) }
        derived.piles.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { p ->
                    val fixed = p.key == "S:DECKS" || p.key == "S:BINDER"
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(colors.surface)
                            .semantics { contentDescription = "Pile ${p.number}, ${p.name}, goes to ${goesTo(p)}" + if (fixed) "" else " — change" }
                            .then(if (fixed) Modifier else Modifier.clickable(role = Role.Button) { placing = p })
                    ) {
                        Box(Modifier.fillMaxWidth().height(4.dp).background(bandColor(p.band)))
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${p.number}", style = TextStyle(fontFamily = BebasNumbers, fontSize = 30.sp, lineHeight = 30.sp), color = colors.textPrimary)
                            Text(p.name, fontSize = 12.sp, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(goesTo(p), fontSize = 11.sp, color = colors.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Column(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("How you'll hear it", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary, modifier = Modifier.a11yHeading())
            SwitchRow("Say the pile out loud (\"Seven, blue\")", voice.speak) { voice = voice.copy(speak = it); store.saveVoice(voice) }
            SwitchRow("Capture without tapping", voice.auto) { voice = voice.copy(auto = it); store.saveVoice(voice) }
            Text(if (voice.auto) "Hold a card still under the camera; take it away and show the next." else "Tap Scan now for each card.", fontSize = 12.sp, color = colors.textMuted)
        }
    }
    placing?.let { p ->
        AlertDialog(
            onDismissRequest = { placing = null },
            containerColor = colors.surface,
            title = { Text("Where pile ${p.number} goes", color = colors.accentLight) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (listOf(BY_RULE to "The box whose rule fits", "" to "No place (Unsorted)") + placeTree(places).map { it.place.id to "  ".repeat(it.depth) + it.place.name }).forEach { (id, name) ->
                        RecipeRow(name, if (pileGoesTo(draft, p) == id) "Chosen" else "", mine = pileGoesTo(draft, p) == id) { draft = withGoTo(draft, p.key, id); placing = null }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { placing = null }) { Text("Close", color = colors.textMuted) } }
        )
    }
    if (printing) {
        AlertDialog(
            onDismissRequest = { printing = false },
            containerColor = colors.surface,
            title = { Text("Print pile signs", color = colors.accentLight) },
            text = { Text("Two signs to a page: the pile's number, big, and its name — one by each pile.", color = colors.textMuted) },
            confirmButton = {
                TextButton(onClick = {
                    printing = false
                    printLabels(context, pileSignsHtml(derived.piles, "A4", "${draft.name}: pile signs"), "${draft.name} pile signs", PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
                }) { Text("A4", color = colors.accent) }
            },
            dismissButton = {
                TextButton(onClick = {
                    printing = false
                    printLabels(context, pileSignsHtml(derived.piles, "LETTER", "${draft.name}: pile signs"), "${draft.name} pile signs", PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.NA_LETTER).build())
                }) { Text("Letter", color = colors.accent) }
            }
        )
    }
    if (replacing && session != null) {
        AlertDialog(
            onDismissRequest = { replacing = false },
            containerColor = colors.surface,
            title = { Text("Start a new sort?", color = colors.accentLight) },
            text = { Text("${session.scans.size} ${if (session.scans.size == 1) "card" else "cards"} sorted with ${session.recipe.name} haven't been filed. Starting again forgets them.", color = colors.textMuted) },
            confirmButton = { TextButton(onClick = { replacing = false; begin() }) { Text("Start again", color = colors.error) } },
            dismissButton = { TextButton(onClick = { replacing = false; onSummary() }) { Text("File those first", color = colors.accent) } }
        )
    }
}

@Composable
private fun SwitchRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChange(!on) }.heightIn(min = 44.dp)) {
        Text(label, fontSize = 14.sp, color = colors.textPrimary, modifier = Modifier.weight(1f))
        Switch(on, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.onAccent))
    }
}

@Composable
private fun RecipeSummaryView(
    collections: List<Collection>, store: RecipeSessionStore, resumes: Int, onBack: () -> Unit, onChange: ((List<Collection>) -> List<Collection>) -> Unit,
    onScan: () -> Unit, onOffer: (String, List<TradeCard>) -> Unit, onFit: (String) -> Unit, onDone: () -> Unit
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val money by Prices.money.collectAsState()
    var session by remember(resumes) { mutableStateOf(store.session()) }
    var checkingMisses by remember { mutableStateOf(false) }
    var choosingPile by remember { mutableStateOf(false) }
    var filed by remember { mutableStateOf<Pair<Int, List<Pair<String, String>>>?>(null) }
    fun update(next: RecipeSessionState?) { store.saveSession(next); session = next }
    filed?.let { (count, binders) ->
        Frame("Filed $count ${if (count == 1) "card" else "cards"}", null, onBack, bottom = { Big("Done", true, Modifier.weight(1f), onClick = onDone) }) {
            Text("They're in your collection, each where its pile goes. Cards for a binder in order wait beside it to be fitted in.", color = colors.textMuted, fontSize = 14.sp)
            binders.forEach { (id, name) -> RecipeRow("Fit in order: $name", "The steps say where each goes, page and slot.") { onFit(id) } }
        }
        return
    }
    val s = session
    if (s == null) {
        Frame("Sorted", null, onBack, bottom = {}) { Text("Nothing being sorted right now.", color = colors.textMuted) }
        return
    }
    val derived = derivePiles(s.recipe, ::fmtLocal)
    val sum = summarize(s.recipe, derived, s.scans)
    val lastAt = s.scans.maxOfOrNull { it.at ?: 0L } ?: 0L
    val minutes = if (lastAt > s.startedAt) maxOf(1L, Math.round((lastAt - s.startedAt) / 60000.0)) else 0L
    val friendsPile = derived.piles.firstOrNull { it.key == "S:FRIENDS" }
    // The friends pile, friend by friend: their user id, name and cards.
    val byFriend = s.scans.filter { friendsPile != null && it.pile == friendsPile.number && it.reason?.kind == "FRIENDS" && it.filed != true && it.reason.friendId != null }
        .groupBy { it.reason!!.friendId!! }
    val fileAll = {
        val at = System.currentTimeMillis()
        val result = fileRecipe(collections, s.recipe, derived, s.scans)
        val places = placesOf(result.collections)
        CopyHistoryStore.init(context)
        CopyHistoryStore.record(result.steps.map { f ->
            val place = f.step?.to?.placeId?.let { id -> places.firstOrNull { it.id == id } }
            val card = MoveCard(f.scan.name, f.scan.scryfallId)
            val from = f.step?.from
            if (from != null) putAwayMove(at, card, 1, place?.let { MoveSpot(it.id, f.to) } ?: MoveSpot("", f.to), places.firstOrNull { it.id == from.placeId }?.let { MoveSpot(it.id, it.name) }, "sorting a pile")
            else addedMove(at, card, 1, place?.let { MoveSpot(it.id, f.to) }, s.recipe.name)
        })
        onChange { fileRecipe(it, s.recipe, derived, s.scans).collections }
        val binders = s.scans.filter { it.filed != true }.mapNotNull { binderFiledInto(s.recipe, it) }.distinct()
            .map { id -> id to (placesOf(collections).firstOrNull { it.id == id }?.name ?: "Binder") }
        update(null)
        filed = s.scans.count { it.filed != true } to binders
    }
    Frame(
        "Sorted ${sum.cards} ${if (sum.cards == 1) "card" else "cards"}",
        listOfNotNull(s.recipe.name, if (minutes > 0) "$minutes min" else null, "${money.format(sum.usd, whole = true)} in all").joinToString(" · "),
        onBack,
        bottom = {
            Big("Keep going", false, onClick = onScan)
            Big("File everything", true, Modifier.weight(1f), enabled = s.scans.any { it.filed != true }, onClick = fileAll)
        }
    ) {
        sum.rows.forEach { r ->
            val band = derived.piles.firstOrNull { it.number == r.from }?.band
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surface).padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    if (r.from == r.to) "${r.from}" else "${r.from}–${r.to}",
                    style = TextStyle(fontFamily = BebasNumbers, fontSize = 24.sp),
                    color = if (r.from == r.to && band != null) bandColor(band) else colors.textMuted,
                    modifier = Modifier.padding(end = 10.dp)
                )
                Text("${r.name} · ${r.cards}", fontSize = 14.sp, color = colors.textPrimary, modifier = Modifier.weight(1f))
                Text(r.detail ?: money.format(r.usd, whole = r.usd >= 10), fontSize = 14.sp, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (sum.rows.isEmpty()) Text("No cards sorted yet.", color = colors.textMuted)
        if (s.misses.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF2A1D14)).padding(horizontal = 14.dp, vertical = 4.dp)) {
                Text("${s.misses.size} ${if (s.misses.size == 1) "card couldn't" else "cards couldn't"} be read", fontSize = 14.sp, color = colors.textPrimary, modifier = Modifier.weight(1f))
                TextButton(onClick = { checkingMisses = true }) { Text("Check them", color = colors.accent, fontWeight = FontWeight.Bold) }
            }
        }
        if (s.scans.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("File them", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary, modifier = Modifier.a11yHeading())
                Text(fileLine(s, derived.piles, collections), fontSize = 13.sp, color = colors.textMuted)
                TextButton(onClick = { choosingPile = true }) { Text("Check a pile (rescan to catch mistakes)", color = colors.accent, fontWeight = FontWeight.Bold) }
                if (friendsPile != null && byFriend.isNotEmpty()) {
                    Text("Offer pile ${friendsPile.number} to ${byFriend.values.joinToString(" and ") { it.first().reason?.friend ?: "a friend" }}:", fontSize = 13.sp, color = colors.textMuted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        byFriend.forEach { (id, scans) ->
                            TextButton(onClick = { onOffer(id, scans.map { TradeCard(it.scryfallId, it.name, it.entry.imageUrl, foil = it.card.foil, quantity = 1) }) }) {
                                Text("Offer to ${scans.first().reason?.friend ?: "a friend"}", color = colors.accent, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
    if (checkingMisses) {
        AlertDialog(
            onDismissRequest = { checkingMisses = false },
            containerColor = colors.surface,
            title = { Text("Couldn't read these", color = colors.accentLight) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Find them in the piles by what the camera made of them, then scan them again — or type their names on the scanner.", color = colors.textMuted, fontSize = 13.sp)
                    s.misses.forEachIndexed { i, m ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("“${m.seen.ifEmpty { "…" }}”", color = colors.textPrimary, modifier = Modifier.weight(1f))
                            TextButton(onClick = { update(s.copy(misses = s.misses.filterIndexed { j, _ -> j != i })) }) { Text("Found it", color = colors.accent) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { checkingMisses = false; onScan() }) { Text("Scan them again", color = colors.accent) } },
            dismissButton = { TextButton(onClick = { update(s.copy(misses = emptyList())); checkingMisses = false }) { Text("Clear the list", color = colors.textMuted) } }
        )
    }
    if (choosingPile) {
        AlertDialog(
            onDismissRequest = { choosingPile = false },
            containerColor = colors.surface,
            title = { Text("Check which pile?", color = colors.accentLight) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Scan the pile again: any card that doesn't belong in it is flagged.", color = colors.textMuted, fontSize = 13.sp)
                    derived.piles.filter { p -> s.scans.any { it.pile == p.number } }.forEach { p ->
                        RecipeRow("Pile ${p.number} · ${p.name}", "${s.scans.count { it.pile == p.number }} cards") {
                            choosingPile = false
                            update(s.copy(checking = PileChecking(p.number)))
                            onScan()
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingPile = false }) { Text("Close", color = colors.textMuted) } }
        )
    }
}

/** "File them" in words: where the piles go — the boxes by rule, the binders with page and slot, the decks. */
private fun fileLine(s: RecipeSessionState, piles: List<RecipePile>, collections: List<Collection>): String {
    val live = s.scans.filter { it.filed != true }
    val used = piles.filter { p -> live.any { it.pile == p.number } }
    val places = placesOf(collections)
    val parts = mutableListOf<String>()
    fun range(list: List<RecipePile>) = if (list.size == 1) "Pile ${list[0].number}" else "Piles ${list.first().number}–${list.last().number}"
    val byRule = used.filter { it.key != "S:DECKS" && it.key != "S:BINDER" && pileGoesTo(s.recipe, it) == BY_RULE }
    if (byRule.isNotEmpty()) parts += "${range(byRule)} ${if (byRule.size == 1) "goes" else "go"} to the boxes whose rules fit"
    for (p in used) {
        val to = pileGoesTo(s.recipe, p)
        when {
            p.key == "S:BINDER" -> {
                val binders = live.filter { it.pile == p.number }.mapNotNull { it.reason?.binder }.distinct()
                parts += "${p.number} to the ${binders.joinToString(" and ")} ${if (binders.size == 1) "binder" else "binders"} with page and slot"
            }
            p.key == "S:DECKS" -> parts += "${p.number} with no place, for the decks' pull lists"
            to.isNotEmpty() && to != BY_RULE -> parts += "${p.number} to ${places.firstOrNull { it.id == to }?.name ?: "its place"}"
            to.isEmpty() -> parts += "${p.number} to Unsorted"
        }
    }
    return if (parts.isEmpty()) "Everything here is filed already." else parts.joinToString("; ") + "."
}
