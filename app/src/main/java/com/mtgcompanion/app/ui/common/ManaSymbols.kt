package com.mtgcompanion.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private val symbolTokenRegex = Regex("\\{[^}]+\\}")

/**
 * Renders [text] with every embedded Scryfall symbol token (`{T}`, `{1}{U}`, `{E}`, `{G/P}`, ...)
 * swapped for its actual icon instead of showing the raw braces — the same treatment
 * Scryfall/Gatherer give oracle text, rulings, and keyword reminder text. Symbols are inlined at
 * their natural spot in the sentence via [InlineTextContent] so wrapping still reads correctly.
 * Falls back to a plain [Text] when there's nothing to substitute.
 */
@Composable
fun InlineManaText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    val matches = remember(text) { symbolTokenRegex.findAll(text).toList() }
    if (matches.isEmpty()) {
        Text(text, modifier = modifier, style = style, color = color)
        return
    }
    val symbolSize: TextUnit = if (style.fontSize != TextUnit.Unspecified) style.fontSize else 14.sp
    val annotated = buildAnnotatedString {
        var last = 0
        matches.forEach { m ->
            append(text.substring(last, m.range.first))
            val code = m.value.removePrefix("{").removeSuffix("}")
            appendInlineContent(code, m.value)
            last = m.range.last + 1
        }
        append(text.substring(last))
    }
    val inlineContent = matches.associate { m ->
        val code = m.value.removePrefix("{").removeSuffix("}")
        code to InlineTextContent(
            Placeholder(symbolSize, symbolSize, PlaceholderVerticalAlign.TextCenter)
        ) {
            AsyncImage(
                model = manaSymbolUrl(code),
                contentDescription = m.value,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
    Text(text = annotated, modifier = modifier, style = style, color = color, inlineContent = inlineContent)
}
