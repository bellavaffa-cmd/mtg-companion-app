package com.mtgcompanion.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// mtgoracle.gg has a single dark, gold-on-black identity with no light variant -
// the app follows that rather than the system light/dark setting.
private val MtgOracleColors = darkColorScheme(
    primary = Gold,
    onPrimary = Bg,
    secondary = GoldLight,
    background = Bg,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextMuted,
    outline = BorderColor,
    error = Color(0xFFCF6659)
)

private val MtgOracleTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = Cinzel,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = 1.2.sp,
        color = GoldLight
    ),
    titleMedium = TextStyle(
        fontFamily = Cinzel,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        letterSpacing = 2.4.sp,
        color = GoldDim
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
        color = TextDim
    ),
    bodyLarge = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        color = TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = TextPrimary
    ),
    bodySmall = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Light,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = TextMuted
    )
)

@Composable
fun MtgCompanionTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MtgOracleColors, typography = MtgOracleTypography, content = content)
}
