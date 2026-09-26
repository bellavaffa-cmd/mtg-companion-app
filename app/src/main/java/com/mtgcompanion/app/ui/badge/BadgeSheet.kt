package com.mtgcompanion.app.ui.badge

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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

/** How wide the badge shows over a game. Its height follows the panel's own shape. */
private val SHEET_PREVIEW_WIDTH = 168.dp

/**
 * Putting a token on a badge without getting up from the table.
 *
 * The same job as [BadgeScreen], minus the chrome: the deck you said you're playing already picked
 * the token list, so this is swipe to the token you want and hold the badge on. Ink and invert are
 * here too, and shared with the badge screen through [BadgeLook], so a change made at the table is
 * still there next time either one is opened.
 *
 * The badge is shown at a size worth looking at rather than as a thumbnail, because the whole point
 * of a preview on a three-colour panel is seeing what the dithering did before committing half a
 * minute to it. Swiping moves between tokens, which is why there's no list of names: the picture is
 * the thing you're choosing between, and it already has the name printed on it.
 *
 * Drawn in the remote's own dark palette rather than the app's panels, because it opens on top of
 * the table and shouldn't look like a different app.
 */
@Composable
fun BadgeSheet(
    deck: Deck?,
    ink: Color,
    muted: Color,
    accent: Color,
    /** Open on this token — the one that was tapped — rather than on the first. */
    initialTokenId: String? = null,
    /** How wide to draw the badge. A screen can afford more than a sheet over a table. */
    previewWidth: Dp = SHEET_PREVIEW_WIDTH
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

    var inkLevel by remember { mutableStateOf(look.ink) }
    var invert by remember { mutableStateOf(look.invert) }

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
            val list = tokens!!
            val start = list.indexOfFirst { it.id == initialTokenId }.coerceAtLeast(0)
            val pager = rememberPagerState(initialPage = start, pageCount = { list.size })
            val current = list.getOrNull(pager.currentPage)

            // One rendered badge per token, thrown away whenever the look changes so the cache can't
            // outgrow the one setting actually in use.
            val rendered = remember(inkLevel, invert) { mutableStateMapOf<String, ImageBitmap>() }

            // A page is exactly as wide as a badge, and the padding either side is whatever's left.
            // Sizing the page to the container instead leaves the badge centred in a page wider than
            // itself, and then the neighbour only peeks by the few points the padding exceeds that
            // slack by — which looks like a rendering fault rather than an invitation to swipe.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val side = ((maxWidth - previewWidth) / 2).coerceAtLeast(0.dp)
                HorizontalPager(
                    state = pager,
                    userScrollEnabled = !writing,
                    pageSize = PageSize.Fixed(previewWidth),
                    pageSpacing = 12.dp,
                    contentPadding = PaddingValues(horizontal = side),
                    modifier = Modifier.fillMaxWidth()
                ) { page ->
                    TokenPreview(
                        token = list[page],
                        inkLevel = inkLevel,
                        invert = invert,
                        rendered = rendered,
                        accent = accent,
                        width = previewWidth
                    )
                }
            }

            Text(
                current?.name.orEmpty(),
                color = ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                if (list.size == 1) "The only token in this deck" else "${pager.currentPage + 1} of ${list.size} — swipe for the others",
                color = muted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // Under the badge, so changing them is a change to the thing you're looking at.
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
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

            Text(badgeStatus(event), color = ink, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            val sending = (event as? BadgeEvent.Working)?.progress as? BadgeProgress.Sending
            if (sending != null) {
                LinearProgressIndicator(
                    progress = { sending.done / sending.total.toFloat() },
                    color = accent,
                    trackColor = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth().height(5.dp)
                )
            }

            var art by remember(current?.artUrl) { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(current?.artUrl) {
                art = null
                val url = current?.artUrl ?: return@LaunchedEffect
                val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()
                art = (context.imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
            }
            val face = current?.let {
                TokenFaceSpec(it.name, it.typeLine, it.powerToughness, it.oracleText, art, it.emblem, inkLevel, invert)
            }

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

/**
 * One token as the badge will show it.
 *
 * Each page renders its own, because the dithering is the point of looking — and keeps it in
 * [rendered] so swiping back to a token you've already seen is instant rather than another pass
 * over a hundred thousand pixels.
 */
@Composable
private fun TokenPreview(
    token: BadgeToken,
    inkLevel: BadgeInk,
    invert: Boolean,
    rendered: MutableMap<String, ImageBitmap>,
    accent: Color,
    width: Dp
) {
    val context = LocalContext.current
    var badge by remember(token.id, inkLevel, invert) { mutableStateOf(rendered[token.id]) }

    LaunchedEffect(token.id, inkLevel, invert) {
        if (badge != null) return@LaunchedEffect
        val art = token.artUrl?.let { url ->
            val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()
            (context.imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
        }
        val spec = TokenFaceSpec(token.name, token.typeLine, token.powerToughness, token.oracleText, art, token.emblem, inkLevel, invert)
        val image = withContext(Dispatchers.Default) {
            previewForBadge(renderTokenFace(spec, DEFAULT_BADGE.width, DEFAULT_BADGE.height), DEFAULT_BADGE)
                .toBadgeImageBitmap()
        }
        rendered[token.id] = image
        badge = image
    }

    Box(
        Modifier
            .width(width)
            .aspectRatio(DEFAULT_BADGE.width / DEFAULT_BADGE.height.toFloat())
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        badge?.let {
            Image(bitmap = it, contentDescription = token.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        } ?: CircularProgressIndicator(color = accent, modifier = Modifier.size(22.dp))
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
