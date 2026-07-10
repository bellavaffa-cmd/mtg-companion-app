package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.NetworkModule
import com.mtgcompanion.app.network.scryfall.ScryfallCard

class CardRepository {
    private val api = NetworkModule.scryfallApi

    suspend fun search(query: String): List<ScryfallCard> {
        if (query.isBlank()) return emptyList()
        return api.searchCards(query).data
    }

    suspend fun getByExactName(name: String): ScryfallCard = api.getCardByExactName(name)
}
