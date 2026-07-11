package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.NetworkModule
import com.mtgcompanion.app.network.spellbook.DeckCardRef
import com.mtgcompanion.app.network.spellbook.FindMyCombosRequest
import com.mtgcompanion.app.network.spellbook.Variant

class ComboRepository {
    private val api = NetworkModule.spellbookApi

    suspend fun findCombosUsing(cardName: String): List<Variant> {
        val query = "card=\"$cardName\""
        return api.findCombosForCard(query).results
    }

    /** Combos fully contained in a deck (commanders + the rest of the cards). */
    suspend fun findCombosInDeck(commanderNames: List<String>, cardNames: List<String>): List<Variant> {
        if (cardNames.isEmpty() && commanderNames.isEmpty()) return emptyList()
        val body = FindMyCombosRequest(
            commanders = commanderNames.map { DeckCardRef(it) },
            main = cardNames.map { DeckCardRef(it) }
        )
        return try {
            api.findMyCombos(body).results?.included.orEmpty()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
