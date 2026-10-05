package com.mtgcompanion.app.ui.collection

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionType
import com.mtgcompanion.app.data.CsvMoney
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.Money
import com.mtgcompanion.app.data.Prices
import com.mtgcompanion.app.data.PrintingFacts
import com.mtgcompanion.app.data.ValueKind
import com.mtgcompanion.app.data.ValueRow
import com.mtgcompanion.app.data.ValueTotals
import com.mtgcompanion.app.data.lentCopies
import com.mtgcompanion.app.data.valueCsv
import com.mtgcompanion.app.data.valueGroups
import com.mtgcompanion.app.data.valueRows
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Value by place, the web app's ValueByPlacePage (src/pages/ValueByPlacePage.tsx): everything owned
 * in total, and place by place as bars — binders and boxes, the deck boxes, lent out, no place yet —
 * with a spreadsheet of every card (CSV, in the user's currency) and a report to print or save as PDF
 * (Android's print framework, as box labels print — printLabels in PlaceLabelScreen.kt). The logic is
 * data/ValueByPlace.kt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValueByPlaceScreen(collections: List<Collection>, decks: List<Deck>, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val money by Prices.money.collectAsState()
    val ids = remember(collections, decks) {
        (collections.filter { it.kind != CollectionType.WISHLIST }.flatMap { c -> c.entries.map { it.scryfallId } } +
            decks.flatMap { d -> d.cards.map { it.scryfallId } } +
            lentCopies(collections, decks).map { it.card.scryfallId }).distinct().sorted()
    }
    var facts by remember { mutableStateOf<Map<String, PrintingFacts>?>(null) }
    LaunchedEffect(ids) {
        facts = runCatching {
            CardRepository().getCardsByIds(ids).associate {
                it.id to PrintingFacts(it.set.orEmpty(), it.collectorNumber.orEmpty(), it.prices?.usd?.toDoubleOrNull(), it.prices?.usdFoil?.toDoubleOrNull())
            }
        }.getOrDefault(emptyMap())
    }
    val rows = remember(collections, decks, facts) { valueRows(collections, decks) { facts?.get(it) } }
    val totals = remember(rows, collections) { valueGroups(rows, collections) }
    val max = maxOf(1.0, totals.groups.maxOfOrNull { it.usd } ?: 0.0)
    var message by remember { mutableStateOf<String?>(null) }
    val csvSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val csv = valueCsv(rows, CsvMoney(money.currency.code, money.rate, money.currency.decimals))
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write((csv + "\n").toByteArray()) } != null }.getOrDefault(false)
            }
            message = if (ok) "Saved." else "That file couldn't be saved."
        }
    }

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("Value by place", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { BackButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(colors.bg).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(message ?: "For insurance or a move: every card, where it is and what it's worth.", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LoanButton("Spreadsheet", primary = false, enabled = rows.isNotEmpty(), modifier = Modifier.weight(1f)) {
                        csvSaver.launch("manabind-value-${LocalDate.now()}.csv")
                    }
                    LoanButton("PDF report", primary = true, enabled = rows.isNotEmpty(), modifier = Modifier.weight(1f)) {
                        printLabels(context, reportHtml(rows, totals, money), "Manabind collection report")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Everything you own", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                    Text(if (facts == null) "…" else money.format(totals.usd, whole = true), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                    Text("${String.format(Locale.UK, "%,d", totals.copies)} ${if (totals.copies == 1) "copy" else "copies"} · prices from today", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                }
            }
            items(totals.groups, key = { it.key }) { g ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(g.label + if (g.detail.isNotEmpty()) " · ${g.detail}" else "", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = colors.textPrimary, modifier = Modifier.weight(1f))
                        Text(money.format(g.usd, whole = true), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    }
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(colors.surface2)) {
                        Box(Modifier.fillMaxWidth((g.usd / max).toFloat().coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(if (g.kind == ValueKind.NONE) colors.textDim else colors.accent))
                    }
                    Text("${g.copies} ${if (g.copies == 1) "copy" else "copies"}", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                }
            }
            if (totals.groups.isEmpty()) item { Text("Nothing owned yet.", color = colors.textMuted) }
        }
    }
}

private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

/** The PDF report as a page to print: the places with their value, then every card. */
private fun reportHtml(rows: List<ValueRow>, totals: ValueTotals, money: Money): String {
    val date = LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK))
    val places = totals.groups.joinToString("") { g ->
        "<tr><td>${esc(listOf(g.detail, g.label).filter { it.isNotEmpty() }.joinToString(" › "))}</td><td>${g.copies}</td><td>${esc(money.format(g.usd))}</td></tr>"
    }
    val cards = rows.sortedWith(compareBy<ValueRow>({ it.where.lowercase() }, { it.name.lowercase() })).joinToString("") { r ->
        "<tr><td>${esc(r.name)}</td><td>${esc(listOf(r.set, r.number).filter { it.isNotEmpty() }.joinToString(" "))}</td><td>${if (r.foil) "Foil" else ""}</td><td>${r.qty}</td>" +
            "<td>${esc(r.where)}</td><td>${esc(r.spot)}</td><td>${r.unitUsd?.let { esc(money.format(it)) } ?: ""}</td><td>${r.unitUsd?.let { esc(money.format(it * r.qty)) } ?: ""}</td></tr>"
    }
    return """<!doctype html><html><head><meta charset="utf-8"><style>
        @page { margin: 12mm; }
        body { font: 10pt/1.35 sans-serif; color: #000; }
        h1 { font-size: 16pt; margin: 0 0 4pt; }
        p { margin: 0 0 10pt; }
        table { width: 100%; border-collapse: collapse; margin-bottom: 12pt; }
        th, td { border-bottom: 0.3mm solid #ccc; padding: 2pt 4pt; text-align: left; vertical-align: top; }
        tr { page-break-inside: avoid; }
        </style></head><body>
        <h1>Manabind collection report</h1>
        <p>${esc(date)} · ${totals.copies} copies · ${esc(money.format(totals.usd))} · prices from Scryfall, in ${esc(money.currency.code)}</p>
        <table><thead><tr><th>Place</th><th>Copies</th><th>Value</th></tr></thead><tbody>$places</tbody></table>
        <table><thead><tr><th>Card</th><th>Set</th><th>Finish</th><th>Qty</th><th>Place</th><th>Spot</th><th>Each</th><th>Total</th></tr></thead><tbody>$cards</tbody></table>
        </body></html>"""
}
