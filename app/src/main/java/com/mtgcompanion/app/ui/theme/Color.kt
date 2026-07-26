package com.mtgcompanion.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.mtgcompanion.app.data.AccentTheme
import com.mtgcompanion.app.data.AppBrightness

/** Resolved palette for the current brightness + accent theme selection. */
data class AppColors(
    val accent: Color,
    val accentLight: Color,
    val accentDim: Color,
    val accentGlow: Color,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val border: Color,
    val borderBright: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val textDim: Color,
    val error: Color
)

private data class AccentHues(val base: Color, val light: Color, val dim: Color)

// One hue per accent theme, mapped to Magic's five colors of mana (Gold stands in for White,
// the app's original identity, lifted from mtgoracle.gg's :root custom properties).
private fun accentHues(theme: AccentTheme): AccentHues = when (theme) {
    AccentTheme.GOLD -> AccentHues(Color(0xFFC4893A), Color(0xFFE0A84E), Color(0xFF7A5428))
    AccentTheme.SAPPHIRE -> AccentHues(Color(0xFF3E7FC9), Color(0xFF63A0E6), Color(0xFF294F7D))
    AccentTheme.AMETHYST -> AccentHues(Color(0xFF8C4FC7), Color(0xFFAE72E3), Color(0xFF5A3280))
    AccentTheme.RUBY -> AccentHues(Color(0xFFC43A4E), Color(0xFFE25C70), Color(0xFF7D2836))
    AccentTheme.EMERALD -> AccentHues(Color(0xFF3AA860), Color(0xFF5CC981), Color(0xFF287A40))
}

/** The accent's base hue, independent of brightness — for swatches/previews in the theme picker. */
fun accentPreviewColor(theme: AccentTheme): Color = accentHues(theme).base

/** Builds the full palette for a brightness + accent combination. */
fun buildAppColors(brightness: AppBrightness, accent: AccentTheme): AppColors {
    val hues = accentHues(accent)
    return if (brightness == AppBrightness.DARK) {
        AppColors(
            accent = hues.base,
            accentLight = hues.light,
            accentDim = hues.dim,
            accentGlow = hues.base.copy(alpha = 0.15f),
            bg = Color(0xFF07080E),
            surface = Color(0xFF0D0D1C),
            surface2 = Color(0xFF141428),
            surface3 = Color(0xFF1C1C32),
            border = hues.base.copy(alpha = 0.18f),
            borderBright = hues.base.copy(alpha = 0.45f),
            textPrimary = Color(0xFFE8E0D0),
            textMuted = Color(0xFF8A8070),
            textDim = Color(0xFF6A6458),
            error = Color(0xFFCF6659)
        )
    } else {
        // Parchment/cream, not a generic Material white — keeps the same accent hue, and swaps
        // which shade carries emphasis since the pale "light" hue reads too washed-out on cream.
        AppColors(
            accent = hues.base,
            accentLight = hues.dim,
            accentDim = hues.light,
            accentGlow = hues.base.copy(alpha = 0.12f),
            bg = Color(0xFFFBF6EA),
            surface = Color(0xFFF2E9D6),
            surface2 = Color(0xFFE8DCC0),
            surface3 = Color(0xFFDDCEAB),
            border = hues.base.copy(alpha = 0.35f),
            borderBright = hues.base.copy(alpha = 0.6f),
            textPrimary = Color(0xFF2A2418),
            textMuted = Color(0xFF5C5342),
            textDim = Color(0xFF7C7260),
            error = Color(0xFFB3261E)
        )
    }
}

val LocalAppColors = compositionLocalOf { buildAppColors(AppBrightness.DEFAULT, AccentTheme.DEFAULT) }

val Gold: Color @Composable get() = LocalAppColors.current.accent
val GoldLight: Color @Composable get() = LocalAppColors.current.accentLight
val GoldDim: Color @Composable get() = LocalAppColors.current.accentDim
val GoldGlow: Color @Composable get() = LocalAppColors.current.accentGlow

val Bg: Color @Composable get() = LocalAppColors.current.bg
val Surface: Color @Composable get() = LocalAppColors.current.surface
val Surface2: Color @Composable get() = LocalAppColors.current.surface2
val Surface3: Color @Composable get() = LocalAppColors.current.surface3

val BorderColor: Color @Composable get() = LocalAppColors.current.border
val BorderBright: Color @Composable get() = LocalAppColors.current.borderBright

val TextPrimary: Color @Composable get() = LocalAppColors.current.textPrimary
val TextMuted: Color @Composable get() = LocalAppColors.current.textMuted
val TextDim: Color @Composable get() = LocalAppColors.current.textDim
