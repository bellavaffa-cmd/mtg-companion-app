package com.mtgcompanion.app.ui.common

// What TalkBack says for things drawn as pictures or colour: mana symbols, colour identities, a
// life-counter seat, a match record. Pure, so the same cases run in tests on both apps — mirrored by
// the web app's src/a11y/descriptions.ts (tests: A11yTextTest.kt here, tests/a11y/descriptions.test.ts there).

private val colourWords = mapOf("W" to "white", "U" to "blue", "B" to "black", "R" to "red", "G" to "green", "C" to "colourless")

private val specialSymbols = mapOf(
    "T" to "tap",
    "Q" to "untap",
    "S" to "snow mana",
    "E" to "energy",
    "X" to "X mana",
    "Y" to "Y mana",
    "Z" to "Z mana",
    "CHAOS" to "chaos",
    "PW" to "planeswalker"
)

/** One half of a hybrid symbol, or a whole plain one, without the word "mana". */
private fun symbolPart(code: String): String? = when {
    code in colourWords -> colourWords.getValue(code)
    code == "COLORLESS" || code == "COLOURLESS" -> "colourless"
    code.isNotEmpty() && code.all { it.isDigit() } -> "$code generic"
    else -> null
}

/**
 * A mana or card symbol as words: "{W}" → "white mana", "{2/U}" → "2 generic or blue mana",
 * "{G/P}" → "Phyrexian green mana", "{T}" → "tap". Takes the code with or without its braces.
 * Anything it doesn't know is read as it's written.
 */
fun manaSymbolName(symbol: String): String {
    val raw = symbol.trim().removePrefix("{").removeSuffix("}")
    val code = raw.uppercase()
    specialSymbols[code]?.let { return it }
    val parts = code.split("/")
    val phyrexian = parts.size > 1 && parts.last() == "P"
    val names = (if (phyrexian) parts.dropLast(1) else parts).map { symbolPart(it) }
    if (names.isEmpty() || names.any { it == null }) return raw
    return (if (phyrexian) "Phyrexian " else "") + names.joinToString(" or ") + " mana"
}

/** A whole cost, "{2}{W}{W}" → "2 generic mana, white mana, white mana". Empty for no symbols. */
fun manaCostName(cost: String): String =
    Regex("\\{([^}]+)\\}").findAll(cost).map { manaSymbolName(it.groupValues[1]) }.joinToString(", ")

/** Joins words the way a sentence does: "a", "a and b", "a, b and c". */
fun joinWords(words: List<String>): String =
    if (words.size <= 1) words.firstOrNull().orEmpty()
    else words.dropLast(1).joinToString(", ") + " and " + words.last()

/** A colour identity in words, in the order given: ["W", "U"] → "white and blue"; none → "colourless". */
fun colourIdentityName(colours: List<String>): String {
    val words = colours.mapNotNull { colourWords[it.uppercase()] }.filter { it != "colourless" }
    return if (words.isEmpty()) "colourless" else joinWords(words)
}

/** Commander damage one seat has taken from one source (a commander's name, or a player's). */
data class SeatDamage(val from: String, val amount: Int)

/**
 * One life-counter seat read as a whole, whichever way the tile is turned:
 * "Seat 2, Sam, 34 life, 6 commander damage from Atraxa". A blank [name] is left out.
 */
fun seatDescription(
    seat: Int,
    name: String?,
    life: Int,
    poison: Int = 0,
    commanderDamage: List<SeatDamage> = emptyList(),
    out: Boolean = false
): String {
    val parts = mutableListOf("Seat $seat")
    name?.trim()?.takeIf { it.isNotEmpty() }?.let { parts += it }
    parts += "$life life"
    if (poison > 0) parts += "$poison poison"
    commanderDamage.filter { it.amount > 0 }.forEach { parts += "${it.amount} commander damage from ${it.from}" }
    if (out) parts += "out of the game"
    return parts.joinToString(", ")
}

/** A win–loss(–draw) record in words: (3, 2, 0) → "3 wins, 2 losses". */
fun recordWords(wins: Int, losses: Int, draws: Int = 0): String {
    fun n(count: Int, one: String, many: String) = "$count ${if (count == 1) one else many}"
    return (listOf(n(wins, "win", "wins"), n(losses, "loss", "losses")) + (if (draws > 0) listOf(n(draws, "draw", "draws")) else emptyList()))
        .joinToString(", ")
}
