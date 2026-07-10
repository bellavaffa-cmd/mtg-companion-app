package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.NetworkModule
import com.mtgcompanion.app.network.tcgplayer.TcgPriceResult

/**
 * Wraps TCGPlayer's partner API. Requires a client id/secret from TCGPlayer's own developer
 * program (https://docs.tcgplayer.com/) - there is no public, keyless tier. Until credentials are
 * configured in Settings, callers should fall back to Scryfall's bundled TCGPlayer-sourced prices.
 */
class TcgPlayerRepository(private val settingsRepository: SettingsRepository) {
    private val api = NetworkModule.tcgPlayerApi

    private var cachedToken: String? = null
    private var cachedForClientId: String? = null

    private suspend fun getBearerToken(): String? {
        val (clientId, clientSecret) = settingsRepository.currentCredentials() ?: return null
        if (cachedToken != null && cachedForClientId == clientId) return cachedToken

        val response = api.getToken(clientId = clientId, clientSecret = clientSecret)
        cachedToken = response.accessToken
        cachedForClientId = clientId
        return cachedToken
    }

    /** Returns null if no TCGPlayer credentials are configured, so callers can fall back. */
    suspend fun getMarketPrice(tcgplayerProductId: Long): List<TcgPriceResult>? {
        val token = getBearerToken() ?: return null
        return api.getPricing(tcgplayerProductId, "Bearer $token").results
    }

    fun invalidateToken() {
        cachedToken = null
        cachedForClientId = null
    }
}
