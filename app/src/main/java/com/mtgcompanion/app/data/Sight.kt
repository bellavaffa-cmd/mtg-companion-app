package com.mtgcompanion.app.data

/**
 * What the card index (CardIndex.kt) says about a scanned card, turned into decisions: which printing
 * of a name the card in hand is, and — when its title couldn't be read — which card it is at all.
 * The thresholds were set on 199 photos of real cards and 120 of things that aren't (bare table,
 * half a card) against the full index of 101,312 pictures (MtgCompanionWeb/tools/card-index, 8-bit
 * model): real cards always found the right picture of their own name, by as little as 0.03; known
 * by sight alone, the rule below takes 182 of the 199 cards and none of the 120 others. Mirrors the
 * web app's src/scan/sight.ts.
 */

/** Among a name's printings, the winner must beat the nearest *other picture* by this much. */
const val PICTURE_MARGIN = 0.03f

/** A card known by sight alone must look at least this much like its picture... */
const val SIGHT_SCORE = 0.72f

/** ...and stand this far clear of every other card's name. */
const val SIGHT_NAME_MARGIN = 0.12f

data class SightPick(
    val entry: IndexEntry,
    /**
     * Whether this is certainly the printing: false when other printings share its very picture (the
     * same art in the same frame, reprinted), which only the small print can tell apart.
     */
    val certain: Boolean
)

/**
 * Which of a name's printings the card is, from [matches] — the nearest of that name's printings,
 * best first. Null when two different pictures are too close to call.
 */
fun printingBySight(matches: List<IndexMatch>): SightPick? {
    val best = matches.firstOrNull() ?: return null
    val rival = matches.firstOrNull { it.entry.group != best.entry.group }
    if (rival != null && best.score - rival.score < PICTURE_MARGIN) return null
    return SightPick(best.entry, certain = matches.drop(1).none { it.entry.group == best.entry.group })
}

/**
 * Which printing, when a set code was read as well: among that set's printings of the name
 * ([inSet]) — unless none of them looks as much like the card as the name's best printing overall
 * ([named]) does, and the set code was misread. Within the set, versions too alike to call still
 * give its best, as a guess.
 */
fun choosePrinting(named: List<IndexMatch>, inSet: List<IndexMatch>): SightPick? {
    val setBest = inSet.firstOrNull()
    val nameBest = named.firstOrNull()
    if (setBest != null && (nameBest == null || setBest.score >= nameBest.score - PICTURE_MARGIN)) {
        return printingBySight(inSet) ?: SightPick(setBest.entry, certain = false)
    }
    return printingBySight(named)
}

/**
 * How far the printing the small print named may look less like the card than its name's best
 * printing does, before the small print is taken to be misread: a set code and number misread as
 * another real printing of the same card (ZNR 381 read as TRK 319) passes every other check.
 */
const val SMALL_PRINT_SLACK = 0.06f

/**
 * Whether what the card looks like bears out the printing its small print named ([printing], its
 * likeness; null when that printing isn't in the index, and there's nothing to say against it).
 */
fun smallPrintAgrees(printing: IndexMatch?, named: List<IndexMatch>): Boolean {
    val best = named.firstOrNull()
    if (printing == null || best == null) return true
    return printing.entry.group == best.entry.group || printing.score >= best.score - SMALL_PRINT_SLACK
}

/** The same card's name: either face of a double-faced card counts. */
fun sameCard(a: String, b: String): Boolean {
    val fa = a.lowercase().split(" // ")
    return a.equals(b, ignoreCase = true) || b.lowercase().split(" // ").any { it in fa }
}

/**
 * The card [anywhere] (the nearest pictures over the whole index, best first) says this is, when it
 * says so clearly enough to act on without the title — or null.
 */
fun cardBySight(anywhere: List<IndexMatch>): IndexEntry? {
    val best = anywhere.firstOrNull() ?: return null
    if (best.score < SIGHT_SCORE) return null
    val otherName = anywhere.firstOrNull { !sameCard(it.entry.name, best.entry.name) }
    // Every one of the nearest being this card is as clear as it gets.
    if (otherName != null && best.score - otherName.score < SIGHT_NAME_MARGIN) return null
    return best.entry
}

/**
 * Whether the card's look plainly says it's some other card than the title was read as: the card's
 * own name's best picture is far behind a clear match to another name. A title read from a blurred
 * or half-covered strip is fuzzily matched to a real card, and this is how a wrong one shows.
 */
fun looksLikeAnotherCard(readName: String, named: List<IndexMatch>, anywhere: List<IndexMatch>): IndexEntry? {
    val sight = cardBySight(anywhere) ?: return null
    if (sameCard(sight.name, readName)) return null
    val sightScore = anywhere.first().score
    val own = named.firstOrNull()?.score ?: -1f
    return if (sightScore - own >= SIGHT_NAME_MARGIN) sight else null
}
