package com.mtgcompanion.app.data

/**
 * A play format a deck can be built for. [scryfallFormat] is the key used in Scryfall's
 * `legalities` map. [deckSize] is the required size (exact for singleton/commander formats,
 * a minimum for 60-card formats). [singleton] means at most one copy of each non-basic card;
 * otherwise [maxCopies] copies are allowed. [usesCommander] formats require a commander whose
 * colour identity constrains the rest of the deck.
 */
enum class GameMode(
    val label: String,
    val scryfallFormat: String,
    val deckSize: Int,
    val exactSize: Boolean,
    val singleton: Boolean,
    val maxCopies: Int,
    val usesCommander: Boolean
) {
    COMMANDER("Commander", "commander", 100, true, true, 1, true),
    BRAWL("Brawl", "brawl", 60, true, true, 1, true),
    STANDARD("Standard", "standard", 60, false, false, 4, false),
    PIONEER("Pioneer", "pioneer", 60, false, false, 4, false),
    MODERN("Modern", "modern", 60, false, false, 4, false),
    PAUPER("Pauper", "pauper", 60, false, false, 4, false),
    LEGACY("Legacy", "legacy", 60, false, false, 4, false),
    VINTAGE("Vintage", "vintage", 60, false, false, 4, false);

    companion object {
        val DEFAULT = COMMANDER
        fun fromName(name: String?): GameMode = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

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
    val gameMode: String = GameMode.DEFAULT.name,
    val createdAt: Long = System.currentTimeMillis()
) {
    val mode: GameMode get() = GameMode.fromName(gameMode)
}

data class DeckStore(val decks: List<Deck> = emptyList())
