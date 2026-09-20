package com.mtgcompanion.app.data.supabase

import com.mtgcompanion.app.data.Collection
import com.mtgcompanion.app.data.CollectionEntry
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.DeckCardEntry
import com.mtgcompanion.app.data.DeckVersion
import com.mtgcompanion.app.data.GameResult

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
                replaceable = pick(b.replaceable, m.replaceable, t.replaceable, minePreferred),
                proxyQuantity = pick(b.proxyQuantity, m.proxyQuantity, t.proxyQuantity, minePreferred)
            )
        }
    )

    /** Games logged on either device, minus any deleted on either; newest first, like the deck page. */
    private fun mergeGameResults(base: List<GameResult>, mine: List<GameResult>, theirs: List<GameResult>): List<GameResult> {
        val removed = (base.map { it.id } - mine.map { it.id }.toSet()).toSet() +
            (base.map { it.id } - theirs.map { it.id }.toSet()).toSet()
        return (theirs + mine).distinctBy { it.id }.filterNot { it.id in removed }.sortedByDescending { it.playedAt }
    }

    /** Saved deck versions from both devices, oldest first, capped the way the repository caps them. */
    private fun mergeVersions(mine: List<DeckVersion>, theirs: List<DeckVersion>): List<DeckVersion> =
        (theirs + mine).distinctBy { it.id }.sortedBy { it.savedAt }.takeLast(MAX_VERSIONS)

    private const val MAX_VERSIONS = 40

    /** [minePreferred]: this device's edit is the more recent one, so it wins any field both changed. */
    fun mergeDecks(base: Deck, mine: Deck, theirs: Deck, minePreferred: Boolean): Deck = theirs.copy(
        name = pick(base.name, mine.name, theirs.name, minePreferred),
        gameMode = pick(base.gameMode, mine.gameMode, theirs.gameMode, minePreferred),
        ownership = pick(base.ownership, mine.ownership, theirs.ownership, minePreferred),
        createdAt = minOf(mine.createdAt, theirs.createdAt),
        commander = pick(base.commander, mine.commander, theirs.commander, minePreferred),
        partnerCommander = pick(base.partnerCommander, mine.partnerCommander, theirs.partnerCommander, minePreferred),
        cards = mergeDeckCards(base.cards, mine.cards, theirs.cards, minePreferred),
        considering = mergeDeckCards(base.considering, mine.considering, theirs.considering, minePreferred),
        tags = mergeStringSet(base.tags, mine.tags, theirs.tags),
        gameResults = mergeGameResults(base.gameResults, mine.gameResults, theirs.gameResults),
        versions = mergeVersions(mine.versions, theirs.versions)
    )

    fun mergeCollections(base: Collection, mine: Collection, theirs: Collection, minePreferred: Boolean): Collection = theirs.copy(
        name = pick(base.name, mine.name, theirs.name, minePreferred),
        type = pick(base.type, mine.type, theirs.type, minePreferred),
        createdAt = minOf(mine.createdAt, theirs.createdAt),
        notWanted = mergeStringSet(base.notWanted, mine.notWanted, theirs.notWanted),
        entries = mergeEntries(
            base.entries, mine.entries, theirs.entries,
            id = { it.scryfallId },
            counts = { listOf(it.quantity, it.foilQuantity) },
            withCounts = { entry, values -> entry.copy(quantity = values[0], foilQuantity = values[1]) },
            mergeRest = { b, m, t ->
                t.copy(
                    name = pick(b.name, m.name, t.name, minePreferred),
                    imageUrl = pick(b.imageUrl, m.imageUrl, t.imageUrl, minePreferred),
                    backImageUrl = pick(b.backImageUrl, m.backImageUrl, t.backImageUrl, minePreferred),
                    tags = pick(b.tags, m.tags, t.tags, minePreferred),
                    priceAlert = pick(b.priceAlert, m.priceAlert, t.priceAlert, minePreferred),
                    auto = pick(b.auto, m.auto, t.auto, minePreferred)
                )
            }
        )
    )
}
