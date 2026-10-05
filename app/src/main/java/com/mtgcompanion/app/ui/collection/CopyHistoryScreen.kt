package com.mtgcompanion.app.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.CopyHistoryStore
import com.mtgcompanion.app.data.CopyMove
import com.mtgcompanion.app.data.dayOf
import com.mtgcompanion.app.data.moveDay
import com.mtgcompanion.app.data.movesOfCard
import com.mtgcompanion.app.ui.common.BackButton
import com.mtgcompanion.app.ui.theme.LocalAppColors

/**
 * A card's history, the web app's CopyHistoryPage (src/pages/CopyHistoryPage.tsx): every move of its
 * copies made on this phone — added, put away, moved, pulled into a deck, put back, lent, back,
 * checked — newest first, kept for a year. The log is data/CopyHistory.kt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopyHistoryScreen(cardName: String, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    remember { CopyHistoryStore.init(context) }
    val log by CopyHistoryStore.moves.collectAsState()
    val moves = remember(log, cardName) { movesOfCard(log, cardName) }
    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(cardName, style = MaterialTheme.typography.labelMedium, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("History", style = MaterialTheme.typography.titleLarge)
                    }
                },
                navigationIcon = { BackButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (moves.isEmpty()) Text("No moves yet. Putting it away, moving it, lending it — each shows here.", color = colors.textMuted)
            else MoveList(moves, named = false)
            Text(
                "Kept on this phone for a year. Also on each place’s page: “Recent moves”.",
                style = MaterialTheme.typography.bodySmall, color = colors.textMuted,
                modifier = Modifier.padding(top = 18.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(12.dp)
            )
        }
    }
}

/** Moves as a timeline, newest first; [named]: with each card's name (a place's Recent moves). */
@Composable
fun MoveList(moves: List<CopyMove>, named: Boolean) {
    val colors = LocalAppColors.current
    val today = dayOf(System.currentTimeMillis())
    Column {
        moves.forEachIndexed { i, m ->
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.width(14.dp)) {
                    Box(Modifier.padding(top = 3.dp).size(12.dp).clip(CircleShape).background(if (i == 0) colors.accent else colors.surface3))
                    if (i < moves.size - 1) Box(Modifier.padding(start = 5.dp).width(2.dp).height(44.dp).background(colors.surface3))
                }
                Column(Modifier.weight(1f).padding(start = 12.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(moveDay(dayOf(m.at), today), style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                    Text((if (named) "${m.name}: " else "") + m.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    m.detail?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = colors.textMuted) }
                }
            }
        }
    }
}
