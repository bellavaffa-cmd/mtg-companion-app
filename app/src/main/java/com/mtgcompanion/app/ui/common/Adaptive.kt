package com.mtgcompanion.app.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * Which layout the window is wide enough for — the same breakpoints as the web app
 * (MtgCompanionWeb/src/components/kit.tsx useLayoutSize):
 * - [PHONE] under 700dp: floating bottom bar, single column.
 * - [TABLET] 700–1099dp: navigation rail, wider grids.
 * - [DESKTOP] 1100dp and up (a large tablet in landscape, a foldable, a Chromebook window): sidebar,
 *   side-by-side panes.
 * Follows the window live: rotating, resizing a split screen or unfolding switches layouts.
 */
enum class LayoutSize {
    PHONE, TABLET, DESKTOP;

    val isWide: Boolean get() = this != PHONE

    /** Width taken by the rail or sidebar, so screens can size themselves to what's left. */
    val navWidth: Dp get() = when (this) {
        PHONE -> 0.dp
        TABLET -> RAIL_WIDTH
        DESKTOP -> SIDEBAR_WIDTH
    }

    /** Side padding for page content. */
    val pagePadding: Dp get() = when (this) {
        PHONE -> 16.dp
        TABLET -> 28.dp
        DESKTOP -> 40.dp
    }
}

val RAIL_WIDTH = 88.dp
val SIDEBAR_WIDTH = 256.dp

private const val TABLET_MIN_DP = 700
private const val DESKTOP_MIN_DP = 1100

/** Provided once by the nav graph; read it anywhere with `LocalLayoutSize.current`. */
val LocalLayoutSize = staticCompositionLocalOf { LayoutSize.PHONE }

@Composable
fun currentLayoutSize(): LayoutSize {
    val width = LocalConfiguration.current.screenWidthDp
    return when {
        width >= DESKTOP_MIN_DP -> LayoutSize.DESKTOP
        width >= TABLET_MIN_DP -> LayoutSize.TABLET
        else -> LayoutSize.PHONE
    }
}

/**
 * How many columns of full card rows (thumbnail, name, stepper, menu) fit in [width]: one on a
 * phone, two once each still gets ~380dp, three on very wide windows.
 */
fun listColumnsFor(width: Dp): Int = when {
    width >= 1180.dp -> 3
    width >= 760.dp -> 2
    else -> 1
}

/**
 * Scales the user's card-grid size setting ([base] columns, chosen on a phone) to [width], keeping
 * tiles about the size they'd be on a phone rather than stretching a few huge cards across a tablet.
 */
fun gridColumnsFor(width: Dp, base: Int): Int {
    val phoneTile = 380f / base.coerceAtLeast(1)
    return max(base, (width.value / phoneTile).toInt()).coerceAtMost(12)
}

/** Keeps a reading-width screen (settings, rules, forms) from stretching edge to edge on a wide window. */
fun Modifier.readableWidth(max: Dp = 760.dp): Modifier = this.widthIn(max = max).fillMaxWidth()

/** The width left for a screen once the rail or sidebar has taken its share. */
@Composable
fun screenContentWidth(): Dp = LocalConfiguration.current.screenWidthDp.dp - LocalLayoutSize.current.navWidth

/** [listColumnsFor] for a full-width list with the usual 20dp side padding. */
@Composable
fun adaptiveListColumns(): Int = listColumnsFor(screenContentWidth() - 40.dp)

/** [gridColumnsFor] for a full-width grid with the usual 20dp side padding. */
@Composable
fun adaptiveGridColumns(base: Int): Int = gridColumnsFor(screenContentWidth() - 40.dp, base)
