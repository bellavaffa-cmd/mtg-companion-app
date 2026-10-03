package com.mtgcompanion.app.ui.lifecounter

import android.content.Context
import com.mtgcompanion.app.data.CardRepository
import com.mtgcompanion.app.data.Deck
import com.mtgcompanion.app.data.tokensNeeded
import com.mtgcompanion.app.network.scryfall.toArtCropUrl

/**
 * What [deck] brings to the table: the tokens its cards make (emblems left out — there's nothing to
 * count) and its "at the beginning of your …" cards. Deck entries don't keep oracle text, so the
 * cards come from Scryfall's batch lookup — one request per 75 cards, and one more for the tokens'
 * power and toughness. Null when the cards couldn't be looked up (offline, say).
 */
suspend fun loadSeatDeckInfo(deck: Deck, cardRepository: CardRepository): SeatDeckInfo? {
    val entries = listOfNotNull(deck.commander, deck.partnerCommander) + deck.cards
    if (entries.isEmpty()) return SeatDeckInfo(deck.name, emptyList(), emptyList())
    val byId = runCatching { cardRepository.getCardsByIds(entries.map { it.scryfallId }).associateBy { it.id } }.getOrNull()
    // The lookup gives back nothing at all rather than failing when it's offline.
    if (byId.isNullOrEmpty()) return null
    val needed = tokensNeeded(deck, byId).filterNot { it.isEmblem }
    val tokenCards = if (needed.isEmpty()) emptyMap()
    else runCatching { cardRepository.getCardsByIds(needed.map { it.id }).associateBy { it.id } }.getOrDefault(emptyMap())
    val tokens = needed.map { t ->
        val card = tokenCards[t.id]
        val power = card?.power
        val toughness = card?.toughness
        SeatToken(t.id, t.name, if (power != null && toughness != null) "$power/$toughness" else null, card?.displayImageUrl.toArtCropUrl())
    }
    val triggers = deckTriggers(entries.distinctBy { it.name }.map { it.name to byId[it.scryfallId]?.displayOracleText })
    return SeatDeckInfo(deck.name, tokens, triggers)
}

/**
 * A real vibration in [pattern] (off/on ms, as VibrationEffect.createWaveform takes it) — unlike
 * touch feedback, which is faint, and silent altogether when the phone's touch vibration is off.
 * False when the phone can't vibrate, so the caller can fall back.
 */
internal fun vibrate(context: Context, pattern: LongArray): Boolean {
    val vibrator = if (android.os.Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(android.os.Vibrator::class.java)
    }
    if (vibrator == null || !vibrator.hasVibrator()) return false
    return runCatching { vibrator.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1)) }.isSuccess
}
