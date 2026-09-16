package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.spellbook.Variant

/**
 * Deck-building guidance derived from a deck's list: what each card does for the deck, how the
 * mana base lines up with the spells, how the list has changed over time, and what's still missing
 * from the collection. Pure functions — the network lookups that feed them live in the ViewModel.
 */

/**
 * A job a card does in a deck. [otag] is Scryfall's community-curated oracle tag (searchable as
 * `otag:ramp`), which is far more reliable than pattern-matching rules text; [heuristicTag] is the
 * matching tag from [com.mtgcompanion.app.network.scryfall.ScryfallCard.tags], used when Scryfall
 * can't be reached. [commanderMin]..[commanderMax] is the usual range for a 100-card Commander deck.
 */
enum class DeckRole(val label: String, val otag: String?, val heuristicTag: String?, val commanderMin: Int, val commanderMax: Int) {
    LANDS("Lands", null, null, 35, 38),
    RAMP("Ramp", "ramp", "Ramp", 10, 12),
    DRAW("Card draw", "draw", "Card Draw", 10, 12),
    REMOVAL("Removal", "removal", "Removal", 8, 10),
    BOARD_WIPE("Board wipes", "board-wipe", "Board Wipe", 2, 3);

    companion object {
        val TAGGED = entries.filter { it.otag != null }
    }
}

/** [min]/[max] are null outside Commander, where these targets don't apply. */
data class RoleCount(val role: DeckRole, val count: Int, val cards: List<String>, val min: Int?, val max: Int?) {
    val status: RoleStatus
        get() = when {
            min == null || max == null -> RoleStatus.NO_TARGET
            count < min -> RoleStatus.SHORT
            count > max -> RoleStatus.OVER
            else -> RoleStatus.ON_TARGET
        }
}

enum class RoleStatus { SHORT, ON_TARGET, OVER, NO_TARGET }

/**
 * Whether a card is played as a land: its front face's type line says Land. That catches artifact
 * lands and Dryad Arbor, which a "primary type" ranking files under Artifact/Creature, while a
 * spell with a land back face (a modal DFC) still counts as the spell it's usually cast as. The
 * roles panel and the mana base both use this, so their land counts agree.
 */
fun isLandType(typeLine: String?): Boolean =
    typeLine?.substringBefore(" // ")?.contains("Land", ignoreCase = true) == true

/**
 * Counts each role across [cards] (by copies). [rolesOf] says which roles a non-land card fills.
 * Board wipes are usually tagged as removal too; they're counted as wipes only, so the two numbers
 * don't double-count the same card.
 */
fun countRoles(cards: List<DeckCardEntry>, mode: GameMode, rolesOf: (DeckCardEntry) -> Set<DeckRole>): List<RoleCount> {
    val commander = mode == GameMode.COMMANDER
    val tally = DeckRole.entries.associateWith { mutableListOf<DeckCardEntry>() }
    cards.forEach { entry ->
        if (isLandType(entry.typeLine)) {
            tally.getValue(DeckRole.LANDS) += entry
            return@forEach
        }
        val roles = rolesOf(entry)
        roles.forEach roleLoop@{ role ->
            if (role == DeckRole.REMOVAL && DeckRole.BOARD_WIPE in roles) return@roleLoop
            if (role != DeckRole.LANDS) tally.getValue(role) += entry
        }
    }
    return DeckRole.entries.map { role ->
        val entries = tally.getValue(role)
        RoleCount(
            role = role,
            count = entries.sumOf { it.quantity },
            // One line per card, not per printing: three Swamp printings read as "Swamp ×25".
            cards = entries.groupBy { it.name }.toSortedMap().map { (name, copies) ->
                val total = copies.sumOf { it.quantity }
                if (total > 1) "$name ×$total" else name
            },
            min = if (commander) role.commanderMin else null,
            max = if (commander) role.commanderMax else null
        )
    }
}

/**
 * Plain-language warnings about the mana base. Mana symbols are written as `{U}` etc. so the UI
 * can render real symbols. Sources are lands only — mana rocks and dorks aren't counted, which the
 * caller should say.
 */
fun manaBaseAdvice(
    pips: List<Pair<String, Int>>,
    landSources: List<Pair<String, Int>>,
    landCount: Int,
    mode: GameMode
): List<String> {
    val advice = mutableListOf<String>()
    val colored = pips.filter { it.first in COLORS && it.second > 0 }
    val totalPips = colored.sumOf { it.second }
    val sourcesByColor = landSources.toMap()
    val totalSources = landSources.filter { it.first in COLORS }.sumOf { it.second }

    if (totalPips > 0 && landCount > 0) {
        colored.forEach { (color, count) ->
            val pipShare = count.toDouble() / totalPips
            val sources = sourcesByColor[color] ?: 0
            if (sources == 0) {
                advice += "No lands make {$color}, but $count of your mana symbols need it."
            } else if (totalSources > 0) {
                val sourceShare = sources.toDouble() / totalSources
                if (pipShare - sourceShare >= 0.12) {
                    advice += "{$color} is ${percent(pipShare)} of your mana symbols but only ${percent(sourceShare)} of your land sources — add more {$color} sources."
                }
            }
        }
    }

    val (low, high) = if (mode == GameMode.COMMANDER) 34 to 40 else 20 to 27
    val typical = if (mode == GameMode.COMMANDER) "36–38" else "22–26"
    if (landCount in 1 until low) {
        advice += "$landCount lands is light — most decks like this run $typical, fewer only with plenty of cheap ramp."
    } else if (landCount > high) {
        advice += "$landCount lands is on the heavy side — most decks like this run $typical."
    }
    return advice
}

private val COLORS = setOf("W", "U", "B", "R", "G")

private fun percent(share: Double) = "${(share * 100).toInt()}%"

/** One version of a deck and what changed since the version before it, with the games played on it. */
data class VersionSummary(
    val version: DeckVersion,
    val added: List<Pair<String, Int>>,
    val removed: List<Pair<String, Int>>,
    val isBaseline: Boolean,
    val wins: Int,
    val losses: Int,
    val draws: Int
) {
    val games: Int get() = wins + losses + draws
}

/**
 * Newest first. Games are credited to whichever version was current when they were played — from
 * that version's save time until the next one's.
 */
fun versionSummaries(deck: Deck): List<VersionSummary> {
    val versions = deck.versions.sortedBy { it.savedAt }
    return versions.mapIndexed { index, version ->
        val previous = versions.getOrNull(index - 1)
        val next = versions.getOrNull(index + 1)
        val added = mutableListOf<Pair<String, Int>>()
        val removed = mutableListOf<Pair<String, Int>>()
        if (previous != null) {
            (version.cards.keys + previous.cards.keys).forEach { name ->
                val delta = (version.cards[name] ?: 0) - (previous.cards[name] ?: 0)
                if (delta > 0) added += name to delta
                if (delta < 0) removed += name to -delta
            }
        }
        val games = deck.gameResults.filter { it.playedAt >= version.savedAt && (next == null || it.playedAt < next.savedAt) }
        VersionSummary(
            version = version,
            added = added.sortedBy { it.first },
            removed = removed.sortedBy { it.first },
            isBaseline = previous == null,
            wins = games.count { it.result == "WIN" },
            losses = games.count { it.result == "LOSS" },
            draws = games.count { it.result == "DRAW" }
        )
    }.reversed()
}

/** A deck card the user doesn't have enough copies of, and how many more they'd need. */
data class MissingCard(val entry: DeckCardEntry, val need: Int)

/**
 * Cards in [deck] not covered by the user's OWNED binders plus their Physical decks, matched by
 * name so any printing counts. Wishlist binders are cards the user *wants*, so they don't count.
 * A Physical deck covers itself, and so never has anything missing.
 */
fun missingCards(deck: Deck, collections: List<Collection>, decks: List<Deck>): List<MissingCard> {
    val owned = mutableMapOf<String, Int>()
    collections.filter { it.kind == CollectionType.OWNED }.forEach { collection ->
        collection.entries.forEach { entry ->
            val key = entry.name.lowercase()
            owned[key] = (owned[key] ?: 0) + entry.quantity + entry.foilQuantity
        }
    }
    decks.filter { it.ownershipType == DeckOwnership.PHYSICAL }.forEach { physical ->
        physical.cards.forEach { entry ->
            val key = entry.name.lowercase()
            owned[key] = (owned[key] ?: 0) + entry.quantity
        }
    }
    // Grouped by name like ownership is, so a deck's three Swamp printings are one "30 Swamp" line
    // — per printing, owned copies would be subtracted from each printing again.
    return deck.cards.groupBy { it.name.lowercase() }.mapNotNull { (key, printings) ->
        val need = printings.sumOf { it.quantity } - (owned[key] ?: 0)
        if (need > 0) MissingCard(printings.first(), need) else null
    }.sortedBy { it.entry.name }
}

/**
 * Name keys for matching a card across sources. Includes the front face alone, since a
 * double-faced card is "A // B" to Scryfall but often just "A" elsewhere.
 */
fun cardNameKeys(name: String): Set<String> {
    val full = name.trim().lowercase()
    return setOf(full, full.substringBefore(" // ").trim())
}

/** A combo a deck is one card short of, and the card(s) it's missing. */
data class NearMissCombo(val combo: Variant, val missing: List<String>)

/** For each card name key in a deck, the combos it's a piece of. */
fun comboPieces(combos: List<Variant>): Map<String, List<Variant>> {
    val pieces = mutableMapOf<String, MutableList<Variant>>()
    combos.forEach { combo ->
        combo.uses.forEach { use ->
            cardNameKeys(use.card.name).forEach { key -> pieces.getOrPut(key) { mutableListOf() } += combo }
        }
    }
    return pieces
}

/** Which of [combo]'s cards aren't among [inDeck] name keys. */
fun missingPieces(combo: Variant, inDeck: Set<String>): List<String> =
    combo.uses.map { it.card.name }.filter { name -> cardNameKeys(name).none { it in inDeck } }.distinct()
