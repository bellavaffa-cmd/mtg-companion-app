package com.mtgcompanion.app.data

data class DeckCardEntry(
    val scryfallId: String,
    val name: String,
    val imageUrl: String?,
    val quantity: Int = 1,
    val canBeCommander: Boolean = false
)

data class Deck(
    val id: String,
    val name: String,
    val commander: DeckCardEntry? = null,
    val cards: List<DeckCardEntry> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

data class DeckStore(val decks: List<Deck> = emptyList())
