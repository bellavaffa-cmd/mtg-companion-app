package com.mtgcompanion.app.data

import java.text.Normalizer

// The printing picker's search field ("Best guess · pick art", Change printing, Sort a pile's
// "Wrong card? › Pick the printing"): a card's printings narrowed to what's typed — a set's name
// ("final fan", "dusk"), its code ("fin", "PLST"), a collector number ("306", "0306"), set and number
// together ("fin 306", "FIN·306", "fin#306"), or the year it came out ("2025"). The same cases run
// in the web app (src/scan/printingSearch.ts), from printingSearchVectors.json.

/** What the search looks at in one printing. [released] is Scryfall's released_at, "2025-06-13". */
data class PrintingSearchFacts(val set: String?, val setName: String?, val number: String?, val released: String?)

/** How well a printing matches, best first; printings in one group keep the order they came in. */
object PrintingMatchGroup {
    const val EXACT_CODE = 0
    const val CODE_PREFIX = 1
    const val SET_NAME = 2
    const val NUMBER = 3
    const val YEAR = 4
}

private val ACCENTS = Regex("\\p{Mn}+")
private val NOT_ALNUM = Regex("[^a-z0-9]+")
private val LEADING_ZEROS = Regex("^0+(?=\\d)")
private val NUMBER_LIKE = Regex("^\\d+[a-z]*$")
private val YEAR = Regex("^(19|20)\\d\\d$")
private val SET_CODE = Regex("^[a-z0-9]{2,6}$")

/** Lower case, accents dropped, anything but letters and digits a single space: "Lim-Dûl" → "lim dul". */
fun foldPrintingText(s: String?): String =
    Normalizer.normalize(s ?: "", Normalizer.Form.NFD)
        .replace(ACCENTS, "")
        .lowercase()
        .replace(NOT_ALNUM, " ")
        .trim()

/** A collector number to compare: folded, no spaces, no leading zeros ("0306" → "306", "306★" → "306"). */
private fun foldNumber(s: String?): String = foldPrintingText(s).replace(" ", "").replace(LEADING_ZEROS, "")

/** The query split into folded words: "FIN·306" → ["fin", "306"]. */
fun printingQueryTokens(query: String): List<String> = foldPrintingText(query).split(' ').filter { it.isNotEmpty() }

/** The group [facts] falls in for the folded query [tokens], or null when it doesn't match. */
fun printingSearchGroup(facts: PrintingSearchFacts, tokens: List<String>): Int? {
    if (tokens.isEmpty()) return null
    val code = foldPrintingText(facts.set).replace(" ", "")
    val name = foldPrintingText(facts.setName)
    val number = foldNumber(facts.number)
    if (tokens.size == 1) {
        val t = tokens[0]
        return when {
            code.isNotEmpty() && code == t -> PrintingMatchGroup.EXACT_CODE
            code.isNotEmpty() && code.startsWith(t) -> PrintingMatchGroup.CODE_PREFIX
            name.contains(t) -> PrintingMatchGroup.SET_NAME
            number.isNotEmpty() && foldNumber(t) == number -> PrintingMatchGroup.NUMBER
            YEAR.matches(t) && (facts.released ?: "").startsWith(t) -> PrintingMatchGroup.YEAR
            else -> null
        }
    }
    // A set and a number: "fin 306", "final fantasy 306".
    val last = tokens.last()
    if (NUMBER_LIKE.matches(last) && number.isNotEmpty() && foldNumber(last) == number) {
        val head = tokens.dropLast(1).joinToString(" ")
        if (tokens.size == 2 && code.isNotEmpty() && code == head) return PrintingMatchGroup.EXACT_CODE
        if (tokens.size == 2 && code.isNotEmpty() && code.startsWith(head)) return PrintingMatchGroup.CODE_PREFIX
        if (name.contains(head)) return PrintingMatchGroup.SET_NAME
    }
    // Several words of a set's name: "final fantasy", "magic 2010".
    return if (name.contains(tokens.joinToString(" "))) PrintingMatchGroup.SET_NAME else null
}

/**
 * [items] that match [query], best first: an exact set code, then a code it begins, then a set's
 * name, then a collector number, then a year — each group in [items]' own order. A blank query
 * keeps them all, as they are.
 */
fun <T> searchPrintings(items: List<T>, query: String, facts: (T) -> PrintingSearchFacts): List<T> {
    val tokens = printingQueryTokens(query)
    if (tokens.isEmpty()) return items
    return items.mapIndexedNotNull { at, item -> printingSearchGroup(facts(item), tokens)?.let { Triple(item, it, at) } }
        .sortedWith(compareBy({ it.second }, { it.third }))
        .map { it.first }
}

/** A set code typed into the search, with the number after it, if there was one. */
data class SetCodeQuery(val set: String, val number: String?)

/**
 * The set code [query] could name, with the number after it — what to ask Scryfall for when the
 * printings loaded so far have no match (a card with so many printings they aren't all in yet).
 * Null for anything that can't be a set code: a code has a letter in it and is 2 to 6 long.
 */
fun setCodeQuery(query: String): SetCodeQuery? {
    val tokens = printingQueryTokens(query)
    if (tokens.isEmpty() || tokens.size > 2) return null
    val set = tokens[0]
    val number = tokens.getOrNull(1)
    if (!SET_CODE.matches(set) || set.none { it in 'a'..'z' }) return null
    if (number != null && !NUMBER_LIKE.matches(number)) return null
    return SetCodeQuery(set, number)
}
