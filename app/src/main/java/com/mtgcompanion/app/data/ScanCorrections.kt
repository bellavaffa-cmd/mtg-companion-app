package com.mtgcompanion.app.data

// The scanner learns from your corrections. When a scanned card is changed to another printing (the
// scan list's "pick art", Sort a pile's "Wrong card?"), what the scanner read is remembered with what
// it should have been, so the same misread comes out right next time. Kept on the Unsorted pile as
// "scanCorrections" and synced like the sorting recipes. The web app's src/scan/scanCorrections.ts,
// rule for rule; both run scanCorrectionVectors.json (ScanCorrectionsTest).
//
// Two kinds:
//  - MISREAD: the read itself — the title as read, the set code and collector number as read (or, when
//    those weren't both read, the card the scanner came up with) — keyed exactly, and put right from
//    the first correction on.
//  - PRINTING: the name read right but the set unreadable (a basic land, a reprint), and the user picks
//    one printing of it. Only once they've picked the same printing twice in a row is it preferred, and
//    only when the set couldn't be read.

/** One thing the scanner has learned. [kind] is "MISREAD" or "PRINTING". The web app's ScanCorrection, field for field. */
data class ScanCorrection(
    /** See [misreadKey] and [printingKey]. */
    val key: String,
    val kind: String,
    /** The title as read ("Lightnin Bolt"). */
    val read: String,
    /** The set code as read, lowercase, when it was. */
    val readSet: String? = null,
    /** The collector number as read, when it was. */
    val readNumber: String? = null,
    /** What the scanner came up with. */
    val wrongId: String,
    val wrongName: String,
    /** What it should have been. */
    val scryfallId: String,
    val name: String,
    val set: String,
    val collectorNumber: String,
    /** How many times the user has made this correction (a PRINTING one counts from 2). */
    val count: Int = 1,
    /** How many times it has put a scan right. */
    val used: Int = 0,
    /** When it was last made or used, ms. */
    val lastUsed: Long = 0L
)

const val KIND_MISREAD = "MISREAD"
const val KIND_PRINTING = "PRINTING"

/** What the scanner read off a card, and what it made of it — before any correction. */
data class ScanReading(
    val read: String,
    val set: String?,
    val number: String?,
    val recognizedId: String,
    val recognizedName: String
)

/** A card a scan was corrected to. */
data class CardRef(val id: String, val name: String, val set: String, val collectorNumber: String)

/** A correction put to use on a scan. */
data class AppliedCorrection(val key: String, val kind: String, val scryfallId: String)

/** At most this many are kept; past it, the ones used longest ago go. */
const val MAX_CORRECTIONS = 500

/** A printing has to be picked this many times in a row before it's preferred. */
const val PRINTING_PICKS = 2

/** Letters and digits only, lowercase: how a read and a name are compared. */
fun normalizeRead(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

/** A double-faced card's front: "Delver of Secrets // Insectile Aberration" is Delver of Secrets. */
private fun front(name: String) = name.substringBefore(" // ")

fun sameScannedName(a: String, b: String): Boolean = normalizeRead(front(a)) == normalizeRead(front(b))

private fun ScanReading.setRead(): String? = set?.trim()?.lowercase()?.ifEmpty { null }
private fun ScanReading.numberRead(): String? = number?.trim()?.lowercase()?.ifEmpty { null }

/**
 * The key of a misread: the title as read with the set code and number when both were read — they
 * name the printing exactly — otherwise with whatever of the set was read and the card the scanner
 * came up with, so only that same wrong answer to that same read is put right.
 */
fun misreadKey(r: ScanReading): String {
    val title = normalizeRead(r.read)
    val set = r.setRead()
    val number = r.numberRead()
    return if (set != null && number != null) "$title|$set:$number" else "$title|${set ?: ""}|=${r.recognizedId}"
}

/** The key of a preferred printing: the card's name. */
fun printingKey(name: String): String = "~" + normalizeRead(front(name))

/** The correction for [r], if one has been learned: a misread first, then a preferred printing (set unread only). */
fun lookupCorrection(list: List<ScanCorrection>?, r: ScanReading): AppliedCorrection? {
    val all = list.orEmpty()
    val key = misreadKey(r)
    val misread = all.firstOrNull { it.key == key }
    if (misread != null && misread.scryfallId != r.recognizedId) return AppliedCorrection(key, KIND_MISREAD, misread.scryfallId)
    if (r.setRead() != null) return null
    val pkey = printingKey(r.recognizedName)
    val pref = all.firstOrNull { it.key == pkey }
    if (pref != null && pref.count >= PRINTING_PICKS && pref.scryfallId != r.recognizedId) return AppliedCorrection(pkey, KIND_PRINTING, pref.scryfallId)
    return null
}

/** Newest first; the key settles a tie. Capped at [MAX_CORRECTIONS]. */
fun capCorrections(list: List<ScanCorrection>): List<ScanCorrection> =
    list.sortedWith(compareByDescending<ScanCorrection> { it.lastUsed }.thenBy { it.key }).take(MAX_CORRECTIONS)

private fun correctionEntry(kind: String, key: String, r: ScanReading, card: CardRef, count: Int, used: Int, now: Long) = ScanCorrection(
    key = key, kind = kind, read = r.read.trim(), readSet = r.setRead(), readNumber = r.numberRead(),
    wrongId = r.recognizedId, wrongName = r.recognizedName,
    scryfallId = card.id, name = card.name, set = card.set, collectorNumber = card.collectorNumber,
    count = count, used = used, lastUsed = now
)

/** [key]'s entry made again: one more time if it's the same card, otherwise replaced. */
private fun upsertCorrection(list: List<ScanCorrection>, kind: String, key: String, r: ScanReading, card: CardRef, now: Long): List<ScanCorrection> {
    val was = list.firstOrNull { it.key == key }
    val same = was != null && was.scryfallId == card.id
    val next = correctionEntry(kind, key, r, card, if (same) was!!.count + 1 else 1, if (same) was!!.used else 0, now)
    return listOf(next) + list.filter { it.key != key }
}

/**
 * [list] after the user changed a scan read as [r] to [card]. [learned] is the key of the correction
 * that scan was put right by, if it was. Picking the card the scanner came up with un-corrects: a
 * learned correction goes, a misread is dropped, and a printing picked once before counts one less.
 */
fun recordCorrection(list: List<ScanCorrection>?, r: ScanReading, card: CardRef, now: Long, learned: String? = null): List<ScanCorrection> {
    var out = list.orEmpty()
    if (card.id == r.recognizedId) {
        val mkey = misreadKey(r)
        out = out.filter { it.key != mkey && it.key != learned }
        if (learned == null && r.setRead() == null) {
            val pkey = printingKey(r.recognizedName)
            out = out.flatMap { c ->
                when {
                    c.key != pkey || c.scryfallId == card.id -> listOf(c)
                    c.count > 1 -> listOf(c.copy(count = c.count - 1))
                    else -> emptyList()
                }
            }
        }
        return capCorrections(out)
    }
    if (learned != null) out = out.filter { it.key != learned }
    out = if (sameScannedName(card.name, r.recognizedName) && r.setRead() == null) upsertCorrection(out, KIND_PRINTING, printingKey(r.recognizedName), r, card, now)
    else upsertCorrection(out, KIND_MISREAD, misreadKey(r), r, card, now)
    return capCorrections(out)
}

/** [key]'s correction has put another scan right. */
fun markUsed(list: List<ScanCorrection>?, key: String, now: Long): List<ScanCorrection> =
    capCorrections(list.orEmpty().map { if (it.key == key) it.copy(used = it.used + 1, lastUsed = maxOf(it.lastUsed, now)) else it })

fun forgetCorrection(list: List<ScanCorrection>?, key: String): List<ScanCorrection> = list.orEmpty().filter { it.key != key }

/**
 * Two devices' corrections, entry by entry: added on either side, kept; both have it, the one used
 * last wins; gone on one side and not used since on the other, gone. Capped as ever.
 */
fun mergeCorrections(base: List<ScanCorrection>?, mine: List<ScanCorrection>?, theirs: List<ScanCorrection>?, minePreferred: Boolean): List<ScanCorrection>? {
    if (base == null && mine == null && theirs == null) return null
    val b = base.orEmpty().associateBy { it.key }
    val m = mine.orEmpty().associateBy { it.key }
    val t = theirs.orEmpty().associateBy { it.key }
    val out = mutableListOf<ScanCorrection>()
    for (key in LinkedHashSet(m.keys + t.keys)) {
        val bc = b[key]
        val mc = m[key]
        val tc = t[key]
        if (mc != null && tc != null) {
            out += when {
                mc.lastUsed > tc.lastUsed -> mc
                tc.lastUsed > mc.lastUsed -> tc
                minePreferred -> mc
                else -> tc
            }
            continue
        }
        val one = mc ?: tc!!
        // Only on one side: new there, or forgotten on the other — unless it's been used since.
        if (bc == null || one.lastUsed > bc.lastUsed) out += one
    }
    return capCorrections(out)
}

/**
 * [theirs] with [source]'s corrections, when [theirs] was saved by an app that doesn't know about them
 * (no "scanCorrections" key) — the same object otherwise.
 */
fun keepCorrectionsFromOlderApp(source: Collection, theirs: Collection): Collection {
    if (theirs.scanCorrections != null || source.scanCorrections == null || !theirs.isUnsorted) return theirs
    return theirs.copy(scanCorrections = source.scanCorrections)
}

/** The corrections learned so far, newest first. */
fun correctionsOf(collections: List<Collection>): List<ScanCorrection> = collections.firstOrNull { it.isUnsorted }?.scanCorrections.orEmpty()

/** [collections] with the corrections set to [list] (on the Unsorted pile, made if it isn't there). */
fun withCorrections(collections: List<Collection>, list: List<ScanCorrection>): List<Collection> =
    withUnsortedPile(collections).map { if (it.isUnsorted) it.copy(scanCorrections = capCorrections(list)) else it }

/** The corrected card, as the list says it: "Plains · DMU #262". */
fun correctedLine(c: ScanCorrection): String = "${c.name} · ${c.set.uppercase()} #${c.collectorNumber}"

/** What was read, as the list says it. */
fun correctionReadLine(c: ScanCorrection): String {
    if (c.kind == KIND_PRINTING) return "${c.wrongName}, set unread"
    val where = c.readSet?.let { s -> " (${s.uppercase()}${c.readNumber?.let { " #$it" } ?: ""})" } ?: ""
    return "“${c.read}”$where, taken for ${c.wrongName}"
}

/** How often, as the list says it: "Corrected twice · used 3 times". */
fun correctionUsedLine(c: ScanCorrection): String {
    fun times(n: Int) = when (n) { 1 -> "once"; 2 -> "twice"; else -> "$n times" }
    if (c.kind == KIND_PRINTING && c.count < PRINTING_PICKS) return "Picked ${times(c.count)} · preferred once picked ${times(PRINTING_PICKS)}"
    return "Corrected ${times(c.count)} · ${if (c.used == 0) "not used yet" else "used ${times(c.used)}"}"
}
