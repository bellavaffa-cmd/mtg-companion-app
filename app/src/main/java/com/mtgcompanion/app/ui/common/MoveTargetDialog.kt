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
 * Pick a destination deck/binder to move [cardName] into. With [onNewBinder], the list ends in a
 * "New binder" row that names one and moves the card straight into it.
 */
@Composable
fun MoveTargetDialog(
    cardName: String,
    targets: List<MoveTarget>,
    onPick: (MoveTarget) -> Unit,
    onDismiss: () -> Unit,
    onNewBinder: ((String) -> Unit)? = null
) {
    var naming by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Move $cardName", color = GoldLight, style = MaterialTheme.typography.titleMedium) },
        text = {
            if (naming && onNewBinder != null) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.take(60) },
                    label = { Text("New binder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (targets.isEmpty() && onNewBinder == null) {
                Text(
                    "No other decks or binders to move to. Create one first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 360.dp)) {
                    if (onNewBinder != null) {
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
                    targets.forEach { target ->
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
                TextButton(onClick = { onNewBinder(newName.trim()) }, enabled = newName.isNotBlank()) { Text("Create & move", color = if (newName.isNotBlank()) Gold else TextMuted) }
            }
        },
        dismissButton = { TextButton(onClick = { if (naming) naming = false else onDismiss() }) { Text(if (naming) "Back" else "Cancel", color = TextMuted) } }
    )
}
