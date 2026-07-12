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
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

/** Pick a destination deck/binder to move [cardName] into. */
@Composable
fun MoveTargetDialog(
    cardName: String,
    targets: List<MoveTarget>,
    onPick: (MoveTarget) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Move $cardName", color = GoldLight, style = MaterialTheme.typography.titleMedium) },
        text = {
            if (targets.isEmpty()) {
                Text(
                    "No other decks or binders to move to. Create one first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 360.dp)) {
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
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = TextMuted) } }
    )
}
