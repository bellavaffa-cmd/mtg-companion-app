package com.mtgcompanion.app.network.scryfall

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ScryfallApi {
    @GET("cards/search")
    suspend fun searchCards(
        @Query("q") query: String,
        @Query("page") page: Int = 1
    ): ScryfallSearchResponse

    @GET("cards/named")
    suspend fun getCardByExactName(@Query("exact") name: String): ScryfallCard

    @GET("cards/named")
    suspend fun getCardByFuzzyName(@Query("fuzzy") name: String): ScryfallCard

    @POST("cards/collection")
    suspend fun getCollection(@Body body: ScryfallCollectionRequest): ScryfallSearchResponse
}
