package com.mtgcompanion.app.tester

import com.mtgcompanion.app.ui.common.BackButton
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtgcompanion.app.data.supabase.SupabaseSync
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val LOG_KINDS = listOf("all" to "All", "screen" to "Screens", "tap" to "Taps", "net" to "Network", "sync" to "Sync", "remote" to "Remote", "scan" to "Scanner", "app" to "App")

/**
 * The tester app's own screen: reports and ideas, this build's checklist, the switches, what sync
 * last did, the second account, and the trail of what the app has been doing. Reached by holding
 * the bug button or from Settings; it doesn't exist in the real app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TesterToolsScreen(supabaseSync: SupabaseSync, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val flags = Tester.flags
    val changes by flags.changes.collectAsState()
    val waiting by Tester.reports.waiting.collectAsState()
    val lastResult by Tester.reports.lastResult.collectAsState()
    val status by supabaseSync.status.collectAsState()
    val account by supabaseSync.auth.account.collectAsState()
    val parked by Tester.accounts.parked.collectAsState()
    val log by TesterLog.flow.collectAsState()
    var logKind by remember { mutableStateOf("all") }
    var accountBusy by remember { mutableStateOf(false) }
    var accountLine by remember { mutableStateOf<String?>(null) }
    var confirmCrash by remember { mutableStateOf(false) }
    val time = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text("Tester tools", color = GoldLight) },
                navigationIcon = { BackButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Tester ${Tester.LABEL}\n${Tester.device()}", color = TextMuted, style = MaterialTheme.typography.bodySmall)

            Section("Tell the developer")
            Text("Shake the phone or tap the bug button on any screen to report a problem with a picture of it. For anything else:", color = TextMuted, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { Tester.startReport("idea") }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)) { Text("Suggest an idea", color = Bg) }
                OutlinedButton(onClick = { Tester.startReport("bug") }) { Text("Report a problem", color = TextPrimary) }
            }
            OutlinedButton(onClick = { Tester.showNotes() }) { Text("What's new in this build", color = TextPrimary) }
            Text(
                when {
                    waiting == 0 -> lastResult ?: "No reports waiting to send."
                    else -> "$waiting waiting to send. ${lastResult.orEmpty()}"
                },
                color = TextMuted, style = MaterialTheme.typography.bodySmall
            )
            if (waiting > 0) OutlinedButton(onClick = { Tester.reports.flush() }) { Text("Send now", color = TextPrimary) }

            Section("Switches")
            // `changes` is read so each row redraws when its switch flips.
            if (changes >= 0) {
                Toggle("Tester banner", "The build number at the top of every screen, so pictures show which build they're from", flags.banner) { flags.banner = it }
                Toggle("Bug button", "The button at the edge of the screen. Hold it to come here", flags.bugButton) { flags.bugButton = it }
                Toggle("Shake to report", "Shake the phone to report a problem", flags.shakeToReport) { flags.shakeToReport = it }
                Toggle("Scanner readout", "On the scanner: what the last scan read and how long it took, with a button for a wrong one", flags.scanDebug) { flags.scanDebug = it }
                Toggle("Ask how features went", "A thumbs up or down after using the scanner, the life counter or a remote, once a build", flags.feedbackPrompts) { flags.feedbackPrompts = it }
                Toggle("Note taps in the log", "Where on the screen each tap landed, to follow what led to a problem", flags.logTaps) { flags.logTaps = it }
                TESTER_FEATURES.forEach { (name, label) ->
                    Toggle(label, "Unfinished: off unless you turn it on", flags.isOn(name)) { flags.set(name, it) }
                }
            }

            Section("Sync")
            Text(
                buildString {
                    append(if (account == null) "Signed out." else "Signed in as ${account?.email}.")
                    append("\n")
                    append(
                        when {
                            status.syncing -> "Syncing now…"
                            status.lastSyncedAt == 0L -> "Not synced yet on this phone."
                            else -> "Last synced ${time.format(Date(status.lastSyncedAt))}: ${status.pulled} brought down, ${status.pushed} sent up."
                        }
                    )
                    status.message?.let { append("\n${if (status.failed) "Problem: " else ""}$it") }
                },
                color = if (status.failed) androidx.compose.ui.graphics.Color(0xFFFF6B4A) else TextPrimary,
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { supabaseSync.syncNow() }, enabled = account != null) { Text("Sync now", color = TextPrimary) }
                OutlinedButton(onClick = { logKind = "sync" }) { Text("Show sync in the log", color = TextPrimary) }
            }

            Section("A second account to test with")
            Text(
                "Try adding, moving and deleting on a throwaway account instead of your real collection. One account is in use and the other is put aside; each one's decks and binders stay on the server and never mix.",
                color = TextMuted, style = MaterialTheme.typography.bodySmall
            )
            val other = parked
            Text(
                "In use: ${account?.email ?: "nobody (signed out)"}\nPut aside: ${other?.email ?: "none"}",
                color = TextPrimary, style = MaterialTheme.typography.bodySmall
            )
            if (other != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { accountBusy = true; scope.launch { accountLine = Tester.accounts.switch(); accountBusy = false } },
                        enabled = !accountBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Bg)
                    ) { Text(if (accountBusy) "Switching…" else "Switch to ${other.email}", color = Bg) }
                    TextButton(onClick = { Tester.accounts.forget(); accountLine = "Forgot ${other.email} on this phone." }, enabled = !accountBusy) { Text("Forget it", color = TextMuted) }
                }
            } else {
                OutlinedButton(
                    onClick = { accountBusy = true; scope.launch { accountLine = Tester.accounts.makeRoomForSecond(); accountBusy = false } },
                    enabled = !accountBusy && account != null
                ) { Text(if (accountBusy) "Syncing first…" else "Put this account aside and sign in to another", color = TextPrimary) }
                if (account == null) OutlinedButton(onClick = onOpenSettings) { Text("Sign in from Settings", color = TextPrimary) }
            }
            accountLine?.let { Text(it, color = Gold, style = MaterialTheme.typography.bodySmall) }

            Section("Activity log")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LOG_KINDS.forEach { (kind, label) ->
                    FilterChip(selected = logKind == kind, onClick = { logKind = kind }, label = { Text(label) })
                }
            }
            val shown = log.filter { logKind == "all" || it.kind == logKind }.asReversed()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${shown.size} lines, newest first", color = TextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    val text = shown.joinToString("\n") { "${time.format(Date(it.at))} ${it.kind}: ${it.text}${if (it.times > 1) " ×${it.times}" else ""}" }
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Manabind tester log", text))
                    Toast.makeText(context, "Log copied.", Toast.LENGTH_SHORT).show()
                }) { Text("Copy", color = Gold) }
            }
            Column(
                Modifier.fillMaxWidth().background(Surface).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                if (shown.isEmpty()) Text("Nothing yet.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                shown.take(120).forEach { line ->
                    Text(
                        "${time.format(Date(line.at))}  ${line.kind}  ${line.text}${if (line.times > 1) "  ×${line.times}" else ""}",
                        color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp
                    )
                }
            }

            Section("Crash reports")
            Text("If the app crashes, what went wrong is saved and sent the next time it opens. This crashes it on purpose, to check that works.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { confirmCrash = true }) { Text("Crash the app now", color = TextPrimary) }
            Box(Modifier.height(24.dp))
        }
    }

    if (confirmCrash) {
        AlertDialog(
            onDismissRequest = { confirmCrash = false },
            containerColor = Surface,
            title = { Text("Crash the tester app?", color = GoldLight, style = MaterialTheme.typography.titleMedium) },
            text = { Text("It closes straight away. Open it again and the crash report sends itself.", color = TextMuted, style = MaterialTheme.typography.bodySmall) },
            confirmButton = { TextButton(onClick = { throw IllegalStateException("Test crash from Tester tools") }) { Text("Crash it", color = Gold) } },
            dismissButton = { TextButton(onClick = { confirmCrash = false }) { Text("Cancel", color = TextMuted) } }
        )
    }
}

@Composable
private fun Section(title: String) {
    Column(Modifier.padding(top = 8.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(BorderColor))
        Text(title, color = GoldLight, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun Toggle(title: String, detail: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
            Text(detail, color = TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = on, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Gold))
    }
}
