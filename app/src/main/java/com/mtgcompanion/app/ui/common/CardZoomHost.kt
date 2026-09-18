package com.mtgcompanion.app.ui.common

import androidx.compose.runtime.mutableStateMapOf
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize

/**
 * Where enlarged cards are drawn: above everything inside [content], in the same composition as the
 * screens, so a card can grow out of its thumbnail and shrink back into it (a separate dialog
 * window can't animate from something drawn in the app's own window). Put one at the root of the
 * app; [CardZoomDialog] finds it through [LocalCardZoomHost].
 */
@Composable
fun CardZoomHost(content: @Composable () -> Unit) {
    val view = LocalView.current
    val host = remember(view) { CardZoomHostState(view) }
    CompositionLocalProvider(LocalCardZoomHost provides host) {
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    host.origin = it.positionInWindow()
                    host.size = it.size
                }
        ) {
            content()
            // The topmost zoom still on screen — including one that's flying away, so back doesn't
            // fall through to the screen behind it.
            val top = host.entries.lastOrNull { !it.closing } ?: host.entries.lastOrNull()
            host.entries.forEach { entry ->
                key(entry) { ZoomOverlay(host, entry, onTop = entry === top) }
            }
        }
    }
}

val LocalCardZoomHost = staticCompositionLocalOf<CardZoomHostState?> { null }

@Stable
class CardZoomHostState internal constructor(internal val view: View) {
    internal val entries = mutableStateListOf<ZoomEntry>()

    /** The thumbnail currently standing in for a flying card, so it isn't drawn twice. */
    private val hiddenKeys = mutableStateMapOf<String, Int>()

    /** Hides [key]'s thumbnail while a card is flying to or from it; call [release] when it lands. */
    internal fun hide(key: String) {
        hiddenKeys[key] = (hiddenKeys[key] ?: 0) + 1
    }

    internal fun release(key: String) {
        val count = (hiddenKeys[key] ?: return) - 1
        if (count <= 0) hiddenKeys.remove(key) else hiddenKeys[key] = count
    }

    internal fun isHidden(key: String): Boolean = (hiddenKeys[key] ?: 0) > 0

    internal var origin = Offset.Zero
    internal var size = IntSize.Zero

    // Thumbnail bounds in window coordinates, per key and per thumbnail showing that key. Not
    // snapshot state: it changes on every scroll frame and is only read when a card takes off.
    private val sources = HashMap<String, HashMap<Any, Rect>>()

    internal fun show(entry: ZoomEntry) {
        entry.closing = false
        if (entry !in entries) entries += entry
    }

    internal fun hide(entry: ZoomEntry) {
        entry.closing = true
    }

    internal fun finish(entry: ZoomEntry) {
        entries.remove(entry)
    }

    internal fun report(key: String, token: Any, bounds: Rect) {
        sources.getOrPut(key) { HashMap() }[token] = bounds
    }

    internal fun forget(key: String, token: Any) {
        val forKey = sources[key] ?: return
        forKey.remove(token)
        if (forKey.isEmpty()) sources.remove(key)
    }

    /**
     * Where [key]'s thumbnail is, in host coordinates: the most visible one when the same card shows
     * more than once (a neighbouring tab kept alive off screen, say), or null when none is on screen.
     */
    internal fun sourceRect(key: String?): Rect? {
        if (key == null) return null
        val window = Rect(origin, size.toSize())
        return sources[key]?.values
            ?.map { it to it.intersect(window) }
            ?.filter { (_, visible) -> visible.width > 1f && visible.height > 1f }
            ?.maxByOrNull { (_, visible) -> visible.width * visible.height }
            ?.first
            ?.translate(-origin)
    }
}

/** One open zoom: the call site keeps [cards] and [onDismiss] current while it's showing. */
@Stable
internal class ZoomEntry(val initialPage: Int, cards: List<ZoomCard>, onDismiss: () -> Unit) {
    var cards by mutableStateOf(cards)
    var onDismiss by mutableStateOf(onDismiss)

    /** The call site has let go; the overlay animates away and then removes itself. */
    var closing by mutableStateOf(false)

    /** A card is flying between its thumbnail and the enlarged view. */
    var flying by mutableStateOf(false)

    /** 0 = closed (at the thumbnail), 1 = fully open. */
    val progress = Animatable(0f)
}

/**
 * Marks a card thumbnail that [CardZoomDialog] can grow out of. [key] is the image URL the zoom's
 * [ZoomCard.imageUrl] uses for that card (even when the thumbnail itself shows an art crop).
 */
@Composable
fun Modifier.zoomSource(key: String?): Modifier {
    val host = LocalCardZoomHost.current
    if (host == null || key == null || host.view !== LocalView.current) return this
    val token = remember { Any() }
    DisposableEffect(host, key) { onDispose { host.forget(key, token) } }
    return this
        .onGloballyPositioned { host.report(key, token, Rect(it.positionInWindow(), it.size.toSize())) }
        .graphicsLayer { alpha = if (host.isHidden(key)) 0f else 1f }
}
