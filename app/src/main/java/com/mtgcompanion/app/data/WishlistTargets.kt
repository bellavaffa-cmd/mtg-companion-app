package com.mtgcompanion.app.data

import kotlin.math.roundToInt
import kotlin.math.roundToLong

// Price targets on the Wishlist: a wishlist card's price alert ([CollectionEntry.priceAlert] — "tell
// me when it's at or under this"), with two options of its own, and what the Wishlist shows about
// them. When an alert goes off is still PriceAlertRules.kt; this is the rest:
//  - [CollectionEntry.alertAnyPrinting]: any printing of the card counts — the cheapest is checked;
//  - [CollectionEntry.alertFoilOnly]: only a foil copy will do — the foil price is checked;
//  - a target from a percentage off today's price, for the target sheet and "Set targets for all…";
//  - the row's line ("Target $70 · $3.40 to go"), the week's drop and the year's low from the card's
//    own price history (CardPriceHistory.kt), left out when the history doesn't say;
//  - the "Under your price" box: the cards under their target, until "Got it" — and again when the
//    card drops further, or goes back over and comes under again (the notification's own rule).
// Mirrors the web app's src/collection/wishlistTargets.ts.

/** The chips under the target: so much off today's price. */
val TARGET_PERCENTS = listOf(10, 20)

/** A target [percent] under [now] (US dollars), to the cent; null without a price. */
fun targetFromPercent(now: Double?, percent: Int): Double? {
    if (now == null || now <= 0) return null
    val p = percent.coerceIn(0, 99)
    return (now * (100 - p)).roundToLong() / 100.0
}

/** One printing's name and prices (US dollars), for "Any printing counts". */
data class PrintingPrice(val name: String, val usd: Double?, val usdFoil: Double?)

// Whether a printing is the card wanted is the scanner's rule, sameCard (Sight.kt): either face of a
// double-faced card counts.

/**
 * The cheapest non-foil and foil prices among [printings] that are the card [name] (each on its
 * own: the cheapest foil may be another printing than the cheapest plain one). Null where none has one.
 */
fun cheapestPrinting(name: String, printings: List<PrintingPrice>): Pair<Double?, Double?> {
    var usd: Double? = null
    var foil: Double? = null
    for (p in printings) {
        if (!sameCard(name, p.name)) continue
        if (p.usd != null && p.usd > 0 && (usd == null || p.usd < usd)) usd = p.usd
        if (p.usdFoil != null && p.usdFoil > 0 && (foil == null || p.usdFoil < foil)) foil = p.usdFoil
    }
    return usd to foil
}

/**
 * The targets "Set targets for all…" sets: [percent] off today's price, for every card without a
 * target and with a price (scryfallId -> US dollars).
 */
fun targetsForAll(entries: List<CollectionEntry>, prices: Map<String, Double?>, percent: Int): Map<String, Double> {
    val out = LinkedHashMap<String, Double>()
    for (e in entries) {
        if ((e.priceAlert ?: 0.0) > 0) continue
        val target = targetFromPercent(prices[e.scryfallId], percent) ?: continue
        if (target > 0) out[e.scryfallId] = target
    }
    return out
}

/** Which prices count for a target: "Any printing counts" and "Foil only". */
data class TargetOptions(val anyPrinting: Boolean, val foilOnly: Boolean)

/**
 * [entry] with its target and options: both options are written (true or false) once a target is
 * set here, so an entry without them was saved by an app that doesn't know them. No target takes
 * the alert off and leaves the options as they were.
 */
fun withTarget(entry: CollectionEntry, usd: Double?, options: TargetOptions? = null): CollectionEntry = when {
    usd == null || usd <= 0 -> entry.copy(priceAlert = null)
    options == null -> entry.copy(priceAlert = usd)
    else -> entry.copy(priceAlert = usd, alertAnyPrinting = options.anyPrinting, alertFoilOnly = options.foilOnly)
}

/** What the card cost on [day], by [series] (oldest first); null when it doesn't go back so far. */
private fun priceOn(series: List<Pair<Long, Double>>, day: Long): Double? {
    var at: Double? = null
    for ((d, p) in series) {
        if (d <= day) at = p else break
    }
    return at
}

/**
 * How much [track]'s price (foil with [foil]) dropped over the last week, in whole percent: null
 * when it didn't drop, or the history doesn't go back a week.
 */
fun weekDrop(track: PriceTrack?, foil: Boolean, today: Long): Int? {
    track ?: return null
    val series = priceSeries(track, if (foil) PriceKind.USD_FOIL else PriceKind.USD)
    if (series.isEmpty()) return null
    val then = priceOn(series, today - 7) ?: return null
    val now = series.last().second
    if (then <= 0 || now >= then) return null
    val percent = ((then - now) / then * 100).roundToInt()
    return percent.takeIf { it >= 1 }
}

/** How many days of history "lowest this year" needs before it's said. */
const val YEAR_LOW_DAYS = 30

/** The lowest [track]'s price (foil with [foil]) has been over the history kept (a year); null with under [YEAR_LOW_DAYS] of it. */
fun yearLow(track: PriceTrack?, foil: Boolean): Double? {
    if (track == null || track.days < YEAR_LOW_DAYS) return null
    return priceSeries(track, if (foil) PriceKind.USD_FOIL else PriceKind.USD).minOfOrNull { it.second }
}

/** A price short: "$70" when it's whole, "$3.40" when it isn't. [money]: (US dollars, whole) -> text. */
fun shortPrice(usd: Double, money: (Double, Boolean) -> String): String {
    val full = money(usd, false)
    val whole = money(usd, true)
    return if (full.replace(Regex("[.,]00(?=\\D*$)"), "") == whole) whole else full
}

/** A wishlist row's line under the name: the target and how far off it is. */
fun targetLine(target: Double?, price: Double?, dropped: Int?, money: (Double, Boolean) -> String): String {
    if (target == null || target <= 0) return "No target · tap to set one"
    val t = "Target ${shortPrice(target, money)}"
    if (price == null) return t
    if (price <= target) return if (dropped != null) "$t · dropped $dropped% this week" else "$t · under your price"
    return "$t · ${shortPrice(((price - target) * 100).roundToLong() / 100.0, money)} to go"
}

/** "8 CARDS · 3 WITH A TARGET" over the Wishlist's rows (shown upper-case). */
fun targetCount(entries: List<CollectionEntry>): String {
    val cards = entries.size
    val set = entries.count { (it.priceAlert ?: 0.0) > 0 }
    return "$cards ${if (cards == 1) "card" else "cards"} · $set with a target"
}

/** What the Wishlist costs: each card's price × the copies wanted (at least one); null before any price is known. */
fun wishlistTotal(entries: List<CollectionEntry>, prices: Map<String, Double?>): Double? {
    var total = 0.0
    var any = false
    for (e in entries) {
        val p = prices[e.scryfallId] ?: continue
        any = true
        total += p * maxOf(1, e.quantity + e.foilQuantity)
    }
    return if (any) (total * 100).roundToLong() / 100.0 else null
}

/**
 * The "Under your price" box: wishlist cards under their target now ([hits]), less the ones the user
 * said "Got it" to ([gotIt]: memory key -> the price then) — unless it's dropped further since.
 */
fun underYourPrice(hits: List<AlertHit>, gotIt: Map<String, Double>): List<AlertHit> =
    hits.filter { it.watch.direction == AlertDirection.BELOW && alertStep(it.watch, it.price, gotIt[it.watch.memoryKey]) is AlertStep.Tell }

/** [gotIt] with [shown] said "Got it" to. */
fun withGotIt(gotIt: Map<String, Double>, shown: List<AlertHit>): Map<String, Double> =
    gotIt + shown.associate { it.watch.memoryKey to it.price }

/**
 * [gotIt] less cards no longer under their target ([hits]: all that are, once prices are in) — so
 * one that goes back over and comes under again is shown again. The same map when nothing goes.
 */
fun gotItKept(gotIt: Map<String, Double>, hits: List<AlertHit>): Map<String, Double> {
    val under = hits.filter { it.watch.direction == AlertDirection.BELOW }.map { it.watch.memoryKey }.toSet()
    return if (gotIt.keys.all { it in under }) gotIt else gotIt.filterKeys { it in under }
}

/**
 * [theirs] with each entry's alert options put back where [source] (the same binder, as this device
 * has it) has them and [theirs] doesn't say — an entry saved by an app that doesn't know them comes
 * without them. The same object when nothing changes.
 */
fun keepAlertOptionsFromOlderApp(source: Collection, theirs: Collection): Collection {
    val mine = source.entries.filter { it.alertAnyPrinting != null || it.alertFoilOnly != null }.associateBy { it.scryfallId }
    fun lacking(e: CollectionEntry): Boolean {
        val m = mine[e.scryfallId] ?: return false
        return (e.alertAnyPrinting == null && m.alertAnyPrinting != null) || (e.alertFoilOnly == null && m.alertFoilOnly != null)
    }
    if (mine.isEmpty() || theirs.entries.none(::lacking)) return theirs
    return theirs.copy(entries = theirs.entries.map { e ->
        if (!lacking(e)) e
        else {
            val m = mine.getValue(e.scryfallId)
            e.copy(alertAnyPrinting = e.alertAnyPrinting ?: m.alertAnyPrinting, alertFoilOnly = e.alertFoilOnly ?: m.alertFoilOnly)
        }
    })
}
