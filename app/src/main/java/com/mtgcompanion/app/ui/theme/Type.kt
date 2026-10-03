package com.mtgcompanion.app.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.mtgcompanion.app.R

/**
 * Interface face. Manrope ships as one variable font (weights 200–800); each weight below points at
 * the same file with its own `wght` axis setting, so bold headings are real bold rather than synthesized.
 */
@OptIn(ExperimentalTextApi::class)
val Manrope = FontFamily(
    listOf(400, 500, 600, 700, 800).map { w ->
        Font(R.font.manrope, FontWeight(w), FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    }
)

/**
 * Numbers face — counts, prices, life totals. The life counter already uses it for its digits, so
 * the rest of the app borrowing it for figures makes the two feel like one product.
 */
val BebasNumbers = FontFamily(Font(R.font.bebas_neue))
