package com.mtgcompanion.app.ui.collection

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtgcompanion.app.data.ImportResult
import com.mtgcompanion.app.data.parseCardList
import com.mtgcompanion.app.ui.common.PillChip
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_IMPORT_BYTES = 5 * 1024 * 1024

/** Where an import is up to. */
sealed interface ImportProgress {
    data object Idle : ImportProgress
    data class Working(val done: Int, val total: Int) : ImportProgress
    data class Done(val result: ImportResult, val binderName: String) : ImportProgress
    data class Failed(val message: String) : ImportProgress
}

/**
 * Adds a list of cards from another app — pasted, or a .txt/.csv file — to a binder. With
 * [askName], the cards go into a new binder named here.
 */
@Composable
fun ImportCardsDialog(
    title: String,
    askName: Boolean,
    progress: ImportProgress,
    onImport: (name: String?, text: String) -> Unit,
    onDismiss: () -> Unit,
    startInNewBinder: Boolean = true
) {
    // With [askName], cards go to a new binder or — "No binder" — the Unsorted pile.
    var newBinder by remember { mutableStateOf(startInNewBinder) }
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var fileError by remember { mutableStateOf<String?>(null) }
    val parsed = remember(text) { parseCardList(text) }
    val working = progress is ImportProgress.Working
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            fileError = null
            val read = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val out = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var tooBig = false
                        while (true) {
                            val n = stream.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            if (out.size() > MAX_IMPORT_BYTES) { tooBig = true; break }
                        }
                        if (tooBig) null else out.toString(Charsets.UTF_8.name())
                    }
                }.getOrNull()
            }
            if (read == null) fileError = "That file couldn't be read (5 MB at most)."
            else {
                text = read
                if (askName && newBinder && name.isBlank()) {
                    name = uri.lastPathSegment.orEmpty().substringAfterLast('/').substringAfterLast(':').substringBeforeLast('.')
                }
            }
        }
    }

    if (progress is ImportProgress.Done) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = colors.surface,
            title = { Text("Import finished") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val r = progress.result
                    Text("Added ${r.added} ${if (r.added == 1) "card" else "cards"}${if (r.foils > 0) " (${r.foils} foil)" else ""} to ${progress.binderName}.")
                    if (r.missing.isNotEmpty()) {
                        Text("Couldn't find ${if (r.missing.size == 1) "this one" else "these ${r.missing.size}"}:", color = colors.textMuted)
                        Text(
                            r.missing.joinToString("\n"),
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            color = colors.textPrimary,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp).clip(RoundedCornerShape(12.dp)).background(colors.surface2).verticalScroll(rememberScrollState()).padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = colors.accent) } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        containerColor = colors.surface,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Paste a list — one card per line, like \"4 Lightning Bolt\" or \"1 Sol Ring (CMR) 472 *F*\" — or choose a .txt or .csv export from Moxfield, ManaBox, Archidekt, Deckbox or TCGplayer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )
                if (askName) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillChip("No binder", !newBinder, { newBinder = false })
                        PillChip("New binder", newBinder, { newBinder = true })
                    }
                    if (!newBinder) {
                        Text(
                            "Cards go into Unsorted, under All cards. Move them into binders whenever you like.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textDim
                        )
                    }
                }
                if (askName && newBinder) {
                    OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, label = { Text("Binder name") }, singleLine = true, enabled = !working, modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Cards") },
                    placeholder = { Text("4 Lightning Bolt\n1 Sol Ring (CMR) 472 *F*") },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = colors.textPrimary),
                    enabled = !working,
                    minLines = 6,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { filePicker.launch(arrayOf("text/*", "application/csv", "application/octet-stream")) }, enabled = !working) {
                        Icon(Icons.Filled.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Choose a file", color = colors.textPrimary)
                    }
                    if (parsed.lines.isNotEmpty()) {
                        Text(
                            "${parsed.lines.size} lines · ${parsed.cardCount} cards" + if (parsed.skipped.isNotEmpty()) " · ${parsed.skipped.size} unreadable" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textDim
                        )
                    }
                }
                if (progress is ImportProgress.Working) {
                    Text("Finding cards… ${progress.done}/${progress.total}", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    LinearProgressIndicator(
                        progress = { if (progress.total == 0) 0f else progress.done.toFloat() / progress.total },
                        color = colors.accent,
                        trackColor = colors.border,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                (fileError ?: (progress as? ImportProgress.Failed)?.message)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !working && parsed.lines.isNotEmpty() && (!askName || !newBinder || name.isNotBlank()),
                onClick = { onImport(if (askName && !newBinder) null else name.trim(), text) }
            ) { Text(if (parsed.cardCount > 0) "Import ${parsed.cardCount} ${if (parsed.cardCount == 1) "card" else "cards"}" else "Import", color = colors.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !working) { Text("Cancel", color = colors.textMuted) } }
    )
}

/**
 * A binder as text for other apps: "Simple" is "4 Lightning Bolt" (everything reads it); "Exact
 * printing" adds "(CMR) 472" so the same art comes back. Copy it, share it, or save a .txt file.
 */
@Composable
fun ExportCollectionDialog(binderName: String, buildText: suspend (exact: Boolean) -> String, onDismiss: () -> Unit) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var exact by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(exact) {
        text = null
        text = runCatching { buildText(exact) }.getOrElse { message = it.message; buildText(false) }
    }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val content = text ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write((content + "\n").toByteArray()) } != null }.getOrDefault(false)
            }
            message = if (ok) "Saved." else "That file couldn't be saved."
        }
    }
    val ready = !text.isNullOrEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("Export binder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Paste it into Moxfield, Archidekt, ManaBox, TCGplayer — or this app on another device.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    PillChip("Simple", !exact, { exact = false })
                    PillChip("Exact printing", exact, { exact = true })
                }
                Text(
                    text ?: "Loading…",
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    color = colors.textPrimary,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(12.dp)).background(colors.surface2).verticalScroll(rememberScrollState()).padding(10.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val content = text ?: return@OutlinedButton
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, content).putExtra(Intent.EXTRA_SUBJECT, binderName), "Share binder"))
                    }, enabled = ready) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Share", color = colors.textPrimary)
                    }
                    OutlinedButton(onClick = { saver.launch(binderName.replace(Regex("[^\\w\\- ]+"), "").trim().ifBlank { "binder" } + ".txt") }, enabled = ready) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Save .txt", color = colors.textPrimary)
                    }
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textMuted) }
            }
        },
        confirmButton = {
            TextButton(enabled = ready, onClick = {
                clipboard.setText(AnnotatedString(text.orEmpty()))
                message = "Copied."
            }) { Text("Copy", color = colors.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = colors.textMuted) } }
    )
}
