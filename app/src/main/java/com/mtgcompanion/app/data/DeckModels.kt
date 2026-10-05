package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.scryfall.ScryfallCard

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
    VINTAGE("Vintage", "vintage", 60, false, false, 4, false),
    // Draft and sealed: a 40-card deck from a pool (Limited.kt). No Scryfall format, so no card is
    // banned or not legal; no copy limit; and its sideboard is the pool, of any size.
    LIMITED("Limited", "", 40, false, false, Int.MAX_VALUE, false);

    /**
     * Whether the format has a sideboard (up to [MAX_SIDEBOARD] cards beside the main deck).
     * Commander and Brawl don't: their "sideboard" lines go to Considering instead.
     */
    val hasSideboard: Boolean get() = !usesCommander

    /** A draft or sealed deck, whose sideboard is the pool it's built from. */
    val limited: Boolean get() = this == LIMITED

    /** The most cards the sideboard may hold; null for no limit — a Limited deck's pool. */
    val sideboardLimit: Int? get() = if (limited) null else MAX_SIDEBOARD

    companion object {
        val DEFAULT = COMMANDER
        /** The most cards a sideboard may hold. */
        const val MAX_SIDEBOARD = 15
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
    PROXY("Proxy", "A real deck built with proxies. It counts as built, but its cards are worth nothing and aren't real copies you can trade."),
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
    // Cached from ScryfallCard.partnerAbility — how this card can share command with a second
    // commander: null, "Partner", the name a "Partner with" names, "Friends forever", "Choose a
    // Background", "Doctor's companion"… (see CommanderPairing.kt). The web stores the same values.
    val partnerAbility: String? = null,
    // Cached from ScryfallCard.backImageUrl — the second face's art for a transform/modal-DFC/flip
    // card, so the zoom overlay can offer a flip control without a network round-trip. Null for
    // single-faced cards and for entries added before this field existed.
    val backImageUrl: String? = null,
    // Cached from ScryfallCard.tags (printed keywords + heuristic theme tags) at add-time, so the
    // zoom overlay can show tag chips without a network round-trip. Empty for entries added before
    // this field existed.
    val tags: List<String> = emptyList(),
    /**
     * The user's own words about this copy — "proxy", "signed", "lent to Sam". They belong to the
     * copy rather than to the card, so they follow it from a binder into a deck and back, and every
     * entry holding the same printing carries the same set. Not [tags], which Scryfall writes and
     * the user can't change, and not [replaceable], which is only true inside one deck.
     */
    val userTags: List<String> = emptyList(),
    // A cut candidate: still in the deck (and in every stat) but flagged as the first thing to take
    // out for something better. Always false for entries on a deck's considering list.
    val replaceable: Boolean = false,
    /**
     * How many of this entry's copies are proxies (see Proxies.kt). Null means "whatever the deck
     * is": all of them in a deck marked Proxy, none in any other.
     */
    val proxyQuantity: Int? = null
)

/**
 * Whether a card can be the commander of a [mode] deck: a legendary creature, or a card whose
 * text says it "can be your commander" ([saysSo]). Brawl also takes a legendary planeswalker.
 * Formats without a commander take none. [typeLine] is the whole line, both faces of a
 * double-faced card included.
 */
fun canLeadDeck(typeLine: String?, saysSo: Boolean, mode: GameMode): Boolean {
    if (!mode.usesCommander) return false
    if (saysSo) return true
    val line = typeLine ?: return false
    if (!line.contains("Legendary")) return false
    return line.contains("Creature") || (mode == GameMode.BRAWL && line.contains("Planeswalker"))
}

/**
 * Whether this deck entry can be the commander of a [mode] deck. [DeckCardEntry.canBeCommander]
 * holds the Commander answer; Brawl also looks at the stored type line for a planeswalker (an
 * entry saved before type lines were kept has none, so only its Commander answer counts).
 */
fun DeckCardEntry.canLead(mode: GameMode): Boolean = canLeadDeck(typeLine, canBeCommander, mode)

/**
 * This entry with what a deck needs to know about [card] — commander-ness, type line, partner
 * ability, back face and tags — which an entry made from a binder card doesn't have. The entry's
 * own printing, picture, count and the user's tags stay as they are.
 */
fun DeckCardEntry.withCardInfo(card: ScryfallCard): DeckCardEntry = copy(
    imageUrl = imageUrl ?: card.displayImageUrl,
    canBeCommander = card.canBeCommander,
    typeLine = card.typeLine ?: typeLine,
    partnerAbility = card.partnerAbility,
    backImageUrl = card.backImageUrl ?: backImageUrl,
    tags = card.tags.ifEmpty { tags }
)

/** One logged game's outcome for a deck's match record. [result] is "WIN", "LOSS", or "DRAW". */
data class GameResult(
    val id: String,
    val result: String,
    /** Who they played, as names joined by ", ". */
    val opponent: String? = null,
    val playedAt: Long = System.currentTimeMillis(),
    /** How long the game ran, when a life counter table kept track. */
    val turns: Int? = null,
    val minutes: Int? = null,
    /** The commanders the opponents played (a partner pair as "A & B"). */
    val commanders: List<String> = emptyList()
)

/**
 * The deck's list as it stood at [savedAt]: card name -> copies (commanders included), keyed by
 * name so that swapping a card's printing doesn't read as a change. Edits within one sitting
 * collapse into a single version — see DeckRepository.
 */
data class DeckVersion(
    val id: String,
    val savedAt: Long,
    val cards: Map<String, Int> = emptyMap(),
    val commanders: List<String> = emptyList()
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
    val ownership: String = DeckOwnership.DEFAULT.name,
    // Cards the user thinks might work but hasn't committed to (a "maybeboard"). Deliberately kept
    // out of [cards], so they never count toward size, curve, price, legality, bracket or combos.
    val considering: List<DeckCardEntry> = emptyList(),
    // Oldest first. Capped — see DeckRepository.
    val versions: List<DeckVersion> = emptyList(),
    /**
     * The sideboard, for formats that have one (GameMode.hasSideboard). Like [considering] it's kept
     * out of [cards], so it never counts toward size, curve, price, bracket or combos — only the
     * legality check looks at it (at most 15 cards; copy limits count main deck and sideboard
     * together). JSON key "sideboard"; data saved before it existed reads as empty. Synced and
     * merged exactly like [considering].
     */
    val sideboard: List<DeckCardEntry> = emptyList()
) {
    val mode: GameMode get() = GameMode.fromName(gameMode)
    val ownershipType: DeckOwnership get() = DeckOwnership.fromName(ownership)
}

data class DeckStore(
    val decks: List<Deck> = emptyList(),
    /**
     * Decks the user deleted here and when, by id. Kept in this file rather than with the sync's
     * bookkeeping on purpose: if this store is lost, these go with it, and the sync can then tell a
     * deletion it was told about from a library that simply isn't there any more (SyncCore).
     */
    val deleted: Map<String, Long> = emptyMap(),
    /**
     * What each printing is tagged, by scryfallId — this store's own note, so a copy keeps its tags
     * when it's moved or re-added and a fresh entry is made for it (see UserTags.kt). Local
     * bookkeeping, rebuilt from the entries, which are what sync.
     */
    val userTags: Map<String, List<String>> = emptyMap()
)
