package com.mtgcompanion.app.data

/**
 * The whole backed-up library (decks + collections) plus the wall-clock time of the local edit
 * that produced it. [updatedAt] is what last-write-wins compares across devices.
 */
data class SyncPayload(
    val decks: List<Deck> = emptyList(),
    val collections: List<Collection> = emptyList(),
    val updatedAt: Long = 0L
)
