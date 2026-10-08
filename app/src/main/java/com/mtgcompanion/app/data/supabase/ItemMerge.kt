package com.mtgcompanion.app.data.supabase

import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionEntry
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckCardEntry
import com.mtgcompanion.app.data.DeckVersion
import com.mtgcompanion.app.data.keepHistoryFromOlderApp
import com.mtgcompanion.app.data.mergeHistory
import com.mtgcompanion.app.data.GameResult
import com.mtgcompanion.app.data.keepCameFromFromOlderApp
import com.mtgcompanion.app.data.keepAlertOptionsFromOlderApp
import com.mtgcompanion.app.data.keepPreReleaseFromOlderApp
import com.mtgcompanion.app.data.keepForSaleFromOlderApp
import com.mtgcompanion.app.data.keepGearFromOlderApp
import com.mtgcompanion.app.data.keepLoansFromOlderApp
import com.mtgcompanion.app.data.keepGradedFromOlderApp
import com.mtgcompanion.app.data.keepSealedFromOlderApp
import com.mtgcompanion.app.data.mergeGraded
import com.mtgcompanion.app.data.mergeSealed
import com.mtgcompanion.app.data.keepPlaceSizes
import com.mtgcompanion.app.data.keepDeckExtrasFromOlderApp
import com.mtgcompanion.app.data.withMergedExtras
import com.mtgcompanion.app.data.keepPlacesFromOlderApp
import com.mtgcompanion.app.data.mergeCameFrom
import com.mtgcompanion.app.data.mergeCopyPlaces
import com.mtgcompanion.app.data.mergeGear
import com.mtgcompanion.app.data.keepRecipesFromOlderApp
import com.mtgcompanion.app.data.keepCorrectionsFromOlderApp
import com.mtgcompanion.app.data.mergeCorrections
import com.mtgcompanion.app.data.mergeRecipes
import com.mtgcompanion.app.data.keepGoalsFromOlderApp
import com.mtgcompanion.app.data.mergeGoals
import com.mtgcompanion.app.data.mergeLoans
import com.mtgcompanion.app.data.mergePlaceLists
import com.mtgcompanion.app.data.tidied

/**
 * Three-way merge for decks and binders, so two devices editing the same one keep both sets of edits
 * instead of the newer save replacing the older one wholesale.
 *
 * Every merge compares three versions: the one both devices last agreed on (the base), this device's
 * version, and the other device's. Both devices run the same rules on the same three versions, so
 * they reach the same result and settle down.
 *
 * The rules, in short:
 *  - A card added on one side is kept.
 *  - A card removed on one side stays removed, even if the other side changed its count.
 *  - Counts that both sides changed add up: +1 here and +2 there lands on +3; two cuts that would
 *    take it below zero settle on the lower count rather than removing the card.
 *  - A field both sides changed differently (a deck's name, say) goes to the more recent edit.
 *  - Where a binder card's copies are kept (its "places") merges line by line like the cards do, and
 *    the storage places themselves (on the Unsorted pile) place by place — see StoragePlaces.kt. A
 *    binder saved by an app that doesn't know about places leaves them as they were. When a place was
 *    last checked (PlaceCheck.kt) merges to the later check. A place's size (BoxSpace.kt) and a card's
 *    copies to sell (Selling.kt) go to whoever changed them; one saved by an app that doesn't know
 *    about them leaves them as they were.
 *  - Where a deck's copies came from (its "cameFrom", see PullList.kt) merges card by card the same
 *    way; a deck saved by an app that doesn't know about it leaves it as it was.
 *  - The gear (on the Unsorted pile, see Gear.kt) merges item by item, the decks a pack of sleeves is
 *    on like a deck's tags; a pile saved by an app that doesn't know about gear leaves it as it was.
 *  - What the scanner learned from corrections (on the Unsorted pile, see ScanCorrections.kt) merges
 *    entry by entry, the one used last winning; a pile saved by an app that doesn't know about them
 *    leaves them as they were.
 *  - The sorting recipes (on the Unsorted pile, see SortRecipes.kt) merge recipe by recipe; a pile
 *    saved by an app that doesn't know about recipes leaves them as they were.
 *  - The collection goals (on the Unsorted pile, see CollectionGoals.kt) merge goal by goal; a pile
 *    saved by an app that doesn't know about goals leaves them as they were.
 *  - The loans (on the Unsorted pile, see Loans.kt) merge loan by loan, their cards card by card, and
 *    the copies back only go up; a pile saved by an app that doesn't know about loans leaves them as
 *    they were.
 *  - Sealed product and graded copies (on the Unsorted pile, see Sealed.kt and Graded.kt) merge product
 *    by product and slab by slab; a sealed product's count adds up like a card's. A pile saved by an
 *    app that doesn't know about them leaves them as they were.
 *  - A deck's primer, folder, archive flag and companion go to whoever changed them; each category's
 *    target the same, one by one; a card's categories merge like its tags. A deck saved by an app that
 *    doesn't know them leaves them as they were (DeckExtras.kt).
 *  - A deck's history (DeckHistory.kt) is every entry from both sides, once by id, the newer copy of
 *    one both have; a deck saved by an app that doesn't know it leaves it as it was.
 * The web app merges the same way — see MtgCompanionWeb/src/sync/mergeItems.ts.
 */
object ItemMerge {

    /** A field's value after a merge: whoever changed it, or the more recent edit when both did. */
    private fun <T> pick(base: T, mine: T, theirs: T, minePreferred: Boolean): T = when {
        mine == theirs -> mine
        mine == base -> theirs
        theirs == base -> mine
        else -> if (minePreferred) mine else theirs
    }

    /** Additions from both sides, minus anything either side removed. */
    private fun mergeStringSet(base: List<String>, mine: List<String>, theirs: List<String>): List<String> {
        val removed = (base - mine.toSet()).toSet() + (base - theirs.toSet()).toSet()
        return (theirs + mine).distinct().filterNot { it in removed }
    }

    /**
     * Merges card entries keyed by printing. The order both devices last agreed on is kept, with
     * whatever either side added appended by id, so both devices end up with the same list in the
     * same order.
     */
    private fun <T : Any> mergeEntries(
        base: List<T>,
        mine: List<T>,
        theirs: List<T>,
        id: (T) -> String,
        counts: (T) -> List<Int>,
        withCounts: (T, List<Int>) -> T,
        mergeRest: (base: T, mine: T, theirs: T) -> T
    ): List<T> {
        val baseMap = base.associateBy(id)
        val mineMap = mine.associateBy(id)
        val theirsMap = theirs.associateBy(id)
        // Both devices must land on the same order, so start from the order they agreed on and
        // append what either side added, by id — never "their order, then mine".
        val added = ((theirs + mine).map(id).distinct() - base.map(id).toSet()).sorted()
        val order = base.map(id) + added

        return order.mapNotNull { key ->
            val b = baseMap[key]
            val m = mineMap[key]
            val t = theirsMap[key]
            when {
                // Removing a card is deliberate, so it stays removed even if the other side touched it.
                b != null && (m == null || t == null) -> null
                b == null -> {
                    // Added on one side, or on both at once: one copy, the larger count.
                    if (m != null && t != null) {
                        withCounts(t, counts(t).zip(counts(m)) { theirCount, myCount -> maxOf(theirCount, myCount) })
                    } else {
                        t ?: m
                    }
                }
                else -> {
                    // In all three: counts add up, everything else follows whoever changed it.
                    val merged = withCounts(
                        mergeRest(b, m!!, t!!),
                        counts(t).indices.map { i ->
                            val summed = counts(t)[i] + (counts(m)[i] - counts(b)[i])
                            // Both sides cut the same card: take the lower count rather than letting
                            // two reductions cancel it out of the deck entirely.
                            if (summed <= 0 && counts(m)[i] > 0 && counts(t)[i] > 0) {
                                minOf(counts(m)[i], counts(t)[i])
                            } else {
                                summed.coerceAtLeast(0)
                            }
                        }
                    )
                    // Every count down to zero means both sides emptied it out — that's a removal.
                    if (counts(merged).isNotEmpty() && counts(merged).all { it <= 0 }) null else merged
                }
            }
        }
    }

    private fun mergeDeckCards(
        base: List<DeckCardEntry>,
        mine: List<DeckCardEntry>,
        theirs: List<DeckCardEntry>,
        minePreferred: Boolean
    ): List<DeckCardEntry> = mergeEntries(
        base, mine, theirs,
        id = { it.scryfallId },
        counts = { listOf(it.quantity) },
        withCounts = { entry, values -> entry.copy(quantity = values[0]) },
        mergeRest = { b, m, t ->
            t.copy(
                name = pick(b.name, m.name, t.name, minePreferred),
                imageUrl = pick(b.imageUrl, m.imageUrl, t.imageUrl, minePreferred),
                canBeCommander = pick(b.canBeCommander, m.canBeCommander, t.canBeCommander, minePreferred),
                typeLine = pick(b.typeLine, m.typeLine, t.typeLine, minePreferred),
                partnerAbility = pick(b.partnerAbility, m.partnerAbility, t.partnerAbility, minePreferred),
                backImageUrl = pick(b.backImageUrl, m.backImageUrl, t.backImageUrl, minePreferred),
                tags = pick(b.tags, m.tags, t.tags, minePreferred),
                // Two devices tagging the same copy keep both tags, as a deck's own tags do.
                userTags = mergeStringSet(b.userTags, m.userTags, t.userTags),
                // A card's categories in this deck merge the same way.
                categories = mergeStringSet(b.categories.orEmpty(), m.categories.orEmpty(), t.categories.orEmpty()).ifEmpty { null },
                replaceable = pick(b.replaceable, m.replaceable, t.replaceable, minePreferred),
                proxyQuantity = pick(b.proxyQuantity, m.proxyQuantity, t.proxyQuantity, minePreferred)
            )
        }
    )

    /** Games logged on either device, minus any deleted on either; newest first, like the deck page. */
    private fun mergeGameResults(base: List<GameResult>, mine: List<GameResult>, theirs: List<GameResult>): List<GameResult> {
        val removed = (base.map { it.id } - mine.map { it.id }.toSet()).toSet() +
            (base.map { it.id } - theirs.map { it.id }.toSet()).toSet()
        // The same game saved by an app that doesn't know about mulligans keeps the count the other side has.
        val counted = (theirs + mine).filter { it.mulligans != null }.associateBy { it.id }
        return (theirs + mine).distinctBy { it.id }.filterNot { it.id in removed }
            .map { g -> if (g.mulligans == null) counted[g.id]?.let { g.copy(mulligans = it.mulligans) } ?: g else g }
            .sortedByDescending { it.playedAt }
    }

    /** Saved deck versions from both devices, oldest first, capped the way the repository caps them. */
    private fun mergeVersions(mine: List<DeckVersion>, theirs: List<DeckVersion>): List<DeckVersion> =
        (theirs + mine).distinctBy { it.id }.sortedBy { it.savedAt }.takeLast(MAX_VERSIONS)

    private const val MAX_VERSIONS = 40

    /** [minePreferred]: this device's edit is the more recent one, so it wins any field both changed. */
    fun mergeDecks(base: Deck, mine: Deck, theirs: Deck, minePreferred: Boolean): Deck =
        // A side saved by an app that doesn't know where the deck's copies came from left that as it was.
        // ...and the same for its primer, folder, archive flag, companion and categories.
        // ...and its history (DeckHistory.kt).
        mergeDecksKnowingCameFrom(
            base,
            keepHistoryFromOlderApp(base, keepDeckExtrasFromOlderApp(base, keepCameFromFromOlderApp(base, mine))),
            keepHistoryFromOlderApp(base, keepDeckExtrasFromOlderApp(base, keepCameFromFromOlderApp(base, theirs))),
            minePreferred
        )

    private fun mergeDecksKnowingCameFrom(base: Deck, mine: Deck, theirs: Deck, minePreferred: Boolean): Deck = withMergedExtras(theirs.copy(
        cameFrom = mergeCameFrom(base.cameFrom, mine.cameFrom, theirs.cameFrom),
        name = pick(base.name, mine.name, theirs.name, minePreferred),
        gameMode = pick(base.gameMode, mine.gameMode, theirs.gameMode, minePreferred),
        ownership = pick(base.ownership, mine.ownership, theirs.ownership, minePreferred),
        createdAt = minOf(mine.createdAt, theirs.createdAt),
        commander = pick(base.commander, mine.commander, theirs.commander, minePreferred),
        partnerCommander = pick(base.partnerCommander, mine.partnerCommander, theirs.partnerCommander, minePreferred),
        cards = mergeDeckCards(base.cards, mine.cards, theirs.cards, minePreferred),
        considering = mergeDeckCards(base.considering, mine.considering, theirs.considering, minePreferred),
        sideboard = mergeDeckCards(base.sideboard, mine.sideboard, theirs.sideboard, minePreferred),
        tags = mergeStringSet(base.tags, mine.tags, theirs.tags),
        gameResults = mergeGameResults(base.gameResults, mine.gameResults, theirs.gameResults),
        versions = mergeVersions(mine.versions, theirs.versions),
        history = mergeHistory(mine.history, theirs.history)
    ), base, mine, theirs, minePreferred)

    fun mergeCollections(base: Collection, mine: Collection, theirs: Collection, minePreferred: Boolean): Collection =
        // A side saved by an app that doesn't know about places left them as they were, and one that
        // doesn't know about loans left those as they were.
        mergeCollectionsKnowingPlaces(
            base,
            // ...and one that doesn't know about a wishlist target's options left those as they were.
            // ...and one that doesn't know about sealed product, graded copies or gear left those as they were.
            // ...and one that doesn't know about sorting recipes left those as they were.
            // ...and one that doesn't know about what the scanner learned left that as it was.
            // ...and one that doesn't know about collection goals left those as they were.
            keepGoalsFromOlderApp(base, keepCorrectionsFromOlderApp(base, keepRecipesFromOlderApp(base, keepGearFromOlderApp(base, keepGradedFromOlderApp(base, keepSealedFromOlderApp(base, keepAlertOptionsFromOlderApp(base, keepPreReleaseFromOlderApp(base, keepForSaleFromOlderApp(base, keepPlaceSizes(base, keepLoansFromOlderApp(base, keepPlacesFromOlderApp(base, mine)))))))))))),
            keepGoalsFromOlderApp(base, keepCorrectionsFromOlderApp(base, keepRecipesFromOlderApp(base, keepGearFromOlderApp(base, keepGradedFromOlderApp(base, keepSealedFromOlderApp(base, keepAlertOptionsFromOlderApp(base, keepPreReleaseFromOlderApp(base, keepForSaleFromOlderApp(base, keepPlaceSizes(base, keepLoansFromOlderApp(base, keepPlacesFromOlderApp(base, theirs)))))))))))),
            minePreferred
        )

    private fun mergeCollectionsKnowingPlaces(base: Collection, mine: Collection, theirs: Collection, minePreferred: Boolean): Collection = theirs.copy(
        storagePlaces = mergePlaceLists(base.storagePlaces, mine.storagePlaces, theirs.storagePlaces, minePreferred),
        loans = mergeLoans(base.loans, mine.loans, theirs.loans, minePreferred),
        sealed = mergeSealed(base.sealed, mine.sealed, theirs.sealed, minePreferred),
        graded = mergeGraded(base.graded, mine.graded, theirs.graded, minePreferred),
        gear = mergeGear(base.gear, mine.gear, theirs.gear, minePreferred),
        sortRecipes = mergeRecipes(base.sortRecipes, mine.sortRecipes, theirs.sortRecipes, minePreferred),
        scanCorrections = mergeCorrections(base.scanCorrections, mine.scanCorrections, theirs.scanCorrections, minePreferred),
        collectionGoals = mergeGoals(base.collectionGoals, mine.collectionGoals, theirs.collectionGoals, minePreferred),
        name = pick(base.name, mine.name, theirs.name, minePreferred),
        type = pick(base.type, mine.type, theirs.type, minePreferred),
        createdAt = minOf(mine.createdAt, theirs.createdAt),
        notWanted = mergeStringSet(base.notWanted, mine.notWanted, theirs.notWanted),
        entries = mergeEntries(
            base.entries, mine.entries, theirs.entries,
            id = { it.scryfallId },
            counts = { listOf(it.quantity, it.foilQuantity) },
            // Each card's places line by line (one added on both sides keeps the other device's), then no
            // more than its merged copies.
            withCounts = { entry, values -> tidied(entry.copy(quantity = values[0], foilQuantity = values[1])) },
            mergeRest = { b, m, t ->
                t.copy(
                    places = mergeCopyPlaces(b.places, m.places, t.places),
                    name = pick(b.name, m.name, t.name, minePreferred),
                    imageUrl = pick(b.imageUrl, m.imageUrl, t.imageUrl, minePreferred),
                    backImageUrl = pick(b.backImageUrl, m.backImageUrl, t.backImageUrl, minePreferred),
                    tags = pick(b.tags, m.tags, t.tags, minePreferred),
                    userTags = mergeStringSet(b.userTags, m.userTags, t.userTags),
                    priceAlert = pick(b.priceAlert, m.priceAlert, t.priceAlert, minePreferred),
                    priceAlertAbove = pick(b.priceAlertAbove, m.priceAlertAbove, t.priceAlertAbove, minePreferred),
                    alertAnyPrinting = pick(b.alertAnyPrinting, m.alertAnyPrinting, t.alertAnyPrinting, minePreferred),
                    alertFoilOnly = pick(b.alertFoilOnly, m.alertFoilOnly, t.alertFoilOnly, minePreferred),
                    condition = pick(b.condition, m.condition, t.condition, minePreferred),
                    language = pick(b.language, m.language, t.language, minePreferred),
                    forTrade = pick(b.forTrade, m.forTrade, t.forTrade, minePreferred),
                    forSale = pick(b.forSale, m.forSale, t.forSale, minePreferred),
                    auto = pick(b.auto, m.auto, t.auto, minePreferred),
                    preRelease = pick(b.preRelease, m.preRelease, t.preRelease, minePreferred)
                )
            }
        )
    )
}
