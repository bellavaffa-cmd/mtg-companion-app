package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.scryfall.ScryfallCard

// The rules behind the new-deck flow (ui/decks/NewDeckScreen): which commanders a format offers,
// how the picker filters and sorts them, and what the deck is called. The web's new-deck flow
// does the same.

/** Every card that can lead a [mode] deck, as a Scryfall search; null for a format with no commander. */
fun commanderQuery(mode: GameMode): String? = when (mode) {
    GameMode.COMMANDER -> "is:commander legal:commander"
    GameMode.BRAWL -> "legal:brawl (t:legendary (t:creature or t:planeswalker) or o:\"can be your commander\")"
    else -> null
}

/** Every Background a "Choose a Background" commander can take. */
const val BACKGROUND_QUERY = "t:background legal:commander"

/** The picker's colour chips: the five colours, then Colourless. */
val IDENTITY_CHIPS = listOf("W", "U", "B", "R", "G", "C")

/** The one-line hint under the colour chips, with nothing picked or something picked. */
fun identityHint(anyPicked: Boolean): String =
    if (!anyPicked) "Pick colours to see only the commanders that fit within them."
    else "Showing commanders whose colour identity fits within the colours picked — colourless ones fit any."

/**
 * Whether a commander with this colour [identity] fits within the [selected] chips (W U B R G,
 * and C for Colourless): everything in its identity was picked, so it could lead a deck of those
 * colours. Nothing picked fits everything; colourless commanders fit any pick, so C alone shows
 * only them.
 */
fun identityFits(identity: List<String>?, selected: Set<String>): Boolean {
    if (selected.isEmpty()) return true
    val colours = selected - "C"
    return identity.orEmpty().all { it.uppercase() in colours }
}

/** Whether [query] (trimmed, any case) is in the card's name, type line or rules text. */
fun matchesText(card: ScryfallCard, query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    return card.name.contains(q, ignoreCase = true) ||
        card.typeLine?.contains(q, ignoreCase = true) == true ||
        card.displayOracleText?.contains(q, ignoreCase = true) == true
}

/** How the commander picker orders its cards. */
enum class CommanderSort(val label: String) {
    /** Most played on EDHREC first. */
    POPULAR("Popular"),
    NAME("Name"),
    /** Most recently printed first. */
    NEWEST("Newest")
}

/**
 * [cards] in [sort]'s order: Popular by EDHREC rank (unranked last), Name A to Z, Newest by
 * release date, latest first. Ties keep the order they came in.
 */
fun sortCommanders(cards: List<ScryfallCard>, sort: CommanderSort): List<ScryfallCard> = when (sort) {
    CommanderSort.POPULAR -> cards.sortedBy { it.edhrecRank ?: Int.MAX_VALUE }
    CommanderSort.NAME -> cards.sortedBy { it.name.lowercase() }
    CommanderSort.NEWEST -> cards.sortedByDescending { it.releasedAt.orEmpty() }
}

/** What the picker shows: [cards] narrowed by text and colours, then sorted. */
fun pickerCards(cards: List<ScryfallCard>, query: String, colours: Set<String>, sort: CommanderSort): List<ScryfallCard> =
    sortCommanders(cards.filter { matchesText(it, query) && identityFits(it.colorIdentity, colours) }, sort)

/**
 * The cards that can join [main] as its second commander: from [candidates], those that can
 * pair with it, never [main] itself.
 */
fun secondCommanderOptions(main: ScryfallCard, candidates: List<ScryfallCard>): List<ScryfallCard> {
    val mainPair = main.pairCard
    return candidates.filter { it.id != main.id && canPair(mainPair, it.pairCard) }
}

/**
 * The new deck's name until the user types one: the commander's ("A & B" for two, a double-faced
 * card by its front name), or "New Modern deck" for a format without one.
 */
fun defaultDeckName(mode: GameMode, commander: String?, partner: String?): String =
    if (commander == null) "New ${mode.label} deck"
    else listOfNotNull(commander, partner).joinToString(" & ") { it.substringBefore(" // ") }

/** The tab a new deck opens on: what to add next for a commander deck, its cards otherwise. */
fun landingTab(mode: GameMode): String = if (mode.usesCommander) "Suggestions" else "Cards"

/** One line under each format in the picker. */
fun formatBlurb(mode: GameMode): String = when (mode) {
    GameMode.COMMANDER -> "100 cards, one of each, led by a legendary commander"
    GameMode.BRAWL -> "60 cards, one of each, with a Standard-legal commander"
    GameMode.STANDARD -> "60 cards from the latest sets"
    GameMode.PIONEER -> "60 cards from Return to Ravnica on"
    GameMode.MODERN -> "60 cards from Eighth Edition on"
    GameMode.PAUPER -> "60 cards, commons only"
    GameMode.LEGACY -> "60 cards from all of Magic, a few banned"
    GameMode.VINTAGE -> "60 cards from all of Magic, a few restricted"
}
