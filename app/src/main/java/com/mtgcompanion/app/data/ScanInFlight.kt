package com.mtgcompanion.app.data

/**
 * The card the camera has confirmed but whose lookup hasn't come back yet.
 *
 * The camera used to sit blocked for the whole of a card's lookup — the network round trip, paced
 * to Scryfall's limits — so nothing could be read until the card was added, and by then "that's the
 * card already in the list" was known. Now the camera moves on as soon as a card is confirmed, and
 * while its lookup is still out, the same card lingering in view has to be recognised as the one
 * already on its way rather than looked up a second time. OCR flickers frame to frame, so that's a
 * resemblance check, not an exact match.
 *
 * It also says whether a lookup that finishes was for the card still in view. If the card left
 * (a run of blank frames) while its lookup was out, the next card in is a fresh one — even another
 * copy of the same card — and mustn't be mistaken for the one that already went.
 */
class ScanInFlight {
    /** Bumped every time the card in view is confirmed gone. */
    private var generation = 0L
    /** The latest confirmed title, until its lookup finishes or the card leaves. */
    private var confirmed: String? = null

    /** A card was confirmed and its lookup queued. Hands back what [finished] needs to be told. */
    fun start(title: String): Long {
        confirmed = title
        return generation
    }

    /** Whether [title] is the card already on its way — the same physical card, still in view. */
    fun isOnItsWay(title: String, same: (String, String) -> Boolean): Boolean =
        confirmed?.let { same(title, it) } == true

    /** Whether the card whose lookup started as [token] is still the one in view. */
    fun stillInView(token: Long): Boolean = token == generation

    /** The card in view has gone: whatever comes next is a new card. */
    fun cardLeft() {
        generation++
        confirmed = null
    }

    /**
     * The lookup started as [token] for [title] has finished, found or not. True when the card it
     * was for hasn't left since — so it's still the card in view, and worth remembering as such.
     */
    fun finished(token: Long, title: String): Boolean {
        if (confirmed == title) confirmed = null
        return token == generation
    }
}
