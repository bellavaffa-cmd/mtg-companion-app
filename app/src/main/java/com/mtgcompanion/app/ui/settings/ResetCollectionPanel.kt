package com.mtgcompanion.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.MtgCompanionApplication
import com.mtgcompanion.app.data.BackupIo
import com.mtgcompanion.app.data.CollectionReset
import com.mtgcompanion.app.data.RESET_DONE
import com.mtgcompanion.app.data.RESET_NOTHING
import com.mtgcompanion.app.data.RESET_SYNC_NOTE
import com.mtgcompanion.app.data.RESET_WORD
import com.mtgcompanion.app.data.ResetScope
import com.mtgcompanion.app.data.backupFileName
import com.mtgcompanion.app.data.resetConfirmed
import com.mtgcompanion.app.data.resetCounts
import com.mtgcompanion.app.data.resetCountsText
import com.mtgcompanion.app.ui.common.AddToSnackbarHost
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings › Data and speed › Danger zone › Reset collection: choose what goes, see how much, save a
 * backup first, type RESET, then Reset — with Undo for a few seconds after ([ResetUndoHost]). The rules
 * and words are in ResetCollection.kt; Undo and the sync in CollectionReset.kt. The web app's
 * ResetCollectionPanel.tsx.
 */
@Composable
fun ResetCollectionPanel() {
    val context = LocalContext.current
    val app = context.applicationContext as MtgCompanionApplication
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val io = remember { BackupIo(app, app.deckRepository, app.collectionRepository, app.settingsRepository) }
    val decks by app.deckRepository.decksFlow.collectAsState(initial = emptyList())
    val collections by app.collectionRepository.collectionsFlow.collectAsState(initial = emptyList())
    val account by app.supabaseSync.auth.account.collectAsState()

    var chosen by remember { mutableStateOf(ResetScope.CARDS) }
    var typed by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var backedUp by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val counts by produceState<String?>(null, decks, collections, chosen) {
        value = withContext(Dispatchers.Default) { resetCountsText(resetCounts(decks, collections, chosen)) }
    }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            saving = true
            val ok = io.save(uri)
            saving = false
            if (ok) backedUp = true else problem = "Couldn't make the backup. Try again."
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(14.dp)
    ) {
        Text("Danger zone", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.error)
        Text(
            "Reset collection removes what you choose from this device and, signed in, from your account and other devices.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted
        )
        ResetScope.entries.forEach { s ->
            ScopeRow(selected = chosen == s, title = s.title, detail = s.detail) { chosen = s }
        }
        Text(
            "Removes: ${counts ?: "…"}",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary
        )
        Button(
            onClick = { problem = null; saver.launch(backupFileName(System.currentTimeMillis())) },
            enabled = !saving,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
            modifier = Modifier.fillMaxWidth().height(44.dp)
        ) {
            Text(
                when {
                    backedUp -> "✓ Backup saved"
                    saving -> "Making the backup…"
                    else -> "Save a backup first"
                },
                fontWeight = FontWeight.Bold
            )
        }
        problem?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = colors.error) }
        OutlinedTextField(
            value = typed,
            onValueChange = { typed = it },
            singleLine = true,
            label = { Text("Type $RESET_WORD to confirm") },
            placeholder = { Text(RESET_WORD) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                val what = chosen
                typed = ""
                backedUp = false
                scope.launch { app.collectionReset.reset(what) }
            },
            enabled = resetConfirmed(typed) && counts != null && counts != RESET_NOTHING,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.error,
                contentColor = Color.White,
                disabledContainerColor = colors.error.copy(alpha = 0.15f),
                disabledContentColor = colors.error.copy(alpha = 0.6f)
            ),
            modifier = Modifier.fillMaxWidth().height(44.dp)
        ) { Text("Reset", fontWeight = FontWeight.Bold) }
        if (account != null) Text(RESET_SYNC_NOTE, style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
    }
}

@Composable
private fun ScopeRow(selected: Boolean, title: String, detail: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surface2).clickable(onClick = onClick).padding(10.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = colors.accent))
        Column(Modifier.weight(1f).padding(start = 4.dp, top = 2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
    }
}

/**
 * The Undo snackbar after Reset collection, on every screen while it's offered. It goes when the reset
 * is committed — Undo's time up, or the app left ([CollectionReset.commitNow]).
 */
@Composable
fun ResetUndoHost(reset: CollectionReset, modifier: Modifier = Modifier) {
    val pending by reset.pending.collectAsState()
    val host = remember { SnackbarHostState() }
    LaunchedEffect(pending) {
        if (pending == null) {
            host.currentSnackbarData?.dismiss()
            return@LaunchedEffect
        }
        val result = host.showSnackbar(RESET_DONE, actionLabel = "Undo", duration = SnackbarDuration.Indefinite)
        if (result == SnackbarResult.ActionPerformed) reset.undo()
    }
    AddToSnackbarHost(host, modifier)
}
