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
 *
 * The number sits on the line above the set code ("L 0306   FFIX" over "FIN • EN"), or on the same
 * line when the reader runs the two together. That is how the two are paired: a frame that holds a
 * second card's small print too (a pile, the card underneath peeking out) used to pair one card's set
 * code with the other card's number, simply because each was the first of its kind read.
 */

/** The languages printed in the small print. */
private const val LANGUAGES = "EN|DE|ES|FR|IT|JA|JP|KO|KR|PT|RU|CS|CT|ZH|PH"

/**
 * A set code, then a bullet the reader saw as any mark at all, or none; then a printed language.
 * The artist's name often runs straight on after it, so nothing is asked of what follows.
 */
private val SET_LANG = Regex("\\b([A-Z0-9]{3,5})(?:\\s*[^\\sA-Za-z0-9]\\s*|\\s+)(?:$LANGUAGES)(?![a-z])")

/**
 * The same read in lowercase ("dsk • en") — only with a mark between the two, since without one
 * any short word before "en" or "de" would pass for a set code.
 */
private val SET_LANG_LOWER = Regex("\\b([A-Za-z0-9]{3,5})\\s*[^\\sA-Za-z0-9]\\s*(?:${LANGUAGES.lowercase()})(?![a-z])")

/**
 * The rarity letter — sometimes read twice over ("Cc"), or lowercase ("u") — then the collector
 * number, whose zeros can come out as the letter o ("oo21"): o counts as a zero, but only in a
 * number with a real digit in it. Up to four digits (Secret Lair runs into the thousands), with the
 * letter or star a promo's number ends in ("0123p", "0045s", "0001★").
 */
private val RARITY_NUMBER = Regex("\\b(?:[CURMSPLT][a-z]?|[curmsplt])\\s+((?=[0-9Oo]*\\d)[0-9Oo]{1,4}(?:[psabcde★](?![A-Za-z0-9]))?)(?![A-Za-z0-9])")

private val SLASH_NUMBER = Regex("\\b(\\d{1,4}[psab★]?)\\s*/\\s*\\d{1,4}\\b")

/** A set code read off one line: as printed, uppercase. */
private fun setOnLine(line: String): String? =
    (SET_LANG.find(line) ?: SET_LANG_LOWER.find(line))?.groupValues?.get(1)?.uppercase()

/** A collector number read off one line: zeros read as o put right, leading zeros dropped. */
private fun numberOnLine(line: String): String? {
    val raw = RARITY_NUMBER.find(line)?.groupValues?.get(1) ?: SLASH_NUMBER.find(line)?.groupValues?.get(1) ?: return null
    val digits = raw.takeWhile { it.isDigit() || it == 'O' || it == 'o' }.replace('O', '0').replace('o', '0').trimStart('0').ifEmpty { "0" }
    return digits + raw.drop(raw.takeWhile { it.isDigit() || it == 'O' || it == 'o' }.length)
}

/**
 * Every printing the small print in [lines] names: each set code with the number on its own line or
 * the line just above it. More than one means more than one card's small print was read.
 */
fun smallPrintReadings(lines: List<String>): List<Pair<String, String>> {
    val out = LinkedHashSet<Pair<String, String>>()
    for ((i, line) in lines.withIndex()) {
        val set = setOnLine(line) ?: continue
        val number = numberOnLine(line) ?: lines.getOrNull(i - 1)?.let { numberOnLine(it) } ?: continue
        out += set to number
    }
    return out.toList()
}

/** How many lines in [lines] carry a set code — more than one, and two cards' small print is in view. */
fun setLineCount(lines: List<String>): Int = lines.count { setOnLine(it) != null }

/**
 * Just the set code from the small print, whether or not the collector number read. The set code is
 * short and bold and reads far more often than the number beside it; on its own it narrows a card's
 * printings to the few in that set, and the card's look can settle which of those it is. Null when two
 * different set codes were read — two cards, and no telling which is which.
 */
fun parseSetCode(lines: List<String>): String? =
    lines.mapNotNull { setOnLine(it) }.distinct().singleOrNull()

/**
 * The exact printing from the small print's lines of text: its set code (uppercase) and collector
 * number (leading zeros dropped), or null when either can't be read with confidence — the card is
 * then found by name. Set code and number come from the same card's small print (see
 * [smallPrintReadings]); two different printings read is no printing. When the two didn't sit
 * together, they're still paired if each was read once and only once.
 */
fun parseSetAndNumber(lines: List<String>): Pair<String, String>? {
    val readings = smallPrintReadings(lines)
    if (readings.size == 1) return readings.single()
    if (readings.size > 1) return null
    val set = lines.mapNotNull { setOnLine(it) }.distinct().singleOrNull() ?: return null
    val number = lines.mapNotNull { numberOnLine(it) }.distinct().singleOrNull() ?: return null
    return set to number
}

/** [number] without the letter or star a promo's number ends in — "123p" is tried as "123" too. */
fun plainNumber(number: String): String = number.trimEnd { !it.isDigit() }

/** Whether two collector numbers are the same printing's: leading zeros and case don't count. */
fun sameNumber(a: String, b: String): Boolean =
    a.trimStart('0').ifEmpty { "0" }.equals(b.trimStart('0').ifEmpty { "0" }, ignoreCase = true)

/** Whether two set-and-number reads name the same printing. */
fun samePrinting(a: Pair<String, String>, b: Pair<String, String>): Boolean =
    a.first.equals(b.first, ignoreCase = true) && sameNumber(a.second, b.second)

/**
 * A printing read off the small print on its own — with the title unread — is only acted on once the
 * same one has been read twice running: one read is too easily one misread digit.
 */
class SmallPrintStreak {
    private var last: Pair<String, String>? = null

    /** [read] (null when nothing read) seen now: the printing, once it's the same as last time. */
    fun see(read: Pair<String, String>?): Pair<String, String>? {
        val before = last
        last = read
        return if (read != null && before != null && samePrinting(read, before)) read else null
    }

    fun reset() { last = null }
}

/**
 * Whether the small print read in view now is a *different* printing from the card just taken, so a
 * new card of the same name (a pile of basic lands) has come into view — not the same one lingering.
 * Only when [read] was read the same on the frame before as well ([previousRead]), and there's a
 * printing to compare with: the one read when the last card was looked up ([lookedUp]), or else
 * the printing it was added as ([added]).
 */
fun newPrintingInView(
    read: Pair<String, String>?,
    previousRead: Pair<String, String>?,
    lookedUp: Pair<String, String>?,
    added: Pair<String, String>?
): Boolean {
    if (read == null || previousRead == null || !samePrinting(read, previousRead)) return false
    val last = lookedUp ?: added ?: return false
    return !samePrinting(read, last)
}

/**
 * The printing read off a whole camera frame — the guide and the margin around it. Only when one
 * card's small print is in it: with two set lines (the card underneath peeking out of a pile), which
 * line is whose can't be told from the text, and the close read off the card's own edges decides.
 */
fun frameSetAndNumber(lines: List<String>): Pair<String, String>? =
    if (setLineCount(lines) > 1) null else parseSetAndNumber(lines)
