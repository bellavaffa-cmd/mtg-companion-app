package com.mtgcompanion.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtgcompanion.app.data.AccentTheme
import com.mtgcompanion.app.data.AppBrightness
import com.mtgcompanion.app.data.SettingsRepository

private fun mtgColorScheme(colors: AppColors): ColorScheme {
    val base = if (colors.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = colors.accent,
        onPrimary = colors.onAccent,
        primaryContainer = colors.accentGlow,
        onPrimaryContainer = colors.textPrimary,
        secondary = colors.accentLight,
        onSecondary = colors.onAccent,
        secondaryContainer = colors.surface3,
        onSecondaryContainer = colors.textPrimary,
        background = colors.bg,
        onBackground = colors.textPrimary,
        surface = colors.surface,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.surface2,
        onSurfaceVariant = colors.textMuted,
        surfaceContainerLowest = colors.bg,
        surfaceContainerLow = colors.surface,
        surfaceContainer = colors.surface,
        surfaceContainerHigh = colors.surface2,
        surfaceContainerHighest = colors.surface3,
        outline = colors.surface3,
        outlineVariant = colors.border,
        error = colors.error,
        // Snackbars draw on inverseSurface. Material's inverse is the opposite brightness, but this
        // app's typography bakes in textPrimary, so keep the bar on-theme and raised instead.
        inverseSurface = colors.surface3,
        inverseOnSurface = colors.textPrimary,
        inversePrimary = colors.accent
    )
}

/**
 * Manrope for everything you read, in sentence case — no more letter-spaced engraved capitals on
 * every label. Figures that deserve weight use [BebasNumbers] directly via [NumberStyle].
 */
private fun mtgTypography(colors: AppColors) = Typography(
    headlineMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.6).sp, color = colors.textPrimary),
    headlineSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 29.sp, letterSpacing = (-0.4).sp, color = colors.textPrimary),
    titleLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 27.sp, letterSpacing = (-0.3).sp, color = colors.textPrimary),
    // Section headers ("Your decks", "Creature (29)") — bold, not tiny spaced caps.
    titleMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.1).sp, color = colors.textPrimary),
    titleSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp, color = colors.textPrimary),
    // Buttons, tabs, bar titles.
    labelLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.sp),
    // Secondary labels and meta lines.
    labelMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.1.sp, color = colors.textMuted),
    labelSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.3.sp),
    bodyLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 25.sp, color = colors.textPrimary),
    bodyMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp, color = colors.textPrimary),
    bodySmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 19.sp, color = colors.textMuted)
)

/** Big figures — counts, prices, totals — in the life counter's condensed face. */
fun NumberStyle(size: Int) = TextStyle(fontFamily = BebasNumbers, fontSize = size.sp, lineHeight = (size * 1.02f).sp, letterSpacing = 0.3.sp)

/** Uppercase micro-label ("CONTINUE BUILDING"), used sparingly above headings. */
val EyebrowStyle = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.3.sp)

private val mtgShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MtgCompanionTheme(settingsRepository: SettingsRepository, content: @Composable () -> Unit) {
    val setting by settingsRepository.appBrightness.collectAsState(initial = AppBrightness.DEFAULT)
    val accent by settingsRepository.accentTheme.collectAsState(initial = AccentTheme.DEFAULT)
    // SYSTEM defers to the device's own dark-mode setting rather than a fixed choice.
    val systemDark = isSystemInDarkTheme()
    val brightness = if (setting == AppBrightness.SYSTEM) {
        if (systemDark) AppBrightness.DARK else AppBrightness.LIGHT
    } else setting
    val colors = remember(brightness, accent) { buildAppColors(brightness, accent) }

    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(
            colorScheme = mtgColorScheme(colors),
            typography = mtgTypography(colors),
            shapes = mtgShapes,
            content = content
        )
    }
}
