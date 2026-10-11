package com.mtgcompanion.app.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.pulledCopies
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.decks.GroupCard
import com.mtgcompanion.app.ui.decks.PullRowLine
import com.mtgcompanion.app.ui.decks.metaLine
import com.mtgcompanion.app.ui.theme.LocalAppColors

/**
 * "Walk this list": what a filtered place or the Storage tab found, as a pull list — place by place,
 * section by section, page and pocket — ticked off by hand or with the scanner's scan-to-tick
 * ([onScan]). The deck pull list's look (PullListScreen.kt); the list is WalkSession's (PlaceFilter.kt).
 * The web app's WalkListPage.tsx.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalkListScreen(onBack: () -> Unit, onScan: () -> Unit) {
    val colors = LocalAppColors.current
    val walk by WalkSession.walk.collectAsState()
    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        walk?.let { Text(it.title, style = MaterialTheme.typography.labelMedium, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        Text("Walk this list", style = MaterialTheme.typography.titleLarge)
                    }
                },
                navigationIcon = { BackButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        },
        bottomBar = {
            val w = walk
            if (w != null && w.list.total > 0) Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().background(colors.bg).padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Button(
                    onClick = onScan,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("Scan to tick", fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 8.dp))
                }
                if (w.ticked.isNotEmpty()) TextButton(onClick = { WalkSession.setTicked(emptySet()) }, modifier = Modifier.height(48.dp)) {
                    Text("Untick all", color = colors.textMuted)
                }
            }
        }
    ) { padding ->
        val w = walk
        if (w == null) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text("Nothing to walk: search or filter a place, or the Storage tab, then choose Walk this list.", color = colors.textMuted)
            }
            return@Scaffold
        }
        val rows = w.list.groups.flatMap { it.rows }
        val pulled = pulledCopies(rows, w.ticked)
        fun toggle(key: String) = WalkSession.setTicked(if (key in w.ticked) w.ticked - key else w.ticked + key)
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text("$pulled of ${w.list.total} found", fontWeight = FontWeight.Bold, color = colors.textPrimary, modifier = Modifier.weight(1f))
                    Text("${w.list.places} ${if (w.list.places == 1) "place" else "places"}", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                }
            }
            if (rows.isEmpty()) item { Text("No cards in places match.", color = colors.textMuted) }
            items(w.list.groups, key = { it.key }) { g ->
                GroupCard(g.title, metaLine(listOf(g.detail, "${pulledCopies(g.rows, w.ticked)} of ${g.rows.sumOf { it.qty }}")), false) {
                    g.rows.forEach { r -> PullRowLine(r, r.key in w.ticked, r.hint, { toggle(r.key) }) }
                }
            }
            item {
                Text("Ticks only say you've found them; nothing moves. The list is kept until you close the app.", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
            }
        }
    }
}
