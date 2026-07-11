package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.NetworkModule
import com.mtgcompanion.app.network.scryfall.ScryfallCard
import com.mtgcompanion.app.network.scryfall.ScryfallCollectionRequest
import com.mtgcompanion.app.network.scryfall.ScryfallIdentifier
import retrofit2.HttpException

class CardRepository {
    private val api = NetworkModule.scryfallApi

    suspend fun search(query: String): List<ScryfallCard> {
        if (query.isBlank()) return emptyList()
        return try {
            api.searchCards(query).data
        } catch (e: HttpException) {
            // Scryfall returns 404 when a (possibly partial, as-you-type) query matches
            // no cards — treat that as an empty result rather than an error.
            if (e.code() == 404) emptyList() else throw e
        }
    }

    suspend fun getByExactName(name: String): ScryfallCard = api.getCardByExactName(name)

    suspend fun getByFuzzyName(name: String): ScryfallCard = api.getCardByFuzzyName(name)

    /** Bulk lookup by Scryfall id; batches into the 75-per-request limit of /cards/collection. */
    suspend fun getCardsByIds(ids: List<String>): List<ScryfallCard> {
        if (ids.isEmpty()) return emptyList()
        return ids.distinct().chunked(75).flatMap { chunk ->
            try {
                api.getCollection(ScryfallCollectionRequest(chunk.map { ScryfallIdentifier(it) })).data
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
