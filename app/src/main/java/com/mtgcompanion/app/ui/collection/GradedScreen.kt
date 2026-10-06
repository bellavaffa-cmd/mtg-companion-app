package com.mtgcompanion.app.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.GradedCard
import com.mtgcompanion.app.data.GradingCompany
import com.mtgcompanion.app.data.backToRaw
import com.mtgcompanion.app.data.gradedOf
import com.mtgcompanion.app.data.graderName
import com.mtgcompanion.app.data.markGraded
import com.mtgcompanion.app.data.placeTree
import com.mtgcompanion.app.data.placesOf
import com.mtgcompanion.app.data.rawSources
import com.mtgcompanion.app.data.removeGraded
import com.mtgcompanion.app.data.saveGraded
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.common.a11yHeading
import com.mtgcompanion.app.ui.common.rememberMoney
import com.mtgcompanion.app.ui.theme.LocalAppColors
import com.mtgcompanion.app.ui.theme.NumberStyle
import java.util.Locale
import java.util.UUID

/*
 * A graded copy, the web app's GradedPage (src/pages/GradedPage.tsx): the slab (grader and grade over
 * the card), who graded it (PSA, BGS, CGC, Other), the grade, the cert number, the user's value — card
 * prices are for raw copies — and where it's kept. A new one takes one of the card's raw copies out of
 * its binder (or is one not in the collection yet); an existing one can come out of its slab (a raw
 * copy again) or be removed. The logic is data/Graded.kt.
 */

/** "One that isn't in your collection" in the Which copy list. */
private const val NOT_HERE = "new"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradedScreen(
    /** The card a new graded copy is of; null when [gradedId] names one already marked. */
    cardName: String?,
    gradedId: String?,
    collections: List<Collection>,
    onBack: () -> Unit,
    onChange: (StorageChange) -> Unit
) {
    val colors = LocalAppColors.current
    val money = rememberMoney()
    val existing = remember(gradedId) { gradedId?.let { id -> gradedOf(collections).firstOrNull { it.id == id } } }
    val name = existing?.name ?: cardName.orEmpty()
    val places = placesOf(collections)
    val sources = remember(existing, name) { if (existing != null) emptyList() else rawSources(collections, name) }
    var from by remember { mutableStateOf(sources.firstOrNull()?.key ?: NOT_HERE) }
    val source = sources.firstOrNull { it.key == from }
    var company by remember { mutableStateOf(existing?.grader ?: GradingCompany.PSA) }
    var companyName by remember { mutableStateOf(existing?.companyName.orEmpty()) }
    var grade by remember { mutableStateOf(existing?.grade.orEmpty()) }
    var cert by remember { mutableStateOf(existing?.cert.orEmpty()) }
    var value by remember { mutableStateOf(existing?.valueUsd?.let { amount(money.toLocal(it)) } ?: "") }
    var placeId by remember { mutableStateOf(existing?.placeId ?: source?.line?.placeId ?: "") }
    var section by remember { mutableStateOf(existing?.section ?: source?.line?.section ?: "") }
    var looked by remember { mutableStateOf<Pair<String, String?>?>(null) }
    var confirm by remember { mutableStateOf<Boolean?>(null) } // true: out of its slab; false: remove
    val place = places.firstOrNull { it.id == placeId }

    // A slab that isn't one of the raw copies: its printing, from Scryfall by name.
    LaunchedEffect(existing, source, name) {
        if (existing == null && source == null && name.isNotEmpty() && looked == null) {
            runCatching { CardRepository().getByExactName(name) }.getOrNull()?.let { looked = it.id to it.displayImageUrl }
        }
    }

    val imageUrl = existing?.imageUrl ?: source?.imageUrl ?: looked?.second
    val scryfallId = existing?.scryfallId ?: source?.scryfallId ?: looked?.first.orEmpty()
    val valueUsd = value.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }?.let { money.toUsd(it) }
    val grader = graderName(company, companyName)

    fun save() {
        val card = (existing ?: GradedCard(id = UUID.randomUUID().toString(), scryfallId = scryfallId, name = name, createdAt = System.currentTimeMillis())).copy(
            scryfallId = scryfallId, name = name, imageUrl = imageUrl, company = company.name,
            companyName = if (company == GradingCompany.OTHER) companyName else null, grade = grade, cert = cert,
            valueUsd = valueUsd, placeId = placeId.ifEmpty { null }, section = if (placeId.isNotEmpty()) section.ifEmpty { null } else null
        )
        onChange { if (existing != null) saveGraded(it, card) else markGraded(it, source, card) }
        onBack()
    }

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Graded copy", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                        Text(name.ifEmpty { "Graded copy" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1, modifier = Modifier.a11yHeading())
                    }
                },
                navigationIcon = { BackButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth().background(colors.bg).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)) {
                Button(
                    onClick = { save() },
                    enabled = grade.isNotBlank() && scryfallId.isNotEmpty(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text(if (existing != null) "Save" else "Mark as graded", fontWeight = FontWeight.ExtraBold) }
            }
        }
    ) { padding ->
        if (name.isEmpty()) {
            Text("That graded copy isn't here any more.", color = colors.textMuted, modifier = Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // The slab: grader and grade over the card.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .width(190.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surface2)
                        .border(2.dp, colors.surface3, RoundedCornerShape(12.dp))
                        .padding(10.dp)
                        .semantics { contentDescription = "$grader ${grade.ifEmpty { "no grade yet" }}" }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(colors.accent).padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(grader, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = colors.onAccent, modifier = Modifier.weight(1f))
                        Text(grade.ifEmpty { "–" }, style = NumberStyle(26), color = colors.onAccent)
                    }
                    Box(Modifier.fillMaxWidth().aspectRatio(63f / 88f).clip(RoundedCornerShape(6.dp)).background(colors.surface3)) {
                        if (imageUrl != null) AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(14.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    GradingCompany.entries.forEach { c ->
                        val on = c == company
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (on) colors.accent else colors.surface2)
                                .clickable { company = c }
                                .semantics { selected = on }
                        ) {
                            Text(c.label, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, color = if (on) colors.onAccent else colors.textMuted)
                        }
                    }
                }
                if (company == GradingCompany.OTHER) {
                    GradedField("Grader") {
                        OutlinedTextField(companyName, { companyName = it.take(40) }, placeholder = { Text("Who graded it", color = colors.textDim) }, singleLine = true, colors = gradedFieldColors(), modifier = Modifier.fillMaxWidth())
                    }
                }
                GradedField("Grade") {
                    OutlinedTextField(
                        grade, { grade = it.take(8) }, placeholder = { Text("10", color = colors.textDim) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = gradedFieldColors(), modifier = Modifier.width(96.dp)
                    )
                }
                GradedField("Cert number") {
                    OutlinedTextField(cert, { cert = it.take(30) }, placeholder = { Text("On the slab's label", color = colors.textDim) }, singleLine = true, colors = gradedFieldColors(), modifier = Modifier.fillMaxWidth())
                }
                GradedField("Your value") {
                    OutlinedTextField(
                        value, { v -> value = v.filter { it.isDigit() || it == '.' || it == ',' }.take(10) },
                        placeholder = { Text("${money.currency.symbol.trim()} — raw price won't fit", color = colors.textDim) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = gradedFieldColors(), modifier = Modifier.fillMaxWidth()
                    )
                }
                if (existing == null) {
                    PickField("Which copy", from, sources.map { it.key to it.label } + (NOT_HERE to "One that isn't in your collection")) { key ->
                        from = key
                        sources.firstOrNull { it.key == key }?.line?.let { placeId = it.placeId; section = it.section.orEmpty() }
                    }
                }
                Text(
                    "Graded cards are kept apart from raw copies: they don't fill deck slots, and their value is what you enter, since card prices are for ungraded copies.",
                    fontSize = 12.sp, color = colors.textMuted, lineHeight = 17.sp
                )
            }

            PickField("Where", placeId, listOf("" to "No place yet") + placeTree(places).map { it.place.id to ("  ".repeat(it.depth) + it.place.name) }) {
                placeId = it
                section = ""
            }
            val sections = place?.sections.orEmpty()
            if (sections.isNotEmpty()) {
                PickField("Section", section, listOf("" to "Any section") + sections.map { it to it }) { section = it }
            }
            if (existing != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    LoanButton("Out of its slab", primary = false, modifier = Modifier.weight(1f)) { confirm = true }
                    LoanButton("Remove", primary = false, modifier = Modifier.weight(1f)) { confirm = false }
                }
            }
        }
    }

    confirm?.let { raw ->
        val g = existing ?: return@let
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = colors.surface,
            title = { Text(if (raw) "Out of its slab?" else "Remove this graded copy?", color = colors.accentLight) },
            text = {
                Text(
                    if (raw) "It goes back to being a raw copy, in the binder it came from and in its place — it fills deck slots again."
                    else "It leaves your collection — sold or gone. Its raw copy doesn't come back.",
                    color = colors.textMuted
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onChange { if (raw) backToRaw(it, g.id) else removeGraded(it, g.id) }
                    confirm = null
                    onBack()
                }) { Text(if (raw) "Make it raw" else "Remove", color = colors.accent) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel", color = colors.textMuted) } }
        )
    }
}

/** A label beside its field, as the slab's form lays them out. */
@Composable
private fun GradedField(label: String, field: @Composable () -> Unit) {
    val colors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 14.sp, color = colors.textMuted, modifier = Modifier.width(96.dp))
        Box(Modifier.weight(1f)) { field() }
    }
}

@Composable
private fun gradedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = LocalAppColors.current.accent,
    unfocusedBorderColor = LocalAppColors.current.border,
    focusedTextColor = LocalAppColors.current.textPrimary,
    unfocusedTextColor = LocalAppColors.current.textPrimary,
    cursorColor = LocalAppColors.current.accent
)

private fun amount(local: Double): String =
    if (local == Math.floor(local)) local.toLong().toString() else String.format(Locale.US, "%.2f", local)
