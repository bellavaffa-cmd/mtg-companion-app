package com.mtgcompanion.app.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mtgcompanion.app.data.CardViewMode
import com.mtgcompanion.app.ui.theme.Gold

/**
 * List or grid, on the card list itself rather than only in Settings › Card Display: shows what a
 * tap switches to, and saves it as that list's setting.
 */
@Composable
fun ViewModeButton(mode: CardViewMode, onChange: (CardViewMode) -> Unit, modifier: Modifier = Modifier) {
    val grid = mode == CardViewMode.GRID
    IconButton(onClick = { onChange(if (grid) CardViewMode.LIST else CardViewMode.GRID) }, modifier = modifier) {
        Icon(
            if (grid) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
            contentDescription = if (grid) "Show as a list" else "Show as a grid",
            tint = Gold
        )
    }
}
