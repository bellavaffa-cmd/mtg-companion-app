package com.mtgcompanion.app.data.social

import kotlin.math.abs

/*
 * Is the trade fair? Both sides' totals at today's prices (Scryfall's, in US dollars — shown in the
 * chosen currency), the difference, how the two sides compare for the balance bar, and — when it's
 * uneven — the cards that would even it out: from the side that's short, the cards the other person
 * wants (their wishlist) that the user has for trade or spare, or the cards the user wants that they
 * have, closest to the gap first. Cards with no price are counted and said, never guessed.
 *
 * Pure, so it can be tested. Mirrors the web app's src/social/tradeFairness.ts, with the same tests
 * (TradeFairnessTest.kt ↔ tests/social/tradeFairness.test.ts).
 */

/** A card's prices in US dollars: regular and foil (null: none). */
data class CardPrice(val usd: Double?, val foil: Double?)

/** One copy's price: the foil price for a foil copy (else the regular one), the regular price otherwise (else the foil one). */
fun unitPrice(card: TradeCard, prices: Map<String, CardPrice>): Double? {
    val p = prices[card.scryfallId] ?: return null
    return if (card.foil) p.foil ?: p.usd else p.usd ?: p.foil
}

/** A side's total in US dollars, and how many of its copies have no price (left out of the total). */
data class SideTotal(val sum: Double, val unpriced: Int)

fun sideTotal(cards: List<TradeCard>, prices: Map<String, CardPrice>): SideTotal {
    var sum = 0.0
    var unpriced = 0
    for (c in cards) {
        val each = unitPrice(c, prices)
        if (each == null) unpriced += c.quantity else sum += each * c.quantity
    }
    return SideTotal(sum, unpriced)
}

/** Within $2, or a tenth of the bigger side, either way is fair. */
fun isFair(diff: Double, a: Double, b: Double): Boolean = abs(diff) <= maxOf(2.0, 0.1 * maxOf(a, b))

/**
 * Both sides compared. [diff]: what the user gets less what they give (above 0, they get more).
 * [unpriced]: copies with no price, both sides. [getShare]: the share of the value the user gets,
 * 0–1, for the balance bar (0.5: even).
 */
data class Fairness(val get: SideTotal, val give: SideTotal, val diff: Double, val fair: Boolean, val unpriced: Int, val getShare: Double)

/** Both sides compared — or null when nothing on either side has a price, so there's no verdict to give. */
fun fairness(get: List<TradeCard>, give: List<TradeCard>, prices: Map<String, CardPrice>): Fairness? {
    val g = sideTotal(get, prices)
    val v = sideTotal(give, prices)
    if (g.sum == 0.0 && v.sum == 0.0) return null
    val diff = g.sum - v.sum
    return Fairness(g, v, diff, isFair(diff, g.sum, v.sum), g.unpriced + v.unpriced, g.sum / (g.sum + v.sum))
}

/** "Even — a fair trade", "Within $1.50 — a fair trade", "You give $12.00 more", "You get $3.00 more". [money]: US dollars written in the chosen currency. */
fun verdictLine(f: Fairness, money: (Double) -> String): String = when {
    f.fair && abs(f.diff) < 0.005 -> "Even — a fair trade"
    f.fair -> "Within ${money(abs(f.diff))} — a fair trade"
    f.diff > 0 -> "You get ${money(f.diff)} more"
    else -> "You give ${money(-f.diff)} more"
}

/** "2 cards have no price and are left out." — or null when every card has one. */
fun unpricedLine(n: Int): String? =
    if (n <= 0) null else "$n ${if (n == 1) "card has no price and is" else "cards have no price and are"} left out."

/** Which list a card would go on to even the trade out. */
enum class TradeSide { WANT, GIVE }

/** WANT: ask them for more (the user gives more); GIVE: offer more (the user gets more); null when it's fair already. */
fun shortSide(f: Fairness?): TradeSide? = when {
    f == null || f.fair -> null
    f.diff < 0 -> TradeSide.WANT
    else -> TradeSide.GIVE
}

/** A card that would even the trade out, and its price for one copy. */
data class Suggestion(val card: TradeCard, val price: Double)

private fun lower(s: String) = s.trim().lowercase()

/**
 * The cards that would even the trade out for [side], from the friend's trade [match]: their cards
 * the user wants (WANT), or the user's cards on their wishlist that are marked for trade or that no
 * deck of the user's plays (GIVE; [decksUse]: the names the decks use, lower case — Spares.kt).
 */
fun candidatesFor(side: TradeSide, match: TradeMatch?, decksUse: Set<String>): List<TradeCard> {
    if (match == null) return emptyList()
    return when (side) {
        TradeSide.WANT -> match.theyHave
        TradeSide.GIVE -> match.theyWant.filter { match.isMarked(it) || lower(it.name) !in decksUse }
    }
}

/**
 * Up to [max] of [candidates] that would close a [gap] (US dollars), closest to it first (then the
 * cheaper, then by name). A card already in the trade ([inTrade], either side), one with no price,
 * or a second printing of the same card is left out.
 */
fun evenOut(gap: Double, candidates: List<TradeCard>, inTrade: List<TradeCard>, prices: Map<String, CardPrice>, max: Int = 3): List<Suggestion> {
    val taken = inTrade.map { lower(it.name) }.toSet()
    val seen = mutableSetOf<String>()
    val out = mutableListOf<Suggestion>()
    for (card in candidates) {
        val key = lower(card.name)
        if (key in taken || key in seen) continue
        val price = unitPrice(card, prices)
        if (price == null || price <= 0.0) continue
        seen += key
        out += Suggestion(card.copy(quantity = 1), price)
    }
    val target = abs(gap)
    return out.sortedWith(
        compareBy<Suggestion> { abs(it.price - target) }.thenBy { it.price }.thenBy { lower(it.card.name) }
    ).take(max)
}

/** "To even it out, ask Priya for one of these" / "To even it out, offer one of these". */
fun evenOutTitle(side: TradeSide, name: String): String =
    if (side == TradeSide.WANT) "To even it out, ask $name for one of these" else "To even it out, offer one of these"
