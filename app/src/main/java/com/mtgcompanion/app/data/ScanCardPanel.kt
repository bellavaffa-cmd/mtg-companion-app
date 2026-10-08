package com.mtgcompanion.app.data

/*
 * The scanner's "Last scanned" panel (ui/scan/ScanCardPanel.kt): the words on it, worked out here so
 * they can be tested. The card's bottom-left details as printed ("FIN · 0306 · L · EN"), how the
 * scanner knew the card, what TalkBack says, whether the camera is reading a different printing off
 * the card still held, and how many copies you already own. The web app's half:
 * src/scan/scanCardPanel.ts, with the same checks.
 */

/** How a scan was identified, for the panel's "From small print" / "By name" / "Best guess" / "Learned". */
enum class ScanHow { SMALL_PRINT, NAME, SIGHT, LEARNED, PICKED }

/** The rarity letter printed at the card's bottom left: C/U/R/M, L for a basic land, T for a token, S for special. */
fun rarityLetter(rarity: String?, typeLine: String?): String? {
    val type = typeLine.orEmpty()
    if (type.contains("Basic", ignoreCase = true) && type.contains("Land", ignoreCase = true)) return "L"
    if (type.startsWith("Token", ignoreCase = true)) return "T"
    return when (rarity?.lowercase()) {
        "common" -> "C"
        "uncommon" -> "U"
        "rare" -> "R"
        "mythic" -> "M"
        "special", "bonus" -> "S"
        else -> null
    }
}

/** The rarity in words, for TalkBack: "basic land", "mythic rare". */
fun rarityWords(rarity: String?, typeLine: String?): String? = when (rarityLetter(rarity, typeLine)) {
    "L" -> "basic land"
    "T" -> "token"
    "C" -> "common"
    "U" -> "uncommon"
    "R" -> "rare"
    "M" -> "mythic rare"
    "S" -> "special"
    else -> null
}

/** The language code as a card prints it: "EN", "JP" for Scryfall's "ja", "KR", "CS"/"CT" for Chinese. English when unknown. */
fun printedLanguage(lang: String?): String = when (val l = lang?.trim()?.lowercase().orEmpty().ifEmpty { "en" }) {
    "ja" -> "JP"
    "ko" -> "KR"
    "zhs" -> "CS"
    "zht" -> "CT"
    else -> l.uppercase()
}

/** "FIN · 0306": the set code in capitals and the collector number as Scryfall has it. */
fun setAndNumber(set: String?, number: String?): String =
    listOfNotNull(set?.takeIf { it.isNotBlank() }?.uppercase(), number?.takeIf { it.isNotBlank() }).joinToString(" · ")

/** The bottom-left details, all on one line: "FIN · 0306 · L · EN · Foil". */
fun scanDetailsLine(set: String?, number: String?, rarity: String?, typeLine: String?, lang: String?, foil: Boolean): String =
    listOfNotNull(
        setAndNumber(set, number).ifEmpty { null },
        rarityLetter(rarity, typeLine),
        printedLanguage(lang),
        if (foil) "Foil" else null
    ).joinToString(" · ")

/** Whether the scanner was only guessing at the printing: shown amber, to be checked. */
fun scanIsGuess(how: ScanHow?, exact: Boolean): Boolean =
    how != ScanHow.LEARNED && how != ScanHow.PICKED && how != ScanHow.SMALL_PRINT && !exact

/** How the card was identified, in a word or two; null when there's nothing to say. */
fun scanHowLabel(how: ScanHow?, exact: Boolean): String? = when {
    how == ScanHow.LEARNED -> "Learned"
    how == ScanHow.PICKED -> "Picked by you"
    how == ScanHow.SMALL_PRINT -> "From small print"
    !exact -> "Best guess"
    how == ScanHow.SIGHT -> "By sight"
    how == ScanHow.NAME -> "By name"
    else -> null
}

/** "×3 in this scan". */
fun copiesInScan(copies: Int): String = "×$copies in this scan"

/** A collector number as said out loud: "0306" is "306". */
fun spokenNumber(number: String): String = number.trimStart('0').ifEmpty { "0" }

/** The panel in one breath, for TalkBack: "Forest, FIN 306, basic land, English, $0.40". */
fun scanPanelSpoken(
    name: String, set: String?, number: String?, rarity: String?, typeLine: String?, lang: String?,
    foil: Boolean, price: String?, guess: Boolean
): String = listOfNotNull(
    name,
    listOfNotNull(set?.takeIf { it.isNotBlank() }?.uppercase(), number?.takeIf { it.isNotBlank() }?.let(::spokenNumber)).joinToString(" ").ifEmpty { null },
    rarityWords(rarity, typeLine),
    languageName((lang?.lowercase()?.ifBlank { null }) ?: "en"),
    if (foil) "foil" else null,
    price,
    if (guess) "best guess, check the printing" else null
).joinToString(", ")

/** The price shown: the foil price for a foil copy when there is one, otherwise the plain one (USD, as Scryfall gives it). */
fun panelPrice(usd: String?, usdFoil: String?, foil: Boolean): String? =
    if (foil) usdFoil ?: usd else usd ?: usdFoil

// ---- The camera reading another printing off the card still held ----

/**
 * The small print read off the card still under the camera, frame by frame: a printing counts once it's
 * read the same on [frames] reads running. A frame with nothing read doesn't break the run.
 */
class HeldPrintingWatch(private val frames: Int = 2) {
    private var streak: Pair<String, String>? = null
    private var count = 0

    /** One frame's read; the printing the camera is steadily reading, or null while it isn't. */
    fun see(read: Pair<String, String>?): Pair<String, String>? {
        if (read != null) {
            val s = streak
            if (s != null && samePrinting(s, read)) count++ else {
                streak = read
                count = 1
            }
        }
        return streak?.takeIf { count >= frames }
    }

    fun reset() {
        streak = null
        count = 0
    }
}

/**
 * What the camera reads, when it isn't the printing the panel shows ([shown]): set and number compared
 * as a printing, so "0307" and "307", "fin" and "FIN" are the same. Null when they agree or nothing's read.
 */
fun printingMismatch(shown: Pair<String, String>?, cameraReads: Pair<String, String>?): Pair<String, String>? =
    cameraReads?.takeIf { shown == null || !samePrinting(shown, it) }

/** "Camera reads FIN · 307". */
fun cameraReadsLine(read: Pair<String, String>): String = "Camera reads ${setAndNumber(read.first, read.second)}"

/** "Use FIN 307". */
fun useCameraLabel(read: Pair<String, String>): String = "Use ${read.first.uppercase()} ${read.second}"

// ---- You own N ----

/** Copies you already have of a scanned card: of this printing (and where), and of its other printings. */
data class OwnedSummary(val samePrinting: Int, val otherPrintings: Int, val places: List<Pair<String, Int>>)

/**
 * Copies of the card [name] in binders, the Unsorted pile, storage places and decks that hold their own
 * copies — the scan's own pile isn't in any of those yet. This printing ([cardId]) by where they are
 * (a place's name, "Krenko deck", or the binder for copies with no place), most first.
 */
fun ownedSummary(collections: List<Collection>, decks: List<Deck>, cardId: String, name: String): OwnedSummary {
    val places = placesOf(collections).associateBy { it.id }
    val where = LinkedHashMap<String, Int>()
    var same = 0
    var other = 0
    fun at(label: String, n: Int) { if (n > 0) where[label] = (where[label] ?: 0) + n }
    for (c in collections) {
        if (c.kind == CollectionType.WISHLIST) continue
        for (e in c.entries) {
            if (!sameCardName(e.name, name)) continue
            val copies = e.quantity + e.foilQuantity
            if (copies <= 0) continue
            if (e.scryfallId != cardId) { other += copies; continue }
            same += copies
            var placed = 0
            for (line in placedCopies(e)) {
                val place = places[line.placeId] ?: continue
                placed += line.qty
                at(place.name, line.qty)
            }
            at(c.name, copies - placed)
        }
    }
    for (d in decks) for (e in realCopiesOf(d)) {
        if (!sameCardName(e.name, name)) continue
        if (e.scryfallId == cardId) { same += e.quantity; at("${d.name} deck", e.quantity) } else other += e.quantity
    }
    return OwnedSummary(same, other, where.entries.sortedByDescending { it.value }.map { it.key to it.value })
}

/** "You own 3 · 2 in Red box, 1 in Krenko deck · +4 in other printings" — the top two places, then "…". */
fun ownedLine(s: OwnedSummary, top: Int = 2): String {
    val others = if (s.otherPrintings > 0) "+${s.otherPrintings} in other printings" else null
    if (s.samePrinting <= 0) return if (others != null) "None of this printing · $others" else "You don't own this card yet"
    val where = s.places.take(top).joinToString(", ") { "${it.second} in ${it.first}" } + if (s.places.size > top) ", …" else ""
    return listOfNotNull("You own ${s.samePrinting}", where.ifEmpty { null }, others).joinToString(" · ")
}
