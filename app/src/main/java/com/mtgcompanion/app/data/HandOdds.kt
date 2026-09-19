package com.mtgcompanion.app.data

// The odds of an opening hand, worked out exactly from the deck list (hypergeometric: drawing
// without putting back). Mirrors the web app's src/decks/handOdds.ts.

/** A keepable seven: this many lands. */
val KEEPABLE_LANDS = 2..4
const val OPENING_HAND = 7

data class HandOdds(
    /** The cards shuffled into the library (a deck less its commanders), its lands and its ramp. */
    val library: Int,
    val lands: Int,
    val ramp: Int,
    /** Whether the first turn has a draw: multiplayer Commander, yes; a two-player game on the play, no. */
    val drawsOnTurnOne: Boolean,
    /** Chance of exactly 0, 1, … 7 lands in the opening seven. */
    val landSpread: List<Double>,
    val keepable: Double,
    /** A keepable seven by the second hand (a London mulligan draws a fresh seven). */
    val keepableWithMulligan: Double,
    /** At least one ramp card in the opening seven. */
    val rampInHand: Double,
    /** Turn → a land for every turn up to it: 3 lands by turn 3, 4 by turn 4. */
    val landDrops: List<Pair<Int, Double>>,
    /** 2+ lands and a ramp card among the cards seen by turn 2. */
    val landsAndRampByTurn2: Double
)

private fun choose(n: Int, k: Int): Double {
    if (k < 0 || k > n || n < 0) return 0.0
    var r = 1.0
    for (i in 1..minOf(k, n - k)) r = r * (n - minOf(k, n - k) + i) / i
    return r
}

/** Chance of exactly [l] lands and [r] ramp among [n] cards drawn from the library. */
private fun exactly(library: Int, lands: Int, ramp: Int, n: Int, l: Int, r: Int): Double =
    choose(lands, l) * choose(ramp, r) * choose(library - lands - ramp, n - l - r) / choose(library, n)

/** Chance of at least [atLeast] lands among [n] cards. */
private fun landsAtLeast(library: Int, lands: Int, n: Int, atLeast: Int): Double =
    (atLeast..n).sumOf { l -> choose(lands, l) * choose(library - lands, n - l) } / choose(library, n)

/** Cards seen by [turn]: the seven, plus a draw each turn (less the first, on the play in a two-player game). */
fun cardsSeenBy(turn: Int, drawsOnTurnOne: Boolean): Int = OPENING_HAND + turn - if (drawsOnTurnOne) 0 else 1

/**
 * The opening-hand odds for a library of [library] cards holding [lands] lands and [ramp] non-land
 * ramp cards. Null for a library too small to draw a hand from.
 */
fun handOdds(library: Int, lands: Int, ramp: Int, drawsOnTurnOne: Boolean): HandOdds? {
    if (library < OPENING_HAND + 4 || lands > library || lands + ramp > library) return null
    val spread = (0..OPENING_HAND).map { l -> choose(lands, l) * choose(library - lands, OPENING_HAND - l) / choose(library, OPENING_HAND) }
    val keepable = KEEPABLE_LANDS.sumOf { spread[it] }
    val byTurn2 = cardsSeenBy(2, drawsOnTurnOne)
    var landsAndRamp = 0.0
    for (l in 2..byTurn2) for (r in 1..byTurn2 - l) landsAndRamp += exactly(library, lands, ramp, byTurn2, l, r)
    return HandOdds(
        library = library,
        lands = lands,
        ramp = ramp,
        drawsOnTurnOne = drawsOnTurnOne,
        landSpread = spread,
        keepable = keepable,
        keepableWithMulligan = 1 - (1 - keepable) * (1 - keepable),
        rampInHand = 1 - choose(library - ramp, OPENING_HAND) / choose(library, OPENING_HAND),
        landDrops = listOf(3, 4).map { t -> t to landsAtLeast(library, lands, cardsSeenBy(t, drawsOnTurnOne), t) },
        landsAndRampByTurn2 = landsAndRamp
    )
}

/** A chance as a whole percent, never claiming a certainty it isn't: "99%" not "100%", "<1%" not "0%". */
fun oddsPercent(p: Double): String = when {
    p <= 0.0 -> "0%"
    p < 0.005 -> "<1%"
    p >= 1.0 -> "100%"
    p > 0.995 -> ">99%"
    else -> "${Math.round(p * 100)}%"
}
