package com.mtgcompanion.app.ui

import com.mtgcompanion.app.data.AccentTheme
import com.mtgcompanion.app.data.AppBrightness
import com.mtgcompanion.app.ui.theme.AppColors
import com.mtgcompanion.app.ui.theme.buildAppColors
import com.mtgcompanion.app.ui.theme.contrastRatio
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * WCAG AA contrast for every text and icon colour pair in both themes, for every accent. The web
 * app checks the same pairs on its tokens — tests/a11y/contrast.test.ts — and the two palettes
 * carry the same values. A failure names the pair: fix the colour in Color.kt, not the one screen.
 */
class ColorContrastTest {

    private class Pair(val fg: String, val bg: String, val min: Double, val what: String)

    private fun AppColors.named(name: String): Color = when (name) {
        "bg" -> bg
        "surface" -> surface
        "surface2" -> surface2
        "surface3" -> surface3
        "textPrimary" -> textPrimary
        "textMuted" -> textMuted
        "textDim" -> textDim
        "accent" -> accent
        "onAccent" -> onAccent
        "success" -> success
        "warning" -> warning
        "error" -> error
        "cut" -> cut
        else -> throw IllegalArgumentException("unknown colour $name")
    }

    private val grounds = listOf("bg", "surface", "surface2")

    private val pairs: List<Pair> =
        // Body text on the ground, cards and raised grey.
        listOf("textPrimary", "textMuted", "textDim", "accent", "success", "warning", "error", "cut").flatMap { fg ->
            grounds.map { bg -> Pair(fg, bg, 4.5, "text") }
        } +
            listOf(Pair("textPrimary", "surface3", 4.5, "text"), Pair("textMuted", "surface3", 4.5, "text")) +
            // Icons, borders of controls and large figures on the raised grey.
            listOf("textDim", "accent", "success", "warning", "error", "cut").map { Pair(it, "surface3", 3.0, "icon") } +
            listOf(
                Pair("onAccent", "accent", 4.5, "text on fill"),
                // The selected chip: the ground's colour on the main text colour.
                Pair("bg", "textPrimary", 4.5, "selected chip")
            )

    private fun check(brightness: AppBrightness) {
        val failures = mutableListOf<String>()
        AccentTheme.entries.forEach { accent ->
            val colors = buildAppColors(brightness, accent)
            pairs.forEach { p ->
                val ratio = contrastRatio(colors.named(p.fg), colors.named(p.bg))
                if (ratio < p.min) failures += "$brightness $accent: ${p.fg} on ${p.bg} (${p.what}) is ${"%.2f".format(ratio)}:1, needs ${p.min}:1"
            }
        }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun `dark theme meets WCAG AA for every accent`() = check(AppBrightness.DARK)

    @Test
    fun `light theme meets WCAG AA for every accent`() = check(AppBrightness.LIGHT)
}
