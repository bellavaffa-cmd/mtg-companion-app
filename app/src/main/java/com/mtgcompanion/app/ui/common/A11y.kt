package com.mtgcompanion.app.ui.common

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.TextUnit

// Accessibility helpers shared by the screens: headings, pane titles for dialogs and sheets, the
// system's "Remove animations", and a capped font scale for text drawn to fit a fixed space. The web
// app's half: src/a11y.css and src/components/useModalFocus.ts; the words themselves: A11yText.kt.

/** Marks a title as a heading, so TalkBack's headings navigation can jump to it. */
fun Modifier.a11yHeading(): Modifier = this.semantics { heading() }

/** Names a dialog or sheet for TalkBack, read out as it opens ("Advanced filters"). */
fun Modifier.a11yPane(title: String): Modifier = this.semantics { paneTitle = title }

/**
 * Whether the system's "Remove animations" is on (animator duration scale 0). Compose already runs
 * its own animations instantly then; this is for movement it would otherwise still show — staggered
 * entrances held back by a delay, pops started by hand.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }
            .getOrDefault(1f) == 0f
    }
}

/**
 * [size] in sp, but growing with the system font size only up to [maxScale] — for labels drawn to fit
 * a fixed space (the life counter's tiles), where 200% text would clip.
 */
@Composable
fun cappedSp(size: TextUnit, maxScale: Float = 1.3f): TextUnit {
    val scale = LocalDensity.current.fontScale
    return if (scale > maxScale) size * (maxScale / scale) else size
}
