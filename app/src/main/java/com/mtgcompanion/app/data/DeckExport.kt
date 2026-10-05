package com.mtgcompanion.app.data

// A deck as text for other apps. Four shapes:
//
//  - Simple: "1 Sol Ring" per line, commanders first — what nearly everything reads.
//  - Exact printing: the same with "(SET) number" after each name, so the art survives the trip.
//  - Arena: MTG Arena's own import shape — "Commander" / "Deck" / "Sideboard" sections, every line
//    "1 Name (SET) 123", double-faced cards by their front face.
//  - MTGO: plain "4 Name" lines, the sideboard after a blank line, no set codes; front-face names
//    for double-faced cards, "Fire/Ice" for split cards. A Commander deck's commanders go in the
//    sideboard part, which is where MTGO looks for them.
//
// Simple and Exact printing add a "Sideboard" section when the deck has one, which this app's own
// importer (parseCardList) reads back into the sideboard, and start with the deck's primer as "// "
// comment lines, which importers skip (Arena and MTGO take no comments, so they leave it out).
// Considering is never exported.

enum class DeckExportFormat(val label: String) {
    SIMPLE("Simple"),
    EXACT("Exact printing"),
    ARENA("Arena"),
    MTGO("MTGO");

    /** Whether the format names each card's printing, which needs the cards looked up first. */
    val needsPrintings: Boolean get() = this == EXACT || this == ARENA
}

/**
 * The name another client knows a card by. Scryfall names a double-faced card "Front // Back";
 * Arena and MTGO want only the front face. A split card (both halves on one face, like Fire // Ice)
 * keeps both: "Fire // Ice" on Arena, "Fire/Ice" on MTGO. A card is taken as split when it has no
 * back picture and no half of its type line is an Adventure or an Omen.
 */
fun clientCardName(entry: DeckCardEntry, format: DeckExportFormat): String {
    val name = entry.name
    if (" // " !in name || format == DeckExportFormat.SIMPLE || format == DeckExportFormat.EXACT) return name
    val front = name.substringBefore(" // ").trim()
    val type = entry.typeLine.orEmpty()
    val split = entry.backImageUrl == null && " // " in type &&
        !type.contains("Adventure", ignoreCase = true) && !type.contains("Omen", ignoreCase = true)
    return when {
        !split -> front
        format == DeckExportFormat.MTGO -> name.split(" // ").joinToString("/") { it.trim() }
        else -> name
    }
}

/**
 * [deck] as text in [format]. [printings] is scryfallId → (set code, collector number), needed by
 * Exact printing and Arena; a card missing from it is written without one.
 */
fun deckExportText(deck: Deck, format: DeckExportFormat, printings: Map<String, Pair<String, String>> = emptyMap()): String {
    val commanders = listOfNotNull(deck.commander, deck.partnerCommander)
    val commanderIds = commanders.map { it.scryfallId }.toSet()
    // The rest of the main deck, less one copy of each commander (a commander is in the card list too).
    val rest = deck.cards.mapNotNull { entry ->
        val left = entry.quantity - if (entry.scryfallId in commanderIds) commanders.count { it.scryfallId == entry.scryfallId } else 0
        if (left > 0) entry.copy(quantity = left) else null
    }.sortedBy { it.name.lowercase() }
    val sideboard = deck.sideboard.sortedBy { it.name.lowercase() }

    fun line(entry: DeckCardEntry, quantity: Int = entry.quantity): String {
        val name = clientCardName(entry, format)
        val printing = if (format.needsPrintings) printings[entry.scryfallId] else null
        return if (printing != null) "$quantity $name (${printing.first.uppercase()}) ${printing.second}" else "$quantity $name"
    }

    val sections = mutableListOf<List<String>>()
    when (format) {
        DeckExportFormat.SIMPLE, DeckExportFormat.EXACT -> {
            sections += primerComments(deck.description)
            sections += commanders.map { line(it, 1) } + rest.map { line(it) }
            if (sideboard.isNotEmpty()) sections += listOf("Sideboard") + sideboard.map { line(it) }
        }
        DeckExportFormat.ARENA -> {
            if (commanders.isNotEmpty()) sections += listOf("Commander") + commanders.map { line(it, 1) }
            sections += listOf("Deck") + rest.map { line(it) }
            if (sideboard.isNotEmpty()) sections += listOf("Sideboard") + sideboard.map { line(it) }
        }
        DeckExportFormat.MTGO -> {
            sections += rest.map { line(it) }
            val after = commanders.map { line(it, 1) } + sideboard.map { line(it) }
            if (after.isNotEmpty()) sections += after
        }
    }
    return sections.filter { it.isNotEmpty() }.joinToString("\n\n") { it.joinToString("\n") }
}
