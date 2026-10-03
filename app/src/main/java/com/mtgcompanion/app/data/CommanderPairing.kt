package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.scryfall.ScryfallCard

// Which two commanders may lead a deck together. Used by the new-deck flow, the deck page's
// "Set as partner commander", the legality check and setCommander. The web app has the same rules
// in src/decks/pairing.ts; keep the two alike.
//
// A deck entry keeps no rules text, only its name, type line and one string,
// [DeckCardEntry.partnerAbility] (synced with the web under that name). It says which pairing
// ability the card has:
//   null                   none
//   "Partner"              plain Partner: pairs with any other plain-Partner card
//   "Friends forever"      pairs with any other Friends forever card (the old wording and the newer
//                          "Partner—Friends forever" both store this)
//   "Partner—<variant>"    another named Partner variant ("Partner—Survivors"): pairs only with the
//                          same variant
//   "Choose a Background"  pairs with a legendary Background enchantment
//   "Doctor's companion"   pairs with a legendary creature that is a Time Lord Doctor and nothing else
//   anything else          the <Name> of "Partner with <Name>": pairs only with that card
// Backgrounds and Time Lord Doctors have no ability of their own; they're known by their type line.

const val PARTNER = "Partner"
const val FRIENDS_FOREVER = "Friends forever"
const val CHOOSE_A_BACKGROUND = "Choose a Background"
const val DOCTORS_COMPANION = "Doctor's companion"

private val FIXED_ABILITIES = setOf(PARTNER, FRIENDS_FOREVER, CHOOSE_A_BACKGROUND, DOCTORS_COMPANION)
private val PARTNER_VARIANT = Regex("^partner\\s*[—–-]\\s*(.+)$", RegexOption.IGNORE_CASE)
private val TYPE_DASH = Regex("\\s+[—–-]\\s+")
private val SPACES = Regex("\\s+")

/** What pairing needs to know about a card: a deck entry has exactly this. */
data class PairCard(val name: String, val typeLine: String?, val partnerAbility: String?)

val DeckCardEntry.pairCard: PairCard get() = PairCard(name, typeLine, partnerAbility)
val ScryfallCard.pairCard: PairCard get() = PairCard(name, typeLine, partnerAbility)

/**
 * The pairing ability of a card with these rules texts (every face's) and Scryfall [keywords], in
 * the stored form described at the top of this file. The oracle text decides; the keyword list
 * backs it up for the fixed phrases.
 */
fun pairingAbility(oracleTexts: List<String?>, keywords: List<String>?): String? {
    val lines = oracleTexts.filterNotNull().flatMap { it.split("\n") }
    for (raw in lines) {
        // Scryfall writes straight apostrophes, but a curly one costs nothing to accept.
        val line = raw.replace('’', '\'').substringBefore(" (").trim()
        val lower = line.lowercase()
        if (lower.startsWith("partner with ")) return line.substring("partner with ".length).trim()
        PARTNER_VARIANT.find(line)?.let { match ->
            val variant = match.groupValues[1].trim()
            return if (variant.equals(FRIENDS_FOREVER, ignoreCase = true)) FRIENDS_FOREVER else "$PARTNER—$variant"
        }
        if (lower == "partner") return PARTNER
        if (lower == FRIENDS_FOREVER.lowercase()) return FRIENDS_FOREVER
        if (lower == CHOOSE_A_BACKGROUND.lowercase()) return CHOOSE_A_BACKGROUND
        if (lower == DOCTORS_COMPANION.lowercase()) return DOCTORS_COMPANION
    }
    val words = keywords.orEmpty().map { it.lowercase() }
    return listOf(FRIENDS_FOREVER, CHOOSE_A_BACKGROUND, DOCTORS_COMPANION).firstOrNull { it.lowercase() in words }
}

/** The front face's type line split into its types and its subtypes. */
private fun typesOf(typeLine: String?): Pair<List<String>, List<String>> {
    val front = typeLine.orEmpty().split("//").first()
    val parts = front.split(TYPE_DASH, limit = 2)
    fun words(s: String) = s.trim().split(SPACES).filter { it.isNotEmpty() }
    return words(parts[0]) to words(parts.getOrElse(1) { "" })
}

/** A legendary Background enchantment — what "Choose a Background" pairs with. */
fun isBackground(typeLine: String?): Boolean {
    val (types, subtypes) = typesOf(typeLine)
    return "Legendary" in types && "Enchantment" in types && "Background" in subtypes
}

/**
 * A legendary creature whose only creature types are Time Lord Doctor — what "Doctor's companion"
 * pairs with. A Doctor that's also, say, a Human doesn't qualify.
 */
fun isTimeLordDoctor(typeLine: String?): Boolean {
    val (types, subtypes) = typesOf(typeLine)
    return "Legendary" in types && "Creature" in types && subtypes.joinToString(" ") == "Time Lord Doctor"
}

private fun isVariant(ability: String) = ability.startsWith("$PARTNER—")

private fun sameName(a: String, b: String): Boolean {
    fun norm(s: String) = s.trim().lowercase()
    return norm(a) == norm(b) || norm(a.substringBefore(" // ")) == norm(b.substringBefore(" // "))
}

/** One way round: whether [a]'s ability accepts [b]. [canPair] asks it both ways. */
private fun accepts(a: PairCard, b: PairCard): Boolean {
    val ability = a.partnerAbility ?: return false
    return when {
        ability == PARTNER -> b.partnerAbility == PARTNER
        ability == FRIENDS_FOREVER || isVariant(ability) -> b.partnerAbility == ability
        ability == CHOOSE_A_BACKGROUND -> isBackground(b.typeLine)
        ability == DOCTORS_COMPANION -> isTimeLordDoctor(b.typeLine)
        ability in FIXED_ABILITIES -> false
        // "Partner with <Name>": only that card.
        else -> sameName(ability, b.name)
    }
}

/**
 * Whether [a] and [b] may be a deck's two commanders, in either order: both plain Partner, one
 * "Partner with" the other, the same Partner variant (Friends forever…), a "Choose a Background"
 * commander and a Background, or a "Doctor's companion" and a Time Lord Doctor.
 */
fun canPair(a: PairCard, b: PairCard): Boolean {
    if (sameName(a.name, b.name)) return false
    return accepts(a, b) || accepts(b, a)
}

fun canPair(a: DeckCardEntry, b: DeckCardEntry): Boolean = canPair(a.pairCard, b.pairCard)

/**
 * What the second commander would be, for a card that can have one, with the button that offers
 * it ([action]) and what it's called in the deck's card actions ([noun]: "Set as partner commander").
 */
enum class SecondCommanderKind(val action: String, val noun: String) {
    PARTNER("Add a partner", "partner commander"),
    BACKGROUND("Add a Background", "Background"),
    DOCTOR("Add a Doctor", "Doctor"),
    COMPANION("Add a Doctor's companion", "Doctor's companion")
}

/**
 * Whether [main] can lead with a second commander, and of what kind: a partner (any Partner
 * flavour), a Background, a Doctor (for a Doctor's companion) or a companion (for a Time Lord
 * Doctor). Null when it can't have one.
 */
fun secondCommanderKind(main: PairCard): SecondCommanderKind? {
    val ability = main.partnerAbility
    return when {
        ability == CHOOSE_A_BACKGROUND -> SecondCommanderKind.BACKGROUND
        ability == DOCTORS_COMPANION -> SecondCommanderKind.DOCTOR
        ability != null -> SecondCommanderKind.PARTNER
        isTimeLordDoctor(main.typeLine) -> SecondCommanderKind.COMPANION
        else -> null
    }
}

/**
 * Whether a [mode] deck may have that kind of second commander: only formats with a commander, and
 * a Background only in Commander (Brawl has none).
 */
fun allowsSecondCommander(mode: GameMode, kind: SecondCommanderKind): Boolean =
    mode.usesCommander && (kind != SecondCommanderKind.BACKGROUND || mode == GameMode.COMMANDER)

/**
 * This entry with its pairing ability read again from the card, when [abilities] (scryfallId ->
 * ability, from freshly fetched cards) has it: an entry saved by an older version stored only
 * plain Partner and "Partner with", so a Friends forever or Background commander had none.
 */
fun DeckCardEntry.withPairingFrom(abilities: Map<String, String?>): DeckCardEntry =
    if (abilities.containsKey(scryfallId)) copy(partnerAbility = abilities[scryfallId]) else this
