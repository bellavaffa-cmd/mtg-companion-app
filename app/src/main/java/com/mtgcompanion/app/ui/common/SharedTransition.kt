package com.mtgcompanion.app.ui.common

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/**
 * The app-wide [SharedTransitionScope], provided once around the nav graph's content (see
 * [com.mtgcompanion.app.ui.nav.MtgNavGraph]) so any screen can opt a thumbnail image into a
 * shared-element hero morph with [CardZoomDialog]'s enlarged view, without threading the scope
 * through every screen's parameter list. Null if read outside that scope (shouldn't happen in
 * practice — every screen composable lives under it) — callers should treat a null scope the same
 * as "no transition available" and fall back to a plain fade/pop-in.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/**
 * Opts this element into a shared-element hero morph keyed by [key] (typically a scryfallId) — a
 * thumbnail and [CardZoomDialog]'s enlarged image sharing the same key will morph between each
 * other's bounds instead of the enlarged one just fading in. Both the thumbnail (always on screen)
 * and the zoom overlay (only composed while shown) mark themselves `visible = true`, which is what
 * "caller managed visibility" means here — the framework detects the matching key appearing or
 * disappearing from composition and animates accordingly, no shared `AnimatedVisibility` needed.
 * A no-op when [key] is null (nothing to key on yet) or outside a [LocalSharedTransitionScope].
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.cardHeroElement(key: String?): Modifier {
    val scope = LocalSharedTransitionScope.current
    if (scope == null || key == null) return this
    val modifier = this
    return with(scope) {
        modifier.sharedElementWithCallerManagedVisibility(
            scope.rememberSharedContentState(key = key),
            visible = true
        )
    }
}
