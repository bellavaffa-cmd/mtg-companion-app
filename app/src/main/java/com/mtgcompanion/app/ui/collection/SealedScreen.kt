package com.mtgcompanion.app.ui.collection

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.DeckRepository
import com.mtgcompanion.app.data.Money
import com.mtgcompanion.app.data.PreconInfo
import com.mtgcompanion.app.data.PreconRepository
import com.mtgcompanion.app.data.SealedOption
import com.mtgcompanion.app.data.SealedProduct
import com.mtgcompanion.app.data.SealedSet
import com.mtgcompanion.app.data.SortSessionStore
import com.mtgcompanion.app.data.changeLabel
import com.mtgcompanion.app.data.importPreconDeck
import com.mtgcompanion.app.data.isPrecon
import com.mtgcompanion.app.data.newSealed
import com.mtgcompanion.app.data.openSealed
import com.mtgcompanion.app.data.openableBoxes
import com.mtgcompanion.app.data.openablePrecons
import com.mtgcompanion.app.data.placeTree
import com.mtgcompanion.app.data.placesOf
import com.mtgcompanion.app.data.preconDeckName
import com.mtgcompanion.app.data.removeSealed
import com.mtgcompanion.app.data.saveSealed
import com.mtgcompanion.app.data.sealedChange
import com.mtgcompanion.app.data.sealedLine
import com.mtgcompanion.app.data.sealedOf
import com.mtgcompanion.app.data.sealedOptions
import com.mtgcompanion.app.data.sealedTotalUsd
import com.mtgcompanion.app.data.sortForOpened
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.common.a11yHeading
import com.mtgcompanion.app.ui.common.rememberMoney
import com.mtgcompanion.app.ui.theme.LocalAppColors
import com.mtgcompanion.app.ui.theme.NumberStyle
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/*
 * Sealed product, the web app's SealedPage (src/pages/SealedPage.tsx): each box, bundle or precon with
 * how many, where it's kept, what was paid each and what it's worth now each (the value the user
 * entered — there are no prices for sealed product), with the change; the total at the top. "Open a
 * booster box" takes one off and starts sorting a new pile (the scanner's sort mode); "Open the
 * precon" makes it a deck with its list filled in. The logic is data/Sealed.kt.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SealedScreen(
    collections: List<Collection>,
    deckRepository: DeckRepository,
    onBack: () -> Unit,
    onChange: (StorageChange) -> Unit,
    /** The scanner sorting a new pile (SortPanel.kt) — the session is set before. */
    onSortPile: () -> Unit,
    onOpenDeck: (String) -> Unit
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val money = rememberMoney()
    val list = sealedOf(collections)
    val total = sealedTotalUsd(list)
    val boxes = openableBoxes(list)
    val precons = openablePrecons(list)
    var editing by remember { mutableStateOf<Pair<SealedProduct, Boolean>?>(null) }
    var adding by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf<Boolean?>(null) } // true: a precon
    var opening by remember { mutableStateOf<SealedProduct?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun openBox(p: SealedProduct) {
        val store = SortSessionStore(context)
        store.saveSession(sortForOpened(store.session(), p, store.piles(collections)))
        onChange { openSealed(it, p.id).first }
        opening = null
        onSortPile()
    }
    fun openPrecon(p: SealedProduct) {
        val file = p.preconFile ?: return
        busy = true
        message = null
        scope.launch {
            try {
                val deck = importPreconDeck(file, preconDeckName(p), deckRepository)
                onChange { openSealed(it, p.id).first }
                opening = null
                onOpenDeck(deck.id)
            } catch (e: Exception) {
                message = if (e is IllegalStateException) e.message ?: "Couldn't make the deck." else "Couldn't make the deck: ${e.message ?: "no connection"}"
                opening = null
            } finally {
                busy = false
            }
        }
    }
    fun choose(precon: Boolean) {
        val from = if (precon) precons else boxes
        if (from.size == 1) opening = from.first() else choosing = precon
    }

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("Sealed", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.a11yHeading()) },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = {
                    if (list.isNotEmpty()) Text(money.format(total, whole = true), style = NumberStyle(26), color = colors.accent, modifier = Modifier.padding(end = 16.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth().background(colors.bg).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp)) {
                Button(
                    onClick = { adding = true },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("+ Add sealed product", fontWeight = FontWeight.ExtraBold) }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (list.isNotEmpty()) item {
                Text("Values are the ones you entered — there are no prices for sealed product.", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
            }
            message?.let { m -> item { Text(m, style = MaterialTheme.typography.labelMedium, color = colors.accentLight) } }
            if (list.isEmpty()) item {
                Text(
                    "No sealed product yet. Add booster boxes, bundles and precons you keep sealed, with what you paid and what they're worth.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(list, key = { it.id }) { p ->
                SealedRow(p, money, sealedLine(p, collections) { money.format(it, whole = true) }) { editing = p to false }
            }
            if (list.isNotEmpty()) item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(14.dp)
                ) {
                    Text("Opening one?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary)
                    Text(
                        "\"Open\" takes it off the sealed list and starts a pile to sort, so every card lands in the right place. A precon becomes a deck with its list filled in.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.textMuted
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        LoanButton("Open a booster box", primary = true, enabled = boxes.isNotEmpty() && !busy, modifier = Modifier.weight(1f)) { choose(false) }
                        LoanButton(if (busy) "Making the deck…" else "Open the precon", primary = false, enabled = precons.isNotEmpty() && !busy, modifier = Modifier.weight(1f)) { choose(true) }
                    }
                }
            }
        }
    }

    if (adding) {
        AddSealedDialog(onDismiss = { adding = false }) { o ->
            adding = false
            editing = newSealed(o, UUID.randomUUID().toString(), System.currentTimeMillis()) to true
        }
    }
    editing?.let { (product, isNew) ->
        SealedDialog(
            product, isNew, collections, money,
            onDismiss = { editing = null },
            onSave = { p -> onChange { saveSealed(it, p) }; editing = null },
            onDelete = { onChange { removeSealed(it, product.id) }; editing = null },
            onOpen = { editing = null; opening = product }
        )
    }
    choosing?.let { precon ->
        AlertDialog(
            onDismissRequest = { choosing = null },
            containerColor = colors.surface,
            title = { Text(if (precon) "Open which precon?" else "Open which one?", color = colors.accentLight) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    (if (precon) precons else boxes).forEach { p ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { choosing = null; opening = p }.heightIn(min = 44.dp).padding(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text(p.name, color = colors.textPrimary, modifier = Modifier.weight(1f))
                            Text("×${p.count}", color = colors.textMuted)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosing = null }) { Text("Cancel", color = colors.textMuted) } }
        )
    }
    opening?.let { p ->
        val asDeck = p.isPrecon && !p.preconFile.isNullOrEmpty()
        AlertDialog(
            onDismissRequest = { if (!busy) opening = null },
            containerColor = colors.surface,
            title = { Text("Open ${p.name}?", color = colors.accentLight) },
            text = {
                Text(
                    if (asDeck) "One comes off the sealed list and becomes a deck with its list filled in."
                    else "One comes off the sealed list, and the scanner starts a pile to sort: scan each card and it says which pile it goes in.",
                    color = colors.textMuted
                )
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = { if (asDeck) openPrecon(p) else openBox(p) }) {
                    Text(if (busy) "Making the deck…" else "Open it", color = colors.accent)
                }
            },
            dismissButton = { TextButton(onClick = { opening = null }) { Text("Cancel", color = colors.textMuted) } }
        )
    }
}

/** One product: its name, count, place and price paid, and its value now with the change. */
@Composable
private fun SealedRow(p: SealedProduct, money: Money, line: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val pct = sealedChange(p)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(colors.surface3), contentAlignment = Alignment.Center) {
            Icon(if (p.isPrecon) Icons.Filled.Style else Icons.Filled.Inventory2, contentDescription = null, tint = colors.textMuted)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(p.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(line, fontSize = 12.sp, color = colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(p.valueUsd?.let { money.format(it, whole = true) } ?: "—", fontWeight = FontWeight.Bold, color = colors.textPrimary)
            Text(
                pct?.let { changeLabel(it) } ?: if (p.valueUsd != null) "value you entered" else "Add a value",
                fontSize = 12.sp,
                color = (pct ?: 0).let { if (it > 0) colors.success else if (it < 0) colors.cut else colors.textMuted }
            )
        }
    }
}

@Composable
private fun sealedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = LocalAppColors.current.accent,
    unfocusedBorderColor = LocalAppColors.current.border,
    focusedTextColor = LocalAppColors.current.textPrimary,
    unfocusedTextColor = LocalAppColors.current.textPrimary,
    cursorColor = LocalAppColors.current.accent
)

/** "+ Add sealed product": search Scryfall's sets and MTGJSON's precons, or name your own. */
@Composable
private fun AddSealedDialog(onDismiss: () -> Unit, onPick: (SealedOption) -> Unit) {
    val colors = LocalAppColors.current
    var query by remember { mutableStateOf("") }
    var sets by remember { mutableStateOf<List<SealedSet>>(emptyList()) }
    var precons by remember { mutableStateOf<List<PreconInfo>>(emptyList()) }
    var offline by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        runCatching { CardRepository().getSets().values.map { SealedSet(it.code, it.name, it.releasedAt, it.cardCount) } }
            .onSuccess { sets = it }.onFailure { offline = true }
        runCatching { PreconRepository().listCommanderPrecons() }.onSuccess { precons = it }.onFailure { offline = true }
    }
    val options = remember(query, sets, precons) { sealedOptions(query, sets, precons) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("Add sealed product", color = colors.accentLight) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(80) },
                    placeholder = { Text("Set or product name (Duskmourn, Blame Game)", color = colors.textDim) },
                    singleLine = true,
                    colors = sealedFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (offline) Text("Couldn't reach the set list — you can still add a product by name.", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (query.isBlank()) Text("Booster boxes, collector boxes, bundles and precons by their set; anything else by name.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    options.forEach { o ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surface2).clickable { onPick(o) }.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Text(if (o.kind == com.mtgcompanion.app.data.SealedKind.OTHER) "“${o.name}”" else o.name, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                            Text(o.detail, fontSize = 12.sp, color = colors.textMuted)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textMuted) } }
    )
}

private fun amountText(local: Double): String =
    if (local == Math.floor(local)) local.toLong().toString() else String.format(Locale.US, "%.2f", local)

/** A product's details: name, how many, where, paid each, value now each — and Open one, Delete. */
@Composable
private fun SealedDialog(
    product: SealedProduct,
    isNew: Boolean,
    collections: List<Collection>,
    money: Money,
    onDismiss: () -> Unit,
    onSave: (SealedProduct) -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit
) {
    val colors = LocalAppColors.current
    val places = placesOf(collections)
    var name by remember { mutableStateOf(product.name) }
    var count by remember { mutableStateOf(maxOf(1, product.count)) }
    var placeId by remember { mutableStateOf(product.placeId.orEmpty()) }
    var paid by remember { mutableStateOf(product.paidUsd?.let { amountText(money.toLocal(it)) } ?: "") }
    var value by remember { mutableStateOf(product.valueUsd?.let { amountText(money.toLocal(it)) } ?: "") }
    fun usd(text: String): Double? = text.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }?.let { money.toUsd(it) }
    val code = money.currency.code
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(if (isNew) "Add sealed product" else product.name, color = colors.accentLight) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(100) }, label = { Text("Name", color = colors.textMuted) },
                    singleLine = true, colors = sealedFieldColors(), modifier = Modifier.fillMaxWidth()
                )
                Text(product.sealedKind.label, style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                Text("How many", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { count = maxOf(1, count - 1) }, enabled = count > 1) { Icon(Icons.Filled.Remove, contentDescription = "One fewer", tint = colors.textPrimary) }
                    Text("$count", fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    IconButton(onClick = { count += 1 }) { Icon(Icons.Filled.Add, contentDescription = "One more", tint = colors.textPrimary) }
                }
                PickField("Where", placeId, listOf("" to "No place yet") + placeTree(places).map { it.place.id to ("  ".repeat(it.depth) + it.place.name) }) { placeId = it }
                OutlinedTextField(
                    value = paid, onValueChange = { v -> paid = v.filter { it.isDigit() || it == '.' || it == ',' }.take(10) },
                    label = { Text("Paid, each ($code)", color = colors.textMuted) }, placeholder = { Text("What one cost", color = colors.textDim) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = sealedFieldColors(), modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = value, onValueChange = { v -> value = v.filter { it.isDigit() || it == '.' || it == ',' }.take(10) },
                    label = { Text("Worth now, each ($code)", color = colors.textMuted) }, placeholder = { Text("What one sells for now", color = colors.textDim) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = sealedFieldColors(), modifier = Modifier.fillMaxWidth()
                )
                val on = product.valueAt?.takeIf { !isNew }?.let {
                    ", on " + DateTimeFormatter.ofPattern("d MMM", Locale.UK).format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
                }.orEmpty()
                Text(
                    "The value you entered$on — there are no prices for sealed product, so update it now and then.",
                    style = MaterialTheme.typography.labelSmall, color = colors.textMuted
                )
                if (!isNew) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        LoanButton(if (product.isPrecon && !product.preconFile.isNullOrEmpty()) "Open the precon" else "Open one", primary = false, modifier = Modifier.weight(1f), onClick = onOpen)
                        LoanButton("Delete", primary = false, modifier = Modifier.weight(1f), onClick = onDelete)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val valueUsd = usd(value)
                val changed = valueUsd != null && Math.round(valueUsd * 100) != Math.round((product.valueUsd ?: -1.0) * 100)
                onSave(
                    product.copy(
                        name = name.trim().ifEmpty { product.name }, count = count, placeId = placeId.ifEmpty { null }, paidUsd = usd(paid), valueUsd = valueUsd,
                        valueAt = if (valueUsd == null) null else if (changed) System.currentTimeMillis() else product.valueAt ?: System.currentTimeMillis()
                    )
                )
            }) { Text("Save", color = colors.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textMuted) } }
    )
}
