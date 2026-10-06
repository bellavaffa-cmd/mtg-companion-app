package com.mtgcompanion.app.data

import com.mtgcompanion.app.network.NetworkModule
import com.mtgcompanion.app.network.mtgjson.MtgJsonDeckCard

/** One Commander precon, from MTGJSON's deck index. */
data class PreconInfo(
    val fileName: String,
    val name: String,
    val setCode: String,
    val releaseDate: String?
)

/** One card in a precon's exact contents. [scryfallId] is null if MTGJSON couldn't map it (rare). */
data class PreconCardEntry(val name: String, val scryfallId: String?, val quantity: Int)

data class PreconContents(val commander: List<PreconCardEntry>, val cards: List<PreconCardEntry>)

/**
 * Official Commander precon decklists, from MTGJSON's free public deck data — the same data
 * Wizards published, not a set's mixed card pool. The deck index (~600KB, every precon/theme deck
 * MTGJSON has ever indexed) is fetched once and cached in memory; each precon's full contents
 * (~600KB itself, since MTGJSON embeds full multi-language card data) is only fetched on demand,
 * when the user actually opens or imports it.
 */
class PreconRepository {
    private val api = NetworkModule.mtgJsonApi
    private var cachedPrecons: List<PreconInfo>? = null

    suspend fun listCommanderPrecons(): List<PreconInfo> {
        cachedPrecons?.let { return it }
        val list = api.getDeckList().data
            .filter { it.type == "Commander Deck" }
            .sortedByDescending { it.releaseDate.orEmpty() }
            .map { PreconInfo(it.fileName, it.name, it.code, it.releaseDate) }
        cachedPrecons = list
        return list
    }

    suspend fun getContents(fileName: String): PreconContents {
        val data = api.getDeck(fileName).data
        fun map(cards: List<MtgJsonDeckCard>) = cards.map { PreconCardEntry(it.name, it.identifiers?.scryfallId, it.count) }
        return PreconContents(map(data.commander), map(data.mainBoard))
    }
}

/**
 * Makes the precon in MTGJSON's [fileName] a new Commander deck called [name], each card resolved on
 * Scryfall — from the Precons screen, and when a sealed precon is opened (Sealed.kt). Throws with a
 * message to show when it can't. The web app's importPreconDeck (src/decks/preconImport.ts).
 */
suspend fun importPreconDeck(
    fileName: String,
    name: String,
    deckRepository: DeckRepository,
    preconRepository: PreconRepository = PreconRepository(),
    cardRepository: CardRepository = CardRepository()
): Deck {
    val contents = preconRepository.getContents(fileName)
    val all = contents.commander + contents.cards
    val ids = all.mapNotNull { it.scryfallId }.distinct()
    if (ids.isEmpty()) throw IllegalStateException("Couldn't resolve any cards for this precon.")
    val cardsById = cardRepository.getCardsByIds(ids).associateBy { it.id }
    val deckEntries = all.mapNotNull { entry ->
        val id = entry.scryfallId ?: return@mapNotNull null
        val card = cardsById[id] ?: return@mapNotNull null
        DeckCardEntry(card.id, card.name, card.displayImageUrl, entry.quantity, card.canBeCommander, card.typeLine, card.partnerAbility, card.backImageUrl, card.tags)
    }
    if (deckEntries.isEmpty()) throw IllegalStateException("None of this precon's cards could be found on Scryfall.")
    // MTGJSON lists 2 commanders for a partner precon — set both when present.
    val commanderEntries = contents.commander.mapNotNull { it.scryfallId }.mapNotNull { id -> deckEntries.firstOrNull { it.scryfallId == id } }
    return deckRepository.createDeckWithCards(
        name,
        GameMode.COMMANDER,
        deckEntries,
        commander = commanderEntries.getOrNull(0),
        partnerCommander = commanderEntries.getOrNull(1)
    )
}
