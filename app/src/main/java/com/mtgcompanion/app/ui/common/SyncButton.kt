package com.mtgcompanion.app.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncDisabled
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mtgcompanion.app.ui.theme.Bg
import com.mtgcompanion.app.ui.theme.LocalAppColors

/** What the sync button shows and does. NavGraph provides it around every screen. */
class SyncControl(
    val signedIn: Boolean,
    val syncing: Boolean,
    val failed: Boolean,
    /** Runs a sync, shown by the pull-to-sync indicator. */
    val onSync: () -> Unit,
    /** Opens sign-in (Settings). */
    val onSignIn: () -> Unit
)

/** Null where cloud sync isn't set up in this build: the button then doesn't show. */
val LocalSyncControl = compositionLocalOf<SyncControl?> { null }

/**
 * Sync now, for a screen's header: spins while syncing, and the result shows in the same pill as pull
 * to sync. A red dot marks a sync that failed — or being signed out, when it opens sign-in instead —
 * so a phone that quietly stopped syncing is easy to spot. [filled] gives it a round background, for
 * headers whose other buttons have one.
 */
@Composable
fun SyncIconButton(modifier: Modifier = Modifier, filled: Boolean = false) {
    val control = LocalSyncControl.current ?: return
    val colors = LocalAppColors.current
    val label = when {
        !control.signedIn -> "Not syncing — sign in"
        control.syncing -> "Syncing…"
        control.failed -> "Sync failed — tap to try again"
        else -> "Sync now"
    }
    Box(
        modifier
            .size(42.dp)
            .clip(CircleShape)
            .then(if (filled) Modifier.background(colors.surface) else Modifier)
            .clickable(enabled = !control.syncing) { if (control.signedIn) control.onSync() else control.onSignIn() }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        val spin = if (control.syncing) {
            val transition = rememberInfiniteTransition(label = "syncButton")
            transition.animateFloat(0f, -360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "syncButtonAngle")
        } else null
        Icon(
            if (control.signedIn) Icons.Filled.Sync else Icons.Filled.SyncDisabled,
            contentDescription = null,
            tint = colors.textPrimary,
            modifier = Modifier.size(21.dp).graphicsLayer { rotationZ = spin?.value ?: 0f }
        )
        if (!control.signedIn || control.failed) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
                    .size(9.dp)
                    .border(2.dp, Bg, CircleShape)
                    .clip(CircleShape)
                    .background(colors.error)
            )
        }
    }
}
