package com.mtgcompanion.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

/** One row in a [CardActionMenu]. [destructive] tints it red, for a "Remove" action. */
data class CardMenuAction(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

/**
 * Long-press actions for a card, as a sheet that slides up from the bottom — thumb-reachable, and
 * roomy enough to show which card it's acting on ([title], [subtitle], [imageUrl]) when known.
 * What [actions] are offered varies by screen, so callers build the list themselves. The name is
 * kept from its earlier anchored-dropdown form so every call site switched over at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardActionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    actions: List<CardMenuAction>,
    title: String? = null,
    subtitle: String? = null,
    imageUrl: String? = null
) {
    if (!expanded) return
    val app = LocalAppColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = app.surface,
        scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 40.dp, height = 5.dp).clip(RoundedCornerShape(50)).background(app.surface3))
        }
    ) {
        KeepSystemBarsHidden()
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(bottom = 20.dp)) {
            if (title != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 12.dp)
                ) {
                    if (imageUrl != null) {
                        ArtImage(model = imageUrl, seed = title, modifier = Modifier.size(width = 64.dp, height = 48.dp).clip(RoundedCornerShape(12.dp)))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (!subtitle.isNullOrBlank()) {
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            actions.forEach { action ->
                val tint = if (action.destructive) app.error else app.textPrimary
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                onDismiss()
                                action.onClick()
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (action.destructive) app.error.copy(alpha = 0.14f) else app.surface2),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(action.icon, contentDescription = null, tint = if (action.destructive) app.error else app.accent, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(action.label, style = MaterialTheme.typography.bodyMedium, color = tint)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
