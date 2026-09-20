package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.NetworkModule
import com.mtgcompanion.app.network.spellbook.DeckCardRef
import com.mtgcompanion.app.network.spellbook.FindMyCombosRequest
import com.mtgcompanion.app.network.spellbook.Variant

/** [included]: complete combos in a deck. [almostIncluded]: combos it's exactly one card short of. */
data class DeckCombos(val included: List<Variant>, val almostIncluded: List<Variant>)

class ComboRepository {
    private val api = NetworkModule.spellbookApi

    /**
     * The combos [cardName] is in. Kept on the device for a week (see [ComboCache]): the lookup
     * takes about a second, and the same card comes up again and again as cards are looked at.
     */
    suspend fun findCombosUsing(cardName: String): List<Variant> {
        ComboCache.get(cardName)?.let { return it }
        val query = "card=\"$cardName\""
        val results = api.findCombosForCard(query).results
        ComboCache.put(cardName, results)
        return results
    }

    /**
     * Combos a deck contains (commanders + the rest of the cards), plus those it's a single card
     * away from. Null when the lookup fails — distinct from "no combos", so callers don't tell the
     * user a deck has none just because they're offline.
     */
    suspend fun findCombosInDeck(commanderNames: List<String>, cardNames: List<String>): DeckCombos? {
        if (cardNames.isEmpty() && commanderNames.isEmpty()) return DeckCombos(emptyList(), emptyList())
        // Kept for a week under this exact decklist, so opening a deck again is instant while an
        // edited deck asks afresh.
        val key = ComboCache.deckKey(commanderNames, cardNames)
        ComboCache.getDeck(key)?.let { return it }
        val body = FindMyCombosRequest(
            commanders = commanderNames.map { DeckCardRef(it) },
            main = cardNames.map { DeckCardRef(it) }
        )
        return try {
            val results = api.findMyCombos(body).results
            DeckCombos(results?.included.orEmpty(), results?.almostIncluded.orEmpty()).also { ComboCache.putDeck(key, it) }
        } catch (e: Exception) {
            null
        }
    }

    /** Free-form combo search against Commander Spellbook's own query language (e.g.
     * `card:"Sol Ring" result:"mana" ci<=WU`) — used by the Rules screen's Combos tab. */
    suspend fun search(query: String, limit: Int = 30): List<Variant> {
        if (query.isBlank()) return emptyList()
        return api.findCombosForCard(query, limit).results
    }
}
