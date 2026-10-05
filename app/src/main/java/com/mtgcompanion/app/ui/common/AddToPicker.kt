package com.mtgcompanion.app.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mtgcompanion.app.data.GameMode
import com.mtgcompanion.app.data.sideboardChoice
import com.mtgcompanion.app.data.sideboardChoiceHint
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.scryfall.toArtCropUrl
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

/**
 * The one picker for putting cards into a deck or binder — adding, moving or copying, from
 * anywhere in the app. Titled by what's being done ([verb] [subject] to…).
 *
 * With both binders and decks on offer it asks which first, then lists only those: one long list
 * of the two mixed together is how a card meant for a deck ended up in a binder. Back goes up a step.
 *
 * - [canMakeBinder] / [canMakeDeck]: the list starts with "New binder…" / "New deck…", named here.
 * - [considering]: on the deck list, an "Into the deck / Considering" choice starting at this
 *   value; null leaves it out.
 * - [quantity]: a copies stepper; null hides it (the scanner's pile says how many).
 * - [canBeFoil]: on the binder list, a Foil switch.
 * - [startKind]: open straight on that kind's list (Back still reaches the first step).
 * - [offerSideboard]: the deck list's choice becomes "Into the deck / Sideboard / Considering" (or
 *   "Into the deck / Sideboard" without [considering]) when a deck there has a sideboard; picking
 *   Sideboard lists only those. A Limited deck's is called Pool. For flows whose change reads
 *   [AddToPick.sideboard] (AddToOps.addCard does).
 * - [printing]: the card being added — a "Printing: SET #number" row opens the printing picker,
 *   and the one chosen comes back as [AddToPick.printing]. Only for adding a new card, never for
 *   moving or copying copies that already exist.
 *
 * On a phone it's a sheet from the bottom, like the card actions sheet; on wider screens the same
 * content opens as a centred panel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPicker(
    verb: AddVerb,
    subject: String,
    targets: List<MoveTarget>,
    onPick: (AddToPick) -> Unit,
    onDismiss: () -> Unit,
    imageUrl: String? = null,
    canMakeBinder: Boolean = true,
    canMakeDeck: Boolean = true,
    considering: Boolean? = false,
    quantity: QuantityLimits? = quantityLimits(verb),
    canBeFoil: Boolean = false,
    startKind: SourceKind? = null,
    offerSideboard: Boolean = false,
    printing: ScryfallCard? = null
) {
    val app = LocalAppColors.current
    // Archived decks are put away: not offered (DeckFolders.kt).
    val offered = targets.filterNot { it.archived }
    if (LocalLayoutSize.current.isWide) {
        Dialog(onDismissRequest = onDismiss) {
            KeepSystemBarsHidden()
            Column(
                Modifier
                    .a11yPane(subject)
                    .width(440.dp)
                    .heightIn(max = 680.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(app.surface)
                    .padding(horizontal = 12.dp, vertical = 16.dp)
            ) {
                PickerContent(verb, subject, offered, imageUrl, canMakeBinder, canMakeDeck, considering, quantity, canBeFoil, startKind, offerSideboard, printing, onDismiss) { pick ->
                    onDismiss()
                    onPick(pick)
                }
            }
        }
        return
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = app.surface,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 40.dp, height = 5.dp).clip(RoundedCornerShape(50)).background(app.surface3))
        }
    ) {
        KeepSystemBarsHidden()
        Column(Modifier.a11yPane(subject).fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 20.dp)) {
            PickerContent(verb, subject, offered, imageUrl, canMakeBinder, canMakeDeck, considering, quantity, canBeFoil, startKind, offerSideboard, printing, onDismiss) { pick ->
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    onDismiss()
                    onPick(pick)
                }
            }
        }
    }
}

@Composable
private fun PickerContent(
    verb: AddVerb,
    subject: String,
    targets: List<MoveTarget>,
    imageUrl: String?,
    canMakeBinder: Boolean,
    canMakeDeck: Boolean,
    startConsidering: Boolean?,
    quantity: QuantityLimits?,
    canBeFoil: Boolean,
    startKind: SourceKind?,
    offerSideboard: Boolean,
    printing: ScryfallCard?,
    onDismiss: () -> Unit,
    onPick: (AddToPick) -> Unit
) {
    val app = LocalAppColors.current
    val kinds = kindsToChoose(targets, canMakeBinder, canMakeDeck)
    // The kind picked at the first step. Without a first step, the one kind there is.
    var kind by remember { mutableStateOf(startKind?.takeIf { kinds != null }) }
    val listKind: SourceKind? = kind ?: if (kinds != null) null else when {
        targets.isNotEmpty() -> targets.first().kind
        canMakeBinder -> SourceKind.BINDER
        canMakeDeck -> SourceKind.DECK
        else -> null
    }
    var naming by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newMode by remember { mutableStateOf(GameMode.DEFAULT) }
    var considering by remember { mutableStateOf(startConsidering ?: false) }
    var sideboard by remember { mutableStateOf(false) }
    // The printing to add, when the card being added was given: the one it came as until another is chosen.
    var chosenPrinting by remember { mutableStateOf(printing) }
    var choosingPrinting by remember { mutableStateOf(false) }
    var copies by remember { mutableIntStateOf(quantity?.default ?: 1) }
    var foil by remember { mutableStateOf(false) }

    val canGoBack = naming || (kinds != null && kind != null)
    val goBack: () -> Unit = { if (naming) { naming = false } else { kind = null } }
    BackHandler(enabled = canGoBack) { goBack() }

    val toDeck = listKind == SourceKind.DECK
    val intoConsidering = toDeck && startConsidering != null && considering
    // Sideboard is a choice only where a deck on offer has one — called Pool when they're all Limited.
    val sideboardOffered = toDeck && offerSideboard && targets.any { it.kind == SourceKind.DECK && it.hasSideboard }
    val sideboardPools = targets.filter { it.kind == SourceKind.DECK && it.hasSideboard }.map { it.pool }
    val intoSideboard = sideboardOffered && sideboard && !considering
    fun pick(target: MoveTarget, isNew: Boolean = false) = onPick(
        AddToPick(
            target = target,
            considering = intoConsidering,
            sideboard = intoSideboard && target.hasSideboard,
            printing = chosenPrinting?.takeIf { printing != null && it.id != printing.id },
            // Considering is a list of cards to think about, not of copies.
            quantity = if (intoConsidering || quantity == null) quantity?.default ?: 1 else copies,
            foil = !toDeck && canBeFoil && foil,
            isNew = isNew,
            newDeckMode = if (isNew && toDeck) newMode else null
        )
    )

    // Header: the card (when there's a picture of it), the title, and a way back up a step.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 12.dp)
    ) {
        if (canGoBack) {
            BackButton(onClick = goBack, modifier = Modifier.size(36.dp))
        } else if (imageUrl != null) {
            ArtImage(model = imageUrl.toArtCropUrl(), seed = subject, modifier = Modifier.size(width = 64.dp, height = 48.dp).clip(RoundedCornerShape(12.dp)))
        }
        Text(addToTitle(verb, subject), style = MaterialTheme.typography.titleMedium, color = app.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }

    when {
        kinds != null && listKind == null -> {
            // Step one: a binder or a deck?
            kinds.forEach { k ->
                val deck = k == SourceKind.DECK
                val count = targets.count { it.kind == k }
                PickerRow(
                    icon = if (deck) Icons.Filled.Style else Icons.Filled.Collections,
                    label = if (deck) "A deck" else "A binder",
                    detail = when {
                        count == 0 -> "Make a new one"
                        deck -> "$count ${if (count == 1) "deck" else "decks"}"
                        else -> "$count ${if (count == 1) "binder" else "binders"}"
                    },
                    onClick = { kind = k }
                )
            }
        }
        naming -> {
            Column(Modifier.padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.take(60) },
                    label = { Text(if (toDeck) "New deck name" else "New binder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (toDeck) GameModeDropdown(selected = newMode, onSelect = { newMode = it }, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = { pick(MoveTarget(listKind ?: SourceKind.BINDER, "", newName.trim()), isNew = true) },
                    enabled = newName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = app.accent, contentColor = app.onAccent),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Create & ${verb.label.lowercase()}") }
            }
        }
        else -> {
            // Into the sideboard, only the decks that have one are listed, and no new deck is offered.
            val shown = targets.filter { it.kind == listKind && (!intoSideboard || it.hasSideboard) }
            val offerNew = (toDeck && canMakeDeck && !intoSideboard) || (listKind == SourceKind.BINDER && canMakeBinder)
            if (toDeck && (startConsidering != null || sideboardOffered)) {
                // Into the deck, its sideboard, or onto its Considering list. 0, 1, 2 in that order.
                val part = when {
                    considering -> 2
                    intoSideboard -> 1
                    else -> 0
                }
                val parts = listOfNotNull(
                    0 to "Into the deck",
                    if (sideboardOffered) 1 to sideboardChoice(sideboardPools) else null,
                    if (startConsidering != null) 2 to "Considering" else null
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp).clip(RoundedCornerShape(12.dp)).background(app.surface3).padding(3.dp)) {
                    parts.forEach { (value, text) ->
                        Text(
                            text,
                            color = if (part == value) app.accent else app.textMuted,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (part == value) app.surface else Color.Transparent)
                                .clickable { considering = value == 2; sideboard = value == 1 }
                                .padding(vertical = 8.dp)
                        )
                    }
                }
                if (intoSideboard) {
                    Text(
                        sideboardChoiceHint(sideboardPools),
                        style = MaterialTheme.typography.labelMedium,
                        color = app.textDim,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                if (considering) {
                    Text(
                        "Cards you might play — kept beside the deck, not counted in it.",
                        style = MaterialTheme.typography.labelMedium,
                        color = app.textDim,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            val shownPrinting = chosenPrinting
            if (shownPrinting != null) {
                // Which printing goes in: the existing printing picker, in the card's zoom.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { choosingPrinting = true }
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Text(
                        "Printing: " + listOfNotNull(shownPrinting.set?.uppercase(), shownPrinting.collectorNumber?.let { "#$it" }).joinToString(" ").ifEmpty { "this one" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = app.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text("Change", style = MaterialTheme.typography.labelLarge, color = app.accent)
                }
                if (choosingPrinting) {
                    CardZoomDialog(
                        listOf(
                            ZoomCard(
                                imageUrl = shownPrinting.displayImageUrl,
                                cardName = shownPrinting.name,
                                backImageUrl = shownPrinting.backImageUrl,
                                onSelectPrinting = { chosen -> chosenPrinting = chosen; choosingPrinting = false }
                            )
                        ),
                        0
                    ) { choosingPrinting = false }
                }
            }
            val showStepper = quantity != null && quantity.max > 1 && !intoConsidering
            val showFoil = listKind == SourceKind.BINDER && canBeFoil
            if (showStepper || showFoil) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    if (showStepper && quantity != null) {
                        Text("Copies", style = MaterialTheme.typography.bodyMedium, color = app.textMuted)
                        IconButton(onClick = { copies = quantity.step(copies, -1) }, enabled = copies > 1) {
                            Icon(Icons.Filled.Remove, contentDescription = "One fewer", tint = if (copies > 1) app.accent else app.textDim)
                        }
                        Text("$copies", style = MaterialTheme.typography.titleMedium, color = app.textPrimary)
                        IconButton(onClick = { copies = quantity.step(copies, 1) }, enabled = copies < quantity.max) {
                            Icon(Icons.Filled.Add, contentDescription = "One more", tint = if (copies < quantity.max) app.accent else app.textDim)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (showFoil) {
                        Text("Foil", style = MaterialTheme.typography.bodyMedium, color = app.textMuted, modifier = Modifier.padding(end = 8.dp))
                        Switch(
                            checked = foil,
                            onCheckedChange = { foil = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = app.accent, checkedThumbColor = app.onAccent)
                        )
                    }
                }
            }
            if (shown.isEmpty() && !offerNew) {
                Text(
                    if (toDeck) "No decks to ${verb.label.lowercase()} to yet." else "No binders to ${verb.label.lowercase()} to yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = app.textMuted,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
                )
            }
            // The height is capped first and the scrolling goes inside it. The other way round the
            // list is cut off at the cap with nothing to scroll, and the places past it can't be reached.
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                if (offerNew) {
                    PickerRow(
                        icon = Icons.Filled.Add,
                        label = if (toDeck) "New deck…" else "New binder…",
                        accent = true,
                        onClick = { naming = true }
                    )
                }
                shown.forEach { target ->
                    PickerRow(
                        icon = if (target.kind == SourceKind.DECK) Icons.Filled.Style else Icons.Filled.Collections,
                        label = target.name,
                        detail = target.cards?.let { "$it ${if (it == 1) "card" else "cards"}" },
                        imageUrl = target.imageUrl,
                        onClick = { pick(target) }
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    if (!canGoBack) {
        Text(
            "Cancel",
            color = app.textMuted,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onDismiss).padding(vertical = 12.dp)
        )
    }
}

/** One row of the picker, in the card actions sheet's style: an icon tile (or a deck's commander) and a label. */
@Composable
private fun PickerRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    detail: String? = null,
    imageUrl: String? = null,
    accent: Boolean = false
) {
    val app = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        if (imageUrl != null) {
            ArtImage(model = imageUrl.toArtCropUrl(), seed = label, modifier = Modifier.size(width = 52.dp, height = 40.dp).clip(RoundedCornerShape(12.dp)))
        } else {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(app.surface2),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = app.accent, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = if (accent) app.accent else app.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = app.textMuted)
        }
    }
}
