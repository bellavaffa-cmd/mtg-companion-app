package com.mtgcompanion.app.ui.badge

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import coil.imageLoader
import coil.request.ImageRequest
import com.mtgcompanion.app.data.nfc.ArgbImage
import com.mtgcompanion.app.data.nfc.BadgeConfig
import com.mtgcompanion.app.data.nfc.BadgeEvent
import com.mtgcompanion.app.data.nfc.BadgePhase
import com.mtgcompanion.app.data.nfc.BadgeProgress
import com.mtgcompanion.app.data.nfc.BadgeWriter
import com.mtgcompanion.app.data.nfc.DEFAULT_BADGE
import com.mtgcompanion.app.data.nfc.previewForBadge
import com.mtgcompanion.app.ui.decks.Panel
import com.mtgcompanion.app.ui.decks.SectionLabel
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.BorderColor
import com.mtgcompanion.app.ui.theme.ErrorColor
import com.mtgcompanion.app.ui.theme.Gold
import com.mtgcompanion.app.ui.theme.OnGold
import com.mtgcompanion.app.ui.theme.SuccessColor
import com.mtgcompanion.app.ui.theme.Surface3
import com.mtgcompanion.app.ui.theme.TextMuted
import com.mtgcompanion.app.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Putting one of a deck's tokens onto an NFC e-paper badge.
 *
 * The badge has no battery, so the whole exchange runs off the phone's field and takes the better
 * part of half a minute — about ten seconds sending the picture and fifteen more while the panel
 * works out how to draw it. Moving the badge in that window loses everything, so most of what this
 * screen does is tell the user where to hold it and how far along it is.
 */
@Composable
fun BadgeScreen(viewModel: BadgeViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val writer = remember(context) { BadgeWriter(context) }

    val deckName by viewModel.deckName.collectAsState()
    val tokens by viewModel.tokens.collectAsState()

    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = tokens?.firstOrNull { it.id == selectedId } ?: tokens?.firstOrNull()

    // Pick the first token as soon as the list arrives, so the preview isn't empty for no reason.
    LaunchedEffect(tokens) { if (selectedId == null) selectedId = tokens?.firstOrNull()?.id }

    // The token's art, decoded into something we can draw into a Canvas — hardware bitmaps can't be.
    var art by remember(selected?.artUrl) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(selected?.artUrl) {
        art = null
        val url = selected?.artUrl ?: return@LaunchedEffect
        val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()
        art = (context.imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
    }

    val look = remember(context) { BadgeLook(context) }
    var ink by remember { mutableStateOf(look.ink) }
    var invert by remember { mutableStateOf(look.invert) }
    val spec = selected?.let { TokenFaceSpec(it.name, it.typeLine, it.powerToughness, art, it.emblem, ink, invert) }

    // What the badge will show, dithered the same way, so nothing is a surprise after 30 seconds.
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(spec?.name, spec?.typeLine, spec?.powerToughness, art, ink, invert) {
        preview = spec?.let {
            withContext(Dispatchers.Default) {
                val face = renderTokenFace(it, DEFAULT_BADGE.width, DEFAULT_BADGE.height)
                previewForBadge(face, DEFAULT_BADGE).toBadgeImageBitmap()
            }
        }
    }

    var event by remember { mutableStateOf<BadgeEvent?>(null) }
    var writing by remember { mutableStateOf(false) }

    // A write ends with your eyes on the badge and the phone face-down against it, so say when it's
    // over rather than leaving you to guess. Keyed on the outcome, not on `event`, so the hundred
    // progress updates on the way there don't restart it.
    val chime = remember { BadgeChime() }
    val haptics = LocalHapticFeedback.current
    val finished = when (event) {
        is BadgeEvent.Done -> true
        is BadgeEvent.Failed -> false
        else -> null
    }
    LaunchedEffect(finished) {
        when (finished) {
            true -> chime.done()
            false -> chime.failed()
            null -> return@LaunchedEffect
        }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    // Reader mode belongs to this screen only: leaving with it on has the phone reading badges
    // behind whatever the user opened next.
    DisposableEffect(activity) {
        onDispose { activity?.let { writer.stop(it) } }
    }

    fun beginWriting() {
        val a = activity ?: return
        val face = spec ?: return
        writing = true
        event = BadgeEvent.Waiting
        writer.start(a, picture = { config -> renderTokenFace(face, config.width, config.height) }) { next ->
            event = next
            // Note: no writer.stop() here. The badge is still on the phone, and handing the radio
            // back at that moment is what made Android announce "New tag collected" over it.
            if (next is BadgeEvent.Done || next is BadgeEvent.Failed) writing = false
        }
    }

    fun stopWriting() {
        writing = false
        event = null
        activity?.let { writer.stop(it) }
    }

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

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when {
                tokens == null -> item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Gold)
                    }
                }

                tokens!!.isEmpty() -> item {
                    Panel {
                        SectionLabel("No tokens in this deck")
                        Text(
                            "Nothing in here makes a token, so there's nothing to put on a badge.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }

                else -> {
                    item {
                        Panel {
                            SectionLabel("Which token")
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 10.dp)
                            ) {
                                items(tokens!!, key = { it.id }) { token ->
                                    TokenChip(
                                        label = token.name,
                                        selected = token.id == (selected?.id),
                                        onClick = { if (!writing) selectedId = token.id }
                                    )
                                }
                            }
                            selected?.let { token ->
                                Text(
                                    "Made by ${token.madeBy.joinToString(" · ")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted,
                                    modifier = Modifier.padding(top = 10.dp)
                                )
                            }
                        }
                    }

                    item {
                        Panel {
                            SectionLabel("What the badge will show")
                            Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                                val shown = preview
                                if (shown == null) {
                                    Box(
                                        Modifier.width(200.dp).aspectRatio(DEFAULT_BADGE.width / DEFAULT_BADGE.height.toFloat())
                                            .clip(RoundedCornerShape(10.dp)).background(Surface3),
                                        contentAlignment = Alignment.Center
                                    ) { CircularProgressIndicator(color = Gold, modifier = Modifier.size(28.dp)) }
                                } else {
                                    Image(
                                        bitmap = shown,
                                        contentDescription = "Badge preview",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .width(200.dp)
                                            .aspectRatio(DEFAULT_BADGE.width / DEFAULT_BADGE.height.toFloat())
                                            .clip(RoundedCornerShape(10.dp))
                                            .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                                    )
                                }
                            }
                            Text(
                                "Three colours and no greys, so the art comes out grainy — that's the panel, not the picture.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                            )
                            // Real e-paper white is closer to newsprint than to a screen's white, so
                            // what looks right here often wants more ink on the badge.
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 12.dp)
                            ) {
                                BadgeInk.entries.forEach { option ->
                                    TokenChip(
                                        label = option.name.lowercase().replaceFirstChar { it.uppercase() },
                                        selected = ink == option,
                                        onClick = { if (!writing) { ink = option; look.ink = option } }
                                    )
                                }
                                TokenChip(
                                    label = "Invert",
                                    selected = invert,
                                    onClick = { if (!writing) { invert = !invert; look.invert = invert } }
                                )
                            }
                        }
                    }

                    item { WritePanel(writer, event, writing, ::beginWriting, ::stopWriting, context) }
                }
            }
        }
    }
}

@Composable
private fun WritePanel(
    writer: BadgeWriter,
    event: BadgeEvent?,
    writing: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    context: Context
) {
    Panel {
        SectionLabel("Put it on the badge")
        when {
            !writer.isSupported -> Text(
                "This phone doesn't have NFC, so it can't write to a badge.",
                style = MaterialTheme.typography.bodySmall, color = TextMuted,
                modifier = Modifier.padding(top = 6.dp)
            )

            !writer.isEnabled -> {
                Text(
                    "NFC is switched off.",
                    style = MaterialTheme.typography.bodySmall, color = TextMuted,
                    modifier = Modifier.padding(top = 6.dp)
                )
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }) {
                    Text("Turn it on", color = Gold)
                }
            }

            else -> {
                Text(
                    badgeStatus(event),
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (event) {
                        is BadgeEvent.Failed -> ErrorColor
                        is BadgeEvent.Done -> SuccessColor
                        else -> TextPrimary
                    },
                    modifier = Modifier.padding(top = 8.dp)
                )
                val sending = (event as? BadgeEvent.Working)?.progress as? BadgeProgress.Sending
                if (sending != null) {
                    LinearProgressIndicator(
                        progress = { sending.done / sending.total.toFloat() },
                        color = Gold,
                        trackColor = Surface3,
                        modifier = Modifier.fillMaxWidth().height(6.dp).padding(top = 10.dp)
                    )
                }
                (event as? BadgeEvent.Found)?.let {
                    Text(
                        it.config.toString(),
                        style = MaterialTheme.typography.bodySmall, color = TextMuted,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (writing) {
                    TextButton(onClick = onStop) { Text("Cancel", color = TextMuted) }
                } else {
                    Button(
                        onClick = onStart,
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = OnGold),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (event is BadgeEvent.Done) "Send another" else "Send to badge") }
                }
            }
        }
    }
}

internal fun badgeStatus(event: BadgeEvent?): String = when (event) {
    null -> "Hold the badge flat against the back of your phone when you're ready. It takes about half a minute."
    BadgeEvent.Waiting -> "Hold the badge against the back of your phone — and keep it there."
    is BadgeEvent.Found -> "Found it. Keep holding."
    is BadgeEvent.Working -> when (val p = event.progress) {
        BadgeProgress.Reading -> "Reading the badge…"
        is BadgeProgress.Sending -> "Sending the picture — ${p.done} of ${p.total}. Don't move it."
        BadgeProgress.Drawing -> "Drawing. This takes about fifteen seconds and the badge must stay put."
    }
    is BadgeEvent.Moved ->
        if (event.timedOut) "The badge is taking its time ${event.phase.label} — ${event.at}. Trying again (${event.attempt} of ${event.of}); keep it where it is."
        else "Lost it ${event.phase.label} — ${event.at} (try ${event.attempt} of ${event.of}). Keep it flat and still; it's going again."
    BadgeEvent.Done -> "Done. The badge keeps the picture with no power at all."
    is BadgeEvent.Failed -> event.reason
}

@Composable
private fun TokenChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Gold else Surface3)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) OnGold else TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * The two notes that end a write: one for a badge that took the picture, one for a badge that
 * didn't. On the notification stream, so a phone on silent stays silent.
 *
 * The tone generator wants a live audio stream and will throw if it can't get one — a lost chime is
 * no reason to lose the result, so a failure here is swallowed.
 */
internal class BadgeChime {
    fun done() = play(ToneGenerator.TONE_PROP_BEEP2, 320)
    fun failed() = play(ToneGenerator.TONE_SUP_ERROR, 500)

    private fun play(tone: Int, millis: Int) {
        runCatching {
            val generator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            generator.startTone(tone, millis)
            // Releasing before the tone has finished cuts it off, so let it run out first.
            Handler(Looper.getMainLooper()).postDelayed({ runCatching { generator.release() } }, millis + 250L)
        }
    }
}

/** ARGB pixels straight into something Compose can draw. */
internal fun ArgbImage.toBadgeImageBitmap(): ImageBitmap =
    Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()

internal fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
