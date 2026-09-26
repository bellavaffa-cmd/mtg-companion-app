package com.mtgcompanion.app.ui.badge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.ui.decks.Panel
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary

/**
 * Putting one of a deck's tokens onto an NFC e-paper badge, on a screen of its own.
 *
 * The screen is only the way in: everything below the title is [BadgeSheet] — the same thing the
 * remote opens over the table, and the same thing a deck's Tokens panel opens when you tap one.
 * There was briefly a second design here, with a row of name pills instead of swiping, which is
 * exactly how two screens for one job start drifting apart. There's one now, given more room here
 * because a whole screen has more to spend than a sheet over a game.
 */
@Composable
fun BadgeScreen(viewModel: BadgeViewModel, onBack: () -> Unit) {
    val deck by viewModel.deck.collectAsState()
    val deckName by viewModel.deckName.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, end = 16.dp, top = 4.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text("Token badge", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                if (deckName.isNotEmpty()) {
                    Text(deckName, style = MaterialTheme.typography.bodySmall, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Panel {
                BadgeSheet(
                    deck = deck,
                    ink = TextPrimary,
                    muted = TextMuted,
                    accent = Gold,
                    // A whole screen can afford a bigger badge than a sheet over a game can.
                    previewWidth = 220.dp
                )
            }
        }
    }
}
