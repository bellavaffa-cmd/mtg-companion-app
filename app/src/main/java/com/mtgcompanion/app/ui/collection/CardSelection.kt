package com.mtgcompanion.app.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.ui.theme.LocalAppColors

// Picking several cards at once in a binder or on All cards: press and hold a card to start, tap
// others to add or drop them, then act on them all from the bar along the bottom.

/** The top bar while cards are picked: how many, select all, and stop. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(count: Int, total: Int, onSelectAll: () -> Unit, onClear: () -> Unit) {
    val colors = LocalAppColors.current
    TopAppBar(
        title = { Text("$count selected", style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            IconButton(onClick = onClear) { Icon(Icons.Filled.Close, contentDescription = "Stop selecting", tint = colors.accent) }
        },
        actions = {
            if (count < total) {
                IconButton(onClick = onSelectAll) { Icon(Icons.Filled.SelectAll, contentDescription = "Select all", tint = colors.textPrimary) }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
    )
}

/** One thing to do with the picked cards. */
data class SelectionAction(val label: String, val icon: ImageVector, val destructive: Boolean = false, val onClick: () -> Unit)

/** The actions for the picked cards, along the bottom of the screen. */
@Composable
fun SelectionActionBar(actions: List<SelectionAction>) {
    val colors = LocalAppColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surface2)
            .padding(6.dp)
    ) {
        actions.forEach { action ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable(onClick = action.onClick).padding(vertical = 8.dp)
            ) {
                val tint = if (action.destructive) colors.error else colors.accent
                Icon(action.icon, contentDescription = null, tint = tint)
                Text(action.label, style = MaterialTheme.typography.labelSmall, color = if (action.destructive) colors.error else colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The tick on a picked card — an empty ring on the others while picking. */
@Composable
fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(if (selected) colors.accent else Color.Black.copy(alpha = 0.45f))
            .border(2.dp, if (selected) colors.accent else Color.White.copy(alpha = 0.85f), CircleShape)
    ) {
        if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = colors.onAccent, modifier = Modifier.size(18.dp))
    }
}
