package com.mtgcompanion.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary

/** A deck or binder a card can be moved into. */
data class MoveTarget(val kind: SourceKind, val id: String, val name: String)

/**
 * Which kind of place [MoveTargetDialog] asks about first — null when there's only one kind to offer,
 * so there's nothing to ask. A "new binder" row counts as binders being on offer.
 */
fun kindsToChoose(targets: List<MoveTarget>, canMakeBinder: Boolean): List<SourceKind>? {
    val binders = canMakeBinder || targets.any { it.kind == SourceKind.BINDER }
    val decks = targets.any { it.kind == SourceKind.DECK }
    return if (binders && decks) listOf(SourceKind.BINDER, SourceKind.DECK) else null
}

/**
 * Pick a destination deck/binder to move [cardName] into. With [onNewBinder], the list ends in a
 * "New binder" row that names one and moves the card straight into it.
 *
 * With both binders and decks to go to, it asks which first and then lists only those: one long
 * list of the two mixed together is how a card meant for a deck ended up in a binder.
 */
@Composable
fun MoveTargetDialog(
    cardName: String,
    targets: List<MoveTarget>,
    onPick: (MoveTarget) -> Unit,
    onDismiss: () -> Unit,
    onNewBinder: ((String) -> Unit)? = null,
    title: String? = null
) {
    var naming by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val kinds = kindsToChoose(targets, canMakeBinder = onNewBinder != null)
    // The kind picked at the first step; stays null (showing everything) when there's no first step.
    var kind by remember { mutableStateOf<SourceKind?>(null) }
    val choosing = kinds != null && kind == null
    val shown = if (kind == null) targets else targets.filter { it.kind == kind }
    val offerNewBinder = onNewBinder != null && kind != SourceKind.DECK
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text(title ?: "Move $cardName", color = GoldLight, style = MaterialTheme.typography.titleMedium) },
        text = {
            if (choosing) {
                Column {
                    kinds.orEmpty().forEach { k ->
                        val deck = k == SourceKind.DECK
                        val count = targets.count { it.kind == k }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { kind = k }
                                .padding(vertical = 14.dp)
                        ) {
                            Icon(if (deck) Icons.Filled.Style else Icons.Filled.Collections, contentDescription = null, tint = Gold, modifier = Modifier.size(22.dp))
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(if (deck) "A deck" else "A binder", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                                Text(
                                    when {
                                        deck -> "$count ${if (count == 1) "deck" else "decks"}"
                                        count == 0 -> "Make a new one"
                                        else -> "$count ${if (count == 1) "binder" else "binders"}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }
            } else if (naming && onNewBinder != null) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.take(60) },
                    label = { Text("New binder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (shown.isEmpty() && !offerNewBinder) {
                Text(
                    "No other decks or binders to move to. Create one first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            } else {
                // The height is capped first and the scrolling goes inside it. The other way round the
                // list is cut off at the cap with nothing to scroll, and the decks past it can't be reached.
                Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    if (kind != null) {
                        Text(
                            if (kind == SourceKind.DECK) "Decks" else "Binders",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMuted,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                    if (offerNewBinder) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { naming = true }
                                .padding(vertical = 12.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = Gold, modifier = Modifier.size(20.dp))
                            Text("New binder…", style = MaterialTheme.typography.bodyMedium, color = Gold, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                    shown.forEach { target ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(target) }
                                .padding(vertical = 12.dp)
                        ) {
                            Icon(
                                if (target.kind == SourceKind.DECK) Icons.Filled.Style else Icons.Filled.Collections,
                                contentDescription = if (target.kind == SourceKind.DECK) "Deck" else "Binder",
                                tint = Gold,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                target.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (naming && onNewBinder != null) {
                TextButton(onClick = { onNewBinder(newName.trim()) }, enabled = newName.isNotBlank()) { Text(if (title != null) "Create" else "Create & move", color = if (newName.isNotBlank()) Gold else TextMuted) }
            }
        },
        dismissButton = {
            // Back out one step at a time: the new binder's name, then the list, then the dialog.
            val canGoBack = naming || (kinds != null && kind != null)
            TextButton(onClick = { if (naming) naming = false else if (kinds != null && kind != null) kind = null else onDismiss() }) {
                Text(if (canGoBack) "Back" else "Cancel", color = TextMuted)
            }
        }
    )
}
