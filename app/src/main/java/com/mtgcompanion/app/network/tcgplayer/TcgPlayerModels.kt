package com.mtgcompanion.app.network.tcgplayer

import com.squareup.moshi.Json

data class TcgTokenResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "expires_in") val expiresIn: Int? = null
)

data class TcgPricingResponse(
    val success: Boolean = false,
    val results: List<TcgPriceResult> = emptyList()
)

data class TcgPriceResult(
    val productId: Long? = null,
    val lowPrice: Double? = null,
    val midPrice: Double? = null,
    val highPrice: Double? = null,
    val marketPrice: Double? = null,
    val subTypeName: String? = null
)
