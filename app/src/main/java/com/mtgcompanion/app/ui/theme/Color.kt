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
    /** Text/icons drawn on top of a solid [accent] fill (dark ink on gold, white on sapphire…). */
    val onAccent: Color,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val border: Color,
    val borderBright: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val textDim: Color,
    val error: Color,
    val success: Color,
    val warning: Color,
    /** Cut candidates — an orange that reads as "on its way out" without being an error. */
    val cut: Color,
    val isDark: Boolean
)

private data class AccentHues(val base: Color, val light: Color, val dim: Color, val lightModeBase: Color)

// One hue per accent theme, mapped to Magic's colours of mana. Brighter than the old parchment-era
// values: on the neutral near-black ground the accent is used sparingly, so it can afford to glow.
// Gold's and Emerald's light-mode hues are a step darker than the first ones, so accent text keeps
// 4.5:1 on the light theme's raised grey (surface2) too. Same values as the web app's
// src/settings/appearance.ts; ColorContrastTest checks them.
private fun accentHues(theme: AccentTheme): AccentHues = when (theme) {
    AccentTheme.GOLD -> AccentHues(Color(0xFFE6B45E), Color(0xFFF2CB86), Color(0xFF8E6A2C), Color(0xFF8A5E18))
    AccentTheme.SAPPHIRE -> AccentHues(Color(0xFF5B9BF0), Color(0xFF86B8F6), Color(0xFF2F5C99), Color(0xFF2563C9))
    AccentTheme.AMETHYST -> AccentHues(Color(0xFFA77BE6), Color(0xFFC3A2F0), Color(0xFF5F3E91), Color(0xFF7B45C4))
    AccentTheme.RUBY -> AccentHues(Color(0xFFEE6A7C), Color(0xFFF594A1), Color(0xFF8E2E3C), Color(0xFFC0304A))
    AccentTheme.EMERALD -> AccentHues(Color(0xFF52C788), Color(0xFF83DBA9), Color(0xFF2B7A4F), Color(0xFF1A7348))
}

/** The accent's base hue, independent of brightness — for swatches/previews in the theme picker. */
fun accentPreviewColor(theme: AccentTheme): Color = accentHues(theme).base

private val DarkInk = Color(0xFF1C1405)

/**
 * Text on a solid accent fill: dark ink or white, whichever reads better. In the dark every accent
 * takes dark ink (white on Sapphire was 2.8:1); in the light every one takes white.
 */
internal fun inkFor(fill: Color): Color =
    if (contrastRatio(DarkInk, fill) >= contrastRatio(Color.White, fill)) DarkInk else Color.White

/** WCAG relative luminance of an sRGB colour (alpha ignored). */
internal fun wcagLuminance(c: Color): Double {
    fun channel(v: Float): Double = if (v <= 0.03928f) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
}

/** WCAG contrast ratio between two opaque colours, 1 to 21. */
internal fun contrastRatio(a: Color, b: Color): Double {
    val la = wcagLuminance(a)
    val lb = wcagLuminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

/** Builds the full palette for a brightness + accent combination. */
fun buildAppColors(brightness: AppBrightness, accent: AccentTheme): AppColors {
    val hues = accentHues(accent)
    return if (brightness == AppBrightness.DARK) {
        // Neutral near-black with a faint cool bias. Surfaces separate by tone, not by outlines.
        AppColors(
            accent = hues.base,
            accentLight = hues.light,
            accentDim = hues.dim,
            accentGlow = hues.base.copy(alpha = 0.16f),
            onAccent = inkFor(hues.base),
            bg = Color(0xFF0C0D11),
            surface = Color(0xFF16181E),
            surface2 = Color(0xFF20222A),
            surface3 = Color(0xFF2B2E38),
            border = Color.White.copy(alpha = 0.06f),
            borderBright = hues.base.copy(alpha = 0.45f),
            textPrimary = Color(0xFFF1EEE6),
            textMuted = Color(0xFFA7A8B3),
            // 4.5:1 on bg, surface and surface2 (was 0xFF6F717C, 3.3:1 on surface2).
            textDim = Color(0xFF8A8C97),
            error = Color(0xFFF07565),
            success = Color(0xFF5BCB8F),
            warning = Color(0xFFF3B64A),
            cut = Color(0xFFFF8A4C),
            isDark = true
        )
    } else {
        // Cool paper white rather than the old parchment; the accent darkens so it keeps contrast.
        AppColors(
            accent = hues.lightModeBase,
            accentLight = hues.lightModeBase,
            accentDim = hues.dim,
            accentGlow = hues.lightModeBase.copy(alpha = 0.12f),
            onAccent = inkFor(hues.lightModeBase),
            bg = Color(0xFFF4F4F7),
            surface = Color(0xFFFFFFFF),
            surface2 = Color(0xFFECEDF2),
            surface3 = Color(0xFFE0E2E9),
            border = Color.Black.copy(alpha = 0.07f),
            borderBright = hues.lightModeBase.copy(alpha = 0.5f),
            textPrimary = Color(0xFF15161B),
            textMuted = Color(0xFF555A66),
            // The quiet text and the status colours are a step darker than the first ones so their
            // text reads at 4.5:1 on bg, surface and surface2 (textDim was 3.6:1 on bg, success 4.0,
            // warning 3.3, cut 3.4). Mirrors the web's theme.css; ColorContrastTest checks them.
            textDim = Color(0xFF62666F),
            error = Color(0xFFC62F24),
            success = Color(0xFF1A7348),
            warning = Color(0xFF8A5C00),
            cut = Color(0xFFB04A10),
            isDark = false
        )
    }
}

val LocalAppColors = compositionLocalOf { buildAppColors(AppBrightness.DEFAULT, AccentTheme.DEFAULT) }

val Gold: Color @Composable get() = LocalAppColors.current.accent
val GoldLight: Color @Composable get() = LocalAppColors.current.accentLight
val GoldDim: Color @Composable get() = LocalAppColors.current.accentDim
val GoldGlow: Color @Composable get() = LocalAppColors.current.accentGlow
val OnGold: Color @Composable get() = LocalAppColors.current.onAccent

val Bg: Color @Composable get() = LocalAppColors.current.bg
val Surface: Color @Composable get() = LocalAppColors.current.surface
val Surface2: Color @Composable get() = LocalAppColors.current.surface2
val Surface3: Color @Composable get() = LocalAppColors.current.surface3

val BorderColor: Color @Composable get() = LocalAppColors.current.border

val TextPrimary: Color @Composable get() = LocalAppColors.current.textPrimary
val TextMuted: Color @Composable get() = LocalAppColors.current.textMuted
val TextDim: Color @Composable get() = LocalAppColors.current.textDim

val CutColor: Color @Composable get() = LocalAppColors.current.cut

/** Magic's five colours plus colorless, tuned to glow on the dark ground (identity strips, pips, fallback art). */
object ManaColors {
    val W = Color(0xFFE9DBA6)
    val U = Color(0xFF4583EE)
    val B = Color(0xFF8B68C4)
    val R = Color(0xFFE8583A)
    val G = Color(0xFF37A96F)
    val C = Color(0xFFA3ABB9)

    fun of(code: String): Color = when (code.uppercase()) {
        "W" -> W
        "U" -> U
        "B" -> B
        "R" -> R
        "G" -> G
        else -> C
    }
}
