package com.mtgcompanion.app.tester

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.GoldLight
import com.mtgcompanion.app.ui.theme.Surface
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary
import org.json.JSONObject
import java.io.File
import kotlin.math.sqrt

/** A report being written: what kind, the picture taken as it began, and what it's already about. */
private data class Draft(val kind: String, val picture: Bitmap?, val about: String = "", val extra: JSONObject? = null)

/**
 * What the tester app draws over every screen: the banner saying which build this is, the bug
 * button, and the dialogs they open. Sits beside the nav graph in [com.mtgcompanion.app.MainActivity]
 * and draws nothing at all outside the tester build.
 */
@Composable
fun TesterOverlay(activity: Activity) {
    if (!Tester.on) return
    val flags = Tester.flags
    // Read so a flipped switch redraws this.
    val changes by flags.changes.collectAsState()
    val feedbackFor by Tester.feedbackFor.collectAsState()
    val lastScan by Tester.lastScan.collectAsState()
    val log by TesterLog.flow.collectAsState()
    var draft by remember { mutableStateOf<Draft?>(null) }
    var notesOpen by remember { mutableStateOf(flags.seenNotesFor < Tester.BUILD && TESTER_NOTES.isNotEmpty()) }

    // The picture is taken first, while the screen still shows the problem and not this dialog.
    val startReport: (String, String, JSONObject?) -> Unit = { kind, about, extra ->
        if (draft == null) TesterReports.screenshot(activity) { draft = Draft(kind, it, about, extra) }
    }

    if (flags.shakeToReport && changes >= 0) {
        DisposableEffect(Unit) {
            val sensors = activity.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val listener = ShakeListener { if (draft == null && !notesOpen) startReport("bug", "", null) }
            sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensors.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
            onDispose { sensors?.unregisterListener(listener) }
        }
    }

    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        if (flags.banner) {
            Text(
                "TESTER · build ${Tester.BUILD}",
                color = Color.Black,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFFFC600).copy(alpha = 0.9f))
                    .padding(horizontal = 8.dp, vertical = 1.dp)
            )
        }

        // The scanner's readout: what the last scan made of the card, and a way to say it was wrong.
        val scan = lastScan
        // The screen name is read through the log, which changes with it.
        val onScanner = log.isNotEmpty() && Tester.screen == "scan"
        if (flags.scanDebug && onScanner && scan != null) {
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 22.dp, start = 10.dp, end = 10.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.78f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(scan.line(), color = Color.White, fontSize = 12.sp, lineHeight = 15.sp)
                TextButton(
                    onClick = { startReport("scan", "This scan was wrong: ", scanFacts(scan)) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    modifier = Modifier.height(28.dp)
                ) { Text("This scan was wrong", color = Color(0xFFFFC600), fontSize = 12.sp) }
            }
        }

        if (flags.bugButton) BugButton(
            onTap = { startReport("bug", "", null) },
            onHold = { Tester.showTools() },
            modifier = Modifier.align(Alignment.CenterEnd)
        )

        feedbackFor?.let { feature ->
            FeedbackPrompt(
                feature = feature,
                onGood = {
                    Tester.reports.submit("feedback", "The $feature: good", JSONObject().put("feature", feature).put("verdict", "good"))
                    Tester.feedbackDone(feature)
                    Toast.makeText(activity, "Thanks, sent.", Toast.LENGTH_SHORT).show()
                },
                onBad = {
                    Tester.feedbackDone(feature)
                    startReport("feedback", "The $feature: ", JSONObject().put("feature", feature).put("verdict", "bad"))
                },
                onDismiss = { Tester.feedbackDone(feature) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    draft?.let { d ->
        ReportDialog(
            draft = d,
            onSend = { kind, note, withPicture ->
                // A wrong scan's picture is the frame the scanner was given, when it kept one.
                val frame = d.extra?.optInt("captureId", 0)?.takeIf { it > 0 }?.let { scanFrame(activity, it) }
                val picture = frame ?: if (withPicture) d.picture?.let { TesterReports.jpeg(it) } else null
                Tester.reports.submit(kind, note, d.extra, picture)
                draft = null
                Toast.makeText(activity, "Report saved. It sends when you're online and signed in.", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { draft = null }
        )
    }

    if (notesOpen && draft == null) {
        NotesDialog(
            onVerdict = { note, works ->
                if (works) Tester.reports.submit("checklist", "${note.title}: works", JSONObject().put("item", note.id).put("verdict", "works"))
                else startReport("checklist", "${note.title}: ", JSONObject().put("item", note.id).put("verdict", "problem"))
            },
            onDone = { flags.seenNotesFor = Tester.BUILD; notesOpen = false }
        )
    }

    // Asked for from the tester tools: this build's list again.
    val reopen by Tester.notesRequested.collectAsState()
    LaunchedEffect(reopen) {
        if (reopen) { notesOpen = true; Tester.notesShown() }
    }
    val asked by Tester.reportRequested.collectAsState()
    LaunchedEffect(asked) {
        asked?.let { kind -> Tester.reportStarted(); startReport(kind, "", null) }
    }
}

private fun scanFacts(scan: ScanOutcome): JSONObject = JSONObject()
    .put("captureId", scan.captureId)
    .put("titleRead", scan.titleRead ?: JSONObject.NULL)
    .put("pickedName", scan.name ?: JSONObject.NULL)
    .put("pickedSet", scan.set ?: JSONObject.NULL)
    .put("pickedNumber", scan.number ?: JSONObject.NULL)
    .put("certain", scan.certain)
    .put("how", scan.how)
    .put("tookMs", scan.tookMs)

/** The frame the scanner filed for attempt [captureId] (see ScanCapture), made small. */
private fun scanFrame(context: Context, captureId: Int): ByteArray? =
    TesterReports.jpegOf(File(File(context.getExternalFilesDir(null), "scan-capture"), "$captureId-frame.jpg"))

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BugButton(onTap: () -> Unit, onHold: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(end = 4.dp)
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .combinedClickable(onClick = onTap, onLongClick = onHold),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.BugReport, contentDescription = "Report a problem (hold for tester tools)", tint = Color(0xFFFFC600), modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun FeedbackPrompt(feature: String, onGood: () -> Unit, onBad: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 90.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.88f))
            .padding(start = 14.dp, end = 4.dp)
    ) {
        Text("How was the $feature?", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onGood) { Text("👍", fontSize = 20.sp) }
        TextButton(onClick = onBad) { Text("👎", fontSize = 20.sp) }
        TextButton(onClick = onDismiss) { Text("Skip", color = Color(0xFFB8B4AA), fontSize = 13.sp) }
    }
}

@Composable
private fun ReportDialog(draft: Draft, onSend: (kind: String, note: String, withPicture: Boolean) -> Unit, onDismiss: () -> Unit) {
    var kind by remember { mutableStateOf(draft.kind) }
    var note by remember { mutableStateOf(draft.about) }
    var withPicture by remember { mutableStateOf(draft.picture != null) }
    // Only a plain report can turn into an idea; the others are about something in particular.
    val canBeIdea = draft.kind == "bug" || draft.kind == "idea"
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = {
            Text(
                when (kind) { "idea" -> "Suggest an idea"; "scan" -> "A scan that came out wrong"; "feedback" -> "What went wrong?"; "checklist" -> "What's the problem?"; else -> "Report a problem" },
                color = GoldLight, style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canBeIdea) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = kind == "bug", onClick = { kind = "bug" }, label = { Text("A problem") })
                        FilterChip(selected = kind == "idea", onClick = { kind = "idea" }, label = { Text("An idea") })
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(4000) },
                    label = { Text(if (kind == "idea") "What would you like?" else "What happened, and what did you expect?") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                draft.picture?.let { picture ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = withPicture, onCheckedChange = { withPicture = it }, colors = CheckboxDefaults.colors(checkedColor = Gold))
                        Text("Include this picture of the screen", color = TextPrimary, style = MaterialTheme.typography.bodySmall)
                    }
                    if (withPicture) {
                        Image(
                            bitmap = picture.asImageBitmap(),
                            contentDescription = "The screen as it was",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.heightIn(max = 170.dp).clip(RoundedCornerShape(8.dp)).background(BorderColor)
                        )
                    }
                }
                Text(
                    "Sent with it: this build and phone, the screen you were on, and the app's last minute or so of activity. It goes to the developer only.",
                    color = TextMuted, style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSend(kind, note.trim(), withPicture) }, enabled = note.isNotBlank()) { Text("Send", color = if (note.isNotBlank()) Gold else TextMuted) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } }
    )
}

/** What's new in this tester build, each with a way to say whether it works. */
@Composable
private fun NotesDialog(onVerdict: (TesterNote, works: Boolean) -> Unit, onDone: () -> Unit) {
    var answered by remember { mutableStateOf(setOf<String>()) }
    AlertDialog(
        onDismissRequest = onDone,
        containerColor = Surface,
        title = { Text("New in tester build ${Tester.BUILD}", color = GoldLight, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TESTER_NOTES.forEach { note ->
                    Column {
                        Text(note.title, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(note.howToTry, color = TextMuted, style = MaterialTheme.typography.bodySmall)
                        if (note.id in answered) {
                            Text("Thanks, noted.", color = Gold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                        } else {
                            Row {
                                TextButton(onClick = { answered = answered + note.id; onVerdict(note, true) }) { Text("Works", color = Gold) }
                                Spacer(Modifier.width(4.dp))
                                TextButton(onClick = { answered = answered + note.id; onVerdict(note, false) }) { Text("Problem", color = Color(0xFFFF6B4A)) }
                            }
                        }
                    }
                }
                Text("You can come back to this from Tester tools once you've tried them.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Done", color = Gold) } }
    )
}

/** Calls [onShake] when the phone is shaken hard, and not again for a moment after. */
private class ShakeListener(private val onShake: () -> Unit) : SensorEventListener {
    private var lastShake = 0L
    private var hits = 0
    private var firstHit = 0L

    override fun onSensorChanged(event: SensorEvent) {
        val (x, y, z) = event.values
        val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
        if (g < SHAKE_G) return
        val now = System.currentTimeMillis()
        // Two hard jolts close together: one alone is a phone put down on a table.
        if (now - firstHit > 800) { firstHit = now; hits = 0 }
        hits++
        if (hits >= 2 && now - lastShake > 2_000) {
            lastShake = now
            hits = 0
            onShake()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private companion object {
        /** Well past walking or a tap on the table; a deliberate shake clears 3 g easily. */
        const val SHAKE_G = 2.7f
    }
}
