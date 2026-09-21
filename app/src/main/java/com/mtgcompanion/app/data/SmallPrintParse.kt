package com.mtgcompanion.app.data

/**
 * The small print at the bottom left of cards since 2015: the set code, then a bullet and the
 * two-letter language ("MSC • EN"), and the collector number after the rarity letter ("U 0211") or
 * as "number/total" ("0211/0280"). Mirrors parseSetAndNumber in the web app's src/scan/scanLogic.ts.
 *
 * The bullet is a few pixels wide, and the reader drops it as often as not — "FRC • EN ▸ Titus
 * Lunter" came back as "FRC ENTTUS LUNTER", bullet gone and the artist run on. Requiring the bullet
 * threw away a set code that had been read perfectly well, so it's optional now; what keeps the
 * match honest is that the language has to be one that's actually printed on cards. And a printing
 * read wrong can't slip through anyway: it's only kept if it names the card the title read.
 */

/** The languages printed in the small print. */
private const val LANGUAGES = "EN|DE|ES|FR|IT|JA|JP|KO|KR|PT|RU|CS|CT|ZH|PH"

/**
 * A set code, then a bullet the reader saw as any mark at all, or none; then a printed language.
 * The artist's name often runs straight on after it, so nothing is asked of what follows.
 */
private val SET_LANG = Regex("\\b([A-Z0-9]{3,5})(?:\\s*[^\\sA-Za-z0-9]\\s*|\\s+)(?:$LANGUAGES)(?![a-z])")

/**
 * The rarity letter — sometimes read twice over ("Cc"), or lowercase ("u") — then the collector
 * number, whose zeros can come out as the letter o ("oo21"): o counts as a zero, but only in a
 * number with a real digit in it.
 */
private val RARITY_NUMBER = Regex("\\b(?:[CURMSPLT][a-z]?|[curmsplt])\\s+((?=[0-9Oo]*\\d)[0-9Oo]{1,4})\\b")

private val SLASH_NUMBER = Regex("\\b(\\d{1,4})\\s*/\\s*\\d{1,4}\\b")

/**
 * The exact printing from the small print's lines of text: its set code (as read) and collector
 * number (leading zeros dropped), or null when either can't be read with confidence — the card is
 * then found by name.
 */
/**
 * Just the set code from the small print, whether or not the collector number read. The set code is
 * short and bold and reads far more often than the number beside it; on its own it narrows a card's
 * printings to the few in that set, and the card's look can settle which of those it is.
 */
fun parseSetCode(lines: List<String>): String? =
    lines.firstNotNullOfOrNull { SET_LANG.find(it)?.groupValues?.get(1) }

fun parseSetAndNumber(lines: List<String>): Pair<String, String>? {
    val setCode = lines.firstNotNullOfOrNull { SET_LANG.find(it)?.groupValues?.get(1) } ?: return null
    val number = lines.firstNotNullOfOrNull { RARITY_NUMBER.find(it)?.groupValues?.get(1) }
        ?: lines.firstNotNullOfOrNull { SLASH_NUMBER.find(it)?.groupValues?.get(1) }
        ?: return null
    return setCode to number.replace('O', '0').replace('o', '0').trimStart('0').ifEmpty { "0" }
}
