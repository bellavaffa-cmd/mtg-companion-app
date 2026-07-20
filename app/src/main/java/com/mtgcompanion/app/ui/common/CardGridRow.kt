package com.mtgcompanion.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Lays [items] out as a card grid inside a [LazyListScope] — a fixed column count, wrapped into
 * rows of plain [item]s — so a "grid" section can sit alongside other items (headers, search
 * fields) in a single scrolling LazyColumn rather than needing its own nested scrollable.
 */
fun <T> LazyListScope.cardGrid(
    items: List<T>,
    columns: Int = 3,
    key: (T) -> Any,
    itemContent: @Composable (T) -> Unit
) {
    items.chunked(columns).forEach { row ->
        item(key = row.joinToString("-") { key(it).toString() }) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { entry -> Box(modifier = Modifier.weight(1f)) { itemContent(entry) } }
                repeat(columns - row.size) { Box(modifier = Modifier.weight(1f)) }
            }
        }
    }
}
