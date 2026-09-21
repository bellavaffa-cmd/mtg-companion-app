package com.mtgcompanion.app.data

/**
 * What the card index (CardIndex.kt) says about a scanned card, turned into decisions: which printing
 * of a name the card in hand is, and — when its title couldn't be read — which card it is at all.
 * The thresholds were set on photos of real cards against the full index of 101,312 pictures
 * (MtgCompanionWeb/tools/card-index): real cards cleared the nearest other name by 0.07 or more (95%
 * by 0.115), the nearest other picture of their own name by 0.1 or more; crops of bare table and
 * half-cards mostly by about 0.01, but by as much as 0.15. Mirrors the web app's src/scan/sight.ts.
 */

/** Among a name's printings, the winner must beat the nearest *other picture* by this much. */
const val PICTURE_MARGIN = 0.05f

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
