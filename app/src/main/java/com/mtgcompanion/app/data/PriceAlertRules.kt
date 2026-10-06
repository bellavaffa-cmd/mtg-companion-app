package com.mtgcompanion.app.data

// When a price alert goes off. Two kinds, one per binder kind:
//  - a wishlist card's [CollectionEntry.priceAlert]: tell me when it's at or BELOW this (to buy it);
//  - an owned card's [CollectionEntry.priceAlertAbove]: tell me when it rises to or ABOVE this (to
//    sell or trade it).
// Both are US dollars. A wishlist alert is checked against the non-foil price (the foil price when
// it's "Foil only", and the cheapest printing's when "Any printing counts" — see WishlistTargets.kt,
// which also says what the Wishlist shows about them); a rise alert against
// the non-foil price too, unless every copy in the entry is foil, when it's the foil price. Kept
// apart from PriceAlerts (the notification and the background check) so it can be tested on its
// own. Mirrors the web app's src/collection/priceAlerts.ts.

enum class AlertDirection { BELOW, ABOVE }

/** One card watched for a price, in the binder [collectionId]. */
data class AlertWatch(val collectionId: String, val entry: CollectionEntry, val direction: AlertDirection, val target: Double) {
    /** Where the background check remembers what it last told about this watch. */
    val memoryKey: String get() = if (direction == AlertDirection.ABOVE) "above:${entry.scryfallId}" else entry.scryfallId
    /**
     * Where its prices are in a price map: under the card's printing, or — when any printing counts —
     * under "any:" and the printing, the cheapest printing's prices (see WishlistTargets.kt).
     */
    val priceKey: String get() = if (direction == AlertDirection.BELOW && entry.alertAnyPrinting == true) "any:${entry.scryfallId}" else entry.scryfallId
}

/** Every alert set: wishlist cards' "at or below" and owned binder cards' "at or above". */
fun alertWatches(collections: List<Collection>): List<AlertWatch> = collections.flatMap { c ->
    c.entries.mapNotNull { e ->
        when (c.kind) {
            CollectionType.WISHLIST -> e.priceAlert?.takeIf { it > 0 }?.let { AlertWatch(c.id, e, AlertDirection.BELOW, it) }
            CollectionType.OWNED -> e.priceAlertAbove?.takeIf { it > 0 }?.let { AlertWatch(c.id, e, AlertDirection.ABOVE, it) }
        }
    }
}

/** The price a watch is checked against, from the card's non-foil and foil prices (US dollars). */
fun alertPrice(watch: AlertWatch, usd: Double?, usdFoil: Double?): Double? =
    if (watch.direction == AlertDirection.ABOVE && watch.entry.quantity <= 0 && watch.entry.foilQuantity > 0) usdFoil ?: usd
    // "Foil only": a plain copy at that price won't do.
    else if (watch.direction == AlertDirection.BELOW && watch.entry.alertFoilOnly == true) usdFoil
    else usd

/** Whether [price] has crossed the watch's line. */
fun crossed(watch: AlertWatch, price: Double): Boolean = when (watch.direction) {
    AlertDirection.BELOW -> price <= watch.target
    AlertDirection.ABOVE -> price >= watch.target
}

/** What the background check does with one watch. */
sealed interface AlertStep {
    /** Not crossed: forget what was told, so crossing again tells again. */
    data object Forget : AlertStep
    /** Crossed, and already told at this price or a less striking one: say nothing. */
    data object Quiet : AlertStep
    /** Crossed, further than last told (or for the first time): tell, and remember [price]. */
    data class Tell(val price: Double) : AlertStep
}

/**
 * The step for a watch now at [price], having last told about it at [told] (null: not told since it
 * last crossed). A card is told about again only when it moves further past the line — cheaper for a
 * wishlist card, dearer for an owned one — or after going back and crossing again.
 */
fun alertStep(watch: AlertWatch, price: Double, told: Double?): AlertStep = when {
    !crossed(watch, price) -> AlertStep.Forget
    told == null -> AlertStep.Tell(price)
    watch.direction == AlertDirection.BELOW && price < told - 0.001 -> AlertStep.Tell(price)
    watch.direction == AlertDirection.ABOVE && price > told + 0.001 -> AlertStep.Tell(price)
    else -> AlertStep.Quiet
}

/** A watch that has crossed its line, at [price] — for the notification and Home's list. */
data class AlertHit(val watch: AlertWatch, val price: Double)

/** The watches that are past their line now, by [prices] ([AlertWatch.priceKey] -> non-foil, foil US dollars). */
fun alertHits(watches: List<AlertWatch>, prices: Map<String, Pair<Double?, Double?>>): List<AlertHit> =
    watches.mapNotNull { w ->
        val (usd, foil) = prices[w.priceKey] ?: return@mapNotNull null
        val price = alertPrice(w, usd, foil) ?: return@mapNotNull null
        if (crossed(w, price)) AlertHit(w, price) else null
    }
