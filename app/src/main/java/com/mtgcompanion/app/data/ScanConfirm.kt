package com.mtgcompanion.app.data

/**
 * When a camera read counts as a card. A fuzzy lookup answers half a title with a real card —
 * "Lightning B" comes back as Lightning Bolt — so before a card joins the list, the read has to
 * account for the whole name. Mirrors the web app's confirmRead in src/scan/scanLogic.ts.
 */

/**
 * Reads of the same title in a row before the card is looked up. A card halfway into the frame, or
 * caught mid-motion, rarely reads the same three times running.
 */
const val STEADY_READS = 3

/**
 * How careful the scanner is, chosen on the scan screen. [ACCURATE] is how it has always been: a
 * name has to read the same on [STEADY_READS] frames running, the small print is read up close for
 * the exact printing, and when that can't be read the art is matched against every printing. [FAST]
 * takes a name after two reads and does neither: the card comes in as its usual printing, unless
 * the frame itself happened to show the set code — a best guess, changed with a tap on the row or
 * later with "change printing". The art match fetches every printing of the card, and on a quick
 * pile those fetches queued in front of the next card's lookup; Fast is for getting through a pile.
 * Mirrors the web app's ScanMode in src/scan/scanLogic.ts.
 */
enum class ScanMode(val label: String, val steadyReads: Int, val readsSmallPrint: Boolean, val matchesArt: Boolean) {
    ACCURATE("Accurate", STEADY_READS, true, true),
    FAST("Fast", 2, false, false);

    companion object {
        fun fromName(name: String?): ScanMode = entries.firstOrNull { it.name == name } ?: ACCURATE
    }
}

/**
 * What came back for a read title: the card itself, a piece of a card's name (the card wasn't all
 * in the frame, or its title was cut off), or a different card altogether.
 */
enum class Confirmation { YES, PARTIAL, DIFFERENT }

private fun letters(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

/**
 * Whether the card a lookup found is really the card that was read. [flavorName] is the name
 * printed large on a Universes Beyond card — "Kefka's Tower" over "Bolas's Citadel" — and is what
 * the camera reads, so a card answering to it has been read correctly.
 */
fun confirmRead(title: String, cardName: String, flavorName: String? = null): Confirmation {
    val names = listOfNotNull(cardName, flavorName)
    val answers = names.map { against(title, it) }
    return when {
        Confirmation.YES in answers -> Confirmation.YES
        Confirmation.PARTIAL in answers -> Confirmation.PARTIAL
        else -> Confirmation.DIFFERENT
    }
}

private fun against(title: String, cardName: String): Confirmation {
    val read = letters(title)
    // A double-faced card is named by its front: "Delver of Secrets // Insectile Aberration".
    val name = letters(cardName.substringBefore(" // "))
    if (read.isEmpty() || name.isEmpty()) return Confirmation.DIFFERENT
    if (read == name) return Confirmation.YES
    // A letter or two misread across a full-length name is still that card.
    if (kotlin.math.abs(read.length - name.length) <= 2 && distanceWithin(read, name, maxOf(1, name.length / 8))) {
        return Confirmation.YES
    }
    // Anything less than the whole name is a card that wasn't all in the frame.
    if (name.contains(read) || read.contains(name)) return Confirmation.PARTIAL
    return Confirmation.DIFFERENT
}

/** Edit distance, stopping early once it passes [limit]. */
private fun distanceWithin(a: String, b: String, limit: Int): Boolean {
    if (kotlin.math.abs(a.length - b.length) > limit) return false
    var previous = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val row = IntArray(b.length + 1)
        row[0] = i
        var best = i
        for (j in 1..b.length) {
            row[j] = minOf(previous[j] + 1, row[j - 1] + 1, previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            best = minOf(best, row[j])
        }
        if (best > limit) return false
        previous = row
    }
    return previous[b.length] <= limit
}
