package com.mtgcompanion.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.sp
import com.mtgcompanion.app.data.AccentTheme
import com.mtgcompanion.app.data.AppBrightness
import com.mtgcompanion.app.data.SettingsRepository

private fun mtgColorScheme(colors: AppColors, brightness: AppBrightness): ColorScheme =
    if (brightness == AppBrightness.DARK) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.bg,
            secondary = colors.accentLight,
            background = colors.bg,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surface2,
            onSurfaceVariant = colors.textMuted,
            outline = colors.border,
            error = colors.error
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.bg,
            secondary = colors.accentLight,
            background = colors.bg,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surface2,
            onSurfaceVariant = colors.textMuted,
            outline = colors.border,
            error = colors.error
        )
    }

private fun mtgTypography(colors: AppColors) = Typography(
    titleLarge = TextStyle(
        fontFamily = Cinzel,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = 1.2.sp,
        color = colors.accentLight
    ),
    titleMedium = TextStyle(
        fontFamily = Cinzel,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        letterSpacing = 2.4.sp,
        color = colors.accentDim
    ),
    labelLarge = TextStyle(
        fontFamily = Cinzel,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 1.8.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Cinzel,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        letterSpacing = 1.4.sp,
        color = colors.textDim
    ),
    bodyLarge = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        color = colors.textPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = colors.textPrimary
    ),
    bodySmall = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Light,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = colors.textMuted
    )
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
            colorScheme = mtgColorScheme(colors, brightness),
            typography = mtgTypography(colors),
            content = content
        )
    }
}
