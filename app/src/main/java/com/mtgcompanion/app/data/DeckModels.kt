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

/**
 * Whether a deck's cards represent real cards the user owns.
 * - [PHYSICAL]: a deck the user physically owns — its cards count toward what they own.
 * - [VIRTUAL]: a deck the user doesn't physically own (e.g. a copy of someone else's list, an
 *   online-only deck) — its cards don't count toward owned totals.
 * - [PROTOTYPE]: a deck still being built/tested, incomplete by design — same as Virtual, its
 *   cards aren't counted as owned until the deck is finished and marked Physical.
 */
enum class DeckOwnership(val label: String, val description: String) {
    PHYSICAL("Physical", "You own this deck's cards — they count toward your collection."),
    VIRTUAL("Virtual", "You don't own this deck physically — its cards aren't counted as owned."),
    PROTOTYPE("Prototype", "Still being built — its cards aren't counted as owned yet.");

    companion object {
        val DEFAULT = PHYSICAL
        fun fromName(name: String?): DeckOwnership = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

data class DeckCardEntry(
    val scryfallId: String,
    val name: String,
    val imageUrl: String?,
    val quantity: Int = 1,
    val canBeCommander: Boolean = false,
    // Cached from Scryfall at add-time so the Cards tab can group by type instantly on open,
    // without waiting on a network round-trip. Null for entries added before this field existed.
    val typeLine: String? = null,
    // Cached from ScryfallCard.partnerAbility — null (no partner ability), "Partner" (pairs with
    // any other plain-partner card), or the exact "Partner with <Name>" target.
    val partnerAbility: String? = null,
    // Cached from ScryfallCard.backImageUrl — the second face's art for a transform/modal-DFC/flip
    // card, so the zoom overlay can offer a flip control without a network round-trip. Null for
    // single-faced cards and for entries added before this field existed.
    val backImageUrl: String? = null,
    // Cached from ScryfallCard.tags (printed keywords + heuristic theme tags) at add-time, so the
    // zoom overlay can show tag chips without a network round-trip. Empty for entries added before
    // this field existed.
    val tags: List<String> = emptyList()
)

/** Whether [a] and [b] can legally be co-commanders under the Partner mechanic. */
fun partnersWith(a: DeckCardEntry, b: DeckCardEntry): Boolean {
    val abilityA = a.partnerAbility ?: return false
    val abilityB = b.partnerAbility ?: return false
    if (abilityA == "Partner" && abilityB == "Partner") return true
    if (abilityA.equals(b.name, ignoreCase = true)) return true
    if (abilityB.equals(a.name, ignoreCase = true)) return true
    return false
}

/** One logged game's outcome for a deck's match record. [result] is "WIN", "LOSS", or "DRAW". */
data class GameResult(
    val id: String,
    val result: String,
    val opponent: String? = null,
    val playedAt: Long = System.currentTimeMillis()
)

data class Deck(
    val id: String,
    val name: String,
    val commander: DeckCardEntry? = null,
    // A reference into [cards], same as [commander] — only meaningful when a Partner pairing with
    // [commander] is valid; the repository clears it whenever that stops being true.
    val partnerCommander: DeckCardEntry? = null,
    val cards: List<DeckCardEntry> = emptyList(),
    val gameMode: String = GameMode.DEFAULT.name,
    val createdAt: Long = System.currentTimeMillis(),
    val tags: List<String> = emptyList(),
    val gameResults: List<GameResult> = emptyList(),
    val ownership: String = DeckOwnership.DEFAULT.name
) {
    val mode: GameMode get() = GameMode.fromName(gameMode)
    val ownershipType: DeckOwnership get() = DeckOwnership.fromName(ownership)
}

data class DeckStore(val decks: List<Deck> = emptyList())
