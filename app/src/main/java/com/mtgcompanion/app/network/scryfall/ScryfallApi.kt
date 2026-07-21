package com.mtgcompanion.app.network.scryfall

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ScryfallApi {
    @GET("cards/search")
    suspend fun searchCards(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("unique") unique: String? = null,
        @Query("order") order: String? = null,
        @Query("dir") dir: String? = null
    ): ScryfallSearchResponse

    /** Card-name suggestions for as-you-type search, e.g. "sol r" -> ["Sol Ring"]. */
    @GET("cards/autocomplete")
    suspend fun autocomplete(@Query("q") query: String): ScryfallCatalog

    /** A single random card, optionally constrained by a search query. */
    @GET("cards/random")
    suspend fun getRandomCard(@Query("q") query: String? = null): ScryfallCard

    @GET("cards/named")
    suspend fun getCardByExactName(@Query("exact") name: String): ScryfallCard

    @GET("cards/named")
    suspend fun getCardByFuzzyName(@Query("fuzzy") name: String): ScryfallCard

    /** A specific printing by set code + collector number, e.g. cards/msc/211. */
    @GET("cards/{set}/{number}")
    suspend fun getCardBySetNumber(@Path("set") set: String, @Path("number") number: String): ScryfallCard

    @POST("cards/collection")
    suspend fun getCollection(@Body body: ScryfallCollectionRequest): ScryfallCollectionResponse

    /** Catalog of bulk-data files; we use the "oracle_cards" entry's download_uri for offline search. */
    @GET("bulk-data")
    suspend fun getBulkData(): BulkDataList

    /** Official rulings for a card, by its Scryfall id. */
    @GET("cards/{id}/rulings")
    suspend fun getRulings(@Path("id") id: String): ScryfallRulingsResponse
}
