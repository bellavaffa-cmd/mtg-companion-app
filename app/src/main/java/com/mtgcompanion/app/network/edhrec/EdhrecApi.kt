package com.mtgcompanion.app.network.edhrec

import retrofit2.http.GET
import retrofit2.http.Path

interface EdhrecApi {
    @GET("pages/commanders/{slug}.json")
    suspend fun getCommanderPage(@Path("slug") slug: String): EdhrecPage

    @GET("pages/cards/{slug}.json")
    suspend fun getCardPage(@Path("slug") slug: String): EdhrecPage
}

/**
 * Mirrors EDHREC's own slug algorithm: lowercase, strip punctuation, spaces to hyphens.
 * Verified against live endpoints, e.g. "Yuriko, the Tiger's Shadow" -> "yuriko-the-tigers-shadow".
 * A two-faced card's page is named for its front face alone — the full "A // B" name finds nothing.
 */
fun edhrecSlug(cardName: String): String {
    val cleaned = cardName.substringBefore(" // ").lowercase()
        .replace(Regex("[^a-z0-9\\s-]"), "")
        .trim()
        .replace(Regex("\\s+"), "-")
    return cleaned
}

/**
 * EDHREC's page for two commanders together: both names slugged, in alphabetical order, joined by
 * a hyphen — "Tymna the Weaver" and "Kraum, Ludevic's Opus" -> "kraum-ludevics-opus-tymna-the-weaver".
 */
fun edhrecPairSlug(first: String, second: String): String =
    listOf(edhrecSlug(first), edhrecSlug(second)).sorted().joinToString("-")
