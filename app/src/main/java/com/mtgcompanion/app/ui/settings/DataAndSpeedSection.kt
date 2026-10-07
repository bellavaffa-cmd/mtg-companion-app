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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.MtgCompanionApplication
import com.mtgcompanion.app.data.BACKUP_NOTE
import com.mtgcompanion.app.data.BackupFile
import com.mtgcompanion.app.data.BackupIo
import com.mtgcompanion.app.data.DataAndSpeed
import com.mtgcompanion.app.data.ParsedBackup
import com.mtgcompanion.app.data.QUICK_OPEN_MS
import com.mtgcompanion.app.data.RestoreMode
import com.mtgcompanion.app.data.backupFileName
import com.mtgcompanion.app.data.backupSummary
import com.mtgcompanion.app.data.lastSyncedLabel
import com.mtgcompanion.app.data.offlineLabel
import com.mtgcompanion.app.data.openLabel
import com.mtgcompanion.app.data.restoredMessage
import com.mtgcompanion.app.data.storageSummary
import com.mtgcompanion.app.data.summaryLines
import com.mtgcompanion.app.ui.collection.allCardEntries
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.UK)

/**
 * Settings › Data and speed: the collection's size, its card data kept for offline (the offline card
 * database, by name), how long All cards took to work out its list when it last opened, when it last
 * synced, and the backup — Save a backup and Restore, through the system's file picker. The words are
 * in DataAndSpeed.kt and the backup's rules in Backup.kt. The web app's DataAndSpeedSection.tsx.
 */
@Composable
fun DataAndSpeedSection() {
    val context = LocalContext.current
    val app = context.applicationContext as MtgCompanionApplication
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val io = remember { BackupIo(app, app.deckRepository, app.collectionRepository, app.settingsRepository) }
    val decks by app.deckRepository.decksFlow.collectAsState(initial = emptyList())
    val collections by app.collectionRepository.collectionsFlow.collectAsState(initial = emptyList())
    val account by app.supabaseSync.auth.account.collectAsState()
    val sync by app.supabaseSync.status.collectAsState()
    val offline by app.offlineCardRepository.status.collectAsState()

    // Copies, and the printings owned with whether the offline card database knows them: worked out off
    // the main thread, a big collection being tens of thousands of copies.
    val figures by produceState<Triple<Int, Int, Int>?>(null, collections, decks, offline.cardCount) {
        value = withContext(Dispatchers.Default) {
            val copies = storageSummary(collections, decks).total
            val printings = allCardEntries(collections, decks)
            val known = app.offlineCardRepository.savedNames(printings.map { it.name.trim().lowercase() })
            Triple(copies, printings.count { it.name.trim().lowercase() in known }, printings.size)
        }
    }
    val opened = remember { DataAndSpeed.lastAllCardsOpen() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    var busy by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<BackupFile?>(null) }
    var mode by remember { mutableStateOf(RestoreMode.MERGE) }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = "Making the backup…"
            val ok = io.save(uri)
            busy = null
            if (ok) notice = "Saved the backup." else problem = "Couldn't make the backup. Try again."
        }
    }
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = "Reading the backup…"
            when (val read = io.read(uri)) {
                is ParsedBackup.Ok -> { mode = RestoreMode.MERGE; pending = read.backup }
                is ParsedBackup.Refused -> problem = read.message
            }
            busy = null
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(14.dp)
    ) {
        FigureRow("Copies", figures?.let { String.format(Locale.UK, "%,d", it.first) } ?: "…")
        FigureRow("Card data saved for offline", figures?.let { offlineLabel(it.second, it.third) } ?: "…")
        FigureRow("Opening All cards", openLabel(opened), quick = opened != null && opened.ms < QUICK_OPEN_MS)
        FigureRow("Last synced", lastSyncedLabel(account != null, sync.lastSyncedAt, now))
    }
    if (figures != null && !offline.hasData) {
        Text(
            "Card data for offline comes from Settings › Offline Search.",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMuted
        )
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(14.dp)
    ) {
        Text("Backup", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.textPrimary)
        Text(BACKUP_NOTE, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Button(
                onClick = { notice = null; saver.launch(backupFileName(System.currentTimeMillis())) },
                enabled = busy == null,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                modifier = Modifier.weight(1f).height(40.dp)
            ) { Text("Save a backup", fontWeight = FontWeight.Bold) }
            Button(
                onClick = { notice = null; opener.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) },
                enabled = busy == null,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.surface2, contentColor = colors.textPrimary),
                modifier = Modifier.weight(1f).height(40.dp)
            ) { Text("Restore", fontWeight = FontWeight.SemiBold) }
        }
        (busy ?: notice)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = if (busy != null) colors.textMuted else colors.success) }
    }

    problem?.let { message ->
        AlertDialog(
            onDismissRequest = { problem = null },
            title = { Text("Can't restore this") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { problem = null }) { Text("OK") } }
        )
    }
    pending?.let { backup ->
        val summary = remember(backup) { backupSummary(backup) }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Restore this backup?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Made ${WHEN.format(Instant.ofEpochMilli(summary.createdAt).atZone(ZoneId.systemDefault()))} " +
                            if (summary.from == "web") "on the web" else "on the phone",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted
                    )
                    summaryLines(summary).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    ModeRow(
                        selected = mode == RestoreMode.MERGE,
                        title = "Merge with what's here",
                        detail = "Keeps everything here and adds back what's only in the backup. Where both have a card, the larger count stays."
                    ) { mode = RestoreMode.MERGE }
                    ModeRow(
                        selected = mode == RestoreMode.REPLACE,
                        title = "Replace with the backup",
                        detail = "Decks and binders in the backup go back to how they were, settings too. Ones made since stay."
                    ) { mode = RestoreMode.REPLACE }
                    if (account != null) Text("Signed in, it syncs to your account afterwards.", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    val chosen = mode
                    scope.launch {
                        busy = "Restoring…"
                        val photos = runCatching { io.restore(backup, chosen) }
                        busy = null
                        photos.onSuccess {
                            val done = restoredMessage(summary, it)
                            notice = if (account != null) "$done It syncs to your account now." else done
                        }.onFailure { problem = "Couldn't restore everything from the backup. Try again." }
                    }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun FigureRow(label: String, value: String, quick: Boolean = false) {
    val colors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = if (quick) colors.success else colors.textPrimary)
    }
}

@Composable
private fun ModeRow(selected: Boolean, title: String, detail: String, onClick: () -> Unit) {
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
