package com.mtgcompanion.app.network.edhrec

import retrofit2.http.GET
import retrofit2.http.Path

interface EdhrecApi {
    @GET("pages/commanders/{slug}.json")
    suspend fun getCommanderPage(@Path("slug") slug: String): EdhrecPage
}

/**
 * Mirrors EDHREC's own slug algorithm: lowercase, strip punctuation, spaces to hyphens.
 * Verified against live endpoints, e.g. "Yuriko, the Tiger's Shadow" -> "yuriko-the-tigers-shadow".
 */
fun edhrecSlug(cardName: String): String {
    val cleaned = cardName.lowercase()
        .replace(Regex("[^a-z0-9\\s-]"), "")
        .trim()
        .replace(Regex("\\s+"), "-")
    return cleaned
}
