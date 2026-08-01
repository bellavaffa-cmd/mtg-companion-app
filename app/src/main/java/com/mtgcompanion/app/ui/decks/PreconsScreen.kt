package com.mtgcompanion.app.ui.decks

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.data.PreconContents
import com.mtgcompanion.app.data.PreconInfo
import com.mtgcompanion.app.ui.common.CardActionMenu
import com.mtgcompanion.app.ui.common.CardMenuAction
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldDim
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.TextDim
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary

/**
 * Browse official Commander precons (exact decklists from MTGJSON, not just a set's mixed card
 * pool). Tap a precon to see its contents; long-press for the "Import as new deck" quick action —
 * same anchored-menu convention used for cards everywhere else in the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreconsScreen(viewModel: PreconsViewModel, onBack: () -> Unit, onImported: (String) -> Unit) {
    val state by viewModel.uiState.collectAsState()
    var query by remember { mutableStateOf("") }
    var viewing by remember { mutableStateOf<PreconInfo?>(null) }
    val context = LocalContext.current

    fun runImport(precon: PreconInfo) {
        viewModel.importAsDeck(
            precon,
            onImported = { deckId -> viewing = null; onImported(deckId) },
            onError = { message -> Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
        )
    }

    val filtered = remember(state.precons, query) {
        val q = query.trim()
        if (q.isBlank()) state.precons
        else state.precons.filter { it.name.contains(q, ignoreCase = true) || it.setCode.contains(q, ignoreCase = true) }
    }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text("PRECON DECKS", color = GoldLight, style = MaterialTheme.typography.labelLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Gold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().background(Bg).padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search precons", color = TextDim) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextMuted) },
                shape = RoundedCornerShape(2.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Gold,
                    unfocusedBorderColor = BorderColor,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Gold,
                    focusedContainerColor = Surface,
                    unfocusedContainerColor = Surface
                ),
                modifier = Modifier.fillMaxWidth().padding(20.dp)
            )

            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Gold)
                }
                state.error != null -> Text(
                    state.error ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                filtered.isEmpty() -> Text(
                    "No precons match \"$query\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.fileName }) { precon ->
                        PreconRow(precon, onClick = { viewing = precon }, onImport = { runImport(precon) })
                    }
                }
            }
        }
    }

    viewing?.let { precon ->
        PreconContentsDialog(
            viewModel = viewModel,
            precon = precon,
            onDismiss = { viewing = null },
            onImport = { runImport(precon) }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PreconRow(precon: PreconInfo, onClick: () -> Unit, onImport: () -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Surface)
                .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(4.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuExpanded = true }
                )
                .padding(12.dp)
        ) {
            Icon(Icons.Filled.Style, contentDescription = null, tint = GoldDim, modifier = Modifier.size(32.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(precon.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Text(
                    precon.setCode.uppercase() + (precon.releaseDate?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted
                )
            }
        }
        CardActionMenu(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            actions = listOf(CardMenuAction("Import as new deck", Icons.Filled.Add, onClick = onImport))
        )
    }
}

@Composable
private fun PreconContentsDialog(
    viewModel: PreconsViewModel,
    precon: PreconInfo,
    onDismiss: () -> Unit,
    onImport: () -> Unit
) {
    var contents by remember(precon) { mutableStateOf<PreconContents?>(null) }
    var error by remember(precon) { mutableStateOf<String?>(null) }

    LaunchedEffect(precon) {
        try {
            contents = viewModel.loadContents(precon)
        } catch (e: Exception) {
            error = "Couldn't load this precon's contents."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text(precon.name, color = GoldLight, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                val current = contents
                when {
                    error != null -> Text(error ?: "", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    current == null -> Box(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = Gold, modifier = Modifier.size(24.dp)) }
                    else -> {
                        if (current.commander.isNotEmpty()) {
                            Text("COMMANDER", style = MaterialTheme.typography.labelMedium, color = TextDim)
                            current.commander.forEach {
                                Text(it.name, style = MaterialTheme.typography.bodyMedium, color = GoldLight)
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                        Text(
                            "${current.cards.sumOf { it.quantity }} CARDS",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextDim
                        )
                        Spacer(Modifier.height(4.dp))
                        current.cards.sortedBy { it.name }.forEach { card ->
                            Text(
                                "${card.quantity}× ${card.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onImport,
                enabled = contents != null,
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
            ) { Text("IMPORT AS DECK", color = Bg) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CLOSE", color = TextMuted) }
        }
    )
}
