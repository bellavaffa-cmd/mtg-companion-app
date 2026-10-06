package com.mtgcompanion.app.ui.collection

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CopyHistoryStore
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.MoveCard
import com.mtgcompanion.app.data.PullGroupKind
import com.mtgcompanion.app.data.PullProgress
import com.mtgcompanion.app.data.PullRow
import com.mtgcompanion.app.data.SellPrinting
import com.mtgcompanion.app.data.cardmarketCsv
import com.mtgcompanion.app.data.markSold
import com.mtgcompanion.app.data.markSparesToSell
import com.mtgcompanion.app.data.markUnusedToSell
import com.mtgcompanion.app.data.pulledCopies
import com.mtgcompanion.app.data.sellPullList
import com.mtgcompanion.app.data.sellRowTitle
import com.mtgcompanion.app.data.sellRowUsd
import com.mtgcompanion.app.data.sellRows
import com.mtgcompanion.app.data.sellTotalUsd
import com.mtgcompanion.app.data.soldMove
import com.mtgcompanion.app.data.tcgplayerMassEntry
import com.mtgcompanion.app.data.unmarkToSell
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.common.a11yHeading
import com.mtgcompanion.app.ui.common.rememberMoney
import com.mtgcompanion.app.ui.decks.GroupCard
import com.mtgcompanion.app.ui.decks.PullRowLine
import com.mtgcompanion.app.ui.decks.metaLine
import com.mtgcompanion.app.ui.theme.LocalAppColors
import com.mtgcompanion.app.ui.theme.NumberStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/*
 * The To sell list, the web app's SellPage (src/pages/SellPage.tsx): every card marked to sell, with
 * where its copies are (place, page and pocket or section) and what they're worth, the total, two
 * quick rules ("Spares over 4", "Not in any deck, over $5"), TCGplayer mass entry and a Cardmarket CSV,
 * a pull list to fetch them (the deck pull list's look, ticks kept on the phone), and "Mark N sold":
 * the ticked cards leave the collection and their pockets show empty. The logic is data/Selling.kt.
 */

/** The pull list's ticks are kept as a deck's are (PullProgress), under this id. */
private const val SELL_LIST = "sell"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellScreen(
    collections: List<Collection>,
    decks: List<Deck>,
    onBack: () -> Unit,
    onChange: (StorageChange) -> Unit,
    onOpenCard: (String) -> Unit
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val money = rememberMoney()
    val progress = remember { PullProgress(context) }
    remember { CopyHistoryStore.init(context) }
    val rows = remember(collections) { sellRows(collections) }
    // Prices for the cards to sell and the ones a quick rule may mark.
    val ids = remember(collections) {
        collections.filter { it.kind != com.mtgcompanion.app.data.CollectionType.WISHLIST }.flatMap { c -> c.entries.map { it.scryfallId } }.distinct().sorted()
    }
    var prices by remember { mutableStateOf<Map<String, SellPrinting>?>(null) }
    LaunchedEffect(ids) {
        prices = runCatching {
            CardRepository().getCardsByIds(ids).associate {
                it.id to SellPrinting(
                    it.set.orEmpty(), it.collectorNumber.orEmpty(),
                    it.prices?.usd?.toDoubleOrNull(), it.prices?.usdFoil?.toDoubleOrNull(), it.prices?.eur?.toDoubleOrNull()
                )
            }
        }.getOrDefault(emptyMap())
    }
    val facts = { id: String -> prices?.get(id) }
    var ticked by remember { mutableStateOf(setOf<String>()) }
    // Rows that have gone (sold or taken off the list elsewhere) don't stay ticked.
    val tickedNow = ticked.filter { k -> rows.any { it.key == k } }.toSet()
    var pulling by remember { mutableStateOf(false) }
    var pullTicks by remember { mutableStateOf(progress.ticked(PullProgress.ListKind.PULL, SELL_LIST)) }
    var confirming by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val total = sellTotalUsd(rows, facts)
    val soldRows = rows.filter { it.key in tickedNow }
    val soldUsd = sellTotalUsd(soldRows, facts)
    val soldCopies = soldRows.sumOf { it.qty }
    val csvSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val csv = cardmarketCsv(rows, facts)
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write((csv + "\n").toByteArray()) } != null }.getOrDefault(false)
            }
            message = if (ok) "Saved. Upload it on Cardmarket's stock page." else "That file couldn't be saved."
        }
    }
    BackHandler(enabled = pulling) { pulling = false }

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = {
                    if (pulling) Column {
                        Text("To sell", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                        Text("Pull list", style = MaterialTheme.typography.titleLarge, modifier = Modifier.a11yHeading())
                    } else Text("To sell", style = MaterialTheme.typography.titleLarge, modifier = Modifier.a11yHeading())
                },
                navigationIcon = { BackButton(onClick = { if (pulling) pulling = false else onBack() }) },
                actions = {
                    if (!pulling && rows.isNotEmpty()) Text(
                        if (prices == null) "…" else money.format(total, whole = true),
                        style = NumberStyle(26),
                        color = colors.accent,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        },
        bottomBar = {
            if (!pulling && rows.isNotEmpty()) Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().background(colors.bg).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)
            ) {
                Button(
                    onClick = { pulling = true },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.surface2, contentColor = colors.textPrimary),
                    modifier = Modifier.height(48.dp)
                ) { Text("Pull list", fontWeight = FontWeight.Bold) }
                Button(
                    onClick = { confirming = true },
                    enabled = soldCopies > 0,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text(
                        "Mark $soldCopies sold" + if (soldCopies > 0 && prices != null) " · ${money.format(soldUsd, whole = soldUsd >= 10)}" else "",
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    ) { padding ->
        if (pulling) {
            SellPullList(collections, pullTicks, Modifier.padding(padding)) { next ->
                pullTicks = next
                progress.setTicked(PullProgress.ListKind.PULL, SELL_LIST, next)
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    QuickChip("+ Spares over 4") {
                        val n = markSparesToSell(collections, decks).second
                        onChange { markSparesToSell(it, decks).first }
                        message = if (n > 0) "$n ${if (n == 1) "copy" else "copies"} beyond four added." else "No card is owned more than four times."
                    }
                    QuickChip("+ Not in any deck, over ${money.format(5.0, whole = true)}", enabled = prices != null) {
                        val n = markUnusedToSell(collections, decks, 5.0, facts).second
                        onChange { markUnusedToSell(it, decks, 5.0, facts).first }
                        message = if (n > 0) "$n ${if (n == 1) "copy" else "copies"} added." else "No card outside your decks is worth over ${money.format(5.0, whole = true)}."
                    }
                }
            }
            message?.let { m -> item { Text(m, style = MaterialTheme.typography.labelMedium, color = colors.accentLight) } }
            if (rows.isEmpty()) item {
                Text(
                    "Nothing to sell yet. Add cards with a quick rule above, or with Sell… on a card's Where it is.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(rows, key = { it.key }) { row ->
                val on = row.key in tickedNow
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
                ) {
                    Checkbox(
                        checked = on,
                        onCheckedChange = { ticked = if (on) tickedNow - row.key else tickedNow + row.key },
                        colors = CheckboxDefaults.colors(checkedColor = colors.accent, checkmarkColor = colors.onAccent, uncheckedColor = colors.textMuted)
                    )
                    Column(Modifier.weight(1f).clickable { onOpenCard(row.name) }) {
                        Text(sellRowTitle(row), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(row.where, style = MaterialTheme.typography.labelMedium, color = colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    val usd = sellRowUsd(row, facts)
                    Text(
                        usd?.let { money.format(it, whole = it >= 10) } ?: if (prices == null) "…" else "—",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    IconButton(onClick = { onChange { unmarkToSell(it, row) } }) {
                        Icon(Icons.Filled.Close, contentDescription = "Not selling ${row.name}", tint = colors.textMuted, modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (rows.isNotEmpty()) item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(14.dp)
                ) {
                    Text("List them", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        LoanButton("TCGplayer mass entry", primary = false, modifier = Modifier.weight(1f)) {
                            clipboard.setText(AnnotatedString(tcgplayerMassEntry(rows, facts)))
                            message = "Copied. Paste it into TCGplayer's mass entry."
                        }
                        LoanButton("Cardmarket CSV", primary = false, modifier = Modifier.weight(1f)) {
                            csvSaver.launch("manabind-to-sell-${LocalDate.now()}.csv")
                        }
                    }
                    Text("Ticked = sold. Sold cards leave your collection and their pockets show as empty.", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                }
            }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            containerColor = colors.surface,
            title = { Text("Mark $soldCopies sold?", color = colors.accentLight) },
            text = { Text("${if (soldCopies == 1) "It leaves" else "They leave"} your collection, here and on your other devices, and ${if (soldCopies == 1) "its pocket shows" else "their pockets show"} as empty.", color = colors.textMuted) },
            confirmButton = {
                TextButton(onClick = {
                    val keys = tickedNow
                    val at = System.currentTimeMillis()
                    // The copies' history: each sold, from where it was (CopyHistory.kt).
                    CopyHistoryStore.record(soldRows.map { r ->
                        soldMove(at, MoveCard(r.name, r.scryfallId), r.qty, "from ${r.where}", r.lines.firstOrNull()?.placeId)
                    })
                    onChange { markSold(it, keys).collections }
                    message = "Sold $soldCopies ${if (soldCopies == 1) "card" else "cards"}."
                    ticked = emptySet()
                    confirming = false
                }) { Text("Mark sold", color = colors.accent) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel", color = colors.textMuted) } }
        )
    }
}

@Composable
private fun QuickChip(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = if (enabled) colors.textPrimary else colors.textDim,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface2)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

/** The cards to sell as a pull list (sellPullList, PullList.kt): place by place, ticked off as they're fetched. */
@Composable
private fun SellPullList(collections: List<Collection>, ticked: Set<String>, modifier: Modifier, onTicks: (Set<String>) -> Unit) {
    val colors = LocalAppColors.current
    val list = remember(collections) { sellPullList(collections) }
    val rows = list.groups.flatMap { it.rows }
    val pulled = pulledCopies(rows, ticked)
    fun toggle(r: PullRow) = onTicks(if (r.key in ticked) ticked - r.key else ticked + r.key)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text("$pulled of ${list.total} pulled", fontWeight = FontWeight.Bold, color = colors.textPrimary, modifier = Modifier.weight(1f))
                Text("${list.places} ${if (list.places == 1) "place" else "places"}", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
        }
        if (rows.isEmpty()) item { Text("Nothing to pull: no cards are marked to sell.", color = colors.textMuted) }
        items(list.groups, key = { it.key }) { g ->
            val of = g.rows.sumOf { it.qty }
            GroupCard(g.title, metaLine(listOfNotNull(g.detail.takeIf { g.kind == PullGroupKind.PLACE && it.isNotEmpty() }, "${pulledCopies(g.rows, ticked)} of $of")), false) {
                g.rows.forEach { r -> PullRowLine(r, r.key in ticked, r.hint, { toggle(r) }) }
            }
        }
        item {
            Box(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text("Ticks here only say you've fetched them. Tick them on the list once they're sold.", fontSize = 12.sp, color = colors.textMuted)
            }
        }
    }
}
