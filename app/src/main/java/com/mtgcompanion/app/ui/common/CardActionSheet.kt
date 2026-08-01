package com.mtgcompanion.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.TextPrimary

/** One row in a [CardActionMenu]. [destructive] tints it red, for a "Remove" action. */
data class CardMenuAction(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

/**
 * Long-press quick-action menu for a card, anchored directly to the card it was pressed on
 * (wrap the card composable in a `Box` and place this alongside it). What [actions] are offered
 * varies by tab (a raw search result can only be added somewhere; an owned card can also be
 * moved, removed, or copied), so callers build the list themselves.
 */
@Composable
fun CardActionMenu(expanded: Boolean, onDismiss: () -> Unit, actions: List<CardMenuAction>) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, modifier = Modifier.background(Surface)) {
        actions.forEach { action ->
            val tint = if (action.destructive) Color(0xFFD3402F) else Gold
            DropdownMenuItem(
                text = {
                    Text(
                        action.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (action.destructive) tint else TextPrimary
                    )
                },
                leadingIcon = { Icon(action.icon, contentDescription = null, tint = tint) },
                onClick = { onDismiss(); action.onClick() }
            )
        }
    }
}
