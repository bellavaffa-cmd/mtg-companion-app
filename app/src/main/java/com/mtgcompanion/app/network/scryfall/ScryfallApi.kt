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
        @Query("order") order: String? = null
    ): ScryfallSearchResponse

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
}
