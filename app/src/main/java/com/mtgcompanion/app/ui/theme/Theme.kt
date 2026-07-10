package com.mtgcompanion.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ManaOrange = Color(0xFFF2A93B)
private val DeepPurple = Color(0xFF1B1035)

private val DarkColors = darkColorScheme(
    primary = ManaOrange,
    background = DeepPurple,
    surface = Color(0xFF241748)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6A3FBF),
    background = Color(0xFFFAF8FF),
    surface = Color.White
)

@Composable
fun MtgCompanionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
