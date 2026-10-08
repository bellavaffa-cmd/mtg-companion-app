@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.mtgcompanion.app.ui.collection

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Filter4
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionEntry
import com.mtgcompanion.app.data.CollectionGoal
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.GOAL_KINDS
import com.mtgcompanion.app.data.GOAL_KIND_DETAILS
import com.mtgcompanion.app.data.GOAL_KIND_LABELS
import com.mtgcompanion.app.data.GOAL_PLAYSET
import com.mtgcompanion.app.data.GOAL_RARITIES
import com.mtgcompanion.app.data.GoalCard
import com.mtgcompanion.app.data.GoalPrice
import com.mtgcompanion.app.data.GoalProgress
import com.mtgcompanion.app.data.GoalSetCard
import com.mtgcompanion.app.data.Money
import com.mtgcompanion.app.data.SetInfo
import com.mtgcompanion.app.data.completeGoals
import com.mtgcompanion.app.data.saveGoal
import com.mtgcompanion.app.data.deleteGoal
import com.mtgcompanion.app.data.CollectionType
import com.mtgcompanion.app.data.deckGoalName
import com.mtgcompanion.app.data.goalNameKey
import com.mtgcompanion.app.data.goalProgress
import com.mtgcompanion.app.data.goalWishlistAdds
import com.mtgcompanion.app.data.goalsOf
import com.mtgcompanion.app.data.missingLine
import com.mtgcompanion.app.data.missingLines
import com.mtgcompanion.app.data.missingNames
import com.mtgcompanion.app.data.newDeckGoal
import com.mtgcompanion.app.data.newListGoal
import com.mtgcompanion.app.data.newSetGoal
import com.mtgcompanion.app.data.parseCardList
import com.mtgcompanion.app.data.progressLine
import com.mtgcompanion.app.data.setGoalName
import com.mtgcompanion.app.data.sortedGoals
import com.mtgcompanion.app.data.withGoals
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.scryfall.ScryfallIdentifier
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.common.PillChip
import com.mtgcompanion.app.ui.common.a11yHeading
import com.mtgcompanion.app.ui.common.rememberMoney
import com.mtgcompanion.app.ui.common.rememberReduceMotion
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.UUID

/*
 * Collection goals on screen: the goals list (open ones nearest done first, then Completed), one goal
 * with its missing cards — Add missing to Wishlist, Offer a trade (friends who have them), count cards
 * in decks — New goal, the Goals card on the Collection's home, "Make this a goal" for a set or a
 * deck, and the watcher that notices a goal completing and celebrates. The rules are
 * data/CollectionGoals.kt. The web app's pages/GoalsPage.tsx and collection/GoalsUi.tsx.
 */

fun newGoalId(): String = UUID.randomUUID().toString()

/** A set's printing as a set goal keeps it. */
fun goalSetCard(c: ScryfallCard) = GoalSetCard(
    c.id, c.name, c.rarity, c.collectorNumber, c.displayImageUrl, c.prices?.usd?.toDoubleOrNull(), c.prices?.usdFoil?.toDoubleOrNull()
)

private val Green = Color(0xFF5FBF7A)

/** The bar: gold while going, green once complete. */
@Composable
fun GoalBar(p: GoalProgress, big: Boolean = false) {
    val colors = LocalAppColors.current
    LinearProgressIndicator(
        progress = { if (p.need == 0) 0f else (p.have.toFloat() / p.need).coerceIn(0f, 1f) },
        color = if (p.complete) Green else colors.accent,
        trackColor = colors.border,
        modifier = Modifier.fillMaxWidth().height(if (big) 10.dp else 6.dp).clip(RoundedCornerShape(50))
    )
}

/** One goal on a list: its name, bar, have/need and what's missing. */
@Composable
fun GoalRow(goal: CollectionGoal, p: GoalProgress, money: Money, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(goal.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (goal.isComplete) Icon(Icons.Filled.TaskAlt, contentDescription = "Complete", tint = Green, modifier = Modifier.size(18.dp))
            else Text(progressLine(p), style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
        }
        GoalBar(p)
        Text(
            if (goal.isComplete && !p.complete) "Completed · now ${progressLine(p)}" else missingLine(p) { money.format(it) },
            style = MaterialTheme.typography.labelSmall, color = colors.textMuted
        )
    }
}

/** The Collection home's Goals card: the two open goals nearest done, and See all. */
@Composable
fun GoalsHomeCard(collections: List<Collection>, decks: List<Deck>, money: Money, onOpenGoals: () -> Unit, onOpenGoal: (String) -> Unit, onNewGoal: () -> Unit) {
    val colors = LocalAppColors.current
    val goals = goalsOf(collections)
    val progress = remember(collections, decks) { HashMap<String, GoalProgress>() }
    val of = { g: CollectionGoal -> progress.getOrPut(g.id) { goalProgress(g, collections, decks) } }
    val (open, done) = remember(collections, decks) { sortedGoals(goals, of) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("Goals", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary, modifier = Modifier.weight(1f).a11yHeading())
            TextButton(onClick = if (goals.isEmpty()) onNewGoal else onOpenGoals) {
                Text(if (goals.isEmpty()) "New goal" else "See all", color = colors.accent, fontWeight = FontWeight.SemiBold)
            }
        }
        if (open.isEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface)
                    .clickable(role = Role.Button, onClick = if (goals.isEmpty()) onNewGoal else onOpenGoals).padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Filled.Flag, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                Text(
                    if (done.isNotEmpty()) "${done.size} complete — set another goal" else "Set a goal: finish a set, playsets, a foil deck…",
                    style = MaterialTheme.typography.bodyMedium, color = colors.textMuted
                )
            }
        } else open.take(2).forEach { g -> GoalRow(g, of(g), money) { onOpenGoal(g.id) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalFrame(title: String, subtitle: String?, onBack: () -> Unit, actions: @Composable () -> Unit = {}, content: @Composable (PaddingValues) -> Unit) {
    val colors = LocalAppColors.current
    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        subtitle?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = { actions() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        },
        content = content
    )
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = LocalAppColors.current.textMuted, modifier = modifier.padding(top = 14.dp, bottom = 6.dp))
}

/** Every goal: open ones nearest done first, then Completed. */
@Composable
fun GoalsScreen(collections: List<Collection>, decks: List<Deck>, onBack: () -> Unit, onOpenGoal: (String) -> Unit, onNewGoal: () -> Unit) {
    val colors = LocalAppColors.current
    val money = rememberMoney()
    val goals = goalsOf(collections)
    val progress = remember(collections, decks) { HashMap<String, GoalProgress>() }
    val of = { g: CollectionGoal -> progress.getOrPut(g.id) { goalProgress(g, collections, decks) } }
    val (open, done) = remember(collections, decks) { sortedGoals(goals, of) }
    GoalFrame("Goals", "Collection", onBack, actions = {
        TextButton(onClick = onNewGoal) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = colors.accent)
            Text("New goal", color = colors.accent, fontWeight = FontWeight.SemiBold)
        }
    }) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            if (goals.isEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 40.dp)) {
                    Icon(Icons.Filled.Flag, contentDescription = null, tint = colors.accent, modifier = Modifier.size(36.dp))
                    Text(
                        "Set a target and watch it fill up: every Duskmourn uncommon, a playset of each shock land, your Krenko deck in foil.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.textMuted
                    )
                    Button(onClick = onNewGoal, colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)) { Text("New goal") }
                }
            }
            items(open, key = { it.id }) { g -> GoalRow(g, of(g), money) { onOpenGoal(g.id) } }
            if (done.isNotEmpty()) item { SectionLabel("Completed") }
            items(done, key = { "done-" + it.id }) { g -> GoalRow(g, of(g), money) { onOpenGoal(g.id) } }
        }
    }
}

/**
 * One goal: have/need, percent and what's missing would cost, "Count cards in decks", the missing
 * cards (or all of them), Add missing to Wishlist and Offer a trade. [onWhoHas]: friends who have
 * the missing cards (signed in only).
 */
@Composable
fun GoalScreen(
    goalId: String,
    collections: List<Collection>,
    decks: List<Deck>,
    cardRepository: CardRepository,
    onBack: () -> Unit,
    onChange: ((List<Collection>) -> List<Collection>) -> Unit,
    onAddWanted: (List<CollectionEntry>) -> Unit,
    onWhoHas: ((List<String>) -> Unit)?,
    onViewCard: (String) -> Unit
) {
    val colors = LocalAppColors.current
    val money = rememberMoney()
    val goal = goalsOf(collections).firstOrNull { it.id == goalId }
    var show by rememberSaveable { mutableStateOf("missing") }
    var message by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var prices by remember { mutableStateOf<Map<String, GoalPrice>>(emptyMap()) }
    val base = remember(goal, collections, decks) { goal?.let { goalProgress(it, collections, decks) } }
    // Prices as Scryfall has them now, for the missing cards — fresher than the goal's own.
    val missingIds = base?.let { missingLines(it).mapNotNull { l -> l.scryfallId }.distinct().take(300) }.orEmpty()
    LaunchedEffect(missingIds.joinToString(",")) {
        if (missingIds.isEmpty()) return@LaunchedEffect
        runCatching { cardRepository.getCardsByIds(missingIds) }.getOrNull()?.let { cards ->
            prices = cards.associate { it.id to GoalPrice(it.prices?.usd?.toDoubleOrNull(), it.prices?.usdFoil?.toDoubleOrNull()) }
        }
    }
    if (goal == null || base == null) {
        GoalFrame("Goal", null, onBack) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).padding(20.dp)) { Text("This goal isn't here any more.", color = colors.textMuted) }
        }
        return
    }
    val p = remember(base, prices) { goalProgress(goal, collections, decks, prices) }
    val missing = missingLines(p)
    val lines = if (show == "missing") missing else p.lines
    fun change(next: CollectionGoal) = onChange { c -> saveGoal(c, next.copy(updatedAt = System.currentTimeMillis())) }
    val deckName = if (goal.kind == "DECK") decks.firstOrNull { it.id == goal.deckId }?.name else null

    GoalFrame(goal.name, "Goal · ${GOAL_KIND_LABELS[goal.kind]}" + if (goal.isFoil) " · foil" else "", onBack, actions = {
        TextButton(onClick = { renaming = goal.name }) { Text("Rename", color = colors.accent) }
        TextButton(onClick = { deleting = true }) { Text("Delete", color = colors.accent) }
    }) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${p.have}/${p.need}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        Text("  ${p.percent}%", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                    }
                    GoalBar(p, big = true)
                    Text(
                        goal.completedAt?.let { at -> "Completed ${DateFormat.getDateInstance().format(Date(at))}" + if (p.complete) "" else " · ${p.need - p.have} missing now" }
                            ?: missingLine(p) { money.format(it) },
                        style = MaterialTheme.typography.bodySmall, color = colors.textMuted
                    )
                    if (goal.kind == "DECK") {
                        Text(
                            deckName?.let { "Follows $it as it changes." } ?: "The deck is gone: this is its list from when the goal was made.",
                            style = MaterialTheme.typography.bodySmall, color = colors.textMuted
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { change(goal.copy(countDecks = !goal.countsDecks)) }) {
                        Checkbox(checked = goal.countsDecks, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = colors.accent))
                        Text(
                            "Count cards in decks" + if (goal.isFoil) " (foil copies a pull list brought in)" else "",
                            style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
            if (missing.isNotEmpty()) item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Button(
                        onClick = {
                            val wishlist = collections.firstOrNull { it.kind == CollectionType.WISHLIST }?.entries.orEmpty()
                            val adds = goalWishlistAdds(p, wishlist)
                            if (adds.isEmpty()) message = "Your Wishlist already has them all."
                            else {
                                onAddWanted(adds.map { CollectionEntry(it.scryfallId, it.name, it.imageUrl, quantity = it.quantity) })
                                message = if (adds.size == 1) "1 card put on your Wishlist." else "${adds.size} cards put on your Wishlist."
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.Star, contentDescription = null)
                        Text("  Add missing to Wishlist")
                    }
                    if (onWhoHas != null) {
                        OutlinedButton(onClick = { onWhoHas(missingNames(p)) }, shape = RoundedCornerShape(8.dp)) {
                            Icon(Icons.Filled.PersonSearch, contentDescription = null, tint = colors.accent)
                            Text("  Offer a trade", color = colors.accent)
                        }
                    }
                }
            }
            message?.let { m -> item { Text(m, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) } }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                    PillChip("Missing", show == "missing", { show = "missing" }, count = missing.size)
                    PillChip("All cards", show == "all", { show = "all" }, count = p.lines.size)
                }
            }
            if (lines.isEmpty()) item {
                Text(if (p.need == 0) "No cards in this goal yet." else "Nothing missing.", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, modifier = Modifier.padding(vertical = 16.dp))
            }
            items(lines, key = { it.key }) { l ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surface)
                        .clickable(role = Role.Button) { onViewCard(l.name) }.padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    AsyncImage(
                        model = l.imageUrl, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.width(32.dp).height(44.dp).clip(RoundedCornerShape(4.dp)).background(colors.surface2)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(l.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val sub = listOfNotNull(l.number?.let { "#$it" }, l.rarity, if (l.missing > 0) l.usd?.let { "${money.format(it)} each" } else null).joinToString(" · ")
                        if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                    }
                    Text("${l.have}/${l.need}", style = MaterialTheme.typography.labelLarge, color = if (l.missing == 0) Green else colors.textPrimary)
                }
            }
        }
    }

    renaming?.let { text ->
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename goal") },
            text = { OutlinedTextField(text, { renaming = it }, singleLine = true, label = { Text("Name") }) },
            confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { change(goal.copy(name = text)); renaming = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
            containerColor = colors.surface
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete ${goal.name}?") },
            text = { Text("The goal goes from every device. Your cards stay where they are.") },
            confirmButton = { TextButton(onClick = { deleting = false; onChange { c -> deleteGoal(c, goal.id) }; onBack() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Keep it") } },
            containerColor = colors.surface
        )
    }
}

private val RARITY_LABELS = mapOf("common" to "Common", "uncommon" to "Uncommon", "rare" to "Rare", "mythic" to "Mythic")

/** Choosing the rarities and the finish for a set goal. */
@Composable
fun SetGoalOptions(setName: String, rarities: List<String>, foil: Boolean, onRarities: (List<String>) -> Unit, onFoil: (Boolean) -> Unit, cards: List<GoalSetCard>?) {
    val colors = LocalAppColors.current
    val count = cards?.count { rarities.isEmpty() || (it.rarity ?: "").lowercase() in rarities }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Which cards", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PillChip("Every card", rarities.isEmpty(), { onRarities(emptyList()) })
            GOAL_RARITIES.forEach { r ->
                PillChip(RARITY_LABELS.getValue(r), r in rarities, { onRarities(GOAL_RARITIES.filter { x -> if (x == r) r !in rarities else x in rarities }) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(role = Role.Checkbox) { onFoil(!foil) }) {
            Checkbox(checked = foil, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = colors.accent))
            Text("In foil — only foil copies count", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.padding(start = 8.dp))
        }
        Text(
            "“${setGoalName(setName, rarities, foil)}”" + (count?.let { " · $it ${if (it == 1) "card" else "cards"}" } ?: ""),
            style = MaterialTheme.typography.bodySmall, color = colors.textMuted
        )
    }
}

/** "Make this a goal" on a set's page. [onCreate] gets the new goal. */
@Composable
fun MakeSetGoalDialog(setCode: String, setName: String, cards: List<ScryfallCard>, onDismiss: () -> Unit, onCreate: (CollectionGoal) -> Unit) {
    val colors = LocalAppColors.current
    var rarities by remember { mutableStateOf(emptyList<String>()) }
    var foil by remember { mutableStateOf(false) }
    val setCards = remember(cards) { cards.map { goalSetCard(it) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Make this a goal") },
        text = { SetGoalOptions(setName, rarities, foil, { rarities = it }, { foil = it }, setCards) },
        confirmButton = {
            TextButton(onClick = { onCreate(newSetGoal(newGoalId(), setCode.lowercase(), setName, setCards, rarities, foil, System.currentTimeMillis())) }) {
                Text("Make it a goal")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = colors.surface
    )
}

/** "Make this a goal" on a deck: every card in foil (or every card at all). */
@Composable
fun MakeDeckGoalDialog(deck: Deck, onDismiss: () -> Unit, onCreate: (CollectionGoal) -> Unit) {
    val colors = LocalAppColors.current
    var foil by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Make this a goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(true to ("Foil this deck" to "Every card in foil. Foil copies the pull list brought into the deck count."), false to ("Own every card" to "Every card, any finish.")).forEach { (f, words) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(role = Role.RadioButton) { foil = f }) {
                        RadioButton(selected = foil == f, onClick = null)
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(words.first, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                            Text(words.second, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                        }
                    }
                }
                Text("“${deckGoalName(deck.name, foil)}” · basic lands left out", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(newDeckGoal(newGoalId(), deck, foil, System.currentTimeMillis())) }) { Text("Make it a goal") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = colors.surface
    )
}

/** A card picked for a list goal. */
private fun pickedOf(c: ScryfallCard, qty: Int) = GoalCard(c.name, c.id, qty, c.displayImageUrl, c.prices?.usd?.toDoubleOrNull(), c.prices?.usdFoil?.toDoubleOrNull())

/**
 * New goal: pick the kind, then — a set (rarities, foil), a deck (foil or not), or a list (cards
 * looked up by name, or a pasted list; a playset's count). [startKind], [startDeck]: from "Make this a goal".
 */
@Composable
fun NewGoalScreen(
    decks: List<Deck>,
    cardRepository: CardRepository,
    startKind: String?,
    startDeck: String?,
    onBack: () -> Unit,
    onCreate: (CollectionGoal) -> Unit
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    var kind by rememberSaveable { mutableStateOf(startKind?.takeIf { it in GOAL_KINDS }) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Set goals
    var sets by remember { mutableStateOf<List<SetInfo>?>(null) }
    var setQuery by rememberSaveable { mutableStateOf("") }
    var set by remember { mutableStateOf<SetInfo?>(null) }
    var setCards by remember { mutableStateOf<List<ScryfallCard>?>(null) }
    var rarities by remember { mutableStateOf(emptyList<String>()) }
    var foil by remember { mutableStateOf(false) }
    LaunchedEffect(kind) {
        if (kind == "SET" && sets == null) {
            sets = runCatching { cardRepository.getSets().values.filter { !it.digital && it.cardCount > 0 }.sortedByDescending { it.releasedAt.orEmpty() } }
                .onFailure { error = "Couldn't fetch the sets from Scryfall." }.getOrNull()
        }
    }
    LaunchedEffect(set) {
        val s = set ?: return@LaunchedEffect
        setCards = null
        setCards = runCatching { cardRepository.getSetCards(s.code) }.onFailure { error = "Couldn't fetch this set's cards from Scryfall." }.getOrNull()
    }

    // Deck goals
    val usable = decks.filter { it.sample != true && it.archived != true }
    var deckId by rememberSaveable { mutableStateOf(startDeck ?: usable.firstOrNull()?.id) }
    var deckFoil by rememberSaveable { mutableStateOf(true) }

    // List goals
    var name by rememberSaveable { mutableStateOf("") }
    var qty by rememberSaveable { mutableStateOf(GOAL_PLAYSET.toString()) }
    var picked by remember { mutableStateOf(emptyList<GoalCard>()) }
    var lookup by rememberSaveable { mutableStateOf("") }
    var pasted by rememberSaveable { mutableStateOf("") }
    val playset = qty.toIntOrNull()?.coerceIn(1, 99) ?: GOAL_PLAYSET

    fun addPicked(c: GoalCard) {
        val at = picked.indexOfFirst { goalNameKey(it.name) == goalNameKey(c.name) }
        picked = if (at < 0) picked + c else picked.mapIndexed { i, x -> if (i == at && kind == "CUSTOM") x.copy(qty = x.qty + c.qty) else x }
    }

    fun create() {
        error = null
        val now = System.currentTimeMillis()
        when (kind) {
            "SET" -> { val s = set; val c = setCards; if (s != null && c != null) onCreate(newSetGoal(newGoalId(), s.code, s.name, c.map { goalSetCard(it) }, rarities, foil, now)) }
            "DECK" -> decks.firstOrNull { it.id == deckId }?.let { onCreate(newDeckGoal(newGoalId(), it, deckFoil, now)) }
            "PLAYSET", "CUSTOM" -> scope.launch {
                busy = true
                // The pasted list's cards, looked up for their pictures and prices; ones Scryfall doesn't know stay, by name.
                val fromPaste = parseCardList(pasted).lines.mapNotNull { l -> l.name?.let { GoalCard(it, qty = maxOf(1, l.quantity)) } }.toMutableList()
                runCatching {
                    val names = fromPaste.associateBy({ goalNameKey(it.name) }, { it.name }).values.toList()
                    val found = HashMap<String, ScryfallCard>()
                    for (chunk in names.chunked(75)) {
                        cardRepository.getCollection(chunk.map { ScryfallIdentifier(name = it) }).data.forEach { found[goalNameKey(it.name)] = it }
                    }
                    for (i in fromPaste.indices) found[goalNameKey(fromPaste[i].name)]?.let { fromPaste[i] = pickedOf(it, fromPaste[i].qty) }
                }
                busy = false
                val cards = picked + fromPaste
                if (cards.isEmpty()) { error = "Add some cards first — look them up by name or paste a list."; return@launch }
                val fallback = if (kind == "PLAYSET") "Playsets of ${cards.size} ${if (cards.size == 1) "card" else "cards"}" else "My list"
                onCreate(newListGoal(newGoalId(), kind!!, name.trim().ifEmpty { fallback }, cards, if (kind == "PLAYSET") playset else null, now))
            }
        }
    }

    GoalFrame("New goal", kind?.let { GOAL_KIND_LABELS[it] }, onBack = { if (kind != null && startKind == null) { kind = null; error = null } else onBack() }) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            if (kind == null) {
                items(GOAL_KINDS) { k ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface)
                            .clickable(role = Role.Button) { kind = k }.padding(14.dp)
                    ) {
                        Icon(
                            when (k) { "SET" -> Icons.Filled.GridView; "PLAYSET" -> Icons.Filled.Filter4; "DECK" -> Icons.Filled.AutoAwesome; else -> Icons.Filled.Checklist },
                            contentDescription = null, tint = colors.accent
                        )
                        Column(Modifier.weight(1f)) {
                            Text(GOAL_KIND_LABELS.getValue(k), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                            Text(GOAL_KIND_DETAILS.getValue(k), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                        }
                    }
                }
            }
            when (kind) {
                "SET" -> {
                    val s = set
                    if (s != null) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Text("${s.name} · ${s.code.uppercase()}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = colors.textPrimary, modifier = Modifier.weight(1f))
                                TextButton(onClick = { set = null; setCards = null }) { Text("Change", color = colors.accent) }
                            }
                        }
                        if (setCards == null && error == null) item { Text("Fetching its cards…", style = MaterialTheme.typography.bodySmall, color = colors.textMuted) }
                        item { SetGoalOptions(s.name, rarities, foil, { rarities = it }, { foil = it }, setCards?.map { goalSetCard(it) }) }
                    } else {
                        item {
                            OutlinedTextField(setQuery, { setQuery = it }, singleLine = true, label = { Text("Find a set — Duskmourn, BLB…") }, modifier = Modifier.fillMaxWidth())
                        }
                        if (sets == null && error == null) item { CircularProgressIndicator(color = colors.accent, modifier = Modifier.padding(16.dp)) }
                        val q = setQuery.trim().lowercase()
                        items(sets.orEmpty().filter { q.isEmpty() || it.name.lowercase().contains(q) || it.code == q }.take(30), key = { it.code }) { info ->
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surface).clickable(role = Role.Button) { set = info }.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(info.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                                Text("${info.code.uppercase()} · ${info.cardCount} cards" + (info.releasedAt?.take(4)?.let { " · $it" } ?: ""), style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                            }
                        }
                    }
                }
                "DECK" -> {
                    if (usable.isEmpty()) item { Text("No decks yet.", color = colors.textMuted) }
                    items(usable, key = { it.id }) { d ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surface).clickable(role = Role.RadioButton) { deckId = d.id }.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            RadioButton(selected = deckId == d.id, onClick = null)
                            Text(d.name, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(role = Role.Checkbox) { deckFoil = !deckFoil }.padding(top = 8.dp)) {
                            Checkbox(checked = deckFoil, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = colors.accent))
                            Text("In foil — every card of the deck foil", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    item { Text("The goal follows the deck as it changes. Basic lands are left out; copies in decks count, so foils already in the deck do.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted) }
                }
                "PLAYSET", "CUSTOM" -> {
                    item { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }, placeholder = { Text(if (kind == "PLAYSET") "Shock lands" else "My list") }, modifier = Modifier.fillMaxWidth()) }
                    if (kind == "PLAYSET") item {
                        OutlinedTextField(qty, { qty = it.filter(Char::isDigit).take(2) }, singleLine = true, label = { Text("Copies of each") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(160.dp))
                    }
                    item { SectionLabel("Cards") }
                    items(picked, key = { "p-" + goalNameKey(it.name) }) { c ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.surface).padding(start = 12.dp)) {
                            Text(c.name, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (kind == "CUSTOM") {
                                TextButton(onClick = { picked = picked.map { if (it === c) it.copy(qty = maxOf(1, it.qty - 1)) else it } }) { Text("−", color = colors.accent) }
                                Text("${c.qty}", color = colors.textPrimary)
                                TextButton(onClick = { picked = picked.map { if (it === c) it.copy(qty = minOf(99, it.qty + 1)) else it } }) { Text("+", color = colors.accent) }
                            }
                            IconButton(onClick = { picked = picked.filter { it !== c } }) { Icon(Icons.Filled.Close, contentDescription = "Remove ${c.name}", tint = colors.textMuted) }
                        }
                    }
                    item {
                        OutlinedTextField(
                            lookup, { lookup = it }, singleLine = true, label = { Text("Add a card by name") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                val n = lookup.trim()
                                if (n.isNotEmpty()) scope.launch {
                                    busy = true
                                    val card = runCatching { cardRepository.getByFuzzyName(n) }.getOrNull()
                                    busy = false
                                    if (card == null) error = "No card called \"$n\"." else { error = null; addPicked(pickedOf(card, if (kind == "PLAYSET") playset else 1)); lookup = "" }
                                }
                            }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item { SectionLabel("Or paste a list") }
                    item {
                        OutlinedTextField(
                            pasted, { pasted = it }, minLines = 4, maxLines = 10,
                            placeholder = { Text(if (kind == "PLAYSET") "Steam Vents\nSacred Foundry\nBlood Crypt" else "4 Lightning Bolt\n2 Counterspell") },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
                        )
                    }
                }
            }
            error?.let { e -> item { Text(e, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) } }
            if (kind != null) item {
                val ready = when (kind) { "SET" -> set != null && setCards != null; "DECK" -> deckId != null; else -> true }
                Button(
                    onClick = { create() }, enabled = ready && !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                    modifier = Modifier.padding(top = 12.dp)
                ) { Text(if (busy) "Looking up the cards…" else "Make it a goal") }
            }
        }
    }
}

private val CONFETTI = listOf(Color(0xFFE6B45E), Color(0xFF5FBF7A), Color(0xFF4D8FE0), Color(0xFFE0674D), Color(0xFFB98CF0), Color(0xFFF3EFE0))

/**
 * Notices goals completing, wherever the cards came from (a scan, an import, a trade): marks them
 * complete — once, on whichever device sees it first — and celebrates: confetti and a buzz, or just
 * the note when the system's "Remove animations" is on. [onChange] changes the binders (the goals
 * ride on the Unsorted pile); [paused]: while a Reset collection waits out its Undo.
 */
@Composable
fun GoalWatcher(collections: List<Collection>, decks: List<Deck>, paused: Boolean, onChange: ((List<Collection>) -> List<Collection>) -> Unit, onOpenGoals: () -> Unit) {
    val colors = LocalAppColors.current
    val view = LocalView.current
    val still = rememberReduceMotion()
    var party by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(collections, decks, paused) {
        if (paused) return@LaunchedEffect
        val goals = goalsOf(collections)
        if (goals.none { it.completedAt == null }) return@LaunchedEffect
        delay(700)
        val found = completeGoals(goals, collections, decks, System.currentTimeMillis())
        if (found.done.isEmpty()) return@LaunchedEffect
        onChange { c ->
            val r = completeGoals(goalsOf(c), c, decks, System.currentTimeMillis())
            if (r.done.isEmpty()) c else withGoals(c, r.goals)
        }
        party = found.goals.filter { it.id in found.done }.map { it.name }
        if (!still) view.performHapticFeedback(if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
    }
    val names = party ?: return
    LaunchedEffect(names) { delay(6000); party = null }
    Box(Modifier.fillMaxSize()) {
        if (!still) {
            val fall = remember(names) { Animatable(0f) }
            LaunchedEffect(names) { fall.animateTo(1f, tween(2600)) }
            Canvas(Modifier.fillMaxSize()) {
                val t = fall.value
                for (i in 0 until 40) {
                    val x = size.width * (((i * 37) % 100) / 100f)
                    val start = (i % 7) * 0.06f
                    val local = ((t - start) / (1f - start)).coerceIn(0f, 1f)
                    if (local <= 0f || local >= 1f) continue
                    val y = -20f + (size.height + 40f) * local
                    rotate(540f * local + i * 13f, pivot = Offset(x, y)) {
                        drawRect(CONFETTI[i % CONFETTI.size], topLeft = Offset(x - 5f, y - 8f), size = Size(10f, 16f), alpha = 1f - local * 0.7f)
                    }
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 96.dp).fillMaxWidth()
                .clip(RoundedCornerShape(18.dp)).background(Color(0xFF2A2210)).padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = colors.accent, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text("Goal complete!", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFFF1EEE6))
                Text(names.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Color(0xFFD9C79F), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = { party = null; onOpenGoals() }) { Text("See goals", color = colors.accent, fontWeight = FontWeight.Bold) }
            IconButton(onClick = { party = null }) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color(0xFFD9C79F)) }
        }
    }
}
