package com.mtgcompanion.app.ui.badge

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.imageLoader
import coil.request.ImageRequest
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.nfc.BadgeEvent
import com.mtgcompanion.app.data.nfc.BadgeProgress
import com.mtgcompanion.app.data.nfc.BadgeWriter
import com.mtgcompanion.app.data.nfc.DEFAULT_BADGE
import com.mtgcompanion.app.data.nfc.previewForBadge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Putting a token on a badge without getting up from the table.
 *
 * The same job as [BadgeScreen], minus the chrome: the deck you said you're playing already picked
 * the token list, so this is choose one and hold the badge on. Ink and invert are here too, and
 * shared with the badge screen through [BadgeLook], so a change made at the table is still there
 * next time either one is opened.
 *
 * Drawn in the remote's own dark palette rather than the app's panels, because it opens on top of
 * the table and shouldn't look like a different app.
 */
@Composable
fun BadgeSheet(
    deck: Deck?,
    ink: Color,
    muted: Color,
    accent: Color
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val writer = remember(context) { BadgeWriter(context) }
    val look = remember(context) { BadgeLook(context) }
    val cards = remember { CardRepository() }
    val haptics = LocalHapticFeedback.current
    val chime = remember { BadgeChime() }

    var tokens by remember(deck?.id) { mutableStateOf<List<BadgeToken>?>(null) }
    LaunchedEffect(deck?.id) { tokens = badgeTokensFor(deck, cards) }

    var selectedId by remember(deck?.id) { mutableStateOf<String?>(null) }
    val selected = tokens?.firstOrNull { it.id == selectedId } ?: tokens?.firstOrNull()
    LaunchedEffect(tokens) { if (selectedId == null) selectedId = tokens?.firstOrNull()?.id }

    var art by remember(selected?.artUrl) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(selected?.artUrl) {
        art = null
        val url = selected?.artUrl ?: return@LaunchedEffect
        val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()
        art = (context.imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
    }

    var inkLevel by remember { mutableStateOf(look.ink) }
    var invert by remember { mutableStateOf(look.invert) }
    val spec = selected?.let {
        TokenFaceSpec(it.name, it.typeLine, it.powerToughness, art, it.emblem, inkLevel, invert)
    }

    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(spec?.name, spec?.powerToughness, art, inkLevel, invert) {
        preview = spec?.let {
            withContext(Dispatchers.Default) {
                previewForBadge(renderTokenFace(it, DEFAULT_BADGE.width, DEFAULT_BADGE.height), DEFAULT_BADGE)
                    .toBadgeImageBitmap()
            }
        }
    }

    var event by remember { mutableStateOf<BadgeEvent?>(null) }
    var writing by remember { mutableStateOf(false) }

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

    // The sheet closing has to hand the radio back, or the phone goes on reading badges behind the
    // table for the rest of the game.
    DisposableEffect(activity) { onDispose { activity?.let { writer.stop(it) } } }

    when {
        deck == null -> Text("Pick the deck you're playing first — that's where the tokens come from.", color = muted, fontSize = 13.sp)
        !writer.isSupported -> Text("This phone doesn't have NFC, so it can't write to a badge.", color = muted, fontSize = 13.sp)
        !writer.isEnabled -> Text("NFC is switched off.", color = muted, fontSize = 13.sp)
        tokens == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(color = accent, modifier = Modifier.size(18.dp))
            Text("Reading the deck…", color = muted, fontSize = 13.sp)
        }
        tokens!!.isEmpty() -> Text("Nothing in ${deck.name} makes a token.", color = muted, fontSize = 13.sp)
        else -> {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                items(tokens!!, key = { it.id }) { token ->
                    SheetChip(
                        label = token.name,
                        selected = token.id == selected?.id,
                        enabled = !writing,
                        ink = ink,
                        accent = accent
                    ) { selectedId = token.id }
                }
            }

            // Shared with the badge screen, so whichever one you change it in, the other follows.
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                items(BadgeInk.entries, key = { it.name }) { option ->
                    SheetChip(
                        label = option.name.lowercase().replaceFirstChar { it.uppercase() },
                        selected = inkLevel == option,
                        enabled = !writing,
                        ink = ink,
                        accent = accent
                    ) { inkLevel = option; look.ink = option }
                }
                item {
                    SheetChip(label = "Invert", selected = invert, enabled = !writing, ink = ink, accent = accent) {
                        invert = !invert
                        look.invert = invert
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                Box(
                    Modifier
                        .width(86.dp)
                        .aspectRatio(DEFAULT_BADGE.width / DEFAULT_BADGE.height.toFloat())
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    preview?.let {
                        Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth())
                    } ?: CircularProgressIndicator(color = accent, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(badgeStatus(event), color = ink, fontSize = 13.sp)
                    val sending = (event as? BadgeEvent.Working)?.progress as? BadgeProgress.Sending
                    if (sending != null) {
                        LinearProgressIndicator(
                            progress = { sending.done / sending.total.toFloat() },
                            color = accent,
                            trackColor = Color.White.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth().height(5.dp)
                        )
                    }
                }
            }

            val face = spec
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (writing) Color.White.copy(alpha = 0.10f) else accent)
                    .clickable(enabled = !writing && face != null && activity != null) {
                        val a = activity ?: return@clickable
                        writing = true
                        event = BadgeEvent.Waiting
                        writer.start(a, picture = { config -> renderTokenFace(face!!, config.width, config.height) }) { next ->
                            event = next
                            if (next is BadgeEvent.Done || next is BadgeEvent.Failed) writing = false
                        }
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (writing) "Hold the badge on the back of the phone" else if (event is BadgeEvent.Done) "Send another" else "Send to badge",
                    color = if (writing) ink else Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/** One pill in the sheet: the remote's look, not the app's. */
@Composable
private fun SheetChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    ink: Color,
    accent: Color,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) accent else Color.White.copy(alpha = 0.10f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            color = if (selected) Color.Black else ink,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
