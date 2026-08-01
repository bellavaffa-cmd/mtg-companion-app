package com.mtgcompanion.app.network.mtgjson

import retrofit2.http.GET
import retrofit2.http.Path

interface MtgJsonApi {
    @GET("DeckList.json")
    suspend fun getDeckList(): MtgJsonDeckIndex

    @GET("decks/{fileName}.json")
    suspend fun getDeck(@Path("fileName") fileName: String): MtgJsonDeckResponse
}
