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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.window.Dialog
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

/**
 * One row in a [CardActionMenu]. [destructive] tints it red, for a "Remove" action. [description]
 * is a one-line hint under the label. Consecutive actions with the same [section] are grouped under
 * a small heading of that name.
 */
data class CardMenuAction(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val description: String? = null,
    val section: String? = null,
    val onClick: () -> Unit
)

/**
 * Long-press actions for a card. On a phone it's a sheet that slides up from the bottom —
 * thumb-reachable, and roomy enough to show which card it's acting on ([title], [subtitle],
 * [imageUrl]). On tablet and desktop layouts the same content opens as a centred menu, the way the
 * web app does, since a full-width sheet on a wide screen puts the actions far from the card.
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

    if (LocalLayoutSize.current.isWide) {
        Dialog(onDismissRequest = onDismiss) {
            KeepSystemBarsHidden()
            Column(
                Modifier
                    .a11yPane(title ?: "Card actions")
                    .width(420.dp)
                    .heightIn(max = 640.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(app.surface)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 16.dp)
            ) {
                MenuContent(title, subtitle, imageUrl, actions) { action ->
                    onDismiss()
                    action.onClick()
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
        scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 40.dp, height = 5.dp).clip(RoundedCornerShape(50)).background(app.surface3))
        }
    ) {
        KeepSystemBarsHidden()
        Column(Modifier.a11yPane(title ?: "Card actions").fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(bottom = 20.dp)) {
            MenuContent(title, subtitle, imageUrl, actions) { action ->
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    onDismiss()
                    action.onClick()
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MenuContent(
    title: String?,
    subtitle: String?,
    imageUrl: String?,
    actions: List<CardMenuAction>,
    onPick: (CardMenuAction) -> Unit
) {
    val app = LocalAppColors.current
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
    var lastSection: String? = null
    actions.forEachIndexed { index, action ->
        val section = action.section
        if (section != null && section != lastSection) {
            Text(
                section,
                style = MaterialTheme.typography.labelMedium,
                color = app.textMuted,
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = if (index == 0) 2.dp else 12.dp, bottom = 2.dp)
            )
        } else if (section == null && lastSection != null) {
            // Out of the grouped actions (to Remove, say): a little air rather than a heading.
            Spacer(Modifier.height(10.dp))
        }
        lastSection = section
        val tint = if (action.destructive) app.error else app.textPrimary
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { onPick(action) }
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (action.destructive) app.error.copy(alpha = 0.14f) else app.surface2),
                contentAlignment = Alignment.Center
            ) {
                Icon(action.icon, contentDescription = null, tint = if (action.destructive) app.error else app.accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(action.label, style = MaterialTheme.typography.bodyMedium, color = tint)
                if (!action.description.isNullOrBlank()) {
                    Text(action.description, style = MaterialTheme.typography.bodySmall, color = app.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
