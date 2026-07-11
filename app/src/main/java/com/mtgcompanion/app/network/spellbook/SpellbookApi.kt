package com.mtgcompanion.app.network.spellbook

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface SpellbookApi {
    @GET("variants")
    suspend fun findCombosForCard(
        @Query("q") query: String,
        @Query("limit") limit: Int = 20
    ): VariantsResponse

    @POST("find-my-combos")
    suspend fun findMyCombos(@Body body: FindMyCombosRequest): FindMyCombosResponse
}
