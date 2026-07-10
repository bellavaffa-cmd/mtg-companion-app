package com.mtgcompanion.app.network.scryfall

import retrofit2.http.GET
import retrofit2.http.Query

interface ScryfallApi {
    @GET("cards/search")
    suspend fun searchCards(
        @Query("q") query: String,
        @Query("page") page: Int = 1
    ): ScryfallSearchResponse

    @GET("cards/named")
    suspend fun getCardByExactName(@Query("exact") name: String): ScryfallCard
}
