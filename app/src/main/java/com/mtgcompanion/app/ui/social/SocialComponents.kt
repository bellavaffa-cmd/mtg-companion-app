package com.mtgcompanion.app.ui.social

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.mtgcompanion.app.data.social.Overview
import com.mtgcompanion.app.data.social.Profile
import com.mtgcompanion.app.data.social.SocialApi
import com.mtgcompanion.app.data.social.SocialRepository
import com.mtgcompanion.app.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

/** A person's picture (a photo, or a GIF that keeps moving), or their initial. */
@Composable
fun Avatar(profile: Profile?, size: Dp = 46.dp, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val url = SocialApi.avatarUrl(profile?.avatarPath)
    val initial: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(colors.accentGlow), contentAlignment = Alignment.Center) {
            Text(
                (profile?.displayName?.trim()?.firstOrNull() ?: '?').uppercase(),
                color = colors.accent,
                fontWeight = FontWeight.ExtraBold,
                fontSize = (size.value * 0.4f).sp
            )
        }
    }
    Box(modifier.size(size).clip(CircleShape).background(colors.surface2)) {
        if (url == null) initial() else SubcomposeAsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            loading = { initial() },
            error = { initial() }
        )
    }
}

/** A QR code for [text] — dark on white, with the quiet margin scanners need. */
@Composable
fun QrCode(text: String, size: Dp, label: String, modifier: Modifier = Modifier) {
    val bitmap = remember(text) {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 4))
        val bmp = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        for (x in 0 until matrix.width) for (y in 0 until matrix.height) {
            bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
        bmp.asImageBitmap()
    }
    // Drawn pixel-sharp: smoothing would blur the modules together.
    Image(
        painter = remember(bitmap) { BitmapPainter(bitmap, filterQuality = FilterQuality.None) },
        contentDescription = null,
        modifier = modifier.size(size).clip(RoundedCornerShape(12.dp)).semantics { contentDescription = label }
    )
}

/** A row for one person: picture, name, handle, and whatever [trailing] holds. */
@Composable
fun PersonRow(
    profile: Profile?,
    detail: String? = null,
    onClick: (() -> Unit)? = null,
    avatarSize: Dp = 44.dp,
    compact: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (compact) colors.surface2 else colors.surface)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 12.dp, vertical = if (compact) 6.dp else 10.dp)
    ) {
        Avatar(profile, avatarSize)
        Column(Modifier.weight(1f)) {
            Text(profile?.displayName ?: "Someone", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val line = listOfNotNull(profile?.handle, detail).joinToString(" · ")
            if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

/** A small gold pill with a count. */
@Composable
fun CountBadge(count: Int) {
    val colors = LocalAppColors.current
    Box(
        Modifier.clip(CircleShape).background(colors.accent).padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("$count", color = colors.onAccent, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
    }
}

@Composable
fun GoldButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: (@Composable () -> Unit)? = null) {
    val colors = LocalAppColors.current
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
        modifier = modifier
    ) {
        icon?.let { it(); Spacer(Modifier.size(6.dp)) }
        Text(text)
    }
}

@Composable
fun LineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: (@Composable () -> Unit)? = null) {
    val colors = LocalAppColors.current
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        icon?.let { it(); Spacer(Modifier.size(6.dp)) }
        Text(text, color = colors.textPrimary)
    }
}

@Composable
fun socialFieldColors() = LocalAppColors.current.let { colors ->
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.accent,
        unfocusedBorderColor = colors.border,
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        cursorColor = colors.accent,
        focusedContainerColor = colors.surface,
        unfocusedContainerColor = colors.surface
    )
}

/** A soft note panel. */
@Composable
fun Notice(text: String, modifier: Modifier = Modifier, warn: Boolean = false) {
    val colors = LocalAppColors.current
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = colors.textPrimary,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (warn) colors.warning.copy(alpha = 0.14f) else colors.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    )
}

/** An icon, a line or two, and maybe a button, centred — for empty and waiting states. */
@Composable
fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, action: (@Composable () -> Unit)? = null) {
    val colors = LocalAppColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp)
    ) {
        Icon(icon, contentDescription = null, tint = colors.textDim, modifier = Modifier.size(40.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, textAlign = TextAlign.Center)
        action?.invoke()
    }
}

/**
 * Shows [content] once the user is signed in and has a profile; before that, what they need to do.
 * Reloads the overview each time it opens, so requests and trades that came in meanwhile show up.
 */
@Composable
fun SocialGate(social: SocialRepository, onSignIn: () -> Unit, content: @Composable (Overview) -> Unit) {
    val account by social.accountFlow.collectAsState()
    val overview by social.overview.collectAsState()
    val error by social.error.collectAsState()
    val loading by social.loading.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(account?.userId) { if (account != null) social.refresh() }
    val current = overview
    when {
        !social.configured -> EmptyState(Icons.Filled.CloudOff, "Accounts aren't set up in this build.")
        account == null -> EmptyState(Icons.Filled.Group, "Sign in to add friends, share decks and binders, and trade.") {
            GoldButton("Sign in", onSignIn)
        }
        current == null -> if (error != null) {
            EmptyState(Icons.Filled.CloudOff, error.orEmpty()) {
                LineButton("Try again", { scope.launch { social.refresh() } }, enabled = !loading)
            }
        } else {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = LocalAppColors.current.accent)
            }
        }
        current.me == null -> Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Make your profile", style = MaterialTheme.typography.titleLarge)
            Text(
                "Friends find you by your username, and see your name and picture — on shared decks, trades and the life counter.",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalAppColors.current.textMuted
            )
            Spacer(Modifier.height(6.dp))
            ProfileEditor(social = social, onDone = null)
        }
        else -> content(current)
    }
}
