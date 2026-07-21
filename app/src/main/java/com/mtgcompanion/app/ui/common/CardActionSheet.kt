package com.mtgcompanion.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.TextPrimary

/** One row in a [CardActionSheet]. [destructive] tints it red, for a "Remove" action. */
data class CardMenuAction(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

/**
 * Long-press quick-action menu for a card. What [actions] are offered varies by tab (a raw search
 * result can only be added somewhere; an owned card can also be moved, removed, or copied), so
 * callers build the list themselves rather than this composable assuming a fixed set.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardActionSheet(cardName: String, actions: List<CardMenuAction>, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Text(
                cardName,
                style = MaterialTheme.typography.titleMedium,
                color = GoldLight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)
            )
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderColor))
            actions.forEach { action ->
                val tint = if (action.destructive) Color(0xFFD3402F) else Gold
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // Clear the sheet's own state first, then run the action — the action
                            // often opens a follow-up dialog (move/remove picker), and both live as
                            // separate state so order between them doesn't matter, but dismissing
                            // first avoids the sheet and the next dialog being visible at once.
                            onDismiss()
                            action.onClick()
                        }
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Icon(action.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                    Text(
                        action.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (action.destructive) tint else TextPrimary,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
        }
    }
}
