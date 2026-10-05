package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.scryfall.ScryfallCard

// Companions: the ten Ikoria creatures that start the game outside it, each only if the starting
// deck meets its condition (rule 702.139). A deck names its companion in Deck.companion; the card
// itself sits in the sideboard — one of the fifteen in a 60-card format, outside the 100 in Commander
// (where the commander is part of the starting deck, and the companion must fit its colours). The
// conditions, from the cards' Oracle text:
//   Gyruda, Doom of Depths    — Your starting deck contains only cards with even mana values.
//   Jegantha, the Wellspring  — No card in your starting deck has more than one of the same mana symbol in its mana cost.
//   Kaheera, the Orphanguard  — Each creature card in your starting deck is a Cat, Elemental, Nightmare, Dinosaur, or Beast card.
//   Keruga, the Macrosage     — Your starting deck contains only cards with mana value 3 or greater and land cards.
//   Lurrus of the Dream-Den   — Each permanent card in your starting deck has mana value 2 or less.
//   Lutri, the Spellchaser    — Each nonland card in your starting deck has a different name.
//   Obosh, the Preypiercer    — Your starting deck contains only cards with odd mana values and land cards.
//   Umori, the Collector      — Each nonland card in your starting deck shares a card type.
//   Yorion, Sky Nomad         — Your starting deck contains at least twenty cards more than the minimum deck size.
//   Zirda, the Dawnwaker      — Each permanent card in your starting deck has an activated ability.
// Pure, so it can be tested; the web app's src/decks/companion.ts checks the same, in the same words.

data class CompanionInfo(
    val name: String,
    /** What it's called in a sentence: "Lurrus". */
    val short: String,
    /** Its condition, plainly. */
    val rule: String
)

val COMPANIONS = listOf(
    CompanionInfo("Gyruda, Doom of Depths", "Gyruda", "Every card must have an even mana value."),
    CompanionInfo("Jegantha, the Wellspring", "Jegantha", "No card may have the same mana symbol twice in its cost."),
    CompanionInfo("Kaheera, the Orphanguard", "Kaheera", "Every creature must be a Cat, Elemental, Nightmare, Dinosaur or Beast."),
    CompanionInfo("Keruga, the Macrosage", "Keruga", "Every card but lands must have mana value 3 or more."),
    CompanionInfo("Lurrus of the Dream-Den", "Lurrus", "Every permanent must have mana value 2 or less."),
    CompanionInfo("Lutri, the Spellchaser", "Lutri", "Every card but lands must have a different name."),
    CompanionInfo("Obosh, the Preypiercer", "Obosh", "Every card but lands must have an odd mana value."),
    CompanionInfo("Umori, the Collector", "Umori", "Every card but lands must share a card type."),
    CompanionInfo("Yorion, Sky Nomad", "Yorion", "The deck needs at least 20 cards more than the minimum."),
    CompanionInfo("Zirda, the Dawnwaker", "Zirda", "Every permanent must have an activated ability.")
)

/** The companion called [name] (whatever its case), if it is one. */
fun companionNamed(name: String?): CompanionInfo? =
    name?.trim()?.lowercase()?.let { n -> COMPANIONS.firstOrNull { it.name.lowercase() == n } }

/**
 * What the check needs to know of a card in the starting deck. Anything not known (null) isn't held
 * against it: a card not looked up yet only counts for its name and copies.
 */
data class CompanionCard(
    val name: String,
    val quantity: Int = 1,
    val cmc: Double? = null,
    /** The mana cost the card has in the deck: the front face's, or both halves' for a split card. */
    val manaCost: String? = null,
    /** The front face's type line. */
    val typeLine: String? = null,
    /** Every face's rules text. */
    val oracleText: String? = null
)

data class CompanionResult(
    val met: Boolean,
    /** The cards that break it, each once, in deck order. */
    val offenders: List<String> = emptyList(),
    /** Yorion: how many cards the deck has, against how many it needs. */
    val has: Int? = null,
    val needs: Int? = null
)

private val PERMANENT_TYPES = setOf("artifact", "battle", "creature", "enchantment", "land", "planeswalker")
private val CARD_TYPES = listOf("artifact", "battle", "creature", "enchantment", "instant", "kindred", "planeswalker", "sorcery")
private val KAHEERA_TYPES = setOf("cat", "elemental", "nightmare", "dinosaur", "beast")
private val WHITESPACE = Regex("\\s+")

/** The card types on a type line (front face, before the dash), lower-cased; "Tribal" is Kindred. */
private fun cardTypes(typeLine: String): List<String> =
    typeLine.split("//")[0].split("—")[0].lowercase().replace(Regex("\\btribal\\b"), "kindred")
        .split(WHITESPACE).filter { it.isNotEmpty() }

private fun subtypes(typeLine: String): List<String> {
    val front = typeLine.split("//")[0]
    val dash = front.indexOf('—')
    return if (dash < 0) emptyList() else front.substring(dash + 1).lowercase().split(WHITESPACE).filter { it.isNotEmpty() }
}

private fun CompanionCard.isLand() = typeLine != null && "land" in cardTypes(typeLine)
private fun CompanionCard.isPermanent() = typeLine != null && cardTypes(typeLine).any { it in PERMANENT_TYPES }
private fun CompanionCard.isCreature() = typeLine != null && "creature" in cardTypes(typeLine)

/** Activated abilities named by a keyword alone, with no colon printed (Scryfall leaves out reminder text). */
private val ACTIVATED_KEYWORDS = Regex(
    "^(equip|cycling|basic landcycling|\\w+cycling|ninjutsu|unearth|level up|reconfigure|crew|fortify|outlast|scavenge|embalm|eternalize|transmute|channel|boast|transfigure|forecast|bloodrush|reinforce|encore|craft|exhaust|station|adapt|monstrosity)\\b",
    setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
)
private val QUOTED = Regex("[\"“][^\"”]*[\"”]")
private val CHANGELING = Regex("^changeling\\b", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
private val MANA_SYMBOL = Regex("\\{([^}]+)\\}")

/**
 * Whether rules text has an activated ability: "[Cost]: [Effect]" — reminder text counts, so a basic
 * land's "({T}: Add {G}.)" does — or a keyword that is one (Equip, Cycling, Crew…). Abilities a card
 * only grants to others, in quotes, don't.
 */
fun hasActivatedAbility(text: String): Boolean {
    val own = text.replace(QUOTED, "")
    return ':' in own || ACTIVATED_KEYWORDS.containsMatchIn(own)
}

/** Whether a mana cost repeats a symbol: {R}{R}, {1}{1} across a split card's halves, {G/W}{G/W}. */
fun repeatsManaSymbol(manaCost: String): Boolean {
    val symbols = MANA_SYMBOL.findAll(manaCost).map { it.groupValues[1].uppercase() }.toList()
    return symbols.toSet().size < symbols.size
}

private fun each(cards: List<CompanionCard>, breaks: (CompanionCard) -> Boolean): CompanionResult {
    val offenders = mutableListOf<String>()
    cards.forEach { c -> if (breaks(c) && offenders.none { it.lowercase() == c.name.lowercase() }) offenders += c.name }
    return CompanionResult(offenders.isEmpty(), offenders)
}

/**
 * Whether [cards] — the starting deck, commanders included — meet companion [name]'s condition, and
 * which cards don't. [minimumSize] is the format's smallest deck (60, 40 in Limited, 100 in Commander).
 * Not a companion: met.
 */
fun checkCompanion(name: String, cards: List<CompanionCard>, minimumSize: Int): CompanionResult =
    when (companionNamed(name)?.short) {
        "Gyruda" -> each(cards) { c -> c.cmc != null && c.cmc % 2 != 0.0 }
        "Jegantha" -> each(cards) { c -> c.manaCost != null && repeatsManaSymbol(c.manaCost) }
        "Kaheera" -> each(cards) { c ->
            c.isCreature() && subtypes(c.typeLine!!).none { it in KAHEERA_TYPES } && !CHANGELING.containsMatchIn(c.oracleText.orEmpty())
        }
        "Keruga" -> each(cards) { c -> c.typeLine != null && !c.isLand() && c.cmc != null && c.cmc < 3 }
        "Lurrus" -> each(cards) { c -> c.isPermanent() && c.cmc != null && c.cmc > 2 }
        "Lutri" -> {
            val copies = mutableMapOf<String, Int>()
            cards.filterNot { it.isLand() }.forEach { copies.merge(it.name.lowercase(), it.quantity, Int::plus) }
            each(cards) { c -> !c.isLand() && (copies[c.name.lowercase()] ?: 0) > 1 }
        }
        "Obosh" -> each(cards) { c -> c.typeLine != null && !c.isLand() && c.cmc != null && c.cmc % 2 != 1.0 }
        "Umori" -> {
            // The type the most nonland cards share is the one the deck keeps; the rest break it.
            val typed = cards.filter { it.typeLine != null && !it.isLand() }
            var best = ""
            var most = -1
            CARD_TYPES.forEach { t ->
                val n = typed.filter { t in cardTypes(it.typeLine!!) }.sumOf { it.quantity }
                if (n > most) { best = t; most = n }
            }
            each(typed) { c -> best !in cardTypes(c.typeLine!!) }
        }
        "Yorion" -> {
            val has = cards.sumOf { it.quantity }
            val needs = minimumSize + 20
            CompanionResult(has >= needs, emptyList(), has, needs)
        }
        "Zirda" -> each(cards) { c -> c.isPermanent() && c.oracleText != null && !hasActivatedAbility(c.oracleText) }
        else -> CompanionResult(true)
    }

/** A deck card as the check sees it, from what Scryfall says of it (or only its entry when it isn't known). */
fun companionCard(entry: DeckCardEntry, card: ScryfallCard? = null): CompanionCard =
    companionCard(entry.name, entry.quantity, entry.typeLine, card)

fun companionCard(name: String, quantity: Int, typeLine: String?, card: ScryfallCard?): CompanionCard {
    if (card == null) return CompanionCard(name, quantity, typeLine = typeLine)
    val faces = card.cardFaces.orEmpty()
    // A split card's cost is both halves'; a double-faced or adventure card's is its front face's.
    val manaCost = if (card.layout == "split") {
        faces.joinToString("") { it.manaCost.orEmpty() }.ifEmpty { card.manaCost.orEmpty() }
    } else if (faces.isNotEmpty()) {
        faces[0].manaCost ?: card.manaCost.orEmpty()
    } else {
        card.manaCost.orEmpty()
    }
    val front = (card.typeLine ?: faces.firstOrNull()?.typeLine ?: typeLine.orEmpty()).split("//")[0].trim()
    val text = (listOf(card.oracleText) + faces.map { it.oracleText }).filter { !it.isNullOrEmpty() }.joinToString("\n")
    return CompanionCard(name, quantity, card.cmc, manaCost.replace(Regex("\\s*//\\s*"), ""), front, text)
}

/** "Sol Ring", "Sol Ring and Arcane Signet", "Sol Ring, Arcane Signet, Mind Stone and 4 more". */
fun nameList(names: List<String>, shown: Int = 3): String = when {
    names.size <= 1 -> names.joinToString("")
    names.size <= shown -> names.dropLast(1).joinToString(", ") + " and " + names.last()
    else -> names.take(shown).joinToString(", ") + " and ${names.size - shown} more"
}

/** The sentence under a broken condition: "Every permanent… Not met by Sol Ring and 2 more." */
fun companionReason(companion: CompanionInfo, result: CompanionResult): String =
    if (result.has != null && result.needs != null) "${companion.rule} It has ${result.has} of the ${result.needs} needed."
    else "${companion.rule} Not met by ${nameList(result.offenders)}."

/** The deck's companion's sideboard entry, if the companion is in the sideboard. */
fun companionEntry(deck: Deck): DeckCardEntry? {
    val name = deck.companion?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
    return deck.sideboard.firstOrNull { it.name.trim().lowercase() == name }
}

/** The deck with [name] as its companion; null or "" takes it off (kept as "" once set — see Deck.companion). */
fun Deck.withCompanion(name: String?): Deck {
    val next = name?.trim().orEmpty()
    if (next.isEmpty() && companion == null) return this
    return copy(companion = next)
}

/**
 * Why adding [card] to the main deck would break the deck's companion condition — "Breaks Lurrus's
 * companion condition" — or null. Only the card's own part is checked (Yorion's count isn't): the
 * deck's cards as their entries know them count for Lutri's names and Umori's types.
 */
fun companionAddProblem(deck: Deck, card: CompanionCard): String? {
    val companion = companionNamed(deck.companion) ?: return null
    if (companion.short == "Yorion") return null
    val result = checkCompanion(companion.name, deck.cards.map { companionCard(it) } + card, 0)
    return if (result.offenders.any { it.lowercase() == card.name.lowercase() }) "Breaks ${companion.short}'s companion condition" else null
}
