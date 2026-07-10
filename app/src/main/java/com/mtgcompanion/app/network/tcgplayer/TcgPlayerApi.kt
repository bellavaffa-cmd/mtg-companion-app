package com.mtgcompanion.app.network.tcgplayer

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface TcgPlayerApi {
    @FormUrlEncoded
    @POST("token")
    suspend fun getToken(
        @Field("grant_type") grantType: String = "client_credentials",
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String
    ): TcgTokenResponse

    @GET("pricing/product/{productId}")
    suspend fun getPricing(
        @Path("productId") productId: Long,
        @Header("Authorization") bearerToken: String
    ): TcgPricingResponse
}
