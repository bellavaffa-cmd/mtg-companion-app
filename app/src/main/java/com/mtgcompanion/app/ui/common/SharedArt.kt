package com.mtgcompanion.app.ui.common

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Shared-element plumbing: the nav host provides both scopes, and any screen can mark a piece of art
 * with [sharedArt]. When the destination marks art with the same key, it flies between the two
 * positions instead of the screens just crossfading — a deck tile's art grows into the deck header.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

object SharedKeys {
    fun deckArt(deckId: String) = "deck-art-$deckId"
    fun cardArt(cardName: String) = "card-art-$cardName"
}

@OptIn(ExperimentalSharedTransitionApi::class)
private val artBoundsTransform = BoundsTransform { _: Rect, _: Rect ->
    spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = Rect.VisibilityThreshold)
}

/** Marks this element as shared art under [key]; a no-op outside the nav host (previews, dialogs). */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedArt(key: String, shape: Shape = RoundedCornerShape(0.dp)): Modifier {
    val transitionScope = LocalSharedTransitionScope.current ?: return this
    val animatedScope = LocalNavAnimatedScope.current ?: return this
    return with(transitionScope) {
        this@sharedArt.sharedElement(
            state = rememberSharedContentState(key),
            animatedVisibilityScope = animatedScope,
            boundsTransform = artBoundsTransform,
            clipInOverlayDuringTransition = OverlayClip(shape)
        )
    }
}
