package com.mtgcompanion.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Scryfall serves every mana/cost symbol as an SVG named after its contents with braces and
 * slashes stripped: {W} -> W.svg, {U/B} -> UB.svg, {2} -> 2.svg, {T} -> T.svg. Our colour
 * distribution uses the word "Colorless", which maps to the generic {C} symbol.
 */
private fun manaSymbolUrl(code: String): String {
    val symbol = when (code.uppercase()) {
        "COLORLESS" -> "C"
        else -> code.uppercase().replace("/", "")
    }
    return "https://svgs.scryfall.io/card-symbols/$symbol.svg"
}

/**
 * A single mana symbol (e.g. "W", "U", "Colorless", "2/W") rendered as its Scryfall logo.
 * The image is pinned inside a fixed-size, clipped box so an SVG never draws past its bounds
 * into neighbouring text.
 */
@Composable
fun ManaSymbol(code: String, size: Dp = 14.dp, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(size).clipToBounds()) {
        AsyncImage(
            model = manaSymbolUrl(code),
            contentDescription = code,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** Renders a Scryfall mana-cost string like "{1}{U}{B}" as a row of mana symbols. */
@Composable
fun ManaCost(cost: String, size: Dp = 16.dp, modifier: Modifier = Modifier) {
    val symbols = Regex("\\{([^}]+)\\}").findAll(cost).map { it.groupValues[1] }.toList()
    if (symbols.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = modifier) {
        symbols.forEach { ManaSymbol(it, size = size) }
    }
}
